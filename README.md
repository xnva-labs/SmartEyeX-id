# SmartEyeX

Wearable AI assistant companion app (XNVA Labs) — native Android, Kotlin + Jetpack Compose.

## Structure
`app/src/main/java/com/xnvalabs/smarteyex/`
- `MainActivity.kt` — entry point + navigation
- `ui/theme`, `ui/components`, `ui/screens/*` — Compose UI
- `service/` — NotificationListenerService, ReminderReceiver
- `data/*` — repositories (privacy, memory, xnai, vision, reminder, ...)

## Build
Open in Android Studio (Koala+), or: `gradle :app:assembleDebug`
(GitHub Actions builds a debug APK on every push to `main`.)

## Backend
Set `XnaiRepository.endpointUrl` to your XNAI server (`/chat`, `/vision`, `/translate`).
