package io.somi.data

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.GLES20
import android.os.StatFs
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Snapshot of device facts relevant to model-tier selection.
 *
 * @param totalRamGB physical RAM as reported by [ActivityManager.MemoryInfo.totalMem]
 * @param availRamGB available RAM as reported by [ActivityManager.MemoryInfo.availMem].
 *   Live, fluctuates with system pressure — read once near app start.
 * @param freeStorageGB free space on the model-storage volume (`Context.getExternalFilesDir`)
 * @param gpuRenderer the EGL/GLES `GL_RENDERER` string, e.g. "Adreno (TM) 740".
 *   Lower-cased substring matching only — vendor casing varies.
 * @param hasVulkan11 system advertises Vulkan 1.1+ via [PackageManager]
 * @param hasOpenCL `libOpenCL.so` resolves on this device. Best-effort:
 *   we attempt [System.load] across the conventional vendor paths and
 *   return true on the first success. Adreno 740 (Magic V2) does ship
 *   the QC OpenCL runtime, but Phase 2 doesn't use it — the field is
 *   informational for Phase 2.5's GPU-backend decision.
 */
data class DeviceInfo(
    val totalRamGB: Double,
    val availRamGB: Double,
    val freeStorageGB: Double,
    val gpuRenderer: String,
    val hasVulkan11: Boolean,
    val hasOpenCL: Boolean,
) {
    /** One-line debug string for logcat / the Phase-2.1 diagnostic banner. */
    fun summary(): String = buildString {
        append("ram=")
        append("%.1f".format(totalRamGB))
        append("/")
        append("%.1f".format(availRamGB))
        append("GB ")
        append("storage=")
        append("%.1f".format(freeStorageGB))
        append("GB ")
        append("gpu=")
        append(gpuRenderer)
        if (hasVulkan11) append(" vk11")
        if (hasOpenCL) append(" cl")
    }
}

/**
 * Detects hardware facts at startup.
 *
 * All probes are non-blocking and safe to call from the main thread —
 * `ActivityManager.getMemoryInfo` and `StatFs` are kernel-cheap;
 * `PackageManager.hasSystemFeature` is a flag lookup. The GPU renderer
 * probe spins up a transient EGL pbuffer surface (1×1 px) just to
 * acquire a context for `glGetString`; that cost is real (~10–50 ms on
 * Magic V2) but happens exactly once because the result is cached for
 * the process lifetime.
 *
 * The OpenCL probe attempts `System.load(path)` across the conventional
 * vendor library paths. A failure throws `UnsatisfiedLinkError` which
 * we swallow — there's no cleaner API for "is this .so loadable" on
 * Android.
 */
@Singleton
class HardwareDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Cheap snapshot. Cached after the first call to avoid the GPU
     * renderer probe's EGL roundtrip on every read.
     */
    fun snapshot(): DeviceInfo {
        cached?.let { return it }
        val info = DeviceInfo(
            totalRamGB = totalRamGB(),
            availRamGB = availRamGB(),
            freeStorageGB = freeStorageGB(),
            gpuRenderer = gpuRenderer(),
            hasVulkan11 = hasVulkan11(),
            hasOpenCL = hasOpenCL(),
        )
        cached = info
        Log.i(TAG, "DeviceInfo: ${info.summary()}")
        return info
    }

    @Volatile
    private var cached: DeviceInfo? = null

    private fun totalRamGB(): Double {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.totalMem.toDouble() / GIB
    }

    private fun availRamGB(): Double {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.availMem.toDouble() / GIB
    }

    /**
     * Free storage in GB — reads the physical device storage, not the
     * app's quota'd allocation. On Android 11+ StatFs on getExternalFilesDir
     * returns the allocatable quota (can be much smaller than physical free),
     * which caused 14B models to show RED on devices with 280+ GB free.
     * Environment.getExternalStorageDirectory() returns the real volume.
     */
    private fun freeStorageGB(): Double {
        return try {
            // Primary: physical external storage (SD card / UFS internal)
            val extDir = android.os.Environment.getExternalStorageDirectory()
            val stat = StatFs(extDir.absolutePath)
            stat.availableBytes.toDouble() / GIB
        } catch (e: Exception) {
            Log.w(TAG, "StatFs on external storage failed, falling back", e)
            try {
                val dir = context.getExternalFilesDir(null) ?: context.filesDir
                val stat = StatFs(dir.absolutePath)
                stat.availableBytes.toDouble() / GIB
            } catch (e2: Exception) {
                Log.w(TAG, "StatFs fallback also failed", e2)
                0.0
            }
        }
    }

    /**
     * Read the GLES2 `GL_RENDERER` string on a transient pbuffer context.
     * Returns "unknown" on EGL setup failure.
     */
    private fun gpuRenderer(): String {
        return try {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return "unknown"
            val versions = IntArray(2)
            if (!EGL14.eglInitialize(display, versions, 0, versions, 1)) return "unknown"

            val configAttribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) ||
                numConfigs[0] == 0
            ) {
                EGL14.eglTerminate(display)
                return "unknown"
            }

            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            val context = EGL14.eglCreateContext(
                display, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0,
            )
            if (context == EGL14.EGL_NO_CONTEXT) {
                EGL14.eglTerminate(display)
                return "unknown"
            }

            val surfAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            val surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfAttribs, 0)
            if (surface == EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
                return "unknown"
            }

            EGL14.eglMakeCurrent(display, surface, surface, context)
            val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "unknown"

            // Tear everything down — we only wanted the string.
            EGL14.eglMakeCurrent(
                display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT,
            )
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)

            renderer
        } catch (t: Throwable) {
            Log.w(TAG, "GLES renderer probe failed", t)
            "unknown"
        }
    }

    /**
     * Vulkan 1.1 capability via the system feature flag.
     *
     * The PackageManager feature key for Vulkan version is
     * `android.hardware.vulkan.version`. The version is encoded as a
     * VkApi-style 32-bit integer; 1.1 = 0x401000.
     */
    private fun hasVulkan11(): Boolean {
        val pm = context.packageManager
        return pm.hasSystemFeature("android.hardware.vulkan.version", VULKAN_1_1)
    }

    /**
     * Best-effort: probe a few well-known vendor paths for the OpenCL ICD.
     * On Adreno phones it's typically at `/system/vendor/lib64/libOpenCL.so`.
     * Magic V2 ships it; many Mediatek/Exynos devices don't.
     *
     * `System.load` either succeeds or throws `UnsatisfiedLinkError`. We
     * don't unload — the runtime keeps the symbol table around, but
     * Phase 2 doesn't use it, so the cost is the dynamic linker resolve
     * and a few page-cache hits.
     */
    private fun hasOpenCL(): Boolean {
        val candidates = listOf(
            "libOpenCL.so",
            "/system/vendor/lib64/libOpenCL.so",
            "/system/vendor/lib/libOpenCL.so",
            "/system/lib64/libOpenCL.so",
            "/system/lib/libOpenCL.so",
        )
        for (path in candidates) {
            try {
                System.load(path)
                return true
            } catch (e: UnsatisfiedLinkError) {
                // Try the next candidate.
            } catch (e: SecurityException) {
                // Some hardened ROMs deny System.load on absolute paths.
            }
        }
        // Final fallback: try System.loadLibrary which uses the LD_LIBRARY_PATH.
        return try {
            System.loadLibrary("OpenCL")
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }

    private companion object {
        const val TAG = "HardwareDetector"
        const val GIB = 1024.0 * 1024.0 * 1024.0
        const val VULKAN_1_1 = 0x401000
    }
}
