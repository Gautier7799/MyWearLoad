package com.gautier.mywearload

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkBg = Color(0xFF19242C) 
            val buttonOffBg = Color(0xFF2C3E48) 
            val buttonOnBg = Color(0xFF3B82F6) 
            val materialYouColor = Color(0xFFC3E7FF) 
            val materialYouIcon = Color(0xFF004A77) 
            
            MaterialTheme { 
                Surface(modifier = Modifier.fillMaxSize(), color = darkBg) { 
                    WearModernUI(this, darkBg, buttonOffBg, buttonOnBg, materialYouColor, materialYouIcon) 
                } 
            }
        }
    }

    fun getFileInfo(uri: Uri): Pair<String, Long> {
        var name = "cadran.apk"
        var size = 1L
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use {
                if (it.moveToFirst()) {
                    val nIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sIdx = it.getColumnIndex(OpenableColumns.SIZE)
                    if (nIdx >= 0) name = it.getString(nIdx)
                    if (sIdx >= 0) size = it.getLong(sIdx)
                }
            }
        }
        return Pair(name, size)
    }
}

data class StoreFace(val id: String, val name: String, val author: String, val iconColor: Color, val downloadUrl: String)

@Composable
fun WearModernUI(
    activity: MainActivity, 
    darkBg: Color, 
    offColor: Color, 
    onColor: Color,
    materialYouColor: Color,
    materialYouIcon: Color
) {
    var currentTab by remember { mutableStateOf("LOCAL") }
    
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var selectedFileSize by remember { mutableLongStateOf(1L) }
    
    var processStatus by remember { mutableStateOf("") }
    var transferProgress by remember { mutableFloatStateOf(0f) }
    var isSending by remember { mutableStateOf(false) }
    
    var showFacesList by remember { mutableStateOf(false) }
    var isFetchingList by remember { mutableStateOf(false) }
    var facesList by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    
    val coroutineScope = rememberCoroutineScope()
    
    // 🔥 استخدمت روابط حقيقية ومضمونة 100% للتجربة (حجمها حوالي 8 ميجا لنرى التحميل) 🔥
    val storeFaces = listOf(
        StoreFace("1", "App Test 1", "F-Droid Store", Color(0xFFE53935), "https://f-droid.org/F-Droid.apk"),
        StoreFace("2", "App Test 2", "F-Droid Store", Color(0xFF8E24AA), "https://f-droid.org/F-Droid.apk"),
        StoreFace("3", "App Test 3", "F-Droid Store", Color(0xFF3949AB), "https://f-droid.org/F-Droid.apk")
    )

    DisposableEffect(Unit) {
        val messageClient = Wearable.getMessageClient(activity)
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.path == "/installed_faces_list") {
                val dataString = String(event.data)
                if (dataString == "EMPTY") {
                    facesList = emptyList()
                } else if (dataString.startsWith("ERROR|")) {
                    facesList = listOf(Pair("خطأ في الساعة:", dataString.removePrefix("ERROR|")))
                } else if (dataString.isNotEmpty()) {
                    val items = dataString.split(";;;")
                    facesList = items.mapNotNull { 
                        val parts = it.split("|")
                        if (parts.size == 2) Pair(parts[0], parts[1]) else null
                    }
                }
                isFetchingList = false
                showFacesList = true
            }
        }
        messageClient.addListener(listener)
        onDispose { messageClient.removeListener(listener) }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            val info = activity.getFileInfo(uri)
            selectedFileName = info.first
            selectedFileSize = info.second
            processStatus = ""
            transferProgress = 0f
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        
        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("My WearLoad", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Pro Edition V4.2", fontSize = 16.sp, color = materialYouColor, fontWeight = FontWeight.Bold)
        }

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Button(
                onClick = { currentTab = "LOCAL" },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 0.dp, bottomEnd = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (currentTab == "LOCAL") onColor else offColor)
            ) {
                Icon(Icons.Filled.Build, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("ملف محلي", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { currentTab = "STORE" },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 16.dp, bottomEnd = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (currentTab == "STORE") onColor else offColor)
            ) {
                Icon(Icons.Filled.ShoppingCart, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("المتجر", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        if (currentTab == "LOCAL") {
            Button(
                onClick = { filePickerLauncher.launch("application/vnd.android.package-archive") }, 
                modifier = Modifier.fillMaxWidth().height(80.dp), 
                shape = RoundedCornerShape(24.dp), 
                colors = ButtonDefaults.buttonColors(containerColor = offColor)
            ) {
                Icon(Icons.Filled.Build, contentDescription = "File", tint = Color.LightGray, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
                    Text(if (selectedFileUri == null) "اختر ملف Cadran" else selectedFileName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(if (selectedFileUri == null) "من ذاكرة الهاتف" else "جاهز للإرسال", color = Color.LightGray, fontSize = 12.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { 
                    if (selectedFileUri != null && !isSending) {
                        coroutineScope.launch {
                            isSending = true
                            transferProgress = 0f
                            sendApkWithProgress(activity, selectedFileUri!!, selectedFileSize, { transferProgress = it }, { processStatus = it })
                            isSending = false
                        }
                    }
                }, 
                enabled = selectedFileUri != null && !isSending, 
                modifier = Modifier.fillMaxWidth().height(80.dp), 
                shape = RoundedCornerShape(24.dp), 
                colors = ButtonDefaults.buttonColors(containerColor = if (selectedFileUri != null) onColor else offColor.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(if (isSending) "جاري الإرسال..." else "إرسال للساعة", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(storeFaces) { face ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .background(offColor, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(48.dp).background(face.iconColor, CircleShape), contentAlignment = Alignment.Center) {
                            Text(face.name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(face.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(face.author, color = Color.Gray, fontSize = 12.sp)
                        }
                        IconButton(
                            onClick = {
                                if (!isSending) {
                                    coroutineScope.launch {
                                        isSending = true
                                        transferProgress = 0f
                                        
                                        val downloadedUri = downloadApkFromUrl(activity, face.downloadUrl, { transferProgress = it }, { processStatus = it })
                                        
                                        if (downloadedUri != null) {
                                            transferProgress = 0f
                                            val file = File(downloadedUri.path!!)
                                            sendApkWithProgress(activity, downloadedUri, file.length(), { transferProgress = it }, { processStatus = it })
                                        }
                                        isSending = false
                                    }
                                }
                            },
                            modifier = Modifier.background(materialYouColor, CircleShape).size(40.dp)
                        ) {
                            Icon(Icons.Filled.ShoppingCart, contentDescription = "Download", tint = materialYouIcon, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        if (currentTab == "LOCAL") {
            Spacer(modifier = Modifier.weight(1f))
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(offColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(16.dp)
                .heightIn(min = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isSending || transferProgress > 0f) {
                LinearProgressIndicator(
                    progress = transferProgress, 
                    modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape), 
                    color = Color(0xFF34A853), 
                    trackColor = darkBg
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("${(transferProgress * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold)
                if (processStatus.isNotEmpty()) {
                    Text(processStatus, color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            } else if (processStatus.isNotEmpty()) {
                Text(
                    text = processStatus, 
                    color = if (processStatus.contains("❌")) Color(0xFFEA4335) else Color(0xFF81C995), 
                    fontWeight = FontWeight.Medium, 
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            } else {
                Text("جاهز للاستخدام", color = Color.Gray, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), 
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .background(if (isFetchingList) offColor else materialYouColor, RoundedCornerShape(32.dp))
                    .clip(RoundedCornerShape(32.dp))
                    .clickable {
                        if (!isFetchingList) {
                            isFetchingList = true
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val nodes = Tasks.await(Wearable.getNodeClient(activity).connectedNodes)
                                    val watchNode = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
                                    if (watchNode != null) {
                                        Wearable.getMessageClient(activity).sendMessage(watchNode.id, "/request_installed_faces", ByteArray(0))
                                        delay(6000)
                                        if (isFetchingList) {
                                            isFetchingList = false
                                            facesList = listOf(Pair("الساعة لا ترد 💤", "تأكد أن التطبيق مفتوح في شاشة الساعة."))
                                            showFacesList = true
                                        }
                                    } else {
                                        isFetchingList = false
                                    }
                                } catch (e: Exception) { isFetchingList = false }
                            }
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isFetchingList) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.List, contentDescription = "History", tint = materialYouIcon, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("السجل", color = materialYouIcon, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            
            Box(modifier = Modifier.size(64.dp).background(offColor, CircleShape).clip(CircleShape).clickable { }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Star, contentDescription = "Gemini", tint = Color(0xFFFABB05), modifier = Modifier.size(32.dp))
            }
            
            Box(modifier = Modifier.size(64.dp).background(offColor, CircleShape).clip(CircleShape).clickable { }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.LightGray, modifier = Modifier.size(28.dp))
            }
        }
    }

    if (showFacesList) {
        AlertDialog(
            onDismissRequest = { showFacesList = false },
            title = { Text("التطبيقات و الواجهات", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxHeight(0.6f)) {
                    items(facesList) { face ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(face.first, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(face.second, color = Color.Gray, fontSize = 12.sp)
                            }
                            if (face.first != "الساعة لا ترد 💤" && !face.first.startsWith("خطأ")) {
                                IconButton(onClick = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val nodes = Tasks.await(Wearable.getNodeClient(activity).connectedNodes)
                                        val watchNode = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
                                        if (watchNode != null) {
                                            Wearable.getMessageClient(activity).sendMessage(watchNode.id, "/uninstall_face", face.second.toByteArray())
                                        }
                                    }
                                    showFacesList = false
                                }) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFFEA4335)) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFacesList = false }) { Text("إغلاق", color = onColor, fontWeight = FontWeight.Bold) } },
            containerColor = darkBg
        )
    }
}

// 🔥 دالة التحميل الذكية التي تتخطى الروابط المعقدة وتعطي الخطأ الفعلي 🔥
suspend fun downloadApkFromUrl(context: Context, urlString: String, onProgressUpdate: (Float) -> Unit, onStatusUpdate: (String) -> Unit): Uri? {
    return withContext(Dispatchers.IO) {
        try {
            onStatusUpdate("⬇️ جاري الاتصال بالسيرفر...")
            var url = URL(urlString)
            var connection = url.openConnection() as HttpURLConnection
            var redirectCount = 0
            var responseCode: Int

            // حلقة للتعامل مع إعادة التوجيه (Redirects)
            while (true) {
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = false 
                connection.connect()

                responseCode = connection.responseCode
                if (responseCode in 300..399) {
                    val redirectUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    url = URL(redirectUrl)
                    connection = url.openConnection() as HttpURLConnection
                    redirectCount++
                    if (redirectCount > 5) {
                        onStatusUpdate("❌ خطأ: تحويلات السيرفر كثيرة")
                        return@withContext null
                    }
                } else {
                    break
                }
            }

            // فحص الخطأ وطباعته
            if (responseCode == 404) {
                onStatusUpdate("❌ الملف غير موجود بالرابط (404)")
                return@withContext null
            } else if (responseCode != HttpURLConnection.HTTP_OK) {
                onStatusUpdate("❌ خطأ في السيرفر: Code $responseCode")
                return@withContext null
            }

            onStatusUpdate("⬇️ جاري التحميل من السحابة...")
            val fileLength = connection.contentLength
            val input = BufferedInputStream(connection.inputStream)
            
            val tempFile = File(context.cacheDir, "temp_face.apk")
            if (tempFile.exists()) tempFile.delete()
            
            val output = FileOutputStream(tempFile)
            val data = ByteArray(8 * 1024)
            var total = 0L
            var count: Int
            
            while (input.read(data).also { count = it } != -1) {
                total += count.toLong()
                if (fileLength > 0) {
                    onProgressUpdate(total.toFloat() / fileLength.toFloat())
                }
                output.write(data, 0, count)
            }
            output.flush()
            output.close()
            input.close()
            
            Uri.fromFile(tempFile)
        } catch (e: Exception) {
            onStatusUpdate("❌ خطأ بالاتصال: ${e.message}")
            null
        }
    }
}

suspend fun sendApkWithProgress(context: Context, apkUri: Uri, totalSize: Long, onProgressUpdate: (Float) -> Unit, onStatusUpdate: (String) -> Unit) {
    withContext(Dispatchers.IO) {
        try {
            onStatusUpdate("🔍 جاري الاتصال بالساعة...")
            val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
            val watchNode = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
            if (watchNode == null) { onStatusUpdate("❌ فشل: تأكد من تشغيل البلوتوث وربط الساعة!"); return@withContext }
            
            val channelClient = Wearable.getChannelClient(context)
            val channel = Tasks.await(channelClient.openChannel(watchNode.id, "/wearload_apk_transfer"))
            
            val inputStream = context.contentResolver.openInputStream(apkUri)
            val outputStream = Tasks.await(channelClient.getOutputStream(channel))
            
            if (inputStream != null && outputStream != null) {
                onStatusUpdate("📡 جاري الإرسال للساعة...")
                val buffer = ByteArray(8 * 1024)
                var bytesCopied = 0L
                var bytes = inputStream.read(buffer)
                while (bytes >= 0) {
                    outputStream.write(buffer, 0, bytes)
                    bytesCopied += bytes
                    if (totalSize > 0) onProgressUpdate(bytesCopied.toFloat() / totalSize.toFloat())
                    bytes = inputStream.read(buffer)
                }
                inputStream.close(); outputStream.close(); channelClient.close(channel)
                onProgressUpdate(1f)
                onStatusUpdate("✅ تم الإرسال بنجاح! وافق على التثبيت في ساعتك.")
            }
        } catch (e: Exception) { onStatusUpdate("❌ خطأ تقني: ${e.message}") }
    }
}
