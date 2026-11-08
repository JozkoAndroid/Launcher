# Handoff: school launcher (Kvaesitso fork)

State after round 15 (2026-11-08). Everything is on branch `brief-widgets` of
`github.com/JozkoAndroid/Launcher` (upstream: `MM2-0/Kvaesitso`). `main` only carries the GitHub Pages
OAuth bounce page in `docs/oauth/`.

## 1. What this is

An "AI-first" Android launcher for one student (Slovak, at a German school), built on Kvaesitso.
Devices: Galaxy Z Fold 5 (cover ~360dp wide, inner ~830dp) and a Galaxy Tab S10 Lite.
The home screen is a dashboard: current lesson, AI daily brief, Schoolhelp tasks, tiles (test countdown,
name day, mobile data, steps, Instagram, "+" add), a floating dock. All UI text is Slovak.
Must stay free: no paid cloud jobs. The phone talks to Schoolhelp (MCP) and Gemini (free tier) directly.

## 2. Working with the user

- Very terse answers, no filler, no em dashes, no emojis. Honest pushback is wanted.
- Design rules: the user's `/mydesign` skill (one primary action per screen, tokens, tinted neutrals,
  one accent, no generic AI icons, motion 150 to 300ms ease-out). Latest direction: **minimal**
  (Niagara / Nothing OS: monochrome, few boxes, one consistent pill shape).
- The user tests on the device and reports by voice (transcribed, often garbled) plus annotated
  screenshots. Directions ("swipe left/right/up/down") are often ambiguous: support both when cheap.
- Ship every round as an APK (see 4) and summarize what changed, what is unverified on device.
- Security: never commit keys or real Schoolhelp data to this public repo. A Gemini key was pasted in
  chat early on; the user should rotate it. Keys live only on the phone (Android Keystore, `SecretStore`).

## 3. Environment setup (fresh container)

- Android SDK at `/opt/android-sdk` with `platforms;android-37.1`, `build-tools;37.0.0` (and 36.0.0),
  `platform-tools`. `local.properties`: `sdk.dir=/opt/android-sdk`.
- Maven Central rate-limits (HTTP 429). Add `~/.gradle/init.d/central-mirror.gradle`:
  ```groovy
  def mirror = "https://maven-central.storage-download.googleapis.com/maven2"
  beforeSettings { settings ->
      settings.pluginManagement.repositories { maven { url = uri(mirror); name = "CentralMirror" } }
      settings.dependencyResolutionManagement.repositories { maven { url = uri(mirror); name = "CentralMirror" } }
  }
  ```
- Gradle 9.6, AGP 9.4.1, compileSdk 37.1, minSdk 26, JDK 21.

## 4. Build, test, ship

- APK for the user: `./gradlew :app:app:assembleDefaultFork`
  -> `app/app/build/outputs/apk/default/fork/app-default-fork.apk` (copy to `launcher-brief.apk`, which
  is gitignored, and send it). Build type `fork` = release (R8) but debug-signed, app id
  `de.mm20.launcher2.debug`, so it installs over earlier test builds.
- Tests: `./gradlew :data:schoolbrief:testDebugUnitTest :app:ui:testDebugUnitTest :app:ui:verifyPaparazziDebug`
- Screenshots: `./gradlew :app:ui:recordPaparazziDebug` (run **record and verify in separate
  invocations**; together they fail). Snapshots in `app/ui/src/test/snapshots/images/`, tests in
  `app/ui/src/test/java/.../widgets/school/` (`SchoolScreenshotTest`, `DashboardScreenshotTest`).
  Paparazzi needs `LocalInspectionMode provides true` (animated content) and a stub
  `LocalActivityResultRegistryOwner` for anything with speech input.
- Live tests in `LiveNetworkTest` run only with env `GEMINI_API_KEY`.
- Commit messages end with the session attribution lines; push to `brief-widgets`.

## 5. Code map (fork code only)

**data/schoolbrief** (feed, Schoolhelp, Gemini)
- `Feed.kt` (Feed, SchoolTask kinds `hausaufgabe|test|projekt|termin`), `BriefLogic.kt` (visible tasks,
  lesson state), `BriefRules.kt` (template brief, slots 06:30/13:30/19:30), `FeedBuilder.kt`.
- `BriefRepository.kt`: interface (feed, status, today, recording, markDone, addTodo, addEvent,
  studyContext, answerNow, pendingChanges ...). `SampleBriefRepository` for previews.
