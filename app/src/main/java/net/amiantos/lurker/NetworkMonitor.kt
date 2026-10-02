// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper

/**
 * Watches the device's default network and reports when it's worth reconnecting:
 * the default network changed (Wi-Fi <-> mobile, a new access point), it came
 * back after being lost, or it regained internet access without changing (a
 * router restart only toggles VALIDATED, with no lost/available pair).
 *
 * A TCP connection rides the network it was opened on, so after a switch the
 * socket is dead even though nothing has said so — the client would otherwise
 * find out only when a keepalive ping goes unanswered, then wait out its backoff.
 * HexDroid and other Android IRC clients reconnect off this signal instead.
 *
 * Callbacks arrive on the main thread, and a burst (available + capability churn)
 * is coalesced into one [onChange] call a second later, once the network settles.
 */
internal class NetworkMonitor(
    private val context: Context,
    private val onChange: (switched: Boolean) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var current: Network? = null
    private var lostCurrent = false
    private val validated = HashMap<Network, Boolean>()
    private var pending: Runnable? = null
    private var pendingSwitched = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val switched = (current != null && current != network) || lostCurrent
            current = network
            lostCurrent = false
            schedule(switched)
        }

        override fun onLost(network: Network) {
            validated.remove(network)
            if (network == current) lostCurrent = true
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val ok = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val was = validated.put(network, ok)
            if (ok && was == false) schedule(switched = false)
        }
    }

    fun start() {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        runCatching { cm.registerDefaultNetworkCallback(callback, main) }
            .onFailure { DebugLog.w("net", "network callback unavailable: ${it.javaClass.simpleName}") }
    }

    private fun schedule(switched: Boolean) {
        pending?.let(main::removeCallbacks)
        pendingSwitched = pendingSwitched || switched
        val r = Runnable {
            pending = null
            val sw = pendingSwitched
            pendingSwitched = false
            onChange(sw)
        }
        pending = r
        main.postDelayed(r, SETTLE_MS)
    }

    private companion object {
        const val SETTLE_MS = 1_000L
    }
}
