package com.example.ui

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Bookmark
import com.example.data.Download
import com.example.data.TabState
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val showTabsManager by viewModel.showTabsManager.collectAsStateWithLifecycle()
    val showBookmarksManager by viewModel.showBookmarksManager.collectAsStateWithLifecycle()
    val showDownloadsManager by viewModel.showDownloadsManager.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val isBookmarked = activeTab?.url?.let {
        viewModel.checkIfBookmarked(it).collectAsStateWithLifecycle().value
    } ?: false

    // Maintain an in-memory Cache of WebViews associated with each tab
    val webViews = remember { mutableMapOf<String, WebView>() }

    // Safely remove deleted WebViews from cache
    val currentTabIds = remember(tabs) { tabs.map { it.id }.toSet() }
    LaunchedEffect(currentTabIds) {
        val iterator = webViews.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in currentTabIds) {
                entry.value.destroy()
                iterator.remove()
            }
        }
    }

    // Capture hardware back button to navigate backwards in active tab if possible
    val activeWebView = activeTab?.let { webViews[it.id] }
    val canGoBack = activeTab?.canGoBack == true

    BackHandler(enabled = canGoBack) {
        activeWebView?.goBack()
    }

    val isPrivateMode = activeTab?.isPrivate == true

    // Set colors according to active tab mode (Incognito uses Dark Purple/Charcoal theme)
    val containerColor = if (isPrivateMode) Color(0xFF1E1B24) else MaterialTheme.colorScheme.background
    val primaryThemeColor = if (isPrivateMode) Color(0xFFC58BF2) else MaterialTheme.colorScheme.primary
    val surfaceColor = if (isPrivateMode) Color(0xFF2D2936) else MaterialTheme.colorScheme.surface
    val onSurfaceColor = if (isPrivateMode) Color(0xFFE2E0E6) else MaterialTheme.colorScheme.onSurface
    val onPrimaryColor = if (isPrivateMode) Color(0xFF381E52) else MaterialTheme.colorScheme.onPrimary

    val themeVariables = remember(isPrivateMode) {
        ThemeVars(
            isPrivate = isPrivateMode,
            container = containerColor,
            primaryColor = primaryThemeColor,
            surface = surfaceColor,
            onSurface = onSurfaceColor,
            onPrimary = onPrimaryColor
        )
    }

    Scaffold(
        modifier = modifier
            .background(themeVariables.container)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopBrowserBar(
                activeTab = activeTab,
                searchQuery = searchQuery,
                isBookmarked = isBookmarked,
                themeVars = themeVariables,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onNavigate = { query ->
                    val finalUrl = formatUrl(query)
                    viewModel.updateSearchQuery(finalUrl)
                    viewModel.updateTabState(activeTabId) { it.copy(url = finalUrl) }
                    activeWebView?.loadUrl(finalUrl)
                },
                onBookmarkToggle = {
                    activeTab?.let { tab ->
                        viewModel.toggleBookmark(tab.title, tab.url, isBookmarked)
                    }
                },
                onToggleTabsManager = { viewModel.setShowTabsManager(true) },
                onToggleBookmarks = { viewModel.setShowBookmarksManager(true) },
                onToggleDownloads = { viewModel.setShowDownloadsManager(true) }
            )
        },
        bottomBar = {
            BottomBrowserBar(
                tabsCount = tabs.size,
                activeTab = activeTab,
                themeVars = themeVariables,
                onBack = { activeWebView?.goBack() },
                onForward = { activeWebView?.goForward() },
                onRefresh = { activeWebView?.reload() },
                onHome = {
                    viewModel.updateSearchQuery("")
                    viewModel.updateTabState(activeTabId) { it.copy(url = "about:blank") }
                },
                onNewTab = { viewModel.addNewTab("about:blank", isPrivate = isPrivateMode) },
                onToggleTabsManager = { viewModel.setShowTabsManager(true) }
            )
        },
        containerColor = themeVariables.container
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(themeVariables.container)
        ) {
            if (activeTab != null) {
                TabWebViewContainer(
                    activeTab = activeTab!!,
                    webViews = webViews,
                    viewModel = viewModel,
                    themeVars = themeVariables,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Tabs Manager Modal Panel
            if (showTabsManager) {
                TabsManagerPanel(
                    tabs = tabs,
                    activeTabId = activeTabId,
                    themeVars = themeVariables,
                    onSwitchTab = { viewModel.setActiveTab(it) },
                    onCloseTab = { viewModel.closeTab(it) },
                    onNewTab = { isPrivate -> viewModel.addNewTab("about:blank", isPrivate = isPrivate) },
                    onDismiss = { viewModel.setShowTabsManager(false) }
                )
            }

            // Bookmarks Manager Sheet/Panel
            if (showBookmarksManager) {
                BookmarksManagerPanel(
                    viewModel = viewModel,
                    themeVars = themeVariables,
                    onBookmarkSelected = { url ->
                        viewModel.updateSearchQuery(url)
                        viewModel.updateTabState(activeTabId) { it.copy(url = url) }
                        activeWebView?.loadUrl(url)
                        viewModel.setShowBookmarksManager(false)
                    },
                    onDismiss = { viewModel.setShowBookmarksManager(false) }
                )
            }

            // Downloads Manager Sheet/Panel
            if (showDownloadsManager) {
                DownloadsManagerPanel(
                    viewModel = viewModel,
                    themeVars = themeVariables,
                    onDismiss = { viewModel.setShowDownloadsManager(false) }
                )
            }
        }
    }
}

