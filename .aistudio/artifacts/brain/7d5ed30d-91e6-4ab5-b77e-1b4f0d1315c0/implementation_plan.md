# Architecture Implementation Plan: Model Test / Revision Course Flow

This plan outlines the architecture and step-by-step implementation for adding the **Model Test & Revision Course** structure to the Shikho Android application alongside the existing Academic Course structure.

---

## 1. User Review Required

> [!IMPORTANT]
> Please review and confirm the following key architectural behaviors:
> 1. **Detection Logic**: When a student enters a course, the app inspects `phase.type` from `programPhasesByStudent` (or selected course phase). If `type.equals("ModelTest", ignoreCase = true)`, the course routes to the new Model Test screen instead of the default chapter list screen.
> 2. **Navigation Flow**:
>    - Academic course: Course Program $\rightarrow$ Subject Grid $\rightarrow$ **Chapter List Screen** (`SubjectChaptersScreen`) $\rightarrow$ Lessons/Exam.
>    - Model Test course: Course Program $\rightarrow$ Subject Grid $\rightarrow$ **Model Test & Live Class Screen** (`ModelTestSubjectScreen`) $\rightarrow$ Tabs (Model Test / Class) $\rightarrow$ Take Exam / Watch Class.
> 3. **Standalone Architecture**: All logic and UI for Model Tests will reside in a dedicated package/files (`com.example.modeltest`) to keep it clean, modular, and completely decoupled from existing chapter code.

---

## 2. Proposed Changes

### Data & API Layer (`com.example.api`, `com.example.course`, `com.example.modeltest`)

#### [NEW] `app/src/main/java/com/example/modeltest/ModelTestRepository.kt` & `ModelTestViewModel.kt`
- Create a dedicated repository and ViewModel for the Model Test flow.
- Execute the GraphQL query `SubjectSpecificModelTestsOrLiveClass`:
  ```graphql
  query SubjectSpecificModelTestsOrLiveClass(
      $program_id: String!,
      $phase_id: String!,
      $subject_id: String!,
      $content_type: LessonContentTypeEnum!
  ) {
      studentSpecificLessons(
          program_id: $program_id,
          phase_id: $phase_id,
          content_type: $content_type,
          subject_id: $subject_id
      ) {
          data {
              access_level
              title
              id
              content_id
              content_type
              start_time
              end_time
              subject_name
              user_activity_state
              model_test {
                  result_publish_time
                  type
                  exam_category
              }
              live_class {
                  chapter_id
                  chapter_name
                  end_time
                  is_on_going
                  start_time
                  subject_name
                  subject_id
                  id
                  type
                  slide_url
                  attachments {
                      id
                      title
                      url
                      file_type
                  }
              }
          }
      }
  }
  ```
- Manage tab states:
  - `Tab 0: Model Test (মডেল টেস্ট)` $\rightarrow$ calls query with `$content_type = "ModelTest"`
  - `Tab 1: Class (ক্লাস)` $\rightarrow$ calls query with `$content_type = "LiveClass"`
- Cache results per tab to prevent re-fetching on rapid tab switches.

---

### UI Layer (`com.example.modeltest`)

#### [NEW] `app/src/main/java/com/example/modeltest/ModelTestSubjectScreen.kt`
- **Top Header**: Subject title, subject icon/color badge, back button, and course phase info.
- **Tab Layout**:
  - Material 3 TabRow / PrimaryTabRow with custom rounded pill indicators.
  - Tab 1: **মডেল টেস্ট (Model Test)** with test count badge.
  - Tab 2: **ক্লাস (Class)** with class count badge.
- **Card Items**:
  - **Exam Card**:
    - Exam icon with theme color tint.
    - Title (`item.title` e.g. "Bangla Exam 02").
    - Formatted Bengali date & time (`start_time`).
    - Activity status badge:
      - `MISSED` / মিসড (Red badge with `0xFFEF4444`)
      - `COMPLETED` / সম্পন্ন (Green badge with `0xFF10B981`)
      - `UPCOMING` / আসন্ন (Blue/Amber badge with countdown)
      - `LIVE` / চলমান (Live pulsing indicator)
    - Action button ("পরীক্ষা দিন" / "ফলাফল দেখুন").
  - **Class Card**:
    - Video/lecture icon or teacher avatar.
    - Title (`item.title` e.g. "গদ্য পর্ব-১ (অপরিচিতা)").
    - Date & time display.
    - Status badge (`MISSED`, `ATTENDED`, `UPCOMING`, `LIVE`).
    - Action button ("ক্লাস দেখুন" / "লেকচার স্লাইড").
- **Empty & Error States**:
  - Clean animated empty state illustration when no tests or classes are scheduled yet.
  - Pull-to-refresh support.

---

### Routing & Navigation Layer (`com.example.AppNavigation.kt`, `CourseSubjectsScreen.kt`)

#### [MODIFY] `app/src/main/java/com/example/AppNavigation.kt`
- Add route `Routes.MODEL_TEST_SUBJECT = "model_test_subject/{subjectCode}?title={title}&color={color}&programId={programId}&phaseId={phaseId}"`.
- Handle navigation callbacks for exam clicks (routing to `ChapterExamScreen`) and class clicks (routing to `LivePlayer` or `LessonDetailPlayerScreen`).

#### [MODIFY] `app/src/main/java/com/example/ui/screens/CourseSubjectsScreen.kt`
- Update `onSubjectClick` handler to check the current course phase type:
  ```kotlin
  val isModelTestCourse = currentPhase?.type.equals("ModelTest", ignoreCase = true)
  if (isModelTestCourse) {
      onNavigateToModelTest(subjectCode, subjectTitle, subjectColor, programId, phaseId)
  } else {
      onNavigateToAcademicChapters(subjectCode, subjectTitle, subjectColor)
  }
  ```

---

## 3. Verification Plan

### Automated Compilation & Build
- Run `compile_applet` to ensure full Kotlin compilation without type or syntax errors.

### Manual CUJ (Critical User Journey) Verification
1. **Academic Course Flow**:
   - Open standard academic course $\rightarrow$ click subject $\rightarrow$ verify Chapter list screen loads as usual without any regressions.
2. **Model Test Course Flow**:
   - Open Model Test course (with `type: "ModelTest"`) $\rightarrow$ click subject $\rightarrow$ verify `ModelTestSubjectScreen` loads.
   - Verify "মডেল টেস্ট" tab shows exam cards with titles, dates, and status badges.
   - Switch to "ক্লাস" tab $\rightarrow$ verify classes load properly with video/live tags and lecture slides.
   - Tap an exam item $\rightarrow$ redirects to exam/solution flow.
   - Tap a class item $\rightarrow$ redirects to video player/live class view.
