# Build Plan: Meridian — Custom Wear OS Watch Face (WFF)

**Project name:** Meridian
**Repo:** `meridian`
**Working app title:** Meridian

## Objective

Build a Wear OS **Watch Face Format (WFF)** watch face ("Meridian") that replicates the visual layout of the stock Pixel "Adventure / Digital Arcs" face, with two intentional changes:

1. **Remove the center "DIGITAL MODE" text label** entirely (it is decorative and non-functional).
2. **Replace the outer tick ring with a battery-level arc** (partial circle, like the built-in battery gauge), keeping the center of the face clean — time only.

This is a personal-use face first (sideload), with an option to publish to the Play Store later.

**Implementation approach — WFF (decided).** Built as declarative WFF, not the canvas/AndroidX (Jetpack Watch Face) API. Rationale: everything Meridian does — digital time, day/date, two complication slots, battery arc with threshold colors — is inside WFF's wheelhouse, and WFF gets Google's renderer to handle power/ambient/burn-in optimization for free on a daily-wear face. Canvas would only win if Meridian needed arbitrary tap regions, unconstrained animation, or was intended as a learning vehicle for the low-level API — none of which apply. If that ever changes, flipping to canvas would change the base sample, the CI (real Kotlin unit tests return, WFF validators drop), and the deliverables.

## Target platform

- **Format:** Watch Face Format (WFF), declarative XML. No executable/canvas rendering code.
- **WFF version:** declare `com.google.wear.watchface.format.version = 2` in the manifest with a matching `minSdk 34`. Google's guidance is to declare the lowest version that covers the features used; v2 is forced by the `HEART_RATE` default provider (a v2 addition) — everything else here (tag expressions, `Arc` + `Transform`, `Condition`, `TextCircular`, `BoundingArc`, color configuration) is v1. Bump further only if a feature demands it (ambient transitions/photos → v4; latest is v5).
- The manifest must also set `android:hasCode="false"` on `<application>` (WFF packages are resource-only) — the sample already does this; keep it.
- **Primary device:** Pixel Watch 4, 45mm. With WFF v2 / minSdk 34 it also runs on any Wear OS 5+ device (all Pixel Watches — PW1/2 received Wear OS 5).
- **Design canvas:** 450 x 450 (WFF standard design space; scales to physical resolution).

## Base project

Start from the official sample, which is Apache-2.0 and already has correct Gradle/manifest wiring:

- Repo: `android/wear-os-samples`
- Module: `WatchFaceFormat/SimpleDigital`
- Face definition lives at: `watchface/src/main/res/raw/watchface.xml`
- Metadata: `watchface/src/main/res/xml/watch_face_info.xml`, plus `AndroidManifest.xml`

Copy that module out as the project skeleton and replace `watchface.xml` with the implementation below. Reference `WatchFaceFormat/Complications` in the same repo for exact `ComplicationSlot` syntax.

## Repo conventions & CI/CD — mirror the regatta-timer repo

**Before scaffolding, check out `johnhringiv/regatta-timer` via `gh`** (`gh repo clone johnhringiv/regatta-timer`). It is an existing, shipping **Wear OS Gradle/Kotlin app by the same owner**, so its structure and CI transfer almost directly to this project — reuse it rather than inventing conventions. Match the following:

**Structure to mirror:**

- Root Gradle Kotlin DSL: `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradlew`, `gradle/`. App module under `app/`.
- `.githooks/pre-commit` (prettier auto-format of Markdown, re-stage). Enable with `git config core.hooksPath .githooks`.
- `.gitattributes` — LF everywhere, CRLF for `*.bat`, binary flags for `*.png *.apk *.aab *.jks` etc. Copy as-is.
- `.github/PULL_REQUEST_TEMPLATE.md`, `.github/dependabot.yml`, `README.md` with an Actions status badge, and a `PRD.md`-style product doc if useful.
- `playstore/` folder for store assets (Phase 2).
- Version fields live in `app/build.gradle.kts` as `versionCode` (int) and `versionName` (string).

**CI to mirror (adapt `.github/workflows/android.yml`):** its job graph is exactly what this project wants —

