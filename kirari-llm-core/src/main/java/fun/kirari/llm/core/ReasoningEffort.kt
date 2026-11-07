package `fun`.kirari.llm.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

@Serializable
enum class ReasoningEffort(val apiValue: String?) {
    AUTO(null), LOW("low"), MEDIUM("medium"), HIGH("high"), XHIGH("xhigh"), MAX("max")
}

fun supportedReasoningEfforts(kind: ProviderKind, model: String): List<ReasoningEffort> {
    val name = model.lowercase()
    val basic = listOf(ReasoningEffort.AUTO, ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH)
    return when (kind) {
        ProviderKind.OPENAI_COMPATIBLE, ProviderKind.OPENAI_RESPONSES -> ReasoningEffort.entries
        ProviderKind.ANTHROPIC -> when {
            listOf("claude-opus-5", "claude-sonnet-5", "claude-haiku-5-5", "claude-opus-4-7", "claude-opus-4-8", "claude-fable-5", "claude-mythos-5").any(name::contains) -> ReasoningEffort.entries
            listOf("claude-opus-4-6", "claude-sonnet-4-6", "claude-mythos-preview").any(name::contains) -> basic + ReasoningEffort.MAX
            name.contains("claude-opus-4-5") -> basic
            else -> listOf(ReasoningEffort.AUTO)
        }
        ProviderKind.GOOGLE -> when {
            name.contains("gemini-3-pro") -> listOf(ReasoningEffort.AUTO, ReasoningEffort.LOW, ReasoningEffort.HIGH)
            name.contains("gemini-3.1-flash-lite-image") -> listOf(ReasoningEffort.AUTO, ReasoningEffort.HIGH)
            Regex("gemini-(2\\.5|3|4)").containsMatchIn(name) -> basic
            else -> listOf(ReasoningEffort.AUTO)
        }
    }
}

internal fun StreamRequest.effectiveReasoningEffort(): ReasoningEffort =
    reasoningEffort.takeIf { it in supportedReasoningEfforts(provider.kind, model) } ?: ReasoningEffort.AUTO

internal fun JsonObjectBuilder.putReasoning(request: StreamRequest) {
    val effort = request.effectiveReasoningEffort()
    val value = effort.apiValue ?: return
    when (request.provider.kind) {
        ProviderKind.OPENAI_COMPATIBLE -> put("reasoning_effort", value)
        ProviderKind.OPENAI_RESPONSES -> putJsonObject("reasoning") { put("effort", value) }
        ProviderKind.ANTHROPIC -> {
            putJsonObject("output_config") { put("effort", value) }
            if (!request.model.lowercase().contains("claude-opus-4-5")) {
                putJsonObject("thinking") { put("type", "adaptive") }
            }
            // Thinking shares the output budget with the final answer.
            put("max_tokens", 32768)
        }
        ProviderKind.GOOGLE -> putJsonObject("generationConfig") {
            putJsonObject("thinkingConfig") {
                if (request.model.lowercase().contains("gemini-2.5")) {
                    put("thinkingBudget", when (effort) {
                        ReasoningEffort.LOW -> 1024
                        ReasoningEffort.MEDIUM -> 8192
                        else -> 24576
                    })
                } else {
                    put("thinkingLevel", value.uppercase())
                }
            }
        }
    }
}
