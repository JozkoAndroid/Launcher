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
- **Signing (round 15 lesson):** `fork` is signed with `~/.android/debug.keystore`, which a fresh container
  creates anew. A new key means the phone refuses the update ("App not installed") and the user has to
  uninstall, losing logins, keys, permissions and layout. The key used since round 15 (certificate
  SHA-256 5a299f32...a6aaef) is kept by the user in the PRIVATE repo `JozkoAndroid/Launcher-feed` as
  `fork-debug.keystore` (never in this public repo). Before building: attach Launcher-feed, then
  `cp <Launcher-feed>/fork-debug.keystore ~/.android/debug.keystore`; after building, check with
  `apksigner verify --print-certs` that the digest matches. If the key is missing, stop and tell the user.

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
- Focus bar (round 15): third mode of the "Môj deň" bar (`DayBarMode`): 15/20/30 min, start. While a
  session runs, `FocusGuardService` (foreground, specialUse, under the countdown notification) checks the
  front app once a second via usage access and covers Instagram, YouTube and Threads (`BlockedApps`) with
  a full-screen overlay, then sends the user home. Needs usage access + "display over other apps". The
  accessibility route was dropped: Play Protect refused it for the sideloaded APK.
- Dock (round 15): long press = app picker (`AppPickerSheet` with limit). Chosen apps = pinned favorites
  (`DockVM.setDock`, other pinned non-app favorites kept); none chosen = most used.
- Week (round 15): `WeekOverview` (panel block and week page): 7-day strip with due dots, then only the
  days with something due. No timetable (user found it excessive).
- Tiles (round 15): `ChipTiles` is a fixed grid (2 columns narrow, 3 from 450dp), empty tiles take no slot.
- Recommended layout (ForkSetup v16, applied on update; `run` once per process, v15 could run twice
  and duplicate blocks): home = Lesson, Weather (compact), Brief, Tasks, ten tiles (add, test, lunch,
  nameday, birthday, chats, data, steps, instagram, music; empty ones take no slot); panel = Week,
  Calendar, Folders, Notes. No duplicates; no "Práve teraz", no "Môj deň" card, test prep via the
  test tile's revision sheet.
- "Môj deň" bottom area: one frame for all modes (`DayBarMode`): mode extras (chat `ChatPart.Above`,
  `FocusNote`) above an equal-width `Segmented`, the bar below at the same size in every mode.
- Revision sheet (`Revision.kt`): test tile tap; note topics + `studyContext` (live), "Učiť sa 20 min"
  starts focus, practise with Gemini secondary.
- Open (asked the user): gestures from the right screen up/down and back, folders and pinned apps in
  the app list.
- Settings > Škola lists every permission (location, mic, notifications, overlay, usage, notification
  access, contacts, steps).
- Weather (round 16): met.no needs contact info in its User-Agent; `metno_contact` was missing (upstream
  sets it in its gitignored config.xml), so no request was ever sent. Now in
  `data/weather/src/main/res/values/metno_contact.xml` (project URL, kept from shrinking).
  `DevicePoseProvider` also reads the fused provider and listens on the main looper.
- Wide dashboard (round 16): `DashboardColumns` keeps each block in its column (edit mode on a wide
  screen: "Stĺpec N" button in the block header; default by kind). No masonry: a growing block pushes
  only the blocks under it.
- Round 19: tiles on the wide dashboard sit in columns like blocks (consecutive tiles in one column share
  a grid; default right column; "Stĺpec" button on tiles too). Widget picker lists only blocks that
  always show something (no Môj deň, Gemini block, Práve teraz, Kvaesitso app grid); conditional tiles
  carry a note. Chat quick action "Opakovací list" (revision sheet for the next test). On the open app
  list, a tap catcher under the search bar (bar strip to the screen edge) focuses search.
- `RecordingParser`: "alive" also lists Schoolhelp services ("browser"); only lesson-like entries count.
  Live shape of lessons.status is in `RecordingParserTest` (trimmed, no personal data).
