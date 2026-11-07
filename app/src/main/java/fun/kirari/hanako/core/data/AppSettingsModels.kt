package `fun`.kirari.hanako.core.data

import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.core.model.ProcessingRoute
import `fun`.kirari.hanako.core.model.withStableAnswerVersionIds
import `fun`.kirari.llm.core.ProviderKind
import `fun`.kirari.llm.core.ReasoningEffort
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ModelProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "OpenAI Compatible",
    val kind: ProviderKind = ProviderKind.OPENAI_COMPATIBLE,
    val baseUrl: String = kind.defaultBaseUrl,
    val apiKey: String = "",
    val chatModel: String = "",
    val visionModel: String = "",
    val ocrModel: String = "",
    val favoriteModels: List<String> = emptyList(),
    val enabled: Boolean = true,
    @kotlinx.serialization.Transient val reasoningEffort: ReasoningEffort = ReasoningEffort.AUTO
)

@Serializable
enum class ModelPurpose {
    OCR,
    TEXT,
    VISION
}

@Serializable
data class ModelSelection(
    val providerId: String? = null,
    val model: String = ""
)

const val LOCAL_OCR_PROVIDER_ID = "__local_mlkit__"
const val LOCAL_OCR_MODEL_ID = "mlkit_chinese_ocr"
@Serializable
data class LocalOcrSettings(
    val installed: Boolean = false,
    val lastMessage: String? = null,
    val providerId: String = LOCAL_OCR_PROVIDER_ID,
    val modelId: String = LOCAL_OCR_MODEL_ID,
    val displayName: String = "ML Kit 中文 OCR"
)

val ProviderKind.displayName: String
    get() = when (this) {
        ProviderKind.OPENAI_COMPATIBLE -> "OpenAI Compatible"
        ProviderKind.OPENAI_RESPONSES -> "OpenAI Responses"
        ProviderKind.ANTHROPIC -> "Anthropic"
        ProviderKind.GOOGLE -> "Google Gemini"
    }

val ProviderKind.defaultBaseUrl: String
    get() = when (this) {
        ProviderKind.OPENAI_COMPATIBLE -> "https://api.openai.com/v1"
        ProviderKind.OPENAI_RESPONSES -> "https://api.openai.com/v1"
        ProviderKind.ANTHROPIC -> "https://api.anthropic.com/v1"
        ProviderKind.GOOGLE -> "https://generativelanguage.googleapis.com/v1beta"
    }

val ProviderKind.requestPathSuffix: String
    get() = when (this) {
        ProviderKind.OPENAI_COMPATIBLE -> "/chat/completions"
        ProviderKind.OPENAI_RESPONSES -> "/responses"
        ProviderKind.ANTHROPIC -> "/messages"
        ProviderKind.GOOGLE -> "/models"
    }

fun ModelProviderConfig.requestPreviewUrl(): String = "${baseUrl.trimEnd('/')}${kind.requestPathSuffix}"

fun creatableProviderKinds(): List<ProviderKind> = ProviderKind.entries.toList()

@Serializable
data class AssistantPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val ocrPrompt: String,
    val textPrompt: String,
    val visionPrompt: String,
    val titleSummary: TitleSummarySettings = TitleSummarySettings()
)

@Serializable
enum class ScreenCaptureMethod {
    MEDIA_PROJECTION,
    SHIZUKU_ADB
}

@Serializable
data class BubbleAppearanceSettings(
    val bubbleDiameterDp: Float = DEFAULT_BUBBLE_DIAMETER_DP,
    val spinnerDiameterDp: Float = DEFAULT_SPINNER_DIAMETER_DP,
    val letterTextSizeDp: Float = DEFAULT_BUBBLE_LETTER_TEXT_SIZE_DP,
    val letterOpacity: Float = DEFAULT_BUBBLE_LETTER_OPACITY,
    val overallOpacity: Float = DEFAULT_BUBBLE_OPACITY
)

