package com.zidny.keyboard

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest

/**
 * Gets Play Integrity tokens that prove a request comes from this app as installed from Google Play.
 * The token is bound to [requestHash] so it cannot be reused for a different translation.
 */
class IntegrityChecker(context: Context) {

    private val manager = IntegrityManagerFactory.createStandard(context.applicationContext)
    private var provider: StandardIntegrityTokenProvider? = null

    /** Calls [onToken] with a token, or null when integrity is not set up or Google Play cannot give one. */
    fun token(requestHash: String, onToken: (String?) -> Unit) {
        if (BuildConfig.CLOUD_PROJECT_NUMBER == 0L) {
            onToken(null)
            return
        }
        val ready = provider
        if (ready != null) {
            request(ready, requestHash, retry = true, onToken)
        } else {
            prepare { fresh -> if (fresh == null) onToken(null) else request(fresh, requestHash, retry = false, onToken) }
        }
    }

    private fun prepare(onReady: (StandardIntegrityTokenProvider?) -> Unit) {
        manager.prepareIntegrityToken(
            PrepareIntegrityTokenRequest.builder().setCloudProjectNumber(BuildConfig.CLOUD_PROJECT_NUMBER).build()
        )
            .addOnSuccessListener { provider = it; onReady(it) }
            .addOnFailureListener { onReady(null) }
    }

    private fun request(ready: StandardIntegrityTokenProvider, requestHash: String, retry: Boolean, onToken: (String?) -> Unit) {
        ready.request(StandardIntegrityTokenRequest.builder().setRequestHash(requestHash).build())
            .addOnSuccessListener { onToken(it.token()) }
            .addOnFailureListener {
                // A provider goes stale after a while; prepare a new one once and try again.
                provider = null
                if (retry) prepare { fresh -> if (fresh == null) onToken(null) else request(fresh, requestHash, retry = false, onToken) }
                else onToken(null)
            }
    }
}
