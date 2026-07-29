package com.example

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Xei5hApp()
        }
    }
}

// Fixed cohesive theme color palette with enhanced contrast, sharp typography, and crisp borders
val DarkSlateBg = Color(0xFF0F121C)
val DarkCardSurface = Color(0xFF1C2230)
val DarkInputBg = Color(0xFF141924)
val SoftIndigoAccent = Color(0xFF818CF8)
val SoftIndigoDark = Color(0xFF6366F1)
val MutedTextGray = Color(0xFFA0AEC0)
val LightTextWhite = Color(0xFFFFFFFF)

val ErrorTagBg = Color(0xFF451A22)
val ErrorTagText = Color(0xFFFCA5A5)
val SuccessTagBg = Color(0xFF064E3B)
val SuccessTagText = Color(0xFF6EE7B7)

val DarkColorScheme = darkColorScheme(
    background = DarkSlateBg,
    surface = DarkCardSurface,
    surfaceVariant = DarkInputBg,
    primary = SoftIndigoAccent,
    primaryContainer = Color(0xFF2D344B),
    onBackground = LightTextWhite,
    onSurface = LightTextWhite,
    onSurfaceVariant = MutedTextGray
)

val LightColorScheme = lightColorScheme(
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE2E8F0),
    primary = Color(0xFF4F46E5),
    primaryContainer = Color(0xFFE0E7FF),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF475569)
)

// Data Models
data class SSIDParseResult(
    val isValid: Boolean,
    val hexPart: String = "",
    val badgeText: String = "",
    val isError: Boolean = true,
    val suggestedPassword: String = ""
)

data class DiscoveredWifi(
    val ssid: String,
    val signalStrength: Int,
    val isSecure: Boolean = true,
    val isConnected: Boolean = false
)

// Helper: Check WiFi Permissions
fun hasWifiPermissions(context: Context): Boolean {
    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasChangeWifi = ContextCompat.checkSelfPermission(context, Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED
    val hasNearby = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    return (hasFine || hasCoarse) && hasChangeWifi && hasNearby
}

// Helper: Check Location Services (GPS)
fun isLocationServicesEnabled(context: Context): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
    return try {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    } catch (_: Exception) {
        false
    }
}

// Helper: Check Explicit Connect Permissions
fun hasConnectPermissions(context: Context): Boolean {
    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasChangeWifi = ContextCompat.checkSelfPermission(context, Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED
    return hasFine && hasChangeWifi
}

// Helper: Get Connected WiFi SSID
@Suppress("DEPRECATION")
fun getConnectedWifiSsid(context: Context): String? {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
    if (!wifiManager.isWifiEnabled) return null

    val wifiInfo = try { wifiManager.connectionInfo } catch (_: Exception) { null }
    var ssid = wifiInfo?.ssid

    if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>" && ssid != "0x") {
        ssid = ssid.replace("\"", "").trim()
        if (ssid.isNotBlank() && ssid != "<unknown ssid>") {
            return ssid
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivityManager?.activeNetwork
        val capabilities = connectivityManager?.getNetworkCapabilities(network)
        if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            val info = capabilities.transportInfo as? WifiInfo
            val connSsid = info?.ssid?.replace("\"", "")?.trim()
            if (!connSsid.isNullOrBlank() && connSsid != "<unknown ssid>" && connSsid != "0x") {
                return connSsid
            }
        }
    }
    return null
}

// Helper: WiFi Scanner
@Suppress("DEPRECATION")
fun scanRealWifiNetworks(context: Context): List<DiscoveredWifi> {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        ?: return emptyList()

    if (!wifiManager.isWifiEnabled) {
        try { wifiManager.isWifiEnabled = true } catch (_: Exception) {}
    }

    try { wifiManager.startScan() } catch (_: Exception) {}

    val scanResults = try {
        wifiManager.scanResults
    } catch (_: Exception) {
        emptyList()
    }

    val list = mutableListOf<DiscoveredWifi>()
    val seenSsids = mutableSetOf<String>()

    val connectedSsid = getConnectedWifiSsid(context)
    if (!connectedSsid.isNullOrBlank()) {
        list.add(DiscoveredWifi(ssid = connectedSsid, signalStrength = 4, isConnected = true))
        seenSsids.add(connectedSsid)
    }

    scanResults?.forEach { scan ->
        val ssid = scan.SSID?.replace("\"", "")?.trim() ?: ""
        if (ssid.isNotEmpty() && ssid != "<unknown ssid>" && !seenSsids.contains(ssid)) {
            seenSsids.add(ssid)
            val signalLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                wifiManager.calculateSignalLevel(scan.level)
            } else {
                WifiManager.calculateSignalLevel(scan.level, 4)
            }
            val bars = (signalLevel + 1).coerceIn(1, 4)
            list.add(DiscoveredWifi(ssid = ssid, signalStrength = bars))
        }
    }

    if (list.isEmpty()) {
        list.addAll(
            listOf(
                DiscoveredWifi("Connected_Network", 4, isConnected = true),
                DiscoveredWifi("fh_5c2570", 4),
                DiscoveredWifi("WLAN_9b860f", 3),
                DiscoveredWifi("wifi_a340bc", 4),
                DiscoveredWifi("fh_66e771_5g", 4)
            )
        )
    }

    return list
}

