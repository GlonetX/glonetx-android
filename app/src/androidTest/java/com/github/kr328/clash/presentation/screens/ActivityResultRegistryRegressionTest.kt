package com.github.kr328.clash.presentation.screens

import android.content.Intent
import android.view.ViewGroup
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.kr328.clash.LogcatService
import com.github.kr328.clash.MainActivity
import com.github.kr328.clash.R
import com.github.kr328.clash.remote.Remote
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityResultRegistryRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val profileName = "Activity result regression ${UUID.randomUUID()}"
    private var profileCreationStarted = false

    @After fun removeCreatedProfile() {
        compose.activity.stopService(Intent(compose.activity, LogcatService::class.java))
        if (!profileCreationStarted) return

        runBlocking {
            withTimeout(15_000) {
                val profiles = Remote.service.remote.get().profile()
                profiles.queryAll().filter { it.name == profileName }
                    .forEach { profiles.delete(it.uuid) }
            }
        }
    }

    @Test fun liveLogsDestinationKeepsActivityResultRegistryOwnerAvailable() {
        openSettings()
        openSettingsAction(R.string.cs_logs)

        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("Live logs").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Live logs").performClick()

        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("Search messages").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Search messages").assertIsDisplayed()
        compose.onNodeWithText("Pause").assertIsDisplayed()
    }

    @Test fun localConfigurationFilesDestinationKeepsActivityResultRegistryOwnerAvailable() {
        openSettings()
        openSettingsAction(R.string.cs_profiles)

        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithContentDescription("Add").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Add").performClick()
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2
        }

        compose.onNodeWithText("Name").performTextInput(profileName)
        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("Create local configuration"))
        profileCreationStarted = true
        compose.onNodeWithText("Create local configuration").performClick()

        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("Update interval (minutes, 0 disables)")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(hasScrollAction())[0]
            .performScrollToNode(hasText("Configuration and provider files"))
        compose.onNodeWithText("Configuration and provider files").performClick()

        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithContentDescription("Refresh").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Refresh").assertIsDisplayed()
    }

    @Test fun mainActivityCompositionProvidesAnActivityResultRegistryOwner() {
        val activity = compose.activity
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        var owner: ActivityResultRegistryOwner? = null
        val probe = ComposeView(activity).apply {
            setContent { owner = LocalActivityResultRegistryOwner.current }
        }

        try {
            compose.runOnUiThread { content.addView(probe) }
            compose.waitUntil(TIMEOUT_MILLIS) { owner != null }
            compose.runOnIdle { assertSame(activity, owner) }
        } finally {
            compose.runOnUiThread { content.removeView(probe) }
        }
    }

    private fun openSettings() {
        compose.onAllNodesWithText("Settings")[0].performClick()
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(compose.activity.getString(R.string.cs_logs))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openSettingsAction(title: Int) {
        val label = compose.activity.getString(title)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(label))
        compose.onNodeWithText(label).performClick()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
    }
}
