package com.aiquota.mobile.widget

import java.util.Locale

internal fun providerWidgetStatusLabel(status: String): String {
    return when (status.trim().uppercase(Locale.US)) {
        "CONNECTED" -> "연결됨"
        "CONNECTING" -> "연결 중"
        "DISCONNECTED", "NOT_CONNECTED" -> "연결 안 됨"
        "COLLECTING", "REFRESHING" -> "데이터 수집 중"
        "STALE" -> "연결됨"
        "INTERACTIVE_AUTH_REQUIRED" -> "연결 확인 필요"
        "UNAVAILABLE" -> "사용할 수 없음"
        "ERROR", "DANGER" -> "오류"
        "WARNING" -> "주의"
        "NORMAL" -> "정상"
        else -> status.ifBlank { "상태 없음" }
    }
}
