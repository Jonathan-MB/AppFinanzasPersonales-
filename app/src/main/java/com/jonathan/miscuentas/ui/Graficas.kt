package com.jonathan.miscuentas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jonathan.miscuentas.dominio.Formato

@Composable
fun Leyenda(items: List<Pair<String, Color>>) {
    Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { (t, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(c, CircleShape))
                Spacer(Modifier.width(4.dp))
                Text(t, fontSize = 12.sp)
            }
        }
    }
}

// dos barras por grupo, ej. presupuestado vs pagado
@Composable
fun BarrasDobles(grupos: List<Triple<String, Long, Long>>, colorA: Color, colorB: Color, alto: Int = 160) {
    if (grupos.isEmpty()) return
    val maximo = grupos.maxOf { maxOf(it.second, it.third) }.coerceAtLeast(1)
    Column {
        Canvas(Modifier.fillMaxWidth().height(alto.dp)) {
            val ancho = size.width / grupos.size
            val barra = ancho * 0.32f
            grupos.forEachIndexed { i, (_, a, b) ->
                val x = i * ancho + ancho * 0.15f
                val ha = size.height * a / maximo
                val hb = size.height * b / maximo
                drawRoundRect(colorA, Offset(x, size.height - ha), Size(barra, ha), CornerRadius(6f))
                drawRoundRect(colorB, Offset(x + barra + 4f, size.height - hb), Size(barra, hb), CornerRadius(6f))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            grupos.forEach { (n, _, _) ->
                Text(n, Modifier.weight(1f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// barras horizontales con el valor al lado
@Composable
fun BarrasHorizontales(items: List<Pair<String, Long>>, colores: List<Color> = Colores.serie) {
    if (items.isEmpty()) return
    val maximo = items.maxOf { it.second }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEachIndexed { i, (n, v) ->
            Column {
                Row {
                    Text(n, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Formato.pesos(v), fontSize = 13.sp)
                }
                Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
                    Box(Modifier.fillMaxWidth(v.toFloat() / maximo).fillMaxHeight().background(colores[i % colores.size], CircleShape))
                }
            }
        }
    }
}

// pagado vs saldo de cada deuda en una sola barra
@Composable
fun BarrasDeuda(items: List<Triple<String, Long, Long>>) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (n, pagado, saldo) ->
            val total = (pagado + saldo).coerceAtLeast(1)
            Column {
                Row {
                    Text(n, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Formato.pesos(saldo), fontSize = 13.sp)
                }
                Row(Modifier.fillMaxWidth().height(8.dp).background(Colores.rojo.copy(alpha = 0.25f), CircleShape)) {
                    if (pagado > 0) Box(Modifier.weight(pagado.toFloat() / total).fillMaxHeight().background(Colores.verde, CircleShape))
                    if (saldo > 0) Spacer(Modifier.weight(saldo.toFloat() / total))
                }
            }
        }
    }
}

@Composable
fun Linea(puntos: List<Pair<String, Long>>, color: Color, alto: Int = 140) {
    if (puntos.size < 2) {
        Text("Aún no hay suficientes datos para la línea.", fontSize = 13.sp)
        return
    }
    val maximo = puntos.maxOf { it.second }.coerceAtLeast(1)
    val minimo = puntos.minOf { it.second }
    val rango = (maximo - minimo).coerceAtLeast(1)
    Column {
        Canvas(Modifier.fillMaxWidth().height(alto.dp).padding(vertical = 6.dp)) {
            val paso = size.width / (puntos.size - 1)
            val path = Path()
            puntos.forEachIndexed { i, (_, v) ->
                val x = i * paso
                val y = size.height - size.height * (v - minimo) / rango
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                drawCircle(color, 5f, Offset(x, y))
            }
            drawPath(path, color, style = Stroke(width = 5f))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(puntos.first().first, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(puntos.last().first, fontSize = 11.sp)
        }
    }
}
