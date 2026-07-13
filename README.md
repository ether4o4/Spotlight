# Spotlight

**by NeverSoft Services**

An enhanced, Android-native "spotlight" — search your **entire device** from one
black search bar, with broad filters to narrow what you're looking for. Ships as
a **2×4 home-screen widget** (a Google-style search bar with filter chips) that
opens a full-screen search experience.

> iPhone Spotlight, but device-wide and filterable: type anything and Spotlight
> surfaces matching apps, photos, videos, audio, documents, downloads, archives,
> APKs, hidden files and contacts — wherever they live on the phone.

**Also available for Windows** — same idea, plus deep note search (Sticky Notes,
Notepad tabs, markdown): see [Spotlight for Windows](#spotlight-for-windows-desktop).

---

## The widget (2×4)

A black `2×4` home-screen widget:

```
┌─────────────────────────────────────────────┐
│ SPOTLIGHT                   NeverSoft Services│  ← brand, red, top-right
│ ┌─────────────────────────────────────────┐ │
│ │ 🔍  Search your entire device           │ │  ← tap → opens search, keyboard up
│ └─────────────────────────────────────────┘ │
│ [  All  ] [ Media ] [ Files ] [ Hidden ]     │  ← filter chips (pre-filter on tap)
└─────────────────────────────────────────────┘
```

- **Black** background, rounded.
- **"NeverSoft Services"** in **red**, pinned to the top-right corner (small).
- A search bar that looks like a browser/Google search bar.
- The four primary filter chips underneath. Tapping the bar opens Spotlight ready
  to type; tapping a chip opens it pre-filtered to that category.

## Filters

Filters come in two tiers, exactly as described:

**Primary (broad) — under the search bar:**

| Filter | What it searches |
| ------ | ---------------- |
| **All** | Everything: apps, contacts, media and files across the device |
| **Media** | Photos, videos and audio (via the system MediaStore) |
| **Files** | Documents, downloads, archives, APKs and any other file |
| **Hidden** | Hidden files & folders (names starting with `.`) |

**Secondary (narrows the primary) — appears when relevant:**

- **Media →** Photos · Videos · Audio
- **Files →** Documents · Downloads · Archives · APKs
- **Hidden →** Hidden files · Hidden folders

Pick a primary filter and the matching secondary row slides in to refine results.

## Searching the whole phone

Typing a query runs a device-wide search that aggregates several providers and
ranks the combined results (exact match → prefix → substring, with apps and
contacts floated to the top in mixed results):

- **Apps** — installed, launchable apps (`PackageManager`)
- **Media** — images / video / audio (`MediaStore`)
- **Files** — full filesystem walk when *All files access* is granted; otherwise
  the indexed `MediaStore.Files` table
- **Contacts** — device contacts (`ContactsContract`)

Tap a result to open it: apps launch, files/media open in their default app (via a
`FileProvider`), contacts open in the contacts app.

---

## Project layout

```
app/src/main/
├── AndroidManifest.xml
├── java/com/neversoft/spotlight/
│   ├── SearchActivity.kt              # search UI: bar + two-tier chips + results
│   ├── model/
│   │   ├── Filters.kt                 # PrimaryFilter / SubFilter / FilterSelection
│   │   └── SearchResult.kt            # result model + ResultType + LaunchAction
│   ├── search/
│   │   ├── SearchEngine.kt            # routes a query to providers, ranks, dedupes
│   │   ├── AppSearchProvider.kt
│   │   ├── MediaSearchProvider.kt
│   │   ├── FileSearchProvider.kt      # filesystem walk + MediaStore fallback
│   │   ├── ContactSearchProvider.kt
│   │   └── FileTypes.kt               # extension → type/mime classification
│   ├── ui/
│   │   └── ResultsAdapter.kt          # RecyclerView rows (loads real app icons)
│   └── widget/
│       └── SpotlightWidgetProvider.kt # 2×4 home-screen widget
└── res/                               # black/red theme, drawables, layouts, icons
```

## Tech

- **Kotlin**, View-based UI with **Material 3** (dark), ViewBinding.
- **Coroutines** — each keystroke cancels the previous search (debounced ~220 ms),
  the filesystem walk is time-boxed so the UI never blocks.
- min SDK **26**, target/compile SDK **34**. AGP 8.6.1 / Gradle 8.7 / Kotlin 1.9.24.

## Permissions

Spotlight requests these so it can actually see the whole device:

- `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` / `READ_MEDIA_AUDIO` (Android 13+) or
  `READ_EXTERNAL_STORAGE` (Android ≤ 12) — media search
- `MANAGE_EXTERNAL_STORAGE` (*All files access*) — full filesystem walk, including
  hidden files. Granted from the in-app banner → system settings screen.
- `READ_CONTACTS` — contact results
- `QUERY_ALL_PACKAGES` — list every installed app

Media and contact permissions are requested on first launch; *All files access* is
offered via a banner because it requires the system settings screen.

## Building

Open the project in **Android Studio** (Koala / Ladybug or newer) and Run, or from
the command line:

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Then add the **Spotlight Search** widget from your launcher's widget picker.

> Don't want to build it yourself? Every push to `main` is compiled by CI and the
> resulting APK is published to the rolling download link below.

---

## Download

[![Download APK](https://img.shields.io/badge/Download-Spotlight%20APK-E53935?logo=android&logoColor=white&style=for-the-badge)](https://github.com/ether4o4/Spotlight/releases/download/nightly/spotlight-debug.apk)

**📲 [Download the latest Spotlight APK](https://github.com/ether4o4/Spotlight/releases/download/nightly/spotlight-debug.apk)**

This is a **rolling** link — CI rebuilds the app on every push to `main` and
re-publishes it to the `nightly` release, so the link above always serves the
**newest** build as a direct `.apk` (no zip to unpack).

> Debug-signed. On your phone, enable *Install unknown apps* for your browser or
> file manager, then open the APK. Requires **Android 8.0+** (minSdk 26).

---

## Spotlight for Windows (desktop)

The same idea, ported to the PC: one dark window, one search bar, filter chips,
and a list that starts **full** (browse everything) and **narrows as you type**.
Lives in `desktop/` as a standalone **Compose for Desktop** (Kotlin/JVM) app.

**What it searches:**

| Source | Depth |
| ------ | ----- |
| **Apps** | Installed applications (Start Menu shortcuts, all-users + per-user) |
| **Files & folders** | Name index of your whole user profile (AppData & co. excluded), incl. hidden entries |
| **Sticky Notes** | *Deep:* reads the app's own store (`plum.sqlite`) — every note searchable by its text |
| **Notepad tabs** | *Deep:* Windows 11 Notepad's TabState — including tabs you never saved |
| **Text & Markdown notes** | *Deep:* `.md` / `.txt` under Documents, Desktop and `~/Notes` (covers Obsidian-style vaults) — matched by name **and content** |

Deep matches — the query found *inside* a note rather than in its title — rank
below title matches and show an excerpt of the matching text under the result.

**Filters:** All · Apps · Files (Documents / Media / Downloads / Archives /
Folders) · Notes (Sticky Notes / Notepad tabs / Text & Markdown) · Hidden
(files / folders).

**Honest limits (v1):**

- **OneNote isn't covered yet** — its notes live behind Microsoft's cloud API
  (sign-in required); that's a separate increment.
- Notepad's TabState format is undocumented, so extraction is best-effort text
  recovery: it finds notes by their text reliably, but is not a full parser.
- Clicking a Sticky Note or Notepad tab opens the owning app — neither supports
  deep-linking to a single note.

### Download (Windows)

[![Download for Windows](https://img.shields.io/badge/Download-Spotlight%20for%20Windows-E53935?logo=windows&logoColor=white&style=for-the-badge)](https://github.com/ether4o4/Spotlight/releases/download/nightly-desktop/spotlight-windows.zip)

**🖥️ [Download the latest Windows build](https://github.com/ether4o4/Spotlight/releases/download/nightly-desktop/spotlight-windows.zip)**

Rolling link — CI rebuilds the app on every push to `main` that touches
`desktop/` and re-publishes it to the `nightly-desktop` release. Unzip, then run
`Spotlight\Spotlight.exe`.

> Unsigned build. If SmartScreen objects, choose *More info → Run anyway*.
> Requires **64-bit Windows 10/11**.

### Building the desktop app

```bash
./gradlew -p desktop run                  # run from source
./gradlew -p desktop test                 # unit tests
./gradlew -p desktop createDistributable  # portable app image
```

```
desktop/src/main/kotlin/com/neversoft/spotlight/desktop/
├── Main.kt                # Compose window: bar + two-tier chips + results
├── model/                 # filters + result model (Android parity)
├── search/                # engine + providers: apps, file index, sticky notes,
│                          #   notepad tabs, text notes
└── win/                   # well-known Windows paths + result launching
```
