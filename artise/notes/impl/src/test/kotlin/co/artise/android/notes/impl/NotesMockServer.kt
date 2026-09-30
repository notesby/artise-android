/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl

import co.artise.android.notes.impl.auth.NotesTokenSource
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.CopyOnWriteArrayList

const val A_ROOM = "!abc:artise.co"
const val A_ROOM_ENCODED = "%21abc%3Aartise.co"

/** Token source handing out "token-1", "token-2"... and recording which ones were refused. */
class FakeNotesTokenSource : NotesTokenSource {
    var issued = 0
    val invalidated = mutableListOf<String>()
    var failure: Throwable? = null

    override suspend fun token(): Result<String> = failure?.let { Result.failure(it) } ?: Result.success("token-${++issued}")

    override suspend fun invalidate(rejected: String) {
        invalidated += rejected
    }
}

/**
 * A MockWebServer answering by "METHOD path" (path without the API prefix, query included), so tests read like the contract.
 * Unregistered requests get a 500 so a test fails loudly instead of hanging.
 */
class NotesMockServer {
    val server = MockWebServer()
    val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val routes = mutableMapOf<String, ArrayDeque<MockResponse>>()
    private val sticky = mutableMapOf<String, MockResponse>()

    val baseUrl: String get() = server.url("/api/notes/v1").toString()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val key = "${request.method} ${request.path.orEmpty().removePrefix("/api/notes/v1")}"
                val queued = synchronized(routes) { routes[key]?.removeFirstOrNull() }
                return queued ?: sticky[key] ?: MockResponse().setResponseCode(500).setBody("""{"error": "unrouted", "message": "$key"}""")
            }
        }
    }

    /** Answers the next [key] request with [response], once. */
    fun enqueue(key: String, response: MockResponse) = synchronized(routes) { routes.getOrPut(key) { ArrayDeque() }.addLast(response) }

    /** Answers every [key] request with [response] when nothing is queued for it. */
    fun always(key: String, response: MockResponse) {
        sticky[key] = response
    }

    fun requestsTo(key: String) = requests.filter { "${it.method} ${it.path.orEmpty().removePrefix("/api/notes/v1")}" == key }

    fun shutdown() = server.shutdown()
}

fun json(status: Int = 200, body: String, headers: Map<String, String> = emptyMap()): MockResponse =
    MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body.trimIndent()).apply {
        headers.forEach { (name, value) -> setHeader(name, value) }
    }

fun error(status: Int, code: String, extra: String = ""): MockResponse =
    json(status, """{"error": "$code", "message": "Human text for $code"${if (extra.isEmpty()) "" else ", $extra"}}""")
