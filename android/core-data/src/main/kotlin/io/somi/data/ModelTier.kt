package io.somi.data

/**
 * Model size tier per SPEC §7.
 *
 * Ordered ascending: TINY < SMALL < MEDIUM < LARGE. The picker auto-selects
 * the LARGEST tier whose ramp-light is GREEN — that ordering is load-bearing
 * for [recommendModelTier].
 */
enum class Tier { TINY, SMALL, MEDIUM, LARGE }

/**
 * Hardware-Ampel verdict for a given tier on a given device.
 *
 * - GREEN: comfortable — RAM headroom + plenty of free storage.
 * - YELLOW: tight — runs but with little safety margin.
 * - RED: do not run — either RAM is borderline or storage is too tight.
 */
enum class Light { GREEN, YELLOW, RED }

/**
 * Concrete tier specification: minimum RAM and storage to consider a model
 * of this tier. Numbers are SPEC §7 verbatim.
 *
 * @param tier identity
 * @param ramMinGB minimum total RAM (process budget — see [recommendModelTier])
 * @param storageMinGB minimum free storage to fit the GGUF
 * @param exampleModel display string for the picker (Phase 2.5+)
 * @param expectedTokPerSec UI hint, not used by the recommender
 */
data class TierSpec(
    val tier: Tier,
    val ramMinGB: Double,
    val storageMinGB: Double,
    val exampleModel: String,
    val expectedTokPerSec: String,
)

/**
 * Tier table from SPEC §7. Order matters: ascending by capacity so the
 * recommender's `lastOrNull { GREEN }` walk picks the largest fitting tier.
 */
val TIER_SPECS: List<TierSpec> = listOf(
    TierSpec(
        tier = Tier.TINY,
        ramMinGB = 1.5,
        storageMinGB = 1.0,
        exampleModel = "Llama 3.2 1B Q4_0",
        expectedTokPerSec = "16–22",
    ),
    TierSpec(
        tier = Tier.SMALL,
        ramMinGB = 2.5,
        storageMinGB = 2.0,
        exampleModel = "Qwen2.5 1.5B Q4_K_M",
        expectedTokPerSec = "10–15",
    ),
    TierSpec(
        tier = Tier.MEDIUM,
        ramMinGB = 3.5,
        storageMinGB = 3.5,
        exampleModel = "Qwen2.5 3B Q4_K_M",
        expectedTokPerSec = "7–10",
    ),
    TierSpec(
        tier = Tier.LARGE,
        ramMinGB = 6.5,
        storageMinGB = 7.0,
        exampleModel = "Qwen2.5 7B Q4_K_M",
        expectedTokPerSec = "4–6",
    ),
)

/**
 * The recommender's verdict — per-tier light + the auto-pick.
 *
 * @param lights every tier's traffic-light state
 * @param auto the largest tier with [Light.GREEN], or [Tier.TINY] as fallback
 *   when nothing is green (the user can still try TINY at their own risk;
 *   the UI will mark it red)
 */
data class Recommendation(
    val lights: Map<Tier, Light>,
    val auto: Tier,
)

/**
 * Score a [DeviceInfo] against [TIER_SPECS] per SPEC §7.
 *
 * Logic (verbatim from SPEC §7's Kotlin pseudocode):
 *
 *   budget = availRamGB * 0.25 + 1.5
 *   for each tier:
 *     ramOk     = spec.ramMinGB <= budget
 *     ramTight  = spec.ramMinGB <= budget * 1.15
 *     storageOk = freeStorageGB >= spec.storageMinGB * 3
 *     light = when:
 *       not ramTight or freeStorage < spec.storageMinGB * 1.5 -> RED
 *       not ramOk or not storageOk                            -> YELLOW
 *       else                                                  -> GREEN
 *   auto = largest GREEN tier, fallback TINY
 *
 * The 25%-of-available-RAM + 1.5 GB KV-cache headroom number is the budget
 * SPEC §7 commits to. Don't tweak it without re-running the math against
 * the tier table — it's calibrated so Magic V2 picks LARGE and a 6 GB
 * phone picks MEDIUM.
 */
fun recommendModelTier(d: DeviceInfo): Recommendation {
    val budget = d.availRamGB * 0.25 + 1.5

    val lights: Map<Tier, Light> = TIER_SPECS.associate { spec ->
        val ramOk = spec.ramMinGB <= budget
        val ramTight = spec.ramMinGB <= budget * 1.15
        val storageOk = d.freeStorageGB >= spec.storageMinGB * 3
        val storageRedline = d.freeStorageGB < spec.storageMinGB * 1.5
        val light = when {
            !ramTight || storageRedline -> Light.RED
            !ramOk || !storageOk -> Light.YELLOW
            else -> Light.GREEN
        }
        spec.tier to light
    }

    val auto = lights.entries
        .lastOrNull { it.value == Light.GREEN }
        ?.key
        ?: Tier.TINY

    return Recommendation(lights = lights, auto = auto)
}
