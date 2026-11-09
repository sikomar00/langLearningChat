package com.chatlearn.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 기기 백업(Auto Backup)에 실어 보낼 JSON 스냅샷.
 *
 * DB 파일을 그대로 백업하지 않는 이유: 쓰는 도중 복사되거나 다른 앱 버전에 복원될 수 있고, 암호화된 키 저장소는
 * Keystore에 묶여 있어 새 기기에서 열리지 않는다. 그래서 백업 규칙(res/xml)은 [DIR_NAME] 폴더만 포함하고,
 * 그 안에는 수동 백업과 같은 형식([Backup])의 스냅샷 한 개만 둔다. API 키와 설정은 들어가지 않는다.
 */
class AutoBackup(
    private val dir: File,
    private val repo: EntryRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxAgeMs: Long = SNAPSHOT_MAX_AGE_MS,
) {
    val file: File get() = File(dir, FILE_NAME)

    /**
     * 스냅샷이 없거나 [maxAgeMs]보다 오래됐으면 새로 쓴다. 새로 썼으면 true.
     * 문장이 하나도 없으면 쓰지 않는다: 새 기기에서 복원하기 전의 빈 DB가 좋은 스냅샷을 덮어쓰면 안 된다.
     */
    suspend fun snapshotIfStale(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val f = file
            if (f.exists() && clock() - f.lastModified() < maxAgeMs) return@runCatching false
            val data = repo.exportAll()
            if (data.entries.isEmpty()) return@runCatching false
            dir.mkdirs()
            // 임시 파일에 다 쓴 뒤 바꿔치기해서, 쓰다 멈춰도 이전 스냅샷이 깨지지 않게 한다.
            val tmp = File(dir, "$FILE_NAME.tmp")
            tmp.writeText(Backup.encode(data))
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
            f.setLastModified(clock())
            true
        }.getOrDefault(false)
    }

    /** 라이브러리가 비어 있고 복원할 스냅샷이 있을 때 그 문장 수. 아니면 0. */
    suspend fun pendingRestoreCount(): Int = withContext(Dispatchers.IO) {
        if (repo.entryCount() > 0) 0 else restorableEntryCount()
    }

    /** 스냅샷에 든 문장 수. 없거나 읽을 수 없으면 0. */
    internal fun restorableEntryCount(): Int {
        val f = file
        if (!f.exists()) return 0
        return runCatching { Backup.decode(f.readText()).entries.size }.getOrDefault(0)
    }

    /** 스냅샷을 되살린다. 라이브러리에 문장이 하나라도 있으면 덮어쓰지 않고 실패한다. 성공하면 복원한 문장 수. */
    suspend fun restore(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            check(repo.entryCount() == 0) { "라이브러리가 비어 있지 않아요" }
            val data = Backup.decode(file.readText())
            repo.importAll(data)
            data.entries.size
        }
    }

    /** 스냅샷을 지운다(복원하지 않고 새로 시작할 때). */
    suspend fun discard() = withContext(Dispatchers.IO) { file.delete(); Unit }

    companion object {
        /** 백업 규칙(res/xml/backup_rules.xml, data_extraction_rules.xml)의 path와 같아야 한다. */
        const val DIR_NAME = "auto-backup"
        const val FILE_NAME = "snapshot.json"
        const val SNAPSHOT_MAX_AGE_MS = 12 * 60 * 60 * 1000L
    }
}
