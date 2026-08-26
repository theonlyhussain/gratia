package com.gratia.music.updater

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive

sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    object UpToDate : UpdateState
    data class UpdateAvailable(val version: String, val changelog: String, val downloadUrl: String) : UpdateState
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val version: String = ""
    ) : UpdateState
    data class ReadyToInstall(val apkFile: File, val version: String = "") : UpdateState
    data class Error(val message: String) : UpdateState
}

class UpdateManager(private val context: Context) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state
    
    private var downloadJob: Job? = null
    @Volatile private var isCancelled = false
    @Volatile private var activeConnection: HttpURLConnection? = null
    
    private val repoUrl = "https://api.github.com/repos/theonlyhussain/gratia/releases/latest"

    suspend fun checkForUpdate(manualCheck: Boolean = false) {
        if (!manualCheck && _state.value is UpdateState.UpdateAvailable) return
        
        _state.value = UpdateState.Checking
        withContext(Dispatchers.IO) {
            try {
                val url = URL(repoUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val tagName = json.getString("tag_name")
                    val body = json.optString("body", "Bug fixes and performance improvements.")
                    
                    val assets = json.getJSONArray("assets")
                    var downloadUrl = ""
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.getString("name").endsWith(".apk")) {
                            downloadUrl = asset.getString("browser_download_url")
                            break
                        }
                    }

                    val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"

                    if (isNewerVersion(tagName, currentVersion) && downloadUrl.isNotEmpty()) {
                        val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Gratia-Update-${tagName}.apk")
                        if (apkFile.exists() && verifyApk(apkFile)) {
                            _state.value = UpdateState.ReadyToInstall(apkFile, tagName)
                        } else {
                            _state.value = UpdateState.UpdateAvailable(tagName, body, downloadUrl)
                        }
                    } else {
                        if (manualCheck) _state.value = UpdateState.UpToDate
                        else _state.value = UpdateState.Idle
                    }
                } else {
                    if (manualCheck) _state.value = UpdateState.Error("Failed to check for updates")
                    else _state.value = UpdateState.Idle
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (manualCheck) _state.value = UpdateState.Error("Failed to connect")
                else _state.value = UpdateState.Idle
            }
        }
    }

    suspend fun downloadUpdate(downloadUrl: String) {
        val availableState = _state.value as? UpdateState.UpdateAvailable ?: return
        val version = availableState.version
        val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Gratia-Update-$version.apk")
        
        if (apkFile.exists() && verifyApk(apkFile)) {
            _state.value = UpdateState.ReadyToInstall(apkFile, version)
            return
        }
        
        isCancelled = false
        _state.value = UpdateState.Downloading(0f, 0L, 0L, version)
        
        withContext(Dispatchers.IO) {
            var output: FileOutputStream? = null
            try {
                var url = URL(downloadUrl)
                var connection = url.openConnection() as HttpURLConnection
                activeConnection = connection
                connection.instanceFollowRedirects = true
                
                var redirectCount = 0
                while (connection.responseCode in 300..399 && redirectCount < 5) {
                    if (isCancelled || !isActive) return@withContext
                    val newUrl = connection.getHeaderField("Location")
                    url = URL(newUrl)
                    connection = url.openConnection() as HttpURLConnection
                    activeConnection = connection
                    redirectCount++
                }

                if (isCancelled || !isActive) return@withContext

                val fileLength = connection.contentLength.toLong()
                output = FileOutputStream(apkFile)

                connection.inputStream.use { input ->
                    val data = ByteArray(8192)
                    var total: Long = 0
                    var count: Int
                    var lastProgress = -1
                    while (input.read(data).also { count = it } != -1) {
                        if (isCancelled || !isActive) {
                            break
                        }
                        total += count
                        if (fileLength > 0) {
                            val progressPercent = (total * 100 / fileLength).toInt()
                            if (progressPercent > lastProgress) {
                                lastProgress = progressPercent
                                val progress = progressPercent.toFloat() / 100f
                                if (!isCancelled && isActive) {
                                    _state.value = UpdateState.Downloading(progress, total, fileLength, version)
                                }
                            }
                        } else {
                            if (!isCancelled && isActive) {
                                _state.value = UpdateState.Downloading(0f, total, 0L, version)
                            }
                        }
                        output.write(data, 0, count)
                    }
                }
                
                output.flush()
                output.close()
                output = null

                if (isCancelled || !isActive) {
                    apkFile.delete()
                    return@withContext
                }

                if (verifyApk(apkFile)) {
                    _state.value = UpdateState.ReadyToInstall(apkFile, version)
                } else {
                    _state.value = UpdateState.Error("Downloaded update is corrupted")
                    apkFile.delete()
                }

            } catch (e: Exception) {
                try { output?.close() } catch (ignored: Exception) {}
                if (isCancelled || !isActive) {
                    apkFile.delete()
                    return@withContext
                }
                e.printStackTrace()
                _state.value = UpdateState.Error("Download failed: ${e.localizedMessage ?: "Unknown error"}")
            } finally {
                activeConnection = null
            }
        }
    }

    fun cancelDownload() {
        isCancelled = true
        try {
            activeConnection?.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        activeConnection = null
        downloadJob?.cancel()
        downloadJob = null
        _state.value = UpdateState.Idle
    }

    fun installUpdate(apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            _state.value = UpdateState.Error("Installation failed")
        }
    }

    fun resetState() {
        _state.value = UpdateState.Idle
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val vLatest = latest.removePrefix("v").trim()
        val vCurrent = current.removePrefix("v").trim()
        
        val latestParts = vLatest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = vCurrent.split(".").mapNotNull { it.toIntOrNull() }
        
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    private fun verifyApk(apkFile: File): Boolean {
        return try {
            val packageInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            if (packageInfo != null) {
                packageInfo.packageName == context.packageName
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
