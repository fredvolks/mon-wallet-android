package ca.monwallet.app.data

import android.content.Context
import android.security.keystore.*
import android.util.Base64
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import ca.monwallet.app.BuildConfig
import java.security.KeyStore
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("device_preferences")

class SecureSettings(private val context: Context) {
    private val prefs = context.getSharedPreferences("secure_settings", 0)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("monwallet.v1", null) as? SecretKey)?.let {
            return it
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                            "monwallet.v1",
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
            }
            .generateKey()
    }

    @Synchronized
    fun get(name: String): String? =
        prefs.getString(name, null)?.let { raw ->
            runCatching {
                    val b = Base64.decode(raw, Base64.NO_WRAP)
                    val c = Cipher.getInstance("AES/GCM/NoPadding")
                    c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b.copyOfRange(0, 12)))
                    String(c.doFinal(b.copyOfRange(12, b.size)))
                }
                .getOrNull()
        }

    @Synchronized
    fun put(name: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(name).commit()
            return
        }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        prefs
            .edit()
            .putString(
                name,
                Base64.encodeToString(c.iv + c.doFinal(value.toByteArray()), Base64.NO_WRAP),
            )
            .commit()
    }

    val preferences =
        context.store.data.map { p ->
            p.asMap().mapKeys { it.key.name }.mapValues { it.value.toString() }
        }

    suspend fun preference(key: String, value: String) {
        context.store.edit { it[stringPreferencesKey(key)] = value }
    }

    val url
        get() = BuildConfig.SUPABASE_URL.trimEnd('/')

    val apiKey
        get() = BuildConfig.SUPABASE_ANON_KEY

    val configured
        get() = url.startsWith("https://") && apiKey.isNotBlank()

    val updateUrl
        get() = get("update_url") ?: BuildConfig.UPDATE_MANIFEST_URL
}
