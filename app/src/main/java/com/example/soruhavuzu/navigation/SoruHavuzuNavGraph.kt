package com.example.soruhavuzu.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.soruhavuzu.data.SessionType
import com.example.soruhavuzu.ui.HomePoolScreen
import com.example.soruhavuzu.ui.HomeScreen
import com.example.soruhavuzu.ui.LoadQuestionScreen
import com.example.soruhavuzu.ui.ModerationPanelScreen
import com.example.soruhavuzu.ui.OnboardingScreen
import com.example.soruhavuzu.ui.ProfileAndDockerScreen
import com.example.soruhavuzu.ui.QuestionSolvingScreen
import com.example.soruhavuzu.ui.SavedReviewScreen
import com.example.soruhavuzu.ui.ScreenTitleHeader
import com.example.soruhavuzu.ui.SolveSessionScreen
import com.example.soruhavuzu.ui.SoruHavuzuUiState
import com.example.soruhavuzu.ui.SoruHavuzuViewModel
import com.example.soruhavuzu.ui.UploadQuestionScreen

/**
 * Tek-Activity (Single-Activity) mimarisi üzerinde Jetpack Navigation Compose
 * ile tanımlanmış ana navigasyon grafiği (NavHost).
 *
 * Her ana rota (Onboarding, Home, QuestionSolving, LoadQuestion) ilgili ekran başlığını
 * gösteren Text bileşenini barındırır ve aynı zamanda tam etkileşimli SoruHavuzu
 * özelliklerini sunar.
 */
