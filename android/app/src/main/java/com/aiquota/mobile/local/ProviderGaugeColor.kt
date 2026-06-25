package com.aiquota.mobile.local

import android.graphics.Color
import java.util.Locale

object ProviderGaugeColor {
    val palette: List<String> = listOf(
        "#D1775E",
        "#E15A4F",
        "#F06A9B",
        "#C95EC8",
        "#8E6BC8",
        "#6554C0",
        "#4F6FA8",
        "#3D8BFF",
        "#2FA7D7",
        "#3A9EB8",
        "#19A7A0",
        "#2F9E71",
        "#4CAF50",
        "#7CB342",
        "#A3A847",
        "#D6A23F",
        "#F59E0B",
        "#E67E22",
        "#B85C7A",
        "#9B6A4D",
        "#6F6F6F",
        "#555B6E",
        "#2F3A4A",
        "#111827"
    )

    fun normalize(input: String?): String? {
        val value = input?.trim().orEmpty()
        if (value.isBlank()) return null

        HEX_PATTERN.matchEntire(value)?.let { match ->
            return "#${match.groupValues[1].uppercase(Locale.US)}"
        }

        RGB_PATTERN.matchEntire(value)?.let { match ->
            val red = match.groupValues[1].toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            val green = match.groupValues[2].toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            val blue = match.groupValues[3].toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            return "#%02X%02X%02X".format(Locale.US, red, green, blue)
        }

        return null
    }

    fun toArgbOrNull(hexColor: String?): Int? {
        val normalized = normalize(hexColor) ?: return null
        return runCatching { Color.parseColor(normalized) }.getOrNull()
    }

    private val HEX_PATTERN = Regex("""^#?([0-9a-fA-F]{6})$""")
    private val RGB_PATTERN = Regex("""^(?:rgb\()?[\s]*(\d{1,3})[\s]*,[\s]*(\d{1,3})[\s]*,[\s]*(\d{1,3})[\s]*\)?$""")
}
