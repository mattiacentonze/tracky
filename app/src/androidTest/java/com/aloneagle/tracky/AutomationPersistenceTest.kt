package com.aloneagle.tracky

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

class AutomationPersistenceTest {
    private lateinit var handle: java.io.Closeable

    private val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    private val databaseRule = object : ExternalResource() {
        override fun before() {
            handle = TestDatabaseSeeder.clearAndSeed(
                sampleTrackerName = "Office Tag",
                includeAutomation = true,
            )
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
    fun storedAutomationSurvivesNavigationRoundTrip() {
        composeRule.onNodeWithText("Automations").performClick()
        composeRule.onNodeWithText("Office Tag leaves 8 m").assertIsDisplayed()
        composeRule.onNodeWithText("Send a notification").assertIsDisplayed()

        composeRule.onNodeWithText("Devices").performClick()
        composeRule.onNodeWithText("Find a device").assertIsDisplayed()
        composeRule.onNodeWithText("Automations").performClick()

        composeRule.onNodeWithText("Office Tag leaves 8 m").assertIsDisplayed()
        composeRule.onNodeWithText("Monitoring").assertIsDisplayed()
    }
}
