package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.auth.attemptAutoSignIn
import com.example.data.auth.authStateFlow
import com.example.data.auth.onGoogleSignInClicked
import com.example.data.auth.signOutUser
import com.example.data.model.AdminAuthStatus
import com.example.data.model.SparkAppearance
import com.example.data.repository.ChatMediaStorageRepository
import com.example.data.repository.DemoPreferencesRepository
import com.example.data.repository.LiveChatRepository
import com.example.data.repository.SparkAdminRepository
import com.example.media.VoiceNoteRecorder
import com.example.notifications.ActiveConversationTracker
import com.example.notifications.FcmTokenRepository
import com.example.ui.components.AuthErrorDialog
import com.example.ui.components.MessengerAvatar
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.AuthLoginScreen
import com.example.ui.screens.CallsScreen
import com.example.ui.screens.DemoChatsScreen
import com.example.ui.screens.LiveChatsScreen
import com.example.ui.screens.SairaAiScreen
import com.example.ui.theme.ChatboxTheme
import com.example.ui.viewmodel.DemoChatViewModel
import com.example.ui.viewmodel.DemoModal
import com.example.ui.viewmodel.LiveChatViewModel
import com.example.ui.viewmodel.LiveScreenMode
import com.example.ui.viewmodel.SairaAiViewModel
import com.example.ui.viewmodel.SparkAdminViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object DemoRoute

@Serializable
data object LiveRoute

@Serializable
data object SairaAiRoute

@Serializable
data object CallsRoute

@Serializable
data object AdminRoute

private data class NavTabItem(
    val route: Any,
    val label: String,
    val shortLabel: String,
    val icon: ImageVector,
    val testTag: String
)

private val appNavTabs = listOf(
    NavTabItem(DemoRoute, "Demo", "Demo", Icons.Rounded.ChatBubbleOutline, "nav_demo"),
    NavTabItem(LiveRoute, "Live chats", "Live", Icons.Rounded.Group, "nav_live"),
    NavTabItem(SairaAiRoute, "Saira AI", "Saira", Icons.Rounded.AutoAwesome, "nav_saira_ai"),
    NavTabItem(CallsRoute, "Calls", "Calls", Icons.Rounded.Phone, "nav_calls"),
    NavTabItem(AdminRoute, "Admin", "Admin", Icons.Rounded.Shield, "nav_admin")
)

