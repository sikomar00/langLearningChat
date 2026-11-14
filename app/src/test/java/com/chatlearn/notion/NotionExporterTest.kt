package com.chatlearn.notion

import com.chatlearn.data.*
import com.chatlearn.domain.Direction
import com.chatlearn.domain.Lang
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
class NotionExporterTest {
    private lateinit var server: MockWebServer
    private lateinit var db: AppDatabase
    private lateinit var repo: EntryRepository
    private lateinit var exporter: NotionExporter

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        db = inMemoryDb(); repo = EntryRepository(db)
        val client = NotionClient(OkHttpClient(), server.url("/").toString().trimEnd('/')) { "t" }
        exporter = NotionExporter(repo, client) { "https://www.notion.so/me/Words-0123456789abcdef0123456789abcdef" }
    }
    @After fun tearDown() { db.close(); runCatching { server.shutdown() } }

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)
    private fun connect() {
        server.enqueue(ok("""{"data_sources":[{"id":"ds1"}]}"""))
        server.enqueue(ok("""{"properties":{"문장":{"type":"title"},"뜻":{"type":"rich_text"},"사람":{"type":"multi_select"},"문법":{"type":"multi_select"}}}"""))
    }

    @Test fun sendsSelectedSentencesAndRemembersTheirPagesSoTheNextSendUpdates() = runBlocking {
        val id = (repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.ES, "Estoy cansado", "피곤해", "", null, emptyList(),
            people = listOf("Ana"), grammar = listOf("es.ser_estar")), 1_000, expand = false) as SaveResult.Saved).id
        connect(); server.enqueue(ok("""{"id":"page1"}"""))
        assertEquals(NotionReport(sent = 1, failed = 0, error = null), exporter.export(listOf(id)))
        assertEquals("page1", db.dao().getEntry(id)!!.notionPageId)
        server.takeRequest(); server.takeRequest()
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["properties"]!!.jsonObject
        assertEquals("ser / estar", body["문법"]!!.jsonObject["multi_select"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("Ana", body["사람"]!!.jsonObject["multi_select"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content)

        connect(); server.enqueue(ok("""{"id":"page1"}"""))
        exporter.export(listOf(id))
        server.takeRequest(); server.takeRequest()
        assertEquals("PATCH", server.takeRequest().method)
    }

    @Test fun aPageDeletedInNotionIsCreatedAgain() = runBlocking {
        val id = (repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "Hi", "안녕", "", null, emptyList()), 1_000, expand = false) as SaveResult.Saved).id
        repo.setNotionPageId(id, "gone")
        connect(); server.enqueue(MockResponse().setResponseCode(404)); server.enqueue(ok("""{"id":"page2"}"""))
        assertEquals(1, exporter.export(listOf(id)).sent)
        assertEquals("page2", db.dao().getEntry(id)!!.notionPageId)
    }

    @Test fun aBadTokenStopsAndIsReported() = runBlocking {
        val id = (repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "Hi", "안녕", "", null, emptyList()), 1_000, expand = false) as SaveResult.Saved).id
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(NotionReport(0, 1, NotionError.BAD_TOKEN), exporter.export(listOf(id)))
    }
}
