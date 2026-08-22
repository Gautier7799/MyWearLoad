package com.gautier.mywearload

import android.net.Uri
import android.os.Bundle
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WearLoadUI()
                }
            }
        }
    }
}

@Composable
fun WearLoadUI() {
    // متغير لحفظ مسار الملف الذي سيختاره المستخدم
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }

    // أداة لفتح مدير الملفات في الهاتف
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
        
        // عرض حالة الملف (هل تم الاختيار أم لا)
        if (selectedFileUri != null) {
            Text(
                text = "✅ تم اختيار الملف بنجاح!", 
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "مسار الملف:\n$selectedFileUri", 
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        } else {
            Text(text = "لم يتم اختيار أي ملف بعد.")
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // زر اختيار الملف
        Button(onClick = { 
            // فتح مدير الملفات للبحث عن ملفات (APK)
            filePickerLauncher.launch("application/vnd.android.package-archive") 
        }) {
            Text("اختر ملف APK من الهاتف")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // زر الإرسال للساعة (سيكون معطلاً حتى تقوم باختيار ملف)
        Button(
            onClick = { /* سنقوم ببرمجة الإرسال للساعة عبر البلوتوث لاحقاً */ },
            enabled = selectedFileUri != null
        ) {
            Text("تثبيت على ساعة Wear OS")
        }
    }
}
