package `fun`.kirari.llm.core

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class ReasoningRequestTest {
    @Test
    fun automatic_keepsExistingWireContract() = runBlocking {
        for (kind in ProviderKind.entries) {
            val body = payload(kind, "unknown-model", ReasoningEffort.AUTO)
            assertFalse(body.containsKey("reasoning"))
            assertFalse(body.containsKey("reasoning_effort"))
            assertFalse(body.containsKey("thinking"))
            assertFalse(body.containsKey("generationConfig"))
        }
    }

    @Test
    fun effort_isSentThroughEachNativeProtocol() = runBlocking {
        assertEquals("high", payload(ProviderKind.OPENAI_COMPATIBLE, "gpt-5.4", ReasoningEffort.HIGH)["reasoning_effort"]?.jsonPrimitive?.content)
        val responses = payload(ProviderKind.OPENAI_RESPONSES, "gpt-5.4", ReasoningEffort.XHIGH)
        assertEquals("xhigh", responses["reasoning"]?.jsonObject?.get("effort")?.jsonPrimitive?.content)
        val claude = payload(ProviderKind.ANTHROPIC, "claude-opus-5-5", ReasoningEffort.HIGH)
        assertEquals("high", claude["output_config"]?.jsonObject?.get("effort")?.jsonPrimitive?.content)
        assertEquals("adaptive", claude["thinking"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
        val gemini = payload(ProviderKind.GOOGLE, "gemini-3-flash-preview", ReasoningEffort.MEDIUM)
            .getValue("generationConfig").jsonObject.getValue("thinkingConfig").jsonObject
        assertEquals("MEDIUM", gemini["thinkingLevel"]?.jsonPrimitive?.content)
        assertFalse(gemini.containsKey("thinkingBudget"))
        val older = payload(ProviderKind.GOOGLE, "gemini-2.5-pro", ReasoningEffort.MEDIUM)
            .getValue("generationConfig").jsonObject.getValue("thinkingConfig").jsonObject
        assertEquals(8192, older["thinkingBudget"]?.jsonPrimitive?.int)
        assertFalse(older.containsKey("thinkingLevel"))
    }

    @Test
    fun unsupportedModels_doNotReceiveForeignParameters() = runBlocking {
        val body = payload(ProviderKind.ANTHROPIC, "claude-3-haiku", ReasoningEffort.MAX)
        assertFalse(body.containsKey("thinking"))
        assertFalse(body.containsKey("output_config"))
        assertFalse(ReasoningEffort.MEDIUM in supportedReasoningEfforts(ProviderKind.GOOGLE, "gemini-3-pro-preview"))
    }

    @Test
    fun signedThinking_survivesSearchToolRoundTrip_withoutBecomingAnswerText() = runBlocking {
        val events = sse(
            "content_block_start" to """{"index":0,"content_block":{"type":"thinking","thinking":""}}""",
            "content_block_delta" to """{"index":0,"delta":{"type":"thinking_delta","thinking":"internal context"}}""",
            "content_block_delta" to """{"index":0,"delta":{"type":"signature_delta","signature":"signed-context"}}""",
            "content_block_start" to """{"index":1,"content_block":{"type":"tool_use","id":"call1","name":"web_search","input":{"query":"sample"}}}"""
        )
        val body = continuation(ProviderKind.ANTHROPIC, "claude-opus-5-5", events)
        val content = body.getValue("messages").jsonArray[0].jsonObject.getValue("content").jsonArray
        assertEquals("thinking", content[0].jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals("signed-context", content[0].jsonObject["signature"]?.jsonPrimitive?.content)
        assertEquals("tool_use", content[1].jsonObject["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun responses_encryptedReasoning_survivesSearchToolRoundTrip() = runBlocking {
        val events = sse(
            "response.output_item.done" to """{"item":{"type":"reasoning","id":"rs1","summary":[],"encrypted_content":"opaque-state"}}""",
            "response.output_item.done" to """{"item":{"type":"function_call","id":"fc1","call_id":"call1","name":"web_search","arguments":"{\"query\":\"sample\"}"}}"""
        )
        val body = continuation(ProviderKind.OPENAI_RESPONSES, "gpt-5.4", events)
        val input = body.getValue("input").jsonArray
        assertEquals("reasoning", input[0].jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals("opaque-state", input[0].jsonObject["encrypted_content"]?.jsonPrimitive?.content)
        assertTrue(input.any { it.jsonObject["type"]?.jsonPrimitive?.content == "function_call_output" })
        assertFalse(input.any { it.jsonObject["role"]?.jsonPrimitive?.content == "assistant" })
    }

    @Test
    fun gemini_thoughtSignature_andToolResult_surviveSearchRoundTrip() = runBlocking {
        val events = sse(null to """{"candidates":[{"content":{"parts":[{"functionCall":{"name":"web_search","args":{"query":"sample"}},"thoughtSignature":"opaque-signature"}]}}]}""")
        val body = continuation(ProviderKind.GOOGLE, "gemini-3-flash-preview", events)
        val contents = body.getValue("contents").jsonArray
        val call = contents[0].jsonObject.getValue("parts").jsonArray[0].jsonObject
        assertEquals("opaque-signature", call["thoughtSignature"]?.jsonPrimitive?.content)
        val response = contents[1].jsonObject.getValue("parts").jsonArray[0].jsonObject.getValue("functionResponse").jsonObject
        assertEquals("web_search", response["name"]?.jsonPrimitive?.content)
        assertEquals("search result", response.getValue("response").jsonObject["result"]?.jsonPrimitive?.content)
    }

    @Test
    fun chatThinkingContext_survivesSearchWithoutBeingDisplayedAsAnAnswer() = runBlocking {
        val events = sse(
            null to """{"choices":[{"delta":{"reasoning_content":"context "}}]}""",
            null to """{"choices":[{"delta":{"reasoning_content":"continued","tool_calls":[{"index":0,"id":"call1","function":{"name":"web_search","arguments":"{\"query\":\"sample\"}"}}]}}]}"""
        )
        val body = continuation(ProviderKind.OPENAI_COMPATIBLE, "deepseek-flash", events)
        val assistant = body.getValue("messages").jsonArray[0].jsonObject
        assertEquals("context continued", assistant["reasoning_content"]?.jsonPrimitive?.content)
        assertFalse(assistant.getValue("content").toString().contains("context continued"))
    }

    @Test
    fun deepSeekAndQwenUseTheirDocumentedMappingsAndNeverBothBudgetAndEffort() = runBlocking {
        val deep = payload(ProviderKind.OPENAI_COMPATIBLE, "deepseek-flash", ReasoningEffort.MEDIUM)
        assertEquals("high", deep["reasoning_effort"]?.jsonPrimitive?.content)
        assertEquals("enabled", deep["thinking"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
        val off = payload(ProviderKind.OPENAI_COMPATIBLE, "deepseek-v4-pro", ReasoningEffort.NONE)
        assertEquals("disabled", off["thinking"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
        assertFalse(off.containsKey("reasoning_effort"))
        val modern = payload(ProviderKind.OPENAI_COMPATIBLE, "qwen3.8-flash", ReasoningEffort.HIGH)
        assertEquals("xhigh", modern["reasoning_effort"]?.jsonPrimitive?.content)
        assertFalse(modern.containsKey("thinking_budget"))
        val older = payload(ProviderKind.OPENAI_COMPATIBLE, "qwen3.5-plus", ReasoningEffort.MEDIUM)
        assertEquals(true, older["enable_thinking"]?.jsonPrimitive?.boolean)
        assertEquals(8192, older["thinking_budget"]?.jsonPrimitive?.int)
        assertFalse(older.containsKey("reasoning_effort"))
        val responses = payload(ProviderKind.OPENAI_RESPONSES, "deepseek-flash", ReasoningEffort.MAX)
        assertEquals("max", responses["reasoning"]?.jsonObject?.get("effort")?.jsonPrimitive?.content)
        assertFalse(responses.containsKey("include"))
    }

    @Test
    fun switchOnlyModelsDoNotPretendToHaveLowMediumHigh() = runBlocking {
        for (model in listOf("kimi-k2.5", "glm-4.7")) {
            assertEquals(listOf(ReasoningEffort.AUTO, ReasoningEffort.NONE, ReasoningEffort.ON),
                supportedReasoningEfforts(ProviderKind.OPENAI_COMPATIBLE, model))
            val body = payload(ProviderKind.OPENAI_COMPATIBLE, model, ReasoningEffort.ON)
            assertEquals("enabled", body["thinking"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
            assertFalse(body.containsKey("reasoning_effort"))
        }
        val forced = payload(ProviderKind.OPENAI_COMPATIBLE, "glm-5.3", ReasoningEffort.NONE)
        assertFalse(forced.containsKey("thinking"))
        assertFalse(forced.containsKey("reasoning_effort"))
    }

    @Test
    fun oldClaudeUsesBudgetWhileNewClaudeUsesAdaptiveEffort() = runBlocking {
        val old = payload(ProviderKind.ANTHROPIC, "claude-opus-4-5", ReasoningEffort.MEDIUM)
        assertEquals("enabled", old["thinking"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
        assertEquals(8192, old["thinking"]?.jsonObject?.get("budget_tokens")?.jsonPrimitive?.int)
        assertTrue(old.getValue("max_tokens").jsonPrimitive.int > 8192)
        assertEquals("medium", old["output_config"]?.jsonObject?.get("effort")?.jsonPrimitive?.content)
        val current = payload(ProviderKind.ANTHROPIC, "anthropic/claude-opus-4.7", ReasoningEffort.MAX)
        assertEquals("adaptive", current["thinking"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
        assertFalse(current["thinking"]!!.jsonObject.containsKey("budget_tokens"))
    }

    @Test
    fun unknownOrNonReasoningModelsDoNotReceiveSpeculativeParameters() = runBlocking {
        for ((kind, model) in listOf(ProviderKind.OPENAI_COMPATIBLE to "custom-model",
            ProviderKind.OPENAI_COMPATIBLE to "gpt-4.1", ProviderKind.ANTHROPIC to "claude-opus-4-99",
            ProviderKind.GOOGLE to "gemini-unknown")) {
            val body = payload(kind, model, ReasoningEffort.HIGH)
            for (key in listOf("reasoning", "reasoning_effort", "thinking", "thinking_budget", "generationConfig")) {
                assertFalse("$model: $key", body.containsKey(key))
            }
        }
        assertFalse(ReasoningEffort.NONE in supportedReasoningEfforts(ProviderKind.OPENAI_RESPONSES, "gpt-6-astra"))
        assertFalse(ReasoningEffort.MAX in supportedReasoningEfforts(ProviderKind.OPENAI_COMPATIBLE, "gpt-5.4"))
        assertFalse(ReasoningEffort.XHIGH in supportedReasoningEfforts(ProviderKind.ANTHROPIC, "claude-sonnet-4-6"))
    }

    @Test
    fun geminiBudgetsAndLevelsStayWithinModelLimits() = runBlocking {
        val pro = payload(ProviderKind.GOOGLE, "gemini-2.5-pro", ReasoningEffort.HIGH)
        assertEquals(32768, pro["generationConfig"]!!.jsonObject["thinkingConfig"]!!.jsonObject["thinkingBudget"]!!.jsonPrimitive.int)
        val flash = payload(ProviderKind.GOOGLE, "gemini-2.5-flash", ReasoningEffort.NONE)
        assertEquals(0, flash["generationConfig"]!!.jsonObject["thinkingConfig"]!!.jsonObject["thinkingBudget"]!!.jsonPrimitive.int)
        assertFalse(ReasoningEffort.NONE in supportedReasoningEfforts(ProviderKind.GOOGLE, "gemini-2.5-pro"))
        assertFalse(ReasoningEffort.MINIMAL in supportedReasoningEfforts(ProviderKind.GOOGLE, "gemini-4-argon"))
        val argon = payload(ProviderKind.GOOGLE, "gemini-4-argon", ReasoningEffort.HIGH)
        assertEquals("HIGH", argon["generationConfig"]!!.jsonObject["thinkingConfig"]!!.jsonObject["thinkingLevel"]!!.jsonPrimitive.content)
    }

    @Test
    fun hostedModelMappingsUseTheProvidersDocumentedDialect() {
        val kind = ProviderKind.OPENAI_COMPATIBLE
        val baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1"
        assertEquals(ReasoningEffort.HIGH, effectiveReasoningEffort(kind, "deepseek-v4-pro", ReasoningEffort.LOW, baseUrl))
        assertEquals(ReasoningEffort.LOW, effectiveReasoningEffort(kind, "deepseek-v4-pro-0813", ReasoningEffort.LOW, baseUrl))
        assertEquals(listOf(ReasoningEffort.AUTO, ReasoningEffort.MAX), supportedReasoningEfforts(kind, "kimi/kimi-k3.1-pro", baseUrl))
        val request = StreamRequest(provider = ProviderConfig(kind, baseUrl, "fixture"), model = "glm-5.2",
            userPrompt = "question", reasoningEffort = ReasoningEffort.NONE,
            firstDeltaTimeoutMillis = 2500L, trustAllHttpsCertificates = false)
        val body = buildJsonObject { putReasoning(request) }
        assertEquals(false, body["enable_thinking"]?.jsonPrimitive?.boolean)
        assertFalse(body.containsKey("reasoning_effort"))
        assertFalse(body.containsKey("thinking"))
    }

    private suspend fun payload(kind: ProviderKind, model: String, effort: ReasoningEffort): JsonObject {
        MockWebServer().use { server ->
            server.enqueue(response(sse()))
            LlmClient().stream(request(server, kind, model).copy(reasoningEffort = effort)).toList()
            return Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        }
    }

    private suspend fun continuation(kind: ProviderKind, model: String, events: String): JsonObject {
        MockWebServer().use { server ->
            server.enqueue(response(events))
            val client = LlmClient()
            val request = request(server, kind, model).copy(reasoningEffort = ReasoningEffort.HIGH, tools = listOf(ToolRegistry.WEB_SEARCH_TOOL))
            val received = client.stream(request).toList()
            assertTrue(received.filterIsInstance<LlmEvent.TextDelta>().isEmpty())
            val call = received.filterIsInstance<LlmEvent.ToolCall>().single()
            server.takeRequest()
            server.enqueue(response(sse()))
            client.stream(request.copy(messages = listOf(
                ChatMessage("assistant", content = JsonArray(emptyList()), toolCalls = listOf(
                    ChatToolCall(call.id ?: "call1", function = ChatToolFunction(call.name, call.arguments.toString()), providerContext = call.providerContext)
                )),
                ChatMessage("tool", content = JsonPrimitive("search result"), toolCallId = call.id ?: "call1")
            ))).toList()
            return Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        }
    }

    private fun request(server: MockWebServer, kind: ProviderKind, model: String) = StreamRequest(
        provider = ProviderConfig(kind, server.url("/v1").toString(), apiKey = "test-key"),
        model = model, userPrompt = "sample question", firstDeltaTimeoutMillis = 2500L, trustAllHttpsCertificates = false
    )

    private fun response(body: String) = MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body)

    private fun sse(vararg events: Pair<String?, String>): String = events.joinToString("") { (type, data) ->
        (type?.let { "event: $it\n" } ?: "") + "data: $data\n\n"
    } + "data: [DONE]\n\n"
}
