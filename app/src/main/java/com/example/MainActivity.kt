package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.soruhavuzu.SoruHavuzuApplication
import com.example.soruhavuzu.data.remote.UserDto
import com.example.soruhavuzu.ui.HomePoolScreen
import com.example.soruhavuzu.ui.ModerationPanelScreen
import com.example.soruhavuzu.ui.SolveSessionScreen
import com.example.soruhavuzu.ui.UploadQuestionScreen
import com.example.soruhavuzu.ui.moderation.ModerationViewModel
import com.example.soruhavuzu.ui.practice.PracticeViewModel
import com.example.soruhavuzu.ui.profile.ProfileScreen
import com.example.soruhavuzu.ui.profile.ProfileViewModel
import com.example.soruhavuzu.ui.review.ReviewScreen
import com.example.soruhavuzu.ui.review.ReviewViewModel
import com.example.soruhavuzu.ui.session.AuthScreen
import com.example.soruhavuzu.ui.session.SessionState
import com.example.soruhavuzu.ui.session.SessionViewModel
import com.example.soruhavuzu.ui.upload.UploadViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SoruHavuzuRoot()
            }
        }
    }
}

private enum class MainTab(val label: String, val selectedIcon: ImageVector, val icon: ImageVector) {
    HOME("Havuz", Icons.Filled.Home, Icons.Outlined.Home),
    UPLOAD("Yükle", Icons.Filled.AddCircle, Icons.Outlined.AddCircleOutline),
    REVIEW("Tekrar", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder),
    MODERATION("Denetim", Icons.Filled.Gavel, Icons.Outlined.Gavel),
    PROFILE("Profil", Icons.Filled.Person, Icons.Outlined.Person)
}

/** Backend'deki Permissions.canModerate ile aynı kural; sekmeyi göstermek için (yetkiyi sunucu yine kontrol eder). */
private fun UserDto.canModerate() = reputation >= 1000 || role == "MODERATOR" || role == "ADMIN"

