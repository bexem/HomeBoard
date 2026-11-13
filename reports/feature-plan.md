# Feature-completion plan — HomeBoard fork (`fork/m1`)

Inputs: `reports/consolidation-audit.md` (findings #1–#12), `reports/ux-review.md` (W1–W9, H1–H9,
improvements 1–10), `FORK.md` M1–M15, verified against the code on 2026-11-13. Every line reference
below was checked in the tree at `9137fd4`.

Scope: **finishing half-implemented features only.** The consolidations themselves (audit #1–#8) are a
separate task; this plan sequences around them. H9 (dead `HelperStatusRow`/`BuildInfo`/
`OpenHomeAssistant`) is audit #3's work, not repeated here.

Standing rules for every item (from AGENTS.md/FORK.md, not restated per item):

- Build with `./gradlew assembleDebug`, install on the Shield (`adb connect <ip>:5555 &&
  adb install -r …`), verify **on the device with the remote**, and say what you saw.
- Never uninstall/wipe/change launcher defaults on the Shield without approval in the same turn.
- One FORK.md section per milestone built, listing files touched and designs to keep.
- No storage-key changes, no export-format changes (v3 stays readable), no edits to the SDP, no
  touching `restartApp()`'s kill-process path, no splitting `HomeAssistantDialog`. The
  "mini cameras / alerts column" idea stays dead (FORK.md "Not doing").

## Depends on consolidations

| Item | Feature | Waits for (audit finding) | Why |
|---|---|---|---|
| F1 | Camera shape remembered (H1) | — | touches `Cameras.kt` only |
| F2 | Shuffle applies without restart (H2) | — | `MainActivity` re-read blocks only |
| F3 | Manual camera pick beats shuffle (H3, W3) | — | build after F2 (same state) |
| F4 | HA address change takes effect (H4) | — | audit #6 keeps `normalizeHaUrl` as a delegate, so order does not matter |
| F5 | One notifications door (H5, W4) | — | schema + summary only |
| F6 | Assistant strip not gated by a Home setting (H6) | — | one condition + wording |
| F7 | Set icon on pinned apps (H7) | — | `AppRow` menu only |
| F8 | Vacuum room cleaning (H8a) | — | `VacuumRow` + one session call |
| F9 | Generic service tile (H8b) | **#2 + #8** (shared `EntityPickerDialog`) | its picker should be the shared one, not a fourth copy |
| F10 | Wording/status batch (W1, W2, W5, W6, W7, W8) | — | text and one status line |
| F11 | Picker "one item" subtitle (W9) | **#2 + #8** | belongs in the shared picker, not patched into three dialogs |

Recommended order: **F1, F2, F4, F5, F6, F7, F10** (all mechanical, independent — any sequence),
then **F3** (builds on F2's re-read), then **F8** (starts with a device discovery step), and after
the consolidation pass lands: **F11, F9**.

---

## F1 — The camera's shape is remembered, and a manual choice sticks (H1)

Two bugs, one feature:

1. `camerasToJson` writes `"aspect"` (`Cameras.kt:133`) but `camerasFromJson` (`Cameras.kt:138-155`)
   never reads it back — every camera restarts at `aspect = 0`, so the Home box is 16:9 until the
   first frame and a manual "Shape: 16:9" is lost on restart and on export→import
   (`ExportImport.kt` reads through the same `camerasFromJson`).
2. `MainActivity.kt:590-599` (`onAspect` in `MainCamera`) overwrites any stored aspect with the
   stream's report, so a manual shape on a camera whose stream reports differently reverts as soon
   as the player starts. FORK.md's "choosing Auto lets the stream set it again" implies non-Auto
   sticks — it doesn't.

**Change**

- `Cameras.kt` `camerasFromJson`: add a named argument to the `Camera(...)` constructor call —
  `aspect = it.optDouble("aspect", 0.0).toFloat()` (`aspect` is the last parameter, default `0f`;
  the existing positional arguments stay as they are).
- `MainActivity.kt:590-599`: learn from the stream only when the stored value is Auto —
  replace the condition with `if (known <= 0f && reported > 0f)`. The Shape menu
  (`Cameras.kt:696-701`, `nextAspect` cycles through 0) already writes 0 for Auto, so choosing
  Auto re-enables learning, exactly as the ledger describes.

**Check (device):** Camera list → doorbell's menu → "Shape: 16:9". Force-stop the launcher
(`adb shell am force-stop com.evofactory.homeboard` — safe, no data touched) and reopen: the Home
box stays 16:9, not the stream's 4:3. Then choose "Shape: Auto", restart: the box returns to the
learned 4:3 within a second of the first frame. Export settings, restart, import: the shape survives
the round trip.

**FORK.md:** the M-camera-shape section's claim becomes true; note the fix there.

## F2 — The four Shuffle switches apply without a restart (H2)

`SettingsPage` calls `onSettingsChanged` on every write, but MainActivity's re-read block
(`MainActivity.kt:1086-1118`) updates every rendered pref except `shuffleCamera`, `shuffleActions`,
`shuffleCards`, `shuffleSides` — those four are set only in `onCreate` (`MainActivity.kt:240-243`).
`applyImport` (`MainActivity.kt:1185-1216`) misses the same four, so an imported shuffle setup also
waits for a restart. The seed is already rolled (`shuffleSeed`, line 247), so applying now works.

**Change**

- `MainActivity.kt` `onSettingsChanged` block: add the four assignments after line 1105
  (`cameraSide = prefs.cameraSide`): `shuffleCamera/shuffleActions/shuffleCards/shuffleSides =
  prefs.…`. The `remember` keys at lines 290-294 (`shuffleSeed, shuffleSides`, `shuffleSeed,
  shuffleCamera`) recompute automatically because they key on these states.
- Same four lines in `applyImport` (after line 1206).

Semantics note: turning a shuffle switch on mid-session applies to *this* session's already-rolled
seed (no re-roll). The rows already read "each session", and mid-session application is a superset of
the promised behaviour, so no wording change is needed.

**Check (device):** with two+ cameras configured, toggle Home → Shuffle → "Shuffle the camera" on:
the Home camera changes at once (no restart). Toggle "Swap the camera and widgets": the Home tab
swaps sides at once. Toggle each off again to leave Ema's prefs as they were (`shuffle_*` are all
false today — put them back).

## F3 — A manual camera pick beats the shuffle, for the rest of the session (H3, W3)

With shuffle on, `camera` is always `sessionCams.first()` (`MainActivity.kt:296-299`), so CH keys
and number buttons call `selectCamera` (lines 473, 484) which writes a selection the display ignores
— the press does nothing. "Camera on Home" is equally dead while shuffle is on. Both `channel_keys`
and per-session shuffle are ledger-deliberate (M2, M7.2); the fix makes them compose instead of one
silently winning.

**Change**

- `MainActivity.kt`: add `private var cameraChosenManually by mutableStateOf(false)` beside
  `selectedCamera`. In `selectCamera()` (line 1300-1303) set it `true`.
- Display (lines 296-299): `val camera = if (shuffleCamera && !cameraChosenManually)
  sessionCams.firstOrNull()?.takeIf { cams.size > 1 } ?: cams.find { it.id == selectedCamera } ?:
  cams.firstOrNull() else …` — i.e. the shuffled pick is the session *default*; any explicit pick
  (CH up/down, number buttons) wins until the process restarts. The seed only rolls at start
  (`Shuffle.rollIfDue`, line 247 — there is no live re-roll timer), so "rest of the session" holds.
- Reset it when shuffle is (re)enabled, so F2's live apply shows the shuffled camera: in the
  `onSettingsChanged` block, after setting `shuffleCamera`, add
  `if (prefs.shuffleCamera && !shuffleCamera) cameraChosenManually = false` (compare before
  assignment), and the same guard in `applyImport`.
- `SettingsSchema.kt` `home_camera_start` row (~line 592): add `showIf = { !it.shuffleCamera }` —
  the row means nothing while the shuffle owns the camera (same precedent as M10 hiding indicators
  that cannot show). `shuffle_camera`'s description gains: "…; picking a camera with the channel
  keys or the number buttons keeps your choice until the TV's next session."
- W3 wording, `channel_keys` row (`SettingsSchema.kt:614-615`): "Channel up and down go to the next
  or previous camera on the Home and Cameras tabs; elsewhere they are left to the TV." Fix the
  matching wrong doc comment at `LauncherPrefs.kt:288-292` the same way.

**Check (device):** shuffle camera on → Home shows the session's camera; CH up switches to the next
one and it *stays* after further navigation; "Camera on Home" row is hidden while shuffle is on;
toggle shuffle off → the row reappears and "Last switched to" shows the camera you picked.

## F4 — Changing the Home Assistant address changes what is running (H4)

`HaAddressDialog` (`HaAddressDialog.kt:34-41`) writes `prefs.haUrl` and clears the tokens, but
`MainActivity.haServer` — the state the connection, the panel and the *Log in* dialog read
(`initialServer = haServer`, line 878) — still holds the old URL and old refresh token. Until a
restart the panel talks to the old server while the rows already read "Not signed in".

**Change**

- `HaAddressDialog.kt`: add `onSaved: (String) -> Unit` parameter; call it with the normalised
  address in both the Save button and `onImeAction` (extract one local `save()` lambda so the two
  cannot drift).
- `MainActivity.kt` where the dialog is opened: pass
  `onSaved = { address -> if (haServer.refreshToken.isNotEmpty()) revokeHaLogin(haServer);
  haServer = HaServer(address); settingsRevision++ }` — the same server-half of the big dialog's
  `onSave` (lines 889-902). The revoke ends the old address's login in Home Assistant's own profile,
  matching what the dialog's "Changing it ends the saved login" already promises.

**Check (device):** set the address to the same server re-typed differently (e.g. add the port
explicitly), Save: the panel reconnects without an app restart, and *Log in* opens pre-filled with
the new address and no login. (Do not point it at a wrong address on the live TV; a same-server
retype exercises the whole path.)

## F5 — One door for notifications (H5, W4)

Both Advanced → Notifications rows ("Access", "Notifications panel") map to
`SettingAction.OpenNotificationAccess` → `notificationsOpen = true` (`MainActivity.kt:1071`) — the
same panel. When access is granted, "Access" opens a list of notifications, which its title does not
promise.

**Change**

- `SettingsSchema.kt`: delete the `notifications_access` action row (~line 724); keep `notifications`.
  Extend its description to carry the access promise: "What Android is showing, listed here; it
  needs notification access, and the panel says how to grant it" (already nearly does — keep one
  clear sentence).
