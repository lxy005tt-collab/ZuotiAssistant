package `fun`.kirari.llm.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.URI
import java.util.Locale

@Serializable
enum class ReasoningEffort(val apiValue: String?) {
    AUTO(null), NONE("none"), MINIMAL("minimal"), LOW("low"), MEDIUM("medium"),
    HIGH("high"), XHIGH("xhigh"), MAX("max"), ON(null)
}

private enum class ReasoningWire {
    NONE, EFFORT, THINKING_EFFORT, ENABLE_THINKING_EFFORT, THINKING_SWITCH,
    ENABLE_THINKING_BUDGET, CLAUDE_ADAPTIVE, CLAUDE_BUDGET, GEMINI_LEVEL, GEMINI_BUDGET
}

private data class ReasoningProfile(
    val wire: ReasoningWire = ReasoningWire.NONE,
    val levels: List<ReasoningEffort> = emptyList(),
    val highBudget: Int = 24576,
    val aliases: Map<ReasoningEffort, ReasoningEffort> = emptyMap(),
    val thinkingOnly: Boolean = false
) {
    val choices get() = listOf(ReasoningEffort.AUTO) + levels
    fun budget(effort: ReasoningEffort): Int? = when (wire) {
        ReasoningWire.CLAUDE_BUDGET, ReasoningWire.GEMINI_BUDGET, ReasoningWire.ENABLE_THINKING_BUDGET ->
            when (effort) {
                ReasoningEffort.NONE -> 0
                ReasoningEffort.LOW -> 1024
                ReasoningEffort.MEDIUM -> 8192
                ReasoningEffort.HIGH -> highBudget
                else -> null
            }
        else -> null
    }
}

private val basic = listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH)
private val deepSeekAliases = mapOf(ReasoningEffort.MINIMAL to ReasoningEffort.LOW,
    ReasoningEffort.MEDIUM to ReasoningEffort.HIGH, ReasoningEffort.XHIGH to ReasoningEffort.HIGH)
private val qwenAliases = mapOf(ReasoningEffort.MINIMAL to ReasoningEffort.LOW,
    ReasoningEffort.HIGH to ReasoningEffort.XHIGH, ReasoningEffort.MAX to ReasoningEffort.XHIGH)

private fun String.matchesModel(vararg ids: String): Boolean = ids.any { id ->
    this == id || startsWith("$id-")
}

private fun String.matchesClaude(vararg ids: String): Boolean = ids.any { id ->
    this == id || this == "$id-latest" || Regex("${Regex.escape(id)}-\\d{8}").matches(this)
}

private fun modelName(model: String): String = model.trim().substringAfterLast('/').lowercase(Locale.ROOT)
private fun isBailian(baseUrl: String): Boolean {
    val host = runCatching { URI(baseUrl).host?.lowercase(Locale.ROOT) }.getOrNull().orEmpty()
    return host.endsWith(".aliyuncs.com") && (host.startsWith("dashscope") || host.contains(".maas."))
}

/** Explicit, protocol-aware families. An unrecognized model sends no speculative parameters.
 * Source links and budget presets are maintained in docs/reasoning-models.md. */