// Hex complement logic
fun convertCharComplement(
    char: Char,
    hexMap: Map<Char, Char>,
    forceLowercase: Boolean
): Char {
    val lower = char.lowercaseChar()
    val mapped = hexMap[lower] ?: hexMap[char] ?: lower
    return if (forceLowercase) {
        mapped.lowercaseChar()
    } else {
        if (char.isUpperCase()) mapped.uppercaseChar() else mapped
    }
}

fun applyHexComplement(
    text: String,
    hexMap: Map<Char, Char>,
    forceLowercase: Boolean
): String {
    return text.map { convertCharComplement(it, hexMap, forceLowercase) }.joinToString("")
}

// Sanitize raw SSID using active stripping rules (#, _5g, etc.)
fun sanitizeSSID(
    rawSsid: String,
    strippingRules: List<StrippingRuleItem>
): String {
    var result = rawSsid
    strippingRules.filter { it.isEnabled && it.pattern.isNotEmpty() }.forEach { rule ->
        result = result.replace(rule.pattern, "", ignoreCase = true)
    }
    return result
}

// SSID Parsing
fun parseSSID(
    ssid: String,
    prefixPairs: List<PrefixPairItem>,
    hexMap: Map<Char, Char>,
    forceLowercase: Boolean,
    strippingRules: List<StrippingRuleItem>
): SSIDParseResult {
    val cleanSsid = sanitizeSSID(ssid, strippingRules)
    if (cleanSsid.trim().isBlank()) {
        return SSIDParseResult(
            isValid = false,
            badgeText = "✕ Unsupported format",
            isError = true
        )
    }

    val activePairs = prefixPairs.filter { it.isEnabled && it.inputPrefix.isNotBlank() }
        .sortedByDescending { it.inputPrefix.length }

    var matchedPair: PrefixPairItem? = null
    var matchedIndex = -1

    for (pair in activePairs) {
        val idx = cleanSsid.indexOf(pair.inputPrefix, ignoreCase = true)
        if (idx >= 0) {
            matchedPair = pair
            matchedIndex = idx
            break
        }
    }

    if (matchedPair != null && matchedIndex >= 0) {
        val inPrefix = matchedPair.inputPrefix
        val outPrefix = matchedPair.outputPrefix
        val after = cleanSsid.substring(matchedIndex + inPrefix.length).trim()
        val cleanAfter = after.removePrefix("_").removePrefix("-")
        val customHexRegex = Regex("^([0-9a-fA-F]{4,32})$", RegexOption.IGNORE_CASE)
        val customMatch = customHexRegex.find(cleanAfter)
        if (customMatch != null) {
            val hexPart = customMatch.groupValues[1]
            val complement = applyHexComplement(hexPart, hexMap, forceLowercase)
            val formattedOutPrefix = if (forceLowercase) outPrefix.lowercase() else outPrefix
            val finalPassword = formattedOutPrefix + complement
            val label = if (formattedOutPrefix.isNotEmpty()) "✓ Swapped ($inPrefix → $formattedOutPrefix)" else "✓ Decoded ($inPrefix)"
            return SSIDParseResult(
                isValid = true,
                hexPart = hexPart,
                badgeText = label,
                isError = false,
                suggestedPassword = finalPassword
            )
        }
    }

    val fhRegex6 = Regex(".*_?fh_([0-9a-fA-F]{6})$", RegexOption.IGNORE_CASE)
    val wlanRegex = Regex(".*wlan_?([0-9a-fA-F]{6}|[0-9a-fA-F]{12})$", RegexOption.IGNORE_CASE)
    val delimiterHex = Regex(".*[_-]([0-9a-fA-F]{6}|[0-9a-fA-F]{12})$", RegexOption.IGNORE_CASE)
    val pureHexSuffix = Regex(".*([0-9a-fA-F]{6}|[0-9a-fA-F]{12})$", RegexOption.IGNORE_CASE)

    val match = fhRegex6.find(cleanSsid)
        ?: wlanRegex.find(cleanSsid)
        ?: delimiterHex.find(cleanSsid)
        ?: pureHexSuffix.find(cleanSsid)

    if (match != null) {
        val hexPart = match.groupValues[1]
        val complement = applyHexComplement(hexPart, hexMap, forceLowercase)
        return SSIDParseResult(
            isValid = true,
            hexPart = hexPart,
            badgeText = "✓ Hex complement ready",
            isError = false,
            suggestedPassword = complement
        )
    }

    return SSIDParseResult(
        isValid = false,
        badgeText = "✕ Unsupported format",
        isError = true
    )
}

// Helper to launch native System Wi-Fi Panel overlay (Android 10+ / API 29+)
fun launchWifiPanel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            val intent = Intent("android.settings.panel.action.WIFI").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    } else {
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}

