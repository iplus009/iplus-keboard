package org.futo.inputmethod.latin.uix

import org.futo.inputmethod.latin.uix.actions.OneHandedModeHelper
import org.futo.inputmethod.updates.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * StrictMathProbeTest — strict verification of numeric + parsing logic that
 * the existing suite only covers loosely.
 *
 * Every test here is labelled with the expectation it encodes. A FAILED test
 * means the production behaviour differs from the documented contract.
 */
class StrictMathProbeTest {

    // ------------------------------------------------------------------
    // PROBE 1: keyboard height snapping must be exactly representable
    // ------------------------------------------------------------------
    // Contract: snapKeyboardHeightMultiplier returns one of a fixed ladder of
    // values: 0.75, 0.80, ... 1.30. The slider renders it as a percentage.
    // A ladder value that is not exactly representable as a float will render
    // as the percentage BELOW it because of floor/round on a value like 0.84999997.

    private val expectedLadder = listOf(
        0.75f, 0.80f, 0.85f, 0.90f, 0.95f,
        1.00f, 1.05f, 1.10f, 1.15f, 1.20f, 1.25f, 1.30f
    )

    @Test
    fun snapLadder_roundTripsToExactlyItself() {
        for (target in expectedLadder) {
            val snapped = snapKeyboardHeightMultiplier(target)
            // Snapping an exact ladder value must be idempotent.
            assertEquals(
                "snap($target) must be exactly $target",
                target, snapped, 0.0f
            )
        }
    }

    @Test
    fun snapLadder_isIdempotentOnSecondPass() {
        // Feeding a snapped value back in must not move it.
        for (raw in listOf(0.751f, 0.77f, 0.799f, 0.82f, 0.977f, 1.024f, 1.266f, 1.3f)) {
            val once = snapKeyboardHeightMultiplier(raw)
            val twice = snapKeyboardHeightMultiplier(once)
            assertEquals("snap must be idempotent for $raw", once, twice, 0.0f)
        }
    }

    @Test
    fun snapLadder_percentageRenderingIsNotOffByOne() {
        // Reproduce what Typing.kt does: (value * 100).roundToInt()
        val rendered = expectedLadder.map { kotlin.math.round(it * 100.0f).toInt() }
        val expected = listOf(75, 80, 85, 90, 95, 100, 105, 110, 115, 120, 125, 130)
        assertEquals("slider percentage labels", expected, rendered)
    }

    @Test
    fun snapLadder_neverExitsDocumentedRange() {
        // Adversarial sweep across and beyond the whole range.
        var v = -2.0f
        while (v <= 4.0f) {
            val snapped = snapKeyboardHeightMultiplier(v)
            assertTrue(
                "snap($v) produced $snapped which is outside [$MIN_KEYBOARD_HEIGHT_MULTIPLIER, $MAX_KEYBOARD_HEIGHT_MULTIPLIER]",
                snapped >= MIN_KEYBOARD_HEIGHT_MULTIPLIER && snapped <= MAX_KEYBOARD_HEIGHT_MULTIPLIER
            )
            v += 0.001f
        }
    }

    @Test
    fun snapLadder_landsOnTheLadderGridForAdversarialInput() {
        // Every result must be a multiple of the step away from MIN.
        var v = -2.0f
        val step = 0.05f
        while (v <= 4.0f) {
            val snapped = snapKeyboardHeightMultiplier(v)
            val steps = (snapped - MIN_KEYBOARD_HEIGHT_MULTIPLIER) / step
            val residual = kotlin.math.abs(steps - kotlin.math.round(steps))
            assertTrue(
                "snap($v) = $snapped is off-grid by $residual steps",
                residual < 0.001f
            )
            v += 0.001f
        }
    }

    // ------------------------------------------------------------------
    // PROBE 2: NaN / Infinity handling
    // ------------------------------------------------------------------

    @Test
    fun clampHandlesNonFiniteInputs() {
        assertEquals(DEFAULT_KEYBOARD_HEIGHT_MULTIPLIER, clampKeyboardHeightMultiplier(Float.NaN), 0.0f)
        assertEquals(DEFAULT_KEYBOARD_HEIGHT_MULTIPLIER, clampKeyboardHeightMultiplier(Float.POSITIVE_INFINITY), 0.0f)
        assertEquals(DEFAULT_KEYBOARD_HEIGHT_MULTIPLIER, clampKeyboardHeightMultiplier(Float.NEGATIVE_INFINITY), 0.0f)
    }

