package io.somi.data

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Snapshot of device facts relevant to model-tier selection.
 *
 * Phase 2.1 placeholder values; Phase 2.2 will populate them from
 * `ActivityManager.MemoryInfo`, `StatFs`, GLES `glGetString(GL_RENDERER)`,
 * and `PackageManager.hasSystemFeature` per SPEC §7.
 */
data class DeviceInfo(
    val totalRamGB: Double,
    val availRamGB: Double,
    val freeStorageGB: Double,
    val gpuRenderer: String,
    val hasVulkan11: Boolean,
    val hasOpenCL: Boolean,
)

/**
 * Detects hardware facts at startup.
 *
 * Phase 2.1: returns hardcoded "unknown / 0 / false" values so the DI
 * graph + ViewModel state machine can be exercised before real probes
 * land. Phase 2.2 wires the actual implementations + unit tests.
 */
@Singleton
class HardwareDetector @Inject constructor() {

    /**
     * Cheap, synchronous snapshot. Safe to call from the main thread.
     * Phase 2.2 will keep that contract — `ActivityManager.getMemoryInfo`
     * and `StatFs` are non-blocking; only the GLES renderer string costs
     * (an EGL context init), which we'll cache after first call.
     */
    fun snapshot(): DeviceInfo = DeviceInfo(
        totalRamGB = 0.0,
        availRamGB = 0.0,
        freeStorageGB = 0.0,
        gpuRenderer = "unknown",
        hasVulkan11 = false,
        hasOpenCL = false,
    )
}
