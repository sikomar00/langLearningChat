package com.chatlearn.ui.translate

/** "Alex, 약속"처럼 쉼표로 구분한 입력을 태그 목록으로 바꾼다. 빈 값과 중복은 버린다. */
fun parseTags(input: String): List<String> =
    input.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

/**
 * 자동완성 후보: [recent] 순서(최근 사용 순)를 유지하고, 쉼표 뒤 마지막 조각을 검색어로 쓴다.
 * 앞에서 이미 고른 태그는 빼고, 검색어가 있으면 그 글자가 들어간 태그만 남긴다.
 */
fun suggestTags(recent: List<String>, input: String, limit: Int = 8): List<String> {
    val chosen = input.substringBeforeLast(',', "").split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val query = input.substringAfterLast(',').trim()
    return recent
        .filter { it !in chosen && (query.isEmpty() || it.contains(query, ignoreCase = true)) }
        .take(limit)
}

/** 후보를 눌렀을 때의 새 입력값: 현재 입력 중인 조각을 후보로 바꾸고 다음 태그를 이어 쓸 수 있게 ", "를 붙인다. */
fun applyTagSuggestion(input: String, tag: String): String {
    val head = input.substringBeforeLast(',', "").trim()
    return if (head.isEmpty()) "$tag, " else "$head, $tag, "
}
