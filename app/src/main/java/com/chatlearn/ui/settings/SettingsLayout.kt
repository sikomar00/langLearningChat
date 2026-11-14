package com.chatlearn.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * 설정 묶음: 위에 묶음 이름, 아래 흰 탑승권 용지 판 하나에 항목들. 항목 사이는 [SettingItem]의 가는 선.
 * 묶음끼리는 넓게, 묶음 안은 좁게 띄워 어디까지가 한 덩어리인지 한눈에 보이게 한다.
 */
@Composable
internal fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(start = 4.dp).semantics { heading() }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), content = content)
        }
    }
}

/** 설정 항목 하나: 굵은 이름, 짧은 설명, 아래 조작. 묶음의 첫 항목이 아니면 위에 가는 선을 긋는다. */
@Composable
internal fun SettingItem(label: String, help: String? = null, first: Boolean = false, content: @Composable ColumnScope.() -> Unit = {}) {
    if (!first) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        help?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        content()
    }
}

/** 이름표 없이 묶음 안의 다음 줄(스위치·숫자 설정처럼 스스로 이름을 가진 줄)을 가는 선으로 나눈다. */
@Composable
internal fun ItemDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
