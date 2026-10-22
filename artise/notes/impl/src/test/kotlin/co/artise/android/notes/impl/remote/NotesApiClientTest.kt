/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.remote

import co.artise.android.notes.api.ChangeAuthor
import co.artise.android.notes.api.FileChange
import co.artise.android.notes.api.NotesException
import co.artise.android.notes.api.NotesPage
import co.artise.android.notes.api.ServerCopy
import co.artise.android.notes.impl.A_ROOM
import co.artise.android.notes.impl.A_ROOM_ENCODED
import co.artise.android.notes.impl.FakeNotesTokenSource
import co.artise.android.notes.impl.NotesMockServer
import co.artise.android.notes.impl.error
import co.artise.android.notes.impl.json
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okio.Buffer
import org.junit.After
import org.junit.Test

class NotesApiClientTest {
    private val server = NotesMockServer()
    private val tokens = FakeNotesTokenSource()
    private val room = RoomId(A_ROOM)

    @After
    fun tearDown() = server.shutdown()

    private fun TestScope.client(): NotesApiClient {
        val baseUrl = server.baseUrl
        return NotesApiClient(OkHttpClient(), tokens, testCoroutineDispatchers(useUnconfinedTestDispatcher = true)) { baseUrl }
    }

    /** A workspace whose server has no notes (no `links.notes_api`): nothing is sent, and no token is even asked for. */
    @Test
    fun `no notes api means no request`() = runTest {
        val client = NotesApiClient(OkHttpClient(), tokens, testCoroutineDispatchers(useUnconfinedTestDispatcher = true)) { null }
        val result = client.chats()
        assertThat(result.exceptionOrNull()).isInstanceOf(NotesException.Network::class.java)
        assertThat(server.requests).isEmpty()
        assertThat(tokens.issued).isEqualTo(0)
    }

    /** Every request carries the OpenID token, and room ids are fully percent-encoded in the path as the contract asks. */
    @Test
    fun `requests are signed and room ids encoded`() = runTest {
        server.enqueue(
            "GET /chats/$A_ROOM_ENCODED/note?path=Recetas%2FMole.md",
            json(
                body = """
                    {"path": "Recetas/Mole.md", "version": "v1", "content": "# Mole"}
                """.trimIndent(),
            ),
        )
        val note = client().note(room, "Recetas/Mole.md").getOrThrow()
        assertThat(note.content).isEqualTo("# Mole")
        assertThat(server.requests.single().getHeader("Authorization")).isEqualTo("Bearer token-1")
    }

    /** Non-ASCII note names (Spanish accents) are sent UTF-8 encoded in the query. */
    @Test
    fun `accented paths are encoded`() = runTest {
        server.enqueue(
            "GET /chats/$A_ROOM_ENCODED/links?path=S%C3%BAper.md",
            json(
                body = """
                    {"path": "Súper.md", "outgoing": [{"target": "Leche", "path": null}],
                    "backlinks": [{"path": "Recetas/Mole.md", "line": "Ver [[Súper]]."}]}
                """.trimIndent(),
            ),
        )
        val links = client().links(room, "Súper.md").getOrThrow()
        assertThat(links.outgoing.single().path).isNull()
        assertThat(links.backlinks.single().line).isEqualTo("Ver [[Súper]].")
    }

    /** The change history: each change says who it was for and how, with the files it touched; the next page asks with `before`. */
    @Test
    fun `changes are read with their author and files, and paged with before`() = runTest {
        server.enqueue(
            "GET /chats/$A_ROOM_ENCODED/changes?limit=50",
            json(
                body = """
                    {"changes": [
                      {"commit": "c2", "at": "2026-10-10T18:02:11-06:00", "message": "Weekly list", "who": "Ana", "how": "automation",
                       "detail": "#3", "by": "Ana",
                       "files": [{"path": "Súper.md", "change": "modified"},
                                 {"path": "Recetas/Mole poblano.md", "change": "renamed", "from": "Recetas/Mole.md"},
                                 {"path": "x.md", "change": "typechanged"}]},
                      {"commit": "c1", "at": "2026-10-09T08:00:00-06:00", "message": "edited outside chat", "who": null, "how": "sync",
                       "detail": "", "by": "synced device", "files": [{"path": "Fotos/luna.jpg", "change": "deleted"}]}
                    ], "more": true}
                """.trimIndent(),
            ),
        )
        server.enqueue("GET /chats/$A_ROOM_ENCODED/changes?limit=50&before=c1", json(body = """{"changes": [], "more": false}"""))
        val page = client().changes(room, before = null).getOrThrow()
        assertThat(page.more).isTrue()
        val first = page.changes.first()
        assertThat(first.author).isEqualTo(ChangeAuthor(who = "Ana", how = "automation", detail = "#3", by = "Ana"))
        assertThat(first.files.map { it.change }).containsExactly(FileChange.MODIFIED, FileChange.RENAMED, FileChange.MODIFIED).inOrder()
        assertThat(first.files[1].from).isEqualTo("Recetas/Mole.md")
        assertThat(page.changes.last().author).isEqualTo(ChangeAuthor(who = null, how = "sync", detail = "", by = "synced device"))
        assertThat(page.changes.last().files.single().change).isEqualTo(FileChange.DELETED)
        assertThat(client().changes(room, before = "c1").getOrThrow().changes).isEmpty()
    }

