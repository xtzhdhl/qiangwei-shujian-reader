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

data class BookSource(
    val url: String,
    val name: String,
    val group: String,
    val enabled: Boolean
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

class BookSourceRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("rose_letter_sources", Context.MODE_PRIVATE)

    fun getSources(): List<BookSource> {
        val raw = prefs.getString("sources", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val url = item.optString("bookSourceUrl")
                if (url.isBlank()) return@mapNotNull null
                BookSource(
                    url = url,
                    name = item.optString("bookSourceName").ifBlank { url },
                    group = item.optString("bookSourceGroup"),
                    enabled = item.optBoolean("enabled", true)
                )
            }
        }.getOrDefault(emptyList())
    }

    fun importJson(content: String): Int {
        val incoming = runCatching {
            val trimmed = content.trim()
            when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    obj.optJSONArray("sources")
                        ?: obj.optJSONArray("bookSources")
                        ?: JSONArray().put(obj)
                }
                else -> error("不是有效的 JSON")
            }
        }.getOrElse { throw IllegalArgumentException("无法解析 JSON 书源文件") }

        val existing = runCatching { JSONArray(prefs.getString("sources", "[]") ?: "[]") }.getOrDefault(JSONArray())
        val urls = mutableSetOf<String>()
        for (i in 0 until existing.length()) {
            existing.optJSONObject(i)?.optString("bookSourceUrl")?.takeIf { it.isNotBlank() }?.let(urls::add)
        }

        var added = 0
        for (i in 0 until incoming.length()) {
            val item = incoming.optJSONObject(i) ?: continue
            val url = item.optString("bookSourceUrl").trim()
            if (url.isBlank() || !urls.add(url)) continue
            existing.put(item)
            added++
        }
        prefs.edit().putString("sources", existing.toString()).apply()
        return added
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
    }
}
