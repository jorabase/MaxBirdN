# Real Shikho Live Class Notification Strategy & Plan

## User Review & Critical Clarifications

> [!IMPORTANT]
> Based on your feedback and Shikho's official live class notification lifecycle:
> 1. **Course Selection Requirement**:
>    - **Yes**, the target course/program (e.g., HSC 2026, SSC, or specific batch) **must be selected** in the app.
>    - *Reason*: Shikho's backend broadcasts live class alerts specifically to `LIVE_ShikhoNotification_Program_{programId}_Phase_{phaseId}` and `LIVE_ShikhoNotification_UserID_{userId}_Program_{programId}` topics. When you select a course, our app automatically subscribes your device to that course's live broadcast channel.
> 2. **Two-Stage Live Class Notifications with Photo**:
>    - **Stage 1 (30 minutes before class)**:
>      - *"⏰ তোমার ক্লাস [সময়] এ শুরু হবে! শিক্ষক: [নাম], বিষয়: [বিষয়]"*
>      - Displays the teacher's profile photo or subject banner (`BigPictureStyle`).
>    - **Stage 2 (Class starts now)**:
>      - *"🔴 তোমার ক্লাস শুরু হয়েছে! এখনই জয়েন করো"*
>      - One-tap direct launch into the live class player.
> 3. **Dual Delivery (FCM Cloud Push + Local Precise Scheduler)**:
>    - **Cloud Push**: Receives real-time broadcast from `shikho-tech` Firebase directly when Shikho teachers start the class.
>    - **Local Backup Alarm**: Fetches scheduled class times from the syllabus API and sets device alarms for 30 minutes in advance with teacher photos, ensuring no class is ever missed even if FCM is delayed by device battery savers.
> 4. **100% Real Verification**:
>    - As you rightly suggested, waiting for the actual scheduled live class from Shikho is the most authentic test. We will also display a subtle connection indicator in Settings showing active course subscriptions (`shikho-tech` connected, topics active).

---

## 1. Technical Architecture & Flow

```
[ Selected Course: e.g. HSC 2026 Batch ]
                     │
         ┌───────────┴───────────┐
         ▼                       ▼
[ FCM Topic Subscription ]   [ Class Schedule Sync ]
  • LIVE_ShikhoNotification    • Reads next classes from API
    Program_..._Phase_...      • Alarms set for T - 30 minutes
         │                               │
         ├───────────────────────────────┤
         ▼                               ▼
[ 30 Minutes Before Class ]      [ Class Starts (Live Now) ]
  • Heads-up Notification         • Urgent Red "Live Now" banner
  • Teacher / Subject Photo       • 1-Tap direct entry to Player
  • Bengali time display          • Deep Link auto-navigation
```

---

## 2. Implementation Steps

1. **Teacher Photo & BigPictureStyle in `ClassAlarmReceiver` & `ShikhoFirebaseMessagingService`**:
   - Enhance both receivers to download and render the teacher or course subject image as a rich preview banner in the notification tray.
2. **Lead-Time Configuration**:
   - Ensure the default alarm lead time is precisely 30 minutes before class start time (matching Shikho's official 30-minute reminder).
3. **Course & Phase State Persistence**:
   - Ensure the selected active course and phase in `CourseViewModel` instantly syncs with `ShikhoNotificationManager`, guaranteeing the FCM topic stays actively subscribed even when the app is completely closed.
4. **FCM & Topic Status in Settings**:
   - Add a clean, informative "নোটিফিকেশন স্ট্যাটাস" card in Settings showing:
     - ফায়ারবেস কানেকশন: সক্রিয় (shikho-tech)
     - বর্তমান কোর্স টপিক: সক্রিয়
     - পরবর্তী লাইভ ক্লাস রিমাইন্ডার: ৩০ মিনিট আগে (ছবিসহ)