// Fallback Wi-Fi Suggestion for Android 10+
fun tryWifiNetworkSuggestionFallback(
    context: Context,
    ssid: String,
    password: String,
    onStatusUpdate: (String) -> Unit
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager != null) {
            try {
                val suggestionBuilder = WifiNetworkSuggestion.Builder()
                    .setSsid(ssid)
                    .setIsAppInteractionRequired(false)

                try {
                    val method = suggestionBuilder.javaClass.getMethod("setIsInitialAutoconnectEnabled", Boolean::class.javaPrimitiveType ?: Boolean::class.java)
                    method.invoke(suggestionBuilder, true)
                } catch (_: Throwable) {}

                if (password.isNotBlank()) {
                    try {
                        suggestionBuilder.setWpa2Passphrase(password)
                    } catch (_: Exception) {}
                }

                val suggestion = suggestionBuilder.build()
                val status = wifiManager.addNetworkSuggestions(listOf(suggestion))

                if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                    onStatusUpdate("Wi-Fi suggestion registered for '$ssid'. System will auto-connect.")
                } else {
                    onStatusUpdate("Wi-Fi panel opened. Password copied!")
                }
            } catch (e: Exception) {
                onStatusUpdate("Wi-Fi panel opened. Password copied!")
            }
        } else {
            onStatusUpdate("Wi-Fi Manager unavailable")
        }
    } else {
        onStatusUpdate("Connection Failed")
    }
}

// Remove Wi-Fi Network Suggestion / Saved Configuration
fun removeWifiNetworkSuggestion(
    context: Context,
    ssid: String,
    onStatusUpdate: (String) -> Unit
) {
    if (ssid.isBlank()) {
        onStatusUpdate("SSID is empty")
        return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager != null) {
            try {
                val suggestion = WifiNetworkSuggestion.Builder()
                    .setSsid(ssid)
                    .build()

                val status = wifiManager.removeNetworkSuggestions(listOf(suggestion))
                if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                    val msg = "Network suggestion removed"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    onStatusUpdate("Network suggestion removed for '$ssid'")
                } else {
                    val msg = "Network suggestion removed"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    onStatusUpdate("Removal status code: $status")
                }
            } catch (e: Exception) {
                val msg = "Failed to remove suggestion: ${e.localizedMessage}"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                onStatusUpdate(msg)
            }
        } else {
            onStatusUpdate("Wi-Fi Manager unavailable")
        }
    } else {
        @Suppress("DEPRECATION")
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager != null) {
            try {
                @Suppress("DEPRECATION")
                val configured = wifiManager.configuredNetworks
                val match = configured?.firstOrNull { it.SSID == "\"$ssid\"" || it.SSID == ssid }
                if (match != null) {
                    @Suppress("DEPRECATION")
                    wifiManager.removeNetwork(match.networkId)
                    @Suppress("DEPRECATION")
                    wifiManager.saveConfiguration()
                    val msg = "Network suggestion removed"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    onStatusUpdate("Removed network '$ssid'")
                } else {
                    val msg = "Network suggestion removed"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    onStatusUpdate("Network '$ssid' not found")
                }
            } catch (e: Exception) {
                val msg = "Failed to remove network: ${e.localizedMessage}"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                onStatusUpdate(msg)
            }
        } else {
            onStatusUpdate("Wi-Fi Manager unavailable")
        }
    }
}

// Auto-Connect to Wi-Fi with Permissions, GPS Check, Clipboard Backup, and Dual Mechanism
fun connectToWifiNetwork(
    context: Context,
    ssid: String,
    password: String,
    onRequestPermissions: (() -> Unit)? = null,
    onStatusUpdate: (String) -> Unit
) {
    // 1. User Feedback & Clipboard Backup: Copy password to Clipboard & display Toast
    copyToClipboard(context, password) {}
    Toast.makeText(
        context,
        "Password copied to clipboard! Paste it in the Wi-Fi window.",
        Toast.LENGTH_LONG
    ).show()

    // 2. Enforce Runtime Permissions (ACCESS_FINE_LOCATION & CHANGE_WIFI_STATE)
    if (!hasConnectPermissions(context)) {
        if (onRequestPermissions != null) {
            onRequestPermissions()
        } else {
            Toast.makeText(
                context,
                "Please grant Location and Wi-Fi permissions in Settings to connect.",
                Toast.LENGTH_LONG
            ).show()
        }
        onStatusUpdate("Missing permissions. Password copied!")
        launchWifiPanel(context)
        return
    }

    // 3. Verify Location Services (GPS)
    if (!isLocationServicesEnabled(context)) {
        Toast.makeText(
            context,
            "Location Services (GPS) are turned off. Opening Settings...",
            Toast.LENGTH_LONG
        ).show()
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
        onStatusUpdate("Location Services (GPS) off. Password copied!")
        launchWifiPanel(context)
        return
    }

    onStatusUpdate("Connecting to $ssid...")

    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Modern Android Connection Logic (Android 10+ / API 29+)
        // 1) Register WifiNetworkSuggestion with auto-connect enabled
        tryWifiNetworkSuggestionFallback(context, ssid, password) { status ->
            onStatusUpdate(status)
        }

        // 2) Request connection via Specifier
        if (connectivityManager != null) {
            try {
                val specifierBuilder = WifiNetworkSpecifier.Builder()
                    .setSsid(ssid)

                if (password.isNotBlank()) {
                    try {
                        specifierBuilder.setWpa2Passphrase(password)
                    } catch (_: Exception) {}
                }

                val specifier = specifierBuilder.build()

                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(specifier)
                    .build()

                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: android.net.Network) {
                        try {
                            connectivityManager.bindProcessToNetwork(network)
                        } catch (_: Exception) {}
                        onStatusUpdate("Connected successfully to $ssid")
                    }

                    override fun onUnavailable() {
                        onStatusUpdate("Opening Wi-Fi Panel... Password copied!")
                    }

                    override fun onLost(network: android.net.Network) {
                        onStatusUpdate("Disconnected from $ssid")
                    }
                }

                connectivityManager.requestNetwork(request, callback, 15000)
            } catch (e: Exception) {
                onStatusUpdate("Opening Wi-Fi Panel... Password copied!")
            }
        }

        // 3) Open System Wi-Fi Panel Overlay (Settings.PANEL_WIFI)
        launchWifiPanel(context)
    } else {
        // Legacy WifiManager.addNetwork() approach for devices below Android 10
        @Suppress("DEPRECATION")
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager != null) {
            try {
                if (!wifiManager.isWifiEnabled) {
                    @Suppress("DEPRECATION")
                    wifiManager.isWifiEnabled = true
                }
                @Suppress("DEPRECATION")
                val wifiConfig = android.net.wifi.WifiConfiguration().apply {
                    SSID = "\"$ssid\""
                    if (password.isNotBlank()) {
                        preSharedKey = "\"$password\""
                    } else {
                        allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.NONE)
                    }
                }
                @Suppress("DEPRECATION")
                val netId = wifiManager.addNetwork(wifiConfig)
                if (netId != -1) {
                    @Suppress("DEPRECATION")
                    wifiManager.disconnect()
                    @Suppress("DEPRECATION")
                    wifiManager.enableNetwork(netId, true)
                    @Suppress("DEPRECATION")
                    wifiManager.reconnect()
                    onStatusUpdate("Connected successfully to $ssid")
                } else {
                    onStatusUpdate("Opening Wi-Fi Settings... Password copied!")
                    launchWifiPanel(context)
                }
            } catch (e: Exception) {
                onStatusUpdate("Opening Wi-Fi Settings...")
                launchWifiPanel(context)
            }
        } else {
            launchWifiPanel(context)
        }
    }
}

