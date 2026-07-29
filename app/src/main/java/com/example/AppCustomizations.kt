package com.example

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// ==========================================
// 1. DATA MODELS
// ==========================================

data class PrefixItem(
    val id: String = UUID.randomUUID().toString(),
    val prefix: String,
    val isEnabled: Boolean = true
)

data class StrippingRuleItem(
    val id: String = UUID.randomUUID().toString(),
    val pattern: String,
    val isEnabled: Boolean = true
)

data class HexPairItem(
    val id: String = UUID.randomUUID().toString(),
    val keyChar: Char,
    val valChar: Char,
    val isEnabled: Boolean = true
)

// ==========================================
// 2. PREFERENCES REPOSITORY (LOCAL PERSISTENCE)
// ==========================================

object AppPreferencesRepository {
    private const val PREFS_NAME = "xei5h_prefs_v3"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Default Hex Complement Map (0<->f | 1<->e | 2<->d | 3<->c | 4<->b | 5<->a | 6<->9 | 7<->8)
    fun getDefaultHexMap(): Map<Char, Char> {
        return mapOf(
            '0' to 'f', '1' to 'e', '2' to 'd', '3' to 'c',
            '4' to 'b', '5' to 'a', '6' to '9', '7' to '8',
            '8' to '7', '9' to '6', 'a' to '5', 'b' to '4',
            'c' to '3', 'd' to '2', 'e' to '1', 'f' to '0'
        )
    }

    fun getDefaultHexPairsList(): List<HexPairItem> {
        return listOf(
            HexPairItem(keyChar = '0', valChar = 'f', isEnabled = true),
            HexPairItem(keyChar = '1', valChar = 'e', isEnabled = true),
            HexPairItem(keyChar = '2', valChar = 'd', isEnabled = true),
            HexPairItem(keyChar = '3', valChar = 'c', isEnabled = true),
            HexPairItem(keyChar = '4', valChar = 'b', isEnabled = true),
            HexPairItem(keyChar = '5', valChar = 'a', isEnabled = true),
            HexPairItem(keyChar = '6', valChar = '9', isEnabled = true),
            HexPairItem(keyChar = '7', valChar = '8', isEnabled = true)
        )
    }

    // Default Network Prefixes (fh_, WLAN_, wifi_)
    fun getDefaultPrefixes(): List<PrefixItem> {
        return listOf(
            PrefixItem(prefix = "fh_", isEnabled = true),
            PrefixItem(prefix = "WLAN_", isEnabled = true),
            PrefixItem(prefix = "wifi_", isEnabled = true)
        )
    }

    // Default Stripping Rules (#, _5g, etc.)
    fun getDefaultStrippingRules(): List<StrippingRuleItem> {
        return listOf(
            StrippingRuleItem(pattern = "#", isEnabled = true),
            StrippingRuleItem(pattern = "_5g", isEnabled = true),
            StrippingRuleItem(pattern = "5G", isEnabled = true),
            StrippingRuleItem(pattern = "_2g", isEnabled = true),
            StrippingRuleItem(pattern = "_EXT", isEnabled = true)
        )
    }

