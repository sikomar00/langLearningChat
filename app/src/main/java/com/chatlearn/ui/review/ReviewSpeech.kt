package com.chatlearn.ui.review

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.LocaleList
import com.chatlearn.domain.ExerciseKind
import com.chatlearn.domain.Lang
import com.chatlearn.review.speechLanguageTag
import com.chatlearn.settings.VoicePrefs
import com.chatlearn.ui.components.langLabel

/** 음성 인식 결과를 어디에 쓸지: 답 입력칸 또는 따라 말하기(카드 id). */
internal sealed interface SpeechTarget {
    data object Answer : SpeechTarget
    data class Shadow(val cardId: Long) : SpeechTarget
}

internal fun speechIntent(lang: Lang, voice: VoicePrefs): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLanguageTag(lang, voice))
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, speechLanguageTag(lang, voice))
    .putExtra(RecognizerIntent.EXTRA_PROMPT, "${langLabel(lang)}로 말해 보세요")
    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

/** 음성 인식 화면을 띄울 수 있는 앱(구글 앱 등)이 있는가. 없으면 마이크 버튼을 숨긴다. */
internal fun speechAvailable(ctx: Context): Boolean =
    ctx.packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty()

/** 답 입력칸 키보드: 철자를 보는 빈칸·받아쓰기는 자동 수정을 끄고, 정답 언어 키보드를 권한다. */
internal fun answerKeyboard(kind: ExerciseKind, lang: Lang, ime: ImeAction, voice: VoicePrefs) = KeyboardOptions(
    capitalization = if (kind == ExerciseKind.CLOZE) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
    autoCorrectEnabled = kind == ExerciseKind.TRANSLATE,
    keyboardType = KeyboardType.Text,
    imeAction = ime,
    hintLocales = LocaleList(speechLanguageTag(lang, voice)),
)