    /** A note's versions carry who/how/detail; an older server sends only `by`, which stays the fallback. */
    @Test
    fun `history reads the new fields and older servers`() = runTest {
        server.enqueue(
            "GET /chats/$A_ROOM_ENCODED/history?path=S%C3%BAper.md",
            json(
                body = """
                    {"path": "Súper.md", "versions": [
                      {"commit": "v2", "at": "2026-10-10T18:02:11-06:00", "message": "add pan", "who": "Luis", "how": "reaction", "detail": "", "by": "Luis"},
                      {"commit": "v1", "at": "2026-09-29T18:02:11-06:00", "message": "app: edit Súper.md", "by": "Ana"}
                    ]}
                """.trimIndent(),
            ),
        )
        val versions = client().history(room, "Súper.md").getOrThrow()
        assertThat(versions.map { it.author }).containsExactly(
            ChangeAuthor(who = "Luis", how = "reaction", detail = "", by = "Luis"),
            ChangeAuthor(who = null, how = null, detail = "", by = "Ana"),
        ).inOrder()
    }

    /** With an ETag, an unchanged tree is a 304 and nothing is downloaded. */
    @Test
    fun `tree sends If-None-Match and understands 304`() = runTest {
        server.enqueue("GET /chats/$A_ROOM_ENCODED/tree", MockResponse().setResponseCode(304))
        val response = client().tree(room, etag = "\"9f1c\"").getOrThrow()
        assertThat(response).isEqualTo(TreeResponse.NotModified)
        assertThat(server.requests.single().getHeader("If-None-Match")).isEqualTo("\"9f1c\"")
    }

    /** A changed tree comes with its files and the ETag to send next time. */
    @Test
    fun `tree returns files and etag`() = runTest {
        server.enqueue(
            "GET /chats/$A_ROOM_ENCODED/tree",
            json(
                body = """
                    {"tree": "9f1c",
                     "files": [{"path": "Recetas/Mole.md", "version": "3b18", "size": 812, "modified": 1790000000, "note": true}]}
                """.trimIndent(),
                headers = mapOf("ETag" to "\"9f1c\""),
            ),
        )
        val changed = client().tree(room, etag = null).getOrThrow() as TreeResponse.Changed
        assertThat(changed.etag).isEqualTo("\"9f1c\"")
        assertThat(changed.tree.files.single().isNote).isTrue()
        assertThat(server.requests.single().getHeader("If-None-Match")).isNull()
    }

    /** Creating a note sends `base_version: null` explicitly, which is how the server knows it's a creation. */
    @Test
    fun `creating a note sends a null base version`() = runTest {
        server.enqueue("PUT /chats/$A_ROOM_ENCODED/note", json(201, """{"path": "Nueva.md", "version": "v1", "merged": false}"""))
        client().saveNote(room, "Nueva.md", "hola", baseVersion = null).getOrThrow()
        val body = Json.parseToJsonElement(server.requests.single().body.readUtf8()).jsonObject
        assertThat(body["base_version"]).isEqualTo(JsonNull)
    }

    /** A merged save returns the merged text to show. */
    @Test
    fun `merged save returns the merged content`() = runTest {
        server.enqueue("PUT /chats/$A_ROOM_ENCODED/note", json(body = """{"path": "Súper.md", "version": "v3", "merged": true, "content": "- leche\n- pan"}"""))
        val saved = client().saveNote(room, "Súper.md", "- leche", baseVersion = "v1").getOrThrow()
        assertThat(saved.merged).isTrue()
        assertThat(saved.content).isEqualTo("- leche\n- pan")
    }

    /** A 409 conflict carries the server's copy, so the app can show both versions. */
    @Test
    fun `conflict carries the server copy`() = runTest {
        server.enqueue("PUT /chats/$A_ROOM_ENCODED/note", error(409, "conflict", """"current": {"path": "Súper.md", "version": "v2", "content": "- pan"}"""))
        val error = client().saveNote(room, "Súper.md", "- leche", baseVersion = "v1").exceptionOrNull()
        assertThat(error).isInstanceOf(NotesException.Conflict::class.java)
        assertThat((error as NotesException.Conflict).current).isEqualTo(ServerCopy("Súper.md", "v2", "- pan"))
        assertThat(error.message).isEqualTo("Human text for conflict")
    }

