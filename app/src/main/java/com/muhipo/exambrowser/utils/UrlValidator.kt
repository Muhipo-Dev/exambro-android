package com.muhipo.exambrowser.utils

import android.net.Uri

object UrlValidator {

    /**
     * Normalizes any input text (scanned QR code or manual input) into a valid http:// or https:// URL.
     * Automatically handles raw IP addresses (e.g. "192.168.1.100", "192.168.1.100:8080/cbt"),
     * domain names, or existing URLs.
     * Returns the normalized URL string if valid, or null if invalid.
     */
    fun normalizeAndValidateUrl(inputString: String?): String? {
        if (inputString.isNullOrBlank()) return null
        var trimmed = inputString.trim()

        // If scheme is missing, prepend http:// by default for IP / server addresses
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed = "http://$trimmed"
        }

        return try {
            val uri = Uri.parse(trimmed)
            val host = uri.host
            if (!host.isNullOrBlank() && (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true))) {
                trimmed
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks if a string is a valid HTTP or HTTPS URL (including raw IP addresses).
     */
    fun isValidHttpUrl(urlString: String?): Boolean {
        return normalizeAndValidateUrl(urlString) != null
    }

    /**
     * Extracts lowercase host or IP address from a URL string.
     */
    fun extractHost(urlString: String?): String? {
        val normalized = normalizeAndValidateUrl(urlString) ?: return null
        return try {
            val uri = Uri.parse(normalized)
            uri.host?.lowercase()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks if a target host is a private LAN IP address (192.168.x.x, 10.x.x.x, 172.16-31.x.x, localhost, etc.).
     */
    fun isPrivateLocalHost(host: String): Boolean {
        if (host == "localhost" || host == "127.0.0.1") return true
        if (host.startsWith("192.168.") || host.startsWith("10.")) return true
        if (host.startsWith("172.")) {
            val parts = host.split(".")
            if (parts.size >= 2) {
                val secondByte = parts[1].toIntOrNull()
                if (secondByte != null && secondByte in 16..31) return true
            }
        }
        return false
    }

    /**
     * Checks if a target URL's host matches the allowed whitelist.
     * Supports exact IP/domain match, subdomain matching, and private local IP access.
     */
    fun isUrlAllowed(targetUrl: String?, allowedDomains: Set<String>): Boolean {
        if (targetUrl.isNullOrBlank()) return false
        val targetHost = extractHost(targetUrl) ?: return false

        // Automatically allow private local LAN IPs for offline intranet exam servers
        if (isPrivateLocalHost(targetHost)) return true

        // If no whitelist is defined, allow default
        if (allowedDomains.isEmpty()) return true

        for (allowed in allowedDomains) {
            val cleanAllowed = allowed.lowercase().trim()
            if (cleanAllowed.isEmpty()) continue
            // Exact match (IP address or domain)
            if (targetHost == cleanAllowed) return true
            // Subdomain match (e.g. static.smamuh1ponorogo.sch.id matches smamuh1ponorogo.sch.id)
            if (targetHost.endsWith(".$cleanAllowed")) return true
        }

        return false
    }
}