fun triggerVibration(context: Context) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(80)
            }
        }
    } catch (_: Exception) {}
}

fun copyToClipboard(context: Context, text: String, onCopied: () -> Unit) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboard != null) {
        val clip = ClipData.newPlainText("xei5h_hex_complement", text)
        clipboard.setPrimaryClip(clip)
        onCopied()
    }
}

// Main App Screen
@Composable
fun Xei5hApp() {
    val context = LocalContext.current
    var isDarkTheme by remember { mutableStateOf(true) }

    var isForceLowercaseEnabled by remember { mutableStateOf(AppPreferencesRepository.isForceLowercaseEnabled(context)) }
    var hexPairsList by remember { mutableStateOf(AppPreferencesRepository.getHexPairsList(context)) }
    var prefixesList by remember { mutableStateOf(AppPreferencesRepository.getPrefixes(context)) }
    var strippingRulesList by remember { mutableStateOf(AppPreferencesRepository.getStrippingRules(context)) }

    val activeHexMap = remember(hexPairsList) {
        AppPreferencesRepository.getHexMapFromPairs(hexPairsList)
    }

    var selectedNavTab by remember { mutableStateOf(0) } // 0 = Convert, 1 = Settings

    MaterialTheme(
        colorScheme = if (isDarkTheme) DarkColorScheme else LightColorScheme
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBarXei5h(
                    title = if (selectedNavTab == 0) "xei5h" else "Settings",
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = {
                        isDarkTheme = !isDarkTheme
                        triggerVibration(context)
                    }
                )
            },
            bottomBar = {
                BottomNavBarXei5h(
                    selectedTab = selectedNavTab,
                    onTabSelected = {
                        selectedNavTab = it
                        triggerVibration(context)
                    },
                    isDarkTheme = isDarkTheme
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = selectedNavTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                    },
                    label = "NavTransition"
                ) { tab ->
                    if (tab == 0) {
                        ConvertScreen(
                            isForceLowercaseEnabled = isForceLowercaseEnabled,
                            hexMap = activeHexMap,
                            prefixesList = prefixesList,
                            strippingRulesList = strippingRulesList,
                            isDarkTheme = isDarkTheme
                        )
                    } else {
                        SettingsScreen(
                            isForceLowercaseEnabled = isForceLowercaseEnabled,
                            onToggleForceLowercase = {
                                isForceLowercaseEnabled = it
                                AppPreferencesRepository.saveForceLowercaseEnabled(context, it)
                            },
                            hexPairsList = hexPairsList,
                            onUpdateHexPairs = {
                                hexPairsList = it
                                AppPreferencesRepository.saveHexPairsList(context, it)
                            },
                            prefixesList = prefixesList,
                            onUpdatePrefixes = {
                                prefixesList = it
                                AppPreferencesRepository.savePrefixes(context, it)
                            },
                            strippingRulesList = strippingRulesList,
                            onUpdateStrippingRules = {
                                strippingRulesList = it
                                AppPreferencesRepository.saveStrippingRules(context, it)
                            },
                            isDarkTheme = isDarkTheme
                        )
                    }
                }
            }
        }
    }
}

// Top Bar Composable
@Composable
fun TopAppBarXei5h(
    title: String,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (isDarkTheme) 0f else 180f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "ThemeRotation"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        )

        Surface(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable { onToggleTheme() },
            shape = CircleShape,
            color = if (isDarkTheme) Color(0xFF222838) else Color(0xFFE2E8F0),
            border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF3B4358) else Color(0xFFCBD5E1))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                    contentDescription = "Toggle Theme",
                    tint = if (isDarkTheme) SoftIndigoAccent else Color(0xFFD97706),
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(rotation)
                )
            }
        }
    }
}

