package org.futo.inputmethod.latin.uix

import org.futo.inputmethod.latin.uix.actions.CategoryItem
import org.futo.inputmethod.latin.uix.actions.EmojiItemItem
import org.futo.inputmethod.latin.uix.actions.EmojiViewItem
import org.futo.inputmethod.latin.uix.actions.emoji.EmojiItem
import org.futo.inputmethod.latin.uix.actions.resolveCategoryForScrollPosition
import org.futo.inputmethod.latin.uix.actions.resolveSpeechLocaleTag
import org.futo.inputmethod.latin.uix.actions.sortLanguagesForPicker
import org.futo.inputmethod.latin.uix.theme.CustomPhotoTheme
import org.futo.inputmethod.latin.uix.theme.ThemeOptionKeys
import org.futo.inputmethod.latin.uix.theme.ThemeOptions
import org.futo.inputmethod.latin.uix.theme.presets.AMOLEDNothing
import org.futo.inputmethod.latin.uix.theme.presets.DynamicNothing
import org.futo.inputmethod.latin.uix.theme.presets.IPlusMatteTheme
import org.futo.inputmethod.latin.uix.theme.presets.NothingDark
import org.futo.inputmethod.latin.uix.theme.presets.NothingLight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class BugFixRegressionUnitTest {

    private fun testEmoji(glyph: String): EmojiItemItem = EmojiItemItem(
        EmojiItem(
            emoji = glyph,
            description = "test",
            category = "Test",
            skinTones = false,
            tags = emptyList(),
            aliases = emptyList()
        )
    )

    @Test
    fun resolveSpeechLocaleTag_handlesBanglaRegionlessAndBlankLocales() {
        assertEquals("bn-BD", resolveSpeechLocaleTag(Locale("bn")))
        assertEquals("bn-IN", resolveSpeechLocaleTag(Locale("bn", "IN")))
        assertEquals("en-US", resolveSpeechLocaleTag(Locale("en", "US")))
        assertEquals("en", resolveSpeechLocaleTag(Locale("en")))
        assertEquals("en-US", resolveSpeechLocaleTag(Locale("")))
    }

    @Test
    fun sortLanguagesForPicker_pinsAutoBengaliEnglishForSourceAndExcludesAutoForTarget() {
        val sample = mapOf(
            "fr" to "french",
            "en" to "English",
            "auto" to "Detect language",
            "bn" to "Bengali",
            "de" to "German",
            "ar" to "Arabic"
        )

        val sourceOrder = sortLanguagesForPicker(sample, allowAuto = true).map { it.first }
        assertEquals(listOf("auto", "bn", "en", "ar", "fr", "de"), sourceOrder)

        val targetOrder = sortLanguagesForPicker(sample, allowAuto = false).map { it.first }
        assertEquals(listOf("bn", "en", "ar", "fr", "de"), targetOrder)

        val missingPinned = sortLanguagesForPicker(mapOf("es" to "Spanish", "ar" to "Arabic"), allowAuto = false)
        assertEquals(listOf("ar" to "Arabic", "es" to "Spanish"), missingPinned)
    }

    @Test
    fun resolveCategoryForScrollPosition_handlesEmptyOutOfBoundsAndNormalScrolling() {
        assertNull(resolveCategoryForScrollPosition(emptyList(), 0, 0))
        assertNull(resolveCategoryForScrollPosition(listOf(testEmoji("😀")), 0, 0))

        val items: List<EmojiViewItem> = listOf(
            CategoryItem("Recent"),
            testEmoji("😀"),
            testEmoji("😂"),
            CategoryItem("Smileys"),
            testEmoji("😍"),
            CategoryItem("Animals"),
            testEmoji("🐶")
        )

        assertNull(resolveCategoryForScrollPosition(items, -1, 2))
        assertEquals("Recent", resolveCategoryForScrollPosition(items, 0, 2)?.title)
        assertEquals("Recent", resolveCategoryForScrollPosition(items, 1, 3)?.title)
        assertEquals("Smileys", resolveCategoryForScrollPosition(items, 3, 4)?.title)
        assertEquals("Animals", resolveCategoryForScrollPosition(items, 2, 6)?.title)
        assertEquals("Animals", resolveCategoryForScrollPosition(items, 99, 100)?.title)
    }

    @Test
    fun nothingThemesAndLegacyMigrationRemainIntact() {
        val requiredKeys = listOf(
            IPlusMatteTheme.key,
            DynamicNothing.key,
            AMOLEDNothing.key,
            NothingLight.key,
            NothingDark.key,
            CustomPhotoTheme.key
        )
        requiredKeys.forEach { key ->
            assertTrue("Expected ThemeOptions to contain $key", ThemeOptions.containsKey(key))
            assertTrue("Expected ThemeOptionKeys to contain $key", ThemeOptionKeys.contains(key))
        }

        assertFalse(ThemeOptionKeys.contains(LEGACY_ORBIT_THEME_KEY))
        assertEquals(IPlusMatteTheme.key, normalizeThemeKey(LEGACY_ORBIT_THEME_KEY))
        assertEquals(AMOLEDNothing.key, normalizeThemeKey(AMOLEDNothing.key))
    }
}
