/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.remote

import co.artise.android.notes.api.Backlink
import co.artise.android.notes.api.MovedNote
import co.artise.android.notes.api.Note
import co.artise.android.notes.api.NoteLinks
import co.artise.android.notes.api.NoteVersion
import co.artise.android.notes.api.NotesChat
import co.artise.android.notes.api.NotesException
import co.artise.android.notes.api.NotesFile
import co.artise.android.notes.api.NotesGraph
import co.artise.android.notes.api.NotesSearchResult
import co.artise.android.notes.api.NotesTree
import co.artise.android.notes.api.OutgoingLink
import co.artise.android.notes.api.SavedNote
import co.artise.android.notes.api.ServerCopy
import co.artise.android.notes.impl.auth.NotesTokenSource
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder

/** Where the Notes API lives. */
const val NOTES_API_BASE_URL = "https://chat.artise.co/api/notes/v1"

/** The answer to a conditional tree request. */
sealed interface TreeResponse {
    /** `304`: nothing changed since the ETag we sent. */
    data object NotModified : TreeResponse

    data class Changed(val tree: NotesTree, val etag: String?) : TreeResponse
}

/**
 * The Notes API v1, one function per endpoint (docs/notes-api.md in family-wiki).
 *
 * Every call signs in with an OpenID token from [tokenSource]. On a `401` it asks for a fresh token and retries once.
 * Failures come back as [NotesException] inside the [Result]; tokens and note text are never logged.
 */
