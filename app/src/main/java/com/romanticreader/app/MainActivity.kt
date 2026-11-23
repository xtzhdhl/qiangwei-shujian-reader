package com.romanticreader.app

import android.content.Context
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LocalLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private val Paper = Color(0xFFFAF6F0)
private val Card = Color(0xFFFFFCF8)
private val Ink = Color(0xFF493A3C)
private val Rose = Color(0xFFB87583)
private val Muted = Color(0xFF927E7D)
private val Blush = Color(0xFFF1E3E0)
private data class Novel(val id: String, val title: String, val path: String, val size: Long)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { QiangweiApp(this) }
    }
}

private fun loadNovels(context: Context): List<Novel> {
    val raw = context.getSharedPreferences("library", Context.MODE_PRIVATE).getString("books", "[]") ?: "[]"
    return try {
        val a = JSONArray(raw)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Novel(o.getString("id"), o.getString("title"), o.getString("path"), o.optLong("size"))
        }
    } catch (_: Exception) { emptyList() }
}
private fun saveNovels(context: Context, books: List<Novel>) {
    val a = JSONArray()
    books.forEach { b -> a.put(JSONObject().put("id", b.id).put("title", b.title).put("path", b.path).put("size", b.size)) }
    context.getSharedPreferences("library", Context.MODE_PRIVATE).edit().putString("books", a.toString()).apply()
}
private fun importNovel(context: Context, uri: Uri): Novel? = try {
    val name = (context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val ix = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (c.moveToFirst() && ix >= 0) c.getString(ix) else null
    } ?: "未命名小说.txt").substringBeforeLast('.')
    val file = File(context.filesDir, "novel_${System.currentTimeMillis()}.txt")
    context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use(input::copyTo) }
    Novel(file.nameWithoutExtension, name, file.absolutePath, file.length())
} catch (_: Exception) { null }

