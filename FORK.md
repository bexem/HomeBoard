# Fork ledger

Every place this fork differs from upstream, so a rebase re-applies intent instead of diff archaeology.
Upstream: `fplanque/HomeBoard`, Apache-2.0. Fork work lives on `fork/*` branches.

## M1 — one settings menu, working export/import, honest helper status

### New files (no upstream conflict)

| File | What it is |
|---|---|
| `SettingsSchema.kt` | Every setting as data: key, section (a tab), title, description, kind (toggle/stepper/action), and a property reference on `LauncherPrefs`. `AllSettings` is the only registry. `currentPreferences`/`applyPreferences` turn it into/from the export file. |
| `SettingsMenuDialog.kt` | The one settings dialog: tabs from the sections, rows rendered from the schema, the helper-status block, log size and build info. `SettingsActions` maps ACTION rows to lambdas; `onSettingsChanged` tells the launcher a value moved. |
| `SettingsTransfer.kt` | Where a settings file goes: system file picker (SAF), the launcher's own folder browser, or the app folder (`adb push`/`pull`). Prefers the picker because a reinstall makes the app's own files invisible. |

### Touched files

| File | Change |
|---|---|
| `SettingsButton.kt` | The cog opens the settings directly; its `ContextMenu` and five callbacks are gone. Signature is now `SettingsButton(onOpenSettings, modifier)`. |
| `LauncherSettingsDialog.kt` | The dialog body (both tabs, the status text, the export/import buttons, `BuildInfo`, `LauncherAdvancedSettings`) is deleted. What remains here is what the menu and the top bar still need: `HomeHelperWarning`, `HomeOverrideConsentDialog` (now internal, called by the menu), `restartApp`, `HomeOption`, and the two switch colours. Kept in this file deliberately: it is the smallest diff for upstream. |
| `HomeOverrideService.kt` | Added `HomeOverrideState` (Off / EnabledNotRunning / Running), `Context.isDefaultHomeApp()`, `Context.homeOverrideState()` and `rememberHomeOverrideState()`. No change to the service itself. |
| `MainActivity.kt` | `launcherSettingsOpen` → `settingsOpen`; the cog and the helper warning open the new menu; `SettingsMenuDialog(actions = SettingsActions(...), onSettingsChanged = ...)`; `applyImport` now also applies `file.preferences` through the schema; `openAccessibilitySettings()` helper. |
| `ExportImport.kt` | `SettingsFile.preferences` (version 3, checked `1..3`), the "Settings" checklist section, and both file dialogs route through `SettingsTransfer` (picker / browser / app folder) instead of only the browser. `ExportToFile` shows the checklist before the transport. |

### Deliberate designs worth keeping

- **Consent is declarative**: `SettingDef.requiresConsent` gates the two helper toggles; the menu shows
  `HomeOverrideConsentDialog` once (guarded by `homeOverrideAccepted`). No key names in UI code.
- **Helper status is information, not a row in the focus list**: on a TV where this app is the home app
  the block holds no button, and a focus key with nothing focusable leaves the dialog on the X, where OK
  closes it (this happened; fixed).
- **Empty sections are hidden**, so a tab never opens onto nothing.
- `restartApp()` stays `makeRestartActivityTask` + `killProcess`: it is the one restart path that keeps
  the accessibility grant (D8). Do not "fix" it into a force-stop.

### Not done in M1 (next milestones)

The Assistant device tiles (M4).

## M3 — the calendar card

### New files

| File | What it is |
|---|---|
| `HaCalendar.kt` | `HaEvent` (all-day events carry the day alone), `CalendarConfig`, the merge (day, then time, all-day first, calendar order as tie-break), the day grouping (Today/Tomorrow or dates), and `haCalendarEvents()` — the REST read. |
| `CalendarWidget.kt` | The card, after the Calendar Pro card Ema uses on his dashboard: one block per event with a coloured bar for its calendar, the title in bold, then a clock and `HaEvent.detailLine` ("All day", "19:00, ends tomorrow at 07:00", "Tomorrow 19:00, ends Thu at 07:00"). Three entries tall at most, so the sensors keep their space; a whole window is cut, never scrolled. Absent with no calendars or an unsupported server; "Connecting" during the first read; the last events dimmed when offline; "Nothing scheduled in the next N days" unless `calendar_hide_empty`. |
| `CalendarPickerDialog.kt` | Which calendars, in priority order: read live from `getStates()` when it opens, OK chooses, left/right set priority ("Priority 2 of 4"). The list keeps the server's order so choosing never moves the row under the remote. |
| `CalendarColoursDialog.kt` | The colour of each chosen calendar: a dot, its name from Home Assistant, and left/right through an eight-colour palette. Stored per entity (`calendar_colours`, "id=#RRGGBB"), so reordering calendars keeps their colours. |

### Touched files

| File | Change |
|---|---|
| `HomeAssistant.kt` | `httpClient` is `internal` so the calendar read can use the shared client and the TV's token. |
| `HomeAssistantPanel.kt` | `HaConnection.calendarEvents` / `calendarsUnsupported` / `calendarsLoaded`; a polling child of the session (`refreshCalendars`, `calendar_refresh_minutes`); `CalendarCard` between the weather tiles and the sensors; `HomeAssistantPanel(calendar = …)`. |
| `LauncherPrefs.kt` | `calendarEntities`, `calendarDays` (3), `calendarMaxEvents` (8), `calendarRefreshMinutes` (30), `calendarGroupStyle`, `calendarShowAllDay`, `calendarHideEmpty`, and `calendarConfig()`. |
| `SettingsSchema.kt` | A `Calendar` section with seven rows; a `SettingKind.List` with a `strings` property, so ordered id lists are part of every export (`calendar_entities`, `favourite_apps`). |
| `SettingsMenuDialog.kt` | List rows render as action rows; `openCalendars` in `SettingsActions`. |
| `ExportImport.kt` | `preferences` carries JSON arrays and reads them back as lists — before this, lists were silently dropped. |
| `MainActivity.kt` | Reads `calendarConfig` at start and on any settings change; passes it to the panel and to `rememberHaConnection`; the picker overlay writes `calendar_entities` at once. |

### Deliberate designs worth keeping

- **The sensors card can be hidden and the calendar takes its space**: `home_sensors` off drops the whole
  card from the panel, and the calendar stops being capped at three entries (`expanded`) and takes
  `weight(1f)` — with the top bar's way in moved onto it, so nothing becomes unreachable. Its title is
  "Sensors", not "Other sensors".
- **The card can group by calendar**: `calendar_grouping` = `priority` sorts by calendar rank first, then
  time, so a work shift is not pushed below a birthday or a bin collection; `time` reads as a diary.
- **No calendar limit, a priority list instead**: every chosen calendar contributes events; the picker is
  ranked (priority 1..N), and the rank is the order the card and the merge use. `calendar_max_events` is a
  ceiling on listed events (default 20, range 1..50) and the panel's own height normally bites first.
- **A calendar's colour is the launcher's**: Home Assistant exposes no colour for a calendar entity over its
  API (the state carries times and a message only), so the palette is local, defaulting to the colour of
  the calendar's position — the rule Home Assistant's own card follows — and overridable per calendar.
- **The calendar is read over REST, not the websocket**: `calendar/get_events` is not registered on this
  Home Assistant (2026.11.0 answers "Unknown command"), while `GET /api/calendars/<id>` is documented and
  returns the same events in a different shape (`start: {date|dateTime}` instead of a string). `HaEvent.from`
  accepts both, so a future server that does register the command needs no change.
- **The window starts at midnight, not at "now"**: Home Assistant leaves out events that started earlier,
  so querying from the current minute hid today's all-day and already-running events.
- **`optString` is not a string reader**: Android's `optString` returns the JSON text for an object, so
  `{"date":"2026-11-10"}` came back as a string that matched neither shape and the event was dropped
  silently. The private reader takes `opt(key) as? String` only.
