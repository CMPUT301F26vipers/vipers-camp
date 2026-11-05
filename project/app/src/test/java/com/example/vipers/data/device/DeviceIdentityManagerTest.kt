/*
 * DeviceIdentityManagerTest.kt
 * Unit tests for DeviceIdentityManager (US 01.09.01): the user is recognized by their device ID,
 * and that decides whether the app opens on Welcome or Home.
 * Known issues: reading the real ANDROID_ID needs a phone, so it's tested in IdentifyByDeviceTest.
 */
package com.example.vipers.data.device

import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.domain.model.UserProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdentityManagerTest {

    private val maya = UserProfile(id = "my-phone", name = "Maya Chen")
    private val sam = UserProfile(id = "other-phone", name = "Sam Lee")

    @Test
    fun hasProfile_isFalseOnFirstLaunch() = runBlocking {
        val manager = DeviceIdentityManager("my-phone", FakeProfileRepository())
        assertFalse(manager.hasProfile())
        assertNull(manager.currentProfile())
    }

    @Test
    fun hasProfile_isTrueOnceThisDeviceHasAProfile() = runBlocking {
        val manager = DeviceIdentityManager("my-phone", FakeProfileRepository(listOf(maya)))
        assertTrue(manager.hasProfile())
    }

    @Test
    fun currentProfile_isThisDevicesProfileNotSomeoneElses() = runBlocking {
        val manager = DeviceIdentityManager("my-phone", FakeProfileRepository(listOf(maya, sam)))
        assertEquals("Maya Chen", manager.currentProfile()?.name)
    }

    @Test
    fun anotherDevice_isADifferentUser() = runBlocking {
        val manager = DeviceIdentityManager("new-phone", FakeProfileRepository(listOf(maya, sam)))
        assertFalse(manager.hasProfile())
    }

    @Test
    fun sameDeviceId_isRecognizedAgainLater() = runBlocking {
        // like closing and reopening the app: a new manager, same ID, same saved profiles
        val repo = FakeProfileRepository()
        repo.saveProfile(maya)
        val afterRestart = DeviceIdentityManager("my-phone", repo)
        assertEquals("Maya Chen", afterRestart.currentProfile()?.name)
    }
}
