package com.aloneagle.tracky

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

class NavigationSmokeTest {
    private lateinit var handle: java.io.Closeable

    private val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    private val databaseRule = object : ExternalResource() {
        override fun before() {
            handle = TestDatabaseSeeder.clearAndSeed()
        }

        override fun after() {
            handle.close()
            TestDatabaseSeeder.clearDatabase()
        }
    }

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(permissionRule).around(databaseRule).around(composeRule)

    @Test
    fun canNavigateBetweenDevicesAutomationsAndSettings() {
        composeRule.onNodeWithText("Find a device").assertIsDisplayed()

        composeRule.onNodeWithText("Automations").performClick()
        composeRule.onNodeWithText("Let distance do the work").assertIsDisplayed()
        composeRule.onNodeWithText("No automations yet").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("New automation").performClick()
        composeRule.onNodeWithText("Office Tag").assertIsDisplayed()
        composeRule.onNodeWithText("Enters range").assertIsDisplayed()
        composeRule.onNodeWithText("Leaves range").assertIsDisplayed()
        composeRule.onNodeWithText("Wi-Fi panel").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("WhatsApp draft").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Telegram draft").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("BLE limits").assertIsDisplayed()

        composeRule.onNodeWithText("Devices").performClick()
        composeRule.onNodeWithText("Find a device").assertIsDisplayed()
    }
}
