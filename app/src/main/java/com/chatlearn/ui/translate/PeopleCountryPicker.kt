package com.chatlearn.ui.translate

import com.chatlearn.ui.components.ChoiceChip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatlearn.domain.countryChoicesFor

/**
 * 저장할 때 한 번 눌러 고르는 "누구와 · 어느 나라" 칩. 대화에서 알아낸 친구는 미리 골라져 있고,
 * 고른 값은 다음 문장에도 남는다(같은 친구와의 대화를 이어서 저장할 때가 많다).
 */
@Composable
fun PeopleCountryPicker(vm: TranslateViewModel, showPeople: Boolean = true, showCountries: Boolean = true, lang: com.chatlearn.domain.Lang? = null) {
    val s by vm.state.collectAsStateWithLifecycle()
    val recentPeople by vm.recentPeople.collectAsStateWithLifecycle()
    val recentCountries by vm.recentCountries.collectAsStateWithLifecycle()
    var addingPerson by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showPeople) ChipRow(ChipRowSpec(Icons.Default.Person, "누구와", (s.people + s.speakers + recentPeople).toList().distinct(), s.people), vm::togglePerson) {
            AssistChip(onClick = { addingPerson = true }, label = { Text("추가") }, leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp)) })
        }
        // 고른 나라 → 문장 언어를 쓰는 나라 → 최근에 쓴 다른 나라 순.
        if (showCountries) ChipRow(ChipRowSpec(Icons.Default.Public, "어느 나라", (s.countries + countryChoicesFor(lang) + recentCountries).toList().distinct(), s.countries), vm::toggleCountry) {}
    }
    if (addingPerson) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingPerson = false },
            title = { Text("친구 이름") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("이름") }) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.togglePerson(name); addingPerson = false }) { Text("추가") } },
            dismissButton = { TextButton(onClick = { addingPerson = false }) { Text("취소") } },
        )
    }
}

private data class ChipRowSpec(val icon: ImageVector, val label: String, val names: List<String>, val selected: Set<String>)

@Composable
private fun ChipRow(spec: ChipRowSpec, onToggle: (String) -> Unit, trailing: @Composable () -> Unit) {
    val (icon, label, names, selected) = spec
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = label, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(names, key = { it }) { n -> ChoiceChip(selected = n in selected, onClick = { onToggle(n) }, label = { Text(n) }) }
            item { trailing() }
        }
    }
}
