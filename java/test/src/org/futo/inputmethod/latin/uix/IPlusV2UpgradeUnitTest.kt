package org.futo.inputmethod.latin.uix

import org.futo.inputmethod.keyboard.internal.CloudTranslationHandler
import org.futo.inputmethod.latin.uix.actions.ClipboardEntry
import org.futo.inputmethod.latin.uix.actions.clipboardEntryKey
import org.futo.inputmethod.latin.uix.actions.emoji.EmojiItem
import org.futo.inputmethod.latin.uix.actions.filterClipboardEntries
import org.futo.inputmethod.latin.uix.actions.generateSkinToneVariants
import org.futo.inputmethod.latin.uix.actions.resolveEmojiSearchTargets
import org.futo.inputmethod.latin.uix.actions.searchEmojiItems
import org.futo.inputmethod.latin.uix.actions.swapLanguagePair
import org.futo.inputmethod.latin.uix.actions.toggleBnEnPair
import org.futo.inputmethod.latin.uix.theme.ThemeOptions
import org.futo.inputmethod.latin.uix.theme.presets.IPlusMatteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class IPlusV2UpgradeUnitTest {

    @Test
    fun legacyOrbitThemeKeyMigratesWhileNothingThemesStayIntact() {
        assertEquals(IPlusMatteTheme.key, normalizeThemeKey(LEGACY_ORBIT_THEME_KEY))
        assertEquals("IPlusMatteTheme", IPlusMatteTheme.key)
        assertEquals("DynamicNothing", normalizeThemeKey("DynamicNothing"))
        assertTrue(ThemeOptions.containsKey("DynamicNothing"))
        assertTrue(ThemeOptions.containsKey("AMOLEDNothing"))
        assertTrue(ThemeOptions.containsKey("NothingLight"))
        assertTrue(ThemeOptions.containsKey("NothingDark"))
        assertTrue(ThemeOptions.containsKey("IPlusMatteTheme"))
    }

    @Test
    fun translatorHelpersCacheKeySwapAndErrors() {
        assertEquals("bn|en|হ্যালো", CloudTranslationHandler.buildCacheKey("bn", "en", "হ্যালো"))
        assertTrue(CloudTranslationHandler.isTransientIoError(SocketTimeoutException("timeout")))
        assertTrue(CloudTranslationHandler.isTransientIoError(IOException("reset")))
        assertFalse(CloudTranslationHandler.isTransientIoError(IllegalArgumentException("bad")))
        assertTrue(CloudTranslationHandler.formatTranslationError(null, 429).contains("Too many"))

        assertNull(swapLanguagePair("auto", "bn"))
        assertEquals("bn" to "en", swapLanguagePair("en", "bn"))
        assertEquals("en" to "bn", toggleBnEnPair("bn", "en"))
        assertEquals("bn" to "en", toggleBnEnPair("fr", "de"))
    }

    @Test
    fun clipboardSearchFilterAndStableKeys() {
        val longA = "a".repeat(600) + "_1"
        val longB = "a".repeat(600) + "_2"
        val e1 = ClipboardEntry(timestamp = 10L, pinned = false, text = longA, uri = null, mimeTypes = emptyList())
        val e2 = ClipboardEntry(timestamp = 11L, pinned = true, text = "Bangla Keyboard", uri = null, mimeTypes = emptyList())
        val e3 = ClipboardEntry(timestamp = 12L, pinned = false, text = longB, uri = null, mimeTypes = emptyList())

        assertNotEquals(clipboardEntryKey(e1, 0), clipboardEntryKey(e3, 0))
        val filtered = filterClipboardEntries(listOf(e1, e2, e3), "bangla")
        assertEquals(1, filtered.size)
        assertEquals("Bangla Keyboard", filtered.first().text)
        assertEquals(listOf(e3, e2, e1), filterClipboardEntries(listOf(e1, e2, e3), "   "))
    }

    @Test
    fun banglaEmojiSearchAndMixedSkinToneVariants() {
        val targets = resolveEmojiSearchTargets("হাসি")
        assertTrue(targets.contains("smile"))

        val fakeSmile = EmojiItem(
            emoji = "😄",
            description = "grinning face with smiling eyes",
            category = "Smileys & Emotion",
            skinTones = false,
            tags = listOf("happy", "joy"),
            aliases = listOf("smile")
        )
        val fakeFire = EmojiItem(
            emoji = "🔥",
            description = "fire",
            category = "Travel & Places",
            skinTones = false,
            tags = emptyList(),
            aliases = listOf("fire")
        )
        val matches = searchEmojiItems(listOf(fakeFire, fakeSmile), "হাসি")
        assertTrue(matches.any { it.emoji == "😄" })

        val woman = EmojiItem("👩", "woman", "People & Body", true, emptyList(), emptyList())
        val man = EmojiItem("👨", "man", "People & Body", true, emptyList(), emptyList())
        val handshake = EmojiItem("\uD83E\uDD1D", "handshake", "People & Body", false, emptyList(), emptyList())
        val emojiMap = mapOf("👩" to woman, "👨" to man, "\uD83E\uDD1D" to handshake)

        val variants = generateSkinToneVariants("👩\u200D\uD83E\uDD1D\u200D👨", emojiMap)
        assertTrue(variants.size > 5)
        assertTrue(variants.size <= 25)
        assertTrue(variants.any { it.contains("\uD83C\uDFFB") && it.contains("\uD83C\uDFFF") })
    }
}
