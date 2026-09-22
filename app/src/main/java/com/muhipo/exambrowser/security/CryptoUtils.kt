package com.muhipo.exambrowser.security

import java.security.MessageDigest
import java.security.SecureRandom

object CryptoUtils {

    fun generateSalt(): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(16)
        random.nextBytes(saltBytes)
        return bytesToHex(saltBytes)
    }

    fun hashPin(pin: String, salt: String): String {
        val input = "$salt:$pin"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytesToHex(digest)
    }

    fun verifyPin(pin: String, salt: String, expectedHash: String): Boolean {
        val computedHash = hashPin(pin, salt)
        return computedHash.equals(expectedHash, ignoreCase = true)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}
