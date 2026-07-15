package com.fliptofocus.lock

/**
 * The "secret remote sequence" unlock: a sequence of D-pad directions the parent sets, entered on
 * the remote and confirmed with the center button. Only directions form the sequence; center means
 * "submit", so no length ever needs to be stored (which also keeps it out of the database).
 */
object Combo {
    const val UP = 0
    const val DOWN = 1
    const val LEFT = 2
    const val RIGHT = 3

    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 12

    /** Canonical string form used for hashing/comparison, e.g. [UP, UP, DOWN] -> "0,0,1". */
    fun encode(sequence: List<Int>): String = sequence.joinToString(",")

    /** Arrow glyph for on-screen feedback. */
    fun symbol(direction: Int): String = when (direction) {
        UP -> "↑"
        DOWN -> "↓"
        LEFT -> "←"
        RIGHT -> "→"
        else -> "•"
    }
}
