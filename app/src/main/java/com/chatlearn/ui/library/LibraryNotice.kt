package com.chatlearn.ui.library

const val AI_FAILED_HINT = "AI에 연결하지 못했어요. 설정 탭의 '연결 확인'으로 키와 모델, 인터넷을 확인해 주세요."

/** 라이브러리 위쪽 안내 문구를 보여 주는 시간. 접근성 설정에서 더 길게 잡았으면 그 시간을 따른다. */
const val LIBRARY_NOTICE_MS = 4_000L

/** 라이브러리 위쪽 안내. 다시 해 볼 일이 남은 안내(sticky)는 누를 때까지 두고, 나머지는 잠시 뒤 저절로 사라진다. */
data class LibraryNotice(val text: String, val sticky: Boolean = false)

fun folderMoveNotice(count: Int, folderName: String?) = LibraryNotice(
    if (folderName == null) "${count}개를 폴더에서 꺼냈어요." else "${count}개를 '$folderName' 폴더로 옮겼어요.",
)

fun expandNotice(created: Int, failedBatches: Int): LibraryNotice =
    if (created == 0 && failedBatches > 0) LibraryNotice(AI_FAILED_HINT, sticky = true)
    else LibraryNotice("다른 언어 카드 ${created}개를 만들었어요." + retryHint(failedBatches), sticky = failedBatches > 0)

fun keyPhraseNotice(filled: Int, none: Int, failedBatches: Int): LibraryNotice =
    if (filled == 0 && none == 0 && failedBatches > 0) LibraryNotice(AI_FAILED_HINT, sticky = true)
    else LibraryNotice("핵심 표현 ${filled}개를 찾았어요." + retryHint(failedBatches), sticky = failedBatches > 0)

private fun retryHint(failedBatches: Int) = if (failedBatches > 0) " (실패한 묶음 ${failedBatches}개는 다시 눌러 보세요)" else ""

/** 노션 보내기 결과. 토큰·데이터베이스 문제는 고칠 곳(설정)을 알려 주고 누를 때까지 둔다. */
fun notionNotice(r: com.chatlearn.notion.NotionReport): LibraryNotice = when (r.error) {
    com.chatlearn.notion.NotionError.BAD_TOKEN, com.chatlearn.notion.NotionError.NOT_SHARED ->
        LibraryNotice("노션에 보내지 못했어요. 오늘 > 톱니 > 백업 > 노션에서 토큰과 데이터베이스 링크를 확인해 주세요.", sticky = true)
    com.chatlearn.notion.NotionError.OFFLINE -> LibraryNotice("인터넷에 연결되어 있지 않아 노션에 보내지 못했어요.", sticky = true)
    com.chatlearn.notion.NotionError.RATE_LIMIT -> LibraryNotice("노션 요청 한도에 걸렸어요. ${r.sent}개는 보냈고, 잠시 뒤 다시 눌러 주세요.", sticky = true)
    else -> LibraryNotice("노션에 ${r.sent}개를 보냈어요." + if (r.failed > 0) " (${r.failed}개는 실패, 다시 눌러 보세요)" else "", sticky = r.failed > 0)
}

