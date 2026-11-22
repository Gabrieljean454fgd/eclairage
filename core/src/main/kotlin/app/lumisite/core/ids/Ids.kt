package app.lumisite.core.ids

/**
 * Business ids of the form "prefix-uuid": the short prefix (ev, ses, tb, cal, srv…) stays
 * readable in journals while the full UUID v7 keeps its time ordering. Clock and entropy are
 * injectable for deterministic tests.
 */
object Ids {
    /** Production: system clock and secure entropy. */
    fun new(prefix: String): String = new(prefix, System.currentTimeMillis(), UuidV7.secure)

    /** Testable form: explicit clock reading and entropy source. */
    fun new(prefix: String, epochMs: Long, entropy: Entropy): String =
        "$prefix-" + UuidV7.generate(epochMs, entropy)
}
