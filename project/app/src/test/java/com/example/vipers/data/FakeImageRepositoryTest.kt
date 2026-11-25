/*
 * FakeImageRepositoryTest.kt
 * Checks that FakeImageRepository behaves like the Firestore one (US 02.01.02b), so other tests
 * can trust it.
 * Known issues: none.
 */
package com.example.vipers.data

import com.example.vipers.data.fake.FakeImageRepository
import com.example.vipers.domain.model.ImageAsset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class FakeImageRepositoryTest {

    private val hintonPhoto = ImageAsset(
        data = "/9j/4AAQSkZJRg==",
        uploaderId = "priya-phone",
        ownerId = "hinton",
        label = "Hinton Ridge Campground",
        createdAt = 1_809_000_000_000,
    )

    @Test
    fun addImage_givesAnIdAndCanBeLoaded() = runBlocking {
        val repo = FakeImageRepository()

        val id = repo.addImage(hintonPhoto)

        assertEquals(hintonPhoto.copy(id = id), repo.getImage(id))
    }

    @Test
    fun addImage_twice_givesDifferentIds() = runBlocking {
        val repo = FakeImageRepository()

        assertNotEquals(repo.addImage(hintonPhoto), repo.addImage(hintonPhoto))
    }

    @Test
    fun addImage_tooBig_throwsAndSavesNothing() = runBlocking {
        val repo = FakeImageRepository()
        val tooBig = hintonPhoto.copy(data = "a".repeat(ImageAsset.MAX_DATA_LENGTH + 1))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.addImage(tooBig) }
        }
        assertTrue(repo.getAllImages().isEmpty())
    }

    @Test
    fun addImage_whenFailing_throwsAndSavesNothing() = runBlocking {
        val repo = FakeImageRepository(failAdds = true)

        assertThrows(IOException::class.java) {
            runBlocking { repo.addImage(hintonPhoto) }
        }
        assertTrue(repo.getAllImages().isEmpty())
    }

    @Test
    fun getImage_isNullWhenMissing() = runBlocking {
        assertNull(FakeImageRepository().getImage("nope"))
    }

    @Test
    fun getAllImages_listsNewestFirst() = runBlocking {
        val older = hintonPhoto.copy(id = "older", createdAt = 1_809_000_000_000)
        val newer = hintonPhoto.copy(id = "newer", createdAt = 1_809_000_500_000)
        val repo = FakeImageRepository(listOf(older, newer))

        assertEquals(listOf("newer", "older"), repo.getAllImages().map { it.id })
    }

    @Test
    fun deleteImage_removesIt() = runBlocking {
        val repo = FakeImageRepository(listOf(hintonPhoto.copy(id = "photo-1")))

        repo.deleteImage("photo-1")

        assertNull(repo.getImage("photo-1"))
        assertEquals(listOf("photo-1"), repo.deletedIds)
    }

    @Test
    fun deleteImage_missing_doesNothing() = runBlocking {
        val repo = FakeImageRepository(listOf(hintonPhoto.copy(id = "photo-1")))

        repo.deleteImage("nope")

        assertEquals(1, repo.getAllImages().size)
    }
}
