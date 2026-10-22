package com.parentalcontrol.child.manager

import java.security.MessageDigest

/**
 * Hash PIN dengan salt (SHA-256). PIN 4 digit hanya punya 10.000 kemungkinan, jadi ini mencegah PIN
 * terbaca polos di database, bukan melawan serangan brute force. Diduplikasi di parent-app.
 */
object PinHasher {

    fun hash(pin: String, salt: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$salt:$pin".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun verify(pin: String, salt: String, expectedHash: String): Boolean =
        MessageDigest.isEqual(hash(pin, salt).toByteArray(), expectedHash.toByteArray())
}
