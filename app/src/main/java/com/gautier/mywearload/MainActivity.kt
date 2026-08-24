package com.gautier.mywearload

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

enum class AppLang { AR, EN, FR }

class AppStrings(val lang: AppLang) {
    val isRtl get() = lang == AppLang.AR
    
    val title = "WearLoad"
    val subtitle = "Pro Edition V5.13"
    
    val localFile get() = when(lang) { AppLang.AR -> "ملف محلي"; AppLang.EN -> "Local File"; AppLang.FR -> "Fichier Local" }
    val store get() = when(lang) { AppLang.AR -> "المتجر"; AppLang.EN -> "Store"; AppLang.FR -> "Boutique" }
    val selectFile get() = when(lang) { AppLang.AR -> "اختر ملف Cadran"; AppLang.EN -> "Select Cadran File"; AppLang.FR -> "Sélectionner Cadran" }
    val fromStorage get() = when(lang) { AppLang.AR -> "من ذاكرة الهاتف"; AppLang.EN -> "From Phone Storage"; AppLang.FR -> "Depuis le stockage" }
    val readyToSend get() = when(lang) { AppLang.AR -> "جاهز للإرسال"; AppLang.EN -> "Ready to Send"; AppLang.FR -> "Prêt à envoyer" }
    val sendToWatch get() = when(lang) { AppLang.AR -> "إرسال إلى الساعة"; AppLang.EN -> "Send to Watch"; AppLang.FR -> "Envoyer à la montre" }
    val sending get() = when(lang) { AppLang.AR -> "جاري الإرسال..."; AppLang.EN -> "Sending..."; AppLang.FR -> "Envoi..." }
    val readyToUse get() = when(lang) { AppLang.AR -> "جاهز للاستخدام"; AppLang.EN -> "Ready to use"; AppLang.FR -> "Prêt à l'emploi" }
    val downloadSend get() = when(lang) { AppLang.AR -> "تحميل وإرسال"; AppLang.EN -> "Download & Send"; AppLang.FR -> "Télécharger & Envoyer" }
    val close get() = when(lang) { AppLang.AR -> "إغلاق"; AppLang.EN -> "Close"; AppLang.FR -> "Fermer" }
    val developer get() = when(lang) { AppLang.AR -> "المطور:"; AppLang.EN -> "Developer:"; AppLang.FR -> "Développeur :" }
    val languageBtn get() = when(lang) { AppLang.AR -> "اللغة: العربية"; AppLang.EN -> "Lang: English"; AppLang.FR -> "Lang: Français" }
    
    val sConnecting get() = when(lang) { AppLang.AR -> "⬇️ جاري الاتصال بالسيرفر..."; AppLang.EN -> "⬇️ Connecting to server..."; AppLang.FR -> "⬇️ Connexion au serveur..." }
    val sErrRedirect get() = when(lang) { AppLang.AR -> "❌ خطأ: تحويلات السيرفر كثيرة"; AppLang.EN -> "❌ Error: Too many redirects"; AppLang.FR -> "❌ Erreur : Trop de redirections" }
    val sErrServer get() = when(lang) { AppLang.AR -> "❌ خطأ في السيرفر: Code"; AppLang.EN -> "❌ Server Error: Code"; AppLang.FR -> "❌ Erreur Serveur : Code" }
    val sDownloading get() = when(lang) { AppLang.AR -> "⬇️ جاري التحميل من السحابة..."; AppLang.EN -> "⬇️ Downloading from cloud..."; AppLang.FR -> "⬇️ Téléchargement depuis le cloud..." }
    val sErrConn get() = when(lang) { AppLang.AR -> "❌ خطأ بالاتصال:"; AppLang.EN -> "❌ Connection error:"; AppLang.FR -> "❌ Erreur de connexion :" }
    val sSearchWatch get() = when(lang) { AppLang.AR -> "🔍 البحث عن الساعة..."; AppLang.EN -> "🔍 Searching for watch..."; AppLang.FR -> "🔍 Recherche de la montre..." }
    val sErrBlue get() = when(lang) { AppLang.AR -> "❌ تأكد من تشغيل البلوتوث وربط الساعة!"; AppLang.EN -> "❌ Please enable Bluetooth & pair watch!"; AppLang.FR -> "❌ Activez le Bluetooth et associez la montre !" }
    val sPreparing get() = when(lang) { AppLang.AR -> "🔄 تحضير الملف للنقل..."; AppLang.EN -> "🔄 Preparing file for transfer..."; AppLang.FR -> "🔄 Préparation du fichier..." }
    val sErrRead get() = when(lang) { AppLang.AR -> "❌ خطأ: لم أتمكن من قراءة الملف"; AppLang.EN -> "❌ Error: Could not read file"; AppLang.FR -> "❌ Erreur : Impossible de lire le fichier" }
    val sTransferring get() = when(lang) { AppLang.AR -> "📡 جاري نقل الملف بالكامل..."; AppLang.EN -> "📡 Transferring full file..."; AppLang.FR -> "📡 Transfert du fichier complet..." }
    val sSuccessTrans get() = when(lang) { AppLang.AR -> "✅ تم النقل! جارٍ التثبيت في الساعة..."; AppLang.EN -> "✅ Transferred! Installing on watch..."; AppLang.FR -> "✅ Transféré ! Installation sur la montre..." }
    val sErrLost get() = when(lang) { AppLang.AR -> "❌ انقطع الاتصال:"; AppLang.EN -> "❌ Connection lost:"; AppLang.FR -> "❌ Connexion perdue :" }
    
