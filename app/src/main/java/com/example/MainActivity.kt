package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.firebase.configureAppCheck
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.components.CosmicStarfieldBackground
import com.example.ui.components.FreeModePill
import com.example.ui.components.StudioLogoBadge
import com.example.ui.screens.ApiUsageScreen
import com.example.ui.screens.CreateVideoScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.MyProjectsScreen
import com.example.ui.screens.ScriptWriterScreen
import com.example.ui.screens.StudioSettingsScreen
import com.example.ui.screens.VideoLibraryScreen
import com.example.ui.screens.VoiceStudioScreen
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.NebulaSurface
import com.example.ui.theme.NebulaTextSecondary
import com.example.ui.theme.StarlightWhite
import com.example.ui.theme.XaviourStudioTheme
import com.example.ui.viewmodel.StudioUiState
import com.example.ui.viewmodel.StudioViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

object StudioRoutes {
    const val HOME = "home"
    const val CREATE_VIDEO = "create_video"
    const val SCRIPT_WRITER = "script_writer"
    const val VOICE_STUDIO = "voice_studio"
    const val MY_PROJECTS = "my_projects"
    const val VIDEO_LIBRARY = "video_library"
    const val SETTINGS = "settings"
    const val API_USAGE = "api_usage"
}

data class StudioDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val isPrimaryBottomNav: Boolean
)

