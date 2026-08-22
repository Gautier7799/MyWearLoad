package com.gautier.mywearload

import android.content.Context
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    var nsdManager: NsdManager? = null
    var discoveryListener: NsdManager.DiscoveryListener? = null
    
    // متغيرات لتحديث الواجهة آلياً
    var autoIp = mutableStateOf("")
    var autoPort = mutableStateOf("")
    var isSearching = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        nsdManager = getSystemService(Context.NSD_SERVICE) as NsdManager
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WearLoadAdbUI(this, autoIp.value, autoPort.value, isSearching.value)
                }
            }
        }
        
        // بدء البحث الآلي عن الساعة بمجرد فتح التطبيق
        startDiscovery()
    }

    // دالة البحث الآلي (الفكرة الجهنمية)
    fun startDiscovery() {
        isSearching.value = true
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                // وجدنا خدمة اقتران ADB!
                if (service.serviceType.contains("_adb-tls-pairing._tcp")) {
                    nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val hostAddress = serviceInfo.host.hostAddress
                            val port = serviceInfo.port
                            
                            if (hostAddress != null) {
                                CoroutineScope(Dispatchers.Main).launch {
                                    autoIp.value = hostAddress
                                    autoPort.value = port.toString()
                                    isSearching.value = false
                                    Toast.makeText(this@MainActivity, "تم العثور على الساعة آلياً! ⌚", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    })
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        
        try {
            nsdManager?.discoverServices("_adb-tls-pairing._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            isSearching.value = false
        }
    }

    override fun onDestroy() {
        if (discoveryListener != null) {
            try { nsdManager?.stopServiceDiscovery(discoveryListener) } catch (e: Exception) {}
        }
        super.onDestroy()
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WearLoadAdbUI(activity: MainActivity, autoIp: String, autoPort: String, isSearching: Boolean) {
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    
    // ربط الحقول بالمتغيرات الآلية
    var ipAddress by remember(autoIp) { mutableStateOf(autoIp) }
    var portNumber by remember(autoPort) { mutableStateOf(autoPort) }
    var pairingCode by remember { mutableStateOf("") }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            selectedFileName = activity.getFileName(uri)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "My WearLoad Pro", 
            style = MaterialTheme.typography.headlineMedium, 
            fontWeight = FontWeight.Bold, 
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (isSearching) {
            Text(text = "🔍 جاري البحث عن الساعة آلياً...", color = MaterialTheme.colorScheme.tertiary)
        } else {
            Text(text = "ADB Auto-Discovery 🚀", color = MaterialTheme.colorScheme.secondary)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = ipAddress,
            onValueChange = { ipAddress = it },
            label = { Text("عنوان IP") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = portNumber,
                onValueChange = { portNumber = it },
                label = { Text("Port") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            
            OutlinedTextField(
                value = pairingCode,
                onValueChange = { pairingCode = it },
                label = { Text("كود الاقتران (6 أرقام)") },
                modifier = Modifier.weight(1.5f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (selectedFileUri != null) {
            Text(text = "✅ تم اختيار: $selectedFileName", fontWeight = FontWeight.Medium)
        } else {
            Text(text = "لم يتم اختيار ملف APK")
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { filePickerLauncher.launch("application/vnd.android.package-archive") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("1. اختر ملف APK")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { 
                // كود تثبيت ADB الحقيقي سيوضع هنا
            },
            enabled = selectedFileUri != null && ipAddress.isNotEmpty() && portNumber.isNotEmpty() && pairingCode.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("2. اقتران وتثبيت")
        }
    }
}
