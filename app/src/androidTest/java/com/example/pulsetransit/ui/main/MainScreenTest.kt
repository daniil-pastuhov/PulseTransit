package com.example.pulsetransit.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.material3.Text
import org.junit.Rule
import org.junit.Test

/** UI tests for PulseTransit. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun placeholder_test() {
    composeTestRule.setContent {
        Text("PulseTransit Test")
    }
    composeTestRule.onNodeWithText("PulseTransit Test").assertExists()
  }
}
