package com.chatlearn.ai

const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com"

/**
 * AI 서버 주소. 디버그 빌드에서만 앱 내부 파일(ai_base_url.txt)로 바꿀 수 있다(에뮬레이터의 가짜 서버 시험용).
 * http(s) 주소가 아니면 무시한다.
 */
fun aiBaseUrl(debuggable: Boolean, overrideText: String?): String {
    val o = overrideText?.trim()?.trimEnd('/').orEmpty()
    if (!debuggable || o.isEmpty()) return GEMINI_BASE_URL
    return if (o.startsWith("http://") || o.startsWith("https://")) o else GEMINI_BASE_URL
}
