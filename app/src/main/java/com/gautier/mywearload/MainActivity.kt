package com.gautier.mywearload

import android.net.Uri
import android.os.Bundle
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WearLoadUI(this) // نمرر الـ Context هنا
                }
            }
        }
    }

    // دالة لإرسال الملف إلى الساعة عبر Data Layer API
    fun sendApkToWatch(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. قراءة الملف من الهاتف كـ Bytes (بيانات خام)
                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()

                if (bytes != null) {
                    // 2. تجهيز البيانات للإرسال عبر البلوتوث للساعة
                    val dataMapRequest = PutDataMapRequest.create("/apk_transfer")
                    dataMapRequest.dataMap.putByteArray("apk_data", bytes)
                    dataMapRequest.dataMap.putLong("timestamp", System.currentTimeMillis()) // لإجبار الساعة على استقباله حتى لو تكرر

                    val putDataRequest = dataMapRequest.asPutDataRequest()
                    putDataRequest.setUrgent() // إرسال سريع

                    // 3. أمر الإرسال الفعلي
                    Wearable.getDataClient(this@MainActivity).putDataItem(putDataRequest).await()
                    
                    // 4. إظهار رسالة نجاح
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "تم الإرسال للساعة بنجاح!", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "حدث خطأ: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

@Composable
fun WearLoadUI(activity: MainActivity) {
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var isSending by remember { mutableStateOf(false) } // متغير لمعرفة هل جاري الإرسال

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedFileUri = uri
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "My WearLoad", style = MaterialTheme.typography.headlineLarge)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (selectedFileUri != null) {
            Text(
                text = "✅ تم اختيار الملف", 
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
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
                    activity.sendApkToWatch(selectedFileUri!!)
                    isSending = false
                }
            },
            enabled = selectedFileUri != null && !isSending
        ) {
            Text(if (isSending) "جاري الإرسال..." else "تثبيت على ساعة Wear OS")
        }
    }
}