@Composable
fun SoruHavuzuNavHost(
    navController: NavHostController,
    viewModel: SoruHavuzuViewModel,
    uiState: SoruHavuzuUiState,
    startDestination: String,
    modifier: Modifier = Modifier
) {
    var isSimpleSkeletonMode by rememberSaveable { mutableStateOf(false) }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // 1. ONBOARDING EKRANI
        composable(route = SoruHavuzuRoutes.ROUTE_ONBOARDING) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenTitleHeader(
                    title = "Onboarding",
                    subtitle = "Hedef Sınav, Doğum Yılı ve Giriş Kurulum Ekranı",
                    isSimpleMode = isSimpleSkeletonMode,
                    onToggleSimpleMode = { isSimpleSkeletonMode = it }
                )
                if (isSimpleSkeletonMode) {
                    OnboardingScreen(modifier = Modifier.fillMaxSize())
                } else {
                    val profile = uiState.userProfile
                    OnboardingScreen(
                        initialNickname = profile?.nickname ?: "MatematikGezgini",
                        initialExam = profile?.targetExam ?: "TYT",
                        initialBirthYear = profile?.birthYear ?: 2007,
                        onCompleteOnboarding = { nickname, email, provider, targetExam, birthYear, selfLevel ->
                            viewModel.completeOnboarding(
                                nickname = nickname,
                                email = email,
                                authProvider = provider,
                                targetExam = targetExam,
                                birthYear = birthYear,
                                initialSelfLevel = selfLevel
                            )
                            navController.navigateTopLevel(SoruHavuzuRoutes.ROUTE_HOME)
                        }
                    )
                }
            }
        }

        // 2. HOME EKRANI
        composable(route = SoruHavuzuRoutes.ROUTE_HOME) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenTitleHeader(
                    title = "Home",
                    subtitle = "Ana Sayfa · 1-10 Zorluk Seçici ve Beni Zorla Modu",
                    isSimpleMode = isSimpleSkeletonMode,
                    onToggleSimpleMode = { isSimpleSkeletonMode = it }
                )
                if (isSimpleSkeletonMode) {
                    HomeScreen(modifier = Modifier.fillMaxSize())
                } else {
                    HomePoolScreen(
                        uiState = uiState,
                        onSelectSingleBucket = viewModel::selectSingleDifficultyBucket,
                        onSelectRange = viewModel::selectDifficultyRange,
                        onSelectTopic = viewModel::selectTopic,
                        onToggleIncludeSolved = viewModel::toggleIncludeSolved,
                        onStartPractice = {
                            viewModel.startSession(SessionType.PRACTICE)
                            navController.navigateTopLevel(SoruHavuzuRoutes.ROUTE_QUESTION_SOLVING)
                        },
                        onStartChallenge = {
                            viewModel.startSession(SessionType.CHALLENGE)
                            navController.navigateTopLevel(SoruHavuzuRoutes.ROUTE_QUESTION_SOLVING)
                        },
                        onStartReview = {
                            navController.navigate(SoruHavuzuRoutes.ROUTE_SAVED_REVIEW) {
                                launchSingleTop = true
                            }
                        },
                        onOpenQuestionDirect = { bucket ->
                            viewModel.selectSingleDifficultyBucket(bucket)
                            viewModel.startSession(
                                sessionType = SessionType.PRACTICE,
                                minBucketOverride = bucket,
                                maxBucketOverride = bucket
                            )
                            navController.navigateTopLevel(SoruHavuzuRoutes.ROUTE_QUESTION_SOLVING)
                        }
                    )
                }
            }
        }

        // 3. QUESTION SOLVING EKRANI
        composable(route = SoruHavuzuRoutes.ROUTE_QUESTION_SOLVING) {
            LaunchedEffect(uiState.activeSession) {
                if (uiState.activeSession == null) {
                    viewModel.startSession(SessionType.PRACTICE)
                }
            }
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenTitleHeader(
                    title = "QuestionSolving",
                    subtitle = "Soru Çözme, Anında Çözüm Açıklaması ve Elo/Rasch Kalibrasyonu",
                    isSimpleMode = isSimpleSkeletonMode,
                    onToggleSimpleMode = { isSimpleSkeletonMode = it }
                )
                if (isSimpleSkeletonMode) {
                    QuestionSolvingScreen(modifier = Modifier.fillMaxSize())
                } else {
                    val activeSession = uiState.activeSession
                    if (activeSession != null) {
                        SolveSessionScreen(
                            session = activeSession,
                            userTheta = uiState.userProfile?.globalTheta ?: 0.25,
                            onSelectOption = viewModel::selectOptionInSession,
                            onSubmitAnswer = viewModel::submitCurrentAnswer,
                            onNextQuestion = viewModel::proceedToNextQuestion,
                            onExpandToNeighborRange = viewModel::expandSessionToSuggestedRange,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onReportQuestion = viewModel::reportQuestion,
                            onFinishSession = viewModel::finishSessionSummary,
                            onCloseSession = {
                                viewModel.closeSession()
                                navController.navigateTopLevel(SoruHavuzuRoutes.ROUTE_HOME)
                            }
                        )
                    } else {
                        QuestionSolvingScreen(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }

        // 4. LOAD QUESTION EKRANI
        composable(route = SoruHavuzuRoutes.ROUTE_LOAD_QUESTION) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenTitleHeader(
                    title = "LoadQuestion",
                    subtitle = "Soru Yükleme, 1-10 Zorluk Tahmini ve Canlı Önizleme",
                    isSimpleMode = isSimpleSkeletonMode,
                    onToggleSimpleMode = { isSimpleSkeletonMode = it }
                )
                if (isSimpleSkeletonMode) {
                    LoadQuestionScreen(modifier = Modifier.fillMaxSize())
                } else {
                    UploadQuestionScreen(
                        uiState = uiState,
                        onSanitizeImage = viewModel::processPickedImage,
                        onSubmitQuestion = viewModel::submitNewQuestion
                    )
                }
            }
        }

        // 5. SAVED / OFFLINE REVIEW SCREEN
        composable(route = SoruHavuzuRoutes.ROUTE_SAVED_REVIEW) {
            SavedReviewScreen(
                uiState = uiState,
                onStartReviewSession = {
                    viewModel.startSession(SessionType.REVIEW)
                    navController.navigateTopLevel(SoruHavuzuRoutes.ROUTE_QUESTION_SOLVING)
                },
                onRemoveFromWrongNotes = viewModel::removeFromWrongNotes,
                onToggleFavorite = viewModel::toggleFavorite
            )
        }

        // 6. MODERATION QUEUE & KEY SUSPECT SCREEN
        composable(route = SoruHavuzuRoutes.ROUTE_MODERATION) {
            ModerationPanelScreen(
                uiState = uiState,
                onApproveQuestion = viewModel::approveQuestionInModeration,
                onRemoveQuestion = viewModel::removeQuestionInModeration,
                onCreateNewVersion = viewModel::createNewQuestionVersion
            )
        }

        // 7. PROFILE, STATISTICS & DOCKER SCREEN
        composable(route = SoruHavuzuRoutes.ROUTE_PROFILE) {
            ProfileAndDockerScreen(
                uiState = uiState,
                onSaveSettings = viewModel::updateUserSettings,
                onTestDocker = viewModel::testDockerConnection,
                onExportData = viewModel::exportUserDataJson,
                onDismissExportDialog = viewModel::dismissExportedJsonDialog,
                onResetAccount = viewModel::resetAccountData,
                onReopenOnboarding = {
                    navController.navigateTopLevel(SoruHavuzuRoutes.ROUTE_ONBOARDING)
                }
            )
        }
    }
}

fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
