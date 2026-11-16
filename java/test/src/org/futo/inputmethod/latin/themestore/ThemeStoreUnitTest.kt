package org.futo.inputmethod.latin.themestore

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.futo.inputmethod.latin.themestore.domain.StoreCatalogFilter
import org.futo.inputmethod.latin.themestore.engine.ThemeManifestEngine
import org.futo.inputmethod.latin.themestore.model.CatalogThemeItem
import org.futo.inputmethod.latin.themestore.model.EntitlementReceiptPayload
import org.futo.inputmethod.latin.themestore.model.SignedEntitlementReceipt
import org.futo.inputmethod.latin.themestore.model.SignedRevocationList
import org.futo.inputmethod.latin.themestore.model.StoreThemeState
import org.futo.inputmethod.latin.themestore.model.ThemeFilter
import org.futo.inputmethod.latin.themestore.security.Ed25519Verifier
import org.futo.inputmethod.latin.themestore.security.ThemeLicenseGuard
import org.futo.inputmethod.latin.themestore.security.ThemeNativeGuard
import org.futo.inputmethod.latin.themestore.storage.InstalledThemeRecord
import org.futo.inputmethod.latin.themestore.storage.ThemeVaultCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import javax.crypto.KeyGenerator

class ThemeStoreUnitTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun ed25519_verifiesRfc8032Vector1_andRejectsTamper() {
        val publicKey = Ed25519Verifier.hexToBytes(
            "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"
        )
        val signature = Ed25519Verifier.hexToBytes(
            "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155" +
                "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b"
        )
        assertTrue(Ed25519Verifier.verify(publicKey, ByteArray(0), signature))

