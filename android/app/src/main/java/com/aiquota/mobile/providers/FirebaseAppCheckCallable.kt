package com.aiquota.mobile.providers

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableResult
import kotlinx.coroutines.tasks.await

internal suspend fun FirebaseFunctions.callWithAppCheckRetry(
    appCheck: FirebaseAppCheck,
    name: String,
    data: Any?
): HttpsCallableResult {
    appCheck.getAppCheckToken(false).await()
    return try {
        getHttpsCallable(name).call(data).await()
    } catch (error: FirebaseFunctionsException) {
        if (error.code != FirebaseFunctionsException.Code.UNAUTHENTICATED) throw error
        appCheck.getAppCheckToken(true).await()
        getHttpsCallable(name).call(data).await()
    }
}
