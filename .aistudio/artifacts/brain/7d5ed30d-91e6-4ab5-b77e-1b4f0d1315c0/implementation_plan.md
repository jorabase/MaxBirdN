# Database-Backed Automatic Video Lesson Completion

Automate video lecture completion by persisting completion states in the Room database when video playback reaches 80%-90% or finishes. Remove any manual click-to-complete actions so that completion status reflects real database state only.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following rules were specified by the user:
> - **Completion Threshold**: A lecture class is marked completed automatically in the database when the video is watched up to **80%-90%** of its total duration or when playback finishes.
> - **Database-Driven UI**: The app will query the Room database (`completed_items` table) to display the "কমপ্লিট" (Completed) status. Manual clicks on cards/buttons will no longer toggle completion state.

---

## 1. Overview & Core Concept

Previously, some UI elements or click actions could immediately mark a lesson as completed without verifying video playback. This update ensures that:
1. Video playback progress is monitored in real-time.
2. When video progress reaches >= 80% (or playback finishes), a `CompletedItemEntity` is inserted into the local Room database (`completed_items`).
3. The UI reactively observes the Room database state (`CompletedItemDao` / `CompletedItemRepository`), displaying "কমপ্লিট" only when an actual entry exists in the database.

---

## 2. User Experience & Visual Design

- **Automatic Video Completion**:
  - As students watch a video lecture, progress is saved in `VideoProgressManager`.
  - Upon reaching 80%+ playback, the status silently updates in the Room database.
  - The lesson item in the course list immediately updates to display a green "কমপ্লিট" badge or checkmark driven reactively by the database `Flow`.
- **Elimination of Fake Click Completions**:
  - Clicking on a lesson or card will only open the player or view details—it will not manually set completion status.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: 80% Playback Threshold in `VideoProgressManager`**:
  - *Chosen Approach*: Update `VideoProgressManager.saveProgress` and player listeners to check if `positionMs >= durationMs * 0.80f`.
  - *Why*: Satisfies the 80%-90% watch requirement specified by the user and ensures genuine completion tracking.
- **Decision 2: Room Database as Single Source of Truth**:
  - *Chosen Approach*: Observe `CompletedItemDao.getAllCompletedItemIds()` reactively via `CompletedItemRepository`.
  - *Why*: Guarantees that the "কমপ্লিট" badge is shown only if the completion row actually exists in Room DB.

---

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────┐
│            ExoPlayer / ShikhoPlayerManager             │
│        (Monitors positionMs / durationMs in loop)      │
└───────────────────────────┬────────────────────────────┘
                            │ position >= 80% duration
┌───────────────────────────▼────────────────────────────┐
│                  VideoProgressManager                  │
│       • Checks isCompleted threshold (>= 0.80f)        │
│       • Triggers CompletedItemDao.markCompleted()      │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│                 Room Database (SQLite)                 │
│              table: `completed_items`                  │
└───────────────────────────┬────────────────────────────┘
                            │ Flow<List<String>>
┌───────────────────────────▼────────────────────────────┐
│            CompletedItemRepository & Flow              │
│       • Exposes completedIdsState to UI Composables    │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│       Course / Chapter / Lesson UI Composables        │
│        • Displays "কমপ্লিট" badge if in DB State      │
└────────────────────────────────────────────────────────┘
```

---