        val badSig = signature.copyOf()
        badSig[0] = (badSig[0].toInt() xor 0x01).toByte()
        assertFalse(Ed25519Verifier.verify(publicKey, ByteArray(0), badSig))
        assertFalse(Ed25519Verifier.verify(publicKey, byteArrayOf(0x01), signature))
    }

    @Test
    fun ed25519_verifiesLiveWorkerSignedRevocationPayload() {
        val list = SignedRevocationList(
            ok = true,
            v = 1,
            generatedAt = 1794845463L,
            entries = emptyList(),
            payloadB64 = "eyJ2IjoxLCJnZW5lcmF0ZWRfYXQiOjE3OTQ4NDU0NjMsImVudHJpZXMiOltdfQ",
            signatureB64 = "ygxsQShqnretfnlfshKLuOPMcFw9cyviPbIX8QWkCdgwjEswpi82BHjFHMTWSvZjtwbcakRrI8XMyA0iCXhtDQ"
        )
        val verified = ThemeLicenseGuard.verifyRevocationList(
            list = list,
            publicKey32 = ThemeNativeGuard.trustedEd25519PublicKeyBytes()
        )
        assertNotNull("Expected live Cloudflare Worker revocation signature to verify", verified)
        assertEquals(0, verified!!.size)
    }

    @Test
    fun receiptVerification_enforcesAppIdDeviceHashTtlAndRevocation() {
        val kpg = KeyPairGenerator.getInstance("Ed25519")
        val kp = kpg.generateKeyPair()
        val x509Encoded = kp.public.encoded
        // X.509 SubjectPublicKeyInfo for Ed25519 ends with the raw 32-byte public key.
        val rawPub32 = x509Encoded.copyOfRange(x509Encoded.size - 32, x509Encoded.size)

        fun signPayload(payload: EntitlementReceiptPayload): SignedEntitlementReceipt {
            val payloadBytes = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
            val sigBytes = Signature.getInstance("Ed25519").run {
                initSign(kp.private)
                update(payloadBytes)
                sign()
            }
            val encoder = Base64.getUrlEncoder().withoutPadding()
            return SignedEntitlementReceipt(
                payload = payload,
                payloadB64 = encoder.encodeToString(payloadBytes),
                signatureB64 = encoder.encodeToString(sigBytes)
            )
        }

        val basePayload = EntitlementReceiptPayload(
            v = 1,
            appId = ThemeLicenseGuard.APP_ID,
            mode = "single",
            themeIds = listOf("kb_cyber_crimson"),
            deviceHash = "device_alpha_hash",
            keyId = "key_001",
            issuedAt = 1_700_000_000L,
            ttlDays = 30
        )
        val receipt = signPayload(basePayload)

        // 1. Valid receipt passes & covers granted theme only
        val valid = ThemeLicenseGuard.verifyReceipt(
            receipt = receipt,
            expectedDeviceHash = "device_alpha_hash",
            revokedTargetHashes = emptySet(),
            publicKey32 = rawPub32,
            nowEpochSec = 1_700_100_000L
        )
        assertNotNull(valid)
        assertTrue(ThemeLicenseGuard.verifyCoversTheme(valid!!, "kb_cyber_crimson"))
        assertFalse(ThemeLicenseGuard.verifyCoversTheme(valid, "kb_royal_gold"))

        // 2. Device clone attack rejected
        assertNull(
            ThemeLicenseGuard.verifyReceipt(
                receipt = receipt,
                expectedDeviceHash = "cloned_device_hash",
                publicKey32 = rawPub32,
                nowEpochSec = 1_700_100_000L
            )
        )

        // 3. Cross-app receipt reuse rejected (LemonSqueezy-class vulnerability)
        val foreignReceipt = signPayload(basePayload.copy(appId = "kunjodrive"))
        assertNull(
            ThemeLicenseGuard.verifyReceipt(
                receipt = foreignReceipt,
                expectedDeviceHash = "device_alpha_hash",
                publicKey32 = rawPub32,
                nowEpochSec = 1_700_100_000L
            )
        )

        // 4. Expired offline TTL rejected
        assertNull(
            ThemeLicenseGuard.verifyReceipt(
                receipt = receipt,
                expectedDeviceHash = "device_alpha_hash",
                publicKey32 = rawPub32,
                nowEpochSec = 1_700_000_000L + 31L * 86400L
            )
        )

        // 5. Revoked key_id or device_hash rejected offline
        assertNull(
            ThemeLicenseGuard.verifyReceipt(
                receipt = receipt,
                expectedDeviceHash = "device_alpha_hash",
                revokedTargetHashes = setOf(ThemeLicenseGuard.sha256Hex("key_001")),
                publicKey32 = rawPub32,
                nowEpochSec = 1_700_100_000L
            )
        )
        assertNull(
            ThemeLicenseGuard.verifyReceipt(
                receipt = receipt,
                expectedDeviceHash = "device_alpha_hash",
                revokedTargetHashes = setOf("device_alpha_hash"),
                publicKey32 = rawPub32,
                nowEpochSec = 1_700_100_000L
            )
        )
    }

    @Test
    fun base64UrlDecode_matchesJavaEncoder() {
        val sample = "Keybird Theme Hub © 2026".toByteArray(Charsets.UTF_8)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(sample)
        val decoded = ThemeLicenseGuard.decodeBase64Url(encoded)
        assertArrayEquals(sample, decoded)
    }

    @Test
    fun vaultCrypto_roundTripsValidAad_andRejectsWrongDeviceOrTamper() {
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val secretKey = keyGen.generateKey()

        val plaintext = """{"theme_id":"kb_royal_gold","title":"Royal Gold"}""".toByteArray(Charsets.UTF_8)
        val aadDeviceA = ThemeVaultCrypto.buildAad("keybird", "kb_royal_gold", "dev_a")
        val aadDeviceB = ThemeVaultCrypto.buildAad("keybird", "kb_royal_gold", "dev_b")

        val encrypted = ThemeVaultCrypto.encrypt(plaintext, aadDeviceA, keyOverride = secretKey)
        assertArrayEquals(
            plaintext,
            ThemeVaultCrypto.decrypt(encrypted, aadDeviceA, keyOverride = secretKey)
        )

        // Copying encrypted vault file to another device must fail GCM authentication
        assertNull(ThemeVaultCrypto.decrypt(encrypted, aadDeviceB, keyOverride = secretKey))

        // Bit-flipping ciphertext must fail GCM authentication
        val tampered = encrypted.copyOf()
        tampered[tampered.lastIndex] = (tampered[tampered.lastIndex].toInt() xor 0xFF).toByte()
        assertNull(ThemeVaultCrypto.decrypt(tampered, aadDeviceA, keyOverride = secretKey))
    }

    @Test
    fun manifestEngine_parsesStarterAndArtPack_andComputesYiqContrast() {
        val rawJson = """
            {
              "schema": 1,
              "theme_id": "kb_cyber_crimson",
              "app_id": "keybird",
              "title": "Cyber Crimson",
              "version": 2,
              "theme_type": "gradient",
              "is_light": false,
              "colors": {
                "primary": "#FF1744",
                "background": "#0A0A0F"
              },
              "keyboard": {
                "bg_type": "gradient",
                "bg_colors": ["#0A0A0F", "#240814"],
                "key_bg": "#1B1520",
                "enter_key_bg": "#FF1744",
                "key_corner_radius_dp": 12.0,
                "font_style": "tech_mono"
              },
              "art_pack": {
                "keymap": { "k_a": "assets/keys/k_a.webp" },
                "group_fallbacks": { "letter": "assets/groups/letter.webp" },
                "scrim_mode": "auto_yiq"
              }
            }
        """.trimIndent()

        val manifest = ThemeManifestEngine.parseManifest(rawJson)
        assertNotNull(manifest)
        val profile = ThemeManifestEngine.toRenderProfile(manifest!!)
        assertEquals("kb_cyber_crimson", profile.themeId)
        assertEquals(2, profile.bgGradientColors.size)
        assertEquals(0xFFFF1744.toInt(), profile.enterKeyBgColor)
        assertEquals("tech_mono", profile.fontStyle)
        assertEquals("assets/keys/k_a.webp", manifest.artPack?.keymap?.get("k_a"))

        // YIQ contrast check: dark text on white key, white text on black key
        assertEquals(0xFF121212.toInt(), ThemeManifestEngine.yiqContrastTextColor(0xFFFFFFFF.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), ThemeManifestEngine.yiqContrastTextColor(0xFF000000.toInt()))
    }

    @Test
    fun storeCatalogFilter_assignsFourStatesAndFiltersCleanly() {
        val catalog = listOf(
            CatalogThemeItem(themeId = "kb_emerald_night", title = "Emerald Night", category = "dark", isPaid = 0, currentVersion = 1),
            CatalogThemeItem(themeId = "kb_paper_light", title = "Paper Light", category = "light", isPaid = 0, currentVersion = 2),
            CatalogThemeItem(themeId = "kb_cyber_crimson", title = "Cyber Crimson", category = "neon", isPaid = 1, priceBdtPaisa = 5000, currentVersion = 1),
            CatalogThemeItem(themeId = "kb_royal_gold", title = "Royal Gold", category = "luxury", isPaid = 1, priceBdtPaisa = 8000, currentVersion = 3)
        )
        val installed = mapOf(
            "kb_paper_light" to InstalledThemeRecord("kb_paper_light", 1, false, "/tmp/p.json", "abc", false, 100L),
            "kb_royal_gold" to InstalledThemeRecord("kb_royal_gold", 3, true, "/tmp/r.vault", "def", true, 100L)
        )

        val entries = StoreCatalogFilter.buildEntries(
            catalog = catalog,
            installed = installed,
            ownedThemeIds = setOf("kb_cyber_crimson", "kb_royal_gold"),
            hasFullAccess = false
        ).associateBy { it.themeId }

        assertEquals(StoreThemeState.FREE, entries["kb_emerald_night"]!!.state)
        assertEquals(StoreThemeState.DOWNLOADED, entries["kb_paper_light"]!!.state)
        assertTrue(entries["kb_paper_light"]!!.hasUpdate)
        assertEquals(StoreThemeState.OWNED, entries["kb_cyber_crimson"]!!.state)
        assertEquals(StoreThemeState.DOWNLOADED, entries["kb_royal_gold"]!!.state)
        assertFalse(entries["kb_royal_gold"]!!.hasUpdate)

        val paidOnly = StoreCatalogFilter.applyFilter(entries.values.toList(), ThemeFilter.PAID, "")
        assertEquals(setOf("kb_cyber_crimson", "kb_royal_gold"), paidOnly.map { it.themeId }.toSet())

        val queryGold = StoreCatalogFilter.applyFilter(entries.values.toList(), ThemeFilter.ALL, "gold")
        assertEquals(listOf("kb_royal_gold"), queryGold.map { it.themeId })
    }
}
