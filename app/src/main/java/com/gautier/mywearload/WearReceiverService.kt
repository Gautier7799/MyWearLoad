package com.gautier.mywearload

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
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
        
        // التحقق من أن القناة صحيحة
        if (channel.path == "/wearload_apk_transfer") {
            try {
                val channelClient = Wearable.getChannelClient(applicationContext)
                
                // ⚠️ هنا قمنا بإلغاء الكوروتين لكي نُجبر الساعة على انتظار الملف كاملاً
                val inputStream = Tasks.await(channelClient.getInputStream(channel))
                
                val apkFile = File(cacheDir, "received_cadran.apk")
                val outputStream = FileOutputStream(apkFile)
                
                // استقبال الملف وتجميعه
                val buffer = ByteArray(8 * 1024)
                var bytes = inputStream.read(buffer)
                while (bytes >= 0) {
                    outputStream.write(buffer, 0, bytes)
                    bytes = inputStream.read(buffer)
                }
                
                inputStream.close()
                outputStream.close()
                
                // إظهار نافذة التثبيت
                installApk(apkFile)
                
            } catch (e: Exception) {
                e.printStackTrace()
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

            val intent = Intent("com.gautier.mywearload.INSTALL_COMPLETE").setPackage(packageName)
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                sessionId,
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
