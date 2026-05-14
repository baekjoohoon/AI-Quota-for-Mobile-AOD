package com.aiusage.mobile.ads

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

class AdConsentManager(context: Context) {
    private val consentInformation = UserMessagingPlatform.getConsentInformation(context)

    val canRequestAds: Boolean
        get() = consentInformation.canRequestAds()

    val privacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun gatherConsent(activity: Activity, onConsentInfoUpdated: () -> Unit) {
        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    onConsentInfoUpdated()
                }
            },
            {
                onConsentInfoUpdated()
            }
        )
    }

    fun showPrivacyOptionsForm(activity: Activity, onFormDismissed: () -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            onFormDismissed()
        }
    }
}
