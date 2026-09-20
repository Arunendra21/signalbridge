# Contributing to SignalBridge

Thanks for helping. This project is honest about what's hard — contributions that move the frontier are the most welcome.

## Open problems, ranked
1. **HFP-HF emulation** — the headline feature. **Done:** the SLC AT-command handshake (`hfp/HfpAtCommands.kt`, unit-tested) and its RFCOMM wiring (`hfp/HfpHandsFreeUnit.kt`). **Next:** establish the HF-side **SCO/eSCO audio link** to actually read/write the call PCM, and test against real Audio Gateways on ≥2 phone models. See `bridge/CellularBridge.kt`.
2. **Codec** — swap raw PCM for Opus to cut Bluetooth bandwidth and improve quality on weak links (`audio/VoiceStreamer.kt`).
3. **Robust discovery** — reconnection, multiple helpers, pairing security (`ble/`).
4. **Reputation & abuse limits** — needed before any cellular path ships (`session/`).

## Ground rules
- Keep the app **data-free**: do not add the `INTERNET` permission or any network call to the core voice path.
- Respect [LEGAL-ETHICS.md](LEGAL-ETHICS.md). No SIM cloning, no unlicensed PSTN termination, no bypassing consent.
- Test the voice link on **two real phones** — emulators don't do Bluetooth audio.

## Workflow
1. Fork, branch from `main`.
2. `gradle :app:assembleDebug` (or Android Studio) must pass.
3. Open a PR describing what you tested and on which devices.

## Style
Kotlin official style. Keep modules small and single-purpose, matching the existing layout.
