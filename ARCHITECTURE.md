# Architecture & Design Rationale

This document explains **what SignalBridge does, why the "impossible" parts are impossible, and the exact path the experimental parts must take.** It is written to be honest — so nobody builds on a false premise.

## 1. The core misconception, cleared up

> "Let me use my neighbour's Airtel SIM frequency on my Jio phone."

A cell tower does **not** authenticate a phone by frequency. Frequency is just the channel. Since GSM, the tower issues a cryptographic challenge and the SIM answers it using a secret key **`Ki`** that:

- never leaves the SIM card, and
- has exactly one matching copy, inside the **home operator's** core network (the AuC/HLR).

So an Airtel tower can only grant normal service to a SIM whose `Ki` it can verify — i.e. an Airtel SIM, or a SIM whose operator Airtel has a **roaming agreement** with. A Jio SIM on an Airtel tower with no roaming deal gets exactly one thing: **emergency service** (unauthenticated, PSAP-only, mandated by the standard).

**Consequences that shape this whole project:**
- You cannot "borrow spectrum." There is no radio trick.
- Borrowing the helper's *SIM identity* would require their `Ki` — physically unextractable, and cloning it is a crime.
- The emergency (112 / SOS) bearer is real and data-free, but it is sealed to the emergency call centre. You cannot route an ordinary call over it, and abusing it is fraud.

Therefore the only thing you can legitimately borrow from a nearby person **without their SIM and without data** is the **audio path of a normal voice call they place themselves** — bridged to you over a local radio link. That realization is the entire design.

## 2. The three layers

### Layer 1 — Discovery (Bluetooth LE)
- **Helper** advertises a service UUID (`BleAdvertiser`). A few bytes on 2.4 GHz, ~10–50 m range.
- **Seeker** scans for that UUID (`BleScanner`) and ranks results by RSSI so the nearest helper surfaces first.
- No data, no location, no server.

### Layer 2 — Voice link (Bluetooth RFCOMM + PCM)
- Once a helper is chosen, the two phones open a **classic Bluetooth RFCOMM socket** (`BluetoothVoiceLink`). Helper hosts, Seeker connects.
- `VoiceStreamer` captures 16 kHz/16-bit mono PCM from the mic and writes 20 ms frames to the socket; simultaneously reads the peer's frames and plays them. Full-duplex intercom.
- This is a raw socket we own — **not** the internet. This layer is complete and works today.

### Layer 3 — Cellular routing (EXPERIMENTAL)
Getting the Seeker's voice *out over the Helper's tower to a real number* is the frontier. Three approaches, none finished, ranked by promise:

**A. Bluetooth Hands-Free (HFP) emulation — best data-free path.**
Make the Seeker's phone present to the Helper's phone as a Bluetooth headset (HFP "HF" role). When the Helper places a normal voice call, the OS routes the call audio to the "headset" (the Seeker). No data — it's the Helper's voice minutes.
*Blocker:* Android exposes the Audio Gateway (AG) role to apps, not the HF role. The HF side must be implemented by hand over RFCOMM + SCO, which is device-dependent. **This is the #1 open problem — see `CellularBridge.kt`.**

**B. Self-managed Telecom (`ConnectionService`).**
SignalBridge owns a call object via `TelecomManager`. Clean for **app-to-app** SignalBridge calls, but cannot terminate onto the public phone network without a **licensed carrier gateway** (Exotel/Twilio-India), which reintroduces data + licensing.

**C. 3GPP ProSe UE-to-Network Relay (sidelink / PC5).**
The standards-correct answer and literally this project's original vision: the Seeker (Remote UE) reaches the network *through* the Helper (Relay UE) over direct phone-to-phone **sidelink** RF. Built for public safety.
*Blocker:* not exposed on consumer Android/iOS, needs carrier provisioning + licensed spectrum. Tracked as research, not a codeable task.

## 3. Trust, consent, settlement
- **Consent-first:** the Helper explicitly approves before any audio flows (`LinkState.CONSENT_PENDING`). Either side can end instantly.
- **Metering:** `SessionManager` counts live seconds.
- **Settlement:** `Settlement` builds a `upi://pay` deep link; the Seeker pays inside their own UPI app after the call. SignalBridge never touches money and needs no data to do it.
- **Abuse surface to respect:** in the experimental cellular path, the Helper lends their *phone number* to the Seeker's call. A production build must add number verification, rate limits, and two-sided reputation before that path ships.

## 4. What "done" looks like per layer
| Layer | Done today? | Definition of done |
|---|---|---|
| Discovery | ✅ | Two installs find each other reliably at ~20 m. |
| Voice link | ✅ | Clear full-duplex audio, no data used. |
| Consent/settlement | ✅ | Helper approves; Seeker pays via UPI. |
| Cellular routing (A) | ⚠️ | HFP-HF emulation proven on ≥2 real phone models. |
| ProSe (C) | 🔬 | Requires a carrier partner; out of app scope. |

## 5. Emergency reality check
Every GSM/LTE phone can already dial **112** on any available network with no SIM auth and no data — and newer phones extend this to **satellite SOS**. SignalBridge is for *non-emergency but urgent* calls. **Do not rely on this app for emergencies; dial 112 directly.**
