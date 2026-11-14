package com.chatlearn.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 문장: 단어 카드, 대화 맥락, 노션 페이지. 기존 문장은 모두 문장 카드.
            db.execSQL("ALTER TABLE entry ADD COLUMN kind TEXT NOT NULL DEFAULT 'SENTENCE'")
            db.execSQL("ALTER TABLE entry ADD COLUMN contextText TEXT")
            db.execSQL("ALTER TABLE entry ADD COLUMN notionPageId TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_entry_createdAt ON entry (createdAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_entry_text_lang_text ON entry_text (lang, text)")
            // FSRS: 복습한 카드는 지금 간격을 안정도로(목표 기억률 90%에서 간격 = 안정도), ease를 난이도로 옮긴다.
            // ease 2.5(보통) → 5, 낮을수록 어렵게(1.3 → 10). 아직 안 본 카드는 첫 복습 때 정한다.
            db.execSQL("ALTER TABLE review_card ADD COLUMN stability REAL")
            db.execSQL("ALTER TABLE review_card ADD COLUMN difficulty REAL")
            db.execSQL(
                "UPDATE review_card SET stability = intervalDays, difficulty = MIN(10.0, MAX(1.0, 5.0 - (ease - 2.5) * 5.0)) " +
                    "WHERE lastReviewedAt IS NOT NULL AND intervalDays > 0"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_review_log_reviewedAt ON review_log (reviewedAt)")
            // 태그 종류: 기존 태그는 모두 주제.
            db.execSQL("ALTER TABLE tag ADD COLUMN kind TEXT NOT NULL DEFAULT 'TOPIC'")
            // 문법 약점 기록.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS grammar_event (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, lang TEXT NOT NULL, " +
                    "pointKey TEXT NOT NULL, source TEXT NOT NULL, isError INTEGER NOT NULL, entryId INTEGER, at INTEGER NOT NULL, " +
                    "FOREIGN KEY(entryId) REFERENCES entry(id) ON UPDATE NO ACTION ON DELETE SET NULL)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_grammar_event_pointKey ON grammar_event (pointKey)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_grammar_event_entryId ON grammar_event (entryId)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS entry_grammar (entryId INTEGER NOT NULL, pointKey TEXT NOT NULL, PRIMARY KEY(entryId, pointKey), " +
                    "FOREIGN KEY(entryId) REFERENCES entry(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_entry_grammar_pointKey ON entry_grammar (pointKey)")
        }
    }

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

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 문장 끝 상태: 기존 문장은 모두 복습하고 통계에 넣는다.
            db.execSQL("ALTER TABLE entry ADD COLUMN known INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE entry ADD COLUMN excludeFromStats INTEGER NOT NULL DEFAULT 0")
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
}
