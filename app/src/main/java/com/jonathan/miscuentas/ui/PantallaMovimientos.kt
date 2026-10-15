package com.jonathan.miscuentas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jonathan.miscuentas.dominio.*
import java.time.LocalDate
import java.time.YearMonth

// un gasto o un ingreso en la misma lista
// neutro: cuadres y envios entre cuentas, no son gasto ni ingreso
data class Registro(val id: String, val fecha: String, val monto: Long, val esIngreso: Boolean, val titulo: String, val detalle: String, val tipo: String, val categoria: String, val original: Any, val neutro: Boolean = false, val montoTexto: String? = null)

fun registros(datos: Datos): List<Registro> {
    val etiquetas = datos.categorias.associateBy { it.nombre }
    val cuentas = datos.cuentas.associateBy { it.id }
    fun conCuenta(id: String?, texto: String) = (cuentas[id ?: Saldos.principal(datos)]?.let { "${it.emoji} ${it.nombre}" } ?: "").let { c -> listOf(c, texto).filter { it.isNotBlank() }.joinToString(" · ") }
    val gastos = datos.movimientos.map { m ->
        val nombre = m.claveObligacion?.let { c -> obligacionNombre(datos, c) } ?: m.categoria.ifBlank { "Gasto" }
        val emoji = etiquetas[m.categoria]?.emoji ?: if (m.tipo == TipoMovimiento.ABONO_EXTRA) "⬇️" else "💸"
        Registro(m.id, m.fecha, m.monto, false, "$emoji $nombre", conCuenta(m.cuentaId, m.nota), m.tipo, m.categoria, m)
    }
    val ingresos = datos.ingresos.map { i ->
        val emoji = etiquetas.values.firstOrNull { it.tipo == TipoEtiqueta.INGRESO && Semilla.tipoIngreso(it.nombre) == i.tipo }?.emoji ?: "💰"
        Registro(i.id, i.fecha, i.monto, true, "$emoji ${i.nota.ifBlank { i.tipo }}", conCuenta(i.cuentaId, i.tipo), "ingreso", Categorias.INGRESO, i)
    }
    // cuadres y envios: se agrupan por grupo para borrar todo junto
    val deCuentas = datos.cuentaMovs.groupBy { it.grupo ?: it.id }.mapNotNull { (_, filas) ->
        val marca = filas.firstOrNull { it.tipo == TipoCuentaMov.CUADRE }
        if (marca != null) {
            val d = marca.monto
            val texto = when {
                d == 0L -> "cuadró exacto"
                d < 0 -> "desfase de ${Formato.pesos(-d)} de menos"
                else -> "desfase de ${Formato.pesos(d)} de más"
            }
            Registro(marca.id, marca.fecha, kotlin.math.abs(d), false, "⚖️ Cuadre de cuentas", texto, "cuentas", "", filas, neutro = true,
                montoTexto = if (d == 0L) "✓" else (if (d < 0) "−" else "+") + Formato.pesos(kotlin.math.abs(d)))
        } else {
            val sale = filas.firstOrNull { it.monto < 0 } ?: return@mapNotNull null
            val entra = filas.firstOrNull { it.monto > 0 }
            fun nombre(id: String?) = cuentas[id]?.let { "${it.emoji} ${it.nombre}" } ?: "?"
            Registro(sale.id, sale.fecha, -sale.monto, false, "🔁 ${nombre(sale.cuentaId)} → ${nombre(entra?.cuentaId)}", sale.nota, "cuentas", "", filas,
                neutro = true, montoTexto = Formato.pesos(-sale.monto))
        }
    }
    // el mismo dia queda arriba lo ultimo que se registro
    fun creado(r: Registro) = (r.original as? Movimiento)?.createdAt ?: (r.original as? Ingreso)?.createdAt
        ?: (r.original as? List<*>)?.filterIsInstance<CuentaMov>()?.firstOrNull()?.createdAt ?: ""
    return (gastos + ingresos + deCuentas).sortedWith(compareByDescending<Registro> { it.fecha }.thenByDescending { creado(it) }.thenByDescending { it.id })
}

