package com.chatlearn.ui.translate

import android.content.Intent

/** 다른 앱의 "공유" 또는 텍스트 선택 메뉴로 넘어온 문장. 비어 있거나 해당 없는 인텐트면 null. */
fun sharedTextOf(intent: Intent?): String? = when (intent?.action) {
    Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
    Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
    else -> null
}?.toString()?.trim()?.takeIf { it.isNotEmpty() }
