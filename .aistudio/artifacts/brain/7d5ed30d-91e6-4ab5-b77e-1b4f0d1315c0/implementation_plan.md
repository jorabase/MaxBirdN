# YouTube-Style Zero-Buffering Streaming, Auto-Resume & Instant Class Loading

This revised implementation plan addresses all core issues raised by the user: eliminating video buffering with a continuous pre-buffer disk cache and dynamic Adaptive Bitrate (480p default seed), fixing the video resume bug so playback actually begins from the saved timestamp, making the seekbar/scrubber effortless to drag with double-tap skip, and accelerating class/lesson list loading to be instantaneous.

## User Review & Critical Decisions

> [!IMPORTANT]
> The user specified:
> 1. **Zero-Buffering & Dynamic ABR**: Videos should **default to 480p** for instantaneous startup, but **dynamically adapt to real-time internet speed** (scaling up to 720p/1080p when Wi-Fi is fast, or stepping down when mobile data fluctuates). Pre-buffer 2–3 minutes ahead into disk cache just like YouTube so network drops don't interrupt playback.
> 2. **Resume Bug Fix**: The player must actually **start playback from the saved timestamp** (e.g. 10m 05s) rather than merely showing a banner while playing from 0:00.
> 3. **Scrubbing / Seeking Enhancement**: Enhance the seek bar with a larger touch target, smooth scrubbing without stutter/jumping, accurate timestamp preview, and responsive double-tap (±10s) seeking.
> 4. **Instant Class Loading**: Classes must load as fast as subjects and chapters (under 100ms) by leveraging smart memory caching and eliminating redundant sequential GraphQL queries.

- **Decision 1 (Dynamic ABR with 480p Default Seed)**: Configure ExoPlayer's `AdaptiveTrackSelection` with an initial bandwidth seed of 1.5 Mbps (480p). Playback starts in under 800ms at 480p, then automatically measures real network throughput to upgrade to 720p/1080p without buffering.
- **Decision 2 (Continuous 2–3 Minute Disk Pre-Buffer)**: Configure `DefaultLoadControl` with `minBufferMs = 45_000` (45s), `maxBufferMs = 180_000` (3 minutes), and `SimpleCache` (512MB LRU) wrapping OkHttp. Media chunks are written to disk as they arrive.
- **Decision 3 (Bulletproof Seek-on-Start)**: Ensure `targetResumeMs` is passed directly into `exoPlayer.setMediaSource(mediaSource, targetResumeMs)` during preparation, and backed up in `onPlaybackStateChanged(STATE_READY)` and `onTimelineChanged`.
- **Decision 4 (Instant Class List Cache)**: If lessons for a program are already cached in memory or Room, display them immediately (0ms). Avoid running 4 sequential network fallback queries when data is already available.

---

## 1. Video Playback & Buffering Engine (`ShikhoPlayerManager.kt`)

### Proposed Changes
- **SimpleCache + CacheDataSource**: Implement a 512MB LRU persistent disk cache in `context.cacheDir/media_stream_cache/`. Wrap `DefaultHttpDataSource.Factory` in `CacheDataSource.Factory` so all video segments (HLS and MP4) are saved directly to disk.
- **Aggressive LoadControl (YouTube-Style Continuous Pre-Buffer)**:
  - `minBufferMs = 45_000` (45 seconds)
  - `maxBufferMs = 180_000` (180 seconds / 3 minutes ahead)
  - `bufferForPlaybackMs = 1_000` (starts within 1s)
  - `bufferForPlaybackAfterRebufferMs = 2_000`
  - `prioritizeTimeOverSizeThresholds = true`
- **Dynamic ABR with 480p Default Seed**:
  - Set `DefaultBandwidthMeter.Builder(context).setInitialBitrateEstimate(1_500_000L)` (1.5 Mbps, targeting 480p).
  - Configure `AdaptiveTrackSelection.Factory` to seamlessly step up to 720p/1080p when bandwidth is high, and step down if network drops, without ever stalling.
- **Live-Edge Constraint Removal**: Recorded and archived lecture streams are treated as pure VOD, removing live-edge buffering restrictions that were causing 4-second stalls.

---

## 2. Auto-Resume Position Fix (`LessonDetailPlayerScreen.kt` & `VideoPlayerScreen.kt`)

