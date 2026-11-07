package `fun`.kirari.hanako.feature.overlay.window

import android.os.Build
import android.util.Log
import android.view.SurfaceControl
import android.view.View
import org.lsposed.hiddenapibypass.HiddenApiBypass

internal object AntiScreenshotHelper {
    private const val TAG = "AntiScreenshot"
    private var initialized = false

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
            val getVri = View::class.java.getDeclaredMethod("getViewRootImpl")
            getVri.isAccessible = true
            val vri = getVri.invoke(view) ?: return false

            val getSc = vri.javaClass.getMethod("getSurfaceControl")
            val sc = getSc.invoke(vri) as? SurfaceControl ?: return false
            if (!sc.isValid) return false

            val txClass = Class.forName("android.view.SurfaceControl\$Transaction")
            val setSkipMethod = txClass.getMethod(
                "setSkipScreenshot",
                SurfaceControl::class.java,
                java.lang.Boolean.TYPE
            )
            SurfaceControl.Transaction().use { transaction ->
                setSkipMethod.invoke(transaction, sc, true)
                transaction.apply()
            }
            Log.d(TAG, "Own overlay layer excluded from screen capture")
            true
        }.onFailure {
            Log.w(TAG, "Layer exclusion unavailable; using temporary hide", it)
        }.getOrDefault(false)
    }
}
