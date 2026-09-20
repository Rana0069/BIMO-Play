# Privacy Policy for BIMO

**Last updated:** September 2026

**BIMO** is an independent Android music player created and maintained by **Rana**. This Privacy Policy explains how information is handled when you use BIMO.

---

## 1. Summary
* **No Developer Telemetry:** BIMO does not run any personal servers, user tracking systems, or external telemetry databases. We do not collect, sell, or rent your personal information.
* **Direct Connections:** When you search, stream, or fetch lyrics, your device connects directly to the relevant third-party provider (such as YouTube Music, LRCLIB, or KuGou).
* **Local Storage:** All preferences, cached playback URLs, downloaded songs, and saved playlists remain stored strictly on your local device.

---

## 2. Information Handled by the App

### A. Account Login & Authentication
* If you choose to log into YouTube Music through BIMO, authentication occurs via an embedded Android WebView directly on Google's authentication servers.
* Session cookies and authentication tokens (e.g., `SAPISID`, Visitor ID) are captured and stored **locally on your device** in Android secure DataStore / SharedPreferences.
* These tokens are used solely to sign your requests when interacting with YouTube Music. **They are never sent to Rana, BIMO maintainers, or any third-party analytics service.**

### B. Local Data Storage
The following information is created and saved entirely on your device via an internal SQLite database (Room):
* Song playback history and statistics.
* Local and remote playlist definitions.
* Favorited artists, albums, and tracks.
* Downloaded audio files and cached thumbnail artwork.
* App settings (equalizer configurations, audio quality preferences, UI theme).

You can clear this data at any time via Android Application Settings or within the app's settings menu.

### C. Network Communications & Third-Party Services
When using BIMO, network requests are made directly from your device to third-party endpoints:
* **YouTube & YouTube Music (Google LLC):** For search queries, stream URLs, audio chunks, and account library synchronization.
* **Lyrics Services (LRCLIB, KuGou):** For retrieving synchronized or plain lyrics based on track metadata.
* **GitHub (Optional update checker):** If update checking is enabled, BIMO queries GitHub's public API to check for new releases.

Each third-party service processes requests in accordance with its own privacy policy:
* [Google Privacy Policy](https://policies.google.com/privacy)
* [LRCLIB Privacy Policy](https://lrclib.net)

### D. Analytics, Advertising, and Crash Reporting
* **No Advertising:** BIMO contains no advertising SDKs or third-party ad networks.
* **No Telemetry / Analytics:** BIMO does not include Firebase Analytics, Google Analytics, Mixpanel, or any user tracking frameworks.
* **Local Crash Logging:** Exceptions and errors are logged solely to Android's local system log (Logcat) and are never automatically transmitted over the network.

---

## 3. Permissions Requested
* **INTERNET / ACCESS_NETWORK_STATE:** Required to stream music, fetch metadata, and download lyrics over WiFi or cellular connections.
* **READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE:** Required only if you use BIMO to scan and play local music files stored on your device.
* **FOREGROUND_SERVICE / FOREGROUND_SERVICE_MEDIA_PLAYBACK:** Required to maintain continuous background playback and provide lock screen controls.
* **POST_NOTIFICATIONS:** Required on Android 13+ to display playback controls in the system notification shade.

---

## 4. Children's Privacy
BIMO does not knowingly collect or solicit personal information from children under the age of 13.

---

## 5. Contact & Questions
If you have questions or concerns regarding this Privacy Policy, you can reach out via:
* **Developer GitHub:** [https://github.com/Rana0069](https://github.com/Rana0069)
* **Community Discord:** [https://discord.gg/Kn6aJYcNQX](https://discord.gg/Kn6aJYcNQX)
