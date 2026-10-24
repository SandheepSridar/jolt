package com.jolt.update

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.jolt.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** `latest.json`, attached to every GitHub release by release.sh. */
data class ReleaseInfo(
    val versionCode: Int,
    val versionName: String,
    val sha256: String,
    val size: Long,
    val notes: String,
)

/**
 * In-app updates from GitHub Releases. Runs only when the user opens the app or taps
 * "Check for updates"; there's no background polling.
 *
 * Android is the real gatekeeper: it only installs an update signed with the same release key
 * as the installed app. The SHA-256 check catches truncated or corrupted downloads before
 * Android's installer even opens.
 */
class Updater(private val context: Context) {
    private val apkFile get() = File(File(context.cacheDir, "updates").apply { mkdirs() }, "update.apk")

    val installedVersionCode: Int = BuildConfig.VERSION_CODE
    val installedVersionName: String = BuildConfig.VERSION_NAME

    /** A newer published release, or null if this is the newest. Throws if GitHub can't be reached. */
    suspend fun check(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val conn = runCatching { open("latest.json").also { it.connect() } }
            .getOrElse { throw IllegalStateException("Couldn't check for updates", it) }
        try {
            if (conn.responseCode != 200) error("Couldn't check for updates (HTTP ${conn.responseCode})")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            ReleaseInfo(
                versionCode = json.getInt("version_code"),
                versionName = json.getString("version_name"),
                sha256 = json.getString("sha256"),
                size = json.optLong("size"),
                notes = json.optString("notes"),
            ).takeIf { it.versionCode > installedVersionCode }
        } catch (e: Exception) {
            if (e is IllegalStateException) throw e
            throw IllegalStateException("Couldn't check for updates", e)
        } finally {
            conn.disconnect()
        }
    }

    /** Downloads and verifies the APK, reporting 0–1 progress. Throws with a readable message on failure. */
    suspend fun download(info: ReleaseInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val conn = runCatching { open(APK_NAME) }.getOrElse { error("Couldn't reach GitHub. Check your connection.") }
        try {
            if (conn.responseCode != 200) error("Download failed (HTTP ${conn.responseCode})")
            val total = info.size.takeIf { it > 0 } ?: conn.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            var read = 0L
            apkFile.outputStream().use { out ->
                conn.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress((read.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(info.sha256, ignoreCase = true)) {
                apkFile.delete()
                error("The download was corrupted. Try again.")
            }
            apkFile
        } finally {
            conn.disconnect()
        }
    }

    /** Android asks once per app before it may install APKs ("Install unknown apps"). */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Hands the verified APK to Android's installer, which shows its own confirm screen. */
    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** GitHub redirects `releases/latest/download/<asset>` to the newest release's copy. */
    private fun open(asset: String): HttpURLConnection =
        (URL("https://github.com/${BuildConfig.UPDATE_REPO}/releases/latest/download/$asset").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 60_000
            setRequestProperty("Cache-Control", "no-cache")
        }

    private companion object {
        const val APK_NAME = "jolt.apk"
    }
}