private fun profile(kind: ProviderKind, model: String, baseUrl: String = ""): ReasoningProfile {
    val name = modelName(model)
    val off = listOf(ReasoningEffort.NONE)
    val max = listOf(ReasoningEffort.MAX)
    val extended = basic + ReasoningEffort.XHIGH + ReasoningEffort.MAX
    val sparse = listOf(ReasoningEffort.LOW, ReasoningEffort.HIGH, ReasoningEffort.MAX)
    when (kind) {
        ProviderKind.ANTHROPIC -> {
            val claude = name.replace(Regex("(?<=\\d)\\.(?=\\d)"), "-")
            return when {
                claude.matchesClaude("claude-opus-5", "claude-opus-5-5", "claude-sonnet-5", "claude-sonnet-5-5", "claude-haiku-5-5",
                    "claude-opus-4-7", "claude-opus-4-8", "claude-fable-5", "claude-fable-5-1", "claude-mythos-5", "claude-mythos-5-1") ->
                    ReasoningProfile(ReasoningWire.CLAUDE_ADAPTIVE, extended)
                claude.matchesClaude("claude-opus-4-6", "claude-sonnet-4-6", "claude-mythos-preview") ->
                    ReasoningProfile(ReasoningWire.CLAUDE_ADAPTIVE, basic + max)
                claude.matchesClaude("claude-opus-4-5", "claude-sonnet-4-5", "claude-haiku-4-5",
                    "claude-sonnet-4", "claude-opus-4", "claude-opus-4-1", "claude-3-7-sonnet") ->
                    ReasoningProfile(ReasoningWire.CLAUDE_BUDGET, off + basic)
                else -> ReasoningProfile()
            }
        }
        ProviderKind.GOOGLE -> return when {
            name.matchesModel("gemini-2.5-pro") -> ReasoningProfile(ReasoningWire.GEMINI_BUDGET, basic, highBudget = 32768)
            name.matchesModel("gemini-2.5-flash") -> ReasoningProfile(ReasoningWire.GEMINI_BUDGET, off + basic)
            name.matchesModel("gemini-3-pro-image") -> ReasoningProfile(ReasoningWire.GEMINI_LEVEL, listOf(ReasoningEffort.HIGH))
            name.matchesModel("gemini-3.1-flash-lite-image", "gemini-3.1-flash-image") ->
                ReasoningProfile(ReasoningWire.GEMINI_LEVEL, listOf(ReasoningEffort.MINIMAL, ReasoningEffort.HIGH))
            name.matchesModel("gemini-3-pro") -> ReasoningProfile(ReasoningWire.GEMINI_LEVEL, listOf(ReasoningEffort.LOW, ReasoningEffort.HIGH))
            name.matchesModel("gemini-3.1-pro", "gemini-3.7-flash", "gemini-3.8-flash", "gemini-4-argon") ->
                ReasoningProfile(ReasoningWire.GEMINI_LEVEL, basic)
            name.matchesModel("gemini-3-flash", "gemini-3.1-flash-lite", "gemini-3.5-flash", "gemini-3.6-flash") ->
                ReasoningProfile(ReasoningWire.GEMINI_LEVEL, listOf(ReasoningEffort.MINIMAL) + basic)
            else -> ReasoningProfile()
        }
        else -> Unit
    }

    // The standard OpenAI effort field is not a universal contract for compatible APIs.
    if (name.startsWith("gpt-") && (name.contains("-chat") || name.contains("-codex") || name.contains("-pro"))) {
        return ReasoningProfile()
    }
    val openAi = when {
        name.matchesModel("gpt-6-astra", "gpt-6.1-sol") -> extended
        name.matchesModel("gpt-6-sol", "gpt-6-luna", "gpt-6.1-terra") -> off + extended
        name.matchesModel("gpt-5.2", "gpt-5.4", "gpt-5.5") -> off + basic + ReasoningEffort.XHIGH
        name.matchesModel("gpt-5.1") -> off + basic
        Regex("gpt-5(?:-mini|-nano)?(?:-\\d{4}-\\d{2}-\\d{2})?").matches(name) -> listOf(ReasoningEffort.MINIMAL) + basic
        name.matchesModel("o1", "o3", "o4-mini") && !name.contains("preview") && !name.contains("-pro") && !name.startsWith("o1-mini") -> basic
        else -> emptyList()
    }
    if (openAi.isNotEmpty()) return ReasoningProfile(ReasoningWire.EFFORT, openAi)

    val bailian = isBailian(baseUrl)
    if (name.matchesModel("deepseek-v4", "deepseek-v4.1", "deepseek-flash")) {
        val wire = if (bailian) ReasoningWire.ENABLE_THINKING_EFFORT else ReasoningWire.THINKING_EFFORT
        if (bailian && name in listOf("deepseek-v4-pro", "deepseek-v4-flash")) {
            return ReasoningProfile(wire, off + listOf(ReasoningEffort.HIGH, ReasoningEffort.MAX), aliases = mapOf(
                ReasoningEffort.LOW to ReasoningEffort.HIGH, ReasoningEffort.MEDIUM to ReasoningEffort.HIGH,
                ReasoningEffort.XHIGH to ReasoningEffort.MAX))
        }
        return ReasoningProfile(wire, off + sparse, aliases = deepSeekAliases)
    }
    if (name.matchesModel("qwen3.8-max", "qwen3.8-flash", "qwen3.8-omni-flash", "qwen3.8-2.4t-a95b", "qwen3.8-27b")) {
        return ReasoningProfile(ReasoningWire.EFFORT,
            off + listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.XHIGH), aliases = qwenAliases)
    }
    // Other provider families below are documented for Chat Completions, not Responses.
    if (kind != ProviderKind.OPENAI_COMPATIBLE) return ReasoningProfile()
    return when {
        name.matchesModel("glm-5.3") -> ReasoningProfile(ReasoningWire.EFFORT, sparse)
        name.matchesModel("glm-5.2") -> ReasoningProfile(if (bailian) ReasoningWire.ENABLE_THINKING_EFFORT else ReasoningWire.EFFORT,
            off + listOf(ReasoningEffort.HIGH, ReasoningEffort.MAX), aliases = mapOf(
                ReasoningEffort.MINIMAL to ReasoningEffort.NONE, ReasoningEffort.LOW to ReasoningEffort.HIGH,
                ReasoningEffort.MEDIUM to ReasoningEffort.HIGH, ReasoningEffort.XHIGH to ReasoningEffort.MAX))
        bailian && name.matchesModel("glm-5", "glm-5.1") ->
            ReasoningProfile(ReasoningWire.ENABLE_THINKING_EFFORT, off + listOf(ReasoningEffort.HIGH, ReasoningEffort.MAX),
                aliases = mapOf(ReasoningEffort.LOW to ReasoningEffort.HIGH, ReasoningEffort.MEDIUM to ReasoningEffort.HIGH,
                    ReasoningEffort.XHIGH to ReasoningEffort.MAX))
        name.matchesModel("kimi-k3", "kimi-k3.1-pro") -> when {
            bailian && model.lowercase(Locale.ROOT).startsWith("kimi/") -> ReasoningProfile(ReasoningWire.EFFORT, max)
            bailian && name.matchesModel("kimi-k3.1-pro") -> ReasoningProfile(ReasoningWire.EFFORT, basic + max)
            bailian -> ReasoningProfile(ReasoningWire.ENABLE_THINKING_EFFORT, off + sparse)
            else -> ReasoningProfile(ReasoningWire.EFFORT, sparse)
        }
        name.matchesModel("glm-4.6", "glm-4.7", "glm-5", "glm-5.1", "kimi-k2.5", "kimi-k2.6", "deepseek-v3.1", "deepseek-v3.2") ->
            ReasoningProfile(if (bailian) ReasoningWire.ENABLE_THINKING_EFFORT else ReasoningWire.THINKING_SWITCH,
                off + ReasoningEffort.ON)
        name.matchesModel("qwen3", "qwen3.5", "qwen3.6", "qwen3.7") &&
            !name.contains("coder") && !name.contains("instruct") && !name.contains("omni") -> {
            val thinkingOnly = name.contains("thinking")
            ReasoningProfile(ReasoningWire.ENABLE_THINKING_BUDGET,
                (if (thinkingOnly) emptyList() else off) + basic, thinkingOnly = thinkingOnly)
        }
        else -> ReasoningProfile()
    }
}