const val DEFAULT_BUBBLE_DIAMETER_DP = 40f
const val DEFAULT_SPINNER_DIAMETER_DP = 50f
const val DEFAULT_BUBBLE_LETTER_TEXT_SIZE_DP = 28f
const val DEFAULT_BUBBLE_LETTER_OPACITY = 100f
const val DEFAULT_BUBBLE_OPACITY = 100f
const val MIN_BUBBLE_DIAMETER_DP = 0f
const val MAX_BUBBLE_DIAMETER_DP = 70f
const val MIN_SPINNER_DIAMETER_DP = 0f
const val MAX_SPINNER_DIAMETER_DP = 70f
const val MIN_BUBBLE_LETTER_TEXT_SIZE_DP = 8f
const val MAX_BUBBLE_LETTER_TEXT_SIZE_DP = 96f

@Serializable
data class AutomationSettings(
    val completionNotificationEnabled: Boolean = true,
    val autoModeTimeoutSeconds: Int = 30,
    val staticModeEnabled: Boolean = false,
    val staticIntraLetterGapMs: Int = 400,
    val staticInterLetterGapMs: Int = 1000,
    val bubbleMenuEnabled: Boolean = true,
    val bubbleAppearance: BubbleAppearanceSettings = BubbleAppearanceSettings(),
    val skipScreenshotEnabled: Boolean = false
)

@Serializable
enum class SearchProviderKind(val displayName: String) {
    TAVILY("Tavily"),
    BRAVE("Brave Search"),
    SERPER("Serper.dev"),
    CUSTOM("自定义 (Tavily 兼容)")
}

val SearchProviderKind.defaultBaseUrl: String
    get() = when (this) {
        SearchProviderKind.TAVILY -> "https://api.tavily.com/search"
        SearchProviderKind.BRAVE -> "https://api.search.brave.com/res/v1/web/search"
        SearchProviderKind.SERPER -> "https://google.serper.dev/search"
        SearchProviderKind.CUSTOM -> ""
    }

@Serializable
data class SearchProviderConfig(
    val kind: SearchProviderKind = SearchProviderKind.TAVILY,
    val baseUrl: String = kind.defaultBaseUrl,
    val apiKey: String = ""
)

@Serializable
data class WebSearchSettings(
    val enabled: Boolean = false,
    val provider: SearchProviderConfig = SearchProviderConfig(),
    val maxResults: Int = 3,
    val automationAlsoSearch: Boolean = false
)

@Serializable
data class AppSettings(
    val schemaVersion: Int = StorageSchema.CURRENT_APP_DATA_VERSION,
    val providers: List<ModelProviderConfig> = listOf(defaultProvider()),
    val selectedProviderId: String? = providers.firstOrNull()?.id,
    val assistants: List<AssistantPreset> = defaultAssistants(),
    val selectedAssistantId: String? = assistants.firstOrNull()?.id,
    val processingRoute: ProcessingRoute = ProcessingRoute.MULTIMODAL_DIRECT,
    val reasoningEffort: ReasoningEffort = ReasoningEffort.AUTO,
    val screenCaptureMethod: ScreenCaptureMethod = ScreenCaptureMethod.MEDIA_PROJECTION,
    val overlayStealthEnabled: Boolean = true,
    val automation: AutomationSettings = AutomationSettings(),
    val floatingWindow: FloatingWindowSettings = FloatingWindowSettings(),
    val trustAllHttpsCertificates: Boolean = false,
    val textModelSelection: ModelSelection = ModelSelection(),
    val visionModelSelection: ModelSelection = ModelSelection(),
    val ocrModelSelection: ModelSelection = ModelSelection(),
    val localOcr: LocalOcrSettings = LocalOcrSettings(),
    val webSearch: WebSearchSettings = WebSearchSettings(),
    val lastResult: ProcessingResult? = null,
    val history: List<ProcessingResult> = emptyList(),
    val historyGroups: List<HistoryGroup> = emptyList(),
    val historyMetadata: List<HistoryRecordMetadata> = emptyList()
)

fun defaultProvider(): ModelProviderConfig = ModelProviderConfig()

fun defaultAssistants(): List<AssistantPreset> = listOf(defaultAssistant())

fun defaultAssistant(): AssistantPreset = problemSolvingAssistantPreset()

