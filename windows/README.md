# Spotlight for Windows

**by NeverSoft Services**

A native Windows port of the [Spotlight](../README.md) Android app — search your **entire
PC** from one black search bar, with broad filters to narrow what you're looking for. Press
**`Alt`+`Space`** anywhere and a Spotlight-style overlay drops in; type a few characters and
matching apps, photos, videos, audio, documents, downloads, archives, programs, hidden files
and contacts surface instantly, ranked best-match-first.

> This folder is a **standalone Windows app**. It does not touch the Android sources in
> [`../app`](../app) — it's a faithful re-implementation of the same idea on .NET / WPF.

---

## How it maps to the Android app

| Android | Windows |
| ------- | ------- |
| 2×4 home-screen **widget** that opens search | Global **`Alt`+`Space`** hotkey + tray icon that opens the overlay |
| Black card, **"NeverSoft Services"** in red, search bar, filter chips | Same — black acrylic card, red brand, search bar, two-tier chips |
| Apps via `PackageManager` (launcher activities) | Apps via the shell **AppsFolder** (Win32 + Store apps) |
| Media via `MediaStore` | Media via a time-boxed walk of your Pictures / Videos / Music |
| Files via filesystem walk / `MediaStore.Files` | Files via an in-memory **index** of your known folders |
| Contacts via `ContactsContract` | Contacts via `%USERPROFILE%\Contacts` (`.contact` files) |
| Tap a result → open / launch | Click or `Enter` → open / launch (`ShellExecute`) |

The **filters are identical** and each one only searches what it should — *All* mixes apps,
contacts, media and files; *Media* never walks for documents; *Files* never lists apps; etc.

| Filter | Searches | Sub-filters |
| ------ | -------- | ----------- |
| **All** | Everything, ranked together | — |
| **Media** | Photos, videos, audio | Photos · Videos · Audio |
| **Files** | Documents, downloads, archives, programs, any file | Documents · Downloads · Archives · **Programs** |
| **Hidden** | Hidden files & folders (`.`-prefixed or Hidden attribute) | Hidden files · Hidden folders |

> The Android **"APKs"** sub-filter is localized to **"Programs"** on Windows
> (`.exe`/`.msi`/`.bat`/…), since APKs are an Android concept.

## Searching is by name, and narrows live

Matching is always on the **file/folder name** as a **substring** — you never type a path.
Type `.` and everything whose name contains a dot appears; add more characters and the list
**shrinks** as the query gets more specific. To make that instant and deterministic, Spotlight
builds an in-memory index of your known folders once in the background (Desktop, Documents,
Downloads, Pictures, Videos, Music, OneDrive, and your profile root), then filters it in memory
on every keystroke. Until the index finishes building, search falls back to a live, time-boxed
walk so it works from the very first keystroke.

Results are ranked exactly like the Android app — **exact match → prefix → substring**, with
apps and contacts floated to the top of mixed results — plus a personalization tiebreaker that
nudges the things you launch most toward the top.

## Keyboard

| Key | Action |
| --- | ------ |
| `Alt`+`Space` | Show / hide Spotlight (global) |
| `↑` / `↓` | Move selection |
| `Enter` | Open / launch the selected result |
| `Ctrl`+`Enter` | Reveal the selected file/folder in Explorer |
| `Ctrl`+`Shift`+`C` | Copy the selected item's path |
| `Alt`+`1…4` | Jump to the All / Media / Files / Hidden filter |
| `Esc` | Hide Spotlight |

## The "luxury" layers

- **Acrylic blur** backdrop (Windows 10/11) with rounded corners, a drop shadow and a smooth
  fade-in — falls back gracefully to a translucent card if blur is unavailable.
- **Real shell icons & thumbnails** for files (image/video thumbnails included), loaded lazily
  and cached; vector glyphs show until/unless a real icon is available.
- **Frecency** — a small usage store remembers what you open and personalizes ranking.
- **Persisted settings** (`%AppData%\NeverSoft\Spotlight\settings.json`) and an optional
  **Launch at startup** toggle (tray menu).
- **Single instance** — launching again just re-summons the running window.
- Lives in the **system tray**; right-click for *Open*, *Launch at startup*, *Exit*.

## Building & running

Requires the **.NET 8 SDK** on **Windows** (WPF is Windows-only).

```powershell
cd windows
dotnet run --project Spotlight
```

Or open `windows/Spotlight.sln` in **Visual Studio 2022** (17.8+) and press F5.

To produce a self-contained executable:

```powershell
dotnet publish Spotlight/Spotlight.csproj -c Release -r win-x64 ^
  --self-contained true -p:PublishSingleFile=true -o publish
```

Then run `publish\Spotlight.exe`. It starts in the tray and shows the overlay; press
`Alt`+`Space` any time to bring it back.

## Project layout

```
windows/Spotlight/
├── App.xaml(.cs)                 # tray app, single-instance, services, index build
├── MainWindow.xaml(.cs)          # the overlay: search bar + two-tier chips + results
├── app.manifest                  # per-monitor DPI, asInvoker
├── Models/
│   ├── Filters.cs                # PrimaryFilter / SubFilter / FilterSelection
│   └── SearchResult.cs           # result model + ResultType + LaunchAction
├── Search/
│   ├── SearchEngine.cs           # routes a query by filter, ranks, personalizes, dedupes
│   ├── FileIndex.cs              # in-memory name index → instant live narrowing
│   ├── FileWalker.cs             # time-boxed fallback walk + FileSearchSpec
│   ├── AppSearchProvider.cs      # shell AppsFolder enumeration
│   ├── MediaSearchProvider.cs    # images / video / audio
│   ├── FileSearchProvider.cs     # files & folders
│   ├── ContactSearchProvider.cs  # %USERPROFILE%\Contacts
│   ├── FileTypes.cs              # extension → type classification
│   ├── KnownFolders.cs           # which folders get searched
│   └── Launcher.cs               # open / launch / reveal / copy-path
├── Services/
│   ├── Settings.cs               # persisted user settings
│   ├── UsageStore.cs             # frecency tracking
│   ├── StartupRegistration.cs    # launch-at-login (Run key)
│   └── AppPaths.cs               # %AppData% storage location
├── Interop/
│   ├── NativeMethods.cs          # Win32 / shell P/Invoke
│   ├── WindowEffects.cs          # acrylic blur, rounded corners, dark frame
│   └── ShellIcon.cs              # icons & thumbnails → ImageSource
├── Ui/
│   ├── ResultRowViewModel.cs     # row VM + lazy icon load + size/date meta
│   ├── Converters.cs             # type → glyph / label, null → visibility
│   └── Glyphs.cs                 # vector icon geometries
└── Themes/
    ├── Colors.xaml               # the black/red NeverSoft palette
    └── Styles.xaml               # chips, search box, list rows, scrollbar
```

## Tech

- **C# / .NET 8**, **WPF** (Windows-only), View-based UI with code-behind and a tiny bit of MVVM.
- **async/await + `CancellationToken`** — each keystroke (debounced ~110 ms) cancels the previous
  search; the index build and any fallback walk are time-boxed so the UI never blocks.
- Targets **Windows 10/11**. No third-party NuGet dependencies.

## Notes & limitations

- Search covers your **known folders** (the places your content lives), not every byte of every
  drive — this keeps it instant without depending on the Windows Search service. Widen the set in
  `Search/KnownFolders.cs` if you want more reach.
- Real **app icons** aren't extracted from the AppsFolder (apps show the app glyph); files and
  folders get real shell icons and thumbnails.
- Acrylic/rounded-corner effects are best-effort and degrade cleanly on older Windows builds.
