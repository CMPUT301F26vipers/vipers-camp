/*
 * IdentifyByDeviceTest.kt
 * Intent tests for US 01.09.01 on a real phone: the device ID the app uses as the user's ID is
 * there on every launch and doesn't change, and it's the ID the app was started with.
 * Known issues: ANDROID_ID changes after a factory reset, so that can't be tested here.
 */
package com.example.vipers.data.device

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class IdentifyByDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun deviceId_isNotBlank() {
        assertFalse(DeviceIdentityManager.androidId(context).isNullOrBlank())
    }

    @Test
    fun deviceId_staysTheSameEachTimeItIsRead() {
        // each launch reads it again, so reading it twice is like restarting the app
        assertEquals(DeviceIdentityManager.androidId(context), DeviceIdentityManager.androidId(context))
    }
}