@Composable
fun SoruHavuzuRoot(sessionViewModel: SessionViewModel = viewModel(factory = SessionViewModel.Factory)) {
    val state by sessionViewModel.state.collectAsStateWithLifecycle()
    val form by sessionViewModel.form.collectAsStateWithLifecycle()

    when (val s = state) {
        SessionState.Loading -> CenteredMessage { CircularProgressIndicator() }
        is SessionState.Unreachable -> CenteredMessage {
            Text(s.message, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = sessionViewModel::restore) { Text("Tekrar Dene") }
            TextButton(onClick = { sessionViewModel.logout() }) { Text("Çıkış Yap") }
        }
        SessionState.LoggedOut -> Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                AuthScreen(
                    form = form,
                    onLogin = sessionViewModel::login,
                    onRegister = sessionViewModel::register,
                    onFieldChanged = sessionViewModel::clearFormError
                )
            }
        }
        is SessionState.LoggedIn -> MainScaffold(
            user = s.user,
            onUserChanged = sessionViewModel::refreshUser,
            onLogout = { sessionViewModel.logout() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    user: UserDto,
    onUserChanged: () -> Unit,
    onLogout: () -> Unit,
    practiceViewModel: PracticeViewModel = viewModel(factory = PracticeViewModel.Factory),
    uploadViewModel: UploadViewModel = viewModel(factory = UploadViewModel.Factory),
    reviewViewModel: ReviewViewModel = viewModel(factory = ReviewViewModel.Factory),
    profileViewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
    moderationViewModel: ModerationViewModel = viewModel(factory = ModerationViewModel.Factory)
) {
    val app = LocalContext.current.applicationContext as SoruHavuzuApplication
    val home by practiceViewModel.home.collectAsStateWithLifecycle()
    val session by practiceViewModel.session.collectAsStateWithLifecycle()
    val upload by uploadViewModel.state.collectAsStateWithLifecycle()
    val review by reviewViewModel.state.collectAsStateWithLifecycle()
    val profile by profileViewModel.state.collectAsStateWithLifecycle()
    val moderation by moderationViewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val tabs = MainTab.entries.filter { it != MainTab.MODERATION || user.canModerate() }

    LaunchedEffect(Unit) { practiceViewModel.initialize(user.targetExamId) }
    // Sekmeye her dönüşte (ve oturum kapanınca) o sekmenin verisini tazele
    LaunchedEffect(tab, session == null) {
        if (session != null) return@LaunchedEffect
        when (tab) {
            MainTab.UPLOAD -> uploadViewModel.loadMyQuestions()
            MainTab.REVIEW -> reviewViewModel.load()
            MainTab.PROFILE -> profileViewModel.load()
            MainTab.MODERATION -> moderationViewModel.load()
            else -> Unit
        }
    }
    LaunchedEffect(moderation.message) {
        moderation.message?.let {
            snackbar.showSnackbar(it)
            moderationViewModel.dismissMessage()
        }
    }
    LaunchedEffect(upload.message) {
        upload.message?.let {
            snackbar.showSnackbar(it)
            uploadViewModel.dismissMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (session == null) {
                TopAppBar(
                    title = { Text("SoruHavuzu", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        },
        bottomBar = {
            if (session == null) {
                NavigationBar(modifier = Modifier.testTag("main_bottom_navigation")) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(if (tab == t) t.selectedIcon else t.icon, contentDescription = t.label) },
                            label = { Text(t.label, maxLines = 1) },
                            modifier = Modifier.testTag("nav_tab_${t.name.lowercase()}")
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(modifier = Modifier.fillMaxSize().widthIn(max = 680.dp)) {
                val active = session
                if (active != null) {
                    SolveSessionScreen(
                        session = active,
                        resolveImageUrl = app::absoluteUrl,
                        onSelectOption = practiceViewModel::selectOption,
                        onSubmitAnswer = practiceViewModel::submitAnswer,
                        onNextQuestion = practiceViewModel::nextQuestion,
                        onExpandToNeighborRange = practiceViewModel::expandToRange,
                        onToggleFavorite = practiceViewModel::toggleFavorite,
                        onReportQuestion = { reason, note ->
                            practiceViewModel.report(reason, note) { message ->
                                scope.launch { snackbar.showSnackbar(message) }
                            }
                        },
                        onFinishSession = practiceViewModel::finishSession,
                        onCloseSession = {
                            practiceViewModel.closeSession()
                            onUserChanged()
                        },
                        onDismissError = practiceViewModel::dismissSessionError
                    )
                } else when (tab) {
                    MainTab.HOME -> HomePoolScreen(
                        user = user,
                        state = home,
                        onSelectExam = practiceViewModel::selectExam,
                        onSelectSubject = practiceViewModel::selectSubject,
                        onSelectTopic = practiceViewModel::selectTopic,
                        onSelectSingleBucket = practiceViewModel::selectBucket,
                        onSelectRange = practiceViewModel::selectRange,
                        onToggleIncludeSolved = practiceViewModel::setIncludeSolved,
                        onStartPractice = { practiceViewModel.startPractice(user.level) },
                        onStartChallenge = { practiceViewModel.startChallenge(user.level) },
                        onOpenReview = { tab = MainTab.REVIEW },
                        onRetry = practiceViewModel::refresh
                    )
                    MainTab.UPLOAD -> UploadQuestionScreen(
                        user = user,
                        catalog = home.catalog,
                        myQuestions = upload.myQuestions,
                        isSubmitting = upload.isSubmitting,
                        onSubmitQuestion = { draft, onSuccess ->
                            uploadViewModel.submit(draft) {
                                onSuccess()
                                onUserChanged() // doğrudan yayın itibar kazandırır
                            }
                        }
                    )
                    MainTab.REVIEW -> ReviewScreen(
                        state = review,
                        onStartReview = { questions -> practiceViewModel.startReview(questions, user.level) },
                        onRemoveWrongNote = reviewViewModel::removeFromWrongNotes,
                        onRetry = reviewViewModel::load
                    )
                    MainTab.MODERATION -> ModerationPanelScreen(
                        state = moderation,
                        resolveImageUrl = app::absoluteUrl,
                        onApproveQuestion = moderationViewModel::approve,
                        onRemoveQuestion = moderationViewModel::remove,
                        onCreateNewVersion = moderationViewModel::createVersion,
                        onRetry = moderationViewModel::load
                    )
                    MainTab.PROFILE -> ProfileScreen(user = user, state = profile, onLogout = onLogout)
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) { content() }
}
