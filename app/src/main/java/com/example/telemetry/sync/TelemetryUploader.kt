package com.example.telemetry.sync

import com.example.telemetry.data.TelemetryRecord
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class TelemetryUploader(private val supabaseUrl: String, private val supabaseKey: String) {

    fun upload(records: List<TelemetryRecord>): Boolean {
        if (records.isEmpty()) return true
        
        try {
            val url = URL("$supabaseUrl/rest/v1/telemetry_logs")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("apikey", supabaseKey)
            conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
            conn.doOutput = true

            val jsonArray = JSONArray()
            for (record in records) {
                val obj = JSONObject().apply {
                    put("timestamp_ms", record.timestampMs)
                    put("event_type", record.eventType)
                    put("device_id", record.deviceId)
                    put("latitude", record.latitude)
                    put("longitude", record.longitude)
                    put("ip_address", record.ipAddress ?: JSONObject.NULL)
                    put("accuracy_meters", record.accuracyMeters ?: JSONObject.NULL)
                    put("payload_json", record.payloadJson)
                }
                jsonArray.put(obj)
            }

            conn.outputStream.use { os ->
                val input = jsonArray.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            return conn.responseCode in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
