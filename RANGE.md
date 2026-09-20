# Range: what's physically possible (and what SignalBridge does)

Bluetooth LE tops out around 10–30 m — short enough that you could often just walk over.
This page is the honest engineering answer to "make it long range" without using mobile data.

## The unavoidable trade-off

You **cannot** have all of these at once from a phone with no mobile data:
long range · clear low-latency full-duplex voice · no extra hardware · no infrastructure.

Every real system gives one up:

| Approach | Range | Voice | What it costs |
|---|---|---|---|
| Bluetooth LE | ~10–30 m | ok | nothing (but too short) |
| **Wi-Fi Direct / Aware** | **~100–200 m** | **clear, low latency** | the `INTERNET` permission for local sockets — **but no mobile data**; audio never leaves the two phones |
| Mesh relay (multi-hop) | range × hops | ok, +latency/hop | needs other app users in between |
| Cellular bridge (helper relays) | unlimited | carrier-grade | needs the HFP/SCO audio path (the hard, maybe-unsolvable piece) |
| LoRa / sub-GHz | 1–5 km | ✗ choppy push-to-talk ~3 kbps | external radio hardware; not phone-only |

## What SignalBridge implements

**Two selectable modes, both zero mobile data:**

- **Short range — Bluetooth LE (default, proven).** ~10–30 m. No `INTERNET` permission at all.
- **Long range — Wi-Fi Direct (new).** ~100–200 m, clearer voice, low latency. Phone-to-phone
  Wi-Fi; the traffic never touches the internet or your data plan. Requires Android's
  `INTERNET` permission, which is simply the OS's name for "may open a socket" — it does
  **not** mean the app sends anything to a remote server. Marked **beta**: it needs testing
  across real phone models (Wi-Fi Direct behaviour varies by vendor).

### Why Wi-Fi Direct is the ceiling for infrastructure-free direct voice
Phones only carry two general-purpose radios you can drive from an app: Bluetooth and Wi-Fi.
Wi-Fi has the most range and bandwidth of the two. Past ~200 m with no infrastructure, the
only options are **mesh** (borrow other people's phones as relays) or the **cellular bridge**
(borrow the helper's tower — unlimited range, and the reason this project exists). Both are
tracked in [ARCHITECTURE.md](ARCHITECTURE.md); the cellular bridge is the true long-distance
answer and remains the headline research goal.

## Voice quality
On Wi-Fi Direct there is ample bandwidth, so audio is sent as 16 kHz PCM with a small jitter
buffer for smoothness. A future step is Opus at ~24 kbps for even cleaner voice on weak links
(tracked in [CONTRIBUTING.md](CONTRIBUTING.md)).
