package com.example.operaproxy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.regex.Pattern

/**
 * Проверка обновлений PortalConnect через GitHub API.
 */
object UpdateChecker {
    private const val GITHUB_API = "https://api.github.com/repos/STR97/STRUGOV/releases/tags/PortalConnect"
    private val VERSION_PATTERN = Pattern.compile("PortalConnect-([0-9]+\\.[0-9]+\\.[0-9]+)-universal\\.apk")
    private val executor = Executors.newSingleThreadExecutor()

    data class UpdateInfo(val hasUpdate: Boolean, val remoteVersion: String?, val downloadUrl: String?)

    fun check(context: Context, onResult: (UpdateInfo) -> Unit) {
        executor.execute {
            try {
                val url = URL(GITHUB_API)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                
                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            val matcher = VERSION_PATTERN.matcher(name)
                            if (matcher.find()) {
                                val remoteVersion = matcher.group(1)
                                val downloadUrl = asset.optString("browser_download_url", "")
                                val currentVersion = "1.0.0"
                                
                                if (remoteVersion != null && isNewer(remoteVersion, currentVersion)) {
                                    onResult(UpdateInfo(true, remoteVersion, downloadUrl))
                                    return@execute
                                }
                            }
                        }
                    }
                }
                onResult(UpdateInfo(false, null, null))
            } catch (e: Exception) {
                onResult(UpdateInfo(false, null, null))
            }
        }
    }

    private fun isNewer(remote: String, current: String): Boolean {
        val r = remote.split(".").mapNotNull { it.toIntOrNull() }
        val c = current.split(".").mapNotNull { it.toIntOrNull() }
        if (r.size < 3 || c.size < 3) return false
        for (i in 0..2) {
            if (r[i] > c[i]) return true
            if (r[i] < c[i]) return false
        }
        return false
    }

    fun downloadAndInstall(
        context: Context, 
        downloadUrl: String, 
        onProgress: (Int) -> Unit, 
        onSuccess: (File) -> Unit, 
        onError: () -> Unit
    ) {
        executor.execute {
            try {
                val file = File(context.cacheDir, "updates").apply { mkdirs() }
                val apkFile = File(file, "PortalConnect-update.apk")
                
                val url = URL(downloadUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 60000
                conn.connect()
                
                val contentLength = conn.contentLength
                conn.inputStream.use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        var total = 0L
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            total += read
                            if (contentLength > 0) {
                                onProgress((total * 100 / contentLength).toInt())
                            }
                        }
                    }
                }
                onSuccess(apkFile)
            } catch (e: Exception) {
                onError()
            }
        }
    }

    fun installApk(context: Context, apkFile: File): Boolean {
        return try {
            val uri = if (Build.VERSION.SDK_INT >= 24) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            } else {
                Uri.fromFile(apkFile)
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
