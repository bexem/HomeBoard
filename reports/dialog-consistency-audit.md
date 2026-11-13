# Dialog and layout consistency audit — 2026-11-13

Asked for after the badge-panel fix (`147c0fd`): *"review the rest of the code for similar or pretty
much around that bullshits"*. Three read-only scouts audited the dialogs, the main-screen layout, and
reuse drift; every high finding and the two layout false-positive candidates were then checked
against the live tree, and the visible ones reproduced on the Shield. Severities: **high** = wrong on
the TV today, **medium** = wrong in an edge case or drift waiting to happen, **low** = cosmetic or
dead code.

## The verdict

The shell is sound — there is exactly one popup implementation (`TopDialog`), one colour picker, one
entity picker, one settings model; the two raw `Dialog` windows are the fullscreen camera and alert
players, and the settings page is a full-screen page, all by design. What the audit found is the same
*class* as the badge panel: three dialogs whose list rows are dead ends for Left/Right (the remote
cannot reach the X from them), one more redundant Close button next to the X, one latent copy of the
header weight bug, and a batch of text that can wrap instead of ellipsising. Everything else the
scouts flagged as reuse drift is either already converged or deliberate.

## High — wrong on the TV today

| # | Where | What | Fix |
|---|---|---|---|
| 1 | `EntityPickerDialog.kt:118` | List rows never call `.ringToTop()`: Left/Right from a row do nothing instead of reaching the X. **Reproduced on the Shield** (Launcher → Screensaver → Cameras it shows → Doorbell row: LEFT and RIGHT both leave focus on the row) | Append `.ringToTop()` to the row modifier |
| 2 | `EntityNamesDialog.kt:117` | Same dead end on the name rows | Same |
| 3 | `CalendarColoursDialog.kt:95` | Same dead end on the colour rows | Same |
| 4 | `Screensaver.kt:326` | `ScreensaverStatusDialog` ships a primary "Close" button next to the X — the same redundancy just removed from `NotificationAccessDialog` | Drop the "Close" button; keep the "TV screensaver settings" action; start the remote on the X |

## Medium — the header bug's remaining copies and kin

| # | Where | What | Fix |
|---|---|---|---|
| 5 | `HomeAssistantTab.kt:1269` | `SelectRow`: the entity name has `weight(1f, fill = false)` beside the reading's `weight(1f)` — the exact pattern that put 230 dp of slack past the TopDialog X. Latent today (no `input_select` entity is chosen on this TV), live the moment one is | Name takes `weight(1f)` (fills, ellipsises); reading drops its weight and sits flush before the chevron |
| 6 | Twelve `Text`s can wrap instead of ellipsising: `EntityPickerDialog.kt:106`, `CalendarColoursDialog.kt:85-86`, `ExportImport.kt:287-288`, `EntityNamesDialog.kt:106,108` (have `maxLines`, missing `overflow`), `ContextMenu.kt:111`, `CameraDialog.kt:222`, `HomeAssistantDialog.kt:729`, `HomeAssistantPanel.kt:~580` | A long camera URL, entity id or label grows its row and can push a dialog past its width | `maxLines = 1, overflow = TextOverflow.Ellipsis` on each |

## Low — reuse extractables (nothing disagrees today)

- `JSONObject`/`JSONArray` string readers re-implemented under two names (`text`/`string`) in
  `HaCalendar.kt:39,69`, `HomeAssistant.kt:296-299`, `HaDashboard.kt:190-196`, `HaHistory.kt:81` —
  extract one `stringOrNull`/`objects` pair.
- Colour parsing split three ways: `HaCalendar.kt:139 colourOf` (`#RRGGBB` only) vs `HaGauge.kt:123
  cssColor` + `:154 hexColor` (full CSS, 3-digit hex) — let `colourOf` delegate to `cssColor`.
- List-move helpers triplicated: `HomeAssistantTab.kt:155,164` and `EntityPickerDialog.kt:70` —
  one `moveInList`.
- Empty-state `Text`s hand-rolled in seven places (`FrigateAlerts.kt:418`, `HomeAssistantDialog.kt:664,887`,
  `Cameras.kt:670`, `ExportImport.kt:270`, `AppRow.kt:572`, `CalendarWidget.kt:76`) with drifting
  type/colour/padding — one `EmptyState` composable.
- The transparent-row `ClickableSurfaceDefaults.colors(...)` block copied at `HomeAssistantDialog.kt:693,781`,
  `HomeAssistantTab.kt:1001`, `HomeAssistantPanel.kt:534` — one `rowColors()`.
- The two-frames-then-requestFocus block at `NotificationsDialog.kt:50`, `SettingsPage.kt:201`,
  `TopDialog.kt:190`, `HomeAssistantDialog.kt:597` — one `requestFocusAfterLayout`.
- `SettingsPage.kt:440`: a trailing `Spacer(weight(1f))` in a Start-arranged Row does nothing — delete.
- `FrigateDialog`/`HomeAssistantDialog` both hand-roll address+credentials+connect — extraction was
  declined when FORK.md recorded "do not split HomeAssistantDialog"; leaving as is unless overruled.

## Checked and dismissed (with reasons)

- `MainActivity.kt:559,564` (warning chips `weight(1f, fill = false)` beside action buttons): the
  actions Row is `spacedBy(PanelItemSpacing, Alignment.End)` — children cluster right with the
  buttons, the weights only cap each chip's share. Measured no gap; deliberate ("measured last, so
  the cog and the clock always keep their room").
- `MainActivity.kt:710` (`spacedBy(EdgePadding)` + weighted side spacers "double-counting"): measured
  on the Shield in the on-the-sides mode — outer margins 202/204 px, symmetric; the `spacedBy` gap is
  what gives the glued pair its minimum middle gap. Not nudged off centre.
- Everything else the scouts covered is listed clean in their reports: AppRow centring, TwoRowGrid,
  Screensaver rotation, ContinueWatching, CalendarWidget's 132 dp cap, HaGauge, the TopDialog header
  (post-fix), and the already-converged pickers/switches/url normalisers/settings model.

## Verified on the Shield (2026-11-13, 17:21–17:38)

- Assistant tab opened and walked: no select entity is chosen, so finding 5 is latent, not visible.
- Launcher → Screensaver → **Cameras it shows** opened for the first time: title "Cameras in the
  screensaver", subtitle "6 of 6 take a turn", Done + X flush right (the `147c0fd` header fix
  carrying over), six checkbox rows, Doorbell focused. The one screen previously never seen.
- LEFT and RIGHT from the Doorbell row: focus unchanged — finding 1 reproduced live.
- Ema's own settings in place at the time: `Space beside the camera` = two fifths, `The gap goes` =
  On the sides (his picks; left untouched).
