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

`home_alerts_row`, `continue_watching`, `app_row_wrap`, favourites, the All apps page (M2); the calendar
(M3); the Assistant device tiles (M4). Each is one entry in `SettingsSchema.kt` plus its feature code.

The build installed on the TV is a **debug** APK (no release keystore on this Mac yet), so it is
debuggable and `run-as` works. Release signing is a separate step before any hand-off.
