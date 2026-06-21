package com.aiquota.mobile.providers

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.concurrent.thread

class GlmUsageFetcherTest {
    @Test
    fun retriesWithoutBearerAfterBearerUnauthorizedResponse() {
        val authorizationHeaders = mutableListOf<String?>()
        val server = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress("127.0.0.1", 0))
        }
        val serverThread = thread(name = "glm-usage-fetcher-test-server") {
            repeat(2) { index ->
                server.accept().use { socket ->
                    val request = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8)
                    var authorization: String? = null
                    while (true) {
                        val line = request.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Authorization:", ignoreCase = true)) {
                            authorization = line.substringAfter(":").trim()
                        }
                    }
                    authorizationHeaders += authorization
                    if (index == 0) {
                        socket.getOutputStream().writeHttpResponse(
                            status = HttpURLConnection.HTTP_UNAUTHORIZED,
                            reason = "Unauthorized",
                            body = ByteArray(0)
                        )
                    } else {
                        socket.getOutputStream().writeHttpResponse(
                            status = HttpURLConnection.HTTP_OK,
                            reason = "OK",
                            body = quotaBody()
                        )
                    }
                }
            }
        }

        try {
            val result = GlmUsageFetcher.fetchUsagePayload(
                apiKey = "zai_test_key",
                endpointUrl = "http://127.0.0.1:${server.localPort}/quota"
            )

            assertEquals("ok", result.diagnostic)
            assertFalse(result.requiresAuth)
            assertNotNull(result.payload)
            assertEquals(
                listOf("Bearer zai_test_key", "zai_test_key"),
                authorizationHeaders
            )
        } finally {
            server.close()
            serverThread.join(1_000L)
        }
    }

    @Test
    fun retriesWithoutBearerWhenBearerResponseOnlyContainsMcpQuota() {
        val authorizationHeaders = mutableListOf<String?>()
        val server = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress("127.0.0.1", 0))
        }
        val serverThread = thread(name = "glm-usage-fetcher-token-fallback-test-server") {
            repeat(2) { index ->
                server.accept().use { socket ->
                    val request = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8)
                    var authorization: String? = null
                    while (true) {
                        val line = request.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Authorization:", ignoreCase = true)) {
                            authorization = line.substringAfter(":").trim()
                        }
                    }
                    authorizationHeaders += authorization
                    socket.getOutputStream().writeHttpResponse(
                        status = HttpURLConnection.HTTP_OK,
                        reason = "OK",
                        body = if (index == 0) mcpOnlyQuotaBody() else quotaBody()
                    )
                }
            }
        }

        try {
            val result = GlmUsageFetcher.fetchUsagePayload(
                apiKey = "zai_test_key",
                endpointUrl = "http://127.0.0.1:${server.localPort}/quota"
            )

            assertEquals("ok", result.diagnostic)
            assertFalse(result.requiresAuth)
            assertNotNull(result.payload)
            assertEquals(
                listOf("Bearer zai_test_key", "zai_test_key"),
                authorizationHeaders
            )
            org.junit.Assert.assertTrue(result.payload!!.contains("TOKENS_LIMIT"))
        } finally {
            server.close()
            serverThread.join(1_000L)
        }
    }

    private fun java.io.OutputStream.writeHttpResponse(status: Int, reason: String, body: ByteArray) {
        val headers = buildString {
            append("HTTP/1.1 ")
            append(status)
            append(" ")
            append(reason)
            append("\r\nContent-Type: application/json\r\nContent-Length: ")
            append(body.size)
            append("\r\nConnection: close\r\n\r\n")
        }.toByteArray(StandardCharsets.US_ASCII)
        write(headers)
        write(body)
        flush()
    }

    private fun quotaBody(): ByteArray {
        return """
            {
              "code": 200,
              "msg": "success",
              "data": {
                "limits": [
                  {
                    "type": "TOKENS_LIMIT",
                    "usage": 100,
                    "currentValue": 10,
                    "percentage": 10
                  }
                ]
              }
            }
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)
    }

    private fun mcpOnlyQuotaBody(): ByteArray {
        return """
            {
              "code": 200,
              "msg": "success",
              "data": {
                "limits": [
                  {
                    "type": "TIME_LIMIT",
                    "usage": 500,
                    "currentValue": 125,
                    "percentage": 25
                  }
                ]
              }
            }
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)
    }
}
