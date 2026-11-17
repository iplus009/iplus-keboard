package org.futo.inputmethod.latin.themestore.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Pure-JVM codec for `.iplustheme` ZIP bundles: SHA-256 verification, ZIP-slip-safe
 * asset extraction, and inline manifest parsing. No Android dependencies so it is
 * directly unit-testable on the JVM and reusable from both the repository (download)
 * and the visual delegate (render).
 */
object ArtBundleCodec {
    /** Hard cap per extracted asset to prevent decompression bombs. */
    const val MAX_ASSET_BYTES = 16 * 1024 * 1024

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Returns true only when the computed SHA-256 matches the (hex, case-insensitive) expectation. */
    fun validateBundleSha256(bytes: ByteArray, expectedSha: String): Boolean {
        if (expectedSha.isBlank()) return false
        return sha256Hex(bytes).equals(expectedSha.trim().lowercase(), ignoreCase = true)
    }

    /** Heuristic: a ZIP container starts with the local-file-header magic `PK\x03\x04`. */
    fun isLikelyZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 'P'.code.toByte() &&
            bytes[1] == 'K'.code.toByte() &&
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()

    /**
     * Normalizes a bundle-relative path and rejects traversal/absolute paths.
     * Returns the safe forward-slash path, or null when the path is unsafe.
     */
    fun sanitizeAssetPath(path: String): String? {
        val raw = path.trim().replace('\\', '/')
        if (raw.isEmpty() || raw.startsWith("/") || raw.contains("..")) return null
        val segments = raw.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) return null
        return segments.joinToString("/")
    }

    /** Lists non-directory entry names without reading their contents. */
    fun listEntries(zipBytes: ByteArray): List<String> = runCatching {
        val out = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (!e.isDirectory) out.add(e.name)
                e = z.nextEntry
            }
        }
        out
    }.getOrDefault(emptyList())

    /**
     * Extracts a single `assets/...` entry. Returns null when the path is unsafe,
     * missing, oversized, or the bundle is not a valid ZIP.
     */
    fun extractAsset(zipBytes: ByteArray, assetPath: String): ByteArray? {
        val wanted = sanitizeAssetPath(assetPath) ?: return null
        if (!wanted.startsWith("assets/")) return null
        return runCatching {
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (!e.isDirectory && e.name == wanted) {
                        return@use readBounded(z, e.size)
                    }
                    e = z.nextEntry
                }
                null
            }
        }.getOrNull()
    }

    /** Extracts the root `manifest.json` from a bundle, or null. */
    fun extractManifestJson(zipBytes: ByteArray): String? = runCatching {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (!e.isDirectory && e.name == "manifest.json") {
                    return@use readBounded(z, e.size)?.toString(Charsets.UTF_8)
                }
                e = z.nextEntry
            }
            null
        }
    }.getOrNull()

    private fun readBounded(z: ZipInputStream, declaredSize: Long): ByteArray? {
        val initial = if (declaredSize in 1..MAX_ASSET_BYTES.toLong()) declaredSize.toInt() else 64 * 1024
        val out = ByteArrayOutputStream(initial)
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = z.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_ASSET_BYTES) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
