package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("VoiceGemBrowser", appName)
  }

  @Test
  fun `verify startup greeting string exists`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val greeting = context.getString(R.string.startup_greeting)
    assertEquals("How may I help you today?", greeting)
  }
}
