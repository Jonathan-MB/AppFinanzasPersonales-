package com.jonathan.miscuentas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jonathan.miscuentas.dominio.*
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun PantallaCalendario(vm: AppViewModel, datos: Datos, irDetalle: (Obligacion) -> Unit) {
    val hoy = vm.hoy()
    var mes by rememberSaveable { mutableStateOf(YearMonth.from(hoy).toString()) }
    val ym = YearMonth.parse(mes)
    var vistaMes by rememberSaveable { mutableStateOf(false) }
    var categoria by rememberSaveable { mutableStateOf("") }
    var estado by rememberSaveable { mutableStateOf("") }
    var dia by remember(mes) { mutableStateOf<LocalDate?>(null) }

    val obligaciones = Obligaciones.generar(datos, ym.atDay(1), ym.atEndOfMonth())
    val ingresos = Obligaciones.ingresosEsperados(datos, ym.atDay(1), ym.atEndOfMonth())
    val filtradas = obligaciones.filter {
        (categoria.isBlank() || it.categoria == categoria) && (estado.isBlank() || it.estado(hoy) == estado) && (dia == null || it.fecha == dia)
    }
    val ingresosFiltrados = if (estado.isBlank() && (categoria.isBlank() || categoria == Categorias.INGRESO)) ingresos.filter { dia == null || it.fecha == dia } else emptyList()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { mes = ym.minusMonths(1).toString() }) { Icon(Icons.Filled.KeyboardArrowLeft, "Mes anterior") }
            Text(Formato.mes(ym), Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            IconButton(onClick = { mes = ym.plusMonths(1).toString() }) { Icon(Icons.Filled.KeyboardArrowRight, "Mes siguiente") }
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !vistaMes, onClick = { vistaMes = false }, label = { Text("Lista") })
            FilterChip(selected = vistaMes, onClick = { vistaMes = true }, label = { Text("Mes") })
            if (YearMonth.from(hoy) != ym) AssistChip(onClick = { mes = YearMonth.from(hoy).toString() }, label = { Text("Hoy") })
            if (dia != null) AssistChip(onClick = { dia = null }, label = { Text("Quitar día ${dia!!.dayOfMonth}") })
        }
        Column(Modifier.padding(horizontal = 12.dp)) {
            val cats = (obligaciones.map { it.categoria } + Categorias.INGRESO).distinct()
            SelectorChips(listOf("" to "Todas") + cats.map { it to it }, categoria) { categoria = it }
            SelectorChips(listOf("" to "Todos") + Estado.todos.map { it to it }, estado) { estado = it }
        }

        val totalP = filtradas.sumOf { it.presupuestado }
        val totalPag = filtradas.sumOf { it.pagado }
        Text(
            "Presupuestado ${Formato.pesos(totalP)} · Pagado ${Formato.pesos(totalPag)} · Falta ${Formato.pesos(filtradas.sumOf { it.falta })}",
            fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )

        if (vistaMes) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                CuadriculaMes(ym, hoy, obligaciones, ingresos, dia) { dia = if (dia == it) null else it }
                Spacer(Modifier.height(8.dp))
                ListaDia(filtradas, ingresosFiltrados, hoy, irDetalle)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                val dias = (filtradas.map { it.fecha } + ingresosFiltrados.map { it.fecha }).distinct().sorted()
                if (dias.isEmpty()) item { Vacio("No hay nada en este mes con esos filtros.") }
                dias.forEach { f ->
                    item(key = "d-$f") {
                        Text(
                            "${Formato.diaSemana(f)} ${Formato.fechaCorta(f)}" + if (f == hoy) "  · hoy" else "",
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                            color = if (f == hoy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    items(ingresosFiltrados.filter { it.fecha == f }, key = { it.clave }) { FilaIngresoEsperado(it) }
                    items(filtradas.filter { it.fecha == f }, key = { it.clave }) { o -> FilaObligacion(o, hoy) { irDetalle(o) } }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun ListaDia(obl: List<Obligacion>, ing: List<IngresoEsperado>, hoy: LocalDate, irDetalle: (Obligacion) -> Unit) {
    ing.forEach { FilaIngresoEsperado(it) }
    obl.forEach { o -> FilaObligacion(o, hoy) { irDetalle(o) } }
    if (obl.isEmpty() && ing.isEmpty()) Vacio("Nada para mostrar.")
}

@Composable
fun FilaObligacion(o: Obligacion, hoy: LocalDate, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.concepto, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                ChipEstado(o.estado(hoy))
            }
            Text(o.categoria + (o.aviso?.let { " · ⚠ $it" } ?: "") + (o.fechaOriginal?.let { " · pospuesto del ${Formato.fechaCorta(it)}" } ?: ""), fontSize = 12.sp, color = if (o.aviso != null) Colores.amarillo else MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("Presup. ${Formato.pesos(o.presupuestado)}", Modifier.weight(1f), fontSize = 12.sp)
                Text("Pagado ${Formato.pesos(o.pagado)}", Modifier.weight(1f), fontSize = 12.sp)
                Text("Falta ${Formato.pesos(o.falta)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun FilaIngresoEsperado(e: IngresoEsperado) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = Colores.verde.copy(alpha = 0.12f)),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("💰 " + e.concepto, fontWeight = FontWeight.Bold)
                Text(if (e.esEstimado) "Ingreso estimado" else "Ingreso", fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Formato.pesos(e.monto), fontWeight = FontWeight.Bold, color = Colores.verde)
                if (e.recibido > 0) Text("recibido ${Formato.pesos(e.recibido)}", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun CuadriculaMes(ym: YearMonth, hoy: LocalDate, obl: List<Obligacion>, ing: List<IngresoEsperado>, elegido: LocalDate?, alTocar: (LocalDate) -> Unit) {
    val primero = ym.atDay(1)
    val blancos = Fechas.indiceDiaSemana(primero)
    val dias = ym.lengthOfMonth()
    Row { listOf("D", "L", "M", "M", "J", "V", "S").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp) } }
    val celdas = blancos + dias
    val filas = (celdas + 6) / 7
    for (fila in 0 until filas) {
        Row {
            for (col in 0 until 7) {
                val n = fila * 7 + col - blancos + 1
                if (n < 1 || n > dias) {
                    Spacer(Modifier.weight(1f).height(54.dp))
                } else {
                    val f = ym.atDay(n)
                    val delDia = obl.filter { it.fecha == f }
                    val conIngreso = ing.any { it.fecha == f }
                    Column(
                        Modifier.weight(1f).height(54.dp).padding(2.dp)
                            .then(if (f == elegido) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
                            .background(if (f == hoy) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .clickable { alTocar(f) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("$n", fontSize = 13.sp, fontWeight = if (f == hoy) FontWeight.Bold else FontWeight.Normal)
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            delDia.take(4).forEach { o -> Box(Modifier.size(7.dp).background(Colores.estado(o.estado(hoy)), CircleShape)) }
                            if (conIngreso) Box(Modifier.size(7.dp).background(Colores.verde, CircleShape))
                        }
                    }
                }
            }
        }
    }
}

// pagos ligados, registrar pago y editar el origen
@Composable
fun PantallaDetalleObligacion(vm: AppViewModel, datos: Datos, clave: String, alVolver: () -> Unit, irMonto: () -> Unit, irEditar: (String, String) -> Unit) {
    val hoy = vm.hoy()
    val partes = clave.split(':')
    val fecha = Fechas.leer(partes.getOrNull(2)) ?: hoy
    // un pospuesto puede haber pasado a otro mes: se busca un poco mas adelante
    val o = Obligaciones.generar(datos, YearMonth.from(fecha).atDay(1), YearMonth.from(fecha).plusMonths(3).atEndOfMonth()).firstOrNull { it.clave == clave }
        ?: Obligaciones.generar(datos, YearMonth.from(fecha).atDay(1), YearMonth.from(fecha).atEndOfMonth())
            .firstOrNull { it.tipoOrigen == partes.getOrNull(0) && it.origenId == partes.getOrNull(1) }
    val confirmar = LocalConfirmador.current
    Column(Modifier.fillMaxSize()) {
        Encabezado(o?.concepto ?: "Pago", alVolver)
        if (o == null) {
            Vacio("Este pago ya no existe (quizá se cambió el origen).")
            return@Column
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Tarjeta {
                Row { Text(Formato.fecha(Fechas.iso(o.fecha)), Modifier.weight(1f), fontWeight = FontWeight.Bold); ChipEstado(o.estado(hoy)) }
                FilaDato("Categoría", o.categoria)
                FilaDato("Presupuestado", Formato.pesos(o.presupuestado))
                FilaDato("Pagado", Formato.pesos(o.pagado), Colores.verde)
                FilaDato("Falta", Formato.pesos(o.falta), negrita = true)
                o.aviso?.let { Text("⚠ $it", color = Colores.amarillo, fontSize = 13.sp) }
                o.fechaOriginal?.let { Text("Pospuesto del ${Formato.fecha(Fechas.iso(it))}", color = Colores.azul, fontSize = 13.sp) }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { vm.seleccion = Seleccion.Pago(o); irMonto() }, Modifier.fillMaxWidth().height(52.dp)) { Text("Registrar pago") }
            Spacer(Modifier.height(8.dp))
            if (o.falta > 0) {
                var moviendo by remember { mutableStateOf(false) }
                var nueva by remember { mutableStateOf<LocalDate?>(Tarjetas.proximoSueldo(datos, hoy)) }
                if (!moviendo) OutlinedButton(onClick = { moviendo = true }, Modifier.fillMaxWidth()) { Text("📆 Posponer") }
                else {
                    CampoFecha("Nueva fecha", nueva, { nueva = it }, atajos = false)
                    Row {
                        TextButton(onClick = { moviendo = false }) { Text("Cancelar") }
                        Spacer(Modifier.weight(1f))
                        Button(enabled = nueva != null, onClick = { vm.posponer(o.clave, nueva!!, ""); alVolver() }) { Text("Posponer") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (o.fechaOriginal != null) {
                OutlinedButton(onClick = { datos.pospuestos.firstOrNull { it.clave == o.clave }?.let { vm.borrar(it) }; alVolver() }, Modifier.fillMaxWidth()) {
                    Text("Volver a la fecha original")
                }
                Spacer(Modifier.height(8.dp))
            }
            if (o.tipoOrigen != Origen.PUENTE) OutlinedButton(onClick = { irEditar(o.tipoOrigen, o.origenId) }, Modifier.fillMaxWidth()) {
                Text(
                    when (o.tipoOrigen) {
                        Origen.DEUDA -> "Editar deuda"
                        Origen.GASTO -> "Editar gasto fijo"
                        Origen.PRESUPUESTO -> "Editar presupuesto"
                        else -> "Editar evento"
                    }
                )
            }
            TituloSeccion("Pagos ligados")
            val ligados = datos.movimientos.filter { m ->
                val c = m.claveObligacion ?: return@filter false
                c == o.clave || (c.startsWith("${o.tipoOrigen}:${o.origenId}:") &&
                    Obligaciones.conPagos(listOf(o), listOf(m)).first().pagado > 0)
            }
            if (ligados.isEmpty()) Vacio("Todavía no hay pagos para esta fecha.")
            ligados.forEach { m ->
                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Formato.pesos(m.monto), fontWeight = FontWeight.Bold)
                            Text("${Formato.fecha(m.fecha)} ${m.nota}", fontSize = 12.sp)
                        }
                        TextButton(onClick = {
                            confirmar.preguntar("Borrar pago", "¿Borrar el pago de ${Formato.pesos(m.monto)}?") { vm.borrar(m) }
                        }) { Text("Borrar", color = Colores.rojo) }
                    }
                }
            }
        }
    }
}
