package com.chatlearn.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatlearn.ui.components.BoardLabel
import com.chatlearn.ui.theme.board
import com.chatlearn.ui.theme.statusColors

/**
 * 라이브러리 편성표: 왼쪽에서 열리는 검은 안내판 서랍. 어디를 볼지(폴더 · 친구 · 상태)를 세로 목록으로 고른다.
 * 고르면 닫힌다. 폴더와 상태는 하나씩, 친구는 태그 필터를 쓴다.
 */
@Composable
internal fun LibraryDrawer(vm: LibraryViewModel, onNewFolder: () -> Unit, close: () -> Unit) {
    val folders by vm.folders.collectAsStateWithLifecycle()
    val folderCounts by vm.folderCounts.collectAsStateWithLifecycle()
    val folderFilter by vm.folderFilter.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val tag by vm.tagFilter.collectAsStateWithLifecycle()
    val statusFilter by vm.statusFilter.collectAsStateWithLifecycle()
    val statusCounts by vm.statusCounts.collectAsStateWithLifecycle()
    val everything by vm.everything.collectAsStateWithLifecycle()
    val personCounts = remember(everything, people) { people.associateWith { p -> everything.count { p in it.tags } } }
    val c = MaterialTheme.statusColors
    val pick = { action: () -> Unit -> action(); close() }

    ModalDrawerSheet(Modifier.width(288.dp), drawerContainerColor = c.board, drawerContentColor = c.onBoard, drawerShape = MaterialTheme.shapes.medium) {
        LazyColumn(contentPadding = PaddingValues(vertical = 16.dp)) {
            item { DrawerHeading("폴더") }
            item {
                DrawerRow("전체", folderCounts.values.sum(), folderFilter == null, Icons.Default.FolderOpen) { pick { vm.folderFilter.value = null } }
            }
            if (folders.isNotEmpty() && (folderCounts[FOLDER_UNFILED] ?: 0) > 0) item {
                DrawerRow("폴더 없음", folderCounts[FOLDER_UNFILED] ?: 0, folderFilter == FOLDER_UNFILED, Icons.Default.Folder) {
                    pick { vm.folderFilter.value = FOLDER_UNFILED }
                }
            }
            items(folders, key = { it.id }) { f ->
                DrawerRow(f.name, folderCounts[f.id] ?: 0, folderFilter == f.id, Icons.Default.Folder) { pick { vm.folderFilter.value = f.id } }
            }
            item { DrawerRow("새 폴더", null, false, Icons.Default.CreateNewFolder) { pick(onNewFolder) } }
            if (people.isNotEmpty()) {
                item { DrawerHeading("친구") }
                items(people) { p ->
                    DrawerRow(p, personCounts[p] ?: 0, tag == p, Icons.Default.Person) { pick { vm.tagFilter.value = if (tag == p) null else p } }
                }
            }
            item { DrawerHeading("상태") }
            item { DrawerRow("모든 상태", null, statusFilter == StatusFilter.ALL, null) { pick { vm.statusFilter.value = StatusFilter.ALL } } }
            items(StatusFilter.entries.filter { it != StatusFilter.ALL && (it == statusFilter || (statusCounts[it] ?: 0) > 0) }) { f ->
                DrawerRow(f.label, statusCounts[f] ?: 0, statusFilter == f, null) { pick { vm.statusFilter.value = if (statusFilter == f) StatusFilter.ALL else f } }
            }
        }
    }
}

@Composable
private fun DrawerHeading(text: String) {
    BoardLabel(
        text, Modifier.padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 8.dp).semantics { heading() },
        color = MaterialTheme.statusColors.onBoardMuted,
    )
}

/** 서랍 한 줄: 아이콘 · 이름 · 수(안내판 숫자). 고른 줄은 호박(지금 보는 곳). */
@Composable
private fun DrawerRow(label: String, count: Int?, selected: Boolean, icon: ImageVector?, onClick: () -> Unit) {
    val c = MaterialTheme.statusColors
    NavigationDrawerItem(
        label = { Text(label, maxLines = 1) },
        selected = selected, onClick = onClick,
        icon = icon?.let { { Icon(it, contentDescription = null, Modifier.size(20.dp)) } },
        badge = count?.let { { Text("$it", style = MaterialTheme.typography.labelLarge.board()) } },
        shape = MaterialTheme.shapes.medium,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = c.flap, selectedTextColor = c.onFlap, selectedIconColor = c.onFlap, selectedBadgeColor = c.onFlap,
            unselectedContainerColor = c.board, unselectedTextColor = c.onBoard, unselectedIconColor = c.onBoardMuted, unselectedBadgeColor = c.onBoardMuted,
        ),
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}
