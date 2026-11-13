# UX review — HomeBoard fork, 2026-11-13

Read-only review of the whole app from the outside in: every settings row, dialog title, hint and
empty state checked against the code that implements it. File:line references are to
`app/src/main/java/com/evofactory/homeboard/`. "Design decision" means the fix needs Ema to choose
between options; "mechanical" means there is one obvious correction.

---

## 1. Does the tin say what it does? — wording vs behaviour

Ranked by how wrong the promise is.

### W1. The Camera list row still advertises the screensaver — SettingsSchema.kt:567
- **Current:** "Camera list · The addresses, how many play at once, and the screensaver".
- **Actually:** the screensaver left the camera dialog in M12 (`ScreensaverDialog`, opened from
  Launcher → *How it plays*). The camera dialog holds addresses, order, shape, Test, and an Advanced
  tab with the two grid switches.
- **Fix:** "The addresses, their order and shape, and how they play in the grid." Mechanical.

### W2. "Per line" describes the opposite of what the stepper sets — SettingsSchema.kt:388-389
- **Current:** title "Per line", description "How many tiles share a line: rows when the page
  scrolls sideways, columns when it scrolls up and down".
- **Actually:** the value is the **number of lines** — `LazyHorizontalGrid(rows = Fixed(lines))` /
  `LazyVerticalGrid(columns = Fixed(lines))` (AllAppsPage.kt:193, 203; LauncherPrefs.kt:494-497 says
  it correctly: "How many lines the All apps page shows at once"). Raising the value adds lines and
  shrinks tiles; "tiles per line" is not what it controls (in vertical mode a column's length is
  unbounded — the page scrolls).
- **Fix:** title "Lines", description "Rows when the page scrolls sideways, columns when it scrolls
  up and down; more lines, smaller tiles." Mechanical.

### W3. "Channel keys switch cameras" — only on two tabs, and not always there — SettingsSchema.kt:614-615
- **Current:** "Channel up and down go to the next or previous camera; off leaves them to the TV".
- **Actually:** on the Alerts and Assistant tabs the keys are always left to the TV
  (MainActivity.kt:468 `if (tab != HomeTab.Home && tab != HomeTab.Cameras) return false`), and on
  Home they silently do nothing while "Shuffle the camera" is on (see H3: the displayed camera is
  `sessionCams.first()` whatever `selectedCamera` says, MainActivity.kt:296-299). The doc comment at
  LauncherPrefs.kt:288-292 ("Channel up and down always switch at once, whichever this is") is wrong
  for the same reason.
- **Fix:** "…go to the next or previous camera on the Home and Cameras tabs; elsewhere they are left
  to the TV" — plus the H3 fix or a note in the shuffle row. Mechanical once H3 is decided.

### W4. Two rows, one door: "Access" and "Notifications panel" — SettingsSchema.kt:724-739
- **Current:** Advanced → Notifications has "Access · Android only shares the TV's notifications
  with an app that is allowed to read them" and "Notifications panel · What Android is showing,
  listed here…".
- **Actually:** both map to `SettingAction.OpenNotificationAccess`, and MainActivity.kt:1071 maps
  that to `notificationsOpen = true` — the same panel. When access is already granted, the "Access"
  row opens a list of notifications, which is not what its title promises; when it is off, both rows
  lead to the same instruction screen.
- **Fix:** see H5. Design decision (which row survives).

### W5. "Home Assistant offline · retrying" never shows when the Sensors card is off — HomeAssistantPanel.kt:398, 442-444
- **Current:** the panel's status line is composed after `if (!showSensors) return@Column` (line 398).
- **Actually:** with "Sensors card" off (the calendar takes the space), the only offline signal is
  the calendar dimming to 60% alpha (CalendarWidget.kt:71). The words never appear, so an outage
  reads as "stale data" rather than "offline".
- **Fix:** move the status line above the `return@Column` (or into the expanded branch). Mechanical.

### W6. Same two switches, two different names — Cameras.kt:765-778 vs SettingsSchema.kt:628-641
- **Current:** the settings page says "Live pictures in the grid" and "One stream at a time"; the
  camera dialog's Advanced tab says "Show substreams on the Cameras tab" and "Minimize simultaneous
  video streams" for the same two prefs (`camera_grid_live`, `minimize_video_streams`).
