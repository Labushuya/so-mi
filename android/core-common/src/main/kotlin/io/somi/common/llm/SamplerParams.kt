package io.somi.common.llm

/**
 * Tunable LLM sampling parameters surfaced to the user via the v0.11.4
 * Settings → "Verhalten" section.
 *
 * Lives in :core-common because both :core-llm (interface) and
 * :core-data (persistence repo) need to reference it; a hop through
 * :core-llm would be a cross-`core-*` dependency that SPEC §3 forbids.
 *
 * Defaults are the values baked into ai_chat.cpp's DEFAULT_SAMPLER_*
 * constants — keep them in sync. Changing the defaults here without
 * mirroring in C++ will produce confusing initial state on first
 * launch where the C++ side runs a different config than what the
 * Settings UI displays.
 */
data class SamplerParams(
    /** Randomness. Lower = more deterministic, higher = more wild. */
    val temperature: Float = 0.3f,
    /** Top-P (nucleus) sampling cutoff. */
    val topP: Float = 0.9f,
    /** Penalty multiplier for repeated tokens. 1.0 = off, > 1 = stronger anti-loop. */
    val repeatPenalty: Float = 1.1f,
    /** Top-K cutoff. Higher = more candidate tokens considered per step. */
    val topK: Int = 40,
    /** Hard ceiling on tokens generated per turn. */
    val maxTokens: Int = 1024,
) {
    companion object {
        val DEFAULTS = SamplerParams()

        // Slider bounds for the Settings UI. Keep the ranges narrow
        // enough that the model still produces coherent German output
        // at the extremes.
        val TEMPERATURE_RANGE: ClosedFloatingPointRange<Float> = 0.0f..1.5f
        val TOP_P_RANGE: ClosedFloatingPointRange<Float> = 0.5f..1.0f
        val REPEAT_PENALTY_RANGE: ClosedFloatingPointRange<Float> = 1.0f..1.5f
        val TOP_K_RANGE: IntRange = 10..100
        val MAX_TOKENS_RANGE: IntRange = 256..2048

        /** Round-trip clamp so persisted-then-reloaded values can't drift outside the UI's range. */
        fun clamp(p: SamplerParams): SamplerParams = p.copy(
            temperature = p.temperature.coerceIn(TEMPERATURE_RANGE),
            topP = p.topP.coerceIn(TOP_P_RANGE),
            repeatPenalty = p.repeatPenalty.coerceIn(REPEAT_PENALTY_RANGE),
            topK = p.topK.coerceIn(TOP_K_RANGE),
            maxTokens = p.maxTokens.coerceIn(MAX_TOKENS_RANGE),
        )
    }
}
