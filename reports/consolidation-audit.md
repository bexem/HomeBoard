# Consolidation audit — HomeBoard fork (`fork/m1`)

Scope: read-only audit of `app/src/main/java/com/evofactory/homeboard/` (~57 files). No code changed.
Ranked by value (most code removed / most cohesion gained first). Each finding: location, consolidation, estimated net lines removed, risk note + cheapest on-device check.

Deliberate designs in `FORK.md` (M1–M15) were treated as constraints: nothing here proposes removing a setting, changing a storage key, or reworking the accessibility helper restart path.

## 1. Two colour pickers → one parameterised picker
- **Files/symbols:** `BackgroundPickers.kt:40` `BackgroundColourDialog` (12 dark-first `BackgroundColours` in `LauncherBackground.kt:91`); `CalendarColoursDialog.kt:41` `CalendarColoursDialog` (8 `CalendarPalette` tints in `HaCalendar.kt:125`), which also opens a `ContextMenu` palette.
- **Duplication:** both render dot + name + "in use"/supporting + a palette; ~35 lines of near-identical Compose in each, plus two palette definitions that are the same shape (`name`/`hex` or `name`/`Int`).
- **Consolidation:** introduce `ColourPickerDialog(palette: List<ColourEntry>, current: Int, onPick: (Int) -> Unit, onDismiss)` where `ColourEntry(name, argb)`, and convert `CalendarPalette` hexes to ARGB at the boundary. Calendar's server-name fetch stays in the caller and feeds `title`/`supporting` strings; the palette menu becomes the picker's body.
- **Net lines removed:** ~60.
- **Risk:** colour selection focus order and the "in use" highlight. Cheapest check: open Background → colour, pick each of 12; open Calendar → colours, pick each of 8; confirm dot + supporting text update and dialog closes on Done.

## 2. Three entity pickers → one generic picker
- **Files/symbols:** `CalendarPickerDialog.kt:37`, `HaCamerasDialog.kt:38`, `FavouritesEditorDialog.kt:37`. All: read list from a source, checkbox per row, left/right reorder chosen items, `LazyColumn` max 280dp, `ListFocus`, Done button.
- **Duplication:** ~360 lines across the three, with identical `move()` and checkbox colour constants (`0xFF5A595F` / `0xFF8E8C92`).
- **Consolidation:** `EntityPickerDialog<T>(title, items, idOf, labelOf, secondaryOf, chosen, onChange, onDismiss, allowReorder)` plus the shared `moveInList`. Calendar's include/leave-out/move menu (FORK M12 "pickers say what OK does") should be the single interaction model: **HaCamerasDialog currently toggles on click and HaCameras has no move menu**, which is the cohesion gap to close in the same pass.
- **Net lines removed:** ~200.
- **Risk:** reorder focus and the menu's "Priority N of M" labels. Cheapest check: on one picker, include, move up/down, leave out, then repeat on the other two; verify server order of rows does not jump (FORK calendar rule).

## 3. Dead code left by M14
- **Files/symbols:** `SettingsMenuDialog.kt:113-176` `HelperStatusRow` (no callers — home helper is now a row, M14); `SettingsMenuDialog.kt:196-214` `BuildInfo` (no callers in `SettingsPage.kt`); `SettingsSchema.kt:51-52` `SettingAction.OpenHomeAssistant` (only handled in `SettingsMenuDialog.kt:92`, never emitted by `AllSettings`).
- **Consolidation:** delete all three (and the now-unused `OpenHomeAssistant` case).
- **Net lines removed:** ~90.
- **Risk:** none functional. Cheapest check: `assembleDebug` + grep for the three names.

## 4. Checkbox colour constants defined six times
- **Files/symbols:** `CalendarPickerDialog.kt:179-180`, `HaCamerasDialog.kt:164-165`, `FavouritesEditorDialog.kt:123-124`, `ExportImport.kt:310-311`, `LauncherSettingsDialog.kt:139-140`, `SettingsMenuDialog.kt:216-217` (`HelperIdleColor`).
- **Consolidation:** one `CheckedBox`/`UncheckedBox` (and `HelperIdleColor` alias) in `TopDialog.kt` or a new `UiConstants.kt`, internal.
- **Net lines removed:** ~12.
- **Risk:** none. Cheapest check: compile.

