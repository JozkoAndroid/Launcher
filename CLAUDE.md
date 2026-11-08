# School launcher (Kvaesitso fork): rules for Claude

Read `docs/HANDOFF.md` before working. Work on branch `brief-widgets`.

- User: terse answers, honest pushback, no em dashes, no emojis. UI text in Slovak (`values-sk`),
  English fallback in `values`.
- Design: one primary action per screen, monochrome with one accent (progress / "now"), minimal.
  Bottom bars only via `widgets/school/Bars.kt` (`PillBar`, `InkButton`, `InputBar`, `pillArriving`,
  `pillLeaving`). Fonts via `SchoolFonts` (never `FontFamily.Serif`: the phone replaces it).
- Scaffold components must apply the `modifier` passed to `Component()`. Read swipe progress only in
  draw/layout lambdas.
- Default changes go through a new `ForkSetup` version, never silently.
- Never commit keys or real Schoolhelp data. Keys stay on the phone (`SecretStore`).
- Ship: `./gradlew :app:app:assembleDefaultFork`, tests and Paparazzi verify must pass (record and verify
  in separate runs). Send the APK and say what is unverified on device.
