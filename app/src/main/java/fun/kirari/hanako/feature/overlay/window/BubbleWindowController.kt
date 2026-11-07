package `fun`.kirari.hanako.feature.overlay.window

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager
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
import `fun`.kirari.hanako.feature.overlay.state.isSelectingRegion
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
    private val stealthEnabled: () -> Boolean,
    private val onOutsideTap: () -> Unit,
    private val onConfigurationUpdate: () -> Unit
) {
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).roundToInt()
    private fun cardDp(value: Int) = (value * density * floatingWindowSettings().sizeScale).roundToInt()
    private val backgroundAlpha get() = (floatingWindowSettings().backgroundOpacity * 255).roundToInt()
    private var root: FrameLayout? = null
    private var content: FrameLayout? = null
    private var controls: DualBubbleView? = null
    private var params: WindowManager.LayoutParams? = null
    private var panel: View? = null
    private var panelBodyVisible = true
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
    private var visualBounds: CardBounds? = null
    private var expansionFraction = 0f
    private var captureExclusionApplied = false
    private var captureProtection: WindowCaptureExclusion? = null
    private val clipRect = Rect()
    private val actualWindowLocation = IntArray(2)
    private val positionBeforeDraw = ViewTreeObserver.OnPreDrawListener {
        visualBounds?.let(::positionContentOnScreen)
        true
    }
    private var appliedAppearance: FloatingWindowSettings? = null
    val headerHeight: Int get() = controls?.desiredHeight ?: cardDp(60)
    private val cardBackground = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.WHITE, Color.WHITE)
    ).apply {
        cornerRadius = cardDp(24).toFloat()
        alpha = backgroundAlpha
    }

    fun show() {
        if (root != null) return
        val appearance = floatingWindowSettings()
        val header = DualBubbleView(context, appearance.sizeScale, onSettingsTap, onCaptureTap,
            onDragStart = {
                dragStartX = visualBounds?.left ?: anchorX
                dragStartY = visualBounds?.top ?: anchorY
            },
            onDrag = { dx, dy ->
                anchorX = dragStartX + dx
                anchorY = dragStartY + dy
                layoutWindow(animate = false)
            },
            onConfigurationUpdate = {
                captureProtection?.refresh(force = true)
                onConfigurationUpdate()
                layoutWindow(animate = false)
            }).apply {
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
            clipChildren = false
        }
        val body = FrameLayout(context).apply {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val bounds = visualBounds
                    outline.setRoundRect(0, 0, bounds?.width ?: view.width,
                        bounds?.height ?: view.height, cardDp(24).toFloat())
                }
            }
        }
        controls = header
        root = host
        content = body
        body.addView(header, FrameLayout.LayoutParams(header.desiredWidth, header.desiredHeight))
        host.addView(body, FrameLayout.LayoutParams(header.desiredWidth, header.desiredHeight))
        params = WindowManager.LayoutParams(header.desiredWidth, header.desiredHeight, overlayWindowType(),
            windowFlags(expanded = false), PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = anchorX
            y = anchorY
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
        }
        windowManager.addView(host, params)
        host.viewTreeObserver.addOnPreDrawListener(positionBeforeDraw)
        captureProtection = WindowCaptureExclusion(host, stealthEnabled())
        layoutWindow(animate = false)
    }

    fun showPanel(view: View, width: Int, height: Int, safeArea: CardBounds, showBody: Boolean = true) {
        val host = content ?: return
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
            view.alpha = 0f
        }
        if (showBody && !panelBodyVisible) view.alpha = 0f
        panelBodyVisible = showBody
        view.visibility = if (showBody) View.VISIBLE else View.INVISIBLE
        host.background = if (showBody) cardBackground else null
        updateExpansion(expansionFraction)
        host.clipToOutline = showBody
        panelWidth = width
        panelHeight = height
        panelArea = safeArea
        layoutWindow(animate = true)
    }

    fun hidePanel(animate: Boolean = false, onHidden: () -> Unit = {}) {
        val host = content
        val header = controls
        val body = panel
        if (animate && closing) return
        cancelBoundsAnimation()
        // After edge clamping, keep the controls where the user currently sees them.
        // Closing no longer pulls the window back towards its pre-expansion position.
        if (body != null) visualBounds?.let { anchorX = it.left; anchorY = it.top }
        if (!animate || host == null || header == null || body == null ||
            root?.visibility != View.VISIBLE || !host.isAttachedToWindow) {
            finishHidingPanel()
            onHidden()
            return
        }

        closing = true
        val anchor = CardBounds(anchorX, anchorY, anchorX + header.desiredWidth, anchorY + header.desiredHeight)
        val target = placeUnifiedWindow(screenArea(), anchor, header.desiredWidth, header.desiredHeight)
        // Translation and clipping share one timeline. The Compose body retains its size,
        // and WindowManager is updated only at the beginning and end of the entire motion.
        animateBounds(target, expandedAtEnd = false, durationMs = 260L) {
            finishHidingPanel(target)
            onHidden()
        }
    }

    private fun finishHidingPanel(settledBounds: CardBounds? = null) {
        closing = false
        panel?.alpha = 1f
        panel?.let { content?.removeView(it) }
        panel = null
        panelBodyVisible = true
        panelArea = null
        content?.apply { background = null; elevation = 0f; clipToOutline = false }
        cardBackground.alpha = backgroundAlpha
        controls?.setExpanded(false)
        expansionFraction = 0f
        targetBounds = null
        if (settledBounds == null) {
            layoutWindow(animate = false)
        } else {
            // Keep the exact animation endpoint; do not run a second positioning pass.
            params?.flags = windowFlags(expanded = false)
            settleBounds(settledBounds, forceWindowUpdate = true)
        }
    }

    fun update(state: OverlayUiState) {
        captureProtection?.setStealthEnabled(state.settings.overlayStealthEnabled)
        captureProtection?.setTemporaryExclusion(state.capturing)
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
        root?.visibility = if (state.isSelectingRegion() ||
            (state.capturing && (!captureExclusionApplied || !state.sheetVisible))) View.INVISIBLE else View.VISIBLE
    }

    fun prepareForCapture(): Boolean {
        val host = root ?: return false
        // Reapply to the current surface, which may have been replaced after rotation.
        captureProtection?.setTemporaryExclusion(true)
        captureExclusionApplied = captureProtection?.refresh() == true
        host.visibility = if (captureExclusionApplied) View.VISIBLE else View.INVISIBLE
        return captureExclusionApplied
    }

    fun destroy() {
        captureProtection?.close()
        captureProtection = null
        cancelBoundsAnimation()
        closing = false
        root?.let {
            if (it.viewTreeObserver.isAlive) it.viewTreeObserver.removeOnPreDrawListener(positionBeforeDraw)
            if (it.isAttachedToWindow) windowManager.removeViewImmediate(it)
        }
        root = null
        content = null
        controls = null
        panel = null
        params = null
        visualBounds = null
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
        val height = header.desiredHeight + if (expanded && panelBodyVisible) panelHeight + cardDp(8) else 0
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
        if (animate && expanded && host.visibility == View.VISIBLE && visualBounds != null && visualBounds != target) {
            animateBounds(target, expandedAtEnd = true,
                durationMs = if (visualBounds?.width != target.width || expansionFraction < 1f) 280L else 150L)
        } else {
            panel?.alpha = 1f
            updateExpansion(if (expanded && panelBodyVisible) 1f else 0f)
            settleBounds(target, forceWindowUpdate = flagsChanged)
        }
    }

    private fun animateBounds(
        target: CardBounds,
        expandedAtEnd: Boolean,
        durationMs: Long,
        onEnd: () -> Unit = {}
    ) {
        val from = visualBounds ?: target
        val startExpansion = expansionFraction
        val startBodyAlpha = panel?.alpha ?: 1f
        val revealing = expandedAtEnd && (startExpansion < 1f || startBodyAlpha < 1f)
        val endExpansion = if (expandedAtEnd && panelBodyVisible) 1f else 0f
        val motionBounds = motionWindowBounds(from, target)
        // Reserve both endpoints once. Animation only changes local render properties.
        sizeContent(maxOf(from.width, target.width), maxOf(from.height, target.height))
        updateWindowBounds(motionBounds, force = true)
        renderBounds(from)
        boundsAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = PathInterpolator(0.22f, 0f, 0.18f, 1f)
            addUpdateListener {
                val progress = it.animatedValue as Float
                val horizontal = if (revealing) (progress / .8f).coerceIn(0f, 1f) else progress
                val vertical = if (revealing) ((progress - .08f) / .92f).coerceIn(0f, 1f) else progress
                updateExpansion(startExpansion + (endExpansion - startExpansion) * horizontal)
                panel?.alpha = when {
                    !expandedAtEnd -> startBodyAlpha * (1f - progress / .55f).coerceIn(0f, 1f)
                    revealing -> startBodyAlpha + (1f - startBodyAlpha) * ((progress - .18f) / .72f).coerceIn(0f, 1f)
                    else -> 1f
                }
                renderBounds(interpolateWindowBounds(from, target, horizontal, vertical))
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    boundsAnimator = null
                    updateExpansion(endExpansion)
                    if (expandedAtEnd) panel?.alpha = 1f
                    if (expandedAtEnd) settleBounds(target)
                    onEnd()
                }
            })
            start()
        }
    }

    private fun updateExpansion(fraction: Float) {
        expansionFraction = fraction.coerceIn(0f, 1f)
        controls?.setExpansionProgress(expansionFraction)
        cardBackground.alpha = (backgroundAlpha * expansionFraction).roundToInt()
    }

    private fun sizeContent(width: Int, height: Int) {
        content?.let { body ->
            if (body.layoutParams.width != width || body.layoutParams.height != height) {
                body.layoutParams = FrameLayout.LayoutParams(width, height)
            }
        }
        controls?.let { header ->
            if (header.layoutParams.width != width || header.layoutParams.height != header.desiredHeight) {
                header.layoutParams = FrameLayout.LayoutParams(width, header.desiredHeight)
            }
        }
    }

    private fun renderBounds(bounds: CardBounds) {
        visualBounds = bounds
        positionContentOnScreen(bounds)
        content?.apply {
            clipRect.set(0, 0, bounds.width, bounds.height)
            clipBounds = clipRect
            invalidateOutline()
        }
        controls?.setVisualWidth(bounds.width)
    }

    private fun positionContentOnScreen(bounds: CardBounds) {
        val host = root ?: return
        val layout = params ?: return
        if (host.isLaidOut) host.getLocationOnScreen(actualWindowLocation)
        else {
            actualWindowLocation[0] = layout.x
            actualWindowLocation[1] = layout.y
        }
        // WindowManager relocation is asynchronous. Rebase against its actual frame
        // immediately before drawing, not the requested coordinates, so the last
        // animation frame and the compact window occupy exactly the same screen pixels.
        content?.translationX = (bounds.left - actualWindowLocation[0]).toFloat()
        content?.translationY = (bounds.top - actualWindowLocation[1]).toFloat()
    }

    private fun settleBounds(bounds: CardBounds, forceWindowUpdate: Boolean = false) {
        updateWindowBounds(bounds, forceWindowUpdate)
        sizeContent(bounds.width, bounds.height)
        renderBounds(bounds)
    }

    private fun updateWindowBounds(bounds: CardBounds, force: Boolean = false) {
        val host = root ?: return
        val layout = params ?: return
        if (!force && layout.x == bounds.left && layout.y == bounds.top &&
            layout.width == bounds.width && layout.height == bounds.height) return
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
