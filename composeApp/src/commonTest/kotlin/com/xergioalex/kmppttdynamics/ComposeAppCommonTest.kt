package com.xergioalex.kmppttdynamics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ComposeAppCommonTest {

    @Test
    fun joinCodeIsSixCharsAlphanumeric() {
        val code = JoinCodeGenerator.generate()
        assertEquals(6, code.length)
        assertTrue(code.all { it.isLetterOrDigit() && (it.isDigit() || it.isUpperCase()) })
    }

    @Test
    fun joinCodeAvoidsConfusingGlyphs() {
        // We strip 0/O/1/I to keep codes readable on stage projection.
        repeat(200) {
            val code = JoinCodeGenerator.generate()
            assertTrue(code.none { it in "01OI" }, "code $code contains a confusing glyph")
        }
    }

    @Test
    fun successiveCodesDiffer() {
        // Not a guarantee in theory, but a regression test against a broken RNG seed.
        assertNotEquals(JoinCodeGenerator.generate(), JoinCodeGenerator.generate())
    }
}
