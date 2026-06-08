package io.somi.data.download

import android.util.Log
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Streams an HTTP body to a file, computing SHA-256 inline, with
 * resume-from-offset support via the Range header.
 *
 * Contract:
 *  - Writes go to [partFile]. Caller is responsible for renaming the
 *    .part to its final name only AFTER verifying [Result.sha256Hex]
 *    matches the manifest.
 *  - On a successful resume (HTTP 206), the existing prefix is re-hashed
 *    from disk so the digest reflects the entire file.
 *  - Cancellation is cooperative: the loop calls
 *    `coroutineContext.ensureActive()` between writes; cancelling the
 *    enclosing coroutine aborts mid-stream and leaves the .part intact
 *    for the next attempt to resume.
 *  - HTTP 200 to a resume request is handled by deleting the existing
 *    partial and starting over (rare, but happens when a CDN forgets
 *    the Range request).
 *  - HTTP 416 (Requested Range Not Satisfiable) means our existing
 *    partial is at-or-past the file size — caller should delete the
 *    .part and retry from byte 0.
 *
 * v0.14.0: visibility opened to `public` so :core-rag's embedding
 * download worker can reuse it instead of duplicating ~150 lines of
 * carefully-tuned resume + hash logic. Behavior unchanged.
 */
class ResumableDownloader(private val client: OkHttpClient) {

    data class Result(
        val sha256Hex: String,
        val bytesWritten: Long,
    )

    /**
     * @throws IOException on any non-cancellation failure.
     * @throws kotlinx.coroutines.CancellationException if the enclosing
     *   coroutine was cancelled mid-stream.
     */
    suspend fun download(
        url: String,
        partFile: File,
        userAgent: String,
        onProgress: suspend (downloaded: Long, total: Long) -> Unit,
    ): Result {
        partFile.parentFile?.mkdirs()
        val existing = if (partFile.exists()) partFile.length() else 0L

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .apply { if (existing > 0L) header("Range", "bytes=$existing-") }
            .build()

        client.newCall(req).execute().use { resp ->
            if (resp.code == 416) {
                // Server says "you already have it all". Force a clean
                // restart by the caller — they'll see this exception and
                // delete the .part.
                throw IOException("HTTP 416 Range Not Satisfiable; existing=$existing")
            }
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code} ${resp.message}")
            }

            val body = resp.body ?: throw IOException("Empty body")
            val contentLength = body.contentLength()    // -1 if chunked

            val digest = MessageDigest.getInstance("SHA-256")
            val appending: Boolean
            val total: Long

            when (resp.code) {
                206 -> {
                    appending = true
                    // Re-hash the prefix so the digest matches the final file.
                    partFile.source().buffer().use { src ->
                        val buf = ByteArray(BUF_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = src.read(buf)
                            if (n == -1) break
                            digest.update(buf, 0, n)
                        }
                    }
                    total = if (contentLength >= 0) existing + contentLength else -1L
                }
                200 -> {
                    // Server ignored our Range — start over.
                    Log.w(TAG, "server returned 200 to a Range request; restarting from 0")
                    appending = false
                    partFile.delete()
                    total = if (contentLength >= 0) contentLength else -1L
                }
                else -> throw IOException("Unexpected status ${resp.code}")
            }

            var downloaded = if (appending) existing else 0L

            partFile.sink(append = appending).buffer().use { out ->
                body.source().use { src ->
                    val buf = ByteArray(BUF_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = src.read(buf)
                        if (n == -1) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        downloaded += n
                        onProgress(downloaded, total)
                    }
                    out.flush()
                }
            }

            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            return Result(sha256Hex = hex, bytesWritten = downloaded)
        }
    }

    private companion object {
        const val TAG = "ResumableDownloader"
        const val BUF_SIZE = 64 * 1024
    }
}
