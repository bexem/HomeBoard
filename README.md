# HomeBoard — a fork

A personal fork of [fplanque/HomeBoard](https://github.com/fplanque/HomeBoard) (Apache-2.0), running on an NVIDIA Shield in a Home Assistant household. `main` is upstream unmodified at `9cce11b` (1.2.0, `versionCode 17`); the work is on `fork/*`, one commit per built milestone, 40 of them. [FORK.md](FORK.md) is the ledger: per milestone, the files touched, the designs worth keeping, what was verified on the TV — and the defects found there and fixed.

There are no releases and no APKs here. Build one yourself, see [Build from source](#build-from-source). This fork keeps upstream's `applicationId` and `versionCode` deliberately, so a build of it and an upstream release are the same app to Android: they cannot sit side by side, and installing one over the other fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` because the signing keys differ. Uninstall first.

## What the fork adds

**The settings are a screen of their own.** Upstream opens a dialog of hand-written rows. Here every setting is one entry in `SettingsSchema.kt` — key, category, title, description, control kind, and the condition under which it applies — and `SettingsPage.kt` draws all of it: a rail of ten entries, a search field that filters every row across all of them, rows grouped into cards by subject, a drawn switch or stepper per control, rows that hide while they cannot do anything, and doors that state what is behind them ("6 cameras", "Devices on it · 13 chosen"). A new setting is one schema entry. The old dialog's body was deleted rather than kept as a second renderer with a second set of focus paths.

**Settings travel between TVs with their lists.** Export and import share one checklist that names the file and each section it will replace — the cameras, the Home Assistant login, its sensors and actions, the Frigate login, the settings as a set, the app tiles in their order — and reach the file three ways: the system picker, the TV's own folder browser, or the app folder for `adb push`/`pull`. The format is v3: every preference, ordered lists included, sits under one "Settings" line instead of a hand-picked subset. The newest three exports are kept.

**A calendar card.** The calendars you chose, in priority order, each with a colour from the launcher's one picker, grouped today/tomorrow or by calendar, all-day entries included, a set number of events, and an empty state that can hide itself. It reads Home Assistant over REST (`/api/calendars`) because `calendar/get_events` is not registered on every server, and accepts both shapes of event.

**Cameras from Home Assistant.** A TV that may reach Home Assistant but not the cameras' own addresses gets the picture from Home Assistant instead: one chosen camera live over HLS, the rest as `camera/proxy` stills refreshed every ten seconds, all with the TV's own token. Direct RTSP stays the default.

**An All apps page, and favourites.** An overlay page with every installed app — grid or sideways scroll, one to six lines, the same tile menu as the row (Pin/Unpin, Hide/Unhide, Set icon, Info, Uninstall) — plus an editor for which apps the row shows and in what order, a row that wraps at its ends, and an option to centre the row when the apps do not reach the edge.

**A background.** A colour or a picture behind everything, with fit and a scrim, over the unchanged dark scheme.

**A shuffle, per session.** One seed per session: the camera on the Home tab, the top-bar actions and the Assistant cards are ordered once when the TV is switched on and stay put, re-rolled after a configurable number of hours, with the camera-and-widgets sides optionally swapped. The app row is exempt, because favourites curate it. The switches apply live.

**The TV's own notifications, and a count that says what it counts.** A notification listener and a panel drawn by the launcher, since this Shield's notification panel cannot be opened by another app. The badge beside the clock counts unseen Frigate alerts, every notification, or only the dismissible ones, opens what it counts, and turns grey when all it has is the services' own ongoing notifications. "Mark the alerts as reviewed" clears the dot and the count without opening the tab.

**Smaller additions.** Names on screen for Home Assistant entities, without touching an id; service tiles picked from the server's own list; vacuum room cleaning from the robot's own rooms; OK on the weather opening the forecast; the Home Assistant address, login and logout as three rows rather than one door; the home helper as a row that states whether it is needed; Frigate's own mark in the rail. And one implementation of each thing that used to exist twice: a colour picker (a grid with recent colours), an entity picker whose OK says what it does, a URL normaliser, a toggle row, a dialog header, an empty state, a focus request that survives layout.

## Where it differs from upstream

- **Assistant tab:** per-room cards of devices you choose, split from the list beside the camera. Lights and fans step their level with left/right, switches toggle, alarms arm and disarm with a code that is not kept, robots start, pause and dock, scripts run.
- **Home tab:** each camera remembers the shape its stream reports and a chosen shape sticks; "Space beside the camera" is five widths (the gap, centred, two fifths, half, the rest); the widgets can sit either side and the slack goes between them or to the two sides; the app row can centre.
- **Cameras tab:** the alert strip is its own switch and gives the grid that room; a grid that fits uses the screen's width.
- **Screensaver:** its settings are rows on the page, not a dialog behind a door, and its camera list uses the shared picker.
- **Removed:** upstream's `CH` mark over the Home camera. The behaviour it advertised is now the "Channel keys switch cameras" setting.

## Fixed upstream

**Every camera went black** once the cameras advertised an AAC audio track: upstream rewrote the stream description to drop audio when a camera's sound was off, and the player then never produced a frame. The description is handed over untouched now and the audio is disabled through the player's own track selection.

The ledger also records what the TV caught in this fork's own work: the first settings menu clipped eight of its thirty-nine rows on a 1080p screen, focus could dead-end in a dialog, a vacuum row drew but could not be selected, "Wider widgets" changed nothing, and a dialog header left a button's width of gap before its X. Those are defects found on the device and fixed, with the symptom, the cause and the check that closed them, per milestone in [FORK.md](FORK.md). They are not features.

Everything below is upstream's README, unchanged apart from this section.

---

A launcher for Android TV and Google TV that puts your apps, cameras, alerts and Home Assistant on one calm screen. No banners to scroll past, no account to create.

Website: https://www.evofactory.com/homeboard/

## Features

- **Cameras:** RTSP cameras in H.264 or H.265, with an optional substream for the grid. It plays as many live as your TV can decode, and shows the rest as their last picture.
- **Home Assistant:** pick sensors, scenes and scripts and put them in your order, with the weather and its forecast. The Assistant tab shows them all, in a card per room, or one of your Home Assistant dashboards, and up to three scenes or scripts of your choice are also buttons in the top bar.
- **Frigate alerts:** the last seven days of alerts, with clips that play full screen.
- **Continue Watching:** what you started in the last two weeks comes first. Apps decide what they share with the TV, so this varies from one app, and one TV, to the next.
- **App row:** move tiles, hide them, see app info or uninstall.
- **Home button helper:** optionally makes the Home button bring you to HomeBoard.
- **TV to TV:** send your settings to another TV on your network.
- **Diagnostics:** a built-in ping tool, network status, and debug logs with logins and tokens stripped out.

Cameras, Home Assistant and Frigate are all optional. Without them, HomeBoard is the most minimalistic launcher ever: your apps, and you need to scroll down to see Continue Watching.

## Privacy

There's no account, no analytics and no HomeBoard server. The app connects to the addresses you enter, to other TVs on your network when you transfer settings, and to wherever your other apps keep the pictures of the shows in Continue Watching. Your Home Assistant password isn't stored: the TV keeps a login you can revoke from your Home Assistant profile.

## Requirements

An Android TV or Google TV on Android 9 or later. Tested on Sony BRAVIA TVs on Android 9 and Google TV 14. Please share your own experience.

## Install the APK

Download the `.apk` file from the [latest release](../../releases/latest), then install it in one of these two ways.

### With a computer

This needs [adb](https://developer.android.com/tools/releases/platform-tools) on your computer, with the TV and computer on the same network.

1. On the TV, open **Settings → Device Preferences → About** and press OK on **Build** seven times to turn on developer options. The menu names vary by TV.
2. In **Settings → Device Preferences → Developer options**, turn on **USB debugging**. On Google TV 14, you may need **Wireless debugging** instead, which shows the address and port to use.
3. Connect, using your TV's IP address. Accept the prompt on the TV if one appears:

   ```bash
   adb connect TV_IP_ADDRESS:5555
   ```

4. Install the APK:

   ```bash
   adb install -r HomeBoard.apk
   ```

Use the name of the file you downloaded in place of `HomeBoard.apk`.

### Without a computer

1. Copy the APK to the TV with a file manager app, or download it with a browser app.
2. When Android asks, allow that app to install unknown apps.
3. Open the APK and choose **Install**.

### Afterwards

HomeBoard appears in your TV's apps. To make the Home button open it, go to the cog → HomeBoard settings, choose how it should take over, and turn on **HomeBoard Helper** in your TV's Accessibility settings. The helper is optional, and if HomeBoard fails to start a few times in a row, it switches itself off.

To update, install a newer APK the same way. Your settings stay in place.

## Build from source

Open this folder in Android Studio, or build a debug APK from the command line, with `JAVA_HOME` set to the JDK that comes with Android Studio:

```bash
./gradlew assembleDebug
```

The APK is in `app/build/outputs/apk/debug/`. A build you sign yourself can't update one from the releases page, or the other way round: Android only accepts updates signed with the same key, so you'd need to uninstall first and set up HomeBoard again.

The release build looks for its signing key outside this repo, and comes out unsigned if it isn't there.

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

Upstream, and all the launcher this fork builds on: copyright 2026 Evo Factory S.à r.l.

The fork's own changes, listed in [FORK.md](FORK.md), are released under the same Apache-2.0 licence.