    @Test
    fun snapHandlesNonFiniteInputs() {
        assertEquals(DEFAULT_KEYBOARD_HEIGHT_MULTIPLIER, snapKeyboardHeightMultiplier(Float.NaN), 0.0f)
        assertEquals(DEFAULT_KEYBOARD_HEIGHT_MULTIPLIER, snapKeyboardHeightMultiplier(Float.POSITIVE_INFINITY), 0.0f)
    }

    @Test
    fun snapHandlesNegativeZero() {
        val snapped = snapKeyboardHeightMultiplier(-0.0f)
        assertEquals(MIN_KEYBOARD_HEIGHT_MULTIPLIER, snapped, 0.0f)
        assertFalse("must not return negative zero", snapped == -0.0f)
    }

    // ------------------------------------------------------------------
    // PROBE 3: version comparison — the update checker
    // ------------------------------------------------------------------

    @Test
    fun isNewerVersion_basicOrdering() {
        assertTrue(UpdateChecker.isNewerVersion("2.0.5", "2.0.4"))
        assertTrue(UpdateChecker.isNewerVersion("2.1.0", "2.0.9"))
        assertTrue(UpdateChecker.isNewerVersion("3.0.0", "2.99.99"))
        assertFalse(UpdateChecker.isNewerVersion("2.0.4", "2.0.5"))
        assertFalse(UpdateChecker.isNewerVersion("2.0.4", "2.0.4"))
    }

    @Test
    fun isNewerVersion_handlesPrefixVersionName() {
        // Release tags carry a leading "v"; the installed versionName does not.
        // extractVersion must strip the prefix so the two compare equal.
        assertFalse(UpdateChecker.isNewerVersion("v2.0.5", "2.0.5"))
        assertTrue(UpdateChecker.isNewerVersion("2.0.6", "2.0.5"))
        assertTrue(UpdateChecker.isNewerVersion("v2.0.6", "2.0.5"))
    }

    /**
     * PROBE: a major-only release tag. The version regex requires at least two
     * dot-separated components, so "v3" falls through to the digit-stripping
     * fallback. Confirm it still yields a comparable version rather than
     * silently reporting "no update available".
     */
    @Test
    fun isNewerVersion_majorOnlyTagIsStillDetected() {
        assertEquals("3", UpdateChecker.extractVersion("v3"))
        assertTrue("a v3 release must be seen as newer than 2.0.5",
            UpdateChecker.isNewerVersion("v3", "2.0.5"))
    }

    @Test
    fun isNewerVersion_emptyAndGarbageInputsDoNotCrash() {
        assertFalse(UpdateChecker.isNewerVersion("", "2.0.5"))
        assertFalse(UpdateChecker.isNewerVersion("2.0.5", ""))
        assertFalse(UpdateChecker.isNewerVersion("abc", "2.0.5"))
        assertFalse(UpdateChecker.isNewerVersion("2.0.5", "abc"))
    }

    /**
     * PROBE: does a multi-digit version component compare numerically?
     *
     * "2.0.10" is a NEWER version than "2.0.9". A lexicographic or
     * per-character comparison gets this wrong.
     */
    @Test
    fun isNewerVersion_multiDigitComponentComparesNumerically() {
        assertTrue(
            "2.0.10 must be newer than 2.0.9",
            UpdateChecker.isNewerVersion("2.0.10", "2.0.9")
        )
        assertTrue(
            "2.10.0 must be newer than 2.9.0",
            UpdateChecker.isNewerVersion("2.10.0", "2.9.0")
        )
        assertFalse(
            "2.0.9 must NOT be newer than 2.0.10",
            UpdateChecker.isNewerVersion("2.0.9", "2.0.10")
        )
    }

    /**
     * PROBE: shorter version interpreted as missing trailing components == 0.
     * "2.1" vs "2.1.0" should be equal (not an update).
     */
    @Test
    fun isNewerVersion_trailingZerosAreEqual() {
        assertFalse(UpdateChecker.isNewerVersion("2.1", "2.1.0"))
        assertFalse(UpdateChecker.isNewerVersion("2.1.0", "2.1"))
        assertTrue(UpdateChecker.isNewerVersion("2.1.1", "2.1"))
    }

