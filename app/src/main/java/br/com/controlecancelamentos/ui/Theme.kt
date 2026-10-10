package br.com.controlecancelamentos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.staticCompositionLocalOf
import br.com.controlecancelamentos.data.BrandingSettings
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Navy = Color(0xFF101C32)
val NavyLight = Color(0xFF243B62)
val Teal = Color(0xFF355FE8)
val Mint = Color(0xFFF4CB89)
private val LightInk = Color(0xFF1B2A40)
private val LightCanvas = Color(0xFFF3F5FB)
// Propriedades respeitam o modo do MaterialTheme, sem perder o contraste no escuro.
val Ink: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val Grey: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val CanvasColor: Color @Composable get() = MaterialTheme.colorScheme.background
val Gold = Color(0xFFE3AD5C)

val LocalBranding = staticCompositionLocalOf { BrandingSettings() }

@Composable
fun PremiumTheme(appearance: Int = 0, content: @Composable () -> Unit) {
    val dark = when (appearance) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFF9DB4FF), onPrimary = Color(0xFF101C32),
        primaryContainer = Color(0xFF293C69), onPrimaryContainer = Color(0xFFEDF0FF),
        secondary = Color(0xFFF1C784), onSecondary = Color(0xFF2B2011),
        background = Color(0xFF101827), onBackground = Color(0xFFF0F3FB),
        surface = Color(0xFF1A2639), onSurface = Color(0xFFF0F3FB),
        surfaceVariant = Color(0xFF263449), onSurfaceVariant = Color(0xFFC3CCDD),
        outline = Color(0xFF62728B), error = Color(0xFFFFB4AB)
    ) else lightColorScheme(
        primary = Teal, onPrimary = Color.White,
        primaryContainer = Color(0xFFE6EBFF), secondary = NavyLight,
        background = LightCanvas, surface = Color.White,
        onSurface = LightInk, onBackground = LightInk,
        onSurfaceVariant = Color(0xFF576780),
        outline = Color(0xFFCFD9E6), error = Color(0xFFB32634)
    )
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)),
        content = content
    )
}
@Composable
fun PremiumPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp), shadowElevation = 2.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun Hero(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Navy, NavyLight, Color(0xFF36558C))),
            RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)).padding(horizontal = 22.dp, vertical = 25.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(LocalBranding.current.appName.uppercase(), style = MaterialTheme.typography.labelMedium, color = Mint, maxLines = 1)
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD6E0F3))
        Text(LocalBranding.current.developerCredit, style = MaterialTheme.typography.labelSmall, color = Color(0xFFB6C7E8), maxLines = 1)
        if (LocalBranding.current.ideaCredit.isNotBlank()) {
            Text("Idealização: ${LocalBranding.current.ideaCredit}", style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFB6C7E8), maxLines = 1)
        }
    }
}

@Composable
fun SectionTitle(value: String) { Text(value, style = MaterialTheme.typography.titleMedium, color = Ink) }

@Composable
fun AppTextField(value: String, onValueChange: (String) -> Unit, label: String,
                 modifier: Modifier = Modifier, singleLine: Boolean = true,
                 minLines: Int = 1, readOnly: Boolean = false,
                 keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) },
        modifier = modifier.fillMaxWidth(), singleLine = singleLine, minLines = minLines, readOnly = readOnly,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary))
}
