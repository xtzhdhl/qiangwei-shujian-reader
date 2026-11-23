package com.romanticreader.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.romanticreader.app.data.BookSourceRepository
import com.romanticreader.app.data.Novel
import com.romanticreader.app.data.NovelRepository
import java.io.File

private val RosePaper = Color(0xFFFFF9F5)
private val RoseWhite = Color(0xFFFFFDFB)
private val RoseInk = Color(0xFF5B4145)
private val RoseMuted = Color(0xFF9D8583)
private val RosePink = Color(0xFFD28E9C)
private val RoseDeep = Color(0xFFB76D80)
private val RoseBlush = Color(0xFFF8E6E5)
private val RoseLine = Color(0xFFEED9D4)
private val Champagne = Color(0xFFB59B79)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RoseLetterApp(this) }
    }
}

private enum class AppTab(val label: String) { Home("首页"), Shelf("书架"), Discover("发现"), Me("我的") }

@Composable
private fun RoseLetterApp(context: Context) {
    val library = remember { NovelRepository(context) }
    val sources = remember { BookSourceRepository(context) }
    var tab by remember { mutableStateOf(AppTab.Home) }
    var books by remember { mutableStateOf(library.getBooks()) }
    var sourceList by remember { mutableStateOf(sources.getSources()) }
    var activeBook by remember { mutableStateOf<Novel?>(null) }
    var readerText by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf("") }
    var sourcePage by remember { mutableStateOf(false) }
    var settingsPage by remember { mutableStateOf(false) }

    val importTxt = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val imported = library.importNovel(it)
            books = library.getBooks()
            toast = if (imported != null) "《${imported.title}》已收藏进书架" else "导入失败，请检查文件"
        }
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val content = runCatching { context.contentResolver.openInputStream(it)?.bufferedReader(Charsets.UTF_8)?.use { r -> r.readText() } }.getOrNull()
            if (content != null) {
                val count = runCatching { sources.importJson(content) }.getOrElse { -1 }
                sourceList = sources.getSources()
                toast = if (count >= 0) "成功导入 $count 个书源" else "书源文件格式无法识别"
            } else toast = "无法读取书源文件"
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = RoseDeep, onPrimary = Color.White, secondary = Champagne,
        background = RosePaper, surface = RoseWhite, onSurface = RoseInk,
        surfaceVariant = RoseBlush, onSurfaceVariant = RoseMuted
    )) {
        if (activeBook != null) {
            RoseReader(
                book = activeBook!!,
                text = readerText,
                initialParagraph = activeBook!!.progressChapter,
                onBack = { activeBook = null; books = library.getBooks() },
                onProgress = { library.updateProgress(activeBook!!.id, it) }
            )
        } else {
            Scaffold(
                containerColor = RosePaper,
                bottomBar = {
                    NavigationBar(containerColor = RoseWhite, tonalElevation = 0.dp, modifier = Modifier.height(72.dp)) {
                        AppTab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item && !sourcePage && !settingsPage,
                                onClick = { tab = item; sourcePage = false; settingsPage = false },
                                icon = {
                                    Icon(
                                        when (item) {
                                            AppTab.Home -> Icons.Rounded.Home
                                            AppTab.Shelf -> Icons.Rounded.LocalLibrary
                                            AppTab.Discover -> Icons.Rounded.Explore
                                            AppTab.Me -> Icons.Rounded.Person
                                        }, null, modifier = Modifier.size(22.dp)
                                    )
                                },
                                label = { Text(item.label, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = RoseBlush, selectedIconColor = RoseDeep, selectedTextColor = RoseDeep)
                            )
                        }
                    }
                }
            ) { padding ->
                when {
                    sourcePage -> SourcesScreen(Modifier.padding(padding), sourceList, onImport = { importJson.launch(arrayOf("application/json", "text/*")) }, onToggle = { s, enabled -> sources.setEnabled(s.url, enabled); sourceList = sources.getSources() }, onDelete = { s -> sources.delete(s.url); sourceList = sources.getSources() }, onBack = { sourcePage = false })
                    settingsPage -> SettingsScreen(Modifier.padding(padding), books.size, sourceList.size, onSources = { sourcePage = true }, onBack = { settingsPage = false })
                    tab == AppTab.Home -> HomeScreen(Modifier.padding(padding), books, onImport = { importTxt.launch(arrayOf("text/plain", "text/*")) }, onOpen = { book -> readerText = library.readText(book); activeBook = book }, onShelf = { tab = AppTab.Shelf })
                    tab == AppTab.Shelf -> ShelfScreen(Modifier.padding(padding), books, onImport = { importTxt.launch(arrayOf("text/plain", "text/*")) }, onOpen = { book -> readerText = library.readText(book); activeBook = book }, onDelete = { book -> library.deleteBook(book.id); books = library.getBooks() })
                    tab == AppTab.Discover -> DiscoverScreen(Modifier.padding(padding), onSources = { sourcePage = true }, onImport = { importJson.launch(arrayOf("application/json", "text/*")) })
                    else -> SettingsScreen(Modifier.padding(padding), books.size, sourceList.size, onSources = { sourcePage = true }, onBack = {})
                }
            }
            if (toast.isNotBlank()) {
                LaunchedEffect(toast) { kotlinx.coroutines.delay(2400); toast = "" }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Surface(color = RoseInk, shape = RoundedCornerShape(24.dp), modifier = Modifier.padding(bottom = 92.dp)) {
                        Text(toast, color = Color.White, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoseHeader(title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 29.sp, fontWeight = FontWeight.SemiBold, color = RoseInk, fontFamily = FontFamily.Serif)
            subtitle?.let { Text(it, fontSize = 13.sp, color = RoseMuted, modifier = Modifier.padding(top = 3.dp)) }
        }
        trailing?.invoke()
    }
}

@Composable
private fun RoseCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(22.dp), color = RoseWhite, border = androidx.compose.foundation.BorderStroke(1.dp, RoseLine.copy(alpha = .65f)), shadowElevation = 1.dp) {
        Column(content = content)
    }
}

