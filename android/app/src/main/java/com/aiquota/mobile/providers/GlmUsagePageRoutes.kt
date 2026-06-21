package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId
import java.net.URI
import java.util.Locale

object GlmLoginPostRedirects {
    fun usageRedirectUrl(providerId: ProviderId, url: String): String? {
        if (providerId != ProviderId.GLM) return null
        return GlmUsagePageRoutes.usageUrlFrom(url)
    }
}

object GlmRuntimeRefreshJobs {
    fun resolve(job: ProviderRefreshJob, connectionMode: GlmConnectionMode): ProviderRefreshJob {
        if (job.providerId != ProviderId.GLM) return job
        if (connectionMode != GlmConnectionMode.WEB_OAUTH) return job
        return job.copy(
            mode = ProviderRefreshMode.HIDDEN_WEB_COLLECTOR,
            startUrl = GlmProviderUrls.WEB_USAGE_URL
        )
    }
}

object GlmUsagePageRoutes {
    fun usageUrlFrom(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host.orEmpty().lowercase(Locale.US)
        if (host != "z.ai" && host != "www.z.ai") return null
        val path = uri.path.orEmpty().lowercase(Locale.US).trimEnd('/')
        if (path != "/manage-apikey/coding-plan/personal/my-plan") return null
        return GlmProviderUrls.WEB_USAGE_URL
    }
}
