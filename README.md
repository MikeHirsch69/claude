# Radio Player (vibe coded)

A simple internet radio player: add stations by stream URL, give them names and custom
logos, edit them anytime, see live album art pulled from Deezer/MusicBrainz using the
stream's ICY metadata, optionally route a station's audio through an HTTP/SOCKS5 proxy,
and control it all from Android Auto.

## 1. Get this project onto GitHub

1. Go to https://github.com and create a free account if you don't have one.
2. Click "New repository" (top right, the `+` icon → "New repository"). Name it e.g.
   `radio-player`, keep it **Private** or Public, don't add a README (we already have one),
   then click "Create repository".
3. On the empty repo page, use the **"uploading an existing file"** link, then drag in the
   entire `RadioPlayer` folder you downloaded from this chat (all files including the hidden
   `.github` folder — make sure your file browser shows hidden files, or use `git` instead,
   see below). Commit directly to `main`.

   **Or, with git on your computer:**
   ```
   cd RadioPlayer
   git init
   git remote add origin https://github.com/YOUR_USERNAME/radio-player.git
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git push -u origin main
   ```

## 2. Let it build

As soon as you push to `main`, GitHub automatically starts building the APK (see
`.github/workflows/build.yml`). To watch it:

1. Open your repo on GitHub → click the **"Actions"** tab.
2. Click the running workflow ("Build APK") → click the `build` job to watch the log live.
3. It takes roughly 5–10 minutes the first time (downloading the Android SDK + Gradle).
4. When it finishes with a green check, scroll to the bottom of that run's page to the
   **"Artifacts"** section and download **`RadioPlayer-debug-apk`**. It's a `.zip` containing
   `app-debug.apk` — unzip it.

If the build fails (red ✕), open the log and copy the error — paste it back to me and I'll
fix the code. This project was hand-written without a local compiler to verify it, so don't
be surprised if the very first run needs a small fix or two; that's normal and quick to sort out.

## 3. Install it on your S24 Ultra

1. Transfer `app-debug.apk` to your phone (email it to yourself, use a cloud drive, or plug
   in via USB and copy it over).
2. On the S24 Ultra, tap the APK file to install. You'll be prompted to allow "install
   unknown apps" for whichever app you used to open it (Files, Chrome, etc.) — allow it.
3. This is a debug build signed with a default auto-generated debug key, which is completely
   fine for installing on your own device — you don't need a Play Store listing or paid
   signing certificate.

## 4. Using the app

- Tap **+** to add a station: give it a name, paste the stream URL, and set a logo either by
  picking an image from your phone or by pasting an image URL. Optionally file it into a
  folder, or set a proxy (type, host, port) if that station needs to appear to come from a
  specific country.
- Tap a station to play it — this opens the Now Playing screen with the current cover art,
  play/pause, and next/previous buttons (next/previous move through whichever list you played
  from: a folder's stations, or the ungrouped list).
- Tap the ⋮ menu on a station to **Edit** (rename, change logo, change folder, change proxy),
  **Move Up / Move Down** (reorder it), or **Delete**.
- Tap **+ → Add Folder** to create a folder; tap a folder to expand/collapse it. Its ⋮ menu
  lets you **Rename** or **Delete Folder** (deleting a folder keeps its stations — they just
  move back to the ungrouped list).
- While playing, if the stream sends ICY metadata (`StreamTitle`), the app parses it as
  "Artist - Title" and looks up cover art — first via Deezer, falling back to MusicBrainz +
  Cover Art Archive if Deezer has no match. Not every internet radio stream sends ICY
  metadata; stations that don't will just show your station logo instead.

## 4b. Backup and restore

Tap the ⋮ menu in the top bar:
- **Backup to file** saves all your stations (name, stream URL, folder, proxy settings, and
  logo — embedded directly in the file, whether it was a picked image or a URL) as a `.json`
  file you choose the location for (e.g. Google Drive, Downloads).
- **Restore from file** picks a `.json` backup and adds everything from it back in. Folders
  are matched/created by name. Running restore twice will create duplicate stations, so only
  restore once per backup unless you want that.

This is also a simple way to move your station list to a new phone.

## 5. Android Auto

1. Plug your S24 Ultra into your car (or use the Android Auto phone-screen simulator app)
   with Android Auto set up.
2. Since this app isn't from the Play Store, you need to allow "unknown sources" in Android
   Auto's developer settings: open the **Android Auto** app on your phone → tap the version
   number in "About" repeatedly (like the classic developer-options trick) until developer
   mode unlocks → open the overflow menu (⋮) → **Developer settings** → enable
   **"Unknown sources"**.
3. Open Radio Player once on the phone first (so it has something to browse), then connect
   to the car — it should appear in the app grid. Your stations show up as a browsable list,
   and tapping one plays it with your logo/album art shown as artwork.

## Notes / limitations to be aware of

- **Database reset on this update**: adding folders and logo-by-URL required a database
  schema change. The very first time you install this updated build, your previously added
  stations will be cleared (fresh empty database) — export a backup first if you added
  anything you want to keep, then restore it after updating. Future updates won't reset data
  unless a new schema change requires it again.
- **Proxies**: currently no username/password auth, just host + port for HTTP or SOCKS5.
- **Album art lookup** depends on free public APIs (Deezer's search API, MusicBrainz +
  Cover Art Archive) which are unauthenticated but rate-limited — very occasional misses or
  short delays are expected, especially on MusicBrainz.
- Debug builds are unsigned for the Play Store and not auto-updating — if you want to tweak
  the app later, edit the code and push again; a new APK will build automatically.

Want changes — different proxy auth, playlists/favorites, sleep timer, EQ, whatever — just
tell me and I'll update the code.
