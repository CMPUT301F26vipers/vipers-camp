/*
 * LotteryMappingTest.kt
 * Unit tests for Lottery.toMap(), ensuring all fields including registration/booking
 * periods, waitlist capacity, and nights description serialize cleanly to Firestore fields.
 * Known issues: reading a document back (toLottery) needs a real DocumentSnapshot, so it's
 * covered by running the app against Firestore / instrumented tests, not here.
 */
package com.example.vipers.data.firestore

import com.example.vipers.domain.model.Lottery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LotteryMappingTest {

    private val lottery = Lottery(
        id = "lottery-1",
        ownerId = "owner-100",
        campsiteIds = listOf("camp-1", "camp-2"),
        nightsOffered = listOf("2027-05-01", "2027-05-02"),
        nightsDescription = "May 2027 weekends",
        registrationStart = 1000000000000L,
        registrationEnd = 1000003600000L,
        bookingStart = 1000003600000L,
        bookingEnd = 1000086400000L,
        winnersCount = 3,
        waitlistCapacity = 25,
        drawnAt = null,
        removed = false
    )

    @Test
    fun toMap_serializesAllFieldsCorrectly() {
        val map = lottery.toMap()

        assertEquals("lottery-1", map["id"])
        assertEquals("owner-100", map["ownerId"])
        assertEquals(listOf("camp-1", "camp-2"), map["campsiteIds"])
        assertEquals(listOf("2027-05-01", "2027-05-02"), map["nightsOffered"])
        assertEquals("May 2027 weekends", map["nightsDescription"])
        assertEquals(1000000000000L, map["registrationStart"])
        assertEquals(1000003600000L, map["registrationEnd"])
        assertEquals(1000003600000L, map["bookingStart"])
        assertEquals(1000086400000L, map["bookingEnd"])
        assertEquals(3, map["winnersCount"])
        assertEquals(25, map["waitlistCapacity"])
        assertNull(map["drawnAt"])
        assertEquals(false, map["removed"])
        assertEquals(0L, map["entriesVersion"])
        assertNull(map["committedDrawId"])
        assertEquals(0, map["manifestChunkCount"])
        assertEquals(0, map["participantCount"])
        assertEquals(0, map["winnerCount"])
    }

    @Test
    fun toMap_serializesNullWaitlistCapacityWhenNoLimitSpecified() {
        val noLimitLottery = lottery.copy(waitlistCapacity = null, nightsDescription = null)
        val map = noLimitLottery.toMap()

        assertNull(map["waitlistCapacity"])
        assertNull(map["nightsDescription"])
    }
}
