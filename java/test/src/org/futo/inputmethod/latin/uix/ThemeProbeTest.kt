package org.futo.inputmethod.latin.uix

import org.futo.inputmethod.latin.uix.presets_probe.PresetCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ThemeProbeTest — verification of the custom-photo preset record format.
 *
 * NOTE ON SCOPE: `android.net.Uri.encode` is a platform stub in JVM unit tests
 * (`testOptions.unitTests.returnDefaultValues = true` makes it return null), so
 * the encode/decode round trip cannot be exercised here. What CAN be verified
 * is the *parsing contract* the reader depends on, and the set-algebra that
 * delete/rename use. Those are covered below and they run entirely on real,
 * non-stubbed logic.
 *
 * The production record layout (ThemePicker.kt:622-630) is:
 *
 *   enc(name) | enc(uri) | dim | blur | zoom | x | y      (exactly 7 fields)
 *
 * and the reader (ThemePicker.kt:650-669) does `record.split("|")` then
 * `if (parts.size == 7)`. A record that does not split into exactly 7 fields is
 * silently skipped — the preset becomes invisible with no error.
 */
class ThemeProbeTest {

    // ------------------------------------------------------------------
    // PROBE 1: numeric round-trip through the record
    // ------------------------------------------------------------------

    @Test
    fun numericFieldsRoundTripThroughTheRecord() {
        val record = listOf("theme", "content://x/1", "0.85", "20.0", "2.5", "-1.0", "1.0")
            .joinToString("|")
        val parts = record.split("|")
        assertEquals(7, parts.size)
        assertEquals(0.85f, parts[2].toFloatOrNull() ?: Float.NaN, 0.0f)
        assertEquals(20f, parts[3].toFloatOrNull() ?: Float.NaN, 0.0f)
        assertEquals(2.5f, parts[4].toFloatOrNull() ?: Float.NaN, 0.0f)
        assertEquals(-1f, parts[5].toFloatOrNull() ?: Float.NaN, 0.0f)
        assertEquals(1f, parts[6].toFloatOrNull() ?: Float.NaN, 0.0f)
    }

    /**
     * PROBE: Float.toString() emits "NaN"/"Infinity", and Kotlin's
     * `toFloatOrNull()` DOES parse those back to the non-finite values.
     * Therefore a saved preset carrying a non-finite numeric field round-trips
     * as non-finite — it never hits the reader's `?:` default fallback.
     *
     * CRITICAL FINDING: `Float.coerceIn` does NOT sanitise NaN. Kotlin's
     * coerceIn is `if (this < min) min else if (this > max) max else this`, and
     * every comparison against NaN is false, so NaN passes straight through.
     * The coerceIn calls in `loadCustomPhotoBackground`
     * (`zoom.coerceIn(1.0f, 2.5f)` at BasicThemeProvider.kt:166,
     * `offsetX.coerceIn(-1f, 1f)` at :169) therefore do NOT protect against a
     * NaN zoom/offset. The clampKeyboardHeightMultiplier helper gets this right
     * via an explicit `isFinite()` guard; the photo path has no such guard.
     */
    @Test
    fun nanSurvivesCoerceIn() {
        assertEquals(Float.NaN, "NaN".toFloatOrNull() ?: Float.NaN, 0.0f)

        // Document the trap: coerceIn is NOT a NaN sanitiser.
        val nanZoom = "NaN".toFloatOrNull() ?: 1.0f
        assertEquals("coerceIn passes NaN through untouched", Float.NaN, nanZoom.coerceIn(1.0f, 2.5f), 0.0f)

        // Infinity IS clamped, because comparisons with infinity are meaningful.
        val infZoom = "Infinity".toFloatOrNull() ?: 1.0f
        assertEquals("Infinity clamps to the max bound", 2.5f, infZoom.coerceIn(1.0f, 2.5f), 0.0f)
    }

    /**
     * PROBE: the reader's exact fallback values. If a numeric field is corrupt,
     * these are what the user silently gets. Confirm 0.33f is the dim default
     * and 1.0f is the zoom default, because a zoom fallback of 0.0f would render
     * an invisible background.
     */
    @Test
    fun corruptNumericsFallBackToSafeDefaults() {
        val parts = listOf("theme", "content://x/1", "xx", "yy", "zz", "ww", "vv").toTypedArray()
        val dim = parts[2].toFloatOrNull() ?: 0.33f
        val blur = parts[3].toFloatOrNull() ?: 0.0f
        val zoom = parts[4].toFloatOrNull() ?: 1.0f
        val x = parts[5].toFloatOrNull() ?: 0.0f
        val y = parts[6].toFloatOrNull() ?: 0.0f

        assertEquals(0.33f, dim, 0.0f)
        assertEquals(0.0f, blur, 0.0f)
        assertEquals("zoom fallback must be 1.0, never 0.0 (would render empty)", 1.0f, zoom, 0.0f)
        assertEquals(0.0f, x, 0.0f)
        assertEquals(0.0f, y, 0.0f)
    }

