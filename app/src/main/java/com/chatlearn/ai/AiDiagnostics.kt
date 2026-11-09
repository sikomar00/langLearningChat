package com.chatlearn.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 최근 AI 호출 실패를 몇 건만 메모리에 둔다(진단용). 앱 프로세스가 끝나면 사라진다.
 * adb 없이도 "언제, 어떤 모델이, 무슨 오류를 냈는지" 볼 수 있게 한다. API 키는 담지 않는다(호출하는 쪽이 가린다).
 */
class AiDiagnostics(private val max: Int = 5) {
    /** [code]가 0이면 HTTP 응답이 없는 연결 오류(시간 초과, 끊김). */
    data class Entry(val at: Long, val model: String, val code: Int, val detail: String)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    /** 최신 순. */
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun record(at: Long, model: String, code: Int, detail: String) {
        val clean = detail.replace(Regex("\\s+"), " ").trim().take(MAX_DETAIL)
        _entries.update { (listOf(Entry(at, model, code, clean)) + it).take(max) }
    }

    fun clear() { _entries.value = emptyList() }

    companion object {
        const val MAX_DETAIL = 200
        private val TIME = DateTimeFormatter.ofPattern("MM/dd HH:mm:ss")
    }

    /** 화면에 보이고 복사해 보낼 한 줄. */
    fun line(e: Entry, zone: ZoneId): String {
        val time = TIME.format(Instant.ofEpochMilli(e.at).atZone(zone))
        val what = if (e.code == 0) "연결 오류" else "HTTP ${e.code}"
        return listOf(time, e.model, what, e.detail).filter { it.isNotBlank() }.joinToString(" · ")
    }
}
