package com.seanime.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class SeanimeService : Service() {

    private var process: Process? = null
    private val CHANNEL_ID = "seanime_channel"
    private val NOTIF_ID = 1
    private val ACTION_STOP_SERVICE = "STOP_SEANIME_SERVICE"
    private lateinit var notificationManager: NotificationManager
    private var lastStatus: String = "Server is running"
    private var permissionReceiver: BroadcastReceiver? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }

        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()

        promoteToForeground(lastStatus)
        registerPermissionReceiver()
        createFakeResolvConf()
        startBinary()

        return START_STICKY
    }

    private fun registerPermissionReceiver() {
        if (permissionReceiver != null) return
        permissionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                promoteToForeground(lastStatus)
            }
        }
        val filter = IntentFilter(ACTION_NOTIFICATION_PERMISSION_GRANTED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(permissionReceiver, filter, 0x4)
        } else {
            registerReceiver(permissionReceiver, filter)
        }
    }

    private fun createFakeResolvConf() {
        try {
            val resolvFile = File(filesDir, "resolv.conf")
            val content = "nameserver 8.8.8.8\nnameserver 1.1.1.1\n"
            FileOutputStream(resolvFile).use { it.write(content.toByteArray()) }
        } catch (e: Exception) {
            Log.e("SeanimeService", "Failed to create resolv.conf", e)
        }
    }

    private fun buildNotification(status: String): Notification {
        val stopIntent = Intent(this, SeanimeService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingStopIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val actionIcon = Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel)
        val stopAction = Notification.Action.Builder(actionIcon, "Exit", pendingStopIntent).build()

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Seanime")
            .setContentText(status)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(stopAction)
            .build()
    }

    private fun promoteToForeground(status: String) {
        lastStatus = status
        val notification = buildNotification(status)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun prepareBinary(): File? {
        try {
            val targetDir = filesDir
            val targetBinary = File(targetDir, "libseanime.so")
            
            val sourceBinary = File(applicationInfo.nativeLibraryDir, "libseanime.so")
            if (!sourceBinary.exists()) {
                promoteToForeground("Error: Binary missing in native lib dir")
                return null
            }
            
            if (!targetBinary.exists() || sourceBinary.length() != targetBinary.length()) {
                sourceBinary.copyTo(targetBinary, overwrite = true)
                try {
                    Runtime.getRuntime().exec(arrayOf("chmod", "755", targetBinary.absolutePath)).waitFor()
                } catch (e: Exception) {
                    targetBinary.setExecutable(true)
                }
            }
            
            val sourceCpp = File(applicationInfo.nativeLibraryDir, "libc++_shared.so")
            if (sourceCpp.exists()) {
                val targetCpp = File(targetDir, "libc++_shared.so")
                if (!targetCpp.exists() || sourceCpp.length() != targetCpp.length()) {
                    sourceCpp.copyTo(targetCpp, overwrite = true)
                }
            }
            
            return targetBinary
        } catch (e: Exception) {
            promoteToForeground("Error: Failed to prepare binary")
            return null
        }
    }

    private fun startBinary() {
        try {
            val binaryPath = prepareBinary()
            if (binaryPath == null) {
                promoteToForeground("Error: Binary preparation failed")
                return
            }

            if (!binaryPath.exists()) {
                promoteToForeground("Error: Binary missing after preparation")
                return
            }

            promoteToForeground("Server is running")

            val linker = "/system/bin/linker"
            val pb = ProcessBuilder(linker, binaryPath.absolutePath, "--datadir", filesDir.absolutePath)
                .directory(filesDir)
                .redirectErrorStream(true)

            pb.environment().apply {
                put("LD_LIBRARY_PATH", filesDir.absolutePath)
                put("GODEBUG", "netdns=cgo")
                put("RESOLV_CONF", File(filesDir, "resolv.conf").absolutePath)
                put("HOME", filesDir.absolutePath)
                put("TMPDIR", cacheDir.absolutePath)
            }

            process = pb.start()

            // Read output silently
            Thread {
                try {
                    process?.inputStream?.bufferedReader()?.use { reader ->
                        while (reader.readLine() != null) {
                            // Do nothing, just consume output
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }.start()

        } catch (e: Exception) {
            promoteToForeground("Error: ${e.message?.take(60)}")
        }
    }

    override fun onDestroy() {
        permissionReceiver?.let { unregisterReceiver(it) }
        permissionReceiver = null
        process?.destroy()
        process = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Seanime Server", NotificationManager.IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_NOTIFICATION_PERMISSION_GRANTED = "com.seanime.app.NOTIFICATION_PERMISSION_GRANTED"
    }
}