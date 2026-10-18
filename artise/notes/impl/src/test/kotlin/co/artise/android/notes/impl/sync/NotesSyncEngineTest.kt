/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package co.artise.android.notes.impl.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import co.artise.android.notes.api.EditKind
import co.artise.android.notes.api.EditState
import co.artise.android.notes.api.MediaInUseException
import co.artise.android.notes.api.NotesException
import co.artise.android.notes.api.UploadStatus
import co.artise.android.notes.impl.A_ROOM
import co.artise.android.notes.impl.A_ROOM_ENCODED
import co.artise.android.notes.impl.FakeNotesTokenSource
import co.artise.android.notes.impl.NotesMockServer
import co.artise.android.notes.impl.analytics.NotesAction
import co.artise.android.notes.impl.analytics.NotesEvent
import co.artise.android.notes.impl.attachments.PhotoConverter
import co.artise.android.notes.impl.db.NotesDatabase
import co.artise.android.notes.impl.error
import co.artise.android.notes.impl.json
import co.artise.android.notes.impl.local.NotesLocalStore
import co.artise.android.notes.impl.remote.NotesApiClient
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.services.analytics.test.FakeAnalyticsService
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okio.Buffer
import org.junit.After
import org.junit.Test
import java.nio.file.Files

class NotesSyncEngineTest {
    private val server = NotesMockServer()
    private val room = RoomId(A_ROOM)
    private val tree = "GET /chats/$A_ROOM_ENCODED/tree"
    private val put = "PUT /chats/$A_ROOM_ENCODED/note"

    // Whether the phone is on Wi-Fi, for downloading photos ahead of time.
    private var onWifi = false
    private val analytics = FakeAnalyticsService()

    // Stands in for Android's decoder: "converts" HEIC by returning fixed JPEG bytes, leaves everything else alone.
    private val photoConverter = PhotoConverter { _, type -> if (type == "image/heic") byteArrayOf(7, 7) else null }

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
            backgroundScope = backgroundScope,
            attachmentsDir = Files.createTempDirectory("attachments").toFile(),
            pendingDir = Files.createTempDirectory("pending").toFile(),
            photoConverter = photoConverter,
            prefetchPolicy = { onWifi },
            analyticsService = analytics,
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

    /**
     * Discarding a refused edit to a note the server has goes back to the server's copy on the next sync,
     * instead of losing the note from the phone.
     */
    @Test
    fun `discarding a refused edit downloads the note again`() = runTest {
        val engine = engine()
        engine.firstSync()
        engine.editNote(room, "Mole.md", "# huge")
        server.enqueue(put, error(413, "too_big"))
        server.enqueue(tree, MockResponse().setResponseCode(304))
        engine.sync(room).getOrThrow()
        engine.discardEdit(engine.edits(room).single().id)

        server.enqueue(tree, treeResponse("t1", "Súper.md" to "v1", "Mole.md" to "m1", "luna.jpg" to "p1"))
        server.enqueue(noteKey("Mole.md"), note("Mole.md", "m1", "# Mole"))
        engine.sync(room).getOrThrow()
        assertThat(server.requestsTo(tree).last().getHeader("If-None-Match")).isNull()
        assertThat(engine.file(room, "Mole.md")?.content).isEqualTo("# Mole")
    }

