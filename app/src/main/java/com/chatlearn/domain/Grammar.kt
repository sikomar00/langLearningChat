package com.chatlearn.domain

/**
 * 문법 항목. AI 응답의 enum과 약점 집계가 같은 키를 쓰도록 목록을 코드에 고정한다
 * (AI가 매번 다른 이름을 지어내면 같은 약점이 여러 줄로 흩어진다).
 */
data class GrammarPoint(val key: String, val lang: Lang, val nameKo: String, val explainKo: String)

private fun en(key: String, name: String, explain: String) = GrammarPoint("en.$key", Lang.EN, name, explain)
private fun es(key: String, name: String, explain: String) = GrammarPoint("es.$key", Lang.ES, name, explain)

val GRAMMAR_POINTS: List<GrammarPoint> = listOf(
    en("articles", "관사 a/an/the", "처음 말하는 하나는 a/an, 서로 아는 것·하나뿐인 것은 the. 셀 수 없는 것과 일반적인 것에는 붙이지 않을 때가 많다."),
    en("plural_countable", "셀 수 있는 명사·복수", "셀 수 있는 명사는 하나면 a, 여럿이면 -s. information, advice 같은 말은 셀 수 없다."),
    en("present_simple", "현재형(습관·사실)", "늘 하는 일과 사실은 현재형. 3인칭 단수(he/she/it)는 동사에 -s를 붙인다."),
    en("present_continuous", "현재진행형", "지금 하고 있거나 곧 하기로 한 일은 be + -ing."),
    en("past_simple", "과거형", "끝난 때가 분명한 일은 과거형(went, saw). yesterday, last week와 함께 쓴다."),
    en("present_perfect", "현재완료", "have + 과거분사. 경험·지금까지 이어진 일·방금 끝나 지금에 영향이 있는 일. 정확한 과거 시점과는 같이 쓰지 않는다."),
    en("future", "미래 표현 will / be going to", "즉석 결정·예측은 will, 이미 정한 계획은 be going to, 정해진 약속은 현재진행형."),
    en("modals", "조동사 can/should/must/might", "조동사 뒤에는 동사원형. 가능·조언·의무·추측의 세기를 고른다."),
    en("conditionals", "조건문 if", "실제 가능한 조건은 if + 현재, would + 동사. 가정은 if + 과거, would + 동사원형."),
    en("prepositions_time_place", "전치사 in/on/at", "시간·장소의 넓이에 따라 in(넓은 범위) on(날·면) at(지점·시각)."),
    en("phrasal_verbs", "구동사", "get up, figure out처럼 동사+작은 말이 한 뜻을 만든다. 목적어 위치에 주의한다."),
    en("gerund_infinitive", "동명사 / to부정사", "enjoy -ing, want to처럼 동사마다 뒤에 오는 형태가 정해져 있다."),
    en("questions_word_order", "의문문 어순", "do/does/did나 조동사를 앞으로. 의문사 질문도 의문사 + 조동사 + 주어 + 동사."),
    en("subject_verb_agreement", "주어·동사 일치", "주어가 단수면 동사도 단수(is, has, -s)."),
    en("comparatives", "비교급·최상급", "짧은 말은 -er/-est, 긴 말은 more/most. than과 the를 빠뜨리지 않는다."),
    en("pronouns", "대명사·소유격", "주격·목적격·소유격(I/me/my) 자리를 구분한다."),
    en("relative_clauses", "관계사 who/which/that", "명사를 뒤에서 꾸밀 때. 사람은 who, 사물은 which/that."),
    en("passive", "수동태", "be + 과거분사. 누가 했는지보다 무엇이 일어났는지가 중요할 때."),
    en("reported_speech", "간접 화법", "남의 말을 전할 때 시제를 한 단계 과거로 옮기는 경우가 많다."),
    en("used_to", "used to / would", "예전에는 했지만 지금은 안 하는 일."),
    en("tag_questions", "부가 의문문", "문장 끝 ..., isn't it? 처럼 확인하는 꼬리. 앞이 긍정이면 뒤는 부정."),
    en("casual_contractions", "구어체 줄임", "gonna, wanna, I'm, don't 등 채팅에서 자연스러운 줄임과 말투."),
    en("word_choice", "단어 선택·연어", "뜻은 맞아도 원어민이 쓰지 않는 조합(make a photo → take a photo)."),
    en("spelling", "철자", "철자 실수."),
    es("ser_estar", "ser / estar", "ser는 정체·성질·시간, estar는 상태·위치·진행. 같은 형용사도 뜻이 달라진다(ser listo / estar listo)."),
    es("gender_agreement", "성·수 일치", "명사의 성(el/la)과 수에 관사·형용사를 맞춘다."),
    es("articles", "관사", "정관사 el/la/los/las와 부정관사 un/una. 일반적인 것에도 정관사를 쓴다."),
    es("present_indicative", "직설법 현재", "주어에 따라 동사 어미가 바뀐다. 불규칙 동사(tener, ir, hacer)에 주의."),
    es("preterite", "단순과거", "끝난 한 번의 일(ayer fui)."),
    es("imperfect", "불완료과거", "과거의 습관·배경·진행(cuando era niño, iba...)."),
    es("preterite_vs_imperfect", "단순과거 vs 불완료과거", "끝난 사건은 단순과거, 배경·습관은 불완료과거."),
    es("present_perfect", "현재완료", "haber + 과거분사(he comido). 지역에 따라 단순과거를 더 쓴다."),
    es("future", "미래·ir a", "ir a + 동사원형이 일상 계획에 흔하다. 미래형은 예측·추측."),
    es("subjunctive_present", "접속법 현재", "바람·감정·의심·명령(espero que vengas, quiero que...) 뒤의 que절."),
    es("subjunctive_past", "접속법 과거", "과거 시점의 바람·가정(si tuviera...)."),
    es("conditional", "조건법", "~할 텐데, 공손한 부탁(me gustaría, podrías)."),
    es("imperative", "명령형", "tú/usted 명령, 부정 명령은 접속법 형태(no vengas)."),
    es("por_para", "por / para", "para는 목적·대상·기한, por는 원인·경로·교환·기간."),
    es("object_pronouns", "목적격 대명사 lo/la/le", "직접목적 lo/la, 간접목적 le. 동사 앞이나 원형·현재분사 뒤에 붙는다."),
    es("reflexive_verbs", "재귀동사", "levantarse, llamarse처럼 se와 함께 쓰는 동사."),
    es("gustar_verbs", "gustar류 동사", "좋아하는 대상이 주어. me gusta(단수) / me gustan(복수)."),
    es("prepositions", "전치사 a/en/de/con", "사람 목적어 앞 a, 장소 en, 출처·소유 de."),
    es("questions_accents", "의문문·악센트", "¿ ?와 의문사 악센트(qué, cómo, dónde)."),
    es("haber_tener", "hay / tener", "있다(hay)와 가지고 있다(tener), tener que(~해야 한다)."),
    es("saber_conocer", "saber / conocer", "사실·방법은 saber, 사람·장소에 익숙함은 conocer."),
    es("diminutives_slang", "구어체·지역 표현", "채팅에서 쓰는 줄임(q, xq), 지소사(-ito), 지역 말투."),
    es("word_choice", "단어 선택·연어", "뜻은 맞아도 원어민이 쓰지 않는 조합."),
    es("spelling_accents", "철자·악센트", "철자와 악센트(tilde) 실수."),
)

private val BY_KEY = GRAMMAR_POINTS.associateBy { it.key }

fun grammarPoint(key: String): GrammarPoint? = BY_KEY[key]

fun grammarKeys(lang: Lang): List<String> = GRAMMAR_POINTS.filter { it.lang == lang }.map { it.key }
