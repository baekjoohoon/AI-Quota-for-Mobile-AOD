package com.aiquota.mobile.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleApiErrorDiagnosticsTest {
    @Test
    fun serviceDisabledDiagnosticIsSpecificAndRedacted() {
        val body = """
            {
              "error": {
                "code": 403,
                "message": "Cloud Code Private API has not been used in project 248131081430 before or it is disabled.",
                "status": "PERMISSION_DENIED",
                "details": [
                  {
                    "@type": "type.googleapis.com/google.rpc.ErrorInfo",
                    "reason": "SERVICE_DISABLED",
                    "domain": "googleapis.com",
                    "metadata": {
                      "consumer": "projects/248131081430",
                      "activationUrl": "https://console.developers.google.com/apis/api/cloudcode-pa.googleapis.com/overview?project=248131081430"
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val summary = GoogleApiErrorDiagnostics.safeSummary(403, body)
        val diagnostic = GoogleApiErrorDiagnostics.providerDiagnostic(
            providerName = "Gemini",
            method = "loadCodeAssist",
            statusCode = 403,
            body = body
        )

        assertEquals("status=403 apiCode=403 apiStatus=PERMISSION_DENIED reason=SERVICE_DISABLED", summary)
        assertEquals("Gemini Cloud Code Private API is disabled for this OAuth project.", diagnostic)
        assertFalse(summary.contains("248131081430"))
        assertFalse(diagnostic.contains("248131081430"))
        assertFalse(summary.contains("activationUrl"))
    }

    @Test
    fun tokenEndpointErrorSummaryStillIncludesInvalidRequest() {
        val body = """{"error":"invalid_request","error_description":"client_secret is missing."}"""

        val summary = GoogleApiErrorDiagnostics.safeSummary(400, body)

        assertTrue(summary.contains("status=400"))
        assertTrue(summary.contains("error=invalid_request"))
        assertTrue(summary.contains("description=client_secret is missing."))
    }
}