- Code map: `graphify update .` on a copy of the fork code (scratchpad) gives graph.json /
  GRAPH_REPORT.md; `graphify explain X`, `graphify path A B` to navigate (local, no LLM).
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
- Search bar = shared pill, bottom position, hidden on home (dock there), no fade (slides with page).
  On pages without it (`showSearchBar = false`) it is moved off screen, not only made invisible: until
  round 15 the invisible pill took the taps on the "Môj deň" bar (chat field opened search, its mic the
  search bar's voice action).
- All pills (dock, input bars, focus bar, search bar) share `pillSurface` (surface 94%, tinted shadow,
  hairline outline) and `pillAt` motion (rise, 92 to 100% scale, soft landing) in `Bars.kt`.
- Gestures (defaults + ForkSetup): swipe left = panel (`Widgets(Widgets2)`), swipe right = "Môj deň"
  (`GestureAction.SchoolChat` id kept), swipe up = search, swipe down = notifications (the user may have
  changed it on the device).

## 5a0. Round 27: Schoolhelp connection restored to round 21 (READ FIRST)

The user reported Schoolhelp got worse with each of rounds 22, 25 and 26 (login race fix, pending
queue changes, hang protection). None of them could be tested against the real Schoolhelp from the
build container. Round 27 restored the connection layer exactly to round 21 (commit b220059):
Http.kt, McpClient.kt, SchoolhelpAuth.kt, SecretStore.kt, PendingQueue.kt, LiveBriefRepository.kt,
RecordingParser.kt. Only additions: `retryNow()` (runs the round 21 sync and recording check now) and a
lenient decoder in PendingQueue (keeps tasks saved by round 25). The notes in 5a to 5a4 about the
connection describe code that is no longer there. Rule from now on: change the Schoolhelp connection
only with a live test against Schoolhelp (the Schoolhelp MCP connector in the session, authorized by
the user), one change at a time.

## 5a00. Round 28: the real cause of the Schoolhelp failures

Device screenshot at login: `{"error":"invalid_request","error_description":"Client ID '...' not found"}`.
Schoolhelp lost its dynamically registered OAuth clients in an outage; the launcher reused its stored
client id for ever, so refresh and every new login failed. `SchoolhelpAuth.begin()` now registers a new
client at every login (verified live: /register 201, /authorize with the new id goes to the consent page).
lessons.status with no recording (Nov 9): `lesson: null, alive: [], mic: null, now_subject: "Ma"`.

## 5a000. Round 29

Pending queue only (connection untouched): a tool error ("Schoolhelp: ...", Schoolhelp's app away) keeps
the change and continues with the next ones; dropped after `PendingOp.MaxRefusals` (20). Login runs
refresh(forceBrief) which flushes first, so waiting tasks go out right after reconnecting.
`PendingPolicyTest` covers it.

## 5a0000. Round 30
- Mic (`Speech.kt`): listens until the stop tap. Android's recognizer ends at every pause, so it is restarted
  (150 ms later); text of all rounds is joined. Ends by itself only after ~12 silent rounds (about a minute).
  Unverified on device: some phones beep at every restart.
- Chat on "Môj deň": the conversation is rows of the page list under the tasks (`chatConversation`), not a
  box. Quick questions stay above the mode switch during a chat. Recent chats: fixed round button top right
  while "Opýtaj sa" is selected (`ChatRecentButton`). The page list's bottom padding follows the measured
  bottom frame. Side panel and sheets still use `ChatPart.All` with the old box.
- Lunch: EduPage is never a lunch source (`ContextRules.isEduPage`), also not a stored lunch or an earlier
  choice. Device report: EduPage sends no menu, its other notifications showed as lunch.
- Schoolhelp connection untouched.
- Claude in the launcher: see round 31.

## 5a00000. Round 31: Claude in "Opýtaj sa"
- Max includes $100/month API credits (official docs, since 2026-10-07). No card in the Console = requests
  stop when used up, never charged. User rule: no extra cost. Never suggest auto-reload or a card.
- `ClaudeChat.kt` (`ClaudeSchoolChat`, bound in `Module.kt`): Sonnet 5.5, effort medium, raw HTTP,
  `anthropic-version: 2026-10-21` (no temperature). Tool loop up to 10 rounds; Claude gets Schoolhelp's own
  tools (`app_call`, `app_run`, `app_tools` from tools/list) and the launcher runs them through the existing
  `McpClient` (additive `listTools`/`callTool`; connection code untouched). Images pass through. Cache
  breakpoints: system block 1 and the last message. Gemini answers when no Claude key, or when Claude is
  out of credit / overloaded / offline (with a note). Spend estimate per calendar month in prefs
  `schoolbrief_claude`, shown as "Claude · ~$x tento mesiac" in the chat header.
- Key: Settings > Škola (Brief block settings), `SecretStore.ClaudeKey`. Gemini stays for brief and news.
- Unverified: a real Claude call (no key here), cost per question on real use.

## 5a. Round 22

- Schoolhelp login: `SchoolhelpAuth.token()` refreshes single-flight (Mutex); logout only on
  invalid_grant for the stored refresh token or invalid_client; other failures are transient. Refresh
  tokens are written with commit(). A second 401 after a fresh token is a transient error in McpClient.
  (Cause of "asks to log in 5x a day": parallel refreshes with the same single-use refresh token.)
- Wide dashboard always 2 columns (rotation no longer changes the count). Sheets open fully expanded.
- Focus: home pill (ring, countdown, task) instead of a text line; `FocusWidget` block (type school_focus).
- Brief: greeting with the name from Settings > Škola (`Personal`), health line Wed, Thu, Sat, Sun, short
  on Fri. News topics `sk, eu, tech, aviation` (Slovak feeds; EU ranked by Slovak angle).
- Chat: recent chats (`ChatHistory`, last 10, on the phone), conversation up to 62% of the screen height.
- Next round (agreed with the user): merge the right panel's two pages into one page of blocks with an
  app-grid block (chosen apps, drag reorder, big icons; also the "4 apps" widget); clearer widget
  settings (tile panel options).

## 5a2. Round 23

- Right panel is one scrolling page (no pager). Apps live in `AppGridWidget` blocks (type school_apps,
  config = app keys in order; `AppGrid.kt`: big icons, hold+drag to move, hold for options, "+" adds;
  settings: `ConfigureAppGrid`). ForkSetup v17 moved the old apps page's apps into one such block at the
  end of the panel (old `AppsPage.kt` removed; prefs "apps_page" read once).
- Focus and apps blocks are in WidgetItem's `schoolBlock` list (no Kvaesitso card behind them).

## 5a3. Round 25

- Pending queue: a tool error (IllegalStateException "Schoolhelp: ...") drops a change only on its third
  refusal (`PendingOp.refusals`); before, an outage answered with a tool error dropped tasks. A structured
  result with "error" is an error. A successful lessons.status flushes the queue at once. today.read is
  parsed inside the runCatching. `retryNow()` ("Skúsiť znova teraz" on Môj deň and in settings).
- Settings show the last Schoolhelp answer time and last error (`lastContact`, `lastProblem`).
- RecordingParser also searches the whole status for a lesson-like object (`deepFind`); the real shape of
  a recording on ANOTHER device is still unknown: check lessons.status live during one.
- Week strip opens Schoolhelp at `SchoolhelpUrl + "kalender"` (guessed path; unverified).

## 5a4. Round 26 (Schoolhelp reliability)

- Root cause of "nothing syncs, try again does nothing": MCP answers arrive as SSE; `Http.request` read
  to EOF and keep-alive pings reset the read timeout, so a call could hang for ever while holding
  `syncLock`. Now: hard deadline per request (watchdog disconnects), SSE read until the event that
  answers the request id (`stopWhen`), `sync()` bounded to 90 s, one MCP call at a time (`callLock`).
  Tested in `HttpDeadlineTest` with a local server.
- Login: two rejections in a row (fresh token refused, or refresh refused) log out, so the app asks to
  reconnect instead of failing silently; accepted calls reset the count.
- `SyncRow`: try again with visible state (sending, sent, error) or "reconnect" when logged out.

## 5b. Design language: "instrument panel" (round 21)

Monochrome ink on the wallpaper or one surface; the accent means only "now / progress"; hairlines (1dp,
onSurface 10%) part things instead of boxes; numbers are the heroes (Display, tabular). One motif: the
**dot / segment** for time. Keep new UI inside these rules:
- Tiles: `ChipTiles` is one panel (one surface, cells parted by hairlines, `LocalInPanel`), not a box
  per value. Rings are thin (3dp); progress is a 2dp line.
- Time: `DayRuler` in the lesson block (lessons as segments, breaks as gaps, the running one filling with
  the accent); `CountdownDots` on the test tile (one dot per day, the test day in the accent); the week
  strip's due dots. New time displays should use the same dot or segment, not new shapes.
- Bars and pills: `pillSurface` everywhere (Bars.kt). Sheets for detail (revision sheet), not new pages.

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
