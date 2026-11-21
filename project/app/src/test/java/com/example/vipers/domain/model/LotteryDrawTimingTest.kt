/*
 * LotteryDrawTimingTest.kt
 * Unit tests for when a lottery can be drawn (US 02.05.01b): the owner can draw once registration
 * has started, even before it ends, and the app draws it on its own only after it ends.
 * Known issues: none.
 */
package com.example.vipers.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LotteryDrawTimingTest {

    // registration runs from 1000 to 2000, and booking from 3000 to 4000
    private val may = Lottery(
        id = "may",
        ownerId = "owner-priya",
        registrationStart = 1000,
        registrationEnd = 2000,
        bookingStart = 3000,
        bookingEnd = 4000,
        winnersCount = 2,
    )

    @Test
    fun canOwnerDraw_whileRegistrationIsOpen_isTrue() {
        assertTrue(may.canOwnerDraw(now = 1500))
    }

    @Test
    fun canOwnerDraw_afterRegistrationEnds_isTrue() {
        assertTrue(may.canOwnerDraw(now = 2500))
    }

    @Test
    fun canOwnerDraw_beforeRegistrationStarts_isFalse() {
        assertFalse(may.canOwnerDraw(now = 500))
    }

    @Test
    fun canOwnerDraw_onceDrawn_isFalse() {
        assertFalse(may.copy(drawnAt = 2100).canOwnerDraw(now = 2500))
    }

    @Test
    fun canOwnerDraw_whenRemoved_isFalse() {
        assertFalse(may.copy(removed = true).canOwnerDraw(now = 2500))
    }

    @Test
    fun canOwnerDraw_afterTheBookingPeriodEnds_isFalse() {
        // the winners couldn't reserve anything any more
        assertFalse(may.canOwnerDraw(now = 4500))
    }

    @Test
    fun canOwnerDraw_withNoRegistrationTimes_isFalse() {
        assertFalse(Lottery(id = "blank").canOwnerDraw(now = 2500))
    }

    @Test
    fun isDueForDraw_afterRegistrationEnds_isTrue() {
        assertTrue(may.isDueForDraw(now = 2001))
    }

    @Test
    fun isDueForDraw_whileRegistrationIsOpen_isFalse() {
        assertFalse(may.isDueForDraw(now = 1500))
    }

    @Test
    fun isDueForDraw_atTheExactEndTime_isFalse() {
        // registration is still open at its end time
        assertFalse(may.isDueForDraw(now = 2000))
    }

    @Test
    fun isDueForDraw_onceDrawn_isFalse() {
        assertFalse(may.copy(drawnAt = 2100).isDueForDraw(now = 2500))
    }
}
