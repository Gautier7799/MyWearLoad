package com.gautier.mywearload

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.PowerManager
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class WearReceiverService : WearableListenerService() {

    // 🔥 هذا الجزء هو الذي سيستقبل طلب التثبيت ويجبر شاشة الساعة على الاستيقاظ 🔥
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "com.gautier.mywearload.INSTALL_CONFIRM") {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
            
            // إذا كان النظام يطلب موافقة المستخدم
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                val confirmationIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirmationIntent != null) {
                    
                    // إيقاظ الشاشة رغماً عنها (Wake Up)
                    val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                    @Suppress("DEPRECATION")
                    val wakeLock = powerManager.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                        "WearLoad::InstallWakeUp"
                    )
                    wakeLock.acquire(3000) // إضاءة الشاشة لمدة 3 ثوانٍ

                    // فتح نافذة التثبيت
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

            // 🔥 توجيه رسالة التثبيت لنفس الخدمة (استخدمنا FLAG_MUTABLE ليتمكن النظام من إرفاق نافذة التثبيت) 🔥
            val intent = Intent(this, WearReceiverService::class.java).apply {
                action = "com.gautier.mywearload.INSTALL_CONFIRM"
            }
            val pendingIntent = PendingIntent.getService(
                this, 
                0, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            
            session.commit(pendingIntent.intentSender)
            session.close()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
