package com.genspark.videotransform.auth

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.genspark.videotransform.data.SecureSettingsStore
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ClientAuthentication
import net.openid.appauth.ClientSecretPost
import net.openid.appauth.NoClientAuthentication
import net.openid.appauth.ResponseTypeValues

class YouTubeAuthActivity : Activity() {
    private lateinit var authService: AuthorizationService
    private lateinit var settingsStore: SecureSettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authService = AuthorizationService(this)
        settingsStore = SecureSettingsStore(this)

        val response = AuthorizationResponse.fromIntent(intent)
        val error = AuthorizationException.fromIntent(intent)
        if (response != null || error != null) {
            handleAuthorizationResponse(response, error)
            return
        }
        if (intent.getBooleanExtra("cancelled", false)) {
            finish()
            return
        }

        val settings = settingsStore.load()
        if (settings.youtubeClientId.isBlank()) {
            Toast.makeText(this, "Set YouTube Client ID in Settings first.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val serviceConfig = AuthorizationServiceConfiguration(
            Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
            Uri.parse("https://oauth2.googleapis.com/token"),
        )
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            settings.youtubeClientId,
            ResponseTypeValues.CODE,
            Uri.parse("videotransform://oauth2redirect"),
        )
            .setScopes(
                "openid",
                "email",
                "profile",
                "https://www.googleapis.com/auth/youtube.upload",
            )
            .build()

        val flag = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val completionIntent = PendingIntent.getActivity(
            this, 1001,
            Intent(this, YouTubeAuthActivity::class.java),
            flag,
        )
        val cancelIntent = PendingIntent.getActivity(
            this, 1002,
            Intent(this, YouTubeAuthActivity::class.java).putExtra("cancelled", true),
            flag,
        )
        authService.performAuthorizationRequest(request, completionIntent, cancelIntent)
    }

    private fun handleAuthorizationResponse(
        response: AuthorizationResponse?,
        error: AuthorizationException?,
    ) {
        if (error != null || response == null) {
            Toast.makeText(this, error?.errorDescription ?: "YouTube auth failed", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val secret = settingsStore.load().youtubeClientSecret
        val clientAuth: ClientAuthentication =
            if (secret.isBlank()) NoClientAuthentication.INSTANCE else ClientSecretPost(secret)
        authService.performTokenRequest(
            response.createTokenExchangeRequest(),
            clientAuth,
        ) { tokenResponse, tokenEx ->
            if (tokenEx != null || tokenResponse == null || tokenResponse.accessToken.isNullOrBlank()) {
                Toast.makeText(this, tokenEx?.errorDescription ?: "Token exchange failed", Toast.LENGTH_LONG).show()
                finish()
                return@performTokenRequest
            }
            settingsStore.updateYouTubeTokens(
                accessToken = tokenResponse.accessToken.orEmpty(),
                refreshToken = tokenResponse.refreshToken,
                expiry = tokenResponse.accessTokenExpirationTime ?: 0L,
            )
            Toast.makeText(this, "YouTube connected", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::authService.isInitialized) authService.dispose()
    }
}
