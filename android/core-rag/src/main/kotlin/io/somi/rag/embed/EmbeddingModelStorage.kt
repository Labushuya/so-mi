package io.somi.rag.embed

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.14.0 M2 — disk layout for the on-device embedding model.
 *
 *   $filesDir/models/embeddings/
 *       <modelId>/
 *           model.onnx
 *           tokenizer.json
 *           model.onnx.part         ← in-flight (sidecar)
 *
 * The `.part` suffix is the same contract as
 * [io.somi.data.ModelStorage]: anything ending in `.part` is
 * not yet verified and must not be loaded.
 *
 * filesDir, not externalFilesDir: the embedder is binary and not
 * useful for the user to inspect. Same justification as the
 * ObjectBox vector store; user-readable artifacts (the .md memory
 * mirror) live under externalFilesDir.
 */
@Singleton
class EmbeddingModelStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val rootDir: File by lazy {
        File(File(context.filesDir, "models"), "embeddings").apply { mkdirs() }
    }

    fun directoryFor(modelId: String): File =
        File(rootDir, modelId).apply { mkdirs() }

    fun finalFile(modelId: String, filename: String): File =
        File(directoryFor(modelId), filename)

    fun partFile(modelId: String, filename: String): File =
        File(directoryFor(modelId), "$filename.part")

    /** True iff every part of [model] is on disk and non-empty. */
    fun isInstalled(model: EmbeddingModel): Boolean {
        val dir = File(rootDir, model.id)
        if (!dir.exists() || !dir.isDirectory) return false
        for (part in model.parts) {
            val f = File(dir, part.filename)
            if (!f.exists() || f.length() <= 0L) {
                Log.i(TAG, "isInstalled(${model.id}) → false (missing ${part.filename})")
                return false
            }
        }
        return true
    }

    fun mainOnnxFor(model: EmbeddingModel): File? {
        if (!isInstalled(model)) return null
        return File(directoryFor(model.id), "model.onnx")
    }

    fun tokenizerJsonFor(model: EmbeddingModel): File? {
        if (!isInstalled(model)) return null
        return File(directoryFor(model.id), "tokenizer.json")
    }

    fun delete(model: EmbeddingModel): Boolean {
        val dir = File(rootDir, model.id)
        if (!dir.exists()) return true
        return dir.deleteRecursively()
    }

    private companion object {
        const val TAG = "EmbeddingModelStorage"
    }
}
