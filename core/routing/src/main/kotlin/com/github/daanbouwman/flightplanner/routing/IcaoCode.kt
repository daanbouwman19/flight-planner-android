package com.github.daanbouwman.flightplanner.routing

/**
 * Packs a four-character airport code into a single `Int`.
 *
 * Every code in the shipped dataset is exactly four characters drawn from
 * `A-Z0-9` — the ETL enforces it and the asset verifier asserts it — which is 36
 * possibilities per position and so fits in 24 bits.
 *
 * This matters because reading a text column out of SQLite costs roughly three
 * times what an integer column costs (JNI crossing plus a UTF-8 decode plus an
 * allocation), and the index holds 24,000 of them. Storing codes as integers
 * removes a text column from the startup read and 24,000 `String` allocations
 * with it, and makes lookup a binary search over an `IntArray` rather than a
 * `HashMap<String, Int>` with boxed values.
 *
 * Codes are only turned back into text when one is actually displayed.
 */
object IcaoCode {

    /** Sentinel for a code that cannot be represented. */
    const val INVALID: Int = -1

    private const val BASE = 36
    private const val LENGTH = 4

    /**
     * Encodes a four-character code, or returns [INVALID].
     *
     * Deliberately strict: silently accepting a malformed code would corrupt
     * lookups in a way that only shows up as a missing airport much later.
     */
    fun encode(code: String): Int {
        if (code.length != LENGTH) return INVALID
        var packed = 0
        for (i in 0 until LENGTH) {
            val symbol = symbolOf(code[i])
            if (symbol < 0) return INVALID
            packed = packed * BASE + symbol
        }
        return packed
    }

    /** Decodes a packed code. Allocates, so call it only for display. */
    fun decode(packed: Int): String {
        if (packed < 0) return ""
        val chars = CharArray(LENGTH)
        var remaining = packed
        for (i in LENGTH - 1 downTo 0) {
            chars[i] = CHARACTERS[remaining % BASE]
            remaining /= BASE
        }
        return String(chars)
    }

    private fun symbolOf(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'A'..'Z' -> c - 'A' + 10
        in 'a'..'z' -> c - 'a' + 10
        else -> -1
    }

    private val CHARACTERS = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
        'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
    )
}