private fun obligacionNombre(datos: Datos, clave: String): String? {
    val p = clave.split(':')
    if (p.size != 3) return null
    return when (p[0]) {
        Origen.DEUDA -> datos.deudas.firstOrNull { it.id == p[1] }?.nombre
        Origen.GASTO -> datos.gastosFijos.firstOrNull { it.id == p[1] }?.nombre
        Origen.PRESUPUESTO -> datos.presupuestos.firstOrNull { it.id == p[1] }?.nombre
        Origen.EVENTO -> datos.eventos.firstOrNull { it.id == p[1] }?.nombre
        else -> null
    }
}

@Composable
fun PantallaMovimientos(vm: AppViewModel, datos: Datos, irEditar: (Registro) -> Unit, borrarConDeshacer: (Any) -> Unit) {
    val hoy = vm.hoy()
    var mes by rememberSaveable { mutableStateOf(YearMonth.from(hoy).toString()) }
    var todo by rememberSaveable { mutableStateOf(false) }
    var buscar by rememberSaveable { mutableStateOf("") }
    var tipo by rememberSaveable { mutableStateOf("") }
    var categoria by rememberSaveable { mutableStateOf("") }
    val confirmar = LocalConfirmador.current
    val ym = YearMonth.parse(mes)

    val base = registros(datos).filter { todo || Fechas.leer(it.fecha)?.let { f -> YearMonth.from(f) == ym } == true }
    val lista = base.filter { r ->
        (buscar.isBlank() || (r.titulo + " " + r.detalle + " " + r.categoria).contains(buscar, ignoreCase = true)) &&
            when (tipo) {
                "gastos" -> !r.esIngreso && !r.neutro
                "ingresos" -> r.esIngreso
                "" -> true
                else -> r.tipo == tipo
            } && (categoria.isBlank() || r.categoria == categoria)
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = !todo, onClick = { mes = ym.minusMonths(1).toString() }) { Icon(Icons.Filled.KeyboardArrowLeft, "Mes anterior") }
            Text(if (todo) "Todo" else Formato.mes(ym), Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            IconButton(enabled = !todo, onClick = { mes = ym.plusMonths(1).toString() }) { Icon(Icons.Filled.KeyboardArrowRight, "Mes siguiente") }
            FilterChip(selected = todo, onClick = { todo = !todo }, label = { Text("Todo") })
        }
        OutlinedTextField(
            value = buscar, onValueChange = { buscar = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Buscar") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
        Column(Modifier.padding(horizontal = 12.dp)) {
            SelectorChips(
                listOf("" to "Todos", "gastos" to "Gastos", "ingresos" to "Ingresos", TipoMovimiento.PAGO_PROGRAMADO to "Pagos",
                    TipoMovimiento.NO_PLANEADO to "No planeados", TipoMovimiento.ABONO_EXTRA to "Abonos", "cuentas" to "Cuentas"), tipo,
            ) { tipo = it }
            val cats = base.map { it.categoria }.filter { it.isNotBlank() }.distinct().sorted()
            if (cats.size > 1) SelectorChips(listOf("" to "Todas") + cats.map { it to it }, categoria) { categoria = it }
        }
        val gastos = lista.filter { !it.esIngreso && !it.neutro }.sumOf { it.monto }
        val ingresos = lista.filter { it.esIngreso }.sumOf { it.monto }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text("Gastos ${Formato.pesos(gastos)}", Modifier.weight(1f), color = Colores.rojo, fontWeight = FontWeight.Bold)
            Text("Ingresos ${Formato.pesos(ingresos)}", color = Colores.verde, fontWeight = FontWeight.Bold)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 90.dp)) {
            if (lista.isEmpty()) item { Vacio("No hay registros con esos filtros.") }
            items(lista, key = { it.id }) { r ->
                Card(onClick = { if (!r.neutro) irEditar(r) }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.titulo, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${Formato.fecha(r.fecha)}  ${r.detalle}", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (r.neutro) Text(r.montoTexto ?: "", fontWeight = FontWeight.Bold)
                        else Text((if (r.esIngreso) "+" else "−") + Formato.pesos(r.monto), fontWeight = FontWeight.Bold, color = if (r.esIngreso) Colores.verde else Colores.rojo)
                        TextButton(onClick = {
                            confirmar.preguntar("Borrar", if (r.neutro) "¿Borrar ${r.titulo} (${r.detalle})?" else "¿Borrar ${r.titulo} por ${Formato.pesos(r.monto)}?") { borrarConDeshacer(r.original) }
                        }) { Text("✕", color = Colores.rojo) }
                    }
                }
            }
        }
    }
}

