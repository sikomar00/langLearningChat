package com.chatlearn.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.foundation.background
import com.chatlearn.ui.theme.statusColors
import com.chatlearn.ui.components.EmptyState
import com.chatlearn.ui.components.LabeledText
import com.chatlearn.ui.components.SectionCard
import com.chatlearn.ui.components.LoadingBox
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import com.chatlearn.data.RenameResult
import com.chatlearn.domain.TextOrigin
import com.chatlearn.stats.StatsCalculator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.chatlearn.ui.translate.parseTags
import com.chatlearn.ui.translate.suggestTags
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatlearn.data.LibraryItem
import com.chatlearn.domain.Lang

private const val AI_FAILED_HINT = "AI에 연결하지 못했어요. 설정 탭의 '연결 확인'으로 키와 모델, 인터넷을 확인해 주세요."

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(vm: LibraryViewModel, onGoTranslate: () -> Unit = {}, onStartReview: (Set<Long>, Lang?) -> Unit = { _, _ -> }) {
    val items by vm.items.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val lang by vm.langFilter.collectAsStateWithLifecycle()
    val tag by vm.tagFilter.collectAsStateWithLifecycle()
    val tags by vm.allTags.collectAsStateWithLifecycle()
    val recentTags by vm.recentTags.collectAsStateWithLifecycle()
    // id만 기억하고 항목은 매번 최신 데이터에서 찾아서, 열린 상세 창이 확장 결과를 바로 반영하게 한다.
    var selectedId by remember { mutableStateOf<Long?>(null) }
    val selected = items.firstOrNull { it.entry.id == selectedId }
    val expandedIds by vm.expandedIds.collectAsStateWithLifecycle()
    var managingTags by remember { mutableStateOf(false) }
    val sort by vm.sort.collectAsStateWithLifecycle()
    val statusFilter by vm.statusFilter.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val pendingDelete by vm.pendingDelete.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    // 필터·정렬·검색을 바꾸면 목록 맨 위부터 보여 준다. 안 그러면 이전 위치에 남아 위쪽 항목이 사라진 것처럼 보인다.
    LaunchedEffect(statusFilter, sort, lang, tag, query) { listState.scrollToItem(0) }
    LaunchedEffect(pendingDelete) {
        if (pendingDelete != null) {
            val r = snackbar.showSnackbar("삭제했어요", actionLabel = "되돌리기", duration = SnackbarDuration.Indefinite)
            if (r == SnackbarResult.ActionPerformed) vm.undoDelete()
        } else snackbar.currentSnackbarData?.dismiss()
    }
    // 길게 눌러 들어가는 선택 모드. 필터가 바뀌어 사라진 항목은 선택에서 뺀다.
    var picked by remember { mutableStateOf<Set<Long>?>(null) }
    val visibleIds = items.map { it.entry.id }.toSet()
    val chosen = picked?.intersect(visibleIds).orEmpty()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (picked != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${chosen.size}개 선택", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { picked = if (chosen.size == visibleIds.size) emptySet() else visibleIds }) {
                    Text(if (chosen.size == visibleIds.size) "선택 해제" else "전체 선택")
                }
                TextButton(onClick = { picked = null }) { Text("취소") }
                Button(enabled = chosen.isNotEmpty(), onClick = { onStartReview(chosen, lang); picked = null }) { Text("선택 복습") }
            }
        }
        val unexpanded by vm.unexpanded.collectAsStateWithLifecycle()
        val missingKeys by vm.missingKeyPhrases.collectAsStateWithLifecycle()
        val expanding by vm.expanding.collectAsStateWithLifecycle()
        val fillingKeys by vm.fillingKeyPhrases.collectAsStateWithLifecycle()
        var message by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        var showFilters by rememberSaveable { mutableStateOf(false) }
        // 보기·묶기 선택은 화면 취향이라 앱을 껐다 켜도 유지되게 따로 저장한다.
        val uiPrefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("ui_prefs", android.content.Context.MODE_PRIVATE)
        var groupBy by remember { mutableStateOf(GroupBy.entries.firstOrNull { it.name == uiPrefs.getString("library_group", null) } ?: GroupBy.STATE) }
        var compact by remember { mutableStateOf(uiPrefs.getBoolean("library_compact", true)) }
        var menuOpen by remember { mutableStateOf(false) }
        val activeFilters = listOf(lang != null, tag != null, statusFilter != StatusFilter.ALL, sort != LibrarySort.RECENT).count { it }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(value = query, onValueChange = { vm.query.value = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("검색") })
            TextButton(onClick = { showFilters = !showFilters }) { Text(if (activeFilters > 0) "필터 · $activeFilters" else "필터") }
            if (tags.isNotEmpty() || unexpanded > 0 || missingKeys > 0) {
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "더 보기") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (tags.isNotEmpty()) DropdownMenuItem(text = { Text("태그 관리") }, onClick = { menuOpen = false; managingTags = true })
                        if (unexpanded > 0) DropdownMenuItem(
                            text = { Text(if (expanding) "확장 중…" else "전체 확장 ($unexpanded)") }, enabled = !expanding,
                            onClick = {
                                menuOpen = false
                                scope.launch {
                                    val r = vm.expandAll().await()
                                    message = if (r.created == 0 && r.failedBatches > 0) AI_FAILED_HINT
                                    else "다른 언어 카드 ${r.created}개를 만들었어요." +
                                        if (r.failedBatches > 0) " (실패한 묶음 ${r.failedBatches}개는 다시 눌러 보세요)" else ""
                                }
                            },
                        )
                        if (missingKeys > 0) DropdownMenuItem(
                            text = { Text(if (fillingKeys) "찾는 중…" else "핵심 표현 만들기 ($missingKeys)") }, enabled = !fillingKeys,
                            onClick = {
                                menuOpen = false
                                scope.launch {
                                    val r = vm.fillKeyPhrases().await()
                                    message = if (r.filled == 0 && r.none == 0 && r.failedBatches > 0) AI_FAILED_HINT
                                    else "핵심 표현 ${r.filled}개를 찾았어요." +
                                        if (r.failedBatches > 0) " (실패한 묶음 ${r.failedBatches}개는 다시 눌러 보세요)" else ""
                                }
                            },
                        )
                    }
                }
            }
        }
        if (showFilters) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(Lang.EN to "영어", Lang.ES to "스페인어")) { (l, label) ->
                    FilterChip(selected = lang == l, onClick = { vm.langFilter.value = if (lang == l) null else l }, label = { Text(label) })
                }
                items(tags) { t ->
                    FilterChip(selected = tag == t, onClick = { vm.tagFilter.value = if (tag == t) null else t }, label = { Text(t) })
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(StatusFilter.entries.toList()) { f ->
                    FilterChip(selected = statusFilter == f, onClick = { vm.statusFilter.value = f }, label = { Text(f.label) })
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                items(LibrarySort.entries.toList()) { o ->
                    FilterChip(selected = sort == o, onClick = { vm.sort.value = o }, label = { Text(o.label) })
                }
                if (activeFilters > 0) item {
                    TextButton(onClick = {
                        vm.langFilter.value = null; vm.tagFilter.value = null
                        vm.statusFilter.value = StatusFilter.ALL; vm.sort.value = LibrarySort.RECENT
                    }) { Text("초기화") }
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                item { Text("묶기", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 4.dp)) }
                items(GroupBy.entries.toList()) { g ->
                    FilterChip(selected = groupBy == g, onClick = { groupBy = g; showFilters = false; uiPrefs.edit().putString("library_group", g.name).apply() }, label = { Text(g.label) })
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                item { Text("보기", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 4.dp)) }
                item { FilterChip(selected = compact, onClick = { compact = true; showFilters = false; uiPrefs.edit().putBoolean("library_compact", true).apply() }, label = { Text("촘촘히") }) }
                item { FilterChip(selected = !compact, onClick = { compact = false; showFilters = false; uiPrefs.edit().putBoolean("library_compact", false).apply() }, label = { Text("카드") }) }
            }
        } else if (activeFilters > 0) {
            // 접어 둔 상태에서도 걸려 있는 조건을 알 수 있게 한 줄로 보여 준다.
            val summary = listOfNotNull(
                lang?.let { if (it == Lang.EN) "영어" else "스페인어" }, tag,
                statusFilter.takeIf { it != StatusFilter.ALL }?.label, sort.takeIf { it != LibrarySort.RECENT }?.label,
            ).joinToString(" · ")
            Text(summary, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { message = null }) }
        // 첫 목록이 오기 전 잠깐 비어 보이는 것을 "없음"으로 오해하지 않게 한다.
        var settled by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(350); settled = true }
        if (items.isEmpty()) {
            if (!settled) LoadingBox()
            else if (statusFilter != StatusFilter.ALL || query.isNotBlank() || lang != null || tag != null) {
                EmptyState("조건에 맞는 문장이 없어요.", icon = Icons.Default.SearchOff)
            } else EmptyState("저장된 문장이 없어요.\n번역 탭에서 문장을 저장해 보세요.", icon = Icons.Default.Book, actionLabel = "번역하러 가기", onAction = onGoTranslate)
        } else if (picked == null) {
            Text("문장을 길게 누르면 여러 개를 골라 복습할 수 있어요.", style = MaterialTheme.typography.bodySmall)
        }
        // 많이 틀린 순으로 볼 때는 순서 자체가 의미라서 묶지 않는다.
        val effective = if (sort == LibrarySort.LAPSES) GroupBy.NONE else groupBy
        val now = System.currentTimeMillis()
        val groups: List<LibGroup> = when (effective) {
            GroupBy.STATE -> groupByState(items, progress).map { (st, l) -> LibGroup("s-$st", stateTitle(st), l, st) }
            GroupBy.DATE -> groupByDate(items, ZoneId.systemDefault(), java.time.LocalDate.now()).map { (t, l) -> LibGroup("d-$t", t, l, null) }
            GroupBy.TAG -> groupByTag(items).map { (t, l) -> LibGroup("t-$t", t, l, null) }
            GroupBy.NONE -> listOf(LibGroup("all", null, items, null))
        }
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 8.dp)) {
            groups.forEach { g ->
                if (g.title != null) stickyHeader(key = "header-${g.key}") { GroupHeader(g.title, g.items.size) }
                items(g.items, key = { it.entry.id }) { item ->
                    val id = item.entry.id
                    val p = progress[id]
                    // 묶음 제목이 상태를 말해 주면 배지는 중복이라 생략한다. "나중에" 묶음은 남은 기간을 보여 준다.
                    val badge = p?.let { if (g.state == null || g.state == EntryState.LATER) dueBadge(it, now) else null }
                    val onClick = { if (picked != null) picked = if (id in chosen) chosen - id else chosen + id else selectedId = id }
                    val onLongClick = { if (picked == null) picked = setOf(id) }
                    if (compact) CompactRow(item, badge, p?.state == EntryState.DUE, p?.state ?: EntryState.NEW, picked != null, id in chosen, onClick, onLongClick)
                    else EntryCard(item, badge, p?.state == EntryState.DUE, p?.state ?: EntryState.NEW, picked != null, id in chosen, onClick, onLongClick)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { SnackbarHost(snackbar) }
    selected?.let { item -> DetailDialog(item, item.entry.id in expandedIds, recentTags, vm) { selectedId = null } }
    if (managingTags) TagManagerDialog(tags, vm) { managingTags = false }
}

@Composable
private fun DetailDialog(item: LibraryItem, hasAltCard: Boolean, recentTags: List<String>, vm: LibraryViewModel, onClose: () -> Unit) {
    var ko by remember(item.entry.id) { mutableStateOf(item.texts[Lang.KO].orEmpty()) }
    var note by remember(item.entry.id) { mutableStateOf(item.entry.note) }
    val altLang = if (item.entry.chatLang == Lang.EN) Lang.ES else Lang.EN
    val altText = item.texts[altLang]
    var alt by remember(item.entry.id, altText) { mutableStateOf(altText.orEmpty()) }
    var chatKey by remember(item.entry.id, item.keyPhrases[item.entry.chatLang]) { mutableStateOf(item.keyPhrases[item.entry.chatLang].orEmpty()) }
    var altKey by remember(item.entry.id, item.keyPhrases[altLang]) { mutableStateOf(item.keyPhrases[altLang].orEmpty()) }
    val editedTags = remember(item.entry.id) { mutableStateListOf(*item.tags.toTypedArray()) }
    var tagInput by remember(item.entry.id) { mutableStateOf("") }

    fun addTags(names: List<String>) = names.forEach { if (it !in editedTags) editedTags.add(it) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "닫기") }
                    Text(item.texts[item.entry.chatLang].orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    TextButton(onClick = {
                        vm.saveText(item.entry.id, Lang.KO, ko); vm.saveNote(item.entry.id, note)
                        if (hasAltCard && altText != null && alt.isNotBlank() && alt != altText) vm.saveText(item.entry.id, altLang, alt)
                        // 입력창에 쓰다 만 태그도 잃지 않게 함께 저장한다.
                        vm.saveTags(item.entry.id, (editedTags + parseTags(tagInput)).distinct())
                        if (chatKey != item.keyPhrases[item.entry.chatLang].orEmpty()) vm.saveKeyPhrase(item.entry.id, item.entry.chatLang, chatKey)
                        if (hasAltCard && altText != null && altKey != item.keyPhrases[altLang].orEmpty()) vm.saveKeyPhrase(item.entry.id, altLang, altKey)
                        onClose()
                    }) { Text("저장") }
                }
                HorizontalDivider()
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SentenceStatsLine(item.entry.id, vm)
                SectionCard("문장") {
                    LabeledText(if (item.entry.chatLang == Lang.EN) "영어 원문" else "스페인어 원문", item.texts[item.entry.chatLang].orEmpty(), valueStyle = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(ko, { ko = it }, label = { Text("한국어 뜻") }, modifier = Modifier.fillMaxWidth())
                    if (hasAltCard && altText != null) {
                        OutlinedTextField(
                            alt, { alt = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (altLang == Lang.ES) "스페인어 확장" else "영어 확장") },
                            supportingText = { if (item.origins[altLang] == TextOrigin.AI) Text("AI 번역 · 고치면 내 문장으로 저장돼요") },
                        )
                    } else if (!hasAltCard) {
                        var making by remember { mutableStateOf(false) }
                        var failed by remember { mutableStateOf(false) }
                        val scope2 = rememberCoroutineScope()
                        OutlinedButton(enabled = !making, onClick = {
                            making = true; failed = false
                            scope2.launch { failed = !vm.expandOne(item.entry.id).await(); making = false }
                        }) { Text(if (making) "만드는 중…" else "다른 언어 카드 만들기") }
                        if (failed) Text("만들지 못했어요. 잠시 후 다시 시도해 주세요.", color = MaterialTheme.colorScheme.error)
                    }
                }
                SectionCard("핵심 표현") {
                    Text("빈칸 채우기에서 이 표현이 빈칸이 돼요. 문장 안에 있는 그대로 적어야 해요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        chatKey, { chatKey = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(if (item.entry.chatLang == Lang.EN) "영어" else "스페인어") },
                    )
                    if (hasAltCard && altText != null) {
                        OutlinedTextField(
                            altKey, { altKey = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(if (altLang == Lang.ES) "스페인어" else "영어") },
                        )
                    }
                }
                SectionCard("설명 · 메모") {
                    OutlinedTextField(note, { note = it }, label = { Text("설명") }, modifier = Modifier.fillMaxWidth())
                }
                SectionCard("태그") {
                    if (editedTags.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(editedTags.toList()) { tag ->
                                InputChip(
                                    selected = false, onClick = { editedTags.remove(tag) }, label = { Text(tag) },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "$tag 제거", modifier = Modifier.size(18.dp)) },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = tagInput, onValueChange = { tagInput = it }, singleLine = true,
                        label = { Text("태그 추가(쉼표로 여러 개)") }, modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            TextButton(onClick = { addTags(parseTags(tagInput)); tagInput = "" }, enabled = tagInput.isNotBlank()) { Text("추가") }
                        },
                    )
                    val suggestions = suggestTags(recentTags.filter { it !in editedTags }, tagInput)
                    if (suggestions.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(suggestions) { tag ->
                                SuggestionChip(onClick = { addTags(listOf(tag)); tagInput = "" }, label = { Text(tag) })
                            }
                        }
                    }
                }
                    HorizontalDivider()
                    TextButton(onClick = { vm.delete(item.entry.id); onClose() }) {
                        Text("이 문장 삭제", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun TagManagerDialog(tags: List<String>, vm: LibraryViewModel, onClose: () -> Unit) {
    var editing by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("태그 관리") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("이름을 바꾸면 모든 문장에 적용돼요. 이미 있는 이름으로 바꾸면 두 태그가 합쳐져요.", style = MaterialTheme.typography.bodySmall)
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                tags.forEach { tag ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tag, Modifier.weight(1f))
                        IconButton(onClick = { editing = tag; newName = tag }) { Icon(Icons.Default.Edit, contentDescription = "$tag 이름 바꾸기") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("닫기") } },
    )

    editing?.let { old ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("'$old' 이름 바꾸기") },
            text = { OutlinedTextField(newName, { newName = it }, singleLine = true, label = { Text("새 이름") }) },
            confirmButton = {
                TextButton(enabled = newName.isNotBlank(), onClick = {
                    scope.launch {
                        message = when (vm.renameTag(old, newName).await()) {
                            RenameResult.Renamed -> "'$old' → '${newName.trim()}'로 바꿨어요."
                            RenameResult.Merged -> "'${newName.trim()}'와 합쳤어요."
                            RenameResult.Unchanged -> "바뀐 게 없어요."
                        }
                        editing = null
                    }
                }) { Text("바꾸기") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun SentenceStatsLine(entryId: Long, vm: LibraryViewModel) {
    val input by vm.statsInput.collectAsStateWithLifecycle()
    val st = input?.let { StatsCalculator.sentenceStats(entryId, it.logs, it.cards) } ?: return
    val fmt = remember { DateTimeFormatter.ofPattern("M월 d일 HH:mm") }
    fun at(ms: Long?) = ms?.let { fmt.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) } ?: "-"
    SectionCard("학습 기록") {
        Text(
            if (st.reviews == 0) "아직 복습하지 않았어요."
            else "복습 ${st.reviews}회 · 기억 ${st.remembered}회 · 틀림 ${st.lapses}회",
            style = MaterialTheme.typography.bodyLarge,
        )
        LabeledText("마지막 복습", at(st.lastReviewedAt))
        LabeledText("다음 복습", at(st.dueAt))
    }
}

@Composable
private fun GroupHeader(title: String, count: Int) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun Pill(text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Surface(color = container, shape = MaterialTheme.shapes.small) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryCard(
    item: LibraryItem, badge: String?, badgeAccent: Boolean, state: EntryState,
    selectionMode: Boolean, checked: Boolean, onClick: () -> Unit, onLongClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        // 왼쪽 색 막대: 복습할 차례=보라, 새 카드=회색, 나중에 복습(이미 복습함)=초록
        val barColor = when (state) {
            EntryState.DUE -> MaterialTheme.colorScheme.primary
            EntryState.NEW -> MaterialTheme.colorScheme.outline
            EntryState.LATER -> MaterialTheme.statusColors.success
        }
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(barColor))
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) Checkbox(checked = checked, onCheckedChange = null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // 1줄: 언어·태그 칩, 오른쪽에 상태 배지
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(if (item.entry.chatLang == Lang.EN) "EN" else "ES", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    item.tags.take(2).forEach { Pill(it, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }
                    if (item.tags.size > 2) Text("+${item.tags.size - 2}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    badge?.let {
                        Text(
                            it, style = MaterialTheme.typography.labelMedium,
                            color = if (badgeAccent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(item.texts[item.entry.chatLang].orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(item.texts[Lang.KO].orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            }
        }
    }
}

private class LibGroup(val key: String, val title: String?, val items: List<LibraryItem>, val state: EntryState?)

/** 한 줄에 한 문장: 외국어 한 줄 + 한국어 한 줄. 정보 밀도를 높인 기본 보기. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactRow(
    item: LibraryItem, badge: String?, badgeAccent: Boolean, state: EntryState,
    selectionMode: Boolean, checked: Boolean, onClick: () -> Unit, onLongClick: () -> Unit,
) {
    val barColor = when (state) {
        EntryState.DUE -> MaterialTheme.colorScheme.primary
        EntryState.NEW -> MaterialTheme.colorScheme.outline
        EntryState.LATER -> MaterialTheme.statusColors.success
    }
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Row(Modifier.height(IntrinsicSize.Min).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).fillMaxHeight().padding(vertical = 6.dp).background(barColor, MaterialTheme.shapes.extraSmall))
            if (selectionMode) Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 8.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(item.texts[item.entry.chatLang].orEmpty(), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(item.texts[Lang.KO].orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Column(Modifier.padding(end = 4.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Pill(if (item.entry.chatLang == Lang.EN) "EN" else "ES", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                badge?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = if (badgeAccent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