## 5. Three toggle-row implementations
- **Files/symbols:** `LauncherSettingsDialog.kt:113` `HomeOption` (ListItem + Switch); `CameraDialog.kt:234` private `SwitchToggle` (Surface + `SwitchMark`); `HomeAssistantTab.kt:1236` `SwitchMark`.
- **Consolidation:** one `SwitchRow(label, on, onToggle, modifier)` plus `SwitchMark`, used by `CameraDialog` and `CamerasDialog` (M2's `HomeOption` rows can stay ListItem-shaped but share `SwitchMark`).
- **Net lines removed:** ~40.
- **Risk:** focus highlight (focused row becomes light-on-dark). Cheapest check: open Cameras → Advanced and Camera edit dialog; toggle each switch with OK and confirm focus stays.

## 6. Address normalisers duplicated
- **Files/symbols:** `HomeAssistant.kt:358` `normalizeHaUrl`; `Frigate.kt:112` `normalizeFrigateUrl`. Identical structure, differing only default scheme/port.
- **Consolidation:** `normalizeUrl(input, defaultScheme, defaultPort)` in a shared location; keep the two public names as one-line delegates (storage/keys unchanged).
- **Net lines removed:** ~20.
- **Risk:** address parsing on the TV. Cheapest check: type `192.168.1.20`, `homeassistant.local:8123`, and a full URL with trailing path in both dialogs; confirm saved base URL.

## 7. `colourOf` placement (supporting #1)
- **Files/symbols:** `CalendarColoursDialog.kt:136` `colourOf` is the only hex→Color converter; `CalendarWidget.kt:91` uses it.
- **Consolidation:** move `colourOf` to `HaCalendar.kt` next to `CalendarPalette`; have the unified picker call it. (No second implementation exists today; this is placement, not duplication.)
- **Net lines removed:** ~3 (move only).
- **Risk:** none.

## 8. Inconsistent picker interaction (cohesion)
- **Files/symbols:** `CalendarPickerDialog.kt:156` opens a `ContextMenu` on OK (Include/Leave out/Move); `HaCamerasDialog.kt` toggles on click and has no move menu; `FavouritesEditorDialog.kt:47` toggles on click and moves with left/right.
- **Consolidation:** adopt the calendar menu model everywhere (FORK M12 explicitly endorses "OK says what it does"). One interaction model across all three pickers.
- **Net lines removed:** ~0 (behaviour change, not deletion).
- **Risk:** design decision required; user must confirm. Cheapest check: drive each picker with only OK and arrows; no hidden left/right-only actions.

## 9. `OpenHomeAssistant` action is dead (see #3)
Folded into #3.

## 10. Minor dead locals / doc drift
- `BackgroundPickers.kt:53` `var focused` is written but never read — remove (1 line).
- `HaCamerasDialog.kt:26-36` doc comment describes "calendars" though it picks cameras — fix comment.
- `HaCamerasDialog.kt:93` comment says "move a chosen calendar" — fix.
- **Net lines removed:** ~1.
- **Risk:** none.

## 11. `ringToTop`/`downToContent` wiring is repeated but is an API, not duplication
- **Files/symbols:** ~40 call sites across dialogs (`CameraDialog`, `HomeAssistantDialog`, etc.).
- **Consolidation:** none recommended — these are the intended focus-protocol calls. Flagging only so it is not mistaken for copy-paste debt. A helper would hide focus intent and is a design decision.

## 12. Large files that are not duplication
- `HomeAssistantDialog.kt` (~900 lines) and `HomeAssistantTab.kt` (~1260 lines) are large but single-owner; FORK.md M8 explicitly rejects splitting the dialog (would duplicate login/session/EntityPicker). Leave as-is.

## Recommended order
1. #3 + #4 + #10 (mechanical, zero behaviour change, ~100 lines) — compile + grep check.
2. #6 (mechanical, ~20 lines) — address check on TV.
3. #1 + #7 (colour picker) — on-device colour check.
4. #2 + #8 (entity pickers) — on-device include/move check; needs a design call on interaction model first.
5. #5 (toggle rows) — on-device focus check.

Nothing here touches storage keys, export format, or the accessibility helper restart path, so M1–M15 invariants hold.