- **Actually:** both editors write the same values live, so nothing breaks — but M11's coherence
  pass renamed the page rows and left the dialog behind, so a user meeting both has to guess they
  are the same setting.
- **Fix:** use the page's names (and one-line descriptions) in the dialog too. Mechanical.

### W7. A light's ◂ ▸ hint hides while the light is off, but the keys still act — HomeAssistantTab.kt:951-957, 981-987
- **Current:** the row intercepts Left/Right whenever the entity has a level (`steppable`), but the
  "N% ◂ ▸" hint is drawn only `if (steppable && on)`.
- **Actually:** Home Assistant keeps a `brightness` attribute on many lights while off, so pressing
  Left on an off light calls `setBrightness(…)` — the light comes on at (level − 10)% with no hint
  on screen that the key would do anything. A surprise action with no affordance.
- **Fix:** show the hint whenever `steppable` ("Off · ◂ ▸ sets the level"), or only step while on
  and let Left/Right move focus. Small design decision.

### W8. Dashboard row summary shows the raw key — SettingsPage.kt:586-593
- **Current:** "What it shows · lovelace-config".
- **Actually:** the dialog lists dashboards by title ("Config"); the row prints the storage key.
- **Fix:** carry the title (the connection has it once read; otherwise show the key only as a
  fallback). Mechanical polish.

### W9. "Left and right set the order" when there is nothing to reorder — HaCamerasDialog.kt:261, CalendarPickerDialog.kt:91
- **Current:** with exactly one camera/calendar chosen, the subtitle still promises left/right
  ordering.
- **Actually:** `move()` needs two items; the keys do nothing.
- **Fix:** "One calendar. OK chooses more." Mechanical.

---

## 2. Is it fully featured? — half-implemented settings and features

Ranked by user impact.

### H1. The camera's shape is never remembered — and the manual choice is silently undone
- **What exists:** each camera's aspect is written to the cameras JSON (Cameras.kt:131-134,
  `put("aspect", …)`), the camera menu offers "Shape: Auto / 16:9 / 4:3 / …" (Cameras.kt:696-701),
  and the Home tab sizes its box from it (Cameras.kt:180).
- **What's broken, twice:**
  1. `camerasFromJson` (Cameras.kt:138-155) never reads `"aspect"` back — every camera starts at
     `aspect = 0` on every launch, so the "remembered for the next time" the ledger describes does
     not happen: the Home box is 16:9 until the first frame, and a **manual Shape choice is lost on
     restart**. An export → import round trip loses it too (`settingsFromJson` uses the same reader,
     ExportImport.kt:157).
  2. MainActivity.kt:590-599 overwrites *any* stored aspect with the stream's report as soon as a
     frame arrives (`if (abs(reported - known) > 0.01f) saveCameras(…)`), so even without a restart
     a manual "Shape: 16:9" on a 4:3 camera reverts the next time the player starts. The ledger's
     "choosing Auto lets the stream set it again" implies non-Auto should stick — it doesn't.
- **Smallest complete version:** read `"aspect"` in `camerasFromJson`, and only learn from the
  stream when the stored value is 0 (Auto). Mechanical.

### H2. All four Shuffle toggles do nothing until a restart
- **What exists:** Home → Shuffle has four switches and a stepper (SettingsSchema.kt:286-326).
- **What's broken:** the settings page calls `onSettingsChanged` on every write
  (SettingsPage.kt:161), but MainActivity's re-read block (MainActivity.kt:1086-1118) updates every
  rendered pref **except** `shuffleCamera/shuffleActions/shuffleCards/shuffleSides` — those fields
  are set only in `onCreate` (240-243). Flipping "Shuffle the camera" moves the switch and writes
  the pref; the camera, the top bar and the cards stay exactly as they were until the process
  restarts. `applyImport` (1185-1216) misses the same four, so an imported shuffle setup also waits
  for a restart.
- **Smallest complete version:** four more lines in each re-read block (the seed is already rolled,
  so applying now works). Mechanical. If "next session only" is the intended semantics, the rows
  must say so — they currently don't.

