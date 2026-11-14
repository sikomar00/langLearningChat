package com.chatlearn.ai

import com.chatlearn.domain.Lang
import com.chatlearn.domain.Verdict
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

    @Test fun overloadedModelRetriesOnceThenSucceeds() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":{"status":"UNAVAILABLE"}}"""))
        server.enqueue(ok("""{"detectedLang":"en","koTranslation":"a","note":"b"}"""))
        assertTrue(ai.translateReceived("hi").isSuccess)
        assertEquals(2, server.requestCount)
    }

    @Test fun overloadedTwiceReportsOverloadedNotGenericServerError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(AiError.OVERLOADED, (ai.translateReceived("hi").exceptionOrNull() as AiException).kind)
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

    @Test fun unexpectedHttpErrorReportsStatusAndBodyForDiagnosis() = runBlocking {
        val seen = mutableListOf<Pair<Int, String>>()
        val diag = GeminiAiService(
            OkHttpClient(), server.url("/").toString().trimEnd('/'),
            apiKey = { "test-key" }, model = { "no-such-model" },
            onHttpError = { code, body -> seen += code to body }, retryDelayMs = 0,
        )
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":{"message":"model not found"}}"""))
        val e = diag.translateReceived("hi").exceptionOrNull() as AiException
        assertEquals(AiError.BAD_MODEL, e.kind)
        assertEquals(404, seen.single().first)
        assertTrue(seen.single().second.contains("model not found"))
    }

    @Test fun networkExceptionIsReportedWithItsTypeForDiagnosis() = runBlocking {
        val seen = mutableListOf<Pair<Int, String>>()
        val diag = GeminiAiService(
            OkHttpClient(), server.url("/").toString().trimEnd('/'),
            apiKey = { "secret-key-123" }, model = { "m" },
            onHttpError = { code, body -> seen += code to body }, retryDelayMs = 0,
        )
        server.shutdown()
        val e = diag.translateReceived("hi").exceptionOrNull() as AiException
        assertEquals(AiError.OFFLINE, e.kind)
        assertEquals(0, seen.single().first)
        assertTrue(seen.single().second.contains("Exception"))
        assertFalse(seen.single().second.contains("secret-key-123"))
    }

    @Test fun slowResponseIsTimeoutNotOffline() = runBlocking {
        val impatient = GeminiAiService(
            OkHttpClient.Builder().readTimeout(300, java.util.concurrent.TimeUnit.MILLISECONDS).build(),
            server.url("/").toString().trimEnd('/'),
            apiKey = { "k" }, model = { "m" }, retryDelayMs = 0,
        )
        server.enqueue(ok("""{"detectedLang":"en","koTranslation":"a","note":"b"}""")
            .setBodyDelay(2, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(AiError.TIMEOUT, (impatient.translateReceived("hi").exceptionOrNull() as AiException).kind)
    }

    @Test fun defaultClientWaitsLongEnoughForSlowModels() {
        val c = geminiHttpClient()
        assertTrue(c.readTimeoutMillis >= 60_000)
        assertTrue(c.callTimeoutMillis >= 60_000)
    }

    @Test fun illegalCharacterInKeyIsBadKeyNotACrash() = runBlocking {
        val badKeyAi = GeminiAiService(
            OkHttpClient(), server.url("/").toString().trimEnd('/'),
            apiKey = { "ab​cd" }, model = { "test-model" }, retryDelayMs = 0,
        )
        val e = badKeyAi.translateReceived("hi").exceptionOrNull() as AiException
        assertEquals(AiError.BAD_KEY, e.kind)
        assertFalse(e.message.orEmpty().contains("ab"))
    }

    @Test fun receivedMessageCarriesTheOtherLanguageVersion() = runBlocking {
        server.enqueue(ok("""{"detectedLang":"en","koTranslation":"a","note":"b","altText":"¿Cómo estás?"}"""))
        assertEquals("¿Cómo estás?", ai.translateReceived("How are you?").getOrThrow().altText)
    }

    @Test fun missingAltTextIsAnEmptyStringNotAnError() = runBlocking {
        server.enqueue(ok("""{"detectedLang":"en","koTranslation":"a","note":"b"}"""))
        assertEquals("", ai.translateReceived("hi").getOrThrow().altText)
    }

    @Test fun sentCandidatesCarryTheOtherLanguageVersion() = runBlocking {
        server.enqueue(ok("""{"candidates":[{"text":"See you soon!","toneNote":"편함","altText":"¡Nos vemos pronto!"}]}"""))
        assertEquals("¡Nos vemos pronto!", ai.suggestSent("곧 보자", Lang.EN).getOrThrow().single().altText)
    }

    @Test fun batchTranslationKeepsOrderAndCount() = runBlocking {
        server.enqueue(ok("""{"texts":["Hola","Adiós"]}"""))
        assertEquals(listOf("Hola", "Adiós"), ai.translateBatch(listOf("Hello", "Bye"), Lang.EN, Lang.ES).getOrThrow())
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("Hello") && body.contains("Bye"))
    }

    @Test fun batchWithWrongCountIsBadFormat() = runBlocking {
        server.enqueue(ok("""{"texts":["Hola"]}"""))
        server.enqueue(ok("""{"texts":["Hola"]}"""))
        val e = ai.translateBatch(listOf("Hello", "Bye"), Lang.EN, Lang.ES).exceptionOrNull() as AiException
        assertEquals(AiError.BAD_FORMAT, e.kind)
    }

    @Test fun emptyBatchNeverCallsTheServer() = runBlocking {
        assertEquals(emptyList(), ai.translateBatch(emptyList(), Lang.EN, Lang.ES).getOrThrow())
        assertEquals(0, server.requestCount)
    }

    @Test fun translateToReturnsText() = runBlocking {
        server.enqueue(ok("""{"text":"Hola"}"""))
        assertEquals("Hola", ai.translateTo("Hello", Lang.EN, Lang.ES).getOrThrow())
    }

    @Test fun receivedMessageCarriesKeyPhrasesWhenGiven() = runBlocking {
        server.enqueue(ok("""{"detectedLang":"es","koTranslation":"a","note":"b","altText":"I thought for a moment","keyPhrase":"por un momento","altKeyPhrase":"for a moment"}"""))
        val r = ai.translateReceived("Lo pensé por un momento").getOrThrow()
        assertEquals("por un momento", r.keyPhrase)
        assertEquals("for a moment", r.altKeyPhrase)
    }

    @Test fun missingKeyPhraseFieldsDefaultToEmpty() = runBlocking {
        server.enqueue(ok("""{"detectedLang":"en","koTranslation":"a","note":"b"}"""))
        val r = ai.translateReceived("hi").getOrThrow()
        assertEquals("", r.keyPhrase); assertEquals("", r.altKeyPhrase)
    }

    @Test fun sentCandidatesCarryKeyPhrases() = runBlocking {
        server.enqueue(ok("""{"candidates":[{"text":"See you soon","toneNote":"편함","altText":"Nos vemos pronto","keyPhrase":"See you","altKeyPhrase":"Nos vemos"}]}"""))
        val c = ai.suggestSent("곧 보자", Lang.EN).getOrThrow().single()
        assertEquals("See you", c.keyPhrase); assertEquals("Nos vemos", c.altKeyPhrase)
    }

    @Test fun extractKeyPhrasesReturnsOneEntryPerSentence() = runBlocking {
        server.enqueue(ok("""{"phrases":["See you","por un momento"]}"""))
        assertEquals(listOf("See you", "por un momento"), ai.extractKeyPhrases(listOf("See you soon", "Lo pensé por un momento"), Lang.EN).getOrThrow())
    }

    @Test fun extractKeyPhrasesRejectsAWrongCount() = runBlocking {
        server.enqueue(ok("""{"phrases":["only one"]}"""))
        server.enqueue(ok("""{"phrases":["only one"]}"""))
        assertTrue(ai.extractKeyPhrases(listOf("a", "b"), Lang.EN).isFailure)
    }

    @Test fun savedExpressionsAreSentAlongWhenGiven() = runBlocking {
        server.enqueue(ok("""{"candidates":[{"text":"See you soon","toneNote":"편함","altText":"","keyPhrase":"","altKeyPhrase":"","usedPhrase":"See you"}]}"""))
        val c = ai.suggestSent("곧 보자", Lang.EN, listOf(KnownExpression("See you", "내일 봐"))).getOrThrow().single()
        assertEquals("See you", c.usedPhrase)
        val prompt = server.takeRequest().body.readUtf8()
        assertTrue(prompt.contains("See you") && prompt.contains("내일 봐"))
    }

    @Test fun noSavedExpressionsMeansNoSuchPromptSection() = runBlocking {
        server.enqueue(ok("""{"candidates":[{"text":"Hi","toneNote":"x"}]}"""))
        val c = ai.suggestSent("안녕", Lang.EN).getOrThrow().single()
        assertEquals("", c.usedPhrase)
        assertFalse(server.takeRequest().body.readUtf8().contains("saved these expressions"))
    }

    @Test fun lookupWordParsesMeaningBaseFormAndNote() = runBlocking {
        server.enqueue(ok("""{"meaningKo":"~할 것 같다","baseForm":"seem","note":"추측을 나타내요"}"""))
        val w = ai.lookupWord("seems", "It seems fine", Lang.EN).getOrThrow()
        assertEquals("~할 것 같다", w.meaningKo); assertEquals("seem", w.baseForm); assertEquals("추측을 나타내요", w.note)
        val prompt = server.takeRequest().body.readUtf8()
        assertTrue(prompt.contains("seems") && prompt.contains("It seems fine"))
    }

    @Test fun lookupWordNeedsAMeaning() = runBlocking {
        server.enqueue(ok("""{"meaningKo":"","baseForm":"x","note":""}"""))
        server.enqueue(ok("""{"meaningKo":"","baseForm":"x","note":""}"""))
        assertTrue(ai.lookupWord("x", "x y", Lang.EN).isFailure)
    }

    @Test fun batchOfReceivedMessagesIsParsedInOrder() = runBlocking {
        server.enqueue(ok("""{"results":[
            {"detectedLang":"es","koTranslation":"안녕","note":"인사","altText":"Hi","keyPhrase":"","altKeyPhrase":""},
            {"detectedLang":"other","koTranslation":"x","note":"y","altText":"","keyPhrase":"","altKeyPhrase":""}]}"""))
        val r = ai.translateReceivedBatch(listOf("Hola", "Olá")).getOrThrow()
        assertEquals(listOf(Lang.ES, null), r.map { it.detectedLang })
        assertEquals("안녕", r[0].koTranslation)
        assertEquals(1, calls) // 한 번의 요청
    }

    @Test fun batchWithAWrongCountFails() = runBlocking {
        server.enqueue(ok("""{"results":[{"detectedLang":"es","koTranslation":"a","note":"b","altText":"","keyPhrase":"","altKeyPhrase":""}]}"""))
        server.enqueue(ok("""{"results":[{"detectedLang":"es","koTranslation":"a","note":"b","altText":"","keyPhrase":"","altKeyPhrase":""}]}"""))
        assertTrue(ai.translateReceivedBatch(listOf("a", "b")).isFailure)
    }

    private val received = """{"detectedLang":"en","koTranslation":"a","note":"b"}"""

    @Test fun dailyQuotaIsReportedAsDailyLimitWithoutRetrying() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody(
            """{"error":{"details":[{"violations":[{"quotaId":"GenerateRequestsPerDayPerProjectPerModel-FreeTier"}]}]}}""",
        ))
        assertEquals(AiError.DAILY_LIMIT, (ai.translateReceived("hi").exceptionOrNull() as AiException).kind)
        assertEquals(1, server.requestCount)
    }

    @Test fun longRetryAfterFailsAtOnceInsteadOfWaiting() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "60"))
        assertEquals(AiError.RATE_LIMIT, (ai.translateReceived("hi").exceptionOrNull() as AiException).kind)
        assertEquals(1, server.requestCount)
    }

    @Test fun internalServerErrorRetriesOnce() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(ok(received))
        assertTrue(ai.translateReceived("hi").isSuccess)
        assertEquals(2, server.requestCount)
    }

    @Test fun blockedPromptIsBlockedAndNotRetried() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"promptFeedback":{"blockReason":"SAFETY"}}"""))
        assertEquals(AiError.BLOCKED, (ai.translateReceived("hi").exceptionOrNull() as AiException).kind)
        assertEquals(1, server.requestCount)
    }

    @Test fun safetyStopIsBlocked() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"candidates":[{"finishReason":"SAFETY"}]}"""))
        assertEquals(AiError.BLOCKED, (ai.translateReceived("hi").exceptionOrNull() as AiException).kind)
        assertEquals(1, server.requestCount)
    }

    @Test fun truncatedAnswerIsBadFormatWithoutRetrying() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[{"text":"{\"detected"}]}}]}""",
        ))
        assertEquals(AiError.BAD_FORMAT, (ai.translateReceived("hi").exceptionOrNull() as AiException).kind)
        assertEquals(1, server.requestCount)
    }

    @Test fun unknownModelIsBadModel() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":{"status":"NOT_FOUND"}}"""))
        assertEquals(AiError.BAD_MODEL, (ai.translateReceived("hi").exceptionOrNull() as AiException).kind)
    }

    @Test fun cancellingTheCallerStopsWaitingForTheServer() = runBlocking {
        server.enqueue(ok(received).setHeadersDelay(10, java.util.concurrent.TimeUnit.SECONDS))
        val started = System.currentTimeMillis()
        val job = launch(kotlinx.coroutines.Dispatchers.Default) { ai.translateReceived("hi") }
        kotlinx.coroutines.delay(300)
        job.cancel()
        withTimeout(3_000) { job.join() }
        assertTrue(System.currentTimeMillis() - started < 5_000)
    }

    @Test fun repeatedRequestIsServedFromCache() = runBlocking {
        server.enqueue(ok(received))
        assertTrue(ai.translateReceived("hi").isSuccess)
        assertEquals("a", ai.translateReceived("hi").getOrThrow().koTranslation)
        assertEquals(1, server.requestCount)
        assertEquals(1, calls)
    }

    @Test fun failuresAreNotCached() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        assertTrue(ai.translateReceived("hi").isFailure)
        server.enqueue(ok(received))
        assertTrue(ai.translateReceived("hi").isSuccess)
    }

    @Test fun connectionCheckAlwaysAsksTheServer() = runBlocking {
        server.enqueue(ok("""{"text":"hola"}"""))
        assertTrue(ai.ping().isSuccess)
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(AiError.BAD_KEY, (ai.ping().exceptionOrNull() as AiException).kind)
    }

    @Test fun noInternetIsReportedWithoutRetrying() = runBlocking {
        val offline = GeminiAiService(OkHttpClient(), "http://no-such-host.invalid", apiKey = { "k" }, model = { "m" }, retryDelayMs = 5_000)
        val started = System.currentTimeMillis()
        assertEquals(AiError.OFFLINE, (offline.translateReceived("hi").exceptionOrNull() as AiException).kind)
        assertTrue(System.currentTimeMillis() - started < 5_000)
    }

    @Test fun coachingAnAttemptKeepsOnlyKnownGrammarKeysAndSendsTheHintLevel() = runBlocking {
        server.enqueue(ok("""{"correct":false,"hint":"ser와 estar 중 어느 쪽일까요?","grammarErrors":["es.ser_estar","made.up"],""" +
            """"answer":"Estoy cansado","explanationKo":"상태라 estar","koMeaning":"피곤해","altText":"I'm tired","keyPhrase":"Estoy cansado"}"""))
        val f = ai.coachAttempt(AttemptRequest(Lang.ES, "Soy cansado", koIntent = "피곤해", hintLevel = 2)).getOrThrow()
        assertFalse(f.correct)
        assertEquals(listOf("es.ser_estar"), f.grammarErrors)
        assertEquals("Estoy cansado", f.answer)
        val sent = server.takeRequest().body.readUtf8()
        assertTrue(sent.contains("es.ser_estar")) // 정해진 목록이 enum으로 간다
        assertTrue(sent.contains("hint level 2") || sent.contains("level 2"))
    }

    @Test fun gradingReportsWhichGrammarWasWrong() = runBlocking {
        server.enqueue(ok("""{"verdict":"close","corrected":"I have been there","explanationKo":"경험","grammarErrors":["en.present_perfect"]}"""))
        val r = ai.gradeAnswer(GradeRequest("가 봤어", Lang.KO, "I went there", Lang.EN, "I've been there")).getOrThrow()
        assertEquals(listOf("en.present_perfect"), r.grammarErrors)
    }

    @Test fun receivedAndSentResultsCarryTheirGrammar() = runBlocking {
        server.enqueue(ok("""{"detectedLang":"es","koTranslation":"a","note":"b","grammar":["es.subjunctive_present"]}"""))
        assertEquals(listOf("es.subjunctive_present"), ai.translateReceived("Ojalá vengas").getOrThrow().grammar)
        server.enqueue(ok("""{"candidates":[{"text":"Ojalá vengas","toneNote":"n","grammar":["es.subjunctive_present"]}]}"""))
        assertEquals(listOf("es.subjunctive_present"), ai.suggestSent("너 오면 좋겠다", Lang.ES).getOrThrow().single().grammar)
    }

    @Test fun practicePromptIsAKoreanSentence() = runBlocking {
        server.enqueue(ok("""{"ko":"내일 비가 오면 집에 있을 거야"}"""))
        assertEquals("내일 비가 오면 집에 있을 거야", ai.practicePrompt(com.chatlearn.domain.grammarPoint("en.conditionals")!!).getOrThrow())
    }

    private fun thinkingOf(model: String, vararg responses: MockResponse): List<String?> = runBlocking {
        val m = GeminiAiService(OkHttpClient(), server.url("/").toString().trimEnd('/'), apiKey = { "k" }, model = { model }, retryDelayMs = 0)
        responses.forEach(server::enqueue)
        assertTrue(m.translateReceived("hola").isSuccess); assertTrue(m.translateReceived("adiós").isSuccess)
        List(server.requestCount) {
            Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["generationConfig"]!!.jsonObject["thinkingConfig"]
                ?.jsonObject?.get("thinkingLevel")?.jsonPrimitive?.content
        }
    }

    @Test fun flashModelsThinkLittleForQuickTranslations() {
        val ok = ok("""{"detectedLang":"es","koTranslation":"a","note":"b"}""")
        assertEquals(listOf("low", "low"), thinkingOf("gemini-flash-latest", ok, ok))
    }

    @Test fun liteModelsKeepTheirOwnMinimalThinking() {
        val ok = ok("""{"detectedLang":"es","koTranslation":"a","note":"b"}""")
        assertEquals(listOf(null, null), thinkingOf("gemini-flash-lite-latest", ok, ok))
    }

    @Test fun aModelThatRejectsTheThinkingLevelIsAskedAgainWithoutIt() {
        val ok = ok("""{"detectedLang":"es","koTranslation":"a","note":"b"}""")
        val rejected = MockResponse().setResponseCode(400).setBody("""{"error":{"message":"Thinking level is not supported for this model."}}""")
        // 한 번 거절되면 다음 요청부터는 처음부터 빼고 보낸다.
        assertEquals(listOf("low", null, null), thinkingOf("gemini-2.5-flash", rejected, ok, ok))
    }
}