    @Test
    fun extractVersion_pullsTheNumericCore() {
        assertEquals("2.0.4", UpdateChecker.extractVersion("Keybird v2.0.4"))
        assertEquals("2.0.4", UpdateChecker.extractVersion("v2.0.4"))
        assertEquals("2.0.4", UpdateChecker.extractVersion("release-2.0.4-stable"))
    }

    @Test
    fun cleanReleaseNotes_stripsMarkdownNoise() {
        val raw = "## What's new\n\n**Bold** and __underline__ text"
        val cleaned = UpdateChecker.cleanReleaseNotes(raw)
        assertFalse("headings must be stripped", cleaned.contains("##"))
        assertFalse("bold markers must be stripped", cleaned.contains("**"))
        assertFalse("underline markers must be stripped", cleaned.contains("__"))
    }

    // ------------------------------------------------------------------
    // PROBE 4: update asset matching must not over-match
    // ------------------------------------------------------------------

    @Test
    fun isMatchingApkAsset_acceptsRealAssetNames() {
        assertTrue(UpdateChecker.isMatchingApkAsset("Keybird-v2.0.5.apk"))
        assertTrue(UpdateChecker.isMatchingApkAsset("keybird-v2.0.4-stable-universal-debug.apk"))
        assertTrue(UpdateChecker.isMatchingApkAsset("iPlus-Keyboard-v2.0.3.apk"))
    }

    @Test
    fun isMatchingApkAsset_rejectsNonApks() {
        assertFalse(UpdateChecker.isMatchingApkAsset("Keybird-v2.0.4.zip"))
        assertFalse(UpdateChecker.isMatchingApkAsset("Keybird-v2.0.4.apk.sha256"))
        assertFalse(UpdateChecker.isMatchingApkAsset("random-other-app.apk"))
        assertFalse(UpdateChecker.isMatchingApkAsset(""))
    }

    /**
     * PROBE (FAILS — real defect): `isMatchingApkAsset` checks
     * `lower.contains("iplus")`, which matches unrelated apps that merely have
     * "iplus" somewhere in the filename. Verified against the real function:
     *   "iplusgames-v1.apk"       -> true
     *   "com.iplus.notes.apk"     -> true
     *   "iplus_wallpaper_hd.apk"  -> true
     * All three would be offered as a keyboard update (and handed to
     * ACTION_VIEW). The matcher needs to be anchored, e.g. require the name to
     * START with the product token rather than merely contain it.
     */
    @Test
    fun isMatchingApkAsset_doesNotMatchForeignAppContainingIplus() {
        assertFalse(
            "an unrelated app must not match",
            UpdateChecker.isMatchingApkAsset("iplusgames-v1.apk")
        )
    }

    /**
     * PROBE (FAILS — real defect): `isMatchingReleaseMetadata` ends with
     * `lowerTag.startsWith("v")`, so ANY release whose tag starts with "v" is
     * treated as this keyboard's release. Verified:
     *   tag "v1.0" title "My Game"    -> true
     *   tag "v9.9" title "some lib"   -> true
     * On a shared/renamed repo this makes the updater offer an unrelated
     * project's artifact.
     */
    @Test
    fun isMatchingReleaseMetadata_doesNotMatchAnyVTaggedRelease() {
        assertFalse(
            "an unrelated v-tagged release must not match",
            UpdateChecker.isMatchingReleaseMetadata("v1.0", "My Game")
        )
        assertFalse(
            "an unrelated v-tagged release must not match",
            UpdateChecker.isMatchingReleaseMetadata("v9.9", "some lib")
        )
    }

    @Test
    fun isMatchingReleaseMetadata_rejectsUnrelatedRelease() {
        assertFalse(UpdateChecker.isMatchingReleaseMetadata("custom-tag", "Other App"))
        assertFalse(UpdateChecker.isMatchingReleaseMetadata("nightly", "Build artifacts"))
    }

    // ------------------------------------------------------------------
    // PROBE 5: one-handed mode state machine
    // ------------------------------------------------------------------