    val wReady get() = when(lang) { AppLang.AR -> "جاهز للاستقبال"; AppLang.EN -> "Ready to Receive"; AppLang.FR -> "Prêt à recevoir" }
    val wReceiving get() = when(lang) { AppLang.AR -> "جاري استلام الملف..."; AppLang.EN -> "Receiving file..."; AppLang.FR -> "Réception du fichier..." }
    val wChecking get() = when(lang) { AppLang.AR -> "⏳ جاري فحص الملف..."; AppLang.EN -> "⏳ Checking file..."; AppLang.FR -> "⏳ Vérification du fichier..." }
    val wPreparingInstall get() = when(lang) { AppLang.AR -> "⏳ جاري تحضير شاشة التثبيت..."; AppLang.EN -> "⏳ Preparing installation screen..."; AppLang.FR -> "⏳ Préparation de l'installation..." }
    val wErrCorrupt get() = when(lang) { AppLang.AR -> "❌ الملف تالف، أعد المحاولة"; AppLang.EN -> "❌ File corrupted, try again"; AppLang.FR -> "❌ Fichier corrompu, réessayez" }
    val wSuccess get() = when(lang) { AppLang.AR -> "✅ تم التثبيت بنجاح!"; AppLang.EN -> "✅ Installed successfully!"; AppLang.FR -> "✅ Installé avec succès !" }
    val wPending get() = when(lang) { AppLang.AR -> "⏳ أكمل التثبيت من الشاشة..."; AppLang.EN -> "⏳ Complete installation on screen..."; AppLang.FR -> "⏳ Terminez l'installation sur l'écran..." }
    val wFail get() = when(lang) { AppLang.AR -> "❌ فشل التثبيت:"; AppLang.EN -> "❌ Install failed:"; AppLang.FR -> "❌ Échec de l'installation :" }
    val wStartErr get() = when(lang) { AppLang.AR -> "❌ خطأ في بدء الاستلام"; AppLang.EN -> "❌ Error starting reception"; AppLang.FR -> "❌ Erreur de réception" }
    val wSendingTxt get() = when(lang) { AppLang.AR -> "جاري النقل..."; AppLang.EN -> "Transferring..."; AppLang.FR -> "Transfert en cours..." }
}

class MainActivity : ComponentActivity() {

    lateinit var prefs: SharedPreferences
    var currentLang by mutableStateOf(AppLang.AR)
    val currentStrings get() = AppStrings(currentLang)

    var watchReceiveStatus by mutableStateOf("")
    var watchIsReceiving by mutableStateOf(false)
    var watchIsSuccess by mutableStateOf(false)

