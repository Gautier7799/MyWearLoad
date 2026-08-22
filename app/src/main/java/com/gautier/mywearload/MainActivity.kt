package com.gautier.mywearload

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.tasks.Tasks
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
            MaterialTheme { Surface(modifier = Modifier.fillMaxSize(), color = darkBg) { WearModernUI(this, buttonOffBg, buttonOnBg) } }
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
fun WearModernUI(activity: MainActivity, offColor: Color, onColor: Color) {
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var selectedFileSize by remember { mutableLongStateOf(1L) }
    var processStatus by remember { mutableStateOf("") }
    var transferProgress by remember { mutableFloatStateOf(0f) }
    var isSending by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            val info = activity.getFileInfo(uri)
            selectedFileName = info.first; selectedFileSize = info.second
            processStatus = ""; transferProgress = 0f
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("My WearLoad", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Pro Edition", fontSize = 16.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(48.dp))

        Button(onClick = { filePickerLauncher.launch("application/vnd.android.package-archive") }, modifier = Modifier.fillMaxWidth().height(80.dp), shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(containerColor = offColor)) {
            Icon(Icons.Filled.Build, contentDescription = "File", tint = Color.LightGray, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.Start) {
                Text(if (selectedFileUri == null) "اختر ملف Cadran" else selectedFileName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(if (selectedFileUri == null) "اضغط هنا للبدء" else "جاهز للإرسال", color = Color.LightGray, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { 
            if (selectedFileUri != null && !isSending) {
                coroutineScope.launch {
                    isSending = true; transferProgress = 0f
                    sendApkWithProgress(activity, selectedFileUri!!, selectedFileSize, { transferProgress = it }, { processStatus = it })
                    isSending = false
                }
            }
        }, enabled = selectedFileUri != null && !isSending, modifier = Modifier.fillMaxWidth().height(80.dp), shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(containerColor = if (selectedFileUri != null) onColor else offColor.copy(alpha = 0.5f))) {
            Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(if (isSending) "جاري الإرسال..." else "إرسال للساعة", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(32.dp))

        if (isSending || transferProgress > 0f) {
            LinearProgressIndicator(progress = transferProgress, modifier = Modifier.fillMaxWidth().height(12.dp), color = Color(0xFF34A853), trackColor = offColor)
            Spacer(modifier = Modifier.height(8.dp))
            Text("${(transferProgress * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (processStatus.isNotEmpty()) {
            Text(processStatus, color = if (processStatus.contains("❌")) Color(0xFFEA4335) else Color(0xFF81C995), fontWeight = FontWeight.Medium, fontSize = 14.sp)
        }
    }
}

suspend fun sendApkWithProgress(context: Context, apkUri: Uri, totalSize: Long, onProgressUpdate: (Float) -> Unit, onStatusUpdate: (String) -> Unit) {
    withContext(Dispatchers.IO) {
        try {
            onStatusUpdate("🔍 جاري الاتصال بالساعة...")
            val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
            val watchNode = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
            if (watchNode == null) { onStatusUpdate("❌ فشل: تأكد من تشغيل البلوتوث!"); return@withContext }
            
            onStatusUpdate("🔗 جاري فتح القناة...")
            val channelClient = Wearable.getChannelClient(context)
            val channel = Tasks.await(channelClient.openChannel(watchNode.id, "/wearload_apk_transfer"))
            
            val inputStream = context.contentResolver.openInputStream(apkUri)
            val outputStream = Tasks.await(channelClient.getOutputStream(channel))
            
            if (inputStream != null && outputStream != null) {
                onStatusUpdate("📡 جاري النقل (يمكنك ترك شاشة الساعة تنطفئ الآن)...")
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
                onStatusUpdate("✅ تم الإرسال! افتح شاشة ساعتك للموافقة على التثبيت.")
            }
        } catch (e: Exception) { onStatusUpdate("❌ خطأ تقني: ${e.message ?: e.javaClass.simpleName}") }
    }
}
