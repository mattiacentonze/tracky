package com.aloneagle.tracky

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

class LaunchTest {
    private val databaseRule = object : ExternalResource() {
        override fun before() {
            TestDatabaseSeeder.clearDatabase()
        }

        override fun after() {
            TestDatabaseSeeder.clearDatabase()
        }
    }

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(databaseRule).around(composeRule)

    @Test
    fun devicesIsTheStartDestination() {
        composeRule.onNodeWithText("Find a device").assertIsDisplayed()
        composeRule.onNodeWithText("Devices").assertIsDisplayed()
        composeRule.onNodeWithText("Automations").assertIsDisplayed()
    }
}
