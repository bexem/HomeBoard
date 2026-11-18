# HomeBoard, tuned for my living room

This is the launcher on my Shield. It's a fork of [fplanque/HomeBoard](https://github.com/fplanque/HomeBoard), which is the actual project: a working TV launcher that plays cameras, talks to Home Assistant and Frigate, and gets out of the way. All of that is his work. Mine sits on top of it.

**This is not meant to compete with it or replace it.** It's a fork of one TV, for one household. If the author ever wants any of what's in here, I'd be glad to send it and see it land upstream. If he doesn't, that's fair enough: it stays mine, a tailored launcher built on something that already works. It is never going to be "the better HomeBoard", and if it ever reads like that, that's not what I mean.

I built it with AI and I'm not going to hide it. The ideas, the screens, what's broken and what to try next are mine. The Kotlin is an agent's, run against my own TV, and I'm the one sitting there with the remote saying what's still wrong.

`main` is upstream, untouched. My work is on `fork/m1`, one commit per thing that got built, merged with upstream in single commits so the history stays readable. [FORK.md](FORK.md) is the log: what changed, what I saw on the TV, what broke, what I did about it, and what I'm still not sure about.

There's no APK here and no releases. Build it yourself, see the bottom. It keeps upstream's package name on purpose, so Android treats it as the same app: it can't sit next to an upstream install, and putting one over the other fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` because the keys differ. Uninstall first, or don't install it.

## What's different, in plain words

**The settings are a page now.** They used to be a dialog, and on a 1080p TV some of it was simply off the screen, so I couldn't reach eight of my own settings. Now it's a full page: categories down the left, a search box, the settings in cards, and each one says what it does. Changing one doesn't need a restart. Every setting is described in one place, so a new one doesn't mean another row to hand-build.

**There's a calendar card.** I wanted bin day and shift work on the home screen, not in my phone. You pick the calendars, put them in priority order, give them a colour, and it shows them by day or grouped by calendar, all-day events included. Events that are over can drop off right away or after up to 24 hours, so the 09:00 standup isn't still there at lunch.

**The Assistant tab works like a remote.** The tab is cards per room with the devices I actually chose, and the list beside the camera is its own list, so I'm not forced to show the same things in both places. Left and right change a light's brightness, the alarm arms with a code it doesn't keep, the robot starts, pauses or docks, and its cleaning rooms come from the robot itself, not from something I typed in.

**Cameras can come from Home Assistant.** One TV doesn't get the cameras' own addresses and passwords; it gets the picture through Home Assistant instead. One camera live, the others as a still picture that refreshes, which is enough for a grid.

**The Home tab can be laid out the way I want.** Each camera remembers its own shape, and if I pick 4:3 by hand it stays 4:3. The space beside the picture has sizes (leave it, centre, give the cards a bit, half, all of it), the cards can be left or right, the app row can centre itself when there aren't enough apps to reach the edge.

**There's an All apps page.** The row wasn't enough: one page with every app, the same menu per app, and a favourites editor for the order of the row.

**The count by the clock is a door.** The Shield won't let an app open its own notification panel, so the launcher draws one from a listener. The count can be Frigate alerts, every notification, or only the ones that can be dismissed, and it opens whatever it counts. Red when something arrived, grey when it's only services talking to themselves.

**Burn-in protection.** Settings call it that because that's what it's for: the same bright pixels in the same place for months is what leaves a shadow on a panel. So a session gets a different camera, a different button order and a different room card order, and the picture and cards can swap sides. One order per session, never mid-evening. The code still says "shuffle", which is the mechanism, not the reason.

**A background.** A colour or a picture behind everything, with fit and a darkening slider. Still a dark launcher, because a light theme means changing every colour in the app.

**Smaller things I got tired of.** Names on screen for Home Assistant devices without renaming anything on the server. Service buttons picked from the server's own list instead of typing a service name. The Home Assistant address, login and logout as three rows instead of one door. The home button helper as a row that says whether it's needed. The screensaver's settings as rows on the page instead of a popup. One colour picker everywhere, one device picker everywhere.

**Upstream 1.3.0 is all in here.** The Clips tab, asking Android to be the default home app, sending settings to a TV by address, the lag watch, the certificate pinning, the newer camera pipeline. Three of its switches used to live in its launcher dialog, so they're rows on the page here: default home app, pure black background, debug info. The screensaver's start delay sits inside its status screen. Upstream's `CH` mark over the camera is gone; what it did is the "Channel keys switch cameras" setting.

## What I fixed

Once my cameras started advertising an audio track, every picture went black. Upstream edits the stream description to strip audio when a camera's sound is off, and the player then never drew a frame. The description goes over untouched now and the audio is switched off in the player instead.

## What the TV caught in my own work

FORK.md also has the unflattering part: the first settings menu couldn't scroll, focus could dead-end in a dialog, a row drew but couldn't be selected, a setting did nothing, a header had a button's width of gap before its X. Those are repairs, filed as repairs with the symptom and the check that closed them. They're not features and I don't count them as any.

## Build it

Open the folder in Android Studio, or set `JAVA_HOME` to the JDK that ships with it and run:

```bash
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. To put it on a TV over the network, with the TV in developer mode:

```bash
adb connect TV_IP_ADDRESS:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A build you sign yourself can't update one from upstream's releases page, and the other way round, because Android only accepts updates signed with the same key.

## Privacy

Same as upstream: no account, no analytics, nothing of mine in the middle. It talks to your Home Assistant, your Frigate and your own TV. Two things worth naming, because they go further than upstream: if you point the count at the TV's notifications, the launcher reads them so it can draw the list, and that list only ever lives in memory; and Android's cloud backup is off, so the Home Assistant and Frigate logins can't end up in a Google backup.

## Licence

Apache 2.0, same as upstream. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

Upstream, and all the launcher this fork builds on: copyright 2026 Evo Factory S.à r.l. The fork's own changes, the ones in [FORK.md](FORK.md), are released under the same licence.