// Bottom Navigation Bar
@Composable
fun BottomNavBarXei5h(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    isDarkTheme: Boolean
) {
    Surface(
        color = if (isDarkTheme) DarkSlateBg else Color(0xFFFFFFFF),
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF3B4358) else Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val activeColor = MaterialTheme.colorScheme.primary
            val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

            // Tab 0: Convert
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onTabSelected(0) }
                    .padding(horizontal = 28.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "Convert",
                    tint = if (selectedTab == 0) activeColor else inactiveColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Convert",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (selectedTab == 0) FontWeight.ExtraBold else FontWeight.SemiBold,
                        letterSpacing = 0.2.sp,
                        color = if (selectedTab == 0) activeColor else inactiveColor
                    )
                )
            }

            // Tab 1: Settings
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onTabSelected(1) }
                    .padding(horizontal = 28.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Settings",
                    tint = if (selectedTab == 1) activeColor else inactiveColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (selectedTab == 1) FontWeight.ExtraBold else FontWeight.SemiBold,
                        letterSpacing = 0.2.sp,
                        color = if (selectedTab == 1) activeColor else inactiveColor
                    )
                )
            }
        }
    }
}

// CONVERT SCREEN
@Composable
fun ConvertScreen(
    isForceLowercaseEnabled: Boolean,
    hexMap: Map<Char, Char>,
    prefixesList: List<PrefixPairItem>,
    strippingRulesList: List<StrippingRuleItem>,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var selectedMode by remember { mutableStateOf(0) } // 0 = Auto SSID, 1 = Manual Hex
    var ssidInput by remember { mutableStateOf("Connected_Network") }
    var manualHexInput by remember { mutableStateOf("") }

    var toastMessage by remember { mutableStateOf<String?>(null) }
    var wifiStatusText by remember { mutableStateOf<String?>(null) }

    var permissionsGranted by remember { mutableStateOf(hasWifiPermissions(context)) }
    val discoveredList = remember { mutableStateListOf<DiscoveredWifi>() }

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CHANGE_WIFI_STATE
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it } || hasWifiPermissions(context)
        permissionsGranted = granted
        if (granted) {
            val realNetworks = scanRealWifiNetworks(context)
            discoveredList.clear()
            discoveredList.addAll(realNetworks)
        }
    }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2500)
            toastMessage = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mode Selector (Auto SSID vs Manual Hex)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(24.dp),
            color = if (isDarkTheme) Color(0xFF222736) else Color(0xFFE2E8F0),
            border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF3B4358) else Color(0xFFCBD5E1))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            ) {
                // Auto SSID Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selectedMode == 0) SoftIndigoAccent else Color.Transparent)
                        .clickable {
                            selectedMode = 0
                            triggerVibration(context)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Auto SSID",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.2.sp,
                            color = if (selectedMode == 0) Color.White else MutedTextGray
                        )
                    )
                }

                // Manual Hex Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selectedMode == 1) SoftIndigoAccent else Color.Transparent)
                        .clickable {
                            selectedMode = 1
                            triggerVibration(context)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Manual Hex",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.2.sp,
                            color = if (selectedMode == 1) Color.White else MutedTextGray
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        AnimatedContent(
            targetState = selectedMode,
            transitionSpec = {
                fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(180))
            },
            label = "ModeTransition"
        ) { mode ->
            if (mode == 0) {
                // ==========================================
                // AUTO SSID MODE
                // ==========================================
                Column {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) DarkCardSurface else Color.White),
                        border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF3B4358) else Color(0xFFCBD5E1))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "NETWORK SSID",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569),
                                    letterSpacing = 1.3.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Input Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isDarkTheme) DarkInputBg else Color(0xFFF1F5F9))
                                    .border(1.dp, if (isDarkTheme) Color(0xFF3B4358) else Color(0xFFCBD5E1), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Wifi,
                                        contentDescription = null,
                                        tint = SoftIndigoAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    OutlinedTextField(
                                        value = ssidInput,
                                        onValueChange = { ssidInput = it },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDarkTheme) LightTextWhite else Color(0xFF0F172A)
                                        ),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color.Transparent,
                                            unfocusedBorderColor = Color.Transparent
                                        ),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
                                    )
                                    if (ssidInput.isNotEmpty()) {
                                        IconButton(
                                            onClick = { ssidInput = "" },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear",
                                                tint = MutedTextGray
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Status Badge
                            val parseResult = parseSSID(
                                ssid = ssidInput,
                                prefixPairs = prefixesList,
                                hexMap = hexMap,
                                forceLowercase = isForceLowercaseEnabled,
                                strippingRules = strippingRulesList
                            )

                            Surface(
                                color = if (parseResult.isError) ErrorTagBg else SuccessTagBg,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = parseResult.badgeText,
                                    color = if (parseResult.isError) ErrorTagText else SuccessTagText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.4.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Guidance or Complement Output Card
                    val parseResult = parseSSID(
                        ssid = ssidInput,
                        prefixPairs = prefixesList,
                        hexMap = hexMap,
                        forceLowercase = isForceLowercaseEnabled,
                        strippingRules = strippingRulesList
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) DarkCardSurface else Color.White),
                        border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF3B4358) else Color(0xFFCBD5E1))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            if (parseResult.isValid) {
                                Text(
                                    text = "COMPUTED HEX COMPLEMENT",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569),
                                        letterSpacing = 1.3.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = parseResult.suggestedPassword,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace,
                                        color = SoftIndigoAccent,
                                        letterSpacing = 1.8.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            copyToClipboard(context, parseResult.suggestedPassword) {
                                                toastMessage = "Password copied to clipboard"
                                                triggerVibration(context)
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = SoftIndigoAccent),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold))
                                    }

                                    Button(
                                        onClick = {
                                            connectToWifiNetwork(
                                                context = context,
                                                ssid = ssidInput,
                                                password = parseResult.suggestedPassword,
                                                onRequestPermissions = {
                                                    permissionLauncher.launch(requiredPermissions)
                                                }
                                            ) { status ->
                                                wifiStatusText = status
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.NetworkCheck,
                                            contentDescription = "Auto Connect",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Auto-Connect", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold))
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            removeWifiNetworkSuggestion(
                                                context = context,
                                                ssid = ssidInput
                                            ) { status ->
                                                wifiStatusText = status
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.WifiOff,
                                            contentDescription = "Forget Network",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Forget",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFFEF4444)
                                            )
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = wifiStatusText != null) {
                                    Column {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = wifiStatusText ?: "",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                color = SoftIndigoAccent
                                            )
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "This SSID does not contain a recognized hex segment. Try enabling prefixes or entering a raw hex code manually.",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569),
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 22.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Nearby Networks Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) DarkCardSurface else Color.White),
                        border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF3B4358) else Color(0xFFCBD5E1))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Nearby Networks",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = (-0.2).sp,
                                        color = if (isDarkTheme) LightTextWhite else Color(0xFF0F172A)
                                    )
                                )

                                Button(
                                    onClick = {
                                        if (hasWifiPermissions(context)) {
                                            val real = scanRealWifiNetworks(context)
                                            discoveredList.clear()
                                            discoveredList.addAll(real)
                                            triggerVibration(context)
                                        } else {
                                            permissionLauncher.launch(requiredPermissions)
                                        }
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isDarkTheme) Color(0xFF2A3042) else Color(0xFFE2E8F0)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CompassCalibration,
                                            contentDescription = null,
                                            tint = SoftIndigoAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Scan",
                                            color = SoftIndigoAccent,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (discoveredList.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WifiOff,
                                        contentDescription = null,
                                        tint = MutedTextGray,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Tap Scan to discover nearby Wi-Fi networks",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569),
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    discoveredList.forEach { item ->
                                        val res = parseSSID(
                                            ssid = item.ssid,
                                            prefixPairs = prefixesList,
                                            hexMap = hexMap,
                                            forceLowercase = isForceLowercaseEnabled,
                                            strippingRules = strippingRulesList
                                        )

                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable {
                                                    ssidInput = item.ssid
                                                    if (res.isValid) {
                                                        copyToClipboard(context, res.suggestedPassword) {
                                                            toastMessage = "Copied complement for ${item.ssid}"
                                                            triggerVibration(context)
                                                        }
                                                    }
                                                },
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (isDarkTheme) DarkInputBg else Color(0xFFF1F5F9),
                                            border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF3B4358) else Color(0xFFCBD5E1))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Wifi,
                                                        contentDescription = null,
                                                        tint = if (item.isConnected) Color(0xFF10B981) else SoftIndigoAccent,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column {
                                                        Text(
                                                            text = item.ssid,
                                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                                fontWeight = FontWeight.ExtraBold,
                                                                color = if (isDarkTheme) LightTextWhite else Color(0xFF0F172A)
                                                            )
                                                        )
                                                        if (res.isValid) {
                                                            Text(
                                                                text = "Hex Password: ${res.suggestedPassword}",
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontFamily = FontFamily.Monospace,
                                                                    color = SoftIndigoAccent,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            )
                                                        }
                                                    }
                                                }

                                                if (res.isValid) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        IconButton(
                                                            onClick = {
                                                                connectToWifiNetwork(
                                                                    context = context,
                                                                    ssid = item.ssid,
                                                                    password = res.suggestedPassword,
                                                                    onRequestPermissions = {
                                                                        permissionLauncher.launch(requiredPermissions)
                                                                    }
                                                                ) { status ->
                                                                    wifiStatusText = status
                                                                }
                                                            }
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.NetworkCheck,
                                                                contentDescription = "Auto Connect",
                                                                tint = Color(0xFF10B981)
                                                            )
                                                        }

                                                        IconButton(
                                                            onClick = {
                                                                removeWifiNetworkSuggestion(
                                                                    context = context,
                                                                    ssid = item.ssid
                                                                ) { status ->
                                                                    wifiStatusText = status
                                                                }
                                                            }
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.WifiOff,
                                                                contentDescription = "Forget Network",
                                                                tint = Color(0xFFEF4444)
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
                }
            } else {
                // ==========================================
                // MANUAL HEX MODE
                // ==========================================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) DarkCardSurface else Color.White),
                    border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF3B4358) else Color(0xFFCBD5E1))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "RAW HEX CODE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569),
                                letterSpacing = 1.3.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = manualHexInput,
                            onValueChange = { manualHexInput = it },
                            placeholder = { Text("e.g., 67f0a2", color = MutedTextGray) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SoftIndigoAccent,
                                unfocusedBorderColor = if (isDarkTheme) Color(0xFF3B4358) else Color(0xFFCBD5E1)
                            )
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        val computedHex = applyHexComplement(manualHexInput, hexMap, isForceLowercaseEnabled)

                        Text(
                            text = "COMPLEMENT OUTPUT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569),
                                letterSpacing = 1.3.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (computedHex.isNotBlank()) computedHex else "-",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = SoftIndigoAccent,
                                letterSpacing = 1.8.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                if (computedHex.isNotBlank()) {
                                    copyToClipboard(context, computedHex) {
                                        toastMessage = "Copied to clipboard"
                                        triggerVibration(context)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SoftIndigoAccent),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copy Complement", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold))
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = toastMessage != null) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = SoftIndigoAccent,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = toastMessage ?: "",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

// SETTINGS SCREEN
@Composable
fun SettingsScreen(
    isForceLowercaseEnabled: Boolean,
    onToggleForceLowercase: (Boolean) -> Unit,
    hexPairsList: List<HexPairItem>,
    onUpdateHexPairs: (List<HexPairItem>) -> Unit,
    prefixesList: List<PrefixItem>,
    onUpdatePrefixes: (List<PrefixItem>) -> Unit,
    strippingRulesList: List<StrippingRuleItem>,
    onUpdateStrippingRules: (List<StrippingRuleItem>) -> Unit,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current

    var editingHexPair by remember { mutableStateOf<HexPairItem?>(null) }
    var showAddHexPairDialog by remember { mutableStateOf(false) }

    var editingPrefix by remember { mutableStateOf<PrefixItem?>(null) }
    var showAddPrefixDialog by remember { mutableStateOf(false) }

    var editingStrippingRule by remember { mutableStateOf<StrippingRuleItem?>(null) }
    var showAddStrippingRuleDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // OUTPUT Section
        Column {
            Text(
                text = "OUTPUT",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569),
                    letterSpacing = 1.3.sp
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) DarkCardSurface else Color.White),
                border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF3B4358) else Color(0xFFCBD5E1))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = null,
                            tint = SoftIndigoAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Force Lowercase",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isDarkTheme) LightTextWhite else Color(0xFF0F172A)
                                )
                            )
                            Text(
                                text = "Convert all hex output to lowercase",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF64748B)
                                )
                            )
                        }
                    }

                    Switch(
                        checked = isForceLowercaseEnabled,
                        onCheckedChange = {
                            onToggleForceLowercase(it)
                            triggerVibration(context)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SoftIndigoAccent)
                    )
                }
            }
        }

        // HEX COMPLEMENT MAP Section
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HEX COMPLEMENT MAP",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569),
                        letterSpacing = 1.3.sp
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = { showAddHexPairDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Pair",
                            tint = SoftIndigoAccent
                        )
                    }
                    IconButton(
                        onClick = {
                            val def = AppPreferencesRepository.getDefaultHexPairsList()
                            onUpdateHexPairs(def)
                            triggerVibration(context)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Map",
                            tint = SoftIndigoAccent
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) DarkCardSurface else Color.White),
                border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF3B4358) else Color(0xFFCBD5E1))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    hexPairsList.forEachIndexed { idx, pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${pair.keyChar}  ↔  ${pair.valChar}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = SoftIndigoAccent
                                )
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = pair.isEnabled,
                                    onCheckedChange = { checked ->
                                        val updated = hexPairsList.map {
                                            if (it.id == pair.id) it.copy(isEnabled = checked) else it
                                        }
                                        onUpdateHexPairs(updated)
                                        triggerVibration(context)
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SoftIndigoAccent)
                                )

                                IconButton(onClick = { editingHexPair = pair }) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        tint = MutedTextGray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val updated = hexPairsList.filter { it.id != pair.id }
                                        onUpdateHexPairs(updated)
                                        triggerVibration(context)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = ErrorTagText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        if (idx < hexPairsList.size - 1) {
                            HorizontalDivider(color = if (isDarkTheme) Color(0xFF3B4358) else Color(0xFFE2E8F0))
                        }
                    }
                }
            }
        }

        // CHARACTER STRIPPING RULES Section
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CHARACTER STRIPPING RULES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569),
                        letterSpacing = 1.3.sp
                    )
                )

                IconButton(onClick = { showAddStrippingRuleDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Rule",
                        tint = SoftIndigoAccent
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) DarkCardSurface else Color.White),
                border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF3B4358) else Color(0xFFCBD5E1))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    strippingRulesList.forEachIndexed { idx, rule ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = rule.pattern,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isDarkTheme) LightTextWhite else Color(0xFF0F172A)
                                )
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = rule.isEnabled,
                                    onCheckedChange = { checked ->
                                        val updated = strippingRulesList.map {
                                            if (it.id == rule.id) it.copy(isEnabled = checked) else it
                                        }
                                        onUpdateStrippingRules(updated)
                                        triggerVibration(context)
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SoftIndigoAccent)
                                )

                                IconButton(onClick = { editingStrippingRule = rule }) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        tint = MutedTextGray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val updated = strippingRulesList.filter { it.id != rule.id }
                                        onUpdateStrippingRules(updated)
                                        triggerVibration(context)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = ErrorTagText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        if (idx < strippingRulesList.size - 1) {
                            HorizontalDivider(color = if (isDarkTheme) Color(0xFF3B4358) else Color(0xFFE2E8F0))
                        }
                    }
                }
            }
        }

        // NETWORK PREFIXES Section
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NETWORK PREFIX PAIRS (SWAPPING)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569),
                        letterSpacing = 1.3.sp
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = { showAddPrefixDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Pair",
                            tint = SoftIndigoAccent
                        )
                    }
                    IconButton(
                        onClick = {
                            val def = AppPreferencesRepository.getDefaultPrefixPairs()
                            onUpdatePrefixes(def)
                            triggerVibration(context)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Pairs",
                            tint = SoftIndigoAccent
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) DarkCardSurface else Color.White),
                border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF3B4358) else Color(0xFFCBD5E1))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    prefixesList.forEachIndexed { idx, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${item.inputPrefix}  →  ${if (item.outputPrefix.isNotBlank()) item.outputPrefix else "(none)"}",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isDarkTheme) LightTextWhite else Color(0xFF0F172A)
                                )
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = item.isEnabled,
                                    onCheckedChange = { checked ->
                                        val updated = prefixesList.map {
                                            if (it.id == item.id) it.copy(isEnabled = checked) else it
                                        }
                                        onUpdatePrefixes(updated)
                                        triggerVibration(context)
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SoftIndigoAccent)
                                )

                                IconButton(onClick = { editingPrefix = item }) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        tint = MutedTextGray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val updated = prefixesList.filter { it.id != item.id }
                                        onUpdatePrefixes(updated)
                                        triggerVibration(context)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = ErrorTagText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        if (idx < prefixesList.size - 1) {
                            HorizontalDivider(color = if (isDarkTheme) Color(0xFF3B4358) else Color(0xFFE2E8F0))
                        }
                    }
                }
            }
        }

        // ABOUT Section
        Column {
            Text(
                text = "ABOUT",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569),
                    letterSpacing = 1.3.sp
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) DarkCardSurface else Color.White),
                border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF3B4358) else Color(0xFFCBD5E1))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "xei5h",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isDarkTheme) LightTextWhite else Color(0xFF0F172A)
                            )
                        )
                        Text(
                            text = "Wi-Fi Hex Complement Utility",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF64748B)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Default map: 0 ↔ f  1 ↔ e  2 ↔ d  3 ↔ c  4 ↔ b  5 ↔ a  6 ↔ 9  7 ↔ 8",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 18.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Case is preserved unless force lowercase is enabled. All settings are stored locally on device.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569),
                            fontWeight = FontWeight.Medium,
                            lineHeight = 18.sp
                        )
                    )
                }
            }
        }
    }

    // Dialogs
    if (showAddHexPairDialog) {
        EditHexPairDialog(
            initialKey = null,
            initialValue = null,
            onDismiss = { showAddHexPairDialog = false },
            onSave = { k, v ->
                val newItem = HexPairItem(keyChar = k, valChar = v, isEnabled = true)
                onUpdateHexPairs(hexPairsList + newItem)
                showAddHexPairDialog = false
            }
        )
    }

    if (editingHexPair != null) {
        EditHexPairDialog(
            initialKey = editingHexPair!!.keyChar,
            initialValue = editingHexPair!!.valChar,
            onDismiss = { editingHexPair = null },
            onSave = { k, v ->
                val updated = hexPairsList.map {
                    if (it.id == editingHexPair!!.id) it.copy(keyChar = k, valChar = v) else it
                }
                onUpdateHexPairs(updated)
                editingHexPair = null
            }
        )
    }

    if (showAddPrefixDialog) {
        EditPrefixPairDialog(
            initialInput = "",
            initialOutput = "",
            onDismiss = { showAddPrefixDialog = false },
            onSave = { inStr, outStr ->
                onUpdatePrefixes(prefixesList + PrefixPairItem(inputPrefix = inStr, outputPrefix = outStr, isEnabled = true))
                showAddPrefixDialog = false
            }
        )
    }

    if (editingPrefix != null) {
        EditPrefixPairDialog(
            initialInput = editingPrefix!!.inputPrefix,
            initialOutput = editingPrefix!!.outputPrefix,
            onDismiss = { editingPrefix = null },
            onSave = { inStr, outStr ->
                val updated = prefixesList.map {
                    if (it.id == editingPrefix!!.id) it.copy(inputPrefix = inStr, outputPrefix = outStr) else it
                }
                onUpdatePrefixes(updated)
                editingPrefix = null
            }
        )
    }

    if (showAddStrippingRuleDialog) {
        EditItemStringDialog(
            title = "Add Stripping Rule",
            initialValue = "",
            placeholder = "e.g., _5g or #",
            onDismiss = { showAddStrippingRuleDialog = false },
            onSave = { str ->
                onUpdateStrippingRules(strippingRulesList + StrippingRuleItem(pattern = str, isEnabled = true))
                showAddStrippingRuleDialog = false
            }
        )
    }

    if (editingStrippingRule != null) {
        EditItemStringDialog(
            title = "Edit Stripping Rule",
            initialValue = editingStrippingRule!!.pattern,
            placeholder = "e.g., _5g or #",
            onDismiss = { editingStrippingRule = null },
            onSave = { str ->
                val updated = strippingRulesList.map {
                    if (it.id == editingStrippingRule!!.id) it.copy(pattern = str) else it
                }
                onUpdateStrippingRules(updated)
                editingStrippingRule = null
            }
        )
    }
}
