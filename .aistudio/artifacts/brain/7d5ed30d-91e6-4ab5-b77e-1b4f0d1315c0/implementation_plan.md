# High-Speed Video Download Engine & Redesigned Downloads System

A comprehensive upgrade to the video download infrastructure, enabling full network bandwidth utilization, background Android system notifications with live download percentage, robust HLS segment stitching for large lectures, and a redesigned modern Downloads screen with storage insights and offline playback controls.

> [!IMPORTANT]
> **User Review & Confirmed Decisions**
> - **Download Engine**: Advanced multi-threaded HLS segment downloader with automatic retry, segment concatenation, and full bandwidth speed utilization.
> - **Live Status & System Notifications**: Foreground Download Service displaying percentage, downloaded bytes, current speed, and status in the Android Notification Shade and in-app UI.
> - **Redesigned Downloads Tab**: Modernized UI with interactive storage usage meter, category filters (Videos / Notes), pause/resume/cancel/retry controls, and offline video playback.

---

## 1. Overview & Core Concept

### What It Does
1. **Bandwidth-Optimized Multi-Segment HLS Downloader**: Resolves HLS master playlists and downloads segments in parallel chunks (`ConcurrentSegmentDownloader`) directly to local app storage (`.mp4`/`.ts` cache) with automatic stream merging, avoiding download stalls or timeouts on 1GB+ large lecture videos.
2. **Foreground System Download Service & Notification**: Shows real-time progress (`XX% • X.X MB/s`), remaining time, and notification actions (Pause, Resume, Cancel) in the Android status bar and system tray.
3. **Redesigned Downloads Screen**: A clean, modern Material 3 interface featuring a top Storage Gauge Card (Used by App vs Free Device Storage), active download progress cards, search & category filter pills, batch management, and offline playback launch.

### Target Audience
HSC & Admission students downloading long 1-2 hour live lecture recordings, solution sheets, and lecture slides for offline study without network interruptions.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Initiating Download**: User clicks "ডাউনলোড" on a video or selects quality (1080p / 720p / 480p / 360p) in `VideoDownloadQualityDialog`.
2. **Notification & Progress**: Download starts immediately with maximum connection speed. System notification shows animated progress bar, downloaded MBs, and live transfer rate in MB/s.
3. **Downloads Tab Experience**: User switches to Downloads tab:
   - **Storage Card**: Shows total downloaded size (e.g. 1.2 GB downloaded), available phone storage, and storage progress bar.
   - **Active Downloads Section**: Live animated download card with progress ring/bar, current transfer speed, pause/resume button, and cancel button.
   - **Offline Media List**: Grouped by subject and date with high-resolution thumbnails, file size tags, single-tap offline video launch, and swipe-to-delete.

### Visual Identity & Theme
- **Theme**: Luxury Dark / Azure Cyber (Dominant dark slate `#0F172A`, azure cyan `#0284C7` / `#38BDF8` accents, glowing progress indicators).
- **Typography**: Clear hierarchy with Bengali digit conversions (`toBengaliDigits()`).
- **Motion**: Spring animations on download progress updates and soft transitions on completion.

---

## 3. Key Product Decisions & Trade-Offs

### Decision 1: HLS Segment Download vs Single Stream Buffer
- **Chosen Approach**: Parse `.m3u8` playlists and fetch `.ts` segments concurrently using Kotlin Coroutines `async` workers with bounded semaphore limits (4 parallel segment connections).
- **Why**: Standard `HttpURLConnection` on long `.m3u8` playlist links often drops or times out on large 1GB+ files. Parallel segment downloads utilize full mobile data / Wi-Fi bandwidth without socket timeouts.

### Decision 2: Local Database Persistence for Download Entities
- **Chosen Approach**: Store download state (ID, title, subtitle, remote URL, local file path, file size, status, timestamp) in Room Database (`DownloadedItemDao`).
- **Why**: Allows instant reactive UI updates using `Flow<List<DownloadedItemEntity>>` across all screens and survives app restarts.

---

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI                              │
│   (DownloadsScreen, VideoPlayerScreen, DownloadQualityDialog)          │
└───────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                 AppFileDownloadManager / DownloadService               │
│    (Foreground Service, Segment Worker Pool, System Notification)      │
└───────────────────┬────────────────────────────────┬───────────────────┘
                    │                                │
                    ▼                                ▼
┌───────────────────────────────┐   ┌────────────────────────────────────┐
│      Room Local Database      │   │    App Internal Storage Cache      │
│  (DownloadedItemEntity Flow)  │   │  (/files/downloads/xxx.mp4/ts)     │
└───────────────────────────────┘   └────────────────────────────────────┘
```

### Key Components to Update / Create
1. **`AppFileDownloadManager.kt`**: Upgrade HLS parsing, multi-segment downloading, bandwidth speed calculator (`MB/s`), and background file merging.
2. **`AppDownloadService.kt`**: Foreground Service managing Android system notification updates with progress actions.
3. **`DownloadsScreen.kt`**: Complete redesign featuring Storage Meter, active downloads status panel, category filters, and offline playback launcher.
4. **`VideoDownloadQualityDialog.kt`**: Enhanced quality selection dialog displaying estimated file size and resolution options.
