# Signal Watch

A personal Android app (sideloaded, not published) that watches one YouTube trader's channel:

1. **Go-live alert**: polls the channel's free RSS feed, confirms with `videos.list` (1 unit), and notifies you when a stream starts.
2. **Stream player**: the official YouTube IFrame embed (android-youtube-player). Nothing is downloaded or recorded.
3. **Built-in chat**: read-only live chat, owner/mod badges, "All chat / Signals only" filter.
4. **Signal catcher**: parses trade calls posted by the owner (and optionally moderators).
5. **Loud signal alarm**: alarm-stream sound, strong vibration, and a full-screen alert over the lock screen.

## Project layout

```
signal-parser/        pure Kotlin/JVM module (no Android): SignalParser, SignalDeduplicator + unit tests
app/src/main/java/com/signalwatch/app/
  data/api            Retrofit YouTube Data API v3, DTOs, API-key interceptor
  data/rss            channel RSS feed (no quota)
  data/settings       DataStore settings
  data/quota          daily quota counter (resets at midnight Pacific)
  data/repo           YouTubeRepository + models
  service             WatchEngine (polling loop) + WatchService (foreground service)
  notify              notification channels, alarm notification, dismiss receiver
  ui/                 theme, components, Offline / Live / Settings / Alert screens
app/src/debug/        "Simulate live stream" (debug builds only)
app/src/release/      empty stub of the simulator
```

## Building

There's no Android SDK on the phone, so GitHub Actions builds the app (`.github/workflows/build.yml`).
Every push to `main` runs the SignalParser tests, builds both APKs and publishes them as a release:

- `SignalWatch.apk`: the normal app (`com.signalwatch.app`)
- `SignalWatch-debug.apk`: the same app plus the **Simulate live stream** switch (`com.signalwatch.app.debug`, installs alongside)

Repo secrets used by CI: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD` (signing) and `YOUTUBE_API_KEY`.

## 1. Add the API key

1. In [Google Cloud Console](https://console.cloud.google.com/), create a project, enable **YouTube Data API v3**, and create an **API key**.
2. Local builds: put it in `local.properties` (git-ignored, never commit it):
   ```
   YOUTUBE_API_KEY=AIza...
   ```
   CI builds: store it as a repo secret and the workflow writes `local.properties` for you:
   ```
   gh secret set YOUTUBE_API_KEY
   ```
3. The key is read into `BuildConfig.YOUTUBE_API_KEY` at build time. It's not hard-coded anywhere.

## 2. Restrict the key

In the console, open the key, then:

- **API restrictions** → Restrict key → only **YouTube Data API v3**.
- **Application restrictions** → **Android apps** → add both packages with the signing certificate's SHA-1:
  - `com.signalwatch.app`
  - `com.signalwatch.app.debug`

  Get the SHA-1 with:
  ```
  keytool -list -v -keystore ~/SignalWatch-signing/signalwatch.keystore -alias signalwatch
  ```
  Both build types are signed with this key, so one SHA-1 covers both.

The app sends the `X-Android-Package` and `X-Android-Cert` headers that Google checks for Android-restricted keys (REST calls don't send them on their own).

> The key ships inside the APK. Anyone with the APK can pull it out, and the Android restriction only checks headers, which can be faked. Keep the API restriction to YouTube Data API v3 only, so the worst a leaked key can do is use up your free daily quota.

## 3. Set the channel handle

Open the app → settings icon (top right) → **Channel handle** → type `@handle` (a full `youtube.com/@handle` URL also works) → **Save**.
The handle is resolved once with `channels.list?forHandle=` (1 unit). The channel ID, name and avatar are cached.

## 4. Grant permissions

- **Notifications** (Android 13+): the app asks on first launch. If you said no: Settings screen → **Notifications** → turn them on.
- **Full-screen alerts** (Android 14+): needed for the alert to appear over the lock screen. If it's off, the Offline screen shows a warning. Tap it (or Settings → **Full-screen alerts**) to open the system page and allow it.
- **Battery**: set Signal Watch to **Unrestricted** (long-press the app icon → App info → Battery). Otherwise Doze can pause polling while the screen is off.

Watching restarts by itself after a reboot or an app update, as long as Go-live alert or Signal alerts is on. After a reboot it starts once you unlock the phone for the first time.

Then tap **Send test alert**. It sends a fake owner signal through the whole pipeline (parser → de-dup → alarm → full-screen Alert screen).

## Quota

Costs from the [official quota table](https://developers.google.com/youtube/v3/determine_quota_cost) (checked 2026-10-25):

| Call | Units |
|---|---|
| `channels.list` | 1 |
| `videos.list` | 1 |
| `liveChatMessages.list` | 5 |

The default allowance is 10,000 units/day. `search.list` is never used.

- **Offline**: the RSS feed is free. `videos.list` only runs for the newest 5 videos that aren't already known to be finished, so most checks cost 0 units.
- **Live**: chat is polled at the server's `pollingIntervalMillis`, but never faster than every 8 s. At the floor that's ~450 calls an hour, about 2,250 units, so a full quota covers roughly 4 hours of chat a day.
- Settings → **API usage today** shows the app's own running count. It's an estimate and resets at midnight Pacific.

## Signal rules

A message counts only if:

- the author is the channel owner, or a moderator when "Trust signals from" is **Owner + mods**, and
- it has a **direction** (buy/sell/long/short), a **symbol** (XAUUSD, EURUSD, …, aliases like gold, nas, nasdaq, dow, dax, btc), and at least one of **entry / SL / TP**.

Recaps ("TP1 hit", "closed my buy"), questions, negations ("don't sell"), lot sizes, percentages and clock times are ignored.
The same direction + symbol + entry within 10 minutes alerts only once. The card keeps the most complete version.

## Debug: Simulate live stream

Install `SignalWatch-debug.apk` → Settings → **Debug** → **Simulate live stream**. The app pretends to be live with a sample video and scripted chat: viewer noise, a fake viewer "signal" that must be ignored, an owner signal, a mod repost with more TPs (de-duplicated), and recap messages.

## Fonts

IBM Plex Sans and IBM Plex Mono are bundled in `app/src/main/res/font` under the SIL Open Font License 1.1.
