package `fun`.kirari.hanako.solve.workflow

import android.util.Base64
import `fun`.kirari.hanako.core.model.loadHistoryBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal interface ConversationAttachments {
    suspend fun encode(paths: List<String>, legacyBase64: String? = null): List<String>
    suspend fun recognize(paths: List<String>, models: ProcessingPipeline.ResolvedModels): String
}

internal class StoredConversationAttachments(private val ocrNode: OcrNode) : ConversationAttachments {
    override suspend fun encode(paths: List<String>, legacyBase64: String?): List<String> = withContext(Dispatchers.IO) {
        if (paths.isEmpty()) return@withContext listOfNotNull(legacyBase64?.takeIf { it.isNotBlank() })
        paths.map { path ->
            val file = File(path)
            require(file.isFile && file.length() > 0) { "对话中的截图已丢失，请点击「新题」后重新截图" }
            Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        }
    }

    override suspend fun recognize(paths: List<String>, models: ProcessingPipeline.ResolvedModels): String {
        val bitmaps = withContext(Dispatchers.IO) {
            val loaded = mutableListOf<android.graphics.Bitmap>()
            try {
                paths.forEach { path ->
                    loaded += requireNotNull(path.loadHistoryBitmap()) { "补充截图已丢失，请重新截图" }
                }
                loaded
            } catch (error: Exception) {
                loaded.forEach { it.recycle() }
                throw error
            }
        }
        return try {
            ocrNode.run(
                OcrNodeInput(CapturedImages("conversation", bitmaps, paths), models),
                WorkflowContext("conversation_ocr")
            ).output.text.also { require(it.isNotBlank()) { "补充截图未识别到文字，请重试或选择更清楚的区域" } }
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }
}
