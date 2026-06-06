package io.somi.data

/**
 * Static catalog of GGUF models the picker offers.
 *
 * SHA-256 values are LFS OIDs from the Hugging Face API as of 2026-06-06.
 * If a hash here ever fails verification post-download, either the file
 * has been re-uploaded upstream (rare; HF treats LFS oids as immutable)
 * or the catalog needs a refresh. Bump these in lockstep, never
 * one-without-the-other.
 *
 * Per Phase-2 architecture decisions: 7B is the Magic-V2 default but
 * download is gated behind a Wi-Fi consent sheet. The 3B tier carries
 * a `qwen-research` license — surface that in the picker before users
 * pick it.
 */
object ModelCatalog {

    /**
     * Tiny smoke-test model. Used in the Phase-2.3 instrumented test;
     * also a sensible "give me chat now even though I'm metered" fallback
     * for users who can't afford the bigger downloads.
     */
    val QWEN25_05B = ModelManifest(
        id = "qwen2.5-0.5b-instruct-q4_0",
        tier = Tier.TINY,
        displayName = "Qwen2.5 0.5B · Q4_0",
        license = "apache-2.0",
        parts = listOf(
            ModelPart(
                filename = "qwen2.5-0.5b-instruct-q4_0.gguf",
                url = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_0.gguf",
                sizeBytes = 428_730_208L,
                sha256 = "7671c0c304e6ce5a7fc577bcb12aba01e2c155cc2efd29b2213c95b18edaf6ed",
            ),
        ),
    )

    /** Default for the SMALL tier. Apache-2.0. Best balance of size/voice. */
    val QWEN25_15B = ModelManifest(
        id = "qwen2.5-1.5b-instruct-q4_k_m",
        tier = Tier.SMALL,
        displayName = "Qwen2.5 1.5B · Q4_K_M",
        license = "apache-2.0",
        parts = listOf(
            ModelPart(
                filename = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
                url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                sizeBytes = 1_117_320_736L,
                sha256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
            ),
        ),
    )

    /**
     * MEDIUM tier. Carries the `qwen-research` license — research /
     * non-commercial. The picker MUST surface this before download.
     * Phase-3 may swap this slot to Phi-3.5-mini-Q4 (MIT) if commercial
     * distribution becomes a concern.
     */
    val QWEN25_3B = ModelManifest(
        id = "qwen2.5-3b-instruct-q4_k_m",
        tier = Tier.MEDIUM,
        displayName = "Qwen2.5 3B · Q4_K_M",
        license = "qwen-research",
        parts = listOf(
            ModelPart(
                filename = "qwen2.5-3b-instruct-q4_k_m.gguf",
                url = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
                sizeBytes = 2_104_932_768L,
                sha256 = "626b4a6678b86442240e33df819e00132d3ba7dddfe1cdc4fbb18e0a9615c62d",
            ),
        ),
    )

    /**
     * LARGE tier — Magic V2 default per SPEC §13. Apache-2.0.
     * Two shards (~3.7 GiB + ~660 MiB). llama.cpp loads multi-part GGUFs
     * by being passed the path to part 00001; ModelStorage must place
     * both parts in the same directory.
     */
    val QWEN25_7B = ModelManifest(
        id = "qwen2.5-7b-instruct-q4_k_m",
        tier = Tier.LARGE,
        displayName = "Qwen2.5 7B · Q4_K_M",
        license = "apache-2.0",
        parts = listOf(
            ModelPart(
                filename = "qwen2.5-7b-instruct-q4_k_m-00001-of-00002.gguf",
                url = "https://huggingface.co/Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/main/qwen2.5-7b-instruct-q4_k_m-00001-of-00002.gguf",
                sizeBytes = 3_993_201_344L,
                sha256 = "dfce12e3862a5283ccfb88221b48480e58745165de856439950d0f22590580db",
            ),
            ModelPart(
                filename = "qwen2.5-7b-instruct-q4_k_m-00002-of-00002.gguf",
                url = "https://huggingface.co/Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/main/qwen2.5-7b-instruct-q4_k_m-00002-of-00002.gguf",
                sizeBytes = 689_872_288L,
                sha256 = "539cf93f78e887edea1c04e2d7d8cdaca9d01dae9c9025bcb8accbe29df3d72a",
            ),
        ),
    )

    /** Catalog ordered ascending by tier, matching the picker layout. */
    val ALL: List<ModelManifest> = listOf(QWEN25_05B, QWEN25_15B, QWEN25_3B, QWEN25_7B)

    /** Lookup by id; null if unknown. */
    fun byId(id: String): ModelManifest? = ALL.firstOrNull { it.id == id }

    /** Which manifest, if any, satisfies a given tier. */
    fun forTier(tier: Tier): ModelManifest? = ALL.firstOrNull { it.tier == tier }
}
