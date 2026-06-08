package io.somi.rag.embed

/**
 * v0.14.0 M2 — declarative manifest for the Phase-3 embedding model.
 *
 * Locked to `paraphrase-multilingual-MiniLM-L12-v2` per v0.14.0
 * planning: multilingual sentence-transformer covering ~50 languages
 * including DE/EN/KO; 384-dim output; ~470 MB FP32 ONNX.
 * BertTokenizer-compatible WordPiece tokenizer (we ship a pure-Kotlin
 * tokenizer reading the HuggingFace `tokenizer.json` schema, so no
 * native tokenizer dep).
 *
 * Two artifacts are needed at runtime:
 *   - model.onnx     — the embedding network
 *   - tokenizer.json — HuggingFace tokenizers JSON spec
 *
 * Both files are downloaded once on first launch by the embedding
 * download worker and cached under `$filesDir/models/embeddings/<modelId>/`.
 * Hash-verified before promote.
 */
data class EmbeddingModel(
    val id: String,
    val displayName: String,
    val embeddingDim: Int,
    val parts: List<EmbeddingModelPart>,
    /** Sum of [parts] sizes — UI hint for the progress bar. */
    val totalSizeBytes: Long,
)

data class EmbeddingModelPart(
    val filename: String,
    /**
     * v0.15.0 — list of mirror URLs tried in order. The worker stops
     * on the first 2xx-with-correct-SHA download. HuggingFace stays
     * primary (highest availability, no GH-Releases rate limit), the
     * GitHub release on this repo is the failover when HF blocks
     * (sanctioned region, SSL pin issue, …). v0.14.x shipped a single
     * `url: String`; the migration to a list is non-breaking on the
     * binary side because the worker only reads the head of the list
     * if the first succeeds.
     */
    val urls: List<String>,
    /** Lowercase hex SHA-256, 64 chars. Verified after download, before promote. */
    val sha256: String,
    /** Soft hint for the progress bar; allowSoftSizeMismatch=true in the worker. */
    val sizeBytes: Long,
    /**
     * v0.15.0 — optional bundled-asset name. If non-null, the
     * EmbeddingModelStorage first tries to copy the file from
     * `app/src/main/assets/<assetName>` before going to the network.
     * Used for tokenizer.json so the first launch can tokenize even
     * without WLAN.
     */
    val bundledAsset: String? = null,
)

/**
 * Single-source-of-truth catalog. Currently exactly one entry —
 * adding a second model means a Settings selector and migration of
 * the ObjectBox vector dimension.
 */
object EmbeddingModelCatalog {
    val PARAPHRASE_MULTILINGUAL_MINILM_L12_V2 = EmbeddingModel(
        id = "paraphrase-multilingual-minilm-l12-v2",
        displayName = "Paraphrase Multilingual MiniLM L12 v2",
        embeddingDim = 384,
        parts = listOf(
            EmbeddingModelPart(
                filename = "model.onnx",
                urls = listOf(
                    "https://huggingface.co/sentence-transformers/" +
                        "paraphrase-multilingual-MiniLM-L12-v2/resolve/main/onnx/model.onnx",
                    // v0.15.0 — GH-Release mirror. Pre-uploaded by
                    // .github/workflows/mirror-embedder-assets.yml on
                    // a tagged release named `embedder-assets`. Stable
                    // URL because GH-Releases never rotate the asset
                    // path once published.
                    "https://github.com/Labushuya/so-mi/releases/download/embedder-assets/model.onnx",
                ),
                // Verified from HF blob page 2026-06-09.
                sha256 = "10f7a088420252b26caf819236ca2c9d2987afd0fc06fec7553b542a5655a05a",
                // HF page reports "Size of remote file: 470 MB" without
                // exact bytes. The worker treats this as a UI hint
                // only; SHA-256 is the authoritative verify path.
                sizeBytes = 470L * 1024L * 1024L,
            ),
            EmbeddingModelPart(
                filename = "tokenizer.json",
                urls = listOf(
                    "https://huggingface.co/sentence-transformers/" +
                        "paraphrase-multilingual-MiniLM-L12-v2/resolve/main/tokenizer.json",
                    "https://github.com/Labushuya/so-mi/releases/download/embedder-assets/tokenizer.json",
                ),
                // Verified from HF blob page 2026-06-09.
                sha256 = "2c3387be76557bd40970cec13153b3bbf80407865484b209e655e5e4729076b8",
                sizeBytes = 9_519_907L,
                // v0.15.0 — APK-Asset-Seed-Pfad bleibt bewusst null:
                // wir wollen die ~9.5 MB tokenizer.json NICHT
                // unnötig im Repo. Der Loader-Pfad ist da
                // (EmbeddingModelDownloadWorker.trySeedFromAsset),
                // wer offline-Erststart will, kann die Datei nach
                // app/src/main/assets/embedder/tokenizer.json
                // legen + bundledAsset auf "embedder/tokenizer.json"
                // setzen. Hinweis in
                // android/app/src/main/assets/embedder/README.md.
                bundledAsset = null,
            ),
        ),
        totalSizeBytes = 470L * 1024L * 1024L + 9_519_907L,
    )

    val DEFAULT = PARAPHRASE_MULTILINGUAL_MINILM_L12_V2
}
