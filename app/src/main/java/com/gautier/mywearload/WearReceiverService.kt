package com.gautier.mywearload

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.io.File
import java.io.FileInputStream

class WearReceiverService : WearableListenerService() {
    
    override fun onChannelOpened(channel: ChannelClient.Channel) {
        super.onChannelOpened(channel)
        
        if (channel.path == "/wearload_apk_transfer") {
            try {
                val apkFile = File(getExternalFilesDir(null), "received_cadran.apk")
                val uri = Uri.fromFile(apkFile)
                
                val channelClient = Wearable.getChannelClient(applicationContext)
                
                // انتظار انتهاء نقل الملف بأمان
                channelClient.registerChannelCallback(channel, object : ChannelClient.ChannelCallback() {
                    override fun onInputClosed(c: ChannelClient.Channel, closeReason: Int, appSpecificErrorCode: Int) {
                        super.onInputClosed(c, closeReason, appSpecificErrorCode)
                        
                        if (closeReason == ChannelClient.ChannelCallback.CLOSE_REASON_NORMAL) {
                            installApk(apkFile) // تثبيت التطبيق بعد اكتمال النقل
                        }
                        
                        channelClient.unregisterChannelCallback(channel, this)
                    }
                })
                
                // استقبال الملف بالطريقة الرسمية من جوجل
                channelClient.receiveFile(channel, uri, false)
                
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
