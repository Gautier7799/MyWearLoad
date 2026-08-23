package com.gautier.mywearload

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {

    // متغيرات الساعة
    var watchReceiveStatus by mutableStateOf("جاهز للاستقبال")
    var watchReceivedMegabytes by mutableFloatStateOf(0f)
    var watchIsReceiving by mutableStateOf(false)
    var watchIsSuccess by mutableStateOf(false)

    private val channelCallback = object : ChannelClient.ChannelCallback() {
        override fun onChannelOpened(channel: ChannelClient.Channel) {
            if (channel.path == "/wearload_apk_transfer") {
                watchIsReceiving = true
                watchIsSuccess = false
                watchReceiveStatus = "جاري الاستلام..."
                watchReceivedMegabytes = 0f

                CoroutineScope(Dispatchers.IO).launch {
                    val apkFile = File(cacheDir, "received_app.apk")
                    var totalBytes = 0L

                    try {
                        val channelClient = Wearable.getChannelClient(this@MainActivity)
                        val inputStream = Tasks.await(channelClient.getInputStream(channel))
                        val outputStream = FileOutputStream(apkFile)
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        
                        try {
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                totalBytes += bytesRead
                                watchReceivedMegabytes = (totalBytes.toFloat() / (1024f * 1024f))
                            }
                        } catch (e: Exception) { } finally {
                            outputStream.close()
                            inputStream.close()
                            channelClient.close(channel)
                        }

                        // فاحص الملف الذكي
                        val packageInfo = packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                        
                        if (packageInfo != null) {
                            watchIsReceiving = false
                            watchReceiveStatus = "⏳ جاري تحضير شاشة التثبيت..."
                            installApk(apkFile)
                        } else {
                            watchIsReceiving = false
                            watchIsSuccess = false
                            watchReceiveStatus = "❌ الملف غير مكتمل، أعد الإرسال"
                        }

                    } catch (e: Exception) {
                        watchIsReceiving = false
                        watchIsSuccess = false
                        watchReceiveStatus = "❌ حدث خطأ في الاتصال"
                    }
                }
            }
        }
        override fun onChannelClosed(channel: ChannelClient.Channel, closeReason: Int, appSpecificErrorCode: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val isWatch = packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
        if (isWatch) {
            Wearable.getChannelClient(this).registerChannelCallback(channelCallback)
        }

        setContent {
            val darkBg = Color(0xFF19242C) 
            if (isWatch) {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = darkBg) {
                        WatchModernUI(this)
                    }
                }
            } else {
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
    }

    override fun onDestroy() {
        super.onDestroy()
        val isWatch = packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
        if (isWatch) {
            Wearable.getChannelClient(this).unregisterChannelCallback(channelCallback)
        }
    }

    private fun installApk(apkFile: File) {
        try {
            val packageInstaller = packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            val out = session.openWrite("wearload_install", 0, apkFile.length())
            val input = FileInputStream(apkFile)
            val buffer = ByteArray(65536)
            var c: Int
            while (input.read(buffer).also { c = it } >= 0) {
                out.write(buffer, 0, c)
            }
            session.fsync(out)
            input.close()
            out.close()

            val action = "com.gautier.mywearload.INSTALL_COMPLETE"
            
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    
                    when (status) {
                        PackageInstaller.STATUS_SUCCESS -> {
                            watchReceiveStatus = "✅ تم التثبيت بنجاح!"
                            watchIsSuccess = true
                        }
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            val userAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(Intent.EXTRA_INTENT)
                            }
                            
                            if (userAction != null) {
                                userAction.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(userAction)
                                watchReceiveStatus = "⏳ أكمل التثبيت من الشاشة..."
                            }
                        }
                        else -> {
                            watchReceiveStatus = "❌ فشل التثبيت: $message"
                            watchIsSuccess = false
                        }
                    }
                    try { context.unregisterReceiver(this) } catch (e: Exception) {}
                }
            }

            val intentFilter = IntentFilter(action)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(receiver, intentFilter)
            }

            val intent = Intent(action).setPackage(packageName)
            val pendingIntent = PendingIntent.getBroadcast(
                this, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pendingIntent.intentSender)

        } catch (e: Exception) {
            watchReceiveStatus = "❌ فشل التثبيت: ${e.message}"
            watchIsSuccess = false
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

// ==========================================
// تصميم شاشة الساعة
// ==========================================
@Composable
fun WatchModernUI(activity: MainActivity) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Text("My WearLoad", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC3E7FF))
            Spacer(modifier = Modifier.height(12.dp))

            if (activity.watchIsReceiving) {
                // تم إزالة trackColor ليتوافق مع الإصدارات الأقدم
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = Color(0xFF3B82F6),
                    strokeWidth = 4.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(String.format("%.1f MB", activity.watchReceivedMegabytes), fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
            } else if (activity.watchIsSuccess) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Success", tint = Color(0xFF34A853), modifier = Modifier.size(48.dp))
            } else if (activity.watchReceiveStatus.contains("❌")) {
                Icon(Icons.Filled.Warning, contentDescription = "Error", tint = Color(0xFFEA4335), modifier = Modifier.size(48.dp))
            } else if (activity.watchReceiveStatus.contains("⏳")) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = Color(0xFFFABB05), 
                    strokeWidth = 4.dp
                )
            } else {
                Box(modifier = Modifier.size(48.dp).background(Color(0xFF2C3E48), CircleShape).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Text("⌚", fontSize = 20.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(activity.watchReceiveStatus, fontSize = 12.sp, color = Color.LightGray, textAlign = TextAlign.Center)
        }
    }
}

