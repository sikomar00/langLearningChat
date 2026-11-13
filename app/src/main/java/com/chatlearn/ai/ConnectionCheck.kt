package com.chatlearn.ai

/** 저장된 키와 모델로 짧은 번역을 한 번 요청해 본다. 문제가 없으면 null, 있으면 원인을 돌려준다. */
suspend fun checkConnection(ai: AiService): AiError? =
    ai.ping().exceptionOrNull()?.let { (it as? AiException)?.kind ?: AiError.SERVER }
