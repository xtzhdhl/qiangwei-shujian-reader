package com.romanticreader.app.source

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.romanticreader.app.data.BookSourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SourceRose = Color(0xFFB76D80)
private val SourceInk = Color(0xFF5B4145)
private val SourceMuted = Color(0xFF9D8583)
private val SourcePaper = Color(0xFFFFF9F5)
private val SourceWhite = Color(0xFFFFFDFB)
private val SourceLine = Color(0xFFEED9D4)

@Composable
fun RemoteBookBrowser(repository: BookSourceRepository, onBack: () -> Unit) {
    val engine = remember { LegadoRuleEngine() }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf(emptyList<SourceBookHit>()) }
    var detail by remember { mutableStateOf<SourceBookDetail?>(null) }
    var chapters by remember { mutableStateOf(emptyList<SourceChapter>()) }
    var currentChapter by remember { mutableStateOf<SourceChapter?>(null) }
    var content by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("选择已启用书源，输入书名开始搜索。") }
    var stage by remember { mutableStateOf("search") }

    fun backStep() {
        when(stage) {
            "content" -> { currentChapter = null; content = ""; stage = "chapters" }
            "chapters" -> { detail = null; chapters = emptyList(); stage = "results" }
            "results" -> { hits = emptyList(); stage = "search" }
            else -> onBack()
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = SourceRose, background = SourcePaper, surface = SourceWhite, onSurface = SourceInk)) {
        Column(Modifier.fillMaxSize().background(SourcePaper).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { backStep() }) { Icon(Icons.Rounded.ArrowBack, "返回", tint = SourceInk) }
                Column(Modifier.weight(1f)) {
                    Text(when(stage) { "content" -> currentChapter?.name ?: "正文"; "chapters" -> detail?.name ?: "目录"; "results" -> "搜索结果"; else -> "在线找书" }, color = SourceInk, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
                    Text("ROSE LETTER · SOURCE READER", color = SourceMuted, fontSize = 9.sp)
                }
            }
            when(stage) {
                "search" -> {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("小说名 / 作者") }, trailingIcon = { Icon(Icons.Rounded.Search, null) })
                        Button(onClick = {
                            if (query.isBlank()) { message = "先输入小说名哦"; return@Button }
                            scope.launch {
                                loading = true; hits = emptyList(); message = "正在搜索已启用的书源…"
                                val result = withContext(Dispatchers.IO) {
                                    val list = mutableListOf<SourceBookHit>()
                                    repository.getSources().filter { it.enabled }.forEach { src ->
                                        val json = repository.getRawSource(src.url) ?: return@forEach
                                        runCatching { engine.search(json, query.trim()) }.onSuccess { list += it }
                                    }
                                    list
                                }
                                hits = result
                                message = if (result.isEmpty()) "没有找到结果。可能部分书源规则尚未兼容，或网站暂时无法访问。" else "找到 ${result.size} 个结果，点开查看详情与目录。"
                                loading = false
                                stage = "results"
                            }
                        }, enabled = !loading, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SourceRose)) { Text(if (loading) "搜索中…" else "搜索小说") }
                        Text(message, color = SourceMuted, fontSize = 12.sp, lineHeight = 18.sp)
                        Text("当前支持常见 CSS、XPath、JSONPath、正则清理与 GET/POST；复杂 JavaScript 书源暂不执行。", color = SourceMuted, fontSize = 11.sp, lineHeight = 17.sp)
                    }
                }
                "results" -> {
                    Text(message, color = SourceMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        items(hits) { hit ->
                            Surface(shape = RoundedCornerShape(18.dp), color = SourceWhite, border = androidx.compose.foundation.BorderStroke(1.dp, SourceLine), modifier = Modifier.fillMaxWidth().clickable {
                                scope.launch {
                                    loading = true
                                    val loaded = withContext(Dispatchers.IO) { runCatching {
                                        val src = repository.getRawSource(hit.sourceUrl) ?: error("书源已停用")
                                        engine.loadDetail(src, hit)
                                    } }
                                    loaded.onSuccess { detail = it; stage = "chapters" }.onFailure { message = "详情加载失败：${it.message ?: "规则不兼容"}" }
                                    loading = false
                                }
                            }) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(hit.name, color = SourceInk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                    Text("${hit.author.ifBlank { "作者未知" }} · ${repository.getSources().firstOrNull { it.url == hit.sourceUrl }?.name ?: "书源"}", color = SourceMuted, fontSize = 11.sp)
                                    if (hit.lastChapter.isNotBlank()) Text("最新：${hit.lastChapter}", color = SourceMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (hit.intro.isNotBlank()) Text(hit.intro, color = SourceInk, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                "chapters" -> {
                    detail?.let { book ->
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(book.name, color = SourceInk, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text("${book.author} · ${book.kind}", color = SourceMuted, fontSize = 12.sp)
                            if (book.intro.isNotBlank()) Text(book.intro, color = SourceInk, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            Button(onClick = {
                                scope.launch {
                                    loading = true
                                    val result = withContext(Dispatchers.IO) { runCatching {
                                        val src = repository.getRawSource(book.sourceUrl) ?: error("书源已停用")
                                        engine.loadChapters(src, book)
                                    } }
                                    result.onSuccess { chapters = it; message = "共 ${it.size} 章" }.onFailure { message = "目录加载失败：${it.message ?: "规则不兼容"}" }
                                    loading = false
                                }
                            }, enabled = !loading, colors = ButtonDefaults.buttonColors(containerColor = SourceRose)) { Text(if (loading) "加载目录中…" else if (chapters.isEmpty()) "加载目录" else "重新加载目录") }
                            Text(message, color = SourceMuted, fontSize = 11.sp)
                        }
                        LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                            items(chapters) { chapter ->
                                Row(Modifier.fillMaxWidth().clickable {
                                    scope.launch {
                                        loading = true
                                        currentChapter = chapter
                                        val result = withContext(Dispatchers.IO) { runCatching {
                                            val src = repository.getRawSource(chapter.sourceUrl) ?: error("书源已停用")
                                            engine.loadContent(src, chapter)
                                        } }
                                        result.onSuccess { content = it; stage = "content" }.onFailure { message = "正文加载失败：${it.message ?: "规则不兼容"}" }
                                        loading = false
                                    }
                                }.padding(vertical = 12.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.MenuBook, null, tint = SourceRose, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(9.dp))
                                    Text(chapter.name, color = SourceInk, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    if (chapter.isVip) Text("VIP", color = SourceMuted, fontSize = 9.sp)
                                }
                                HorizontalDivider(color = SourceLine)
                            }
                        }
                    }
                }
                "content" -> {
                    Column(Modifier.fillMaxSize()) {
                        Text(detail?.name ?: "", color = SourceMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
                        Text(currentChapter?.name ?: "", color = SourceInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            item { Text(content.ifBlank { "正文内容为空，可能规则未匹配或站点要求登录。" }, color = SourceInk, fontSize = 18.sp, lineHeight = 32.sp) }
                        }
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = {
                                val idx = chapters.indexOf(currentChapter)
                                if (idx > 0) {
                                    val prev = chapters[idx - 1]
                                    scope.launch {
                                        loading = true; currentChapter = prev
                                        content = withContext(Dispatchers.IO) {
                                            val src = repository.getRawSource(prev.sourceUrl) ?: error("书源已停用")
                                            engine.loadContent(src, prev)
                                        }
                                        loading = false
                                    }
                                }
                            }) { Text("上一章", color = SourceRose) }
                            TextButton(onClick = {
                                val idx = chapters.indexOf(currentChapter)
                                if (idx >= 0 && idx < chapters.lastIndex) {
                                    val next = chapters[idx + 1]
                                    scope.launch {
                                        loading = true; currentChapter = next
                                        content = withContext(Dispatchers.IO) {
                                            val src = repository.getRawSource(next.sourceUrl) ?: error("书源已停用")
                                            engine.loadContent(src, next)
                                        }
                                        loading = false
                                    }
                                }
                            }) { Text("下一章", color = SourceRose) }
                        }
                    }
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = SourceRose)
        }
    }
}
