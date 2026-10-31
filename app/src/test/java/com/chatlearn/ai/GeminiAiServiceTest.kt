package com.chatlearn.ai

import com.chatlearn.domain.Lang
import com.chatlearn.domain.Verdict
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.*

class GeminiAiServiceTest {
    private lateinit var server: MockWebServer
    private lateinit var ai: GeminiAiService
    private var calls = 0

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        ai = GeminiAiService(
            OkHttpClient(), server.url("/").toString().trimEnd('/'),
            apiKey = { "test-key" }, model = { "test-model" },
            onCall = { calls++ }, retryDelayMs = 0,
        )
    }

    @After fun tearDown() { runCatching { server.shutdown() } }

    private fun body(inner: String) =
        """{"candidates":[{"content":{"parts":[{"text":${JsonPrimitive(inner)}}]}}]}"""

    private fun ok(inner: String) = MockResponse().setResponseCode(200).setBody(body(inner))

    @Test fun parsesReceivedMessage() = runBlocking {
        server.enqueue(ok("""{"detectedLang":"en","koTranslation":"어떻게 지내?","note":"안부 인사"}"""))
        val r = ai.translateReceived("How are you?").getOrThrow()
        assertEquals(Lang.EN, r.detectedLang)
        assertEquals("어떻게 지내?", r.koTranslation)
        assertEquals(1, calls)
    }

    @Test fun unsupportedLanguageMapsToNull() = runBlocking {
        server.enqueue(ok("""{"detectedLang":"other","koTranslation":"x","note":"y"}"""))
        assertNull(ai.translateReceived("Olá, tudo bem?").getOrThrow().detectedLang)
    }

    @Test fun sendsKeyHeaderModelPathAndJsonSchema() = runBlocking {
        server.enqueue(ok("""{"detectedLang":"es","koTranslation":"a","note":"b"}"""))
        ai.translateReceived("hola")
        val req = server.takeRequest()
        assertEquals("test-key", req.getHeader("x-goog-api-key"))
        assertEquals("/v1beta/models/test-model:generateContent", req.path)
        val gen = Json.parseToJsonElement(req.body.readUtf8()).jsonObject["generationConfig"]!!.jsonObject
        assertEquals("application/json", gen["responseMimeType"]!!.jsonPrimitive.content)
    }

    @Test fun rateLimitRetriesOnceThenSucceeds() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(ok("""{"detectedLang":"en","koTranslation":"a","note":"b"}"""))
        assertTrue(ai.translateReceived("hi").isSuccess)
        assertEquals(2, server.requestCount)
        assertEquals(1, calls)
    }

    @Test fun rateLimitTwiceFails() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setResponseCode(429))
        val e = ai.translateReceived("hi").exceptionOrNull() as AiException
        assertEquals(AiError.RATE_LIMIT, e.kind)
        assertEquals(2, server.requestCount)
    }

    @Test fun forbiddenMeansBadKey() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(AiError.BAD_KEY, (ai.translateReceived("hi").exceptionOrNull() as AiException).kind)
    }

    @Test fun invalidKeyBadRequestMeansBadKey() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"API key not valid"}}"""))
        assertEquals(AiError.BAD_KEY, (ai.translateReceived("hi").exceptionOrNull() as AiException).kind)
    }

    @Test fun malformedJsonRetriesOnceThenFails() = runBlocking {
        server.enqueue(ok("not json"))
        server.enqueue(ok("still not json"))
        assertEquals(AiError.BAD_FORMAT, (ai.translateReceived("hi").exceptionOrNull() as AiException).kind)
        assertEquals(2, server.requestCount)
    }

    @Test fun malformedThenValidSucceeds() = runBlocking {
        server.enqueue(ok("not json"))
        server.enqueue(ok("""{"detectedLang":"en","koTranslation":"a","note":"b"}"""))
        assertTrue(ai.translateReceived("hi").isSuccess)
    }

    @Test fun networkFailureMeansOffline() = runBlocking {
        server.shutdown()
        assertEquals(AiError.OFFLINE, (ai.translateReceived("hi").exceptionOrNull() as AiException).kind)
    }

    @Test fun parsesSentCandidates() = runBlocking {
        server.enqueue(ok("""{"candidates":[{"text":"See you soon!","toneNote":"편한 말투"},{"text":"I look forward to seeing you.","toneNote":"정중함"}]}"""))
        val r = ai.suggestSent("곧 보자", Lang.EN).getOrThrow()
        assertEquals(2, r.size)
        assertEquals("See you soon!", r[0].text)
    }

    @Test fun emptyCandidateListIsBadFormat() = runBlocking {
        server.enqueue(ok("""{"candidates":[]}"""))
        server.enqueue(ok("""{"candidates":[]}"""))
        assertEquals(AiError.BAD_FORMAT, (ai.suggestSent("곧 보자", Lang.EN).exceptionOrNull() as AiException).kind)
    }

    @Test fun parsesGradeVerdict() = runBlocking {
        server.enqueue(ok("""{"verdict":"close","corrected":"I am fine.","explanationKo":"관사 없이도 자연스러워요."}"""))
        val r = ai.gradeAnswer(GradeRequest("괜찮아", Lang.KO, "I fine", Lang.EN, "I'm fine.")).getOrThrow()
        assertEquals(Verdict.CLOSE, r.verdict)
    }

    @Test fun quotesNewlinesAndEmojiProduceValidRequestJson() = runBlocking {
        server.enqueue(ok("""{"detectedLang":"en","koTranslation":"a","note":"b"}"""))
        val nasty = "He said \"hi\"\nthen left 😂\\o/ " + "x".repeat(3000)
        ai.translateReceived(nasty)
        val sent = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val text = sent["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(text.contains(nasty))
    }

    @Test fun translateToReturnsText() = runBlocking {
        server.enqueue(ok("""{"text":"Hola"}"""))
        assertEquals("Hola", ai.translateTo("Hello", Lang.EN, Lang.ES).getOrThrow())
    }
}
