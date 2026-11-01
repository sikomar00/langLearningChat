package com.chatlearn.review

import com.chatlearn.data.DueCard

/**
 * 같은 문장의 영어·스페인어 카드가 연달아 나오지 않게 순서를 나눈다.
 * 매번 "바로 앞과 다른 문장" 중에서 남은 카드가 가장 많은 문장의 카드를 고르고(끝에 같은 문장만 남는 것을 막는다),
 * 같으면 원래 순서가 앞선 것을 고른다. 나눌 방법이 없으면 그대로 둔다.
 */
fun spreadSiblings(cards: List<DueCard>): List<DueCard> {
    val queues = LinkedHashMap<Long, ArrayDeque<IndexedValue<DueCard>>>()
    cards.withIndex().forEach { queues.getOrPut(it.value.card.entryId) { ArrayDeque() }.addLast(it) }
    val out = ArrayList<DueCard>(cards.size)
    var last: Long? = null
    while (out.size < cards.size) {
        val open = queues.filter { it.value.isNotEmpty() }
        val choices = open.filterKeys { it != last }.ifEmpty { open }
        val pick = choices.entries.maxWithOrNull(
            compareBy<Map.Entry<Long, ArrayDeque<IndexedValue<DueCard>>>> { it.value.size }.thenByDescending { it.value.first().index },
        )!!
        out += pick.value.removeFirst().value
        last = pick.key
    }
    return out
}