    // Force Lowercase Flag
    fun isForceLowercaseEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean("force_lowercase_enabled", true)
    }

    fun saveForceLowercaseEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("force_lowercase_enabled", enabled).apply()
    }

    // Hex Pairs Persistence
    fun getHexPairsList(context: Context): List<HexPairItem> {
        val jsonString = getPrefs(context).getString("hex_pairs_json_v2", null) ?: return getDefaultHexPairsList()
        return try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<HexPairItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val kStr = obj.getString("keyChar")
                val vStr = obj.getString("valChar")
                if (kStr.isNotEmpty() && vStr.isNotEmpty()) {
                    list.add(
                        HexPairItem(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            keyChar = kStr[0],
                            valChar = vStr[0],
                            isEnabled = obj.optBoolean("isEnabled", true)
                        )
                    )
                }
            }
            if (list.isEmpty()) getDefaultHexPairsList() else list
        } catch (_: Exception) {
            getDefaultHexPairsList()
        }
    }

    fun saveHexPairsList(context: Context, list: List<HexPairItem>) {
        val array = JSONArray()
        list.forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("keyChar", item.keyChar.toString())
            obj.put("valChar", item.valChar.toString())
            obj.put("isEnabled", item.isEnabled)
            array.put(obj)
        }
        getPrefs(context).edit().putString("hex_pairs_json_v2", array.toString()).apply()
    }

    // Derive active Map from Pairs List
    fun getHexMapFromPairs(pairs: List<HexPairItem>): Map<Char, Char> {
        val map = mutableMapOf<Char, Char>()
        pairs.filter { it.isEnabled }.forEach { pair ->
            map[pair.keyChar.lowercaseChar()] = pair.valChar.lowercaseChar()
            map[pair.valChar.lowercaseChar()] = pair.keyChar.lowercaseChar()
        }
        return map
    }

    // Legacy Hex Map backward compatibility
    fun getHexMap(context: Context): Map<Char, Char> {
        val pairs = getHexPairsList(context)
        return getHexMapFromPairs(pairs)
    }

    // Prefixes Persistence
    fun getPrefixes(context: Context): List<PrefixItem> {
        val jsonString = getPrefs(context).getString("prefixes_json", null) ?: return getDefaultPrefixes()
        return try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<PrefixItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    PrefixItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        prefix = obj.getString("prefix"),
                        isEnabled = obj.optBoolean("isEnabled", true)
                    )
                )
            }
            if (list.isEmpty()) getDefaultPrefixes() else list
        } catch (_: Exception) {
            getDefaultPrefixes()
        }
    }

    fun savePrefixes(context: Context, list: List<PrefixItem>) {
        val array = JSONArray()
        list.forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("prefix", item.prefix)
            obj.put("isEnabled", item.isEnabled)
            array.put(obj)
        }
        getPrefs(context).edit().putString("prefixes_json", array.toString()).apply()
    }

    // Stripping Rules Persistence
    fun getStrippingRules(context: Context): List<StrippingRuleItem> {
        val jsonString = getPrefs(context).getString("stripping_rules_json", null) ?: return getDefaultStrippingRules()
        return try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<StrippingRuleItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    StrippingRuleItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        pattern = obj.getString("pattern"),
                        isEnabled = obj.optBoolean("isEnabled", true)
                    )
                )
            }
            if (list.isEmpty()) getDefaultStrippingRules() else list
        } catch (_: Exception) {
            getDefaultStrippingRules()
        }
    }

    fun saveStrippingRules(context: Context, list: List<StrippingRuleItem>) {
        val array = JSONArray()
        list.forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("pattern", item.pattern)
            obj.put("isEnabled", item.isEnabled)
            array.put(obj)
        }
        getPrefs(context).edit().putString("stripping_rules_json", array.toString()).apply()
    }
}

// ==========================================
// 3. EDIT / ADD DIALOG COMPOSABLES
// ==========================================

@Composable
fun EditHexPairDialog(
    initialKey: Char?,
    initialValue: Char?,
    onDismiss: () -> Unit,
    onSave: (Char, Char) -> Unit
) {
    var keyText by remember { mutableStateOf(initialKey?.toString() ?: "") }
    var valText by remember { mutableStateOf(initialValue?.toString() ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = if (initialKey != null) "Edit Hex Pair" else "Add Hex Pair",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = keyText,
                        onValueChange = { if (it.length <= 1) keyText = it },
                        label = { Text("Hex 1") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text("↔", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    OutlinedTextField(
                        value = valText,
                        onValueChange = { if (it.length <= 1) valText = it },
                        label = { Text("Hex 2") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val k = keyText.trim().firstOrNull()
                    val v = valText.trim().firstOrNull()
                    if (k != null && v != null) {
                        onSave(k, v)
                    } else {
                        errorMsg = "Please enter valid single characters."
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SoftIndigoAccent)
            ) {
                Text("Save", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(color = MutedTextGray))
            }
        }
    )
}

@Composable
fun EditItemStringDialog(
    title: String,
    initialValue: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var textValue by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        text = {
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                placeholder = { Text(placeholder) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = textValue.trim()
                    if (clean.isNotEmpty()) {
                        onSave(clean)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SoftIndigoAccent)
            ) {
                Text("Save", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(color = MutedTextGray))
            }
        }
    )
}
