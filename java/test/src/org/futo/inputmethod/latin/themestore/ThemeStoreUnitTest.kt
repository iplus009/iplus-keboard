package org.futo.inputmethod.latin.themestore

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.themestore.domain.StoreCatalogFilter
import org.futo.inputmethod.latin.themestore.engine.ArtBundleCodec
import org.futo.inputmethod.latin.themestore.engine.LabelScript
import org.futo.inputmethod.latin.themestore.engine.StoreFontEngine
import org.futo.inputmethod.latin.themestore.engine.ThemeManifestEngine
import org.futo.inputmethod.latin.themestore.model.CatalogThemeItem
import org.futo.inputmethod.latin.themestore.model.EntitlementReceiptPayload
import org.futo.inputmethod.latin.themestore.model.ProductTabFilter
import org.futo.inputmethod.latin.themestore.model.SignedEntitlementReceipt
import org.futo.inputmethod.latin.themestore.model.SignedRevocationList
import org.futo.inputmethod.latin.themestore.model.StoreThemeState
import org.futo.inputmethod.latin.themestore.model.ThemeFilter
import org.futo.inputmethod.latin.themestore.network.SupabaseAuthClient
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
import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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

        val neonCat = StoreCatalogFilter.applyFilter(entries.values.toList(), ThemeFilter.ALL, "", "neon")
        assertEquals(listOf("kb_cyber_crimson"), neonCat.map { it.themeId })
    }

    private fun buildZip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            entries.forEach { (name, data) ->
                z.putNextEntry(ZipEntry(name))
                z.write(data)
                z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    @Test
    fun resolveArtAssetKey_matchesLettersShiftSlotsAndGroupFallbacks() {
        val keymap = mapOf(
            "k_a" to "assets/keys/k_a_ace.webp",
            "k_l" to "assets/keys/k_l_luffy.webp",
            "k_z" to "assets/keys/k_z_zoro.webp",
            "k_shift" to "assets/keys/k_shift_shanks.webp",
            "slot_r1_c0" to "assets/keys/k_bangla1.webp",
            "slot_r2_c3" to "assets/keys/k_bangla2.webp"
        )
        val fallbacks = mapOf(
            "letter" to "assets/groups/letter_glass_ocean.webp",
            "functional" to "assets/groups/functional_glass.webp",
            "accent" to "assets/groups/accent_gold_glass.webp",
            "space" to "assets/keys/k_space_crew_banner.webp",
            "default" to "assets/groups/letter_glass_ocean.webp"
        )

        // English letter keys resolve via k_<letter> (A = Ace, L = Luffy, Z = Zoro)
        assertEquals("assets/keys/k_a_ace.webp",
            ThemeManifestEngine.resolveArtAssetKey(keymap, fallbacks, 'a'.code, "a", 0, 0, 100, 100))
        assertEquals("assets/keys/k_l_luffy.webp",
            ThemeManifestEngine.resolveArtAssetKey(keymap, fallbacks, 'l'.code, "l", 0, 0, 100, 100))
        assertEquals("assets/keys/k_z_zoro.webp",
            ThemeManifestEngine.resolveArtAssetKey(keymap, fallbacks, 'z'.code, "z", 0, 0, 100, 100))

        // Shift resolves via code, not label
        assertEquals("assets/keys/k_shift_shanks.webp",
            ThemeManifestEngine.resolveArtAssetKey(keymap, fallbacks, Constants.CODE_SHIFT, null, 0, 0, 100, 100))

        // Bangla Avro/Probhat keys resolve via slot_r<row>_c<col> (label is a Bengali letter)
        assertEquals("assets/keys/k_bangla1.webp",
            ThemeManifestEngine.resolveArtAssetKey(keymap, fallbacks, 'ক'.code, "ক", 0, 100, 100, 100))
        assertEquals("assets/keys/k_bangla2.webp",
            ThemeManifestEngine.resolveArtAssetKey(keymap, fallbacks, 'ক'.code, "ক", 300, 200, 100, 100))

        // Unknown letter falls back to group_fallbacks["letter"]
        assertEquals("assets/groups/letter_glass_ocean.webp",
            ThemeManifestEngine.resolveArtAssetKey(keymap, fallbacks, '?'.code, "?", 0, 0, 100, 100))

        // Unknown group (accent) falls back to group_fallbacks["accent"]
        assertEquals("assets/groups/accent_gold_glass.webp",
            ThemeManifestEngine.resolveArtAssetKey(keymap, fallbacks, Constants.CODE_ENTER, null, 0, 0, 100, 100))

        // Missing group falls back to "default"
        assertEquals("assets/groups/letter_glass_ocean.webp",
            ThemeManifestEngine.resolveArtAssetKey(
                keymap,
                mapOf("default" to "assets/groups/letter_glass_ocean.webp"),
                'x'.code, "x", 0, 0, 100, 100
            ))
    }

    @Test
    fun onePieceArtPack_matchesAlphabetToCharacterRule() {
        // Mirrors kb_one_piece_master manifest: every k_<letter> portrait's name starts with that letter.
        val keymap = mapOf(
            "k_q" to "assets/keys/k_q_queen.webp",
            "k_w" to "assets/keys/k_w_whitebeard.webp",
            "k_e" to "assets/keys/k_e_enel.webp",
            "k_r" to "assets/keys/k_r_robin.webp",
            "k_t" to "assets/keys/k_t_trafalgar_law.webp",
            "k_y" to "assets/keys/k_y_yamato.webp",
            "k_u" to "assets/keys/k_u_usopp.webp",
            "k_i" to "assets/keys/k_i_imu.webp",
            "k_o" to "assets/keys/k_o_oden.webp",
            "k_p" to "assets/keys/k_p_perona.webp",
            "k_a" to "assets/keys/k_a_ace.webp",
            "k_s" to "assets/keys/k_s_sanji.webp",
            "k_d" to "assets/keys/k_d_doflamingo.webp",
            "k_f" to "assets/keys/k_f_franky.webp",
            "k_g" to "assets/keys/k_g_garp.webp",
            "k_h" to "assets/keys/k_h_hancock.webp",
            "k_j" to "assets/keys/k_j_jinbe.webp",
            "k_k" to "assets/keys/k_k_kaido.webp",
            "k_l" to "assets/keys/k_l_luffy.webp",
            "k_z" to "assets/keys/k_z_zoro.webp",
            "k_x" to "assets/keys/k_x_x_drake.webp",
            "k_c" to "assets/keys/k_c_chopper.webp",
            "k_v" to "assets/keys/k_v_vivi.webp",
            "k_b" to "assets/keys/k_b_brook.webp",
            "k_n" to "assets/keys/k_n_nami.webp",
            "k_m" to "assets/keys/k_m_mihawk.webp"
        )
        keymap.forEach { (keyId, assetPath) ->
            if (keyId.length != 2 || !keyId[1].isLetter()) return@forEach
            val letter = keyId[1]
            val basename = assetPath.substringAfterLast('/')
            val prefix = "k_${letter}_"
            assertTrue(
                "Expected $keyId asset '$basename' to start with '$prefix'",
                basename.startsWith(prefix)
            )
            val characterToken = basename.removePrefix(prefix).substringBefore('.')
            assertTrue(
                "Letter-match violated for $keyId: character '$characterToken' must start with '${letter.uppercase()}'",
                characterToken.isNotEmpty() && characterToken[0].uppercaseChar() == letter.uppercaseChar()
            )
        }
        // Explicit A/Ace, L/Luffy, Z/Zoro spot-checks per the mandate.
        assertEquals("assets/keys/k_a_ace.webp", keymap["k_a"])
        assertEquals("assets/keys/k_l_luffy.webp", keymap["k_l"])
        assertEquals("assets/keys/k_z_zoro.webp", keymap["k_z"])
    }

    @Test
    fun artBundleCodec_verifiesShaAndRejectsMismatch() {
        val bytes = "Keybird iplustheme bundle bytes".toByteArray(Charsets.UTF_8)
        val sha = ArtBundleCodec.sha256Hex(bytes)
        assertEquals(64, sha.length)
        assertTrue(ArtBundleCodec.validateBundleSha256(bytes, sha))
        assertTrue(ArtBundleCodec.validateBundleSha256(bytes, sha.uppercase()))
        assertFalse(ArtBundleCodec.validateBundleSha256(bytes, "0".repeat(64)))
        assertFalse(ArtBundleCodec.validateBundleSha256(bytes, ""))
        assertFalse(ArtBundleCodec.validateBundleSha256("tampered".toByteArray(), sha))
        assertFalse(ArtBundleCodec.isLikelyZip(bytes))
    }

    @Test
    fun artBundleCodec_sanitizesPaths_andBlocksZipSlip() {
        assertNull(ArtBundleCodec.sanitizeAssetPath("../etc/passwd"))
        assertNull(ArtBundleCodec.sanitizeAssetPath("assets/../../evil.webp"))
        assertNull(ArtBundleCodec.sanitizeAssetPath("/abs/path.webp"))
        assertNull(ArtBundleCodec.sanitizeAssetPath(""))
        assertNull(ArtBundleCodec.sanitizeAssetPath("assets//keys.webp"))
        assertEquals("assets/keys/k_a.webp", ArtBundleCodec.sanitizeAssetPath("assets\\keys\\k_a.webp"))
        assertEquals("assets/keys/k_a.webp", ArtBundleCodec.sanitizeAssetPath("assets/keys/k_a.webp"))
    }

    @Test
    fun artBundleCodec_extractsManifestAndAssets_safely() {
        val zip = buildZip(
            "manifest.json" to """{"schema":1,"theme_id":"kb_art","title":"Art"}""".toByteArray(Charsets.UTF_8),
            "assets/keys/k_a_ace.webp" to byteArrayOf(1, 2, 3, 4),
            "assets/groups/letter_glass_ocean.webp" to byteArrayOf(5, 6),
            "assets/keys/../evil.webp" to byteArrayOf(9, 9, 9)
        )
        assertTrue(ArtBundleCodec.isLikelyZip(zip))

        assertArrayEquals(byteArrayOf(1, 2, 3, 4), ArtBundleCodec.extractAsset(zip, "assets/keys/k_a_ace.webp"))
        assertArrayEquals(byteArrayOf(5, 6), ArtBundleCodec.extractAsset(zip, "assets/groups/letter_glass_ocean.webp"))

        // Non-assets paths and traversal paths are refused even when the ZIP contains such entries.
        assertNull(ArtBundleCodec.extractAsset(zip, "manifest.json"))
        assertNull(ArtBundleCodec.extractAsset(zip, "../evil.webp"))
        assertNull(ArtBundleCodec.extractAsset(zip, "assets/keys/../evil.webp"))
        assertNull(ArtBundleCodec.extractAsset(zip, "assets/missing.webp"))

        val manifest = ArtBundleCodec.extractManifestJson(zip)
        assertNotNull(manifest)
        assertTrue(manifest!!.contains("kb_art"))
        assertEquals(
            setOf("manifest.json", "assets/keys/k_a_ace.webp", "assets/groups/letter_glass_ocean.webp", "assets/keys/../evil.webp"),
            ArtBundleCodec.listEntries(zip).toSet()
        )
    }

    @Test
    fun manifestEngine_parsesEffectModesForMegaPacks() {
        val effects = listOf(
            "glass", "motion", "live", "3d_live_motion", "touch",
            "neon", "aurora", "holographic", "parallax", "ripple"
        )
        effects.forEach { mode ->
            val json = """
                {"schema":1,"theme_id":"kb_gen_0001","title":"Neon Dragon",
                 "colors":{"primary":"#F59E0B"},
                 "keyboard":{"key_bg":"#CC14182A","key_pressed_bg":"#E83A4A6E","functional_key_bg":"#B810141C","effect_mode":"$mode","glass_rim_glow":true,"scrim_alpha":0.6}}
            """.trimIndent()
            val manifest = ThemeManifestEngine.parseManifest(json)
            assertNotNull(manifest)
            val profile = ThemeManifestEngine.toRenderProfile(manifest!!)
            assertEquals(mode, profile.effectMode)
            assertEquals(mode, ThemeManifestEngine.normalizeEffectMode(" $mode ".uppercase()))
            assertEquals(0xCC14182A.toInt(), profile.keyBgColor)
            assertEquals(0xE83A4A6E.toInt(), profile.keyPressedBgColor)
            assertEquals(0xB810141C.toInt(), profile.functionalKeyBgColor)
            assertTrue(profile.glassRimGlow)
            assertEquals(0.6f, profile.scrimAlpha, 0.0001f)
        }
        assertEquals("glass", ThemeManifestEngine.normalizeEffectMode("unknown_effect"))
        assertEquals("glass", ThemeManifestEngine.normalizeEffectMode(null))
        // Missing effect_mode falls back to glass
        val plain = ThemeManifestEngine.parseManifest(
            """{"schema":1,"theme_id":"t","title":"T","colors":{}}"""
        )
        assertNotNull(plain)
        assertEquals("glass", ThemeManifestEngine.toRenderProfile(plain!!).effectMode)
    }

    @Test
    fun storeCatalogFilter_passesBundleSha256Through() {
        val catalog = listOf(
            CatalogThemeItem(
                themeId = "kb_one_piece_grandline",
                title = "One Piece",
                category = "anime",
                isPaid = 1,
                currentVersion = 1,
                bundleSha256 = "abc123".repeat(10)
            )
        )
        val entries = StoreCatalogFilter.buildEntries(
            catalog = catalog,
            installed = emptyMap(),
            ownedThemeIds = setOf("kb_one_piece_grandline"),
            hasFullAccess = false
        )
        assertEquals("abc123".repeat(10), entries.first().bundleSha256)
    }

    @Test
    fun storeCatalogFilter_mapsDiscountsAndSeparatesOwnedFromDownloaded() {
        val catalog = listOf(
            CatalogThemeItem(
                themeId = "kb_sale_owned",
                title = "Sale Owned",
                category = "anime",
                isPaid = 1,
                priceBdtPaisa = 7000,
                originalPriceBdtPaisa = 10000,
                discountPercent = 30,
                discountLabel = "30% OFF",
                currentVersion = 2
            ),
            CatalogThemeItem(
                themeId = "kb_full_price",
                title = "Full Price",
                category = "matte",
                isPaid = 1,
                priceBdtPaisa = 5000,
                originalPriceBdtPaisa = 5000,
                discountPercent = 0,
                discountLabel = ""
            )
        )
        val entries = StoreCatalogFilter.buildEntries(
            catalog = catalog,
            installed = emptyMap(),
            ownedThemeIds = setOf("kb_sale_owned"),
            hasFullAccess = false
        )
        val owned = entries.first { it.themeId == "kb_sale_owned" }
        assertTrue(owned.hasDiscount)
        assertEquals(10000, owned.originalPriceBdtPaisa)
        assertEquals(7000, owned.priceBdtPaisa)
        assertEquals("30% OFF", owned.discountLabel)
        assertTrue(owned.isOwned)
        assertFalse(owned.isDownloaded)
        assertEquals(StoreThemeState.OWNED, owned.state)

        val unowned = entries.first { it.themeId == "kb_full_price" }
        assertFalse(unowned.hasDiscount)
        assertEquals(StoreThemeState.PAID, unowned.state)
    }

    @Test
    fun supabaseAuthClient_buildsPkceChallengeAndParsesOAuthCallbacks() {
        val verifier = SupabaseAuthClient.generatePkceVerifier()
        assertTrue(verifier.length >= 43)
        assertFalse(verifier.contains("="))

        val challenge = SupabaseAuthClient.computePkceS256Challenge(verifier)
        assertEquals(43, challenge.length)
        assertFalse(challenge.contains("+") || challenge.contains("/") || challenge.contains("="))

        val url = SupabaseAuthClient.buildGoogleAuthorizeUrl(challenge)
        assertTrue(url.startsWith("${SupabaseAuthClient.SUPABASE_URL}/auth/v1/authorize?"))
        assertTrue(url.contains("provider=google"))
        assertTrue(url.contains("code_challenge_method=s256"))
        assertTrue(url.contains("keybird%3A%2F%2Fauth-callback"))

        val codeCb = SupabaseAuthClient.parseOAuthCallbackParams("keybird://auth-callback?code=abc-123-xyz")
        assertEquals("abc-123-xyz", codeCb.code)
        assertNull(codeCb.errorDescription)

        val fragCb = SupabaseAuthClient.parseOAuthCallbackParams(
            "keybird://auth-callback#access_token=tok_1&refresh_token=ref_1&expires_in=7200"
        )
        assertEquals("tok_1", fragCb.accessToken)
        assertEquals("ref_1", fragCb.refreshToken)
        assertEquals(7200L, fragCb.expiresInSec)

        val errCb = SupabaseAuthClient.parseOAuthCallbackParams(
            "keybird://auth-callback?error=access_denied&error_description=User%20cancelled"
        )
        assertEquals("User cancelled", errCb.errorDescription)

        val recoveryQuery = SupabaseAuthClient.parseOAuthCallbackParams(
            "keybird://auth-callback?access_token=rec_tok&refresh_token=rec_ref&type=recovery"
        )
        assertEquals("rec_tok", recoveryQuery.accessToken)
        assertEquals("recovery", recoveryQuery.type)

        val recoveryFrag = SupabaseAuthClient.parseOAuthCallbackParams(
            "keybird://auth-callback#access_token=rec_tok_2&type=recovery"
        )
        assertEquals("recovery", recoveryFrag.type)
    }

    @Test
    fun supabaseAuthClient_validatesAndSerializesPasswordUpdate() {
        val payload = SupabaseAuthClient.buildUpdatePasswordPayload("newSecret123")
        assertTrue(payload.contains("\"password\":\"newSecret123\""))

        SupabaseAuthClient.validateNewPassword("123456")
        var rejected = false
        try {
            SupabaseAuthClient.validateNewPassword("12345")
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("Passwords shorter than 6 chars must be rejected", rejected)
    }

    @Test
    fun supabaseAuthClient_buildsHttpsRedirectPathsAndGoogleIdTokenPayload() {
        val signupPath = SupabaseAuthClient.buildAuthRedirectPath("/auth/v1/signup")
        assertTrue(signupPath.startsWith("/auth/v1/signup?redirect_to="))
        assertTrue(signupPath.contains("https%3A%2F%2Fiplus-theme-hub.diagnostic-fanatic.workers.dev%2Fauth%2Fcallback"))
        assertFalse(signupPath.contains("localhost"))

        val recoverPath = SupabaseAuthClient.buildAuthRedirectPath("/auth/v1/recover")
        assertTrue(recoverPath.startsWith("/auth/v1/recover?redirect_to="))
        assertTrue(recoverPath.contains("https%3A%2F%2Fiplus-theme-hub.diagnostic-fanatic.workers.dev%2Fauth%2Fcallback"))

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            SupabaseAuthClient.sha256Hex("abc")
        )

        val payloadWithNonce = SupabaseAuthClient.buildGoogleIdTokenPayload("id.tok.123", "raw-nonce-1")
        assertTrue(payloadWithNonce.contains("\"provider\":\"google\""))
        assertTrue(payloadWithNonce.contains("\"id_token\":\"id.tok.123\""))
        assertTrue(payloadWithNonce.contains("\"nonce\":\"raw-nonce-1\""))

        val payloadWithoutNonce = SupabaseAuthClient.buildGoogleIdTokenPayload("id.tok.123", null)
        assertTrue(payloadWithoutNonce.contains("\"id_token\":\"id.tok.123\""))
        assertFalse(payloadWithoutNonce.contains("\"nonce\""))
    }

    @Test
    fun storeFontEngine_classifiesBanglaNumericAndLatinScripts() {
        assertEquals(LabelScript.BANGLA, StoreFontEngine.classifyLabelScript("ক"))
        assertEquals(LabelScript.BANGLA, StoreFontEngine.classifyLabelScript("অ আ"))
        assertEquals(LabelScript.BANGLA, StoreFontEngine.classifyLabelScript("৳"))
        assertEquals(LabelScript.NUMBER, StoreFontEngine.classifyLabelScript("5"))
        assertEquals(LabelScript.NUMBER, StoreFontEngine.classifyLabelScript("123"))
        assertEquals(LabelScript.NUMBER, StoreFontEngine.classifyLabelScript("৫"))
        assertEquals(LabelScript.NUMBER, StoreFontEngine.classifyLabelScript("০১২৩"))
        assertEquals(LabelScript.LATIN, StoreFontEngine.classifyLabelScript("Q"))
        assertEquals(LabelScript.LATIN, StoreFontEngine.classifyLabelScript("?123"))
        assertEquals(LabelScript.LATIN, StoreFontEngine.classifyLabelScript(""))
        assertEquals(LabelScript.LATIN, StoreFontEngine.classifyLabelScript(null))
    }

    @Test
    fun storeCatalogFilter_filtersByProductTabsAndFiveDigitShortCode() {
        val catalog = listOf(
            CatalogThemeItem(themeId = "kb_emerald_night", shortCode = "10001", title = "Emerald Night", category = "dark", themeType = "color", isPaid = 0),
            CatalogThemeItem(themeId = "kb_one_piece_grandline", shortCode = "10089", title = "One Piece: Grand Line", category = "anime", themeType = "art_pack", isPaid = 1, priceBdtPaisa = 10000),
            CatalogThemeItem(themeId = "kb_font_hind_siliguri", shortCode = "10090", title = "Hind Siliguri Clean", category = "font", themeType = "font", isPaid = 0),
            CatalogThemeItem(themeId = "kb_stk_onepiece_chibi", shortCode = "10108", title = "One Piece Chibi Reactions", category = "sticker", themeType = "sticker", isPaid = 1, priceBdtPaisa = 10000)
        )
        val entries = StoreCatalogFilter.buildEntries(catalog, emptyMap(), emptySet(), false)

        assertEquals(
            setOf("kb_emerald_night", "kb_one_piece_grandline"),
            StoreCatalogFilter.applyFilter(entries, ThemeFilter.ALL, "", "all", ProductTabFilter.THEMES).map { it.themeId }.toSet()
        )
        assertEquals(
            listOf("kb_font_hind_siliguri"),
            StoreCatalogFilter.applyFilter(entries, ThemeFilter.ALL, "", "all", ProductTabFilter.FONTS).map { it.themeId }
        )
        assertEquals(
            listOf("kb_stk_onepiece_chibi"),
            StoreCatalogFilter.applyFilter(entries, ThemeFilter.ALL, "", "all", ProductTabFilter.STICKERS).map { it.themeId }
        )

        // Searching by 5-digit short code with or without '#'
        assertEquals(
            listOf("kb_one_piece_grandline"),
            StoreCatalogFilter.applyFilter(entries, ThemeFilter.ALL, "#10089").map { it.themeId }
        )
        assertEquals(
            listOf("kb_stk_onepiece_chibi"),
            StoreCatalogFilter.applyFilter(entries, ThemeFilter.ALL, "10108").map { it.themeId }
        )
    }

    @Test
    fun manifestEngine_parsesFontPackAndStickerPackManifests() {
        val fontJson = """
            {
              "schema": 1,
              "theme_id": "kb_font_hind_siliguri",
              "app_id": "keybird",
              "title": "Hind Siliguri Clean",
              "version": 1,
              "theme_type": "font",
              "colors": { "primary": "#00E676" },
              "font_pack": {
                "latin_asset": "assets/fonts/latin.ttf",
                "bangla_asset": "assets/fonts/bangla.ttf",
                "number_asset": "assets/fonts/number.ttf",
                "latin_label": "Inter",
                "bangla_label": "Hind Siliguri",
                "number_label": "JetBrains Mono"
              }
            }
        """.trimIndent()
        val fontManifest = ThemeManifestEngine.parseManifest(fontJson)
        assertNotNull(fontManifest)
        assertEquals("font", fontManifest!!.themeType)
        assertEquals("assets/fonts/bangla.ttf", fontManifest.fontPack?.banglaAsset)
        assertEquals("Hind Siliguri", fontManifest.fontPack?.banglaLabel)

        val stickerJson = """
            {
              "schema": 1,
              "theme_id": "kb_stk_onepiece_chibi",
              "app_id": "keybird",
              "title": "One Piece Chibi Reactions",
              "version": 1,
              "theme_type": "sticker",
              "colors": { "primary": "#FF5722" },
              "sticker_pack": {
                "pack_icon_asset": "assets/stickers/s01_luffy_grin.webp",
                "stickers": [
                  { "id": "s01", "label": "Luffy Grin", "asset": "assets/stickers/s01_luffy_grin.webp" },
                  { "id": "s02", "label": "Zoro Cool", "asset": "assets/stickers/s02_zoro_cool.webp" }
                ]
              }
            }
        """.trimIndent()
        val stickerManifest = ThemeManifestEngine.parseManifest(stickerJson)
        assertNotNull(stickerManifest)
        assertEquals("sticker", stickerManifest!!.themeType)
        assertEquals(2, stickerManifest.stickerPack?.stickers?.size)
        assertEquals("s01", stickerManifest.stickerPack?.stickers?.first()?.id)
    }

    @Test
    fun parseManifest_readsKeyColorMapAndMotionFields() {
        val json = """
            {
              "schema": 1,
              "theme_id": "kb_matte_bauhaus_001",
              "app_id": "keybird",
              "title": "Bauhaus Hybrid Matte",
              "version": 1,
              "theme_type": "color",
              "is_light": true,
              "colors": { "primary": "#C23636" },
              "keyboard": {
                "bg_type": "solid",
                "bg_colors": ["#000000"],
                "key_bg": "#EAE6E1",
                "key_pressed_bg": "#C8C4BE",
                "functional_key_bg": "#1C1D20",
                "enter_key_bg": "#C23636",
                "keyPressedText": "#FFFFFF",
                "bg_animated_asset": "assets/bg/live.webp",
                "key_color_map": {
                  "palette": ["#E2B438", "#2A5288", "#1C1D20", "#EAE6E1", "#C23636"],
                  "patternMode": "bauhaus_cycle",
                  "keys": {
                    "k_q": "#E2B438",
                    "w": "#2A5288",
                    "অ": "#C23636",
                    "slot_r1_c2": "#2A5288"
                  }
                },
                "motion": {
                  "bgAnimatedAsset": "assets/bg/live.webp",
                  "pressEffect": "ripple",
                  "pressColor": "#E2B438",
                  "parallaxDp": 4.5,
                  "particles": true,
                  "particleDensity": 3,
                  "particleColor": "#00E676"
                }
              }
            }
        """.trimIndent()
        val manifest = ThemeManifestEngine.parseManifest(json)
        assertNotNull(manifest)
        val k = manifest!!.keyboard
        assertEquals("#FFFFFF", k.keyPressedText)
        assertEquals("assets/bg/live.webp", k.bgAnimatedAsset)
        assertNotNull(k.keyColorMap)
        assertEquals("bauhaus_cycle", k.keyColorMap?.patternMode)
        assertEquals(5, k.keyColorMap?.palette?.size)
        assertEquals("#E2B438", k.keyColorMap?.keys?.get("k_q"))
        assertEquals("#C23636", k.keyColorMap?.keys?.get("অ"))
        assertNotNull(k.motion)
        assertEquals("assets/bg/live.webp", k.motion?.bgAnimatedAsset)
        assertEquals("ripple", k.motion?.pressEffect)
        assertEquals(4.5f, k.motion?.parallaxDp ?: 0f, 0.001f)
        assertTrue(k.motion?.particles == true)
        assertEquals(3, k.motion?.particleDensity)
        assertEquals("#00E676", k.motion?.particleColor)
    }

    @Test
    fun resolveKeyFillColor_bauhausCycleAndExplicitKeys_returnsDistinctColorsAndContrastText() {
        val json = """
            {
              "schema": 1,
              "theme_id": "kb_matte_bauhaus_001",
              "title": "Bauhaus Hybrid Matte",
              "is_light": true,
              "colors": { "primary": "#C23636" },
              "keyboard": {
                "key_bg": "#EAE6E1",
                "functional_key_bg": "#1C1D20",
                "enter_key_bg": "#C23636",
                "key_color_map": {
                  "palette": ["#E2B438", "#2A5288", "#1C1D20", "#EAE6E1", "#C23636"],
                  "patternMode": "bauhaus_cycle",
                  "keys": {
                    "k_q": "#E2B438",
                    "t": "#C23636",
                    "অ": "#2A5288",
                    "slot_r2_c1": "#1C1D20"
                  }
                }
              }
            }
        """.trimIndent()
        val manifest = ThemeManifestEngine.parseManifest(json)!!
        val profile = ThemeManifestEngine.toRenderProfile(manifest)

        val qColor = ThemeManifestEngine.resolveKeyFillColor(profile, 'q'.code, "q", 0, 0, 100, 100)
        val tColor = ThemeManifestEngine.resolveKeyFillColor(profile, 't'.code, "t", 400, 0, 100, 100)
        val banglaColor = ThemeManifestEngine.resolveKeyFillColor(profile, 'অ'.code, "অ", 0, 0, 100, 100)
        val slotColor = ThemeManifestEngine.resolveKeyFillColor(profile, 'x'.code, "x", 100, 200, 100, 100)

        assertEquals(0xFFE2B438.toInt(), qColor)
        assertEquals(0xFFC23636.toInt(), tColor)
        assertEquals(0xFF2A5288.toInt(), banglaColor)
        assertEquals(0xFF1C1D20.toInt(), slotColor)

        // Contrast text over light yellow (#E2B438) is dark (#121212); over dark blue (#2A5288) is white (#FFFFFF)
        assertEquals(0xFF121212.toInt(), ThemeManifestEngine.resolveKeyTextColor(profile, 'q'.code, "q", 0, 0, 100, 100))
        assertEquals(0xFFFFFFFF.toInt(), ThemeManifestEngine.resolveKeyTextColor(profile, 'অ'.code, "অ", 0, 0, 100, 100))

        // Unmapped letter in bauhaus_cycle still resolves to one of the palette colors
        val cycleColor = ThemeManifestEngine.resolveKeyFillColor(profile, 'g'.code, "g", 400, 100, 100, 100)
        assertNotNull(cycleColor)
        assertTrue(profile.keyColorPalette.contains(cycleColor!!))
    }

    @Test
    fun resolveKeyFillColor_wasdMode_highlightsOnlyWasdSlots() {
        val json = """
            {
              "schema": 1,
              "theme_id": "kb_gaming_rgb_001",
              "title": "Gaming WASD",
              "colors": { "primary": "#00E676" },
              "keyboard": {
                "key_bg": "#17171A",
                "enter_key_bg": "#00E676",
                "key_color_map": {
                  "palette": ["#17171A", "#00E676"],
                  "patternMode": "wasd"
                }
              }
            }
        """.trimIndent()
        val profile = ThemeManifestEngine.toRenderProfile(ThemeManifestEngine.parseManifest(json)!!)

        assertEquals(0xFF00E676.toInt(), ThemeManifestEngine.resolveKeyFillColor(profile, 'w'.code, "w", 100, 0, 100, 100))
        assertEquals(0xFF00E676.toInt(), ThemeManifestEngine.resolveKeyFillColor(profile, 'a'.code, "a", 0, 100, 100, 100))
        assertEquals(0xFF00E676.toInt(), ThemeManifestEngine.resolveKeyFillColor(profile, 's'.code, "s", 100, 100, 100, 100))
        assertEquals(0xFF00E676.toInt(), ThemeManifestEngine.resolveKeyFillColor(profile, 'd'.code, "d", 200, 100, 100, 100))
        assertNull(ThemeManifestEngine.resolveKeyFillColor(profile, 'k'.code, "k", 700, 100, 100, 100))
    }

    @Test
    fun computeCenterCropRect_preservesAspectRatioWithoutStretching() {
        val rect = ThemeManifestEngine.computeCenterCropRect(
            srcWidth = 1080,
            srcHeight = 720,
            dstWidth = 1080,
            dstHeight = 540
        )
        assertEquals(1080f, rect.width(), 0.5f)
        assertEquals(720f, rect.height(), 0.5f)
        assertEquals(0f, rect.left, 0.5f)
        assertEquals(-90f, rect.top, 0.5f)
    }

    @Test
    fun shouldAnimateLiveBackground_respectsVisibilityPowerSaveAndAnimatorScale() {
        assertTrue(
            ThemeManifestEngine.shouldAnimateLiveBackground(
                hasLiveContent = true,
                isVisible = true,
                isPowerSaveMode = false,
                animatorDurationScale = 1.0f
            )
        )
        assertFalse(
            ThemeManifestEngine.shouldAnimateLiveBackground(
                hasLiveContent = true,
                isVisible = false,
                isPowerSaveMode = false,
                animatorDurationScale = 1.0f
            )
        )
        assertFalse(
            ThemeManifestEngine.shouldAnimateLiveBackground(
                hasLiveContent = true,
                isVisible = true,
                isPowerSaveMode = true,
                animatorDurationScale = 1.0f
            )
        )
        assertFalse(
            ThemeManifestEngine.shouldAnimateLiveBackground(
                hasLiveContent = true,
                isVisible = true,
                isPowerSaveMode = false,
                animatorDurationScale = 0.0f
            )
        )
        assertFalse(
            ThemeManifestEngine.shouldAnimateLiveBackground(
                hasLiveContent = false,
                isVisible = true,
                isPowerSaveMode = false,
                animatorDurationScale = 1.0f
            )
        )
    }

    @Test
    fun parseManifest_readsKeyStyleAlphaAndRainbowTextDefaultsAndOverrides() {
        val raw = """
            {
              "schema": 1,
              "theme_id": "kb_neon_outline_test",
              "title": "Neon Outline Test",
              "colors": { "primary": "#00F5D4" },
              "keyboard": {
                "bg_colors": ["#000000"],
                "key_bg": "#111118",
                "key_style": "outline",
                "key_alpha": 0.25,
                "rainbow_text": true,
                "effect_mode": "neon"
              }
            }
        """.trimIndent()
        val manifest = ThemeManifestEngine.parseManifest(raw)!!
        val profile = ThemeManifestEngine.toRenderProfile(manifest)
        assertEquals("outline", profile.keyStyle)
        assertEquals(0.25f, profile.keyAlpha, 0.001f)
        assertTrue(profile.rainbowText)

        // Normalized fallback on unknown values
        assertEquals("classic", ThemeManifestEngine.normalizeKeyStyle("unknown_xyz"))
        assertEquals("ceramic_3d", ThemeManifestEngine.normalizeKeyStyle("CERAMIC_3D"))
        assertEquals("borderless", ThemeManifestEngine.normalizeKeyStyle("borderless"))
    }

    @Test
    fun resolveKeyTextColor_rainbowText_variesAcrossColumnsWithHighContrast() {
        val raw = """
            {
              "schema": 1,
              "theme_id": "kb_rainbow_neon",
              "title": "Rainbow Neon",
              "is_light": false,
              "colors": { "primary": "#FF2A6D" },
              "keyboard": {
                "bg_colors": ["#000000"],
                "key_bg": "#000000",
                "key_style": "borderless",
                "rainbow_text": true
              }
            }
        """.trimIndent()
        val profile = ThemeManifestEngine.toRenderProfile(ThemeManifestEngine.parseManifest(raw)!!)
        val c0 = ThemeManifestEngine.resolveKeyTextColor(profile, 'q'.code, "q", 0, 0, 100, 100)
        val c1 = ThemeManifestEngine.resolveKeyTextColor(profile, 'w'.code, "w", 100, 0, 100, 100)
        val c2 = ThemeManifestEngine.resolveKeyTextColor(profile, 'e'.code, "e", 200, 0, 100, 100)
        assertTrue(c0 != c1)
        assertTrue(c1 != c2)
        // Functional enter still preserves enterKeyFgColor
        assertEquals(
            profile.enterKeyFgColor,
            ThemeManifestEngine.resolveKeyTextColor(
                profile,
                org.futo.inputmethod.latin.common.Constants.CODE_ENTER,
                null,
                900,
                300,
                100,
                100
            )
        )
    }

    @Test
    fun stickerMimeTypeAndKeyPressedTextColor_stayDistinctAndAccurate() {
        assertEquals("image/webp", org.futo.inputmethod.latin.themestore.engine.StickerPackRegistry.mimeTypeForStickerFileName("s01.webp"))
        assertEquals("image/png", org.futo.inputmethod.latin.themestore.engine.StickerPackRegistry.mimeTypeForStickerFileName("custom_1.PNG"))
        assertEquals("image/jpeg", org.futo.inputmethod.latin.themestore.engine.StickerPackRegistry.mimeTypeForStickerFileName("photo.jpg"))
        assertEquals("image/jpeg", org.futo.inputmethod.latin.themestore.engine.StickerPackRegistry.mimeTypeForStickerFileName("photo.JPEG"))

        // Regression: dark theme with bright yellow enter (#FACC15 -> dark enterKeyFg #121212) must keep
        // white keyPressedTextColor over dark pressed key (#2C2C33) instead of collapsing to enterKeyFgColor.
        val raw = """
            {
              "schema": 1,
              "theme_id": "kb_amoled_yellow_enter",
              "title": "AMOLED Yellow Enter",
              "is_light": false,
              "colors": { "primary": "#FACC15", "on_primary": "#121212" },
              "keyboard": {
                "key_bg": "#16161A",
                "key_pressed_bg": "#2C2C33",
                "enter_key_bg": "#FACC15",
                "enter_key_fg": "#121212",
                "key_text": "#FFFFFF",
                "keyPressedText": "#FFFFFF"
              }
            }
        """.trimIndent()
        val profile = ThemeManifestEngine.toRenderProfile(ThemeManifestEngine.parseManifest(raw)!!)
        assertEquals(0xFF121212.toInt(), profile.enterKeyFgColor)
        assertEquals(0xFFFFFFFF.toInt(), profile.keyPressedTextColor)
    }

    @Test
    fun omittedEnterFgAndSpacebarFields_deriveFromThemePaletteInsteadOfMatrixGreen() {
        val raw = """
            {
              "schema": 1,
              "theme_id": "kb_live_animated_001",
              "title": "Breathing aurora and drifting light 1",
              "is_light": true,
              "colors": { "primary": "#0D0F75", "on_primary": "#FFFFFF", "background": "#B4C8E1" },
              "keyboard": {
                "key_bg": "#F5EBFF",
                "key_pressed_bg": "#FAFBFF",
                "enter_key_bg": "#0D0F75",
                "key_text": "#121212"
              }
            }
        """.trimIndent()
        val profile = ThemeManifestEngine.toRenderProfile(ThemeManifestEngine.parseManifest(raw)!!)
        // Dark indigo enter (#0D0F75) must auto-pick white foreground (#FFFFFF), never black (#000000).
        assertEquals(0xFFFFFFFF.toInt(), profile.enterKeyFgColor)
        // Spacebar bg & stroke must derive from key_bg (#F5EBFF) and enter_key_bg (#0D0F75), never #1B1B1F / #00E676.
        assertEquals(0xFFF5EBFF.toInt(), profile.spacebarBgColor)
        assertEquals(0xFF0D0F75.toInt(), profile.spacebarStrokeColor)
        assertEquals(0xFF0D0F75.toInt(), profile.gestureTrailColor)
        assertEquals(0xFF0D0F75.toInt(), ThemeManifestEngine.resolveSpacebarLabelColor(profile))

        // Dark-on-dark safety: if both spacebarBg and spacebarStroke are dark, label falls back to legible keyText/YIQ.
        val darkOnDark = profile.copy(
            spacebarBgColor = 0xFF141628.toInt(),
            spacebarStrokeColor = 0xFF1E293B.toInt(),
            keyTextColor = 0xFFFFFFFF.toInt()
        )
        assertEquals(0xFFFFFFFF.toInt(), ThemeManifestEngine.resolveSpacebarLabelColor(darkOnDark))

        // Previously cached v4.1.0 local JSON that had encodeDefaults (#000000/#1B1B1F/#00E676) baked in heals automatically.
        val cachedLegacyRaw = """
            {
              "schema": 1,
              "theme_id": "kb_live_animated_001",
              "title": "Breathing aurora and drifting light 1",
              "is_light": true,
              "colors": { "primary": "#0D0F75", "on_primary": "#FFFFFF", "background": "#B4C8E1" },
              "keyboard": {
                "key_bg": "#F5EBFF",
                "key_pressed_bg": "#FAFBFF",
                "enter_key_bg": "#0D0F75",
                "enter_key_fg": "#000000",
                "spacebar_bg": "#1B1B1F",
                "spacebar_stroke": "#00E676",
                "gesture_trail": "#00E676",
                "key_text": "#121212"
              }
            }
        """.trimIndent()
        val healed = ThemeManifestEngine.toRenderProfile(ThemeManifestEngine.parseManifest(cachedLegacyRaw)!!)
        assertEquals(0xFFFFFFFF.toInt(), healed.enterKeyFgColor)
        assertEquals(0xFFF5EBFF.toInt(), healed.spacebarBgColor)
        assertEquals(0xFF0D0F75.toInt(), healed.spacebarStrokeColor)
        assertEquals(0xFF0D0F75.toInt(), healed.gestureTrailColor)
    }
}
