package org.futo.inputmethod.latin.uix.presets_probe

/**
 * PresetCodec — a TEST-ONLY faithful replica of the custom-photo-preset
 * serialization that lives inline in ThemePicker.kt (lines 622-630, 650-669,
 * 731-735).
 *
 * IMPORTANT: this copy deliberately does NOT call `android.net.Uri.encode` /
 * `Uri.decode`. Those are Android platform methods, and under
 * `testOptions.unitTests.returnDefaultValues = true` they are stubs that return
 * null, which would make every round-trip test meaningless. Instead the codec
 * takes pre-encoded strings, so the *parsing contract* can be verified on real
 * logic. The percent-encoding behaviour itself is verified by reading
 * production source, not by these tests.
 *
 * Format mirrored from production:
 *
 *   encode: parts.joinToString("|"), name and uri percent-encoded first
 *   decode: record.split("|"), requires exactly 7 parts,
 *           numerics via toFloatOrNull() with defaults 0.33/0.0/1.0/0.0/0.0
 */
object PresetCodec {

    data class Decoded(
        val name: String,
        val uri: String,
        val dim: Float,
        val blur: Float,
        val zoom: Float,
        val x: Float,
        val y: Float
    )

    /** Builds a record from already-percent-encoded name/uri. */
    fun encode(
        encodedName: String,
        encodedUri: String,
        dim: Float,
        blur: Float,
        zoom: Float,
        x: Float,
        y: Float
    ): String = listOf(
        encodedName,
        encodedUri,
        dim.toString(),
        blur.toString(),
        zoom.toString(),
        x.toString(),
        y.toString()
    ).joinToString("|")

    /** Returns null when the record does not have exactly 7 parts. */
    fun decode(record: String): Decoded? {
        if (record.isEmpty()) return null
        val parts = record.split("|")
        if (parts.size != 7) return null
        return Decoded(
            name = parts[0],
            uri = parts[1],
            dim = parts[2].toFloatOrNull() ?: 0.33f,
            blur = parts[3].toFloatOrNull() ?: 0.0f,
            zoom = parts[4].toFloatOrNull() ?: 1.0f,
            x = parts[5].toFloatOrNull() ?: 0.0f,
            y = parts[6].toFloatOrNull() ?: 0.0f
        )
    }
}