### Root Cause Analysis
- Currently, when a user opens a lesson, `activeStreamUrl` is initially empty while resolving. The video key is generated with an empty URL.
- When `dbProgress` is retrieved from Room asynchronously, `exoPlayer.playbackState` is `STATE_BUFFERING` or `STATE_IDLE`. The check `if (playbackState == STATE_READY)` fails, so `seekTo()` is skipped.
- When `STATE_READY` later fires, `pendingResumeSeekMs` was often lost or overwritten by `LaunchedEffect(activeStreamUrl)` calling `setMediaSource` with `targetResumeMs = 0L`.
- The UI showed "পূর্বের ১০:০৫ মিনিট থেকে চলছে", but the player was actually playing from 0:00!

### Proposed Changes
- **Direct Start Position in MediaSource**: Store the resolved resume timestamp before calling `setMediaSource(mediaSource, targetResumeMs)`. ExoPlayer Media3 will seek to `targetResumeMs` natively during initial preparation before first frame render.
- **Two-Tier Fallback on `STATE_READY` and `onTimelineChanged`**:
  - In `Player.Listener`, verify if `currentPosition < 1000L` and `savedProgress.positionMs > 2000L`. If so, perform an explicit `exoPlayer.seekTo(savedProgress.positionMs)` and synchronize `currentPosition`.
  - Mark `hasAutoResumed = true` only after verifying `exoPlayer.currentPosition >= savedProgress.positionMs - 1000L`.
- Apply this fix symmetrically across `LessonDetailPlayerScreen.kt`, `VideoPlayerScreen.kt`, and `AnimatedLessonPlayerScreen.kt`.

---

## 3. Scrubbing & Seeking User Experience (`PlayerControlsOverlay.kt`)

### Proposed Changes
- **Enlarged Touch Target & Smooth Scrubbing**:
  - Increase the seekbar slider touch container height to 56.dp with generous padding so user fingers easily grab and drag the slider thumb without missing.
  - Implement instant seek-on-tap: tapping anywhere along the progress track immediately seeks to that position.
- **Floating Time Preview Badge**:
  - When dragging the slider thumb, display an illuminated badge above the thumb showing the exact target timestamp (e.g., `১০:০৫ / ৪৫:০০`) in Bengali numerals.
- **Dual Double-Tap Skip (+10s / -10s)**:
  - Add high-responsiveness double-tap gestures to the left and right halves of the video player overlay with floating ripple animation (+10s forward, -10s rewind).
- **Secondary Buffer Bar**:
  - Ensure the secondary buffer bar accurately reflects the 2–3 minutes of pre-buffered media ahead of the current position.

---

## 4. Instant Class / Lesson List Loading (`CourseViewModel.kt` & `CourseRepository.kt`)

### Root Cause Analysis
- When opening a chapter, `loadLessonsForChapter` initiates `fetchAllLessonsForProgram`.
- In `CourseRepository`, it sequentially attempts `GetUpcomingLessonsPhaseWise`, then `GetStudentSpecificLessonsWithBatch`, then `GetUpcomingLessons`, then date-range queries.
- On cellular networks, this sequential cascade takes 4–8 seconds, showing an empty screen or loading spinner even though all program lessons were already fetched and stored in `LessonCacheManager`.

### Proposed Changes
- **Instant Display from Memory/Cache**:
  - Before launching any coroutine, check `LessonCacheManager.findLessonsForChapter` and `lessonsCache`.
  - If cached lessons are found, set `lessons = cached` and `isLessonsLoading = false` immediately. The screen appears instantly (0ms latency).
- **Session-Wide Program Lesson Cache**:
  - In `CourseRepository`, cache the full lesson list for the active `programId` in memory. If already fetched once during the session, return immediately without redundant network requests.
- **Background Pre-Fetch**:
  - When the user opens the `SubjectChaptersScreen`, quietly pre-warm the lessons in the background. When the student clicks a chapter, the classes are 100% ready.

---

## 5. Verification Plan

### Build & Integrity Checks
- Run `compile_applet` to verify compilation and dependency resolution with Media3 and OkHttp Cache.
- Verify zero syntax or runtime errors across `ShikhoPlayerManager`, `LessonDetailPlayerScreen`, `VideoPlayerScreen`, and `CourseViewModel`.

### Playback & Feature Verification
- Verify ExoPlayer loads with `CacheDataSource` and buffers 180 seconds ahead.
- Verify Adaptive Bitrate starts at 480p and adapts smoothly to connection throughput.
- Verify closing a video at e.g. 10:05 and reopening it resumes playback directly at 10:05.
- Verify dragging the slider and double-tapping forward/backward feels fluid and responsive.
- Verify chapter classes load immediately without delay.
