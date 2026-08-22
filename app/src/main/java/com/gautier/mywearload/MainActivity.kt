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
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkBg = Color(0xFF19242C)
            val buttonOffBg = Color(0xFF2C3E48)
            val buttonOnBg = Color(0xFF3B82F6)
            
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = darkBg) {
                    WearModernUI(this, buttonOffBg, buttonOnBg)
                }
            }
        }
    }

    fun getFileInfo(uri: Uri): Pair<String, Long> {
        var name = "cadran.apk"
        var size = 1L
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) name = it.getString(nameIndex)
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
    
    var processStatus by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            selectedFileName = activity.getFileInfo(uri).first
            processStatus = ""
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("My WearLoad", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Pro Edition", fontSize = 16.sp, color = Color.Gray)
        
        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { filePickerLauncher.launch("application/vnd.android.package-archive") },
            modifier = Modifier.fillMaxWidth().height(80.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = offColor)
        ) {
            Icon(Icons.Filled.Build, contentDescription = "File", tint = Color.LightGray, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.Start) {
                Text(if (selectedFileUri == null) "اختر ملف Cadran" else selectedFileName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(if (selectedFileUri == null) "اضغط هنا للبدء" else "جاهز للإرسال", color = Color.LightGray, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { 
                if (selectedFileUri != null && !isSending) {
                    coroutineScope.launch {
                        isSending = true
                        sendApkReliable(activity, selectedFileUri!!) { status -> 
                            processStatus = status 
                        }
                        isSending = false
                    }
                }
            },
            enabled = selectedFileUri != null && !isSending,
            modifier = Modifier.fillMaxWidth().height(80.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedFileUri != null) onColor else offColor.copy(alpha = 0.5f)
            )
        ) {
            Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(if (isSending) "جاري الإرسال..." else "إرسال للساعة", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (isSending) {
            // شريط التقدم أصبح يتحرك باستمرار (دليل على استقرار النقل)
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(12.dp),
                color = Color(0xFF34A853),
                trackColor = offColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("جاري النقل بطريقة موثوقة...", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (processStatus.isNotEmpty()) {
            Text(
                text = processStatus, 
                color = if (processStatus.contains("❌")) Color(0xFFEA4335) else Color(0xFF81C995), 
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
        }
    }
}

suspend fun sendApkReliable(
    context: Context, 
    apkUri: Uri, 
    onStatusUpdate: (String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            onStatusUpdate("🔍 جاري الاتصال بالساعة...")
            val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
            val watchNode = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()

            if (watchNode == null) {
                onStatusUpdate("❌ فشل: تأكد من تشغيل البلوتوث وأن الساعة متصلة!")
                return@withContext
            }

            onStatusUpdate("📦 جاري تجهيز الملف للإرسال...")
            val tempFile = File(context.getExternalFilesDir(null), "temp_send.apk")
            val inputStream = context.contentResolver.openInputStream(apkUri)
            val outputStream = FileOutputStream(tempFile)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            
            val fileUri = Uri.fromFile(tempFile)

            onStatusUpdate("🔗 فتح قناة الاتصال الموثوقة...")
            val channelClient = Wearable.getChannelClient(context)
            val channel = Tasks.await(channelClient.openChannel(watchNode.id, "/wearload_apk_transfer"))

            onStatusUpdate("📡 جاري النقل (يرجى الانتظار ولا تغلق الشاشة)...")
            // الإرسال بالطريقة الرسمية من جوجل (مستقرة 100%)
            Tasks.await(channelClient.sendFile(channel, fileUri))
            
            onStatusUpdate("✅ تم النقل بنجاح! راقب شاشة ساعتك.")

        } catch (e: Exception) {
            onStatusUpdate("❌ خطأ تقني: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}
