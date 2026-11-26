package com.example.ui

import com.example.BuildConfig
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AppTab
import com.example.data.model.CoreAppTab
import com.example.ui.components.AppUpdateDetailsDialog
import com.example.ui.components.CustomMenuContentScreen
import com.example.ui.components.MessengerAvatar
import com.example.ui.components.ProfileModalDialog
import com.example.ui.components.customMenuIconVector
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.AuthLoginScreen
import com.example.ui.screens.CallsScreen
import com.example.ui.screens.ChatboxDmScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SairaAiScreen
import com.example.ui.theme.ChatboxPrimary
import com.example.ui.theme.ChatboxSoftLavender
import com.example.ui.theme.ChatboxTheme
import com.example.ui.theme.ChatboxUpdateGreen
import com.example.ui.theme.DmLavenderAccent
import com.example.ui.theme.LocalChatboxColors
import com.example.ui.viewmodel.ChatboxAppViewModel
import kotlinx.coroutines.delay
import java.io.File

private data class NavDestinationItem(
    val id: String,
    val tab: AppTab,
    val label: String,
    val icon: ImageVector
)

@Composable
fun ChatboxRoot(viewModel: ChatboxAppViewModel = viewModel()) {
    val isOnline by viewModel.isOnline.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val loginLanguage by viewModel.loginLanguage.collectAsState()
    val appearance by viewModel.appearance.collectAsState()
    val dynamicConfig by viewModel.dynamicConfig.collectAsState()
    val publicWelcome by viewModel.publicWelcome.collectAsState()
    val appUpdate by viewModel.appUpdate.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val ownProfile by viewModel.ownProfile.collectAsState()
    val localUserProfile by viewModel.localUserProfile.collectAsState()
    val userAvatarPath by viewModel.userAvatarPath.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val activeThreadId by viewModel.activeThreadId.collectAsState()
    val showProfileDialog by viewModel.showProfileDialog.collectAsState()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsState()
    val chatboxThreads by viewModel.chatboxThreads.collectAsState()
    val chatboxMessages by viewModel.chatboxMessages.collectAsState()
    val chatboxBackupPin by viewModel.chatboxBackupPin.collectAsState()
    val chatboxBlockedThreads by viewModel.chatboxBlockedThreads.collectAsState()
    val chatboxCallLogs by viewModel.chatboxCallLogs.collectAsState()
    val chatboxFavoriteThreads by viewModel.chatboxFavoriteThreads.collectAsState()
    val chatboxScheduledCalls by viewModel.chatboxScheduledCalls.collectAsState()
    val localSairaSessions by viewModel.localSairaSessions.collectAsState()
    val quickReaction by viewModel.quickReaction.collectAsState()
    val sairaOffThreads by viewModel.sairaOffThreads.collectAsState()
    val oneMinAutoReplyEnabled by viewModel.oneMinAutoReplyEnabled.collectAsState()
    val notifAutoReplyEnabled by viewModel.notifAutoReplyEnabled.collectAsState()
    val liveLocationEnabled by viewModel.liveLocationEnabled.collectAsState()
    val dmPeerNicknames by viewModel.dmPeerNicknames.collectAsState()
    val dmMutedPeers by viewModel.dmMutedPeers.collectAsState()
    val sairaRules by viewModel.sairaRules.collectAsState()
    val sairaAutoReplyLogs by viewModel.sairaAutoReplyLogs.collectAsState()
    val fallbackKindId by viewModel.aiFallbackProviderKind.collectAsState()
    val fallbackApiKey by viewModel.aiFallbackApiKey.collectAsState()
    val fallbackBaseUrl by viewModel.aiFallbackBaseUrl.collectAsState()
    val fallbackModel by viewModel.aiFallbackModel.collectAsState()
    val hasCompletedLogin by viewModel.hasCompletedLogin.collectAsState()
    val storedAccountIdentifier by viewModel.storedAccountIdentifier.collectAsState()

    var splashDone by remember { mutableStateOf(false) }
    var splashAppeared by remember { mutableStateOf(false) }
    val splashAlpha by animateFloatAsState(
        targetValue = if (splashAppeared) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "splashAlpha"
    )
    val splashScale by animateFloatAsState(
        targetValue = if (splashAppeared) 1f else 0.88f,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "splashScale"
    )

    LaunchedEffect(Unit) {
        splashAppeared = true
        delay(1200)
        splashDone = true
    }

    ChatboxTheme(useLavenderTheme = appearance.theme == "lavender") {
        val colors = LocalChatboxColors.current
        if (!splashDone) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .testTag("launch_splash_screen"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .alpha(splashAlpha)
                        .scale(splashScale)
                        .padding(horizontal = 32.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .size(112.dp)
                            .border(2.dp, Color(0xFF10B981).copy(alpha = 0.40f), CircleShape),
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 8.dp
                    ) {
                        Image(
                            painter = painterResource(R.drawable.chatbox_logo_lavender),
                            contentDescription = stringResource(R.string.app_name),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = dynamicConfig.appTitle.ifBlank { stringResource(R.string.app_name) },
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.inkSubtle
                    )
                }
            }
            return@ChatboxTheme
        }

        val authFlowActive by viewModel.authFlowActive.collectAsState()
        // Only a real Firebase session (number + its own password, Google, or a session
        // that already existed) opens the app. The local "skip" flag counts only in
        // debug/test builds.
        val mayEnterWithoutAccount = BuildConfig.DEBUG && hasCompletedLogin
        if (authFlowActive || (currentUser == null && !mayEnterWithoutAccount)) {
            AuthLoginScreen(
                selectedLanguageCode = loginLanguage,
                initialIdentifier = storedAccountIdentifier,
                onLanguageSelected = viewModel::setLoginLanguage,
                onBackSkipLogin = if (BuildConfig.DEBUG) viewModel::enterAppWithoutLogin else null,
                onLoginWithCredentials = viewModel::loginWithCredentials,
                onCreateAccount = viewModel::createAccountWithCredentials,
                onResetPassword = viewModel::resetAccountPassword,
                onSendResetCode = viewModel::sendResetCodeToGmail,
                onResetWithCode = viewModel::resetPasswordWithGmailCode
            )
            return@ChatboxTheme
        }

        // After login: the Gmail popup blocks everything until a Gmail is linked.
        val gmailRequired by viewModel.gmailRequired.collectAsState()
        if (currentUser != null && gmailRequired) {
            com.example.ui.screens.GmailRequiredDialog(
                needsPassword = remember(currentUser) { viewModel.currentAccountNeedsPassword() },
                onLinkGmail = viewModel::linkGmail,
                onSaveTypedGmail = viewModel::saveTypedGmail,
                knownGmails = viewModel.knownGmails.collectAsState().value,
                useDeviceAccountChooser = true
            )
        }

        val uid = currentUser?.uid.orEmpty()
        // Show the linked Gmail, never the private "<number>@phone.chatbox.app" login address.
        val knownGmailsForHeader by viewModel.knownGmails.collectAsState()
        val visibleEmail = remember(currentUser, gmailRequired, knownGmailsForHeader) {
            currentUser?.let { u ->
                u.providerData.firstOrNull { it.providerId == "google.com" }?.email
                    ?: u.email?.takeUnless { it.endsWith("@phone.chatbox.app") }
                    ?: knownGmailsForHeader.firstOrNull()
            }
        }
        val effectiveDisplayName = ownProfile?.displayName
            ?: currentUser?.displayName
            ?: localUserProfile.displayName

        val navItems = remember(isAdmin, dynamicConfig.customMenus) {
            buildList {
                add(NavDestinationItem("chatbox", AppTab.Core(CoreAppTab.CHATBOX), "Chatbox", Icons.Rounded.ChatBubbleOutline))
                add(NavDestinationItem("saira", AppTab.Core(CoreAppTab.SAIRA), "Saira", Icons.Rounded.AutoAwesome))
                add(NavDestinationItem("calls", AppTab.Core(CoreAppTab.CALLS), "Calls", Icons.Rounded.Phone))
                add(NavDestinationItem("profile", AppTab.Core(CoreAppTab.PROFILE), "Profile", Icons.Rounded.Person))
                if (isAdmin) {
                    add(NavDestinationItem("admin", AppTab.Core(CoreAppTab.ADMIN), "Admin", Icons.Rounded.Shield))
                }
                dynamicConfig.customMenus.forEach { menu ->
                    add(
                        NavDestinationItem(
                            id = "custom_${menu.id}",
                            tab = AppTab.Custom(menu),
                            label = menu.label,
                            icon = customMenuIconVector(menu.iconEnum)
                        )
                    )
                }
            }
        }

        val resolvedTab = remember(selectedTab, navItems) {
            when (val currentTab = selectedTab) {
                is AppTab.Core -> {
                    if (currentTab.tab == CoreAppTab.ADMIN && !isAdmin) {
                        AppTab.Core(CoreAppTab.CHATBOX)
                    } else currentTab
                }
                is AppTab.Custom -> {
                    val customId = currentTab.menu.id
                    navItems.firstOrNull { it.id == "custom_$customId" }?.tab
                        ?: AppTab.Core(CoreAppTab.CHATBOX)
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            val useSideRail = maxWidth >= 720.dp

            val mainContent: @Composable (Modifier) -> Unit = { contentMod ->
                Box(modifier = contentMod) {
                    when (resolvedTab) {
                        is AppTab.Core -> when (resolvedTab.tab) {
                            CoreAppTab.CHATBOX -> ChatboxDmScreen(
                                threads = chatboxThreads,
                                activeThreadId = activeThreadId,
                                messagesByThread = chatboxMessages,
                                peerNicknames = dmPeerNicknames,
                                mutedThreadIds = dmMutedPeers,
                                blockedThreadIds = chatboxBlockedThreads,
                                globalQuickReaction = quickReaction,
                                backupPinHint = chatboxBackupPin,
                                publicWelcomeBanner = publicWelcome,
                                isOnline = isOnline,
                                appUpdateActive = appUpdate.active,
                                ownShortId = ownProfile?.shortId?.ifBlank { null } ?: localUserProfile.shortId,
                                onSelectThread = viewModel::selectThread,
                                onSendMessage = { threadId, body, timeStr ->
                                    viewModel.sendChatboxMessage(threadId, body, timeStr)
                                },
                                onSendImage = viewModel::sendChatboxImage,
                                onSendVoice = viewModel::sendChatboxVoice,
                                onCreateDirectThread = viewModel::openOrCreateDirectThread,
                                onCreateGroupWithContact = viewModel::createGroupWithContact,
                                onMarkThreadUnread = viewModel::markThreadUnread,
                                onToggleMute = viewModel::setDmPeerMuted,
                                onUpdateNickname = viewModel::setDmPeerNickname,
                                onThreadQuickReactionChange = viewModel::setThreadQuickReaction,
                                onToggleE2eeVerified = viewModel::setThreadE2eeVerified,
                                onDisappearingChange = viewModel::setThreadDisappearingTimer,
                                onToggleReadReceipts = viewModel::setThreadReadReceipts,
                                onUnlockHistoryWithPin = viewModel::unlockThreadHistoryWithPin,
                                onUpdateBackupPin = viewModel::updateBackupPin,
                                onToggleBlock = viewModel::toggleThreadBlocked,
                                onSubmitReport = viewModel::submitThreadReport,
                                onLogCall = viewModel::logChatboxCall,
                                onAppUpdateClick = { viewModel.openUpdateDialog(true) },
                                onSearchPeer = viewModel::searchPeer,
                                onOpenPeer = viewModel::openChatWithPeer,
                                onRequestLogin = viewModel::requestLogin,
                                sairaOffThreadIds = if (oneMinAutoReplyEnabled) sairaOffThreads else chatboxThreads.map { it.id }.toSet(),
                                onSetThreadSaira = viewModel::setThreadSairaEnabled
                            )
                            CoreAppTab.SAIRA -> SairaAiScreen(
                                currentUserId = uid,
                                isOnline = isOnline,
                                appUpdateActive = appUpdate.active,
                                onAppUpdateClick = { viewModel.openUpdateDialog(true) },
                                oneMinAutoReplyEnabled = oneMinAutoReplyEnabled,
                                onChangeOneMinAutoReply = viewModel::setOneMinAutoReplyEnabled,
                                notifAutoReplyEnabled = notifAutoReplyEnabled,
                                onChangeNotifAutoReply = viewModel::setNotifAutoReplyEnabled,
                                liveLocationEnabled = liveLocationEnabled,
                                onChangeLiveLocation = viewModel::setLiveLocationEnabled,
                                rules = sairaRules,
                                onSaveRules = viewModel::saveSairaRules,
                                autoReplyLogs = sairaAutoReplyLogs,
                                onClearAutoReplyLogs = viewModel::clearSairaAutoReplyLogs,
                                fallbackKindId = fallbackKindId,
                                fallbackApiKey = fallbackApiKey,
                                fallbackBaseUrl = fallbackBaseUrl,
                                fallbackModel = fallbackModel,
                                onSaveFallbackConfig = viewModel::saveAiFallbackConfig,
                                sessionRepo = viewModel.sairaSessionRepo,
                                localSessions = localSairaSessions,
                                onSaveLocalExchange = viewModel::saveLocalSairaExchange,
                                onDeleteLocalSession = viewModel::deleteLocalSairaSession,
                                dmThreads = chatboxThreads,
                                onTestAutoReply = { sender, platform, incoming ->
                                    viewModel.testAutoReply(sender, platform, incoming, recordLog = true)
                                },
                                onSimulateIncomingDm = { tid, body ->
                                    viewModel.simulateIncomingChatboxMessage(tid, body, triggerAutoReplyAfterWait = true)
                                }
                            )
                            CoreAppTab.CALLS -> CallsScreen(
                                isOnline = isOnline,
                                appUpdateActive = appUpdate.active,
                                callLogs = chatboxCallLogs,
                                threads = chatboxThreads,
                                favoriteThreadIds = chatboxFavoriteThreads,
                                scheduledCalls = chatboxScheduledCalls,
                                onOpenThread = { threadId ->
                                    viewModel.selectThread(threadId)
                                    viewModel.selectTab(AppTab.Core(CoreAppTab.CHATBOX))
                                },
                                onAddOrFindContact = { query ->
                                    viewModel.openOrCreateDirectThread(query, "")
                                },
                                onLogCall = viewModel::logChatboxCall,
                                onToggleFavorite = viewModel::toggleFavoriteThread,
                                onScheduleCall = viewModel::scheduleCall,
                                onRemoveScheduledCall = viewModel::removeScheduledCall,
                                onClearCallLogs = viewModel::clearChatboxCallLogs,
                                onAppUpdateClick = { viewModel.openUpdateDialog(true) },
                                onBackToConversations = { viewModel.selectTab(AppTab.Core(CoreAppTab.CHATBOX)) }
                            )
                            CoreAppTab.PROFILE -> ProfileScreen(
                                currentUserId = uid,
                                userEmail = visibleEmail,
                                fallbackDisplayName = effectiveDisplayName,
                                ownProfile = ownProfile,
                                localProfile = localUserProfile,
                                blockedThreadIds = chatboxBlockedThreads,
                                chatboxThreads = chatboxThreads,
                                avatarPath = userAvatarPath,
                                isOnline = isOnline,
                                isAdmin = isAdmin,
                                loginLanguage = loginLanguage,
                                quickReaction = quickReaction,
                                oneMinAutoReplyEnabled = oneMinAutoReplyEnabled,
                                liveLocationEnabled = liveLocationEnabled,
                                appearance = appearance,
                                appUpdate = appUpdate,
                                liveChatRepo = viewModel.liveChatRepo,
                                onPickAvatarUri = viewModel::saveOwnAvatarFromUri,
                                onRemoveAvatar = viewModel::removeOwnAvatar,
                                onProfileUpdated = viewModel::updateOwnProfile,
                                onSaveLocalProfile = viewModel::saveLocalUserProfile,
                                onRetryEnsureProfile = viewModel::retryEnsureOwnProfile,
                                onUnblockThread = viewModel::unblockThread,
                                onQuickReactionChange = viewModel::setQuickReaction,
                                onChangeOneMinAutoReply = viewModel::setOneMinAutoReplyEnabled,
                                onChangeLiveLocation = viewModel::setLiveLocationEnabled,
                                onLanguageSelected = viewModel::setLoginLanguage,
                                onAppUpdateClick = { viewModel.openUpdateDialog(true) },
                                onNavigateTab = { target -> viewModel.selectTab(AppTab.Core(target)) }
                            )
                            CoreAppTab.ADMIN -> AdminScreen(
                                currentUserId = uid,
                                currentUserEmail = currentUser?.email,
                                isOnline = isOnline,
                                appearance = appearance,
                                welcomeBanner = publicWelcome,
                                appUpdate = appUpdate,
                                dynamicConfig = dynamicConfig,
                                authRepo = viewModel.authRepo,
                                sparkAdminRepo = viewModel.sparkAdminRepo,
                                onAppUpdateClick = { viewModel.openUpdateDialog(true) }
                            )
                        }
                        is AppTab.Custom -> CustomMenuContentScreen(
                            menu = resolvedTab.menu,
                            appUpdateActive = appUpdate.active,
                            onAppUpdateClick = { viewModel.openUpdateDialog(true) }
                        )
                    }
                }
            }

            if (useSideRail) {
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = colors.surface,
                        header = {
                            if (appUpdate.active) {
                                Surface(
                                    onClick = { viewModel.openUpdateDialog(true) },
                                    shape = CircleShape,
                                    color = ChatboxUpdateGreen,
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Text(
                                        text = "UPD",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            navItems.forEach { item ->
                                val selected = isTabSelected(resolvedTab, item.tab)
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(item.tab) },
                                    icon = {
                                        NavTabIcon(
                                            item = item,
                                            selected = selected,
                                            displayName = effectiveDisplayName,
                                            avatarPath = userAvatarPath,
                                            tint = if (selected) ChatboxPrimary else DmLavenderAccent.copy(alpha = 0.72f)
                                        )
                                    },
                                    label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = ChatboxPrimary,
                                        selectedTextColor = ChatboxPrimary,
                                        unselectedIconColor = DmLavenderAccent.copy(alpha = 0.72f),
                                        unselectedTextColor = colors.inkMuted,
                                        indicatorColor = ChatboxSoftLavender
                                    ),
                                    modifier = Modifier.testTag("nav_${item.id}")
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.width(1.dp).fillMaxHeight(), color = colors.border)
                    mainContent(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                val navBar: @Composable () -> Unit = {
                    if (navItems.size <= 6) {
                        NavigationBar(containerColor = Color.White) {
                            navItems.forEach { item ->
                                val selected = isTabSelected(resolvedTab, item.tab)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(item.tab) },
                                    icon = {
                                        NavTabIcon(
                                            item = item,
                                            selected = selected,
                                            displayName = effectiveDisplayName,
                                            avatarPath = userAvatarPath,
                                            tint = if (selected) ChatboxPrimary else DmLavenderAccent.copy(alpha = 0.72f)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = item.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = ChatboxPrimary,
                                        selectedTextColor = ChatboxPrimary,
                                        unselectedIconColor = DmLavenderAccent.copy(alpha = 0.72f),
                                        unselectedTextColor = colors.inkMuted,
                                        indicatorColor = ChatboxSoftLavender
                                    ),
                                    modifier = Modifier.testTag("nav_${item.id}")
                                )
                            }
                        }
                    } else {
                        Surface(color = Color.White, shadowElevation = 2.dp) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                navItems.forEach { item ->
                                    val selected = isTabSelected(resolvedTab, item.tab)
                                    Surface(
                                        onClick = { viewModel.selectTab(item.tab) },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (selected) ChatboxSoftLavender else Color.Transparent,
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 64.dp, minHeight = 52.dp)
                                            .testTag("nav_${item.id}")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            NavTabIcon(
                                                item = item,
                                                selected = selected,
                                                displayName = effectiveDisplayName,
                                                avatarPath = userAvatarPath,
                                                tint = if (selected) ChatboxPrimary else DmLavenderAccent.copy(alpha = 0.72f)
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = item.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (selected) ChatboxPrimary else colors.inkMuted,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (appearance.menu == "top") {
                    Scaffold(
                        containerColor = colors.background,
                        topBar = navBar
                    ) { innerPadding ->
                        mainContent(Modifier.fillMaxSize().padding(innerPadding))
                    }
                } else {
                    Scaffold(
                        containerColor = colors.background,
                        bottomBar = navBar
                    ) { innerPadding ->
                        mainContent(Modifier.fillMaxSize().padding(innerPadding))
                    }
                }
            }
        }

        if (showProfileDialog) {
            ProfileModalDialog(
                uid = uid,
                email = visibleEmail,
                fallbackDisplayName = effectiveDisplayName,
                localProfile = localUserProfile,
                isOnline = isOnline,
                liveChatRepo = viewModel.liveChatRepo,
                onDismiss = { viewModel.openProfileDialog(false) },
                onProfileUpdated = viewModel::updateOwnProfile,
                onSaveLocalProfile = viewModel::saveLocalUserProfile
            )
        }

        if (showUpdateDialog && appUpdate.active) {
            AppUpdateDetailsDialog(
                appUpdate = appUpdate,
                onDismiss = { viewModel.openUpdateDialog(false) }
            )
        }
    }
}

private fun isTabSelected(current: AppTab, candidate: AppTab): Boolean = when {
    current is AppTab.Core && candidate is AppTab.Core -> current.tab == candidate.tab
    current is AppTab.Custom && candidate is AppTab.Custom -> current.menu.id == candidate.menu.id
    else -> false
}

@Composable
private fun NavTabIcon(
    item: NavDestinationItem,
    selected: Boolean,
    displayName: String,
    avatarPath: String?,
    tint: Color
) {
    if (item.tab is AppTab.Core && item.tab.tab == CoreAppTab.PROFILE) {
        val colors = LocalChatboxColors.current
        val hasLocalAvatar = remember(avatarPath) {
            !avatarPath.isNullOrBlank() && File(avatarPath).exists()
        }
        val ringColor = if (selected) ChatboxPrimary else DmLavenderAccent.copy(alpha = 0.55f)
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .border(if (selected) 2.dp else 1.dp, ringColor, CircleShape)
                .padding(1.5.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (hasLocalAvatar) {
                AsyncImage(
                    model = File(avatarPath!!),
                    contentDescription = item.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                MessengerAvatar(
                    label = displayName,
                    size = 23.dp
                )
            }
        }
    } else {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}
