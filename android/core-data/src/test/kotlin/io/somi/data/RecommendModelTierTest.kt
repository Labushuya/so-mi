package io.somi.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM tests for [recommendModelTier]. The function is intentionally
 * Android-free, so we don't need Robolectric or instrumented tests.
 *
 * Fixtures are calibrated against the hybrid budget formula:
 *
 *   budget = max(totalRamGB * 0.45, availRamGB * 0.5 + 1.5)
 *
 * See the kdoc on [recommendModelTier] for the SPEC §7 deviation rationale
 * and the per-fixture math.
 */
class RecommendModelTierTest {

    private fun device(
        totalRamGB: Double,
        availRamGB: Double = totalRamGB * 0.6,
        freeStorageGB: Double = 32.0,
        gpuRenderer: String = "Adreno (TM) 740",
        hasVulkan11: Boolean = true,
        hasOpenCL: Boolean = false,
    ) = DeviceInfo(
        totalRamGB = totalRamGB,
        availRamGB = availRamGB,
        freeStorageGB = freeStorageGB,
        gpuRenderer = gpuRenderer,
        hasVulkan11 = hasVulkan11,
        hasOpenCL = hasOpenCL,
    )

    @Test
    fun `Magic V2 16GB total 6GB available picks LARGE green`() {
        // Reference device. Magic V2 reports ~15–16 GB total and ~6 GB
        // available shortly after launch with normal background apps.
        // budget = max(16*0.45, 6*0.5+1.5) = max(7.2, 4.5) = 7.2
        // tight  = 7.2 * 1.15 = 8.28
        //   TINY   (1.5 ≤ 7.2) GREEN
        //   SMALL  (2.5 ≤ 7.2) GREEN
        //   MEDIUM (3.5 ≤ 7.2) GREEN
        //   LARGE  (6.5 ≤ 7.2) GREEN
        val d = device(totalRamGB = 16.0, availRamGB = 6.0, freeStorageGB = 200.0)
        val rec = recommendModelTier(d)
        assertEquals(Light.GREEN, rec.lights[Tier.TINY])
        assertEquals(Light.GREEN, rec.lights[Tier.SMALL])
        assertEquals(Light.GREEN, rec.lights[Tier.MEDIUM])
        assertEquals(Light.GREEN, rec.lights[Tier.LARGE])
        assertEquals(Tier.LARGE, rec.auto)
    }

    @Test
    fun `24GB flagship with 20GB available picks LARGE green`() {
        // budget = max(24*0.45, 20*0.5+1.5) = max(10.8, 11.5) = 11.5
        // tight  = 13.225 — LARGE 6.5 well under both
        val d = device(totalRamGB = 24.0, availRamGB = 20.0, freeStorageGB = 200.0)
        val rec = recommendModelTier(d)
        assertEquals(Light.GREEN, rec.lights[Tier.LARGE])
        assertEquals(Tier.LARGE, rec.auto)
    }

    @Test
    fun `6GB midrange phone with 4GB available picks MEDIUM green`() {
        // budget = max(6*0.45, 4*0.5+1.5) = max(2.7, 3.5) = 3.5
        // tight  = 3.5 * 1.15 = 4.025
        //   TINY   (1.5 ≤ 3.5)  GREEN
        //   SMALL  (2.5 ≤ 3.5)  GREEN
        //   MEDIUM (3.5 ≤ 3.5)  GREEN (boundary: ramOk holds)
        //   LARGE  (6.5 > 4.025) RED
        val d = device(totalRamGB = 6.0, availRamGB = 4.0, freeStorageGB = 50.0)
        val rec = recommendModelTier(d)
        assertEquals(Light.GREEN, rec.lights[Tier.TINY])
        assertEquals(Light.GREEN, rec.lights[Tier.SMALL])
        assertEquals(Light.GREEN, rec.lights[Tier.MEDIUM])
        assertEquals(Light.RED, rec.lights[Tier.LARGE])
        assertEquals(Tier.MEDIUM, rec.auto)
    }

    @Test
    fun `3GB low-end phone with 2GB available picks SMALL green`() {
        // budget = max(3*0.45, 2*0.5+1.5) = max(1.35, 2.5) = 2.5
        // tight  = 2.875
        //   TINY   (1.5 ≤ 2.5)   GREEN
        //   SMALL  (2.5 ≤ 2.5)   GREEN (boundary)
        //   MEDIUM (3.5 > 2.875) RED
        //   LARGE  (6.5 > 2.875) RED
        // SMALL on a 3 GB phone is acceptable: TINY is far too cramped for
        // a real conversation, and SMALL (Qwen2.5 1.5B) fits comfortably in
        // 2.5 GB with a quantized KV cache.
        val d = device(totalRamGB = 3.0, availRamGB = 2.0, freeStorageGB = 8.0)
        val rec = recommendModelTier(d)
        assertEquals(Light.GREEN, rec.lights[Tier.TINY])
        assertEquals(Light.GREEN, rec.lights[Tier.SMALL])
        assertEquals(Light.RED, rec.lights[Tier.MEDIUM])
        assertEquals(Light.RED, rec.lights[Tier.LARGE])
        assertEquals(Tier.SMALL, rec.auto)
    }

    @Test
    fun `pathological 1GB device with no storage falls back to TINY red`() {
        // 1 GB total, 0.5 GB available, 0.4 GB free storage. Even TINY's
        // storage redline (storageMin*1.5 = 1.5 GB) trips → all RED.
        // Recommender returns TINY as the explicit fallback so the UI has
        // something to render; user is forced into the manual override.
        val d = device(totalRamGB = 1.0, availRamGB = 0.5, freeStorageGB = 0.4)
        val rec = recommendModelTier(d)
        assertEquals(Light.RED, rec.lights[Tier.TINY])
        assertEquals(Light.RED, rec.lights[Tier.SMALL])
        assertEquals(Light.RED, rec.lights[Tier.MEDIUM])
        assertEquals(Light.RED, rec.lights[Tier.LARGE])
        assertEquals(Tier.TINY, rec.auto)
    }

    @Test
    fun `auto pick is always the largest GREEN tier across the fixture matrix`() {
        // Walk a range of plausible devices and assert the invariant: if
        // any tier is GREEN, `auto` equals the highest such tier; otherwise
        // `auto` is TINY.
        val matrix = listOf(
            device(totalRamGB = 16.0, availRamGB = 6.0, freeStorageGB = 200.0),
            device(totalRamGB = 24.0, availRamGB = 20.0, freeStorageGB = 200.0),
            device(totalRamGB = 12.0, availRamGB = 8.0, freeStorageGB = 100.0),
            device(totalRamGB = 8.0, availRamGB = 5.0, freeStorageGB = 64.0),
            device(totalRamGB = 6.0, availRamGB = 4.0, freeStorageGB = 50.0),
            device(totalRamGB = 4.0, availRamGB = 2.5, freeStorageGB = 16.0),
            device(totalRamGB = 3.0, availRamGB = 2.0, freeStorageGB = 8.0),
            device(totalRamGB = 1.0, availRamGB = 0.5, freeStorageGB = 0.4),
        )
        for (d in matrix) {
            val rec = recommendModelTier(d)
            val largestGreen = Tier.values()
                .filter { rec.lights[it] == Light.GREEN }
                .maxByOrNull { it.ordinal }
            val expected = largestGreen ?: Tier.TINY
            assertEquals(
                "auto-pick mismatch for device=$d lights=${rec.lights}",
                expected,
                rec.auto,
            )
        }
    }
}
