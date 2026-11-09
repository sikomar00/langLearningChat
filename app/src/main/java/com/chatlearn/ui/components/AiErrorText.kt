package com.chatlearn.ui.components

import com.chatlearn.ai.AiError

/** AI 오류를 사용자에게 보여 줄 문구. 화면마다 따로 쓰지 말고 여기서만 정한다. */
fun aiErrorText(e: AiError): String = when (e) {
    AiError.RATE_LIMIT -> "오늘 AI 한도에 도달했어요. 잠시 후 다시 시도해 주세요."
    AiError.OFFLINE -> "인터넷에 연결되어 있지 않아요."
    AiError.TIMEOUT -> "AI 응답이 너무 오래 걸려요. 잠시 후 다시 시도해 주세요."
    AiError.OVERLOADED -> "지금 이 AI 모델에 요청이 몰려 있어요. 잠시 후 다시 시도하거나, 설정에서 다른 모델 이름으로 바꿔 보세요."
    AiError.BAD_KEY -> "API 키를 확인해 주세요. 설정 탭에서 바꿀 수 있어요."
    AiError.BAD_FORMAT -> "AI 응답을 읽지 못했어요. 다시 시도해 주세요."
    AiError.SERVER -> "AI 서버에 문제가 생겼어요."
    AiError.MODEL_NOT_FOUND -> "설정한 모델을 찾을 수 없어요. 설정 탭에서 모델 이름을 확인하거나 다른 모델로 바꿔 주세요."
}

/** 키나 모델 이름이 문제라서 다시 시도해도 같다. 설정에서 고쳐야 하는 오류. */
fun AiError.needsSettings(): Boolean = this == AiError.BAD_KEY || this == AiError.MODEL_NOT_FOUND
