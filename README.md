# Spotlight

**by NeverSoft Services**

An enhanced, Android-native "spotlight" — search your **entire device** from one
black search bar, with broad filters to narrow what you're looking for. Ships as
a **2×4 home-screen widget** (a Google-style search bar with filter chips) that
opens a full-screen search experience.

> iPhone Spotlight, but device-wide and filterable: type anything and Spotlight
> surfaces matching apps, photos, videos, audio, documents, downloads, archives,
> APKs, hidden files and contacts — wherever they live on the phone.

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

> Note: this repo was scaffolded in an environment without the Android SDK, so the
> APK has not been compiled here — build it in Android Studio (which provides the
> SDK). The Gradle wrapper, manifest, sources and resources are all in place.
