package com.gautier.mywearload

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.PowerManager
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class WearReceiverService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        
        when (messageEvent.path) {
            "/request_installed_faces" -> {
                Thread {
                    try {
                        val pm = packageManager
                        val packages = pm.getInstalledPackages(0)
                        val facesList = mutableListOf<String>()
                        
                        for (pack in packages) {
                            val isSystemApp = (pack.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                            if (!isSystemApp && pack.packageName != packageName) {
                                val appName = pack.applicationInfo.loadLabel(pm).toString()
                                facesList.add("$appName|${pack.packageName}")
                            }
                        }
                        
                        val payload = if (facesList.isNotEmpty()) {
                            facesList.joinToString(";;;").toByteArray()
                        } else {
                            "EMPTY".toByteArray()
                        }

                        Wearable.getMessageClient(this@WearReceiverService).sendMessage(
                            messageEvent.sourceNodeId, 
                            "/installed_faces_list", 
                            payload
                        )
                    } catch (e: Exception) {
                        val errorMsg = "ERROR|${e.message}"
                        Wearable.getMessageClient(this@WearReceiverService).sendMessage(
                            messageEvent.sourceNodeId, 
                            "/installed_faces_list", 
                            errorMsg.toByteArray()
                        )
                    }
                }.start()
            }
            "/uninstall_face" -> {
                val packageToUninstall = String(messageEvent.data)
                
                val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                @Suppress("DEPRECATION")
                val wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE, "WearLoad::UninstallWakeUp")
                wakeLock.acquire(3000)

                val intent = Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:$packageToUninstall"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "com.gautier.mywearload.INSTALL_CONFIRM") {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
            
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                val confirmationIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirmationIntent != null) {
                    val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                    @Suppress("DEPRECATION")
                    val wakeLock = powerManager.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                        "WearLoad::InstallWakeUp"
                    )
                    wakeLock.acquire(3000)

                    confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(confirmationIntent)
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        super.onChannelOpened(channel)
        
        if (channel.path == "/wearload_apk_transfer") {
            Thread {
                val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WearLoad::BackgroundTransfer")
                wakeLock.acquire(10 * 60 * 1000L)
                
                try {
                    val channelClient = Wearable.getChannelClient(applicationContext)
                    val inputStream = Tasks.await(channelClient.getInputStream(channel))
                    
                    val apkFile = File(cacheDir, "received_cadran_pro.apk")
                    val outputStream = FileOutputStream(apkFile)
                    
                    val buffer = ByteArray(8 * 1024)
                    var bytes = inputStream.read(buffer)
                    while (bytes >= 0) {
                        outputStream.write(buffer, 0, bytes)
                        bytes = inputStream.read(buffer)
                    }
                    
                    inputStream.close()
                    outputStream.close()
                    channelClient.close(channel)
                    
                    installApk(apkFile)
                    
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    if (wakeLock.isHeld) wakeLock.release()
                }
            }.start()
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
            
            val buffer = ByteArray(8 * 1024)
            var bytes = input.read(buffer)
            while (bytes >= 0) {
                out.write(buffer, 0, bytes)
                bytes = input.read(buffer)
            }
            
            session.fsync(out)
            input.close()
            out.close()

            val intent = Intent(this, WearReceiverService::class.java).apply {
                action = "com.gautier.mywearload.INSTALL_CONFIRM"
            }
            val pendingIntent = PendingIntent.getService(
                this, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            
            session.commit(pendingIntent.intentSender)
            session.close()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
