/*
 * PolicySectionsTest.kt
 * Unit tests for the policy text shown on the Policy screen (US 01.11.01, mockup E21).
 * Known issues: none.
 */
package com.example.vipers.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicySectionsTest {

    @Test
    fun sections_matchTheMockupHeadingsInOrder() {
        assertEquals(
            listOf("How the lottery works", "Reserving", "Fair use"),
            POLICY_SECTIONS.map { it.title },
        )
    }

    @Test
    fun sections_eachHaveAtLeastOneParagraph() {
        POLICY_SECTIONS.forEach { section ->
            assertTrue(section.title, section.paragraphs.isNotEmpty())
        }
    }

    @Test
    fun sections_haveNoBlankParagraphs() {
        POLICY_SECTIONS.flatMap { it.paragraphs }.forEach { paragraph ->
            assertTrue(paragraph.isNotBlank())
        }
    }

    @Test
    fun sections_haveNoRepeatedParagraphs() {
        val paragraphs = POLICY_SECTIONS.flatMap { it.paragraphs }

        assertEquals(paragraphs.size, paragraphs.toSet().size)
    }
}
