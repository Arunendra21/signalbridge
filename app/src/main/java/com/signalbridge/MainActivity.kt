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
import com.signalbridge.session.RangeMode
import com.signalbridge.session.Role
import com.signalbridge.session.SessionManager
import com.signalbridge.session.VoiceLinkService
import com.signalbridge.settlement.Settlement

class MainActivity : ComponentActivity() {

    private lateinit var session: SessionManager

    private val permsGranted = mutableStateOf(false)
    private val btEnabled = mutableStateOf(false)

    private var pendingRole: Role? = null
    private var pendingUpi: String? = null

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
                        onStartSeeker = { gateAndRun(Role.SEEKER, null) },
                        onStartHelper = { upi -> gateAndRun(Role.HELPER, upi) },
                        onFixPermissions = { ensurePermissions() },
                        onEnableBt = { ensureBluetooth() },
                        onGoLive = { VoiceLinkService.start(this) },
                        onEnd = { VoiceLinkService.stop(this) },
                        onPay = { payee, secs ->
                            Settlement.launchUpi(this, payee, "SignalBridge Helper", secs)
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permsGranted.value = Permissions.allGranted(this)
        btEnabled.value = isBtOn()
    }

    // ---------------- Readiness gate ----------------

    private fun gateAndRun(role: Role, upi: String?) {
        pendingRole = role; pendingUpi = upi
        continueGate()
    }

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
        Role.HELPER -> session.becomeHelper(pendingUpi)
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
    onStartSeeker: () -> Unit,
    onStartHelper: (String?) -> Unit,
    onFixPermissions: () -> Unit,
    onEnableBt: () -> Unit,
    onGoLive: () -> Unit,
    onEnd: () -> Unit,
    onPay: (String?, Int) -> Boolean,
) {
    val mode by session.mode.collectAsState()
    val role by session.role.collectAsState()
    val state by session.state.collectAsState()
    val helpers by session.helpers.collectAsState()
    val seconds by session.seconds.collectAsState()
    val payee by session.payeeUpi.collectAsState()

    var showHelperUpiDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state) { if (state == LinkState.LIVE) onGoLive() }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("SignalBridge", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Borrow a nearby signal for an urgent call — no mobile data.", fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))

        if (role == Role.IDLE) {
            // Range mode picker.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == RangeMode.BLUETOOTH,
                    onClick = { session.setMode(RangeMode.BLUETOOTH) },
                    label = { Text("Bluetooth · ~20 m") },
                )
                FilterChip(
                    selected = mode == RangeMode.WIFI,
                    onClick = { session.setMode(RangeMode.WIFI) },
                    label = { Text("Wi-Fi · ~150 m (beta)") },
                )
            }
            Spacer(Modifier.height(12.dp))

            if (!permsGranted) StatusCard(
                "Permissions needed", "SignalBridge needs Bluetooth/Wi-Fi + microphone access.",
                "Grant permissions", onFixPermissions
            )
            if (permsGranted && !btEnabled) StatusCard(
                "Bluetooth is off", "Turn on Bluetooth to find or offer a signal.",
                "Turn on Bluetooth", onEnableBt
            )

            Spacer(Modifier.height(8.dp))
            Button(onStartSeeker, Modifier.fillMaxWidth()) { Text("I need to make a call") }
            Spacer(Modifier.height(12.dp))
            OutlinedButton({ showHelperUpiDialog = true }, Modifier.fillMaxWidth()) {
                Text("I can help (share my signal)")
            }
        } else {
            when {
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
                                supportingContent = {
                                    Text(h.signalDbm?.let { "signal $it dBm" } ?: "Wi-Fi Direct")
                                },
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

                state == LinkState.CONNECTING -> Info("Connecting…")
                state == LinkState.WAITING_APPROVAL -> Info("Waiting for the helper to approve…")

                state == LinkState.LIVE -> {
                    Text("● LIVE", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("%02d:%02d".format(seconds / 60, seconds % 60), fontSize = 40.sp)
                    Text("Talking over ${if (mode == RangeMode.WIFI) "Wi-Fi Direct" else "Bluetooth"} — no data used.")
                    Spacer(Modifier.height(24.dp))
                    Button({ onEnd(); session.end() }, Modifier.fillMaxWidth()) { Text("End") }
                }

                state == LinkState.ENDED -> {
                    Info("Call ended.")
                    if (role == Role.SEEKER && seconds > 0) PaySection(payee, seconds, onPay)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(session::reset, Modifier.fillMaxWidth()) { Text("Done") }
                }
            }
        }
    }

    if (showHelperUpiDialog) {
        HelperUpiDialog(
            onDismiss = { showHelperUpiDialog = false },
            onConfirm = { upi -> showHelperUpiDialog = false; onStartHelper(upi) },
        )
    }
}

@Composable
private fun PaySection(payee: String?, seconds: Int, onPay: (String?, Int) -> Boolean) {
    var manual by remember { mutableStateOf("") }
    val known = Settlement.isValidPayee(payee)
    Spacer(Modifier.height(8.dp))
    Text("Owed to helper: ₹%.2f".format(Settlement.amountRupees(seconds)))
    if (known) {
        Text("Paying: $payee", fontSize = 12.sp)
        Button({ onPay(payee, seconds) }, Modifier.fillMaxWidth()) { Text("Pay helper (UPI)") }
    } else {
        Text("The helper didn't share a UPI id. Enter it to pay:", fontSize = 12.sp)
        OutlinedTextField(
            value = manual, onValueChange = { manual = it },
            label = { Text("helper UPI id (name@bank)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onPay(manual, seconds) },
            enabled = Settlement.isValidPayee(manual),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Pay helper (UPI)") }
    }
}

@Composable
private fun HelperUpiDialog(onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    var upi by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your UPI id (optional)") },
        text = {
            Column {
                Text("So the person you help can pay you back. Leave blank to skip.", fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = upi, onValueChange = { upi = it },
                    label = { Text("name@bank") }, singleLine = true,
                )
            }
        },
        confirmButton = { TextButton({ onConfirm(upi.trim().ifBlank { null }) }) { Text("Start helping") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
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