@Composable
private fun HomeScreen(modifier: Modifier, books: List<Novel>, onImport: () -> Unit, onOpen: (Novel) -> Unit, onShelf: () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            RoseHeader("拾阅 ✧", "在文字里，遇见更大的世界。", trailing = {
                IconButton(onClick = onImport) { Icon(Icons.Rounded.Search, "导入 / 搜索", tint = RoseDeep) }
            })
        }
        item {
            RoseCard(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFFFE9E8), Color(0xFFF8F0E9), Color(0xFFEDE5F4)))).padding(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("A LITTLE READING GARDEN", fontSize = 10.sp, letterSpacing = 2.sp, color = Champagne)
                        Text("把喜欢的故事\n收进自己的花园", fontSize = 24.sp, lineHeight = 32.sp, color = RoseInk, fontWeight = FontWeight.Medium)
                        Text("慢一点，读一段属于自己的时光。", fontSize = 12.sp, color = RoseMuted)
                        Button(onClick = onImport, colors = ButtonDefaults.buttonColors(containerColor = RoseDeep), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(5.dp)); Text("导入一本书")
                        }
                    }
                    Icon(Icons.Rounded.LocalFlorist, null, tint = Color(0xFFD9A4AB).copy(alpha = .24f), modifier = Modifier.align(Alignment.BottomEnd).size(90.dp))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                HomeQuick("我的书架", Icons.Rounded.LocalLibrary, books.size.toString(), onShelf)
                HomeQuick("书源管理", Icons.Rounded.Explore, "JSON", onImport)
                HomeQuick("阅读记录", Icons.Rounded.History, "继续", { books.firstOrNull()?.let(onOpen) })
            }
        }
        item { SectionTitle("继续阅读", "CONTINUE", onShelf) }
        if (books.isEmpty()) item { EmptyBooksCard(onImport) }
        else items(books.take(3), key = { it.id }) { book ->
            BookRow(book, onClick = { onOpen(book) }, trailing = {
                Text("继续阅读 ›", fontSize = 12.sp, color = RoseDeep)
            })
        }
        item { SectionTitle("为你留白", "A QUIET MOMENT") }
        item {
            RoseCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Champagne, modifier = Modifier.size(28.dp))
                    Text("今日阅读，随心而始", color = RoseInk, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text("不必赶路，让故事陪你慢慢走。", color = RoseMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeQuick(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, detail: String, onClick: () -> Unit) {
    RoseCard(Modifier.weight(1f).clickable(onClick = onClick)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, tint = RoseDeep, modifier = Modifier.size(24.dp))
            Text(title, color = RoseInk, fontSize = 12.sp)
            Text(detail, color = RoseMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ShelfScreen(modifier: Modifier, books: List<Novel>, onImport: () -> Unit, onOpen: (Novel) -> Unit, onDelete: (Novel) -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { RoseHeader("我的书架", "MY LITTLE LIBRARY", trailing = { IconButton(onClick = onImport) { Icon(Icons.Rounded.Add, "添加书籍", tint = RoseDeep) } }) }
        item {
            RoseCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("藏书", fontSize = 13.sp, color = RoseMuted)
                        Text("${books.size} 本", fontSize = 25.sp, color = RoseInk, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = onImport) { Icon(Icons.Rounded.Add, null); Text("导入 TXT") }
                }
            }
        }
        if (books.isEmpty()) item { EmptyBooksCard(onImport) }
        items(books, key = { it.id }) { book -> BookRow(book, onClick = { onOpen(book) }, trailing = { IconButton(onClick = { onDelete(book) }) { Icon(Icons.Rounded.DeleteOutline, "删除", tint = RoseMuted) } }) }
    }
}

@Composable
private fun BookRow(book: Novel, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    RoseCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp, 78.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Color(0xFFEED7DF), Color(0xFFF8EDE5)))), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MenuBook, null, tint = RoseDeep.copy(alpha = .65f), modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(book.title, fontSize = 15.sp, color = RoseInk, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("TXT · ${(book.size / 1024).coerceAtLeast(1)} KB", fontSize = 11.sp, color = RoseMuted)
                Text(if (book.progressChapter > 0) "已读到第 ${book.progressChapter + 1} 段" else "尚未开始", fontSize = 11.sp, color = RoseDeep)
            }
            trailing()
        }
    }
}