@Composable
fun ChatboxAppRoot(
    pendingConversationId: String? = null,
    onConsumePendingConversation: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val authFlow = remember { Firebase.auth.authStateFlow() }
    val currentUser by authFlow.collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    var authReady by remember { mutableStateOf(Firebase.auth.currentUser != null) }
    var authLoading by remember { mutableStateOf(false) }
    var authError by rememberSaveable { mutableStateOf<String?>(null) }

    val demoViewModel: DemoChatViewModel = viewModel(
        factory = remember(context) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DemoChatViewModel(DemoPreferencesRepository(context.applicationContext)) as T
                }
            }
        }
    )
    val demoUiState by demoViewModel.uiState.collectAsStateWithLifecycle()

    val sairaAiViewModel: SairaAiViewModel = viewModel()

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = { authReady = true },
            onUnauthenticated = { authReady = true },
            scope = scope
        )
    }

    val startSignIn: () -> Unit = {
        if (!authLoading) {
            authLoading = true
            authError = null
            onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    authLoading = false
                    authReady = true
                },
                onAuthError = { message ->
                    authLoading = false
                    authReady = true
                    authError = message
                },
                onAuthCancelled = {
                    authLoading = false
                    authReady = true
                },
                scope = scope
            )
        }
    }

    val startSignOut: () -> Unit = {
        if (!authLoading) {
            authLoading = true
            signOutUser(
                credentialManager = credentialManager,
                scope = scope,
                onBeforeSignOut = {
                    FcmTokenRepository(context.applicationContext).unregisterCurrentToken()
                },
                onSignOutComplete = {
                    authLoading = false
                }
            )
        }
    }

    authError?.let { message ->
        AuthErrorDialog(message = message, onDismiss = { authError = null })
    }

    val signedInUser = currentUser
    if (signedInUser == null) {
        ChatboxTheme(lavenderAppearance = false) {
            AuthLoginScreen(
                language = demoUiState.loginLanguage,
                authReady = authReady,
                authLoading = authLoading,
                onLanguageSelected = { demoViewModel.setLoginLanguage(it) },
                onGoogleSignIn = startSignIn
            )
        }
    } else {
        key(signedInUser.uid) {
            val liveViewModel: LiveChatViewModel = viewModel(
                key = "live_${signedInUser.uid}",
                factory = remember(signedInUser.uid, context) {
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return LiveChatViewModel(
                                repository = LiveChatRepository(context.applicationContext),
                                currentUserId = signedInUser.uid,
                                displayName = signedInUser.displayName,
                                mediaRepository = ChatMediaStorageRepository(context.applicationContext),
                                voiceRecorder = VoiceNoteRecorder(context.applicationContext)
                            ) as T
                        }
                    }
                }
            )
            val adminViewModel: SparkAdminViewModel = viewModel(
                key = "admin_${signedInUser.uid}",
                factory = remember(signedInUser.uid, context) {
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return SparkAdminViewModel(
                                repository = SparkAdminRepository(context.applicationContext),
                                currentUserId = signedInUser.uid,
                                currentUserEmail = signedInUser.email
                            ) as T
                        }
                    }
                }
            )

            val appearance by adminViewModel.appearance.collectAsStateWithLifecycle()
            val welcomeBanner by adminViewModel.welcomeBanner.collectAsStateWithLifecycle()

            LaunchedEffect(signedInUser.uid) {
                FcmTokenRepository(context.applicationContext).syncCurrentToken()
            }

            ChatboxTheme(lavenderAppearance = appearance.theme == "lavender") {
                AuthenticatedAppShell(
                    user = signedInUser,
                    appearance = appearance,
                    welcomeBanner = welcomeBanner,
                    authLoading = authLoading,
                    pendingConversationId = pendingConversationId,
                    onConsumePendingConversation = onConsumePendingConversation,
                    demoViewModel = demoViewModel,
                    sairaAiViewModel = sairaAiViewModel,
                    liveViewModel = liveViewModel,
                    adminViewModel = adminViewModel,
                    credentialManager = credentialManager,
                    onSignIn = startSignIn,
                    onSignOut = startSignOut
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedAppShell(
    user: FirebaseUser,
    appearance: SparkAppearance,
    welcomeBanner: String,
    authLoading: Boolean,
    pendingConversationId: String?,
    onConsumePendingConversation: () -> Unit,
    demoViewModel: DemoChatViewModel,
    sairaAiViewModel: SairaAiViewModel,
    liveViewModel: LiveChatViewModel,
    adminViewModel: SparkAdminViewModel,
    credentialManager: CredentialManager,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsRepo = remember(context) { DemoPreferencesRepository(context.applicationContext) }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val isAdminTab = backStackEntry?.destination?.hasRoute<AdminRoute>() == true
    val isLiveTab = backStackEntry?.destination?.hasRoute<LiveRoute>() == true

    val demoUiState by demoViewModel.uiState.collectAsStateWithLifecycle()
    val aiUiState by sairaAiViewModel.uiState.collectAsStateWithLifecycle()
    val conversationsState by liveViewModel.conversationsState.collectAsStateWithLifecycle()
    val blockedIds by liveViewModel.blockedIdsState.collectAsStateWithLifecycle()
    val peerProfiles by liveViewModel.peerProfiles.collectAsStateWithLifecycle()
    val conversationMessages by liveViewModel.searchableMessagesByConversation.collectAsStateWithLifecycle()
    val threadState by liveViewModel.threadState.collectAsStateWithLifecycle()
    val voicePlaybackState by liveViewModel.voicePlaybackState.collectAsStateWithLifecycle()
    val profileReady by liveViewModel.profileReady.collectAsStateWithLifecycle()
    val adminUiState by adminViewModel.uiState.collectAsStateWithLifecycle()
    val quickReaction by prefsRepo.quickReaction.collectAsStateWithLifecycle(initialValue = "💜")

    LaunchedEffect(pendingConversationId) {
        if (!pendingConversationId.isNullOrBlank()) {
            navigateToTab(navController, LiveRoute)
            liveViewModel.selectConversation(pendingConversationId)
            onConsumePendingConversation()
        }
    }

    DisposableEffect(isLiveTab, threadState.selectedId, threadState.screen) {
        ActiveConversationTracker.isLiveChatsVisible =
            isLiveTab && threadState.screen == LiveScreenMode.THREAD
        ActiveConversationTracker.activeConversationId =
            if (ActiveConversationTracker.isLiveChatsVisible) threadState.selectedId else null
        onDispose {
            ActiveConversationTracker.isLiveChatsVisible = false
            ActiveConversationTracker.activeConversationId = null
        }
    }

    LaunchedEffect(user.uid, isAdminTab, adminUiState.status) {
        if (isAdminTab && adminUiState.status == AdminAuthStatus.IDLE) {
            adminViewModel.checkAdminAccess()
        }
    }

    AdaptiveAppScaffold(
        navController = navController,
        menuPosition = appearance.menu,
        userDisplayName = user.displayName,
        onAboutClick = {
            navigateToTab(navController, DemoRoute)
            demoViewModel.showModal(DemoModal.ABOUT)
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = LiveRoute,
            modifier = Modifier.fillMaxSize()
        ) {
            composable<DemoRoute> {
                DemoChatsScreen(
                    uiState = demoUiState,
                    publicWelcomeBanner = welcomeBanner,
                    userDisplayName = user.displayName,
                    userEmail = user.email,
                    isSignedIn = true,
                    authBusy = authLoading,
                    onSelectContact = { demoViewModel.selectContact(it) },
                    onSearchChange = { demoViewModel.updateSearch(it) },
                    onDraftChange = { demoViewModel.updateDraft(it) },
                    onAppendEmoji = { demoViewModel.appendEmoji() },
                    onSendMessage = { demoViewModel.sendMessage() },
                    onShowModal = { demoViewModel.showModal(it) },
                    onAccountAction = onSignOut
                )
            }
            composable<LiveRoute> {
                LiveChatsScreen(
                    currentUserId = user.uid,
                    conversationsState = conversationsState,
                    blockedIds = blockedIds,
                    peerProfiles = peerProfiles,
                    conversationMessages = conversationMessages,
                    threadState = threadState,
                    voicePlaybackState = voicePlaybackState,
                    profileReady = profileReady,
                    quickReaction = quickReaction,
                    onQuickReactionChange = { emoji ->
                        scope.launch { prefsRepo.setQuickReaction(emoji) }
                    },
                    onSelectConversation = { liveViewModel.selectConversation(it) },
                    onSetScreen = { liveViewModel.setScreen(it) },
                    onOpenCalls = { navigateToTab(navController, CallsRoute) },
                    onOpenThreadSearch = { liveViewModel.openThreadSearch() },
                    onCloseThreadSearch = { liveViewModel.closeThreadSearch() },
                    onThreadSearchQueryChange = { liveViewModel.updateThreadSearch(it) },
                    onToggleNewConversation = { liveViewModel.toggleNewConversation() },
                    onSearchChange = { liveViewModel.updateSearch(it) },
                    onPeerDraftChange = { liveViewModel.updatePeerDraft(it) },
                    onMarkIdCopied = { liveViewModel.markIdCopied() },
                    onStartConversation = { liveViewModel.startConversation() },
                    onRespondToRequest = { id, status -> liveViewModel.respondToRequest(id, status) },
                    onLoadOlderMessages = { liveViewModel.loadOlderMessages() },
                    onMessageDraftChange = { liveViewModel.updateMessageDraft(it) },
                    onAppendEmoji = { liveViewModel.appendMessageEmoji(it) },
                    onCreateCameraUri = { liveViewModel.createCameraPhotoUri() },
                    onAttachImage = { liveViewModel.attachPendingImage(it) },
                    onClearAttachedImage = { liveViewModel.clearPendingImage() },
                    onStartVoiceRecording = { liveViewModel.startVoiceRecording() },
                    onStopVoiceRecording = { sendNow -> liveViewModel.stopVoiceRecording(sendNow) },
                    onCancelVoiceRecording = { liveViewModel.cancelVoiceRecording() },
                    onClearPendingVoice = { liveViewModel.clearPendingVoice() },
                    onToggleVoicePlayback = { key, src, dur -> liveViewModel.toggleVoicePlayback(key, src, dur) },
                    onAttachmentError = { liveViewModel.reportAttachmentError(it) },
                    onSendMessage = { liveViewModel.sendMessage() },
                    onSendQuickReaction = { liveViewModel.sendQuickReaction(it) },
                    onBlockPeer = { peerUid, conv -> liveViewModel.blockPeer(peerUid, conv) },
                    onUnblockPeer = { peerUid -> liveViewModel.unblockPeer(peerUid) }
                )
            }
            composable<SairaAiRoute> {
                SairaAiScreen(
                    isSignedIn = true,
                    authBusy = authLoading,
                    uiState = aiUiState,
                    onSignIn = onSignIn,
                    onOpenOfflineDemo = {
                        demoViewModel.selectContact("saira")
                        navigateToTab(navController, DemoRoute)
                    },
                    onDraftChange = { sairaAiViewModel.updateDraft(it) },
                    onSubmitQuestion = { sairaAiViewModel.submitQuestion() }
                )
            }
            composable<CallsRoute> {
                CallsScreen(
                    onBackToConversations = { navigateToTab(navController, LiveRoute) }
                )
            }
            composable<AdminRoute> {
                AdminScreen(
                    uiState = adminUiState,
                    onCheckAccess = { adminViewModel.checkAdminAccess() },
                    onVerifyGoogleAgain = {
                        adminViewModel.verifyGoogleAccountAgain(context, credentialManager)
                    },
                    onCommandChange = { adminViewModel.updateCommand(it) },
                    onPreviewCommand = { adminViewModel.previewCommand() },
                    onApplyProposal = { adminViewModel.applyProposal() },
                    onTargetEmailChange = { adminViewModel.updateTargetEmail(it) },
                    onTargetConfirmedChange = { adminViewModel.setTargetConfirmed(it) },
                    onInviteDelegate = { adminViewModel.inviteDelegate() },
                    onRevokeDelegate = { email -> adminViewModel.revokeDelegate(email) },
                    onAcceptDelegation = { adminViewModel.acceptDelegation() }
                )
            }
        }
    }
}

@Composable
private fun AdaptiveAppScaffold(
    navController: NavHostController,
    menuPosition: String,
    userDisplayName: String?,
    onAboutClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        if (useRail) {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .imePadding()
                ) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        header = {
                            Image(
                                painter = painterResource(id = R.drawable.chatbox_logo),
                                contentDescription = stringResource(id = R.string.cd_chatbox_logo),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .size(38.dp)
                                    .clip(CircleShape)
                            )
                        },
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        appNavTabs.forEach { tab ->
                            val selected = currentDestination?.hasRoute(tab.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(navController, tab.route) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = onAboutClick,
                            modifier = Modifier.testTag("rail_about_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.HelpOutline,
                                contentDescription = stringResource(id = R.string.cd_about)
                            )
                        }
                        Box(modifier = Modifier.padding(bottom = 12.dp)) {
                            MessengerAvatar(label = userDisplayName ?: "Y", size = 34.dp)
                        }
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outline)
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        content()
                    }
                }
            }
        } else {
            val bar: @Composable () -> Unit = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    appNavTabs.forEach { tab ->
                        val selected = currentDestination?.hasRoute(tab.route::class) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateToTab(navController, tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.shortLabel, maxLines = 1) },
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
            }

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = { if (menuPosition == "top") bar() },
                bottomBar = { if (menuPosition != "top") bar() }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .imePadding()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.CenterHorizontally)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

private fun navigateToTab(navController: NavHostController, route: Any) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
