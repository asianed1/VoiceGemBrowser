package com.example.security

import java.net.InetAddress
import java.net.URI

/**
 * Validates external media, podcast, and RSS URLs to block cleartext HTTP,
 * local-file URIs, and private-network SSRF targets without adding external dependencies.
 */
object UrlSecurityGuard {

    fun isSafeHttpsUrl(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty() || trimmed.length > 2048) return false

        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return false
        }

        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "https") return false

        val host = uri.host?.lowercase()?.trim() ?: return false
        if (host.isEmpty()) return false

        if (isDisallowedHost(host)) return false

        return true
    }

    internal fun isDisallowedHost(host: String): Boolean {
        if (host == "localhost" ||
            host.endsWith(".localhost") ||
            host.endsWith(".local") ||
            host.endsWith(".internal")
        ) {
            return true
        }

        // Check numeric IPv4/IPv6 literals synchronously (never does DNS lookup for hostnames)
        if (isIpLiteral(host)) {
            return try {
                val addr = InetAddress.getByName(host)
                addr.isAnyLocalAddress ||
                    addr.isLoopbackAddress ||
                    addr.isLinkLocalAddress ||
                    addr.isSiteLocalAddress ||
                    addr.isMulticastAddress
            } catch (_: Exception) {
                true
            }
        }
        return false
    }

    private fun isIpLiteral(host: String): Boolean {
        val cleaned = host.removePrefix("[").removeSuffix("]")
        val ipv4Regex = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
        return ipv4Regex.matches(cleaned) || cleaned.contains(":")
    }

    fun sanitizeHttpsUrlOrNull(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        return if (isSafeHttpsUrl(trimmed)) trimmed else null
    }
}