1. **`version-check`** (PR only, skips dependabot): fails the PR unless `versionCode` is bumped vs. the base branch **and** `versionName` changes once per PR to main. Keep this.
2. **`docs-format`**: prettier `--check` on Markdown.
3. **`build`**: Temurin JDK 21, `gradle/actions/setup-gradle`, restore release keystore from `secrets.KEYSTORE_B64` into `keystore.properties`, run unit tests + `assembleRelease`, upload artifact(s). **Adapt for WFF:** add the **WFF format validator + memory-footprint validator** as build/test steps so an invalid or over-budget face fails CI. The build artifact is the watch-face APK.
4. **`release`** (push to main only): download the built artifact, derive version from `app/build.gradle.kts`, and `gh release create` a tagged release. Note the repo's convention: **the squash-merge commit body becomes the release changelog** (`git log -1 --format=%b > notes.md`).

**What differs from regatta-timer:** that app is hand-written Kotlin with unit tests; this is a declarative WFF face. So drop app-logic unit tests in favor of the WFF validators, and there's no `pages.yml` equivalent needed here. Everything else (versioning discipline, keystore flow, release automation, hooks, gitattributes) carries over unchanged.

## Git workflow

- **`main` stays clean and releasable.** Every version is a single squashed commit on `main`.
- Develop **v0.1 on a feature branch** (e.g. `feat/wff-face-v0.1`). All iteration, WIP, and fixups happen there.
- Open a PR into `main`; let CI (`version-check`, `docs-format`, `build` + validators) gate it.
- **Squash-merge** into `main` so v0.1 lands as one commit. Write the PR body as the intended changelog, since the release workflow uses the squash commit body as release notes.
- Set `versionCode = 1`, `versionName = "0.1"` for this first cut (first merge to an empty base skips the bump check per the workflow's guard).
- **Package / applicationId:** use a namespaced ID consistent with the owner's convention (e.g. `com.johnhringiv.meridian`) so it doesn't collide and reads cleanly in the manifest and any future store listing.
- Tag/release is produced automatically by the `release` job on the push to `main`.

## Visual spec

### Center — time

- Digital time, `hh:mm`, `hourFormat="SYNC_TO_DEVICE"` (respect the device 12/24h setting).
- Center-aligned, large (~120px), positioned in the vertical middle.
- **Two variants:** interactive = `NORMAL` weight; ambient (AOD) = `THIN` weight, via `<Variant mode="AMBIENT" .../>` on alpha/font.
- No seconds.

### Day + date row

- A single centered text row below the time showing day-of-week + month + day (e.g., `TUE  JUL 14`), sourced from WFF date tags (`[DAY_OF_WEEK_S]`, `[MONTH_S]`, `[DAY]`).
- **Do NOT render any center mode label.** This is the whole point — the "DIGITAL MODE" element from the stock face is simply never authored.
- Dim slightly in ambient.

### Outer rim — battery arc (replaces the tick ring)

- **Remove the full minute/tick ring.**
- Draw a **battery gauge as an arc around the rim**:
  - A faint background "track" arc (full sweep).
  - A foreground arc whose **sweep length is proportional to the current battery level** (`level% -> proportion of the arc`).
  - Recommended geometry: a full 360° ring (it inherits the tick ring's framing role) OR a gapped arc (e.g., ~300° with a gap at the bottom) if a full ring reads too heavy. Agent's choice; make it a single easily-tweaked parameter.
- **Threshold color** on the foreground arc via a `Condition`/`Compare` block on the battery level:
  - healthy (default, e.g. copper/green),
  - warning under ~30% (amber),
  - critical under ~15% (red).
- **Ambient variant:** thinner and/or dimmer arc in AOD to avoid a bright always-lit ring (burn-in / power).
- **Data source (confirmed against docs):** `[BATTERY_PERCENT]` (0–100) is a WFF v1 data source. Drive the arc with the documented pattern — an `Arc` whose `endAngle` is recomputed by a child transform: `<Transform target="endAngle" value="[BATTERY_PERCENT] * 3.6"/>`. WFF angles are measured clockwise with 0° at 12 o'clock. See the skeleton in the appendix.

### Complication slots (user-assignable)

- Two circular complication slots below the time (side by side), matching the stock layout's lower complications.
- **`supportedTypes` must match rendered types.** Every type listed in `supportedTypes` needs its own `<Complication type="...">` renderer block inside the slot, or that type renders blank when the user picks it. The official `WatchFaceFormat/Complications` sample has a renderer per supported type — copy its `RANGED_VALUE`, `SMALL_IMAGE`, and `MONOCHROMATIC_IMAGE` blocks (Apache-2.0) rather than writing them from scratch, or trim `supportedTypes` to what's actually rendered.
- Recommended v1 set: `RANGED_VALUE SHORT_TEXT MONOCHROMATIC_IMAGE SMALL_IMAGE EMPTY` with all renderers copied from the sample.
- These stay user-selectable in the watch's face editor (e.g. heart rate, steps). Battery does NOT need a slot since it's on the rim.
- SHORT_TEXT renders `[COMPLICATION.TEXT]` and, when present, `[COMPLICATION.MONOCHROMATIC_IMAGE]` (SHORT_TEXT data may carry an icon).
- Consider a `DefaultProviderPolicy` per slot (e.g. steps left, heart rate right) so the face isn't empty on first selection; verify exact attribute names against the WFF reference — the Complications sample omits it, so slots default to empty otherwise.
- **Out of scope for v1:** the curved rim "arc" complications from the stock face. The rim is used by the battery arc in this design; adding rim arc-complications later would require making the battery a dedicated segment. Leave as a future option.

### Theme / color

- Copper-forward palette. Placeholders: primary `#E8B18A`, secondary/dim `#C08552`, background `#000000`.
- **Preferred:** expose a user-selectable theme color via WFF configuration (`[CONFIGURATION.themeColor.N]`) so color isn't hard-coded. If time-constrained, hard-code the copper palette for v1 and make configurability a follow-up.

### Ambient (AOD) behavior

- Thin time, dimmed date, thinner/dimmer battery arc.
- Minimize lit pixels overall. Every persistent element needs an `AMBIENT` variant.

## Validation (do this before every flash)

- Both validators live in **github.com/google/watchface**:
  - **Format (XSD) validator** — `third_party/wff/` — checks `watchface.xml` against the declared WFF schema version.
  - **Memory footprint evaluator** — `play-validations/` — runs against the built APK; WFF enforces a memory budget (face is rejected at publish if over).
  - Optional: **`tools/wff-optimizer/`** shrinks XML/resources if the footprint check gets tight.
- Wire both into the Gradle build / CI so a schema typo or budget overage fails fast rather than silently not rendering on-device. Confirm exact jar invocation from each tool's README when wiring.

## Build & deploy

### Phase 1 — Sideload (initial, this is v0.1)

1. Build via Gradle wrapper -> APK (same `assembleRelease` path the CI `build` job uses; local debug build is fine for iteration).
2. Enable wireless debugging on the watch (developer options).
3. `adb connect <watch-ip>` then `adb install <apk>`.
4. Select and customize the face on-device (assign the two complications).

- Note: no companion-app sync for sideloaded faces; expect an edit → build → validate → reinstall loop.
- The CI `release` job already attaches the built APK to a GitHub release on merge to `main`, so the sideload artifact is produced automatically too.

### Phase 2 — Play Store (optional, later)

- A verified Play Console developer account is already in place.
- Package as AAB, pass WFF format + memory validation, complete store listing, content rating, data-safety, submit for review. Reuse the `playstore/` asset folder convention from regatta-timer.
- The keystore flow is already modeled by the regatta-timer `android.yml` (`KEYSTORE_B64` secret -> `keystore.properties`); reuse it for signed release builds.
- **Naming/trademark:** the name "Meridian" is clear of Google's "Pixel"/"Adventure" marks (do not use those anywhere in the listing). Before publishing, re-verify "Meridian" isn't trademark-crowded in the watch-face / app space (it's a common word with existing users, e.g. Meridian Audio) — the repo name is unaffected since it's namespaced to the account, but the public store title should be checked. Fall back to a distinct title if needed.

## Constraints & gotchas

- **No live preview.** WFF has no design-time render; iterate by building and flashing. `PREVIEW_TIME` metadata only sets the static store thumbnail.
- **~15fps cap** on WFF animation. Fine for a mostly-static digital face.
- **Memory budget** is enforced at validation/publish — keep image assets lean.
- **No executable code / no tap-to-launch on plain elements.** Interactive launch is only available via complication slots. Not needed for this design.
- Battery-arc sweep math (confirmed): `<Transform target="endAngle" value="[BATTERY_PERCENT] * 3.6"/>` on an `Arc`. For a gapped arc, scale by the gap sweep instead (e.g. `startAngle="-150"`, `value="-150 + [BATTERY_PERCENT] * 3.0"` for 300°).
- Threshold colors can't be transformed on a `Stroke` in v1 — branch instead with `Condition`/`Expressions`/`Compare`/`Default` (each `Expression` gets a `name`; wrap comparisons in CDATA). Three arc variants, one per color band.
- A `supportedTypes` entry without a matching `<Complication type>` renderer block renders blank — keep them in lockstep.

## Acceptance criteria

1. Face builds, passes format + memory validation, installs via `adb`, and is selectable on a Wear OS 6 watch.
2. Center shows time only — no "DIGITAL MODE" or any mode label anywhere.
3. Outer rim shows a battery arc that visibly tracks charge level and changes color at the warning/critical thresholds; no tick ring present.
4. Two lower circular complication slots are user-assignable and render assigned data.
5. Ambient mode renders a thin/dimmed version of all persistent elements.
6. Copper theme applied (configurable if feasible).
7. Repo mirrors regatta-timer conventions (Gradle KTS, `.githooks`, `.gitattributes`, PR template, dependabot, Actions badge); `android.yml`-style CI with `version-check`, `docs-format`, `build` (WFF validators wired in), and `release` jobs passing.
8. v0.1 lands on a clean `main` as a **single squash-merged commit** (`versionCode = 1`, `versionName = "0.1"`), via a PR from `feat/wff-face-v0.1` with CI green; the PR body reads as the release changelog.

## References

- WFF overview + element docs: developer.android.com/training/wearables/wff
- WFF version feature matrix: developer.android.com/training/wearables/wff/release-notes
- Transforms (arc/endAngle pattern): developer.android.com/training/wearables/wff/transform
- Condition element reference: developer.android.com/reference/wear-os/wff/common/condition
- Validators + optimizer: github.com/google/watchface (`third_party/wff/`, `play-validations/`, `tools/wff-optimizer/`)
- Sample faces (Apache-2.0): github.com/android/wear-os-samples -> WatchFaceFormat/{SimpleDigital, Complications}
- Battery tag `[BATTERY_PERCENT]`, arc transform, and Condition syntax verified against these docs 2026-07-15; re-check `DefaultProviderPolicy` attribute names at implementation time.

## Appendix — starting watchface.xml (time + day/date + two slots; battery ring to be added per spec above)

Use this as the confirmed-correct base for the parts it covers, then finish theming on top. Syntax for TimeText, Variant, and ComplicationSlot below is verified against the official samples; the battery-arc skeleton uses the documented `Arc`/`Transform` pattern and `Condition` branching (exact geometry/thickness to be tuned on-device). Slots here render SHORT_TEXT only — before widening `supportedTypes`, copy the per-type renderer blocks from the `WatchFaceFormat/Complications` sample.

```xml
<?xml version="1.0"?>
<WatchFace width="450" height="450">
  <Metadata key="CLOCK_TYPE" value="DIGITAL"/>
  <Metadata key="PREVIEW_TIME" value="10:14:00"/>
  <Scene backgroundColor="#ff000000">

    <!-- Center time: bold interactive / thin ambient -->
    <DigitalClock x="0" y="0" width="450" height="450">
      <TimeText format="hh:mm" hourFormat="SYNC_TO_DEVICE" align="CENTER"
                x="0" y="120" width="450" height="130" alpha="255">
        <Variant mode="AMBIENT" target="alpha" value="0"/>
        <Font family="SYNC_TO_DEVICE" size="120" weight="NORMAL" slant="NORMAL" color="#ffE8B18A"/>
      </TimeText>
      <TimeText format="hh:mm" hourFormat="SYNC_TO_DEVICE" align="CENTER"
                x="0" y="120" width="450" height="130" alpha="0">
        <Variant mode="AMBIENT" target="alpha" value="255"/>
        <Font family="SYNC_TO_DEVICE" size="120" weight="THIN" slant="NORMAL" color="#ffE8B18A"/>
      </TimeText>
    </DigitalClock>

    <!-- Day + date row (no mode label) -->
    <Group x="0" y="250" width="450" height="40" name="day_date_row">
      <PartText x="0" y="0" width="450" height="40">
        <Variant mode="AMBIENT" target="alpha" value="180"/>
        <Text align="CENTER">
          <Font family="SYNC_TO_DEVICE" size="26" color="#ffC08552">
            <Template><![CDATA[%s  %s %s]]>
              <Parameter expression="[DAY_OF_WEEK_S]"/>
              <Parameter expression="[MONTH_S]"/>
              <Parameter expression="[DAY]"/>
            </Template>
          </Font>
        </Text>
      </PartText>
    </Group>

    <!-- Outer rim battery arc: faint full track + level-driven foreground arc
         with threshold colors. Full-ring geometry; for a gapped arc change
         startAngle and the multiplier (see gotchas). -->
    <Group x="0" y="0" width="450" height="450" name="battery_ring">
      <Arc centerX="225" centerY="225" width="430" height="430" startAngle="0" endAngle="360">
        <Variant mode="AMBIENT" target="alpha" value="60"/>
        <Stroke color="#40C08552" thickness="8" cap="ROUND"/>
      </Arc>
      <Condition>
        <Expressions>
          <Expression name="critical"><![CDATA[[BATTERY_PERCENT] <= 15]]></Expression>
          <Expression name="warning"><![CDATA[[BATTERY_PERCENT] <= 30]]></Expression>
        </Expressions>
        <Compare expression="critical">
          <Arc centerX="225" centerY="225" width="430" height="430" startAngle="0" endAngle="0">
            <Transform target="endAngle" value="[BATTERY_PERCENT] * 3.6"/>
            <Variant mode="AMBIENT" target="alpha" value="120"/>
            <Stroke color="#ffE05252" thickness="8" cap="ROUND"/>
          </Arc>
        </Compare>
        <Compare expression="warning">
          <Arc centerX="225" centerY="225" width="430" height="430" startAngle="0" endAngle="0">
            <Transform target="endAngle" value="[BATTERY_PERCENT] * 3.6"/>
            <Variant mode="AMBIENT" target="alpha" value="120"/>
            <Stroke color="#ffE0A048" thickness="8" cap="ROUND"/>
          </Arc>
        </Compare>
        <Default>
          <Arc centerX="225" centerY="225" width="430" height="430" startAngle="0" endAngle="0">
            <Transform target="endAngle" value="[BATTERY_PERCENT] * 3.6"/>
            <Variant mode="AMBIENT" target="alpha" value="120"/>
            <Stroke color="#ffE8B18A" thickness="8" cap="ROUND"/>
          </Arc>
        </Default>
      </Condition>
    </Group>

    <!-- Left circular complication (user-assignable) -->
    <ComplicationSlot x="100" y="300" width="110" height="110" slotId="0"
                      displayName="left_circle"
                      supportedTypes="SHORT_TEXT EMPTY">
      <BoundingOval x="0" y="0" width="110" height="110" outlinePadding="2.0"/>
      <Complication type="SHORT_TEXT">
        <PartText x="0" y="30" width="110" height="50">
          <Text align="CENTER" ellipsis="TRUE">
            <Font family="SYNC_TO_DEVICE" size="28" color="#ffE8B18A">
              <Template><![CDATA[%s]]>
                <Parameter expression="[COMPLICATION.TEXT]"/>
              </Template>
            </Font>
          </Text>
        </PartText>
      </Complication>
    </ComplicationSlot>

    <!-- Right circular complication (user-assignable) -->
    <ComplicationSlot x="240" y="300" width="110" height="110" slotId="1"
                      displayName="right_circle"
                      supportedTypes="SHORT_TEXT EMPTY">
      <BoundingOval x="0" y="0" width="110" height="110" outlinePadding="2.0"/>
      <Complication type="SHORT_TEXT">
        <PartImage x="38" y="10" width="34" height="34">
          <Image resource="[COMPLICATION.MONOCHROMATIC_IMAGE]"/>
        </PartImage>
        <PartText x="0" y="48" width="110" height="45">
          <Text align="CENTER" ellipsis="TRUE">
            <Font family="SYNC_TO_DEVICE" size="26" color="#ffE8B18A">
              <Template><![CDATA[%s]]>
                <Parameter expression="[COMPLICATION.TEXT]"/>
              </Template>
            </Font>
          </Text>
        </PartText>
      </Complication>
    </ComplicationSlot>

  </Scene>
</WatchFace>
```