@Composable
private fun QiangweiApp(context: Context) {
    var tab by remember { mutableIntStateOf(0) }
    var books by remember { mutableStateOf(loadNovels(context)) }
    var reading by remember { mutableStateOf<Novel?>(null) }
    var content by remember { mutableStateOf("") }
    var importMessage by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val novel = importNovel(context, uri)
            if (novel != null) {
                books = books + novel
                saveNovels(context, books)
                importMessage = "《${novel.title}》已加入书架"
            } else importMessage = "导入失败，请确认文件可以读取"
        }
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = Rose, onPrimary = Color.White, background = Paper, surface = Card, onSurface = Ink)) {
        if (reading != null) {
            ReaderPage(reading!!, content, onBack = { reading = null })
        } else {
            Scaffold(
                containerColor = Paper,
                bottomBar = {
                    NavigationBar(containerColor = Card, tonalElevation = 0.dp) {
                        listOf("书架", "发现", "设置").forEachIndexed { i, label ->
                            val icons = listOf(Icons.Rounded.LocalLibrary, Icons.Rounded.Explore, Icons.Rounded.Settings)
                            NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icons[i], null) }, label = { Text(label) })
                        }
                    }
                }
            ) { padding ->
                when (tab) {
                    0 -> Shelf(Modifier.padding(padding), books, importMessage,
                        onImport = { picker.launch(arrayOf("text/plain", "text/*")) },
                        onOpen = { novel ->
                            content = try { File(novel.path).readText(Charsets.UTF_8) } catch (_: Exception) { "暂时无法读取这本书，请重新导入。" }
                            reading = novel
                        },
                        onDelete = { novel -> books = books.filterNot { it.id == novel.id }; saveNovels(context, books); File(novel.path).delete() })
                    1 -> Discover(Modifier.padding(padding))
                    else -> Settings(Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun Shelf(modifier: Modifier, books: List<Novel>, message: String?, onImport: () -> Unit, onOpen: (Novel) -> Unit, onDelete: (Novel) -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("蔷薇书笺", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text("把喜欢的故事，妥帖收藏。", color = Muted, fontSize = 14.sp)
        }
        item {
            Surface(shape = RoundedCornerShape(26.dp), color = Blush) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("A LITTLE READING MOMENT", color = Rose, fontSize = 10.sp, letterSpacing = 2.sp)
                    Text("让故事慢慢展开", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Medium)
                    Text("把喜爱的文字，收进自己的小小花园。", color = Muted, fontSize = 13.sp)
                    Button(onClick = onImport, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Rose)) {
                        Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("导入本地 TXT")
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("我的书架", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Text("${books.size} 本", color = Muted, fontSize = 13.sp)
            }
        }
        if (message != null) item { Text(message, color = Rose, fontSize = 13.sp) }
        if (books.isEmpty()) item {
            Surface(color = Card, shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Book, null, tint = Rose, modifier = Modifier.size(36.dp))
                    Text("书架还在等待第一本故事", fontWeight = FontWeight.Medium)
                    Text("点击上方按钮，从手机文件中导入 TXT 小说。", color = Muted, fontSize = 12.sp)
                }
            }
        }
        items(books, key = { it.id }) { book ->
            Surface(color = Card, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().clickable { onOpen(book) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(54.dp, 68.dp).background(Blush, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Book, null, tint = Rose, modifier = Modifier.size(27.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(book.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        Text("TXT · ${(book.size / 1024).coerceAtLeast(1)} KB", color = Muted, fontSize = 12.sp)
                        Text("点击继续阅读", color = Rose, fontSize = 12.sp)
                    }
                    IconButton(onClick = { onDelete(book) }) { Icon(Icons.Rounded.DeleteOutline, "删除", tint = Muted) }
                }
            }
        }
    }
}

@Composable
private fun ReaderPage(book: Novel, text: String, onBack: () -> Unit) {
    var fontSize by remember { mutableFloatStateOf(19f) }
    var dark by remember { mutableStateOf(false) }
    val bg = if (dark) Color(0xFF242124) else Color(0xFFF8F3EA)
    val fg = if (dark) Color(0xFFE9DFD6) else Color(0xFF40383A)
    Column(Modifier.fillMaxSize().background(bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回", tint = fg) }
            Text(book.title, modifier = Modifier.weight(1f), color = fg, maxLines = 1, fontWeight = FontWeight.Medium)
            TextButton(onClick = { dark = !dark }) { Text(if (dark) "日间" else "夜间", color = fg) }
        }
        Text("正文", color = if (dark) Muted else Rose, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 26.dp, vertical = 8.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 26.dp, vertical = 18.dp)) {
            item { Text(text, color = fg, fontSize = fontSize.sp, lineHeight = (fontSize * 1.85f).sp) }
        }
        Surface(color = if (dark) Color(0xFF302B30) else Card) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 12.dp)) {
                Text("字号  ${fontSize.toInt()}", color = fg, fontSize = 12.sp)
                Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 14f..30f, colors = SliderDefaults.colors(thumbColor = Rose, activeTrackColor = Rose))
            }
        }
    }
}

@Composable
private fun Discover(modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("发现", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Text("探索书源与下一段故事。", color = Muted)
        Surface(color = Card, shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("在线书源", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text("Legado 兼容书源导入与在线搜索正在接入。", color = Muted, fontSize = 13.sp)
                Text("下一阶段将支持 JSON 书源文件和 URL 导入。", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Settings(modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("设置", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Text("让阅读体验更合心意。", color = Muted)
        Surface(color = Card, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("蔷薇书笺", fontWeight = FontWeight.SemiBold)
                Text("版本 0.1.1 · 本地 TXT 阅读", color = Muted, fontSize = 13.sp)
                Text("已支持本地 TXT 导入、书架保存、正文阅读、字号调整与日夜主题。", color = Muted, fontSize = 12.sp)
            }
        }
    }
}
