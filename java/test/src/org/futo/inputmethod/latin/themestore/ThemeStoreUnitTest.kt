package org.futo.inputmethod.latin.themestore

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.themestore.domain.StoreCatalogFilter
import org.futo.inputmethod.latin.themestore.engine.ArtBundleCodec
import org.futo.inputmethod.latin.themestore.engine.ThemeManifestEngine
import org.futo.inputmethod.latin.themestore.model.CatalogThemeItem
import org.futo.inputmethod.latin.themestore.model.EntitlementReceiptPayload
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
    }
}
