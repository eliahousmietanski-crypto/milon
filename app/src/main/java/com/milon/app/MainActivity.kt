package com.milon.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.milon.app.ui.ContributionsScreen
import com.milon.app.ui.EditEntryScreen
import com.milon.app.ui.EntryListScreen
import com.milon.app.ui.EntryScreen
import com.milon.app.ui.ListKind
import com.milon.app.ui.RootScreen
import com.milon.app.ui.SearchScreen
import com.milon.app.ui.SearchState
import com.milon.app.ui.SettingsScreen
import com.milon.app.ui.theme.MilonTheme

val LocalApp = staticCompositionLocalOf<MilonApp> { error("MilonApp manquant") }

class MainActivity : ComponentActivity() {
    /** Fichier de propositions ouvert depuis une autre application. */
    private var pendingImport by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        val app = application as MilonApp
        setContent {
            val scale by app.settings.hebrewScale.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalApp provides app) {
                MilonTheme(hebrewScale = scale) {
                    MilonNav(
                        pendingImport = pendingImport,
                        onImportHandled = { pendingImport = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> streamExtra(intent)
            else -> null
        }
        if (uri != null) pendingImport = uri
    }

    @Suppress("DEPRECATION")
    private fun streamExtra(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
}

object Routes {
    const val SEARCH = "search"
    const val CONTRIB = "contrib"
    const val SETTINGS = "settings"
    fun entry(uid: String) = "entry/${Uri.encode(uid)}"
    fun root(root: String) = "root/${Uri.encode(root)}"
    fun list(kind: ListKind) = "list/${kind.name}"
    fun edit(uid: String? = null, lemma: String? = null) =
        "edit?uid=${Uri.encode(uid ?: "")}&lemma=${Uri.encode(lemma ?: "")}"
}

@Composable
private fun MilonNav(pendingImport: Uri?, onImportHandled: () -> Unit) {
    val app = LocalApp.current
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    // L'état de recherche survit à la navigation (on retrouve sa recherche en revenant)
    val search = remember { SearchState(app.repo, scope) }

    LaunchedEffect(Unit) {
        app.repo.open()
        search.refresh()
        app.updates.checkInBackground()
    }
    LaunchedEffect(pendingImport) {
        if (pendingImport != null) nav.navigate(Routes.CONTRIB) { launchSingleTop = true }
    }

    val enter = slideInHorizontally(tween(220)) { it / 6 } + fadeIn(tween(220))
    val exit = fadeOut(tween(150))
    val popEnter = fadeIn(tween(220))
    val popExit = slideOutHorizontally(tween(200)) { it / 6 } + fadeOut(tween(200))

    NavHost(
        navController = nav,
        startDestination = Routes.SEARCH,
        enterTransition = { enter },
        exitTransition = { exit },
        popEnterTransition = { popEnter },
        popExitTransition = { popExit },
    ) {
        composable(Routes.SEARCH) {
            SearchScreen(search, nav)
        }
        composable(
            "entry/{uid}",
            arguments = listOf(navArgument("uid") { type = NavType.StringType }),
        ) { back ->
            EntryScreen(back.arguments?.getString("uid").orEmpty(), nav)
        }
        composable(
            "root/{root}",
            arguments = listOf(navArgument("root") { type = NavType.StringType }),
        ) { back ->
            RootScreen(back.arguments?.getString("root").orEmpty(), nav)
        }
        composable(
            "list/{kind}",
            arguments = listOf(navArgument("kind") { type = NavType.StringType }),
        ) { back ->
            EntryListScreen(ListKind.valueOf(back.arguments?.getString("kind") ?: ListKind.HISTORY.name), nav)
        }
        composable(
            "edit?uid={uid}&lemma={lemma}",
            arguments = listOf(
                navArgument("uid") { type = NavType.StringType; defaultValue = "" },
                navArgument("lemma") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { back ->
            EditEntryScreen(
                uid = back.arguments?.getString("uid").orEmpty().ifBlank { null },
                initialLemma = back.arguments?.getString("lemma").orEmpty(),
                nav = nav,
            )
        }
        composable(Routes.CONTRIB) {
            ContributionsScreen(nav, pendingImport, onImportHandled)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(nav)
        }
    }
}

fun NavHostController.back() {
    if (previousBackStackEntry != null) popBackStack()
}
