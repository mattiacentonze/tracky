package com.aloneagle.tracky

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

class RenamePersistenceTest {
    private lateinit var handle: java.io.Closeable

    private val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    private val databaseRule = object : ExternalResource() {
        override fun before() {
            handle = TestDatabaseSeeder.clearAndSeed(sampleTrackerName = "Bag Tag")
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
    fun finderRenamePersistsBackToDevices() {
        composeRule.onNodeWithText("Bag Tag").assertIsDisplayed()
        composeRule.onNodeWithText("Find").performClick()
        composeRule.onNodeWithText("FINDING").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Rename").performClick()
        composeRule.onNodeWithText("Display name").performTextClearance()
        composeRule.onNodeWithText("Display name").performTextInput("Laptop Sleeve")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Laptop Sleeve").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Find a device").assertIsDisplayed()
        composeRule.onNodeWithText("Laptop Sleeve").assertIsDisplayed()
    }
}
