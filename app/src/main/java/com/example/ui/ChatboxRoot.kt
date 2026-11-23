package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Groups
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.model.AppTab
import com.example.data.model.CoreAppTab
import com.example.ui.components.AppUpdateDetailsDialog
import com.example.ui.components.CustomMenuContentScreen
import com.example.ui.components.ProfileModalDialog
import com.example.ui.components.customMenuIconVector
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.AuthLoginScreen
import com.example.ui.screens.CallsScreen
import com.example.ui.screens.DemoChatsScreen
import com.example.ui.screens.LiveChatsScreen
import com.example.ui.screens.SairaAiScreen
import com.example.ui.theme.ChatboxAccentViolet
import com.example.ui.theme.ChatboxPrimary
import com.example.ui.theme.ChatboxSoftLavender
import com.example.ui.theme.ChatboxTheme
import com.example.ui.theme.ChatboxUpdateGreen
import com.example.ui.theme.LocalChatboxColors
import com.example.ui.viewmodel.ChatboxAppViewModel
import kotlinx.coroutines.delay

private data class NavDestinationItem(
    val id: String,
    val tab: AppTab,
    val label: String,
    val icon: ImageVector
)

@Composable
fun ChatboxRoot(viewModel: ChatboxAppViewModel = viewModel()) {
    val context = LocalContext.current
    val isOnline by viewModel.isOnline.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val authLoading by viewModel.authLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val loginLanguage by viewModel.loginLanguage.collectAsState()
    val rememberMeEnabled by viewModel.rememberMeEnabled.collectAsState()
    val rememberedPhone by viewModel.rememberedPhone.collectAsState()
    val appearance by viewModel.appearance.collectAsState()
    val dynamicConfig by viewModel.dynamicConfig.collectAsState()
    val publicWelcome by viewModel.publicWelcome.collectAsState()
    val appUpdate by viewModel.appUpdate.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val ownProfile by viewModel.ownProfile.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val showProfileDialog by viewModel.showProfileDialog.collectAsState()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsState()
    val demoMessages by viewModel.demoMessages.collectAsState()
    val quickReaction by viewModel.quickReaction.collectAsState()
    val oneMinAutoReplyEnabled by viewModel.oneMinAutoReplyEnabled.collectAsState()
    val notifAutoReplyEnabled by viewModel.notifAutoReplyEnabled.collectAsState()
    val sairaRules by viewModel.sairaRules.collectAsState()
    val fallbackKindId by viewModel.aiFallbackProviderKind.collectAsState()
    val fallbackApiKey by viewModel.aiFallbackApiKey.collectAsState()
    val fallbackBaseUrl by viewModel.aiFallbackBaseUrl.collectAsState()
    val fallbackModel by viewModel.aiFallbackModel.collectAsState()

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
        if (currentUser == null) {
            viewModel.trySilentSignIn(context)
        }
        delay(1500)
        splashDone = true
    }

    ChatboxTheme(useLavenderTheme = appearance.theme == "lavender") {
        val colors = LocalChatboxColors.current
        if (!splashDone) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ChatboxSoftLavender,
                                colors.background,
                                ChatboxSoftLavender.copy(alpha = 0.65f)
                            )
                        )
                    )
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
                            .border(2.5.dp, ChatboxAccentViolet.copy(alpha = 0.45f), CircleShape),
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
                        text = stringResource(R.string.app_name),
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

        val user = currentUser
        if (user == null) {
            AuthLoginScreen(
                languageCode = loginLanguage,
                authLoading = authLoading,
                authError = authError,
                isOnline = isOnline,
                rememberMeEnabled = rememberMeEnabled,
                rememberedPhone = rememberedPhone,
                onSaveRememberedPhone = viewModel::saveRememberedLoginPhone,
                onLanguageSelected = viewModel::setLoginLanguage,
                onGoogleSignIn = { viewModel.signInWithGoogle(context) },
                onSendPasswordReset = viewModel::sendPasswordReset
            )
            return@ChatboxTheme
        }

        // Key user session state cleanly per authenticated UID
        key(user.uid) {
            val navItems = remember(isAdmin, dynamicConfig.customMenus) {
                buildList {
                    add(NavDestinationItem("demo", AppTab.Core(CoreAppTab.DEMO), "Demo", Icons.Rounded.ChatBubbleOutline))
                    add(NavDestinationItem("live", AppTab.Core(CoreAppTab.LIVE), "Live", Icons.Rounded.Groups))
                    add(NavDestinationItem("saira", AppTab.Core(CoreAppTab.SAIRA), "Saira", Icons.Rounded.AutoAwesome))
                    add(NavDestinationItem("calls", AppTab.Core(CoreAppTab.CALLS), "Calls", Icons.Rounded.Phone))
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
                    if (isAdmin) {
                        add(NavDestinationItem("admin", AppTab.Core(CoreAppTab.ADMIN), "Admin", Icons.Rounded.Shield))
                    }
                }
            }

            // If a custom menu was removed, fall back safely to Live chats
            val resolvedTab = remember(selectedTab, navItems) {
                when (selectedTab) {
                    is AppTab.Core -> {
                        if ((selectedTab as AppTab.Core).tab == CoreAppTab.ADMIN && !isAdmin) {
                            AppTab.Core(CoreAppTab.LIVE)
                        } else selectedTab
                    }
                    is AppTab.Custom -> {
                        val customId = (selectedTab as AppTab.Custom).menu.id
                        navItems.firstOrNull { it.id == "custom_$customId" }?.tab
                            ?: AppTab.Core(CoreAppTab.LIVE)
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
                                CoreAppTab.DEMO -> DemoChatsScreen(
                                    publicWelcomeBanner = publicWelcome,
                                    userDisplayName = ownProfile?.displayName ?: user.displayName,
                                    userEmail = user.email,
                                    isOnline = isOnline,
                                    appUpdateActive = appUpdate.active,
                                    messagesByContact = demoMessages,
                                    onSendDemoMessage = viewModel::sendDemoMessage,
                                    onAppUpdateClick = { viewModel.openUpdateDialog(true) },
                                    onSignOut = viewModel::signOut,
                                    onOpenCalls = { viewModel.selectTab(AppTab.Core(CoreAppTab.CALLS)) }
                                )
                                CoreAppTab.LIVE -> LiveChatsScreen(
                                    currentUserId = user.uid,
                                    displayName = user.displayName,
                                    isOnline = isOnline,
                                    appUpdateActive = appUpdate.active,
                                    oneMinAutoReplyEnabled = oneMinAutoReplyEnabled,
                                    quickReaction = quickReaction,
                                    ownProfile = ownProfile,
                                    homeAnnouncement = dynamicConfig.homeAnnouncement,
                                    liveChatRepo = viewModel.liveChatRepo,
                                    onQuickReactionChange = viewModel::setQuickReaction,
                                    onOpenProfile = { viewModel.openProfileDialog(true) },
                                    onOpenCalls = { viewModel.selectTab(AppTab.Core(CoreAppTab.CALLS)) },
                                    onAppUpdateClick = { viewModel.openUpdateDialog(true) }
                                )
                                CoreAppTab.SAIRA -> SairaAiScreen(
                                    currentUserId = user.uid,
                                    isOnline = isOnline,
                                    appUpdateActive = appUpdate.active,
                                    onAppUpdateClick = { viewModel.openUpdateDialog(true) },
                                    oneMinAutoReplyEnabled = oneMinAutoReplyEnabled,
                                    onChangeOneMinAutoReply = viewModel::setOneMinAutoReplyEnabled,
                                    notifAutoReplyEnabled = notifAutoReplyEnabled,
                                    onChangeNotifAutoReply = viewModel::setNotifAutoReplyEnabled,
                                    rules = sairaRules,
                                    onSaveRules = viewModel::saveSairaRules,
                                    fallbackKindId = fallbackKindId,
                                    fallbackApiKey = fallbackApiKey,
                                    fallbackBaseUrl = fallbackBaseUrl,
                                    fallbackModel = fallbackModel,
                                    onSaveFallbackConfig = viewModel::saveAiFallbackConfig,
                                    sessionRepo = viewModel.sairaSessionRepo
                                )
                                CoreAppTab.CALLS -> CallsScreen(
                                    isOnline = isOnline,
                                    appUpdateActive = appUpdate.active,
                                    onAppUpdateClick = { viewModel.openUpdateDialog(true) },
                                    onBackToConversations = { viewModel.selectTab(AppTab.Core(CoreAppTab.LIVE)) }
                                )
                                CoreAppTab.ADMIN -> AdminScreen(
                                    currentUserId = user.uid,
                                    currentUserEmail = user.email,
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
                            navItems.forEach { item ->
                                val selected = isTabSelected(resolvedTab, item.tab)
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(item.tab) },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = ChatboxPrimary,
                                        selectedTextColor = ChatboxPrimary,
                                        indicatorColor = ChatboxSoftLavender
                                    ),
                                    modifier = Modifier.testTag("nav_${item.id}")
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.width(1.dp).fillMaxHeight(), color = colors.border)
                        mainContent(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    val navBar: @Composable () -> Unit = {
                        NavigationBar(containerColor = colors.surface) {
                            navItems.forEach { item ->
                                val selected = isTabSelected(resolvedTab, item.tab)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(item.tab) },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = ChatboxPrimary,
                                        selectedTextColor = ChatboxPrimary,
                                        indicatorColor = ChatboxSoftLavender
                                    ),
                                    modifier = Modifier.testTag("nav_${item.id}")
                                )
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
                    uid = user.uid,
                    email = user.email,
                    fallbackDisplayName = user.displayName,
                    isOnline = isOnline,
                    liveChatRepo = viewModel.liveChatRepo,
                    onDismiss = { viewModel.openProfileDialog(false) },
                    onProfileUpdated = viewModel::updateOwnProfile,
                    onSignOut = viewModel::signOut
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
}

private fun isTabSelected(current: AppTab, candidate: AppTab): Boolean = when {
    current is AppTab.Core && candidate is AppTab.Core -> current.tab == candidate.tab
    current is AppTab.Custom && candidate is AppTab.Custom -> current.menu.id == candidate.menu.id
    else -> false
}
