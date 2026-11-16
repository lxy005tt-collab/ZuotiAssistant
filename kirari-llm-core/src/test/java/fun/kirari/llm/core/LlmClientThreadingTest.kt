package `fun`.kirari.llm.core

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class LlmClientThreadingTest {
    @Test
    fun multiImagePayloadIsBuiltOffUiThread_butDeltasKeepTheCollectorsContext() = runBlocking {
        val payloadThread = AtomicReference<Thread>()
        val logger = object : LlmLogger {
            override val isVerboseEnabled = true
            override fun v(tag: String, message: String) {
                if (message.startsWith("chat request payload=")) payloadThread.set(Thread.currentThread())
            }
        }
        val server = MockWebServer()
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
            .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\ndata: [DONE]\n\n"))
        server.start()
        val ui = Executors.newSingleThreadExecutor { Thread(it, "test-ui") }.asCoroutineDispatcher()
        try {
            val uiThread = withContext(ui) { Thread.currentThread() }
            val events = withContext(ui) {
                LlmClient(logger = logger).stream(StreamRequest(
                    provider = ProviderConfig(ProviderKind.OPENAI_COMPATIBLE, server.url("/v1").toString()),
                    model = "test", imagesBase64 = List(4) { "A".repeat(128 * 1024) },
                    firstDeltaTimeoutMillis = 5000, trustAllHttpsCertificates = false
                )).onEach { assertEquals(uiThread, Thread.currentThread()) }.toList()
            }
            assertNotNull(payloadThread.get())
            assertNotEquals(uiThread, payloadThread.get())
            assertEquals("ok", events.filterIsInstance<LlmEvent.TextDelta>().single().text)
        } finally {
            ui.close()
            server.shutdown()
        }
    }
}
