package com.github.kr328.clash

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.remote.StatusClient
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.settings.RuntimePhase
import com.github.kr328.clash.service.settings.SettingsSnapshot
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.cancel
import com.github.kr328.clash.remote.Remote
import java.net.InetAddress
import java.net.URI
import java.util.*
import com.github.kr328.clash.design.R

class ExternalControlActivity : Activity(), CoroutineScope by MainScope() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        pendingAction = savedInstanceState?.getString(STATE_PENDING_ACTION)
            ?.let { runCatching { PendingAction.valueOf(it) }.getOrNull() }

        launch { handleIntent() }
    }

    private suspend fun handleIntent() {
        val manager = try {
            withContext(Dispatchers.IO) { Remote.service.remote.get().settings() }
        } catch (e: Exception) {
            showStartFailure(e)
            finish()
            return
        }
        val settings = try {
            withContext(Dispatchers.IO) { manager.getSnapshot() }
        } catch (e: Exception) {
            showStartFailure(e)
            finish()
            return
        }
        val controlToken = try {
            withContext(Dispatchers.IO) { manager.getControlToken() }
        } catch (e: Exception) {
            showStartFailure(e)
            finish()
            return
        }
        // The app's own dynamic shortcuts carry a private token so they keep
        // working when external control is disabled. Other apps cannot read it.
        val trustedShortcut = controlToken.isNotBlank() &&
                intent.getStringExtra(EXTRA_CONTROL_TOKEN) == controlToken

        if (!settings.desired.externalControlEnabled && !trustedShortcut) {
            Toast.makeText(this, R.string.external_control_disabled, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        try {
            when (intent.action) {
                Intent.ACTION_VIEW -> {
                    val uri = intent.data ?: return finish()
                    val url = uri.getQueryParameter("url") ?: return finish()

                    if (!withContext(Dispatchers.IO) { isSafeProfileUrl(url) }) {
                        Toast.makeText(
                            this@ExternalControlActivity,
                            R.string.external_control_invalid_url,
                            Toast.LENGTH_LONG,
                        ).show()
                        finish()
                        return
                    }

                    val uuid = withProfile {
                        val type = when (uri.getQueryParameter("type")?.lowercase(Locale.getDefault())) {
                            "url" -> Profile.Type.Url
                            "file" -> Profile.Type.File
                            else -> Profile.Type.Url
                        }
                        val rawName = uri.getQueryParameter("name") ?: getString(R.string.new_profile)
                        val name = rawName.trim()
                            .ifEmpty { getString(R.string.new_profile) }
                            .take(128)

                        val parsedInterval = uri.getQueryParameter("update-interval")?.toLongOrNull() ?: 0L
                        val updateInterval = if (parsedInterval > 0) parsedInterval.coerceAtLeast(15L) else 0L
                        val intervalMs = java.util.concurrent.TimeUnit.MINUTES.toMillis(updateInterval)

                        createRestricted(name, url).also {
                            patch(it, name, url, intervalMs, null)
                        }
                    }
                    startActivity(Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_NAV_ROUTE, "edit/$uuid")
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    finish()
                    return
                }

                Intents.ACTION_TOGGLE_CLASH -> {
                    if (isClashRunning() || settings.phase == RuntimePhase.Running) {
                        stopClash()
                    } else {
                        startClash(PendingAction.START)
                    }
                }

                Intents.ACTION_START_CLASH -> {
                    if (isClashRunning() || settings.phase == RuntimePhase.Running) {
                        Toast.makeText(this, R.string.external_control_started, Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        startClash(PendingAction.START)
                    }
                }

                Intents.ACTION_STOP_CLASH -> {
                    stopClash()
                }
            }
        } catch (e: Exception) {
            showStartFailure(e)
            finish()
        }
    }

    /**
     * Rejects profile URLs that cannot safely be fetched from a deep link:
     * non-HTTP(S)/content schemes and HTTP(S) hosts that resolve to the local
     * device or the local network. Called from the IO-backed coroutine so the
     * DNS lookup never blocks the UI thread.
     */
    private fun isSafeProfileUrl(url: String): Boolean {
        return try {
            val parsed = URI(url)
            val scheme = parsed.scheme?.lowercase(Locale.ROOT) ?: return false

            when (scheme) {
                "content" -> return false
                "http", "https" -> Unit
                else -> return false
            }

            val host = parsed.host
                ?.lowercase(Locale.ROOT)
                ?.removePrefix("[")
                ?.removeSuffix("]")
                ?.removeSuffix(".")
                ?: return false

            if (host.isEmpty() || host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local"))
                return false

            val addresses = InetAddress.getAllByName(host)

            if (addresses.isEmpty())
                return false

            addresses.none {
                it.isLoopbackAddress ||
                    it.isLinkLocalAddress ||
                    it.isSiteLocalAddress ||
                    it.isAnyLocalAddress ||
                    (it.address.size == 16 && (it.address[0].toInt() and 0xfe) == 0xfc)
            }
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        /** Private token used by the app's own dynamic shortcuts. */
        const val EXTRA_CONTROL_TOKEN = "com.github.kr328.clash.extra.CONTROL_TOKEN"

        private const val REQUEST_VPN_CONSENT = 1001
        private const val STATE_PENDING_ACTION = "pending_action"
        private const val STARTUP_TIMEOUT_MS = 30_000L

        // The activity is noHistory, so keep the pending action until the
        // consent result has been handled and the startup attempt completed.
        @Volatile
        private var pendingAction: PendingAction? = null

        private enum class PendingAction { START }
    }

    private fun isClashRunning(): Boolean {
        return StatusClient(this).currentProfile() != null
    }

    private suspend fun startClash(action: PendingAction) {
        val activeProfile = withProfile { queryActive() }
        if (activeProfile == null) {
            Toast.makeText(this, R.string.no_profile_selected, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val vpnRequest = try {
            startClashService()
        } catch (e: Exception) {
            showStartFailure(e)
            finish()
            return
        }
        if (vpnRequest != null) {
            pendingAction = action
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(vpnRequest, REQUEST_VPN_CONSENT)
            } catch (e: Exception) {
                pendingAction = null
                showStartFailure(e)
                finish()
            }
            return
        }

        pendingAction = null
        awaitStartup()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_VPN_CONSENT) return

        val action = pendingAction
        pendingAction = null
        if (resultCode != RESULT_OK) {
            Toast.makeText(this, "VPN permission denied", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        launch {
            try {
                startClashService()
            } catch (e: Exception) {
                showStartFailure(e)
                finish()
                return@launch
            }

            if (action != null) {
                awaitStartup()
            } else {
                finish()
            }
        }
    }

    private suspend fun awaitStartup() {
        val started = withTimeoutOrNull(STARTUP_TIMEOUT_MS) {
            var sawStarting = false
            while (true) {
                if (isClashRunning()) return@withTimeoutOrNull true

                val snapshot = withTimeoutOrNull(1_000L) { readRuntimeState() }
                when (snapshot?.phase) {
                    RuntimePhase.Running -> return@withTimeoutOrNull true
                    RuntimePhase.Failed -> return@withTimeoutOrNull false
                    RuntimePhase.Starting -> sawStarting = true
                    RuntimePhase.Stopped -> if (sawStarting) return@withTimeoutOrNull false
                    else -> Unit
                }
                delay(200)
            }
            @Suppress("UNREACHABLE_CODE")
            false
        } ?: false

        if (started) {
            Toast.makeText(this, R.string.external_control_started, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, R.string.unable_to_start_vpn, Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private suspend fun readRuntimeState(): SettingsSnapshot? = withContext(Dispatchers.IO) {
        Remote.service.remote.get().settings().getSnapshot()
    }

    private fun showStartFailure(error: Exception) {
        val detail = error.message?.takeIf { it.isNotBlank() }
        val message = if (detail == null) getString(R.string.unable_to_start_vpn)
            else "${getString(R.string.unable_to_start_vpn)}: $detail"
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun stopClash() {
        stopClashService()
        Toast.makeText(this, R.string.external_control_stopped, Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingAction?.let { outState.putString(STATE_PENDING_ACTION, it.name) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        cancel()
        super.onDestroy()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
