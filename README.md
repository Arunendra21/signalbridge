# SignalBridge

**Borrow a nearby phone's signal for an urgent call — over local Bluetooth radio, with zero mobile data on either phone.**

You're standing somewhere your SIM has no bars, but someone next to you does. SignalBridge lets the two phones find each other over Bluetooth and open a live voice link — no internet, no data plan, no server in the middle. The person who shares their signal gives explicit consent and can be paid back for it.

> ⚠️ **Honest status.** This is an open research project, not a finished telecom product. Read the [Capability status](#capability-status) table before you expect it to place calls to arbitrary phone numbers. What works today is a **data-free phone-to-phone voice link**. Routing that voice out over the helper's *cellular* network to any number is an documented, **experimental** frontier (see [`CellularBridge.kt`](app/src/main/java/com/signalbridge/bridge/CellularBridge.kt)).

---

## Capability status

| Capability | Status | Notes |
|---|---|---|
| Discover a nearby helper | ✅ Works | No data — a Bluetooth LE beacon (or Wi-Fi Direct service record). |
| Short-range voice link (~20 m) | ✅ Works | Bluetooth LE L2CAP. No `INTERNET` permission at all. Zero mobile data. |
| Long-range voice link (~150 m) | 🧪 Beta | Wi-Fi Direct mode — clearer voice, ~10× the range. Local sockets only (no mobile data). Needs real-hardware testing. See [RANGE.md](RANGE.md). |
| Pay the helper their **real** UPI id | ✅ Works | The helper's UPI id travels over the consent handshake; the seeker pays that id (or types one if the helper skipped it). |
| Explicit consent + on/off control for the helper | ✅ Works | Helper must approve before any audio flows. |
| Meter airtime + settle via UPI | ✅ Works | Payment is out-of-band in the user's own UPI app. |
| Route the call out to **any phone number** over the helper's tower | ⚠️ Experimental | Blocked by Android's closed in-call audio path. Approaches documented in code. |
| Use another SIM's "frequency" directly | ❌ Impossible | Cellular auth is a secret key (`Ki`), not spectrum. No app can do this. See [ARCHITECTURE.md](ARCHITECTURE.md). |

## Range & "no data"

Two selectable modes, **both use zero mobile data**:
- **Bluetooth (default, ~20 m):** voice over a Bluetooth LE **L2CAP** channel. Declares **no `INTERNET` permission** at all.
- **Wi-Fi Direct (beta, ~150 m):** voice over a phone-to-phone Wi-Fi Direct **TCP** socket — clearer and ~10× the range. This mode needs Android's `INTERNET` permission, which is only the OS's name for *"may open a socket"*; the audio stays inside the local Wi-Fi Direct group and never reaches the internet or your data plan.

Why not further? See **[RANGE.md](RANGE.md)** — kilometre range with no infrastructure is only reachable via the cellular bridge (the helper's tower) or external radio hardware. Wi-Fi Direct is the ceiling for infrastructure-free direct phone-to-phone voice.

## How it works (30-second version)

```
  Seeker phone                         Helper phone
  (no signal)                          (has signal)
  ┌──────────┐    BLE advertise        ┌──────────┐
  │  scan  ◄─┼─────────────────────────┤ advertise│   1. discovery (no data)
  │          │                         │          │
  │  mic ────┼──► LE L2CAP voice chan ─┼──► spkr   │   2. voice link (no data)
  │  spkr ◄──┼──── LE L2CAP voice chan ┼──── mic   │      full-duplex intercom
  └──────────┘                         └────┬─────┘
                                            │  (experimental) route into a
                                            ▼  real cellular voice call
                                        Airtel tower ──► any phone number
```

Full technical write-up: **[ARCHITECTURE.md](ARCHITECTURE.md)**.

## Download & install

1. Go to the [**Releases**](../../releases) page.
2. Download the latest `signalbridge-debug.apk` (built automatically by GitHub Actions).
3. On your Android phone: enable *Install unknown apps* for your browser, then open the APK.
4. Install it on **two** Android phones (one Seeker, one Helper) and grant Bluetooth + microphone permissions.

> Requires Android 10+ (uses Bluetooth LE L2CAP). It's a debug-signed build for testing — not yet a Play Store release.

## Build it yourself

Open the project in **Android Studio** (which provisions the Gradle wrapper and Android SDK for you) and run the `app` configuration, **or** on a machine with the Android SDK + Gradle 8.9:

```bash
gradle :app:assembleDebug
# APK lands in app/build/outputs/apk/debug/
```

## Testing the voice link

You need **two Android phones**. Install on both, put your SIM(s) in, then:
1. Phone A → *"I can help"* (starts advertising).
2. Phone B → *"I need to make a call"* → tap Phone A in the list.
3. Phone A approves → both phones are now a Bluetooth intercom. Talk.

## Project layout

```
app/src/main/java/com/signalbridge/
  ble/            BLE discovery (advertise + scan)
  bt/             Bluetooth LE L2CAP transport (short range)
  wifi/           Wi-Fi Direct transport (long range, beta)
  transport/      transport-neutral VoiceChannel + consent/UPI handshake
  audio/          mic capture + speaker playback
  session/        orchestration, state, neutral Helper model, foreground service
  hfp/            HFP hands-free handshake (cellular-bridge groundwork)
  bridge/         EXPERIMENTAL cellular routing (documented)
  settlement/     UPI payback (pays the helper's real id)
  MainActivity.kt Compose UI (mode picker, consent, pay)
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). The most valuable open problem is the **HFP-emulation** path in [`CellularBridge.kt`](app/src/main/java/com/signalbridge/bridge/CellularBridge.kt) — proving data-free call routing on real hardware.

## Legal & ethics

Read **[LEGAL-ETHICS.md](LEGAL-ETHICS.md)** before deploying. Lending your phone identity to someone else's call has real consequences; the app is built consent-first for that reason. Emergency calls (112) already work on any network with no app — don't rely on this for emergencies.

## License

[MIT](LICENSE).
