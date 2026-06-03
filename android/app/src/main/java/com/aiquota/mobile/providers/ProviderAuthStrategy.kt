package com.aiquota.mobile.providers

enum class ProviderAuthStoreKind {
    WEBVIEW_PROFILE,
    NATIVE_TOKEN,
    TOKEN_OR_WEBVIEW_FALLBACK,
    BACKEND_GATEWAY
}

enum class ProviderCollectionKind {
    WEBVIEW_COLLECTOR,
    NATIVE_API,
    NATIVE_API_WITH_WEBVIEW_FALLBACK,
    BACKEND_GATEWAY
}
