package `fun`.kirari.hanako.core.ui.richtext

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import `fun`.kirari.hanako.core.model.RichTextBlockKind

internal val markdownFlavor by lazy {
    GFMFlavourDescriptor(makeHttpsAutoLinks = true, useSafeLinks = true)
}

internal val markdownParser by lazy {
    MarkdownParser(markdownFlavor)
}

private fun String.normalizeMarkdownNewlines(): String = replace("\r\n", "\n").replace('\r', '\n')

internal val inlineLatexRegex = Regex("""\\\((.+?)\\\)""", RegexOption.DOT_MATCHES_ALL)
internal val blockLatexRegex = Regex("""[ \t]*\\\[(.+?)\\\][ \t]*""", RegexOption.DOT_MATCHES_ALL)
internal val escapedDollarBlockDelimiterRegex = Regex("""(?m)^([ \t]*)\\\$\\\$[ \t]*$""")
internal val dollarBlockLatexRegex = Regex("""(?m)(^|[ \t]*\n)[ \t]*\$\$[ \t]*\n?([\s\S]+?)\n?[ \t]*\$\$[ \t]*(?=\n|$)""")
internal val codeBlockRegex = Regex("```[\\s\\S]*?```|`[^`\n]*`", RegexOption.DOT_MATCHES_ALL)
internal val breakLineRegex = Regex("(?i)<br\\s*/?>")
internal val copyMarkerRegex = Regex("""\[(?:copy|复制):((?:\\.|[^\]])*)\]""", RegexOption.DOT_MATCHES_ALL)

private fun codeRangesIn(content: String): List<IntRange> {
    val codeBlocks = mutableListOf<IntRange>()
    codeBlockRegex.findAll(content).forEach { match ->
        codeBlocks.add(match.range)
    }
    return codeBlocks
}

/** Copy the answer itself, retaining literal examples inside Markdown code blocks. */
internal fun unwrapCopyMarkers(content: String): String {
    val codeBlocks = codeRangesIn(content)
    return copyMarkerRegex.replace(content) { match ->
        if (codeBlocks.any { match.range.first in it }) match.value else match.groupValues[1]
    }
}

private fun replaceCopyMarkers(content: String): Pair<String, List<CopyMarkerToken>> {
    val codeBlocks = codeRangesIn(content)
    fun inCodeBlock(index: Int): Boolean = codeBlocks.any { index in it }

    val markers = mutableListOf<CopyMarkerToken>()
    val result = copyMarkerRegex.replace(content) { match ->
        if (inCodeBlock(match.range.first)) {
            match.value
        } else {
            val rawSource = match.groupValues[1]
            if (rawSource.isBlank()) {
                match.value
            } else {
                val placeholder = "HanakoCopyMarker${markers.size}"
                markers += CopyMarkerToken(placeholder = placeholder, rawSource = rawSource)
                placeholder
            }
        }
    }

    return result to markers
}

internal fun preprocessMarkdown(content: String, extractCopyMarkers: Boolean = true): Pair<String, List<CopyMarkerToken>> {
    val normalizedContent = normalizeMathFences(content.normalizeMarkdownNewlines())
    val (copyPreprocessed, copyMarkers) = if (extractCopyMarkers) {
        replaceCopyMarkers(normalizedContent)
    } else {
        normalizedContent to emptyList()
    }
    // Each replacement changes offsets. Recompute protected ranges for that pass.
    fun replaceOutsideCode(source: String, regex: Regex, replacement: (MatchResult) -> String): String {
        val ranges = codeRangesIn(source)
        return regex.replace(source) { match ->
            if (ranges.any { match.range.first in it }) match.value else replacement(match)
        }
    }

    var result = replaceOutsideCode(copyPreprocessed, escapedDollarBlockDelimiterRegex) { match ->
        "${match.groupValues[1]}${'$'}${'$'}"
    }
    result = replaceOutsideCode(result, inlineLatexRegex) { match -> "${'$'}${match.groupValues[1]}${'$'}" }
    result = replaceOutsideCode(result, blockLatexRegex) { match ->
        "\n${'$'}${'$'}\n${match.groupValues[1].trim()}\n${'$'}${'$'}\n"
    }
    result = replaceOutsideCode(result, dollarBlockLatexRegex) { match ->
        val prefix = if (match.groupValues[1].contains('\n')) "\n" else match.groupValues[1]
        "${prefix}${'$'}${'$'}\n${match.groupValues[2].trim()}\n${'$'}${'$'}"
    }
    val protected = codeRangesIn(result) + Regex("""\$\$[\s\S]*?\$\$|(?<!\\)\$[^\n]*?(?<!\\)\$""").findAll(result).map { it.range }.toList()
    result = bareMathEnvironment.replace(result) { match ->
        if (protected.any { match.range.first in it }) match.value else "\n$$\n${match.value}\n$$\n"
    }
    return result to copyMarkers
}

