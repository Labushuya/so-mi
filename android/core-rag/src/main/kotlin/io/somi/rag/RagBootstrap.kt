package io.somi.rag

import dagger.Lazy
import io.objectbox.BoxStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.14.0 M1 — lazy facade that opens the [BoxStore] on first
 * `ensureOpen()` call. Lets the app warm-up coroutine in
 * [io.somi.app.SoMiApp] force the open without leaking BoxStore
 * types upward; consumers of `:core-rag` see only this singleton
 * for now. M2/M3 hang real init off this same seam.
 *
 * Idempotent — safe to call from multiple coroutines because
 * [Lazy] is thread-safe by contract.
 *
 * **Lifecycle:** the BoxStore is opened on first ensureOpen() and
 * never closed in production. ObjectBox's own JNI takes care of
 * orderly shutdown when the process dies. Closing on Application
 * onTerminate() would be a no-op anyway — Android rarely calls it.
 */
@Singleton
class RagBootstrap @Inject constructor(
    private val boxStoreLazy: Lazy<BoxStore>,
) {
    /** Force the BoxStore open. Returns immediately on subsequent calls. */
    fun ensureOpen() {
        boxStoreLazy.get()
    }
}
