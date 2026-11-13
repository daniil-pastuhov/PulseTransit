# Fjord implementation plan

Spec: `brief.md` (state plan, decisions log), `tokens.json`, the `data-note`/`data-trigger` texts on
each state in `src/pages/*.html`. Every step covers Android, desktop and iOS together.

**Delivery:** one release. Each step is a PR into the `design/fjord` integration branch; one final PR
merges it into main, then `scripts/release` cuts the version. Main stays releasable for hotfixes.

Already in place (don't rebuild): paging with "Stops 1–4 of 6", per-stop failure (`failedStopIds`),
"No upcoming departures · Search further ahead", live lux reading, `schedule_no_days`, 640 dp settings
column, Location only in board mode, Duo `VerticalBarHost`, widget `boardAtMillis`/`platform`.

## 1. Foundations — tokens, type, icons, base components
- Generate `theme/Color.kt` and `iosApp/Theme/Palette.swift` from `tokens.json` (script). Full M3
  `ColorScheme` + a `PulseColors` CompositionLocal for `late`, `now`, `route*`, warning/success/info
  containers and per-theme line colours. Dark = true black. Line colours move out of
  `StopBoardPanel.getTransportColor` / `Palette.transport`; widgets use the tuned values.
- Bundle Public Sans 400–700 (Compose resources `font/`, iOS `UIAppFonts`); `Typography` from the
  type roles plus clock/countdown/pill styles, tabular figures. Glance keeps the system font.
- Material Symbols Rounded (bundled vectors, only those used) / SF Symbols. `TransportIcon(mode)` and
  weather icons from `parseWeatherSymbol`. Remove all emoji (board, sheets, `leg_walk`, intro, Swift).
- Components (Compose + SwiftUI): `LinePill`, `DelayChip`, `Callout` (T4), `ScreenError` (T5),
  `Skeleton` (surface-container), 60% scrim + outlined sheets/dialogs.
- `commonTest`: contrast test over the `contrast` pairs in `tokens.json`.

## 2. Shared behaviour (commonMain, tested)
- `RELATIVE_TIME_CUTOFF_SECONDS` 30 → 15 min; "min" unit string (iOS shows "5m" today).
- `showStaleNotice` in `StopBoardViewModel.UiState`: only once data is older than 2 × the effective
  refresh interval (night/dim multipliers included).
- Undo as state, not events (keeps one `UiState` per ViewModel, easy for SwiftUI): `pendingUndo`
  plus `undo()`/`dismissUndo()` in `StopBoardViewModel` (line hidden, destination saved) and
  `SettingsViewModel` (stop removed with position + directions, stop added).
- Rows carry the group's `typicalHeadwayMinutes`; panel head carries a direction summary.
- `PAGE_INTERVAL_MS` 15 → 20 s; `manualRefreshing` flag; explicit today/tomorrow `TripTime` builder.

## 3. Board and shell
- Header: clock + "Fri 2 Oct"; weather = now block + hourly cells, hidden when weather is empty;
  only Search + Settings (Refresh → pull-to-refresh with thin bar on phones). One-line footer.
- Rows: one time, sub-line (platform / "Every ~8 min · +4 more"), cancelled, delay chip with struck
  planned time, untruncated pills. Panel head sub-line.
- States: skeleton loading, first-use empty, single banner slot by priority (unreachable > location
  off > update) under the header, update banner as T4, route banner in `route` colours, paging dots,
  dimmed. Snackbar host for step 2's events.

## 4. Detail sheets
- Departure: "19:53 in 16 min", stop rail, stops-failed "Try again", cancelled → "Show the 19:53
  departure", close on filter + Undo.
- Trip: timeline rail, under-way states; `JourneyAlertsViewModel.UiState` gains `locationLost`,
  `notificationsDenied`, `locationDenied`. New `expect fun openAppSettings()`.
- Right-side sheet (max 440 px) on wall/fold/desktop — custom component in Compose.

## 5. Route search + travel time
- Results stay in the sheet: alternatives, Save destination + Undo. New: Recent (persisted in
  `SettingsRepository`), origin name (reverse geocode or stored label), earliest-arrival fallback
  query (also used by the board banner's "The next one arrives 08:41").
- Travel time picker as a sheet: segmented Leave now / Depart at / Arrive by, explicit Today/Tomorrow.

## 6. On-spot
- Route rows and stop head restyle; "Approximate location" / "About 300 m". Skeletons while locating;
  T5 denied/unavailable; "No destinations yet" + Add; collapsed divider bar naming what it hides;
  stop chooser as a bottom sheet; stale rule from step 2.

## 7. Onboarding and permissions
- Mode selection brand row + selected state; intro Skip, "Step n of N" dots.
- Location and camera rationales + denied states (camera asked from Settings, not the board).

## 8. Settings
- Centred column from 700, two columns from 1000 in board mode (what the board shows / how the device
  behaves). "Wake and dim" group: sensitivity and auto-dim shown disabled until camera wake is on
  (dimming stays tied to camera wake). Route search stays in both modes.
- Switches and segmented buttons; Location as T4 callouts; "Or enter coordinates" with T1 error.
- Own screens: Add stop (distances, max reached, Undo), Directions (loading/failed/Retry —
  `fetchQuaysForStop` swallows failures today), Destination schedule (Save enabled, T1 on submit).
- Tri-state Travel by; journey alerts "n of 7" switches; Data sources skeleton, API data only.
- New: stop reorder in `SettingsRepository`; T6 mode-switch confirmation with counts.

## 9. Widgets and journey progress
- Shared: walk legs with duration in `WidgetRouteLeg`; more upcoming rows for 4×4 glance.
- App: `GlanceView.status`, `RouteView.legs` as a list, title + body messages, vector icons,
  redone 2×2, new 4×1/4×4, picker empty state.
- Per-leg segmented progress (Android `ProgressStyle` API 36 + fallback; iOS `ContentState` segments).

## Deviations decided while building
- Steps 3: a phone used as a board still pages its stops (the design scrolls them). Pull to refresh
  on iOS needs iOS 16 (`refreshable` on a ScrollView); iOS 15 relies on the automatic refresh.
  "Nothing leaves in the next 24 hours" uses the real departure window, not the mock-up's 2 hours.
- Step 4: iOS keeps the system sheet (an iPad form sheet) instead of a custom right-side sheet.
  "Open settings" on the time-only fallback note helps the next journey; a running one keeps its
  mode. The walk-only "faster than any ride" note waits for the planner query of step 5.
- Step 5: route search names its origin "From the board" / "From where you are" rather than a
  street address (no reverse geocoding call for a label). Closing the sheet keeps the searched
  place on the board until it is dismissed or saved there, as before. "Arrive by" that can't be
  met gets the earliest arrival in route search; the board's saved destinations always route for
  now, so the board banner needs no such fallback.
- Step 6: the stop chooser is an adaptive sheet on Android (bottom, or a side sheet from 700 dp)
  and the system sheet on iOS, as in step 4. "Allow location" on iOS opens the Settings app, since
  iOS asks only once.
- Step 7: on-spot location denied offers "Continue without location" (the board then says location
  is off) instead of "Set location manually", as on the spot has no fixed location; on iOS it
  shows once access is refused, and while undecided the way on is the prompt, since the board
  would ask anyway. The location rationale names MET Norway as well as Entur: the forecast is for
  the device's position. Camera denied says the board stays at full brightness, since dimming is
  tied to camera wake. The permission art is the feature's symbol on a tile, not the drawn scenes.
  On the spot's intro is the design's three steps (Tap for details is the board's only). Neither
  board asks for the camera on its own any more, and on Android the on-spot board no longer asks
  for location (it shows "Allow location"). On iOS it still does while access is undecided, e.g.
  after switching to on the spot in Settings, since its location source asks the first time.
- Step 8: typed coordinates accept a decimal comma (Norwegian keyboards type one) instead of the
  design's "use a dot" error; the field errors are for empty, not a number and out of range. Add
  stop shows a generic stop icon, as the geocoder's nearby search returns no transport modes. On
  iOS stops and destinations are removed by swipe (the platform's idiom) and the iPad's two
  columns are two Forms side by side. A tick in Directions now means "only these" with none ticked
  meaning all, as the design draws it (before, every box showed ticked when all were shown).
  The destination schedule stays a sheet (a dialog on wide screens) over Settings rather than a
  screen of its own; Save is always enabled and explains "Pick at least one day" (T1). Unused
  strings left by the old Settings were removed.
- Step 9: the widgets' icons are vector drawables generated by gen-icons.py from the same Material
  Symbols (a widget cannot draw Compose vectors); the widget's weather mapping mirrors the board's.
  Android's per-leg progress was already segmented (ProgressStyle, plain bar before 16); the iOS Live
  Activity now draws one self-filling segment per leg. iOS WidgetKit remains a follow-up.
