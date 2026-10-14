package com.jonathan.miscuentas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jonathan.miscuentas.dominio.Fechas
import com.jonathan.miscuentas.dominio.Formato
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// confirmacion dibujada dentro de la pantalla
class Confirmador {
    var pregunta by mutableStateOf<Pregunta?>(null)

    data class Pregunta(val titulo: String, val mensaje: String, val si: String, val accion: () -> Unit)

    fun preguntar(titulo: String, mensaje: String, si: String = "Sí, borrar", accion: () -> Unit) {
        pregunta = Pregunta(titulo, mensaje, si, accion)
    }
}

val LocalConfirmador = staticCompositionLocalOf { Confirmador() }

@Composable
fun CapaConfirmacion(c: Confirmador) {
    val p = c.pregunta ?: return
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(enabled = true) { c.pregunta = null },
        contentAlignment = Alignment.Center,
    ) {
        Card(Modifier.padding(24.dp).fillMaxWidth().clickable(enabled = false) {}) {
            Column(Modifier.padding(20.dp)) {
                Text(p.titulo, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text(p.mensaje)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { c.pregunta = null }) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { c.pregunta = null; p.accion() },
                        colors = ButtonDefaults.buttonColors(containerColor = Colores.rojo),
                    ) { Text(p.si) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Encabezado(titulo: String, alVolver: (() -> Unit)? = null, acciones: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(titulo, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            if (alVolver != null) IconButton(onClick = alVolver) { Icon(Icons.Filled.ArrowBack, "Volver") }
        },
        actions = acciones,
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}

@Composable
fun Tarjeta(modifier: Modifier = Modifier, color: Color? = null, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colores = if (color != null) CardDefaults.cardColors(containerColor = color) else CardDefaults.cardColors()
    val mod = modifier.fillMaxWidth()
    if (onClick != null) {
        Card(onClick = onClick, modifier = mod, colors = colores) { Column(Modifier.padding(16.dp), content = content) }
    } else {
        Card(modifier = mod, colors = colores) { Column(Modifier.padding(16.dp), content = content) }
    }
}

@Composable
fun TituloSeccion(texto: String, modifier: Modifier = Modifier) {
    Text(texto, modifier = modifier.padding(top = 16.dp, bottom = 8.dp), fontWeight = FontWeight.Bold, fontSize = 17.sp)
}

@Composable
fun FilaDato(etiqueta: String, valor: String, color: Color = Color.Unspecified, negrita: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(etiqueta, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, color = color, fontWeight = if (negrita) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun ChipEstado(estado: String) {
    val c = Colores.estado(estado)
    Box(Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(estado, color = c, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BarraProgreso(usado: Long, total: Long, modifier: Modifier = Modifier, avance: Boolean = false) {
    val pct = if (total > 0) usado.toFloat() / total else 0f
    // en avance de pagos llenar la barra es bueno, no alerta
    val color = when {
        avance -> if (pct >= 1f) Colores.verde else MaterialTheme.colorScheme.primary
        pct > 1f -> Colores.rojo
        pct >= 0.8f -> Colores.amarillo
        else -> MaterialTheme.colorScheme.primary
    }
    LinearProgressIndicator(
        progress = { pct.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

@Composable
fun CampoTexto(etiqueta: String, valor: String, alCambiar: (String) -> Unit, modifier: Modifier = Modifier, numerico: Boolean = false, lineas: Int = 1) {
    OutlinedTextField(
        value = valor, onValueChange = alCambiar, label = { Text(etiqueta) },
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        singleLine = lineas == 1, minLines = lineas,
        keyboardOptions = if (numerico) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
    )
}

// pone los puntos de miles mientras se escribe
private object Miles : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digitos = text.text
        if (digitos.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val conPuntos = Formato.pesos(digitos.toLong()).removePrefix("$")
        val mapa = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val restantes = digitos.length - offset
                val puntosDespues = if (restantes > 0) (restantes - 1) / 3 else 0
                return conPuntos.length - restantes - puntosDespues
            }
            override fun transformedToOriginal(offset: Int): Int = conPuntos.take(offset).count { it.isDigit() }
        }
        return TransformedText(AnnotatedString(conPuntos), mapa)
    }
}

@Composable
fun CampoPesos(etiqueta: String, valor: Long?, alCambiar: (Long?) -> Unit, modifier: Modifier = Modifier, grande: Boolean = false) {
    // si viene con un valor queda todo seleccionado para escribir encima
    var campo by remember {
        val t = valor?.toString() ?: ""
        mutableStateOf(TextFieldValue(t, TextRange(0, t.length)))
    }
    if (campo.text.toLongOrNull() != valor) {
        val t = valor?.toString() ?: ""
        campo = TextFieldValue(t, TextRange(t.length))
    }
    OutlinedTextField(
        value = campo,
        onValueChange = { nuevo ->
            val limpio = nuevo.text.filter { it.isDigit() }.trimStart('0').take(13)
            val cursor = nuevo.selection.end.coerceAtMost(limpio.length)
            campo = TextFieldValue(limpio, if (limpio == nuevo.text) nuevo.selection else TextRange(cursor))
            alCambiar(limpio.toLongOrNull())
        },
        label = { Text(etiqueta) },
        prefix = { Text("$") },
        visualTransformation = Miles,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        textStyle = if (grande) LocalTextStyle.current.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold) else LocalTextStyle.current,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoFecha(etiqueta: String, fecha: LocalDate?, alCambiar: (LocalDate?) -> Unit, permitirVacio: Boolean = false, atajos: Boolean = true) {
    var abierto by remember { mutableStateOf(false) }
    val hoy = LocalDate.now()
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(etiqueta, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            OutlinedButton(onClick = { abierto = !abierto }) {
                Icon(Icons.Filled.DateRange, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(fecha?.let { Formato.fecha(Fechas.iso(it)) } ?: "Sin fecha")
            }
            if (atajos) {
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = fecha == hoy, onClick = { alCambiar(hoy); abierto = false }, label = { Text("Hoy") })
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = fecha == hoy.minusDays(1), onClick = { alCambiar(hoy.minusDays(1)); abierto = false }, label = { Text("Ayer") })
            }
            if (permitirVacio && fecha != null) {
                TextButton(onClick = { alCambiar(null) }) { Text("Quitar") }
            }
        }
        if (abierto) {
            val estado = rememberDatePickerState(
                initialSelectedDateMillis = (fecha ?: hoy).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            )
            LaunchedEffect(estado.selectedDateMillis) {
                val ms = estado.selectedDateMillis ?: return@LaunchedEffect
                val elegida = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                if (elegida != fecha) {
                    alCambiar(elegida); abierto = false
                }
            }
            DatePicker(state = estado, showModeToggle = false, title = null, headline = null)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectorChips(opciones: List<Pair<String, String>>, seleccion: String?, alElegir: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        opciones.forEach { (valor, texto) ->
            FilterChip(selected = valor == seleccion, onClick = { alElegir(valor) }, label = { Text(texto) })
        }
    }
}

@Composable
fun Interruptor(texto: String, valor: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { alCambiar(!valor) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, Modifier.weight(1f))
        Switch(checked = valor, onCheckedChange = alCambiar)
    }
}

@Composable
fun Etiqueta(texto: String, color: Color) {
    Text(texto, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun Vacio(texto: String) {
    Text(texto, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
}