### H3. "Shuffle the camera" silently kills camera switching on Home, and "Camera on Home"
- **What exists:** with shuffle on and 2+ cameras, `camera` is always `sessionCams.first()`
  (MainActivity.kt:296-299). CH keys and number buttons still call `selectCamera` (473, 484), which
  writes a selection the Home tab then ignores — the press does nothing visible.
- **Also:** "Camera on Home" (SettingsSchema.kt:592-600) changes nothing while shuffle is on:
  `applyHomeCameraStart()` (MainActivity.kt:1286-1298) sets `selectedCamera`, which the display
  ignores. Neither row hides or mentions the other, so two settings both claim the same job and one
  quietly wins.
- **Smallest complete version:** either a manual switch wins for the rest of the session (compare
  against a "shuffled pick" id and fall back to `selectedCamera` after the first manual switch), or
  `showIf` "Camera on Home" off while `shuffle_camera` is on and say so in both descriptions.
  **Design decision.**

### H4. Changing the Home Assistant address changes nothing that is running
- **What exists:** Home Assistant → Address (HaAddressDialog.kt:297-304) saves the new URL, clears
  the tokens, and says "Changing it ends the saved login".
- **What's broken:** the dialog writes prefs directly; `MainActivity.haServer` — the state the
  connection, the panel and even the *Log in* dialog read (`initialServer = haServer`,
  MainActivity.kt:878) — still holds the old URL and the old refresh token. Until a restart the
  panel keeps talking to the old server while the settings rows already read "Not signed in", and
  *Log in* opens pre-filled with the old address and a login that no longer exists in prefs.
- **Smallest complete version:** give the dialog a callback that does what the big dialog's Save
  does for the server part (`haServer = HaServer(newUrl); settingsRevision++`). Mechanical.

### H5. The Notifications "Access" row is a second door to the panel
- **What exists / missing:** see W4. The panel itself is complete (list, Clear all, per-app open,
  instructions when access is off — NotificationsDialog.kt:233-263, 334-357). The Access row adds
  only its "Allowed / Not allowed" summary; OK on it opens the same screen as its neighbour.
- **Smallest complete version:** one row — "Notifications panel · Allowed · 8 showing" — whose
  dialog states the grant status at the top; or keep both and make Access open a status screen even
  when granted. **Design decision** (which shape).

### H6. The Assistant tab's alert strip is gated by a setting that lives in Home
- **What exists:** on the Assistant tab the strip (and the No-recent-alerts tile) shows only when
  **both** `ha_bottom_row` (Assistant → "Alerts and apps below") **and** `home_alerts_row`
  (Home → Rows → "Alert strip") are on (MainActivity.kt:390-397: `alertsRowAllowed = tab ==
  HomeTab.Cameras || homeAlertsRow`).
- **What's missing:** neither row mentions the other. Someone who turns "Alerts and apps below" on
  and sees no strip has no pointer to the Home setting that is blocking it.
- **Smallest complete version:** on the Assistant tab let `ha_bottom_row` be the only gate (one
  condition change), or name the dependency in `ha_bottom_row`'s description. Small design decision.

### H7. A pinned app cannot get a custom icon from the row
- **What exists:** the row's tile menu offers "Pin to favourites" **or** "Set icon"/"Reset icon" —
  they sit in opposite branches of one if/else (AppRow.kt:378-386). Once an app is pinned its menu
  shows only "Unpin from favourites"; to change its icon you must unpin it, or go through All apps,
  whose menu offers Set icon for every app (AllAppsPage.kt:234-236).
- **Smallest complete version:** move the icon entries out of the favourites branch so both menus
  agree. Mechanical.

### H8. M4 leftovers: the vacuum stops at three services, and there is no generic service tile
- **What exists:** `VacuumRow` offers Start / Pause / Return to dock, chosen by state
  (HomeAssistantTab.kt:1008-1012). The ledger itself lists room cleaning and the generic service
  tile as "still to build" — verified: neither exists anywhere.
- **Smallest complete version:** read `rooms` from the vacuum's attributes and add "Clean <room>"
  entries to the same ContextMenu; the service tile needs a domain/service picker, which is a bigger
  job. **Design decision** (which services to expose).

### H9. Dead weight the M13/M14 reworks left behind
- `HelperStatusRow` and `BuildInfo` (SettingsMenuDialog.kt:113-212) have no callers — the ledger
  says the helper block was deleted; these are its remains.
