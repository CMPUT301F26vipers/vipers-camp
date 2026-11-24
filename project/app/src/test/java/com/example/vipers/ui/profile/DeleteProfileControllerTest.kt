/*
 * DeleteProfileControllerTest.kt
 * Unit tests for DeleteProfileController (US 01.08.01): the dialog warns about campsites only
 * when the user owns some, Cancel deletes nothing, Delete deletes the profile, and a failed
 * delete shows an error and keeps the profile. Uses the fake repositories.
 * Known issues: none.
 */
package com.example.vipers.ui.profile

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.ProfileRepository
import com.example.vipers.domain.service.DeletionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteProfileControllerTest {

    private val profiles = FakeProfileRepository(listOf(UserProfile(id = "sam", name = "Sam")))
    private val campsites = FakeCampsiteRepository()
    private val lotteries = FakeLotteryRepository()
    private val reservations = FakeReservationRepository(campsites, lotteries)

    @Before
    fun setUp() {
        // run viewModelScope work straight away in tests
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun controller(
        profileRepository: ProfileRepository = profiles,
        campsiteRepository: CampsiteRepository = campsites,
    ) = DeleteProfileController(
        DeletionService(profileRepository, campsiteRepository, lotteries, reservations),
        campsiteRepository,
        "sam",
    )

    @Test
    fun askToDelete_withNoCampsites_opensTheDialogWithoutTheCampsiteWarning() {
        val controller = controller()

        controller.askToDelete()

        assertTrue(controller.isConfirming)
        assertEquals(false, controller.ownsCampsites)
    }

    @Test
    fun askToDelete_whenTheyOwnCampsites_warnsAboutThem() = runBlocking {
        campsites.addCampsite(Campsite(id = "site-1", ownerId = "sam", name = "Lakeside"))
        val controller = controller()

        controller.askToDelete()

        assertTrue(controller.isConfirming)
        assertEquals(true, controller.ownsCampsites)
    }

    @Test
    fun askToDelete_whenTheCheckFails_stillOpensAndWarnsAnyway() {
        val failing = object : CampsiteRepository by campsites {
            override suspend fun getCampsitesByOwner(ownerId: String) =
                throw IOException("offline")
        }
        val controller = controller(campsiteRepository = failing)

        controller.askToDelete()

        assertTrue(controller.isConfirming)
        assertNull(controller.ownsCampsites)
    }

    @Test
    fun cancel_closesTheDialogAndDeletesNothing() = runBlocking {
        val controller = controller()
        controller.askToDelete()

        controller.cancel()

        assertFalse(controller.isConfirming)
        assertFalse(controller.deleted)
        assertNotNull(profiles.getProfile("sam"))
    }

    @Test
    fun confirm_deletesTheProfile() = runBlocking {
        val controller = controller()
        controller.askToDelete()

        controller.confirm()

        assertFalse(controller.isConfirming)
        assertTrue(controller.deleted)
        assertFalse(controller.isDeleting)
        assertNull(controller.deleteError)
        assertNull(profiles.getProfile("sam"))
    }

    @Test
    fun delete_whenItFails_showsAnErrorAndKeepsTheProfile() = runBlocking {
        val failing = object : ProfileRepository by profiles {
            override suspend fun deleteProfile(id: String, imageId: String?) =
                throw IOException("offline")
        }
        val controller = controller(profileRepository = failing)

        controller.delete()

        assertFalse(controller.deleted)
        assertFalse(controller.isDeleting)
        assertEquals(
            "Couldn't delete your profile. Check your connection and try again.",
            controller.deleteError,
        )
        assertNotNull(profiles.getProfile("sam"))
    }

    @Test
    fun askToDelete_afterAnError_clearsIt() = runBlocking {
        val failing = object : ProfileRepository by profiles {
            override suspend fun deleteProfile(id: String, imageId: String?) =
                throw IOException("offline")
        }
        val controller = controller(profileRepository = failing)
        controller.delete()
        assertNotNull(controller.deleteError)

        controller.askToDelete()

        assertNull(controller.deleteError)
    }
}
