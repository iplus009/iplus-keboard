package org.futo.inputmethod.latin.uix

import org.futo.inputmethod.keyboard.Key
import org.futo.inputmethod.keyboard.internal.PointerTrackerQueue
import org.futo.inputmethod.latin.ImeInsetsMath
import org.futo.inputmethod.latin.uix.actions.AllActionsMap
import org.futo.inputmethod.latin.uix.actions.ActionCategory
import org.futo.inputmethod.latin.uix.actions.CategoryItem
import org.futo.inputmethod.latin.uix.actions.DefaultActionSettings
import org.futo.inputmethod.latin.uix.actions.EmojiItemItem
import org.futo.inputmethod.latin.uix.actions.EmojiViewItem
import org.futo.inputmethod.latin.uix.actions.OneHandedAction
import org.futo.inputmethod.latin.uix.actions.OneHandedModeHelper
import org.futo.inputmethod.latin.uix.actions.emoji.EmojiItem
import org.futo.inputmethod.latin.uix.actions.resolveCategoryForScrollPosition
import org.futo.inputmethod.latin.uix.actions.resolveSpeechLocaleTag
import org.futo.inputmethod.latin.uix.actions.sortLanguagesForPicker
import org.futo.inputmethod.updates.UpdateChecker
import org.futo.inputmethod.latin.uix.theme.CustomPhotoTheme
import org.futo.inputmethod.latin.uix.theme.ThemeOptionKeys
import org.futo.inputmethod.latin.uix.theme.ThemeOptions
import org.futo.inputmethod.latin.uix.theme.presets.AMOLEDNothing
import org.futo.inputmethod.latin.uix.theme.presets.DynamicNothing
import org.futo.inputmethod.latin.uix.theme.presets.IPlusMatteTheme
import org.futo.inputmethod.latin.uix.theme.presets.NothingDark
import org.futo.inputmethod.latin.uix.theme.presets.NothingLight
import org.futo.inputmethod.latin.utils.AutoCorrectionUtils
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

    @Test
    fun pointerTrackerQueueClearAllAndContainsResetStalePointers() {
        val queue = PointerTrackerQueue()
        var canceledCount = 0
        val elem1 = object : PointerTrackerQueue.Element {
            override fun isModifier() = false
            override fun isInDraggingFinger() = false
            override fun onPhantomUpEvent(eventTime: Long) {}
            override fun cancelTrackingForAction() {
                canceledCount++
            }
        }
        val elem2 = object : PointerTrackerQueue.Element {
            override fun isModifier() = false
            override fun isInDraggingFinger() = false
            override fun onPhantomUpEvent(eventTime: Long) {}
            override fun cancelTrackingForAction() {
                canceledCount++
            }
        }

        queue.add(elem1)
        queue.add(elem2)
        assertTrue(queue.contains(elem1))
        assertTrue(queue.contains(elem2))

        queue.clearAll()
        assertEquals(2, canceledCount)
        assertFalse(queue.contains(elem1))
        assertFalse(queue.contains(elem2))

        // Verify queue remains usable after full clear
        queue.add(elem1)
        assertTrue(queue.contains(elem1))
    }

    @Test
    fun keyOnReleasedClearsPressedState() {
        val key = Key("k", "", 'k'.code, null, null, 0, 0, 0, 0, 100, 100, 4, 4)
        assertFalse(key.isPressed)
        key.onPressed()
        assertTrue(key.isPressed)
        key.onReleased()
        assertFalse(key.isPressed)
    }

    @Test
    fun autoCorrectionGuardRejectsBanglishAndShortWordsWhilePreservingTypos() {
        assertFalse(AutoCorrectionUtils.isPlausibleAutoCorrection("kicu", "muchi"))
        assertFalse(AutoCorrectionUtils.isPlausibleAutoCorrection("amar", "army"))
        assertFalse(AutoCorrectionUtils.isPlausibleAutoCorrection("tumi", "time"))
        assertFalse(AutoCorrectionUtils.isPlausibleAutoCorrection("ami", "air"))
        assertFalse(AutoCorrectionUtils.isPlausibleAutoCorrection("ze", "the"))
        assertFalse(AutoCorrectionUtils.isPlausibleAutoCorrection("ki", "hi"))
        assertFalse(AutoCorrectionUtils.isPlausibleAutoCorrection("ha", "he"))

        assertTrue(AutoCorrectionUtils.isPlausibleAutoCorrection("teh", "the"))
        assertTrue(AutoCorrectionUtils.isPlausibleAutoCorrection("helo", "hello"))
        assertTrue(AutoCorrectionUtils.isPlausibleAutoCorrection("dont", "don't"))
        assertTrue(AutoCorrectionUtils.isPlausibleAutoCorrection("im", "I'm"))
        assertTrue(AutoCorrectionUtils.isPlausibleAutoCorrection("i", "I"))
        assertTrue(AutoCorrectionUtils.isPlausibleAutoCorrection("definately", "definitely"))

        assertEquals(4, AutoCorrectionUtils.damerauLevenshteinDistance("kicu", "muchi"))
        assertEquals(1, AutoCorrectionUtils.damerauLevenshteinDistance("teh", "the"))
    }

    @Test
    fun imeInsetsMathPreventsZeroHeightFrameAndClampsWindowCoordinates() {
        assertEquals(840, ImeInsetsMath.resolveEffectiveTouchableHeight(0, 720, 120, 600))
        assertEquals(720, ImeInsetsMath.resolveEffectiveTouchableHeight(0, 0, 120, 600))
        assertEquals(860, ImeInsetsMath.resolveEffectiveTouchableHeight(860, 720, 120, 600))

        assertEquals(1550, ImeInsetsMath.computeImeInsetsTopY(0, 2400, 2400, 850))
        assertEquals(1406, ImeInsetsMath.computeImeInsetsTopY(96, 2160, 2400, 850))
        assertEquals(0, ImeInsetsMath.computeImeInsetsTopY(0, 600, 600, 900))
    }

    @Test
    fun oneHandedModeCycleOppositeAndNormalizationWorks() {
        assertEquals("right", OneHandedModeHelper.nextMode("off"))
        assertEquals("left", OneHandedModeHelper.nextMode("right"))
        assertEquals("off", OneHandedModeHelper.nextMode("left"))
        assertEquals("right", OneHandedModeHelper.nextMode("unknown"))

        assertEquals("right", OneHandedModeHelper.oppositeSide("left"))
        assertEquals("left", OneHandedModeHelper.oppositeSide("right"))
        assertEquals("right", OneHandedModeHelper.oppositeSide("off"))

        assertEquals("left", OneHandedModeHelper.normalizeMode("left"))
        assertEquals("right", OneHandedModeHelper.normalizeMode("right"))
        assertEquals("off", OneHandedModeHelper.normalizeMode(null))
        assertEquals("off", OneHandedModeHelper.normalizeMode("center"))
    }

    @Test
    fun actionRegistryPreservesIndicesAndRegistersOneHanded() {
        val keys = AllActionsMap.keys.toList()
        val expectedLegacyPrefix = listOf(
            "emoji",
            "settings",
            "paste",
            "text_edit",
            "translate",
            "themes",
            "undo",
            "redo",
            "voice_input",
            "system_voice_input",
            "switch_language",
            "clipboard_history",
            "mem_dbg",
            "cut",
            "copy",
            "select_all",
            "more"
        )
        assertEquals(expectedLegacyPrefix, keys.take(expectedLegacyPrefix.size))
        assertEquals("one_handed", keys.last())
        assertEquals(18, keys.size)
        assertTrue(DefaultActionSettings[ActionCategory.Favorites].orEmpty().contains(OneHandedAction))
    }

    @Test
    fun updateCheckerMatchesKeybirdAndLegacyIplusApks() {
        assertTrue(UpdateChecker.isMatchingApkAsset("Keybird-v2.0.4.apk"))
        assertTrue(UpdateChecker.isMatchingApkAsset("keybird-v2.0.4-stable-universal-debug.apk"))
        assertTrue(UpdateChecker.isMatchingApkAsset("iPlus-Keyboard-v2.0.3.apk"))
        assertFalse(UpdateChecker.isMatchingApkAsset("Keybird-v2.0.4.zip"))
        assertFalse(UpdateChecker.isMatchingApkAsset("random-other-app.apk"))

        assertTrue(UpdateChecker.isMatchingReleaseMetadata("v2.0.4", "Keybird v2.0.4"))
        assertTrue(UpdateChecker.isMatchingReleaseMetadata("v2.0.3", "iPlus v2.0.3"))
        assertFalse(UpdateChecker.isMatchingReleaseMetadata("custom-tag", "Other App"))
    }
}
