package com.example.backgroundservices

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private val permissionToRequest = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
        arrayOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }else{
        arrayOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    companion object{
        const val ACTION_SERVICE_STOPPED_JUMP = "com.example.backgroundservices.ACTION_SERVICE_STOPPED_JUMP"
        const val ACTION_SERVICE_STOPPED_LATIHAN = "com.example.backgroundservices.ACTION_SERVICE_STOPPED_LATIHAN"
        const val EXTRA_MODE = "EXTRA_MODE"
        const val MODE_TRACKER = "MODE_TRACKER"
        const val MODE_LATIHAN = "MODE_LATIHAN"
        const val REQUEST_CODE_PERMISSIONS = 123
    }

    private fun hasAllPermission(): Boolean{
        return permissionToRequest.all {
            ContextCompat.checkSelfPermission(this,it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private lateinit var statusTextView: TextView
    private lateinit var btnStart: Button
    private lateinit var btnLatihan: Button
    private var pendingMode: String = MODE_TRACKER

    private fun startTrackerService(){
        val serviceIntent = Intent(this, TrackerService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        statusTextView.text = "Background service sedang berjalan"
    }

    private fun handleIntent(intent: Intent?){
        when (intent?.action){
            ACTION_SERVICE_STOPPED_JUMP -> { statusTextView.text = "Background service sudah berhenti karena anda melompat"
            }
            ACTION_SERVICE_STOPPED_LATIHAN -> {
                statusTextView.text = "Latihan selesai"
            }
        }
    }
    private fun startLatihanService() {
        val serviceIntent = Intent(this, TrackerService::class.java).apply {
            putExtra(EXTRA_MODE, MODE_LATIHAN)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        statusTextView.text = "Latihan sedang berjalan. Silakan berjalan lebih dari 2 langkah"
    }

    override fun onNewIntent(intent: Intent){
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            var allPermissionGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionGranted = false
                    break
                }
            }
            if (allPermissionGranted){
                val serviceIntent = Intent(this, TrackerService::class.java)
                ContextCompat.startForegroundService(this, serviceIntent)
                finish()
            }else {
                statusTextView.text = "Izin ditolak, service tidak dapat dijalankan"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        btnStart = findViewById(R.id.btnStart)
        btnLatihan = findViewById(R.id.btnLatihan)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        btnStart.setOnClickListener {
            if (hasAllPermission()){
                startTrackerService()
            } else {
                pendingMode = MODE_TRACKER
                ActivityCompat.requestPermissions(
                    this,
                    permissionToRequest,
                    REQUEST_CODE_PERMISSIONS
                )
            }
        }

        btnLatihan.setOnClickListener {
            if (hasAllPermission()) {
                startLatihanService()
            } else {
                pendingMode = MODE_LATIHAN
                ActivityCompat.requestPermissions(
                    this,
                    permissionToRequest,
                    REQUEST_CODE_PERMISSIONS
                )
            }
        }

        handleIntent(intent)
    }
}