package io.ledge.sdk.hash

import java.security.MessageDigest

object ContextHasher {

    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun hashContentBlocks(blocks: List<Map<String, Any?>>): String {
        val canonical = blocks.joinToString("|") { block ->
            "${block["role"]}:${block["content"]}"
        }
        return sha256(canonical)
    }
}
