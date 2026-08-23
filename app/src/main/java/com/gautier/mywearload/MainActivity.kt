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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkBg = Color(0xFF19242C)
            val buttonOffBg = Color(0xFF2C3E48)
            val buttonOnBg = Color(0xFF3B82F6)
            
            MaterialTheme { 
                Surface(modifier = Modifier.fillMaxSize(), color = darkBg) { 
                    WearModernUI(this, darkBg, buttonOffBg, buttonOnBg) 
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

@Composable
fun WearModernUI(activity: MainActivity, darkBg: Color, offColor: Color, onColor: Color) {
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var selectedFileSize by remember { mutableLongStateOf(1L) }
    var processStatus by remember { mutableStateOf("") }
    var transferProgress by remember { mutableFloatStateOf(0f) }
    var isSending by remember { mutableStateOf(false) }
    
    // متغيرات القائمة
    var showFacesList by remember { mutableStateOf(false) }
    var isFetchingList by remember { mutableStateOf(false) }
    var facesList by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    
    val coroutineScope = rememberCoroutineScope()
    
    DisposableEffect(Unit) {
        val messageClient = Wearable.getMessageClient(activity)
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.path == "/installed_faces_list") {
                val dataString = String(event.data)
                if (dataString == "EMPTY") {
                    facesList = emptyList()
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
            selectedFileName = info.first; selectedFileSize = info.second
            processStatus = ""; transferProgress = 0f
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("My WearLoad", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Pro Edition", fontSize = 16.sp, color = Color.Gray)
        }

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
                Text(if (selectedFileUri == null) "اضغط هنا للبدء" else "جاهز للإرسال", color = Color.LightGray, fontSize = 12.sp)
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

        Spacer(modifier = Modifier.weight(1f))

        if (isSending || transferProgress > 0f) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(progress = transferProgress, modifier = Modifier.fillMaxWidth().height(12.dp), color = Color(0xFF34A853), trackColor = offColor)
                Spacer(modifier = Modifier.height(8.dp))
                Text("${(transferProgress * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        if (processStatus.isNotEmpty()) {
            Text(processStatus, color = if (processStatus.contains("❌")) Color(0xFFEA4335) else Color(0xFF81C995), fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp).align(Alignment.CenterHorizontally))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 🔥 تعديل الزر هنا ليعمل مباشرة بدون شروط معقدة 🔥
            IconButton(
                onClick = {
                    isFetchingList = true
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val nodes = Tasks.await(Wearable.getNodeClient(activity).connectedNodes)
                            val watchNode = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
                            if (watchNode != null) {
                                Wearable.getMessageClient(activity).sendMessage(watchNode.id, "/request_installed_faces", ByteArray(0))
                            } else {
                                isFetchingList = false
                            }
                        } catch (e: Exception) { 
                            isFetchingList = false 
                        }
                    }
                },
                modifier = Modifier.size(64.dp).background(offColor, CircleShape)
            ) {
                if (isFetchingList) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.List, contentDescription = "History", tint = Color.LightGray, modifier = Modifier.size(32.dp))
                }
            }
            
            IconButton(
                onClick = { },
                modifier = Modifier.size(64.dp).background(offColor, CircleShape)
            ) {
                Icon(Icons.Filled.Star, contentDescription = "Gemini", tint = Color(0xFFFABB05), modifier = Modifier.size(32.dp))
            }
            IconButton(
                onClick = { },
                modifier = Modifier.size(64.dp).background(offColor, CircleShape)
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.LightGray, modifier = Modifier.size(32.dp))
            }
        }
    }

    if (showFacesList) {
        AlertDialog(
            onDismissRequest = { showFacesList = false },
            title = { Text("التطبيقات و الواجهات", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                if (facesList.isEmpty()) {
                    Text("لا توجد واجهات مثبتة يدوياً.", color = Color.LightGray)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxHeight(0.6f)) {
                        items(facesList) { face ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(face.first, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(face.second, color = Color.Gray, fontSize = 10.sp)
                                }
                                IconButton(onClick = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val nodes = Tasks.await(Wearable.getNodeClient(activity).connectedNodes)
                                        val watchNode = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
                                        if (watchNode != null) {
                                            Wearable.getMessageClient(activity).sendMessage(watchNode.id, "/uninstall_face", face.second.toByteArray())
                                        }
                                    }
                                    showFacesList = false
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFFEA4335))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFacesList = false }) {
                    Text("إغلاق", color = onColor, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = darkBg
        )
    }
}

suspend fun sendApkWithProgress(context: Context, apkUri: Uri, totalSize: Long, onProgressUpdate: (Float) -> Unit, onStatusUpdate: (String) -> Unit) {
    withContext(Dispatchers.IO) {
        try {
            onStatusUpdate("🔍 جاري الاتصال...")
            val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
            val watchNode = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
            if (watchNode == null) { onStatusUpdate("❌ فشل: تأكد من تشغيل البلوتوث!"); return@withContext }
            
            val channelClient = Wearable.getChannelClient(context)
            val channel = Tasks.await(channelClient.openChannel(watchNode.id, "/wearload_apk_transfer"))
            
            val inputStream = context.contentResolver.openInputStream(apkUri)
            val outputStream = Tasks.await(channelClient.getOutputStream(channel))
            
            if (inputStream != null && outputStream != null) {
                onStatusUpdate("📡 جاري النقل (يمكنك إغلاق شاشة الساعة)...")
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
                onStatusUpdate("✅ تم الإرسال! وافق على التثبيت في ساعتك.")
            }
        } catch (e: Exception) { onStatusUpdate("❌ خطأ تقني: ${e.message}") }
    }
}
