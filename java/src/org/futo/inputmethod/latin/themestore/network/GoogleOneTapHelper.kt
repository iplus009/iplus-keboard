package org.futo.inputmethod.latin.themestore.network

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import org.futo.inputmethod.latin.BuildConfig
import java.util.UUID

sealed class GoogleOneTapOutcome {
    data class IdTokenReady(val idToken: String, val rawNonce: String) : GoogleOneTapOutcome()
    data class Cancelled(val message: String) : GoogleOneTapOutcome()
    data class Unavailable(val reason: String) : GoogleOneTapOutcome()
    data class Failed(val error: String) : GoogleOneTapOutcome()
}

/**
 * Wraps Android Credential Manager + Google Identity to show a native bottom-sheet
 * 1-Tap Google Sign-In picker and return the Google ID token + raw nonce for Supabase.
 */
object GoogleOneTapHelper {
    fun isConfigured(): Boolean = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    suspend fun requestGoogleIdToken(context: Context): GoogleOneTapOutcome {
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        if (webClientId.isBlank()) {
            return GoogleOneTapOutcome.Unavailable("Native 1-Tap Web Client ID is not configured")
        }
        val activity = findActivity(context)
            ?: return GoogleOneTapOutcome.Unavailable("Google 1-Tap requires an active screen")

        return try {
            val rawNonce = UUID.randomUUID().toString()
            val hashedNonce = SupabaseAuthClient.sha256Hex(rawNonce)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .setNonce(hashedNonce)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val credentialManager = CredentialManager.create(activity)
            val result = credentialManager.getCredential(
                request = request,
                context = activity
            )
            val cred = result.credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
                val idToken = googleCred.idToken
                if (idToken.isBlank()) {
                    GoogleOneTapOutcome.Failed("Google returned an empty ID token")
                } else {
                    GoogleOneTapOutcome.IdTokenReady(idToken = idToken, rawNonce = rawNonce)
                }
            } else {
                GoogleOneTapOutcome.Failed("Unexpected credential type returned by Google")
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleOneTapOutcome.Cancelled("Google sign-in cancelled")
        } catch (e: NoCredentialException) {
            GoogleOneTapOutcome.Unavailable(e.message ?: "No Google account available for 1-Tap")
        } catch (e: GoogleIdTokenParsingException) {
            GoogleOneTapOutcome.Failed(e.message ?: "Unable to parse Google ID token")
        } catch (e: GetCredentialException) {
            GoogleOneTapOutcome.Unavailable(e.message ?: "Google Credential Manager unavailable")
        } catch (e: Exception) {
            GoogleOneTapOutcome.Failed(e.message ?: "Google 1-Tap sign-in failed")
        }
    }

    private tailrec fun findActivity(context: Context): Activity? = when (context) {
        is Activity -> context
        is ContextWrapper -> findActivity(context.baseContext)
        else -> null
    }
}
