/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.artiseid

import android.app.Activity
import android.content.Context
import android.os.Build
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.cryptography.impl.AESEncryptionDecryptionService
import io.element.android.libraries.cryptography.test.SimpleSecretKeyRepository
import io.element.android.libraries.sessionstorage.test.observer.FakeSessionObserver
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class EncryptedArtiseIdTokenStoreTest {
    private val context = RuntimeEnvironment.getApplication()
    private val tokens = ArtiseIdTokens("at-1", "rt-1", 1_000L)

    /** The tokens come back after a restart, and aren't stored in the clear. */
    @Test
    fun `tokens are kept encrypted`() = runTest {
        val keys = SimpleSecretKeyRepository()
        val store = EncryptedArtiseIdTokenStore(context, keys, AESEncryptionDecryptionService(), testCoroutineDispatchers())
        store.save(tokens)
        val again = EncryptedArtiseIdTokenStore(context, keys, AESEncryptionDecryptionService(), testCoroutineDispatchers())
        assertThat(again.load()).isEqualTo(tokens)
        val raw = context.getSharedPreferences("artise_id", Context.MODE_PRIVATE).all.values.joinToString()
        assertThat(raw).doesNotContain("at-1")
        assertThat(raw).doesNotContain("rt-1")
        again.clear()
        assertThat(again.load()).isNull()
    }

    /** Without the key (a restore to a new phone), the saved tokens count as signed out. */
    @Test
    fun `tokens without their key are forgotten`() = runTest {
        EncryptedArtiseIdTokenStore(context, SimpleSecretKeyRepository(), AESEncryptionDecryptionService(), testCoroutineDispatchers()).save(tokens)
        val otherKey = EncryptedArtiseIdTokenStore(context, SimpleSecretKeyRepository(), AESEncryptionDecryptionService(), testCoroutineDispatchers())
        assertThat(otherKey.load()).isNull()
        assertThat(context.getSharedPreferences("artise_id", Context.MODE_PRIVATE).all).isEmpty()
    }

    /** Signing out of the last workspace forgets Artise ID's tokens; signing out of one of several keeps them. */
    @Test
    fun `the last sign-out forgets the tokens`() = runTest {
        val store = InMemoryArtiseIdTokenStore(tokens)
        val dispatchers = testCoroutineDispatchers()
        val clock = FakeSystemClock()
        val auth = ArtiseIdAuth(ArtiseIdConfigClient(OkHttpClient(), clock, dispatchers), store, OkHttpClient(), clock, dispatchers, backgroundScope)
        val observer = FakeSessionObserver()
        ArtiseIdSessionCleanup(auth, observer).onAppStartup(Robolectric.buildActivity(Activity::class.java).get())
        observer.onSessionDeleted("@ana:acme.artise.co", wasLastSession = false)
        assertThat(store.tokens).isEqualTo(tokens)
        observer.onSessionDeleted("@ana:mas-k8s.artise.co", wasLastSession = true)
        assertThat(store.tokens).isNull()
    }
}
