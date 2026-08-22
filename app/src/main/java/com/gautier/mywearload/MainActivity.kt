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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.URL

class MainActivity : ComponentActivity() {
    
    var nsdManager: NsdManager? = null
    var discoveryListener: NsdManager.DiscoveryListener? = null
    
    var autoIp = mutableStateOf("")
    var autoPairPort = mutableStateOf("")
    var autoConnectPort = mutableStateOf("")
    var isSearching = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nsdManager = getSystemService(Context.NSD_SERVICE) as NsdManager
        
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    WearLoadAdbUI(this, autoIp.value, autoPairPort.value, autoConnectPort.value, isSearching.value)
                }
            }
        }
    }

    fun startDiscovery() {
        if (discoveryListener != null) {
            try { nsdManager?.stopServiceDiscovery(discoveryListener) } catch (e: Exception) {}
        }
        isSearching.value = true
        autoIp.value = ""
        autoPairPort.value = ""
        autoConnectPort.value = ""

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains("_adb-tls-pairing._tcp")) {
                    nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            CoroutineScope(Dispatchers.Main).launch {
                                autoIp.value = serviceInfo.host.hostAddress ?: ""
                                autoPairPort.value = serviceInfo.port.toString()
                                checkDiscoveryDone()
                            }
                        }
                    })
                }
                if (service.serviceType.contains("_adb-tls-connect._tcp")) {
                    nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            CoroutineScope(Dispatchers.Main).launch {
                                autoConnectPort.value = serviceInfo.port.toString()
                                checkDiscoveryDone()
                            }
                        }
                    })
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { isSearching.value = false }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        
        try {
            nsdManager?.discoverServices("_adb-tls-pairing._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            nsdManager?.discoverServices("_adb-tls-connect._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            isSearching.value = false
        }
    }

    fun checkDiscoveryDone() {
        if (autoPairPort.value.isNotEmpty() && autoConnectPort.value.isNotEmpty()) {
            isSearching.value = false
            Toast.makeText(this, "تم التقاط جميع بيانات الساعة بنجاح! ⌚", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        if (discoveryListener != null) {
            try { nsdManager?.stopServiceDiscovery(discoveryListener) } catch (e: Exception) {}
        }
        super.onDestroy()
    }

    fun getRealApkPath(uri: Uri): String? {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val file = File(cacheDir, "temp_app.apk")
            FileOutputStream(file).use { output ->
                inputStream.copyTo(output)
            }
            return file.absolutePath
        } catch (e: Exception) {
            return null
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
        return result ?: "app.apk"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WearLoadAdbUI(activity: MainActivity, autoIp: String, autoPairPort: String, autoConnectPort: String, isSearching: Boolean) {
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    
    var ipAddress by remember(autoIp) { mutableStateOf(autoIp) }
    var pairPort by remember(autoPairPort) { mutableStateOf(autoPairPort) }
    var connectPort by remember(autoConnectPort) { mutableStateOf(autoConnectPort) }
    var pairingCode by remember { mutableStateOf("") }
    
    var processStatus by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            selectedFileName = activity.getFileName(uri)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("My WearLoad Pro", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = "Guide", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("دليل الاقتران السريع:", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("1️⃣ في ساعتك: افتح (تصحيح الأخطاء لاسلكياً).", fontSize = 14.sp)
                Text("2️⃣ اضغط على (إقران جهاز جديد) لظهور الكود.", fontSize = 14.sp)
                Text("3️⃣ المس شاشة الساعة كل 3 ثوانٍ لكي لا تنطفئ.", fontSize = 14.sp)
                Text("4️⃣ اضغط (بحث آلي) هنا، ثم اكتب كود الاقتران.", fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { activity.startDiscovery() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Refresh, contentDescription = "Search", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isSearching) "جاري البحث عن الساعة..." else "بحث آلي عن الساعة 🔍")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(value = ipAddress, onValueChange = { ipAddress = it }, label = { Text("عنوان IP") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = pairPort, onValueChange = { pairPort = it }, label = { Text("بورت الاقتران") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(value = connectPort, onValueChange = { connectPort = it }, label = { Text("بورت الاتصال") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = pairingCode, onValueChange = { pairingCode = it }, label = { Text("كود الاقتران (6 أرقام)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (selectedFileUri != null) {
            Text(text = "✅ تم اختيار: $selectedFileName", fontWeight = FontWeight.Medium)
        } else {
            Text(text = "لم يتم اختيار ملف APK", color = MaterialTheme.colorScheme.error)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { filePickerLauncher.launch("application/vnd.android.package-archive") }, modifier = Modifier.fillMaxWidth()) {
            Text("1. اختر ملف APK")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (processStatus.isNotEmpty()) {
            Text(text = processStatus, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = { 
                coroutineScope.launch {
                    val engine = RealAdbEngine(activity)
                    val realApkPath = activity.getRealApkPath(selectedFileUri!!)
                    
                    if (realApkPath != null) {
                        val paired = engine.pairAndConnect(ipAddress, pairPort, connectPort, pairingCode) { status ->
                            processStatus = status
                        }
                        if (paired) {
                            engine.installApk(ipAddress, connectPort, realApkPath) { status ->
                                processStatus = status
                            }
                        }
                    } else {
                        processStatus = "❌ خطأ في قراءة ملف الـ APK"
                    }
                }
            },
            enabled = selectedFileUri != null && ipAddress.isNotEmpty() && pairPort.isNotEmpty() && connectPort.isNotEmpty() && pairingCode.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("2. اقتران وتثبيت الحقيقي 🚀")
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ----------------------------------------------------
// المحرك الحقيقي (Real ADB Engine)
// ----------------------------------------------------
class RealAdbEngine(private val context: Context) {

    private suspend fun setupAdb(onProgress: (String) -> Unit): String? = withContext(Dispatchers.IO) {
        val adbFile = File(context.filesDir, "adb")
        if (!adbFile.exists() || adbFile.length() < 1000000) {
            onProgress("📥 جاري تحميل محرك ADB (3 ميجابايت)...")
            try {
                // الرابط الموثوق من Magisk
                URL("https://raw.githubusercontent.com/Magisk-Modules-Repo/adb-ndk/master/bin/adb.bin-arm64").openStream().use { input ->
                    FileOutputStream(adbFile).use { output ->
                        input.copyTo(output)
                    }
                }
                adbFile.setExecutable(true)
            } catch (e: Exception) {
                onProgress("❌ فشل تحميل محرك ADB: ${e.message}")
                return@withContext null
            }
        }
        return@withContext adbFile.absolutePath
    }

    private fun execCmd(cmd: String): String {
        return try {
            val process = Runtime.getRuntime().exec(cmd)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) { output.append(line).append("\n") }
            while (errorReader.readLine().also { line = it } != null) { output.append(line).append("\n") }
            process.waitFor()
            output.toString()
        } catch (e: Exception) {
            e.toString()
        }
    }

    suspend fun pairAndConnect(ip: String, pairPort: String, connectPort: String, code: String, onProgress: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val adbPath = setupAdb(onProgress) ?: return@withContext false
        
        onProgress("🔐 جاري إرسال كود الاقتران...")
        val pairResult = execCmd("$adbPath pair $ip:$pairPort $code")
        if (!pairResult.contains("Successfully paired")) {
            onProgress("❌ فشل الاقتران: تأكد من الكود وأن شاشة الساعة مضاءة.\n$pairResult")
            return@withContext false
        }

        onProgress("🔗 جاري الاتصال بالساعة...")
        val connectResult = execCmd("$adbPath connect $ip:$connectPort")
        if (!connectResult.contains("connected")) {
            onProgress("❌ فشل الاتصال بالساعة.\n$connectResult")
            return@withContext false
        }
        
        onProgress("✅ تم الاتصال بنجاح!")
        return@withContext true
    }

    suspend fun installApk(ip: String, connectPort: String, apkPath: String, onProgress: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val adbPath = File(context.filesDir, "adb").absolutePath
        
        onProgress("🚀 جاري رفع وتثبيت التطبيق على الساعة (قد يستغرق وقتاً)...")
        val installResult = execCmd("$adbPath -s $ip:$connectPort install -r $apkPath")
        
        if (installResult.contains("Success")) {
            onProgress("🎉 تمت عملية التثبيت بنجاح على ساعتك!")
            return@withContext true
        } else {
            onProgress("❌ فشل التثبيت:\n$installResult")
            return@withContext false
        }
    }
}
