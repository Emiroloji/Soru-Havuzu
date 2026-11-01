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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
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
import com.example.soruhavuzu.ui.SolveSessionScreen
import com.example.soruhavuzu.ui.practice.PracticeViewModel
import com.example.soruhavuzu.ui.session.AuthScreen
import com.example.soruhavuzu.ui.session.SessionState
import com.example.soruhavuzu.ui.session.SessionViewModel
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
    practiceViewModel: PracticeViewModel = viewModel(factory = PracticeViewModel.Factory)
) {
    val app = LocalContext.current.applicationContext as SoruHavuzuApplication
    val home by practiceViewModel.home.collectAsStateWithLifecycle()
    val session by practiceViewModel.session.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val tabs = MainTab.entries.filter { it != MainTab.MODERATION || user.canModerate() }

    LaunchedEffect(Unit) { practiceViewModel.initialize(user.targetExamId) }

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
                    MainTab.UPLOAD -> ComingSoon("Soru yükleme", "Sunucuya bağlanan yükleme ekranı bir sonraki adımda geliyor.")
                    MainTab.REVIEW -> ComingSoon("Yanlışlarım ve favoriler", "Yanlış yaptığın ve kaydettiğin sorular sunucuda tutuluyor; listesi bir sonraki adımda geliyor.")
                    MainTab.MODERATION -> ComingSoon("Moderasyon", "İnceleme kuyruğu ekranı sonraki adımlarda geliyor.")
                    MainTab.PROFILE -> ProfileSummary(user = user, onLogout = onLogout)
                }
            }
        }
    }
}

@Composable
private fun ProfileSummary(user: UserDto, onLogout: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(user.nickname, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(user.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Seviye: ${user.level} / 10", style = MaterialTheme.typography.bodyLarge)
                Text("İtibar: ${user.reputation}", style = MaterialTheme.typography.bodyLarge)
                Text("Rol: ${user.role}", style = MaterialTheme.typography.bodyLarge)
            }
        }
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().testTag("logout_button")) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Spacer(modifier = Modifier.padding(4.dp))
            Text("Çıkış Yap")
        }
    }
}

@Composable
private fun ComingSoon(title: String, message: String) {
    CenteredMessage {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
