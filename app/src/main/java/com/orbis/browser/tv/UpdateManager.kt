package com.orbis.browser.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class UpdateManager(private val activity: Activity) {

    fun checkForUpdates(force: Boolean = false) {
        val preferences = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastCheck = preferences.getLong(KEY_LAST_CHECK, 0L)
        if (!force && now - lastCheck < CHECK_INTERVAL_MS) return
        preferences.edit().putLong(KEY_LAST_CHECK, now).apply()

        thread {
            runCatching { fetchLatestRelease() }
                .onSuccess { release ->
                    if (release != null && isNewer(release.version, localVersionName())) {
                        activity.runOnUiThread { showUpdateDialog(release) }
                    }
                }
        }
    }

    private fun localVersionName(): String {
        return runCatching {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.packageManager.getPackageInfo(
                    activity.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                activity.packageManager.getPackageInfo(activity.packageName, 0)
            }
            packageInfo.versionName ?: "0.0.0"
        }.getOrDefault("0.0.0")
    }

    private fun fetchLatestRelease(): ReleaseInfo? {
        val connection = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Orbis-Browser-TV")

        return connection.useConnection {
            if (responseCode !in 200..299) return@useConnection null
            val json = JSONObject(inputStream.bufferedReader().use { it.readText() })
            val version = json.optString("tag_name").removePrefix("v")
            val notes = json.optString("body")
            val assets = json.optJSONArray("assets") ?: return@useConnection null
            var apkUrl: String? = null
            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                val name = asset.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    break
                }
            }
            apkUrl?.let { ReleaseInfo(version, notes, it) }
        }
    }

    private fun showUpdateDialog(release: ReleaseInfo) {
        AlertDialog.Builder(activity)
            .setTitle("Atualização disponível")
            .setMessage("Versão ${release.version} disponível. Deseja baixar e instalar agora?")
            .setNegativeButton("Depois", null)
            .setPositiveButton("Atualizar") { _, _ -> downloadAndInstall(release) }
            .show()
    }

    private fun downloadAndInstall(release: ReleaseInfo) {
        Toast.makeText(activity, "Baixando atualização…", Toast.LENGTH_SHORT).show()
        thread {
            runCatching {
                val apkFile = File(activity.cacheDir, "orbis-browser-${release.version}.apk")
                val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 15_000
                connection.readTimeout = 60_000
                connection.setRequestProperty("User-Agent", "Orbis-Browser-TV")
                connection.useConnection {
                    if (responseCode !in 200..299) error("Falha HTTP $responseCode")
                    inputStream.use { input -> apkFile.outputStream().use { output -> input.copyTo(output) } }
                }
                apkFile
            }.onSuccess { apkFile ->
                activity.runOnUiThread { requestInstall(apkFile) }
            }.onFailure {
                activity.runOnUiThread {
                    Toast.makeText(activity, "Não foi possível baixar a atualização", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun requestInstall(apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(activity, "Autorize o Orbis Browser a instalar atualizações", Toast.LENGTH_LONG).show()
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            )
            return
        }

        val apkUri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val remoteParts = remote.split('.').map { it.toIntOrNull() ?: 0 }
        val localParts = local.split('.').map { it.toIntOrNull() ?: 0 }
        val size = maxOf(remoteParts.size, localParts.size)
        for (index in 0 until size) {
            val remoteValue = remoteParts.getOrElse(index) { 0 }
            val localValue = localParts.getOrElse(index) { 0 }
            if (remoteValue != localValue) return remoteValue > localValue
        }
        return false
    }

    private inline fun <T> HttpURLConnection.useConnection(block: HttpURLConnection.() -> T): T {
        return try { block() } finally { disconnect() }
    }

    private data class ReleaseInfo(val version: String, val notes: String, val apkUrl: String)

    companion object {
        private const val LATEST_RELEASE_API =
            "https://api.github.com/repos/PrimalSword/Orbis-Browser-TV/releases/latest"
        private const val PREFS = "orbis_update_preferences"
        private const val KEY_LAST_CHECK = "last_update_check"
        private const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
    }
}