val ALL_STUDIO_DESTINATIONS = listOf(
    StudioDestination(StudioRoutes.HOME, "Home", Icons.Default.Home, true),
    StudioDestination(StudioRoutes.CREATE_VIDEO, "Create", Icons.Default.AddCircle, true),
    StudioDestination(StudioRoutes.SCRIPT_WRITER, "Script", Icons.Default.EditNote, true),
    StudioDestination(StudioRoutes.VOICE_STUDIO, "Voice", Icons.Default.GraphicEq, true),
    StudioDestination(StudioRoutes.MY_PROJECTS, "Projects", Icons.Default.FolderCopy, false),
    StudioDestination(StudioRoutes.VIDEO_LIBRARY, "Library", Icons.Default.VideoLibrary, true),
    StudioDestination(StudioRoutes.API_USAGE, "Credits", Icons.Default.Analytics, false),
    StudioDestination(StudioRoutes.SETTINGS, "Settings", Icons.Default.Settings, false)
)

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check setup with launch intent before UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            XaviourStudioTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            val viewModel: StudioViewModel = viewModel(
                key = user.uid,
                factory = StudioViewModel.provideFactory(user.uid)
            )
            XaviourStudioShell(
                viewModel = viewModel,
                userEmail = user.email
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XaviourStudioShell(
    viewModel: StudioViewModel,
    userEmail: String?
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: StudioRoutes.HOME

    val projectsState by viewModel.projectsState.collectAsStateWithLifecycle()
    val usageLogsState by viewModel.usageLogsState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val freeModeStatus by viewModel.freeModeStatus.collectAsStateWithLifecycle()
    val customLogoUri by viewModel.customLogoUri.collectAsStateWithLifecycle()
    val logoPosition by viewModel.logoPosition.collectAsStateWithLifecycle()
    val logoSize by viewModel.logoSize.collectAsStateWithLifecycle()
    val backgroundMusicName by viewModel.backgroundMusicName.collectAsStateWithLifecycle()
    val hardwareSupports1080p by viewModel.hardwareSupports1080p.collectAsStateWithLifecycle()
    val brandingPreviewPaths by viewModel.brandingPreviewPaths.collectAsStateWithLifecycle()
    val ttsCapabilities by viewModel.ttsCapabilities.collectAsStateWithLifecycle()
    val isScriptBusy by viewModel.isScriptActionBusy.collectAsStateWithLifecycle()
    val selectedProjectId by viewModel.selectedProjectId.collectAsStateWithLifecycle()
    val activeScenes by viewModel.selectedScenesState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val pipelineState by viewModel.pipelineState.collectAsStateWithLifecycle()
    val storageBytes by viewModel.storageBytes.collectAsStateWithLifecycle()
    val voicePreviewStatus by viewModel.voicePreviewStatus.collectAsStateWithLifecycle()
    val isPlayingAudio by viewModel.isPlayingAudioPreview.collectAsStateWithLifecycle()
    val actionBanner by viewModel.actionBanner.collectAsStateWithLifecycle()

    val projects = (projectsState as? StudioUiState.Success)?.data.orEmpty()
    val usageLogs = (usageLogsState as? StudioUiState.Success)?.data.orEmpty()

    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val credentialManager = remember(context) { CredentialManager.create(context) }

    LaunchedEffect(actionBanner) {
        val msg = actionBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CosmicStarfieldBackground()

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpandedLayout = maxWidth >= 700.dp

            val scaffoldContent: @Composable () -> Unit = {
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = DeepNavy.copy(alpha = 0.85f),
                                titleContentColor = StarlightWhite
                            ),
                            navigationIcon = {
                                if (!isExpandedLayout) {
                                    IconButton(
                                        onClick = { scope.launch { drawerState.open() } },
                                        modifier = Modifier.testTag("open_drawer_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Menu,
                                            contentDescription = "Open studio navigation menu",
                                            tint = StarlightWhite
                                        )
                                    }
                                }
                            },
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StudioLogoBadge(customLogoUri = customLogoUri, sizeDp = 30)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "XAVIOUR AI STUDIO",
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            actions = {
                                FreeModePill(
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        )
                    },
                    bottomBar = {
                        if (!isExpandedLayout) {
                            NavigationBar(
                                containerColor = DeepNavy.copy(alpha = 0.96f),
                                contentColor = StarlightWhite
                            ) {
                                ALL_STUDIO_DESTINATIONS.filter { it.isPrimaryBottomNav }.forEach { dest ->
                                    NavigationBarItem(
                                        selected = currentRoute == dest.route,
                                        onClick = { navigateTo(dest.route) },
                                        icon = {
                                            Icon(imageVector = dest.icon, contentDescription = dest.label)
                                        },
                                        label = { Text(dest.label, maxLines = 1) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MetallicGold,
                                            selectedTextColor = MetallicGold,
                                            indicatorColor = ElectricBlue.copy(alpha = 0.25f),
                                            unselectedIconColor = NebulaTextSecondary,
                                            unselectedTextColor = NebulaTextSecondary
                                        ),
                                        modifier = Modifier.testTag("nav_${dest.route}")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (isExpandedLayout) {
                            NavigationRail(
                                containerColor = DeepNavy.copy(alpha = 0.92f),
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                ALL_STUDIO_DESTINATIONS.forEach { dest ->
                                    NavigationRailItem(
                                        selected = currentRoute == dest.route,
                                        onClick = { navigateTo(dest.route) },
                                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                                        label = { Text(dest.label) },
                                        colors = NavigationRailItemDefaults.colors(
                                            selectedIconColor = MetallicGold,
                                            selectedTextColor = MetallicGold,
                                            indicatorColor = ElectricBlue.copy(alpha = 0.25f),
                                            unselectedIconColor = NebulaTextSecondary,
                                            unselectedTextColor = NebulaTextSecondary
                                        )
                                    )
                                }
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = StudioRoutes.HOME,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            composable(StudioRoutes.HOME) {
                                HomeDashboardScreen(
                                    projectsState = projectsState,
                                    usageLogsState = usageLogsState,
                                    settings = settings,
                                    freeModeStatus = freeModeStatus,
                                    customLogoUri = customLogoUri,
                                    storageBytes = storageBytes,
                                    onNavigateToCreate = { navigateTo(StudioRoutes.CREATE_VIDEO) },
                                    onNavigateToScript = { navigateTo(StudioRoutes.SCRIPT_WRITER) },
                                    onNavigateToVoice = { navigateTo(StudioRoutes.VOICE_STUDIO) },
                                    onNavigateToProjects = { navigateTo(StudioRoutes.MY_PROJECTS) },
                                    onNavigateToLibrary = { navigateTo(StudioRoutes.VIDEO_LIBRARY) },
                                    onNavigateToSettings = { navigateTo(StudioRoutes.SETTINGS) },
                                    onNavigateToUsage = { navigateTo(StudioRoutes.API_USAGE) },
                                    onOpenProject = { proj ->
                                        viewModel.selectProject(proj.id)
                                        navigateTo(
                                            if (proj.status == "completed") StudioRoutes.VIDEO_LIBRARY
                                            else StudioRoutes.SCRIPT_WRITER
                                        )
                                    }
                                )
                            }

                            composable(StudioRoutes.CREATE_VIDEO) {
                                CreateVideoScreen(
                                    formState = formState,
                                    pipelineState = pipelineState,
                                    settings = settings,
                                    backgroundMusicName = backgroundMusicName,
                                    hardwareSupports1080p = hardwareSupports1080p,
                                    onUpdateForm = { viewModel.updateForm(it) },
                                    onAttachImages = { uris -> viewModel.attachImagesToForm(uris) },
                                    onRemoveAttachedImage = { path -> viewModel.removeAttachedImageFromForm(path) },
                                    onSelectBackgroundMusic = { uri -> viewModel.selectBackgroundMusic(uri) },
                                    onGenerateClicked = { viewModel.requestVideoGeneration() },
                                    onTogglePause = { viewModel.togglePauseGeneration() },
                                    onCancel = { viewModel.cancelGeneration() },
                                    onOpenGeneratedScript = { pid ->
                                        viewModel.selectProject(pid)
                                        navigateTo(StudioRoutes.SCRIPT_WRITER)
                                    },
                                    onOpenLibrary = { navigateTo(StudioRoutes.VIDEO_LIBRARY) }
                                )
                            }

                            composable(StudioRoutes.SCRIPT_WRITER) {
                                ScriptWriterScreen(
                                    projects = projects,
                                    selectedProjectId = selectedProjectId,
                                    scenes = activeScenes,
                                    isBusy = isScriptBusy,
                                    onSelectProject = { viewModel.selectProject(it) },
                                    onSaveScript = { proj, updatedScenes -> viewModel.saveScriptEdits(proj, updatedScenes) },
                                    onExpandScript = { proj, curScenes -> viewModel.expandProjectScript(proj, curScenes) },
                                    onRegenerateSection = { proj, secName, curText, onDone ->
                                        viewModel.regenerateSection(proj, secName, curText, onDone)
                                    },
                                    onRegenerateScene = { proj, scene -> viewModel.regenerateScene(proj, scene) },
                                    onAttachSceneImage = { proj, idx, uri, curScenes ->
                                        viewModel.attachImageToScene(proj, idx, uri, curScenes)
                                    },
                                    onGoToCreate = { navigateTo(StudioRoutes.CREATE_VIDEO) }
                                )
                            }

                            composable(StudioRoutes.VOICE_STUDIO) {
                                VoiceStudioScreen(
                                    projects = projects,
                                    activeScenes = activeScenes,
                                    settings = settings,
                                    ttsCapabilities = ttsCapabilities,
                                    previewStatus = voicePreviewStatus,
                                    isPlayingPreview = isPlayingAudio,
                                    onRefreshTtsCapabilities = { viewModel.inspectTtsCapabilities() },
                                    onSynthesizeAndPreview = { text, lang, tone, gender, speed ->
                                        viewModel.synthesizeStandaloneVoice(text, lang, tone, gender, speed)
                                    },
                                    onPlayFile = { path -> viewModel.playAudioFile(path) },
                                    onStopAudio = { viewModel.stopAudioPreview() }
                                )
                            }

                            composable(StudioRoutes.MY_PROJECTS) {
                                MyProjectsScreen(
                                    projects = projects,
                                    onOpenProjectScript = { proj ->
                                        viewModel.selectProject(proj.id)
                                        navigateTo(StudioRoutes.SCRIPT_WRITER)
                                    },
                                    onResumeProject = { proj ->
                                        viewModel.resumeProject(proj)
                                        navigateTo(StudioRoutes.CREATE_VIDEO)
                                    },
                                    onRenameProject = { id, name -> viewModel.renameProject(id, name) },
                                    onDeleteProject = { id -> viewModel.deleteProject(id) },
                                    onGoToCreate = { navigateTo(StudioRoutes.CREATE_VIDEO) }
                                )
                            }

                            composable(StudioRoutes.VIDEO_LIBRARY) {
                                VideoLibraryScreen(
                                    projects = projects,
                                    onRegenerateThumbnail = { proj, title ->
                                        viewModel.regenerateFreeThumbnail(proj, title)
                                    },
                                    onGoToCreate = { navigateTo(StudioRoutes.CREATE_VIDEO) }
                                )
                            }

                            composable(StudioRoutes.SETTINGS) {
                                StudioSettingsScreen(
                                    settings = settings,
                                    customLogoUri = customLogoUri,
                                    logoPosition = logoPosition,
                                    logoSize = logoSize,
                                    backgroundMusicName = backgroundMusicName,
                                    hardwareSupports1080p = hardwareSupports1080p,
                                    brandingPreviewPaths = brandingPreviewPaths,
                                    userEmail = userEmail,
                                    onSaveSettings = { viewModel.updateSettings(it) },
                                    onLogoSelected = { uriStr -> viewModel.saveCustomLogoUri(uriStr) },
                                    onSelectBackgroundMusic = { uri -> viewModel.selectBackgroundMusic(uri) },
                                    onPreviewBranding = { ch, tag -> viewModel.generateBrandingPreview(ch, tag) },
                                    onDismissBrandingPreview = { viewModel.dismissBrandingPreview() },
                                    onSignOut = {
                                        signOutUser(
                                            credentialManager = credentialManager,
                                            scope = scope
                                        )
                                    }
                                )
                            }

                            composable(StudioRoutes.API_USAGE) {
                                ApiUsageScreen(
                                    usageLogs = usageLogs,
                                    freeModeStatus = freeModeStatus
                                )
                            }
                        }
                    }
                }
            }

            if (isExpandedLayout) {
                scaffoldContent()
            } else {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(drawerContainerColor = NebulaSurface) {
                            Text(
                                text = "XAVIOUR AI STUDIO",
                                style = MaterialTheme.typography.titleLarge,
                                color = MetallicGold,
                                modifier = Modifier.padding(20.dp)
                            )
                            ALL_STUDIO_DESTINATIONS.forEach { dest ->
                                NavigationDrawerItem(
                                    label = { Text(dest.label) },
                                    selected = currentRoute == dest.route,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        navigateTo(dest.route)
                                    },
                                    icon = { Icon(dest.icon, contentDescription = null) },
                                    colors = NavigationDrawerItemDefaults.colors(
                                        selectedContainerColor = ElectricBlue.copy(alpha = 0.24f),
                                        selectedIconColor = MetallicGold,
                                        selectedTextColor = MetallicGold,
                                        unselectedIconColor = NebulaTextSecondary,
                                        unselectedTextColor = StarlightWhite
                                    ),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                ) {
                    scaffoldContent()
                }
            }
        }
    }
}