    @Test
    fun oneHandedCycleIsClosedAndTotal() {
        // Cycle must return to the start after 3 steps.
        assertEquals("off", OneHandedModeHelper.nextMode(OneHandedModeHelper.nextMode(OneHandedModeHelper.nextMode("off"))))
    }

    @Test
    fun oneHandedNormalizeIsIdempotent() {
        for (m in listOf(null, "", "off", "left", "right", "LEFT", "Right", "center", "  left  ")) {
            val once = OneHandedModeHelper.normalizeMode(m)
            val twice = OneHandedModeHelper.normalizeMode(once)
            assertEquals("normalize must be idempotent for '$m'", once, twice)
        }
    }

    @Test
    fun oneHandedNormalizeOnlyEverYieldsThreeStates() {
        val allowed = setOf("off", "left", "right")
        for (m in listOf(null, "", "off", "left", "right", "LEFT", "garbage", "0", "true")) {
            assertTrue(
                "normalizeMode('$m') = '${OneHandedModeHelper.normalizeMode(m)}' is not an allowed state",
                OneHandedModeHelper.normalizeMode(m) in allowed
            )
        }
    }

    /**
     * PROBE: oppositeSide of "off". The rail's swap button asks for the
     * opposite of the current side. From "off" there is no opposite; the
     * documented behaviour is to land on "right".
     */
    @Test
    fun oneHandedOppositeSideFromOff() {
        assertEquals("right", OneHandedModeHelper.oppositeSide("off"))
        assertEquals("right", OneHandedModeHelper.oppositeSide(null))
    }

    @Test
    fun oneHandedOppositeIsAnInvolution() {
        assertEquals("left", OneHandedModeHelper.oppositeSide(OneHandedModeHelper.oppositeSide("left")))
        assertEquals("right", OneHandedModeHelper.oppositeSide(OneHandedModeHelper.oppositeSide("right")))
    }

    // ------------------------------------------------------------------
    // PROBE 6: keyboard height clamp inside KeyboardBuilder
    // ------------------------------------------------------------------

    @Test
    fun keyboardBuilderClampIsBoundedAndMonotonic() {
        val display = 2400
        val min = Math.round(display * 0.16f)
        val max = Math.round(display * 0.65f)

        // Sweep the raw height and verify the clamp is monotonic non-decreasing
        // and always inside [min, max].
        var previous = Int.MIN_VALUE
        var raw = -5000
        while (raw <= 8000) {
            val clamped = org.futo.inputmethod.keyboard.internal.KeyboardBuilder
                .clampScaledKeyboardHeight(raw, display)
            assertTrue(
                "clamp($raw) = $clamped is outside [$min, $max]",
                clamped in min..max
            )
            assertTrue(
                "clamp is not monotonic at raw=$raw ($previous -> $clamped)",
                clamped >= previous
            )
            previous = clamped
            raw += 7
        }
    }

    @Test
    fun keyboardBuilderClampEdgeCaseForInvalidDisplay() {
        // displayHeight <= 0 => no display information, must still return >= 1.
        assertEquals(1, org.futo.inputmethod.keyboard.internal.KeyboardBuilder.clampScaledKeyboardHeight(-10, 0))
        assertEquals(1, org.futo.inputmethod.keyboard.internal.KeyboardBuilder.clampScaledKeyboardHeight(0, 0))
        assertEquals(500, org.futo.inputmethod.keyboard.internal.KeyboardBuilder.clampScaledKeyboardHeight(500, 0))
        assertEquals(1, org.futo.inputmethod.keyboard.internal.KeyboardBuilder.clampScaledKeyboardHeight(0, -100))
    }

    /**
     * PROBE: a 320x240 legacy device. displayHeight = 240.
     * min = round(240*0.16) = 38, max = round(240*0.65) = 156.
     * The default keyboard is 292dp which on a 1.0 density device is 292px.
     * It must clamp down to 156, not overflow the screen.
     */
    @Test
    fun keyboardBuilderClampOnTinyLegacyScreen() {
        assertEquals(156, org.futo.inputmethod.keyboard.internal.KeyboardBuilder.clampScaledKeyboardHeight(292, 240))
    }
}
