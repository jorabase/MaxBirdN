package com.example

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.api.ShikhoApiService
import com.example.auth.AuthViewModel
import com.example.auth.AuthViewModelFactory
import com.example.auth.SessionManager
import com.example.course.ChapterExamViewModel
import com.example.course.ChapterExamViewModelFactory
import com.example.course.CourseViewModel
import com.example.course.CourseViewModelFactory
import com.example.database.AppDatabase
import com.example.database.SavedItemRepository
import com.example.home.HomeViewModel
import com.example.home.HomeViewModelFactory
import com.example.modeltest.data.ModelTestRepository
import com.example.modeltest.ui.ModelTestViewModel
import com.example.modeltest.ui.ModelTestViewModelFactory
import com.example.modeltest.ui.screens.*
import com.example.profile.EditProfileViewModel
import com.example.profile.EditProfileViewModelFactory
import com.example.quiz.PracticeQuizViewModel
import com.example.quiz.PracticeQuizViewModelFactory
import com.example.reportcard.ReportCardViewModel
import com.example.reportcard.ReportCardViewModelFactory
import com.example.saved.SavedViewModel
import com.example.saved.SavedViewModelFactory
import com.example.security.DeviceActivationRepository
import com.example.smartnotes.SmartNotesViewModel
import com.example.smartnotes.SmartNotesViewModelFactory
import com.example.syllabus.ChangeSyllabusViewModel
import com.example.syllabus.ChangeSyllabusViewModelFactory
import com.example.ui.screens.*
import com.example.utils.ClassTypeUtils
import java.net.URLDecoder
import java.net.URLEncoder

private fun safeUrlEncode(value: String?): String {
    if (value.isNullOrEmpty()) return ""
    return try {
        URLEncoder.encode(value, "UTF-8")
    } catch (_: Exception) {
        value
    }
}

private fun safeUrlDecode(value: String?): String {
    if (value.isNullOrEmpty()) return ""
    return try {
        URLDecoder.decode(value, "UTF-8")
    } catch (_: IllegalArgumentException) {
        // Guard against unescaped '%' characters (e.g., "100% কমন") that crash URLDecoder
        try {
            URLDecoder.decode(value.replace("%(?![0-9a-fA-F]{2})".toRegex(), "%25"), "UTF-8")
        } catch (_: Exception) {
            value
        }
    } catch (_: Exception) {
        value
    }
}

private fun NavController.safePopBackStack(fallbackRoute: String = Routes.HOME) {
    val currentEntry = currentBackStackEntry
    if (currentEntry != null && currentEntry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
        if (previousBackStackEntry != null) {
            popBackStack()
        } else if (currentDestination?.route != fallbackRoute) {
            navigate(fallbackRoute) {
                popUpTo(0) { inclusive = true }
            }
        }
    } else if (previousBackStackEntry != null) {
        popBackStack()
    }
}

object Routes {
    const val SPLASH = "splash"
    const val ACTIVATION = "activation"
    const val LOGIN = "login"
    const val PIN = "pin/{phone}"
    const val OTP = "otp/{phone}/{authType}"
    const val SET_PIN = "set_pin/{phone}"
    const val RESET_SUCCESS = "reset_success/{phone}"
    const val HOME = "home"
    const val PROFILE = "profile"
    const val EDIT_PROFILE = "edit_profile"
    const val CHANGE_SYLLABUS = "change_syllabus"
    const val SAVED_ITEMS = "saved_items"
    const val DOWNLOADS = "downloads?isOfflineOnly={isOfflineOnly}"
    fun downloadsRoute(isOfflineOnly: Boolean = false) = "downloads?isOfflineOnly=$isOfflineOnly"
    const val REPORT_CARD = "report_card?programId={programId}&programTitle={programTitle}&phaseId={phaseId}"
    const val SMART_NOTES = "smart_notes/{subjectCode}?title={title}&color={color}&phaseId={phaseId}"
    const val CHAPTER_RESOURCES = "chapter_resources/{chapterId}?name={name}&subjectCode={subjectCode}&phaseId={phaseId}"
    const val SUBJECT_CHAPTERS = "subject_chapters/{subjectCode}?title={title}&color={color}"
    const val MODEL_TEST_SUBJECT = "model_test_subject/{subjectCode}?title={title}&color={color}&programId={programId}&phaseId={phaseId}"
    const val MODEL_TEST_DETAIL = "model_test_detail/{modelTestId}?lessonId={lessonId}&userActivityState={userActivityState}&title={title}&startTime={startTime}&endTime={endTime}"
    fun modelTestDetailRoute(
        modelTestId: String,
        lessonId: String = "",
        userActivityState: String = "",
        title: String = "",
        startTime: String = "",
        endTime: String = ""
    ): String {
        val encLessonId = safeUrlEncode(lessonId)
        val encState = safeUrlEncode(userActivityState)
        val encT = safeUrlEncode(title)
        val encStart = safeUrlEncode(startTime)
        val encEnd = safeUrlEncode(endTime)
        return "model_test_detail/$modelTestId?lessonId=$encLessonId&userActivityState=$encState&title=$encT&startTime=$encStart&endTime=$encEnd"
    }
    const val MODEL_TEST_MCQ_EXAM = "model_test_mcq_exam/{sessionId}"
    const val MODEL_TEST_CQ_READONLY = "model_test_cq_readonly/{sessionId}"
    const val MODEL_TEST_CQ_UPLOAD = "model_test_cq_upload/{sessionId}"
    const val MODEL_TEST_RESULT = "model_test_result/{sessionId}"
    const val MODEL_TEST_FEEDBACK = "model_test_feedback/{sessionId}"
    const val CHAPTER_LESSONS = "chapter_lessons/{chapterId}?name={name}&status={status}&initialTab={initialTab}&subjectCode={subjectCode}&subjectTitle={subjectTitle}&subjectColor={subjectColor}"
    const val LESSON_DETAIL_PLAYER = "lesson_detail_player"
    const val VIDEO_PLAYER = "video_player?url={url}&title={title}&subject={subject}&color={color}&isLive={isLive}"
    const val ROUTINE_FULL = "routine_full"
    const val COURSE_ENROLLMENT_DETAILS = "course_enrollment_details"
    const val CHAPTER_EXAM = "chapter_exam/{sessionId}?lessonId={lessonId}&title={title}&chapter={chapter}"
    const val PRACTICE_QUIZ_CHAPTERS = "practice_quiz_chapters/{subjectCode}?title={title}&color={color}&chapterId={chapterId}&chapterName={chapterName}"
    const val PRACTICE_QUIZ_COUNT = "practice_quiz_count"
    const val PRACTICE_QUIZ_PLAYER = "practice_quiz_player/{sessionId}"
    const val PRACTICE_QUIZ_RESULT = "practice_quiz_result/{sessionId}"
    const val PRACTICE_QUIZ_FEEDBACK = "practice_quiz_feedback/{sessionId}"
    const val NOTIFICATION_SETTINGS = "notification_settings"
    const val HEADER_WALLPAPER_SETTINGS = "header_wallpaper_settings"
    const val ANIMATED_LESSON_CHAPTERS = "animated_lesson_chapters/{subjectId}?title={title}&color={color}&programId={programId}&phaseId={phaseId}"
    const val ANIMATED_LESSON_LIST = "animated_lesson_list/{chapterId}?chapterName={chapterName}&subjectColor={subjectColor}&fromChapterPage={fromChapterPage}"
    const val ANIMATED_LESSON_PLAYER = "animated_lesson_player?url={url}&title={title}&subject={subject}&chapter={chapter}"
    fun animatedLessonPlayerRoute(url: String, title: String, subject: String = "", chapter: String = ""): String {
        val encUrl = safeUrlEncode(url)
        val encTitle = safeUrlEncode(title)
        val encSub = safeUrlEncode(subject)
        val encChap = safeUrlEncode(chapter)
        return "animated_lesson_player?url=$encUrl&title=$encTitle&subject=$encSub&chapter=$encChap"
    }
    const val LIVE_CLASS = "live_class/{classId}/{lessonId}?lessonTitle={lessonTitle}&subjectName={subjectName}"
    fun liveClassRoute(classId: String, lessonId: String, lessonTitle: String = "", subjectName: String = ""): String {
        val cleanClassId = classId.trim()
        val cleanLessonId = lessonId.trim().ifBlank { cleanClassId }
        val encTitle = safeUrlEncode(lessonTitle)
        val encSubject = safeUrlEncode(subjectName)
        return "live_class/$cleanClassId/$cleanLessonId?lessonTitle=$encTitle&subjectName=$encSubject"
    }

