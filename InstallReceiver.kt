package com.gautier.mywearload

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        
        // إذا كان النظام يطلب موافقة المستخدم على التثبيت
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmIntent = intent.getParcelableExtra<Intent>(PackageInstaller.EXTRA_INTENT)
            if (confirmIntent != null) {
                // إجبار النافذة على الظهور فوق أي شيء آخر
                confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirmIntent)
            }
        } 
        // يمكنك هنا إضافة شروط أخرى للنجاح أو الفشل إذا أردت
    }
}
