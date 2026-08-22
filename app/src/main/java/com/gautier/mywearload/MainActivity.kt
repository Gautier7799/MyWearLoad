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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    WearBluetoothUI(this)
                }
            }
        }
    }

    fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) { result = cursor.getString(index) }
                }
            } finally { cursor?.close() }
        }
        return result ?: "cadran.apk"
    }
}

@Composable
fun WearBluetoothUI(activity: MainActivity) {
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var processStatus by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            selectedFileName = activity.getFileName(uri)
            processStatus = "" 
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("My WearLoad Pro", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Bluetooth Edition ⚡", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
        
        Spacer(modifier = Modifier.height(32.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = "Guide", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("خطوات النقل السريع:", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("1️⃣ اختر ملف الخلفية (Cadran/APK).", fontSize = 14.sp)
                Text("2️⃣ اضغط إرسال للساعة (البلوتوث متصل).", fontSize = 14.sp)
                Text("3️⃣ وافق على التثبيت من شاشة ساعتك.", fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        if (selectedFileUri != null) {
            Text(text = "✅ الملف: $selectedFileName", fontWeight = FontWeight.Medium)
        } else {
            Text(text = "لم يتم اختيار أي ملف", color = MaterialTheme.colorScheme.error)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(onClick = { filePickerLauncher.launch("application/vnd.android.package-archive") }, modifier = Modifier.fillMaxWidth()) {
            Text("1. اختر ملف الخلفية (Cadran)")
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (processStatus.isNotEmpty()) {
            Text(text = processStatus, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = { 
                coroutineScope.launch {
                    if (selectedFileUri != null) {
                        sendApkToWatch(activity, selectedFileUri!!) { status ->
                            processStatus = status
                        }
                    }
                }
            },
            enabled = selectedFileUri != null,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("2. إرسال للساعة 🚀")
        }
    }
}

// ----------------------------------------------------
// محرك الإرسال الفعلي عبر البلوتوث (Wearable Data Layer)
// ----------------------------------------------------
suspend fun sendApkToWatch(context: Context, apkUri: Uri, onProgress: (String) -> Unit) {
    withContext(Dispatchers.IO) {
        try {
            onProgress("🔍 جاري البحث عن الساعة المتصلة بالبلوتوث...")
            
            // جلب الأجهزة (الساعات) المتصلة
            val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
            val watchNode = nodes.firstOrNull()

            if (watchNode == null) {
                onProgress("❌ لم يتم العثور على ساعة متصلة! تأكد من البلوتوث.")
                return@withContext
            }

            onProgress("🔗 تم إيجاد: ${watchNode.displayName}. جاري فتح قناة النقل...")
            val channelClient = Wearable.getChannelClient(context)
            
            // فتح قناة اتصال سريعة مع الساعة
            val channel = Tasks.await(channelClient.openChannel(watchNode.id, "/wearload_apk_transfer"))

            onProgress("📡 جاري إرسال الملف... (قد يستغرق بضع ثوانٍ)")
            val outputStream = Tasks.await(channelClient.getOutputStream(channel))
            val inputStream = context.contentResolver.openInputStream(apkUri)

            if (inputStream != null && outputStream != null) {
                // ضخ الملف من الهاتف إلى الساعة
                inputStream.copyTo(outputStream)
                
                inputStream.close()
                outputStream.close()
                onProgress("✅ تم الإرسال بنجاح! راجع شاشة ساعتك الآن لتأكيد التثبيت.")
            } else {
                onProgress("❌ حدث خطأ أثناء قراءة الملف من الهاتف.")
            }
            
            channelClient.close(channel)

        } catch (e: Exception) {
            onProgress("❌ فشل الإرسال: تأكد من اتصال الساعة.")
        }
    }
}