    private val channelCallback = object : ChannelClient.ChannelCallback() {
        override fun onChannelOpened(channel: ChannelClient.Channel) {
            if (channel.path == "/wearload_apk_transfer") {
                watchIsReceiving = true
                watchIsSuccess = false
                watchReceiveStatus = currentStrings.wReceiving

                CoroutineScope(Dispatchers.IO).launch {
                    val apkFile = File(cacheDir, "received_app.apk")
                    if (apkFile.exists()) apkFile.delete()

                    try {
                        val channelClient = Wearable.getChannelClient(this@MainActivity)
                        Tasks.await(channelClient.receiveFile(channel, Uri.fromFile(apkFile), false))
                    } catch (e: Exception) {
                        watchIsReceiving = false
                        watchIsSuccess = false
                        watchReceiveStatus = currentStrings.wStartErr
                    }
                }
            }
        }
        
        override fun onChannelClosed(channel: ChannelClient.Channel, closeReason: Int, appSpecificErrorCode: Int) {
            if (channel.path == "/wearload_apk_transfer") {
                CoroutineScope(Dispatchers.IO).launch {
                    watchReceiveStatus = currentStrings.wChecking
                    delay(1500) 
                    
                    val apkFile = File(cacheDir, "received_app.apk")
                    val packageInfo = packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                    
                    if (packageInfo != null) {
                        watchIsReceiving = false
                        watchReceiveStatus = currentStrings.wPreparingInstall
                        installApk(apkFile)
                    } else {
                        watchIsReceiving = false
                        watchIsSuccess = false
                        watchReceiveStatus = currentStrings.wErrCorrupt
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val savedLangStr = prefs.getString("APP_LANG", "AR") ?: "AR"
        currentLang = try { AppLang.valueOf(savedLangStr) } catch (e: Exception) { AppLang.AR }
        watchReceiveStatus = currentStrings.wReady

        val isWatch = packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
        if (isWatch) {
            Wearable.getChannelClient(this).registerChannelCallback(channelCallback)
        }

        setContent {
            val bgColor = Color(0xFF191C1B) 
            val googleBlue = Color(0xFF4285F4)
            val googleYellow = Color(0xFFFBBC05)
            val googleGreen = Color(0xFF34A853)
            val googleRed = Color(0xFFEA4335)
            val surfaceColor = Color(0xFF2C322F) 
            val pillActive = Color(0xFF89D6B3) 
            val pillActiveText = Color(0xFF003824)

            if (isWatch) {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                        WatchModernUI(this, googleBlue, googleGreen, googleYellow, googleRed)
                    }
                }
            } else {
                MaterialTheme { 
                    Surface(modifier = Modifier.fillMaxSize(), color = bgColor) { 
                        WearModernUI(this, bgColor, surfaceColor, pillActive, pillActiveText, googleYellow, googleGreen) 
                    } 
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val isWatch = packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
        if (isWatch) {
            Wearable.getChannelClient(this).unregisterChannelCallback(channelCallback)
        }
    }

    private fun installApk(apkFile: File) {
        try {
            val packageInstaller = packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            val out = session.openWrite("wearload_install", 0, apkFile.length())
            val input = FileInputStream(apkFile)
            val buffer = ByteArray(65536)
            var c: Int
            while (input.read(buffer).also { c = it } >= 0) {
                out.write(buffer, 0, c)
            }
            session.fsync(out)
            input.close()
            out.close()

            val action = "com.gautier.mywearload.INSTALL_COMPLETE"
            
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    
                    when (status) {
                        PackageInstaller.STATUS_SUCCESS -> {
                            watchReceiveStatus = currentStrings.wSuccess
                            watchIsSuccess = true
                        }
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            val userAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(Intent.EXTRA_INTENT)
                            }
                            
                            if (userAction != null) {
                                userAction.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(userAction)
                                watchReceiveStatus = currentStrings.wPending
                            }
                        }
                        else -> {
                            watchReceiveStatus = "${currentStrings.wFail} $message"
                            watchIsSuccess = false
                        }
                    }
                    try { context.unregisterReceiver(this) } catch (e: Exception) {}
                }
            }

            val intentFilter = IntentFilter(action)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(receiver, intentFilter)
            }

            val intent = Intent(action).setPackage(packageName)
            val pendingIntent = PendingIntent.getBroadcast(
                this, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pendingIntent.intentSender)

        } catch (e: Exception) {
            watchReceiveStatus = "${currentStrings.wFail} ${e.message}"
            watchIsSuccess = false
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

// أداة لرسم شعار Wear OS الملون برمجياً
@Composable
fun WearOSLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = w * 0.25f

        // الخط الأزرق المائل
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(w * 0.2f, h * 0.1f),
            end = Offset(w * 0.45f, h * 0.9f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // الخط الأصفر المائل
        drawLine(
            color = Color(0xFFFBBC05),
            start = Offset(w * 0.55f, h * 0.1f),
            end = Offset(w * 0.8f, h * 0.9f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // النقطة الحمراء
        drawCircle(
            color = Color(0xFFEA4335),
            radius = strokeWidth * 0.6f,
            center = Offset(w * 0.95f, h * 0.25f)
        )

        // النقطة الخضراء
        drawCircle(
            color = Color(0xFF34A853),
            radius = strokeWidth * 0.6f,
            center = Offset(w * 0.85f, h * 0.6f)
        )
    }
}

@Composable
fun WatchModernUI(activity: MainActivity, blue: Color, green: Color, yellow: Color, red: Color) {
    val strings = activity.currentStrings
    
    CompositionLocalProvider(LocalLayoutDirection provides if (strings.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(strings.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = blue)
                Spacer(modifier = Modifier.height(12.dp))

                if (activity.watchIsReceiving) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp), color = blue, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(strings.wSendingTxt, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                } else if (activity.watchIsSuccess) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Success", tint = green, modifier = Modifier.size(40.dp))
                } else if (activity.watchReceiveStatus.contains("❌")) {
                    Icon(Icons.Filled.Warning, contentDescription = "Error", tint = red, modifier = Modifier.size(40.dp))
                } else if (activity.watchReceiveStatus.contains("⏳")) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp), color = yellow, strokeWidth = 3.dp)
                } else {
                    Box(modifier = Modifier.size(40.dp).background(Color.DarkGray, CircleShape).clip(CircleShape), contentAlignment = Alignment.Center) {
                        WearOSLogo(modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(activity.watchReceiveStatus, fontSize = 11.sp, color = Color.LightGray, textAlign = TextAlign.Center)
            }
        }
    }
}

data class StoreFace(val id: String, val name: String, val author: String, val imageUrl: String, val downloadUrl: String)

@Composable
fun WearModernUI(
    activity: MainActivity, 
    bgColor: Color, 
    surfaceColor: Color, 
    pillActive: Color,
    pillActiveText: Color,
    warningColor: Color,
    successColor: Color
) {
    val strings = activity.currentStrings
    
    var currentTab by remember { mutableStateOf("LOCAL") }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var selectedFileSize by remember { mutableLongStateOf(1L) }
    var processStatus by remember { mutableStateOf("") }
    var transferProgress by remember { mutableFloatStateOf(0f) }
    var isSending by remember { mutableStateOf(false) }
    var selectedFacePreview by remember { mutableStateOf<StoreFace?>(null) }
    
    val coroutineScope = rememberCoroutineScope()
    
    val storeFaces = remember { mutableStateListOf(
        StoreFace("1", "Casio Retro", "Classic Watch", "https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=300&q=80", "https://f-droid.org/F-Droid.apk"),
        StoreFace("2", "Pixel Minimal", "Modern UI", "https://images.unsplash.com/photo-1579586337278-3befd40fd17a?auto=format&fit=crop&w=300&q=80", "https://f-droid.org/F-Droid.apk")
    )}

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                activity.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) { }
            
