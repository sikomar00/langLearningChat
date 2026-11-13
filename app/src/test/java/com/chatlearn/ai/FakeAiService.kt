package com.chatlearn.ai

import com.chatlearn.domain.Lang
import com.chatlearn.domain.Verdict

class FakeAiService : AiService {
    var received: (String) -> Result<ReceivedResult> =
        { Result.success(ReceivedResult(Lang.EN, "번역문", "설명", "texto alterno")) }
    var sent: (String, Lang) -> Result<List<SentCandidate>> =
        { _, _ -> Result.success(listOf(SentCandidate("Hi!", "편한 말투", "¡Hola!"))) }
    var grade: (GradeRequest) -> Result<GradeResult> =
        { Result.success(GradeResult(Verdict.CORRECT, it.reference, "좋아요")) }
    var translate: (String, Lang, Lang) -> Result<String> = { t, _, _ -> Result.success("[번역] $t") }
    var batch: (List<String>, Lang, Lang) -> Result<List<String>> =
        { items, _, _ -> Result.success(items.map { "[번역] $it" }) }
    var keyPhrases: (List<String>, Lang) -> Result<List<String>> =
        { items, _ -> Result.success(items.map { "" }) }
    var receivedBatch: (List<String>) -> Result<List<ReceivedResult>> = { items ->
        Result.success(items.map { ReceivedResult(Lang.EN, "번역:$it", "설명", "alt $it") })
    }
    var lookup: (String, String, Lang) -> Result<WordInfo> =
        { w, _, _ -> Result.success(WordInfo("뜻:$w", w, "설명")) }
    var correct: (String) -> Result<CorrectionResult> = { t ->
        Result.success(CorrectionResult(Lang.EN, "I don't know.", false, "don't로 줄여 써요.", "몰라.", listOf("No idea."), "don't know", "No lo sé.", "No lo sé"))
    }
    var lastKnown: List<KnownExpression> = emptyList()
    var lastSentTarget: Lang? = null
    val calls = mutableListOf<String>()

    override suspend fun translateReceived(text: String) = received(text).also { calls += "received" }
    override suspend fun suggestSent(koText: String, target: Lang, known: List<KnownExpression>) =
        sent(koText, target).also { calls += "sent"; lastKnown = known; lastSentTarget = target }
    override suspend fun gradeAnswer(req: GradeRequest) = grade(req).also { calls += "grade" }
    override suspend fun translateTo(text: String, from: Lang, to: Lang) = translate(text, from, to).also { calls += "translate" }
    override suspend fun translateReceivedBatch(items: List<String>) =
        receivedBatch(items).also { calls += "receivedBatch" }
    override suspend fun lookupWord(word: String, sentence: String, lang: Lang) =
        lookup(word, sentence, lang).also { calls += "lookup" }
    override suspend fun extractKeyPhrases(items: List<String>, lang: Lang) =
        keyPhrases(items, lang).also { calls += "keyphrases" }
    override suspend fun translateBatch(items: List<String>, from: Lang, to: Lang) =
        batch(items, from, to).also { calls += "batch" }
    override suspend fun correctSentence(text: String) = correct(text).also { calls += "correct" }
}