fun supportedReasoningEfforts(kind: ProviderKind, model: String, baseUrl: String = ""): List<ReasoningEffort> =
    profile(kind, model, baseUrl).choices

fun reasoningTokenBudget(kind: ProviderKind, model: String, effort: ReasoningEffort, baseUrl: String = ""): Int? =
    profile(kind, model, baseUrl).budget(effort)

fun effectiveReasoningEffort(kind: ProviderKind, model: String, effort: ReasoningEffort, baseUrl: String = ""): ReasoningEffort {
    val profile = profile(kind, model, baseUrl)
    val mapped = profile.aliases[effort] ?: effort
    return mapped.takeIf { it in profile.choices } ?: ReasoningEffort.AUTO
}

internal fun StreamRequest.effectiveReasoningEffort(): ReasoningEffort =
    effectiveReasoningEffort(provider.kind, model, reasoningEffort, provider.baseUrl)

internal fun StreamRequest.supportsEncryptedReasoning(): Boolean {
    val name = modelName(model)
    return (name.startsWith("gpt-") || Regex("o[134](?:-.*)?").matches(name)) &&
        supportedReasoningEfforts(provider.kind, model, provider.baseUrl).size > 1
}

internal fun JsonObjectBuilder.putReasoning(request: StreamRequest) {
    val profile = profile(request.provider.kind, request.model, request.provider.baseUrl)
    val effort = request.effectiveReasoningEffort()
    if (effort == ReasoningEffort.AUTO) return
    val value = effort.apiValue
    val enabled = effort != ReasoningEffort.NONE
    // Responses uses a nested effort object, including compatible DeepSeek/Qwen services.
    if (request.provider.kind == ProviderKind.OPENAI_RESPONSES) {
        if (value != null) putJsonObject("reasoning") { put("effort", value) }
        return
    }
    when (profile.wire) {
        ReasoningWire.EFFORT -> if (value != null) put("reasoning_effort", value)
        ReasoningWire.THINKING_EFFORT, ReasoningWire.ENABLE_THINKING_EFFORT -> {
            if (profile.wire == ReasoningWire.ENABLE_THINKING_EFFORT) put("enable_thinking", enabled)
            else putJsonObject("thinking") { put("type", if (enabled) "enabled" else "disabled") }
            if (enabled && value != null) put("reasoning_effort", value)
        }
        ReasoningWire.THINKING_SWITCH -> putJsonObject("thinking") { put("type", if (enabled) "enabled" else "disabled") }
        ReasoningWire.ENABLE_THINKING_BUDGET -> {
            if (!profile.thinkingOnly) put("enable_thinking", enabled)
            if (enabled) put("thinking_budget", requireNotNull(profile.budget(effort)))
        }
        ReasoningWire.CLAUDE_ADAPTIVE -> {
            putJsonObject("output_config") { put("effort", value) }
            putJsonObject("thinking") { put("type", "adaptive") }
            put("max_tokens", 32768)
        }
        ReasoningWire.CLAUDE_BUDGET -> {
            putJsonObject("thinking") {
                put("type", if (enabled) "enabled" else "disabled")
                if (enabled) put("budget_tokens", requireNotNull(profile.budget(effort)))
            }
            if (enabled) {
                if (modelName(request.model).replace('.', '-').matchesModel("claude-opus-4-5")) {
                    putJsonObject("output_config") { put("effort", value) }
                }
                put("max_tokens", 32768)
            }
        }
        ReasoningWire.GEMINI_LEVEL, ReasoningWire.GEMINI_BUDGET -> putJsonObject("generationConfig") {
            putJsonObject("thinkingConfig") {
                if (profile.wire == ReasoningWire.GEMINI_BUDGET) put("thinkingBudget", requireNotNull(profile.budget(effort)))
                else put("thinkingLevel", requireNotNull(value).uppercase(Locale.ROOT))
            }
        }
        ReasoningWire.NONE -> Unit
    }
}
