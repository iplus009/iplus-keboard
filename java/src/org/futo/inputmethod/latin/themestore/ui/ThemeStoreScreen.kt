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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.themestore.domain.StoreCatalogFilter
import org.futo.inputmethod.latin.themestore.domain.ThemeStoreRepository
import org.futo.inputmethod.latin.themestore.engine.ThemeManifestEngine
import org.futo.inputmethod.latin.themestore.engine.ThemeRegistry
import org.futo.inputmethod.latin.themestore.model.StoreCatalogEntry
import org.futo.inputmethod.latin.themestore.model.StoreThemeState
import org.futo.inputmethod.latin.themestore.model.ThemeFilter
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
    val rawThemeKey = useDataStoreValue(THEME_KEY)
    val activeThemeKey = normalizeThemeKey(rawThemeKey)

    var allEntries by remember { mutableStateOf(repo.getCachedEntries()) }
    var activeFilter by remember { mutableStateOf(ThemeFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedPaidIds by remember { mutableStateOf(setOf<String>()) }
    var busyThemeId by remember { mutableStateOf<String?>(null) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf<String?>(null) }

    var showRedeemDialog by remember { mutableStateOf(false) }
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var showCheckoutDialog by remember { mutableStateOf(false) }

    fun refreshFromCache() {
        allEntries = repo.getCachedEntries()
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
        triggerSync()
    }

    LaunchedEffect(revision) {
        refreshFromCache()
    }

    val filteredEntries = remember(allEntries, activeFilter, searchQuery) {
        StoreCatalogFilter.applyFilter(allEntries, activeFilter, searchQuery)
    }

    val selectedCartEntries = remember(allEntries, selectedPaidIds) {
        allEntries.filter { selectedPaidIds.contains(it.themeId) && it.state == StoreThemeState.PAID }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenTitle("Theme Store", showBack = true, navController = navController)

            // Quick toolbar: Redeem Key | Restore & Device | Sync
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { showRedeemDialog = true },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Redeem Key", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = { showRecoveryDialog = true },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text("Restore / Device", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { triggerSync() }, enabled = !isSyncing) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync catalog")
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search Keybird themes...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                StoreFilterPill("My Themes", activeFilter == ThemeFilter.MY_THEMES) {
                    activeFilter = ThemeFilter.MY_THEMES
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
                        val isActive = activeThemeKey == ThemeRegistry.storeKeyFor(entry.themeId)
                        val isInCart = selectedPaidIds.contains(entry.themeId)
                        StoreThemeCard(
                            entry = entry,
                            isActive = isActive,
                            isInCart = isInCart,
                            isBusy = busyThemeId == entry.themeId,
                            onPrimaryAction = {
                                when (entry.state) {
                                    StoreThemeState.FREE, StoreThemeState.OWNED -> {
                                        busyThemeId = entry.themeId
                                        scope.launch {
                                            repo.downloadAndApplyTheme(entry)
                                                .onSuccess {
                                                    refreshFromCache()
                                                    Toast.makeText(context, "${entry.title} applied!", Toast.LENGTH_SHORT).show()
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
                                            scope.launch {
                                                repo.applyInstalledTheme(entry.themeId)
                                                refreshFromCache()
                                                Toast.makeText(context, "${entry.title} selected", Toast.LENGTH_SHORT).show()
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
                            },
                            onInstantBuy = {
                                selectedPaidIds = selectedPaidIds + entry.themeId
                                showCheckoutDialog = true
                            }
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
                    Button(onClick = { showCheckoutDialog = true }) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Buy Now")
                    }
                }
            }
        }
    }

    if (showCheckoutDialog && selectedCartEntries.isNotEmpty()) {
        PurchaseRequestDialog(
            selectedThemes = selectedCartEntries,
            paymentAccounts = repo.lastPaymentAccounts,
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
            .clickable(enabled = !isBusy && entry.state == StoreThemeState.PAID, onClick = onPrimaryAction)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Mini keyboard visual swatch preview
            val isLightPreview = entry.themeId.contains("light", ignoreCase = true) ||
                entry.themeId.contains("paper", ignoreCase = true)
            val boardTop = if (isLightPreview) Color(0xFFF3F4F6) else Color(0xFF101318)
            val keyColor = if (isLightPreview) Color(0xFFFFFFFF) else Color(0xFF1E222B)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.verticalGradient(listOf(boardTop, accentColor.copy(alpha = 0.25f))))
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter),
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

                // State badge
                val (badgeLabel, badgeBg) = when {
                    entry.hasUpdate -> "UPDATE v${entry.remoteVersion}" to MaterialTheme.colorScheme.error
                    isActive -> "ACTIVE" to MaterialTheme.colorScheme.primary
                    entry.state == StoreThemeState.DOWNLOADED -> "DOWNLOADED" to Color(0xFF2E7D32)
                    entry.state == StoreThemeState.OWNED -> "OWNED" to Color(0xFF0288D1)
                    entry.state == StoreThemeState.FREE -> "FREE" to Color(0xFF2E7D32)
                    else -> formatBdtFromPaisa(entry.priceBdtPaisa) to Color(0xFFD97706)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isInCart) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
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
                    text = entry.category.replaceFirstChar { it.uppercase() } +
                        if (entry.description.isNotBlank()) " • ${entry.description}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
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
                        entry.state == StoreThemeState.DOWNLOADED && isActive -> "Applied"
                        entry.state == StoreThemeState.DOWNLOADED -> "Apply"
                        entry.state == StoreThemeState.OWNED -> "Install"
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