@Composable
private fun EmptyBooksCard(onImport: () -> Unit) {
    RoseCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Book, null, tint = RoseDeep, modifier = Modifier.size(38.dp))
            Text("书架还在等待第一本故事", color = RoseInk, fontWeight = FontWeight.Medium)
            Text("把喜欢的 TXT 小说放进来吧。", color = RoseMuted, fontSize = 12.sp)
            Button(onClick = onImport, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = RoseDeep)) { Text("选择 TXT 文件") }
        }
    }
}

@Composable
private fun SectionTitle(title: String, english: String, action: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 19.sp, color = RoseInk, fontWeight = FontWeight.SemiBold)
            Text(english, fontSize = 9.sp, letterSpacing = 1.8.sp, color = Champagne)
        }
        action?.let { TextButton(onClick = it) { Text("更多 ›", color = RoseMuted, fontSize = 12.sp) } }
    }
}

@Composable
private fun DiscoverScreen(modifier: Modifier, onSources: () -> Unit, onImport: () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { RoseHeader("发现", "DISCOVER YOUR NEXT STORY") }
        item {
            RoseCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("书源花园", fontSize = 22.sp, color = RoseInk, fontWeight = FontWeight.Medium)
                    Text("把可用的小说网站书源收进来，未来可搜索、发现和同步书籍。", color = RoseMuted, fontSize = 13.sp, lineHeight = 21.sp)
                    Button(onClick = onSources, colors = ButtonDefaults.buttonColors(containerColor = RoseDeep)) { Text("管理书源") }
                }
            }
        }
        item {
            RoseCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Legado JSON", fontWeight = FontWeight.SemiBold, color = RoseInk)
                    Text("导入常见 Legado 格式 JSON。首版保存源信息与完整规则数据；在线搜索和规则执行引擎将在后续阶段接入。", fontSize = 12.sp, color = RoseMuted, lineHeight = 19.sp)
                    OutlinedButton(onClick = onImport) { Icon(Icons.Rounded.FileOpen, null); Spacer(Modifier.width(6.dp)); Text("导入书源文件") }
                }
            }
        }
        item {
            RoseCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("阅读小贴士", fontSize = 16.sp, color = RoseInk)
                    Text("TXT 导入支持 UTF-8、GB18030、UTF-16 等常见编码尝试。阅读页会整理空行与章节标题，让正文更舒服。", fontSize = 12.sp, color = RoseMuted, lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun SourcesScreen(modifier: Modifier, sources: List<com.romanticreader.app.data.BookSource>, onImport: () -> Unit, onToggle: (com.romanticreader.app.data.BookSource, Boolean) -> Unit, onDelete: (com.romanticreader.app.data.BookSource) -> Unit, onBack: () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { RoseHeader("书源管理", "SOURCE GARDEN", trailing = { IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, "关闭") } }) }
        item {
            RoseCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("导入书源", fontSize = 18.sp, color = RoseInk)
                    Text("支持 JSON 数组或单个书源对象；同一书源网址会自动跳过重复项。", color = RoseMuted, fontSize = 12.sp, lineHeight = 18.sp)
                    Button(onClick = onImport, colors = ButtonDefaults.buttonColors(containerColor = RoseDeep)) { Icon(Icons.Rounded.Add, null); Text("选择 JSON 文件") }
                }
            }
        }
        item { SectionTitle("已收藏的书源", "SAVED SOURCES") }
        if (sources.isEmpty()) item { EmptyBooksCard(onImport) }
        items(sources, key = { it.url }) { source ->
            RoseCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(source.name, fontSize = 14.sp, color = RoseInk, fontWeight = FontWeight.Medium)
                        Text(source.url, fontSize = 10.sp, color = RoseMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (source.group.isNotBlank()) Text(source.group, fontSize = 10.sp, color = Champagne)
                    }
                    Switch(source.enabled, onCheckedChange = { onToggle(source, it) })
                    IconButton(onClick = { onDelete(source) }) { Icon(Icons.Rounded.DeleteOutline, "删除", tint = RoseMuted) }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier, bookCount: Int, sourceCount: Int, onSources: () -> Unit, onBack: () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { RoseHeader("我的", "A PERSONAL READING SPACE") }
        item {
            RoseCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("蔷薇书笺", fontSize = 22.sp, fontFamily = FontFamily.Serif, color = RoseInk)
                    Text("Read · More · Beautiful", color = Champagne, fontSize = 11.sp)
                    Text("藏书 $bookCount 本    书源 $sourceCount 个", color = RoseMuted, fontSize = 12.sp)
                }
            }
        }
        item { SettingItem(Icons.Rounded.Explore, "书源管理", "导入、启用或移除书源", onSources) }
        item { SettingItem(Icons.Rounded.Palette, "阅读主题", "纸白、奶油、粉雾、夜间主题将在阅读设置中扩展", {}) }
        item { SettingItem(Icons.Rounded.TextFields, "字体", "系统字体与自定义字体支持正在扩展", {}) }
        item { SettingItem(Icons.Rounded.Backup, "备份与恢复", "本地书架与书源备份规划中", {}) }
        item { SettingItem(Icons.Rounded.Info, "关于", "蔷薇书笺 · 0.2.0", {}) }
    }
}

