package `fun`.kirari.hanako.feature.overlay.window

import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import `fun`.kirari.hanako.feature.overlay.state.OverlayStealthStatus
import `fun`.kirari.hanako.feature.overlay.state.StealthStatus

/** Transparent layer exclusion only: never sets FLAG_SECURE or blocks screen capture. */
internal class WindowCaptureExclusion(private val view: View, enabled: Boolean) : AutoCloseable {
    private var stealthEnabled = enabled
    private var temporaryExclusion = false
    private var lastTarget: AntiScreenshotHelper.SurfaceTarget? = null
    private var lastApplied: Boolean? = null
    private var failed = false
    private var waitingFrames = 0
    private var closed = false
    private val beforeDraw = ViewTreeObserver.OnPreDrawListener { refresh(); true }
    private val attachment = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) { close() }
    }

    init {
        if (enabled) OverlayStealthStatus.report(this, StealthStatus.CHECKING)
        view.viewTreeObserver.addOnPreDrawListener(beforeDraw)
        view.addOnAttachStateChangeListener(attachment)
        view.invalidate()
    }

    fun setStealthEnabled(enabled: Boolean) {
        if (stealthEnabled == enabled) return
        stealthEnabled = enabled
        failed = false
        waitingFrames = 0
        if (enabled) OverlayStealthStatus.report(this, StealthStatus.CHECKING)
        else OverlayStealthStatus.remove(this)
        refresh(force = true)
        view.invalidate()
    }

    fun setTemporaryExclusion(enabled: Boolean) {
        if (temporaryExclusion == enabled) return
        temporaryExclusion = enabled
        refresh(force = true)
        view.invalidate()
    }

    /** Cached reflection reads each frame; transactions only for changed surfaces/settings. */
    fun refresh(force: Boolean = false): Boolean {
        if (closed) return false
        val requested = stealthEnabled || temporaryExclusion
        if (!requested && lastApplied != true) return false
        if (failed && !force) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return unsupported()
        if (!view.isAttachedToWindow) return false
        return try {
            val target = AntiScreenshotHelper.currentSurface(view)
            if (target == null) {
                if (++waitingFrames >= 3) unsupported() else {
                    view.postInvalidateOnAnimation()
                    false
                }
            } else {
                waitingFrames = 0
                if (force || target != lastTarget || requested != lastApplied) {
                    AntiScreenshotHelper.setExcluded(target, requested)
                    lastTarget = target
                    lastApplied = requested
                }
                failed = false
                if (stealthEnabled) OverlayStealthStatus.report(this, StealthStatus.REQUESTED)
                else OverlayStealthStatus.remove(this)
                requested
            }
        } catch (error: Exception) {
            Log.w("OverlayStealth", "Transparent layer exclusion unavailable", error)
            unsupported()
        }
    }

    private fun unsupported(): Boolean {
        failed = true
        // A failed temporary exclusion still uses the existing brief hide for our
        // own AI capture. It must not advertise system screenshot/recording privacy.
        val clearingFailed = !stealthEnabled && !temporaryExclusion && lastApplied == true
        if ((stealthEnabled || clearingFailed) && OverlayStealthStatus.report(this, StealthStatus.UNSUPPORTED)) {
            val message = if (clearingFailed) "无法关闭隐匿，请重新启动悬浮窗"
                else "不支持透明隐匿，悬浮窗仍可能被截图或录屏"
            Toast.makeText(view.context, message, Toast.LENGTH_LONG).show()
        }
        return false
    }

    override fun close() {
        if (closed) return
        closed = true
        if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnPreDrawListener(beforeDraw)
        view.removeOnAttachStateChangeListener(attachment)
        OverlayStealthStatus.remove(this)
        lastTarget = null
    }
}
