package com.example.soruhavuzu.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.School
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * proje.md §8'de tanımlanan temel ekranlar (Onboarding, Home, QuestionSolving, LoadQuestion)
 * ve yardımcı ekranlar için merkezi Navigation Compose rotaları.
 */
object SoruHavuzuRoutes {
    const val ROUTE_ONBOARDING = "onboarding"
    const val ROUTE_HOME = "home"
    const val ROUTE_QUESTION_SOLVING = "question_solving"
    const val ROUTE_LOAD_QUESTION = "load_question"
    const val ROUTE_SAVED_REVIEW = "saved_review"
    const val ROUTE_MODERATION = "moderation"
    const val ROUTE_PROFILE = "profile"
}

enum class BottomNavDestination(
    val route: String,
    val labelTr: String,
    val testTag: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    ONBOARDING(
        route = SoruHavuzuRoutes.ROUTE_ONBOARDING,
        labelTr = "Onboarding",
        testTag = "nav_tab_onboarding",
        selectedIcon = Icons.Filled.School,
        unselectedIcon = Icons.Outlined.School
    ),
    HOME(
        route = SoruHavuzuRoutes.ROUTE_HOME,
        labelTr = "Home",
        testTag = "nav_tab_home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    QUESTION_SOLVING(
        route = SoruHavuzuRoutes.ROUTE_QUESTION_SOLVING,
        labelTr = "QuestionSolving",
        testTag = "nav_tab_solving",
        selectedIcon = Icons.Filled.Quiz,
        unselectedIcon = Icons.Outlined.Quiz
    ),
    LOAD_QUESTION(
        route = SoruHavuzuRoutes.ROUTE_LOAD_QUESTION,
        labelTr = "LoadQuestion",
        testTag = "nav_tab_upload",
        selectedIcon = Icons.Filled.AddCircle,
        unselectedIcon = Icons.Outlined.AddCircleOutline
    ),
    MODERATION(
        route = SoruHavuzuRoutes.ROUTE_MODERATION,
        labelTr = "Denetim",
        testTag = "nav_tab_moderation",
        selectedIcon = Icons.Filled.Gavel,
        unselectedIcon = Icons.Outlined.Gavel
    ),
    PROFILE(
        route = SoruHavuzuRoutes.ROUTE_PROFILE,
        labelTr = "Profil",
        testTag = "nav_tab_profile",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person
    )
}
