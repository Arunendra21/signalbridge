# Legal & Ethics

SignalBridge touches telecom, other people's phone identity, and payments. Read this before deploying or extending it.

## 1. Emergencies
Emergency calls (**112** in India) already work on **any** available network, with any SIM or no SIM, and no data — this is mandated by the GSM/LTE standard. **Do not rely on SignalBridge for emergencies. Dial 112 directly.**

## 2. Never impersonate a network or clone a SIM
- Do not attempt to extract, copy, or emulate another SIM's secret key (`Ki`). SIM cloning is illegal in most jurisdictions, including India.
- Do not transmit on licensed cellular uplink frequencies from a phone. The data-free voice link uses **Bluetooth** (unlicensed 2.4 GHz), which is legal for this purpose.

## 3. The experimental cellular path lends a real phone identity
If the experimental cellular-bridge layer is ever completed, the **Helper's phone number** becomes the caller ID for the **Seeker's** call. That is significant:
- The Helper could be associated with a call they didn't make the content of.
- This is why the app is **consent-first** and why a production build **must** add: phone-number verification, per-session rate limits, two-sided reputation, and a clear record that the Helper approved.
- Never route spam, fraud, harassment, or automated/bulk calls through a Helper. Build technical limits against it.

## 4. Telecom regulation (India / TRAI / DoT)
- **App-to-app voice** (SignalBridge-to-SignalBridge) is like any OTT calling app — generally fine.
- **Terminating calls onto the public phone network** (reaching real phone numbers) is regulated. Doing it at scale requires going through a **licensed** provider and honouring their rules. Do not build unlicensed PSTN termination.

## 5. Privacy
- The app requests **no INTERNET permission** and collects no accounts.
- BLE scanning uses the `neverForLocation` flag — it is not used to track location.
- Do not add analytics, cloud logging, or contact upload without explicit, revocable user consent.

## 6. Payments
- SignalBridge never handles money directly. It only opens a `upi://pay` deep link that the user confirms in their own bank/UPI app.
- Do not store card, bank, or UPI credentials in the app.

## 7. This is provided "as is"
See [LICENSE](LICENSE). No warranty. You are responsible for complying with the laws of your jurisdiction.
