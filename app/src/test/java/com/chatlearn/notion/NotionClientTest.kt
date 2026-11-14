package com.chatlearn.notion

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import kotlin.test.*

class NotionClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: NotionClient

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        client = NotionClient(OkHttpClient(), server.url("/").toString().trimEnd('/')) { "secret-token" }
    }
    @After fun tearDown() { runCatching { server.shutdown() } }

    private fun json(body: String) = MockResponse().setResponseCode(200).setBody(body)

    private fun connectResponses() {
        server.enqueue(json("""{"object":"database","id":"dddddddddddddddddddddddddddddddd","data_sources":[{"id":"55555555555555555555555555555555","name":"문장"}]}"""))
        server.enqueue(json("""{"object":"data_source","id":"55555555555555555555555555555555","properties":{
            "Name":{"type":"title"},"뜻":{"type":"rich_text"},"언어":{"type":"select"},"사람":{"type":"multi_select"},
            "저장일":{"type":"date"},"문법":{"type":"rich_text"}}}"""))
    }

    private val page = NotionPage(
        sentence = "Estoy cansado", meaning = "피곤해", lang = "스페인어", kind = "받은 말",
        people = listOf("Ana"), countries = listOf("멕시코"), tags = emptyList(), grammar = listOf("ser / estar"),
        note = "", savedOn = LocalDate.of(2026, 11, 14),
    )

    @Test fun extractsTheIdFromALinkOrARawId() {
        assertEquals("0123456789abcdef0123456789abcdef", notionIdOf("https://www.notion.so/me/Words-0123456789abcdef0123456789abcdef?v=1"))
        assertEquals("0123456789abcdef0123456789abcdef", notionIdOf("01234567-89ab-cdef-0123-456789abcdef"))
        assertNull(notionIdOf("not a link"))
        // 링크에 붙은 보기(?v=) id가 아니라 데이터베이스 id를, 제목이 16진수 글자로 끝나도 정확히.
        val db = "0123456789abcdef0123456789abcdef"
        assertEquals(db, notionIdOf("https://www.notion.so/ws/Card-$db?v=fedcba9876543210fedcba9876543210&pvs=4"))
    }

    @Test fun connectFindsTheDataSourceAndItsProperties() = runBlocking {
        connectResponses()
        val t = client.connect("dddddddddddddddddddddddddddddddd").getOrThrow()
        assertEquals("55555555555555555555555555555555", t.dataSourceId)
        assertEquals("Name", t.titleProperty)
        val first = server.takeRequest()
        assertEquals("/v1/databases/dddddddddddddddddddddddddddddddd", first.path)
        assertEquals("Bearer secret-token", first.getHeader("Authorization"))
        assertNotNull(first.getHeader("Notion-Version"))
        Unit
    }

    @Test fun aNewPageGoesToTheDataSourceWithOnlyMatchingProperties() = runBlocking {
        connectResponses()
        val t = client.connect("dddddddddddddddddddddddddddddddd").getOrThrow()
        server.takeRequest(); server.takeRequest()
        server.enqueue(json("""{"object":"page","id":"99999999999999999999999999999999"}"""))
        assertEquals("99999999999999999999999999999999", client.upsert(t, null, page).getOrThrow())
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        val body = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("55555555555555555555555555555555", body["parent"]!!.jsonObject["data_source_id"]!!.jsonPrimitive.content)
        val props = body["properties"]!!.jsonObject
        assertEquals("Estoy cansado", props["Name"]!!.jsonObject["title"]!!.jsonArray[0].jsonObject["text"]!!.jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("Ana", props["사람"]!!.jsonObject["multi_select"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("2026-11-14", props["저장일"]!!.jsonObject["date"]!!.jsonObject["start"]!!.jsonPrimitive.content)
        assertEquals("ser / estar", props["문법"]!!.jsonObject["rich_text"]!!.jsonArray[0].jsonObject["text"]!!.jsonObject["content"]!!.jsonPrimitive.content)
        assertNull(props["나라"]) // 데이터베이스에 없는 속성은 보내지 않는다
    }

    @Test fun aSentPageIsUpdatedInsteadOfDuplicated() = runBlocking {
        connectResponses()
        val t = client.connect("dddddddddddddddddddddddddddddddd").getOrThrow()
        server.takeRequest(); server.takeRequest()
        server.enqueue(json("""{"object":"page","id":"99999999999999999999999999999999"}"""))
        client.upsert(t, "99999999999999999999999999999999", page).getOrThrow()
        val req = server.takeRequest()
        assertEquals("PATCH", req.method)
        assertEquals("/v1/pages/99999999999999999999999999999999", req.path)
    }

    @Test fun aStoredPageIdThatIsNotANotionIdNeverReachesTheUrl() = runBlocking {
        val t = NotionTarget("0123456789abcdef0123456789abcdef", "Name", mapOf("Name" to "title"))
        server.enqueue(json("""{"id":"new"}"""))
        assertEquals("new", client.upsert(t, "../users/me", page).getOrThrow())
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/v1/pages", req.path)
    }

    @Test fun aGarbledReplyIsAServerProblemNotABadToken() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>login</html>"))
        assertEquals(NotionError.SERVER, (client.connect("dddddddddddddddddddddddddddddddd").exceptionOrNull() as NotionException).kind)
    }

    @Test fun errorsAreNamed() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(NotionError.BAD_TOKEN, (client.connect("dddddddddddddddddddddddddddddddd").exceptionOrNull() as NotionException).kind)
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(NotionError.NOT_SHARED, (client.connect("dddddddddddddddddddddddddddddddd").exceptionOrNull() as NotionException).kind)
    }
}
