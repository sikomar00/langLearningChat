package com.chatlearn.settings

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.time.LocalDate

private const val WEEK_MS = 7 * 86_400_000L
private const val PREFIX = "chatlearn-auto-"
private const val KEEP = 4

/** 폴더를 골라 두었고 마지막 백업에서 7일이 지났으면 자동 백업할 때다. */
fun autoBackupDue(folderSet: Boolean, lastBackupAt: Long, now: Long): Boolean = folderSet && now - lastBackupAt >= WEEK_MS

fun autoBackupName(day: LocalDate) = "$PREFIX$day.json"

/** 자동 백업 파일 가운데 최근 [keep]개를 빼고 지울 것(날짜가 이름에 있어 이름순 = 날짜순). 다른 파일은 건드리지 않는다. */
fun staleAutoBackups(names: List<String>, keep: Int = KEEP): List<String> =
    names.filter { it.startsWith(PREFIX) && it.endsWith(".json") }.sorted().dropLast(keep)

/**
 * 고른 폴더(저장소 접근 프레임워크 트리)에 오늘 날짜 백업 파일을 만들고, 오래된 자동 백업은 지운다.
 * 라이브러리 없이 DocumentsContract만 쓴다. 실패하면 예외를 그대로 올려 호출부가 알린다.
 */
fun writeAutoBackup(context: Context, tree: Uri, day: LocalDate, json: String) {
    val resolver = context.contentResolver
    val treeId = DocumentsContract.getTreeDocumentId(tree)
    val dir = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
    val file = DocumentsContract.createDocument(resolver, dir, "application/json", autoBackupName(day)) ?: error("백업 파일을 만들 수 없어요")
    try {
        resolver.openOutputStream(file, "wt")?.use { it.write(json.toByteArray()) } ?: error("백업 파일을 열 수 없어요")
    } catch (e: Exception) {
        // 반쯤 쓴 파일이 남으면 "최근 4개"에 끼어 멀쩡한 백업을 밀어낸다.
        runCatching { DocumentsContract.deleteDocument(resolver, file) }
        throw e
    }
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
    val existing = buildMap {
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) put(c.getString(1), c.getString(0))
        }
    }
    staleAutoBackups(existing.keys.toList()).forEach { name ->
        runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, existing.getValue(name))) }
    }
}
