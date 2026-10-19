package ca.monwallet.app

import ca.monwallet.app.data.SecureSettings
import ca.monwallet.app.updates.updateManifestCandidates
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateManifestTest {
    private val stable = SecureSettings.STABLE_UPDATE_MANIFEST_URL

    @Test fun legacyGithubLatestUrlFallsBackToWalletManifest() {
        val legacy =
            "https://github.com/fredvolks/mon-wallet-android/releases/latest/download/version.json"
        assertEquals(listOf(legacy, stable), updateManifestCandidates(legacy))
    }

    @Test fun stableFeedIsNotFetchedTwice() {
        assertEquals(listOf(stable), updateManifestCandidates(stable))
    }

    @Test fun invalidHttpSourceNeverOverridesStableFeed() {
        assertEquals(listOf(stable), updateManifestCandidates("http://example.invalid/version.json"))
    }
}
