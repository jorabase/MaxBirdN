# Implementation Plan - Live Exam System (লাইভ এক্সাম সিষ্টেম)

We will implement the complete, authentic Live Exam workflow matching the 13 provided screenshots and GraphQL network payloads from the HAR files.

## User Journey & Workflow

```
[Exam Slot / Details Screen]
      │ (Tap "টেস্ট শুরু করো")
      ▼
[Live MCQ Stage] (30 mins countdown, real-time submit)
      │ (MCQ Finished / Auto-transition)
      ▼
[Live CQ Writing Stage]
  - Unique Code Card ("তোমার ইউনিক কোড : ৪২৮৭")
  - Rules (Writing: 1 hr, Upload: 40 mins)
  - Questions (ক, খ, গ, ঘ) with stimulus & math equations
      │ (Tap "উত্তরপত্র আপলোড করো")
      ▼
[CQ Answer Upload Dashboard]
  - Progress tracker (e.g. ০/২, ১/২, ২/২)
  - Question Cards with "সম্পূর্ণ প্রশ্ন দেখো" dialog
  - "উত্তরপত্র আপলোড করো" & "উত্তরপত্র রিভিউ করো" states
      │ (Tap on question upload)
      ▼
[Page Photo Capture & S3 Upload Screen]
  - Page boxes (১ নম্বর পৃষ্ঠা, ২ নম্বর পৃষ্ঠা...)
  - Camera / Gallery Photo Picker with thumbnail & "রিমুভ করো"
  - "আরো ছবি যুক্ত করো" button
  - AWS S3 Pre-signed URL upload & Submit mutation
      │ (All questions completed)
      ▼
[Submit Confirmation Dialog]
  - "তুমি কি টেস্ট সাবমিট করতে চাও? একবার সাবমিট করলে আর সাবমিট করতে পারবে না"
      │ (Tap "হ্যাঁ, সাবমিট করবো")
      ▼
[Live Exam Result & Celebration Screen]
  - Confetti party ribbons & "অভিনন্দন!" banner
  - Result publish date/time notice (e.g. ৩০ সেপ্টেম্বর, ২০২৬ | সকাল ১১ টায়)
  - MCQ Exam instant result card (পাস/ফেল, সঠিক, ভুল, উত্তর দেয়নি)
  - "মাস্টার সল্যুশন" & "ফিডব্যাক দেখো" buttons
  - "হোম এ ফিরে যাও" button
```

---

## Proposed Changes

### 1. Data Layer (`ModelTestModels.kt` & `ModelTestRepository.kt`)
- Add data models for:
  - `StartModelTestSession` / `GetMTSessionQueryOnly`
  - `GetCqInfoOfModelTest` & `GetCqUploadRelatedInfo`
  - `StartCqSessionSubmission`
  - `GetPreSignedUrlList` (AWS S3 signed upload URL)
  - `SubmitCqSession` (attaching uploaded `file_id` & `page_no` to question answer)
  - `CqSessionFinalSubmit` (final submission)
  - `GetModelTestResultPublishTime` & `GetModelTestPreResult`
- Implement repository functions for all of the above GraphQL operations and direct image bytes PUT upload to S3 pre-signed URLs.

### 2. State Management (`ModelTestViewModel.kt`)
- Track live exam stages (`MCQ`, `CQ_READING`, `CQ_DASHBOARD`, `CQ_PAGE_UPLOAD`, `CONFIRMATION`, `RESULT`).
- Manage CQ Unique Code (e.g., `4287` or dynamic `u_code`), writing timer (e.g., 60 mins) and upload timer (40 mins).
- Handle page image selection (camera/gallery picker), temporary previews, upload status per page, and overall progress (`0/2`, `1/2`, `2/2`).
- Seamless stage transitions and auto-save.

### 3. UI Screens & Components
- **`CqLiveExamScreen.kt`**:
  - Top live timer (`৫৬ : ১০`).
  - Unique Code card with copy/highlight.
  - Test instructions and question list with LaTeX/Markdown math rendering.
  - Upload dashboard with progress indicator and action buttons.
  - Page upload sub-screen with camera/gallery launcher, image compression, preview, and "আরো ছবি যুক্ত করো".
  - Confirmation alert dialog matching Screenshot 9.
- **`LiveExamResultScreen.kt`**:
  - Full-screen confetti particle celebration animation.
  - "অভিনন্দন! তোমার টেস্টটি সফলভাবে সম্পন্ন হয়েছে" banner.
  - Result publish notice card.
  - MCQ Result scorecard (পাস/ফেল, ১০টি সঠিক, ২০টি ভুল, ০টি উত্তর দেয়নি).
  - CQ summary, Master Solution & Feedback buttons.
  - "হোম এ ফিরে যাও" navigation.
- **`ModelTestDetailScreen.kt`**:
  - Live Exam Slot card (`06.00 PM - 08.10 PM`).
  - Live Exam Started alert (`তোমার এক্সাম শুরু হয়েছে ৫০ মিনিট আগে`).
  - Correct completion ticks for finished stages.

---

## Verification Plan

### Automated & Manual Verification
1. **Compilation Check**: Run `compile_applet` to ensure clean build with no syntax or type errors.
2. **Live Exam Initiation**: Test starting a Live Exam session from the details screen.
3. **CQ Live Screen**: Test question viewing, timer countdown, and unique code display.
4. **Photo Capture & Upload**: Test picking/capturing answer sheet photos for page 1, page 2, etc., and verifying progress updates (`১/২`, `২/২`).
5. **Final Submission**: Test tapping "সবগুলো সাবমিট করো", confirming in the dialog, and verifying transition to the celebration result screen with MCQ score summary and result publish time.
