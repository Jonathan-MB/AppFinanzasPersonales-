package com.jonathan.miscuentas.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.jonathan.miscuentas.dominio.Estado

object Colores {
    val verde = Color(0xFF16A34A)
    val rojo = Color(0xFFDC2626)
    val amarillo = Color(0xFFD97706)
    val gris = Color(0xFF6B7280)
    val azul = Color(0xFF2563EB)
    val morado = Color(0xFF7C3AED)
    val serie = listOf(Color(0xFF0F766E), Color(0xFFF59E0B), Color(0xFF2563EB), Color(0xFFDB2777), Color(0xFF7C3AED), Color(0xFF16A34A), Color(0xFF64748B))

    fun estado(e: String): Color = when (e) {
        Estado.PAGADO -> verde
        Estado.VENCIDO, Estado.VENCIDO_PARCIAL -> rojo
        Estado.PARCIAL -> amarillo
        else -> gris
    }

    fun semaforo(s: String): Color = when (s) {
        "verde" -> verde
        "amarillo" -> amarillo
        "rojo" -> rojo
        else -> gris
    }
}

private val claro = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF134E4A),
    secondary = Color(0xFFF59E0B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF78350F),
    tertiaryContainer = Color(0xFFFEE2E2),
    onTertiaryContainer = Color(0xFF7F1D1D),
    background = Color(0xFFF6F7F9),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEF2F4),
)

private val oscuro = darkColorScheme(
    primary = Color(0xFF2DD4BF),
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF134E4A),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF451A03),
    secondaryContainer = Color(0xFF78350F),
    onSecondaryContainer = Color(0xFFFEF3C7),
    tertiaryContainer = Color(0xFF7F1D1D),
    onTertiaryContainer = Color(0xFFFEE2E2),
    background = Color(0xFF0F1417),
    surface = Color(0xFF182025),
    surfaceVariant = Color(0xFF243038),
)

@Composable
fun TemaMisCuentas(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) oscuro else claro, content = content)
}
