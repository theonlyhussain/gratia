package com.gratia.music.updater

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * What the updater is doing right now.
 *
 * Every state a user can reach has a name here, because a screen that can only
 * say "spinning" is a screen that lies the moment the network is slow. The
 * version numbers carried by [UpToDate] and [UpdateAvailable] are the ones the
 * server actually returned — nothing is hard-coded.
 */
sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    data class UpToDate(val latestVersion: String) : UpdateState

    data class UpdateAvailable(
        val version: String,
        val changelog: String,
        val downloadUrl: String,
        /** Release asset size in bytes, when GitHub reports one. */
        val sizeBytes: Long? = null,
    ) : UpdateState

    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val version: String = ""
    ) : UpdateState

    data class ReadyToInstall(val apkFile: File, val version: String = "") : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * The one update system.
 *
 * There is no background check and no "smart" mode: a check happens when the
 * user presses Check for updates, and at no other time. Everything else here is
 * the honest machinery around that — one release lookup, one download with
 * progress, one hand-off to Android's package installer, and a message for each
 * way any of those can fail.
 */
class UpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "UpdateManager"

        /**
         * Double-tap protection, not a rate limit. Checking is user-initiated
         * and cheap; the guard exists so that a pressed-twice button does not
         * open two release lookups whose answers can land out of order.
         */
        private const val MIN_RECHECK_INTERVAL_MS = 10_000L

        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
    }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    private var downloadJob: Job? = null

    @Volatile private var isCancelled = false
    @Volatile private var activeConnection: HttpURLConnection? = null

    @Volatile private var checkInFlight = false
    @Volatile private var lastCheckCompletedAtMs = 0L

    private val repoUrl = "https://api.github.com/repos/theonlyhussain/gratia/releases/latest"

    /** The installed version, read from the package rather than remembered. */
    fun currentVersionName(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
    } catch (e: Exception) {
        "—"
    }

    /**
     * Looks up the latest release.
     *
     * @param manualCheck whether a user asked. A non-manual check reports
     *   failures as [UpdateState.Idle] rather than an error, because nobody is
     *   waiting to read it.
     * @param force bypasses the double-tap guard — used when the user explicitly
     *   retries after a failure.
     */
    suspend fun checkForUpdate(manualCheck: Boolean = true, force: Boolean = false) {
        if (checkInFlight) {
            Log.d(TAG, "UPDATE_CHECK_SKIPPED reason=already_checking")
            return
        }
        if (!force && manualCheck &&
            System.currentTimeMillis() - lastCheckCompletedAtMs < MIN_RECHECK_INTERVAL_MS
        ) {
            Log.d(TAG, "UPDATE_CHECK_SKIPPED reason=recently_checked")
            return
        }
        if (_state.value is UpdateState.Checking) return

        if (manualCheck && !com.gratia.music.data.network.NetworkMonitor.isCurrentlyOnline()) {
            _state.value = UpdateState.Error("You're offline. Connect to the internet and try again.")
            return
        }

        checkInFlight = true
        _state.value = UpdateState.Checking
        try {
            withContext(Dispatchers.IO) {
                checkBlocking(manualCheck)
            }
        } finally {
            checkInFlight = false
            lastCheckCompletedAtMs = System.currentTimeMillis()
        }
    }

    private fun checkBlocking(manualCheck: Boolean) {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(repoUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "UPDATE_CHECK_HTTP_FAILURE code=$code")
                _state.value = if (manualCheck) {
                    UpdateState.Error(
                        when (code) {
                            403, 429 -> "The update server is rate-limiting requests. Try again in a few minutes."
                            in 500..599 -> "The update server is having trouble (HTTP $code). Try again shortly."
                            else -> "Couldn't check for updates (HTTP $code)."
                        }
                    )
                } else {
                    UpdateState.Idle
                }
                return
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val tagName = json.optString("tag_name").takeIf { it.isNotBlank() }
            if (tagName == null) {
                Log.w(TAG, "UPDATE_CHECK_INVALID_RELEASE reason=missing_tag")
                _state.value = if (manualCheck) {
                    UpdateState.Error("The latest release couldn't be read. Try again later.")
                } else {
                    UpdateState.Idle
                }
                return
            }

            val latestVersion = tagName.removePrefix("v").trim()
            val body = json.optString("body").takeIf { it.isNotBlank() }
                ?: "No release notes were published for this version."

            var downloadUrl = ""
            var sizeBytes: Long? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url")
                        val size = asset.optLong("size", 0L)
                        sizeBytes = size.takeIf { it > 0 }
                        break
                    }
                }
            }

            val currentVersion = currentVersionName()

            if (isNewerVersion(latestVersion, currentVersion) && downloadUrl.isNotEmpty()) {
                val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                downloadsDir?.listFiles()?.forEach { file ->
                    if (file.name.startsWith("Gratia-Update-") && file.name.endsWith(".apk")) {
                        if (!verifyApk(file)) file.delete()
                    }
                }

                val apkFile = File(downloadsDir, "Gratia-Update-$latestVersion.apk")
                _state.value = if (apkFile.exists() && verifyApk(apkFile)) {
                    UpdateState.ReadyToInstall(apkFile, latestVersion)
                } else {
                    UpdateState.UpdateAvailable(latestVersion, body, downloadUrl, sizeBytes)
                }
            } else {
                // Up to date — and the version it compared against is reported,
                // so the screen can show what the server actually said instead of
                // only asserting that nothing is new.
                _state.value = UpdateState.UpToDate(latestVersion)
            }
        } catch (e: Exception) {
            Log.w(TAG, "UPDATE_CHECK_FAILED ${e.message}")
            _state.value = if (manualCheck) {
                UpdateState.Error("Couldn't check for updates. Check your connection and try again.")
            } else {
                UpdateState.Idle
            }
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun downloadUpdate(downloadUrl: String) {
        val availableState = _state.value as? UpdateState.UpdateAvailable ?: return
        val version = availableState.version
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir == null) {
            _state.value = UpdateState.Error("No writable storage for the update.")
            return
        }
        val apkFile = File(downloadsDir, "Gratia-Update-$version.apk")

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
                    val newUrl = connection.getHeaderField("Location") ?: break
                    url = URL(newUrl)
                    connection = url.openConnection() as HttpURLConnection
                    activeConnection = connection
                    redirectCount++
                }

                if (isCancelled || !isActive) return@withContext

                val httpCode = connection.responseCode
                if (httpCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "UPDATE_DOWNLOAD_HTTP_FAILURE code=$httpCode")
                    _state.value = UpdateState.Error("The download failed (HTTP $httpCode). Try again.")
                    return@withContext
                }

                val fileLength = connection.contentLength.toLong()
                output = FileOutputStream(apkFile)

                connection.inputStream.use { input ->
                    val data = ByteArray(8192)
                    var total: Long = 0
                    var count: Int
                    var lastProgress = -1
                    while (input.read(data).also { count = it } != -1) {
                        if (isCancelled || !isActive) break
                        total += count
                        if (fileLength > 0) {
                            val progressPercent = (total * 100 / fileLength).toInt()
                            if (progressPercent > lastProgress) {
                                lastProgress = progressPercent
                                if (!isCancelled && isActive) {
                                    _state.value = UpdateState.Downloading(
                                        progressPercent.toFloat() / 100f, total, fileLength, version
                                    )
                                }
                            }
                        } else {
                            // No length reported: say so rather than inventing a
                            // percentage from nothing.
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

                if (apkFile.length() == 0L) {
                    apkFile.delete()
                    _state.value = UpdateState.Error("The downloaded update was empty. Try again.")
                } else if (verifyApk(apkFile)) {
                    _state.value = UpdateState.ReadyToInstall(apkFile, version)
                } else {
                    _state.value = UpdateState.Error("The downloaded update couldn't be verified and was discarded.")
                    apkFile.delete()
                }
            } catch (e: IOException) {
                try { output?.close() } catch (ignored: Exception) {}
                if (isCancelled || !isActive) {
                    apkFile.delete()
                    return@withContext
                }
                Log.w(TAG, "UPDATE_DOWNLOAD_IO_FAILURE ${e.message}")
                apkFile.delete()
                _state.value = UpdateState.Error(describeDownloadFailure(e))
            } catch (e: Exception) {
                try { output?.close() } catch (ignored: Exception) {}
                if (isCancelled || !isActive) {
                    apkFile.delete()
                    return@withContext
                }
                Log.w(TAG, "UPDATE_DOWNLOAD_FAILED ${e.message}")
                apkFile.delete()
                _state.value = UpdateState.Error("Download failed. Check your connection and try again.")
            } finally {
                activeConnection = null
            }
        }
    }

    /** A storage error is not a network error, and the message should say which. */
    private fun describeDownloadFailure(e: IOException): String {
        val text = (e.message ?: "").lowercase()
        return when {
            text.contains("enospc") || text.contains("no space") ->
                "Not enough storage to download the update. Free some space and try again."
            text.contains("permission") || text.contains("eacces") ->
                "Gratia couldn't write the update to storage."
            else -> "Download failed. Check your connection and try again."
        }
    }

    fun cancelDownload() {
        isCancelled = true
        try {
            activeConnection?.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "cancelDownload disconnect failed: ${e.message}")
        }
        activeConnection = null
        downloadJob?.cancel()
        downloadJob = null
        _state.value = UpdateState.Idle
    }

    fun installUpdate(apkFile: File) {
        try {
            if (!apkFile.exists()) {
                _state.value = UpdateState.Error("The downloaded update is no longer on this device. Download it again.")
                return
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                // Not an error the user can't fix, and not a silent dead end:
                // say what is needed, then take them to the switch.
                _state.value = UpdateState.Error(
                    "Allow Gratia to install apps, then tap Install again."
                )
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Log.w(TAG, "UPDATE_INSTALL_UNAVAILABLE ${e.message}")
            _state.value = UpdateState.Error("No app installer is available on this device.")
        } catch (e: Exception) {
            Log.w(TAG, "UPDATE_INSTALL_FAILED ${e.message}")
            _state.value = UpdateState.Error("Couldn't start the installer. Try again.")
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
        if (latestParts.isEmpty()) return false

        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    /**
     * Confirms a downloaded APK is really a newer Gratia, not an HTML error page
     * with an .apk name or a build for some other app. Both answers matter: an
     * installer handed a foreign package would fail at the last step, and the
     * user would have waited for a download to be told nothing useful.
     */
    private fun verifyApk(apkFile: File): Boolean {
        return try {
            val packageInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            if (packageInfo != null) {
                if (packageInfo.packageName != context.packageName) return false

                val currentPackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val downloadedVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    packageInfo.versionCode.toLong()
                }
                val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    currentPackageInfo.longVersionCode
                } else {
                    currentPackageInfo.versionCode.toLong()
                }

                downloadedVersionCode > currentVersionCode
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