- `SchoolChat.kt`: Gemini chat with school data, Slovak dates pre-formatted (model got weekdays wrong).
- `live/`: `SchoolhelpAuth` (OAuth 2.1, PKCE, GitHub Pages bounce -> `launcherbrief://oauth`),
  `McpClient` (`appCall`, `appCallAll` reads `paged_json` via `result_offset`), `LiveBriefRepository`,
  `GeminiWriter` (model `gemini-flash-lite-latest`, JSON schema, validated, template fallback),
  `NewsSource`, `SecretStore`, `TodayParser`, `RecordingParser`, `PendingQueue` (offline changes).

**data/widgets** `SchoolBriefWidgets.kt`: widget types (Lesson, Brief, Tasks, Context, Assistant,
Folders, Week, MyDay, TestPrep, ChipWidget with kinds `add,test,chats,lunch,nameday,birthday,music,
steps,data,instagram` and `ChipWidgetConfig` style/font/icon/accent). Registered in `Widget.kt`,
`WidgetsService`, `WidgetPickerSheet`, `ConfigureWidgetSheet`, `WidgetItem`, `external/AppWidget.kt`.

**app/ui/.../launcher/widgets/school/** (all fork UI)
- `SchoolTokens.kt` tokens, `Segment`/`segmentShape` (joined sheet), `SchoolFonts` (Display = Google Sans
  Flex wdth 112; Serif/SerifDisplay = bundled Fraunces).
- `Speech.kt` (round 15): `rememberSpeechInput` -> `SpeechInput`. The launcher listens itself
  (`SpeechRecognizer`, `RECORD_AUDIO` asked on first tap, Slovak then the phone's language, live partial
  text). Status and errors show in the `InputBar` hint. The system speech screen is only a fallback (round
  14 used it: on the Fold it opened a settings prompt instead of listening).
- `Bars.kt`: **the one bar design** (`PillBar`, `InkButton`, `InputBar(voice = SpeechInput)`, `Segmented`) and
  the **pill motion** (`pillArriving` D(1-p)^2, `pillLeaving` D p^2, D = 128dp + nav bar). Use these for
  any new bottom bar.
- `SchoolWidgets.kt` (lesson hero on wallpaper, brief, tasks with check circles, context chips, contextItem),
  `ChipStyles.kt` (tile/ring/glance/pill, `ChipTiles` balanced grid, `LocalTileWidth`), `MoreWidgets.kt`
  (week, my day, test prep), `Dock.kt`, `DayPage.kt` ("Môj deň"), `AppsPage.kt`, `FoldersWidget.kt`
  (folders = Kvaesitso tags, `AppPickerSheet`), `AddEvent.kt` ("+" tile and sheet), `Focus.kt` (focus
  timer, round 15: start from a plan step's play button or a free 15/25/45 min line on "Môj deň"; silent
  ongoing notification with a live countdown and Live Update request; exact alarm (`USE_EXACT_ALARM`);
  loud end notification with "Hotovo" / "+5 min"; time running out never ticks the step;
  `FocusAlarmReceiver` handles the alarm and the notification buttons), `SchoolPages.kt` (chat VM, `ChatDock`, quick actions, DayCard),
  `context/` (Práve teraz: rules, sources, VM, settings), `Links.kt`, `AssistantWidget.kt` (Gemini overlay
  via `SearchManager.launchAssist` reflection).
- `ForkSetup.kt`: versioned one-time migrations (now `Version = 13`), recommended layout
  (`layoutHome`, `applyRecommendedLayout`, `resetLayout`). Add a new version for any default change.
- Settings: `ui/settings/school/SchoolSettingsScreen.kt` (Settings > Škola: Schoolhelp, Gemini,
  permissions, Práve teraz values, layout reset), route `settings/school`.

**Scaffold changes** (`ui/launcher/scaffold/`)
- Components must apply the `modifier` passed to `Component()`: it carries the page slide. (Missing it
  made the Day page draw over home; cost two rounds.)
- Swipe progress is read only in draw/layout lambdas (`secondaryPageAnimation`, `searchBarAnimation`),
  never in composition (per-frame recomposition = jank).
- Push threshold: a third of the way, both directions; a fling during a transition goes to the scaffold
  (`onPreFling` in the scaffold's nested scroll connection).
- Search from a side page: `switchToSearch` stores `returnGesture`; drag-dismiss, system back and
  launching an app (STOP) return via `switchBack()`/`leaveSearch()` to that page; home button and
  `reset()` clear it. While returning, drags do not move the page (`returningFromSearch`, `backPull`).
- Screen off -> `reset()` to home. App launched from search -> back to origin page on return.
- `WidgetsComponent` for `Widgets2` = side panel with a 2-page `HorizontalPager` (widgets, apps page),
  pull past either end opens search with keyboard (opens on finger lift).
- Search bar = shared pill (`pillColor`, `pillOutline`, same shadow), bottom position, hidden on home
  (dock there). Pages without it (`showSearchBar = false`, Môj deň) move it off screen: invisible but
  on top it stole taps from the page's own bar (round 15 bug). All pills share one motion (rise D(1-p)^2,
  grow from 96 %, fade in by 60 %), read in `graphicsLayer`.
- Focus: `Focus.kt` (`FocusTimer`, prefs "focus" with `ends`/`block`). Two sessions built it in parallel
  and were merged: the engine (live notification, exact alarm, +5 min) from session ...MCqTV, the
  start UI is the "Fokus" mode bar at the bottom of Môj deň (`FocusBar`, 15/20/30/45 min); the top line
  only shows a running session. Blocking Instagram/YouTube/Threads is done by Kvaesitso's own
  `LauncherAccessibilityService` (services/global-actions) on `TYPE_WINDOW_STATE_CHANGED`: needs the
  user to enable the launcher's accessibility service.
- Gestures (defaults + ForkSetup): swipe left = panel (`Widgets(Widgets2)`), swipe right = "Môj deň"
  (`GestureAction.SchoolChat` id kept), swipe up = search, swipe down = notifications (the user may have
  changed it on the device).

## 6. Schoolhelp MCP notes

Endpoint `https://lessons.labeltranslator.com/mcp`, tool `app_call {name, arguments}`.
- `timetable.read`; `list_events` (no big `limit`: "Capability number out of range"; page with `offset`);
  `mark_done {event_id, done}`; `calendar.add_private {title, start, kind, folder}`.
- `today.read` returns `paged_json` (about 21k chars, 2 pages): `plans[]` (steps with minutes, done),
  `todos[]`; `today.add_todo {text}`, `today.update_todo {todo_id, done}`, `today.set_step {i, done}`.
- `lessons.status`: `lesson` (this workspace) may be null while another device records; also read `alive`.
  Never verified with a live recording.
- `stoff.read {folder, limit}`: current topic + recent lessons (used for test prep and explanations).
- `talk.list` result nested under `result`. `lessons.answer_now` answers the teacher's last question.
- Offline: every change goes through `PendingQueue` (`schoolbrief/pending.json`), sent in order; a change
  Schoolhelp refuses (`IllegalStateException("Schoolhelp: ...")`) is dropped. Today cached in `today.json`.

## 7. Verified vs not verified on device

Verified by the user: OAuth login, tasks load, Gemini overlay, brief, tick "Hotovo", masonry dashboard,
panel/apps page look, dock, Day page look, and (round 15 check of rounds 12 to 14) bottom search pill,
consistent pill motion, return from search to the origin page, transparent Day page, minimal tiles,
Fraunces, screen-off reset, Instagram minutes, add-event date picker, apps page drag reorder.
Reported broken in round 15 and rewritten, not yet verified on device: focus timer (never showed up),
chat/task bar mic (opened a settings prompt instead of listening). The home dock's round button stays the
Gemini overlay (user's choice).
Unverified: whether Samsung shows the countdown as a Live Update (Now Bar); the exact alarm on time.

## 8. Open items / backlog

1. Device-check the list in 7 first; most bugs this project had were gesture/scaffold edge cases.
2. Grade tracker block (user wants it once Schoolhelp has grades; "Abitur grade tracking").
3. Lunch Bell detection ("Lunch Bell Z2" notification) never confirmed working.
4. Recording row: confirm `lessons.status` shape during a live recording.
5. Smoothness: no baseline profile yet (needs a device/emulator); app list scroll jank reported earlier.
6. Settings: the user wants them simpler; Kvaesitso's own settings are untouched (maybe hide rarely used).
7. Minimal design: continue (fewer labels, maybe drop news icons, unify Kvaesitso weather card style).
8. Focus timer: done in round 15 (see 5); check on device.

## 9. Starter prompt for the next chat

"Continue the school launcher in this repo (branch `brief-widgets`). Read `docs/HANDOFF.md` and
`CLAUDE.md` first. Set up the SDK and Maven mirror as in section 3, build `:app:app:assembleDefaultFork`,
run the tests, then ask me for the device results of section 7 before changing gesture code."
