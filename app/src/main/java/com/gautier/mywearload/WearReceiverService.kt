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
    
    override fun onChannelOpened(channel: ChannelClient.Channel) {
        super.onChannelOpened(channel)
        
        if (channel.path == "/wearload_apk_transfer") {
            // إعطاء الساعة منشط يمنعها من إغلاق الاتصال!
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WearLoad::TransferLock")
            wakeLock.acquire(10 * 60 * 1000L) // 10 دقائق كحد أقصى
            
            try {
                val channelClient = Wearable.getChannelClient(applicationContext)
                val inputStream = Tasks.await(channelClient.getInputStream(channel))
                
                val apkFile = File(getExternalFilesDir(null), "received_cadran.apk")
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
                
                // بدء التثبيت
                installApk(apkFile)
                
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
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

            // رسالة التثبيت الرسمية
            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            session.commit(pendingIntent.intentSender)
            session.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
