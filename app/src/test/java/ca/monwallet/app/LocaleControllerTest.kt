package ca.monwallet.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LocaleControllerTest {
    @Test fun selectedLanguagePersistsAndLoadsAndroidResources() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val previous = Locale.getDefault()
        val preferences = app.getSharedPreferences("display_preferences", 0)
        try {
            preferences.edit().putString("language", "fr").commit()
            assertEquals("Portefeuille", LocaleController.wrap(app).getString(R.string.nav_portfolio))
            preferences.edit().putString("language", "en").commit()
            assertEquals("Portfolio", LocaleController.wrap(app).getString(R.string.nav_portfolio))
            assertEquals("en", LocaleController.language(app))
        } finally {
            preferences.edit().clear().commit()
            Locale.setDefault(previous)
        }
    }
}
