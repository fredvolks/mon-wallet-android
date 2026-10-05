package ca.monwallet.app

import android.view.ViewGroup
import androidx.work.Configuration
import androidx.work.WorkManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

// Robolectric does not run AndroidX Startup's provider before Application.onCreate.
class TestMonWallet : MonWallet() {
    override fun onCreate() {
        if (runCatching { WorkManager.getInstance(this) }.isFailure)
            WorkManager.initialize(this,Configuration.Builder().build())
        super.onCreate()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = TestMonWallet::class)
@LooperMode(LooperMode.Mode.PAUSED)
class AppLaunchTest {
    @Test fun nativeActivityCreatesComposeContent() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val root=controller.get().findViewById<ViewGroup>(android.R.id.content)
            assertNotNull(root)
            assertTrue(root.childCount>0)
        } finally { controller.pause().stop().destroy() }
    }
}
