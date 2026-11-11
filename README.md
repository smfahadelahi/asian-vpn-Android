# AsiaShield VPN (Android)

A WireGuard-based Android VPN client focused on Asian exit nodes. 100% Kotlin, Jetpack
Compose (Material 3), Hilt, Coroutines/Flow and DataStore.

| | |
|---|---|
| Min SDK | 26 (Android 8.0) |
| Target / compile SDK | 34 |
| Java / JVM target | 17 |
| Build | Gradle 8.7 (Kotlin DSL + version catalog), AGP 8.5.2, Kotlin 1.9.24 |
| Tunnel | `com.wireguard.android:tunnel:1.0.20230706` |

## Before you ship: configure real servers

`AsiaServerRepository.kt` ships a **placeholder** catalogue: hosts use the reserved
`*.asiashield.example` domain and the peer public keys are random. Until you replace them
with WireGuard servers you operate, the app builds and runs but every connection ends in
`FAILED` ("Could not resolve …") and every ping shows "Offline".

For each node set `host`, `port` and `publicKey`. The client generates its own private key
on first connect (stored in DataStore, excluded from backups) and uses the tunnel address
`10.66.66.2/32` (`WireGuardConfigGenerator.DEFAULT_ADDRESS`). Your servers must accept that
client key/address – in a real deployment you would register the client public key with a
provisioning API and receive the address from it.

Note: per the spec, `AllowedIPs` is `0.0.0.0/0`, so only IPv4 is tunnelled. Add `::/0` (and
an IPv6 interface address) if your servers support IPv6, otherwise IPv6 traffic bypasses
the VPN on dual-stack networks.

## Architecture

```
app/src/main/java/com/asiashield/vpn
├── domain/           pure Kotlin models, repository contracts, use cases
│   ├── model/        VpnState (state machine), VpnStatus, VpnServer, AsiaRegion
│   ├── repository/   ServerRepository, SettingsRepository, VpnController
│   └── usecase/      FilterServersUseCase, SmartConnectUseCase
├── data/
│   ├── repository/   AsiaServerRepository (curated catalogue + latency map)
│   ├── latency/      LatencyTracker (ICMP RTT probe)
│   ├── settings/     DataStoreSettingsRepository
│   ├── vpn/          WireGuardConfigGenerator, VpnStateHolder, VpnControllerImpl
│   └── service/      VpnConnectionService (foreground VpnService)
├── presentation/     VpnViewModel, HomeScreen, ServerListScreen, theme, navigation
└── di/               Hilt module
```

- **State machine** – `IDLE → CONNECTING → CONNECTED → DISCONNECTING → IDLE`, with `FAILED`
  reachable from any in-flight state. Illegal transitions are rejected in `VpnStateHolder`.
- **VpnConnectionService** – a foreground `VpnService` that generates the WireGuard config,
  drives the SDK's `GoBackend`, waits for a real handshake before reporting `CONNECTED`,
  samples throughput once a second, and shows a status notification with a one-tap
  **Disconnect** action. (`GoBackend` creates the TUN device from its own internal
  `GoBackend$VpnService`, which the SDK's manifest contributes automatically.)
- **VpnViewModel** – handles the `VpnService.prepare()` consent flow, region filtering,
  search, and lowest-latency **Smart Connect**.

## Build locally

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest
```

Requires JDK 17 and the Android SDK (platform 34).

## CI/CD

`.github/workflows/build-apk.yml` runs on every push to `main` and on pull requests:

1. JDK 17 + Gradle cache, wrapper validation, unit tests, `./gradlew assembleRelease`.
2. Verifies the APK signature and uploads it as the `AsiaShieldVPN-apk` artifact.
3. On pushes to `main`, publishes a GitHub Release tagged `build-<short-sha>` with the APK
   attached.

### Signing

Without configuration the release APK is signed with a throw-away **debug key** so it can be
installed immediately. That key differs on every CI run, so one build cannot be installed
as an update over another (uninstall first), and it must not be used for store
distribution. For stable signing add these repository secrets:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | key alias |
| `ANDROID_KEY_PASSWORD` | key password |
