package org.futo.inputmethod.latin.uix

import android.speech.SpeechRecognizer
import org.futo.inputmethod.keyboard.Key
import org.futo.inputmethod.keyboard.internal.CloudTranslationHandler
import org.futo.inputmethod.keyboard.internal.KeyboardBuilder
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
import org.futo.inputmethod.latin.uix.actions.StickerAction
import org.futo.inputmethod.latin.uix.actions.buildVoiceSpeechLanguageOptions
import org.futo.inputmethod.latin.uix.actions.emoji.EmojiItem
import org.futo.inputmethod.latin.uix.actions.resolveCategoryForScrollPosition
import org.futo.inputmethod.latin.uix.actions.resolveSpeechLocaleTag
import org.futo.inputmethod.latin.uix.actions.resolveVoiceSpeechCandidates
import org.futo.inputmethod.latin.uix.actions.shouldAutoCommitOnSpeechError
import org.futo.inputmethod.latin.uix.actions.shouldRetrySpeechRecognizerError
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
        // New actions are appended, so legacy indexes above stay byte-for-byte stable.
        assertEquals("one_handed", keys[expectedLegacyPrefix.size])
        assertEquals("stickers", keys.last())
        assertEquals(19, keys.size)
        assertTrue(DefaultActionSettings[ActionCategory.Favorites].orEmpty().contains(OneHandedAction))
        assertTrue(DefaultActionSettings[ActionCategory.Favorites].orEmpty().contains(StickerAction))
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

    @Test
    fun keyboardSizeMultiplierClampsAndSnapsWithinSafeBounds() {
        assertEquals(MIN_KEYBOARD_HEIGHT_MULTIPLIER, clampKeyboardHeightMultiplier(0.33f), 0.0001f)
        assertEquals(MIN_KEYBOARD_HEIGHT_MULTIPLIER, clampKeyboardHeightMultiplier(-1.0f), 0.0001f)
        assertEquals(MAX_KEYBOARD_HEIGHT_MULTIPLIER, clampKeyboardHeightMultiplier(1.75f), 0.0001f)
        assertEquals(DEFAULT_KEYBOARD_HEIGHT_MULTIPLIER, clampKeyboardHeightMultiplier(Float.NaN), 0.0001f)
        assertEquals(DEFAULT_KEYBOARD_HEIGHT_MULTIPLIER, clampKeyboardHeightMultiplier(Float.POSITIVE_INFINITY), 0.0001f)
        assertEquals(0.85f, clampKeyboardHeightMultiplier(0.85f), 0.0001f)

        assertEquals(0.75f, snapKeyboardHeightMultiplier(0.76f), 0.0001f)
        assertEquals(0.85f, snapKeyboardHeightMultiplier(0.84f), 0.0001f)
        assertEquals(1.00f, snapKeyboardHeightMultiplier(0.998f), 0.0001f)
        assertEquals(1.30f, snapKeyboardHeightMultiplier(1.29f), 0.0001f)
    }

    @Test
    fun keyboardBuilderClampsScaledHeightToDisplaySafeRange() {
        // 2400px portrait screen: min 16% = 384px, max 65% = 1560px
        assertEquals(657, KeyboardBuilder.clampScaledKeyboardHeight(657, 2400))
        assertEquals(1140, KeyboardBuilder.clampScaledKeyboardHeight(1140, 2400))
        assertEquals(384, KeyboardBuilder.clampScaledKeyboardHeight(200, 2400))
        assertEquals(1560, KeyboardBuilder.clampScaledKeyboardHeight(1800, 2400))
        assertEquals(500, KeyboardBuilder.clampScaledKeyboardHeight(500, 0))
        assertEquals(1, KeyboardBuilder.clampScaledKeyboardHeight(-10, 0))
    }

    @Test
    fun voiceSpeechHelpersHandleLanguageOptionsCandidatesRetryAndAutoCommit() {
        val bnOptions = buildVoiceSpeechLanguageOptions("bn-BD")
        assertEquals(listOf("bn-BD" to "বাংলা", "en-US" to "English"), bnOptions)

        val extraOptions = buildVoiceSpeechLanguageOptions("ar-SA")
        assertEquals(3, extraOptions.size)
        assertEquals("ar-SA", extraOptions.first().first)

        assertEquals("আমি ভালো আছি", resolveVoiceSpeechCandidates(listOf(" ", "আমি ভালো আছি"), "fallback"))
        assertEquals("partial text", resolveVoiceSpeechCandidates(emptyList(), " partial text "))
        assertEquals("", resolveVoiceSpeechCandidates(null, "   "))

        assertTrue(shouldAutoCommitOnSpeechError(SpeechRecognizer.ERROR_NO_MATCH, true, "hello"))
        assertTrue(shouldAutoCommitOnSpeechError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, true, "বাংলা"))
        assertFalse(shouldAutoCommitOnSpeechError(SpeechRecognizer.ERROR_NO_MATCH, false, "hello"))
        assertFalse(shouldAutoCommitOnSpeechError(SpeechRecognizer.ERROR_NO_MATCH, true, "   "))

        assertTrue(shouldRetrySpeechRecognizerError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY, 0))
        assertTrue(shouldRetrySpeechRecognizerError(SpeechRecognizer.ERROR_CLIENT, 0))
        assertFalse(shouldRetrySpeechRecognizerError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY, 1))
        assertFalse(shouldRetrySpeechRecognizerError(SpeechRecognizer.ERROR_AUDIO, 0))
    }

    @Test
    fun cloudTranslationCacheNormalizesWhitespaceAndSameLanguageShortcut() {
        CloudTranslationHandler.clearCacheForTest()
        assertEquals("bn|en|হ্যালো", CloudTranslationHandler.buildCacheKey("bn", "en", "  হ্যালো  "))
        assertEquals("", CloudTranslationHandler.getCached("bn", "en", "   "))
        assertEquals("hello", CloudTranslationHandler.getCached("en", "en", " hello "))

        CloudTranslationHandler.putCachedForTest("bn", "en", "কেমন আছেন", "How are you")
        assertEquals("How are you", CloudTranslationHandler.getCached("bn", "en", " কেমন আছেন "))
        assertTrue(CloudTranslationHandler.buildTranslateUrl("bn", "en", " হ্যালো ").contains("sl=bn&tl=en"))
        CloudTranslationHandler.clearCacheForTest()
    }

    @Test
    fun updateCheckerRejectsUnrelatedIplusApksAndArbitraryVTags() {
        assertFalse(UpdateChecker.isMatchingApkAsset("iplusgames-v1.apk"))
        assertFalse(UpdateChecker.isMatchingApkAsset("com.iplus.notes.apk"))
        assertFalse(UpdateChecker.isMatchingApkAsset("iplus_wallpaper_hd.apk"))
        assertFalse(UpdateChecker.isMatchingReleaseMetadata("v1.0.0", "Other App"))
        assertFalse(UpdateChecker.isMatchingReleaseMetadata("v2.5", "Games Release"))
    }

    @Test
    fun coerceFiniteInReplacesNonFinitePhotoParameters() {
        assertEquals(1.0f, Float.NaN.coerceFiniteIn(1.0f, 1.0f, 2.5f))
        assertEquals(0.0f, Float.POSITIVE_INFINITY.coerceFiniteIn(0.0f, -1f, 1f))
        assertEquals(0.35f, Float.NEGATIVE_INFINITY.coerceFiniteIn(0.35f, 0f, 0.85f))
        assertEquals(2.5f, 5.0f.coerceFiniteIn(1.0f, 1.0f, 2.5f))
        assertEquals(0.25f, 0.25f.coerceFiniteIn(0.35f, 0f, 0.85f))
    }

    @Test
    fun themeNameTruncationAndCollisionAvoidSurrogateSplitsAndPresetLoss() {
        val boundaryEmoji = "a".repeat(31) + "\uD83D\uDE2D"
        val truncated = truncateThemeNameSafely(boundaryEmoji, 32)
        assertEquals("a".repeat(31), truncated)
        assertFalse(Character.isHighSurrogate(truncated.last()))

        val allEmojis = "\uD83D\uDE2D".repeat(17)
        val trimmedEmojis = truncateThemeNameSafely(allEmojis, 32)
        assertEquals(32, trimmedEmojis.length)
        assertEquals("\uD83D\uDE2D".repeat(16), trimmedEmojis)

        val saved = listOf(
            "Sunset|uri1|0.2|0.0|1.0|0.0|0.0",
            "Night|uri1|0.2|0.0|1.0|0.0|0.0"
        )
        assertTrue(hasThemeNameCollision(saved, "sunset"))
        assertTrue(hasThemeNameCollision(saved, "Sunset", excludingRecord = saved[1]))
        assertFalse(hasThemeNameCollision(saved, "Sunset", excludingRecord = saved[0]))
        assertFalse(hasThemeNameCollision(saved, "Ocean"))
    }

    @Test
    fun cryAndBanglaExpressionSearchPrioritizesFacesOverNoise() {
        val sample = listOf(
            org.futo.inputmethod.latin.uix.actions.emoji.EmojiItem("🚗", "automobile", "Travel & Places", false, emptyList(), listOf("car")),
            org.futo.inputmethod.latin.uix.actions.emoji.EmojiItem("🐱", "cat face", "Animals & Nature", false, emptyList(), listOf("cat")),
            org.futo.inputmethod.latin.uix.actions.emoji.EmojiItem("🔑", "key", "Objects", false, emptyList(), listOf("key")),
            org.futo.inputmethod.latin.uix.actions.emoji.EmojiItem("😢", "crying face", "Smileys & Emotion", false, listOf("sad", "tear", "cry", "crying"), listOf("cry")),
            org.futo.inputmethod.latin.uix.actions.emoji.EmojiItem("😭", "loudly crying face", "Smileys & Emotion", false, listOf("sad", "cry", "crying", "tear"), listOf("sob")),
            org.futo.inputmethod.latin.uix.actions.emoji.EmojiItem("🥹", "face holding back tears", "Smileys & Emotion", false, listOf("tears", "tear", "cry", "sad"), listOf("face_holding_back_tears")),
            org.futo.inputmethod.latin.uix.actions.emoji.EmojiItem("😿", "crying cat", "Smileys & Emotion", false, listOf("sad", "tear", "cry", "crying", "cat"), listOf("crying_cat_face"))
        )

        val cryMatches = org.futo.inputmethod.latin.uix.actions.searchEmojiItems(sample, "cry").map { it.emoji }
        assertTrue(cryMatches.containsAll(listOf("😢", "😭", "🥹", "😿")))
        assertFalse(cryMatches.contains("🚗"))
        assertFalse(cryMatches.contains("🐱"))
        assertFalse(cryMatches.contains("🔑"))

        val banglaMatches = org.futo.inputmethod.latin.uix.actions.searchEmojiItems(sample, "কান্না").map { it.emoji }
        assertTrue(banglaMatches.containsAll(listOf("😢", "😭", "🥹", "😿")))
        assertFalse(banglaMatches.contains("🚗"))
    }
}
