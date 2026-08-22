package com.gautier.mywearload

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.io.InputStream
import java.io.OutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WearLoadUI(this)
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
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "app.apk"
    }

    fun sendApkToWatch(uri: Uri, fileName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = Wearable.getNodeClient(this@MainActivity).connectedNodes.await()
                val watchNode = nodes.firstOrNull()

                if (watchNode == null) {
                    runOnUiThread { Toast.makeText(this@MainActivity, "لم يتم العثور على ساعة متصلة بالبلوتوث!", Toast.LENGTH_LONG).show() }
                    return@launch
                }

                runOnUiThread { Toast.makeText(this@MainActivity, "جاري إعداد الساعة للاستقبال...", Toast.LENGTH_SHORT).show() }

                // أضفنا مؤقت زمني (10 ثواني) حتى لا يعلق التطبيق إذا رفضت الساعة
                withTimeout(10000) {
                    val messageClient = Wearable.getMessageClient(this@MainActivity)
                    messageClient.sendMessage(watchNode.id, "/file_name", fileName.toByteArray()).await()
                    
                    kotlinx.coroutines.delay(500)

                    val channelClient = Wearable.getChannelClient(this@MainActivity)
                    val channel = channelClient.openChannel(watchNode.id, "/file_channel").await()

                    val inputStream: InputStream? = contentResolver.openInputStream(uri)
                    val outputStream: OutputStream = channelClient.getOutputStream(channel).await()

                    if (inputStream != null) {
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }

                        outputStream.flush()
                        outputStream.close()
                        inputStream.close()
                        channelClient.close(channel).await()

                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "✅ تم إرسال $fileName للساعة بنجاح!", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    // سيظهر لنا الخطأ الفعلي الآن بدلاً من التعليق
                    Toast.makeText(this@MainActivity, "فشل الإرسال: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

@Composable
fun WearLoadUI(activity: MainActivity) {
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            selectedFileName = activity.getFileName(uri)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "My WearLoad AI", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (selectedFileUri != null) {
            Text(text = "✅ تم اختيار:", color = MaterialTheme.colorScheme.secondary)
            Text(text = selectedFileName, fontWeight = FontWeight.Medium)
        } else {
            Text(text = "لم يتم اختيار أي ملف بعد.")
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { filePickerLauncher.launch("application/vnd.android.package-archive") },
            enabled = !isSending
        ) {
            Text("اختر ملف APK من الهاتف")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { 
                if (selectedFileUri != null) {
                    isSending = true
                    // بمجرد انتهاء دالة الإرسال (نجاح أو فشل) سيعود الزر لطبيعته
                    activity.sendApkToWatch(selectedFileUri!!, selectedFileName)
                    CoroutineScope(Dispatchers.Main).launch {
                        kotlinx.coroutines.delay(10500) // ننتظر حتى ينتهي المؤقت كحد أقصى
                        isSending = false
                    }
                }
            },
            enabled = selectedFileUri != null && !isSending,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
        ) {
            Text(if (isSending) "جاري الإرسال..." else "إرسال إلى Pixel Watch")
        }
    }
}