// editar un gasto o un ingreso ya guardado
@Composable
fun PantallaEditarRegistro(vm: AppViewModel, datos: Datos, id: String, alVolver: () -> Unit, borrarConDeshacer: (Any) -> Unit) {
    val mov = datos.movimientos.firstOrNull { it.id == id }
    val ing = datos.ingresos.firstOrNull { it.id == id }
    if (mov == null && ing == null) {
        LaunchedEffect(Unit) { alVolver() }
        return
    }
    val confirmar = LocalConfirmador.current
    var monto by remember { mutableStateOf<Long?>(mov?.monto ?: ing?.monto) }
    var fecha by remember { mutableStateOf(Fechas.leer(mov?.fecha ?: ing?.fecha)) }
    var nota by remember { mutableStateOf(mov?.nota ?: ing?.nota ?: "") }
    var categoria by remember { mutableStateOf(mov?.categoria ?: "") }
    var tipoIngreso by remember { mutableStateOf(ing?.tipo ?: TipoIngreso.OTRO) }
    var completo by remember { mutableStateOf(mov?.liquida ?: false) }
    var cuenta by remember { mutableStateOf(mov?.cuentaId ?: ing?.cuentaId ?: Saldos.principal(datos)) }
    val esRecibo = mov?.claveObligacion != null && !mov.claveObligacion.startsWith(Origen.PRESUPUESTO + ":")

    Column(Modifier.fillMaxSize()) {
        Encabezado(if (mov != null) "Editar gasto" else "Editar ingreso", alVolver)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            CampoPesos("Monto", monto, { monto = it }, grande = true)
            CampoFecha("Fecha", fecha, { fecha = it })
            CampoTexto("Nota", nota, { nota = it })
            SelectorCuenta(datos, if (mov != null) "¿De dónde salió?" else "¿A qué cuenta entró?", cuenta) { cuenta = it }
            if (mov != null) {
                mov.claveObligacion?.let { c ->
                    val fechaPago = Fechas.leer(c.substringAfterLast(':'))?.let { " del " + Formato.fecha(Fechas.iso(it)) } ?: ""
                    Text("Pago de ${obligacionNombre(datos, c) ?: "un pago borrado"}$fechaPago", fontSize = 12.sp)
                }
                if (esRecibo) {
                    SelectorChips(listOf("si" to "✓ Pago completo", "no" to "Es un abono"), if (completo) "si" else "no") { completo = it == "si" }
                }
                Text("Categoría", fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                val cats = (datos.categorias.filter { it.tipo == TipoEtiqueta.GASTO || it.tipo == TipoEtiqueta.SISTEMA }.map { it.nombre }.filter { it != Categorias.INGRESO } + mov.categoria)
                    .filter { it.isNotBlank() }.distinct()
                SelectorChips(cats.map { it to it }, categoria) { categoria = it }
            } else {
                Text("Tipo", fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                SelectorChips(TipoIngreso.todos.map { it to it }, tipoIngreso) { tipoIngreso = it }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                enabled = (monto ?: 0) > 0 && fecha != null,
                onClick = {
                    val f = Fechas.iso(fecha!!)
                    if (mov != null) vm.guardar(mov.copy(monto = monto!!, fecha = f, nota = nota, categoria = categoria, liquida = esRecibo && completo, cuentaId = cuenta))
                    else if (ing != null) vm.guardar(ing.copy(monto = monto!!, fecha = f, nota = nota, tipo = tipoIngreso, cuentaId = cuenta))
                    alVolver()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Guardar cambios") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                confirmar.preguntar("Borrar", "¿Borrar este registro?") { borrarConDeshacer(mov ?: ing!!); alVolver() }
            }, modifier = Modifier.fillMaxWidth()) { Text("Borrar", color = Colores.rojo) }
        }
    }
}
