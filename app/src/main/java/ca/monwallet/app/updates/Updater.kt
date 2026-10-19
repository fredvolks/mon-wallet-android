package ca.monwallet.app.updates

import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import ca.monwallet.app.BuildConfig
import ca.monwallet.app.data.SecureSettings
import ca.monwallet.app.marketdata.Http
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/** No branch/tag called "latest" is used: LoveRoll had hijacked that release alias. */
internal fun updateManifestCandidates(preferred: String): List<String> =
    listOf(preferred, SecureSettings.STABLE_UPDATE_MANIFEST_URL)
        .filter { it.startsWith("https://") }
        .distinct()

class Updater(private val context: Context, private val secure: SecureSettings) {
    data class Release(
        val code: Int,
        val name: String,
        val url: String,
        val sha: String,
        val notes: String,
        val mandatory: Boolean,
        val min: Int,
    )

    suspend fun check(): Release? {
        var lastError: Exception? = null
        // Existing APKs and repository variables can still point to the former
        // GitHub "latest" URL, which currently selects a LoveRoll release.
        // Fall back to an independent, stable Mon Wallet manifest on HTTP 404
        // or malformed data without ever accepting another app's APK.
        for (url in updateManifestCandidates(secure.updateUrl)) {
            try {
                val j = Http.json(
                    Request.Builder().url(url).header("Cache-Control", "no-cache").build()
                )
                val r = Release(
                    j.getInt("versionCode"),
                    j.getString("versionName"),
                    j.getString("apkUrl"),
                    j.getString("sha256"),
                    j.optString("releaseNotes"),
                    j.optBoolean("mandatory"),
                    j.optInt("minimumSupportedVersion", 1),
                )
                require(r.code > 0 && r.name.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) {
                    "Version Mon Wallet invalide."
                }
                require(r.url.startsWith("https://") &&
                    r.sha.matches(Regex("[a-fA-F0-9]{64}"))) {
                    "Manifeste de mise à jour invalide."
                }
                // SHA-256, version code, package ID and signing certificate are
                // independently verified by download() before installation.
                return r.takeIf { it.code > BuildConfig.VERSION_CODE }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw IllegalStateException(
            "Impossible de vérifier les mises à jour Mon Wallet. Réessaie plus tard.",
            lastError,
        )
    }

    suspend fun download(r: Release): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, "MonWallet-${r.code}.apk.part")
            try {
                Http.client.newCall(Request.Builder().url(r.url).build()).execute().use { response
                    ->
                    check(response.isSuccessful)
                    val body = response.body ?: error("Fichier vide.")
                    check(body.contentLength() <= 200 * 1024 * 1024) { "APK trop volumineux." }
                    body.byteStream().use { input ->
                        file.outputStream().use { out ->
                            val bytes = ByteArray(65536)
                            var total = 0L
                            while (true) {
                                val n = input.read(bytes)
                                if (n < 0) break
                                total += n
                                check(total <= 200 * 1024 * 1024)
                                out.write(bytes, 0, n)
                            }
                        }
                    }
                }
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { stream ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        val n = stream.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                    }
                }
                val sha = digest.digest().joinToString("") { "%02x".format(it) }
                check(sha.equals(r.sha, true)) { "Intégrité de l’APK invalide." }
                val flags =
                    if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
                    else PackageManager.GET_SIGNATURES
                val incoming =
                    context.packageManager.getPackageArchiveInfo(file.path, flags)
                        ?: error("APK invalide.")
                val current = context.packageManager.getPackageInfo(context.packageName, flags)
                check(incoming.packageName == context.packageName) {
                    "Identifiant d’application invalide."
                }
                val code =
                    if (Build.VERSION.SDK_INT >= 28) incoming.longVersionCode
                    else incoming.versionCode.toLong()
                check(code == r.code.toLong() && code > BuildConfig.VERSION_CODE) {
                    "Version invalide."
                }
                fun certificates(p: android.content.pm.PackageInfo) =
                    if (Build.VERSION.SDK_INT >= 28)
                        p.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
                    else p.signatures?.map { it.toCharsString() }?.toSet()
                check(
                    certificates(incoming) != null &&
                        certificates(incoming) == certificates(current)
                ) {
                    "Certificat de signature différent."
                }
                val ready = File(dir, "MonWallet-${r.code}.apk")
                check(file.renameTo(ready))
                ready
            } catch (e: Exception) {
                file.delete()
                throw e
            }
        }

    fun install(file: File) {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    )
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }
}
