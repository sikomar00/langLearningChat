package com.chatlearn.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 폴더: 문장은 폴더 하나에만 들어간다. 폴더를 지우면 문장은 남고 폴더만 비운다(SET NULL).
            db.execSQL("CREATE TABLE IF NOT EXISTS folder (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_folder_name ON folder (name)")
            db.execSQL("ALTER TABLE entry ADD COLUMN folderId INTEGER REFERENCES folder(id) ON UPDATE NO ACTION ON DELETE SET NULL")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_entry_folderId ON entry (folderId)")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE entry_text ADD COLUMN keyPhrase TEXT")
        }
    }

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 카드: 문장 하나에 언어별 카드를 허용한다.
            db.execSQL("DROP INDEX IF EXISTS index_review_card_entryId")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_review_card_entryId_answerLang ON review_card (entryId, answerLang)")
            // 태그: 마지막 사용 시각. 기존 값은 붙은 문장 중 가장 최근 저장 시각.
            db.execSQL("ALTER TABLE tag ADD COLUMN lastUsedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "UPDATE tag SET lastUsedAt = COALESCE((SELECT MAX(e.createdAt) FROM entry e " +
                    "JOIN entry_tag et ON et.entryId = e.id WHERE et.tagId = tag.id), 0)"
            )
            // 복습 기록: 응답 시간과 복습 방식. 기존 기록은 모두 쓰기 번역.
            db.execSQL("ALTER TABLE review_log ADD COLUMN responseMs INTEGER")
            db.execSQL("ALTER TABLE review_log ADD COLUMN exercise TEXT NOT NULL DEFAULT 'TRANSLATE'")
        }
    }
}
