package com.github.wlezzar.doks.utils

import java.math.BigInteger
import java.security.MessageDigest

object Hasher {
    fun hash(vararg parts: String): String =
        MessageDigest
            .getInstance("MD5")
            .apply {
                parts.forEach { update(it.toByteArray()) }
            }
            .digest()
            .let { BigInteger(1, it).toString() }

    fun hashMap(map: Map<String, String>): String = hash(
        map.entries.map { "${it.key}/${it.value}" }.sorted().joinToString(separator = ",")
    )
}