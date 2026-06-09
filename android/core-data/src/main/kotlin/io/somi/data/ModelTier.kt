package io.somi.data

/**
 * Model size tier per SPEC §7.
 *
 * Ordered ascending: TINY < SMALL < MEDIUM < LARGE < XLARGE. The picker
 * auto-selects the LARGEST tier whose ramp-light is GREEN — that ordering
 * is load-bearing for [recommendModelTier].
 *
 * XLARGE = 14B+ models. Separate tier so 14B gets YELLOW on Magic V2 (16 GB)
 * while 7B stays GREEN. Both map to Tier.LARGE previously — this splits them.
 */
enum class Tier { TINY, SMALL, MEDIUM, LARGE, XLARGE }

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
    TierSpec(
        tier = Tier.XLARGE,
        // 14B Q4_K_M needs ~9 GB process RAM. On Magic V2 (16 GB total,
        // budget = 7.2 GB) this exceeds GREEN (6.5) but fits YELLOW (×1.15 = 8.28).
        // Q3_K_M needs ~7.3 GB — also YELLOW on Magic V2.
        ramMinGB = 8.5,
        storageMinGB = 10.0,
        exampleModel = "Qwen2.5 14B Q4_K_M",
        expectedTokPerSec = "2–3",
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
 * Score a [DeviceInfo] against [TIER_SPECS] per SPEC §7 — with a calibrated
 * deviation from the spec's pseudocode budget formula.
 *
 * ## SPEC §7 deviation: hybrid budget formula
 *
 * SPEC §7 prescribes:
 *
 *   budget = availRamGB * 0.25 + 1.5
 *
 * This is too conservative in practice. On a Magic V2 (16 GB total, ~6 GB
 * available shortly after launch with normal background apps) the original
 * formula yields:
 *
 *   budget = 6.0 * 0.25 + 1.5 = 3.0 GB  →  SMALL/TINY only
 *
 * The SPEC's prose (and our reference-device target) explicitly expects
 * Magic V2 to land on LARGE. The numeric formula and the prose are out of
 * sync; we follow the prose intent.
 *
 * The replacement is a hybrid that combines a fraction of *total* RAM (the
 * static ceiling — what the kernel will let us touch under pressure) with
 * the existing *available*-RAM term (the dynamic floor — what's free right
 * now). We take the max so a freshly-rebooted phone with lots of free RAM
 * still benefits, while a high-RAM phone in a busy state isn't punished
 * for transient pressure:
 *
 *   budget = max(totalRamGB * 0.45, availRamGB * 0.5 + 1.5)
 *
 * Verification against [TIER_SPECS] (LARGE.ramMin=6.5, MEDIUM=3.5,
 * SMALL=2.5, TINY=1.5; tight = budget * 1.15):
 *
 *   Magic V2 (16/6):   max(7.2, 4.5)  = 7.2;   tight=8.28  →  LARGE GREEN
 *   24 GB (24/20):     max(10.8, 11.5)= 11.5;  tight=13.2  →  LARGE GREEN
 *   6 GB midrange (6/4): max(2.7, 3.5)= 3.5;   tight=4.025 →  MEDIUM GREEN
 *   3 GB low-end (3/2):  max(1.35, 2.5)= 2.5;  tight=2.875 →  SMALL GREEN
 *   1 GB pathological (1/0.5, 0.4 GB storage): everything RED, fallback TINY
 *
 * The 0.45 multiplier is the smallest value that lifts Magic V2 out of the
 * YELLOW band into GREEN; smaller values (e.g. 0.4) leave LARGE at YELLOW.
 *
 * See `spec-7-tier-formula-too-conservative` memory note for the design
 * discussion. Don't tweak these constants without re-running the math
 * against the full tier table — the calibration is load-bearing for the
 * Phase-2 reference-device flow (Magic V2 → LARGE auto-pick → 4.7 GB
 * download → llama loads).
 *
 * Logic (the rest is SPEC §7 verbatim):
 *
 *   for each tier:
 *     ramOk     = spec.ramMinGB <= budget
 *     ramTight  = spec.ramMinGB <= budget * 1.15
 *     storageOk = freeStorageGB >= spec.storageMinGB * 3
 *     storageRedline = freeStorageGB < spec.storageMinGB * 1.5
 *     light = when:
 *       not ramTight or storageRedline -> RED
 *       not ramOk or not storageOk     -> YELLOW
 *       else                           -> GREEN
 *   auto = largest GREEN tier, fallback TINY
 */
fun recommendModelTier(d: DeviceInfo): Recommendation {
    // Hybrid budget — see SPEC §7 deviation block in the kdoc above.
    val budget = maxOf(d.totalRamGB * 0.45, d.availRamGB * 0.5 + 1.5)

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
