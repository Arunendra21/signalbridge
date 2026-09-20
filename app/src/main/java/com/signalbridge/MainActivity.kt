package com.signalbridge

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signalbridge.session.LinkState
import com.signalbridge.session.Role
import com.signalbridge.session.SessionManager
import com.signalbridge.session.VoiceLinkService
import com.signalbridge.settlement.Settlement

class MainActivity : ComponentActivity() {

    private lateinit var session: SessionManager

    // Live UI signals for readiness — recomputed on every resume so reopening the app
    // always reflects the CURRENT state of permissions and the Bluetooth radio.
    private val permsGranted = mutableStateOf(false)
    private val btEnabled = mutableStateOf(false)

    /** Role the user asked for, held while we satisfy permissions / turn Bluetooth on. */
    private var pendingRole: Role? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permsGranted.value = result.values.all { it } && Permissions.allGranted(this)
        if (permsGranted.value) continueGate() else pendingRole = null
    }

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        btEnabled.value = isBtOn()
        if (btEnabled.value) continueGate() else pendingRole = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager.get(this)

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    HomeScreen(
                        session = session,
                        permsGranted = permsGranted.value,
                        btEnabled = btEnabled.value,
                        onStartRole = ::gateAndRun,
                        onFixPermissions = { ensurePermissions() },
                        onEnableBt = { ensureBluetooth() },
                        onGoLive = { VoiceLinkService.start(this) },
                        onEnd = { VoiceLinkService.stop(this) },
                        onPay = { secs ->
                            Settlement.launchUpi(this, "helper@upi", "SignalBridge Helper", secs)
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have toggled permissions or Bluetooth in Settings while away.
        permsGranted.value = Permissions.allGranted(this)
        btEnabled.value = isBtOn()
    }

    // ---------------- Readiness gate ----------------

    /** Entry point when the user taps a role: satisfy permissions + Bluetooth, then start. */
    private fun gateAndRun(role: Role) {
        pendingRole = role
        continueGate()
    }

    /** Advances through the checklist; each launcher callback calls back into here. */
    private fun continueGate() {
        val role = pendingRole ?: return
        when {
            !Permissions.allGranted(this) -> ensurePermissions()
            !isBtOn() -> ensureBluetooth()
            else -> { pendingRole = null; startRole(role) }
        }
    }

    private fun ensurePermissions() {
        val missing = Permissions.missing(this)
        if (missing.isEmpty()) { permsGranted.value = true; continueGate() }
        else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun ensureBluetooth() {
        if (isBtOn()) { btEnabled.value = true; continueGate() }
        else enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    private fun startRole(role: Role) = when (role) {
        Role.HELPER -> session.becomeHelper()
        Role.SEEKER -> session.becomeSeeker()
        Role.IDLE -> Unit
    }

    private fun isBtOn(): Boolean =
        getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
}

@Composable
private fun HomeScreen(
    session: SessionManager,
    permsGranted: Boolean,
    btEnabled: Boolean,
    onStartRole: (Role) -> Unit,
    onFixPermissions: () -> Unit,
    onEnableBt: () -> Unit,
    onGoLive: () -> Unit,
    onEnd: () -> Unit,
    onPay: (Int) -> Unit,
) {
    val role by session.role.collectAsState()
    val state by session.state.collectAsState()
    val helpers by session.helpers.collectAsState()
    val seconds by session.seconds.collectAsState()

    LaunchedEffect(state) { if (state == LinkState.LIVE) onGoLive() }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("SignalBridge", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Borrow a nearby signal for an urgent call — over Bluetooth, no mobile data.", fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))

        // Readiness banner — always visible while idle so the user knows what's missing.
        if (role == Role.IDLE) {
            if (!permsGranted) StatusCard(
                "Permissions needed", "SignalBridge needs Bluetooth + microphone access.",
                "Grant permissions", onFixPermissions
            )
            if (permsGranted && !btEnabled) StatusCard(
                "Bluetooth is off", "Turn on Bluetooth to find or offer a signal.",
                "Turn on Bluetooth", onEnableBt
            )
        }

        when {
            role == Role.IDLE -> {
                Spacer(Modifier.height(8.dp))
                Button({ onStartRole(Role.SEEKER) }, Modifier.fillMaxWidth()) {
                    Text("I need to make a call")
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton({ onStartRole(Role.HELPER) }, Modifier.fillMaxWidth()) {
                    Text("I can help (share my signal)")
                }
            }

            role == Role.SEEKER && state == LinkState.SCANNING -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Looking for nearby helpers…", fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(12.dp))
                if (helpers.isEmpty()) Text("No helpers found yet. Keep the app open near one.")
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(helpers) { h ->
                        ListItem(
                            headlineContent = { Text(h.name) },
                            supportingContent = { Text("signal ${h.rssi} dBm") },
                            trailingContent = {
                                Button({ session.connectToHelper(h) }) { Text("Ask") }
                            }
                        )
                        HorizontalDivider()
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton({ onEnd(); session.end(); session.reset() }, Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }

            role == Role.HELPER && state == LinkState.ADVERTISING ->
                Info("Waiting for someone nearby to connect…")

            role == Role.HELPER && state == LinkState.CONSENT_PENDING -> {
                Info("Someone nearby is asking to borrow your signal.")
                Spacer(Modifier.height(12.dp))
                Button(session::helperApprove, Modifier.fillMaxWidth()) { Text("Approve & start") }
                OutlinedButton(session::helperDecline, Modifier.fillMaxWidth()) { Text("Decline") }
            }

            state == LinkState.CONNECTING -> Info("Connecting over Bluetooth…")
            state == LinkState.WAITING_APPROVAL -> Info("Waiting for the helper to approve…")

            state == LinkState.LIVE -> {
                Text("● LIVE", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("%02d:%02d".format(seconds / 60, seconds % 60), fontSize = 40.sp)
                Text("Talking over Bluetooth radio — no data used.")
                Spacer(Modifier.height(24.dp))
                Button({ onEnd(); session.end() }, Modifier.fillMaxWidth()) { Text("End") }
            }

            state == LinkState.ENDED -> {
                Info("Call ended.")
                if (role == Role.SEEKER && seconds > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("Owed to helper: ₹%.2f".format(Settlement.amountRupees(seconds)))
                    Button({ onPay(seconds) }, Modifier.fillMaxWidth()) { Text("Pay helper (UPI)") }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(session::reset, Modifier.fillMaxWidth()) { Text("Done") }
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, body: String, action: String, onAction: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Button(onAction) { Text(action) }
        }
    }
}

@Composable private fun Info(text: String) =
    Text(text, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
