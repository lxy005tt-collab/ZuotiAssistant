package `fun`.kirari.hanako.feature.overlay.window

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
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
import `fun`.kirari.hanako.feature.overlay.presentation.OverlayViewModel
import `fun`.kirari.hanako.feature.overlay.state.OverlaySheetMode
import `fun`.kirari.hanako.feature.overlay.ui.OverlayPanel
import kotlin.math.roundToInt

/** Expands content inside the controls' window; never creates a second overlay. */
internal class PanelWindowController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val lifecycleOwner: LifecycleOwner,
    private val viewModelStoreOwner: ViewModelStoreOwner,
    private val savedStateRegistryOwner: SavedStateRegistryOwner,
    private val viewModel: OverlayViewModel,
    private val floatingWindow: BubbleWindowController
) {
    private var panelView: FrameLayout? = null
    private var mode = OverlaySheetMode.HOME
    private var closing = false
    private var keyboardBottom = 0
    private var contentHeight = 0
    private var contentKey: String? = null

    fun showOrUpdate(mode: OverlaySheetMode) {
        this.mode = mode
        val state = viewModel.uiState.value
        val key = if (state.settingsMenuVisible) "settings" else "$mode:${state.result?.id}"
        if (key != contentKey) {
            contentKey = key
            contentHeight = 0
            if (state.settingsMenuVisible) hideKeyboard()
        }
        if (closing) {
            closing = false
        }
        if (panelView == null) panelView = createPanelRoot()
        reposition()
    }

    fun reposition() {
        val root = panelView ?: return
        if (closing) return
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()
        fun cardDp(value: Int) = (value * density * viewModel.uiState.value.settings.floatingWindow.sizeScale).roundToInt()
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        var left = dp(8)
        var top = dp(32)
        var right = metrics.widthPixels - dp(8)
        var bottom = metrics.heightPixels - maxOf(dp(24), keyboardBottom) - dp(8)
        if (Build.VERSION.SDK_INT >= 30) {
            val current = windowManager.currentWindowMetrics
            val insets = current.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            left = current.bounds.left + insets.left + dp(8)
            top = current.bounds.top + insets.top + dp(8)
            right = current.bounds.right - insets.right - dp(8)
            bottom = current.bounds.bottom - maxOf(insets.bottom, keyboardBottom) - dp(8)
        }
        val safeArea = CardBounds(left, top, right.coerceAtLeast(left + 1), bottom.coerceAtLeast(top + 1))
        val state = viewModel.uiState.value
        val compact = mode == OverlaySheetMode.RESULT || state.settingsMenuVisible
        val expanded = !compact && state.cardExpanded
        val desiredHeight = when {
            state.settingsMenuVisible -> contentHeight.takeIf { it > 0 } ?: cardDp(80)
            mode == OverlaySheetMode.RESULT -> contentHeight.takeIf { it > 0 } ?: cardDp(36)
            expanded -> cardDp(560)
            mode == OverlaySheetMode.HOME -> cardDp(260)
            mode == OverlaySheetMode.CROP -> cardDp(440)
            else -> cardDp(380)
        }
        // Scale the card's footprint, while measured content keeps its readable text size.
        val maxHeight = minOf(safeArea.height, cardDp(if (compact) 420 else 620)) - floatingWindow.headerHeight - cardDp(8)
        floatingWindow.showPanel(root,
            width = cardDp(if (compact) 280 else if (expanded) 384 else 320),
            height = desiredHeight.coerceIn(1, maxHeight.coerceAtLeast(1)),
            safeArea = safeArea)
    }

    fun hideWithAnimation() {
        val root = panelView ?: return
        if (closing) return
        closing = true
        hideKeyboard()
        floatingWindow.hidePanel(animate = true) {
            if (panelView === root && closing) clearPanelState()
        }
    }

    fun dismiss() {
        hideKeyboard()
        floatingWindow.hidePanel()
        clearPanelState()
    }

    fun prepareForCapture(keepVisible: Boolean) {
        if (keepVisible) hideKeyboard() else dismiss()
    }

    private fun clearPanelState() {
        panelView = null
        closing = false
        keyboardBottom = 0
        contentHeight = 0
        contentKey = null
    }

    private fun updateContentHeight(height: Int) {
        if (height <= 0 || contentHeight == height) return
        contentHeight = height
        panelView?.post { reposition() }
    }

    private fun createPanelRoot(): FrameLayout = object : FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                viewModel.handlePanelBack()
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        override fun onConfigurationChanged(newConfig: Configuration) {
            super.onConfigurationChanged(newConfig)
            post { reposition() }
        }
    }.apply {
        setViewTreeLifecycleOwner(lifecycleOwner)
        setViewTreeViewModelStoreOwner(viewModelStoreOwner)
        setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
        addView(ComposeView(context).apply {
            setContent {
                HanakoTheme {
                    OverlayPanel(viewModel, onDismiss = viewModel::closeSheet,
                        onContentHeightChanged = ::updateContentHeight)
                }
            }
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val ime = if (insets.isVisible(WindowInsetsCompat.Type.ime())) {
                maxOf(keyboardBottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
            } else 0
            if (keyboardBottom != ime) { keyboardBottom = ime; view.post { reposition() } }
            insets
        }
        if (Build.VERSION.SDK_INT >= 33) {
            doOnAttach {
                val dispatcher = findOnBackInvokedDispatcher()
                val callback = android.window.OnBackInvokedCallback { viewModel.handlePanelBack() }
                dispatcher?.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
                doOnDetach { dispatcher?.unregisterOnBackInvokedCallback(callback) }
            }
        }
    }

    private fun hideKeyboard() {
        val token = panelView?.windowToken ?: return
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(token, 0)
        panelView?.clearFocus()
    }
}