- `SettingsPage.kt` `rowSummary` (lines 602-603): drop the `notifications_access` branch; make
  `"notifications"` read `if (Notifications.granted || Notifications.allowed)
  "${Notifications.entries.size} showing" else "Not allowed"` — the surviving row now states the
  grant before you press it, which is all the Access row added.
- `NotificationsDialog` already explains the grant when access is off (M9) — no change there.

**Check (device):** Advanced → Notifications shows one row reading "8 showing" (access is
currently granted on this Shield); OK opens the panel. (Optional, with approval: revoke with
`adb shell cmd notification disallow_listener …` and check the row reads "Not allowed" and the panel
shows the instructions — then re-grant. If not approved, verify the not-allowed branch by reading
the code path, not the device.)

## F6 — The Assistant tab's strip is not gated by a Home setting (H6)

On the Assistant tab the strip shows only when **both** `ha_bottom_row` (Assistant → "Alerts and
apps below") and `home_alerts_row` (Home → Rows → "Alert strip") are on:
`MainActivity.kt:390-391` — `alertsRowAllowed = tab == HomeTab.Cameras || homeAlertsRow`. Neither
row mentions the other. The Home switch is deliberately about the Home tab (M2: "the Frigate events
row … now need homeAlertsRow on the Home tab; Cameras keeps them"), so the Assistant tab's own
switch should be its only gate, the same way Cameras is exempt.

**Change**

- `MainActivity.kt:390`: `val alertsRowAllowed = tab == HomeTab.Cameras || tab == HomeTab.Assistant
  || homeAlertsRow` (line 397's `showNoAlertsTile` follows the same value, unchanged).
- `SettingsSchema.kt` `home_alerts_row` (~line 328): make sure the description says the strip is on
  the **Home tab** ("Cameras always shows it; the Assistant tab has its own switch") — check the
  current text and adjust only if it does not.

**Check (device):** with `home_alerts_row` off and `ha_bottom_row` on, the Assistant tab shows the
alert strip; turning "Alerts and apps below" off removes it. Leave both prefs as found
(`ha_bottom_row=false` today).

## F7 — A pinned app can get a custom icon from the row (H7)

The row's tile menu puts "Set icon"/"Reset icon" inside the *unpinned* branch
(`AppRow.kt:378-386`): once pinned, the menu shows only "Unpin from favourites". The All apps page
offers Set icon for every app (`AllAppsPage.kt:234-236`); the two menus disagree.

**Change**

- `AppRow.kt`: move the `if (appIconOverrides.containsKey(app.key)) add(MenuAction("Reset icon")…)`
  and `add(MenuAction("Set icon")…)` lines (382-385) out of the `else`, so both branches offer
  Pin/Unpin **and** Set/Reset icon. Pinned menu becomes: Unpin, (Reset icon), Set icon, Info,
  (Uninstall), Show hidden.

**Check (device):** pin an app, open its tile menu on the Home row: "Set icon" is there; pick a
picture (SAF picker), the tile changes on the row *and* on the All apps page; "Reset icon" returns
the app's own art; unpin still works.

## F8 — Vacuum room cleaning (H8a, M4 leftover)

`VacuumRow` (`HomeAssistantTab.kt:1000-1048`) offers Start / Pause / Return to dock only. The ledger
lists "vacuum room cleaning from the entity's `rooms` attribute" as still to build. Yiko is the
Dreame robot; the exact wire format must be read from the live entity, not guessed.

**Change**

1. *Discovery first (device/HA, no code):* read the live state of the vacuum entities
   (HA Developer Tools → States, or `adb logcat` after adding a temporary log) and record the exact
   shape of the `rooms` attribute (list of names, or list of `{id, name}`) and `supported_features`
   (the SEND_COMMAND bit). If `rooms` is absent on both robots, **stop and report**: the integration
   does not expose rooms and this item closes as not available on this server.
2. `HomeAssistant.kt` `HaSession`: add `suspend fun vacuumRoom(entityId: String, roomId: String)`
   calling the service the integration documents — for Dreame integrations this is typically
   `vacuum.send_command` with `command: "set_room_clean"` (adjust to what step 1 shows; keep the
   existing private `callService` pattern).
3. `HomeAssistantTab.kt` `VacuumRow`: read `rooms` from `entity.attributes`; when non-empty, add one
   `MenuAction("Clean a room")` that opens a second `ContextMenu` (title "Clean which room?")
   listing the rooms; each entry calls `connection.vacuumRoom(...)` and closes both menus.

**Check (device):** Yiko's row menu offers "Clean a room" and lists the real rooms; picking one
starts the room clean (confirm in HA's Logbook: the service call, and the robot leaves its dock —
warn Ema first, this moves a real robot). Baka's row behaves per its own attributes.

## F9 — The generic service tile (H8b, M4 leftover) — after consolidations #2 + #8

A tile that runs any Home Assistant service (`automation.reload`, `script.foo` is already covered by
scripts — this is for domain services with no entity). Build it on the shared `EntityPickerDialog`
from audit #2 so it is not a fourth picker.

**Change**

- `HomeAssistant.kt` `HaSession`: `suspend fun services(): Map<String, List<String>>` via the
  websocket command `get_services` (shape: `{domain: {service: {name, description}}}` — verify
  against the live server with the same discovery approach as F8). Generalise the private
  `callService` so a no-entity variant exists: `suspend fun runService(domain: String, service:
  String)` (same timeout/no-answer drop semantics).
- `LauncherPrefs.kt`: `haServices` (`ha_services`, a `readList`/`writeList` string list, default
  empty).
- `SettingsSchema.kt`: Assistant section, "Custom services" LIST row — `SettingKind.List` with the
  property, so it exports/imports like `favourite_apps` (no format change; a new key).
- New `HaServicesDialog.kt`: the shared picker over `domain.service` entries (label the domain, the
  service name under it), plus a delete path (the calendar "Leave it out" model).
- `HomeAssistantTab.kt` / wherever the Assistant tab composes action rows: render each chosen
  service as an `ActionButton` that calls `runService`; group them under a "Services" card heading
  only when non-empty (empty list = nothing new on screen, exactly as today).

**Check (device):** add a harmless service (`input_number.reload`) via the new row; it appears on
the Assistant tab; OK runs it (HA Logbook shows the call; nothing in the house changes). Export →
import carries it.

## F10 — Wording and status batch (W1, W2, W5, W6, W7, W8)

All mechanical; none touches behaviour except W7's hint.

| Ref | File | Change |
|---|---|---|
| W1 | `SettingsSchema.kt:567` | "Camera list" description → "The addresses, their order and shape, and how they play in the grid." (the screensaver left in M12) |
| W2 | `SettingsSchema.kt:~388` | "Per line" row → title "Lines", description "Rows when the page scrolls sideways, columns when it scrolls up and down; more lines, smaller tiles." (the value *is* the number of lines — `AllAppsPage.kt:193,203`) |
| W5 | `HomeAssistantPanel.kt:442-444` | Move the `haStatusMessage(...)` Text above `if (!showSensors) return@Column` (line 398), so "Home Assistant offline · retrying" also shows when the sensors card is off and the calendar has the space |
| W6 | `Cameras.kt:765-778` | Camera dialog's Advanced-tab switches renamed to the page's names: "Show substreams on the Cameras tab" → "Live pictures in the grid" (description from `SettingsSchema`), "Minimize simultaneous video streams" → "One stream at a time" |
| W7 | `HomeAssistantTab.kt:981-987` | Show the level hint whenever `steppable`, not only when on: `if (steppable)` with text `"${level ?: 0}%   ◂ ▸"` when on and `"Off · ◂ ▸ sets the level"` when off — the keys already act while off (`onPreviewKeyEvent` at 951-957), so this adds the affordance instead of the surprise |
| W8 | `SettingsPage.kt:586-593` + `HomeAssistantDialog.kt` onSave + `LauncherPrefs.kt` | Dashboard summaries print the raw key ("lovelace-config"). Store the chosen dashboard's *title* when it is saved: dialog Save writes `ha_dashboard_label` / `ha_home_dashboard_label` prefs (cosmetic, not exported; `dashboards()` at `HomeAssistant.kt:548-556` already has the titles); `rowSummary` reads the label, falling back to the key when absent |

**Check (device):** one pass: read the Camera list row (no screensaver mention); change "Lines" and
watch tiles shrink as the number rises; toggle "Sensors card" off, stop HA (or wait for an outage
window) — the offline line shows under the calendar; open the camera dialog's Advanced tab — the two
switch names match the settings page; focus an off light with a level — the "Off · ◂ ▸" hint shows,
Right turns it on at +10%; the Assistant "What it shows" row reads "Config", not "lovelace-config".

## F11 — Picker subtitle when there is nothing to reorder (W9) — fold into consolidation #2 + #8

With exactly one item chosen, `HaCamerasDialog`/`CalendarPickerDialog` subtitles still promise
left/right ordering. Do **not** patch the three dialogs: the shared `EntityPickerDialog` from audit
#2 should compute its subtitle from `chosen.size` — `1` → "One <noun>. OK chooses more.", otherwise
the current reorder hint. The noun is a constructor parameter alongside `title`.

**Check (device):** open the HA cameras picker with one entity chosen: subtitle reads "One camera.
OK chooses more."; choose a second: the reorder hint returns.

---

## Explicitly not in this plan

- **H9 / audit #3** (`HelperStatusRow`, `BuildInfo`, `OpenHomeAssistant`, `onOpenAccessibility`,
  the dead `rowSummary` branch): consolidation task, zero behaviour.
- **ux-review §3 improvements 1–10** (helper warning opens the helper directly, stepper units,
  search-hit → category, "can't open" toast, "Transport" tab name, remembered settings category,
  Uninstall on All apps, search placeholders, "N can be cleared", follow-the-unhidden-app): real but
  new polish, not half-finished features. Cheapest to fold into the milestone that touches the same
  file (e.g. #2 stepper units and #5 "Transport" belong with F10's batch if the executor has the
  room; #1 and #6 are one-liners in `MainActivity`/`SettingsPage` worth doing whenever F4/F5 are
  open). Do not let them expand this plan's scope.
- **A light theme, mini cameras/alerts column, splitting `HomeAssistantDialog`**: rejected in the
  ledger; stay rejected.

## Design decisions made here (flag to Ema before building, all reversible)

1. **F3**: manual camera pick wins for the rest of the session; "Camera on Home" hides while
   "Shuffle the camera" is on. Alternative rejected: hiding `shuffle_camera` instead (it is the
   newer, deliberately-built feature and the ledger describes its pick as the session default).
2. **F5**: one Notifications row (panel) carrying the access state, over keeping two rows.
3. **F6**: the Assistant tab's own switch is its only gate; the Home "Alert strip" switch stays
   Home-only.
4. **W7**: keep Left/Right acting on an off light and add the on-screen hint, over disabling the
   keys while off.
5. **F9**: custom services are a list of `domain.service` ids chosen from the server's own
   `get_services`, not free-typed.
