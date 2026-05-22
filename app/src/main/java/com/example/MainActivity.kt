package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var isDarkTheme by remember { mutableStateOf(false) }
            
            val activeColorScheme = if (isDarkTheme) {
                darkColorScheme(
                    primary = Color(0xFF10B981), // Aquamarine green
                    onPrimary = Color(0xFF022F22),
                    background = Color(0xFF111827), // Deep dark
                    onBackground = Color(0xFFF3F4F6),
                    surface = Color(0xFF1F2937), // Grey-blue slate card
                    onSurface = Color(0xFFF9FAFB),
                    surfaceVariant = Color(0xFF374151),
                    onSurfaceVariant = Color(0xFFD1D5DB)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF047857), // Pure aquamarine
                    onPrimary = Color.White,
                    background = Color(0xFFF3F4F6), // Light clean grey as requested
                    onBackground = Color(0xFF1F2937), // Steel grey texts
                    surface = Color.White, // White cards as requested
                    onSurface = Color(0xFF1F2937),
                    surfaceVariant = Color(0xFFE5E7EB),
                    onSurfaceVariant = Color(0xFF374151)
                )
            }

            MaterialTheme(
                colorScheme = activeColorScheme,
                content = {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        RouterAssistantScreen(
                            isDarkTheme = isDarkTheme,
                            onToggleTheme = { isDarkTheme = !isDarkTheme }
                        )
                    }
                }
            )
        }
    }
}

// Data models for parsing
data class SSIDParseResult(
    val isValid: Boolean,
    val hexPart: String = "",
    val badgeText: String = "",
    val badgeColorType: BadgeColorType = BadgeColorType.ERROR,
    val suggestedPassword: String = ""
)

enum class BadgeColorType {
    SUCCESS,
    PROCESSING,
    ERROR
}

// Simulated Wifi Network Info
data class DiscoveredWifi(
    val ssid: String,
    val signalStrength: Int, // 1 to 4 bars
    val isSecure: Boolean = true
)

// Hex complement mapping
fun convertCharComplement(char: Char): Char {
    return when (char.lowercaseChar()) {
        '0' -> 'f'
        '1' -> 'e'
        '2' -> 'd'
        '3' -> 'c'
        '4' -> 'b'
        '5' -> 'a'
        '6' -> '9'
        '7' -> '8'
        '8' -> '7'
        '9' -> '6'
        'a' -> '5'
        'b' -> '4'
        'c' -> '3'
        'd' -> '2'
        'e' -> '1'
        'f' -> '0'
        else -> char
    }
}

fun applyHexComplement(text: String): String {
    return text.map { convertCharComplement(it) }.joinToString("")
}

// Parses general SSIDs to extract relevant hex suffix
fun parseSSID(ssid: String): SSIDParseResult {
    if (ssid.trim().isBlank()) {
        return SSIDParseResult(
            isValid = false,
            badgeText = "بانتظار إدخال اسم الشبكة 📡",
            badgeColorType = BadgeColorType.PROCESSING
        )
    }

    // RegEx checking
    val fhRegex6 = Regex(".*_?fh_([0-9a-fA-F]{6})$", RegexOption.IGNORE_CASE)
    val wlanRegex = Regex(".*wlan_?([0-9a-fA-F]{6}|[0-9a-fA-F]{12})$", RegexOption.IGNORE_CASE)
    val delimiterHex = Regex(".*[_-]([0-9a-fA-F]{6}|[0-9a-fA-F]{12})$")
    val pureHexSuffix = Regex(".*([0-9a-fA-F]{6}|[0-9a-fA-F]{12})$")

    val match = fhRegex6.find(ssid)
        ?: wlanRegex.find(ssid)
        ?: delimiterHex.find(ssid)
        ?: pureHexSuffix.find(ssid)

    if (match != null) {
        val hexPart = match.groupValues[1]
        val complement = applyHexComplement(hexPart)
        
        val badgeText = when {
            ssid.contains("fh_", ignoreCase = true) -> "قيد فك التشفير 🔓 (fh_)"
            ssid.contains("wlan", ignoreCase = true) -> "قيد فك التشفير 🔓 (wlan)"
            else -> "قيد التشفير 🔒 (مكمل)"
        }
        
        return SSIDParseResult(
            isValid = true,
            hexPart = hexPart,
            badgeText = badgeText,
            badgeColorType = BadgeColorType.SUCCESS,
            suggestedPassword = complement
        )
    }

    // Checking if contains other hex digits (typing progress)
    val hasHex = ssid.any { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
    if (hasHex && ssid.length < 5) {
        return SSIDParseResult(
            isValid = false,
            badgeText = "قيد الكتابة... ✍️",
            badgeColorType = BadgeColorType.PROCESSING
        )
    }

    return SSIDParseResult(
        isValid = false,
        badgeText = "تنسيق غير مدعوم ❌",
        badgeColorType = BadgeColorType.ERROR
    )
}

// Safe physical tactile feedback helper
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
                it.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(120)
            }
        }
    } catch (e: Exception) {
        // Fallback for isolated simulation environments
    }
}

