# Dynamic HLS Stream Resolution & Real Size Calculation Fix

Fixes the root cause of large video download failures by replacing hardcoded URL string replacements with dynamic parsing of Shikho's `playlist.m3u8` master playlists (supporting `media.m3u8`, `video.m3u8`, and custom CDN paths from the HAR log), calculating exact real MB file sizes, and refining the Downloads screen layout to eliminate text clipping.

> [!IMPORTANT]
> **Root Cause & HAR Analysis Findings**
> - **404 Not Found on `video.m3u8`**: Large lecture videos on Shikho CDN (`shikho-stream2.tenbytecdn.com`) use `media.m3u8` (e.g. `/720p/media.m3u8`), whereas smaller/animated videos use `video.m3u8` or `stream.m3u8`.
> - **Old Hardcoded Replacement Failure**: The previous `resolveQualityUrl` hardcoded `replace("/playlist.m3u8", "/$quality/video.m3u8")`. When requesting large videos, HTTP 404 occurred, causing size calculations to fail and fall back to static estimations (`~১২০ - ২৫০ মেগাবাইট`), and download execution to fail.
> - **Fix Strategy**: Dynamically parse `#EXT-X-STREAM-INF` from the master playlist to extract exact child URLs (`media.m3u8`), bandwidth values, and total segment durations, enabling 100% accurate MB rendering and successful downloads.

---

## 1. Overview & Core Concept

### What Will Be Fixed
1. **Dynamic HLS Master Playlist Resolution (`AppFileDownloadManager.kt`)**:
   - Parses `playlist.m3u8` dynamically.
   - Extracts exact relative URLs (`720p/media.m3u8`, `480p/media.m3u8`, `360p/media.m3u8`) and bandwidths.
   - Fetches the child playlist (`media.m3u8`), sums `#EXTINF` segment durations, and calculates exact real file size in MBs (e.g. `~৬৫৬.৪ মেগাবাইট` for 1 hr 25 min lecture).
2. **Robust Multi-Segment Downloader**:
   - Uses child playlist URL (`media.m3u8`) directly for segment extraction (`segment-0.ts`, `segment-1.ts`, ...).
   - High-throughput parallel segment fetching with `Semaphore(6)` and live speed calculation in MB/s.
3. **Downloads Screen Layout Refinement (`DownloadsScreen.kt`)**:
   - Fixes vertical clipping on "মেমোরি ঠিক আছে" green pill.
   - Clean responsive layout for Storage Gauge Card and Download Item Cards.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Opening Download Modal**: User clicks "ডাউনলোড" on any video (e.g. `Adolescence-01` 1hr 25min lecture).
2. **Quality Selection**: The dialog fetches the master playlist in real time and renders exact calculated file sizes:
   - `720p (এইচডি) ~৬৫৬.৪ মেগাবাইট`
   - `480p (মাঝারি - সেরা পছন্দ) ~৫১৯.১ মেগাবাইট`
   - `360p (কম ডাটা) ~৩৭৮.৮ মেগাবাইট`
3. **Downloading & Notification**: Progress bar in Android Notification Shade displays `XX% • X.X MB/s` and downloaded MBs.
4. **Downloads Tab**: Storage gauge card displays exact total downloaded MBs without layout breaking.

---

## 3. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI                              │
│         (VideoDownloadQualityDialog & DownloadsScreen)                  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   AppFileDownloadManager                               │
│  1. fetch Master Playlist -> parse #EXT-X-STREAM-INF                   │
│  2. extract real child URI (720p/media.m3u8) & BANDWIDTH               │
│  3. fetch child playlist -> sum #EXTINF -> calculate exact size MB     │
│  4. stream segments (segment-X.ts) in parallel using Semaphore(6)      │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                 AppDownloadNotificationHelper & Room DB                │
│    (Foreground Notification with speed MB/s, DownloadedItemEntity)     │
└────────────────────────────────────────────────────────────────────────┘
```

### Key Changes Needed
1. **`AppFileDownloadManager.kt`**:
   - Rewrite `resolveQualityUrl` and `getRealAvailableDownloadQualities` to extract child URIs from master playlist dynamically rather than replacing strings.
   - Support `media.m3u8`, `video.m3u8`, and `stream.m3u8` seamlessly.
2. **`DownloadsScreen.kt`**:
   - Adjust `StorageGaugeCard` layout paddings, heights, and flex weights to eliminate clipping on small screens.
