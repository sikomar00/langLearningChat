package com.chatlearn.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.chatlearn.domain.Lang
import com.chatlearn.ui.theme.statusColors

/** 화면 가운데에 놓는 로딩 표시. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    // Expressive 로딩 인디케이터: 모양이 변하며 도는 형태라 기다리는 동안 덜 지루하다.
    Box(modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
}

/** 버튼 안에 넣는 작은 스피너. */
@Composable
fun ButtonSpinner() = CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)

/** 비어 있을 때: 아이콘 + 안내 + (선택) 다음 행동 버튼. */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline) }
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) FilledTonalButton(onClick = onAction) { Text(actionLabel) }
    }
}

enum class MessageKind { INFO, SUCCESS, ERROR }

/** 결과·안내 한 줄. 성공/오류를 색으로만이 아니라 접두 문구로도 구분하고 싶을 때 호출부에서 문구에 담는다. */
@Composable
fun InfoMessage(text: String, kind: MessageKind = MessageKind.INFO, modifier: Modifier = Modifier) {
    val color = when (kind) {
        MessageKind.INFO -> MaterialTheme.colorScheme.primary
        MessageKind.SUCCESS -> MaterialTheme.statusColors.success
        MessageKind.ERROR -> MaterialTheme.colorScheme.error
    }
    Text(text, modifier, color = color, style = MaterialTheme.typography.bodyMedium)
}

/** 제목 + 설명 + 스위치. 행 전체를 눌러도 토글되고 스크린리더는 스위치로 읽는다. */
@Composable
fun SwitchRow(title: String, description: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** 제목이 있는 카드 묶음. */
@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

fun langLabel(l: Lang): String = when (l) { Lang.KO -> "한국어"; Lang.EN -> "영어"; Lang.ES -> "스페인어" }

/** 작은 라벨 + 값. 구역 안에서 항목끼리 구분이 되게 한다. */
@Composable
fun LabeledText(
    label: String, text: String, modifier: Modifier = Modifier,
    valueStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    valueColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = valueStyle, color = valueColor)
    }
}
