package com.chatlearn.ui.library

import com.chatlearn.stats.EntryState
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import com.chatlearn.ui.components.EmptyState
import com.chatlearn.ui.components.LoadingBox
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Person
import com.chatlearn.ui.theme.board
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import java.time.ZoneId
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatlearn.data.LibraryItem
import com.chatlearn.domain.Lang

private val PickedSaver = androidx.compose.runtime.saveable.Saver<Set<Long>?, LongArray>(
    save = { it?.toLongArray() }, restore = { it.toSet() },
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    vm: LibraryViewModel, onGoTranslate: () -> Unit = {}, showGestureHint: Boolean = true, onDismissGestureHint: () -> Unit = {},
    onStartReview: (Set<Long>, Lang?) -> Unit = { _, _ -> },
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val lang by vm.langFilter.collectAsStateWithLifecycle()
    val tag by vm.tagFilter.collectAsStateWithLifecycle()
    val tags by vm.allTags.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val statusCounts by vm.statusCounts.collectAsStateWithLifecycle()
    val recentTags by vm.recentTags.collectAsStateWithLifecycle()
    // id만 기억하고 항목은 매번 최신 데이터에서 찾아서, 열린 상세 창이 확장 결과를 바로 반영하게 한다.
    var selectedId by remember { mutableStateOf<Long?>(null) }
    val everything by vm.everything.collectAsStateWithLifecycle()
    val selected = everything.firstOrNull { it.entry.id == selectedId }
    val openRequest by vm.openRequest.collectAsStateWithLifecycle()
    LaunchedEffect(openRequest) { openRequest?.let { selectedId = it; vm.openRequest.value = null } }
    val expandedIds by vm.expandedIds.collectAsStateWithLifecycle()
    var managingTags by remember { mutableStateOf(false) }
    val sort by vm.sort.collectAsStateWithLifecycle()
    val statusFilter by vm.statusFilter.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val pendingDelete by vm.pendingDelete.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val folderCounts by vm.folderCounts.collectAsStateWithLifecycle()
    val folderFilter by vm.folderFilter.collectAsStateWithLifecycle()
    val folderNames = remember(folders) { folders.associate { it.id to it.name } }
    // 폴더 선택 창을 띄울 문장들(스와이프는 1개, 선택 모드는 여러 개). null이면 닫힘.
    var moveTargets by remember { mutableStateOf<Set<Long>?>(null) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    var folderDialog by remember { mutableStateOf<FolderDialogKind?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    // 필터·정렬·검색을 바꾸면 목록 맨 위부터 보여 준다. 안 그러면 이전 위치에 남아 위쪽 항목이 사라진 것처럼 보인다.
    // 다른 탭에 다녀왔을 때는 필터가 그대로라 보던 위치를 유지한다.
    var lastFilterKey by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(statusFilter, sort, lang, tag, query, folderFilter) {
        val key = listOf(statusFilter, sort, lang, tag, query, folderFilter).toString()
        if (lastFilterKey != null && lastFilterKey != key) listState.scrollToItem(0)
        lastFilterKey = key
    }
    LaunchedEffect(pendingDelete) {
        if (pendingDelete != null) {
            // 처음 안내 알림이 떠 있어도 되돌리기가 그 뒤에 줄 서지 않게 먼저 닫는다.
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar("삭제했어요", actionLabel = "되돌리기", duration = SnackbarDuration.Indefinite)
            if (r == SnackbarResult.ActionPerformed) vm.undoDelete()
        } else snackbar.currentSnackbarData?.dismiss()
    }
    // 길게 눌러 들어가는 선택 모드. 필터가 바뀌어 사라진 항목은 선택에서 뺀다.
    var picked by rememberSaveable(stateSaver = PickedSaver) { mutableStateOf<Set<Long>?>(null) }
    BackHandler(enabled = picked != null) { picked = null }
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
            }
            val sendingToNotion by vm.sendingToNotion.collectAsStateWithLifecycle()
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(shape = MaterialTheme.shapes.medium, enabled = chosen.isNotEmpty(), onClick = { onStartReview(chosen, lang); picked = null }) { Text("선택 복습") }
                OutlinedButton(shape = MaterialTheme.shapes.medium, enabled = chosen.isNotEmpty() && !sendingToNotion, onClick = { vm.sendToNotion(chosen) }) { Text(if (sendingToNotion) "보내는 중…" else "노션") }
                OutlinedButton(shape = MaterialTheme.shapes.medium, enabled = chosen.isNotEmpty(), onClick = { moveTargets = chosen }) {
                    Icon(Icons.Default.Folder, contentDescription = null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("폴더로")
                }
                OutlinedButton(
                    shape = MaterialTheme.shapes.medium,
                    enabled = chosen.isNotEmpty(), onClick = { confirmBulkDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Icon(Icons.Default.Delete, contentDescription = null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("삭제") }
            }
        }
        val unexpanded by vm.unexpanded.collectAsStateWithLifecycle()
        val missingKeys by vm.missingKeyPhrases.collectAsStateWithLifecycle()
        val expanding by vm.expanding.collectAsStateWithLifecycle()
        val fillingKeys by vm.fillingKeyPhrases.collectAsStateWithLifecycle()
        var notice by remember { mutableStateOf<LibraryNotice?>(null) }
        val scope = rememberCoroutineScope()
        var showFilters by rememberSaveable { mutableStateOf(false) }
        // 보기·묶기 선택은 화면 취향이라 앱을 껐다 켜도 유지되게 따로 저장한다.
        val uiPrefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("ui_prefs", android.content.Context.MODE_PRIVATE)
        var groupBy by remember { mutableStateOf(GroupBy.entries.firstOrNull { it.name == uiPrefs.getString("library_group", null) } ?: GroupBy.STATE) }
        var compact by remember { mutableStateOf(uiPrefs.getBoolean("library_compact", true)) }
        var menuOpen by remember { mutableStateOf(false) }
        // 상태는 늘 보이는 칩 줄에 있으니 접힌 필터 수에는 세지 않는다.
        val activeFilters = listOf(lang != null, tag != null, sort != LibrarySort.RECENT).count { it }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // 폴더: 전체 / 폴더 없음 / 각 폴더 / 새 폴더
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                item { FilterChip(selected = folderFilter == null, onClick = { vm.folderFilter.value = null }, label = { Text("전체") }) }
                if (folders.isNotEmpty() && (folderCounts[FOLDER_UNFILED] ?: 0) > 0) item {
                    FilterChip(
                        selected = folderFilter == FOLDER_UNFILED, onClick = { vm.folderFilter.value = if (folderFilter == FOLDER_UNFILED) null else FOLDER_UNFILED },
                        label = { Text("폴더 없음 ${folderCounts[FOLDER_UNFILED] ?: 0}") },
                    )
                }
                items(folders, key = { it.id }) { f ->
                    FilterChip(
                        selected = folderFilter == f.id, onClick = { vm.folderFilter.value = if (folderFilter == f.id) null else f.id },
                        leadingIcon = { Icon(if (folderFilter == f.id) Icons.Default.FolderOpen else Icons.Default.Folder, contentDescription = null, Modifier.size(18.dp)) },
                        label = { Text("${f.name} ${folderCounts[f.id] ?: 0}") },
                    )
                }
                item { AssistChip(onClick = { folderDialog = FolderDialogKind.Create }, label = { Text("새 폴더") }, leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null, Modifier.size(18.dp)) }) }
            }
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
                                    notice = expandNotice(r.created, r.failedBatches)
                                }
                            },
                        )
                        if (missingKeys > 0) DropdownMenuItem(
                            text = { Text(if (fillingKeys) "찾는 중…" else "핵심 표현 만들기 ($missingKeys)") }, enabled = !fillingKeys,
                            onClick = {
                                menuOpen = false
                                scope.launch {
                                    val r = vm.fillKeyPhrases().await()
                                    notice = keyPhraseNotice(r.filled, r.none, r.failedBatches)
                                }
                            },
                        )
                    }
                }
            }
        }
        folders.firstOrNull { it.id == folderFilter }?.let { f ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("'${f.name}' 폴더", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                TextButton(onClick = { folderDialog = FolderDialogKind.Rename(f) }) { Text("이름 변경") }
                TextButton(onClick = { folderDialog = FolderDialogKind.Delete(f) }) { Text("폴더 삭제", color = MaterialTheme.colorScheme.error) }
            }
        }
        // 익힌 단계와 자동 묶음: 수가 있는 칩만, 수는 안내판 숫자로. 위 폴더 줄에 "전체"가 있어 여기서는 다시 누르면 풀린다.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(StatusFilter.entries.filter { it != StatusFilter.ALL && (it == statusFilter || (statusCounts[it] ?: 0) > 0) }) { f ->
                FilterChip(
                    selected = statusFilter == f, onClick = { vm.statusFilter.value = if (statusFilter == f) StatusFilter.ALL else f },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(f.label)
                            Text(" ${statusCounts[f] ?: 0}", style = MaterialTheme.typography.labelLarge.board())
                        }
                    },
                )
            }
        }
        if (showFilters) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                item { Text("언어·태그", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 4.dp)) }
                items(listOf(Lang.EN to "영어", Lang.ES to "스페인어")) { (l, label) ->
                    FilterChip(selected = lang == l, onClick = { vm.langFilter.value = if (lang == l) null else l }, label = { Text(label) })
                }
                items(tags - people.toSet()) { t ->
                    FilterChip(selected = tag == t, onClick = { vm.tagFilter.value = if (tag == t) null else t }, label = { Text(t) })
                }
            }
            if (people.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                item { Text("친구", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 4.dp)) }
                items(people) { t ->
                    FilterChip(
                        selected = tag == t, onClick = { vm.tagFilter.value = if (tag == t) null else t }, label = { Text(t) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, Modifier.size(18.dp)) },
                    )
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                items(LibrarySort.entries.toList()) { o ->
                    FilterChip(selected = sort == o, onClick = { vm.sort.value = o }, label = { Text(o.label) })
                }
                if (activeFilters > 0) item {
                    TextButton(onClick = {
                        vm.langFilter.value = null; vm.tagFilter.value = null
                        vm.sort.value = LibrarySort.RECENT
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
                sort.takeIf { it != LibrarySort.RECENT }?.label,
            ).joinToString(" · ")
            Text(summary, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        // 결과 안내는 잠시 보여 주고 저절로 지운다. 다시 해 볼 일이 남은 안내는 누를 때까지 둔다.
        val a11y = androidx.compose.ui.platform.LocalAccessibilityManager.current
        val notionReport by vm.notionReport.collectAsStateWithLifecycle()
        LaunchedEffect(notionReport) { notionReport?.let { notice = notionNotice(it); vm.notionReported() } }
        LaunchedEffect(notice) {
            val n = notice ?: return@LaunchedEffect
            if (n.sticky) return@LaunchedEffect
            kotlinx.coroutines.delay(a11y?.calculateRecommendedTimeoutMillis(LIBRARY_NOTICE_MS, containsText = true) ?: LIBRARY_NOTICE_MS)
            notice = null
        }
        notice?.let { Text(it.text, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClickLabel = "안내 닫기") { notice = null }) }
        moveTargets?.let { ids ->
            val anyInFolder = items.any { it.entry.id in ids && it.entry.folderId != null }
            FolderPickerDialog(
                folders = folders, counts = folderCounts, canRemove = anyInFolder,
                onPick = { fid ->
                    vm.moveToFolder(ids, fid)
                    notice = folderMoveNotice(ids.size, fid?.let { folderNames[it].orEmpty() })
                    moveTargets = null; picked = null
                },
                onCreate = { name ->
                    scope.launch {
                        val fid = vm.createFolder(name).await()
                        if (fid != null) {
                            vm.moveToFolder(ids, fid).join()
                            notice = folderMoveNotice(ids.size, name.trim())
                            moveTargets = null; picked = null
                        }
                    }
                },
                onDismiss = { moveTargets = null },
            )
        }
        if (confirmBulkDelete) AlertDialog(
            onDismissRequest = { confirmBulkDelete = false },
            title = { Text("${chosen.size}개를 삭제할까요?") },
            text = { Text("복습 기록도 함께 지워지고 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteMany(chosen); confirmBulkDelete = false; picked = null }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmBulkDelete = false }) { Text("취소") } },
        )
        folderDialog?.let { kind ->
            when (kind) {
                FolderDialogKind.Create -> NameDialog("새 폴더", "", "만들기", onDismiss = { folderDialog = null }) { name ->
                    val fid = vm.createFolder(name).await()
                    if (fid != null) { vm.folderFilter.value = fid; folderDialog = null; null } else "이름을 입력해 주세요."
                }
                is FolderDialogKind.Rename -> NameDialog("폴더 이름 변경", kind.folder.name, "변경", onDismiss = { folderDialog = null }) { name ->
                    if (vm.renameFolder(kind.folder.id, name).await()) { folderDialog = null; null } else "이미 있거나 비어 있는 이름이에요."
                }
                is FolderDialogKind.Delete -> AlertDialog(
                    onDismissRequest = { folderDialog = null },
                    title = { Text("'${kind.folder.name}' 폴더를 삭제할까요?") },
                    text = { Text("폴더만 지워지고 안의 문장은 그대로 남아요.") },
                    confirmButton = { TextButton(onClick = { vm.deleteFolder(kind.folder.id); folderDialog = null }) { Text("삭제", color = MaterialTheme.colorScheme.error) } },
                    dismissButton = { TextButton(onClick = { folderDialog = null }) { Text("취소") } },
                )
            }
        }
        // 첫 목록이 오기 전 잠깐 비어 보이는 것을 "없음"으로 오해하지 않게 한다.
        var settled by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(350); settled = true }
        if (items.isEmpty()) {
            if (!settled) LoadingBox()
            else if (folderFilter != null && statusFilter == StatusFilter.ALL && query.isBlank() && lang == null && tag == null) {
                EmptyState("이 폴더는 비어 있어요.\n문장을 오른쪽으로 밀어 폴더에 넣어 보세요.", icon = Icons.Default.FolderOpen)
            } else if (statusFilter != StatusFilter.ALL || query.isNotBlank() || lang != null || tag != null) {
                EmptyState("조건에 맞는 문장이 없어요.", icon = Icons.Default.SearchOff)
            } else EmptyState("저장된 문장이 없어요.\n번역 탭에서 문장을 저장해 보세요.", icon = Icons.Default.Book, actionLabel = "번역하러 가기", onAction = onGoTranslate)
        }
        // 한 번 익히면 필요 없는 안내: 첫 화면을 차지하지 않게 한 번만 알림으로 띄우고, 닫으면 다시 나오지 않는다.
        if (items.isNotEmpty() && showGestureHint && picked == null) LaunchedEffect(Unit) {
            snackbar.showSnackbar("길게 누르면 여러 개 선택 · 오른쪽으로 밀면 폴더 이동 · 왼쪽으로 밀면 삭제", actionLabel = "알겠어요", duration = SnackbarDuration.Long)
            onDismissGestureHint()
        }
        // 많이 틀린 순으로 볼 때는 순서 자체가 의미라서 묶지 않는다.
        val effective = if (sort == LibrarySort.LAPSES) GroupBy.NONE else groupBy
        val now = System.currentTimeMillis()
        val today = java.time.LocalDate.now()
        val groups: List<LibGroup> = remember(effective, items, progress, today) {
            when (effective) {
                GroupBy.STATE -> groupByState(items, progress).map { (st, l) -> LibGroup("s-$st", boardTitle(st), l, st) }
                GroupBy.PERSON -> groupByPerson(items).mapIndexed { i, (t, l) -> LibGroup("p-$i", t, l, null) }
                GroupBy.DATE -> groupByDate(items, ZoneId.systemDefault(), today).map { (t, l) -> LibGroup("d-$t", t, l, null) }
                GroupBy.TAG -> groupByTag(items).map { (t, l) -> LibGroup("t-$t", t, l, null) }
                GroupBy.NONE -> listOf(LibGroup("all", null, items, null))
            }
        }
        if (compact && items.isNotEmpty() && picked == null) BoardColumns()
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
                    val folderName = item.entry.folderId?.let { folderNames[it] }
                    SwipeRow(
                        enabled = picked == null,
                        onMove = { moveTargets = setOf(id) },
                        onDelete = { if (selectedId == id) selectedId = null; vm.delete(id) },
                    ) {
                        if (compact) CompactRow(item, p, now, folderName, picked != null, id in chosen, onClick, onLongClick, showPerson = effective != GroupBy.PERSON)
                        else EntryCard(item, badge, p?.state == EntryState.DUE, folderName, picked != null, id in chosen, onClick, onLongClick)
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { SnackbarHost(snackbar) }
    selected?.let { item -> DetailDialog(item, item.entry.id in expandedIds, recentTags, folders, vm, onReview = { onStartReview(setOf(item.entry.id), null); selectedId = null }) { selectedId = null } }
    if (managingTags) TagManagerDialog(tags, vm) { managingTags = false }
}

private class LibGroup(val key: String, val title: String?, val items: List<LibraryItem>, val state: EntryState?)