private val bareMathEnvironment = Regex("""\\begin\{(equation\*?|align\*?|aligned|alignedat|gather\*?|gathered|multline\*?|eqnarray\*?|cases|dcases|matrix|[pbBvV]matrix)\}[\s\S]*?\\end\{\1\}""")

private fun normalizeMathFences(source: String): String = Regex("(?m)^ {0,3}```(?:math|latex|tex)[ \\t]*\\n([\\s\\S]*?)\\n {0,3}```[ \\t]*$")
    .replace(source) { match -> "$$\n${match.groupValues[1].trim()}\n$$" }

internal data class CopyMarkerToken(
    val placeholder: String,
    val rawSource: String
) {
    @Deprecated("Use rawSource")
    val copyText: String get() = rawSource
}

internal data class MarkdownParseResult(
    val preprocessed: String,
    val astTree: ASTNode,
    val copyMarkers: List<CopyMarkerToken> = emptyList()
)

internal sealed class MarkdownRenderBlock {
    data class Markdown(val content: String) : MarkdownRenderBlock()
    data class DisplayMath(val latex: String) : MarkdownRenderBlock()
}

data class RichTextBlock(
    val kind: RichTextBlockKind,
    val rawMarkdown: String,
    val ordinal: Int
)

fun extractRichTextBlocks(content: String): List<RichTextBlock> {
    val (preprocessed, _) = preprocessMarkdown(content, extractCopyMarkers = false)
    val blocks = mutableListOf<RichTextBlock>()
    splitDisplayMathBlocks(preprocessed).forEach { renderBlock ->
        when (renderBlock) {
            is MarkdownRenderBlock.DisplayMath -> blocks += RichTextBlock(
                kind = RichTextBlockKind.DISPLAY_MATH,
                rawMarkdown = renderBlock.latex,
                ordinal = blocks.size
            )
            is MarkdownRenderBlock.Markdown -> {
                val tree = markdownParser.buildMarkdownTreeFromString(renderBlock.content)
                tree.children.forEach { child ->
                    if (child.getText(renderBlock.content).isNotBlank()) {
                        blocks += RichTextBlock(
                            kind = RichTextBlockKind.PARAGRAPH,
                            rawMarkdown = child.getText(renderBlock.content),
                            ordinal = blocks.size
                        )
                    }
                }
            }
        }
    }
    return blocks.ifEmpty {
        listOf(RichTextBlock(RichTextBlockKind.PARAGRAPH, content, 0))
    }
}

internal fun ASTNode.getText(text: String): String = text.substring(startOffset, endOffset)

internal fun ASTNode.findChildRecursive(vararg types: IElementType): ASTNode? {
    if (type in types) return this
    for (child in children) {
        child.findChildRecursive(*types)?.let { return it }
    }
    return null
}

internal fun parseMarkdown(content: String, extractCopyMarkers: Boolean = true): MarkdownParseResult {
    val (preprocessed, copyMarkers) = preprocessMarkdown(content, extractCopyMarkers)
    return MarkdownParseResult(
        preprocessed = preprocessed,
        astTree = markdownParser.buildMarkdownTreeFromString(preprocessed),
        copyMarkers = copyMarkers
    )
}

internal fun splitDisplayMathBlocks(content: String): List<MarkdownRenderBlock> {
    val blocks = mutableListOf<MarkdownRenderBlock>()
    val codeRanges = codeRangesIn(content)
    var cursor = 0
    fun nextDelimiter(from: Int): Int {
        var candidate = content.indexOf("$$", from)
        while (candidate >= 0) {
            var slash = candidate - 1
            while (slash >= 0 && content[slash] == '\\') slash--
            if ((candidate - slash - 1) % 2 == 0 && codeRanges.none { candidate in it }) return candidate
            candidate = content.indexOf("$$", candidate + 2)
        }
        return -1
    }
    while (cursor < content.length) {
        val start = nextDelimiter(cursor)
        if (start < 0) break
        val end = nextDelimiter(start + 2)
        if (end < 0) break
        content.substring(cursor, start).takeIf { it.isNotEmpty() }?.let { blocks += MarkdownRenderBlock.Markdown(it) }
        blocks += MarkdownRenderBlock.DisplayMath(content.substring(start, end + 2))
        cursor = end + 2
    }
    content.substring(cursor).takeIf { it.isNotEmpty() }?.let { blocks += MarkdownRenderBlock.Markdown(it) }
    return blocks.ifEmpty { listOf(MarkdownRenderBlock.Markdown(content)) }
}

internal fun extractCodeFenceContent(node: ASTNode, content: String): String {
    val startIndex = node.children.indexOfFirst { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
    if (startIndex == -1) return node.getText(content)
    val eolElement = node.children.subList(0, startIndex).findLast { it.type == MarkdownTokenTypes.EOL } ?: return node.getText(content)
    val startOffset = eolElement.endOffset
    val endOffset = node.children.findLast { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }?.endOffset ?: return node.getText(content)
    return content.substring(startOffset, endOffset).trimIndent()
}