@Composable
private fun SettingItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String, onClick: () -> Unit) {
    RoseCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = RoseDeep)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = RoseInk, fontWeight = FontWeight.Medium)
                Text(desc, color = RoseMuted, fontSize = 11.sp)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = RoseMuted)
        }
    }
}

@Composable
private fun RoseReader(book: Novel, text: String, initialParagraph: Int, onBack: () -> Unit, onProgress: (Int) -> Unit) {
    var fontSize by remember { mutableFloatStateOf(19f) }
    var lineSpace by remember { mutableFloatStateOf(1.8f) }
    var dark by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(initialParagraph.coerceAtLeast(0)) }
    val paragraphs = remember(text) { formatNovelText(text) }
    val background = if (dark) Color(0xFF282426) else Color(0xFFF8F2E9)
    val foreground = if (dark) Color(0xFFE8DCD2) else Color(0xFF493C3B)
    val safePage = page.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))

    Column(Modifier.fillMaxSize().background(background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回", tint = foreground) }
            Text(book.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = foreground, fontSize = 14.sp)
            IconButton(onClick = { controls = !controls }) { Icon(Icons.Rounded.Tune, "阅读设置", tint = foreground) }
            IconButton(onClick = { dark = !dark }) { Icon(if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, "切换日夜", tint = foreground) }
        }
        if (paragraphs.isNotEmpty()) {
            Text("第 ${safePage + 1} / ${paragraphs.size} 段", color = RoseMuted, fontSize = 10.sp, modifier = Modifier.padding(start = 26.dp, top = 6.dp, bottom = 10.dp))
            Box(Modifier.weight(1f).fillMaxWidth().clickable { page = (page + 1).coerceAtMost(paragraphs.lastIndex); onProgress(page) }) {
                LazyColumn(contentPadding = PaddingValues(horizontal = 26.dp, vertical = 16.dp)) {
                    item {
                        Text(paragraphs[safePage], color = foreground, fontSize = fontSize.sp, lineHeight = (fontSize * lineSpace).sp, letterSpacing = .3.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { page = (page - 1).coerceAtLeast(0); onProgress(page) }, enabled = page > 0) { Text("上一段", color = RoseDeep) }
                Slider(page.toFloat(), onValueChange = { page = it.toInt() }, valueRange = 0f..paragraphs.lastIndex.coerceAtLeast(1).toFloat(), modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = RoseDeep, activeTrackColor = RoseDeep))
                TextButton(onClick = { page = (page + 1).coerceAtMost(paragraphs.lastIndex); onProgress(page) }, enabled = page < paragraphs.lastIndex) { Text("下一段", color = RoseDeep) }
            }
        } else Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("这本书暂时没有正文", color = foreground) }
        if (controls) {
            Surface(color = if (dark) Color(0xFF332D31) else RoseWhite, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("字号 ${fontSize.toInt()}", color = foreground, fontSize = 12.sp)
                        Slider(fontSize, { fontSize = it }, valueRange = 14f..30f, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = RoseDeep, activeTrackColor = RoseDeep))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("行距", color = foreground, fontSize = 12.sp)
                        Slider(lineSpace, { lineSpace = it }, valueRange = 1.4f..2.2f, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = RoseDeep, activeTrackColor = RoseDeep))
                    }
                }
            }
        }
    }
}

private fun formatNovelText(raw: String): List<String> {
    val cleaned = raw.replace("\r\n", "\n").replace('\r', '\n')
        .replace(Regex("[\\t ]{2,}"), " ")
        .trim()
    if (cleaned.isBlank()) return emptyList()
    val chunks = cleaned.split(Regex("\n\\s*\u0000?\n+"))
    val paragraphs = chunks.flatMap { chunk ->
        chunk.split('\n').map { it.trim() }.filter { it.isNotBlank() }
    }
    return paragraphs.map { paragraph ->
        val isChapter = paragraph.matches(Regex("^(第[0-9一二三四五六七八九十百千零〇两]+[章节回卷].{0,50}|正文|序章|楔子|尾声|番外.*)$"))
        if (isChapter) "        $paragraph\n\n" else "　　$paragraph\n\n"
    }
}

