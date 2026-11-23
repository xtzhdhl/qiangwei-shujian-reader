package com.romanticreader.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class Novel(
    val id: String,
    val title: String,
    val path: String,
    val size: Long,
    val progressChapter: Int = 0,
    val lastReadAt: Long = 0L
)

data class SourceVerificationRecord(
    val status: String = "未检测",
    val message: String = "尚未执行连接检测",
    val checkedAt: Long = 0L
)

data class BookSource(
    val url: String,
    val name: String,
    val group: String,
    val enabled: Boolean,
    val verificationStatus: String = "未检测",
    val verificationMessage: String = "尚未执行连接检测",
    val lastVerifiedAt: Long = 0L
)

class NovelRepository(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("rose_letter_library", Context.MODE_PRIVATE)

    fun getBooks(): List<Novel> {
        val raw = prefs.getString("books", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                Novel(
                    id = item.optString("id"),
                    title = item.optString("title", "未命名"),
                    path = item.optString("path"),
                    size = item.optLong("size"),
                    progressChapter = item.optInt("progressChapter", 0),
                    lastReadAt = item.optLong("lastReadAt", 0L)
                ).takeIf { it.id.isNotBlank() && it.path.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveBooks(books: List<Novel>) {
        val array = JSONArray()
        books.forEach { book ->
            array.put(
                JSONObject()
                    .put("id", book.id)
                    .put("title", book.title)
                    .put("path", book.path)
                    .put("size", book.size)
                    .put("progressChapter", book.progressChapter)
                    .put("lastReadAt", book.lastReadAt)
            )
        }
        prefs.edit().putString("books", array.toString()).apply()
    }

    fun importNovel(uri: Uri): Novel? = runCatching {
        val displayName = app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && column >= 0) cursor.getString(column) else null
        } ?: "未命名小说.txt"

        val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取所选文件")
        require(bytes.isNotEmpty()) { "文件内容为空" }

        val normalized = decodeText(bytes)
            .replace("\u0000", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        val id = "book_${System.currentTimeMillis()}"
        val file = File(app.filesDir, "$id.txt")
        file.writeText(normalized, Charsets.UTF_8)

        val book = Novel(
            id = id,
            title = displayName.substringBeforeLast('.', displayName).ifBlank { "未命名小说" },
            path = file.absolutePath,
            size = file.length(),
            progressChapter = 0,
            lastReadAt = System.currentTimeMillis()
        )
        saveBooks(getBooks() + book)
        book
    }.getOrNull()

    fun readText(book: Novel): String = File(book.path).takeIf { it.exists() }?.readText(Charsets.UTF_8).orEmpty()

    fun updateProgress(bookId: String, chapterIndex: Int) {
        val updated = getBooks().map { book ->
            if (book.id == bookId) book.copy(
                progressChapter = chapterIndex.coerceAtLeast(0),
                lastReadAt = System.currentTimeMillis()
            ) else book
        }
        saveBooks(updated)
    }

    fun deleteBook(bookId: String) {
        val target = getBooks().firstOrNull { it.id == bookId }
        target?.let { runCatching { File(it.path).delete() } }
        saveBooks(getBooks().filterNot { it.id == bookId })
    }

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)

        val candidates = buildList {
            if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) add(Charsets.UTF_16LE)
            if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) add(Charsets.UTF_16BE)
            add(Charsets.UTF_8)
            add(Charset.forName("GB18030"))
            add(Charset.forName("Big5"))
            add(Charsets.UTF_16LE)
        }

        for (charset in candidates.distinct()) {
            val start = if (charset == Charsets.UTF_16LE || charset == Charsets.UTF_16BE) {
                if (bytes.size >= 2 && (bytes[0] == 0xFF.toByte() || bytes[0] == 0xFE.toByte())) 2 else 0
            } else 0
            val decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val decoded = runCatching {
                decoder.decode(ByteBuffer.wrap(bytes, start, bytes.size - start)).toString()
            }.getOrNull()
            if (!decoded.isNullOrBlank()) return decoded
        }
        return String(bytes, Charset.forName("GB18030"))
    }
}


data class BookSourceSubscription(
    val url: String,
    val name: String,
    val sourceCount: Int,
    val lastSyncAt: Long
)

