package com.chatlearn.ui.translate

import android.content.Intent

/** 다른 앱의 "공유" 또는 텍스트 선택 메뉴로 넘어온 문장. 비어 있거나 해당 없는 인텐트면 null. */
fun sharedTextOf(intent: Intent?): String? = when (intent?.action) {
    Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
    Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
    else -> null
}?.toString()?.trim()?.takeIf { it.isNotEmpty() }

/** 텍스트 선택 메뉴로 왔고 고칠 수 있는 칸이면, 고른 문장을 원래 칸에 돌려줄 수 있다. */
fun canReturnText(intent: Intent?): Boolean =
    intent?.action == Intent.ACTION_PROCESS_TEXT && !intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
