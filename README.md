# TradeNotify

An Android alarm clock for Telegram trading signals. When a watched channel posts a message that
matches one of your rules, the phone rings a real alarm — looping siren on the alarm stream,
vibration, screen on, full-screen dismiss/snooze over the lock screen.

## Install it on your phone

1. On the Android phone, open the [**latest release**](https://github.com/rosinski141/TradePhoneNotify/releases/latest) and download the `.apk` file.
2. Open the downloaded file. Android will ask whether to allow installs from your browser — allow it.
3. **If you see "Unsafe app blocked"**, tap **More details → Install anyway**. Play Protect shows this for every app that didn't come from the Play Store; it is not a warning about this app specifically.
4. Open TradeNotify. A setup wizard walks you through the permissions one at a time — it won't let you skip the ones that matter.
5. **If the notification-access switch is greyed out** and says *"Controlled by restricted setting"*, see below.
6. On the Status screen, press **Test alarm** to hear what a real signal sounds like.

### "Controlled by restricted setting"

Android 13 and later block notification access for any app installed outside an app store. The
switch is greyed out and nothing explains why. To unlock it:

**Settings → Apps → TradeNotify → ⋮ (top-right) → Allow restricted settings**

Then go back and turn notification access on. The setup wizard offers a direct **Open App info**
button for this. Note that this affects installs from a browser or file manager; `adb install` does
not trigger it, which is why it never shows up during development.

Installing a newer APK over an existing copy keeps your rules and history. The app checks for new
releases itself and offers a one-tap download from **Settings → Version**.

### Moving to a new phone

**Settings → Backup your rules → Export** writes a `.json` file you can carry across and **Import**
on the new device. Worth doing before you switch — rules are the only thing here that can't be
recreated automatically.

---

## Automatic updates with Obtainium

A sideloaded app has no store behind it, so it never updates itself.
[Obtainium](https://github.com/ImranR98/Obtainium) watches this repo's releases and offers the
update as soon as one is published — worth setting up if you'd rather not check by hand.

**Add it by URL** (works everywhere): open Obtainium → **Add App** → paste

```
https://github.com/rosinski141/TradePhoneNotify
```

**Or use the config link** from the phone, which pre-fills everything (Obtainium shows the raw
config for confirmation before adding anything):

```
obtainium://app/%7B%22id%22%3A%22com.mati.tradenotify%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Frosinski141%2FTradePhoneNotify%22%2C%22author%22%3A%22rosinski141%22%2C%22name%22%3A%22TradeNotify%22%7D
```

That link is this config, URL-encoded:

```json
{"id":"com.mati.tradenotify","url":"https://github.com/rosinski141/TradePhoneNotify","author":"rosinski141","name":"TradeNotify"}
```

> **Obtainium does not necessarily remove the "Controlled by restricted setting" step.** Whether
> Android applies that block depends on the installer being session-based, and installing through
> another app does not guarantee it — F-Droid's client hits the same restriction
> ([fdroidclient#2680](https://gitlab.com/fdroid/fdroidclient/-/work_items/2680)). Assume you still
> need **App info → ⋮ → Allow restricted settings** once, on first install. What Obtainium reliably
> solves is *updates*, not that first grant.

The app also checks for new releases itself — **Settings → Version** — so Obtainium is a
convenience, not a requirement.

---

## How it detects signals

TradeNotify reads the notifications that the Telegram app on your phone already produces, via
Android's `NotificationListenerService`. That means:

- **No Telegram credentials, no `api_id`, no bot.** It works for channels you merely subscribe to,
  including ones you don't own and can't add a bot to.
- **The channel must not be muted in Telegram.** TradeNotify only sees what Telegram itself
  notifies about. This is the single most common reason for it to go quiet, so the app watches for
  it and warns you (see *Nothing heard recently* on the Status screen).
- **Very long posts can arrive truncated.** The parser prefers Telegram's `MessagingStyle` extras,
  which usually carry the full message, and falls back through big-text, content-text and
  inbox-style lines.

`SignalSource`/`Signal` are the seam: a TDLib (MTProto) backend could feed the same pipeline later
without touching the rules, alarm or UI layers.

## Requirements

Already installed on this machine by the setup step:

| Component | Version |
|---|---|
| JDK | Temurin 21.0.12 (`JAVA_HOME`) |
| Android SDK | platform `android-37.0`, build-tools `37.0.0`, platform-tools |
| Gradle | 9.7.1, via the wrapper (`./gradlew`) |
| AGP / Kotlin | 9.4.0 / 2.3.21 (Kotlin is built into AGP 9 — there is no `kotlin-android` plugin) |

`compileSdk 37`, `targetSdk 36`, `minSdk 29`.

`JAVA_HOME` and `ANDROID_HOME` are set as persistent user environment variables, and
`local.properties` points at the SDK.

## Build

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew test                 # 65 unit tests, no device needed
./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk (~2.9 MB)
                               # signed when keystore.properties exists, unsigned otherwise
```

## Install

With USB debugging on and the phone plugged in:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Otherwise copy `app-debug.apk` to the phone and open it to sideload.

## First run

The Status screen has a setup checklist; every row that isn't green has a **Fix** button that opens
the right system settings page.

| Grant | Why |
|---|---|
| Notification access | **Required.** Nothing is detected without it. |
| Show notifications | **Required.** The alarm is delivered as a notification. |
| Alarm channel enabled | **Required.** The "Signal alarms" category must not be off. |
| Full-screen alarms | Optional. Lets the alarm take over the lock screen. Android 14+ restricts this to alarm/calling apps. |
| Unrestricted battery | Optional but recommended — stops the system unbinding the listener. |
| Ring through Do Not Disturb | Optional. |

Then press **Test alarm**. It runs the real alarm path, not a simulation.

To add a rule, open **Seen channels** and tap a channel Telegram has already notified about — the
exact name is filled in for you. Typing channel names by hand is the easiest way to build a rule
that silently never matches.

## Rules

A message alarms when its channel matches **and** it passes the keyword filters:

- **Must contain** — one keyword per line. Any one is enough, or switch to requiring all. Leave
  empty to alarm on every message in the channel.
- **Must NOT contain** — suppresses follow-ups like `TP HIT` or `CLOSED`.
- Matching is case-insensitive substring. Wrap a keyword in slashes for a regex: `/^BUY\s+\w+/`.
- **Cooldown** stops a burst from alarming repeatedly. It is persisted, so it survives the listener
  process being killed and restarted.

The rule editor has a **Test this rule** box: paste a real message and it tells you immediately
whether it would alarm.

## Testing end to end without waiting for a signal

Debug builds accept a synthetic signal from adb, which goes through the exact production pipeline —
matching, cooldown, history and alarm:

```bash
adb shell am broadcast \
  -n com.mati.tradenotify.debug/com.mati.tradenotify.debug.DebugInjectReceiver \
  -a com.mati.tradenotify.DEBUG_INJECT \
  -e channel "FX Signals Pro" \
  -e text "BUY EURUSD @ 1.0840 SL 1.0810 TP 1.0900"
```

Note the `.debug` application id suffix. Worth confirming:

1. The alarm rings and the dismiss screen appears over the lock screen.
2. Re-running immediately is suppressed by the cooldown (History shows it as `COOLDOWN`).
3. A text containing an exclude keyword is logged as `NO_MATCH` and stays silent.

Follow the pipeline with:

```bash
adb logcat -s TradeNotify
```

Honor and Huawei devices encrypt third-party logcat output, so this shows nothing there — use the
in-app **History** screen instead, which records every message seen and why it did or did not fire.

## Why it might go quiet

**History records every message seen, matched or not** — it is the tool for answering "why didn't
my rule fire?". Beyond that, two failures are silent by nature:

- The channel got muted in Telegram.
- An OEM battery manager unbound the listener. Settings shows instructions for the phone you are
  actually on; on Honor/Huawei the one that matters is Battery → App launch → turn off "Manage
  automatically" for TradeNotify.

A watchdog re-binds the listener every 15 minutes and posts a visible warning if notification
access has been revoked.

## Layout

```
ingest/    listener, notification parsing, dedupe, pipeline, watchdog
match/     rule matching, quiet hours, channel health  — pure Kotlin, unit tested
alarm/     foreground service (sound/vibration/wakelock), full-screen activity, snooze
data/      Room database, settings
ui/        Compose screens, permission checklist
```
