package com.parakey.keyboard

import com.parakey.keyboard.update.UpdatePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {

    @Test
    fun newerVersionsAreDetected() {
        assertTrue(UpdatePolicy.isNewerVersion("v1.1.3", "1.1.2"))
        assertTrue(UpdatePolicy.isNewerVersion("2.0.0", "1.9.9"))
        assertTrue(UpdatePolicy.isNewerVersion("1.1.10", "1.1.9"))
        assertFalse(UpdatePolicy.isNewerVersion("1.1.2", "1.1.2"))
        assertFalse(UpdatePolicy.isNewerVersion("V1.0.9", "1.1.0"))
    }

    @Test
    fun onlyProjectReleaseApksAreTrusted() {
        assertTrue(
            UpdatePolicy.isTrustedApkUrl(
                "https://github.com/SrihariPHS/parakey/releases/download/v1.1.3/app-release.apk",
            ),
        )
        assertFalse(UpdatePolicy.isTrustedApkUrl("https://example.com/app-release.apk"))
        assertFalse(UpdatePolicy.isTrustedApkUrl("https://github.com/other/repo/releases/download/v1/app-release.apk"))
        assertFalse(UpdatePolicy.isTrustedApkUrl("https://github.com/SrihariPHS/parakey/releases/download/v1/notes.txt"))
    }

    @Test
    fun sha256IsParsedFromDigestOrSidecar() {
        val digest = "f1ef2532cdd9ba880762848865763bc0351f8101a1c56558c1ddf67d4833bbc4"
        assertEquals(digest, UpdatePolicy.parseSha256("sha256:$digest"))
        assertEquals(digest, UpdatePolicy.parseSha256("$digest  app/build/outputs/apk/release/app-release.apk"))
        assertNull(UpdatePolicy.parseSha256("not a digest"))
        assertNull(UpdatePolicy.parseSha256(null))
    }

    @Test
    fun releaseNotesAreShortenedForTheCard() {
        assertEquals("Fixes and improvements. Faster typing.", UpdatePolicy.summarizeReleaseNotes("# v1.1.3\n\nFixes and improvements.\nFaster typing."))
        assertTrue(UpdatePolicy.summarizeReleaseNotes("x".repeat(500)).length <= 281)
    }
}
