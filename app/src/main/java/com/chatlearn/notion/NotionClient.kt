package com.chatlearn.notion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.LocalDate

/** BAD_TOKEN: 통합 토큰이 틀림. NOT_SHARED: 데이터베이스가 없거나 통합에 연결(공유)되지 않음. */
enum class NotionError { BAD_TOKEN, NOT_SHARED, OFFLINE, RATE_LIMIT, SERVER }

class NotionException(val kind: NotionError) : Exception(kind.name)

/** 보낼 데이터베이스: 페이지를 만들 데이터 소스와, 그 안의 속성 이름 → 종류(title, rich_text, select…). */
data class NotionTarget(val dataSourceId: String, val titleProperty: String, val properties: Map<String, String>)

/** 노션에 한 줄로 보낼 문장. */
data class NotionPage(
    val sentence: String, val meaning: String, val lang: String, val kind: String,
    val people: List<String>, val countries: List<String>, val tags: List<String>, val grammar: List<String>,
    val note: String, val savedOn: LocalDate,
)

private val NOTION_UUID = Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""")
private val NOTION_ID = Regex("""(?<![0-9a-fA-F])[0-9a-fA-F]{32}(?![0-9a-fA-F])""")

/** 주소에 넣어도 되는 노션 id(하이픈 있든 없든 16진수 32자리)만 하이픈 없이 돌려준다. */
internal fun safeId(id: String): String? = id.replace("-", "").takeIf { it.length == 32 && it.all { c -> c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' } }?.lowercase()

/**
 * 노션 링크나 id(하이픈 있든 없든)에서 데이터베이스 id를 꺼낸다. 없으면 null.
 * 링크 뒤의 ?v=(보기 id)는 버리고, 제목과 id를 잇는 하이픈은 지우지 않는다("Card-<id>"의 d가 id에 붙지 않게).
 */
fun notionIdOf(input: String): String? {
    val path = input.substringBefore('?').substringBefore('#')
    NOTION_UUID.findAll(path).lastOrNull()?.let { return it.value.replace("-", "").lowercase() }
    return NOTION_ID.findAll(path).lastOrNull()?.value?.lowercase()
}

/**
 * 노션 API 경계. 2025-09-03 버전부터 페이지는 데이터베이스가 아니라 그 안의 데이터 소스에 만든다.
 * 데이터베이스의 속성 이름이 앱과 같을 때만 그 칸을 채운다(사용자가 고친 데이터베이스여도 깨지지 않게).
 */
class NotionClient(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://api.notion.com",
    private val token: () -> String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun connect(databaseId: String): Result<NotionTarget> {
        val dbId = safeId(databaseId) ?: return Result.failure(NotionException(NotionError.NOT_SHARED))
        return call("GET", "/v1/databases/$dbId", null).mapCatching { db ->
        val ds = safeId(db["data_sources"]!!.jsonArray.first().jsonObject["id"]!!.jsonPrimitive.content) ?: throw NotionException(NotionError.SERVER)
        val props = call("GET", "/v1/data_sources/$ds", null).getOrThrow()["properties"]!!.jsonObject
            .mapValues { (_, v) -> v.jsonObject["type"]!!.jsonPrimitive.content }
        NotionTarget(ds, props.entries.first { it.value == "title" }.key, props)
        }
    }

    /** [pageId]가 있으면 그 페이지를 고치고, 없으면 새로 만든다. 페이지 id를 돌려준다. */
    suspend fun upsert(target: NotionTarget, pageId: String?, page: NotionPage): Result<String> {
        val props = propertiesOf(target, page)
        // 페이지 id는 백업 파일로 들어올 수도 있다. 노션 id 모양이 아니면 주소에 넣지 않고 새 페이지를 만든다
        // (토큰을 실은 요청이 엉뚱한 경로로 가지 않게).
        val pid = pageId?.let(::safeId)
        val result = if (pid == null) {
            call("POST", "/v1/pages", buildJsonObject {
                putJsonObject("parent") { put("type", "data_source_id"); put("data_source_id", target.dataSourceId) }
                put("properties", props)
            })
        } else call("PATCH", "/v1/pages/$pid", buildJsonObject { put("properties", props) })
        return result.mapCatching { it["id"]!!.jsonPrimitive.content }
    }

    private fun propertiesOf(t: NotionTarget, p: NotionPage) = buildJsonObject {
        put(t.titleProperty, buildJsonObject { put("title", richText(p.sentence)) })
        fun field(name: String, type: String, value: JsonElement) { if (t.properties[name] == type) put(name, buildJsonObject { put(type, value) }) }
        fun text(name: String, v: String) { if (v.isNotBlank()) field(name, "rich_text", richText(v)) }
        fun select(name: String, v: String) = field(name, "select", buildJsonObject { put("name", v) })
        fun multi(name: String, vs: List<String>) {
            // 같은 칸을 글자로 만든 데이터베이스면 쉼표로 이어 쓴다.
            if (t.properties[name] == "rich_text") text(name, vs.joinToString(", "))
            else field(name, "multi_select", buildJsonArray { vs.forEach { v -> addJsonObject { put("name", v.replace(",", " ")) } } })
        }
        text("뜻", p.meaning); select("언어", p.lang); select("종류", p.kind)
        multi("사람", p.people); multi("나라", p.countries); multi("태그", p.tags); multi("문법", p.grammar)
        text("메모", p.note)
        field("저장일", "date", buildJsonObject { put("start", p.savedOn.toString()) })
    }

    private fun richText(s: String) = buildJsonArray { addJsonObject { putJsonObject("text") { put("content", s.take(2000)) } } }

    private suspend fun call(method: String, path: String, body: JsonObject?): Result<JsonObject> = withContext(Dispatchers.IO) {
        val req = try {
            Request.Builder().url(baseUrl + path)
                .header("Authorization", "Bearer ${token()}")
                .header("Notion-Version", NOTION_VERSION)
                .method(method, body?.toString()?.toRequestBody("application/json".toMediaType()))
                .build()
        } catch (e: IllegalArgumentException) {
            // 토큰에 헤더에 쓸 수 없는 글자가 들어 있으면 OkHttp가 값을 담아 던진다(토큰 노출). 종류만 알린다.
            return@withContext Result.failure(NotionException(NotionError.BAD_TOKEN))
        }
        val (code, text) = try {
            http.newCall(req).execute().use { r -> r.code to r.body?.string().orEmpty() }
        } catch (e: IOException) {
            return@withContext Result.failure(NotionException(NotionError.OFFLINE))
        }
        when (code) {
            // 로그인 페이지 같은 엉뚱한 응답은 토큰 문제가 아니라 서버 문제로 본다.
            in 200..299 -> runCatching { json.parseToJsonElement(text).jsonObject }.recoverCatching { throw NotionException(NotionError.SERVER) }
            401 -> Result.failure(NotionException(NotionError.BAD_TOKEN))
            403, 404 -> Result.failure(NotionException(NotionError.NOT_SHARED))
            429 -> Result.failure(NotionException(NotionError.RATE_LIMIT))
            else -> Result.failure(NotionException(NotionError.SERVER))
        }
    }

    private companion object { const val NOTION_VERSION = "2025-09-03" }
}