data class SavedOnlineBook(
    val sourceUrl: String,
    val bookUrl: String,
    val title: String,
    val author: String,
    val intro: String = "",
    val coverUrl: String = "",
    val tocUrl: String = "",
    val kind: String = "",
    val lastChapter: String = "",
    val savedAt: Long = System.currentTimeMillis()
)

class BookSourceRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("rose_letter_sources", Context.MODE_PRIVATE)

    fun getOnlineBooks(): List<SavedOnlineBook> {
        val raw = prefs.getString("online_books", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val item = array.optJSONObject(i) ?: return@mapNotNull null
                SavedOnlineBook(
                    sourceUrl = item.optString("sourceUrl"),
                    bookUrl = item.optString("bookUrl"),
                    title = item.optString("title"),
                    author = item.optString("author"),
                    intro = item.optString("intro"),
                    coverUrl = item.optString("coverUrl"),
                    tocUrl = item.optString("tocUrl"),
                    kind = item.optString("kind"),
                    lastChapter = item.optString("lastChapter"),
                    savedAt = item.optLong("savedAt", 0L)
                ).takeIf { it.sourceUrl.isNotBlank() && it.bookUrl.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    fun saveOnlineBook(book: SavedOnlineBook): Boolean {
        val books = getOnlineBooks()
        if (books.any { it.sourceUrl == book.sourceUrl && it.bookUrl == book.bookUrl }) return false
        val array = JSONArray()
        (books + book).forEach { b ->
            array.put(JSONObject()
                .put("sourceUrl", b.sourceUrl).put("bookUrl", b.bookUrl)
                .put("title", b.title).put("author", b.author).put("intro", b.intro)
                .put("coverUrl", b.coverUrl).put("tocUrl", b.tocUrl).put("kind", b.kind)
                .put("lastChapter", b.lastChapter).put("savedAt", b.savedAt))
        }
        prefs.edit().putString("online_books", array.toString()).apply()
        return true
    }

    fun removeOnlineBook(sourceUrl: String, bookUrl: String) {
        val array = JSONArray()
        getOnlineBooks().filterNot { it.sourceUrl == sourceUrl && it.bookUrl == bookUrl }.forEach { b ->
            array.put(JSONObject()
                .put("sourceUrl", b.sourceUrl).put("bookUrl", b.bookUrl)
                .put("title", b.title).put("author", b.author).put("intro", b.intro)
                .put("coverUrl", b.coverUrl).put("tocUrl", b.tocUrl).put("kind", b.kind)
                .put("lastChapter", b.lastChapter).put("savedAt", b.savedAt))
        }
        prefs.edit().putString("online_books", array.toString()).apply()
    }

    fun isOnlineBookSaved(sourceUrl: String, bookUrl: String): Boolean =
        getOnlineBooks().any { it.sourceUrl == sourceUrl && it.bookUrl == bookUrl }

    fun getSources(): List<BookSource> {
        val raw = prefs.getString("sources", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val url = item.optString("bookSourceUrl")
                if (url.isBlank()) return@mapNotNull null
                val verification = getVerificationRecord(url)
                BookSource(
                    url = url,
                    name = item.optString("bookSourceName").ifBlank { url },
                    group = item.optString("bookSourceGroup"),
                    enabled = item.optBoolean("enabled", true),
                    verificationStatus = verification.status,
                    verificationMessage = verification.message,
                    lastVerifiedAt = verification.checkedAt
                )
            }
        }.getOrDefault(emptyList())
    }

    fun getRawSource(sourceUrl: String, requireEnabled: Boolean = true): JSONObject? {
        val array = runCatching { JSONArray(prefs.getString("sources", "[]") ?: "[]") }.getOrNull() ?: return null
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (item.optString("bookSourceUrl") == sourceUrl &&
                (!requireEnabled || item.optBoolean("enabled", true))
            ) return JSONObject(item.toString())
        }
        return null
    }

    fun getVerificationRecord(sourceUrl: String): SourceVerificationRecord {
        val all = runCatching { JSONObject(prefs.getString("source_verification", "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        val item = all.optJSONObject(sourceUrl) ?: return SourceVerificationRecord()
        return SourceVerificationRecord(
            status = item.optString("status", "未检测"),
            message = item.optString("message", "尚未执行连接检测"),
            checkedAt = item.optLong("checkedAt", 0L)
        )
    }

    fun saveVerificationRecord(sourceUrl: String, status: String, message: String) {
        val all = runCatching { JSONObject(prefs.getString("source_verification", "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        all.put(sourceUrl, JSONObject()
            .put("status", status)
            .put("message", message.take(1200))
            .put("checkedAt", System.currentTimeMillis()))
        prefs.edit().putString("source_verification", all.toString()).apply()
    }

    fun getSubscriptions(): List<BookSourceSubscription> {
        val raw = prefs.getString("subscriptions", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val url = item.optString("url")
                if (url.isBlank()) return@mapNotNull null
                BookSourceSubscription(
                    url = url,
                    name = item.optString("name").ifBlank { url },
                    sourceCount = item.optInt("sourceCount"),
                    lastSyncAt = item.optLong("lastSyncAt")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveSubscriptions(items: List<BookSourceSubscription>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("url", item.url)
                    .put("name", item.name)
                    .put("sourceCount", item.sourceCount)
                    .put("lastSyncAt", item.lastSyncAt)
            )
        }
        prefs.edit().putString("subscriptions", array.toString()).apply()
    }

    /**
     * Imports a Legado-compatible source list. Rule fields are kept as-is:
     * both object-valued rules and legacy JSON-string rule blocks are retained.
     */
    fun importJson(content: String): Int {
        val incoming = parseSourceArray(content)
        val existing = runCatching { JSONArray(prefs.getString("sources", "[]") ?: "[]") }
            .getOrDefault(JSONArray())

        val existingIndex = mutableMapOf<String, Int>()
        for (i in 0 until existing.length()) {
            existing.optJSONObject(i)?.optString("bookSourceUrl")
                ?.takeIf { it.isNotBlank() }?.let { existingIndex[it] = i }
        }

        var changed = 0
        for (i in 0 until incoming.length()) {
            val item = incoming.optJSONObject(i) ?: continue
            val sourceUrl = item.optString("bookSourceUrl").trim()
            if (sourceUrl.isBlank()) continue
            val oldIndex = existingIndex[sourceUrl]
            if (oldIndex == null) {
                if (!item.has("enabled")) item.put("enabled", true)
                existingIndex[sourceUrl] = existing.length()
                existing.put(item)
                changed++
            } else {
                val old = existing.optJSONObject(oldIndex)
                // Preserve the user's local enabled/disabled choice on subscription refresh.
                if (old != null) item.put("enabled", old.optBoolean("enabled", true))
                val verification = runCatching { JSONObject(prefs.getString("source_verification", "{}") ?: "{}") }
                    .getOrDefault(JSONObject())
                if (old != null && old.toString() != item.toString()) {
                    verification.remove(sourceUrl)
                    prefs.edit().putString("source_verification", verification.toString()).apply()
                }
                existing.put(oldIndex, item)
                changed++
            }
        }
        prefs.edit().putString("sources", existing.toString()).apply()
        return changed
    }

    fun addSubscription(url: String): Int {
        val normalized = validateSubscriptionUrl(url)
        val raw = downloadText(normalized)
        val changed = importJson(raw)
        val count = runCatching { parseSourceArray(raw).length() }.getOrDefault(changed)
        val previous = getSubscriptions().filterNot { it.url == normalized }
        val displayName = runCatching {
            val array = parseSourceArray(raw)
            array.optJSONObject(0)?.optString("bookSourceGroup")?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: "远程书源订阅"
        saveSubscriptions(previous + BookSourceSubscription(normalized, displayName, count, System.currentTimeMillis()))
        return changed
    }

    fun syncSubscription(url: String): Int {
        val normalized = validateSubscriptionUrl(url)
        val raw = downloadText(normalized)
        val changed = importJson(raw)
        val count = runCatching { parseSourceArray(raw).length() }.getOrDefault(changed)
        val existing = getSubscriptions()
        val old = existing.firstOrNull { it.url == normalized }
        val updated = existing.filterNot { it.url == normalized } + BookSourceSubscription(
            normalized,
            old?.name ?: "远程书源订阅",
            count,
            System.currentTimeMillis()
        )
        saveSubscriptions(updated)
        return changed
    }

    fun removeSubscription(url: String) {
        saveSubscriptions(getSubscriptions().filterNot { it.url == url })
    }

    private fun validateSubscriptionUrl(value: String): String {
        val url = value.trim()
        val parsed = runCatching { java.net.URL(url) }.getOrElse {
            throw IllegalArgumentException("请输入有效的书源链接")
        }
        require(parsed.protocol.equals("https", true) || parsed.protocol.equals("http", true)) {
            "只支持 HTTP 或 HTTPS 的 JSON 书源地址"
        }
        require(parsed.host.isNotBlank()) { "书源地址缺少网站域名" }
        return parsed.toString()
    }

    private fun downloadText(url: String): String {
        val connection = (java.net.URL(url).openConnection() as? java.net.HttpURLConnection)
            ?: error("不支持的订阅地址")
        connection.connectTimeout = 20000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36 RoseLetter/0.4")
        connection.setRequestProperty("Accept", "application/json, text/plain, */*")
        connection.setRequestProperty("Accept-Encoding", "gzip")
        connection.setRequestProperty("Cache-Control", "no-cache")
        try {
            val status = connection.responseCode
            if (status !in 200..299) error("订阅服务器返回 HTTP ${status}（${connection.responseMessage ?: "请求失败"}）")
            val length = connection.contentLengthLong
            if (length > 30L * 1024 * 1024) error("书源文件超过 30 MB，已停止导入")
            val rawStream = connection.inputStream
            val input = if (connection.contentEncoding.equals("gzip", true)) java.util.zip.GZIPInputStream(rawStream) else rawStream
            val bytes = input.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > 30 * 1024 * 1024) error("书源文件超过 30 MB，已停止导入")
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            val body = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF").trim()
            if (body.startsWith("<!DOCTYPE", true) || body.startsWith("<html", true) || body.contains("Just a moment", true) || body.contains("verify you are human", true)) {
                error("服务器返回了网页/防护验证页，不是 JSON。请换用该书源仓库提供的 raw 下载地址")
            }
            if (body.isBlank()) error("服务器返回内容为空")
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSourceArray(content: String): JSONArray {
        var text = content.trim().removePrefix("\uFEFF").trim()
        if (text.startsWith(")]}',")) text = text.substringAfter(",").trim()
        if (text.startsWith("while(1);")) text = text.removePrefix("while(1);").trim()

        fun parse(value: String): Any? = runCatching { org.json.JSONTokener(value).nextValue() }.getOrNull()
        var root: Any? = parse(text)
        if (root is String) root = parse(root as String)

        val collected = linkedMapOf<String, JSONObject>()
        fun visit(value: Any?, depth: Int) {
            if (value == null || depth > 10) return
            when (value) {
                is JSONArray -> for (i in 0 until value.length()) visit(value.opt(i), depth + 1)
                is JSONObject -> {
                    val sourceUrl = value.optString("bookSourceUrl").trim()
                    if (sourceUrl.isNotBlank()) {
                        collected.putIfAbsent(sourceUrl, JSONObject(value.toString()))
                        return
                    }
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        val child = value.opt(keys.next())
                        if (child is JSONArray || child is JSONObject || child is String) {
                            val nested = if (child is String) parse(child) else child
                            visit(nested, depth + 1)
                        }
                    }
                }
                is String -> visit(parse(value), depth + 1)
            }
        }
        visit(root, 0)
        require(collected.isNotEmpty()) {
            val preview = text.take(100).replace("\n", " ")
            "没有识别到书源。需要 JSON 数组或包含 bookSourceUrl 的 Legado 对象；服务器内容开头：${preview}"
        }
        return JSONArray().also { array -> collected.values.forEach { array.put(it) } }
    }

    fun setEnabled(url: String, enabled: Boolean) {
        val array = runCatching { JSONArray(prefs.getString("sources", "[]") ?: "[]") }.getOrDefault(JSONArray())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (item.optString("bookSourceUrl") == url) item.put("enabled", enabled)
        }
        prefs.edit().putString("sources", array.toString()).apply()
    }

    fun delete(url: String) {
        val current = runCatching { JSONArray(prefs.getString("sources", "[]") ?: "[]") }.getOrDefault(JSONArray())
        val updated = JSONArray()
        for (i in 0 until current.length()) {
            val item = current.optJSONObject(i) ?: continue
            if (item.optString("bookSourceUrl") != url) updated.put(item)
        }
        prefs.edit().putString("sources", updated.toString()).apply()
        val verification = runCatching { JSONObject(prefs.getString("source_verification", "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        verification.remove(url)
        prefs.edit().putString("source_verification", verification.toString()).apply()
    }
}
