package com.papa.grabador_llamadas

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File

class MainActivity : FlutterActivity() {

    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "grabador/native")
            .setMethodCallHandler { call, result -> handle(call, result) }
    }

    private fun handle(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "startMonitor" -> {
                prefs.edit().putBoolean("monitorEnabled", true).apply()
                CallRecorderService.start(this)
                result.success(true)
            }
            "stopMonitor" -> {
                prefs.edit().putBoolean("monitorEnabled", false).apply()
                CallRecorderService.stop(this)
                result.success(true)
            }
            "getStatus" -> result.success(fullStatus())
            "manualStart" -> {
                val i = Intent(this, CallRecorderService::class.java)
                    .setAction(CallRecorderService.ACTION_RECORD_START)
                    .putExtra(CallRecorderService.EXTRA_TYPE, CallRecorderService.TYPE_TEST)
                ContextCompat.startForegroundService(this, i)
                result.success(true)
            }
            "manualStop" -> {
                CallRecorderService.instance?.stopRecording()
                result.success(true)
            }
            "getSettings" -> result.success(
                mapOf(
                    "recordGsm" to prefs.getBoolean("recordGsm", true),
                    "recordWs" to prefs.getBoolean("recordWs", true),
                    "askBeforeRecord" to prefs.getBoolean("askBeforeRecord", true),
                    "autoSpeaker" to prefs.getBoolean("autoSpeaker", true),
                    "audioSourceGsm" to (prefs.getString("audioSourceGsm", "MIC") ?: "MIC"),
                    "audioSourceWs" to (prefs.getString("audioSourceWs", "MIC") ?: "MIC")
                )
            )
            "setSetting" -> {
                val args = call.arguments as? Map<*, *> ?: run {
                    result.error("bad_args", null, null)
                    return
                }
                val key = args["key"] as? String
                val value = args["value"]
                if (key != null) {
                    val editor = prefs.edit()
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        else -> editor.putString(key, value?.toString())
                    }
                    editor.apply()
                }
                result.success(true)
            }
            "getRecordings" -> {
                val dir = File(getExternalMediaDirs().firstOrNull() ?: filesDir, "Grabaciones")
                val list = dir.listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".m4a") }
                    ?.sortedByDescending { it.lastModified() }
                    ?.map {
                        mapOf(
                            "name" to it.name,
                            "path" to it.absolutePath,
                            "size" to it.length(),
                            "modified" to it.lastModified()
                        )
                    } ?: emptyList<Map<String, Any?>>()
                result.success(list)
            }
            "deleteRecording" -> {
                val path = call.argument<String>("path")
                val ok = path != null && File(path).delete()
                result.success(ok)
            }
            "hasNotificationAccess" -> result.success(hasNotificationAccess())
            "openNotificationAccess" -> {
                try {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (_: Exception) {}
                result.success(true)
            }
            "isIgnoringBattery" -> result.success(
                getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
            )
            "requestIgnoreBattery" -> {
                try {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                    )
                } catch (_: Exception) {
                    try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Exception) {}
                }
                result.success(true)
            }
            "requestPermissions" -> {
                val perms = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE)
                if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
                ActivityCompat.requestPermissions(this, perms.toTypedArray(), 7001)
                result.success(true)
            }
            else -> result.notImplemented()
        }
    }

    private fun hasNotificationAccess(): Boolean {
        val raw = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return raw?.contains(packageName) == true
    }

    private fun fullStatus(): Map<String, Any?> {
        val svc = CallRecorderService.instance
        val base = svc?.statusMap() ?: mapOf(
            "serviceRunning" to false,
            "recording" to false,
            "type" to "",
            "source" to "",
            "file" to "",
            "durationMs" to 0L,
            "amp" to 0,
            "peak" to 0,
            "lastPeak" to 0,
            "speaker" to false
        )
        val out = base.toMutableMap()
        out["mic"] = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        out["phone"] = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        out["notifPerm"] = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        out["notificationAccess"] = hasNotificationAccess()
        out["batteryIgnored"] = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        out["monitorEnabledPref"] = prefs.getBoolean("monitorEnabled", true)
        return out
    }
}
