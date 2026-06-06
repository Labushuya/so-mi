package io.somi.data

/**
 * Static description of a downloadable GGUF model. The catalog of these
 * lives in [ModelCatalog]; the picker UI in Phase 2.5 reads them and the
 * download manager in Phase 2.4 consumes them.
 *
 * @param id stable kebab-case identifier; used as filename stem on disk
 *   and as the unique-work key in WorkManager
 * @param tier which [Tier] this model fits into (Tiny/Small/Medium/Large)
 * @param displayName human-facing label, e.g. "Qwen2.5 7B Q4_K_M"
 * @param parts one or more shard files; single-shard models have parts.size == 1
 * @param totalSizeBytes sum of all parts' sizes; used by ModelStorage's
 *   StorageManager.allocateBytes preflight
 * @param license Apache-2.0 / qwen-research / MIT — surfaced in the picker
 *   so users can decline non-commercial licenses if they want to.
 *   See [ModelCatalog.QWEN25_3B] for the load-bearing case.
 */
data class ModelManifest(
    val id: String,
    val tier: Tier,
    val displayName: String,
    val parts: List<ModelPart>,
    val license: String,
) {
    val totalSizeBytes: Long get() = parts.sumOf { it.sizeBytes }
}

/**
 * One shard of a GGUF model.
 *
 * llama.cpp loads multi-part GGUFs by passing the path to part 1 and
 * auto-discovering siblings in the same directory by filename pattern.
 * ModelStorage MUST therefore download all parts to the same directory
 * and verify each part's [sha256] before marking the model as installed.
 *
 * @param filename exact name on the Hugging Face repo, e.g.
 *   "qwen2.5-7b-instruct-q4_k_m-00001-of-00002.gguf"
 * @param url full HTTPS download URL (HF resolve/main → CDN redirect)
 * @param sizeBytes size in bytes; preflight + Range-resume rely on it
 * @param sha256 hex (lower-case) SHA-256 of the file content. HF surfaces
 *   this as the LFS oid via the api/models/{repo}?blobs=true endpoint.
 *   Byte-identical to a `sha256sum` of the downloaded file.
 */
data class ModelPart(
    val filename: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
)