    const val PDF_VIEWER = "pdf_viewer?url={url}&title={title}&initialRemoteUrl={initialRemoteUrl}&subject={subject}"
    fun pdfViewerRoute(url: String, title: String = "", initialRemoteUrl: String = "", subject: String = ""): String {
        val encUrl = safeUrlEncode(url)
        val encTitle = safeUrlEncode(title)
        val encRemote = safeUrlEncode(initialRemoteUrl)
        val encSub = safeUrlEncode(subject)
        return "pdf_viewer?url=$encUrl&title=$encTitle&initialRemoteUrl=$encRemote&subject=$encSub"
    }
}

// ============================================================
//  Premium Navigation Transition System
// ============================================================

private data class NavTransitions(
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition
)

private object NavAnim {
    /** Auth flow: নিচ থেকে হালকা ভেসে ওঠা + fade */
    val auth = NavTransitions(
        enter = {
            slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it / 7 } +
                fadeIn(tween(340))
        },
        exit = { fadeOut(tween(220)) },
        popEnter = { fadeIn(tween(280)) },
        popExit = {
            slideOutVertically(tween(320, easing = FastOutSlowInEasing)) { it / 7 } +
                fadeOut(tween(260))
        }
    )

    /** Home: soft scale + fade — প্রিমিয়াম landing feel */
    val home = NavTransitions(
        enter = {
            scaleIn(tween(380, easing = FastOutSlowInEasing), initialScale = 0.93f) +
                fadeIn(tween(340))
        },
        exit = { fadeOut(tween(220)) },
        popEnter = { fadeIn(tween(300)) },
        popExit = {
            scaleOut(tween(300, easing = FastOutSlowInEasing), targetScale = 0.95f) +
                fadeOut(tween(280))
        }
    )

    /** Main flow: ডান থেকে slide + scale, back-এ বামে slide */
    val forward = NavTransitions(
        enter = {
            slideInHorizontally(tween(360, easing = FastOutSlowInEasing)) { it / 5 } +
                fadeIn(tween(300)) +
                scaleIn(tween(360, easing = FastOutSlowInEasing), initialScale = 0.97f)
        },
        exit = {
            slideOutHorizontally(tween(280)) { -it / 9 } +
                fadeOut(tween(220))
        },
        popEnter = {
            slideInHorizontally(tween(300)) { -it / 9 } +
                fadeIn(tween(260))
        },
        popExit = {
            slideOutHorizontally(tween(340, easing = FastOutSlowInEasing)) { it / 5 } +
                fadeOut(tween(260))
        }
    )

    /** Immersive: Video/Exam/Player — পুরো নিচ থেকে ওঠে, back-এ নামে */
    val immersive = NavTransitions(
        enter = {
            slideInVertically(tween(400, easing = FastOutSlowInEasing)) { it } +
                fadeIn(tween(300))
        },
        exit = { fadeOut(tween(200)) },
        popEnter = { fadeIn(tween(260)) },
        popExit = {
            slideOutVertically(tween(360, easing = FastOutSlowInEasing)) { it } +
                fadeOut(tween(300))
        }
    )
}

