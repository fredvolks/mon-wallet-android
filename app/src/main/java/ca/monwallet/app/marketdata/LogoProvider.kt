package ca.monwallet.app.marketdata

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import ca.monwallet.app.BuildConfig
import ca.monwallet.app.domain.OfficialDomains
import ca.monwallet.app.domain.Security
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

interface LogoProvider {
    suspend fun load(context: Context, security: Security): Bitmap?
}

/** Only audited issuer domains are requested. Disk and memory caches survive recompositions. */
object OfficialLogoProvider : LogoProvider {
    private val memory = LruCache<String, Bitmap>(80)

    override suspend fun load(context: Context, security: Security): Bitmap? = withContext(Dispatchers.IO) {
        val domain = OfficialDomains.forIdentity(security.symbol, security.exchange, security.currency, security.type)
            ?: return@withContext null
        val identity = listOf(security.symbol, security.exchange, security.currency, security.type, domain)
            .joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
            .joinToString("") { "%02x".format(it) }
        synchronized(memory) { memory.get(digest) }?.let {
            if (BuildConfig.DEBUG) Log.d("MonWalletLogo", "memory hit ${security.symbol} ${security.exchange}")
            return@withContext it
        }
        val directory = File(context.filesDir, "issuer_logos").apply { mkdirs() }
        val file = File(directory, "$digest.png")
        val stored = if (file.exists()) BitmapFactory.decodeFile(file.path) else null
        if (stored != null) {
            if (BuildConfig.DEBUG) Log.d("MonWalletLogo", "disk hit ${security.symbol} ${security.exchange}")
            synchronized(memory) { memory.put(digest, stored) }
            return@withContext stored
        }
        if (BuildConfig.DEBUG) Log.d("MonWalletLogo", "fetch ${security.symbol} ${security.exchange} domain=$domain")
        runCatching {
            val url = Http.url("https://www.google.com/s2/favicons", mapOf("domain" to domain, "sz" to "128"))
            Http.client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful || response.header("Content-Type")?.startsWith("image/") != true) return@use null
                val bytes = response.body?.bytes()?.takeIf { it.size in 150..262144 } ?: return@use null
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?.takeIf { it.width >= 16 && it.height >= 16 } ?: return@use null
                file.writeBytes(bytes)
                synchronized(memory) { memory.put(digest, bitmap) }
                bitmap
            }
        }.getOrNull()
    }
}