    /** Local edits and syncs that change files tell open screens to refresh. */
    @Test
    fun `changes are signalled`() = runTest {
        val engine = engine()
        engine.firstSync()
        engine.changes(room).test {
            engine.editNote(room, "Súper.md", "- pan")
            awaitItem()
            engine.deleteFile(room, "Mole.md")
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private val uploadKey = "PUT /chats/$A_ROOM_ENCODED/raw?path=attachments%2Fluna.jpg"

    /** Offline, an attachment is kept on the phone and queued: it's listed and shows at once, with no request made. */
    @Test
    fun `attachments queue offline`() = runTest {
        val engine = engine()
        engine.firstSync()
        server.requests.clear()
        val path = engine.addAttachment(room, "luna.jpg", byteArrayOf(1, 2, 3), "image/jpeg").getOrThrow()
        assertThat(path).isEqualTo("attachments/luna.jpg")
        assertThat(server.requests).isEmpty()
        assertThat(engine.file(room, path)?.isNote).isFalse()
        assertThat(engine.attachment(room, path).getOrThrow().readBytes()).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(engine.edits(room).single().kind).isEqualTo(EditKind.UPLOAD)
    }

    /** On sync the photo uploads before the note that embeds it, then shows from the phone without downloading. */
    @Test
    fun `uploads go before the note that embeds them`() = runTest {
        val engine = engine()
        engine.firstSync()
        val path = engine.addAttachment(room, "luna.jpg", byteArrayOf(1, 2, 3), "image/jpeg").getOrThrow()
        engine.editNote(room, "Mole.md", "# Mole\n![[luna.jpg]]")
        server.requests.clear()
        server.enqueue(uploadKey, json(201, """{"path": "attachments/luna.jpg", "version": "a1"}"""))
        server.enqueue(put, json(body = """{"path": "Mole.md", "version": "m2", "merged": false}"""))
        server.enqueue(tree, MockResponse().setResponseCode(304))
        engine.sync(room).getOrThrow()
        assertThat(server.requests.map { it.method + " " + it.path.orEmpty().substringBefore('?') }.take(2))
            .containsExactly("PUT /api/notes/v1/chats/$A_ROOM_ENCODED/raw", "PUT /api/notes/v1/chats/$A_ROOM_ENCODED/note")
            .inOrder()
        assertThat(server.requestsTo(uploadKey).single().body.readByteArray()).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(engine.edits(room)).isEmpty()
        assertThat(engine.file(room, path)?.version).isEqualTo("a1")
        server.requests.clear()
        assertThat(engine.attachment(room, path).getOrThrow().readBytes()).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(server.requests).isEmpty()
    }

    /** Someone uploaded the same name meanwhile: the photo takes the next free name and the unsent note follows it. */
    @Test
    fun `upload name clash renames and fixes the note`() = runTest {
        val engine = engine()
        engine.firstSync()
        engine.addAttachment(room, "luna.jpg", byteArrayOf(1), "image/jpeg").getOrThrow()
        engine.editNote(room, "Mole.md", "![[luna.jpg]]")
        server.enqueue(uploadKey, error(409, "exists", """"current": {"path": "attachments/luna.jpg", "version": "x"}"""))
        server.enqueue(
            "PUT /chats/$A_ROOM_ENCODED/raw?path=attachments%2Fluna%20%282%29.jpg",
            json(201, """{"path": "attachments/luna (2).jpg", "version": "a2"}"""),
        )
        server.enqueue(put, json(body = """{"path": "Mole.md", "version": "m2", "merged": false}"""))
        server.enqueue(tree, MockResponse().setResponseCode(304))
        engine.sync(room).getOrThrow()
        val sentNote = Json.parseToJsonElement(server.requestsTo(put).single().body.readUtf8()).jsonObject
        assertThat(sentNote["content"]?.jsonPrimitive?.content).isEqualTo("![[luna (2).jpg]]")
        assertThat(engine.file(room, "attachments/luna (2).jpg")?.version).isEqualTo("a2")
    }

    /**
     * A server-side error on one item (here a proxy's plain-text 413) doesn't hold up the rest: the note is still sent,
     * updates are still pulled, and the upload stays queued and goes through on a later sync.
     */
    @Test
    fun `a failing upload does not block the queue`() = runTest {
        val engine = engine()
        engine.firstSync()
        engine.addAttachment(room, "luna.jpg", byteArrayOf(1), "image/jpeg").getOrThrow()
        engine.editNote(room, "Súper.md", "- pan")
        server.enqueue(uploadKey, MockResponse().setResponseCode(413).setBody("Request Entity Too Large"))
        server.enqueue(put, json(body = """{"path": "Súper.md", "version": "v2", "merged": false}"""))
        server.enqueue(tree, treeResponse("t2", "Súper.md" to "v2", "Mole.md" to "m1", "luna.jpg" to "p1"))
        val report = engine.sync(room).getOrThrow()
        assertThat(report.failed).isEqualTo(1)
        assertThat(report.sent).isEqualTo(1)
        assertThat(server.requestsTo(tree)).isNotEmpty()
        val waiting = engine.edits(room).single()
        assertThat(waiting.kind).isEqualTo(EditKind.UPLOAD)
        assertThat(waiting.state).isEqualTo(EditState.PENDING)

        server.enqueue(uploadKey, json(201, """{"path": "attachments/luna.jpg", "version": "a1"}"""))
        server.enqueue(tree, MockResponse().setResponseCode(304))
        assertThat(engine.sync(room).getOrThrow().failed).isEqualTo(0)
        assertThat(engine.edits(room)).isEmpty()
    }

    /**
     * A HEIC photo queued before photos were converted on attach is converted before it uploads, as a .jpg, and a note
     * already sent that embeds it gets an edit pointing at the new name.
     */
    @Test
    fun `queued heic photos are converted and notes follow`() = runTest {
        val engine = engine()
        engine.firstSync()
        engine.addAttachment(room, "luna.heic", byteArrayOf(1, 2), "image/heic").getOrThrow()
        engine.editNote(room, "Mole.md", "![[luna.heic]]")
        server.enqueue(put, json(body = """{"path": "Mole.md", "version": "m2", "merged": false}"""))
        server.enqueue(
            "PUT /chats/$A_ROOM_ENCODED/raw?path=attachments%2Fluna.jpg",
            json(201, """{"path": "attachments/luna.jpg", "version": "a1"}"""),
        )
        server.enqueue(put, json(body = """{"path": "Mole.md", "version": "m3", "merged": false}"""))
        server.enqueue(tree, MockResponse().setResponseCode(304))
        engine.sync(room).getOrThrow()
        server.enqueue(tree, MockResponse().setResponseCode(304))
        engine.sync(room).getOrThrow()
        val upload = server.requestsTo("PUT /chats/$A_ROOM_ENCODED/raw?path=attachments%2Fluna.jpg").single()
        assertThat(upload.getHeader("Content-Type")).startsWith("image/jpeg")
        assertThat(upload.body.readByteArray()).isEqualTo(byteArrayOf(7, 7))
        assertThat(engine.file(room, "Mole.md")?.content).isEqualTo("![[luna.jpg]]")
        val lastNote = Json.parseToJsonElement(server.requestsTo(put).last().body.readUtf8()).jsonObject
        assertThat(lastNote["content"]?.jsonPrimitive?.content).isEqualTo("![[luna.jpg]]")
        assertThat(engine.edits(room)).isEmpty()
    }

    /** An upload the server refuses (too big) waits for a choice; discarding it removes the attachment. */
    @Test
    fun `refused uploads can be discarded`() = runTest {
        val engine = engine()
        engine.firstSync()
        engine.addAttachment(room, "luna.jpg", byteArrayOf(1), "image/jpeg").getOrThrow()
        server.enqueue(uploadKey, error(413, "too_big"))
        server.enqueue(tree, MockResponse().setResponseCode(304))
        engine.sync(room).getOrThrow()
        val refused = engine.edits(room).single()
        assertThat(refused.state).isEqualTo(EditState.REJECTED)
        engine.discardEdit(refused.id)
        assertThat(engine.edits(room)).isEmpty()
        assertThat(engine.file(room, "attachments/luna.jpg")).isNull()
    }

    /**
     * Each photo not on the server yet says where it is: waiting, then retrying after a server-side failure, then
     * refused for good; Retry puts a refused one back in the queue and tries at once.
     */
    @Test
    fun `uploads say where each photo is`() = runTest {
        val engine = engine()
        engine.firstSync()
        engine.addAttachment(room, "luna.jpg", byteArrayOf(1), "image/jpeg").getOrThrow()
        assertThat(engine.uploads(room)).containsExactly("attachments/luna.jpg", UploadStatus.WAITING)

        server.enqueue(uploadKey, MockResponse().setResponseCode(413).setBody("Request Entity Too Large"))
        server.enqueue(tree, MockResponse().setResponseCode(304))
        engine.sync(room).getOrThrow()
        assertThat(engine.uploads(room)).containsExactly("attachments/luna.jpg", UploadStatus.RETRYING)

        server.enqueue(uploadKey, error(413, "too_big"))
        server.enqueue(tree, MockResponse().setResponseCode(304))
        engine.sync(room).getOrThrow()
        assertThat(engine.uploads(room)).containsExactly("attachments/luna.jpg", UploadStatus.FAILED)

        server.enqueue(uploadKey, json(201, """{"path": "attachments/luna.jpg", "version": "a1"}"""))
        server.enqueue(tree, MockResponse().setResponseCode(304))
        engine.retryUpload(room, "attachments/luna.jpg")
        runCurrent()
        assertThat(server.requestsTo(uploadKey)).hasSize(3)
        assertThat(engine.uploads(room)).isEmpty()
        assertThat(engine.file(room, "attachments/luna.jpg")?.version).isEqualTo("a1")
    }

    /** Cancelling a photo not uploaded yet removes it from the phone and from the note, and nothing is sent for it. */
    @Test
    fun `cancelled uploads leave the phone and the note`() = runTest {
        val engine = engine()
        engine.firstSync()
        engine.addAttachment(room, "luna.jpg", byteArrayOf(1), "image/jpeg").getOrThrow()
        engine.editNote(room, "Mole.md", "# Mole\n\n![[luna.jpg]]\n\nfin")
        engine.cancelUpload(room, "attachments/luna.jpg")
        assertThat(engine.uploads(room)).isEmpty()
        assertThat(engine.file(room, "attachments/luna.jpg")).isNull()
        assertThat(engine.file(room, "Mole.md")?.content).isEqualTo("# Mole\n\nfin")
        val edit = engine.edits(room).single()
        assertThat(edit.kind).isEqualTo(EditKind.SAVE)
        assertThat(edit.content).isEqualTo("# Mole\n\nfin")
        server.requests.clear()
        server.enqueue(put, json(body = """{"path": "Mole.md", "version": "m2", "merged": false}"""))
        server.enqueue(tree, MockResponse().setResponseCode(304))
        engine.sync(room).getOrThrow()
        assertThat(server.requests.none { it.path.orEmpty().contains("/raw") }).isTrue()
    }

    /** For "Help improve Artise", actions are counted with the kind of file only: never a note's name or text. */
    @Test
    fun `note actions are counted without names`() = runTest {
        val engine = engine()
        engine.firstSync()
        engine.editNote(room, "Mole.md", "secreto")
        engine.addAttachment(room, "luna.jpg", byteArrayOf(1), "image/jpeg").getOrThrow()
        engine.addAttachment(room, "factura.pdf", byteArrayOf(2), "application/pdf").getOrThrow()
        assertThat(analytics.capturedEvents).containsExactly(
            NotesEvent(NotesAction.NoteSaved),
            NotesEvent(NotesAction.AttachmentAdded, kind = "photo"),
            NotesEvent(NotesAction.AttachmentAdded, kind = "document"),
        ).inOrder()
        assertThat(analytics.capturedEvents.map { it.getName() }).containsExactly("NotesNoteSaved", "NotesAttachmentAdded", "NotesAttachmentAdded")
        assertThat(analytics.capturedEvents.flatMap { it.getProperties().orEmpty().values }).containsExactly("photo", "document")
    }

    private suspend fun NotesSyncEngine.syncWithPhoto(moleText: String) {
        server.enqueue(tree, treeResponse("t1", "Mole.md" to "m1", "luna.jpg" to "p1", "factura.pdf" to "f1"))
        server.enqueue(noteKey("Mole.md"), note("Mole.md", "m1", moleText))
        sync(room).getOrThrow()
    }

    private val lunaLinks = "GET /chats/$A_ROOM_ENCODED/links?path=luna.jpg"
    private val lunaDelete = "DELETE /chats/$A_ROOM_ENCODED/file?path=luna.jpg&base=p1"

    /** The photos and files list says which notes use each one, from the notes on the phone. */
    @Test
    fun `media lists which notes use each file`() = runTest {
        val engine = engine()
        engine.syncWithPhoto("![[luna.jpg]]")
        val media = engine.media(room).associateBy { it.path }
        assertThat(media.keys).containsExactly("luna.jpg", "factura.pdf")
        assertThat(media["luna.jpg"]?.usedBy).containsExactly("Mole.md")
        assertThat(media["factura.pdf"]?.usedBy).isEmpty()
    }

    /** An unused file is deleted only after the notes are refreshed and the server confirms no note uses it. */
    @Test
    fun `unused files are deleted after checking the server`() = runTest {
        val engine = engine()
        engine.syncWithPhoto("# Mole")
        server.enqueue(tree, MockResponse().setResponseCode(304))
        server.enqueue(lunaLinks, json(body = """{"path": "luna.jpg", "outgoing": [], "backlinks": []}"""))
        server.enqueue(lunaDelete, json(body = """{"path": "luna.jpg", "deleted": true}"""))
        server.always(tree, MockResponse().setResponseCode(304))
        engine.deleteMedia(room, "luna.jpg", removeFromNotes = false).getOrThrow()
        runCurrent()
        assertThat(server.requestsTo(lunaDelete)).hasSize(1)
        assertThat(engine.file(room, "luna.jpg")).isNull()
        assertThat(engine.edits(room)).isEmpty()
    }

    /** A file another app's note uses (the server knows) isn't deleted: the person is told which notes use it. */
    @Test
    fun `files used elsewhere are kept`() = runTest {
        val engine = engine()
        engine.syncWithPhoto("# Mole")
        server.enqueue(tree, MockResponse().setResponseCode(304))
        server.enqueue(lunaLinks, json(body = """{"path": "luna.jpg", "backlinks": [{"path": "Viaje.md", "line": "![[luna.jpg]]"}]}"""))
        val error = engine.deleteMedia(room, "luna.jpg", removeFromNotes = false).exceptionOrNull()
        assertThat((error as MediaInUseException).usedBy).containsExactly("Viaje.md")
        assertThat(engine.file(room, "luna.jpg")).isNotNull()
        assertThat(server.requestsTo(lunaDelete)).isEmpty()
    }

    /** "Remove from notes and delete": the notes lose the photo first, then the file is deleted. */
    @Test
    fun `files are taken out of notes before deleting`() = runTest {
        val engine = engine()
        engine.syncWithPhoto("Hoy\n\n![[luna.jpg]]\n\nfin")
        server.enqueue(tree, MockResponse().setResponseCode(304))
        server.enqueue(lunaLinks, json(body = """{"path": "luna.jpg", "backlinks": [{"path": "Mole.md", "line": "![[luna.jpg]]"}]}"""))
        server.enqueue(put, json(body = """{"path": "Mole.md", "version": "m2", "merged": false}"""))
        server.enqueue(lunaDelete, json(body = """{"path": "luna.jpg", "deleted": true}"""))
        server.always(tree, MockResponse().setResponseCode(304))
        engine.deleteMedia(room, "luna.jpg", removeFromNotes = true).getOrThrow()
        runCurrent()
        assertThat(engine.file(room, "Mole.md")?.content).isEqualTo("Hoy\n\nfin")
        val writes = server.requests.map { it.method + " " + it.path.orEmpty().substringBefore('?') }.filter { !it.startsWith("GET") }
        assertThat(writes).containsExactly("PUT /api/notes/v1/chats/$A_ROOM_ENCODED/note", "DELETE /api/notes/v1/chats/$A_ROOM_ENCODED/file").inOrder()
    }

    /**
     * If a note elsewhere starts using the file between the check and the delete, the server keeps it (`in_use`): the
     * delete waits for a choice, with the server's reason, and dropping it brings the file back on the next sync.
     */
    @Test
    fun `the server can keep a file notes use`() = runTest {
        val engine = engine()
        engine.syncWithPhoto("# Mole")
        server.enqueue(tree, MockResponse().setResponseCode(304))
        server.enqueue(lunaLinks, json(body = """{"path": "luna.jpg", "backlinks": []}"""))
        server.enqueue(lunaDelete, error(409, "in_use", """"used_by": ["Viaje.md"]"""))
        server.always(tree, MockResponse().setResponseCode(304))
        engine.deleteMedia(room, "luna.jpg", removeFromNotes = false).getOrThrow()
        runCurrent()
        val refused = engine.edits(room).single()
        assertThat(refused.kind).isEqualTo(EditKind.DELETE)
        assertThat(refused.state).isEqualTo(EditState.REJECTED)

        engine.discardEdit(refused.id)
        server.enqueue(tree, treeResponse("t1", "Mole.md" to "m1", "luna.jpg" to "p1", "factura.pdf" to "f1"))
        engine.sync(room).getOrThrow()
        assertThat(engine.file(room, "luna.jpg")).isNotNull()
    }

    /** On Wi-Fi, photos embedded in notes download after a sync, so they show offline; documents wait to be opened. */
    @Test
    fun `photos are downloaded ahead of time on wifi`() = runTest {
        onWifi = true
        val engine = engine()
        server.enqueue(tree, treeResponse("t1", "Mole.md" to "m1", "luna.jpg" to "p1", "factura.pdf" to "f1"))
        server.enqueue(noteKey("Mole.md"), note("Mole.md", "m1", "![[luna.jpg]]\\n\\n![[factura.pdf]]"))
        server.always("GET /chats/$A_ROOM_ENCODED/raw?path=luna.jpg", MockResponse().setBody(Buffer().write(byteArrayOf(5))))
        engine.sync(room).getOrThrow()
        // advanceUntilIdle() stops once the test itself is done; runCurrent() also runs the background download.
        runCurrent()
        assertThat(server.requestsTo("GET /chats/$A_ROOM_ENCODED/raw?path=luna.jpg")).hasSize(1)
        assertThat(server.requestsTo("GET /chats/$A_ROOM_ENCODED/raw?path=factura.pdf")).isEmpty()
        // Already on the phone: showing it costs nothing.
        assertThat(engine.attachment(room, "luna.jpg").getOrThrow().readBytes()).isEqualTo(byteArrayOf(5))
        assertThat(server.requestsTo("GET /chats/$A_ROOM_ENCODED/raw?path=luna.jpg")).hasSize(1)
    }

    /** On mobile data nothing downloads ahead of time: families' prepaid data isn't spent on photos nobody opened. */
    @Test
    fun `nothing downloads ahead of time on mobile data`() = runTest {
        onWifi = false
        val engine = engine()
        server.enqueue(tree, treeResponse("t1", "Mole.md" to "m1", "luna.jpg" to "p1"))
        server.enqueue(noteKey("Mole.md"), note("Mole.md", "m1", "![[luna.jpg]]"))
        engine.sync(room).getOrThrow()
        runCurrent()
        assertThat(server.requests.none { it.path.orEmpty().contains("/raw") }).isTrue()
    }

    /** An attachment is downloaded once; opening it again uses the copy on the phone. */
    @Test
    fun `attachments are downloaded once`() = runTest {
        val engine = engine()
        engine.firstSync()
        server.enqueue("GET /chats/$A_ROOM_ENCODED/raw?path=luna.jpg", MockResponse().setBody(Buffer().write(byteArrayOf(9, 9))))
        assertThat(engine.attachment(room, "luna.jpg").getOrThrow().readBytes()).isEqualTo(byteArrayOf(9, 9))
        assertThat(engine.attachment(room, "luna.jpg").getOrThrow().readBytes()).isEqualTo(byteArrayOf(9, 9))
        assertThat(server.requestsTo("GET /chats/$A_ROOM_ENCODED/raw?path=luna.jpg")).hasSize(1)
    }

    /** Attachment names: taken ones get a number, folders and hidden names are cleaned. */
    @Test
    fun `free attachment names`() {
        assertThat(NotesSyncEngine.freeAttachmentPath("foto.jpg", setOf("attachments/foto.jpg"))).isEqualTo("attachments/foto (2).jpg")
        assertThat(NotesSyncEngine.freeAttachmentPath("../.secreto", emptySet())).isEqualTo("attachments/secreto")
        assertThat(NotesSyncEngine.freeAttachmentPath("  ", emptySet())).isEqualTo("attachments/archivo")
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

    /** The tree's folders are kept, so empty ones show; folders holding files count too. */
    @Test
    fun `tree folders are kept`() = runTest {
        val engine = engine()
        server.enqueue(
            tree,
            json(
                body = """{"tree": "t1", "files": [], "folders": ["Viajes", "Viajes/Oaxaca"]}""",
                headers = mapOf("ETag" to "\"t1\""),
            ),
        )
        engine.sync(room).getOrThrow()
        assertThat(engine.folders(room)).containsExactly("Viajes", "Viajes/Oaxaca").inOrder()
        server.enqueue(tree, json(body = """{"tree": "t2", "files": [], "folders": ["Viajes"]}""", headers = mapOf("ETag" to "\"t2\"")))
        engine.sync(room).getOrThrow()
        assertThat(engine.folders(room)).containsExactly("Viajes")
    }

    /** A new folder and its parents show at once, before the next sync. */
    @Test
    fun `create a folder`() = runTest {
        val engine = engine()
        engine.firstSync()
        server.enqueue("POST /chats/$A_ROOM_ENCODED/folder", json(201, """{"path": "Recetas/Postres"}"""))
        engine.createFolder(room, "Recetas/Postres").getOrThrow()
        assertThat(server.requests.last().body.readUtf8()).contains("Recetas/Postres")
        assertThat(engine.folders(room)).containsExactly("Recetas", "Recetas/Postres").inOrder()
        server.enqueue("POST /chats/$A_ROOM_ENCODED/folder", error(409, "exists"))
        assertThat(engine.createFolder(room, "Recetas").exceptionOrNull()).isInstanceOf(NotesException.Exists::class.java)
    }

    /** Moving a folder renames everything inside on the phone, keeps it readable, and refuses with unsent edits. */
    @Test
    fun `move a folder`() = runTest {
        val engine = engine()
        server.enqueue(tree, treeResponse("t1", "Recetas/Mole.md" to "m1", "Recetas/Postres/flan.jpg" to "p1"))
        server.enqueue(noteKey("Recetas%2FMole.md"), note("Recetas/Mole.md", "m1", "# Mole"))
        engine.sync(room).getOrThrow()
        server.enqueue(
            "POST /chats/$A_ROOM_ENCODED/move",
            json(
                body = """
                    {"from": "Recetas", "to": "Cocina/Recetas", "links_updated": [],
                     "moved": [{"from": "Recetas/Mole.md", "to": "Cocina/Recetas/Mole.md", "version": "m2"},
                               {"from": "Recetas/Postres/flan.jpg", "to": "Cocina/Recetas/Postres/flan.jpg", "version": "p2"}]}
                """.trimIndent(),
            ),
        )
        engine.moveFolder(room, "Recetas", "Cocina/Recetas").getOrThrow()
        assertThat(server.requests.last().body.readUtf8()).contains("\"folder\":true")
        assertThat(engine.file(room, "Cocina/Recetas/Mole.md")?.content).isEqualTo("# Mole")
        assertThat(engine.file(room, "Cocina/Recetas/Mole.md")?.version).isEqualTo("m2")
        assertThat(engine.file(room, "Recetas/Mole.md")).isNull()
        assertThat(engine.folders(room)).containsExactly("Cocina", "Cocina/Recetas", "Cocina/Recetas/Postres").inOrder()
        engine.editNote(room, "Cocina/Recetas/Mole.md", "# Mole negro")
        assertThat(engine.moveFolder(room, "Cocina", "Comida").exceptionOrNull()).isInstanceOf(NotesException.Conflict::class.java)
    }

    /** A folder that isn't empty is refused with the server's counts; with recursive, it goes with what's inside. */
    @Test
    fun `delete a folder`() = runTest {
        val engine = engine()
        server.enqueue(tree, treeResponse("t1", "Viajes/Oaxaca.md" to "o1", "Viajes/mapa.pdf" to "p1"))
        server.enqueue(noteKey("Viajes%2FOaxaca.md"), note("Viajes/Oaxaca.md", "o1", "# Oaxaca"))
        engine.sync(room).getOrThrow()
        server.enqueue("DELETE /chats/$A_ROOM_ENCODED/folder?path=Viajes", error(409, "not_empty", "\"files\": 2, \"folders\": 0"))
        val refused = engine.deleteFolder(room, "Viajes", recursive = false).exceptionOrNull() as NotesException.NotEmpty
        assertThat(refused.files).isEqualTo(2)
        assertThat(engine.files(room)).hasSize(2)
        server.enqueue(
            "DELETE /chats/$A_ROOM_ENCODED/folder?path=Viajes&recursive=true",
            json(body = """{"path": "Viajes", "deleted": true, "notes": 1, "files": 1}"""),
        )
        assertThat(engine.deleteFolder(room, "Viajes", recursive = true).getOrThrow().notes).isEqualTo(1)
        assertThat(engine.files(room)).isEmpty()
        assertThat(engine.folders(room)).isEmpty()
    }
}
