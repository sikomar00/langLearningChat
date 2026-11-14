package com.chatlearn

import android.content.Intent
import com.chatlearn.ui.translate.TranslateMode
import com.chatlearn.ui.translate.canReturnText
import com.chatlearn.ui.translate.sharedTextOf

/** 알림·앱 바로가기·위젯이 앱을 열 때 쓰는 액션. [EXTRA_TARGET]에 열 곳을 담는다. */
const val ACTION_OPEN = "com.chatlearn.action.OPEN"
const val EXTRA_TARGET = "target"

/** 앱을 열면서 바로 할 일. */
sealed interface LaunchRequest {
    /** 다른 앱에서 공유·선택 메뉴로 넘어온 문장. [editable]: 고칠 수 있는 칸에서 선택해 왔다. */
    data class Shared(val text: String, val editable: Boolean = false) : LaunchRequest
    data object Review : LaunchRequest
    /** 클립보드의 메시지를 붙여넣고 받은 말로 번역한다(창이 포커스를 얻은 뒤에 읽어야 한다). */
    data object PasteTranslate : LaunchRequest
    /** 번역 탭을 이 모드로 연다(보낼 말, 첨삭). */
    data class Compose(val mode: TranslateMode) : LaunchRequest
}

fun launchRequestOf(intent: Intent?): LaunchRequest? {
    sharedTextOf(intent)?.let { return LaunchRequest.Shared(it, canReturnText(intent)) }
    if (intent?.action != ACTION_OPEN) return null
    return when (intent.getStringExtra(EXTRA_TARGET)) {
        "review" -> LaunchRequest.Review
        "paste" -> LaunchRequest.PasteTranslate
        "sent" -> LaunchRequest.Compose(TranslateMode.SENT)
        "correct" -> LaunchRequest.Compose(TranslateMode.CORRECT)
        else -> null
    }
}
