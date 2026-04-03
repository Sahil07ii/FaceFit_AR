package com.example.facefitar.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handle Google Sign in using Jetpack CredentialManager.
 * Note: Replace 'YOUR_WEB_CLIENT_ID' with the actual Web Client ID from Firebase Console.
 */
const val WEB_CLIENT_ID = "620858264562-05b4ec7hop20lqkfa3de476181j1tp99.apps.googleusercontent.com" // Add Real Web Client ID here!

suspend fun signInWithGoogleCredentialManager(context: Context): String? {
    val credentialManager = CredentialManager.create(context)

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(WEB_CLIENT_ID)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()
        
    try {
        val result = credentialManager.getCredential(
            request = request,
            context = context
        )
        
        val credential = result.credential
        if (credential is com.google.android.libraries.identity.googleid.GoogleIdTokenCredential || 
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
             try {
                 val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                 return googleIdTokenCredential.idToken
             } catch (e: GoogleIdTokenParsingException) {
                 e.printStackTrace()
             }
        } 
    } catch (e: GetCredentialException) {
        e.printStackTrace()
    }
    return null
}
