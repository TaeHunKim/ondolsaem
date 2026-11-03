package io.github.taehunkim.ondolsaem

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.taehunkim.ondolsaem.sdk.OndolSaem.Categories
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The first-run screens: four steps, then the tabs; once done they do not come back. */
@RunWith(AndroidJUnit4::class)
class OnboardingTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val settings get() = (rule.activity.application as OndolSaemApp).container.settings
    private var saved = true

    @Before fun showAgain() = runBlocking<Unit> {
        saved = settings.onboardingDone.first()
        settings.setOnboardingDone(false)
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("onboarding").assertIsDisplayed() }.isSuccess }
    }

    @After fun restore() = runBlocking<Unit> { settings.setOnboardingDone(saved) }

    @Test fun fourStepsLeadToTheTabs() {
        rule.onNodeWithText("Step 1 of 4").assertIsDisplayed()
        rule.onNodeWithText("Welcome to OndolSaem").assertIsDisplayed()
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Apps on this phone").assertIsDisplayed()
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Recommended models").assertIsDisplayed()
        rule.onNodeWithTag("onb_cat_${Categories.EMBEDDING}").assertIsOn()
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Notifications").assertIsDisplayed()
        rule.onNodeWithTag("onb_finish").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("tab_0").assertIsDisplayed() }.isSuccess }
        assertTrue(runBlocking { settings.onboardingDone.first() })
    }
}