    /** Every error code in the contract maps to its own exception. */
    @Test
    fun `error codes map to exceptions`() = runTest {
        val cases = mapOf(
            error(404, "no_chat") to NotesException.NoChat::class.java,
            error(400, "bad_path") to NotesException.BadPath::class.java,
            error(404, "not_found") to NotesException.NotFound::class.java,
            error(409, "exists", """"current": {"path": "a.md", "version": "v"}""") to NotesException.Exists::class.java,
            error(409, "deleted") to NotesException.Deleted::class.java,
            error(413, "too_big") to NotesException.TooBig::class.java,
            error(415, "not_a_note") to NotesException.NotANote::class.java,
            MockResponse().setResponseCode(502).setBody("<html>Bad gateway</html>") to NotesException.Server::class.java,
        )
        val client = client()
        for ((response, expected) in cases) {
            server.enqueue("GET /chats/$A_ROOM_ENCODED/note?path=a.md", response)
            assertThat(client.note(room, "a.md").exceptionOrNull()).isInstanceOf(expected)
        }
    }

    /** A 401 gets one retry with a fresh token; the refused token is invalidated. */
    @Test
    fun `401 retries once with a new token`() = runTest {
        server.enqueue("GET /chats", error(401, "unauthorized"))
        server.enqueue("GET /chats", json(body = """{"user": "@ana:artise.co", "chats": [{"id": "!abc:artise.co", "name": "Familia", "tree": "9f1c"}]}"""))
        val chats = client().chats().getOrThrow()
        assertThat(chats.single().name).isEqualTo("Familia")
        assertThat(tokens.invalidated).containsExactly("token-1")
        assertThat(server.requests.map { it.getHeader("Authorization") }).containsExactly("Bearer token-1", "Bearer token-2").inOrder()
    }

    /** A second 401 in a row is a real sign-in problem: no endless retries. */
    @Test
    fun `second 401 gives up`() = runTest {
        server.always("GET /chats", error(401, "unauthorized"))
        assertThat(client().chats().exceptionOrNull()).isInstanceOf(NotesException.Unauthorized::class.java)
        assertThat(server.requests).hasSize(2)
    }

    /** No token available means Unauthorized, without calling the server. */
    @Test
    fun `no token means unauthorized without a request`() = runTest {
        tokens.failure = IllegalStateException("no session")
        assertThat(client().chats().exceptionOrNull()).isInstanceOf(NotesException.Unauthorized::class.java)
        assertThat(server.requests).isEmpty()
    }

    /** No connection is a Network error, which the sync engine treats as "try again later". */
    @Test
    fun `no connection is a network error`() = runTest {
        val client = client()
        server.shutdown()
        assertThat(client.chats().exceptionOrNull()).isInstanceOf(NotesException.Network::class.java)
    }

    /** A move reports which other notes had their links rewritten. */
    @Test
    fun `move returns updated links`() = runTest {
        server.enqueue(
            "POST /chats/$A_ROOM_ENCODED/move",
            json(
                body = """
                    {"from": "Súper.md", "to": "Listas/Súper.md", "version": "v2",
                    "links_updated": ["Recetas/Mole.md"]}
                """.trimIndent(),
            ),
        )
        val moved = client().move(room, "Súper.md", "Listas/Súper.md", "v1").getOrThrow()
        assertThat(moved.linksUpdated).containsExactly("Recetas/Mole.md")
    }

    /** The graph's edges come as pairs. */
    @Test
    fun `graph edges are pairs`() = runTest {
        server.enqueue(
            "GET /chats/$A_ROOM_ENCODED/graph",
            json(
                body = """
                    {"nodes": ["Recetas/Mole.md", "Súper.md"], "edges": [["Recetas/Mole.md", "Súper.md"]]}
                """.trimIndent(),
            ),
        )
        assertThat(client().graph(room).getOrThrow().edges).containsExactly("Recetas/Mole.md" to "Súper.md")
    }

    /** Deleting sends the base version, so the server can refuse if someone changed the file meanwhile. */
    @Test
    fun `delete sends the base version`() = runTest {
        server.enqueue("DELETE /chats/$A_ROOM_ENCODED/file?path=a.md&base=v1", json(body = """{"path": "a.md", "deleted": true}"""))
        assertThat(client().delete(room, "a.md", "v1").isSuccess).isTrue()
    }

