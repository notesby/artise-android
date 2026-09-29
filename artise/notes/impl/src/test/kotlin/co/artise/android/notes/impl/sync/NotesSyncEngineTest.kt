/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import co.artise.android.notes.api.EditKind
import co.artise.android.notes.api.EditState
import co.artise.android.notes.api.NotesException
import co.artise.android.notes.impl.A_ROOM
import co.artise.android.notes.impl.A_ROOM_ENCODED
import co.artise.android.notes.impl.FakeNotesTokenSource
import co.artise.android.notes.impl.NotesMockServer
import co.artise.android.notes.impl.db.NotesDatabase
import co.artise.android.notes.impl.error
import co.artise.android.notes.impl.json
import co.artise.android.notes.impl.local.NotesLocalStore
import co.artise.android.notes.impl.remote.NotesApiClient
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Test

class NotesSyncEngineTest {
    private val server = NotesMockServer()
    private val room = RoomId(A_ROOM)
    private val tree = "GET /chats/$A_ROOM_ENCODED/tree"
    private val put = "PUT /chats/$A_ROOM_ENCODED/note"

    @After
    fun tearDown() = server.shutdown()

    private fun TestScope.engine(): NotesSyncEngine {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { NotesDatabase.Schema.create(it) }
        val dispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true)
        return NotesSyncEngine(
            api = NotesApiClient(OkHttpClient(), FakeNotesTokenSource(), dispatchers, server.baseUrl),
            store = NotesLocalStore(NotesDatabase(driver)),
            clock = FakeSystemClock(),
            dispatchers = dispatchers,
        )
    }

    private fun treeResponse(hash: String, vararg files: Pair<String, String>): MockResponse {
        val entries = files.joinToString { (path, version) ->
            """{"path": "$path", "version": "$version", "size": 10, "modified": 1790000000, "note": ${path.endsWith(".md")}}"""
        }
        return json(body = """{"tree": "$hash", "files": [$entries]}""", headers = mapOf("ETag" to "\"$hash\""))
    }

    private fun note(path: String, version: String, content: String) = json(body = """{"path": "$path", "version": "$version", "content": "$content"}""")

    private fun noteKey(path: String) = "GET /chats/$A_ROOM_ENCODED/note?path=$path"

    /** Joins the chat's notes: every note is downloaded for offline use, photos only listed. */
    private suspend fun NotesSyncEngine.firstSync() {
        server.enqueue(tree, treeResponse("t1", "Súper.md" to "v1", "Mole.md" to "m1", "luna.jpg" to "p1"))
        server.enqueue(noteKey("S%C3%BAper.md"), note("Súper.md", "v1", "- leche"))
        server.enqueue(noteKey("Mole.md"), note("Mole.md", "m1", "# Mole"))
        sync(room).getOrThrow()
    }

    /** First sync downloads notes' text, lists raw files without downloading them, and saves the tree. */
    @Test
    fun `first sync downloads notes and lists raw files`() = runTest {
        val engine = engine()
        engine.firstSync()
        val files = engine.files(room).associateBy { it.path }
        assertThat(files["Súper.md"]?.content).isEqualTo("- leche")
        assertThat(files["luna.jpg"]?.content).isNull()
        assertThat(server.requests.none { it.path.orEmpty().contains("raw") }).isTrue()
    }

    /** Syncing again with nothing changed costs one 304 and changes nothing. */
    @Test
    fun `unchanged tree is a single 304`() = runTest {
        val engine = engine()
        engine.firstSync()
        server.requests.clear()
        server.enqueue(tree, MockResponse().setResponseCode(304))
        val report = engine.sync(room).getOrThrow()
        assertThat(report.updated).isEqualTo(0)
        assertThat(server.requests.single().getHeader("If-None-Match")).isEqualTo("\"t1\"")
    }

    /** Only files whose version changed are downloaded again; files gone from the server are dropped. */
    @Test
    fun `pull updates changed files and drops removed ones`() = runTest {
        val engine = engine()
        engine.firstSync()
        server.requests.clear()
        server.enqueue(tree, treeResponse("t2", "Súper.md" to "v2", "luna.jpg" to "p1"))
        server.enqueue(noteKey("S%C3%BAper.md"), note("Súper.md", "v2", "- leche\n- pan"))
        val report = engine.sync(room).getOrThrow()
        assertThat(report.updated).isEqualTo(1)
        assertThat(report.removed).isEqualTo(1)
        assertThat(engine.file(room, "Mole.md")).isNull()
        assertThat(server.requestsTo(noteKey("Mole.md"))).isEmpty()
    }

    /** Edits made offline show at once, and several edits before a sync go out as one save from the original version. */
    @Test
    fun `offline edits are local at once and coalesced`() = runTest {
        val engine = engine()
        engine.firstSync()
        engine.editNote(room, "Súper.md", "- leche\n- huevos")
        engine.editNote(room, "Súper.md", "- leche\n- huevos\n- pan")
        assertThat(engine.file(room, "Súper.md")?.content).isEqualTo("- leche\n- huevos\n- pan")
        val edit = engine.edits(room).single()
        assertThat(edit.kind).isEqualTo(EditKind.SAVE)

        server.enqueue(put, json(body = """{"path": "Súper.md", "version": "v2", "merged": false}"""))
        server.enqueue(tree, MockResponse().setResponseCode(304))
        assertThat(engine.sync(room).getOrThrow().sent).isEqualTo(1)
        val body = Json.parseToJsonElement(server.requestsTo(put).single().body.readUtf8()).jsonObject
        assertThat(body["base_version"]?.jsonPrimitive?.content).isEqualTo("v1")
        assertThat(engine.edits(room)).isEmpty()
        assertThat(engine.file(room, "Súper.md")?.version).isEqualTo("v2")
    }

    /** When the server merged our edit with someone else's, the merged text replaces ours. */
    @Test
    fun `merged save replaces the local text`() = runTest {
        val engine = engine()
        engine.firstSync()
        engine.editNote(room, "Súper.md", "- leche\n- huevos")
        server.enqueue(put, json(body = """{"path": "Súper.md", "version": "v3", "merged": true, "content": "- leche\n- huevos\n- pan"}"""))
        server.enqueue(tree, MockResponse().setResponseCode(304))
        engine.sync(room).getOrThrow()
        assertThat(engine.file(room, "Súper.md")?.content).isEqualTo("- leche\n- huevos\n- pan")
    }

    /** No connection: the edit stays queued with its text, and nothing is lost. */
    @Test
    fun `network failure keeps the queue`() = runTest {
        val engine = engine()
        engine.firstSync()
        engine.editNote(room, "Súper.md", "- leche\n- huevos")
        server.shutdown()
        assertThat(engine.sync(room).exceptionOrNull()).isInstanceOf(NotesException.Network::class.java)
        assertThat(engine.edits(room).single().state).isEqualTo(EditState.PENDING)
        assertThat(engine.file(room, "Súper.md")?.content).isEqualTo("- leche\n- huevos")
    }

    /**
     * A conflict waits for the person without blocking other notes; a later pull never overwrites the conflicted note;
     * resolving saves the choice on top of the server's version.
     */
    @Test
    fun `conflict waits for a choice and resolving saves on the server version`() = runTest {
        val engine = engine()
        engine.firstSync()
        engine.editNote(room, "Súper.md", "- leche\n- huevos")
        engine.editNote(room, "Mole.md", "# Mole poblano")
        server.enqueue(put, error(409, "conflict", """"current": {"path": "Súper.md", "version": "v2", "content": "- pan"}"""))
        server.enqueue(put, json(body = """{"path": "Mole.md", "version": "m2", "merged": false}"""))
        server.enqueue(tree, treeResponse("t2", "Súper.md" to "v2", "Mole.md" to "m2", "luna.jpg" to "p1"))
        server.enqueue(noteKey("Mole.md"), note("Mole.md", "m2", "# Mole poblano"))

        val report = engine.sync(room).getOrThrow()
        assertThat(report.sent).isEqualTo(1)
        assertThat(report.needChoice).isEqualTo(1)
        val conflict = engine.edits(room).single()
        assertThat(conflict.state).isEqualTo(EditState.CONFLICT)
        assertThat(conflict.serverCopy?.content).isEqualTo("- pan")
        // The pull skipped Súper.md: the person's text is still there.
        assertThat(engine.file(room, "Súper.md")?.content).isEqualTo("- leche\n- huevos")
        assertThat(server.requestsTo(noteKey("S%C3%BAper.md"))).hasSize(1)

        engine.resolveConflict(conflict.id, "- leche\n- huevos\n- pan")
        server.enqueue(put, json(body = """{"path": "Súper.md", "version": "v3", "merged": false}"""))
        server.enqueue(tree, MockResponse().setResponseCode(304))
        engine.sync(room).getOrThrow()
        val resent = Json.parseToJsonElement(server.requestsTo(put).last().body.readUtf8()).jsonObject
        assertThat(resent["base_version"]?.jsonPrimitive?.content).isEqualTo("v2")
        assertThat(engine.edits(room)).isEmpty()
    }

    /** A note created offline is sent as a creation; deleting it before it was sent leaves nothing to do. */
    @Test
    fun `new notes are created and unsent ones deleted locally`() = runTest {
        val engine = engine()
        engine.firstSync()
        engine.createNote(room, "Ideas.md", "- viaje").getOrThrow()
        assertThat(engine.createNote(room, "Ideas.md", "otra").exceptionOrNull()).isInstanceOf(NotesException.Exists::class.java)
        engine.deleteFile(room, "Ideas.md")
        assertThat(engine.edits(room)).isEmpty()
        assertThat(engine.file(room, "Ideas.md")).isNull()
    }

    /** Deleting a synced note queues a delete with its version; a note already gone on the server counts as done. */
    @Test
    fun `deletes are queued and already-gone counts as done`() = runTest {
        val engine = engine()
        engine.firstSync()
        engine.deleteFile(room, "Mole.md")
        assertThat(engine.edits(room).single().kind).isEqualTo(EditKind.DELETE)
        server.enqueue("DELETE /chats/$A_ROOM_ENCODED/file?path=Mole.md&base=m1", error(404, "not_found"))
        server.enqueue(tree, MockResponse().setResponseCode(304))
        engine.sync(room).getOrThrow()
        assertThat(engine.edits(room)).isEmpty()
    }

    /** Someone deleted the note while we edited it: keeping our copy re-creates it. */
    @Test
    fun `deleted meanwhile can be kept`() = runTest {
        val engine = engine()
        engine.firstSync()
        engine.editNote(room, "Mole.md", "# Mole negro")
        server.enqueue(put, error(409, "deleted"))
        server.enqueue(tree, MockResponse().setResponseCode(304))
        engine.sync(room).getOrThrow()
        val edit = engine.edits(room).single()
        assertThat(edit.state).isEqualTo(EditState.DELETED)

        engine.keepDeletedNote(edit.id)
        server.enqueue(put, json(201, """{"path": "Mole.md", "version": "m9", "merged": false}"""))
        server.enqueue(tree, MockResponse().setResponseCode(304))
        engine.sync(room).getOrThrow()
        val body = Json.parseToJsonElement(server.requestsTo(put).last().body.readUtf8()).jsonObject
        assertThat(body["base_version"].toString()).isEqualTo("null")
    }

    /** A refusal that retrying can't fix (too big) is set aside with the server's reason, not retried forever. */
    @Test
    fun `permanent refusals are set aside`() = runTest {
        val engine = engine()
        engine.firstSync()
        engine.editNote(room, "Mole.md", "# huge")
        server.enqueue(put, error(413, "too_big"))
        server.enqueue(tree, MockResponse().setResponseCode(304))
        engine.sync(room).getOrThrow()
        val edit = engine.edits(room).single()
        assertThat(edit.state).isEqualTo(EditState.REJECTED)
        assertThat(edit.error).isEqualTo("Human text for too_big")
    }

    /** Discarding a conflicted edit goes back to the server's text. */
    @Test
    fun `discarding a conflict restores the server copy`() = runTest {
        val engine = engine()
        engine.firstSync()
        engine.editNote(room, "Súper.md", "- leche\n- huevos")
        server.enqueue(put, error(409, "conflict", """"current": {"path": "Súper.md", "version": "v2", "content": "- pan"}"""))
        server.enqueue(tree, MockResponse().setResponseCode(304))
        engine.sync(room).getOrThrow()
        engine.discardEdit(engine.edits(room).single().id)
        val file = engine.file(room, "Súper.md")
        assertThat(file?.content).isEqualTo("- pan")
        assertThat(file?.version).isEqualTo("v2")
        assertThat(file?.hasLocalEdits).isFalse()
    }

    /** A live state event with the tree we already have triggers no request at all. */
    @Test
    fun `same tree from the state event does nothing`() = runTest {
        val engine = engine()
        engine.firstSync()
        server.requests.clear()
        assertThat(engine.onTreeChanged(room, "t1").getOrThrow()).isNull()
        assertThat(server.requests).isEmpty()
    }

    /** Moving needs the note's unsent edits out first, so the server moves the right version. */
    @Test
    fun `move refuses while edits are unsent`() = runTest {
        val engine = engine()
        engine.firstSync()
        engine.editNote(room, "Súper.md", "- leche\n- huevos")
        assertThat(engine.moveNote(room, "Súper.md", "Listas/Súper.md").exceptionOrNull()).isInstanceOf(NotesException.Conflict::class.java)
    }

    /** Moving renames locally and pulls the notes whose links the server rewrote. */
    @Test
    fun `move renames and pulls rewritten links`() = runTest {
        val engine = engine()
        engine.firstSync()
        server.enqueue(
            "POST /chats/$A_ROOM_ENCODED/move",
            json(
                body = """
                    {"from": "Súper.md", "to": "Compras.md", "version": "v1", "links_updated": ["Mole.md"]}
                """.trimIndent(),
            ),
        )
        server.enqueue(tree, treeResponse("t2", "Compras.md" to "v1", "Mole.md" to "m2", "luna.jpg" to "p1"))
        server.enqueue(noteKey("Mole.md"), note("Mole.md", "m2", "Ver [[Compras]]"))
        engine.moveNote(room, "Súper.md", "Compras.md").getOrThrow()
        assertThat(engine.file(room, "Compras.md")?.content).isEqualTo("- leche")
        assertThat(engine.file(room, "Súper.md")).isNull()
        assertThat(engine.file(room, "Mole.md")?.content).isEqualTo("Ver [[Compras]]")
    }
}
