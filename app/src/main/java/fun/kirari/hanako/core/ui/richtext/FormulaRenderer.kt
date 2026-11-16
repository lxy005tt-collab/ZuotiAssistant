package `fun`.kirari.hanako.core.ui.richtext

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.RenderProcessGoneDetail
import androidx.compose.runtime.State
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.json.JSONTokener
import ru.noties.jlatexmath.JLatexMathDrawable
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume

internal data class FormulaRender(val drawable: Drawable? = null, val failed: Boolean = false)
private data class FormulaKey(val source: String, val pixels: Float, val color: Int, val display: Boolean)

/** Parse once off the UI thread; measurement and painting share the same cached result. */
internal object FormulaRenderer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val parseMutex = Mutex() // JLaTeXMath has shared parser/font state.
    private val cache = LinkedHashMap<FormulaKey, MutableState<FormulaRender>>(32, .75f, true)
    private val pending = mutableSetOf<FormulaKey>()

    fun state(context: Context, latex: String, pixels: Float, color: Int): State<FormulaRender> {
        val key = FormulaKey(processLatex(latex), pixels.coerceAtLeast(1f), color,
            latex.trimStart().let { it.startsWith("$$") || it.startsWith("\\[") })
        cache[key]?.let { return it }
        val state = mutableStateOf(FormulaRender())
        cache[key] = state
        pending.add(key)
        val appContext = context.applicationContext
        scope.launch {
            // Yield before starting work when requested during Compose measurement.
            delay(1)
            val result = runCatching {
                require(key.source.length <= 16384 && latexNestingWithinLimit(key.source))
                val native = if (requiresExtendedLatex(key.source)) null else withContext(Dispatchers.Default) {
                    parseMutex.withLock {
                        runCatching {
                            JLatexMathDrawable.builder(key.source).textSize(key.pixels)
                                .color(key.color).padding(0).align(JLatexMathDrawable.ALIGN_LEFT).build()
                        }.getOrNull()
                    }
                }
                native ?: MathJaxEngine.render(appContext, key.source, key.pixels, key.color, key.display)
            }.getOrNull()
            pending.remove(key)
            state.value = FormulaRender(result, failed = result == null)
            // Visible formulas retain their State even if evicted, so a long conversation
            // cannot trigger a render/evict/re-render loop or recompose every other formula.
            while (cache.size > 256) {
                val expired = cache.keys.firstOrNull { it !in pending } ?: break
                cache.remove(expired)
            }
        }
        return state
    }
}

/** One invisible JS engine, only for formulas the fast native renderer cannot handle. */
private object MathJaxEngine {
    private class UnsupportedFormula(message: String) : Exception(message)
    private val mutex = Mutex()
    private var webView: WebView? = null
    private var loaded = CompletableDeferred<Unit>()

    suspend fun render(context: Context, source: String, pixels: Float, color: Int, display: Boolean): Drawable = mutex.withLock {
        try {
            withTimeout(12_000) {
                val view = webView ?: create(context).also { webView = it }
                loaded.await()
                while (view.evaluate("Boolean(window.MathJax && MathJax.startup && MathJax.startup.document)") != "true") delay(30)
                val rgb = "#%06x".format(color and 0xffffff)
                val encoded = view.evaluate("renderFormula(${JSONObject.quote(source)},$pixels,${JSONObject.quote(rgb)},$display)")
                val response = JSONObject(JSONTokener(encoded).nextValue() as String)
                if (response.has("error")) throw UnsupportedFormula(response.optString("error"))
                val xml = response.getString("svg")
                require(xml.length <= 4_000_000)
                val width = response.getInt("width")
                val height = response.getInt("height")
                withContext(Dispatchers.Default) {
                    PictureDrawable(SVG.getFromString(xml).renderToPicture(width, height)).apply {
                        setBounds(0, 0, width, height)
                    }
                }
            }
        } catch (error: Exception) {
            if (error is UnsupportedFormula) throw error
            // A missing/broken system WebView must leave readable source, never crash the app.
            webView?.destroy()
            webView = null
            throw error
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(context: Context): WebView {
        loaded = CompletableDeferred()
        return WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.blockNetworkLoads = true
            settings.domStorageEnabled = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                    val stream = if (request.url.toString() == "https://math.zuoti.local/tex-svg-full.js")
                        context.assets.open("mathjax/tex-svg-full.js") else ByteArrayInputStream(ByteArray(0))
                    return WebResourceResponse("application/javascript", "UTF-8", stream)
                }
                override fun onPageFinished(view: WebView, url: String) { loaded.complete(Unit) }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    if (webView === view) webView = null
                    loaded.completeExceptionally(IllegalStateException("Formula renderer stopped"))
                    view.destroy()
                    return true
                }
            }
            val html = context.assets.open("mathjax/renderer.html").bufferedReader().use { it.readText() }
            loadDataWithBaseURL("https://math.zuoti.local/", html, "text/html", "UTF-8", null)
        }
    }

    private suspend fun WebView.evaluate(script: String): String = suspendCancellableCoroutine { continuation ->
        evaluateJavascript(script) { value -> if (continuation.isActive) continuation.resume(value) }
    }
}

internal fun latexNestingWithinLimit(source: String): Boolean {
    var depth = 0
    source.forEachIndexed { index, char ->
        if (index == 0 || source[index - 1] != '\\') {
            if (char == '{' && ++depth > 80) return false
            if (char == '}') depth--
        }
    }
    return true
}

private val extendedLatexCommands = Regex("""\\(?:ce|pu|qty|dv|pdv|dd|bra|ket|braket|expval|matrixel|bm|tag|newcommand|renewcommand|def|gdef|DeclareMathOperator|require)\b|\\begin\{(?:align\*?|alignedat|gather\*?|multline\*?|cases\*?|dcases|rcases|subarray)\}""")
internal fun requiresExtendedLatex(source: String): Boolean = source.length > 500 || extendedLatexCommands.containsMatchIn(source)