// ==========================================
// تصميم شاشة الهاتف (V5.5)
// ==========================================
data class StoreFace(val id: String, val name: String, val author: String, val imageUrl: String, val downloadUrl: String)

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
    var showAddDialog by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()
    
    val storeFaces = remember { mutableStateListOf(
        StoreFace("1", "Casio Retro", "Classic Watch", "https://cdn2.f-cdn.com/contestentries/1381283/26462719/5b583f769fa95_thumb900.jpg", "https://f-droid.org/F-Droid.apk"),
        StoreFace("2", "Pixel Minimal", "Modern UI", "https://i.pinimg.com/736x/87/4f/ea/874feab00bb7097c55c70757754d9299.jpg", "https://f-droid.org/F-Droid.apk")
    )}

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
            Text("Pro Edition V5.5", fontSize = 16.sp, color = materialYouColor, fontWeight = FontWeight.Bold)
        }

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Button(
                onClick = { currentTab = "LOCAL" }, modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 0.dp, bottomEnd = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (currentTab == "LOCAL") onColor else offColor)
            ) { Text("ملف محلي", color = Color.White, fontWeight = FontWeight.Bold) }
            
            Button(
                onClick = { currentTab = "STORE" }, modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 16.dp, bottomEnd = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (currentTab == "STORE") onColor else offColor)
            ) { Text("المتجر", color = Color.White, fontWeight = FontWeight.Bold) }
        }

        if (currentTab == "LOCAL") {
            Button(
                onClick = { filePickerLauncher.launch("application/vnd.android.package-archive") }, 
                modifier = Modifier.fillMaxWidth().height(80.dp), shape = RoundedCornerShape(24.dp), 
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
                            isSending = true; transferProgress = 0f
                            sendApkWithProgress(activity, selectedFileUri!!, selectedFileSize, { transferProgress = it }, { processStatus = it })
                            isSending = false
                        }
                    }
                }, 
                enabled = selectedFileUri != null && !isSending, modifier = Modifier.fillMaxWidth().height(80.dp), shape = RoundedCornerShape(24.dp), 
                colors = ButtonDefaults.buttonColors(containerColor = if (selectedFileUri != null) onColor else offColor.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(if (isSending) "جاري الإرسال..." else "إرسال للساعة", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
            }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(storeFaces) { face ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).background(offColor, RoundedCornerShape(16.dp)).padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = face.imageUrl,
                                contentDescription = "Watch Face",
                                modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.DarkGray)
                            )
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(face.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(face.author, color = Color.Gray, fontSize = 12.sp)
                            }
                            
                            IconButton(
                                onClick = {
                                    if (!isSending) {
                                        coroutineScope.launch {
                                            isSending = true; transferProgress = 0f
                                            val downloadedUri = downloadApkFromUrl(activity, face.downloadUrl, { transferProgress = it }, { processStatus = it })
                                            if (downloadedUri != null) {
                                                transferProgress = 0f
                                                sendApkWithProgress(activity, downloadedUri, File(downloadedUri.path!!).length(), { transferProgress = it }, { processStatus = it })
                                            }
                                            isSending = false
                                        }
                                    }
                                },
                                modifier = Modifier.background(materialYouColor, CircleShape).size(40.dp)
                            ) { Icon(Icons.Filled.ShoppingCart, contentDescription = "Download", tint = materialYouIcon, modifier = Modifier.size(20.dp)) }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            IconButton(
                                onClick = { storeFaces.remove(face) },
                                modifier = Modifier.background(Color(0xFFEA4335).copy(alpha = 0.2f), CircleShape).size(40.dp)
                            ) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFFEA4335), modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
                
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    containerColor = onColor
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Store", tint = Color.White)
                }
            }
        }

        Spacer(modifier = if (currentTab == "LOCAL") Modifier.weight(1f) else Modifier.height(8.dp))

        Column(
            modifier = Modifier.fillMaxWidth().background(offColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp)).padding(16.dp).heightIn(min = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            if (isSending || transferProgress > 0f) {
                LinearProgressIndicator(progress = transferProgress, modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape), color = Color(0xFF34A853), trackColor = darkBg)
                Spacer(modifier = Modifier.height(8.dp))
                Text("${(transferProgress * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold)
                if (processStatus.isNotEmpty()) Text(processStatus, color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            } else if (processStatus.isNotEmpty()) {
                Text(processStatus, color = if (processStatus.contains("❌")) Color(0xFFEA4335) else Color(0xFF81C995), fontWeight = FontWeight.Medium, fontSize = 14.sp, textAlign = TextAlign.Center)
            } else {
                Text("جاهز للاستخدام", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }

    if (showAddDialog) {
        var newName by remember { mutableStateOf("") }
        var newAuthor by remember { mutableStateOf("") }
        var newImgUrl by remember { mutableStateOf("") }
        var newApkUrl by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = offColor,
            title = { Text("إضافة واجهة جديدة", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    // تم تبسيط الكود ليكون متوافقاً مع كل الإصدارات
                    OutlinedTextField(
                        value = newName, 
                        onValueChange = { newName = it }, 
                        label = { Text("اسم الواجهة (مثال: Rolex)", color = Color.Gray) }, 
                        modifier = Modifier.fillMaxWidth(), 
                        textStyle = TextStyle(color = Color.White)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newAuthor, 
                        onValueChange = { newAuthor = it }, 
                        label = { Text("المطور", color = Color.Gray) }, 
                        modifier = Modifier.fillMaxWidth(), 
                        textStyle = TextStyle(color = Color.White)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newImgUrl, 
                        onValueChange = { newImgUrl = it }, 
                        label = { Text("رابط الصورة (JPG/PNG)", color = Color.Gray) }, 
                        modifier = Modifier.fillMaxWidth(), 
                        textStyle = TextStyle(color = Color.White)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newApkUrl, 
                        onValueChange = { newApkUrl = it }, 
                        label = { Text("رابط التحميل (APK)", color = Color.Gray) }, 
                        modifier = Modifier.fillMaxWidth(), 
                        textStyle = TextStyle(color = Color.White)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotEmpty() && newApkUrl.isNotEmpty()) {
                        storeFaces.add(StoreFace(System.currentTimeMillis().toString(), newName, newAuthor, newImgUrl, newApkUrl))
                        showAddDialog = false
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = onColor)) { Text("حفظ وإضافة") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("إلغاء", color = Color.LightGray) }
            }
        )
    }
}

suspend fun downloadApkFromUrl(context: Context, urlString: String, onProgressUpdate: (Float) -> Unit, onStatusUpdate: (String) -> Unit): Uri? {
    return withContext(Dispatchers.IO) {
        try {
            onStatusUpdate("⬇️ جاري الاتصال بالسيرفر...")
            var url = URL(urlString)
            var connection = url.openConnection() as HttpURLConnection
            var redirectCount = 0
            var responseCode: Int

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
                } else break
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
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
                if (fileLength > 0) onProgressUpdate(total.toFloat() / fileLength.toFloat())
                output.write(data, 0, count)
            }
            output.flush(); output.close(); input.close()
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
            
            val inputStream = if (apkUri.scheme == "file") FileInputStream(File(apkUri.path!!)) else context.contentResolver.openInputStream(apkUri)
            val outputStream = Tasks.await(channelClient.getOutputStream(channel))
            
            if (inputStream != null && outputStream != null) {
                onStatusUpdate("📡 جاري الإرسال للساعة...")
                delay(500)
                val buffer = ByteArray(8 * 1024)
                var bytesCopied = 0L
                var bytes = inputStream.read(buffer)
                while (bytes >= 0) {
                    outputStream.write(buffer, 0, bytes)
                    bytesCopied += bytes
                    if (totalSize > 0) onProgressUpdate(bytesCopied.toFloat() / totalSize.toFloat())
                    bytes = inputStream.read(buffer)
                }
                outputStream.flush()
                delay(1000)
                inputStream.close(); outputStream.close(); channelClient.close(channel)
                onProgressUpdate(1f)
                onStatusUpdate("✅ تم الإرسال بنجاح! راقب شاشة ساعتك.")
            }
        } catch (e: Exception) { onStatusUpdate("❌ خطأ تقني: ${e.message}") }
    }
}
