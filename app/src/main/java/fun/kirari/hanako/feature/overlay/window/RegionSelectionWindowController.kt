package `fun`.kirari.hanako.feature.overlay.window

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.doOnDetach
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import `fun`.kirari.hanako.core.ui.theme.HanakoTheme
import `fun`.kirari.hanako.feature.overlay.ui.AnswerBubbleTheme
import `fun`.kirari.hanako.feature.overlay.ui.RegionSelectionScreen

/** Temporary full-screen selection; the answer still lives in the original floating window. */
internal class RegionSelectionWindowController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val lifecycleOwner: LifecycleOwner,
    private val viewModelStoreOwner: ViewModelStoreOwner,
    private val savedStateRegistryOwner: SavedStateRegistryOwner,
    private val stealthEnabled: () -> Boolean,
    private val onCancel: () -> Unit,
    private val onConfirm: (Bitmap) -> Unit
) {
    private var root: FrameLayout? = null
    private var source: Bitmap? = null
    private var captureProtection: WindowCaptureExclusion? = null

    fun show(bitmap: Bitmap) {
        if (root != null && source === bitmap) {
            captureProtection?.setStealthEnabled(stealthEnabled())
            return
        }
        dismiss()
        val host = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    onCancel()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            setBackgroundColor(Color.WHITE)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(viewModelStoreOwner)
            setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
            addView(ComposeView(context).apply {
                setContent { HanakoTheme { AnswerBubbleTheme(true) { RegionSelectionScreen(bitmap, onCancel, onConfirm) } } }
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        ViewCompat.setOnApplyWindowInsetsListener(host) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        if (Build.VERSION.SDK_INT >= 33) {
            host.doOnAttach {
                val dispatcher = host.findOnBackInvokedDispatcher()
                val callback = android.window.OnBackInvokedCallback { onCancel() }
                dispatcher?.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
                host.doOnDetach { dispatcher?.unregisterOnBackInvokedCallback(callback) }
            }
        }
        root = host
        source = bitmap
        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, overlayWindowType(),
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
        }
        windowManager.addView(host, layout)
        captureProtection = WindowCaptureExclusion(host, stealthEnabled())
    }

    fun dismiss() {
        captureProtection?.close()
        captureProtection = null
        root?.let { if (it.isAttachedToWindow) windowManager.removeViewImmediate(it) }
        root = null
        source = null
    }
}
