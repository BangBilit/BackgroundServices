package com.example.backgroundservices

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

import kotlin.math.abs

class TrackerService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private val NOTIFICATION_CHANNEL_ID_SERVICE_RUNNING = "tracker_service_running"
    private val NOTIFICATION_CHANNEL_ID_JUMP_DETECTED = "tracker_jump_detected"
    private val NOTIFICATION_ID_SERVICE_RUNNING = 1
    private val NOTIFICATION_ID_JUMP_DETECTED = 2

    private var isLatihanMode = false
    private var stepCount = 0
    private var lastStepTime = 0L

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID_SERVICE_RUNNING,
                "Service Running Channel",
                NotificationManager.IMPORTANCE_LOW
            )

            val jumpChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID_JUMP_DETECTED,
                "Jump Detected Channel",
                NotificationManager.IMPORTANCE_HIGH
            )

            manager?.createNotificationChannel(serviceChannel)
            manager?.createNotificationChannel(jumpChannel)
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        createNotificationChannels()
        Log.d("TrackerService", "Service onCreate")
    }

    private fun startJumpDetection() {
        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TrackerService", "Service onStartCommand")

        val mode = intent?.getStringExtra(MainActivity.EXTRA_MODE)
        isLatihanMode = (mode == MainActivity.MODE_LATIHAN)
        stepCount = 0
        lastStepTime = 0L

        startForeground(NOTIFICATION_ID_SERVICE_RUNNING, buildServiceRunningNotification())

        startJumpDetection()
        return START_STICKY
    }

    private fun buildServiceRunningNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentTitle = if (isLatihanMode) "Latihan Berjalan" else "Pelacak Aktivitas Layar"
        val contentText = if (isLatihanMode) {
            "Latihan sedang berjalan (Langkah: $stepCount/3)"
        } else {
            "Aplikasi Tracker Services sedang berjalan"
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_SERVICE_RUNNING)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showJumpNotification() {
        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SERVICE_STOPPED_JUMP
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingMainActivityIntent = PendingIntent.getActivity(
            this, 0, mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val jumpNotification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_JUMP_DETECTED)
            .setContentTitle("Lompatan Terdeteksi")
            .setContentText("Service berhenti. Ketuk untuk membuka aplikasi.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingMainActivityIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID_JUMP_DETECTED, jumpNotification)
        } catch (e:SecurityException){
            Log.e(
                "TrackerService",
                "Gagal memunculkan notifikasi karena permission POST_NOTIFICATIONS tidak aktif: ${e.message}"
            )
        }
    }

    private fun showLatihanNotification() {
        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SERVICE_STOPPED_LATIHAN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingMainActivityIntent = PendingIntent.getActivity(
            this, 0, mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val latihanNotification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_JUMP_DETECTED)
            .setContentTitle("Latihan Selesai")
            .setContentText("Latihan selesai! Anda telah berjalan lebih dari 2 langkah. Ketuk untuk membuka aplikasi.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingMainActivityIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID_JUMP_DETECTED, latihanNotification)
        } catch (e: SecurityException) {
            Log.e(
                "TrackerService",
                "Gagal memunculkan notifikasi karena permission POST_NOTIFICATIONS tidak aktif: ${e.message}"
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        Log.d("TrackerService", "Service onDestroy")
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val y = event.values[1]
            val currentTime = System.currentTimeMillis()

            if (isLatihanMode) {
                val stepThreshold = 12
                if (abs(y) > stepThreshold && (currentTime - lastStepTime > 500)) {
                    lastStepTime = currentTime
                    stepCount++
                    Log.d("TrackerService", "Langkah terdeteksi: $stepCount")

                    val notificationManager = getSystemService(NotificationManager::class.java)
                    notificationManager?.notify(
                        NOTIFICATION_ID_SERVICE_RUNNING,
                        buildServiceRunningNotification()
                    )

                    if (stepCount > 2) {
                        Log.d("TrackerService", "Latihan selesai, langkah: $stepCount")
                        sensorManager.unregisterListener(this)

                        showLatihanNotification()
                        stopSelf()
                    }
                }
            } else {
                val jumpThreshold = 15
                if (abs(y) > jumpThreshold) {
                    Log.d("TrackerService", "Lompatan terdeteksi")
                    sensorManager.unregisterListener(this)

                    showJumpNotification()
                    stopSelf()

                }
            }
        }
    }
}