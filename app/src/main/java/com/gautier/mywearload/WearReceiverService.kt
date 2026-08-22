package com.gautier.mywearload

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class WearReceiverService : WearableListenerService() {
    
    override fun onChannelOpened(channel: ChannelClient.Channel) {
        super.onChannelOpened(channel)
        
        // التحقق من أن القناة هي الخاصة بتطبيقنا
        if (channel.path == "/wearload_apk_transfer") {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val channelClient = Wearable.getChannelClient(applicationContext)
                    val inputStream = Tasks.await(channelClient.getInputStream(channel))
                    
                    // حفظ الملف المستلم مؤقتاً في الساعة
                    val apkFile = File(cacheDir, "received_cadran.apk")
                    val outputStream = FileOutputStream(apkFile)
                    
                    inputStream.copyTo(outputStream)
                    
                    inputStream.close()
                    outputStream.close()
                    
                    // الملف جاهز، نطلب من نظام الساعة إظهار نافذة التثبيت
                    installApk(apkFile)
                    
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun installApk(apkFile: File) {
        val packageInstaller = packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        val out = session.openWrite("wearload_install", 0, apkFile.length())
        val input = FileInputStream(apkFile)
        input.copyTo(out)
        session.fsync(out)
        input.close()
        out.close()

        // إظهار نافذة التأكيد (هل تريد التثبيت؟) على شاشة الساعة
        val intent = Intent("com.gautier.mywearload.INSTALL_COMPLETE").setPackage(packageName)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            sessionId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        session.commit(pendingIntent.intentSender)
        session.close()
    }
}
