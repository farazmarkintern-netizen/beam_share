package com.example.beamshare.cloud

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

object CloudAuthManager {

    private val auth = FirebaseAuth.getInstance()

    // Web Client ID — Firebase Console se lo
    private const val WEB_CLIENT_ID = "76646506484-rebv0sl0ltrk48l0hqkl5jtfapeccec9.apps.googleusercontent.com"

    fun isLoggedIn() = auth.currentUser != null
    fun getUserId() = auth.currentUser?.uid
    fun getUserEmail() = auth.currentUser?.email
    fun logout() = auth.signOut()

    suspend fun signInWithGoogle(
        context: Context,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val credentialManager = CredentialManager.create(context)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdToken = GoogleIdTokenCredential
                    .createFrom(credential.data).idToken

                val firebaseCredential = GoogleAuthProvider
                    .getCredential(googleIdToken, null)

                auth.signInWithCredential(firebaseCredential)
                    .addOnSuccessListener { onSuccess(it.user?.uid ?: "") }
                    .addOnFailureListener { onError(it.message ?: "Error") }
            }
        } catch (e: Exception) {
            onError(e.message ?: "Sign-in failed")
        }
    }
}