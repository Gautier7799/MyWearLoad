package com.gautier.mywearload

import android.content.Intent
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

    // الدالة الجديدة: تسليم الملف لتطبيق WearLoad الأصلي
    fun forwardApkToOriginalApp(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                // توجيه الملف مباشرة لتطبيق WearLoad الأصلي إذا كان مثبتاً
                setPackage("com.camope3.wearload") 
            }
            startActivity(intent)
            Toast.makeText(this, "تم تحويل الملف للإرسال...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // إذا لم يجد التطبيق الأصلي، يفتح قائمة المشاركة العادية
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                startActivity(Intent.createChooser(fallbackIntent, "اختر تطبيق WearLoad:"))
            } catch (ex: Exception) {
                Toast.makeText(this, "تطبيق WearLoad الأصلي غير موجود في الهاتف!", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
fun WearLoadUI(activity: MainActivity) {
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }

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

        Button(onClick = { filePickerLauncher.launch("application/vnd.android.package-archive") }) {
            Text("اختر ملف APK من الهاتف")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { 
                if (selectedFileUri != null) {
                    activity.forwardApkToOriginalApp(selectedFileUri!!)
                }
            },
            enabled = selectedFileUri != null,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
        ) {
            Text("إرسال إلى Pixel Watch")
        }
    }
}