    // ------------------------------------------------------------------
    // PROBE 2: malformed records are skipped, not crashy
    // ------------------------------------------------------------------

    @Test
    fun recordsWithWrongFieldCountAreSkipped() {
        // Reproduce ThemePicker.kt:652 `if (parts.size == 7)`
        fun accepted(record: String) = record.split("|").size == 7

        assertEquals("too few fields", false, accepted("too|few|fields"))
        assertEquals("too many fields", false, accepted("a|b|c|d|e|f|g|h"))
        assertEquals("empty string", false, accepted(""))
        assertEquals("exactly seven", true, accepted("a|b|c|d|e|f|g"))
    }

    // ------------------------------------------------------------------
    // PROBE 3: set algebra used by delete and rename
    // ------------------------------------------------------------------

    /**
     * PROBE: delete is `savedThemes.value - record` on a Set, and save is
     * `savedThemes.value + record`. Two presets with the same name AND the same
     * settings produce the identical record string, so the Set collapses them —
     * the user "saves" a second preset and the first silently disappears.
     */
    @Test
    fun identicalPresetsCollapseInTheSet() {
        val r1 = "same|content://x/1|0.5|0.0|1.0|0.0|0.0"
        val r2 = "same|content://x/1|0.5|0.0|1.0|0.0|0.0"
        assertEquals("identical records collapse into one Set element", 1, (setOf(r1) + r2).size)
    }

    @Test
    fun differentlyNamedPresetsStayDistinct() {
        val r1 = "one|content://x/1|0.5|0.0|1.0|0.0|0.0"
        val r2 = "two|content://x/1|0.5|0.0|1.0|0.0|0.0"
        assertEquals(2, (setOf(r1) + r2).size)
    }

    /**
     * PROBE (documents a real data-loss path): rename is
     * `(savedThemes.value - old) + renamed` (ThemePicker.kt:735). If `renamed`
     * happens to equal an already-existing record, the Set absorbs it and the
     * preset count drops — the user renames "b" to "a" and loses one preset
     * with no warning. The UI does not check for a name collision first.
     */
    @Test
    fun renamingOntoAnExistingRecordLosesAPreset() {
        val a = "a|content://x/1|0.5|0.0|1.0|0.0|0.0"
        val b = "b|content://x/1|0.5|0.0|1.0|0.0|0.0"
        val saved = setOf(a, b)

        // Rename b -> a (same image, same settings, name "a")
        val renamedBtoA = "a|content://x/1|0.5|0.0|1.0|0.0|0.0"
        val after = (saved - b) + renamedBtoA

        assertEquals("renaming b to a silently drops a preset", 1, after.size)
        assertEquals("and the surviving preset is the one that was already there", a, after.first())
    }

    // ------------------------------------------------------------------
    // PROBE 4: the name input cap
    // ------------------------------------------------------------------

    @Test
    fun nameIsCappedAt32Chars() {
        // Mirrors `onValueChange = { newThemeName.value = it.take(32) }`
        val typed = "x".repeat(100)
        assertEquals(32, typed.take(32).length)
    }

    /**
     * PROBE: `take(32)` counts UTF-16 code units. With emoji names the cut can
     * land between a high and low surrogate, leaving a lone surrogate that is
     * then handed to Uri.encode.
     *
     * Note `String.length` already counts UTF-16 units while
     * `codePointCount` counts whole code points, so the two disagree after a
     * mid-pair cut — which is exactly how the lone surrogate is detected here.
     */
    @Test
    fun takeCanSplitASurrogatePair() {
        val emoji = "\uD83D\uDE00"           // one code point, two UTF-16 units
        val name = "x" + emoji.repeat(20)     // 1 + 40 = 41 units, odd offset
        val capped = name.take(32)

        assertEquals("32 UTF-16 units", 32, capped.length)
        // With the leading "x", the 32nd unit is a high surrogate with no low.
        assertEquals("the cut lands on a high surrogate with no low to follow",
            true, Character.isHighSurrogate(capped.last()))
        assertEquals("16 whole code points survive, plus one lone surrogate",
            17, capped.codePointCount(0, capped.length))
    }

    // ------------------------------------------------------------------
    // PROBE 5: PresetCodec contract (test-local mirror)
    // ------------------------------------------------------------------

    @Test
    fun presetCodecRejectsWrongFieldCount() {
        assertNull(PresetCodec.decode("too|few|fields"))
        assertNull(PresetCodec.decode("a|b|c|d|e|f|g|h"))
        assertNull(PresetCodec.decode(""))
    }

    @Test
    fun presetCodecAppliesDocumentedDefaults() {
        val d = PresetCodec.decode("t|content://x/1|xx|yy|zz|ww|vv")
        assertNotNull(d)
        assertEquals(0.33f, d!!.dim, 0.0f)
        assertEquals(0.0f, d.blur, 0.0f)
        assertEquals(1.0f, d.zoom, 0.0f)
        assertEquals(0.0f, d.x, 0.0f)
        assertEquals(0.0f, d.y, 0.0f)
    }
}
