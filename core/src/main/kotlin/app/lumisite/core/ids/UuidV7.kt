package app.lumisite.core.ids

import java.security.SecureRandom
import java.util.UUID

/**
 * Injectable entropy source for id generation: each call returns pseudo-random bits, of which
 * only the low-order bits are consumed. Production uses [SecureRandom]; tests inject a fixed
 * value or a counter so generation is deterministic.
 */
fun interface Entropy {
    fun nextLong(): Long
}

/**
 * UUID version 7 (RFC 9562 §5.7): the 48 most significant bits carry the Unix timestamp in
 * milliseconds, then the version field (7), 12 random bits (rand_a), the RFC 4122 variant (10)
 * and 62 random bits (rand_b). Rendered in canonical 8-4-4-4-12 lowercase hex form.
 *
 * The timestamp leads the canonical string, so lexicographic order follows time as soon as
 * milliseconds differ; within one millisecond, random bits keep generated UUIDs distinct.
 */
object UuidV7 {
    private const val TIMESTAMP_MASK = 0x0000_FFFF_FFFF_FFFFL // 48 bits
    private const val VERSION_7 = 0x7L
    private const val RAND_A_MASK = 0x0FFFL // 12 bits
    private const val VARIANT_RFC4122 = Long.MIN_VALUE // top bits 10 of the least significant word
    private const val RAND_B_MASK = 0x3FFF_FFFF_FFFF_FFFFL // 62 bits

    private val CANONICAL_V7 =
        Regex("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")

    private val SECURE_RANDOM = SecureRandom()

    /** Production entropy; never used by tests (they inject their own). */
    val secure: Entropy = Entropy { SECURE_RANDOM.nextLong() }

    /** Canonical UUID v7 for instant [epochMs]; clock and entropy are injected. */
    fun generate(epochMs: Long, entropy: Entropy = secure): String {
        val msb = (epochMs and TIMESTAMP_MASK shl 16) or
            (VERSION_7 shl 12) or (entropy.nextLong() and RAND_A_MASK)
        val lsb = VARIANT_RFC4122 or (entropy.nextLong() and RAND_B_MASK)
        return UUID(msb, lsb).toString()
    }

    /** [s] itself if it is a canonical lowercase UUID v7, null otherwise (strict rejection). */
    fun parseOrNull(s: String): String? = s.takeIf { isUuidV7(it) }

    fun isUuidV7(s: String): Boolean = CANONICAL_V7.matches(s)

    /** Unix timestamp in ms carried by the top 48 bits, null if [s] is not a canonical v7. */
    fun timestampMsOrNull(s: String): Long? =
        parseOrNull(s)?.let { (it.substring(0, 8) + it.substring(9, 13)).toLong(16) }
}
