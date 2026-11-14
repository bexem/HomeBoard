# HomeBoard, but it's the one I actually use

This is the launcher on the Shield in my living room. It started as [fplanque/HomeBoard](https://github.com/fplanque/HomeBoard) (Apache-2.0), and then I kept asking for things until it turned into a fork.

I'm not going to pretend otherwise: I built this with AI. The ideas, the screens, what's wrong with them and what to try next are mine; the Kotlin is an agent's, run against my own TV, and I'm the one sitting there with the remote saying what's broken. Upstream is a real, complete app and it's good, so if the above bothers you, go use it.

`main` is upstream, untouched, at `9cce11b` (1.2.0). My work is on `fork/*`: 40 commits, roughly one per thing that got built. [FORK.md](FORK.md) is the log, and it's long on purpose: what changed, what I saw on the TV, what broke, what I did about it, and what I'm still not sure about.

There's no APK here, no releases, nothing to download. Build one yourself, [Build from source](#build-from-source). It keeps upstream's `applicationId` and `versionCode` on purpose, so Android sees it as the same app: it can't sit next to an upstream release, and putting one over the other fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` because the keys differ. Uninstall first, or don't install it.

## What I added

**The settings, rebuilt.** Upstream's settings are a dialog, and on a 1080p TV a good chunk of it is simply off the screen. Mine is a page: a rail down the left, a search field that finds any setting, cards grouped by subject, rows drawn from data. Every setting is one entry in `SettingsSchema.kt` (key, title, description, which control, when it shows), so a new setting is one line and not another hand-built row with its own focus bugs. The old dialog body is deleted, not sitting there as a second thing to keep correct.

**Export and import that actually carry the settings.** One checklist names the file and what it will replace (cameras, the Home Assistant login, its sensors and actions, the Frigate login, the settings as a set, the app tiles in their order), and it can come from the system picker, the TV's own file browser, or the app folder for `adb push`/`pull`. Format is v3: every preference sits under one "Settings" line, lists included, instead of a subset I hand-picked three versions ago. Newest three exports are kept.

**A calendar card.** I wanted bin day and shift work on the home screen. You choose the calendars, give them a priority, give each a colour from the one colour picker, and it groups them by day or by calendar, all-day events included, with a cap on how many it draws. It reads Home Assistant over REST (`/api/calendars`) because `calendar/get_events` isn't registered on my server, and it accepts both shapes of event.

**Cameras from Home Assistant.** For a TV that can reach Home Assistant but shouldn't be handed the cameras' addresses: one camera live over HLS, the rest as `camera/proxy` stills every ten seconds, all with the TV's own token. Direct RTSP stays the default, because it's about two seconds against five to ten.

**All apps, and favourites.** The row alone wasn't enough, so there's a page with every installed app, grid or sideways, one to six lines, with the same menu a tile has (pin, hide, set icon, info, uninstall). Plus an editor for which apps the row shows and in what order, a row that wraps at the ends, and the option to centre it when there aren't enough apps to reach the edge.

**A background.** A colour or a picture behind everything, with fit and a scrim. Still the dark scheme, because making it light would mean touching every colour in the app.

**Shuffle.** New session, different camera, and the top bar and Assistant cards reshuffle with it. One seed per session, re-rolled after N hours, never in the middle of me watching something. The app row stays put, because favourites are the point.

**The TV's own notifications.** The Shield's notification panel can't be opened by another app, so the launcher draws its own, from a listener. The count beside the clock opens what it counts, and it can count Frigate alerts, every notification, or only the dismissible ones. Red when there's news, grey when it's just services talking to themselves. "Mark the alerts as reviewed" clears it without having to open the tab.

**Smaller stuff.** Names for Home Assistant entities without touching an id, service buttons picked from the server's own list instead of typed, the vacuum's own rooms, OK on the weather for the forecast, address/login/logout as three rows instead of one door, the home helper as a row that says whether it's needed, Frigate's own icon in the rail. And one of each thing that used to exist twice: colour picker, entity picker, URL normaliser, switch row, dialog header, empty state.

## Where it behaves differently

- **Assistant tab:** a card per room with the devices I chose, separate from the list beside the camera. Left and right step a light or a fan, OK switches them, the alarm arms and disarms with a code it doesn't keep, the robot starts, pauses and docks, scripts run.
- **Home tab:** each camera remembers the shape its stream reports, and a shape I picked by hand sticks. "Space beside the camera" has five widths now (gap, centred, two fifths, half, the rest). The widgets can sit either side, and the gap goes between them or to the sides. The app row can centre.
- **Cameras tab:** the alert strip has its own switch and gives the grid that room, and a grid that fits uses the screen's width instead of leaving a margin.
- **Screensaver:** its settings are rows on the page. It was a popup for no good reason.
- **Gone:** upstream's `CH` mark over the Home camera. The behaviour behind it is now the "Channel keys switch cameras" setting, which is where a setting belongs.

## What I fixed

Every camera went black once my cameras started advertising an AAC track: upstream edited the stream description to strip the audio when a camera's sound was off, and the player then never drew a frame. The description goes over untouched now, and the audio is turned off through the player instead.

## And the mess in between

FORK.md also has everything the TV caught in my own work, and it's not flattering: the first settings menu couldn't scroll, so eight of thirty-nine rows were unreachable. Focus could dead-end in a dialog. The vacuum row drew but couldn't be selected. "Wider widgets" changed nothing at all. A dialog header left a button's width of gap before its X. Those are repairs, listed as repairs with the symptom, the cause and the check that closed them. They're not features and I'm not counting them as any.

Everything below is upstream's README, unchanged apart from this bit.

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