private fun NavGraphBuilder.animatedComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    anim: NavTransitions = NavAnim.forward,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        arguments = arguments,
        enterTransition = anim.enter,
        exitTransition = anim.exit,
        popEnterTransition = anim.popEnter,
        popExitTransition = anim.popExit,
        content = { entry -> content(entry) }
    )
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val sessionManager = remember { SessionManager(context) }
    val apiService = remember { ShikhoApiService.create(sessionManager) }

    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModelFactory(apiService, sessionManager)
    )

    val appDatabase = remember { AppDatabase.getDatabase(context) }
    val savedItemRepository = remember { SavedItemRepository(appDatabase.savedItemDao()) }
    val completedItemRepository = remember {
        com.example.database.CompletedItemRepository(appDatabase.completedItemDao(), sessionManager)
    }

    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(
            context.applicationContext as android.app.Application,
            apiService,
            sessionManager,
            completedItemRepository
        )
    )

    val courseViewModel: CourseViewModel = viewModel(
        factory = CourseViewModelFactory(apiService, sessionManager, completedItemRepository)
    )

    val chapterExamViewModel: ChapterExamViewModel = viewModel(
        factory = ChapterExamViewModelFactory(apiService, sessionManager, completedItemRepository)
    )

    val practiceQuizViewModel: PracticeQuizViewModel = viewModel(
        factory = PracticeQuizViewModelFactory(apiService, sessionManager, savedItemRepository, completedItemRepository)
    )

    val savedViewModel: SavedViewModel = viewModel(
        factory = SavedViewModelFactory(savedItemRepository)
    )

    val smartNotesViewModel: SmartNotesViewModel = viewModel(
        factory = SmartNotesViewModelFactory(apiService, sessionManager)
    )

    val modelTestRepository = remember {
        ModelTestRepository(
            apiService = apiService,
            sessionManager = sessionManager,
            modelTestDao = appDatabase.modelTestDao()
        )
    }
    val modelTestViewModel: ModelTestViewModel = viewModel(
        factory = ModelTestViewModelFactory(modelTestRepository)
    )

    val authState by authViewModel.authState.collectAsState()
    var showSessionExpiredDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        sessionManager.unauthorizedEvent.collect {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute == null ||
                (!currentRoute.startsWith(Routes.LOGIN) &&
                    !currentRoute.startsWith(Routes.SPLASH) &&
                    !currentRoute.startsWith(Routes.ACTIVATION))
            ) {
                showSessionExpiredDialog = true
            }
        }
    }

    LaunchedEffect(Unit) {
        val initialPending = com.example.notification.NotificationDeepLinkDispatcher.consumePendingDeepLink()
        if (!initialPending.isNullOrBlank()) {
            com.example.notification.NotificationDeepLinkDispatcher.setPendingDeepLink(initialPending)
        }
        com.example.notification.NotificationDeepLinkDispatcher.deepLinkEvents.collect { link ->
            if (link.isNotBlank()) {
                try {
                    val cleanLink = link.trim()
                    if (cleanLink.startsWith("http://", ignoreCase = true) ||
                        cleanLink.startsWith("https://", ignoreCase = true)
                    ) {
                        val browserIntent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(cleanLink)
                        ).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(browserIntent)
                    } else if (cleanLink.startsWith("shikho://", ignoreCase = true)) {
                        val uri = android.net.Uri.parse(cleanLink)
                        when (uri.host?.lowercase()) {
                            "chapter-report", "report", "report_card" -> {
                                navController.navigate(Routes.REPORT_CARD) { launchSingleTop = true }
                            }
                            "routine", "full-routine", "routine_full" -> {
                                navController.navigate(Routes.ROUTINE_FULL) { launchSingleTop = true }
                            }
                            "live", "live-class" -> {
                                val classId = uri.getQueryParameter("class_id") ?: uri.getQueryParameter("id") ?: ""
                                val lessonId = uri.getQueryParameter("lesson_id") ?: classId
                                val title = uri.getQueryParameter("title") ?: ""
                                val subject = uri.getQueryParameter("subject") ?: ""
                                if (classId.isNotBlank()) {
                                    navController.navigate(
                                        Routes.liveClassRoute(
                                            classId = classId,
                                            lessonId = lessonId,
                                            lessonTitle = title,
                                            subjectName = subject
                                        )
                                    ) { launchSingleTop = true }
                                }
                            }
                            "downloads" -> {
                                navController.navigate(Routes.downloadsRoute()) { launchSingleTop = true }
                            }
                            else -> {
                                val path = uri.path?.removePrefix("/") ?: uri.host
                                if (!path.isNullOrBlank()) {
                                    navController.navigate(path) { launchSingleTop = true }
                                }
                            }
                        }
                    } else {
                        when (cleanLink.lowercase()) {
                            "routine", "full-routine", "routine_full" ->
                                navController.navigate(Routes.ROUTINE_FULL) { launchSingleTop = true }
                            "downloads" ->
                                navController.navigate(Routes.downloadsRoute()) { launchSingleTop = true }
                            "report", "report_card" ->
                                navController.navigate(Routes.REPORT_CARD) { launchSingleTop = true }
                            else ->
                                navController.navigate(cleanLink) { launchSingleTop = true }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AppNavigation", "Error navigating deep link: $link", e)
                }
            }
        }
    }

    val handleOpenLessonDetail: (com.example.api.StudentLessonItem) -> Unit = { lesson ->
        if (lesson.isModelTest ||
            lesson.content_type?.equals("ModelTest", ignoreCase = true) == true ||
            lesson.model_test != null
        ) {
            val mTestId = lesson.content_id?.takeIf { it.isNotBlank() } ?: lesson.id
            navController.navigate(
                Routes.modelTestDetailRoute(
                    modelTestId = mTestId,
                    lessonId = lesson.id,
                    userActivityState = lesson.user_activity_state ?: "",
                    title = lesson.title ?: "মডেল টেস্ট",
                    startTime = lesson.start_time ?: "",
                    endTime = lesson.end_time ?: ""
                )
            )
        } else if (lesson.isLiveExam ||
            lesson.isExam ||
            lesson.content_type?.equals("LiveExam", ignoreCase = true) == true
        ) {
            val sessionId = lesson.session_id?.takeIf { it.isNotBlank() }
                ?: lesson.live_class?.session_id?.takeIf { it.isNotBlank() }
                ?: lesson.content_id?.takeIf { it.isNotBlank() }
                ?: lesson.id
            val title = ClassTypeUtils.formatLessonTitle(lesson.title ?: "অধ্যায় পরীক্ষা")
            val chapter = lesson.subject_name ?: ""
            val encodedTitle = safeUrlEncode(title)
            val encodedChapter = safeUrlEncode(chapter)
            navController.navigate("chapter_exam/$sessionId?lessonId=${lesson.id}&title=$encodedTitle&chapter=$encodedChapter")
        } else if (lesson.isLiveNow ||
            lesson.live_class?.is_on_going == true ||
            lesson.user_activity_state.equals("LIVE", ignoreCase = true)
        ) {
            val classId = lesson.live_class?.id?.takeIf { it.isNotBlank() }
                ?: lesson.content_id?.takeIf { it.isNotBlank() }
                ?: lesson.id ?: ""
            val lessonId = lesson.id ?: classId
            val title = lesson.title ?: "লাইভ ক্লাস"
            val subject = lesson.subject_name ?: "বিষয়"
            courseViewModel.selectLesson(lesson)
            navController.navigate(
                Routes.liveClassRoute(
                    classId = classId,
                    lessonId = lessonId,
                    lessonTitle = title,
                    subjectName = subject
                )
            )
        } else {
            courseViewModel.selectLesson(lesson)
            navController.navigate(Routes.LESSON_DETAIL_PLAYER)
        }
    }

    val isActivated = remember { DeviceActivationRepository.isDeviceActivated(context) }
    val startDestination = if (!isActivated) Routes.ACTIVATION else Routes.SPLASH

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        animatedComposable(Routes.SPLASH, anim = NavAnim.auth) {
            SplashScreen(
                sessionManager = sessionManager,
                onNavigateOnline = { isLoggedIn ->
                    val activated = DeviceActivationRepository.isDeviceActivated(context)
                    val destination = if (!activated) {
                        Routes.ACTIVATION
                    } else if (isLoggedIn) {
                        Routes.HOME
                    } else {
                        Routes.LOGIN
                    }
                    navController.navigate(destination) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateOffline = {
                    val activated = DeviceActivationRepository.isDeviceActivated(context)
                    if (!activated) {
                        navController.navigate(Routes.ACTIVATION) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Routes.downloadsRoute(isOfflineOnly = true)) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                }
            )
        }

        animatedComposable(Routes.ACTIVATION, anim = NavAnim.auth) {
            DeviceActivationScreen(
                onActivationSuccess = {
                    try {
                        val isLoggedIn = sessionManager.getAccessToken() != null
                        val destination = if (isLoggedIn) Routes.HOME else Routes.LOGIN
                        navController.navigate(destination) {
                            popUpTo(Routes.ACTIVATION) { inclusive = true }
                        }
                    } catch (e: Throwable) {
                        android.util.Log.e("AppNavigation", "Navigation failed after activation: ${e.message}", e)
                    }
                }
            )
        }

        animatedComposable(Routes.LOGIN, anim = NavAnim.auth) {
            LoginScreen(
                viewModel = authViewModel,
                authState = authState,
                sessionManager = sessionManager,
                onNavigateToPin = { phone ->
                    navController.navigate("pin/$phone")
                },
                onNavigateToOtp = { phone, authType ->
                    navController.navigate("otp/$phone/$authType")
                },
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToActivation = {
                    navController.navigate(Routes.ACTIVATION) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        animatedComposable(Routes.PIN, anim = NavAnim.auth) { backStackEntry ->
            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            PinScreen(
                phone = phone,
                viewModel = authViewModel,
                authState = authState,
                onLoginSuccess = {
                    homeViewModel.loadData()
                    courseViewModel.loadSubjects(forceRefresh = true)
                    sessionManager.setJustSignedUp(false)
                    sessionManager.setAccountComplete(true)
                    navController.navigate(Routes.HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onForgotPasswordNavigate = { authType ->
                    navController.navigate("otp/$phone/$authType")
                },
                onBack = {
                    navController.safePopBackStack(Routes.LOGIN)
                }
            )
        }

        animatedComposable(Routes.OTP, anim = NavAnim.auth) { backStackEntry ->
            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            val authType = backStackEntry.arguments?.getString("authType") ?: "signup"
            OtpScreen(
                phone = phone,
                authType = authType,
                viewModel = authViewModel,
                authState = authState,
                onNavigateToSetPin = { p ->
                    navController.navigate("set_pin/$p") {
                        popUpTo(Routes.LOGIN)
                    }
                },
                onBack = {
                    navController.safePopBackStack(Routes.LOGIN)
                }
            )
        }

        animatedComposable(Routes.SET_PIN, anim = NavAnim.auth) { backStackEntry ->
            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            SetPinScreen(
                phone = phone,
                viewModel = authViewModel,
                authState = authState,
                onPinSetSuccess = { p ->
                    navController.navigate("reset_success/$p") {
                        popUpTo(Routes.LOGIN)
                    }
                },
                onBack = {
                    navController.safePopBackStack(Routes.LOGIN)
                }
            )
        }

        animatedComposable(Routes.RESET_SUCCESS, anim = NavAnim.auth) { backStackEntry ->
            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            ResetSuccessScreen(
                phone = phone,
                onLoginClick = { p ->
                    navController.navigate("pin/$p") {
                        popUpTo(Routes.LOGIN)
                    }
                }
            )
        }

        animatedComposable(Routes.HOME, anim = NavAnim.home) {
            LaunchedEffect(Unit) {
                if (!DeviceActivationRepository.isDeviceActivated(context)) {
                    navController.navigate(Routes.ACTIVATION) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            MainContainerScreen(
                homeViewModel = homeViewModel,
                courseViewModel = courseViewModel,
                sessionManager = sessionManager,
                onNavigateToSubjectChapters = { subjectCode, subjectTitle, subjectColor ->
                    val encodedTitle = safeUrlEncode(subjectTitle)
                    val encodedColor = safeUrlEncode(subjectColor)
                    val courseState = courseViewModel.uiState.value
                    if (courseState.isModelTestCourse) {
                        val pId = courseState.programId
                        val phId = courseState.activePhaseId
                        navController.navigate("model_test_subject/$subjectCode?title=$encodedTitle&color=$encodedColor&programId=$pId&phaseId=$phId")
                    } else {
                        navController.navigate("subject_chapters/$subjectCode?title=$encodedTitle&color=$encodedColor")
                    }
                },
                onNavigateToEditProfile = {
                    navController.navigate(Routes.EDIT_PROFILE)
                },
                onNavigateToChangeSyllabus = {
                    navController.navigate(Routes.CHANGE_SYLLABUS)
                },
                onNavigateToProfile = {
                    navController.navigate(Routes.PROFILE)
                },
                onNavigateToCourseEnrollment = {
                    navController.navigate(Routes.COURSE_ENROLLMENT_DETAILS)
                },
                onNavigateToFullRoutine = {
                    navController.navigate(Routes.ROUTINE_FULL)
                },
                onNavigateToSavedItems = {
                    navController.navigate(Routes.SAVED_ITEMS)
                },
                onNavigateToDownloads = {
                    navController.navigate(Routes.downloadsRoute(false))
                },
                onNavigateToReportCard = { programId, programTitle, phaseId ->
                    val encTitle = safeUrlEncode(programTitle)
                    val pId = programId ?: ""
                    val phId = phaseId ?: ""
                    navController.navigate("report_card?programId=$pId&programTitle=$encTitle&phaseId=$phId")
                },
                onOpenLessonDetail = handleOpenLessonDetail,
                onNavigateToExam = { sessionId, lessonId, title, chapter ->
                    val encodedTitle = safeUrlEncode(title)
                    val encodedChapter = safeUrlEncode(chapter)
                    navController.navigate("chapter_exam/$sessionId?lessonId=$lessonId&title=$encodedTitle&chapter=$encodedChapter")
                },
                onPlayVideo = { videoUrl, title, subjectName, subjectColor, isLive ->
                    val encodedUrl = safeUrlEncode(videoUrl)
                    val encodedTitle = safeUrlEncode(title)
                    val encodedSubject = safeUrlEncode(subjectName)
                    val encodedColor = safeUrlEncode(subjectColor)
                    navController.navigate("video_player?url=$encodedUrl&title=$encodedTitle&subject=$encodedSubject&color=$encodedColor&isLive=$isLive")
                },
                onOpenPdf = { filePath, title ->
                    navController.navigate(Routes.pdfViewerRoute(url = filePath, title = title))
                },
                onNavigateToNotificationHistory = {
                    navController.navigate(Routes.NOTIFICATION_SETTINGS)
                },
                onNavigateToNotification = {
                    navController.navigate(Routes.NOTIFICATION_SETTINGS)
                },
                onNavigateToHeaderWallpaper = {
                    navController.navigate(Routes.HEADER_WALLPAPER_SETTINGS)
                },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        animatedComposable(Routes.HEADER_WALLPAPER_SETTINGS, anim = NavAnim.forward) {
            HeaderWallpaperSettingsScreen(
                sessionManager = sessionManager,
                onBack = {
                    navController.safePopBackStack()
                }
            )
        }

        animatedComposable(Routes.NOTIFICATION_SETTINGS, anim = NavAnim.forward) {
            NotificationSettingsScreen(
                sessionManager = sessionManager,
                homeViewModel = homeViewModel,
                onBack = {
                    navController.safePopBackStack()
                }
            )
        }

        animatedComposable(Routes.ROUTINE_FULL, anim = NavAnim.forward) {
            FullRoutineScreen(
                viewModel = homeViewModel,
                onBack = {
                    navController.safePopBackStack()
                },
                onOpenLessonDetail = handleOpenLessonDetail,
                onNavigateToExam = { sessionId, lessonId, title, chapter ->
                    val encodedTitle = safeUrlEncode(title)
                    val encodedChapter = safeUrlEncode(chapter)
                    navController.navigate("chapter_exam/$sessionId?lessonId=$lessonId&title=$encodedTitle&chapter=$encodedChapter")
                }
            )
        }

        animatedComposable(Routes.EDIT_PROFILE, anim = NavAnim.forward) {
            val editProfileViewModel: EditProfileViewModel = viewModel(
                factory = EditProfileViewModelFactory(apiService, sessionManager)
            )
            EditProfileScreen(
                viewModel = editProfileViewModel,
                onBack = {
                    navController.safePopBackStack()
                }
            )
        }

        animatedComposable(Routes.CHANGE_SYLLABUS, anim = NavAnim.forward) {
            val changeSyllabusViewModel: ChangeSyllabusViewModel = viewModel(
                factory = ChangeSyllabusViewModelFactory(apiService, sessionManager)
            )
            ChangeSyllabusScreen(
                viewModel = changeSyllabusViewModel,
                onBack = {
                    navController.safePopBackStack()
                }
            )
        }

        animatedComposable(Routes.COURSE_ENROLLMENT_DETAILS, anim = NavAnim.forward) {
            com.example.ui.screens.CourseEnrollmentDetailsScreen(
                apiService = apiService,
                sessionManager = sessionManager,
                onBack = {
                    navController.safePopBackStack()
                }
            )
        }

        animatedComposable(
            route = Routes.SUBJECT_CHAPTERS,
            anim = NavAnim.forward,
            arguments = listOf(
                navArgument("subjectCode") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("color") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val subjectCode = backStackEntry.arguments?.getString("subjectCode") ?: ""
            val title = safeUrlDecode(backStackEntry.arguments?.getString("title"))
            val color = safeUrlDecode(backStackEntry.arguments?.getString("color")).ifBlank { null }

            SubjectChaptersScreen(
                subjectCode = subjectCode,
                subjectTitle = title,
                subjectColorHex = color,
                viewModel = courseViewModel,
                onBack = {
                    navController.safePopBackStack()
                },
                onNavigateToPracticeQuiz = { sCode, sTitle, sColor ->
                    val encodedTitle = safeUrlEncode(sTitle)
                    val encodedColor = safeUrlEncode(sColor)
                    navController.navigate("practice_quiz_chapters/$sCode?title=$encodedTitle&color=$encodedColor&chapterId=&chapterName=")
                },
                onNavigateToSmartNotes = { sCode, sTitle, sColor, phId ->
                    val encodedTitle = safeUrlEncode(sTitle)
                    val encodedColor = safeUrlEncode(sColor)
                    val ph = phId ?: ""
                    navController.navigate("smart_notes/$sCode?title=$encodedTitle&color=$encodedColor&phaseId=$ph")
                },
                onNavigateToAnimatedLessons = { sCode, sTitle, sColor, pId, phId ->
                    val encodedTitle = safeUrlEncode(sTitle)
                    val encodedColor = safeUrlEncode(sColor)
                    val encodedProgId = safeUrlEncode(pId)
                    val encodedPhaseId = safeUrlEncode(phId)
                    navController.navigate("animated_lesson_chapters/$sCode?title=$encodedTitle&color=$encodedColor&programId=$encodedProgId&phaseId=$encodedPhaseId")
                },
                onChapterClick = { chapterId, chapterName, chapterStatus, initialTab ->
                    val encodedName = safeUrlEncode(chapterName)
                    val encodedStatus = safeUrlEncode(chapterStatus)
                    val encodedSubCode = safeUrlEncode(subjectCode)
                    val encodedSubTitle = safeUrlEncode(title)
                    val encodedSubColor = safeUrlEncode(color)
                    navController.navigate("chapter_lessons/$chapterId?name=$encodedName&status=$encodedStatus&initialTab=$initialTab&subjectCode=$encodedSubCode&subjectTitle=$encodedSubTitle&subjectColor=$encodedSubColor")
                },
                onPlayVideo = { videoUrl, videoTitle, subjectName, subjectColor, isLive ->
                    val encodedUrl = safeUrlEncode(videoUrl)
                    val encodedTitle = safeUrlEncode(videoTitle)
                    val encodedSubject = safeUrlEncode(subjectName)
                    val encodedColor = safeUrlEncode(subjectColor)
                    navController.navigate("video_player?url=$encodedUrl&title=$encodedTitle&subject=$encodedSubject&color=$encodedColor&isLive=$isLive")
                }
            )
        }

        animatedComposable(
            route = Routes.MODEL_TEST_SUBJECT,
            anim = NavAnim.forward,
            arguments = listOf(
                navArgument("subjectCode") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("color") { type = NavType.StringType; defaultValue = "" },
                navArgument("programId") { type = NavType.StringType; defaultValue = "" },
                navArgument("phaseId") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val subjectCode = backStackEntry.arguments?.getString("subjectCode") ?: ""
            val title = safeUrlDecode(backStackEntry.arguments?.getString("title"))
            val color = safeUrlDecode(backStackEntry.arguments?.getString("color")).ifBlank { null }
            val programId = backStackEntry.arguments?.getString("programId").takeUnless { it.isNullOrBlank() }
                ?: courseViewModel.uiState.value.programId
            val phaseId = backStackEntry.arguments?.getString("phaseId").takeUnless { it.isNullOrBlank() }
                ?: courseViewModel.uiState.value.activePhaseId

            LaunchedEffect(subjectCode, phaseId, programId) {
                modelTestViewModel.initSubject(
                    programId = programId,
                    phaseId = phaseId,
                    subjectCode = subjectCode,
                    subjectTitle = title,
                    subjectColor = color ?: "#0072EC"
                )
            }

            ModelTestDashboardScreen(
                subjectCode = subjectCode,
                subjectTitle = title,
                subjectColorHex = color,
                viewModel = modelTestViewModel,
                onBack = {
                    navController.safePopBackStack()
                },
                onOpenModelTest = { lesson: com.example.api.StudentLessonItem ->
                    val mTestId = lesson.content_id?.takeIf { it.isNotBlank() } ?: lesson.id ?: ""
                    navController.navigate(
                        Routes.modelTestDetailRoute(
                            modelTestId = mTestId,
                            lessonId = lesson.id ?: "",
                            userActivityState = lesson.user_activity_state ?: "",
                            title = lesson.title ?: "মডেল টেস্ট",
                            startTime = lesson.start_time ?: "",
                            endTime = lesson.end_time ?: ""
                        )
                    )
                },
                onOpenClass = { lesson: com.example.api.StudentLessonItem ->
                    handleOpenLessonDetail(lesson)
                },
                onNavigateHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }

        animatedComposable(
            route = Routes.MODEL_TEST_DETAIL,
            anim = NavAnim.forward,
            arguments = listOf(
                navArgument("modelTestId") { type = NavType.StringType },
                navArgument("lessonId") { type = NavType.StringType; defaultValue = "" },
                navArgument("userActivityState") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("startTime") { type = NavType.StringType; defaultValue = "" },
                navArgument("endTime") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val modelTestId = backStackEntry.arguments?.getString("modelTestId") ?: ""
            val lessonId = safeUrlDecode(backStackEntry.arguments?.getString("lessonId"))
            val userActivityState = safeUrlDecode(backStackEntry.arguments?.getString("userActivityState"))
            val title = safeUrlDecode(backStackEntry.arguments?.getString("title"))
            val startTime = safeUrlDecode(backStackEntry.arguments?.getString("startTime"))
            val endTime = safeUrlDecode(backStackEntry.arguments?.getString("endTime"))

            ModelTestDetailScreen(
                modelTestId = modelTestId,
                lessonId = lessonId,
                userActivityState = userActivityState,
                lessonTitle = title,
                lessonStartTime = startTime,
                lessonEndTime = endTime,
                viewModel = modelTestViewModel,
                onBack = { navController.safePopBackStack() },
                onStartExam = { sessionId ->
                    navController.navigate("model_test_mcq_exam/$sessionId")
                },
                onViewFeedback = { sessionId ->
                    navController.navigate("model_test_feedback/$sessionId")
                },
                onViewResult = { id ->
                    navController.navigate("model_test_result/$id")
                },
                onNavigateHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }

        animatedComposable(
            route = Routes.MODEL_TEST_MCQ_EXAM,
            anim = NavAnim.forward,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""

            MCQExamScreen(
                sessionId = sessionId,
                viewModel = modelTestViewModel,
                onNavigateToCqReadOnly = { sId ->
                    navController.navigate("model_test_cq_readonly/$sId") {
                        popUpTo("model_test_mcq_exam/$sessionId") { inclusive = true }
                    }
                },
                onNavigateToCqUpload = { sId ->
                    navController.navigate("model_test_cq_upload/$sId") {
                        popUpTo("model_test_mcq_exam/$sessionId") { inclusive = true }
                    }
                },
                onNavigateToResult = { sId ->
                    navController.navigate("model_test_result/$sId") {
                        popUpTo("model_test_mcq_exam/$sessionId") { inclusive = true }
                    }
                },
                onNavigateToFeedback = { sId ->
                    navController.navigate("model_test_feedback/$sId") {
                        popUpTo("model_test_mcq_exam/$sessionId") { inclusive = true }
                    }
                },
                onExitExam = {
                    navController.safePopBackStack()
                }
            )
        }

        animatedComposable(
            route = Routes.MODEL_TEST_CQ_READONLY,
            anim = NavAnim.forward,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""

            CQExamScreen(
                sessionId = sessionId,
                viewModel = modelTestViewModel,
                onFinishPracticeExam = { sId ->
                    navController.navigate("model_test_feedback/$sId") {
                        popUpTo("model_test_cq_readonly/$sId") { inclusive = true }
                    }
                }
            )
        }

        animatedComposable(
            route = Routes.MODEL_TEST_CQ_UPLOAD,
            anim = NavAnim.forward,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""

            LaunchedEffect(sessionId) {
                modelTestViewModel.startLiveCqExam(sessionId) {}
            }

            LiveCqExamScreen(
                viewModel = modelTestViewModel,
                onNavigateBack = { navController.safePopBackStack() },
                onNavigateToUploadDashboard = {
                    navController.navigate("model_test_live_cq_dash/$sessionId")
                }
            )
        }

        animatedComposable(
            route = "model_test_live_cq_dash/{sessionId}",
            anim = NavAnim.forward,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""

            LiveCqUploadDashboardScreen(
                viewModel = modelTestViewModel,
                onNavigateBack = { navController.safePopBackStack() },
                onNavigateToPageUpload = { questionId ->
                    navController.navigate("model_test_live_cq_page/$sessionId/$questionId")
                },
                onFinalSubmitted = {
                    navController.navigate("model_test_result/$sessionId") {
                        popUpTo("model_test_cq_upload/$sessionId") { inclusive = true }
                    }
                }
            )
        }

        animatedComposable(
            route = "model_test_live_cq_page/{sessionId}/{questionId}",
            anim = NavAnim.forward,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("questionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val questionId = backStackEntry.arguments?.getString("questionId") ?: ""

            LiveCqPageUploadScreen(
                questionId = questionId,
                viewModel = modelTestViewModel,
                onNavigateBack = { navController.safePopBackStack() },
                onSubmittedSuccessfully = {
                    navController.safePopBackStack()
                }
            )
        }

        animatedComposable(
            route = Routes.MODEL_TEST_RESULT,
            anim = NavAnim.forward,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            val modelTestUiState by modelTestViewModel.uiState.collectAsState()
            val isLiveExam = modelTestUiState.liveCqExamId.isNotBlank() ||
                modelTestUiState.liveExamFinalResult != null ||
                modelTestUiState.modelTestInfo?.exam_category?.contains("LIVE", ignoreCase = true) == true

            if (isLiveExam) {
                LiveExamResultScreen(
                    sessionId = sessionId,
                    viewModel = modelTestViewModel,
                    onNavigateBack = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    },
                    onNavigateToFeedback = { sId ->
                        navController.navigate("model_test_feedback/$sId")
                    }
                )
            } else {
                com.example.modeltest.ui.screens.ModelTestResultScreen(
                    sessionId = sessionId,
                    viewModel = modelTestViewModel,
                    onViewFeedback = { sId ->
                        navController.navigate("model_test_feedback/$sId")
                    },
                    onNavigateHome = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    }
                )
            }
        }

        animatedComposable(
            route = Routes.MODEL_TEST_FEEDBACK,
            anim = NavAnim.forward,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""

            MCQFeedbackScreen(
                sessionId = sessionId,
                viewModel = modelTestViewModel,
                onBack = { navController.safePopBackStack() }
            )
        }

        animatedComposable(
            route = Routes.CHAPTER_LESSONS,
            anim = NavAnim.forward,
            arguments = listOf(
                navArgument("chapterId") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
                navArgument("status") { type = NavType.StringType; defaultValue = "" },
                navArgument("initialTab") { type = NavType.IntType; defaultValue = 0 },
                navArgument("subjectCode") { type = NavType.StringType; defaultValue = "" },
                navArgument("subjectTitle") { type = NavType.StringType; defaultValue = "" },
                navArgument("subjectColor") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val chapterId = backStackEntry.arguments?.getString("chapterId") ?: ""
            val name = safeUrlDecode(backStackEntry.arguments?.getString("name"))
            val status = safeUrlDecode(backStackEntry.arguments?.getString("status"))
            val initialTab = backStackEntry.arguments?.getInt("initialTab") ?: 0
            val subjectCode = safeUrlDecode(backStackEntry.arguments?.getString("subjectCode"))
            val subjectTitle = safeUrlDecode(backStackEntry.arguments?.getString("subjectTitle"))
            val subjectColor = safeUrlDecode(backStackEntry.arguments?.getString("subjectColor")).ifBlank { null }

            ChapterLessonsScreen(
                chapterId = chapterId,
                chapterName = name,
                chapterStatus = status,
                initialTab = initialTab,
                subjectCode = subjectCode,
                subjectTitle = subjectTitle,
                subjectColorHex = subjectColor,
                viewModel = courseViewModel,
                completedItemRepository = completedItemRepository,
                onBack = {
                    navController.safePopBackStack()
                },
                onNavigateToPracticeQuiz = { sCode, sTitle, sColor, cId, cName ->
                    val encodedTitle = safeUrlEncode(sTitle)
                    val encodedColor = safeUrlEncode(sColor)
                    val encodedChapterId = safeUrlEncode(cId)
                    val encodedChapterName = safeUrlEncode(cName)
                    navController.navigate("practice_quiz_chapters/$sCode?title=$encodedTitle&color=$encodedColor&chapterId=$encodedChapterId&chapterName=$encodedChapterName")
                },
                onNavigateToExam = { sessionId, lessonId, title, chapter ->
                    val encodedTitle = safeUrlEncode(title)
                    val encodedChapter = safeUrlEncode(chapter)
                    navController.navigate("chapter_exam/$sessionId?lessonId=$lessonId&title=$encodedTitle&chapter=$encodedChapter")
                },
                onNavigateToChapterResources = { cId, cName, sCode, phId ->
                    val encodedName = safeUrlEncode(cName)
                    val encodedSubCode = safeUrlEncode(sCode)
                    val encodedPhase = safeUrlEncode(phId)
                    navController.navigate("chapter_resources/$cId?name=$encodedName&subjectCode=$encodedSubCode&phaseId=$encodedPhase")
                },
                onNavigateToAnimatedLessons = { cId, cName ->
                    val encodedName = safeUrlEncode(cName)
                    val encodedColor = safeUrlEncode(subjectColor)
                    navController.navigate("animated_lesson_list/$cId?chapterName=$encodedName&subjectColor=$encodedColor&fromChapterPage=true")
                },
                onOpenLessonDetail = handleOpenLessonDetail,
                onPlayVideo = { videoUrl, title, subjectName, subColor, isLive ->
                    val encodedUrl = safeUrlEncode(videoUrl)
                    val encodedTitle = safeUrlEncode(title)
                    val encodedSubject = safeUrlEncode(subjectName)
                    val encodedColor = safeUrlEncode(subColor)
                    navController.navigate("video_player?url=$encodedUrl&title=$encodedTitle&subject=$encodedSubject&color=$encodedColor&isLive=$isLive")
                }
            )
        }

        animatedComposable(
            route = Routes.CHAPTER_EXAM,
            anim = NavAnim.immersive,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("lessonId") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("chapter") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            val lessonId = backStackEntry.arguments?.getString("lessonId") ?: ""
            val title = safeUrlDecode(backStackEntry.arguments?.getString("title"))
            val chapter = safeUrlDecode(backStackEntry.arguments?.getString("chapter"))

            ChapterExamScreen(
                sessionId = sessionId,
                lessonId = lessonId,
                examTitle = title,
                chapterName = chapter,
                viewModel = chapterExamViewModel,
                onBack = { navController.safePopBackStack() }
            )
        }

        animatedComposable(Routes.LESSON_DETAIL_PLAYER, anim = NavAnim.immersive) {
            val courseUiState by courseViewModel.uiState.collectAsState()
            LessonDetailPlayerScreen(
                lesson = courseUiState.selectedLesson,
                isLessonLoading = courseUiState.isLessonDetailLoading,
                subjectName = courseUiState.selectedSubjectTitle,
                subjectColorHex = courseUiState.selectedSubjectColor,
                onRefreshLesson = {
                    courseViewModel.reloadSelectedLesson()
                },
                onNavigateToLiveClass = { classId, lessonId, title, subName ->
                    navController.navigate(
                        Routes.liveClassRoute(
                            classId = classId,
                            lessonId = lessonId,
                            lessonTitle = title,
                            subjectName = subName
                        )
                    )
                },
                onNavigateToExam = { sessionId, lessonId, title, chapter ->
                    val encodedTitle = safeUrlEncode(title)
                    val encodedChapter = safeUrlEncode(chapter)
                    navController.navigate("chapter_exam/$sessionId?lessonId=$lessonId&title=$encodedTitle&chapter=$encodedChapter") {
                        popUpTo(Routes.LESSON_DETAIL_PLAYER) { inclusive = true }
                    }
                },
                onPlayAnimatedLesson = { videoUrl, title ->
                    navController.navigate(
                        Routes.animatedLessonPlayerRoute(
                            url = videoUrl,
                            title = title,
                            subject = courseUiState.selectedSubjectTitle
                        )
                    )
                },
                onOpenChapterResources = {
                    val lesson = courseUiState.selectedLesson
                    val chapterId = lesson?.chapter_id?.takeIf { it.isNotBlank() }
                        ?: lesson?.live_class?.chapter_id
                        ?: ""
                    val sCode = lesson?.subject_code?.takeIf { it.isNotBlank() }
                        ?: courseUiState.selectedSubjectCode
                    val encodedTitle = safeUrlEncode(courseUiState.selectedSubjectTitle.ifBlank { "রিসোর্স" })
                    val encodedColor = safeUrlEncode(courseUiState.selectedSubjectColor.ifBlank { "#0072EC" })
                    val encodedPhase = safeUrlEncode(lesson?.phase_id)

                    if (chapterId.isNotBlank()) {
                        val encodedName = safeUrlEncode(
                            lesson?.chapter_name?.takeIf { it.isNotBlank() } ?: courseUiState.selectedSubjectTitle
                        )
                        navController.navigate("chapter_resources/$chapterId?name=$encodedName&subjectCode=$sCode&phaseId=$encodedPhase")
                    } else if (sCode.isNotBlank()) {
                        navController.navigate("smart_notes/$sCode?title=$encodedTitle&color=$encodedColor&phaseId=$encodedPhase")
                    }
                },
                onOpenSubjectResources = {
                    val lesson = courseUiState.selectedLesson
                    val sCode = lesson?.subject_code?.takeIf { it.isNotBlank() }
                        ?: courseUiState.selectedSubjectCode
                    val encodedTitle = safeUrlEncode(courseUiState.selectedSubjectTitle.ifBlank { "রিসোর্স" })
                    val encodedColor = safeUrlEncode(courseUiState.selectedSubjectColor.ifBlank { "#0072EC" })
                    val encodedPhase = safeUrlEncode(lesson?.phase_id)
                    if (sCode.isNotBlank()) {
                        navController.navigate("smart_notes/$sCode?title=$encodedTitle&color=$encodedColor&phaseId=$encodedPhase")
                    }
                },
                onNavigateToPdfViewer = { url, title ->
                    navController.navigate(
                        Routes.pdfViewerRoute(
                            url = url,
                            title = title,
                            subject = courseUiState.selectedSubjectTitle
                        )
                    )
                },
                onBack = {
                    navController.safePopBackStack()
                }
            )
        }

        animatedComposable(
            route = Routes.ANIMATED_LESSON_CHAPTERS,
            anim = NavAnim.forward,
            arguments = listOf(
                navArgument("subjectId") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("color") { type = NavType.StringType; defaultValue = "" },
                navArgument("programId") { type = NavType.StringType; defaultValue = "" },
                navArgument("phaseId") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val subjectId = backStackEntry.arguments?.getString("subjectId") ?: ""
            val title = safeUrlDecode(backStackEntry.arguments?.getString("title"))
            val color = safeUrlDecode(backStackEntry.arguments?.getString("color")).ifBlank { null }
            val programId = safeUrlDecode(backStackEntry.arguments?.getString("programId")).ifBlank { null }
            val phaseId = safeUrlDecode(backStackEntry.arguments?.getString("phaseId")).ifBlank { null }

            AnimatedLessonChaptersScreen(
                subjectId = subjectId,
                subjectTitle = title,
                subjectColorHex = color,
                programId = programId,
                phaseId = phaseId,
                viewModel = courseViewModel,
                onBack = { navController.safePopBackStack() },
                onChapterClick = { chapterId, chapterName ->
                    val encodedName = safeUrlEncode(chapterName)
                    val encodedColor = safeUrlEncode(color)
                    navController.navigate("animated_lesson_list/$chapterId?chapterName=$encodedName&subjectColor=$encodedColor&fromChapterPage=false")
                }
            )
        }

        animatedComposable(
            route = Routes.ANIMATED_LESSON_LIST,
            anim = NavAnim.forward,
            arguments = listOf(
                navArgument("chapterId") { type = NavType.StringType },
                navArgument("chapterName") { type = NavType.StringType; defaultValue = "" },
                navArgument("subjectColor") { type = NavType.StringType; defaultValue = "" },
                navArgument("fromChapterPage") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val chapterId = backStackEntry.arguments?.getString("chapterId") ?: ""
            val chapterName = safeUrlDecode(backStackEntry.arguments?.getString("chapterName"))
            val subjectColor = safeUrlDecode(backStackEntry.arguments?.getString("subjectColor")).ifBlank { null }
            val fromChapterPage = backStackEntry.arguments?.getBoolean("fromChapterPage") ?: false

            AnimatedLessonListScreen(
                chapterId = chapterId,
                chapterName = chapterName,
                subjectColorHex = subjectColor,
                fromChapterPage = fromChapterPage,
                viewModel = courseViewModel,
                onBack = { navController.safePopBackStack() },
                onPlayVideo = { videoUrl, videoTitle ->
                    val selectedSubject = courseViewModel.uiState.value.selectedSubjectTitle
                    navController.navigate(
                        Routes.animatedLessonPlayerRoute(
                            url = videoUrl,
                            title = videoTitle,
                            subject = selectedSubject,
                            chapter = chapterName
                        )
                    )
                }
            )
        }

        animatedComposable(
            route = Routes.ANIMATED_LESSON_PLAYER,
            anim = NavAnim.immersive,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("subject") { type = NavType.StringType; defaultValue = "" },
                navArgument("chapter") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val url = safeUrlDecode(backStackEntry.arguments?.getString("url"))
            val title = safeUrlDecode(backStackEntry.arguments?.getString("title"))
            val subject = safeUrlDecode(backStackEntry.arguments?.getString("subject"))
            val chapter = safeUrlDecode(backStackEntry.arguments?.getString("chapter"))

            AnimatedLessonPlayerScreen(
                videoUrl = url,
                title = title,
                subjectName = subject,
                chapterName = chapter,
                onBack = { navController.safePopBackStack() }
            )
        }

        animatedComposable(
            route = Routes.VIDEO_PLAYER,
            anim = NavAnim.immersive,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("subject") { type = NavType.StringType; defaultValue = "" },
                navArgument("color") { type = NavType.StringType; defaultValue = "" },
                navArgument("isLive") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val url = safeUrlDecode(backStackEntry.arguments?.getString("url"))
            val title = safeUrlDecode(backStackEntry.arguments?.getString("title"))
            val subject = safeUrlDecode(backStackEntry.arguments?.getString("subject")).ifBlank { null }
            val color = safeUrlDecode(backStackEntry.arguments?.getString("color")).ifBlank { null }
            val isLive = backStackEntry.arguments?.getBoolean("isLive") ?: false

            VideoPlayerScreen(
                videoUrl = url,
                title = title,
                subjectName = subject,
                subjectColorHex = color,
                isLive = isLive,
                onBack = {
                    navController.safePopBackStack()
                }
            )
        }

        animatedComposable(
            route = Routes.PDF_VIEWER,
            anim = NavAnim.immersive,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("initialRemoteUrl") { type = NavType.StringType; defaultValue = "" },
                navArgument("subject") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val url = safeUrlDecode(backStackEntry.arguments?.getString("url"))
            val title = safeUrlDecode(backStackEntry.arguments?.getString("title"))
            val initialRemote = safeUrlDecode(backStackEntry.arguments?.getString("initialRemoteUrl"))
            val subject = safeUrlDecode(backStackEntry.arguments?.getString("subject"))

            com.example.ui.screens.PdfViewerScreen(
                slideUrl = url,
                title = title,
                initialRemoteUrl = initialRemote.takeIf { it.isNotBlank() },
                subject = subject,
                onBack = {
                    navController.safePopBackStack()
                }
            )
        }

        animatedComposable(Routes.PROFILE, anim = NavAnim.forward) {
            ProfileScreen(
                viewModel = authViewModel,
                authState = authState,
                sessionManager = sessionManager,
                onNavigateToEditProfile = {
                    navController.navigate(Routes.EDIT_PROFILE)
                },
                onNavigateToSavedItems = {
                    navController.navigate(Routes.SAVED_ITEMS)
                },
                onNavigateToDownloads = {
                    navController.navigate(Routes.downloadsRoute(false))
                },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ==========================================
        // Practice Quiz Navigation Flow
        // ==========================================

        animatedComposable(
            route = Routes.PRACTICE_QUIZ_CHAPTERS,
            anim = NavAnim.forward,
            arguments = listOf(
                navArgument("subjectCode") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("color") { type = NavType.StringType; defaultValue = "" },
                navArgument("chapterId") { type = NavType.StringType; defaultValue = "" },
                navArgument("chapterName") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val subjectCode = backStackEntry.arguments?.getString("subjectCode") ?: ""
            val title = safeUrlDecode(backStackEntry.arguments?.getString("title"))
            val color = safeUrlDecode(backStackEntry.arguments?.getString("color")).ifBlank { null }
            val chapterId = safeUrlDecode(backStackEntry.arguments?.getString("chapterId")).ifBlank { null }
            val chapterName = safeUrlDecode(backStackEntry.arguments?.getString("chapterName")).ifBlank { null }

            PracticeQuizChapterSelectionScreen(
                subjectCode = subjectCode,
                subjectTitle = title,
                subjectColorHex = color,
                targetChapterId = chapterId,
                targetChapterName = chapterName,
                viewModel = practiceQuizViewModel,
                onBack = {
                    navController.safePopBackStack()
                },
                onProceedToCountSelection = {
                    navController.navigate(Routes.PRACTICE_QUIZ_COUNT)
                }
            )
        }

        animatedComposable(
            route = Routes.PRACTICE_QUIZ_COUNT,
            anim = NavAnim.forward
        ) {
            PracticeQuizCountSelectionScreen(
                viewModel = practiceQuizViewModel,
                onBack = {
                    navController.safePopBackStack()
                },
                onNavigateToPlayer = { sessionId ->
                    navController.navigate("practice_quiz_player/$sessionId")
                }
            )
        }

        animatedComposable(
            route = Routes.PRACTICE_QUIZ_PLAYER,
            anim = NavAnim.immersive,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""

            PracticeQuizPlayerScreen(
                sessionId = sessionId,
                viewModel = practiceQuizViewModel,
                onBack = {
                    navController.safePopBackStack()
                },
                onNavigateToResult = { sid ->
                    navController.navigate("practice_quiz_result/$sid") {
                        popUpTo(Routes.PRACTICE_QUIZ_COUNT) { inclusive = true }
                    }
                }
            )
        }

        animatedComposable(
            route = Routes.PRACTICE_QUIZ_RESULT,
            anim = NavAnim.forward,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""

            PracticeQuizResultScreen(
                sessionId = sessionId,
                sessionManager = sessionManager,
                viewModel = practiceQuizViewModel,
                onBackToChapters = {
                    if (!navController.popBackStack(Routes.PRACTICE_QUIZ_CHAPTERS, inclusive = true)) {
                        navController.safePopBackStack()
                    }
                },
                onNavigateToFeedback = { sid ->
                    navController.navigate("practice_quiz_feedback/$sid")
                }
            )
        }

        animatedComposable(
            route = Routes.PRACTICE_QUIZ_FEEDBACK,
            anim = NavAnim.forward,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""

            PracticeQuizFeedbackScreen(
                sessionId = sessionId,
                viewModel = practiceQuizViewModel,
                onBack = {
                    navController.safePopBackStack()
                },
                onDone = {
                    if (!navController.popBackStack(Routes.PRACTICE_QUIZ_CHAPTERS, inclusive = true)) {
                        navController.safePopBackStack()
                    }
                }
            )
        }

        animatedComposable(Routes.SAVED_ITEMS, anim = NavAnim.forward) {
            SavedItemsScreen(
                viewModel = savedViewModel,
                onBack = {
                    navController.safePopBackStack()
                }
            )
        }

        animatedComposable(
            route = Routes.DOWNLOADS,
            anim = NavAnim.forward,
            arguments = listOf(
                navArgument("isOfflineOnly") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val isOfflineOnly = backStackEntry.arguments?.getBoolean("isOfflineOnly") ?: false
            DownloadsScreen(
                onBack = {
                    navController.safePopBackStack()
                },
                isOfflineOnly = isOfflineOnly,
                onNavigateOnline = {
                    val isLoggedIn = sessionManager.getAccessToken() != null
                    val destination = if (isLoggedIn) Routes.HOME else Routes.LOGIN
                    navController.navigate(destination) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onPlayVideo = { videoUrl, title, subjectName, subjectColor, isLive ->
                    val encodedUrl = safeUrlEncode(videoUrl)
                    val encodedTitle = safeUrlEncode(title)
                    val encodedSubject = safeUrlEncode(subjectName)
                    val encodedColor = safeUrlEncode(subjectColor)
                    navController.navigate("video_player?url=$encodedUrl&title=$encodedTitle&subject=$encodedSubject&color=$encodedColor&isLive=$isLive")
                },
                onOpenPdf = { filePath, title ->
                    navController.navigate(Routes.pdfViewerRoute(url = filePath, title = title))
                }
            )
        }

        animatedComposable(
            route = Routes.SMART_NOTES,
            anim = NavAnim.forward,
            arguments = listOf(
                navArgument("subjectCode") { type = NavType.StringType },
                navArgument("title") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                },
                navArgument("color") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                },
                navArgument("phaseId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val subjectCode = backStackEntry.arguments?.getString("subjectCode") ?: ""
            val title = safeUrlDecode(backStackEntry.arguments?.getString("title"))
            val color = safeUrlDecode(backStackEntry.arguments?.getString("color"))
            val phaseId = backStackEntry.arguments?.getString("phaseId") ?: ""

            SmartNotesScreen(
                subjectCode = subjectCode,
                subjectTitle = title,
                subjectColorHex = color,
                phaseId = phaseId,
                viewModel = smartNotesViewModel,
                onBack = {
                    navController.safePopBackStack()
                },
                onNavigateToChapterResources = { chapterId, chapterName, subCode, phId ->
                    val encodedName = safeUrlEncode(chapterName)
                    navController.navigate("chapter_resources/$chapterId?name=$encodedName&subjectCode=$subCode&phaseId=$phId")
                }
            )
        }

        animatedComposable(
            route = Routes.CHAPTER_RESOURCES,
            anim = NavAnim.forward,
            arguments = listOf(
                navArgument("chapterId") { type = NavType.StringType },
                navArgument("name") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                },
                navArgument("subjectCode") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                },
                navArgument("phaseId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val chapterId = backStackEntry.arguments?.getString("chapterId") ?: ""
            val chapterName = safeUrlDecode(backStackEntry.arguments?.getString("name"))
            val subjectCode = backStackEntry.arguments?.getString("subjectCode") ?: ""
            val phaseId = backStackEntry.arguments?.getString("phaseId") ?: ""

            ChapterResourcesScreen(
                chapterId = chapterId,
                chapterName = chapterName,
                subjectCode = subjectCode,
                phaseId = phaseId,
                viewModel = smartNotesViewModel,
                onBack = {
                    navController.safePopBackStack()
                }
            )
        }

        animatedComposable(
            route = Routes.REPORT_CARD,
            anim = NavAnim.forward,
            arguments = listOf(
                navArgument("programId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                },
                navArgument("programTitle") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                },
                navArgument("phaseId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val programId = backStackEntry.arguments?.getString("programId")?.ifBlank { null }
            val programTitle = safeUrlDecode(backStackEntry.arguments?.getString("programTitle")).ifBlank { null }
            val phaseId = backStackEntry.arguments?.getString("phaseId")?.ifBlank { null }

            val reportCardViewModel: ReportCardViewModel = viewModel(
                factory = ReportCardViewModelFactory(
                    apiService = apiService,
                    sessionManager = sessionManager,
                    leaderboardDao = appDatabase.leaderboardStudentDao()
                )
            )

            LaunchedEffect(programId, phaseId) {
                reportCardViewModel.initialize(
                    programId = programId,
                    programTitle = programTitle,
                    initialPhaseId = phaseId
                )
            }

            ReportCardScreen(
                viewModel = reportCardViewModel,
                onBack = {
                    navController.safePopBackStack()
                }
            )
        }

        animatedComposable(
            route = Routes.LIVE_CLASS,
            anim = NavAnim.immersive,
            arguments = listOf(
                navArgument("classId") { type = NavType.StringType },
                navArgument("lessonId") { type = NavType.StringType },
                navArgument("lessonTitle") { type = NavType.StringType; defaultValue = "" },
                navArgument("subjectName") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val classId = backStackEntry.arguments?.getString("classId") ?: ""
            val lessonId = backStackEntry.arguments?.getString("lessonId") ?: ""
            val lessonTitle = safeUrlDecode(backStackEntry.arguments?.getString("lessonTitle"))
            val subjectName = safeUrlDecode(backStackEntry.arguments?.getString("subjectName"))

            val selectedLesson = courseViewModel.uiState.value.selectedLesson
            val candidateUrls: List<String> = selectedLesson?.candidateStreamUrls ?: emptyList()
            val fallbackSubject = courseViewModel.uiState.value.selectedSubjectTitle
                .ifBlank { selectedLesson?.subject_name.orEmpty() }

            com.example.liveclass.LiveClassPage(
                classId = classId,
                lessonId = lessonId,
                lessonTitle = lessonTitle.ifBlank { selectedLesson?.title ?: "লাইভ ক্লাস" },
                subjectName = subjectName.ifBlank { fallbackSubject.ifBlank { "বিষয়" } },
                initialCandidateUrls = candidateUrls,
                sessionManager = sessionManager,
                onBack = {
                    navController.safePopBackStack()
                }
            )
        }
    }

    if (showSessionExpiredDialog) {
        com.example.ui.dialogs.SessionExpiredDialog(
            onReLogin = {
                showSessionExpiredDialog = false
                sessionManager.clearSession()
                sessionManager.resetUnauthorizedNotified()
                authViewModel.logout()
                navController.navigate(Routes.LOGIN) {
                    popUpTo(0) { inclusive = true }
                }
            }
        )
    }
}
