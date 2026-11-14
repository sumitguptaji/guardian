# Guardian - transparent on-device family monitoring

A single Android app for your own minor child's phone. **No server, no VPS.** The
phone collects activity, builds a PDF, and emails it to you on a schedule over SMTP.
It is built to be **visible, not covert** - a permanent "Monitoring active"
notification shows whenever it runs. Keep it that way; see "Law & ethics".

## What it collects

| Data | Notes |
|---|---|
| Call log | number, contact name, direction, time, duration |
| SMS | full message text (toggle in settings) |
| App notifications | title + body of notifications from all apps - the only way to see WhatsApp / Instagram / Signal message **previews** without rooting |
| Location | periodic last-known position, with a Google Maps link in the report |

**Honest limits**
- **Notification history starts when you switch it on.** Android has no API to read
  notifications that arrived before the listener was enabled. There is no "past
  history" to recover - only everything from enable-time onward.
- **Full chat contents are not readable.** WhatsApp/Instagram/Signal databases are
  encrypted and sandboxed; only their notification previews are visible. Rooting the
  phone is the only way past that, and you should not do it.

## How reports work (and offline catch-up)

- Two schedules: **every 4 hours** and **once daily** (hour you choose, IST).
- Each report covers **everything since the last successful send** (a watermark).
- Collection needs no internet, so data keeps piling up while the phone is offline.
- The email step is network-constrained with retry/backoff: if the phone is offline
  at report time, the job waits and fires the moment the internet is back, covering
  the whole gap. The watermark only advances after a send succeeds, so **nothing is
  ever lost or skipped.**
- Data older than 30 days is pruned from the phone automatically.

## Architecture

```
child's phone only:
  WorkManager (collect every 15 min, no network)  -->  encrypted SQLite
  NotificationListenerService (live)               -->  encrypted SQLite
  WorkManager (report 4h + daily, needs network)   -->  PDF  --SMTP/TLS-->  your inbox
```

## Build

Open `android/` in Android Studio and Run, or from CLI:

```bash
cd android
./gradlew assembleRelease   # -> app/build/outputs/apk/release/app-release.apk
```

## Install & set up (on the child's phone)

1. Sideload the APK (enable "Install unknown apps").
2. Open Guardian and fill the settings:
   - **SMTP host** `smtp-relay.brevo.com`, **port** `587`
   - **SMTP login** your Brevo login, **password** your Brevo SMTP key (see security note)
   - **From** a sender verified in Brevo (e.g. `zoo@zooleads.com`)
   - **Send reports to** `sgrock619@gmail.com`
   - **Daily report hour** e.g. `8`
   - tick **Include full SMS text** if you want message bodies
3. Tap **Save settings**.
4. **Grant call / SMS / location permissions** - and set Location to "Allow all the time".
5. **Enable notification access** - toggle Guardian on in the system screen that opens.
6. **Allow to run in background (battery)** - accept, so the OS doesn't kill it.
7. Tap **Start monitoring**. The permanent notification appears and reports begin.

Settings are entered once in the app and stored encrypted (EncryptedSharedPreferences).
There are no config files to edit.

## Security note about the SMTP key - important

A sending key placed on a phone can be extracted and abused to send spam as your
domain, which would wreck its reputation. Protect it:
- Use a **dedicated Brevo SMTP key** for this app, not one you use elsewhere, so you
  can revoke it without affecting anything.
- If a key has ever been pasted into a chat, email, or anywhere shared, **rotate it**
  and put only the fresh one into the app.
- Keep the Brevo sending limit on that key low.

## Law & ethics (India)

- Monitoring **your own minor child, with their knowledge**, is lawful - this tool is
  for exactly that, which is why the notification is permanent and there is no hidden mode.
- **Covertly** intercepting anyone's calls/SMS/notifications - a spouse or any adult -
  can be an offence under the IT Act (s.66E, s.72) and the Telegraph Act.
- Stop using it once your child is an adult.

## Files

```
android/
  app/src/main/AndroidManifest.xml
  app/src/main/java/com/guardian/client/
    MainActivity.kt          settings + setup screen
    MonitorService.kt        permanent "Monitoring active" notification
    NotificationService.kt   captures app notification previews
    Collectors.kt            call log + SMS -> store
    Store.kt                 encrypted-at-rest SQLite
    Config.kt                settings + secrets + report watermarks
    PdfReport.kt             paginated PDF renderer (no external lib)
    ReportBuilder.kt         assembles report sections
    Mailer.kt                SMTP send with PDF attachment
    Workers.kt               CollectWorker + ReportWorker
    Scheduler.kt             registers the periodic jobs
    BootReceiver.kt          restarts after reboot
  app/build.gradle.kts
  build.gradle.kts
  settings.gradle.kts
```
