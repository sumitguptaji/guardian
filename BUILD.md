# Getting the installable APK

The app can't be compiled inside the Claude workspace (Google's Android SDK servers
are blocked there). Pick whichever route suits you - both give a sideloadable
`app-debug.apk`. No Play Store, no signing setup; a debug APK installs directly.

## Route A - GitHub builds it for you (no tools to install)  ← easiest

1. Create a new **private** repo on github.com (e.g. `guardian`).
2. From this unzipped folder, push it:

   ```bash
   cd guardian
   git init -b main
   git add .
   git commit -m "Guardian app"
   git remote add origin https://github.com/sumitguptaji/guardian.git
   git push -u origin main
   ```

   (No git? On the repo page use **Add file -> Upload files** and drag the folder in.)
3. Open the repo's **Actions** tab. The "Build Guardian APK" job runs (~4 min).
4. Download the APK from either:
   - the run's **Artifacts** section (`guardian-debug-apk`), or
   - the **Releases** page (tag `latest`) - a direct download link.
5. Copy that `app-debug.apk` to the child's phone and install it.

## Route B - build on your own machine with Android Studio

1. Install Android Studio (bundles the SDK).
2. **Open** the `android/` folder, let it sync, then **Build > Build Bundle(s)/APK(s) > Build APK(s)**.
3. APK appears at `android/app/build/outputs/apk/debug/app-debug.apk`.

## Installing on the phone

1. Transfer the APK (USB, or email/Drive to the phone).
2. On the phone, tap it and allow "Install unknown apps" for the file manager/browser.
3. Open Guardian, grant permissions, enable notification access + background battery,
   tap **Start monitoring**. Credentials are already filled in.