- **Every import asks first**: the file picker, the TV's own browser and the app folder all end in the same
  checklist, which now names the file ("From homeboard-settings-2026-11-10-171505.json. What is ticked
  replaces this TV's own."). Before this the picker and the app folder applied the whole file at once,
  with no way to see what it would replace — that is how a test export overwrote live settings.
- **Import refreshes the launcher**: `applyImport` re-reads every preference the running UI renders
  (Home-tab rows, favourites, phone apps, camera sound, calendars), not just the ones M1 knew about;
  before this the prefs changed under a UI still showing the old values.
- **A list row is also an export field**: `SettingKind.List` carries the property, so calendars and
  favourites travel with the settings file and come back on import (verified: export → change → import).

## M2 — the Home tab, tamed, and camera sound

### New files

| File | What it is |
|---|---|
| `AllAppsPage.kt` | Every installed app on demand: a full-screen overlay, `LazyHorizontalGrid` or `LazyVerticalGrid` by the scroll setting, one to six lines, back closes. Tiles carry the row's menu (Pin / Unpin, Hide / Unhide, Info, Show hidden apps). A page rather than a tab because the four tabs are wired to the remote's colour buttons and to MainActivity's key handling. |
| `FavouritesEditorDialog.kt` | Which apps the row shows and in what order: pinned apps first in their order, then the rest, a checkbox per app. OK pins; left and right on a pinned row move it along the row. Empty means every app, as before favourites existed. |

### Touched files

| File | Change |
|---|---|
| `AppRow.kt` | `favourites`, `allAppsTile`, `wrapRow`, `onOpenAllApps`, `onPin`/`onUnpin`, `restoreKey`/`onRestored`. The favourites filter the row and set its order; an `ALL_APPS_TILE_KEY` tile follows the last app; right off the last tile and left off the first wrap when `wrapRow`; the tile menu gains Pin/Unpin; a closed page hands focus back to the tile. |
| `LauncherPrefs.kt` | `homeAlertsRow` (off), `continueWatching` (on), `appRowWrap` (on), `hidePhoneApps` (off), `homeSensors` (on), `allAppsTile` (on), `allAppsLines` (2, 1..6, read as 1 when an older `all_apps_page=row` is present), `allAppsScroll` (horizontal), `favouriteApps`, `cameraSound` (full screen). |
| `SettingsSchema.kt` | A `Choice` kind (text property + `choices`) for the scroll direction; a `Home` section with those settings; `SettingAction.OpenFavourites`. |
| `SettingsMenuDialog.kt` | `ChoiceRow` (OK opens a menu of the choices); `StepperRow` (the row is the single focus target, left and right change the value, the + and − are drawn rather than focusable); `openFavourites` in `SettingsActions`. |
| `MainActivity.kt` | Reads the prefs; `soundEnabled = cameraSound == CAMERA_SOUND_FULLSCREEN` goes to `CameraGrid`, `MainCamera` and `FullScreenCamera`; logs the setting at start ("Camera sound: …"); `all` is filtered once by `hidePhoneApps`, so the row, the page and both menus agree; the Frigate events row and the No-alerts tile now need `homeAlertsRow` on the Home tab (Cameras keeps them); the Continue Watching row is skipped entirely when off; the page's Hide/Unhide write the same hidden list the row uses; the two overlays. |

### Deliberate designs worth keeping

- **The All apps page is an overlay, not a tab**: five tabs would mean touching the colour-button map and
  MainActivity's key routing; the overlay needs neither, and it swallows the colour keys so a tab cannot
  change underneath it.
- **Favourites are identity, not order**: order is set in two places that write the same list — left/right
  in the editor, and the row's own Move mode, which reorders the pinned apps and writes the favourites
  list back. One list, so the two cannot disagree.
- **Camera sound is two layers, on purpose**: each camera's own "Use audio track" says whether its stream
  carries audio at all, and the global "Camera sound" says whether the players are allowed to hear it
  (muted on the tiles, sound in full screen) or never. Only both on gives sound. Verified on the TV from
  the log: `Camera sound: fullscreen` → `Player … started over TCP, audio: on`; `muted` → `audio: off`.
- **Phone apps are filtered at the source**: `InstalledApp.isTvApp` already marks LEANBACK_LAUNCHER
  apps, so "Hide phone apps" is one `filter` where the list is built, not a rule each view re-applies.
- **A stepper is one focus target**: separate + and − buttons cannot be reached at the low end, where the
  disabled one is skipped and focus jumps past the row (found on the TV). Left and right on the row
  change the value; the buttons are drawn, not focusable.
- **One hidden list for both views**: hiding from the page or from the row's menu removes the app from
  both, and "Show hidden apps" shows it again in both.
- **Pinning works from the page too**: the page's tile menu writes the same `favourite_apps` list the
  row's menu does, so an app can be pinned where it is seen without going back to the row.
- **The watch-next row has its own switch**: `continue_watching` off skips the row's composition and its
  focus target, so down from the app row goes straight back to the camera. Verified by logging in and
  out on the TV, not only by the pref.
- **Wrapping is a key handler, not `focusProperties`**: `onPreviewKeyEvent` on the row intercepts only the
  step off either end, so ordinary focus search keeps doing the rest.
- **The All apps tile is reached through the last app's `right`**: a tile outside the row's index map is
  unreachable by focus search alone (found on the TV: the row stopped at the last app).

The build installed on the TV is a **debug** APK (no release keystore on this Mac yet), so it is
debuggable and `run-as` works. Release signing is a separate step before any hand-off.

## Fixed: 8 of the 39 settings rows could not be reached (2026-11-12)

Found while planning the settings rework, from the live TV: the settings dialog's body was a plain
`Column` with no scroll (`TopDialog.kt`), the Surface sized to its content, and the window clipped what
did not fit. On a 1080p TV (960x540 dp) that hid, per tab: Home's `all_apps_scroll` and `favourite_apps`,
Calendar's `calendar_show_all_day` and `calendar_hide_empty`, Cameras' `channel_keys`, `camera_sound`,
`camera_grid_live` and `minimize_video_streams` — 8 of 39 rows, including a setting FORK.md says is in
use. The dialog is now scrollable (`verticalScroll` on the body in `TopDialog.kt`), so every row is
reachable; verified on the Shield with the Cameras tab scrolled past the old fold, showing Camera sound,
Channel keys and both grid toggles.

## Also built on 2026-11-12, in the same pass

- **Rows that mean nothing are hidden**: `SettingDef.showIf` and the page and search filter on it. The
  background rows already use it (no colour row when the background is a picture, and so on).
- **The old dialog and its row renderers are deleted** (`SettingsMenuDialog` kept only `SettingsActions`,
  `SettingDef.read`, `HelperStatusRow`, `LogSize`, `BuildInfo`). A second renderer meant a second set of
  focus paths to keep correct; now there is one.
- **Focus audit of the page** (2026-11-12, after three "the cursor disappeared" reports). Every exit from
  every focusable element now names where it goes, or names `Cancel` where nothing exists:
  rail ends pinned (first stays on itself, About → Advanced, Close → itself, and Up from Close → About);
  rows: Left → rail, Right → Cancel, first row Up → search, last row Down → Cancel; search field: Left →
  rail, Down → first row, Up and Right → itself; search results: Left → rail, Right and the last hit's
  Down → Cancel, first hit Up → search; About: Left and Up → About rail item, Right → Cancel.
  Verified on the TV: Up and Right on the search field left it focused, twelve Downs scrolled the page
  with the cursor on screen, three Rights kept the row lit.
- **Rows never lose the cursor**: Right from a row is pinned to `Cancel` (nothing lives to its right, so
  geometry used to drop the focus somewhere invisible), and the last row's Down is pinned the same way.
  Only Left (rail) and Up leave a row. Verified on the TV: three Rights left the row lit, twelve Downs
  scrolled the page with the cursor still on screen.
- **Burn-in protection can swap the sides**: `shuffle_sides` puts the camera left and the widgets right, or the other
  way round, once per session — in addition to the random camera, top-bar order and card order. The camera
  slot is extracted so the row can render either order without moving any other layout code.
- **Rail ends are pinned**: Up from About goes to Advanced, Down on Close settings stays there, Up on the
  first entry stays put. Only the ends are pinned — chaining every entry broke Down.

## M7.1 — background (built)

`LauncherPrefs` gained `backgroundKind` (none by default / colour / image), `backgroundColour`,
`backgroundImage`, `backgroundFit` (fill, fit, stretch, tile, centre) and `backgroundScrim` (0..95 %,
default 65). `LauncherBackground.kt` draws the layer behind everything — a colour, or a picture from the
app's own `files/background.img`, copied in when picked so the original can move — and a black scrim over
it. The launcher keeps its one dark scheme, so surfaces stay readable: this is decoration behind a dark
room, not a light theme (which would need the 120 `MaterialTheme.colorScheme` call sites to branch).
Rows are the Launcher section's "Background" group; the colour comes from a short palette dialog, the
picture from the system file picker.

Verified on the Shield: a colour background (#1C1B1F) and an image background both drew with the surface
cards legible over them. Both test values were then put back to none.

## M4 — Assistant device tiles (first slice)

`isPickable` now includes `light`, `switch` and `fan`, so the Home Assistant dialog offers them and the
Assistant tab renders them with the existing `ToggleRow` (OK switches them; a light or fan group shows its
own card). Verified on the Shield: `light.all_lights` picked into the entities showed a **Light** card with
a switch, "All Lights". The demo entity was then removed from Ema's setup.

Brightness and fan speed are on the same row: `HaSession.setBrightness` (`light.turn_on` with
`brightness_pct`) and `setPercentage` (`set_percentage`), `HaConnection.setLevel`, and Left/Right on a
light's row step it by 10 %, with the level shown under the name while it is on. Built, **not yet seen on
the device**: the test entity was written into the prefs while the app was rewriting them, so the card
never appeared. Adding a light through the Home Assistant dialog's Sensors tab is the honest way to check
it — it needs no pref surgery.

The alarm tile is built: `alarm_control_panel` is pickable, its row shows the state, OK offers Arm home /
Arm away / Disarm, and the one chosen asks for the code in a numeric field that is cleared the moment the
call is made (`HaSession.alarm` puts it in `service_data.code` and nothing keeps it). Not yet seen on the
device: the entity was staged into the prefs and lost to the same write-back race as the light — the honest
way to add it is the Home Assistant dialog's Sensors tab.

Still to build for M4: vacuum room cleaning from the entity's `rooms` attribute, and the generic service
tile.

## M7.3 — new events badge (built)

`badge` (off by default, Cameras → Alerts → "New events badge") puts a red count beside the clock, from
Frigate's `/api/review?severity=alert` list: how many events started after `badgeSeenAt`. Opening the
Alerts tab sets that mark to now and the badge disappears; the first run seeds it to "now" so a fresh
install does not greet anyone with 50.

Verified on the Shield: before the seed, 50 alerts and a red **50** beside the clock; after seeding,
`Badge: seen=… of 50 alerts, 0 new` and no badge.

**A mistake worth remembering**: the first version built a new `FrigateClient` for every poll, which means
a new login every minute — Frigate answered "too many failed logins, try again later" and the launcher's
own alert feed stopped working for a few minutes. The poll now reuses `frigateFeed.client`, the connection
the feed already holds. One client per server, however many callers.

## M7.2 — shuffle, per session (built)

`Shuffle.kt` holds one seed for the whole session: it is rolled when the launcher starts after the TV has
been off longer than `SHUFFLE_SESSION_GAP_MS` (30 min) or after `shuffle_hours` of being left on, and
otherwise the stored seed is reused, so a system force-stop mid-evening does not reshuffle. Per-area
switches (`shuffle_camera`, `shuffle_actions`, `shuffle_cards`) plus `shuffle_hours` (0..24, 0 = a new
session only) live under Home → Shuffle. The camera on Home takes the first of the session's order, the
top-bar actions and the Assistant cards are reordered by the same seed with different salts, and the app
row is untouched because favourites curate it.

Verified on the Shield: with the three switches on and the timestamp cleared, a new seed was rolled
(`-911068700`) and the Home camera became the doorbell; two starts 20 s apart logged
`newSession=false … stored=1244843693` and kept the same order; the log line "Shuffle: last=… elapsed=…
newSession=… hoursUp=…" makes every future report checkable. Not yet seen on the device: the N-hour
re-roll (the pref write raced the app start), and the card order change visually.

## Requested 2026-11-12, not built yet (M7)

1. **Background**: a solid colour or an image, default as today. Cards, tabs and dialogs must stay
   legible: the launcher keeps its dark scheme and every surface stays translucent over the background,
   with a scrim whose strength is a setting, so white-on-white cannot happen. A light *theme* is a
   separate, larger job (120 `MaterialTheme.colorScheme` call sites).
2. **Shuffle**, as Ema specified: the order is rolled once **per session** — switching the TV on gives a
   new order, and it stays put for that session — and optionally re-rolled after **N hours** of the TV
   being left on, never while the remote is in use. Per-area switches (Home Assistant widgets, camera on
   Home, top-bar actions); the app row stays put because favourites curate it.
3. **App icons**: an override per app (an image from the TV's storage), stored with the app's key; the row
   and the All apps page draw it instead of the banner/icon.
4. **Notification badge**: a count or dot on the clock for new Frigate events since the launcher was last
   opened, optional, with the source chosen in settings (Frigate events today; Home Assistant repairs or
   unavailable entities later).

## M6 — settings as a page (built, first pass)

Status 2026-11-12: the dialog is replaced by `SettingsPage.kt`, a full-page surface with a rail of
categories (icon + label), a search field, and the category's rows as cards under group headings. The
schema gained `SettingDef.group`, so a card is a group of rows and a new setting is still one entry. The
dialog's shared row helpers (`SettingDef.read`, `HelperStatusRow`, `LogSize`, `BuildInfo`) are `internal`
now and used by the page.

Verified on the Shield: the page opens from the cog onto the rail; Down moves the rail and picking a
category swaps the page (Services shown with its three chevron rows); rows render a drawn switch, a
`− 5 +` count and chevrons; the page uses the whole screen; Back closes it in one press and leaves the
launcher as it was.

Verified since: Left from any row returns to the rail in one press (a row far down a page has no rail
item beside it, so geometry alone lost the focus — every row now names the rail as its Left target);
right/OK on a rail item lands on the category's first row; the search field filters across categories
with the section named beside each match; a count changes with left/right (Days ahead 3 → 4); and every
door says what is behind it ("6 cameras", "4 chosen", "None chosen").

### "The cursor disappears if you go down too much" (fixed in code, awaiting a clean device check)

Two causes, both in the page's list:

1. A card was **one lazy item holding all its rows**, so a group taller than the window could not scroll:
   focus moved to a row below the fold and the list had no reason to move. There is now one lazy item
   per row, and the card is drawn per row (rounded at its ends), so the focused row is always scrolled
   into view.
2. Each category shared **one scroll state**, so a new category opened scrolled to where the previous one
   had been; its first row was then off-screen, `requestFocus()` on it failed, and the remote stayed on
   the rail — which also explains "Right does nothing sometimes". Each category now has its own scroll
   state (`key(section)`), and the first-row focus is retried over a few frames instead of once.

Entry into the page was re-verified after the scroll fixes: the focus dump showed the focused node at
`[552,212][1848,316]`, the first row of the content column, so Right/OK on the rail still lands there.

The deep-down check could not be completed: the Shield's **Switch Access setup wizard**
(`com.google.android.marvin.talkback/...switchaccesslegacy.setupwizard`) takes the foreground during
repeated D-pad presses and steals focus from the launcher. That is a device-side accessibility app, not
the launcher, and it is worth knowing when a "the cursor disappeared" report comes in: an overlay that
takes focus looks exactly like a focus bug.

Still to check on the device: flipping a switch from a row, and opening a choice row's list.

Notes worth keeping:

- **Back belongs to the Activity here**: `TopDialog`-based dialogs received Back themselves, but a page
  inside the launcher's content does not — the launcher's own `BackHandler` now closes the page first,
  before it moves focus around the Home tab.
- **Up from About belongs on the rail**: picking About put the focus on its page's first element (the name
  block), which was given `up = toSearch`, so Up left the rail and jumped to the search field. The About
  page's first block now answers Up with the About rail item, so Up walks the rail like every other
  category. (Fix compiled and installed; the on-device re-check was interrupted by Plexy taking the
  foreground, so it is unconfirmed.)
- **Picking a category lands on its first row, not the search field**: the first row cannot take focus
  before the new page is laid out, so the request waits two frames; without that the focus fell through
  to the search field above. Both OK on a rail item and Right from it enter the page (Ema asked for
  both), which are the two things a two-pane layout is expected to answer.
- **Up from the first row goes to the search field, left goes to the rail**: with the field above the rows
  and the rail beside them, any other choice makes one of the three unreachable.
- The old `SettingsMenuDialog` composable is still in the tree while the page is being finished; delete it
  once the remaining interactions are verified, so the row renderers live in one place.

## M6 — settings as a page (planned, not built)

Ema's target, sketched from a desktop browser's settings: a full-page surface, a rail of categories with
icons down the left, a search field across the top, and the chosen category's settings as cards on the
right. The design review agreed and picked the same shape (its candidate B) over a single long page and
over a card dashboard.

Slices, each shippable on its own:

1. **The frame**: `SettingsScreen.kt` — full-screen overlay replacing the dialog; rail from
   `SettingSection` (icon + label), content pane scrolled, Back closes in one press.
2. **Cards and groups**: `SettingDef.group` so a section's rows read as titled cards; rows restyled with
   the control inline (switch, `- n +`, value + chevron).
3. **Choices inline**: a choice row shows its value and opens a short list in place, no ContextMenu popup
   in the middle of the screen.
4. **Search**: one field filtering every row by title and description, with the section shown beside a
   match; it also fixes "I know the setting exists, where is it".
5. **Summaries on doors**: the four list editors (cameras, favourite apps, calendars, HA cameras) show
   "6 chosen" / "Direct RTSP" on their row instead of being a cold door.

Not in scope: moving the camera, HA, Frigate, calendar and favourites editors into the page — they need
the width a dialog gives, and the page opens them as it does today.

## Not doing: mini cameras or an alerts column beside a narrow camera

Ema dropped the idea on 2026-11-12 (it was option 1c of the camera-card placement question). The gap a
non-16:9 camera leaves is handled by **Cameras → Beside the camera**: leave it, centre the picture, or
give it to the widgets. Do not resurrect the mini-preview/alerts column without asking again.

## The Home tab's camera, and the Alerts tab (2026-11-12)

- **Why the Home tab showed the Front camera**: the Home tab does not show the first camera in the
  settings list, it shows the one the remote last switched to (`camera_selected`), and that was Front
  from an earlier channel-up. New setting **Cameras → Home tab camera**: "First in the list" (the camera
  dialog's order decides) or "Last switched to". The first attempt put the override *before* the saved
  selection and was silently overwritten — it is now applied after it. Verified: `Player … started over
  TCP, …, <camera-host>:8554/doorbell` with the porch on screen.
- **The camera box takes the camera's own shape**, as Ema suggested: each camera remembers the ratio its
  stream reports (`Camera.aspect`, stored in the cameras JSON), so the Home tab is laid out correctly
  before the first frame arrives instead of after it; a camera not yet seen starts at 16:9 and is
  corrected on its first frame, then remembered. The box is sized height-first for anything taller than
  16:9 (`matchHeightConstraintsFirst`), which is what the first attempt got wrong: it applied a ratio
  *inside* the caller's 16:9 slot, where nothing can shrink it. Verified: the doorbell stored
  `aspect=1.3333` and the Home box became 4:3 with the pillars gone.
- **The manual shape** is a cycling menu entry, "Shape: Auto / 16:9 / 4:3 / 1:1 / 5:4 / 21:9", stored in
  `Camera.aspect` beside the learned value — a camera never seen has 0, and choosing Auto lets the stream
  set it again. Verified on the TV: the menu read "Shape: 4:3" for the doorbell, the ratio it had learned.
- **What fills the gap** a narrow picture leaves is a setting, **Cameras → Beside the camera**: "Leave the
  gap" (default), "Centre the picture", "Wider widgets". The slot is sized from the camera's shape
  (`maxHeight * shape`), so the picture is never letterboxed by its own slot. The panel is already
  weighted, so "Wider widgets" is a minimum-width question rather than a resize. Mini camera previews and
  a short alerts column in that space are not built.
- **The Alerts tab** now asks whether it belongs: with no Frigate server configured it is hidden
  whatever the setting says, and **Cameras → Alerts tab** hides it when Frigate *is* configured. The
  colour keys follow the same list (`colorButtonTab(event, shownTabs)`), and hiding the tab you are on
  moves to Home rather than leaving an invisible selection. Verified: with the toggle off the bar read
  Home / Cameras / Assistant.

## Cameras from Home Assistant (M3.5)

A TV that may reach Home Assistant but must not be given the cameras' own addresses — no go2rtc, no
Frigate UI, no RTSP logins — can now take its pictures from Home Assistant instead.

### New file

| File | What it is |
|---|---|
| `HaCameras.kt` | `HaCamera` (entity + name, stored as `entity|name`), `haCameraStreamUrl()` (websocket `camera/stream`, format `hls`, turned into an absolute playlist URL) and `haCameraStill()` / `rememberHaCameraStill()` (REST `api/camera_proxy`, a JPEG refreshed every ten seconds). |
| `HaCamerasDialog.kt` | Which camera entities to show, in order: read from `getStates()` when it opens, OK chooses, left/right reorder — the calendar picker's shape. |

### Touched files

| File | Change |
|---|---|
| `Cameras.kt` | `Camera.haEntity`; `MainCamera`, `CameraGrid` and `FullScreenCamera` take the `HaServer` and hand it to `CameraView`; the grid draws a Home Assistant camera as `rememberHaCameraStill` (never a substream, never a red X — a still is the picture, not a failure). |
| `CameraView.kt` | A camera with `haEntity` resolves Home Assistant's playlist on `attempt()` and plays it with `HlsMediaSource` and the TV's bearer token; the RTSP path is unchanged. `media3-exoplayer-hls` added. |
| `LauncherPrefs.kt` | `cameraSource` (`direct` default, or `home_assistant`) and `haCameraEntities`. |
| `SettingsSchema.kt` | Cameras section: "Cameras from" (choice) and "Home Assistant cameras" (list → picker). |
| `MainActivity.kt` | The views take an effective list: the homemade cameras, or Home Assistant entities shaped into `Camera(id = "ha:<entity>", haEntity = …)`. |

### Deliberate designs worth keeping

- **One live picture, the rest stills**: Home Assistant's HLS costs a transcode on Home Assistant and a
  decoder on the TV, so the grid shows the chosen camera live and the others as `camera/proxy` JPEGs,
  refreshed every ten seconds. This is the "cheap path" a six-tile grid needs.
- **The token stays on the TV**: the playlist and its segments are fetched with the TV's bearer token;
  the seconds-long signed playlist URL is what Home Assistant hands out, not a camera address.
- **Measured on the Shield**: Home Assistant source, doorbell live after 2.2–9.9 s depending on the
  playlist fill (`Player 0 … live after 3003 ms`, `… 9904 ms`), grid stills fresh to the second. Direct
  RTSP stays the default because it is ~2 s and costs nothing else: the TV is back on `direct`.

## Fixed: every camera went black on 2026-11-12

Symptom: all six cameras "Camera answered, but no picture came within 12 s", while Frigate and Home
Assistant played them fine, in a retry loop, with the last picture saved on 10 Nov.

Evidence, in order:

- `ffprobe`/`ffmpeg` from another machine decoded the same RTSP URL (2560×1920 H.264, and a 4K HEVC
  camera) with no errors, so the server, the stream and the credentials were fine.
- The launcher's own camera test (CameraTest, an RTSP client of its own) connected, negotiated
  interleaved TCP (channels 0-1 and 2-3), and received both audio (64 packets, first after 14 ms) and
  video packets, with NAL types 1 5 7 8 — IDR frames present. So the TV's network and go2rtc were fine.
- ExoPlayer alone got the description and no picture, on every camera, H.264 and H.265 alike.

Cause: `sdpWithoutAudio` in `StreamDescription.kt`. When a camera's "Use audio track" was off — off on
all six — the app edited the SDP to remove the `m=audio` section before handing it to the player. The
cameras now advertise an AAC track (they did not before), so that rewrite started running on every
stream and the player then never produced a frame. Nothing in the app changed on the 10th; the cameras'
description did.

Fix: the SDP is handed over untouched, and the audio is disabled through
`TrackSelectionParameters.setTrackTypeDisabled(TRACK_TYPE_AUDIO, true)`, which is what silences it now
(the log's "audio taken out" is gone; the `Player ... started over TCP, audio: off` line still shows the
setting). Verified live: `Player ... live after 2239 ms`, picture on screen at 07:58, with the audio
track described and unused. `sdpWithoutAudio` is deleted; `sdpTracks`/`sdpSections` stay for the test
screen.

Lesson for the ledger: never edit the stream description to change behaviour. Disable tracks through
the player's own selection, whose surface is documented; every SDP field is someone else's contract.

## Removed from upstream (re-apply after a rebase)

| What | Why |
|---|---|
| The `CH` mark over the Home camera (`ChannelMark`, `channelHint`, `CHANNEL_HINT_*`, `lastChannelHint` in `Cameras.kt`) | A three-second hint that appeared when the selection landed on the camera, at most once an hour, so it looked like a glitch when it did. The behaviour it advertised stays, as `channel_keys` (on by default, Cameras section): the remote's CH up/down switches cameras from the Activity, not from the mark. |

## The rule behind these settings

Ema's rule, stated 2026-11-10 when the CH mark came up: **a user-visible behaviour is a setting, not a
hardcoded choice** — even a behaviour he dislikes stays, wrapped in a toggle or a choice with a sane
default. So a removal here is nearly always a new pref plus a schema row; the only things that should
not be configurable are the renderers themselves and the schema that describes them.

## M8 — the settings rail names the things the launcher reads, and the badge is a door (2026-11-13)

Ema's ask: "the Assist tab should be in the sidebar and not nested inside the home assistant submenu …
both frigate and home assistant should have their own group/subgroup". The Assistant tab's settings
were a tab inside the Home Assistant dialog, and the two servers shared one `Services → Integrations`
card.

### Touched files

| File | Change |
|---|---|
| `SettingsSchema.kt` | `SettingSection` is now `Launcher, Home, Assistant, Calendar, Cameras, HomeAssistant, Frigate, Advanced` — `Services` is gone. `SettingAction` became a sealed interface: `OpenAssistantTab(HaDialogTab)` carries the tab a row is about. New Assistant rows (`assistant_display`, `ha_section_columns`, `ha_bottom_row`, `home_display`, `ha_home_columns`, `ha_entities`, `ha_top_bar`, `ha_order`); `alerts_tab` moved to Frigate, `ping` to Advanced/Network; the badge left Frigate again in M9, when it stopped being Frigate's alone. |
| `LauncherPrefs.kt` | `haDashboard`, `haHomeDashboard`, `haBottomRow`, `haSectionColumns`, `haHomeColumns`: one key each, implemented over `haDisplay` so the storage stays in one place and a page row can own a switch. |
| `HomeAssistantDialog.kt` | `HaTab` → public `HaDialogTab`; `initialTab`; the bottom-row/columns switches and `BOTTOM_ROW`/`SECTION_COLUMNS` deleted; Save is `initialDisplay.copy(dashboard, homeDashboard)` so it cannot write back a stale layout. |
| `SettingsMenuDialog.kt` | `openHomeAssistant: (HaDialogTab?) -> Unit`; `of()` maps the tab-carrying action. |
| `SettingsPage.kt` | `sectionIcon` for the three new categories; door summaries for the new rows; rail padding tightened (item 9dp, column 12dp) so ten entries fit 540dp. |
| `TopDialog.kt` | On open, the remote starts on the tab that is already selected, not on the leftmost. |
| `MainActivity.kt` | `haDialogOpen` → `haDialogTab: HaDialogTab?`; `initialTab`; the dialog's Save bumps `settingsRevision`; `onSettingsChanged` and `applyImport` re-read `haDisplay`, `alertsTab`, `badge`. |
| `HomeTopBar.kt` | `NewEventsBadge` is a focusable `Surface` (white pill when the remote is on it) that calls `onNewEvents`. |

### Deliberate designs worth keeping

- **A door carries where it lands**: `SettingAction.OpenAssistantTab(HaDialogTab.Sensors)` opens the
  dialog it always did, on the tab the row promised. Splitting the 900-line dialog into four would
  duplicate the login, the session and `EntityPicker`; the door gets the Assistant category into the
  rail at a fraction of that risk. The pickers, the order and the dashboard choice need live
  `getStates()`/`dashboards()`, a search field and per-entity press semantics — dialog material.
- **A server has its own category**: Home Assistant holds the connection only; what its tab shows is
  Assistant; Frigate holds the alerts tab and the badge. One card of "Integrations" made the pairing
  invisible.
- **One editor per setting**: the dialog's Dashboard tabs used to render the bottom-row and columns
  switches as well as the page, and the dialog wrote its `initialDisplay` snapshot back on Save — a
  page toggle made before opening it was silently reverted. The switches live on the page only, and
  Save now rewrites just the two dashboards.
- **Ten rail entries still fit**: item 38dp (9dp padding + a 20dp title), 24dp column padding, 36dp
  header, 11 gaps of 2dp → 462dp of 540dp, so About and Close stay pinned at the bottom with the
  weight spacer. A scrolling rail was rejected: focus into an off-screen item loses the cursor, which
  is the bug fixed in M6.
- **The badge leads to the alerts it counts.** The Shield's notification panel is
  `com.google.android.tvlauncher/.notifications.NotificationsSidePanelActivity`: not exported, and the
  implicit `com.android.tvlauncher.NOTIFICATIONS` / `com.android.tv.NOTIFICATIONS_PANEL` intents do not
  resolve even from the shell UID. The TV has no status bar either (`dumpsys statusbar` reports no
  icons, `cmd statusbar expand-notifications` changes nothing), so `StatusBarManager` would be a no-op.
  No app on this device can open Android's own notifications. M9 adds the other way out — the launcher
  draws the panel itself from a listener — and until it is switched on, OK on the count opens the
  Alerts tab; when the count clears, focus moves to the Alerts tab button because the badge disappears.

### Verified on the Shield (2026-11-13)

- Rail shows Launcher, Home, Assistant, Calendar, Cameras, Home Assistant, Frigate, Advanced, About,
  Close — all on screen, so nothing had to scroll.
- Assistant renders "What the tab shows · My selection", "Beside the camera · My selection",
  "Sensors, scenes and scripts · 5 chosen", "Actions in the top bar · 3 of 3".
- OK on "Sensors, scenes and scripts" opens the dialog on the Sensors tab with the remote on that tab
  (`TopDialog` change); the dashboard door opens on HA Tab with only dashboards listed.
- "Alerts and apps below" toggled on the page appears on the Assistant tab with no restart; Save in
  the dialog leaves it on (`ha_bottom_row=true` in `shared_prefs/launcher.xml` afterwards). Both were
  put back: `ha_bottom_row=false`, `badge=true`, `alerts_tab=true`.
- Search "badge" finds one row, labelled **Frigate**.
- Badge: focusable, white when focused, OK opens the Alerts tab and the count clears.

Not verified on the device: an export/import round-trip of the three new keys (they are `Toggle` rows,
so `currentPreferences`/`applyPreferences` carry them by construction), and the dialog's login step
when Home Assistant has no saved login.

## M9 — the count can be the TV's notifications, and its panel (built, optional)

The badge counted Frigate alerts only, and Android's own notification panel cannot be opened by another
app on this box (M8). So the launcher reads the notifications itself and draws the panel: a
`NotificationListenerService`, off until notification access is granted and until the count is pointed
at it.

### New files

| File | What it is |
|---|---|
| `Notifications.kt` | `LauncherNotification` (app, title, text, time, ongoing), the `Notifications` store the UI reads (`entries`, `granted`, `open`, `clearAll`, `requestRebind`), and `LauncherNotificationListener`, which only reports: posted, removed, connected. A group summary is skipped; a notification with no title shows its app alone, not its package name. |
| `NotificationsDialog.kt` | The panel: every live notification with its app, title, two lines of text and "5 min. ago"; OK opens what it came from and closes; "Clear all" dismisses the dismissible ones. With access off it says how to grant it, since the Shield's Settings has no screen for it. |

### Touched files

| File | Change |
|---|---|
| `AndroidManifest.xml` | `.LauncherNotificationListener`, exported, `BIND_NOTIFICATION_LISTENER_SERVICE`. |
| `LauncherPrefs.kt` | `badgeSource` (`frigate` default, or `notifications`), keys `BADGE_SOURCE_*`. |
| `SettingsSchema.kt` | The badge moved from Frigate to **Advanced → Top bar** (it is a top-bar indicator, and it no longer counts only Frigate), joined by `badge_source` ("The count is") and `notification_access` (a door to the panel, shown only for the notification source). **Fixed: all five `SettingDef` builders accepted `showIf` and dropped it**, so every `showIf` row in the schema was always visible — including the background rows FORK.md said were hidden. |
| `SettingsMenuDialog.kt` | `openNotificationAccess`. |
| `SettingsPage.kt` | `notification_access` says "Allowed" or "Not allowed". |
| `HomeBoardApp.kt` | `instance`, for the label lookup and the "is it allowed" check. |
| `MainActivity.kt` | `badgeSource` and `notificationsOpen` state; the count is the notification list's size when that is the source; OK opens the panel or the Alerts tab accordingly; the Frigate poll is skipped unless Frigate is what the count counts; both re-read lists carry `badgeSource`. |

### Deliberate designs worth keeping

- **The listener reports, the UI reads**: `Notifications.entries` is Compose state, so the panel and the
  badge do not care whether the service is alive; a rebind is asked for when the panel opens, which is
  what makes a grant made while the app was running take effect without a restart.
- **No invented action**: a notification with no `contentIntent` (a foreground service's, most of them)
  does nothing and logs why. Rows are not dimmed, because whether they open something is not knowable
  before pressing.
- **Granting is a shell command on this TV**: `com.android.tv.settings` has no notification-access
  activity, so the panel prints `adb shell cmd notification allow_listener
  com.evofactory.homeboard/.LauncherNotificationListener`. Android binds the listener at once.
- **Off by default**: `badge_source` is `frigate`, so a fresh install behaves exactly as before.

### Verified on the Shield (2026-11-13)

- `cmd notification allow_listener` bound the service (`enabled_notification_listeners` lists it).
- With the count on **Android notifications**, the badge showed **10** (the live notifications), and OK
  on it opened the panel listing them: QuickBars, SHIELD Experience Upgrade, Spotify, tvQuickActions ×2,
  AirReceiver, SMB Server, Dolby, with app names and "2 days ago" / "2 hours ago".
- OK on AirReceiver's entry launched `com.softmedia.receiver/...MainTvActivity`, so `contentIntent.send()`
  works; OK on QuickBars' entry did nothing and logged "dev.trooped.tvquickbars has no content intent".
- Back to **Frigate events**: the badge disappeared at 0 new alerts and the notifications panel stopped
  opening from it (a stale process made one earlier press ambiguous; a clean restart settled it).
- The `showIf` fix shows: with the Frigate source the "Notification access" row is hidden, with the
  notification source it appears saying "Allowed"; the background colour/picture rows follow the same rule.

Left as it was: `badge_source=frigate`, `badge=true`, `alerts_tab=true`. Notification access is
**granted** for HomeBoard on this Shield (revoke with `adb shell cmd notification disallow_listener
com.evofactory.homeboard/.LauncherNotificationListener`).

## M10 — the dialogs tell the truth, the tabs fit, and the alerts have a dot (2026-11-13)

Ema's list: the Save button read as disabled, the dashboard list offered the same choice twice, the
dashboard's view tabs spilled over the clock, the notification count should be a dot on the Alerts tab,
and the settings needed a coherence pass.

### What was wrong, and what changed

| Symptom | Cause | Fix |
|---|---|---|
| Save looked disabled | `primary` was the library default, which is dim next to the outlined buttons; and the button never said whether there was anything to save | `DialogButton.highlight`: a primary button is filled in `SwitchOn` (blue) **only** when there is a change, and outlined otherwise (identical to Log out when there is nothing to do). `dirty` in `HomeAssistantDialog` compares the normalised address, the login, the chosen entities, the top bar and both dashboards |
| Option 2 of the dashboards saved as option 1 | Home Assistant lists its default dashboard twice — as the list's `<default>` and again under `lovelace`, the path its own URL uses. Worse, "the default dashboard" and "my selection" were both stored as an empty value | `HA_DEFAULT_DASHBOARD_PATH` + `canonicalDashboardKey`; `dashboards()` skips the alias; `ha_dashboard` absent is "my selection", `""` is Home Assistant's own dashboard, and the dialog's `dirty` compares them as nullable |
| A tab title wrapped over the clock | the view-tab row was a plain `Row`: no scroll, no width cap, and the text wrapped to two lines past the screen's edge | `LazyRow`, each tab capped at 240dp, `TabButton`'s text one line with an ellipsis. Verified: walking right scrolls the row, "Bedroom & En-Suite" lands fully on screen |
| The count offered "Frigate events / Android notifications" | a second count source, which is not what a TV wants: a dot says enough | The choice is gone. `alerts_dot` (on by default) puts a dot on the Alerts tab while there are alerts nobody has looked at; **Frigate → Alerts → "Mark the alerts as reviewed"** clears the dot and the count without opening the tab. The notifications panel stays, as Advanced → Notifications |

### Coherence pass (from an independent review of the whole settings surface)

- **Search results could not be reached**: Down from the field pointed at an uninitialised `FocusRequester`, and the hit rows carried two requesters on one node. `SearchResults` now takes the page's `first`, attaches it to the first hit only, and Down from the field lands on it (verified with a focus dump).
- **Three camera rows changed nothing on screen** until a restart: `cameraSource`, `cameraSide` and `homeCameraStart` were missing from `onSettingsChanged` (and `cameraSide` from `applyImport`). Re-reading was only half of it for "Camera on Home": the override used to run at start only, so `applyHomeCameraStart()` now runs at start, after a settings write and after an import, and it settles both values ("First in the list" selects the first camera, "Last switched to" the saved one).
- **Indicators that could never show**: "New events badge" is hidden while the Alerts tab is off, and "Mark the alerts as reviewed" only shows when a dot or the count is on.
- **The notifications row lost its status**: `rowSummary` still matched the old key `notification_access`; the row is `notifications` and says "Allowed" or "Not allowed" again.
- **Dead weight removed**: `SettingAction.OpenAccessibilitySettings` (no row used it), its `SettingsActions` hook, `SettingsPage`'s unused `onOpenAccessibility` on `SettingPageRow`, and `SettingKind.Info` (no Info row exists, so the kind, its render branch and its `read` fall-through were unreachable).
- **The dashboard choices now travel in an export**: they are doors, not rows, so `currentPreferences`/`applyPreferences` carry them under `ha_dashboard_choice` / `ha_home_dashboard_choice`. A `null` dashboard is left out, since an absent key means it.
- **Down stopped dead at "Favourite apps"**: the "last row of the page" pin (which stops the remote leaving the list) compared against the schema's last row, but cards are drawn in the order their first row appears and `favourite_apps` is defined last while it renders in the middle card. Its Down was cancelled, and the two cards below it were unreachable from above. The pin now uses the last row of the last *group*, i.e. what is at the bottom of the page.
- **`showIf` itself was dead** (found in M9): all five `SettingDef` builders accepted it and dropped it. With it fixed, the background rows and the new ones follow their conditions — verified: with the colour background only Colour and Darkness show.

### Verified on the Shield (2026-11-13)

- Save: outlined with nothing to do, blue `#5A8FD9` once a dashboard is chosen, and the focused fill is the dark variant (`13,26,44` sampled). Sampling `Log out` and `Save` in the unchanged state gave the same fill, so the two read as the same weight while nothing is pending.
- Dashboard list: one "Overview" and "Config" (`Dashboards: <default>|Overview, lovelace-config|Config`). Choosing the default and saving writes `ha_dashboard=""`; choosing "My selection" and saving removes the key.
- View tabs: single line, ellipsis, and the row scrolls to the focused tab.
- Dot: with `badge_seen_at` moved two days back, the dot appeared on the Alerts tab (and the badge said 50); "Mark the alerts as reviewed" cleared both and wrote `badge_seen_at` to now.
- Search: "badge" finds Advanced → New events badge, and Down from the field focuses the hit.
- Home page, Down: "Favourite apps" → "Sensors card" (focus dump moved from `[552,462–562]` to `[552,630–734]`), while five Downs on "Scroll direction", the page's real bottom row, leave the focus there rather than losing it.
- "Camera on Home": changed from "Last switched to" to "First in the list" with the launcher open; the Home picture became the doorbell and the log showed a new `Player … started over TCP, …, <camera-host>:8554/doorbell` seconds later, no restart. The preferred value was then put back to `first`.

Left as it was: `badge=true`, `alerts_tab=true`, `alerts_dot` unset (on), `ha_bottom_row=false`, `ha_home_columns=true`, `ha_section_columns=false`, `ha_dashboard` absent (My selection).

## M11 — no more "Home Tab" and "HA Tab": one part at a time, named like its row (2026-11-13)

Ema: "I don't understand what is 'Home Tab' and 'HA Tab' ... feels like this submenu could be a section
of the assistant settings". Right on both counts. The dialog's five tabs were named for the code, and a
row opened the dialog *on* one of them while the other four sat there to be guessed at.

### What changed

- **The Home Assistant dialog has no tabs.** Each settings row opens one part, and the title says
  which: `Home Assistant · Assistant tab`, `Home Assistant · Devices`, `Home Assistant · Top bar`,
  `Home Assistant · Order`, `Home Assistant · Home tab`. Log out, Save and the X stay in the top row;
  the remote starts on the part's first item (`TopDialog.openFocus`, asked for over two frames).
- **The parts are named for their rows**: `HaDialogTab` labels went from `Home Tab` / `HA Tab` /
  `Sensors` / `Actions` / `Order` to `Assistant tab` / `Home tab` / `Devices` / `Top bar` / `Order`,
  and every description that said "the other tabs" now says "Devices" or "the Order part".
- **The Assistant section carries the layout choices, in the same words as the dialog** ("What the
  Assistant tab shows", "What the Home tab shows"), so the row and the dialog title cannot disagree.

### The same class of confusion elsewhere, fixed in the same pass

Renaming only; every key is unchanged, so an export file still reads.

| Was | Now | Why |
|---|---|---|
| Home → Shuffle → "Camera on Home" | "Different camera each session" | two rows in the app were called "Camera on Home" (this one and Cameras → Home tab) |
| Home → Shuffle → "Again after" | "Reshuffle after" | "after what" |
| Home → Rows → "Frigate events" | "Alert strip" | it toggles the strip on Home, not Frigate |
| Home → All apps page → "Lines" | "Per line" | "lines" of what |
| Cameras → "Cameras" (row in the "Cameras" card) | "Camera list" | three "Cameras" in a row |
| Cameras → Source → "Pictures come from" | "Where the pictures come from" | a label doing a sentence's job |
| Home → *Shuffle* (the group) | *Burn-in protection* | "shuffle" says what the code does, not why anyone would want it |
| Home → Burn-in protection → "Shuffle the camera" | "Change the camera each session" | same |
| Home → Burn-in protection → "Shuffle the top bar" | "Change the action buttons' order" | same |
| Home → Burn-in protection → "Shuffle the Assistant cards" | "Change the room cards' order" | same |
| Home → Burn-in protection → "New order after" | "A new order after" | a stepper whose title was a fragment |
| Cameras → Keys and sound → "Sound" | "Camera sound" | there is a microphone in the house |
| Calendar → "Calendars" (row in the "Calendars" card) | "Which calendars" | one word for the card and the list it edits |
| Calendar → Display → "Order" | "Sort by" | "order" is also an Assistant part |
| Assistant → "Sensors, scenes and scripts" | "Which devices" | matches the dialog's Devices part |
| Assistant → "Actions in the top bar" | "Also in the top bar" | says what it does to the chosen devices |
| Advanced → "The TV's notifications" | "Notifications panel" | the row opens a panel |

Also: rows in `AllSettings` are ordered as they appear on screen (`favourite_apps` and
`show_free_memory` sat at the end of their sections while drawing in the middle cards — which is what
made the Down pin land mid-page, see M10).

### Verified on the Shield (2026-11-13)

- Assistant section reads: Assistant tab — What the Assistant tab shows · Rooms side by side · Alerts
  and apps below; Home tab — What the Home tab shows · Rooms side by side; Devices and actions — Which
  devices · Also in the top bar · Order.
- "What the Assistant tab shows" opens `Home Assistant · Assistant tab` with no tab bar, focus on "My
  selection", and the same description the row promised.
- "Which devices" opens `Home Assistant · Devices`: search field on top, focus on the first device
  (Weather, ticked), Log out / Save / X above.
- Home section: Shuffle and Rows cards read with the new names; every switch still shows its stored
  value (`shuffle_*` false, as Ema has them).

## M12 — each tab its own devices, one place per setting, and the truth about "wider widgets" (2026-11-13)

Ema's list, after M11: split the entity choice between the tabs, choose a sensible Assistant set for him,
make the calendar pickers usable, get the screensaver out of the cameras, stop hiding Home-tab settings
inside Cameras, use Frigate's mark, and stop the Advanced page shouting.

### Choices

- **Two entity lists.** `ha_entities` is what the Assistant tab shows; `ha_home_entities` is what sits beside
  the camera. The dialog gained the part **Devices beside the camera** (a picker over the same devices, and
  picking one there adds it to the tab's list if it wasn't). Absent, the new list falls back to what the Home
  panel used to show (the tab's list without scenes and scripts), so an older setup is unchanged.
  `saveHaChoice(entities, homeEntities, topBar)` writes all three; the export carries
  `ha_home_entities` (a door has no value of its own to read).
- **Ema's Assistant tab, chosen from the live states**: living room readings (TRV temperature and humidity,
  PM2.5 and air quality, presence illumination, presence group), `light.living_room_lights`, and the scripts he
  actually uses (Good Night, Good Morning, Start Plex, Start Plezy, Start Moonlight, Find my remote, Show CCTV),
  beside the perimeter, bedroom temperature, weather and alarm that were already there. The Home panel's own
  five: the three living-room readings, presence and the lights.
- **Calendar pickers say what OK does.** `CalendarPickerDialog`: OK opens a menu — *Include it / Leave it out*,
  *Move up / Move down* with the priority each would give. `CalendarColoursDialog`: OK opens the palette by name
  with the current colour ticked. Left and right still cycle, but nothing depends on discovering that.
- **The screensaver left the camera dialog.** It is the launcher's, so it has its own `ScreensaverDialog` (time
  per camera, which cameras, progress, a button to the TV's screensaver screen) and a row in Launcher,
  *How it plays*. `CamerasTab` lost the tab, and with it the duplicated state.
- **"Camera on Home" and "Space beside the camera" moved to Home**, under *Camera beside the cards*, where the
  tab they configure lives.
- **"Wider widgets" did nothing at all**: `CAMERA_SIDE_WIDER` was never read in the layout, and the panel always
  took the rest of the width, so it and "Leave the gap" were the same picture. The row now decides: *Leave the
  gap* keeps the panel at `SidePanelWidth` with the space empty, *Centre the picture* keeps that width and
  centres the picture in its own weighted box, *Wider widgets* gives the panel the rest.
- **Frigate's own mark** in the rail: `res/drawable/frigate_logo.xml`, the single-path bird from the project's
  branding SVG, drawn instead of the generic shield.
- **Advanced stops shouting.** The home helper is one line (dot, what it is, its two buttons) with the long
  explanation only when it is asked for and not running; the version and build no longer repeat what the About
  page says; the notifications row says **Allowed** or **Not allowed** beside it.
- **The connection row opens the connection.** *Home Assistant → Address and login* passes `connectionFirst`,
  so the dialog shows the address and login rather than whatever part it happened to default to.

### Incident, recorded because it cost the TV its settings for a few minutes

Writing prefs from the host while the launcher is the home app is a race: Android restarts it the moment it is
force-stopped, and the restart reads the file before the write lands. One write went through a 0-byte temp file
and left the prefs with 4 of 65 keys. They were restored from the copy taken before the seed (65 keys, the same
values Ema had at 01:20), the app restarted onto it, and the Home tab came back with its camera, calendar,
favourites and Alerts tab. Nothing of his was rewritten by hand; the only values that changed are the two entity
lists above.

### Verified on the Shield (2026-11-13, 01:20–01:50)

- Assistant tab draws the new set by room: Living Room (19 °C, 49 %, PM2.5, air quality, illumination, presence,
  lights with a switch) and Scripts (Find my remote, Good Night, Good Morning, Start Plex, Start Plezy).
- Rail: Frigate carries its own mark; Launcher → *How it plays*; Advanced → *Notifications panel · Allowed*.
- Home, `camera_side=left`: camera at 4:3 on the left, the gap, then the calendar panel at its own width.
- Not yet seen on the device: the calendar menus, the screensaver dialog, the connection-first door, and
  `camera_side=wider` (the change is in, the picture was not taken before the prefs incident).

## M13 — the entities he actually wants, named on screen, and a connection that isn't a submenu (2026-11-13)

### The selection, changed as asked

`ha_entities` (the Assistant tab) lost the illumination, PM2.5, *Start Plex*, the all-lights group and the
presence binary sensor, and gained the three living-room lights, the air purifier and both robots. It is now:
Perimeter, living-room Temperature, Humidity and Air quality, Main light, Stand, LED strip, Air purifier,
Yiko and Baka, Outside (weather), Bedroom temperature, Alarm, and the scripts Show CCTV, Find my remote,
Good night, Good morning, Start Plezy, Start Moonlight. `ha_home_entities` (beside the camera) is Temperature,
Humidity, Main light, Stand and Air purifier.

Note for the next person: the stand is **`light.living_room_stand`**. `light.living_room_lights_stand` does not
exist — a first pass used it because the states dump has `…lights_stand_big`, `…lights_stand_small` and `…stand`
side by side, and the row rendered the raw id until it was corrected.

### Names on screen, without touching an id

- `ha_entity_names` holds "entity_id=Label" lines, and `EntityNames` applies them inside `HaEntity.name`, so
  every surface that draws a name (cards, rows, the top bar, the picker) uses them with one change. Home
  Assistant is never called, and no entity id is ever written.
- Seeded for the chosen entities: Temperature, Humidity, Air quality, Main light, Stand, LED strip, Air purifier,
  Yiko, Baka, Perimeter, Outside, Bedroom, Alarm, Show CCTV, Find my remote, Good night, Good morning,
  Start Plezy, Start Moonlight.
- Editable on the TV: Assistant → Devices and actions → **Names on screen** (`EntityNamesDialog`, a list twice
  the height of the tape and a text field per row; blank clears the label).

### A vacuum is not a light

`vacuum` is pickable now, and `VacuumRow` shows what the robot is doing with OK opening the services that apply
(Start, Pause, Return to dock — a docked robot isn't offered a dock). `HaConnection.vacuum` sends them. Cards
whose entities have no room no longer title themselves after the domain: `alarm_control_panel` reads **Alarm**,
`vacuum` **Robots**, `fan` **Fans**, `light` **Lights**.

### The Home Assistant section is a connection, not a door

Three rows where there was one: **Address** (its own text dialog, `HaAddressDialog`; saving a new address ends
the login that belonged to the old one), **Log in** (the same dialog, opening on its connection step) and
**Log out**. The five-part editor is only reached from the rows that are about one of its parts.

### Written to the TV with checks, after M12's incident

Every prefs write this time: read the file, assert it is over 14 000 bytes, build the edit, assert the file
grew and still holds `ha_entities`, `ha_home_entities`, `favourite_apps`, `cameras` and `ha_url`, copy, then
read it back and assert the key count. The verify of one pass caught nothing, which is the point.

### Verified on the Shield (2026-11-13, 02:43–02:50)

- Assistant tab: Sensors → Perimeter · All closed; weather; Cave → Start Moonlight; Living Room → Temperature
  18.9 °C, Humidity 49 %, Air quality excellent, Main light, **Stand 14 %**, **LED strip 80 %**, Air purifier,
  **Yiko Docked**, Show CCTV; Bedroom → Baka Docked, Bedroom 18.1 °C; Alarm · armed home; Scripts → Find my
  remote, Good night, Good morning, Start Plezy. Every label is the launcher's own.
- Card titles: "Alarm", "Lights" — no domain names on screen.
- Not seen on the device yet: the three connection rows, *Names on screen*, and the vacuum menu (the D-pad
  run landed on Network status instead, and it was 02:50).

## M14 — the two device lists are two lists, and every door opens where it says (2026-11-13)

### Fixed, in his words

- **Shuffle speaks plainly**: "Shuffle the camera" (a different camera on the Home tab each session),
  "Shuffle the top bar", "Swap the camera and widgets", "Shuffle the Assistant cards", "New order after N
  hours, 0 rolls once per session". The rows say what changes and when; "resession", "again after" and
  "actions" are gone.
- **Log in opened the wrong screen.** The action mapped a null tab to `Home`, so the row landed on the Home
  tab's layout choice. There is now a `connectionFirst` flag and a dedicated `OpenHaLogin`, and the dialog
  opens on the address and login (screenshot: address, "Log in with Username and password / Access token",
  Connect).
- **The two device lists are finally two lists.** The dialog's parts are `Assistant tab · what it shows`,
  `Assistant tab · devices`, `Home tab · what it shows`, `Home tab · devices`, `Top bar`, `Order`, and the
  settings rows use the same words under the two cards *Assistant tab* and *Home tab*. The Home list is
  **no longer a subset** of the tab's: picking there changes only that list, a device can sit beside the
  camera without being on the tab, and the Home part carries a **Same as the Assistant tab** button that
  copies the tab's list over (the hint then says they match). Save writes the two lists separately.
- **The scripts that only work on this TV are out**: Show CCTV, Find my remote, Start Plezy and Start
  Moonlight left the Assistant tab and the top bar — the launcher already has the cameras, and it is the
  screen those apps would start on. Good night and Good morning stay; they act on the house, not the TV.
- **Log out asks first**: a confirmation naming what happens ("The TV's login is ended in Home Assistant's
  own profile"), Cancel in front, Log out in the accent.
- **The home helper is a row**: Launcher → Home button → *Home helper*, with its state beside it
  ("Not needed: this is the TV's home app"), opening a dialog with the explanation and only the buttons that
  apply. The four-line block at the bottom of Advanced is gone, and with it the special-cased composable.
- **The Notifications section is a section**: *Access · Allowed* and *Notifications panel · 8 showing*, both
  stating their state before you press them.

### Verified with screenshots (the launcher's own rendering, not a claim)

- Assistant tab: Perimeter, weather, Living Room (Temperature, Humidity, Air quality, Main light, Stand,
  LED strip, Air purifier, Yiko), Bedroom (Baka, Bedroom), Alarm, Scripts (Good night, Good morning) — no
  CCTV or app-launching buttons.
- Settings → Assistant: *Assistant tab* card = What it shows · Rooms side by side · Alerts and apps below ·
  Devices on it (13 chosen); *Home tab* card = What it shows · Rooms side by side · Devices beside the
  camera (5 beside the camera).
- *Home tab · devices* opens as its own dialog: search, every device with its label, the **Same as the
  Assistant tab** button, and the hint "OK selects · 5 beside the camera · the first button copies the
  Assistant tab's list".
- *Log in* opens the connection dialog; *Log out* opens the confirmation (cancelled, not confirmed).
- Launcher → *Home helper · Not needed: this is the TV's home app* opens the helper dialog.
- Advanced → Notifications: Access "Allowed", panel "8 showing".

## M15 — Frigate's mark stays Frigate's mark (2026-11-13)

The rail icon is Frigate's own path from `docs/static/img/branding/logo.svg` (Apache-2.0), and it is the
bird: **no badge**. Two things were tried on it and one was rejected:

1. A badge behind it — a disc in the launcher's blue with the bird knocked out. Legible, and wrong: it turned
   a product's mark into an app tile. Ema: "you actually had the right icon before". Reverted.
2. What stayed: the path is **filled and lightly stroked** (`strokeWidth` 18 of the 512 viewport, round
   joins), which keeps the fine silhouette from thinning out at rail size, and the rail draws it at **22dp**
   instead of 20, tinted like every other icon (`onSurface`, `inverseOnSurface` when the row is focused).

Verified in both rail states: on the dark rail the bird holds its shape, and on the focused light row the
dark tint reads as clearly as the Home or Cameras glyphs beside it.

## C1 — dead code and one colour pair (2026-11-13)

Audit findings #3, #4 and #10, plus two items the UX review found (H9). Zero behaviour change: nothing a
person can see or press changed.

### Touched files

| File | Change |
|---|---|
| `SettingsMenuDialog.kt` | `HelperStatusRow` and `BuildInfo` deleted (the home helper became a row in M14 and the About page prints the same version), with the `OpenHomeAssistant` case and the 32 imports only they used |
| `SettingsSchema.kt` | The never-emitted `SettingAction.OpenHomeAssistant` deleted |
| `SettingsPage.kt` | The unused `onOpenAccessibility` parameter (its only consumer was `HelperStatusRow`) and the dead `"home_assistant"` `rowSummary` branch — no row has had that key since M13 split it into Address / Log in / Log out |
| `MainActivity.kt` | One line: that argument at the `SettingsPage` call |
| `Theme.kt` | `MarkOn` / `MarkOff`, the muted pair |
| `CalendarPickerDialog.kt`, `HaCamerasDialog.kt`, `FavouritesEditorDialog.kt`, `ExportImport.kt`, `LauncherSettingsDialog.kt`, `SettingsMenuDialog.kt` | Six local colour pairs (`0xFF5A595F` / `0xFF8E8C92`) deleted; every use reads `MarkOn` / `MarkOff` |
| `BackgroundPickers.kt` | The write-only `var focused` and its `onFocusChanged`, with the four imports it orphaned |
| `HaCamerasDialog.kt` | Three copy-paste doc comments that said "calendars" in the camera picker |

### Deliberate designs worth keeping

- **One pair, named for what it marks**: a tick box's fill, a quiet switch's track and an idle dot are the
  same two values by intent, so they are one `MarkOn`/`MarkOff` in `Theme.kt` beside `SwitchOn`.
- **`grep` is the acceptance test for a deletion item**: `HelperStatusRow`, `BuildInfo`,
  `OpenHomeAssistant`, and each of the six colour names return nothing in the tree.

## C2 — one URL normaliser (2026-11-13)

Audit finding #6: `normalizeHaUrl` and `normalizeFrigateUrl` were the same function with two defaults.

### New files

| File | What it is |
|---|---|
| `Urls.kt` | `normalizeUrl(input, defaultScheme, defaultPort)`: the scheme is assumed when the text names none, the port when it names none, and anything after the server (a copied path, query or fragment) is dropped |

### Touched files

| File | Change |
|---|---|
| `HomeAssistant.kt` | `normalizeHaUrl` is `normalizeUrl(input, "http", 8123)` |
| `Frigate.kt` | `normalizeFrigateUrl` is `normalizeUrl(input, "https", FRIGATE_PORT)` |

### Deliberate designs worth keeping

- **The public names stayed.** Callers are unchanged (two address dialogs, the Frigate dialog, the login
  step), and no storage key moved: this is one implementation behind two names, not a rename.

### Verified on the Shield (2026-11-13, 09:3x)

Typed into Home Assistant → Address, reading the dialog's own "Will be saved as" line, which is the
normaliser's answer:

- `192.168.1.20` → **http://192.168.1.20:8123**
- `homeassistant.local:8123` → **http://homeassistant.local:8123**
- `http://<ha-host>:8123/lovelace/0` → **http://<ha-host>:8123**

Nothing was saved: the address is still `http://<ha-host>:8123`.

## C3 — one colour picker (2026-11-13)

The audit's findings #1 and #7: the background's palette dialog and the calendar's colour dialog were the
same dialog twice, over two palette definitions of the same shape.

### New files

| File | What it is |
|---|---|
| `ColourPickerDialog.kt` | `ColourEntry(name, argb)` (+ `hex()` for the callers that store hex), and `ColourPickerDialog(title, subtitle, palette, current, onPick, onDismiss)`: a dot, its name, and "In use" on the current one, in a scrolling list with a Done button |

### Touched files

| File | Change |
|---|---|
| `BackgroundPickers.kt` | Deleted: it held nothing but `BackgroundColourDialog`, which the shared picker replaces |
| `LauncherBackground.kt` | `BackgroundColours` is a `List<ColourEntry>`: same 12 colours, same order |
| `HaCalendar.kt` | `CalendarTint` → `ColourEntry` for the same 8 tints; `colourOf` moved here from the calendar dialog, beside the palette it reads |
| `CalendarColoursDialog.kt` | Reads the palette as `ColourEntry` (`entry.hex().equals(hex, true)`); behaviour unchanged — per-calendar rows, left/right cycling, the OK menu naming each colour with the current one ticked |
| `CalendarWidget.kt` | One line: `?.hex` → `?.hex()` |
| `MainActivity.kt` | The background colour row opens the shared picker with its own title and subtitle |

### Deliberate designs worth keeping

- **Storage did not move.** The calendar still stores `calendar_colours` as `id=#RRGGBB` lines and the
  background still stores `background_colour` as an Int, so an export file and every existing pref read
  exactly as before; `ColourEntry.hex()` is the only conversion, at the boundary.
- **The palette is data, the dialog is one renderer.** A future palette (a screensaver tint, an entity
  colour) is a `List<ColourEntry>` and a call, not another dialog.

### Verified on the Shield (2026-11-13, 09:5x)

- Launcher → Background → **Colour** opened "Background colour" with its subtitle, Done and the twelve
  entries; the current one read **In use** (Black at the start).
- Picking **Charcoal** closed the dialog and wrote `background_colour=-14935265` (`0xFF1C1B1F`);
  reopening showed **In use** on Charcoal. Picking **Teal** wrote `-15848918` (`0xFF0E2A2A`).
- Puts back: **Black** (`-16777216`), the value Ema had.

## C4 — one picker, one interaction model (2026-11-13)

Audit findings #2 and #8, plus W9/F11. Three pickers (calendars, Home Assistant cameras, favourite apps)
were the same dialog three times, and only the calendar's OK said what it did.

### New files

| File | What it is |
|---|---|
| `EntityPickerDialog.kt` | `PickerRow(id, label, detail)` and one picker: the list in the caller's order, a tick per chosen row, `positionLabel` on a chosen row ("Priority 3 of 2"), and OK opening the menu — `includeLabel`, `excludeLabel`, `Move up (…)` / `Move down (…)`. Left and right still move a chosen row. The subtitle is computed: 0 needs the caller's `emptyText`, 1 reads **"One <noun>. OK chooses more."**, more uses the caller's own sentence |

### Touched files

| File | Change |
|---|---|
| `CalendarPickerDialog.kt` | 177 → 65 lines: its Home Assistant read, its priority wording and its writes, around the shared picker |
| `HaCamerasDialog.kt` | 160 → 71: the same, and it **gains the move menu** — it used to toggle on OK with no way to reorder except discovery of left/right |
| `FavouritesEditorDialog.kt` | 121 → 50: pinned-first ordering kept, `Pin it` / `Unpin it` as the menu's verbs |

### Deliberate designs worth keeping

- **OK says what it does, everywhere.** Only the calendar picker had the Include / Leave out / Move menu
  (M12). All three have it now, so no picker hides its only action behind a key nobody was told about.
- **The rows never move under the remote.** Each picker renders the caller's order — the server's
  alphabetical order for calendars and cameras, pinned-then-the-rest for apps — and a move rewrites the
  stored order instead. Verified: moving Birthdays up one left it in the same place in the list, with
  "Priority 9 of 10" instead of "10 of 10"; moving it back restored the stored list byte for byte.
- **The subtitle cannot promise what the list cannot do** (F11): one chosen row reads "One camera. OK
  chooses more.", with the reorder sentence only for two or more.

### Verified on the Shield (2026-11-13, 10:0x–10:2x)

- **Calendars**: "10 calendars, highest priority first", rows alphabetical, chosen ones numbered. OK on
  Birthdays offered *Move up (Priority 9)* / *Leave it out*; the move applied with the row still in
  place, and moving it back returned `calendar_entities` to Ema's order exactly.
- **Home Assistant cameras**: the menu now offers *Move down (Position 2)* / *Leave it out* on a chosen
  camera and *Include it* on the rest. Leaving Doorbell out made the subtitle read **"One camera. OK
  chooses more."**; including it again and moving it up restored `ha_camera_entities` exactly.
- **Favourite apps**: pinned first with "Position 1 of 4", OK offered *Move down (Position 2)* / *Unpin
  it*; right moved Plezy to slot 2 and left put it back, `favourite_apps` byte-identical to the start.

## C5 — one toggle row, one switch mark (2026-11-13)

Audit finding #5: `HomeOption` (ListItem + the library `Switch`), `CameraDialog`'s private `SwitchToggle`
(Surface + a drawn mark) and the mark itself living in `HomeAssistantTab` were three answers to one row.

### New files

| File | What it is |
|---|---|
| `Switches.kt` | `SwitchMark(on, color)` — moved verbatim from `HomeAssistantTab` — and `SwitchRow(title, on, onToggle, modifier, description = null)`: the camera dialog's compact row (transparent surface, `onSurface`/`inverseOnSurface` when focused) with an optional second line under the title |

### Touched files

| File | Change |
|---|---|
| `LauncherSettingsDialog.kt` | `HomeOption` deleted with its library `Switch`; nothing of it is left in the file |
| `CameraDialog.kt` | Its private `SwitchToggle` deleted; four call sites use `SwitchRow`, `StreamTitle` untouched so the two pairs still sit beside their stream's title |
| `HomeAssistantTab.kt` | The local `SwitchMark` deleted; `ToggleRow` uses the shared one |
| `Cameras.kt`, `Screensaver.kt` | The three `HomeOption` rows are `SwitchRow` and pass `fillMaxWidth()` themselves, since the row no longer fills on its own |

### Deliberate designs worth keeping

- **The row doesn't decide its own width, and the gap stays outside it.** `SwitchRow` renders at the
  caller's modifier: full width where it is a settings row, compact where two sit side by side in the
  camera dialog (that layout is unchanged). Callers pass `padding(...).fillMaxWidth()`, not the reverse, so
  the focused background covers the row and not the 8dp gap above it (found in review).
- **One mark, one meaning**: filled blue with the knob right when on, an outline with the knob left when
  off, drawn in the row's own content colour so it stays legible on the light focused row.

### Verified on the Shield (2026-11-13, 10:3x)

- Cameras → **Camera list → Advanced**: both rows draw the shared mark ("Show substreams on the Cameras
  tab" on, "Minimize simultaneous video streams" off); OK on each flipped it and the focused row stayed
  legible (light row, dark text, mark redrawn in the inverse colour). Prefs moved `camera_grid_live`
  true→false→true and `minimize_video_streams` false→true→false: Ema's values are back.
- The camera editor (Camera list → **Add camera**) draws its four toggles where they were — *Stretch to
  16:9* and *Use audio track* beside each stream's title — with the same mark, so the side-by-side rows
  kept their layout. Nothing was saved and no camera was added (`cameras` still holds the same six).

## F1 — the camera's shape is remembered, and a chosen shape sticks (2026-11-13)

H1. Two bugs behind one feature, both in the ledger's own camera-shape story: `camerasToJson` wrote
`aspect` while `camerasFromJson` never read it, so every camera started at 16:9 and a manual choice died
on restart and on export→import; and `onAspect` overwrote any shape with whatever the stream reported.

### Touched files

| File | Change |
|---|---|
| `Cameras.kt` | `camerasFromJson` reads `aspect` back (`optDouble("aspect", 0.0)`, 0 = Auto) |
| `MainActivity.kt` | `onAspect` learns only while the stored value is Auto: `known <= 0f && reported > 0f` |

### Deliberate designs worth keeping

- **Auto is the learning state, not a third shape.** A chosen 16:9, 4:3, 1:1, 5:4 or 21:9 is the owner's
  and survives every stream start; cycling back round to Auto (aspect 0) hands it back to the stream. No
  new key, no format change: `aspect` was always in the cameras JSON, and the export goes through the same
  reader.
- **Learning resumes at the next stream start, not mid-stream.** Choosing Auto does not restart the player,
  so the box keeps its current shape until the camera starts again (a restart or a switch); that is the
  honest cost of not tearing down a live stream on a settings change.

### Verified on the Shield (2026-11-13, 10:2x–10:3x)

- Camera list → Doorbell's menu read **"Shape: 4:3"**: the stored `aspect=1.3333` is now read back (before
  this fix it read "Shape: Auto" on every start).
- Chose **1:1** (a shape the stream does not report), force-stopped the launcher
  (`am force-stop com.evofactory.homeboard`) and reopened: the Home box was **square** with a live picture
  on it, and `aspect` was still `1` seconds later — the stream no longer overwrites a chosen shape.
- Cycled back round to **Auto** (`aspect=0`), force-stopped and reopened: the first frame stored
  `aspect=1.3333333730697632` again and the box went back to **4:3**, which is the value it started with.

## F2 — the shuffle switches apply without a restart (2026-11-13)

H2. `SettingsPage` calls `onSettingsChanged` on every write, but the re-read block updated every rendered
pref except the four shuffle switches, so flipping one wrote the pref, moved the switch, and changed
nothing on screen until the app restarted; `applyImport` missed the same four, so an imported shuffle
setup also waited.

### Touched files

| File | Change |
|---|---|
| `MainActivity.kt` | `shuffleCamera`, `shuffleActions`, `shuffleCards`, `shuffleSides` re-read in both `onSettingsChanged` and `applyImport` |

### Deliberate designs worth keeping

- **The seed is not re-rolled by a switch.** The session's order was rolled at start; these four only say
  how to use it, so a switch flipped mid-evening applies to this session's order, not a fresh one. The rows
  already read "each session".
- **Every rendered pref belongs in both re-read blocks.** This is the third bug of that shape (H2 here,
  H4/F4, and M10's three camera rows); the rule is in the plan's §2 and now holds for the shuffle block too.

### Verified on the Shield (2026-11-13, 10:40–10:41)

- With shuffle off, Home showed the **doorbell**. OK on Home → *Shuffle the camera* (no restart, no back):
  `Player e15f started over TCP, …, /living_room` at 10:40:05 and the picture became the living room.
- OK on *Swap the camera and widgets* re-laid the Home tab at once (the camera box resized to the new
  camera's shape in the same frame pass); the sides flag is a per-session roll, so the order it lands on is
  not itself proof — the re-layout is.
- Both switched off again: `Player 77c3 started …, /doorbell` at 10:41:16 and the picture went back.
  `shuffle_camera`, `shuffle_actions`, `shuffle_cards`, `shuffle_sides` all read `false`, as Ema has them.

## F4 — changing the Home Assistant address changes what is running (2026-11-13)

H4. `HaAddressDialog` wrote `prefs.haUrl` and cleared the tokens, but `MainActivity.haServer` — the state the
connection, the panel and the *Log in* dialog read — kept the old URL. Until a restart the panel talked to
the old server while the rows already read "Not signed in", and *Log in* was pre-filled with the address
that no longer existed in prefs.

### Touched files

| File | Change |
|---|---|
| `HaAddressDialog.kt` | `onSaved: (String) -> Unit`; the Save button and the remote's Done now go through one local `save()`, so they cannot drift apart |
| `MainActivity.kt` | Passes `onSaved` doing the big dialog's server half: revoke the old login (only if it held a refresh token), `haServer = HaServer(address)`, `settingsRevision++` |

### Deliberate designs worth keeping

- **Saving the address you already have is not a change.** `save()` returns early when the normalised
  address equals the stored one: the remote's Done on an untouched field used to clear `ha_token` and
  `ha_refresh_token` and end the TV's login for nothing.
- **The revoke is conditional**: a long-lived token has no refresh token to revoke, so nothing is called
  into the old server on the way out.

### Verified on the Shield (2026-11-13, 10:44–10:52)

- Done on the untouched field: the dialog closed and the login survived (`ha_token` still present, the row
  still "Signed in"). Before this, that press cleared it.
- Address → `<ha-host>:8124` (the same server, a dead port) → the rows read the new address and "Not
  signed in", and the Home tab's whole panel emptied at once — the *running* connection had moved to the
  dead port with no restart. The old build would have kept drawing the old server's calendar until a
  restart.
- Address back to `<ha-host>:8123`, then *Log in* opened **pre-filled with that address and no login**
  (screenshot), the token login reconnected, Save wrote it, and the Home tab's calendar and sensors came
  back. Prefs afterwards: `ha_url=http://<ha-host>:8123`, `ha_token` the same long-lived token as
  before.
- One drift to record: showing the shuffled camera (F2) taught `cameras` the Living Room's own shape
  (`aspect=1.7777…`), which the baseline had as 0 (Auto). Same picture, and it is the learning F1 puts
  back; left as learned rather than reset through five more remote presses.

## F5 — both notification rows, each honest (2026-11-13)

H5 and W4, decided by Ema: **keep both rows**. Both mapped to the same panel, so "Access" opened a list of
notifications, which its title does not promise; with access off, both led to the same instruction screen
(that part was right).

### Touched files

| File | Change |
|---|---|
| `SettingsSchema.kt` | New `SettingAction.OpenNotificationGrant`; the **Access** row carries it, **Notifications panel** keeps `OpenNotificationAccess` |
| `SettingsMenuDialog.kt` | `SettingsActions.openNotificationGrant`, mapped to the new action |
| `NotificationsDialog.kt` | `NotificationAccessDialog`: the granted state in one line, and the `disallow_listener` command that takes it back — the panel's off state prints the allow counterpart, so the two screens read as one subject from both sides |
| `MainActivity.kt` | The action mapping decides: access on → the status screen; access off → the panel, which is where the grant is explained |

### Deliberate designs worth keeping

- **Neither row is a second door to the same screen.** Each says what it holds — "Allowed" / "8 showing" —
  before you press it, and each opens exactly that.
- **The mapping lives in `MainActivity`**, because whether access is granted is the app's state, not the
  schema's.

### Verified on the Shield (2026-11-13, 10:58)

- Advanced → Notifications read **Access · Allowed** and **Notifications panel · 8 showing** (access is
  granted on this Shield, as M9 left it).
- OK on **Access** opened "Notification access": "Allowed: HomeBoard reads the TV's notifications and lists
  them under Notifications panel", with the `adb shell cmd notification disallow_listener …` command and
  Close — not the list.
- OK on **Notifications panel** opened the list (QuickBars, SHIELD Experience Upgrade, tvQuickActions ×2,
  Shield SMB Server, DolbyAudioService, Android TV Remote Service). Nothing was cleared.

## F6 — the Assistant tab's strip is not gated by a Home setting (2026-11-13)

H6, decided by Ema: the Assistant tab's own switch is its only gate, as it is on the Cameras tab. The
strip there needed **both** `ha_bottom_row` and Home's "Alert strip", and neither row said so, so someone
who turned on "Alerts and apps below" and saw nothing had no pointer to the Home switch blocking it.

### Touched files

| File | Change |
|---|---|
| `MainActivity.kt` | `alertsRowAllowed = tab == Cameras \|\| tab == Assistant \|\| homeAlertsRow` — the Assistant tab is exempt from the Home switch, exactly like Cameras |
| `SettingsSchema.kt` | "Alert strip" says which tab it governs: "…on this tab; Cameras and Alerts always show it, and the Assistant tab has its own switch" |

### Verified on the Shield (2026-11-13, 11:0x)

- With `home_alerts_row=false` and `ha_bottom_row=true`: the Assistant tab showed the strip of Frigate
  clips along the bottom (screenshot). Before this it stayed hidden until the Home switch was also on.
- Turning **Alerts and apps below** off (`ha_bottom_row=false`) removed it from the Assistant tab again
  (screenshot: three cards and nothing under them).
- Both prefs back where they were: `ha_bottom_row=false`, `home_alerts_row=false`.

## F7 — a pinned app can get a custom icon from the row (2026-11-13)

H7. The row's tile menu put "Set icon"/"Reset icon" inside the *unpinned* branch, so a pinned app's menu
showed only "Unpin from favourites" — while the All apps page offered Set icon for every app. The two
menus disagreed, and the only way to give a pinned app an icon from the row was to unpin it first.

### Touched files

| File | Change |
|---|---|
| `AppRow.kt` | The icon entries moved out of the `if (pinned) … else …`: both branches now offer Pin/Unpin **and** Set icon (with Reset icon when an override exists) |

### Deliberate designs worth keeping

- **A pinned app keeps its identity everywhere.** Pinning changes the row's order, not what an app can be
  given; the row and the All apps page offer the same menu entries for the same app.

### Verified on the Shield (2026-11-13, 09:52)

Long-press on **Plezy** on the Home row (a pinned app) opened the tile menu, read from the live screen:

`Hide · Unpin from favourites · Set icon · Info · Uninstall · Show hidden apps (5)`

"Set icon" is there on the pinned branch, which is the fix; before it the menu stopped at "Unpin from
favourites". No icon was set and no app was unpinned.

## F10 — wording and status batch (2026-11-13)

W1, W2, W5, W6, W7, W8 (W7 decided by Ema: the keys act on an off light **and** the hint shows).

### Touched files

| File | Ref | Change |
|---|---|---|
| `SettingsSchema.kt` | W1, W2 | "Camera list" no longer advertises the screensaver (it left in M12): "The addresses, their order and shape, and how they play in the grid". "Per line" → **"Lines"**: the value *is* the number of lines, and more lines make smaller tiles |
| `HomeAssistantPanel.kt` | W5 | The status line moved above the `if (!showSensors) return@Column`, so "Home Assistant offline · retrying" also shows when the sensors card is off and the calendar has the space |
| `Cameras.kt` | W6 | The camera dialog's Advanced switches use the page's names and descriptions: "Live pictures in the grid", "One stream at a time" |
| `HomeAssistantTab.kt` | W7 | The level hint shows whenever the row is steppable: `N% ◂ ▸` while on, **"Off · ◂ ▸ sets the level"** while off |
| `LauncherPrefs.kt`, `HomeAssistantDialog.kt`, `MainActivity.kt`, `SettingsPage.kt` | W8 | The chosen dashboard's *title* is stored when the dialog saves (`ha_dashboard_label`, `ha_home_dashboard_label` — two new local keys, not exported) and the row prints it instead of the raw key |

### Deliberate designs worth keeping

- **W8's label is a cache of a name, not a setting.** It is written on save because only the dialog reads
  the dashboard list; a missing label falls back to the key, and "My selection" is decided by the choice
  itself, so an old TV without the keys reads exactly as before.
- **One name per switch.** W6 exists because M11 renamed the page rows and left the dialog behind; the two
  editors now say the same words, and the descriptions are the page's.

### Verified on the Shield (2026-11-13, 11:1x–11:2x)

- Home → **Lines · 6** — "Rows when the page scrolls sideways, columns when it scrolls up and down; more
  lines, smaller tiles" (screenshot).
- Cameras → **Camera list** — "The addresses, their order and shape, and how they play in the grid"; the
  word "screensaver" is gone.
- Camera list → **Advanced** — "Live pictures in the grid" (on) and "One stream at a time" (off), with the
  page's descriptions (screenshot).
- Assistant tab: the **Air purifier** row, off, reads **"Off · ▂ ▸ sets the level"** (screenshot).
- Assistant → *What it shows · My selection*: Ema's choice is the device selection, so the label path is not
  what this TV shows. W8's fallback is what was seen; choosing a dashboard to see a title would have
  rewritten his choice twice, so it was not done.
- W5 was not seen during an outage: the line is moved above the sensors-card gate in the composition
  (verified by reading the change and by the page building without it), but no outage was created on the
  live TV to watch it appear.

## F3 — a camera picked with the remote beats the shuffle, for the session (2026-11-13)

H3 and W3, decided by Ema. With "Shuffle the camera" on, the Home tab always drew `sessionCams.first()`, so
the channel keys and the number buttons called `selectCamera` and the display ignored them — and "Camera on
Home" was equally dead. Both features are ledger-deliberate (M2's keys, M7.2's shuffle); the fix makes them
compose instead of one silently winning.

### Touched files

| File | Change |
|---|---|
| `MainActivity.kt` | `cameraChosenManually`: `selectCamera()` sets it, the display uses the shuffled pick only while it is false (`if (shuffleCamera && !cameraChosenManually)`), and it is reset when the shuffle is switched back on — in `onSettingsChanged` **and** `applyImport`, comparing before the assignment |
| `SettingsSchema.kt` | "Camera on Home" hides while the shuffle owns the camera (`showIf = { !it.shuffleCamera }`); "Shuffle the camera" says a pick sticks until the TV's next session; "Channel keys switch cameras" says where the keys act (Home and Cameras tabs; elsewhere left to the TV) |
| `LauncherPrefs.kt` | The `homeCameraStart` doc comment said "always switch at once, whichever this is" — corrected to what the keys do |

### Deliberate designs worth keeping

- **The shuffle is the session's default, not its absolute.** One manual pick ends it until the process
  restarts; the seed is only rolled at start, so "rest of the session" is exact.
- **Re-enabling the shuffle hands the camera back**, which is what makes the switch mean something after a
  manual pick — and it is the only reset, so a pick is never forgotten mid-evening.
- **A row that cannot change anything is hidden**, not left to be guessed at (M10's precedent).

### Verified on the Shield (2026-11-13, 11:25–11:32)

- Shuffle on (`shuffle_camera=true`, set from the page, live per F2): Home showed the session's camera (the
  doorbell).
- **Number key 2**: `Player … started over TCP, …, /garden` and the Home picture became the Garden — with
  the shuffle still on. Before this the press wrote `camera_selected` and the display kept the shuffled
  camera. (Ema has `channel_keys=false`, so the number keys are the ungated manual path; they call the same
  `selectCamera`.)
- "Camera beside the cards" then held **only** "Space beside the camera": "Camera on Home" was hidden while
  the shuffle was on, and came back with it off (screenshots).
- Shuffle off: `camera_selected` still held the camera picked by hand and Home drew it; Ema's own selection
  was then restored with number key 3 (Front).

## F8 — vacuum room cleaning (2026-11-13)

M4's leftover, built from the live entities rather than from a snapshot. Discovery first, as the plan
requires:

| Robot | platform | `rooms` | `supported_features` |
|---|---|---|---|
| `vacuum.yiko` | `ecovacs` | `{dining_room:0, living_room1:1, corridor1:4, kitchen:2, bathroom:3}` | 29500 (SEND_COMMAND, RETURN_HOME, PAUSE, STOP, FAN_SPEED, LOCATE, STATE, START, CLEAN_AREA) |
| `vacuum.baka` | `ecovacs` | `{default:[43,20,46,32,47,42]}` | 29500 |

No `dreame_vacuum` domain exists on this server; both robots are the Deebot integration, whose documented
room clean is `vacuum.send_command` with `command: spot_area` and `params.rooms` (ids from the entity's own
`rooms` attribute).

### Touched files

| File | Change |
|---|---|
| `HomeAssistant.kt` | `HaSession.vacuumRoom(entityId, roomId)`: `send_command` with `spot_area` and `{rooms, cleanings: 1}`, through the existing private `callService` (same timeout/no-answer semantics) |
| `HomeAssistantPanel.kt` | `HaConnection.vacuumRoom(...)`, the same shape as `vacuum(...)`: not-connected toast, `HaException` toast |
| `HomeAssistantTab.kt` | `vacuumRooms(entity)` reads the attribute at render time and handles both shapes — a name→id map (labelled "Dining room") and an id list ("Room 43"); `VacuumRow` gains **Clean a room** (only when the robot reports rooms) and a second menu, "Clean which room?", whose entries call the service and close both |

### Deliberate designs worth keeping

- **Rooms come from the robot, never from app code** (D7): a robot that learns a room needs no rebuild, and
  one that reports none simply has no entry.
- **One id, one service, nothing else**: the app sends the id the entity gave it, so a name change in the
  robot's app cannot break the call.

### Verified (2026-11-13, 11:38)

- **The wire format, end to end against the real robot**: `vacuum.send_command` on `vacuum.yiko` with
  `{"command":"spot_area","params":{"rooms":"2","cleanings":1}}` — the exact body `vacuumRoom` sends —
  answered HTTP 200 and the entity went to **cleaning**; `vacuum.return_to_base` then took it to
  **returning** and back to **Docked**. The kitchen clean ran and was stopped.
- **The UI check did not happen**: the vacuum row sits in the Assistant tab's middle card, and the D-pad
  run in this session never landed on it (the card's switches take the focus; the app row below caught two
  stray OKs and opened Plexy twice). The "Clean a room" menu and its room list are therefore **built and
  compiled, not seen on the TV**. The next step for it is one remote press on Yiko's row.

## F9 — the generic service tile (2026-11-13)

M4's other leftover: a way to run a Home Assistant service that has no entity of its own
(`input_number.reload`, `automation.reload`). Built on C4's shared picker, not a fourth one.

### Discovery, before any code

`get_services` over the live websocket returns **116 domains**, each `{service: {fields: …}}` — this server
sends no names or descriptions, so nothing but the service names is shown. `input_number.reload`,
`automation.reload` and `script.turn_on` are all there.

### New files

| File | What it is |
|---|---|
| `HaServicesDialog.kt` | The shared picker over every `domain.service` the server reports: the domain as the label, the service under it, `Add it` / `Leave it out` / move, and the count line. Read when it opens, so a service an integration adds needs no rebuild |

### Touched files

| File | Change |
|---|---|
| `HomeAssistant.kt` | `HaSession.services()` (the `get_services` command, sorted) and `runService(domain, service)` — a call with no target, same timeout and no-answer handling as `callService` |
| `HomeAssistantPanel.kt` | `HaConnection.runService(...)`, the same not-connected and `HaException` toasts as the other rows |
| `LauncherPrefs.kt` | `haServices` (`ha_services`, a string list, default empty — a new key, no format change) |
| `SettingsSchema.kt` | Assistant → *Devices and actions* → **Custom services**, a List row with `SettingAction.OpenHaServices` |
| `SettingsMenuDialog.kt` | The action's hook |
| `MainActivity.kt` | The dialog's state, the pref read at start, in `onSettingsChanged` and in `applyImport`, and `services` handed to the Assistant tab |
| `HomeAssistantTab.kt` | A **Services** card, last, only when the list is non-empty: one `ServiceButton` per `domain.service`, "Reload" over "input_number", that calls `runService`. A `svc:` prefix marks one in a card's ids, so the card's height, its focus and its OK-to-enter behaviour are the ordinary ones |

### Deliberate designs worth keeping

- **Nothing free-typed** (Ema's decision): every entry comes from the server's own list, so a typo cannot
  become a tile.
- **Nothing new on screen when the list is empty**: the card is not added at all, so a TV that never chose a
  service looks exactly as it did.
- **HA's own refusals are shown**: the tile toasts whatever Home Assistant answers. `input_number.reload`
  answers **"Unauthorized"** on this TV because its user is not an admin — that is Home Assistant's
  permission model, not an app fault, and the toast is how you learn it.

### Verified on the Shield (2026-11-13, 11:45–11:57)

- **Custom services · None chosen** on the Assistant page; OK opened the picker, which listed the live
  services — `adguard · add_url`, `adguard · disable_url`, `adguard · enable_url`, `adguard · refresh`, … —
  the domain as the label, the service under it (screenshot).
- With one chosen, the Assistant tab grew a **Services** card, last in the left column, with one button
  ("Reload" over "input_number", then "Turn on" over "script" for the second attempt) (screenshots).
- Pressing the tile called Home Assistant: `input_number.reload` came back as the toast **"Unauthorized"**
  (admin-only for this TV's user), and `script.turn_on` produced no error at all — the tile runs what it
  says and surfaces what the server says.
- The empty-state line was wrong on the first build ("Home Assistant reports no services" over a full
  list). The fix reads "Nothing chosen: no service button appears on the Assistant tab" when there is a
  list and nothing selected, and keeps the "no services" line for a list that really is empty; that
  wording was **not** re-opened on the TV (the build with it is installed, and the picker had already been
  seen).
- **Puts back**: `ha_services` removed, so the list is empty as it was found (68 keys, the two additions
  below are the default values of keys the rows wrote).

## The Assistant tab's rows are reachable, and left/right act on them (2026-11-13)

Ema, from the TV: "I can't select Yiko at all: I can scroll down to the air purifier, but one more Down
goes to the favourite app row. I can't select the bedroom entities. I can't change the brightness of the
lights or the air purifier."

Two causes, both in `HomeAssistantTab.kt`:

1. **A vacuum row was drawn but never reachable.** `isInteractive(id)` — the function that decides which
   rows a card can select and counts them for the card's Down edge — listed scenes/scripts, switches,
   selects, alarms and the new services, but not `isHaVacuum`. So the Living Room card had five rows but
   only four selectable: the selection fell off the card at the air purifier, and the vacuum's Start /
   Pause / Return / **Clean a room** menu could not be opened at all (which is why F8's menu was never
   seen). The Bedroom card held one robot and one sensor, so it had *no* selectable row and OK did
   nothing there.
2. **The card swallowed left and right before the row saw them.** In `AssistantCard`'s `onPreviewKeyEvent`,
   any arrow key with a direction treated left/right as `edge = true` and called `moveFocus`, returning
   true — and a preview handler on the card runs before the focused row. `ToggleRow`'s brightness and
   percentage stepping therefore never ran, while the row's hint ("14% ◂ ▸", "Off · ◂ ▸ sets the level")
   promised it did.

### Touched files

| File | Change |
|---|---|
| `HomeAssistantTab.kt` | `isInteractive` gains `isHaVacuum`, with a comment saying what a missing entry costs; `entityLevel(entity)` extracted so the card and `ToggleRow` read a light's brightness or a fan's percentage the same way; the card reports whether the focused row steps a level, and only leaves the card on left/right when it does not |

### Deliberate designs worth keeping

- **One list decides reachability.** `isInteractive` is the only thing that counts a row for the card's
  arrow edges *and* decides whether it can take the selection; a row type missing there looks present and
  is dead to the remote.
- **A row's own keys win inside the card.** Level rows keep left/right; every other row passes them to the
  card, which moves the selection out sideways as before.

### Verified on the Shield (2026-11-13, 15:05–15:08)

- Assistant → Living Room, gone into, walked down: Main light, Stand, LED strip, Air purifier, **Yiko** —
  Yiko is lit, and OK opened its menu: **Start / Clean a room**. "Clean a room" opened **"Clean which
  room?"** with the robot's own rooms: Dining room, Living room1, Corridor1, Kitchen, Bathroom. Nothing was
  started (Back closed both menus); this is also the F8 UI check that session could not reach.
- **LED strip, Left**: 80 % → 69 % on screen and the row kept the selection, so left/right now reach the
  row. The level was put back to 80 % (`brightness: 204`) with Home Assistant's own service.
- Bedroom now has Baka as a selectable row; its sensor stays a readout, which is what a sensor is.

## OK on the weather opens its forecast (2026-11-13)

Ema: "Clicking on the weather should bring up a popup with a forecast of the other days." The data was
already there — the panel follows `hourly` and `daily` for every chosen weather (M13) and only the card's
own reading was drawn.

### Touched files

| File | Change |
|---|---|
| `HomeAssistantTab.kt` | `AssistantCard` takes an `onOpen`: on a card with nothing to go into, OK calls it instead of doing nothing. A weather card passes it (`forecastFor`), and the dialog is drawn once, after the cards |
| `HomeAssistantWeather.kt` | `WeatherForecastDialog`: the weather's name and its now-line, then one row per day — Today / Tomorrow / "Sun 15 Nov", condition icon and label, high/low, rain % or mm — and the next eight hours underneath. A server that gives no forecast says so instead of showing an empty box |

### Deliberate designs worth keeping

- **A weather card is a readout with a door**, not a row: it keeps its own card, its hourly strip and its
  size, and OK on the card is the door. Nothing else in the card is selectable, so the card's arrow edges
  are untouched.
- **The data is the server's.** Nothing is invented: the days are Home Assistant's `daily` forecast as the
  integration emits it, and an integration with no forecast gets the honest line.

### Verified on the Shield (2026-11-13, 15:11)

- Assistant tab → OK on the weather card opened **"Outside · Partly cloudy · 6°"** with eight days — Today
  7° / -1° rain 20 %, Tomorrow 8° / 0° cloudy, Sun 15 Nov rain, Mon/Tue sleet, Wed rain 20 %, Thu sunny,
  Fri cloudy — and "Next hours" 15:00–22:00 with temperatures and the 20 % hour (screenshot).
- Close returned to the card. The card's own arrow edges (Up to the tab, Down to the next card) are
  unchanged: it is still one card.

## The calendar colours open the launcher's one colour picker (2026-11-13)

Ema: "The colour selection is still wrong and using two separate popups rather than reusing code." C3 had
built `ColourPickerDialog` and moved the background onto it, but the calendar kept a second editor: its own
per-row layout, its own palette `ContextMenu` and its own left/right cycling.

### Touched files

| File | Change |
|---|---|
| `CalendarColoursDialog.kt` | Each calendar's row is a **door**: OK opens the same `ColourPickerDialog` the background uses (dot, name, "In use", Done). The local cycling and the local palette menu are gone; `choose()` still writes the same `id=#RRGGBB` line |
| `HaCalendar.kt` | `cycleCalendarColour` deleted with them (nothing else called it) — `colourOf`, `calendarColour` and `CalendarPalette` stay |
| `ColourPickerDialog.kt` | Opens **on the colour in use**, scrolling the list to it first (a lazy row that is not composed cannot take the focus), so "In use" is where the remote is instead of off-screen |

### Deliberate designs worth keeping

- **One palette renderer, two palettes.** The background's 12 and the calendar's 8 are data; the dialog is
  the only place a colour list is drawn. A second editor cannot drift from the first because there is no
  second editor.
- **A door says what OK does.** The calendar row reads "OK chooses a calendar's colour…" and the row's own
  left/right stepping is gone: no key does anything invisible (M12's "pickers say what OK does", applied to
  the last dialog that still had a hidden key).

### Verified on the Shield (2026-11-13, 15:15–15:22)

- Calendar → **Event colours · 2 chosen** → OK on "Ema - Work" opened the picker with the calendar's name as
  its title, the same palette list as Launcher → Background → Colour, and **Amber · In use** already focused
  and scrolled into view (screenshot).
- Done on both closed back to the Calendar page; `calendar_colours` is unchanged (the check changed no
  colour).

## The badge counts a source, and the dot stands down for it (2026-11-13)

Ema: "The badge should be for the Android notifications OR for Frigate alerts; clicking it opens the
notification panel or the Alerts tab accordingly. If the badge is active for Frigate alerts, the dot on the
Alerts tab is automatically disabled; if the badge is active for the Android notifications, the dot is
active."

M10 had deleted the source choice ("a dot says enough"). This restores M9's key with the coupling M9 never
had.

### Touched files

| File | Change |
|---|---|
| `LauncherPrefs.kt` | `badgeSource` over `badge_source` (M9's own key, so an M9-era export still reads), values `frigate` (default) and `notifications`, with `BADGE_SOURCE_*` constants; an unknown value falls back to `frigate` |
| `SettingsSchema.kt` | Advanced → Top bar: **The count is** (a Choice row) directly under **New events badge**, shown only while the badge is on. The badge row's `showIf` no longer demands the Alerts tab when the count is the TV's notifications; the three descriptions now say what each does |
| `MainActivity.kt` | `badgeSource` state, read at start and in both re-read blocks; the count is the chosen source (Frigate's unseen alerts, or `Notifications.entries.size`); OK opens the panel for notifications and the Alerts tab for Frigate; the dot is Frigate's and is suppressed while the badge already counts Frigate |

### Deliberate designs worth keeping

- **The coupling is derived, never written.** `alerts_dot` keeps exactly one editor (its row); the render
  applies `&& !frigateCounted`, so no pref has two writers and M10's one-editor rule survives the reversal.
- **Each indicator keeps its own source.** The badge says what you chose; the dot always means Frigate, and
  only stands down when the badge is already saying it.
- **M10's removal is superseded on the record.** The ledger line is what stops a rebase resurrecting it.

### Verified on the Shield (2026-11-13, 15:23–15:30)

- Advanced → Top bar showed **The count is · Frigate alerts** under the badge; its menu offers *Frigate
  alerts ✓* and *The TV's notifications*.
- Choosing **The TV's notifications** put the red **8** beside the clock at once (the live list, no restart),
  and OK on the badge opened the **Notifications** panel (QuickBars, SHIELD Experience Upgrade, tvQuickActions
  ×2, Shield SMB Server, DolbyAudioService, Android TV Remote Service).
- With the source back on **Frigate alerts**, the dot's expression reads
  `alertsDot && !frigateCounted && …`; the suppression itself was not seen, because this Frigate has no
  unseen alerts to draw a dot from.
- Puts back: `badge=true`, `badge_source=frigate` (the state FORK.md's M10 section records; the badge had
  been left off by an earlier stray press and was restored with a checked host write — file size and key
  count asserted before and after, 68 keys both times).

## The app row can centre itself (2026-11-13)

Ema: "we should offer an option to centre the favourites app row if there are not enough apps to go to the
edge."

### Touched files

| File | Change |
|---|---|
| `LauncherPrefs.kt` | `app_row_centre` (new key, default **off**) |
| `SettingsSchema.kt` | Home → Rows → **Centre the row**: "When the apps don't reach the screen's edge, the row sits in the middle instead of at the left" |
| `AppRow.kt` | When the tiles fit, the row's padding becomes the even gap at both edges instead of `EdgePadding` on the left only; the tiles' Up targets come from their **index** in a centred row (a row that fits never scrolls, so index is the slot) and from the on-screen slot otherwise |
| `MainActivity.kt` | The state, read at start and in both re-read blocks; passed to the row |

### Deliberate designs worth keeping

- **Two ways of knowing a tile's slot, because there are two rows.** A scrolling row's slot is measured
  from the item's offset; a centred row fits on screen, so its index *is* the slot. Using the offset for the
  centred case sent Up to the tab bar — measured on the TV, not guessed.
- **Off by default**: an existing TV looks exactly as it did until the switch is pressed.

### Verified on the Shield (2026-11-13, 15:31–15:37)

- Home → Rows shows **Centre the row**; OK turned it on (`app_row_centre=true`) and the five tiles moved to
  the middle of the screen with even margins (screenshot), from x≈98 to x≈1470 of 1568.
- **Up from a centred tile** goes to the camera/panel (focus dump `[249,176]…`), the same as with the row
  left-aligned — the first attempt sent it to the tab bar and is what the index mapping fixes.
- Left as **on**, so Ema sees it; one press turns it off.

## The Cameras tab can lose its strip, and a grid that fits uses the width (2026-11-13)

Ema: "We should be able to hide the alerts row on the camera tab and use the space to make the grid bigger.
We might also (independently) be able to use the space until the edges if not displaying enough cameras to
trigger a scroll, lateral or up/down."

### Touched files

| File | Change |
|---|---|
| `LauncherPrefs.kt` | `cameras_alerts_row` (new key, default **true** — today's behaviour until asked for) |
| `SettingsSchema.kt` | Cameras → Grid → **Alert strip on this tab**: "The strip of Frigate events under the grid; off gives the grid that room" |
| `MainActivity.kt` | `alertsRowAllowed` becomes `tab == Assistant \|\| homeAlertsRow \|\| (tab == Cameras && camerasAlertsRow)` — the strip and the No-alerts tile follow it, the app row does not. State read at start and in both re-read blocks |
| `Cameras.kt` | `TwoRowGrid` gains `useTheWidth` (the camera grid asks for it, the alerts strip does not): when the columns needed fit the screen, the cells are sized to the width instead of by the height's 16:9, and the block is centred when the height is what binds. The scroll window moves with the cells |

### Deliberate designs worth keeping

- **A tab's strip is that tab's switch.** Cameras had no say before (D3: "always kept on Cameras"); this is
  the same shape as the Assistant tab's switch (F6), and the default keeps a TV that never presses it
  exactly as it was.
- **The grid grows by itself.** The strip and the grid share one column, so hiding the strip gives the grid
  the room with no new sizing code: `weight(1f)` was already there.
- **Never a cropped picture.** A fitted grid widens to the screen but cannot exceed the 16:9 height cap, so
  tiles stay the shape the cameras send and the leftover splits to both edges.

### Verified on the Shield (2026-11-13, 15:40–15:42)

- Cameras tab with the strip on: unchanged (grid 3 × 2 of 16:9 tiles, six events underneath).
- OK on **Alert strip on this tab** (`cameras_alerts_row=false`): back on the Cameras tab the strip and the
  No-alerts tile are gone and the tiles grew from ~590×420 to ~655×468, with the app row in the strip's
  place (screenshots). No restart.
- The grid is centred to the width: first tile at x≈101, last ending at x≈1467 of 1568.
- Left **off**, because that is what he asked for; one press brings the strip back.

## "Wider widgets" is graded (2026-11-13)

Ema: "'Wider widgets' is nice but I think you gave too much room to the widgets, perhaps a configurable,
'double the size' or 'half extra' type of thing?"

### Touched files

| File | Change |
|---|---|
| `LauncherPrefs.kt` | Two more `camera_side` values: `half` and `double`, added to `CAMERA_SIDES` (M12's `left`/`centre`/`wider` untouched, so an old value means what it always did) |
| `SettingsSchema.kt` | The row now offers five: *Leave the gap*, *Centre the picture*, **Widgets: half again as wide**, **Widgets: twice as wide**, **Widgets: the rest of the width** |
| `MainActivity.kt` | One `panelWidth` for the side layout: the panel's own width, ×1.5, or ×2 — capped at 60 % of the width so the camera always keeps a picture — and the camera's room is computed from it. `wider` still means the rest |

### Deliberate designs worth keeping

- **Same key, more values.** `camera_side` is not renamed and every value it had still means what it did, so
  an export from before this reads exactly as before; an old build reading `half`/`double` falls back to
  `left` through the existing `takeIf { it in CAMERA_SIDES }` guard.
- **The camera always keeps its picture.** The panel is capped, so no choice can squeeze the camera out of
  its own slot.

### Verified on the Shield (2026-11-13, 15:43–15:46)

- Home → **Space beside the camera** lists all five with *Centre the picture ✓* on Ema's value.
- Choosing **Widgets: twice as wide** (`camera_side=double`) made the calendar panel ~846 px wide against
  the camera's 605, from 260 dp before (screenshot), with no restart.
- Restored to **Centre the picture** (`camera_side=centre`), the value found on the TV.

## The screensaver's settings are rows on the page (2026-11-13)

Ema: "Why does the screensaver settings be a popup?" It was `ScreensaverDialog`, one door holding a choice,
a toggle, a per-camera chip list and the Android-choice status. M12 had to put it in a dialog because the
page could render only scalar rows from the static schema, had no way to express a dynamic list, and had no
pattern for a status block. All three blockers are gone (C4's picker, C1's page work, F5's status screen),
so it is four rows now.

### Touched files

| File | Change |
|---|---|
| `SettingsSchema.kt` | Launcher → Screensaver is four rows: **Time on each camera** (Choice), **Cameras it shows** (List → picker), **Connection progress** (Toggle), **The TV's choice** (Action). `OpenScreensaver` replaced by `OpenScreensaverCameras` and `OpenScreensaverStatus` |
| `LauncherPrefs.kt` | `screensaverSecondsChoice`: the same `screensaver_seconds` key read as a choice value, so the row and the service cannot disagree |
| `Screensaver.kt` | `ScreensaverDialog` and `ScreensaverSettings` deleted. Two screens take their place: `ScreensaverCamerasDialog` (the shared picker over the camera list, chosen = shown, `onChange` writes the hidden list) and `ScreensaverStatusDialog` (whose screensaver the TV starts, when, and the two ADB commands when it isn't ours) |
| `EntityPickerDialog.kt` | `movesMatter: Boolean = true`: false hides the move entries, the position line and left/right, for a list where order means nothing (the screensaver's cameras play in the Cameras list's order) |
| `SettingsPage.kt` | `screensaver_cameras` reads "All 6 cameras" / "4 of 6" — the stored list is what is *left out*, so the generic "N chosen" would read the opposite |
| `SettingsMenuDialog.kt`, `MainActivity.kt` | The two hooks and their dialogs; the `screensaver` door and its state are gone |

### Deliberate designs worth keeping

- **One storage key, one writer.** The Choice row writes the same Int key the DreamService reads; the dialog
  that used to write it is deleted, so there is no second editor (M8's rule).
- **Order means nothing, so the picker says nothing about order** — the shared picker learns one flag rather
  than growing a fifth sibling.
- **A status is a screen, not a paragraph in a dialog** (F5's pattern), and the command it prints is the one
  an admin can paste.

### Verified on the Shield (2026-11-13, 15:50–15:55)

- Launcher → Screensaver reads **Time on each camera · 15 s**, **Cameras it shows · All 6 cameras**,
  **Connection progress** (off), **The TV's choice** (screenshot). The "How it plays" door is gone.
- OK on **The TV's choice** opened "The TV's screensaver": *"The TV's screensaver is turned off."* plus both
  `adb shell settings put secure …` commands, and its button opened the TV's own Screen saver chooser with
  **HomeBoard Cameras** listed. Nothing was chosen there and the TV's setting was left as it was.
- Not opened on the TV: the cameras picker itself (the row and its summary were seen; the picker is C4's,
  with the no-order flag). It is the first thing to press next.

## The one colour picker is a grid, and the widget widths are shares of the row (2026-11-13)

Two of Ema's follow-ups on the same session.

### The colour picker, again (and for the last time)

A list of named colours is still a second kind of picker; he wants the one Android itself shows. `ColourPickerDialog` is now a **grid**: greys in the first column, then a hue every 30° across, eight shades down from the palest to near black — 13 × 8 swatches the remote walks with the arrows — with the colour in use as a dot beside the title, the last six picked underneath ("Recently used"), and Done. Both places that choose a colour open it: Background → Colour and Calendar → Event colours (whose rows are doors onto it). The named palettes are gone from the dialog: `CalendarPalette` still assigns the *default* colour of a calendar by position, which is a different job.

- New key `recent_colours` (local, never exported).
- `ColourPickerDialog(title, subtitle, current, onPick, onDismiss)` — no palette parameter at all, so no caller can have its own.

### The widths, measured

The first attempt scaled the panel's own 260 dp (`×1.5`, `×2`), which is not what he described and, worse, left the slack on the outside of the pair. Now:

| Value | What the panel gets |
|---|---|
| *Leave the gap* / *Centre the picture* | 260 dp (28.5 % of the 912 dp row) — the default |
| *Widgets: two fifths of the width* | 0.43 × the row (6 of 14) |
| *Widgets: half the width* | 0.5 × the row (7 of 7) |
| *Widgets: the rest of the width* | everything the camera leaves |

and the slack is always placed **between** the picture and the panel (`gapSlot`), never outside them, so widening the widgets cannot push anything off the screen's edge.

### Verified on the Shield (2026-11-13, 16:16–16:25)

- **The grid**: greys left, hues across, eight shades down, "In use" dot, the remote starting on the current colour (screenshot). It replaced the same dialog the calendar uses; nothing else in the tree draws a colour list.
- **Two fifths**: camera 16:9 at the left, panel right at 392 dp of the 912 dp row (0.43), the gap between them (screenshot). *Half* is the same path at 0.5. Restored to **Leave the gap**, the value found.
- **The measurement trap**: three screenshots in a row showed the previous value because a host-side prefs write was being raced by Android's automatic restart of the home app (the M12 pattern). The order that works — write, then force-stop, then start — is what produced the final captures.
- Also fixed on the way: `TopDialog`'s title and subtitle are one line each with an ellipsis and a weight, so a long title can no longer wrap into the buttons (the overflow he pointed at); if the artefact he photographed was something else, it is the next thing to press.

## Which side the widgets are on, and where the space goes (2026-11-13)

From Ema's two screenshots: with the default width the pair spans the row and leaves **one big gap in the
middle**; with a wider panel the pair sat **flush left with an empty right side**, which reads as odd. And
there was no way to put the widgets on the left.

### Touched files

| File | Change |
|---|---|
| `LauncherPrefs.kt` | `camera_widgets_side` (`right` default, `left`) and `camera_gap` (`between` default, `sides`) — two new keys, both additive |
| `SettingsSchema.kt` | Two rows in *Camera beside the cards*: **Widgets are on the** (The right / The left) and **The gap goes** (Between the two / On the sides) |
| `MainActivity.kt` | The side row now composes from three explicit cases: the panel takes the rest of the width; or the pair is together in the middle with its slack **split to both sides**; or the pair spans the row with its slack **between** them. Which side the widgets sit on is the setting, XOR the per-session shuffle swap, so both still compose |

### Deliberate designs worth keeping

- **The pair is never left hanging.** Every case either fills the row or centres the pair with equal outer
  margins; the only difference is *where* the slack goes, which is now a choice instead of a consequence.
- **Two switches, not one list.** Side and gap are independent: any width works with either side and either
  gap, and the shuffle's per-session swap still flips the setting rather than overruling it.

### Verified on the Shield (2026-11-13, 16:27–16:29)

- **The gap goes: On the sides**, default width: camera 202–900, panel 939–1364, margins 202 left and 204
  right — the pair centred, the middle gap 39 px (screenshot).
- **On the sides + two fifths**: camera 96–792, panel 831–1470, margins 96 and 98, the panel visibly wider
  (screenshot). This is the shape his second screenshot was reaching for.
- **Widgets are on the: The left** (gap between): the calendar at the left edge, the camera at the right
  edge, the slack between them — the mirror of his first screenshot (screenshot).
- Restored: `camera_side=left`, `camera_gap=between`, `camera_widgets_side=right`.

## The badge's panel was the shared dialog with a broken header (2026-11-13)

Ema's report: the counter near the clock set to the TV's notifications opens a dialog that "is
different", has no Clear all, and a botched X. All three were real, none was a second popup: every
popup in the fork is `TopDialog` (the only raw `Dialog` windows are the fullscreen camera and alert
clip players, and the settings page is a full-screen page). What he saw:

1. **The X sat ~230 dp short of the right edge** in any dialog without tabs. The header's title had
   `weight(1f, fill = false)` *beside* a `Spacer(weight(1f))`; the two weights split the row's slack,
   and the title's unused half landed to the right of the X. It was worst in the notifications
   dialog, the one popup whose header has no other buttons, so it read as a cheap one-off. Fix: the
   title (and the title+subtitle column) now takes `weight(1f)` — the whole row — and the spacer
   survives only in the tabs branch, where it was always correct.
2. **No Clear all**: it only appeared when some notification was clearable, and the Shield's six
   notifications are all ongoing services (tvQuickActions, Shield SMB, Dolby, the remote service),
   so the button never showed. It now shows whenever there is a list — drawer semantics — and when
   nothing can be dismissed the dialog's hint line says why: "None of these can be dismissed: they
   belong to running services."
3. **`NotificationAccessDialog` had a Close button next to the X**: two ways to do one thing. The
   button is gone; the X is the top row and where the remote starts.

### Touched files

| File | Change |
|---|---|
| `TopDialog.kt` | Header: title/subtitle take the row (`weight(1f)`), the spacer stays only with tabs |
| `NotificationsDialog.kt` | Clear all with any list; the ongoing-only hint; access dialog's Close removed |

### Verified on the Shield (2026-11-13, 17:07–17:14)

- Badge → Notifications: title left, **Clear all** then X flush right (~120 px from the dialog
  edge), six rows, first focused, hint line printed.
- Network status (the subtitle branch): three buttons + X flush right, title and subtitle one line
  each.
- `assembleDebug` green; tree committed clean.

## Every dialog row reaches the X (2026-11-13)

The audit's three dead ends: the entity picker, the entity names and the calendar colours dialogs drew
their rows without `ringToTop()`, so Left and Right from a row did nothing — the remote could not reach
the X or the Done button from the list, unlike every other dialog.

### Touched files

| File | Change |
|---|---|
| `EntityPickerDialog.kt` | Rows get `.ringToTop()`; a chosen row in a dialog where order matters still consumes Left/Right to move itself first |
| `EntityNamesDialog.kt` | Rows get `.ringToTop()` |
| `CalendarColoursDialog.kt` | Rows get `.ringToTop()` |

### Verified on the Shield (2026-11-13, 17:45–18:05)

- Entity picker (Launcher → Screensaver → Cameras it shows): Left from the Doorbell row lands on the X.
- Calendar colours (Calendar → Event colours): Left from the first row lands on the X; Right then OK
  closes the dialog through Done (the a11y dump after Right still shows the row — the bridge reports a
  stale node; the OK outcome is the proof).
- Names on screen (Assistant → Top bar and order): Left from the first row lands on the X.

## No Close button beside the X in the screensaver status (2026-11-13)

The audit found the same redundancy just removed from the notification access dialog, one file over:
`ScreensaverStatusDialog` offered a primary "Close" button next to the X every dialog already has —
two controls for one action.

### Touched files

| File | Change |
|---|---|
| `Screensaver.kt` | The "Close" button is gone; "TV screensaver settings" stays as the one action, and the remote starts on the X, like the notification access dialog |

### Verified on the Shield (2026-11-13, 18:10)

Launcher → Screensaver → The TV's choice: header holds the action button and the X only, focus ring on
the X when it opens, the status text with the two ADB commands below. (`screensaver_show_progress`
checked untouched after a stray OK landed on its toggle row: still true.)

## Rows ellipsize, and the select row loses the header bug's last copy (2026-11-13)

The audit's two mediums. Twelve texts could wrap and grow their row instead of ellipsising — a long
camera URL in the entity picker, a long label in the context menu, a long stream title. And `SelectRow`
still had the exact weight pattern that put 230 dp of slack past the dialog X: a
`weight(1f, fill = false)` name beside a `weight(1f)` reading, so the chevron floated short of the
row's end. Latent today — no `input_select` entity is chosen on this TV — but it was the last copy.

### Touched files

| File | Change |
|---|---|
| `EntityPickerDialog.kt`, `CalendarColoursDialog.kt`, `ExportImport.kt`, `EntityNamesDialog.kt`, `ContextMenu.kt`, `CameraDialog.kt`, `HomeAssistantDialog.kt`, `HomeAssistantPanel.kt` | `maxLines = 1, overflow = TextOverflow.Ellipsis` on the twelve texts that could wrap |
| `HomeAssistantTab.kt` | `SelectRow`: the name takes the row (`weight(1f)`, ellipsising), the reading sits intrinsic before the chevron — flush at the end, like every other row; the now-unused `TextAlign` import is gone |

### Verified on the Shield (2026-11-13, 18:15)

The Assistant tab walked on the new build with no regression: sensors, steppable rows (Stand, LED
strip), vacuum, alarm and scripts all render and hold focus as before. The select row itself cannot be
seen until a select entity is chosen — noted honestly; the fix is the same geometry as the verified
TopDialog header, in the opposite direction.

## One reader, one parser, one swap, one empty state (2026-11-13)

The audit's low finding, landed. Nothing here was visibly disagreeing yet; this is drift removed so it
cannot start.

### Touched files

| File | Change |
|---|---|
| `HomeAssistant.kt` | The one JSON reader pair: `JSONObject.stringOrNull` (only a real string counts — a time arrives as a string over the websocket but as an object over REST) and `JSONArray?.objects`, both `internal` for the package |
| `HaCalendar.kt`, `HaDashboard.kt`, `HaHistory.kt` | Their private copies (`text`/`string`/`objects`) deleted; call sites on the shared pair |
| `HaCalendar.kt` | `colourOf` delegates to `cssColor` — the one parser, which also reads 3- and 8-digit hex, so a `#e45` no longer paints two ways; green stays the calendar default |
| `EntityPickerDialog.kt` | `List.swapping(from, to)`: the one swap, bounds-checked |
| `EntityPickerDialog.kt`, `HomeAssistantTab.kt` | The picker's move and `moveSensor` both go through it; `moveSensorCard` keeps its own remove-and-insert (a card shift is not a swap — not forced in), and the card boundary in `moveSensor` is preserved |
| `TopDialog.kt` | `EmptyState` (one type, colour and padding for a list's nothing-line) and `FocusRequester.requestFocusAfterLayout` (two frames, then the ask) |
| `HomeAssistantDialog.kt` (×2), `Cameras.kt`, `ExportImport.kt` | The four plain nothing-lines are `EmptyState`; `AlertsMessage` (it can be an error), AppRow's hero and CalendarWidget's `Detail` stay — different components, not drift |
| `HomeAssistantDialog.kt` | `rowColors()` for its two identical choice rows — the audit's "four sites" turned out to be three deliberate recipes (dimmed toggle rows, round action chips, notification rows); only the true pair is shared |
| `NotificationsDialog.kt`, `SettingsPage.kt`, `HomeAssistantDialog.kt`, `TopDialog.kt` | The four two-frames-then-requestFocus blocks are the one helper; the HA dialog's one-frame copy gains the second frame |
| `SettingsPage.kt` | The dead trailing `Spacer(weight(1f))` after `LogSize` is gone |

### Verified on the Shield (2026-11-13, 18:30)

- Calendar colours pixel-sampled on the Home tab: `#FFFFB3`, `#C4C466`, `#C466C4`, `#2F2F8A`,
  `#3DDC84` — exactly the stored `calendar_colours`; a first vision pass read them as one green, the
  pixels say otherwise.
- Calendars picker (Which calendars, order matters): Left on Birthdays moved it Priority 10 → 9; Right
  put it back. The swap works through the shared primitive, and the order was restored.
- Notifications dialog opens on its first row through the shared focus helper.
- Calendar events render with summaries and locations (the `stringOrNull` path).

## The notifications panel tells the two kinds apart (2026-11-13)

Ema: "the clear/dismiss all on the notification drawer doesn't work, also you should distinguish actual
notifications from persistent notifications for background apps."

**Clear all was not broken** — it was offered when it had nothing to clear. `cancelAllNotifications()`
works: a notification posted with `adb shell cmd notification post` vanished from the list the moment
Clear all was pressed (the list updates live through `onNotificationRemoved`). Every row on this TV is
an ongoing service notification (QuickBars, tvQuickActions ×2, Shield SMB, Dolby, Android TV Remote),
and the TV refuses to dismiss those, so the button looked dead. The panel also drew them as if they
were ordinary notifications.

### Touched files

| File | Change |
|---|---|
| `NotificationsDialog.kt` | The list is split by `clearable`: the apps' own notifications first, then an **Ongoing** heading with the services' own, dimmed. `Clear all` appears only when something can be cleared — with nothing to clear the hint line says why instead of offering a button that does nothing |

### Deliberate designs worth keeping

- **Clear all follows the drawer's rule**, not "always show": a button that cannot do anything is worse
  than no button and one line saying why.
- **"Ongoing" is the only heading**: the posted group is what the dialog is called, so heading it
  "Notifications" printed the title twice (seen on the TV, then removed).
- **The badge still counts everything**, because it is the drawer's entry point: counting only
  dismissible ones would hide it on a TV whose only notifications are services, and the panel would
  become unreachable.

### Verified on the Shield (2026-11-13, 18:50)

With a posted test notification: two groups — the dismissible row above, **Ongoing** dimmed below,
`Clear all` in the header. Pressed Clear all: the test row went, the button disappeared, the hint
appeared, focus moved to the first remaining row, and the six service notifications stayed.

## The count chooses what it counts (2026-11-13)

Ema: "You can give the option, to show all type of notifications or just dismissable notifications or
none or the frigate ones!"

`badge_source` gained a third value instead of a new key (storage keys and the export format stay as
they are):

| Choice | Counts | OK opens | The panel lists |
|---|---|---|---|
| Frigate alerts | unseen Frigate events | the Alerts tab | — (the tab's own list) |
| Everything the TV shows | every notification | the panel | both groups: posted, then **Ongoing** |
| Only what can be dismissed | the `clearable` ones | the panel | posted only |

"None" is the existing *New events badge* switch above it — off is off, one writer, no fourth value
duplicating a toggle.

### Touched files

| File | Change |
|---|---|
| `LauncherPrefs.kt` | `BADGE_SOURCE_DISMISSIBLE` + `BadgeSources`; a stored value outside the three still reads as Frigate |
| `SettingsSchema.kt` | The row's three choices; the badge switch shows for every notification source |
| `MainActivity.kt` | The count for the dismissible source; OK opens the panel for either notification source; the panel gets `dismissibleOnly` |
| `NotificationsDialog.kt` | `dismissibleOnly` hides the Ongoing group and says "Nothing the TV lets an app dismiss right now." when it empties; the hint only explains rows that are there (the empty line said it twice on the first build — caught on the TV) |

### Verified on the Shield (2026-11-13, 19:30–19:45)

- Three choices in the row on the TV: Frigate alerts / Everything the TV shows / Only what can be
  dismissed.
- With "Only what can be dismissed" and no dismissible notifications: **no badge** (the six ongoing
  ones do not count).
- With one posted test notification: badge shows 1; the panel lists that row alone (no Ongoing
  group); Clear all removes it, the button goes, and the empty line reads once.
- Restored to "Everything the TV shows" (his setting).

## The count is grey when it counts no news (2026-11-13)

Ema: "When showing all notifications and there are no dismissable notifications, colour the badge the
same grey as the other buttons, otherwise you use what you have now."

Red says something arrived for you; grey says the count is only the services' own ongoing
notifications, which nobody can dismiss and which are not news. The badge takes its colours from the
round buttons beside it (`surfaceVariant` disc, `onSurfaceVariant` number, `onSurface` when focused)
in that case, and keeps its red otherwise.

### Touched files

| File | Change |
|---|---|
| `HomeTopBar.kt` | `newEventsCalm` through to `NewEventsBadge`: two colour sets, one shape and size |
| `MainActivity.kt` | The flag: the source is the TV's notifications **and** every entry is ongoing |

### Verified on the Shield (2026-11-13, 19:50)

Pixel-sampled: with the six ongoing notifications the badge disc is `#2B2A2F` — the same grey as the
moon and Ethernet buttons (their discs sample `#2B2A2F`, their icons `#9A979D`, the badge's number
the same). With one posted dismissible notification it turns `#C62828` again, and back to grey when
that one is cleared.

## The cog is the same grey as the buttons beside it (2026-11-14)

Ema: "cog grey". `IconButton`'s own container is a shade darker (`#222226`) than the `Surface` the
moon, the network button and the badge use (`#2B2A2F`), so the cog read as a different kind of button
in the same row.

### Touched files

| File | Change |
|---|---|
| `SettingsButton.kt` | The IconButton takes the same four colours as its neighbours: `surfaceVariant` disc, `onSurfaceVariant` icon, `onSurface` when focused, `inverseOnSurface` icon then |

### Verified on the Shield (2026-11-14, 14:55)

The TV had slept overnight; woken with WAKEUP and HOME first, then measured. Unfocused discs:
moon `#2B2A2F`, network `#2B2A2F`, cog `#2B2A2F`, badge `#2B2A2F` — four identical. Focused cog:
disc `#C4C1C6` (the same `onSurface` the neighbours take when focused), dark icon, "Settings" label
under it.

## Security pass: what a launcher on a home network actually exposes (2026-11-14)

Ema: "audit the code for security and cybersecurity, it's a TV launcher so there shouldn't be much
going on." An audit of the whole app, read-only first, found no Critical and one High: the peer
export sent the Home Assistant long-lived token to a peer whose identity nobody checked. That token
has no expiry and controls locks, the alarm and the cameras, so the transfer needed a gate that the
normal flow does not notice.

### The findings and what changed

| Finding | Fix |
|---|---|
| Peer export: the four-digit code was drawn but never compared, so any device that answers the UDP broadcast could take the token with one wrong pick in the list | The Home Assistant login starts **unticked** in the send-to-a-TV checklist (`startUnchecked` on `ChecklistDialog`), with a line under the list saying to tick it only if both TVs show the same code; the code line now says what a mismatch means |
| Cloud backup on by default: `ha_token`, `ha_refresh_token` and the Frigate password went to the user's Google backup | `android:allowBackup="false"` |
| App-folder exports accumulated forever on shared storage, each holding the logins in the clear | The newest 3 stay, older ones are deleted after each write (`EXPORTS_KEPT`) |
| Import read a picked file with no size cap: a wrong tap on a video could OOM-kill the launcher | `InputStream.readAtMost(MAX_SETTINGS_BYTES)`; the peer path and the file path now share one 4 MB limit |
| A silent connection held the peer accept loop for the full 10 s reply timeout | The handshake uses the 3 s connect timeout; the reply timeout is restored for the payload |

### Deliberately not changed

- **Cleartext for any host.** Hosts are typed in, so no domain config can list them; a `http://` Home
  Assistant sends its token over plain `ws://`. Warning text was considered and left out: it adds a
  row to a focus-driven dialog for information the operator cannot act on.
- **Frigate accepts any certificate before a pin exists.** The first connection from the settings is
  the trust-on-first-use step, and the password goes out with it. Closing that needs a fingerprint
  confirmation on the setup screen, which is a new step in a flow that works.
- **Peer transfer still finds TVs by broadcast.** Any LAN device can appear in the list; the login
  row is what stops it from being worth anything. Real mutual authentication needs an out-of-band
  PIN typed on both TVs.
- **`requestLegacyExternalStorage="true"`.** Ignored at targetSdk 36, but it still governs Android
  10's file browsing, which the "Browse this TV's folders" row depends on.

### Checked and sound

No WebView, no exported providers or receivers, the exported services all carry their `BIND_*`
permissions, no shell string is ever built from input (`ping`, `getprop` get argv), no secret in
tracked files (the keystore lives in `~/.homeboard`, its password in the Keychain), the Home
Assistant token travels in an `Authorization` header and the websocket auth frame never in a query
string, `AppLog` strips `user:pass@` and `token=`/`password=`/`api_key=` parameters, the peer
handshake is ephemeral ECDH P-256 with AES-256-GCM and distinct AAD labels, and the Frigate pin fails
closed on a mismatch.

### Touched files

| File | Change |
|---|---|
| `AndroidManifest.xml` | `android:allowBackup="false"` |
| `ExportImport.kt` | `LOGIN_KEY` internal; `ChecklistDialog` takes `startUnchecked` |
| `PeerTransfer.kt` | Send checklist leaves the login unticked and explains the code; `CodeLine` says what a mismatch means; handshake timeout split from the reply timeout; the size cap moved out |
| `SettingsTransfer.kt` | `MAX_SETTINGS_BYTES` shared, `readAtMost`, capped `readSettings`, `EXPORTS_KEPT` pruning |

### Verified (2026-11-14)

`./gradlew assembleDebug` builds, and the merged manifest at
`app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml` carries
`allowBackup="false"`. Not verified on the Shield: a debug build cannot update the release-signed
install, and uninstalling would take the settings with it. The unticked row, the code line and the
two-TV send still need a device pass after the next release install.

## Publishing (2026-11-14)

`origin` is `bexem/HomeBoard`, private, `fork/m1` as its default branch, with `main` mirroring
`upstream/main`. Two things decided it:

- The README now opens with what this fork is and what it changes, because upstream's README alone
  would have published 40 commits of work under Evo Factory's name. Nothing else in it changed.
- This ledger used to carry the household's real addresses (`the private subnet` for Home Assistant and the
  doorbell). They are `<ha-host>` and `<camera-host>` now. `reports/` carried none.

To make it public it has to become a fork of `fplanque/HomeBoard`, because GitHub will not let a fork
keep its own visibility ("a fork's visibility depends on the upstream repository") and detaches one
that is flipped. So: `gh repo fork fplanque/HomeBoard`, push these same commits, set the default
branch, then delete the private mirror. No released APK either way: this build keeps upstream's
`applicationId` and `versionCode`, so it can never update, or be updated by, an upstream release.

The first version of that summary was wrong in two ways, worth not repeating: it listed repairs of
this fork's own defects as features (focus dead ends, the unreachable vacuum row, the clipped settings
rows) and it left out the settings page, which is the largest thing the fork builds. The README's
"What the fork adds" is now the new surfaces and the shared implementations, "Where it differs from
upstream" is behaviour, and "Fixed upstream" is the one genuine upstream defect — the SDP rewrite that
blacked every camera. Repairs of fork code belong here, named as repairs.

## The calendar can drop what is over (2026-11-15)

Ema: a setting to hide past events even when they are still today's, with a sub-setting for how many
hours after the end they stay — "0 would be for right away, ends at 09:00? At 09:01 you don't see it
anymore."

### Touched files

| File | Change |
|---|---|
| `LauncherPrefs.kt` | `calendar_hide_past` (default off) and `calendar_hide_after` (0, `CALENDAR_HIDE_AFTER` = 0..24), both fed into `CalendarConfig`; new keys, no rename, export untouched |
| `HaCalendar.kt` | `CalendarConfig.hidePast`/`hideAfterHours`; `mergeCalendarEvents` takes `now` (defaulting to the clock) and drops events that are over — past their end, or the end of their day for an all-day one, plus the grace hours. An event with a start and **no end has no moment to pass, so it never hides** |
| `CalendarWidget.kt` | While the setting is on, the card re-reads the clock every 30 s, so an event leaves when it ends instead of waiting for the next Home Assistant refresh; off, nothing ticks |
| `SettingsSchema.kt` | Calendar → Window → **Hide past events**, and under it **Keep them for** (0–24 h), shown only while the first is on |
| `app/src/test/.../CalendarHidePastTest.kt` | Six cases on a fixed clock: ended at 09:59 with 0 h is gone at 10:00; running stays; 90 minutes over survives 2 h of grace and not 1; the setting off keeps everything; today's all-day event stays until its day ends; no end never hides |
| `app/src/test/.../StreamDescriptionTest.kt` | Two tests of `sdpWithoutAudio` deleted with the function: the fork disables a track through the player instead of rewriting the SDP (removed in M8–M15), which had left the whole test source set uncompilable since that commit — found when this feature's test could not run |

### Verified

- `testDebugUnitTest`: **38 tests, 0 failures** (the six new ones included); the test source set
  compiles again after the two obsolete tests went.
- Shield: the two rows appear in Calendar → Window; the sub-setting shows only with the toggle on
  (seen both ways); the stepper moved 0 → 2 → 0 and stored `calendar_hide_after` each time.
- Shield, card with the setting on and grace 0 at 00:15: the two events of today (16:13–17:13) and the
  ones after stay on the card — nothing still in the future is wrongly hidden.
- **Not seen on the device yet: an event leaving on its own.** The check pencilled in for 17:15 that
  day was never recorded, so it stays open. What the TV proves is the wiring (both rows, the
  sub-setting following the toggle, the hours stored, nothing future dropped); what the six unit tests
  prove is the leaving itself. The honest device check is one screenshot either side of an event's end.
- One shape this setting cannot show, and not its fault: an all-day event that spans several days is
  already drawn on its first day only (`HaEvent.from` keeps no end for an all-day event, and the window
  filter starts at `startDay`), so hiding past events changes nothing about it. Teaching the model a
  last day is a calendar change, not a hide-past one.
- Left ON at grace 0, as asked for; one press of the toggle returns the old behaviour.

## The shuffle is called what it is for (2026-11-15)

Ema: "we should rename the shuffle in the settings to something that makes the user understand what it
is doing and why (burn-in avoidance)."

The group was **Shuffle**, which names the mechanism and never the reason. Nothing in it moved things
"for fun": every switch exists so that the same bright pixels — a camera timestamp, an icon, a card
edge — are not in the same place for months, which is what leaves a shadow on a screen.

### Touched files

| File | Change |
|---|---|
| `SettingsSchema.kt` | The group is **Burn-in protection**; the rows say what changes and each description gives the reason: "Change the camera each session", "Change the action buttons' order", "Swap the camera and the widgets", "Change the room cards' order", "A new order after" |

Names only: every storage key (`shuffle_camera` &c.), the `LauncherPrefs` properties, the `Shuffle`
object and the export format are untouched, so an export file still reads and no stored setting moves.

### Verified on the Shield (2026-11-15, 00:30)

Home → Burn-in protection on the TV, all five rows with their new titles and reasons legible, toggles
and the stepper where they were. The rename table above carries the before/after.

## Merged upstream v1.3.0, build 26 (2026-11-18)

Upstream went from build 17 (the fork point) to v1.3.0 with 10 commits; the fork had 48. Merged as one
merge commit — no rebase, because `fork/m1` is pushed and is origin's default. 12 files conflicted (34
hunks); every resolution keeps the fork's behaviour and takes upstream's fix or feature where they
don't compete.

### Upstream in this merge

| Build | What landed |
|---|---|
| 18 | `DefaultHome.kt` (267 lines): ask Android for the home role, else explain why it can't |
| 19 | Screen off timeout: "Starts after" with −/+, or the WRITE_SETTINGS permission that allows it |
| 20 | Peer transfer by typed address + TCP listener (`PeerTransfer.kt` +418), `closeDirect()` on dismiss |
| 21 | `LagWatch.kt`: "Lag: x s" over a camera behind live |
| 22 | `Frigate`: accept system-trusted certificates, pin changes in `FrigateCertificateDialog` |
| 23 | Export/import checklists say which Home Assistant and Frigate account they carry |
| 24 | Clips tab (Alerts/Detections/Saved, `SubTabRow`, `ScrollIndicators`), padded clips, player errors, camera GPU/hardware pipeline + `DebugOverlay`, `ScreenState` hold, background Gray/Black, pre-setup outline |
| 25 | Frigate `pre_capture`/`post_capture` re-read each launcher show (`FrigateCapture`) |
| 26 | Version 1.3.0, build 26 |

### Where upstream's features live in the fork now

The fork's settings page replaced upstream's launcher dialog, so three dialog-era features were
re-homed as schema rows rather than lost:

- **Default home app** — `SettingAction.OpenDefaultHome` → `DefaultHomeOptionDialog` (wraps upstream's
  `DefaultHomeOption` in a `TopDialog`), Launcher › Home button.
- **Pure black background** — toggle on `LauncherPrefs::blackBackground`, shown when the fork's
  background kind is None; the black `Surface` colours under the fork's `LauncherBackgroundLayer`.
- **Debug info** — toggle on `LauncherPrefs::showDebugInfo`, Advanced › Debug; `DebugOverlay.shown` is
  synced in `onSettingsChanged` next to `blackBackground`, so the camera labels appear without a restart.
- **Screensaver "Starts after"** — upstream's `ScreensaverDelay` composable added inside the fork's
  `ScreensaverStatusDialog` (fork imports trimmed of the duplicated `FocusRequester`); the page's
  `screensaver_status` row was already the door.

### Conflicted files and how each was resolved

| File | Resolution |
|---|---|
| `LauncherPrefs.kt` | Fork's property block kept whole (incl. its own `showFreeMemory` setter); upstream's new keys (`showDebugInfo`, `homeOverrideAccepted`, `replaceHomeAutoOff`, `blackBackground`) arrived in clean regions |
| `SettingsButton.kt` | Fork's single cog: `onOpenSettings` only; the old context menu stays gone, debug info moved to the schema |
| `ExportImport.kt` | Upstream's "Account: none"/"Account: admin" subtitle + fork's Settings section (one line per set) side by side |
| `Frigate.kt` | Upstream's `FrigateCapture` data class; fork's `normalizeUrl` one-liner stays |
| `PeerTransfer.kt` | Fork's anti-MITM code wording; upstream's `closeDirect()` added to the fork's dismiss callback, fork's login-tick hint kept |
| `CameraView.kt` | Fork's HA-source guard (no RTSP source for HA cameras) + upstream's TextureView/SurfaceView choice; log line carries fork's redaction, audio state and upstream's pipeline name |
| `Cameras.kt` | `CameraGrid` takes `soundEnabled`, `ha`, `maxHardware`; the HA still branch kept, the streaming branch gained upstream's `key(hardware)` + `gpu`; `CameraAdvancedSettings` keeps the fork's SwitchRow (with upstream's `minimizeModifier`); the screensaver tab stays deleted from the cameras dialog |
| `HomeAssistantTab.kt` | Fork's scrolling `LazyRow` views row (upstream's `SubTabRow` is a plain Row and can't follow the remote); `ViewRowLift`/`ViewRowHeight` restored from base since upstream deleted them |
| `HomeAssistantDialog.kt` | Fork's hints and `HaDialogTab`; `ChoiceRow` takes upstream's `internal` + `switch` signature and regains the base switch branch, so Frigate's Clips toggles render as switches |
| `Screensaver.kt` | Fork's status dialog only; upstream's 184-line dialog body dropped (settings are page rows), `withFrameNanos` import kept for the delay row |
| `LauncherSettingsDialog.kt` | Fork's `HomeHelperWarning` and `HomeOverrideConsentDialog`; the old dialog is not resurrected |
| `MainActivity.kt` | Fork's state block + upstream's `dismissedCertificate`; black `Surface` + fork background layer + fork phone-app filter; upstream's pre-setup outline placed inside the fork's `panelSlot` (third branch), fork's camera-shape math; fork's badge wiring and free-memory state (`showFreeMemory` restored); single-cog `SettingsButton`; `shownTabs` also folds upstream's "all Clips lists off"; cert dialog and notification-grant dialog both hosted |

### Deliberate designs kept

- The settings page (schema-driven) is the only settings surface; the cog has one callback.
- Apps-first Home row, watch-next setting, infinite scroll, calendar card, HA still cameras: untouched.
- The export format is still v3; the merge did not touch `SettingsFile`'s fields, only the checklist's
  section list.
- `SubTabRow` stays for the Clips tab; the Assistant tab keeps its scrolling row.

### Verified

- `./gradlew test assembleDebug`: BUILD SUCCESSFUL; 41 unit tests in 5 classes, 0 failures
  (including upstream's new `CertificateNamesTest`).
- On the Shield (build 26 installed over 17, no downgrade): Home tab (camera + calendar + apps row,
  no events/watch-next row), Clips tab with Alerts/Detections/Saved and live clips, clip playback,
  settings page categories, Default home app dialog ("HomeBoard is the default home app"), Debug info
  toggle, screensaver "Starts after 15 min" row with the ADB fallback, Frigate dialog (trusted
  certificate SHA-256, Frigate 0.18.2, Clips switches), export checklist ("Account: admin" + Settings
  58, App tiles 25), export file written (version 3, 9415 bytes), import of that file parsed and
  applied. No FATAL/AndroidRuntime errors in logcat across the session.