// Data class to store calculated theme styling parameters statically based on incognito state
data class ThemeVars(
    val isPrivate: Boolean,
    val container: Color,
    val primaryColor: Color,
    val surface: Color,
    val onSurface: Color,
    val onPrimary: Color
)

@Composable
fun TopBrowserBar(
    activeTab: TabState?,
    searchQuery: String,
    isBookmarked: Boolean,
    themeVars: ThemeVars,
    onQueryChange: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onBookmarkToggle: () -> Unit,
    onToggleTabsManager: () -> Unit,
    onToggleBookmarks: () -> Unit,
    onToggleDownloads: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Surface(
        color = themeVars.surface,
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Optional progress bar
            if (activeTab?.isLoading == true) {
                LinearProgressIndicator(
                    progress = { (activeTab.progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = themeVars.primaryColor,
                    trackColor = themeVars.surface
                )
            } else {
                Spacer(modifier = Modifier.height(3.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Private Mode Indicator Key
                if (themeVars.isPrivate) {
                    Box(
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4A154B).copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Private Mode Active",
                            tint = themeVars.primaryColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Address / Search Input Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(max = 52.dp)
                        .testTag("address_url_input"),
                    placeholder = {
                        Text(
                            text = if (themeVars.isPrivate) "Search privately with AnshSearch" else "Search or enter web URL",
                            fontSize = 14.sp,
                            color = themeVars.onSurface.copy(alpha = 0.6f)
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = themeVars.primaryColor,
                        unfocusedBorderColor = themeVars.onSurface.copy(alpha = 0.2f),
                        focusedContainerColor = themeVars.container,
                        unfocusedContainerColor = themeVars.container,
                        focusedTextColor = themeVars.onSurface,
                        unfocusedTextColor = themeVars.onSurface
                    ),
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            if (searchQuery.isNotBlank()) {
                                onNavigate(searchQuery)
                                focusManager.clearFocus()
                            }
                        }
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = if (themeVars.isPrivate) Icons.Default.Lock else Icons.Default.Search,
                            contentDescription = "Search icon",
                            tint = themeVars.primaryColor
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear web input",
                                    tint = themeVars.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Favorite/Bookmark Star Key
                IconButton(
                    onClick = onBookmarkToggle,
                    enabled = activeTab != null && activeTab.url != "about:blank",
                    modifier = Modifier.testTag("bookmark_toggle_btn")
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Bookmark this web page",
                        tint = if (isBookmarked) Color(0xFFFFC107) else themeVars.onSurface.copy(alpha = 0.7f)
                    )
                }

                // SubMenu trigger
                var showDrop by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { showDrop = !showDrop },
                        modifier = Modifier.testTag("menu_dropdown_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Show App Menu",
                            tint = themeVars.onSurface
                        )
                    }

                    DropdownMenu(
                        expanded = showDrop,
                        onDismissRequest = { showDrop = false },
                        modifier = Modifier.background(themeVars.surface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Bookmarks", color = themeVars.onSurface) },
                            leadingIcon = { Icon(Icons.Default.Star, contentDescription = "Bookmarks", tint = themeVars.primaryColor) },
                            onClick = {
                                showDrop = false
                                onToggleBookmarks()
                            },
                            modifier = Modifier.testTag("menu_bookmarks_opt")
                        )
                        DropdownMenuItem(
                            text = { Text("Downloads", color = themeVars.onSurface) },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = "Downloads", tint = themeVars.primaryColor) },
                            onClick = {
                                showDrop = false
                                onToggleDownloads()
                            },
                            modifier = Modifier.testTag("menu_downloads_opt")
                        )
                        DropdownMenuItem(
                            text = { Text("Tabs Switcher", color = themeVars.onSurface) },
                            leadingIcon = { Icon(Icons.Default.List, contentDescription = "Tabs", tint = themeVars.primaryColor) },
                            onClick = {
                                showDrop = false
                                onToggleTabsManager()
                            },
                            modifier = Modifier.testTag("menu_tabs_opt")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BottomBrowserBar(
    tabsCount: Int,
    activeTab: TabState?,
    themeVars: ThemeVars,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onHome: () -> Unit,
    onNewTab: () -> Unit,
    onToggleTabsManager: () -> Unit
) {
    Surface(
        color = themeVars.surface,
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Backward
            IconButton(
                onClick = onBack,
                enabled = activeTab?.canGoBack == true,
                modifier = Modifier.testTag("nav_back_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Go backward to pre page",
                    tint = if (activeTab?.canGoBack == true) themeVars.onSurface else themeVars.onSurface.copy(alpha = 0.3f)
                )
            }

            // Forward
            IconButton(
                onClick = onForward,
                enabled = activeTab?.canGoForward == true,
                modifier = Modifier.testTag("nav_forward_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Go forward to next page",
                    tint = if (activeTab?.canGoForward == true) themeVars.onSurface else themeVars.onSurface.copy(alpha = 0.3f)
                )
            }

            // Reload / Home Toggle
            IconButton(
                onClick = { if (activeTab?.url == "about:blank") onHome() else onRefresh() },
                modifier = Modifier.testTag("nav_refresh_home_btn")
            ) {
                Icon(
                    imageVector = if (activeTab?.url == "about:blank") Icons.Default.Home else Icons.Default.Refresh,
                    contentDescription = "Home / Refresh action",
                    tint = themeVars.onSurface
                )
            }

            // Quick plus icon for opening tabs
            IconButton(
                onClick = onNewTab,
                modifier = Modifier.testTag("nav_new_tab_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add new browser tab",
                    tint = themeVars.primaryColor
                )
            }

            // Tab counter indicator
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleTabsManager() }
                    .padding(8.dp)
                    .size(28.dp)
                    .background(themeVars.primaryColor.copy(alpha = 0.15f))
                    .testTag("tab_switcher_badge_btn"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tabsCount.toString(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = themeVars.primaryColor
                )
            }
        }
    }
}

@Composable
fun TabWebViewContainer(
    activeTab: TabState,
    webViews: MutableMap<String, WebView>,
    viewModel: BrowserViewModel,
    themeVars: ThemeVars,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        val context = LocalContext.current
        val activeWeb = remember(activeTab.id) {
            webViews.getOrPut(activeTab.id) {
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = !activeTab.isPrivate
                        cacheMode = if (activeTab.isPrivate) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        supportZoom()
                        builtInZoomControls = true
                        displayZoomControls = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }

                    if (activeTab.isPrivate) {
                        CookieManager.getInstance().setAcceptCookie(false)
                    } else {
                        CookieManager.getInstance().setAcceptCookie(true)
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            viewModel.updateTabState(activeTab.id) {
                                it.copy(
                                    url = url ?: "about:blank",
                                    isLoading = true,
                                    canGoBack = canGoBack(),
                                    canGoForward = canGoForward()
                                )
                            }
                            if (url != null && url != "about:blank") {
                                viewModel.updateSearchQuery(url)
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            viewModel.updateTabState(activeTab.id) {
                                it.copy(
                                    title = view?.title ?: "Web Page",
                                    url = url ?: "about:blank",
                                    isLoading = false,
                                    canGoBack = canGoBack(),
                                    canGoForward = canGoForward()
                                )
                            }
                            if (url != null && url != "about:blank") {
                                viewModel.updateSearchQuery(url)
                            }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                                return false
                            }
                            // Allow system intents for non-http links (e.g. market, mailto, etc.)
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                                return true
                            } catch (e: Exception) {
                                return false
                            }
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            viewModel.updateTabState(activeTab.id) {
                                it.copy(progress = newProgress)
                            }
                        }

                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            super.onReceivedTitle(view, title)
                            viewModel.updateTabState(activeTab.id) {
                                it.copy(title = title ?: "Web Page")
                            }
                        }
                    }

                    setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                        viewModel.startDownload(url, userAgent, contentDisposition, mimeType, contentLength)
                    }

                    if (activeTab.url != "about:blank") {
                        loadUrl(activeTab.url)
                    }
                }
            }
        }

        // Display Welcome page or load actual WebView
        if (activeTab.url == "about:blank") {
            WelcomeScreen(
                isPrivate = activeTab.isPrivate,
                themeVars = themeVars,
                onSearch = { query ->
                    val finalUrl = formatUrl(query)
                    viewModel.updateSearchQuery(finalUrl)
                    viewModel.updateTabState(activeTab.id) {
                        it.copy(url = finalUrl)
                    }
                    activeWeb.loadUrl(finalUrl)
                }
            )
        } else {
            AndroidView(
                factory = { activeWeb },
                update = { webView ->
                    if (webView.url != activeTab.url && activeTab.url != "about:blank") {
                        webView.loadUrl(activeTab.url)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun WelcomeScreen(
    isPrivate: Boolean,
    themeVars: ThemeVars,
    onSearch: (String) -> Unit
) {
    var homeSearchQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val topOverlayBrush = remember(isPrivate) {
        if (isPrivate) {
            Brush.verticalGradient(
                colors = listOf(Color(0xFF2C133D), Color(0xFF1E1B24))
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(Color(0xFFEBF3FC), Color(0xFFFFFFFF))
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(topOverlayBrush)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(48.dp))

            // Browser Branding Graphic
            Box(
                modifier = Modifier
                    .size(82.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = if (isPrivate) {
                                listOf(Color(0xFF8A2BE2), Color(0xFFC58BF2))
                            } else {
                                listOf(Color(0xFF3F8CFF), Color(0xFF00D1FF))
                            }
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPrivate) Icons.Default.Lock else Icons.Default.Search,
                    contentDescription = "AnshSearch Platform",
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Branding Display Text
            Text(
                text = "AnshSearch",
                fontSize = 34.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.ExtraBold,
                color = themeVars.onSurface,
                letterSpacing = (-0.5).sp
            )

            Text(
                text = if (isPrivate) "Private Browsing Session active" else "Explore the web instantly",
                fontSize = 15.sp,
                color = themeVars.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Search Bar for Home Feed
            OutlinedTextField(
                value = homeSearchQuery,
                onValueChange = { homeSearchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .testTag("home_search_input"),
                placeholder = {
                    Text(
                        text = if (isPrivate) "Search privately..." else "Search info or enter address",
                        color = themeVars.onSurface.copy(alpha = 0.5f)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = themeVars.primaryColor,
                    unfocusedBorderColor = themeVars.onSurface.copy(alpha = 0.15f),
                    focusedContainerColor = themeVars.surface,
                    unfocusedContainerColor = themeVars.surface,
                    focusedTextColor = themeVars.onSurface,
                    unfocusedTextColor = themeVars.onSurface
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (homeSearchQuery.isNotBlank()) {
                            onSearch(homeSearchQuery)
                            focusManager.clearFocus()
                        }
                    }
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search icon",
                        tint = themeVars.primaryColor
                    )
                },
                trailingIcon = {
                    if (homeSearchQuery.isNotBlank()) {
                        IconButton(onClick = { homeSearchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = themeVars.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Speed Dial Grid
            Text(
                text = "Quick Navigations",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = themeVars.onSurface.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                textAlign = TextAlign.Start
            )

            QuickLinksGrid(onLinkClick = { url ->
                onSearch(url)
            }, isPrivate = isPrivate)

            Spacer(modifier = Modifier.height(24.dp))

            // Incognito Description Info Card
            if (isPrivate) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2C133D).copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF8A2BE2).copy(alpha = 0.3f), Color.Transparent)
                        )
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Private Mode Indicator",
                                tint = themeVars.primaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Your privacy is protected",
                                fontWeight = FontWeight.Bold,
                                color = themeVars.onSurface,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "AnshSearch doesn't store your web history, search terms, or forms in this tab session. Site cache data are cleared when you close the tab.",
                            fontSize = 12.sp,
                            color = themeVars.onSurface.copy(alpha = 0.7f),
                            lineHeight = 16.sp
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = themeVars.surface
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Secure Browsing Info",
                                tint = themeVars.primaryColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Private mode is built-in",
                                fontWeight = FontWeight.Bold,
                                color = themeVars.onSurface,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Need confidential browse sessions? Tap the top-right menu and choose Tab Switcher to launch secure Private Tabs at any time.",
                            fontSize = 12.sp,
                            color = themeVars.onSurface.copy(alpha = 0.7f),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun QuickLinksGrid(onLinkClick: (String) -> Unit, isPrivate: Boolean) {
    val items = listOf(
        QuickLink("Google", "https://www.google.com", "G"),
        QuickLink("Wikipedia", "https://www.wikipedia.org", "W"),
        QuickLink("YouTube", "https://www.youtube.com", "Y"),
        QuickLink("GitHub", "https://github.com", "H"),
        QuickLink("Reddit", "https://www.reddit.com", "R"),
        QuickLink("Yahoo", "https://yahoo.com", "Y")
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        items.forEach { item ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onLinkClick(item.url) }
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPrivate) Color(0xFF2D2936) else Color(0xFFF0F4F8)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.shortHand,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPrivate) Color(0xFFC58BF2) else Color(0xFF3F8CFF)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.name,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isPrivate) Color(0xFFE2E0E6) else Color(0xFF333333)
                )
            }
        }
    }
}

data class QuickLink(val name: String, val url: String, val shortHand: String)

fun formatUrl(input: String): String {
    val trimmed = input.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("about:blank")) {
        return trimmed
    }
    // Simple regex pattern for checking domain formats
    val domainPattern = "^(?!-)[A-Za-z0-9-]+([\\-\\.]{1}[a-z0-9]+)*\\.[A-Za-z]{2,6}(:[0-9]{1,5})?(/.*)?$".toRegex()
    return if (trimmed.matches(domainPattern)) {
        "https://$trimmed"
    } else {
        "https://www.google.com/search?q=" + java.net.URLEncoder.encode(trimmed, "UTF-8")
    }
}

// -------------------------------------------------------------
// TABS MANAGER DIALOG/PANEL
// -------------------------------------------------------------
@Composable
fun TabsManagerPanel(
    tabs: List<TabState>,
    activeTabId: String,
    themeVars: ThemeVars,
    onSwitchTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .testTag("tabs_manager_overlay"),
        colors = CardDefaults.cardColors(containerColor = themeVars.container),
        shape = RoundedCornerShape(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Tabs Manager",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = themeVars.onSurface
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("tabs_manager_close_btn")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Tab Manager", tint = themeVars.onSurface)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // LazyVerticalGrid of Open Tabs
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tabs) { tab ->
                    val isSelected = tab.id == activeTabId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clickable { onSwitchTab(tab.id) }
                            .testTag("tab_card_${tab.id}"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                themeVars.primaryColor.copy(alpha = 0.2f)
                            } else {
                                themeVars.surface
                            }
                        ),
                        border = if (isSelected) {
                            CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.linearGradient(listOf(themeVars.primaryColor, themeVars.primaryColor))
                            )
                        } else {
                            null
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                        ) {
                            // Top Row: Private Status Icon & Close btn
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (tab.isPrivate) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(Color(0xFF4A154B))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "Private",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = themeVars.primaryColor
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(Color(0xFF1E88E5))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "Standard",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onCloseTab(tab.id) },
                                    modifier = Modifier
                                        .size(24.dp)
                                        .testTag("close_tab_btn_${tab.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Tab",
                                        tint = themeVars.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Tab Title
                            Text(
                                text = tab.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = themeVars.onSurface
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Tab URL
                            Text(
                                text = if (tab.url == "about:blank") "New Tab" else tab.url,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = themeVars.onSurface.copy(alpha = 0.6f),
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Row: Add Standard and Private tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Regular
                Button(
                    onClick = { onNewTab(false) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("add_tab_standard_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (themeVars.isPrivate) Color(0xFF1E88E5) else themeVars.primaryColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Standard", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                // Private
                Button(
                    onClick = { onNewTab(true) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("add_tab_private_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4A126B),
                        contentColor = Color(0xFFE2A7FF)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Private 🕶️", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// BOOKMARKS MANAGER DIALOG/PANEL
// -------------------------------------------------------------
@Composable
fun BookmarksManagerPanel(
    viewModel: BrowserViewModel,
    themeVars: ThemeVars,
    onBookmarkSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    var searchFilter by remember { mutableStateOf("") }
    val filteredList = remember(bookmarks, searchFilter) {
        bookmarks.filter {
            it.title.contains(searchFilter, ignoreCase = true) ||
                    it.url.contains(searchFilter, ignoreCase = true)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .testTag("bookmarks_panel"),
        colors = CardDefaults.cardColors(containerColor = themeVars.container),
        shape = RoundedCornerShape(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Saved Bookmarks",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = themeVars.onSurface
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("bookmarks_panel_close")
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = themeVars.onSurface)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search filter
            OutlinedTextField(
                value = searchFilter,
                onValueChange = { searchFilter = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bookmark_search"),
                placeholder = { Text("Filter saved bookmarks...", color = themeVars.onSurface.copy(alpha = 0.5f)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = themeVars.primaryColor,
                    unfocusedBorderColor = themeVars.onSurface.copy(alpha = 0.2f),
                    focusedContainerColor = themeVars.surface,
                    unfocusedContainerColor = themeVars.surface,
                    focusedTextColor = themeVars.onSurface,
                    unfocusedTextColor = themeVars.onSurface
                ),
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = themeVars.primaryColor) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = themeVars.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No saved bookmarks",
                            color = themeVars.onSurface.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredList) { bookmark ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onBookmarkSelected(bookmark.url) }
                                .testTag("bookmark_item_${bookmark.id}"),
                            colors = CardDefaults.cardColors(containerColor = themeVars.surface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFC107),
                                    modifier = Modifier.size(24.dp)
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bookmark.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = themeVars.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = bookmark.url,
                                        fontSize = 12.sp,
                                        color = themeVars.onSurface.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.deleteBookmark(bookmark) },
                                    modifier = Modifier.testTag("delete_bookmark_${bookmark.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Bookmark",
                                        tint = Color(0xFFEF5350)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// DOWNLOADS MANAGER DIALOG/PANEL
// -------------------------------------------------------------
@Composable
fun DownloadsManagerPanel(
    viewModel: BrowserViewModel,
    themeVars: ThemeVars,
    onDismiss: () -> Unit
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val simpleDateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .testTag("downloads_panel"),
        colors = CardDefaults.cardColors(containerColor = themeVars.container),
        shape = RoundedCornerShape(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Downloads History",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = themeVars.onSurface
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("downloads_panel_close")
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = themeVars.onSurface)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (downloads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = themeVars.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No saved downloads",
                            color = themeVars.onSurface.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(downloads) { download ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag("download_item_${download.id}"),
                            colors = CardDefaults.cardColors(containerColor = themeVars.surface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = themeVars.primaryColor,
                                        modifier = Modifier.size(24.dp)
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = download.fileName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = themeVars.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        val formattedDate = remember(download.timestamp) {
                                            simpleDateFormat.format(Date(download.timestamp))
                                        }

                                        val formattedSize = remember(download.totalBytes) {
                                            formatBytes(download.totalBytes)
                                        }

                                        Text(
                                            text = "$formattedSize • $formattedDate",
                                            fontSize = 11.sp,
                                            color = themeVars.onSurface.copy(alpha = 0.5f)
                                        )
                                    }

                                    // Action buttons (Open file if exist)
                                    val isFileOk = remember(download.filePath) {
                                        File(download.filePath).exists()
                                    }

                                    if (isFileOk) {
                                        TextButton(
                                            onClick = {
                                                try {
                                                    val file = File(download.filePath)
                                                    val uri = Uri.fromFile(file)
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(uri, download.mimeType)
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    // Standard action fallback open downloads directory
                                                    try {
                                                        val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
                                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        context.startActivity(intent)
                                                    } catch (err: Exception) {
                                                        // Fallback
                                                    }
                                                }
                                            }
                                        ) {
                                            Text("Open", color = themeVars.primaryColor)
                                        }
                                    } else {
                                        Text(
                                            "Deleted",
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.deleteDownload(download) },
                                        modifier = Modifier.testTag("delete_download_${download.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete records",
                                            tint = Color(0xFFEF5350)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
