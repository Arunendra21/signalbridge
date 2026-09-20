package com.signalbridge

import android.Manifest
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

    private val permissions = arrayOf(
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.RECORD_AUDIO,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager.get(this)

        val asker = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { /* proceed regardless; UI reflects failures */ }

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    HomeScreen(
                        session = session,
                        onNeedPermissions = { asker.launch(permissions) },
                        onGoLive = { VoiceLinkService.start(this) },
                        onEnd = { VoiceLinkService.stop(this) },
                        onPay = { secs ->
                            // Demo payee — a real build collects the helper's VPA during pairing.
                            Settlement.launchUpi(this, "helper@upi", "SignalBridge Helper", secs)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    session: SessionManager,
    onNeedPermissions: () -> Unit,
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
        Text(
            "Borrow a nearby signal for an urgent call — over Bluetooth, no mobile data.",
            fontSize = 14.sp
        )
        Spacer(Modifier.height(24.dp))

        when {
            role == Role.IDLE -> {
                Button(
                    onClick = { onNeedPermissions(); session.becomeSeeker() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("I need to make a call") }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { onNeedPermissions(); session.becomeHelper() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("I can help (share my signal)") }
            }

            role == Role.SEEKER && state == LinkState.SCANNING -> {
                Text("Looking for nearby helpers…", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(helpers) { h ->
                        ListItem(
                            headlineContent = { Text(h.device.address) },
                            supportingContent = { Text("signal ${h.rssi} dBm") },
                            trailingContent = {
                                Button(onClick = { session.connectToHelper(h) }) { Text("Ask") }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }

            role == Role.HELPER && state == LinkState.ADVERTISING ->
                Info("Waiting for someone nearby to connect…")

            role == Role.HELPER && state == LinkState.CONSENT_PENDING -> {
                Info("Someone nearby is asking to borrow your signal.")
                Spacer(Modifier.height(12.dp))
                Button(session::helperApprove, Modifier.fillMaxWidth()) { Text("Approve & start") }
                OutlinedButton({ onEnd(); session.end() }, Modifier.fillMaxWidth()) { Text("Decline") }
            }

            state == LinkState.CONNECTING -> Info("Connecting over Bluetooth…")

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

@Composable private fun Info(text: String) =
    Text(text, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