            selectedFileUri = uri
            val info = activity.getFileInfo(uri)
            selectedFileName = info.first
            selectedFileSize = info.second
            processStatus = ""
            transferProgress = 0f
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides if (strings.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)) {
            
            // 1. Title Alone at the top (Center Aligned)
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp, top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    
                    // استخدام الشعار البرمجي الملون بدلاً من الصور الخارجية
                    WearOSLogo(modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        text = strings.title, 
                        fontSize = 32.sp, 
                        fontWeight = FontWeight.ExtraBold, 
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(strings.subtitle, fontSize = 14.sp, color = warningColor, fontWeight = FontWeight.Bold)
            }

            // 2. Control Grid (Language and Tabs)
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Language Button (Pill Shape)
                Button(
                    onClick = {
                        val nextLang = when (activity.currentLang) {
                            AppLang.AR -> AppLang.EN
                            AppLang.EN -> AppLang.FR
                            AppLang.FR -> AppLang.AR
                        }
                        activity.currentLang = nextLang
                        activity.prefs.edit().putString("APP_LANG", nextLang.name).apply()
                        if (!activity.watchIsReceiving && !activity.watchIsSuccess) {
                            activity.watchReceiveStatus = activity.currentStrings.wReady
                        }
                        processStatus = ""
                    },
                    modifier = Modifier.weight(1f).height(64.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = surfaceColor)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Filled.Settings, contentDescription = "Language", tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.languageBtn, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            // Tabs (Pill Shapes)
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { currentTab = "LOCAL" }, modifier = Modifier.weight(1f).height(64.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = if (currentTab == "LOCAL") pillActive else surfaceColor)
                ) { 
                    Text(strings.localFile, color = if (currentTab == "LOCAL") pillActiveText else Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) 
                }
                
                Button(
                    onClick = { currentTab = "STORE" }, modifier = Modifier.weight(1f).height(64.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = if (currentTab == "STORE") pillActive else surfaceColor)
                ) { 
                    Text(strings.store, color = if (currentTab == "STORE") pillActiveText else Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) 
                }
            }

            // 3. Content Area
            if (currentTab == "LOCAL") {
                // Select File Button (Pill Shape)
                Button(
                    onClick = { filePickerLauncher.launch("application/vnd.android.package-archive") }, 
                    modifier = Modifier.fillMaxWidth().height(80.dp), 
                    shape = CircleShape, 
                    colors = ButtonDefaults.buttonColors(containerColor = surfaceColor)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp).background(Color(0xFF3F4944), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Build, contentDescription = "File", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
                            Text(if (selectedFileUri == null) strings.selectFile else selectedFileName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (selectedFileUri == null) strings.fromStorage else strings.readyToSend, color = Color.LightGray, fontSize = 14.sp)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Send Button (Pill Shape - Accent)
                Button(
                    onClick = { 
                        if (selectedFileUri != null && !isSending) {
                            coroutineScope.launch {
                                isSending = true; transferProgress = 0f
                                sendApkToWatchModern(activity, selectedFileUri!!, strings, { transferProgress = it }, { processStatus = it })
                                isSending = false
                            }
                        }
                    }, 
                    enabled = selectedFileUri != null && !isSending, 
                    modifier = Modifier.fillMaxWidth().height(80.dp), 
                    shape = CircleShape, 
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedFileUri != null) pillActive else surfaceColor,
                        disabledContainerColor = surfaceColor
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically, 
                        horizontalArrangement = Arrangement.Start,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp).background(if (selectedFileUri != null) pillActiveText.copy(alpha=0.2f) else Color(0xFF3F4944), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = "Send", tint = if (selectedFileUri != null) pillActiveText else Color.Gray, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = if (isSending) strings.sending else strings.sendToWatch, 
                            color = if (selectedFileUri != null) pillActiveText else Color.Gray, 
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(storeFaces) { face ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .clip(CircleShape)
                                    .background(surfaceColor)
                                    .clickable { selectedFacePreview = face }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = face.imageUrl,
                                    contentDescription = "Watch Face",
                                    modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.DarkGray)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(face.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(face.author, color = Color.Gray, fontSize = 12.sp)
                                }
                                IconButton(
                                    onClick = {
                                        if (!isSending) {
                                            coroutineScope.launch {
                                                isSending = true; transferProgress = 0f
                                                val downloadedUri = downloadApkFromUrl(activity, face.downloadUrl, strings, { transferProgress = it }, { processStatus = it })
                                                if (downloadedUri != null) {
                                                    transferProgress = 0f
                                                    sendApkToWatchModern(activity, downloadedUri, strings, { transferProgress = it }, { processStatus = it })
                                                }
                                                isSending = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.background(Color(0xFF3F4944), CircleShape).size(48.dp)
                                ) { Icon(Icons.Filled.ShoppingCart, contentDescription = "Download", tint = Color.White, modifier = Modifier.size(20.dp)) }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = if (currentTab == "LOCAL") Modifier.weight(1f) else Modifier.height(16.dp))

            // 4. Status Footer (Pill Shape)
            Column(
                modifier = Modifier.fillMaxWidth().background(surfaceColor, CircleShape).padding(horizontal = 24.dp, vertical = 16.dp).heightIn(min = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                if (isSending || transferProgress > 0f) {
                    LinearProgressIndicator(progress = transferProgress, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = pillActive, trackColor = Color(0xFF3F4944))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${(transferProgress * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold)
                    if (processStatus.isNotEmpty()) Text(processStatus, color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                } else if (processStatus.isNotEmpty()) {
                    Text(processStatus, color = if (processStatus.contains("❌")) Color(0xFFEA4335) else successColor, fontWeight = FontWeight.Medium, fontSize = 14.sp, textAlign = TextAlign.Center)
                } else {
                    Text(strings.readyToUse, color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (selectedFacePreview != null) {
            AlertDialog(
                onDismissRequest = { selectedFacePreview = null },
                containerColor = surfaceColor,
                title = { Text(selectedFacePreview!!.name, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        AsyncImage(
                            model = selectedFacePreview!!.imageUrl,
                            contentDescription = "Preview",
                            modifier = Modifier.size(200.dp).clip(CircleShape).background(Color.DarkGray)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("${strings.developer} ${selectedFacePreview!!.author}", color = Color.LightGray, fontSize = 16.sp)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val face = selectedFacePreview!!
                            selectedFacePreview = null 
                            if (!isSending) {
                                coroutineScope.launch {
                                    isSending = true; transferProgress = 0f
                                    val downloadedUri = downloadApkFromUrl(activity, face.downloadUrl, strings, { transferProgress = it }, { processStatus = it })
                                    if (downloadedUri != null) {
                                        transferProgress = 0f
                                        sendApkToWatchModern(activity, downloadedUri, strings, { transferProgress = it }, { processStatus = it })
                                    }
                                    isSending = false
                                }
                            }
                        }, 
                        colors = ButtonDefaults.buttonColors(containerColor = pillActive)
                    ) { Text(strings.downloadSend, color = pillActiveText, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { selectedFacePreview = null }) { Text(strings.close, color = Color.LightGray) }
                }
            )
        }
    }
}

suspend fun downloadApkFromUrl(context: Context, urlString: String, strings: AppStrings, onProgressUpdate: (Float) -> Unit, onStatusUpdate: (String) -> Unit): Uri? {
    return withContext(Dispatchers.IO) {
        try {
            onStatusUpdate(strings.sConnecting)
            var url = URL(urlString)
            var connection = url.openConnection() as HttpURLConnection
            var redirectCount = 0
            var responseCode: Int

            while (true) {
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = false 
                connection.connect()

                responseCode = connection.responseCode
                if (responseCode in 300..399) {
                    val redirectUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    url = URL(redirectUrl)
                    connection = url.openConnection() as HttpURLConnection
                    redirectCount++
                    if (redirectCount > 5) {
                        onStatusUpdate(strings.sErrRedirect)
                        return@withContext null
                    }
                } else break
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                onStatusUpdate("${strings.sErrServer} $responseCode")
                return@withContext null
            }

            onStatusUpdate(strings.sDownloading)
            val fileLength = connection.contentLength
            val input = BufferedInputStream(connection.inputStream)
            val tempFile = File(context.cacheDir, "temp_face.apk")
            if (tempFile.exists()) tempFile.delete()
            
            val output = FileOutputStream(tempFile)
            val data = ByteArray(16 * 1024)
            var total = 0L
            var count: Int
            
            while (input.read(data).also { count = it } != -1) {
                total += count.toLong()
                if (fileLength > 0) onProgressUpdate(total.toFloat() / fileLength.toFloat())
                output.write(data, 0, count)
            }
            output.flush(); output.close(); input.close()
            Uri.fromFile(tempFile)
        } catch (e: Exception) {
            onStatusUpdate("${strings.sErrConn} ${e.message}")
            null
        }
    }
}

suspend fun sendApkToWatchModern(context: Context, apkUri: Uri, strings: AppStrings, onProgressUpdate: (Float) -> Unit, onStatusUpdate: (String) -> Unit) {
    withContext(Dispatchers.IO) {
        try {
            onStatusUpdate(strings.sSearchWatch)
            val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
            val watchNode = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
            
            if (watchNode == null) { 
                onStatusUpdate(strings.sErrBlue) 
                return@withContext 
            }
            
            onStatusUpdate(strings.sPreparing)
            val safeFile = File(context.cacheDir, "ready_to_send.apk")
            if (safeFile.exists()) safeFile.delete()
            
            val inputStream = if (apkUri.scheme == "file") FileInputStream(File(apkUri.path!!)) else context.contentResolver.openInputStream(apkUri)
            if (inputStream != null) {
                FileOutputStream(safeFile).use { output ->
                    inputStream.copyTo(output)
                }
                inputStream.close()
            } else {
                onStatusUpdate(strings.sErrRead)
                return@withContext
            }

            onStatusUpdate(strings.sTransferring)
            val channelClient = Wearable.getChannelClient(context)
            val channel = Tasks.await(channelClient.openChannel(watchNode.id, "/wearload_apk_transfer"))
            
            val progressJob = launch {
                var simProgress = 0f
                while(simProgress < 0.95f) {
                    delay(500)
                    simProgress += 0.05f
                    onProgressUpdate(simProgress)
                }
            }
            
            Tasks.await(channelClient.sendFile(channel, Uri.fromFile(safeFile)))
            channelClient.close(channel)
            
            progressJob.cancel()
            onProgressUpdate(1f)
            onStatusUpdate(strings.sSuccessTrans)
            
            if(safeFile.exists()) safeFile.delete()

        } catch (e: Exception) { 
            onStatusUpdate("${strings.sErrLost} ${e.message}") 
        }
    }
}
