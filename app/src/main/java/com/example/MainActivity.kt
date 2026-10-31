package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.example.soruhavuzu.data.SessionType
import com.example.soruhavuzu.data.SoruHavuzuDatabase
import com.example.soruhavuzu.data.SoruHavuzuRepository
import com.example.soruhavuzu.ui.HomePoolScreen
import com.example.soruhavuzu.ui.MainTab
import com.example.soruhavuzu.ui.ModerationPanelScreen
import com.example.soruhavuzu.ui.ProfileAndDockerScreen
import com.example.soruhavuzu.ui.SavedReviewScreen
import com.example.soruhavuzu.ui.SolveSessionScreen
import com.example.soruhavuzu.ui.SoruHavuzuViewModel
import com.example.soruhavuzu.ui.UploadQuestionScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            SoruHavuzuDatabase::class.java,
            "soruhavuzu_v1.db"
        ).fallbackToDestructiveMigration(false).build()
    }

    private val repository by lazy {
        SoruHavuzuRepository(database.dao(), applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val vm: SoruHavuzuViewModel = viewModel(
                    factory = SoruHavuzuViewModel.Factory(repository)
                )
                SoruHavuzuApp(viewModel = vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoruHavuzuApp(viewModel: SoruHavuzuViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Sub-tabs navigate back to HOME_POOL
    if (uiState.activeSession == null && uiState.currentTab != MainTab.HOME_POOL) {
        BackHandler {
            viewModel.selectTab(MainTab.HOME_POOL)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (uiState.activeSession == null) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SoruHavuzu",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Elo/Rasch v1",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            if (uiState.activeSession == null) {
                NavigationBar {
                    NavigationBarItem(
                        selected = uiState.currentTab == MainTab.HOME_POOL,
                        onClick = { viewModel.selectTab(MainTab.HOME_POOL) },
                        icon = {
                            Icon(
                                imageVector = if (uiState.currentTab == MainTab.HOME_POOL) Icons.Filled.Home else Icons.Outlined.Home,
                                contentDescription = "Soru Havuzu"
                            )
                        },
                        label = { Text("Havuz") },
                        modifier = Modifier.testTag("nav_tab_home")
                    )
                    NavigationBarItem(
                        selected = uiState.currentTab == MainTab.UPLOAD,
                        onClick = { viewModel.selectTab(MainTab.UPLOAD) },
                        icon = {
                            Icon(
                                imageVector = if (uiState.currentTab == MainTab.UPLOAD) Icons.Filled.AddCircle else Icons.Outlined.AddCircleOutline,
                                contentDescription = "Soru Yükle"
                            )
                        },
                        label = { Text("Soru Yükle") },
                        modifier = Modifier.testTag("nav_tab_upload")
                    )
                    NavigationBarItem(
                        selected = uiState.currentTab == MainTab.SAVED_REVIEW,
                        onClick = { viewModel.selectTab(MainTab.SAVED_REVIEW) },
                        icon = {
                            Icon(
                                imageVector = if (uiState.currentTab == MainTab.SAVED_REVIEW) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Tekrar ve Favori"
                            )
                        },
                        label = { Text("Yanlışlarım") },
                        modifier = Modifier.testTag("nav_tab_saved")
                    )
                    NavigationBarItem(
                        selected = uiState.currentTab == MainTab.MODERATION,
                        onClick = { viewModel.selectTab(MainTab.MODERATION) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    val count = uiState.moderationQueueQuestions.size
                                    if (count > 0) {
                                        Badge { Text("$count") }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (uiState.currentTab == MainTab.MODERATION) Icons.Filled.Gavel else Icons.Outlined.Gavel,
                                    contentDescription = "Moderasyon"
                                )
                            }
                        },
                        label = { Text("Denetim") },
                        modifier = Modifier.testTag("nav_tab_moderation")
                    )
                    NavigationBarItem(
                        selected = uiState.currentTab == MainTab.PROFILE,
                        onClick = { viewModel.selectTab(MainTab.PROFILE) },
                        icon = {
                            Icon(
                                imageVector = if (uiState.currentTab == MainTab.PROFILE) Icons.Filled.Person else Icons.Outlined.Person,
                                contentDescription = "Profil ve Sistem"
                            )
                        },
                        label = { Text("Profil") },
                        modifier = Modifier.testTag("nav_tab_profile")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
            ) {
                // Status Notification Banner (Push/Upload/Moderation feedback)
                AnimatedVisibility(visible = uiState.statusBannerMessage != null) {
                    val msg = uiState.statusBannerMessage ?: ""
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("status_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.dismissBanner() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Bildirimi Kapat",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

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
                        onCloseSession = viewModel::closeSession
                    )
                } else {
                    when (uiState.currentTab) {
                        MainTab.HOME_POOL -> {
                            HomePoolScreen(
                                uiState = uiState,
                                onSelectSingleBucket = viewModel::selectSingleDifficultyBucket,
                                onSelectRange = viewModel::selectDifficultyRange,
                                onSelectTopic = viewModel::selectTopic,
                                onToggleIncludeSolved = viewModel::toggleIncludeSolved,
                                onStartPractice = {
                                    viewModel.startSession(SessionType.PRACTICE)
                                },
                                onStartChallenge = {
                                    viewModel.startSession(SessionType.CHALLENGE)
                                },
                                onStartReview = {
                                    viewModel.startSession(SessionType.REVIEW)
                                },
                                onOpenQuestionDirect = { bucket ->
                                    viewModel.selectSingleDifficultyBucket(bucket)
                                    viewModel.startSession(
                                        sessionType = SessionType.PRACTICE,
                                        minBucketOverride = bucket,
                                        maxBucketOverride = bucket
                                    )
                                }
                            )
                        }

                        MainTab.UPLOAD -> {
                            UploadQuestionScreen(
                                uiState = uiState,
                                onSanitizeImage = viewModel::processPickedImage,
                                onSubmitQuestion = viewModel::submitNewQuestion
                            )
                        }

                        MainTab.SAVED_REVIEW -> {
                            SavedReviewScreen(
                                uiState = uiState,
                                onStartReviewSession = {
                                    viewModel.startSession(SessionType.REVIEW)
                                },
                                onRemoveFromWrongNotes = viewModel::removeFromWrongNotes,
                                onToggleFavorite = viewModel::toggleFavorite
                            )
                        }

                        MainTab.MODERATION -> {
                            ModerationPanelScreen(
                                uiState = uiState,
                                onApproveQuestion = viewModel::approveQuestionInModeration,
                                onRemoveQuestion = viewModel::removeQuestionInModeration,
                                onCreateNewVersion = viewModel::createNewQuestionVersion
                            )
                        }

                        MainTab.PROFILE -> {
                            ProfileAndDockerScreen(
                                uiState = uiState,
                                onSaveSettings = viewModel::updateUserSettings,
                                onTestDocker = viewModel::testDockerConnection,
                                onExportData = viewModel::exportUserDataJson,
                                onDismissExportDialog = viewModel::dismissExportedJsonDialog,
                                onResetAccount = viewModel::resetAccountData
                            )
                        }
                    }
                }
            }
        }
    }
}
