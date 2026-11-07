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
        assertEquals("high", payload(ProviderKind.OPENAI_COMPATIBLE, "gpt-reasoner", ReasoningEffort.HIGH)["reasoning_effort"]?.jsonPrimitive?.content)
        val responses = payload(ProviderKind.OPENAI_RESPONSES, "gpt-reasoner", ReasoningEffort.XHIGH)
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
        val body = continuation(ProviderKind.OPENAI_RESPONSES, "gpt-reasoner", events)
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
