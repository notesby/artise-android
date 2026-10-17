/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.artiseid

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Serves Artise ID and api.artise.co at their real https addresses: [okHttpClient] sends every request to a local server instead, so the
 * code under test keeps its https-only checks. Each route answers with its responses in turn, the last one repeating.
 */
class ArtiseIdTestServer : Closeable {
    private val server = MockWebServer()
    private val routes = ConcurrentHashMap<String, MutableList<MockResponse>>()
    val requests = CopyOnWriteArrayList<Pair<String, RecordedRequest>>()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val route = "${request.method} ${request.getHeader(ORIGINAL_HOST)}${request.requestUrl?.encodedPath}"
                requests += route to request
                val responses = routes[route] ?: return MockResponse().setResponseCode(404)
                return synchronized(responses) { if (responses.size > 1) responses.removeAt(0) else responses.first() }
            }
        }
        server.start()
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val original = chain.request()
            val local = original.url.newBuilder().scheme("http").host(server.hostName).port(server.port).build()
            chain.proceed(original.newBuilder().url(local).header(ORIGINAL_HOST, original.url.host).build())
        }
        .build()

    /** [route] is "METHOD host/path", e.g. "GET api.artise.co/v1/sign-in". */
    fun on(route: String, vararg responses: MockResponse) {
        routes[route] = responses.toMutableList()
    }

    fun requestsTo(route: String): List<RecordedRequest> = requests.filter { it.first == route }.map { it.second }

    /** The usual Artise ID: settings with Google and Microsoft (no Apple), discovery, and a token endpoint. */
    fun givenArtiseId(workspaces: String = WORKSPACES_ONE) {
        on(SIGN_IN, json(SIGN_IN_JSON))
        on(DISCOVERY, json(DISCOVERY_JSON))
        on(TOKEN, json("""{"access_token": "at-1", "refresh_token": "rt-1", "expires_in": 3600, "token_type": "Bearer"}"""))
        on(WORKSPACES, json(workspaces))
    }

    override fun close() = server.shutdown()

    companion object {
        private const val ORIGINAL_HOST = "X-Test-Original-Host"
        const val SIGN_IN = "GET api.artise.co/v1/sign-in"
        const val DISCOVERY = "GET id.artise.co/.well-known/openid-configuration"
        const val TOKEN = "POST id.artise.co/oauth/v2/token"
        const val WORKSPACES = "GET api.artise.co/v1/me/workspaces"
        const val REDIRECT = "https://join.artise.co/oauth"

        val SIGN_IN_JSON = """
            {"issuer": "https://id.artise.co", "client_id": "395423549856219219", "redirect_uri": "$REDIRECT",
             "scopes": ["openid", "profile", "email"], "providers": {"google": "g-idp", "microsoft": "m-idp"}}
        """.trimIndent()
        const val DISCOVERY_JSON = """{"issuer": "https://id.artise.co", "authorization_endpoint": "https://id.artise.co/oauth/v2/authorize",
            "token_endpoint": "https://id.artise.co/oauth/v2/token"}"""
        const val WORKSPACES_ONE = """{"workspaces": [{"slug": "mas-k8s", "name": "Prueba MAS", "server_name": "mas-k8s.artise.co",
            "homeserver_url": "https://matrix.mas-k8s.artise.co"}]}"""
        const val WORKSPACES_TWO = """{"workspaces": [
            {"slug": "mas-k8s", "name": "Prueba MAS", "server_name": "mas-k8s.artise.co", "homeserver_url": "https://matrix.mas-k8s.artise.co"},
            {"slug": "acme", "name": "Acme", "server_name": "acme.artise.co", "homeserver_url": "https://matrix.acme.artise.co"}]}"""
        const val WORKSPACES_NONE = """{"workspaces": []}"""

        fun json(body: String): MockResponse = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    }
}

class InMemoryArtiseIdTokenStore(var tokens: ArtiseIdTokens? = null) : ArtiseIdTokenStore {
    override suspend fun load() = tokens

    override suspend fun save(tokens: ArtiseIdTokens) {
        this.tokens = tokens
    }

    override suspend fun clear() {
        tokens = null
    }
}
