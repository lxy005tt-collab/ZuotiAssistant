package `fun`.kirari.hanako.core.ui.richtext

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp


@Composable
internal fun LatexInline(
    latex: String,
    modifier: Modifier = Modifier
) {
    LatexText(
        latex = latex,
        modifier = modifier,
        fontSize = LocalTextStyle.current.fontSize,
        color = LocalContentColor.current
    )
}

@Composable
internal fun LatexBlock(
    latex: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 2.dp)
    ) {
        LatexText(
            latex = latex,
            fontSize = LocalTextStyle.current.fontSize,
            color = LocalContentColor.current
        )
    }
}

@Composable
internal fun LatexText(
    latex: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val resolvedStyle = style.merge(
        fontSize = fontSize,
        color = color
    )
    val resolvedFontSize = if (fontSize == TextUnit.Unspecified) LocalTextStyle.current.fontSize else fontSize
    val pixels = density.formulaPixels(resolvedFontSize)
    val textColor = resolvedStyle.color.takeIf { it != Color.Unspecified } ?: LocalContentColor.current
    val render by remember(latex, pixels, textColor) {
        FormulaRenderer.state(context = context, latex = latex, pixels = pixels, color = textColor.toArgb())
    }
    val drawable = render.drawable
    if (drawable == null && !render.failed) {
        Box(modifier.size(with(density) { pixels.toDp() }))
        return
    }

    if (drawable == null) {
        Text(
            text = processLatex(latex),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = modifier
        )
        return
    }

    with(density) {
        val widthDp = drawable.bounds.width().toDp()
        val heightDp = drawable.bounds.height().toDp()
        Canvas(
            modifier = modifier.size(
                width = widthDp,
                height = heightDp
            )
        ) {
            drawable.draw(drawContext.canvas.nativeCanvas)
        }
    }
}

internal fun assumeLatexSize(rendered: FormulaRender, fontSize: Float): Rect {
    return rendered.drawable?.bounds ?: if (rendered.failed) Rect() else
        Rect(0, 0, fontSize.toInt().coerceAtLeast(1), fontSize.toInt().coerceAtLeast(1))
}

internal fun Density.formulaPixels(size: TextUnit): Float =
    if (size.type == TextUnitType.Sp) size.toPx() else 14.sp.toPx()

private val inlineDollarRegex = Regex("""^\$(.*?)\$""", RegexOption.DOT_MATCHES_ALL)
private val displayDollarRegex = Regex("""^\$\$(.*?)\$\$""", RegexOption.DOT_MATCHES_ALL)
private val inlineParenRegex = Regex("""^\\\((.*?)\\\)""", RegexOption.DOT_MATCHES_ALL)
private val displayBracketRegex = Regex("""^\\\[(.*?)\\\]""", RegexOption.DOT_MATCHES_ALL)

internal fun processLatex(latex: String): String {
    val trimmed = latex.trim()
    val content = when {
        displayDollarRegex.matches(trimmed) -> displayDollarRegex.find(trimmed)?.groupValues?.get(1)?.trim().orEmpty()
        inlineDollarRegex.matches(trimmed) -> inlineDollarRegex.find(trimmed)?.groupValues?.get(1)?.trim().orEmpty()
        displayBracketRegex.matches(trimmed) -> displayBracketRegex.find(trimmed)?.groupValues?.get(1)?.trim().orEmpty()
        inlineParenRegex.matches(trimmed) -> inlineParenRegex.find(trimmed)?.groupValues?.get(1)?.trim().orEmpty()
        else -> trimmed
    }
    return normalizeLatexEnvironments(content)
}

private fun normalizeLatexEnvironments(latex: String): String {
    return latex
        .replace("""\begin{vmatrix}""", """\left|\begin{matrix}""")
        .replace("""\end{vmatrix}""", """\end{matrix}\right|""")
        .replace("""\begin{Vmatrix}""", """\left\|\begin{matrix}""")
        .replace("""\end{Vmatrix}""", """\end{matrix}\right\|""")
}
