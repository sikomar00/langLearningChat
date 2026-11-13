package com.chatlearn.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
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
    // 결과 문구는 스크린리더가 바로 읽어 준다.
    Text(text, modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }, color = color, style = MaterialTheme.typography.bodyMedium)
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

/** 제목이 있는 묶음. 강조는 오늘 화면의 말풍선에 맡기고, 여기는 옅은 바탕에 제목만 둔다. */
@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
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

/** 목록의 한 줄: 왼쪽 설명, 오른쪽 값. 줄 사이는 얇은 구분선. */
@Composable
fun StatRow(label: String, value: String, modifier: Modifier = Modifier, showDivider: Boolean = true) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        if (showDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** 숫자 설정의 범위와 한 번에 바뀌는 양. [zeroLabel]은 0일 때 숫자 대신 보일 말(예: "끔"). */
data class StepperSpec(val range: IntRange, val step: Int = 1, val zeroLabel: String? = null)

/** 숫자 설정: − 값 + . 누를 때마다 바로 저장한다(키보드로 숫자를 칠 필요가 없다). */
@Composable
fun Stepper(label: String, saved: Int, onChange: (Int) -> Unit, spec: StepperSpec) {
    // 저장이 끝나기 전에 여러 번 눌러도 누른 만큼 바뀌게 화면 값을 따로 들고 있는다.
    var value by androidx.compose.runtime.remember(saved) { androidx.compose.runtime.mutableIntStateOf(saved) }
    val (range, step, zeroLabel) = spec
    fun set(v: Int) { value = v.coerceIn(range); onChange(value) }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { set(value - step) }, enabled = value > range.first) {
            Icon(androidx.compose.material.icons.Icons.Default.Remove, contentDescription = "$label 줄이기")
        }
        Text(
            if (value == 0 && zeroLabel != null) zeroLabel else "$value",
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = com.chatlearn.ui.theme.Rounded),
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 56.dp),
        )
        FilledTonalIconButton(onClick = { set(value + step) }, enabled = value < range.last) {
            Icon(androidx.compose.material.icons.Icons.Default.Add, contentDescription = "$label 늘리기")
        }
    }
}

