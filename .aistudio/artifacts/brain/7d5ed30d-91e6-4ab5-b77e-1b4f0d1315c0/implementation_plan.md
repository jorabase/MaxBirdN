# Implementation Plan: Model Test Exact GraphQL Schema & Navigation Bar Padding Fix

Fix the UI navigation bar overlap and integrate 100% real GraphQL API queries matching the provided Shikho HAR capture.

---

## 1. User Review Required
> [!IMPORTANT]
> - The GraphQL queries, mutation names, variable schemas, and response parsers will be strictly matched with the official Shikho HAR logs (`getModelTestSession`, `getMcqSession`, `submitMcqSession`, `getMcqSessionMinimumResult`, `getCqSession`, `practiceModelTestSessions`, `cqExam`, etc.).
> - Navigation bar padding (`Modifier.navigationBarsPadding()`) will be applied to all bottom bars in MCQ, CQ, Detail, Feedback, and Dashboard screens to prevent the Android navigation buttons from covering the UI actions.

---

## 2. Proposed Changes

### UI & Navigation Insets (`com.example.modeltest.ui.screens`)
- **`MCQExamScreen.kt`**: Add `Modifier.navigationBarsPadding()` to the bottom bar surface and action row so "পূর্ববর্তী" (Previous), "এগিয়ে যাও" (Next), and "সাবমিট করো" (Submit) are completely visible above the system navigation bar.
- **`CQExamScreen.kt` & `CQExamUploadScreen.kt`**: Add `Modifier.navigationBarsPadding()` to bottom action containers.
- **`ModelTestDetailScreen.kt`**: Add `Modifier.navigationBarsPadding()` to the bottom bar container.
- **`MCQFeedbackScreen.kt` & `ModelTestResultScreen.kt`**: Ensure bottom controls respect navigation bar insets.

### Data Models (`com.example.modeltest.data.ModelTestModels.kt`)
- Update / add Moshi data models matching the real GraphQL schema:
  - `ModelTestSessionResult` with `stages: List<ModelTestStageSession>` (`session_id`, `type`, `is_running`, `is_completed`, `start_time`, `end_time`).
  - `McqSessionContainer` & `McqSession` containing `questions: List<ShikhoMcqQuestion>` (`id`, `question_no`, `title`, `mcq_options: List<ShikhoMcqOption> { no, description }`).
  - `SubmitMcqSessionInput` (`answers: List<Map<String, Any>>`).
  - `McqSessionMinimumResult` (`total`, `correct`).
  - `CqSessionContainer` & `CqSession` (`questions: List<ShikhoCqQuestion> { id, title, sub_questions { question, marks } }`).
  - `PracticeModelTestSessionsResponse` (`data: List<PracticeSessionData>`).
  - `CqExamMasterSolutionResponse` (`master_solutions: List<MasterSolutionItem> { title, url }`).

### Repository (`com.example.modeltest.data.ModelTestRepository.kt`)
- Replace all legacy GraphQL queries with exact Shikho queries:
  - `getModelTestSession(is_practice: $is_practice, model_test_id: $model_test_id, lesson_id: $lesson_id, query_only: $query_only)`
  - `getMcqSession(session_id: $session_id)`
  - `submitMcqSession(id: $id, is_final_submitted: $is_final_submitted, is_timeout: $is_timeout, question_answer: $answers)`
  - `getMcqSessionMinimumResult(session_id: $sessionId)`
  - `getCqSession(session_id: $session_id)`
  - `practiceModelTestSessions(model_test_id: $model_test_id)`
  - `cqExam(id: $cqId) { master_solutions { title, url } }`
- Remove hardcoded 2-question fallback so all questions are dynamically received and parsed from the real GraphQL endpoints.

### ViewModel (`com.example.modeltest.ui.ModelTestViewModel.kt`)
- In `startExamSession`, locate the active MCQ stage session ID (`stage.type == "MCQ"`) and pass it to `loadMcqQuestions`.
- Support multiple question formats (options as `mcq_options: List<{ no: String, description: String }>` and title text).
- Map option letter selection (`A`, `B`, `C`, `D` <-> Bengali indices `ক`, `খ`, `গ`, `ঘ`) seamlessly.

---

## 3. Verification Plan

### Automated Verification
- Run `compile_applet` to confirm successful Kotlin compilation with no type mismatch or syntax errors.

### Manual Verification
1. Open Model Test Detail screen and verify "টেস্টের নিয়মাবলী" and separate "মাস্টার সল্যুশন" buttons for MCQ/CQ.
2. Click "প্র্যাকটিস টেস্ট শুরু করো" -> Verify real 30 MCQ questions load directly from GraphQL API.
3. Verify that the bottom navigation bar buttons ("পূর্ববর্তী", "এগিয়ে যাও", "সাবমিট করো") are clearly elevated above the Android system navigation bar.
4. Select options and submit -> Verify minimal score popup and feedback screen with real analytics data.