    /** The chat's pages come with title, type, folder, aliases and summary; photos and documents are pages too. */
    @Test
    fun `pages are read`() = runTest {
        server.enqueue(
            "GET /chats/$A_ROOM_ENCODED/pages",
            json(
                body = """
                    {"pages": [
                      {"path": "people/Ana.md", "title": "Ana", "type": "person", "folder": "people",
                       "aliases": ["Mamá"], "summary": "Ana, Hector's mother", "note": true},
                      {"path": "Fotos/playa.jpg", "title": "playa.jpg", "type": "image", "folder": "", "aliases": [], "summary": "", "note": false}]}
                """.trimIndent(),
            ),
        )
        val pages = client().pages(room).getOrThrow()
        assertThat(pages).containsExactly(
            NotesPage("people/Ana.md", "Ana", "person", "people", listOf("Mamá"), "Ana, Hector's mother", isNote = true),
            NotesPage("Fotos/playa.jpg", "playa.jpg", "image", "", emptyList(), "", isNote = false),
        ).inOrder()
    }

    /** An ambiguous link comes with its candidates, so the app can ask instead of opening the first. */
    @Test
    fun `ambiguous links carry their candidates`() = runTest {
        server.enqueue(
            "GET /chats/$A_ROOM_ENCODED/links?path=S%C3%BAper.md",
            json(body = """{"path": "Súper.md", "outgoing": [{"target": "Ana", "path": null, "candidates": ["people/Ana.md", "clients/Ana.md"]}]}"""),
        )
        val link = client().links(room, "Súper.md").getOrThrow().outgoing.single()
        assertThat(link.path).isNull()
        assertThat(link.candidates).containsExactly("people/Ana.md", "clients/Ana.md").inOrder()
    }

    private val previewKey = "GET /chats/$A_ROOM_ENCODED/preview?path=docs%2FRecetario.pdf&size=256"

    private fun png(bytes: ByteArray, etag: String = "\"p1\"") = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "image/png")
        .setHeader("X-Width", "396")
        .setHeader("X-Height", "512")
        .setHeader("ETag", etag)
        .setBody(Buffer().write(bytes))

    /** `/preview` is asked with the encoded path, the size and the token; a PNG comes back with its measures and ETag. */
    @Test
    fun `preview requests a picture`() = runTest {
        server.enqueue(previewKey, png(byteArrayOf(1, 2, 3)))
        val answer = client().preview(room, "docs/Recetario.pdf", 256).getOrThrow() as PreviewResponse.Image
        assertThat(answer.bytes).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(answer.width).isEqualTo(396)
        assertThat(answer.height).isEqualTo(512)
        assertThat(answer.etag).isEqualTo("\"p1\"")
        val request = server.requests.single()
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer token-1")
        assertThat(request.getHeader("If-None-Match")).isNull()
    }

    @Test
    fun `preview reads the first lines of a text file`() = runTest {
        server.enqueue(
            "GET /chats/$A_ROOM_ENCODED/preview?path=lista.csv&size=128",
            MockResponse().setResponseCode(200).setHeader("Content-Type", "text/plain; charset=utf-8").setHeader("ETag", "\"t1\"").setBody("a,b\n1,2"),
        )
        val answer = client().preview(room, "lista.csv", 128).getOrThrow()
        assertThat(answer).isEqualTo(PreviewResponse.Text("a,b\n1,2", etag = "\"t1\""))
    }

    @Test
    fun `preview sends the etag it has and accepts not modified`() = runTest {
        server.enqueue(previewKey, MockResponse().setResponseCode(304))
        val answer = client().preview(room, "docs/Recetario.pdf", 256, etag = "\"p1\"").getOrThrow()
        assertThat(answer).isEqualTo(PreviewResponse.NotModified)
        assertThat(server.requests.single().getHeader("If-None-Match")).isEqualTo("\"p1\"")
    }

    /** No preview for Office files (415), a broken file (422) or a missing one (404): none, not an error. */
    @Test
    fun `preview is none for files the server cannot show`() = runTest {
        val client = client()
        server.enqueue(previewKey, error(415, "no_preview"))
        assertThat(client.preview(room, "docs/Recetario.pdf", 256).getOrThrow()).isEqualTo(PreviewResponse.None)
        server.enqueue(previewKey, error(422, "unreadable"))
        assertThat(client.preview(room, "docs/Recetario.pdf", 256).getOrThrow()).isEqualTo(PreviewResponse.None)
        server.enqueue(previewKey, error(404, "not_found"))
        assertThat(client.preview(room, "docs/Recetario.pdf", 256).getOrThrow()).isEqualTo(PreviewResponse.None)
        // Anything else is still a failure
        server.enqueue(previewKey, error(500, "boom"))
        assertThat(client.preview(room, "docs/Recetario.pdf", 256).isFailure).isTrue()
    }
}
