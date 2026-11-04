/*
 * UserProfileTest.kt
 * Unit tests for profile-editing validation.
 * Known issues: none.
 */
package com.example.vipers.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserProfileTest {

    @Test
    fun validateEdit_acceptsValidName() {
        val profile = UserProfile(
            id = "device123",
            name = "Sam Patel",
            email = "sam@example.com",
            phone = "7805551234"
        )

        assertTrue(profile.validateEdit().isEmpty())
    }

    @Test
    fun validateEdit_rejectsBlankName() {
        val profile = UserProfile(name = "   ")

        assertEquals(
            "Name is required",
            profile.validateEdit()["name"]
        )
    }

    @Test
    fun validateEdit_allowsOptionalContactFields() {
        val profile = UserProfile(
            name = "Sam Patel",
            email = "",
            phone = ""
        )

        assertTrue(profile.validateEdit().isEmpty())
    }
}
