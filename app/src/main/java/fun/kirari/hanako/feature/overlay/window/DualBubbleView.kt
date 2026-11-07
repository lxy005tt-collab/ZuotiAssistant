package `fun`.kirari.hanako.feature.overlay.window

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import `fun`.kirari.hanako.R
import kotlin.math.abs
import kotlin.math.roundToInt

/** Two real click targets: ACTION_UP is delivered immediately, without double-tap arbitration. */
internal class DualBubbleView(
    context: Context,
    scale: Float,
    onSettings: () -> Unit,
    onCapture: () -> Unit,
    private val onDragStart: () -> Unit,
    private val onDrag: (Int, Int) -> Unit,
    private val onConfigurationUpdate: () -> Unit
) : FrameLayout(context) {
    private var sizeScale = scale.coerceIn(.6f, 1f)
    private var backgroundOpacity = .94f
    private val density get() = resources.displayMetrics.density * sizeScale
    val diameter get() = (48 * density).roundToInt()
    val inset get() = (6 * density).roundToInt()
    val gap get() = (8 * density).roundToInt()
    val desiredWidth get() = diameter * 2 + gap + inset * 2
    val desiredHeight get() = diameter + inset * 2
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Path()
    private val lightTrail = Path()
    private val sparkle = Path()
    private val scanCorners = Path()
    private val leftCircle = RectF()
    private val rightCircle = RectF()
    private var captureGradient: LinearGradient? = null
    private val measure = PathMeasure()
    private val tip = FloatArray(2)
    private val bodyColor = ContextCompat.getColor(context, R.color.overlay_glass_fill)
    private val iconColor = ContextCompat.getColor(context, R.color.overlay_icon)
    private val blue = ContextCompat.getColor(context, R.color.overlay_orbit_blue)
    private val lightBlue = ContextCompat.getColor(context, R.color.overlay_orbit_light)
    private val settingsFill = Color.rgb(245, 245, 245)
    private var phase = 0f
    private var processing = false
    private var expanded = false
    private var expansionFraction = 0f
    private var visualWidth: Int? = null
    private val drawingWidth get() = visualWidth ?: width.takeIf { it > 0 } ?: desiredWidth
    private var settingsAngle = 0f
    private var settingsOpen = false
    private var settingsAnimator: ValueAnimator? = null
    // Visibility callbacks may run from the View constructor before field initialization.
    private val orbitAnimator: ValueAnimator? = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1400L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }
    private val settingsTarget = View(context).apply {
        contentDescription = "悬浮窗设置"
        isFocusable = true
        background = circleRipple(Color.argb(40, 0, 0, 0))
        setOnClickListener { onSettings() }
    }
    private val captureTarget = View(context).apply {
        contentDescription = "截取整屏并解题"
        isFocusable = true
        background = circleRipple(Color.argb(85, 255, 255, 255))
        setOnClickListener { onCapture() }
    }
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    init {
        setWillNotDraw(false)
        addView(settingsTarget, LayoutParams(diameter, diameter).apply { leftMargin = inset; topMargin = inset })
        addView(captureTarget, LayoutParams(diameter, diameter).apply {
            gravity = Gravity.TOP or Gravity.RIGHT
            rightMargin = inset
            topMargin = inset
        })
        rebuildGeometry(desiredWidth)
    }

    fun setExpanded(value: Boolean) {
        val fraction = if (value) 1f else 0f
        if (expanded == value && expansionFraction == fraction) return
        expanded = value
        expansionFraction = fraction
        rebuildGeometry(drawingWidth)
        invalidate()
    }

    fun updateAppearance(scale: Float, opacity: Float): Boolean {
        val newScale = scale.coerceIn(.6f, 1f)
        val resized = sizeScale != newScale
        backgroundOpacity = opacity.coerceIn(.6f, 1f)
        if (resized) {
            sizeScale = newScale
            settingsTarget.layoutParams = LayoutParams(diameter, diameter).apply {
                leftMargin = inset; topMargin = inset
            }
            captureTarget.layoutParams = LayoutParams(diameter, diameter).apply {
                gravity = Gravity.TOP or Gravity.RIGHT; rightMargin = inset; topMargin = inset
            }
            rebuildGeometry(drawingWidth)
        }
        invalidate()
        return resized
    }

    fun setExpansionProgress(progress: Float) {
        expansionFraction = progress.coerceIn(0f, 1f)
        invalidate()
    }

    /** Move the right control without remeasuring the window or the Compose answer. */
    fun setVisualWidth(value: Int) {
        if (visualWidth == value) return
        visualWidth = value
        captureTarget.translationX = (value - width).toFloat()
        rebuildGeometry(value)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        captureTarget.translationX = (drawingWidth - w).toFloat()
        rebuildGeometry(drawingWidth)
    }

    private fun rebuildGeometry(viewWidth: Int) {
        val left = RectF(inset.toFloat(), inset.toFloat(), (inset + diameter).toFloat(), (inset + diameter).toFloat())
        val right = RectF((viewWidth - inset - diameter).toFloat(), inset.toFloat(), (viewWidth - inset).toFloat(), (inset + diameter).toFloat())
        leftCircle.set(left)
        rightCircle.set(right)
        outline.rewind()
        outline.addRoundRect(RectF(left.left, left.top, right.right, right.bottom), diameter / 2f, diameter / 2f, Path.Direction.CW)
        measure.setPath(outline, true)
        val cx = right.centerX()
        val cy = right.centerY()
        captureGradient = LinearGradient(
            right.left, right.top, right.right, right.bottom,
            Color.rgb(23, 23, 23), Color.rgb(23, 23, 23), Shader.TileMode.CLAMP
        )
        val radius = 10f * density
        val arm = 4.5f * density
        scanCorners.apply {
            rewind()
            moveTo(cx - radius, cy - radius + arm)
            lineTo(cx - radius, cy - radius)
            lineTo(cx - radius + arm, cy - radius)
            moveTo(cx + radius - arm, cy - radius)
            lineTo(cx + radius, cy - radius)
            lineTo(cx + radius, cy - radius + arm)
            moveTo(cx + radius, cy + radius - arm)
            lineTo(cx + radius, cy + radius)
            lineTo(cx + radius - arm, cy + radius)
            moveTo(cx - radius + arm, cy + radius)
            lineTo(cx - radius, cy + radius)
            lineTo(cx - radius, cy + radius - arm)
        }
        sparkle.apply {
            rewind()
            moveTo(cx, cy - 5.5f * density)
            lineTo(cx + 1.7f * density, cy - 1.7f * density)
            lineTo(cx + 5.5f * density, cy)
            lineTo(cx + 1.7f * density, cy + 1.7f * density)
            lineTo(cx, cy + 5.5f * density)
            lineTo(cx - 1.7f * density, cy + 1.7f * density)
            lineTo(cx - 5.5f * density, cy)
            lineTo(cx - 1.7f * density, cy - 1.7f * density)
            close()
        }
    }

    fun update(busy: Boolean, menuOpen: Boolean) {
        captureTarget.isEnabled = !busy
        if (processing != busy) {
            processing = busy
            if (busy && isAttachedToWindow && visibility == VISIBLE) orbitAnimator?.start() else orbitAnimator?.cancel()
            invalidate()
        }
        if (settingsOpen != menuOpen) {
            settingsOpen = menuOpen
            settingsAnimator?.cancel()
            settingsAnimator = ValueAnimator.ofFloat(settingsAngle, if (menuOpen) 90f else 0f).apply {
                duration = 140L
                addUpdateListener { settingsAngle = it.animatedValue as Float; invalidate() }
                start()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.shader = null
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        paint.color = bodyColor
        if (expansionFraction < 1f) {
            paint.alpha = (255 * backgroundOpacity * (1f - expansionFraction)).roundToInt()
            canvas.drawPath(outline, paint)
        }
        if (expansionFraction > 0f) {
            paint.color = Color.argb((58 * expansionFraction).roundToInt(), 0, 0, 0)
            canvas.drawRoundRect(drawingWidth / 2f - 9 * density, desiredHeight / 2f - 1.5f * density,
                drawingWidth / 2f + 9 * density, desiredHeight / 2f + 1.5f * density, 2 * density, 2 * density, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * density
        paint.color = blue
        paint.alpha = if (processing) 120 else (74 * (1f - expansionFraction)).roundToInt()
        if (expansionFraction < 1f || processing) canvas.drawPath(outline, paint)
        if (processing) {
            lightTrail.rewind()
            val length = measure.length
            val end = phase * length
            val start = end - length * .24f
            if (start < 0) measure.getSegment(length + start, length, lightTrail, true)
            measure.getSegment(start.coerceAtLeast(0f), end, lightTrail, true)
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = blue
            paint.alpha = 35
            paint.strokeWidth = 4 * density
            canvas.drawPath(lightTrail, paint)
            paint.alpha = 255
            paint.strokeWidth = 2.4f * density
            canvas.drawPath(lightTrail, paint)
            measure.getPosTan(end, tip, null)
            paint.color = lightBlue
            paint.style = Paint.Style.FILL
            canvas.drawCircle(tip[0], tip[1], 2.4f * density, paint)
        }
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        paint.color = settingsFill
        val buttonRadius = diameter / 2f - 4 * density
        val cy = leftCircle.centerY()
        val leftCx = leftCircle.centerX()
        canvas.drawCircle(leftCx, cy, buttonRadius, paint)
        paint.shader = captureGradient
        canvas.drawCircle(rightCircle.centerX(), rightCircle.centerY(), buttonRadius, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.color = iconColor
        paint.strokeWidth = 1.8f * density
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        canvas.save()
        canvas.rotate(settingsAngle, leftCx, cy)
        for (index in 0 until 3) {
            val y = cy + (index - 1) * 7f * density
            val knobX = leftCx + (if (index == 1) 4f else -4f) * density
            canvas.drawLine(leftCx - 9f * density, y, leftCx + 9f * density, y, paint)
            paint.style = Paint.Style.FILL
            paint.color = settingsFill
            canvas.drawCircle(knobX, y, 2.6f * density, paint)
            paint.style = Paint.Style.STROKE
            paint.color = iconColor
            canvas.drawCircle(knobX, y, 2.6f * density, paint)
        }
        canvas.restore()
        paint.color = Color.WHITE
        canvas.drawPath(scanCorners, paint)
        paint.style = Paint.Style.FILL
        canvas.drawPath(sparkle, paint)
    }

    private fun circleRipple(color: Int): RippleDrawable = RippleDrawable(
        ColorStateList.valueOf(color), null,
        GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
    )

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            downX = event.rawX
            downY = event.rawY
            dragging = false
            onDragStart()
        } else if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            dragging = dragging || abs(event.rawX - downX) > slop || abs(event.rawY - downY) > slop
        }
        return dragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.rawX - downX) > slop || abs(event.rawY - downY) > slop) dragging = true
                if (dragging) onDrag((event.rawX - downX).roundToInt(), (event.rawY - downY).roundToInt())
            }
            MotionEvent.ACTION_UP -> { if (!dragging) performClick(); dragging = false }
            MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        post { onConfigurationUpdate() }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && processing && isAttachedToWindow) orbitAnimator?.start()
        else orbitAnimator?.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (processing && visibility == VISIBLE) orbitAnimator?.start()
    }

    override fun onDetachedFromWindow() {
        orbitAnimator?.cancel()
        settingsAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}
