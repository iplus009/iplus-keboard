package org.futo.inputmethod.latin.themestore.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.themestore.domain.RedeemResult
import org.futo.inputmethod.latin.themestore.model.CreateOrderResponse
import org.futo.inputmethod.latin.themestore.model.OrderHistoryEntry
import org.futo.inputmethod.latin.themestore.model.PaymentAccountsDto
import org.futo.inputmethod.latin.themestore.model.StoreCatalogEntry
import org.futo.inputmethod.latin.themestore.network.SignUpOutcome
import org.futo.inputmethod.latin.themestore.network.SupabaseAuthClient

private val TRX_ID_REGEX = Regex("^[A-Z0-9]{8,12}$")

internal fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
}

internal fun formatBdtFromPaisa(paisa: Int): String {
    val whole = paisa / 100
    val frac = paisa % 100
    return if (frac == 0) "৳$whole" else "৳$whole.${frac.toString().padStart(2, '0')}"
}

@Composable
fun PurchaseRequestDialog(
    selectedThemes: List<StoreCatalogEntry>,
    paymentAccounts: PaymentAccountsDto,
    onDismiss: () -> Unit,
    onCreateOrder: suspend (buyerName: String, buyerContact: String, paymentMethod: String) -> Result<CreateOrderResponse>,
    onSubmitTrx: suspend (order: CreateOrderResponse, trxId: String) -> Result<String>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var buyerName by remember { mutableStateOf("") }
    var buyerContact by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("bkash") }
    var createdOrder by remember { mutableStateOf<CreateOrderResponse?>(null) }
    var trxInput by remember { mutableStateOf("") }
    var submittedStatus by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val baseTotalPaisa = remember(selectedThemes) {
        selectedThemes.sumOf { it.priceBdtPaisa }
    }

    Dialog(onDismissRequest = { if (!isBusy) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Buy ${selectedThemes.size} Theme${if (selectedThemes.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    selectedThemes.forEach { theme ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = theme.title,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (theme.hasDiscount) {
                                    Text(
                                        text = formatBdtFromPaisa(theme.originalPriceBdtPaisa),
                                        style = MaterialTheme.typography.bodySmall,
                                        textDecoration = TextDecoration.LineThrough,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = formatBdtFromPaisa(theme.priceBdtPaisa),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Base Total (1 Bundle API Key)", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = formatBdtFromPaisa(baseTotalPaisa),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                val order = createdOrder
                if (order == null) {
                    Text(
                        text = "1. Select Payment Method",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MethodToggleChip(
                            label = "bKash",
                            selected = paymentMethod == "bkash",
                            onClick = { paymentMethod = "bkash" },
                            modifier = Modifier.weight(1f)
                        )
                        MethodToggleChip(
                            label = "Nagad",
                            selected = paymentMethod == "nagad",
                            onClick = { paymentMethod = "nagad" },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    val previewNumber = if (paymentMethod == "nagad") paymentAccounts.nagad else paymentAccounts.bkash
                    if (previewNumber.isNotBlank()) {
                        Text(
                            text = "${paymentMethod.uppercase()} Personal/Merchant: $previewNumber",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedTextField(
                        value = buyerName,
                        onValueChange = { buyerName = it },
                        label = { Text("Your Name (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = buyerContact,
                        onValueChange = { buyerContact = it; errorMsg = null },
                        label = { Text("WhatsApp / Telegram / Phone (Required)") },
                        placeholder = { Text("01XXXXXXXXX or @username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    errorMsg?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss, enabled = !isBusy) {
                            Text("Cancel")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            enabled = !isBusy,
                            onClick = {
                                val contact = buyerContact.trim()
                                if (contact.length < 5) {
                                    errorMsg = "Enter a valid phone number, WhatsApp, or @username"
                                    return@Button
                                }
                                isBusy = true
                                errorMsg = null
                                scope.launch {
                                    onCreateOrder(buyerName.trim(), contact, paymentMethod)
                                        .onSuccess { createdOrder = it }
                                        .onFailure { errorMsg = it.message ?: "Failed to create order" }
                                    isBusy = false
                                }
                            }
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .height(16.dp)
                                        .width(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Get Exact Amount")
                            }
                        }
                    }
                } else if (submittedStatus == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Send EXACT Amount (Unique Paisa Offset)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "৳${order.expectedAmountBdt}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                OutlinedButton(onClick = {
                                    copyToClipboard(context, "Exact Amount", order.expectedAmountBdt)
                                }) {
                                    Text("Copy")
                                }
                            }
                            if (order.payTo.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${order.paymentMethod.uppercase()} to: ${order.payTo}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    TextButton(onClick = {
                                        copyToClipboard(context, "Payment Number", order.payTo)
                                    }) {
                                        Text("Copy No.")
                                    }
                                }
                            }
                            Text(
                                text = "Send exact paisa so your payment matches cleanly. After paying, paste only the 8–12 char TrxID below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = trxInput,
                        onValueChange = { trxInput = it.uppercase(); errorMsg = null },
                        label = { Text("Transaction ID (TrxID)") },
                        placeholder = { Text("e.g. BL74X9K2QA") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    errorMsg?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss, enabled = !isBusy) {
                            Text("Close")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            enabled = !isBusy,
                            onClick = {
                                val cleanTrx = trxInput.trim().uppercase()
                                if (!TRX_ID_REGEX.matches(cleanTrx)) {
                                    errorMsg = "Enter only the 8–12 character alphanumeric TrxID (do not paste the full SMS)"
                                    return@Button
                                }
                                isBusy = true
                                errorMsg = null
                                scope.launch {
                                    onSubmitTrx(order, cleanTrx)
                                        .onSuccess { status -> submittedStatus = status }
                                        .onFailure { errorMsg = it.message ?: "Failed to submit TrxID" }
                                    isBusy = false
                                }
                            }
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .height(16.dp)
                                        .width(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Submit TrxID")
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Order Submitted (${submittedStatus})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Order ID: ${order.orderId}\nAmount: ৳${order.expectedAmountBdt}\n\nAdmin has been notified on Telegram. Once verified, you will receive your 1-Time API Key to activate all ${selectedThemes.size} selected theme(s).",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(onClick = onDismiss) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RedeemKeyDialog(
    onDismiss: () -> Unit,
    onRedeem: suspend (apiKey: String) -> Result<RedeemResult>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apiKeyInput by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<RedeemResult?>(null) }

    Dialog(onDismissRequest = { if (!isBusy) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Redeem 1-Time API Key",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                val redeemed = result
                if (redeemed == null) {
                    Text(
                        text = "Enter the 1-time license key from Admin. Your unlocked themes will download cleanly and remain bound offline to this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it; errorMsg = null },
                        label = { Text("API Key") },
                        placeholder = { Text("OP1-...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    errorMsg?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss, enabled = !isBusy) {
                            Text("Cancel")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            enabled = !isBusy,
                            onClick = {
                                val cleanKey = apiKeyInput.trim()
                                if (cleanKey.length < 10) {
                                    errorMsg = "Enter a valid 1-time API key"
                                    return@Button
                                }
                                isBusy = true
                                errorMsg = null
                                scope.launch {
                                    onRedeem(cleanKey)
                                        .onSuccess { result = it }
                                        .onFailure { errorMsg = it.message ?: "Activation failed" }
                                    isBusy = false
                                }
                            }
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .height(16.dp)
                                        .width(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Activate")
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Unlocked ${redeemed.grantedThemeIds.size} paid theme(s)!",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Themes: ${redeemed.grantedThemeIds.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (redeemed.recoveryToken.isNotBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "SAVE THIS RECOVERY TOKEN (Shown Once)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = redeemed.recoveryToken,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall
                            )
                            OutlinedButton(onClick = {
                                copyToClipboard(context, "Recovery Token", redeemed.recoveryToken)
                            }) {
                                Text("Copy Recovery Token")
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(onClick = onDismiss) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecoveryAndDeviceDialog(
    deviceHash: String,
    recoveryHint: String?,
    orderHistory: List<OrderHistoryEntry>,
    onDismiss: () -> Unit,
    onRecover: suspend (recoveryToken: String?, trxId: String?, buyerContact: String?) -> Result<RedeemResult>,
    onDeactivateDevice: suspend () -> Result<Unit>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var useTokenMode by remember { mutableStateOf(true) }
    var recoveryToken by remember { mutableStateOf("") }
    var trxId by remember { mutableStateOf("") }
    var buyerContact by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var confirmDeactivate by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!isBusy) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Restore & Device Tools",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                // Device slot & deactivation section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "This Device",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Device ID: ${deviceHash.take(12)}...${deviceHash.takeLast(6)}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!recoveryHint.isNullOrBlank()) {
                        Text(
                            text = "Saved Token Hint: $recoveryHint",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (!confirmDeactivate) {
                        OutlinedButton(
                            enabled = !isBusy,
                            onClick = { confirmDeactivate = true }
                        ) {
                            Text("Deactivate This Device")
                        }
                    } else {
                        Text(
                            text = "Confirm: Deactivating releases your slot on the server and clears local paid themes on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                enabled = !isBusy,
                                onClick = { confirmDeactivate = false }
                            ) { Text("Keep Active") }
                            Button(
                                enabled = !isBusy,
                                onClick = {
                                    isBusy = true
                                    errorMsg = null
                                    scope.launch {
                                        onDeactivateDevice()
                                            .onSuccess {
                                                statusMessage = "Device deactivated. You can now recover on a new device."
                                                confirmDeactivate = false
                                            }
                                            .onFailure { errorMsg = it.message ?: "Deactivation failed" }
                                        isBusy = false
                                    }
                                }
                            ) { Text("Confirm Deactivate") }
                        }
                    }
                }

                HorizontalDivider()

                // Self-serve recovery section (max 2 / 30 days)
                Text(
                    text = "Restore Paid Themes (Max 2 / 30 days)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MethodToggleChip(
                        label = "Recovery Token",
                        selected = useTokenMode,
                        onClick = { useTokenMode = true; errorMsg = null },
                        modifier = Modifier.weight(1f)
                    )
                    MethodToggleChip(
                        label = "TrxID + Contact",
                        selected = !useTokenMode,
                        onClick = { useTokenMode = false; errorMsg = null },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (useTokenMode) {
                    OutlinedTextField(
                        value = recoveryToken,
                        onValueChange = { recoveryToken = it; errorMsg = null },
                        label = { Text("Recovery Token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = trxId,
                        onValueChange = { trxId = it.uppercase(); errorMsg = null },
                        label = { Text("Approved TrxID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = buyerContact,
                        onValueChange = { buyerContact = it; errorMsg = null },
                        label = { Text("Order Contact / Phone") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                statusMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    enabled = !isBusy,
                    onClick = {
                        isBusy = true
                        errorMsg = null
                        statusMessage = null
                        scope.launch {
                            val res = if (useTokenMode) {
                                onRecover(recoveryToken, null, null)
                            } else {
                                onRecover(null, trxId, buyerContact)
                            }
                            res.onSuccess {
                                if (it.recoveryToken.isNotBlank()) {
                                    copyToClipboard(context, "New Recovery Token", it.recoveryToken)
                                }
                                statusMessage = "Restored ${it.grantedThemeIds.size} theme(s). New recovery token copied!"
                            }.onFailure {
                                errorMsg = it.message ?: "Recovery failed"
                            }
                            isBusy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isBusy) "Restoring..." else "Restore Purchases")
                }

                if (orderHistory.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        text = "Recent Orders (${orderHistory.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    orderHistory.take(5).forEach { entry ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(entry.orderId, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(entry.status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                text = "${entry.paymentMethod.uppercase()} ৳${entry.expectedAmountBdt} • ${entry.themeIds.size} theme(s)",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (entry.trxId.isNotBlank()) {
                                Text("TrxID: ${entry.trxId}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !isBusy) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun AccountAuthDialog(
    onDismiss: () -> Unit,
    onSignedIn: suspend () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var infoMsg by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!isBusy) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isSignUp) "Create Keybird Account" else "Sign In to Keybird Store",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Themes you buy or unlock stay saved to your account forever. After reinstalling, just log in and download only the ones you want.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MethodToggleChip(
                        label = "Sign In",
                        selected = !isSignUp,
                        onClick = { isSignUp = false; errorMsg = null },
                        modifier = Modifier.weight(1f)
                    )
                    MethodToggleChip(
                        label = "Create Account",
                        selected = isSignUp,
                        onClick = { isSignUp = true; errorMsg = null },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedButton(
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        errorMsg = null
                        val authUrl = SupabaseAuthClient.startGoogleOAuthUrl(context)
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
                            infoMsg = "Finish signing in with Google in your browser to return automatically."
                        }.onFailure {
                            errorMsg = "Unable to open browser for Google sign-in"
                        }
                    }
                ) {
                    Text("Continue with Google", fontWeight = FontWeight.SemiBold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text("OR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMsg = null },
                    label = { Text("Email address") },
                    placeholder = { Text("you@gmail.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMsg = null },
                    label = { Text("Password (min 6 chars)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isSignUp) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            enabled = !isBusy,
                            onClick = {
                                val targetEmail = email.trim()
                                if (!targetEmail.contains("@")) {
                                    errorMsg = "Enter your email above first, then tap Forgot Password"
                                    return@TextButton
                                }
                                isBusy = true
                                errorMsg = null
                                scope.launch {
                                    SupabaseAuthClient.sendPasswordReset(targetEmail)
                                        .onSuccess {
                                            infoMsg = "Password reset link sent to $targetEmail"
                                        }
                                        .onFailure { err ->
                                            errorMsg = err.message ?: "Unable to send reset email"
                                        }
                                    isBusy = false
                                }
                            }
                        ) {
                            Text("Forgot Password?")
                        }
                    }
                }

                infoMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, enabled = !isBusy) {
                        Text("Close")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = !isBusy,
                        onClick = {
                            isBusy = true
                            errorMsg = null
                            infoMsg = null
                            scope.launch {
                                if (isSignUp) {
                                    SupabaseAuthClient.signUpWithEmail(context, email, password)
                                        .onSuccess { outcome ->
                                            when (outcome) {
                                                is SignUpOutcome.SignedIn -> {
                                                    onSignedIn()
                                                }
                                                is SignUpOutcome.ConfirmationRequired -> {
                                                    infoMsg = "Verification email sent to ${outcome.email}. Confirm your inbox link, then Sign In."
                                                    isSignUp = false
                                                }
                                            }
                                        }
                                        .onFailure { err ->
                                            errorMsg = err.message ?: "Sign-up failed"
                                        }
                                } else {
                                    SupabaseAuthClient.signInWithEmail(context, email, password)
                                        .onSuccess {
                                            onSignedIn()
                                        }
                                        .onFailure { err ->
                                            errorMsg = err.message ?: "Sign-in failed"
                                        }
                                }
                                isBusy = false
                            }
                        }
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .height(16.dp)
                                    .width(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(if (isSignUp) "Create Account" else "Sign In")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MethodToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = fg
        )
    }
}
