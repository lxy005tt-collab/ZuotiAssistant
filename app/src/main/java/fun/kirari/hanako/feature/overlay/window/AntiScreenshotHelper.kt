package `fun`.kirari.hanako.feature.overlay.window

import android.os.Build
import android.util.Log
import android.view.SurfaceControl
import android.view.View
import org.lsposed.hiddenapibypass.HiddenApiBypass

internal object AntiScreenshotHelper {
    private const val TAG = "AntiScreenshot"
    private var initialized = false
    private class Access {
        val viewRoot = View::class.java.getDeclaredMethod("getViewRootImpl").apply { isAccessible = true }
        val surface = Class.forName("android.view.ViewRootImpl").getMethod("getSurfaceControl")
        val skip = SurfaceControl.Transaction::class.java.getMethod(
            "setSkipScreenshot", SurfaceControl::class.java, java.lang.Boolean.TYPE)
        // ViewRootImpl may reuse the Java wrapper when replacing its native surface.
        val nativeObject = runCatching {
            SurfaceControl::class.java.getDeclaredField("mNativeObject").apply { isAccessible = true }
        }.getOrNull()
    }
    private val access by lazy { initialize(); runCatching { Access() } }

    internal data class SurfaceTarget(val control: SurfaceControl, val nativeHandle: Long)

    fun currentSurface(view: View): SurfaceTarget? {
        if (!view.isAttachedToWindow) return null
        val api = access.getOrThrow()
        val viewRoot = api.viewRoot.invoke(view) ?: return null
        val surface = api.surface.invoke(viewRoot) as? SurfaceControl ?: return null
        if (!surface.isValid) return null
        return SurfaceTarget(surface, runCatching { api.nativeObject?.getLong(surface) }.getOrNull() ?: 0L)
    }

    fun setExcluded(target: SurfaceTarget, excluded: Boolean) {
        SurfaceControl.Transaction().use { transaction ->
            access.getOrThrow().skip.invoke(transaction, target.control, excluded)
            transaction.apply()
        }
    }

    fun initialize() {
        if (initialized) return
        initialized = true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }.onFailure {
            Log.w(TAG, "HiddenApiBypass initialization failed", it)
        }
    }

    /**
     * 对通过 [WindowManager.addView] 添加的悬浮窗 View 应用防截屏保护。
     *
     * 必须在 [WindowManager.addView] 之后调用。
     * 内部通过 [View.post] 延迟到 View 附着窗口后执行，
     * 通过反射获取 [ViewRootImpl.getSurfaceControl] 并调用
     * [SurfaceControl.Transaction.setSkipScreenshot]。
     *
     * 仅对本应用的悬浮窗设置，不改变被截图应用的任何窗口属性。
     */
    fun applyTo(view: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        view.post { excludeFromCapture(view) }
    }

    /**
     * Skip our layer (and its children) rather than marking it SECURE, which would
     * leave a blank region. Hidden APIs vary across ROMs; failure must fall back to
     * briefly hiding the window before capture. A successful call is not a device
     * compatibility guarantee: the result is still checked on the actual device.
     */
    fun excludeFromCapture(view: View): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !view.isAttachedToWindow) return false
        initialize()
        return runCatching {
            val target = currentSurface(view) ?: return false
            setExcluded(target, true)
            Log.d(TAG, "Own overlay layer excluded from screen capture")
            true
        }.onFailure {
            Log.w(TAG, "Layer exclusion unavailable; using temporary hide", it)
        }.getOrDefault(false)
    }
}
