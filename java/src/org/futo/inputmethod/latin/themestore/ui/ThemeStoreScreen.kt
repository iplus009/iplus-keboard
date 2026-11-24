package org.futo.inputmethod.latin.themestore.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.themestore.domain.StoreCatalogFilter
import org.futo.inputmethod.latin.themestore.domain.ThemeStoreRepository
import org.futo.inputmethod.latin.themestore.engine.ThemeManifestEngine
import org.futo.inputmethod.latin.themestore.engine.ThemeRegistry
import org.futo.inputmethod.latin.themestore.model.ProductTabFilter
import org.futo.inputmethod.latin.themestore.model.StoreCatalogEntry
import org.futo.inputmethod.latin.themestore.model.StoreThemeState
import org.futo.inputmethod.latin.themestore.model.ThemeFilter
import org.futo.inputmethod.latin.themestore.network.SupabaseAuthClient
import org.futo.inputmethod.latin.uix.THEME_KEY
import org.futo.inputmethod.latin.uix.normalizeThemeKey
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue

@Composable
fun ThemeStoreScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember(context) { ThemeStoreRepository(context) }

    val revision by ThemeRegistry.revision.collectAsState()
    val accountSession by SupabaseAuthClient.sessionFlow.collectAsState()
    val rawThemeKey = useDataStoreValue(THEME_KEY)
    val activeThemeKey = normalizeThemeKey(rawThemeKey)

    var allEntries by remember { mutableStateOf(repo.getCachedEntries()) }
    var activeFontId by remember { mutableStateOf(repo.getActiveFontId()) }
    var activeProductTab by remember { mutableStateOf(ProductTabFilter.ALL) }
    var activeFilter by remember { mutableStateOf(ThemeFilter.ALL) }
    var selectedCategory by remember { mutableStateOf("all") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedPaidIds by remember { mutableStateOf(setOf<String>()) }
    var busyThemeId by remember { mutableStateOf<String?>(null) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf<String?>(null) }
    var previewEntryId by remember { mutableStateOf<String?>(null) }

    var showAuthDialog by remember { mutableStateOf(false) }
    var pendingAuthAction by remember { mutableStateOf<String?>(null) }
    var lastSyncedUserId by remember { mutableStateOf<String?>(SupabaseAuthClient.currentSession(context)?.userId) }
    var showRedeemDialog by remember { mutableStateOf(false) }
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var showCheckoutDialog by remember { mutableStateOf(false) }

    fun refreshFromCache() {
        allEntries = repo.getCachedEntries()
        activeFontId = repo.getActiveFontId()
        // Drop any selected themes that became owned or disappeared
        val buyableIds = allEntries.filter { it.state == StoreThemeState.PAID }.map { it.themeId }.toSet()
        selectedPaidIds = selectedPaidIds.intersect(buyableIds)
    }

    fun triggerSync() {
        if (isSyncing) return
        isSyncing = true
        syncError = null
        scope.launch {
            repo.syncCatalogAndRevocations()
                .onSuccess {
                    allEntries = it
                    val buyableIds = it.filter { e -> e.state == StoreThemeState.PAID }.map { e -> e.themeId }.toSet()
                    selectedPaidIds = selectedPaidIds.intersect(buyableIds)
                }
                .onFailure {
                    syncError = it.message ?: "Unable to reach Theme Hub"
                }
            isSyncing = false
        }
    }

    LaunchedEffect(Unit) {
        SupabaseAuthClient.currentSession(context)
        triggerSync()
    }

    LaunchedEffect(accountSession?.userId) {
        val session = accountSession
        if (session != null) {
            val wasDialogOpen = showAuthDialog
            if (wasDialogOpen) {
                showAuthDialog = false
                when (pendingAuthAction) {
                    "checkout" -> showCheckoutDialog = true
                    "redeem" -> showRedeemDialog = true
                }
                pendingAuthAction = null
            }
            if (lastSyncedUserId != session.userId) {
                lastSyncedUserId = session.userId
                val synced = repo.syncAccountEntitlements().getOrDefault(emptyList())
                refreshFromCache()
                if (wasDialogOpen) {
                    Toast.makeText(
                        context,
                        if (synced.isNotEmpty()) "Welcome back! ${synced.size} owned theme(s) ready" else "Signed in!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                refreshFromCache()
            }
        } else {
            lastSyncedUserId = null
            refreshFromCache()
        }
    }

    LaunchedEffect(revision) {
        refreshFromCache()
    }

    val tabScopedEntries = remember(allEntries, activeProductTab) {
        StoreCatalogFilter.applyFilter(allEntries, ThemeFilter.ALL, "", "all", activeProductTab)
    }
    val themesCount = remember(allEntries) { allEntries.count { it.themeType == "color" || it.themeType == "art_pack" } }
    val fontsCount = remember(allEntries) { allEntries.count { it.themeType == "font" } }
    val stickersCount = remember(allEntries) { allEntries.count { it.themeType == "sticker" } }

    val categoryCounts = remember(tabScopedEntries) {
        tabScopedEntries.groupingBy { it.category.trim().lowercase().ifEmpty { "general" } }.eachCount()
    }
    val availableCategories = remember(categoryCounts) {
        listOf("all") + categoryCounts.keys.sorted()
    }

    val filteredEntries = remember(allEntries, activeProductTab, activeFilter, selectedCategory, searchQuery) {
        StoreCatalogFilter.applyFilter(allEntries, activeFilter, searchQuery, selectedCategory, activeProductTab)
    }

    val selectedCartEntries = remember(allEntries, selectedPaidIds) {
        allEntries.filter { selectedPaidIds.contains(it.themeId) && it.state == StoreThemeState.PAID }
    }

    fun isEntryActive(entry: StoreCatalogEntry): Boolean = when (entry.themeType) {
        "font" -> activeFontId == entry.themeId
        "sticker" -> false
        else -> activeThemeKey == ThemeRegistry.storeKeyFor(entry.themeId)
    }

    fun handlePrimaryAction(entry: StoreCatalogEntry) {
        val isInCart = selectedPaidIds.contains(entry.themeId)
        when (entry.state) {
            StoreThemeState.FREE, StoreThemeState.OWNED -> {
                busyThemeId = entry.themeId
                scope.launch {
                    repo.downloadAndApplyTheme(entry)
                        .onSuccess {
                            refreshFromCache()
                            val msg = when (entry.themeType) {
                                "font" -> "${entry.title} font applied!"
                                "sticker" -> "${entry.title} ready! Open Emoji ➔ Stickers tab."
                                else -> "${entry.title} applied!"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                        .onFailure {
                            Toast.makeText(context, it.message ?: "Download failed", Toast.LENGTH_LONG).show()
                        }
                    busyThemeId = null
                }
            }
            StoreThemeState.DOWNLOADED -> {
                if (entry.hasUpdate) {
                    busyThemeId = entry.themeId
                    scope.launch {
                        repo.downloadAndApplyTheme(entry)
                            .onSuccess {
                                refreshFromCache()
                                Toast.makeText(context, "${entry.title} updated & applied", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure {
                                Toast.makeText(context, it.message ?: "Update failed", Toast.LENGTH_LONG).show()
                            }
                        busyThemeId = null
                    }
                } else {
                    when (entry.themeType) {
                        "font" -> {
                            repo.setActiveFontId(entry.themeId)
                            refreshFromCache()
                            Toast.makeText(context, "${entry.title} font active", Toast.LENGTH_SHORT).show()
                        }
                        "sticker" -> {
                            Toast.makeText(context, "Open Keybird Emoji ➔ Stickers tab to send stickers!", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            scope.launch {
                                repo.applyInstalledTheme(entry.themeId)
                                refreshFromCache()
                                Toast.makeText(context, "${entry.title} selected", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            StoreThemeState.PAID -> {
                selectedPaidIds = if (isInCart) {
                    selectedPaidIds - entry.themeId
                } else {
                    selectedPaidIds + entry.themeId
                }
            }
        }
    }

    fun handleInstantBuy(entry: StoreCatalogEntry) {
        selectedPaidIds = selectedPaidIds + entry.themeId
        if (accountSession == null) {
            pendingAuthAction = "checkout"
            showAuthDialog = true
        } else {
            showCheckoutDialog = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(org.futo.inputmethod.latin.uix.settings.KeybirdUiTokens.pageBackground())
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenTitle("Theme Store", showBack = true, navController = navController)

            // Compact Account & Quick Actions Card (merges account status + Redeem/Restore/Support/Sync)
            val ownedPaidCount = remember(allEntries) { allEntries.count { it.isPaid && it.isOwned } }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = org.futo.inputmethod.latin.uix.settings.KeybirdUiTokens.cardSurface(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .border(
                        1.dp,
                        org.futo.inputmethod.latin.uix.settings.KeybirdUiTokens.hairlineBorder(),
                        RoundedCornerShape(14.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = if (accountSession != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = accountSession?.email?.ifBlank { "Signed In" } ?: "Sign in to save purchases forever",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (accountSession != null) {
                                        "$ownedPaidCount lifetime paid item(s) • Synced"
                                    } else {
                                        "Account required before buying or unlocking items"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (accountSession != null) {
                            TextButton(
                                onClick = {
                                    repo.signOutAccount()
                                    selectedPaidIds = emptySet()
                                    refreshFromCache()
                                    Toast.makeText(context, "Signed out. Log back in anytime to restore your items.", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Logout", style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            Button(
                                onClick = {
                                    pendingAuthAction = null
                                    showAuthDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Login", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (accountSession == null) {
                                    pendingAuthAction = "redeem"
                                    showAuthDialog = true
                                } else {
                                    showRedeemDialog = true
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Redeem", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { showRecoveryDialog = true },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("Restore", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { openTelegramSupport(context, repo.supportTelegramHandle) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Telegram Admin Support",
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(repo.supportTelegramHandle, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(
                            onClick = { triggerSync() },
                            enabled = !isSyncing,
                            modifier = Modifier.size(34.dp)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Sync catalog", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search name or #5-digit code (e.g. 10089)", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Product type tabs: All | Themes | Fonts | Stickers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StoreFilterPill("All (${allEntries.size})", activeProductTab == ProductTabFilter.ALL) {
                    activeProductTab = ProductTabFilter.ALL
                    selectedCategory = "all"
                }
                StoreFilterPill("Themes ($themesCount)", activeProductTab == ProductTabFilter.THEMES) {
                    activeProductTab = ProductTabFilter.THEMES
                    selectedCategory = "all"
                }
                StoreFilterPill("Fonts ($fontsCount)", activeProductTab == ProductTabFilter.FONTS) {
                    activeProductTab = ProductTabFilter.FONTS
                    selectedCategory = "all"
                }
                StoreFilterPill("Stickers ($stickersCount)", activeProductTab == ProductTabFilter.STICKERS) {
                    activeProductTab = ProductTabFilter.STICKERS
                    selectedCategory = "all"
                }
                if (activeFontId != null) {
                    StoreFilterPill("Reset Font ✕", false) {
                        repo.setActiveFontId(null)
                        refreshFromCache()
                        Toast.makeText(context, "Restored default keyboard font", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // Combined single-row Filter + Categories strip to keep theme cards above the fold
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StoreFilterPill("All", activeFilter == ThemeFilter.ALL) {
                    activeFilter = ThemeFilter.ALL
                }
                StoreFilterPill("Free", activeFilter == ThemeFilter.FREE) {
                    activeFilter = ThemeFilter.FREE
                }
                StoreFilterPill("Paid", activeFilter == ThemeFilter.PAID) {
                    activeFilter = ThemeFilter.PAID
                }
                StoreFilterPill("My Items", activeFilter == ThemeFilter.MY_THEMES) {
                    activeFilter = ThemeFilter.MY_THEMES
                }
                if (availableCategories.size > 2) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(18.dp)
                            .background(org.futo.inputmethod.latin.uix.settings.KeybirdUiTokens.hairlineBorder())
                    )
                    availableCategories.forEach { cat ->
                        val countLabel = if (cat == "all") {
                            "All Categories (${tabScopedEntries.size})"
                        } else {
                            val titleCat = cat.split('_').joinToString(" ") { part ->
                                part.replaceFirstChar { it.uppercase() }
                            }
                            "$titleCat (${categoryCounts[cat] ?: 0})"
                        }
                        StoreFilterPill(countLabel, selectedCategory == cat) {
                            selectedCategory = cat
                        }
                    }
                }
            }

            syncError?.let { err ->
                Text(
                    text = "Offline / Sync note: $err",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            if (filteredEntries.isEmpty() && !isSyncing) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (allEntries.isEmpty()) "No themes loaded yet" else "No matching themes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { triggerSync() }) {
                            Text("Sync Theme Hub")
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = if (selectedCartEntries.isNotEmpty()) 96.dp else 20.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredEntries, key = { it.themeId }) { entry ->
                        val isActive = isEntryActive(entry)
                        val isInCart = selectedPaidIds.contains(entry.themeId)
                        StoreThemeCard(
                            entry = entry,
                            isActive = isActive,
                            isInCart = isInCart,
                            isBusy = busyThemeId == entry.themeId,
                            onPreview = { previewEntryId = entry.themeId },
                            onPrimaryAction = { handlePrimaryAction(entry) },
                            onInstantBuy = { handleInstantBuy(entry) }
                        )
                    }
                }
            }
        }

        // Floating single-by-single cart checkout bar
        if (selectedCartEntries.isNotEmpty()) {
            val totalPaisa = selectedCartEntries.sumOf { it.priceBdtPaisa }
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 8.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${selectedCartEntries.size} theme(s) • ${formatBdtFromPaisa(totalPaisa)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "One checkout -> One bundle API key",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { selectedPaidIds = emptySet() }) {
                        Text("Clear")
                    }
                    Button(onClick = {
                        if (accountSession == null) {
                            pendingAuthAction = "checkout"
                            showAuthDialog = true
                        } else {
                            showCheckoutDialog = true
                        }
                    }) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Buy Now")
                    }
                }
            }
        }
    }

    if (showAuthDialog) {
        AccountAuthDialog(
            onDismiss = {
                showAuthDialog = false
                pendingAuthAction = null
            }
        )
    }

    val activePreviewEntry = remember(allEntries, previewEntryId) {
        previewEntryId?.let { id -> allEntries.firstOrNull { it.themeId == id } }
    }
    if (activePreviewEntry != null) {
        ThemePreviewDialog(
            entry = activePreviewEntry,
            isActive = isEntryActive(activePreviewEntry),
            isInCart = selectedPaidIds.contains(activePreviewEntry.themeId),
            isBusy = busyThemeId == activePreviewEntry.themeId,
            supportTelegram = repo.supportTelegramHandle,
            onDismiss = { previewEntryId = null },
            onPrimaryAction = { handlePrimaryAction(activePreviewEntry) },
            onInstantBuy = { handleInstantBuy(activePreviewEntry) }
        )
    }

    if (showCheckoutDialog && selectedCartEntries.isNotEmpty()) {
        PurchaseRequestDialog(
            selectedThemes = selectedCartEntries,
            paymentAccounts = repo.lastPaymentAccounts,
            supportTelegram = repo.supportTelegramHandle,
            onDismiss = { showCheckoutDialog = false },
            onCreateOrder = { name, contact, method ->
                repo.createOrder(
                    themeIds = selectedCartEntries.map { it.themeId },
                    buyerName = name,
                    buyerContact = contact,
                    paymentMethod = method
                )
            },
            onSubmitTrx = { order, trxId ->
                repo.submitTrx(
                    orderId = order.orderId,
                    trxId = trxId,
                    orderMeta = order,
                    themeIds = selectedCartEntries.map { it.themeId }
                )
            }
        )
    }

    if (showRedeemDialog) {
        RedeemKeyDialog(
            onDismiss = {
                showRedeemDialog = false
                refreshFromCache()
            },
            onRedeem = { apiKey ->
                repo.redeemApiKey(apiKey).also { res ->
                    if (res.isSuccess) refreshFromCache()
                }
            }
        )
    }

    if (showRecoveryDialog) {
        RecoveryAndDeviceDialog(
            deviceHash = repo.deviceHash,
            recoveryHint = repo.getActiveRecoveryHint(),
            orderHistory = repo.getOrderHistory(),
            onDismiss = {
                showRecoveryDialog = false
                refreshFromCache()
            },
            onRecover = { token, trx, contact ->
                repo.recoverPurchases(token, trx, contact).also { res ->
                    if (res.isSuccess) refreshFromCache()
                }
            },
            onDeactivateDevice = {
                repo.deactivateDevice().also { res ->
                    if (res.isSuccess) refreshFromCache()
                }
            }
        )
    }
}

@Composable
private fun StoreFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = fg
        )
    }
}

@Composable
private fun StoreThemeCard(
    entry: StoreCatalogEntry,
    isActive: Boolean,
    isInCart: Boolean,
    isBusy: Boolean,
    onPreview: () -> Unit,
    onPrimaryAction: () -> Unit,
    onInstantBuy: () -> Unit
) {
    val accentColor = remember(entry.accentHex) {
        Color(ThemeManifestEngine.parseHexColor(entry.accentHex, 0xFF00E676.toInt()))
    }
    val borderColor = when {
        isActive -> MaterialTheme.colorScheme.primary
        isInCart -> accentColor
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    }
    val borderWidth = if (isActive || isInCart) 2.dp else 1.dp

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
            .clickable(enabled = !isBusy, onClick = onPreview)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Mini keyboard / real artwork / font specimen preview
            val isLightPreview = remember(entry.themeId, entry.description) {
                entry.themeId.contains("light", ignoreCase = true) ||
                    entry.themeId.contains("paper", ignoreCase = true) ||
                    Regex("\\blight\\b", RegexOption.IGNORE_CASE).containsMatchIn(entry.description)
            }
            val boardTop = if (isLightPreview) Color(0xFFF3F4F6) else Color(0xFF101318)
            val keyColor = if (isLightPreview) Color(0xFFFFFFFF) else Color(0xFF1E222B)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.verticalGradient(listOf(boardTop, accentColor.copy(alpha = 0.25f))))
            ) {
                val thumbUrl = entry.previewUrl.ifBlank { entry.previewUrls.en }
                StoreRemoteThumbnail(
                    rawUrl = thumbUrl,
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (entry.themeType == "font") {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Aa • অ আ • ১২৩",
                                color = if (isLightPreview) Color(0xFF111827) else Color(0xFFF9FAFB),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.75f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(accentColor)
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                repeat(5) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(14.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(keyColor)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(keyColor)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(2.6f)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(keyColor)
                                        .border(1.dp, accentColor, RoundedCornerShape(4.dp))
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(accentColor)
                                )
                            }
                        }
                    }
                }

                // State badge (top-start)
                val (badgeLabel, badgeBg) = when {
                    entry.hasUpdate -> "UPDATE v${entry.remoteVersion}" to MaterialTheme.colorScheme.error
                    isActive && entry.themeType == "font" -> "ACTIVE FONT" to MaterialTheme.colorScheme.primary
                    isActive -> "ACTIVE" to MaterialTheme.colorScheme.primary
                    entry.state == StoreThemeState.DOWNLOADED -> "DOWNLOADED" to Color(0xFF2E7D32)
                    entry.state == StoreThemeState.OWNED -> "OWNED" to Color(0xFF0288D1)
                    entry.state == StoreThemeState.FREE -> "FREE" to Color(0xFF2E7D32)
                    entry.hasDiscount -> (entry.discountLabel.ifBlank { "${entry.discountPercent}% OFF" }) to Color(0xFFDC2626)
                    else -> formatBdtFromPaisa(entry.priceBdtPaisa) to Color(0xFFD97706)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.SansSerif,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 5-digit short code pill or In-Cart checkmark (top-end)
                if (isInCart) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "In Cart",
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else if (entry.shortCode.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xB8000000))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "#${entry.shortCode}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Column {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.category.split('_').joinToString(" ") { part ->
                        part.replaceFirstChar { it.uppercase() }
                    } + if (entry.description.isNotBlank()) " • ${entry.description}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (entry.state == StoreThemeState.PAID && entry.hasDiscount) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.state == StoreThemeState.PAID && entry.hasDiscount) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatBdtFromPaisa(entry.originalPriceBdtPaisa),
                            style = MaterialTheme.typography.labelMedium,
                            textDecoration = TextDecoration.LineThrough,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatBdtFromPaisa(entry.priceBdtPaisa),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD97706)
                        )
                    }
                }
            }

            when (entry.state) {
                StoreThemeState.PAID -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = onPrimaryAction,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                if (isInCart) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(if (isInCart) "Added" else "Cart", maxLines = 1)
                        }
                        Button(
                            onClick = onInstantBuy,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Text("Buy", maxLines = 1)
                        }
                    }
                }
                else -> {
                    val btnText = when {
                        isBusy -> "Working..."
                        entry.hasUpdate -> "Update"
                        entry.themeType == "sticker" && entry.state == StoreThemeState.DOWNLOADED -> "In Stickers"
                        entry.themeType == "sticker" -> "Download"
                        entry.themeType == "font" && isActive -> "Font Active"
                        entry.themeType == "font" && entry.state == StoreThemeState.DOWNLOADED -> "Apply Font"
                        entry.themeType == "font" -> "Get Font"
                        entry.state == StoreThemeState.DOWNLOADED && isActive -> "Applied"
                        entry.state == StoreThemeState.DOWNLOADED -> "Apply"
                        entry.state == StoreThemeState.OWNED -> "Download"
                        else -> "Get & Apply"
                    }
                    Button(
                        onClick = onPrimaryAction,
                        enabled = !isBusy && !(entry.state == StoreThemeState.DOWNLOADED && isActive && !entry.hasUpdate),
                        colors = if (isActive && !entry.hasUpdate) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(btnText, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