// Copy to Clipboard helper
fun copyToClipboard(context: Context, text: String, onCopied: () -> Unit) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboard != null) {
        val clip = ClipData.newPlainText("RouterComplementPassword", text)
        clipboard.setPrimaryClip(clip)
        onCopied()
    }
}

@Composable
fun RouterAssistantScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    // Screen State
    var selectedTab by remember { mutableStateOf(0) } // 0 = Auto SSID, 1 = Manual Mode
    var ssidInput by remember { mutableStateOf("fh_5c2570") }
    var manualInput by remember { mutableStateOf("") }
    
    // Scan WiFi simulation states
    var scanLoading by remember { mutableStateOf(false) }
    var showScanResult by remember { mutableStateOf(false) }
    val discoveredList = remember {
        mutableStateListOf<DiscoveredWifi>()
    }

    // Dynamic error/shake state
    val shakeOffset = remember { Animatable(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    // Dynamic rotation angle for dark/light toggle icon
    var rotationAngle by remember { mutableStateOf(0f) }
    val animatedRotation by animateFloatAsState(
        targetValue = rotationAngle,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "ThemeRotation"
    )

    // Clear toast message after 2.5 seconds
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2500)
            toastMessage = null
        }
    }

    // Shake error trigger helper
    val triggerError = { msg: String ->
        errorMessage = msg
        triggerVibration(context)
        coroutineScope.launch {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 500
                    -25f at 50 with FastOutLinearInEasing
                    25f at 150 with LinearEasing
                    -20f at 250 with LinearEasing
                    20f at 350 with LinearEasing
                    -10f at 400 with LinearOutSlowInEasing
                    10f at 450 with LinearOutSlowInEasing
                    0f at 500
                }
            )
        }
    }

    // Main layout container (Scrollable to support all mobile screen resolutions seamlessly)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 🌟 Top Navigation Bar with Interactive Rotating Theme Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dual Brand Header
                Column(
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Router Password 🔐",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Text(
                        text = "مساعد كلمة مرور الراوتر المطور",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Rotating Orb Toggle Button (☀️/🌙)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = if (isDarkTheme) {
                                    listOf(Color(0xFF312E81), Color(0xFF1E1B4B))
                                } else {
                                    listOf(Color(0xFFFDE047), Color(0xFFF97316))
                                }
                            )
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            rotationAngle += 360f
                            onToggleTheme()
                            triggerVibration(context)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = "تبديل المظهر الليل والنهار",
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(animatedRotation)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 💳 Main Transforming Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = shakeOffset.value }
                    .shadow(
                        elevation = if (isDarkTheme) 6.dp else 12.dp,
                        shape = RoundedCornerShape(24.dp),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title Indicator
                    Text(
                        text = "تحويل مكمل السداسي عشر Hex Complement",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // 🕹️ Sliding Modes Tabs (Sliding Modes Selectors)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(4.dp)
                    ) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val totalWidth = maxWidth
                            val tabWidth = totalWidth / 2
                            
                            val slidingLeftOffset by animateDpAsState(
                                targetValue = if (selectedTab == 0) 0.dp else tabWidth,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "ModeSlide"
                            )

                            // Glassy emerald sliding pill
                            Box(
                                modifier = Modifier
                                    .offset(x = slidingLeftOffset)
                                    .width(tabWidth)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )

                            // Interactive Labels
                            Row(modifier = Modifier.fillMaxSize()) {
                                // Tab 0: SSID Auto
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            selectedTab = 0
                                            errorMessage = null
                                            triggerVibration(context)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CompassCalibration,
                                            contentDescription = null,
                                            tint = if (selectedTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "SSID تلقائي",
                                            color = if (selectedTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }

                                // Tab 1: Custom Manual Hex
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            selectedTab = 1
                                            errorMessage = null
                                            triggerVibration(context)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            tint = if (selectedTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "تخصيص يدوي",
                                            color = if (selectedTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 🔀 Animated Panel Switcher with Horizontal Sliding motion
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInHorizontally { width -> width } + fadeIn()) togetherWith
                                        (slideOutHorizontally { width -> -width } + fadeOut())
                            } else {
                                (slideInHorizontally { width -> -width } + fadeIn()) togetherWith
                                        (slideOutHorizontally { width -> width } + fadeOut())
                            }.using(
                                SizeTransform(clip = false)
                            )
                        },
                        label = "FieldsTransition"
                    ) { activeTab ->
                        if (activeTab == 0) {
                            // ==========================================
                            // 📡 SSID AUTO CONVERSION MODE PANEL
                            // ==========================================
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        text = "اسم شبكة الواي فاي (SSID):",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                        ),
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }

                                OutlinedTextField(
                                    value = ssidInput,
                                    onValueChange = {
                                        ssidInput = it
                                        errorMessage = null
                                    },
                                    placeholder = { 
                                        Text(
                                            text = "مثل: fh_5c2570 أو wlan9b86...",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            ),
                                            textAlign = TextAlign.Right
                                        ) 
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Wifi,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    trailingIcon = {
                                        if (ssidInput.isNotEmpty()) {
                                            IconButton(onClick = { ssidInput = "" }) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "امسح الحقل",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium,
                                        textDirection = TextDirection.Ltr
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        unfocusedContainerColor = Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("ssid_text_field"),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Ascii,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { keyboardController?.hide() }
                                    )
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // 🛡️ Live Badge: Dynamic, interactive system feedback with background colors
                                val parseResult = parseSSID(ssidInput)
                                val badgeBgColor = when (parseResult.badgeColorType) {
                                    BadgeColorType.SUCCESS -> Color(0xFFD1FAE5) // Soft green
                                    BadgeColorType.PROCESSING -> Color(0xFFFEF3C7) // Soft amber
                                    BadgeColorType.ERROR -> Color(0xFFFEE2E2) // Soft danger red
                                }
                                val badgeTextColor = when (parseResult.badgeColorType) {
                                    BadgeColorType.SUCCESS -> Color(0xFF065F46)
                                    BadgeColorType.PROCESSING -> Color(0xFF92400E)
                                    BadgeColorType.ERROR -> Color(0xFF991B1B)
                                }

                                AnimatedVisibility(
                                    visible = true,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Surface(
                                        color = badgeBgColor,
                                        shape = RoundedCornerShape(30.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(badgeTextColor)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = parseResult.badgeText,
                                                color = badgeTextColor,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                }

                                // Interactive success complement live visualizer
                                if (parseResult.isValid) {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 20.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "الرمز السداسي (Suffix):",
                                                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                )
                                                Text(
                                                    text = parseResult.hexPart.uppercase(),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))

                                            Spacer(modifier = Modifier.height(10.dp))

                                            Text(
                                                text = "مكمل السداسي عشر (كلمة المرور المقترحة):",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                modifier = Modifier.padding(bottom = 6.dp)
                                            )

                                            Text(
                                                text = parseResult.suggestedPassword,
                                                style = MaterialTheme.typography.headlineSmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = 24.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // ==========================================
                            // 🕹️ MANUAL TRANSFORMATION CUSTOM MODE
                            // ==========================================
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Header for manual mode with Undo (↩️) back arrow
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // ↩️ Return to Auto button
                                    TextButton(
                                        onClick = {
                                            selectedTab = 0
                                            errorMessage = null
                                            triggerVibration(context)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "العودة للوضع التلقائي",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "رجوع تلقائي",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }

                                    Text(
                                        text = "الرمز السداسي (12 رمزاً فقط):",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                        )
                                    )
                                }

                                OutlinedTextField(
                                    value = manualInput,
                                    onValueChange = { newValue ->
                                        // Restrict input perfectly to hexadecimals and maximum length of 12
                                        val filtered = newValue.uppercase().filter { it in "0123456789ABCDEF" }
                                        manualInput = if (filtered.length > 12) filtered.take(12) else filtered
                                        errorMessage = null
                                    },
                                    placeholder = {
                                        Text(
                                            text = "A1B2C3D4E5F6",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.QrCode,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    trailingIcon = {
                                        if (manualInput.isNotEmpty()) {
                                            IconButton(onClick = { manualInput = "" }) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "امسح الحقل",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    supportingText = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${manualInput.length}/12",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = if (manualInput.length == 12) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            )
                                            Text(
                                                text = "الحروف السداسية المدخلة فقط",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center,
                                        letterSpacing = 1.sp
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        unfocusedContainerColor = Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("manual_text_field"),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Ascii,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { keyboardController?.hide() }
                                    )
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Quick wrapping shortcuts below manual field: (to wlan, to fh_, without prefix)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Button 1: To wlan
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 4.dp)
                                            .clickable {
                                                if (manualInput.length == 12) {
                                                    val complement = applyHexComplement(manualInput)
                                                    val formatted = "wlan$complement"
                                                    copyToClipboard(context, formatted) {
                                                        toastMessage = "تم نسخ ببادئة wlan بنجاح! 📋"
                                                        triggerVibration(context)
                                                    }
                                                } else {
                                                    triggerError("يرجى إكمال الـ 12 رمزاً أولاً لتشغيل الاختصار!")
                                                }
                                            }
                                    ) {
                                        Text(
                                            text = "إلى wlan 🌐",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            ),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }

                                    // Button 2: To fh_
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 4.dp)
                                            .clickable {
                                                if (manualInput.length >= 6) {
                                                    // Standard fh_ takes las 6 chars of MAC/Hex complement
                                                    val cleanInput = if (manualInput.length > 6) manualInput.takeLast(6) else manualInput
                                                    val complement = applyHexComplement(cleanInput)
                                                    val formatted = "fh_$complement"
                                                    copyToClipboard(context, formatted) {
                                                        toastMessage = "تم نسخ ببادئة fh_ بنجاح! 📋"
                                                        triggerVibration(context)
                                                    }
                                                } else {
                                                    triggerError("يرجى إدخال 6 رموز على الأقل لتهيئة بادئة fh_")
                                                }
                                            }
                                    ) {
                                        Text(
                                            text = "إلى fh_ 📶",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            ),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }

                                    // Button 3: No Prefix
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 4.dp)
                                            .clickable {
                                                if (manualInput.isNotEmpty()) {
                                                    val complement = applyHexComplement(manualInput)
                                                    copyToClipboard(context, complement) {
                                                        toastMessage = "تم النسخ بدون بادئة بنجاح! 📋"
                                                        triggerVibration(context)
                                                    }
                                                } else {
                                                    triggerError("يرجى إدخال الرموز السداسية أولاً للتطبيق بنجاح")
                                                }
                                            }
                                    ) {
                                        Text(
                                            text = "بدون بادئة 🔲",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            ),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Interactive error box display
                    errorMessage?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // ⚡ Primary processing and Copy trigger Button
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            if (selectedTab == 0) {
                                // Auto SSID mode processing
                                val res = parseSSID(ssidInput)
                                if (res.isValid) {
                                    copyToClipboard(context, res.suggestedPassword) {
                                        toastMessage = "تم حساب كلمة المرور ونسخها فورا! 🥳📋"
                                        triggerVibration(context)
                                    }
                                } else {
                                    triggerError("صيغة SSID المدخلة غير مدعومة! يرجى التحقق من المدخلات")
                                }
                            } else {
                                // Manual mode processing
                                if (manualInput.length == 12) {
                                    val comp = applyHexComplement(manualInput)
                                    copyToClipboard(context, comp) {
                                        toastMessage = "نجاح! تم تحويل المكمل لـ 12 رمزاً ونسخه! 📋"
                                        triggerVibration(context)
                                    }
                                } else {
                                    triggerError("الرمز غير مكتمل! يجب إدخال 12 رمزاً سداسياً بدقة")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("submit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تحويل ونسخ المكمل الفوري ⚡",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 📡 Simulator Area: "شبكات قريبة مكتشفة" (Discovered Nearby Signals)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CellTower,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "شبكات الـ Wi-Fi القريبة المكتشفة",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "ابحث عن شبكات الجوار، وبلمسة واحدة قم بتبديل وسحب النتيجة فورا للحافظة",
                        style = MaterialTheme.typography.bodySmall.copy(
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Scan Trigger Button
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                scanLoading = true
                                delay(1000) // 1 second standard simulations delay
                                scanLoading = false
                                showScanResult = true
                                discoveredList.clear()
                                discoveredList.addAll(
                                    listOf(
                                        DiscoveredWifi("fh_5c2570", 4),
                                        DiscoveredWifi("wlan9b860f", 3),
                                        DiscoveredWifi("fh_a340bc", 4),
                                        DiscoveredWifi("wlan1f2d3e", 2),
                                        DiscoveredWifi("HomeRouter_8d4e", 3),
                                        DiscoveredWifi("fh_66e771", 4)
                                    )
                                )
                                triggerVibration(context)
                            }
                        },
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (scanLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("جاري فحص النطاق المحيط...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("البحث عن شبكات قريبة 📡")
                        }
                    }

                    // Animated Network Listing (Slide Down)
                    AnimatedVisibility(
                        visible = showScanResult && !scanLoading,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        ) {
                            discoveredList.forEach { wifi ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .clickable {
                                            // One-tap Action: Set SSID in input field, process complements, copy, Vibrate and Toast!
                                            selectedTab = 0
                                            ssidInput = wifi.ssid
                                            
                                            val prs = parseSSID(wifi.ssid)
                                            if (prs.isValid) {
                                                copyToClipboard(context, prs.suggestedPassword) {
                                                    toastMessage = "تم سحب ${wifi.ssid}، مكملها: ${prs.suggestedPassword} منسوخ! ⚡📋"
                                                    triggerVibration(context)
                                                }
                                            } else {
                                                // For non-standard networks
                                                toastMessage = "تم اختيار ${wifi.ssid} ولكن مكملها يحتاج تحقيق صيغة ⚠️"
                                                triggerVibration(context)
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Left side Action Button
                                    Button(
                                        onClick = {
                                            selectedTab = 0
                                            ssidInput = wifi.ssid
                                            val prs = parseSSID(wifi.ssid)
                                            if (prs.isValid) {
                                                copyToClipboard(context, prs.suggestedPassword) {
                                                    toastMessage = "تم سحب ${wifi.ssid} ومكملها منسوخ! ⚡📋"
                                                    triggerVibration(context)
                                                }
                                            } else {
                                                toastMessage = "تم سحب ${wifi.ssid} للتجربة! 📡"
                                                triggerVibration(context)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            contentColor = MaterialTheme.colorScheme.primary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(
                                            text = "تبديل ⚡",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }

                                    // Right side Network description
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            modifier = Modifier.padding(end = 10.dp)
                                        ) {
                                            Text(
                                                text = wifi.ssid,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    textDirection = TextDirection.Ltr
                                                )
                                            )
                                            Text(
                                                text = if (parseSSID(wifi.ssid).isValid) "موافق للتشفير التلقائي" else "نطاق عام غير مرمز",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = if (parseSSID(wifi.ssid).isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                ),
                                                fontSize = 10.sp
                                            )
                                        }

                                        // Signal Strength indicator icon
                                        Icon(
                                            imageVector = when (wifi.signalStrength) {
                                                4 -> Icons.Default.Wifi
                                                3 -> Icons.Default.NetworkWifi3Bar
                                                2 -> Icons.Default.NetworkWifi2Bar
                                                else -> Icons.Default.NetworkWifi1Bar
                                            },
                                            contentDescription = "قوة الإشارة",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 📋 Floating Custom Overlay Action Toast (Beautiful Material 3 styling alert)
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
        ) {
            toastMessage?.let { msg ->
                Surface(
                    color = if (msg.contains("نجاح") || msg.contains("تم")) Color(0xFF047857) else Color(0xFFDC2626),
                    tonalElevation = 8.dp,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .shadow(16.dp, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (msg.contains("نجاح") || msg.contains("تم")) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}
