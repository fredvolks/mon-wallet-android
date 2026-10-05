package ca.monwallet.app

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** A small per-app locale setting, independent of the portfolio database. */
object LocaleController {
    private const val PREFS = "display_preferences"
    private const val KEY = "language"

    fun language(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "fr")
            ?.takeIf { it == "fr" || it == "en" } ?: "fr"

    fun wrap(base: Context): Context {
        val configuration = Configuration(base.resources.configuration)
        val locale = Locale.forLanguageTag(language(base))
        Locale.setDefault(locale)
        configuration.setLocale(locale)
        return base.createConfigurationContext(configuration)
    }

    fun select(activity: Activity, language: String) {
        require(language == "fr" || language == "en")
        if (language == language(activity)) return
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, language).apply()
        activity.recreate()
    }
}
