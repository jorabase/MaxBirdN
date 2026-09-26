# Implementation Plan: Dynamic Exam & Model Test Routing based on `content_type`

## 🎯 Objective
Ensure that throughout the entire application (Routine, Full Routine, Course Subjects, Chapter Lessons, and Direct Navigation), items are properly differentiated and routed based on their `content_type`:
1. **`content_type == "LiveExam"`** ➡️ Chapter Exam Flow (`ChapterExamScreen` / MCQ & CQ Chapter Live Exam).
2. **`content_type == "ModelTest"`** (or when `model_test` object / `model_test_id` is present) ➡️ Model Test Flow (`ModelTestDetailScreen` / `MCQExamScreen` / `CQExamScreen` / `ModelTestResultScreen`).

---

## 🔍 Key Areas Identified for Update

### 1. Data Models (`ApiModels.kt`)
- Ensure `RoutineItem`, `LessonItem`, `ChapterItem`, and related models have:
  - `content_type: String?` properly exposed and mapped (`"LiveExam"`, `"ModelTest"`, `"Lecture"`, `"AnimatedVideo"`, `"Resource"`, `"Quiz"`, etc.)
  - `model_test: ModelTestSummary?` or `model_test_id: String?` / nested `model_test` payload parsing.
  - Helper functions/properties like `isModelTest`, `isLiveExam`, `isExam` on items.

### 2. Routine Cards (`RoutineCard.kt` & `FullRoutineScreen.kt`)
- Update Routine Cards (Home Weekly Routine, Full Routine List & Calendar View) so that:
  - When an item has `content_type == "ModelTest"` or `model_test != null`:
    - Display the proper Model Test badge / type badge (e.g. "মডেল টেস্ট", MCQ+CQ indicator, result publish time).
    - Clicking it navigates directly to the Model Test Detail / Exam screen with `modelTestId` and `courseId`.
  - When an item has `content_type == "LiveExam"`:
    - Display the Chapter Live Exam badge.
    - Clicking it navigates to the Chapter Exam screen (`ChapterExamScreen`).

### 3. Subject & Chapter Lessons (`SubjectChaptersScreen.kt`, `ChapterLessonsScreen.kt`, `ChapterLessonItemCard.kt`)
- In Subject Chapters & Chapter Contents list:
  - Identify items where `content_type == "ModelTest"` vs `content_type == "LiveExam"`.
  - Show proper exam icons, badges (e.g., "মডেল টেস্ট" vs "অধ্যায় পরীক্ষা"), duration, and marks.
  - On click, trigger the respective callback:
    - `onNavigateToModelTest(modelTestId, courseId, title)` for `ModelTest`.
    - `onNavigateToChapterExam(examId, courseId, title)` for `LiveExam`.

### 4. Navigation & ViewModel Flow (`AppNavigation.kt` & `CourseViewModel.kt`)
- Connect the navigation routes so that all exam clicks from Home Routine, Full Routine, Subject List, and Chapter Lesson lists smoothly transition into:
  - `ModelTestDetailScreen` for Model Tests.
  - `ChapterExamScreen` for Live Chapter Exams.
- Ensure back-navigation returns the user to the correct previous screen without breaking state.

---

## 🛠️ Verification Plan
1. **Compilation Check**: Run `compile_applet` to ensure full Kotlin & Jetpack Compose compatibility.
2. **Flow Verification**:
   - Verify `content_type == "LiveExam"` loads Chapter Exam UI.
   - Verify `content_type == "ModelTest"` routes into the Model Test UI.
   - Verify Routine Card clicks properly distinguish between Lecture, LiveExam, and ModelTest.
