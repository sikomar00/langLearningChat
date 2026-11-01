package com.chatlearn.domain

/** 채점 결과와 어긋나는 평가는 보여 주지 않는다. 채점이 없으면(정답 보기, 스스로 채점) 전부 보여 준다. */
fun ratingChoices(verdict: Verdict?): List<Rating> = when (verdict) {
    Verdict.WRONG -> listOf(Rating.AGAIN, Rating.HARD)
    Verdict.CLOSE -> listOf(Rating.AGAIN, Rating.HARD, Rating.GOOD)
    Verdict.CORRECT -> listOf(Rating.HARD, Rating.GOOD, Rating.EASY)
    null -> Rating.entries
}

/** 난이도가 아니라 얼마나 기억났는지로 이름을 붙인다. */
fun ratingLabel(r: Rating): String = when (r) {
    Rating.AGAIN -> "몰랐어요"
    Rating.HARD -> "겨우 기억"
    Rating.GOOD -> "기억했어요"
    Rating.EASY -> "완벽해요"
}
