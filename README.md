# Car Business Android App

This Android wrapper embeds the local Car Business V2 app in a WebView.

## Features included
- Offline local app
- Single-screen car add/edit flow
- Optional vehicle details
- Expense line items
- Purchase and sale payment breakup (cash/bank/cheque/etc.)
- Summary and profit/loss
- Data Bank ledger
- Local browser/WebView persistence

## Build in Android Studio
1. Open this folder in Android Studio.
2. Let Gradle sync.
3. Build > Build APK(s).
4. APK will be under `app/build/outputs/apk/debug/app-debug.apk`.

## Build with GitHub Actions
Push this project to a GitHub repository. The included workflow `Build Android APK` builds a debug APK and uploads it as an Actions artifact.

## Data note
App data is stored locally inside Android WebView app storage. Uninstalling the app can remove local data, so use the app's backup/export feature regularly. Google Drive backup can be added/configured separately with OAuth credentials.