- `SettingsPage` takes `onOpenAccessibility` (SettingsPage.kt:73, 363) and never uses it.
- `rowSummary`'s `"home_assistant"` branch (SettingsPage.kt:584) matches a key no row has had since
  M13 split it into Address / Log in / Log out.
- **Fix:** delete all three. Mechanical.

---

## 3. What would make it better? — concrete improvements, ranked by value/effort

1. **Make the helper warning open the helper, not the settings page** — MainActivity.kt:521-525.
   The top-bar warning ("Helper not running") sets `settingsOpen = true`; the page then opens on
   the Home category (SettingsPage.kt:75) and the helper row is in Launcher, two rail moves away.
   `homeHelperOpen = true` opens the explanation with its two buttons directly. One line.
2. **Show units on stepper values** — SettingsPage.kt:521-528. Rows render bare numbers ("3",
   "30", "65", "0"); "Days ahead · 3" survives, "Refresh every · 30" and "Darkness over it · 65"
   make the reader recall the description. "3 days", "30 min", "65 %", "0 (once per session)".
3. **Let a search hit take you to its category** — SettingsPage.kt:734-768. Results act on the row
   (toggles work in place, doors open), but the section label beside a hit is text only: there is no
   way to land on the setting's card among its siblings. A "Show in <category>" entry on the hit's
   long-press menu (or OK-and-hold) is the smallest version.
4. **Say when a notification cannot be opened** — Notifications.kt:73-77 logs "no content intent"
   and returns false; the dialog then closes only on success (NotificationsDialog.kt:258), so OK
   looks like a dead key. A toast ("This one doesn't open anything") turns a silent no-op into an
   answer.
5. **Rename the camera editor's "Advanced" tab to "Transport"** — CameraDialog.kt:89-92. Two
   different "Advanced" tabs now exist in nested dialogs (the per-camera one holds only TCP/UDP;
   the list's holds the grid switches). The content already has a "Transport" heading; the tab
   should match.
6. **Remember the last settings category** — SettingsPage.kt:75 starts on Home every time the cog
   is pressed; hoist `section` to MainActivity state so a cameras-and-alerts evening stops
   re-navigating.
7. **Uninstall from the All apps page** — AllAppsPage.kt:232-240 has Pin/Hide/Info but no Uninstall
   (the row's menu has it, AppRow.kt:388-395). Same intent, one more entry.
8. **Search field placeholders that say what they search** — HomeAssistantDialog.kt:644 is
   "Search" in all three pickers; "Search devices" costs nothing.
9. **Notifications row summary could separate the dismissible** — SettingsPage.kt:602 shows
   "8 showing"; Clear all can only remove the clearable ones. "8 showing · 3 can be cleared" sets
   the expectation before the press.
10. **After Unhide on the All apps page, follow the app** — AllAppsPage.kt:91-95 re-focuses
    `shown.firstOrNull()` after any change; the unhidden app is appended at the end of the order, so
    the cursor jumps to the far end of the page. Focus the row that was acted on instead.

---

## Ledger vs code

Three places where FORK.md's claim does not match what the code does now:

- **"Each camera remembers the ratio its stream reports… remembered for the next time"** — not
  true: `camerasFromJson` drops `aspect` (H1). The device checks were done within one process.
- **M7.2 shuffle** — verified with restarts ("two starts 20 s apart"); the toggles' live behaviour
  was never checked, and it does nothing (H2).
- **M14: "the four-line block at the bottom of Advanced is gone, and with it the special-cased
  composable"** — the block is gone from the UI, but `HelperStatusRow` (and `BuildInfo`) are still
  in the tree, unreferenced (H9).

Everything else I checked from the ledger held up: consent gating, the badge/dot lifecycle
(opening the Alerts tab clears both, MainActivity.kt:364-368), "Mark the alerts as reviewed"
(1072-1078), `showIf` filtering including the background rows, one-editor-per-setting for the HA
dialog parts (Save writes only the two dashboards, HomeAssistantDialog.kt:479), the door summaries
("6 cameras", "5 beside the camera", "Signed in"), search reaching its first hit, and the
notifications panel's grant screen.