private fun problemSolvingAssistantPreset(): AssistantPreset = AssistantPreset(
    name = "题目解答助手",
    ocrPrompt = "请准确提取图片中的题目、选项、公式和注释，尽量保持原有结构，不要解释。",
    textPrompt = "你是题目解答助手。请先识别题目内容，再给出解题思路、关键知识点和答案。",
    visionPrompt = "你是题目解答助手。请直接阅读图片中的题目内容，给出解题思路、关键知识点和答案。"
)

private fun legacyChatSummaryAssistantPreset(): AssistantPreset = AssistantPreset(
    name = "聊天记录总结助手",
    ocrPrompt = "请准确提取图片中的全部文字，按原有结构输出，不要解释。",
    textPrompt = "你是聊天记录总结助手。请提炼重点、待办、情绪倾向，并用简洁中文输出。",
    visionPrompt = "你是聊天记录总结助手。请直接阅读图片内容，提炼重点、待办、情绪倾向，并用简洁中文输出。"
)

private fun legacyDefaultAssistants(): List<AssistantPreset> = listOf(
    legacyChatSummaryAssistantPreset(),
    problemSolvingAssistantPreset()
)

fun AssistantPreset.previewPrompt(): String {
    return textPrompt.ifBlank {
        visionPrompt.ifBlank {
            ocrPrompt
        }
    }
}

val ModelPurpose.displayName: String
    get() = when (this) {
        ModelPurpose.OCR -> "OCR"
        ModelPurpose.TEXT -> "文本"
        ModelPurpose.VISION -> "多模态"
    }

val ScreenCaptureMethod.displayName: String
    get() = when (this) {
        ScreenCaptureMethod.MEDIA_PROJECTION -> "系统屏幕录制"
        ScreenCaptureMethod.SHIZUKU_ADB -> "Shizuku + adb 截屏"
    }

val ScreenCaptureMethod.description: String
    get() = when (this) {
        ScreenCaptureMethod.MEDIA_PROJECTION -> "通过系统屏幕录制权限建立截图会话，兼容当前悬浮球流程。"
        ScreenCaptureMethod.SHIZUKU_ADB -> "通过 Shizuku 授权后调用 adb 截屏，避免每次启动都请求屏幕录制。"
    }

fun AppSettings.modelSelectionFor(purpose: ModelPurpose): ModelSelection = when (purpose) {
    ModelPurpose.OCR -> ocrModelSelection
    ModelPurpose.TEXT -> textModelSelection
    ModelPurpose.VISION -> visionModelSelection
}

fun AppSettings.availableProviders(): List<ModelProviderConfig> = providers

fun AppSettings.resolveModelProvider(purpose: ModelPurpose): ModelProviderConfig? {
    val selection = modelSelectionFor(purpose)
    if (purpose == ModelPurpose.OCR && selection.isLocalOcrSelection()) return null
    return availableProviders().firstOrNull { it.id == selection.providerId }
        ?.copy(reasoningEffort = if (purpose == ModelPurpose.OCR) ReasoningEffort.AUTO else reasoningEffort)
}

fun AppSettings.resolveModelName(purpose: ModelPurpose): String {
    val selection = modelSelectionFor(purpose)
    return if (purpose == ModelPurpose.OCR && selection.isLocalOcrSelection()) {
        localOcr.displayName
    } else {
        selection.model
    }
}

fun AppSettings.normalize(): AppSettings {
    val normalizedAssistants = normalizeAssistants(
        assistants = assistants,
        selectedAssistantId = selectedAssistantId
    )
    val availableProviders = providers
    val fallbackProvider = availableProviders.firstOrNull { it.id == selectedProviderId } ?: availableProviders.firstOrNull()
    val normalizedGroups = historyGroups
        .map { it.copy(name = it.name.normalizedHistoryGroupName()) }
        .filter { it.name.isNotBlank() }
        .distinctBy { it.name.lowercase() }
    val normalizedHistoryResults = history.map { it.withStableAnswerVersionIds() }
    val normalizedLastResult = lastResult?.withStableAnswerVersionIds()
    val canonicalHistory = (listOfNotNull(normalizedLastResult) + normalizedHistoryResults)
        .distinctBy { it.id }
    val normalizedHistory = copy(
        history = canonicalHistory,
        lastResult = canonicalHistory.firstOrNull(),
        historyGroups = normalizedGroups
    )
        .normalizedHistoryMetadata()
    return copy(
        schemaVersion = maxOf(schemaVersion, StorageSchema.CURRENT_APP_DATA_VERSION),
        automation = automation.normalize(),
        floatingWindow = floatingWindow.normalized(),
        selectedProviderId = selectedProviderId
            ?.takeIf { candidate -> availableProviders.any { it.id == candidate } }
            ?: fallbackProvider?.id,
        assistants = normalizedAssistants.assistants,
        selectedAssistantId = normalizedAssistants.selectedAssistantId,
        textModelSelection = textModelSelection.normalize(availableProviders),
        visionModelSelection = visionModelSelection.normalize(availableProviders),
        ocrModelSelection = ocrModelSelection.normalize(availableProviders, allowLocalOcr = true),
        lastResult = canonicalHistory.firstOrNull(),
        history = canonicalHistory,
        historyGroups = normalizedGroups,
        historyMetadata = normalizedHistory
    )
}

