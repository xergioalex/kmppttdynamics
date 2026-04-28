package com.xergioalex.kmppttdynamics

import kotlin.random.Random

/**
 * Generates 6-character join codes for meetup rooms.
 *
 * Excludes 0/O and 1/I because they are visually ambiguous when the host
 * projects the code on stage and someone two rows back has to type it.
 */
object JoinCodeGenerator {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generate(length: Int = 6, random: Random = Random.Default): String =
        buildString(length) {
            repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
}
