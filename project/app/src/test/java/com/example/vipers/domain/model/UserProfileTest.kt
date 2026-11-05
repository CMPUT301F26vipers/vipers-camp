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

    @Test
    fun validateEdit_allowsBlankContactFieldsWithSpaces() {
        val profile = UserProfile(
            name = "Sam Patel",
            email = "   ",
            phone = "   "
        )

        assertTrue(profile.validateEdit().isEmpty())
    }

    @Test
    fun validateEdit_rejectsInvalidEmail() {
        val invalidEmails = listOf("plainaddress", "missingatsign.com", "@missingusername.com", "user@.com", "user@domain")
        for (invalid in invalidEmails) {
            val profile = UserProfile(name = "Sam Patel", email = invalid)
            val errors = profile.validateEdit()
            assertEquals("Expected error for email: $invalid", "Invalid email address", errors["email"])
        }
    }

    @Test
    fun validateEdit_acceptsValidEmailFormats() {
        val validEmails = listOf("sam@example.com", "user.name+tag@domain.co.uk", "first_last@sample.ca")
        for (valid in validEmails) {
            val profile = UserProfile(name = "Sam Patel", email = valid)
            assertTrue("Expected valid for email: $valid", profile.validateEdit().isEmpty())
        }
    }

    @Test
    fun validateEdit_rejectsInvalidPhone() {
        val invalidPhones = listOf("123", "abc", "phone-number", "12345")
        for (invalid in invalidPhones) {
            val profile = UserProfile(name = "Sam Patel", phone = invalid)
            val errors = profile.validateEdit()
            assertEquals("Expected error for phone: $invalid", "Invalid phone number", errors["phone"])
        }
    }

    @Test
    fun validateEdit_acceptsValidPhoneFormats() {
        val validPhones = listOf("7805551234", "780-555-0100", "+1 (780) 555-1234", "780 555 0100")
        for (valid in validPhones) {
            val profile = UserProfile(name = "Sam Patel", phone = valid)
            assertTrue("Expected valid for phone: $valid", profile.validateEdit().isEmpty())
        }
    }
}