class NotesApiClient(
    private val okHttpClient: OkHttpClient,
    private val tokenSource: NotesTokenSource,
    private val dispatchers: CoroutineDispatchers,
    baseUrl: String = NOTES_API_BASE_URL,
) {
    private val baseUrl: HttpUrl = baseUrl.trimEnd('/').toHttpUrl()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun chats(): Result<List<NotesChat>> = call(get(url("chats"))) { body ->
        json.decodeFromString<ChatsDto>(body).chats.map { NotesChat(RoomId(it.id), it.name, it.tree) }
    }

    /** The chat's file list; with [etag] set, [TreeResponse.NotModified] when nothing changed. */
    suspend fun tree(roomId: RoomId, etag: String?): Result<TreeResponse> {
        val request = get(url(roomId, "tree")).newBuilder()
            .apply { if (etag != null) header("If-None-Match", etag) }
            .build()
        return send(request) { response ->
            if (response.code == HTTP_NOT_MODIFIED) {
                TreeResponse.NotModified
            } else {
                val dto = json.decodeFromString<TreeDto>(response.bodyString())
                TreeResponse.Changed(
                    tree = NotesTree(dto.tree, dto.files.map { NotesFile(it.path, it.version, it.size, it.modified, it.note) }),
                    etag = response.header("ETag"),
                )
            }
        }
    }

    /** A note's text, now or, with [atCommit], as it was at that commit from [history]. */
    suspend fun note(roomId: RoomId, path: String, atCommit: String? = null): Result<Note> {
        val url = url(roomId, "note").newBuilder()
            .addQueryParameter("path", path)
            .apply { if (atCommit != null) addQueryParameter("at", atCommit) }
            .build()
        return call(get(url)) { body -> json.decodeFromString<NoteDto>(body).let { Note(it.path, it.version, it.content) } }
    }

    /**
     * Saves a note. [baseVersion] is the version the edit started from, or `null` to create the note.
     * The server merges edits to different lines; see [SavedNote.merged].
     */
    suspend fun saveNote(roomId: RoomId, path: String, content: String, baseVersion: String?): Result<SavedNote> {
        val body = json.encodeToString(SaveNoteDto(path, content, baseVersion)).toRequestBody(jsonMediaType)
        return call(request(url(roomId, "note")).put(body).build()) { response ->
            json.decodeFromString<SavedDto>(response).let { SavedNote(it.path, it.version, it.merged, it.content) }
        }
    }

    /** A raw file's bytes (photos, PDFs...). */
    suspend fun raw(roomId: RoomId, path: String): Result<ByteArray> {
        val url = url(roomId, "raw").newBuilder().addQueryParameter("path", path).build()
        return send(get(url)) { it.body.bytes() }
    }

    /** Uploads a raw file; [baseVersion] `null` creates it. Returns the new version. */
    suspend fun saveRaw(roomId: RoomId, path: String, bytes: ByteArray, contentType: String, baseVersion: String?): Result<String> {
        val url = url(roomId, "raw").newBuilder()
            .addQueryParameter("path", path)
            .apply { if (baseVersion != null) addQueryParameter("base", baseVersion) }
            .build()
        return call(request(url).put(bytes.toRequestBody(contentType.toMediaType())).build()) { body ->
            json.decodeFromString<SavedDto>(body).version
        }
    }

    suspend fun delete(roomId: RoomId, path: String, baseVersion: String): Result<Unit> {
        val url = url(roomId, "file").newBuilder()
            .addQueryParameter("path", path)
            .addQueryParameter("base", baseVersion)
            .build()
        return call(request(url).delete().build()) { }
    }

    suspend fun move(roomId: RoomId, from: String, to: String, baseVersion: String): Result<MovedNote> {
        val body = json.encodeToString(MoveDto(from, to, baseVersion)).toRequestBody(jsonMediaType)
        return call(request(url(roomId, "move")).post(body).build()) { response ->
            json.decodeFromString<MovedDto>(response).let { MovedNote(it.from, it.to, it.version, it.linksUpdated) }
        }
    }

    suspend fun search(roomId: RoomId, query: String): Result<List<NotesSearchResult>> {
        val url = url(roomId, "search").newBuilder().addQueryParameter("q", query).build()
        return call(get(url)) { body -> json.decodeFromString<SearchDto>(body).results.map { NotesSearchResult(it.path, it.lines) } }
    }

    suspend fun links(roomId: RoomId, path: String): Result<NoteLinks> {
        val url = url(roomId, "links").newBuilder().addQueryParameter("path", path).build()
        return call(get(url)) { body ->
            val dto = json.decodeFromString<LinksDto>(body)
            NoteLinks(
                path = dto.path,
                outgoing = dto.outgoing.map { OutgoingLink(it.target, it.path) },
                backlinks = dto.backlinks.map { Backlink(it.path, it.line) },
            )
        }
    }

    suspend fun graph(roomId: RoomId): Result<NotesGraph> = call(get(url(roomId, "graph"))) { body ->
        val dto = json.decodeFromString<GraphDto>(body)
        NotesGraph(dto.nodes, dto.edges.filter { it.size == 2 }.map { it[0] to it[1] })
    }

    suspend fun history(roomId: RoomId, path: String): Result<List<NoteVersion>> {
        val url = url(roomId, "history").newBuilder().addQueryParameter("path", path).build()
        return call(get(url)) { body -> json.decodeFromString<HistoryDto>(body).versions.map { NoteVersion(it.commit, it.at, it.message, it.by) } }
    }

    private fun url(vararg segments: String): HttpUrl = baseUrl.newBuilder().apply { segments.forEach { addPathSegment(it) } }.build()

    // The contract wants room ids fully percent-encoded in the path: `!abc:artise.co` → `%21abc%3Aartise.co`.
    private fun url(roomId: RoomId, endpoint: String): HttpUrl = baseUrl.newBuilder()
        .addPathSegment("chats")
        .addEncodedPathSegment(URLEncoder.encode(roomId.value, Charsets.UTF_8.name()))
        .addPathSegment(endpoint)
        .build()

    private fun request(url: HttpUrl) = Request.Builder().url(url)

    private fun get(url: HttpUrl) = request(url).get().build()

    /** [send] for JSON endpoints: hands the body text to [parse]. */
    private suspend fun <T> call(request: Request, parse: (String) -> T): Result<T> = send(request) { parse(it.bodyString()) }

    /** Signs [request] in, retries once with a fresh token on `401`, and maps failures to [NotesException]. */
    private suspend fun <T> send(request: Request, read: (Response) -> T): Result<T> = withContext(dispatchers.io) {
        val firstToken = tokenSource.token().getOrElse { return@withContext Result.failure(noToken(it)) }
        execute(request, firstToken, read, retryOnUnauthorized = true) ?: run {
            tokenSource.invalidate(firstToken)
            val secondToken = tokenSource.token().getOrElse { return@withContext Result.failure(noToken(it)) }
            // With retryOnUnauthorized = false a 401 becomes a failure, so this is never null.
            execute(request, secondToken, read, retryOnUnauthorized = false) ?: Result.failure(NotesException.Unauthorized("Signed out"))
        }
    }

    /** One attempt. `null` means a `401` worth retrying with a new token. */
    private fun <T> execute(request: Request, token: String, read: (Response) -> T, retryOnUnauthorized: Boolean): Result<T>? {
        val signed = request.newBuilder().header("Authorization", "Bearer $token").build()
        return try {
            okHttpClient.newCall(signed).execute().use { response ->
                when {
                    response.code == HTTP_UNAUTHORIZED && retryOnUnauthorized -> null
                    response.isSuccessful || response.code == HTTP_NOT_MODIFIED -> Result.success(read(response))
                    else -> Result.failure(errorFrom(response))
                }
            }
        } catch (e: IOException) {
            Result.failure(NotesException.Network(e))
        } catch (e: SerializationException) {
            Result.failure(unexpectedAnswer(e))
        } catch (e: IllegalArgumentException) {
            // kotlinx.serialization reports a missing required field as an IllegalArgumentException.
            Result.failure(unexpectedAnswer(e))
        }
    }

    private fun noToken(cause: Throwable) = NotesException.Unauthorized(cause.message ?: "No OpenID token")

    private fun unexpectedAnswer(cause: Throwable) = NotesException.Server(0, "Unexpected answer from the notes server: ${cause.message}")

    private fun errorFrom(response: Response): NotesException {
        val dto = tryOrNull { json.decodeFromString<ErrorDto>(response.bodyString()) }
        val message = dto?.message ?: "HTTP ${response.code}"
        val current = dto?.current?.let { ServerCopy(it.path, it.version, it.content) }
        return when (dto?.error) {
            "unauthorized" -> NotesException.Unauthorized(message)
            "no_chat" -> NotesException.NoChat(message)
            "bad_path" -> NotesException.BadPath(message)
            "not_found" -> NotesException.NotFound(message)
            "exists" -> NotesException.Exists(message, current)
            "conflict" -> NotesException.Conflict(message, current)
            "deleted" -> NotesException.Deleted(message)
            "too_big" -> NotesException.TooBig(message)
            "not_a_note" -> NotesException.NotANote(message)
            "in_use" -> NotesException.InUse(message, dto.usedBy)
            else -> if (response.code == HTTP_UNAUTHORIZED) NotesException.Unauthorized(message) else NotesException.Server(response.code, message)
        }
    }

    private fun Response.bodyString(): String = body.string()

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_MODIFIED = 304
    }
}
