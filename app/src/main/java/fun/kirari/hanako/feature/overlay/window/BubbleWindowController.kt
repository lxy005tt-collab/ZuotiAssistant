package `fun`.kirari.hanako.feature.overlay.window

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import `fun`.kirari.hanako.core.data.FloatingWindowSettings
import `fun`.kirari.hanako.feature.overlay.state.OverlayUiState
import kotlin.math.roundToInt

/** The controls and every expanded sheet share this single native overlay window. */
internal class BubbleWindowController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val lifecycleOwner: LifecycleOwner,
    private val viewModelStoreOwner: ViewModelStoreOwner,
    private val savedStateRegistryOwner: SavedStateRegistryOwner,
    private val onSettingsTap: () -> Unit,
    private val onCaptureTap: () -> Unit,
    private val floatingWindowSettings: () -> FloatingWindowSettings,
    private val onOutsideTap: () -> Unit,
    private val onConfigurationUpdate: () -> Unit
) {
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).roundToInt()
    private fun cardDp(value: Int) = (value * density * floatingWindowSettings().sizeScale).roundToInt()
    private val backgroundAlpha get() = (floatingWindowSettings().backgroundOpacity * 255).roundToInt()
    private var root: FrameLayout? = null
    private var controls: DualBubbleView? = null
    private var params: WindowManager.LayoutParams? = null
    private var panel: View? = null
    private var panelWidth = 0
    private var panelHeight = 0
    private var panelArea: CardBounds? = null
    private var anchorX = dp(12)
    private var anchorY = dp(120)
    private var dragStartX = 0
    private var dragStartY = 0
    private var boundsAnimator: ValueAnimator? = null
    private var closing = false
    private var targetBounds: CardBounds? = null
    private var appliedAppearance: FloatingWindowSettings? = null
    val headerHeight: Int get() = controls?.desiredHeight ?: cardDp(60)
    private val cardBackground = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.WHITE, Color.rgb(246, 250, 255))
    ).apply {
        cornerRadius = cardDp(24).toFloat()
        alpha = backgroundAlpha
        setStroke(dp(1), Color.argb(36, 70, 101, 139))
    }

    fun show() {
        if (root != null) return
        val appearance = floatingWindowSettings()
        val header = DualBubbleView(context, appearance.sizeScale, onSettingsTap, onCaptureTap,
            onDragStart = {
                dragStartX = (params?.x ?: anchorX) + ((params?.width ?: 0) - (controls?.desiredWidth ?: 0)) / 2
                dragStartY = params?.y ?: anchorY
            },
            onDrag = { dx, dy ->
                anchorX = dragStartX + dx
                anchorY = dragStartY + dy
                layoutWindow(animate = false)
            },
            onConfigurationUpdate = { onConfigurationUpdate(); layoutWindow(animate = false) }).apply {
            updateAppearance(appearance.sizeScale, appearance.backgroundOpacity)
        }
        val host = object : FrameLayout(context) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    if (floatingWindowSettings().closeOnOutsideTap) onOutsideTap()
                    return true
                }
                return super.dispatchTouchEvent(event)
            }
        }.apply {
            // Compose creates its window recomposer from this native root, not from the
            // nested panel. Bind all owners before this root or any Compose child attaches.
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(viewModelStoreOwner)
            setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
        }
        controls = header
        root = host
        host.addView(header, FrameLayout.LayoutParams(header.desiredWidth, header.desiredHeight))
        params = WindowManager.LayoutParams(header.desiredWidth, header.desiredHeight, overlayWindowType(),
            windowFlags(expanded = false), PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = anchorX
            y = anchorY
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
        }
        windowManager.addView(host, params)
        AntiScreenshotHelper.applyTo(host)
        layoutWindow(animate = false)
    }

    fun showPanel(view: View, width: Int, height: Int, safeArea: CardBounds) {
        val host = root ?: return
        if (closing) {
            cancelBoundsAnimation()
            closing = false
            targetBounds = null
        }
        if (panel !== view) {
            panel?.let(host::removeView)
            (view.parent as? ViewGroup)?.removeView(view)
            panel = view
            host.addView(view)
        }
        view.alpha = 1f
        cardBackground.alpha = backgroundAlpha
        host.background = cardBackground
        host.elevation = cardDp(8).toFloat()
        host.clipToOutline = true
        panelWidth = width
        panelHeight = height
        panelArea = safeArea
        controls?.setExpanded(true)
        layoutWindow(animate = true)
    }

    fun hidePanel(animate: Boolean = false, onHidden: () -> Unit = {}) {
        val host = root
        val header = controls
        val layout = params
        val body = panel
        if (animate && closing) return
        cancelBoundsAnimation()
        if (!animate || host == null || header == null || layout == null || body == null ||
            host.visibility != View.VISIBLE || !host.isAttachedToWindow) {
            finishHidingPanel()
            onHidden()
            return
        }

        closing = true
        val from = CardBounds(layout.x, layout.y, layout.x + layout.width, layout.y + layout.height)
        val anchor = CardBounds(anchorX, anchorY, anchorX + header.desiredWidth, anchorY + header.desiredHeight)
        val target = placeUnifiedWindow(screenArea(), anchor, header.desiredWidth, header.desiredHeight)
        // Leave the Compose body at its expanded size. Only the native window clips it;
        // closing must not reflow Markdown at each narrower animation frame.
        boundsAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220L
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener {
                val progress = it.animatedValue as Float
                fun between(start: Int, end: Int) = (start + (end - start) * progress).roundToInt()
                body.alpha = (1f - progress / .65f).coerceIn(0f, 1f)
                cardBackground.alpha = (backgroundAlpha * (1f - progress)).roundToInt()
                host.elevation = cardDp(8) * (1f - progress)
                header.setCollapseProgress(progress)
                applyBounds(CardBounds(between(from.left, target.left), between(from.top, target.top),
                    between(from.right, target.right), between(from.bottom, target.bottom)))
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    boundsAnimator = null
                    finishHidingPanel()
                    onHidden()
                }
            })
            start()
        }
    }

    private fun finishHidingPanel() {
        closing = false
        panel?.alpha = 1f
        panel?.let { root?.removeView(it) }
        panel = null
        panelArea = null
        root?.apply { background = null; elevation = 0f; clipToOutline = false }
        cardBackground.alpha = backgroundAlpha
        controls?.setExpanded(false)
        targetBounds = null
        layoutWindow(animate = false)
    }

    fun update(state: OverlayUiState) {
        val appearance = state.settings.floatingWindow
        if (appliedAppearance != appearance) {
            appliedAppearance = appearance
            val resized = controls?.updateAppearance(appearance.sizeScale, appearance.backgroundOpacity) == true
            cardBackground.cornerRadius = cardDp(24).toFloat()
            if (!closing) cardBackground.alpha = backgroundAlpha
            if (resized) {
                targetBounds = null
                if (panel == null) layoutWindow(animate = false)
            }
        }
        controls?.update(state.working || state.capturing, state.settingsMenuVisible)
        root?.visibility = if (state.capturing) View.INVISIBLE else View.VISIBLE
    }

    fun hideForCapture() { root?.visibility = View.INVISIBLE }

    fun destroy() {
        cancelBoundsAnimation()
        closing = false
        root?.let { if (it.isAttachedToWindow) windowManager.removeViewImmediate(it) }
        root = null
        controls = null
        panel = null
        params = null
    }

    private fun layoutWindow(animate: Boolean) {
        if (closing) return
        val host = root ?: return
        val header = controls ?: return
        val layout = params ?: return
        val expanded = panel != null
        val area = panelArea ?: screenArea()
        val anchor = CardBounds(anchorX, anchorY, anchorX + header.desiredWidth, anchorY + header.desiredHeight)
        val width = if (expanded) panelWidth else header.desiredWidth
        val height = header.desiredHeight + if (expanded) panelHeight + cardDp(8) else 0
        val target = placeUnifiedWindow(area, anchor, width, height)
        // Keep the child at its final size while the parent reveals it. This avoids text reflow
        // and prevents the appearing action row from squeezing the answer during growth.
        panel?.let { body ->
            val bodyHeight = (target.height - header.desiredHeight - cardDp(8)).coerceAtLeast(1)
            val bodyParams = body.layoutParams as? FrameLayout.LayoutParams
            if (bodyParams == null || bodyParams.width != target.width || bodyParams.height != bodyHeight || bodyParams.topMargin != header.desiredHeight) {
                body.layoutParams = FrameLayout.LayoutParams(target.width, bodyHeight).apply { topMargin = header.desiredHeight }
            }
        }
        val flags = windowFlags(expanded)
        val flagsChanged = layout.flags != flags
        layout.flags = flags
        if (target == targetBounds && !flagsChanged) return
        targetBounds = target
        cancelBoundsAnimation()
        if (animate && expanded && host.visibility == View.VISIBLE && layout.height != target.height) {
            boundsAnimator = ValueAnimator.ofInt(layout.height, target.height).apply {
                duration = 150L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    val frame = placeUnifiedWindow(area, anchor, target.width, it.animatedValue as Int)
                    applyBounds(frame)
                }
                start()
            }
        } else {
            applyBounds(target)
        }
    }

    private fun applyBounds(bounds: CardBounds) {
        val host = root ?: return
        val layout = params ?: return
        controls?.let { header ->
            val headerParams = header.layoutParams as FrameLayout.LayoutParams
            if (headerParams.width != bounds.width || headerParams.height != header.desiredHeight) {
                headerParams.width = bounds.width
                headerParams.height = header.desiredHeight
                header.layoutParams = headerParams
            }
        }
        layout.x = bounds.left
        layout.y = bounds.top
        layout.width = bounds.width
        layout.height = bounds.height
        if (host.isAttachedToWindow) windowManager.updateViewLayout(host, layout)
    }

    private fun windowFlags(expanded: Boolean): Int =
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            (if (expanded) WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

    private fun cancelBoundsAnimation() {
        boundsAnimator?.apply {
            removeAllUpdateListeners()
            removeAllListeners()
            cancel()
        }
        boundsAnimator = null
    }

    private fun screenArea(): CardBounds {
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            return CardBounds(metrics.bounds.left + insets.left + dp(4), metrics.bounds.top + insets.top + dp(4),
                metrics.bounds.right - insets.right - dp(4), metrics.bounds.bottom - insets.bottom - dp(4))
        }
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return CardBounds(dp(4), dp(28), metrics.widthPixels - dp(4), metrics.heightPixels - dp(28))
    }
}
