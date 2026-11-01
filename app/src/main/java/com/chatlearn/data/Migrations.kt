package com.chatlearn.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
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