private fun AutomationSettings.normalize(): AutomationSettings {
    return copy(
        autoModeTimeoutSeconds = autoModeTimeoutSeconds.coerceAtLeast(1),
        bubbleAppearance = bubbleAppearance.normalize()
    )
}

private fun BubbleAppearanceSettings.normalize(): BubbleAppearanceSettings {
    return copy(
        bubbleDiameterDp = bubbleDiameterDp.coerceIn(MIN_BUBBLE_DIAMETER_DP, MAX_BUBBLE_DIAMETER_DP),
        spinnerDiameterDp = spinnerDiameterDp.coerceIn(MIN_SPINNER_DIAMETER_DP, MAX_SPINNER_DIAMETER_DP),
        letterTextSizeDp = letterTextSizeDp.coerceIn(
            MIN_BUBBLE_LETTER_TEXT_SIZE_DP,
            MAX_BUBBLE_LETTER_TEXT_SIZE_DP
        ),
        letterOpacity = letterOpacity.coerceIn(0f, 100f),
        overallOpacity = overallOpacity.coerceIn(0f, 100f)
    )
}

private data class NormalizedAssistants(
    val assistants: List<AssistantPreset>,
    val selectedAssistantId: String?
)

private fun normalizeAssistants(
    assistants: List<AssistantPreset>,
    selectedAssistantId: String?
): NormalizedAssistants {
    val normalizedAssistants = when {
        assistants.isEmpty() -> defaultAssistants()
        assistants.matchesLegacyDefaultAssistants() -> defaultAssistants()
        else -> assistants
    }
    val normalizedSelectedAssistantId = normalizedAssistants.firstOrNull { it.id == selectedAssistantId }?.id
        ?: normalizedAssistants.firstOrNull()?.id
    return NormalizedAssistants(
        assistants = normalizedAssistants,
        selectedAssistantId = normalizedSelectedAssistantId
    )
}

private fun List<AssistantPreset>.matchesLegacyDefaultAssistants(): Boolean {
    val legacyDefaults = legacyDefaultAssistants()
    return size == legacyDefaults.size && zip(legacyDefaults).all { (current, legacy) ->
        current.samePromptProfileAs(legacy)
    }
}

private fun AssistantPreset.samePromptProfileAs(other: AssistantPreset): Boolean {
    return name == other.name &&
        ocrPrompt == other.ocrPrompt &&
        textPrompt == other.textPrompt &&
        visionPrompt == other.visionPrompt
}

private fun ModelSelection.normalize(
    providers: List<ModelProviderConfig>,
    allowLocalOcr: Boolean = false
): ModelSelection {
    if (allowLocalOcr && isLocalOcrSelection()) {
        return copy(
            providerId = LOCAL_OCR_PROVIDER_ID,
            model = model.ifBlank { LOCAL_OCR_MODEL_ID }
        )
    }
    // Empty is an intentional choice. Never refill it or redirect a removed provider's
    // model to another service, including while loading older settings.
    return if (providers.any { it.id == providerId } && model.isNotBlank()) {
        copy(model = model.trim())
    } else ModelSelection()
}

fun ModelSelection.isLocalOcrSelection(): Boolean = providerId == LOCAL_OCR_PROVIDER_ID
