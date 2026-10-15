package com.jonathan.miscuentas.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jonathan.miscuentas.dominio.*
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PantallaRegistrar(vm: AppViewModel, datos: Datos, irGasto: () -> Unit, irIngreso: () -> Unit, irMonto: () -> Unit, irDeudas: () -> Unit, irDetalle: (Obligacion) -> Unit) {
    val hoy = vm.hoy()
    val mes = YearMonth.from(hoy)
    fun enMes(f: String) = Fechas.leer(f)?.let { YearMonth.from(it) == mes } ?: false
    val entroMes = datos.ingresos.filter { enMes(it.fecha) }.sumOf { it.monto }
    val salioMes = datos.movimientos.filter { enMes(it.fecha) }.sumOf { it.monto }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Hola 👋", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        // control de la plata
        TarjetaPlata(vm, datos, irDeudas)

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tarjeta(Modifier.weight(1f)) {
                Text("Entró este mes", fontSize = 12.sp)
                Text(Formato.pesos(entroMes), fontWeight = FontWeight.Bold, color = Colores.verde)
            }
            Tarjeta(Modifier.weight(1f)) {
                Text("Salió este mes", fontSize = 12.sp)
                Text(Formato.pesos(salioMes), fontWeight = FontWeight.Bold, color = Colores.rojo)
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("¿Qué vas a registrar?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonGrande("➖", "Gasto", Colores.rojo, Modifier.weight(1f), irGasto)
            BotonGrande("➕", "Ingreso", Colores.verde, Modifier.weight(1f), irIngreso)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                val moto = datos.categorias.firstOrNull { it.tipo == TipoEtiqueta.INGRESO && it.nombre == "Moto" }
                    ?: Categoria("et-moto", "Moto", false, "🏍", TipoEtiqueta.INGRESO)
                vm.seleccion = Seleccion.IngresoEtiqueta(moto); irMonto()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("🏍  + Moto", fontSize = 17.sp) }

        // todo lo de la quincena, no solo los primeros
        SeccionQuincena(vm, datos, irMonto, irDetalle)
        SeccionProximaQuincena(vm, datos, irMonto)
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
fun BotonGrande(icono: String, texto: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.height(110.dp), shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icono, fontSize = 30.sp)
            Text(texto, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun FilaPago(o: Obligacion, hoy: LocalDate, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(o.concepto, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${Formato.diaSemana(o.fecha)} ${Formato.fechaCorta(o.fecha)} · ${o.categoria}", fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Formato.pesos(o.falta), fontWeight = FontWeight.Bold)
                ChipEstado(o.estado(hoy))
            }
        }
    }
}

// cuadro de una etiqueta para tocar
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CuadroEtiqueta(emoji: String, nombre: String, detalle: String? = null, color: Color? = null, onClick: () -> Unit, onLargo: (() -> Unit)? = null) {
    Card(
        modifier = Modifier.fillMaxWidth().height(96.dp).combinedClickable(onClick = onClick, onLongClick = onLargo),
        colors = if (color != null) CardDefaults.cardColors(containerColor = color) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(emoji, fontSize = 26.sp)
            Text(nombre, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = if (detalle != null) 1 else 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
            if (detalle != null) Text(detalle, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val emojisGasto = listOf("🍔", "🛒", "⛽", "🧴", "🚌", "❤️", "🍕", "🎮", "💊", "👕", "🏠", "🐶", "🎁", "📦", "☕", "🍺", "🎓", "📱", "💡", "🚕", "✂️", "🏋️", "🎬", "🧾")
private val emojisIngreso = listOf("💼", "🏍", "🎄", "🤝", "💻", "💵", "🏦", "🎁", "📈", "🪙")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormNuevaEtiqueta(datos: Datos, tipo: String, inicial: Categoria? = null, alGuardar: (Categoria) -> Unit, alCancelar: () -> Unit, alBorrar: ((Categoria) -> Unit)? = null) {
    var nombre by remember { mutableStateOf(inicial?.nombre ?: "") }
    val emojis = if (tipo == TipoEtiqueta.INGRESO) emojisIngreso else emojisGasto
    var emoji by remember { mutableStateOf(inicial?.emoji ?: emojis.first()) }
    var presupuesto by remember { mutableStateOf(inicial?.presupuestoId) }
    Tarjeta {
        Text(if (inicial == null) "Nueva etiqueta" else "Editar etiqueta", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        CampoTexto("Nombre", nombre, { nombre = it })
        Text("Ícono", fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            emojis.forEach { e ->
                FilterChip(selected = e == emoji, onClick = { emoji = e }, label = { Text(e, fontSize = 18.sp) })
            }
        }
        if (tipo == TipoEtiqueta.GASTO && datos.presupuestos.isNotEmpty()) {
            Text("¿Descuenta de un presupuesto?", fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            SelectorChips(listOf("" to "No") + datos.presupuestos.filter { it.activo }.map { it.id to it.nombre }, presupuesto ?: "") {
                presupuesto = it.ifBlank { null }
            }
        }
        Row(Modifier.padding(top = 8.dp)) {
            TextButton(onClick = alCancelar) { Text("Cancelar") }
            if (inicial != null && alBorrar != null) TextButton(onClick = { alBorrar(inicial) }) { Text("Borrar", color = Colores.rojo) }
            Spacer(Modifier.weight(1f))
            Button(enabled = nombre.isNotBlank(), onClick = {
                val orden = (datos.categorias.maxOfOrNull { it.orden } ?: 0) + 1
                alGuardar(
                    (inicial ?: Categoria(nuevoId(), nombre.trim(), false, emoji, tipo, orden))
                        .copy(nombre = nombre.trim(), emoji = emoji, presupuestoId = presupuesto)
                )
            }) { Text("Guardar") }
        }
    }
}

@Composable
fun PantallaEtiquetasGasto(vm: AppViewModel, datos: Datos, alVolver: () -> Unit, irMonto: () -> Unit) {
    val hoy = vm.hoy()
    val mes = YearMonth.from(hoy)
    val confirmar = LocalConfirmador.current
    var creando by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf<Categoria?>(null) }
    val etiquetas = datos.categorias.filter { it.tipo == TipoEtiqueta.GASTO }.sortedBy { it.orden }
    val pagos = Obligaciones.generar(datos, mes.atDay(1).minusMonths(1), mes.atEndOfMonth())
        .filter { it.falta > 0 && it.tipoOrigen != Origen.PRESUPUESTO && (it.mes == mes || it.fecha < hoy) }

    Column(Modifier.fillMaxSize()) {
        Encabezado("¿En qué gastaste?", alVolver)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (creando || editando != null) {
                item(span = { GridItemSpan(3) }) {
                    FormNuevaEtiqueta(datos, TipoEtiqueta.GASTO, editando,
                        alGuardar = { vm.guardar(it); creando = false; editando = null },
                        alCancelar = { creando = false; editando = null },
                        alBorrar = { et ->
                            confirmar.preguntar("Borrar etiqueta", "¿Borrar \"${et.nombre}\"? Los gastos ya registrados no se borran.") {
                                vm.borrar(et); editando = null
                            }
                        })
                }
            }
            item(span = { GridItemSpan(3) }) { Text("Etiquetas  ·  mantén presionada para editar", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(etiquetas, key = { it.id }) { et ->
                val detalle = et.presupuestoId?.let { pid ->
                    Obligaciones.instanciaPresupuesto(datos, pid, hoy)?.let { "quedan ${Formato.pesos(it.presupuestado - it.pagado)}" }
                }
                CuadroEtiqueta(et.emoji, et.nombre, detalle, onClick = { vm.seleccion = Seleccion.Etiqueta(et); irMonto() }, onLargo = { editando = et; creando = false })
            }
            item {
                CuadroEtiqueta("＋", "Nueva etiqueta", color = MaterialTheme.colorScheme.secondaryContainer, onClick = { creando = true; editando = null })
            }
            if (pagos.isNotEmpty()) {
                item(span = { GridItemSpan(3) }) { TituloSeccion("Pagos de este mes") }
                items(pagos, key = { it.clave }) { o ->
                    val vencido = o.fecha < hoy
                    CuadroEtiqueta(
                        if (o.tipoOrigen == Origen.DEUDA) "💳" else "📅", o.concepto,
                        "${Formato.fechaCorta(o.fecha)} · ${Formato.pesos(o.falta)}",
                        color = if (vencido) MaterialTheme.colorScheme.tertiaryContainer else null,
                        onClick = { vm.seleccion = Seleccion.Pago(o); irMonto() },
                    )
                }
            }
            val deudas = datos.deudas.filter { it.activa && Calculos.saldoEstimado(it, datos, hoy) > 0 }
            if (deudas.isNotEmpty()) {
                item(span = { GridItemSpan(3) }) { TituloSeccion("Abono extra a una deuda") }
                items(deudas, key = { "ab-" + it.id }) { d ->
                    CuadroEtiqueta("⬇️", d.nombre, Formato.pesos(Calculos.saldoEstimado(d, datos, hoy)), onClick = { vm.seleccion = Seleccion.AbonoDeuda(d); irMonto() })
                }
            }
        }
    }
}

@Composable
fun PantallaEtiquetasIngreso(vm: AppViewModel, datos: Datos, alVolver: () -> Unit, irMonto: () -> Unit) {
    val hoy = vm.hoy()
    val mes = YearMonth.from(hoy)
    val confirmar = LocalConfirmador.current
    var creando by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf<Categoria?>(null) }
    val etiquetas = datos.categorias.filter { it.tipo == TipoEtiqueta.INGRESO }.sortedBy { it.orden }
    val esperados = Obligaciones.ingresosEsperados(datos, mes.atDay(1).minusMonths(1), mes.plusMonths(1).atEndOfMonth())
        .filter { it.recibido < it.monto && it.fecha <= hoy.plusDays(20) }

    Column(Modifier.fillMaxSize()) {
        Encabezado("¿Qué te entró?", alVolver)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (creando || editando != null) {
                item(span = { GridItemSpan(3) }) {
                    FormNuevaEtiqueta(datos, TipoEtiqueta.INGRESO, editando,
                        alGuardar = { vm.guardar(it); creando = false; editando = null },
                        alCancelar = { creando = false; editando = null },
                        alBorrar = { et ->
                            confirmar.preguntar("Borrar etiqueta", "¿Borrar \"${et.nombre}\"?") { vm.borrar(et); editando = null }
                        })
                }
            }
            if (esperados.isNotEmpty()) {
                item(span = { GridItemSpan(3) }) { Text("Ingresos esperados (toca para marcarlo recibido)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(esperados, key = { it.clave }) { e ->
                    CuadroEtiqueta(if (e.tipo == TipoIngreso.SALARIO) "💼" else "📥", e.concepto, "${Formato.fechaCorta(e.fecha)} · ${Formato.pesos(e.monto - e.recibido)}",
                        color = MaterialTheme.colorScheme.primaryContainer,
                        onClick = { vm.seleccion = Seleccion.IngresoEsperadoSel(e); irMonto() })
                }
            }
            item(span = { GridItemSpan(3) }) { TituloSeccion("Etiquetas") }
            items(etiquetas, key = { it.id }) { et ->
                CuadroEtiqueta(et.emoji, et.nombre, onClick = { vm.seleccion = Seleccion.IngresoEtiqueta(et); irMonto() }, onLargo = { editando = et; creando = false })
            }
            item {
                CuadroEtiqueta("＋", "Nueva etiqueta", color = MaterialTheme.colorScheme.secondaryContainer, onClick = { creando = true; editando = null })
            }
        }
    }
}

// solo el monto y guardar
@Composable
fun PantallaMonto(vm: AppViewModel, datos: Datos, alVolver: () -> Unit, alGuardar: (String) -> Unit) {
    // se guarda al entrar; al limpiar la seleccion despues de guardar esta pantalla no debe volver otra vez
    val sel = remember { vm.seleccion } ?: run { LaunchedEffect(Unit) { alVolver() }; return }
    val esIngreso = sel is Seleccion.IngresoEtiqueta || sel is Seleccion.IngresoEsperadoSel
    val (emoji, titulo, sugerido) = when (sel) {
        is Seleccion.Etiqueta -> Triple(sel.etiqueta.emoji, sel.etiqueta.nombre, null)
        is Seleccion.Pago -> Triple(if (sel.obligacion.tipoOrigen == Origen.DEUDA) "💳" else "📅", sel.obligacion.concepto, sel.obligacion.falta)
        is Seleccion.IngresoEtiqueta -> Triple(sel.etiqueta.emoji, sel.etiqueta.nombre, null)
        is Seleccion.IngresoEsperadoSel -> Triple("💼", sel.esperado.concepto, sel.esperado.monto - sel.esperado.recibido)
        is Seleccion.AbonoDeuda -> Triple("⬇️", "Abono a ${sel.deuda.nombre}", null)
    }
    var monto by remember { mutableStateOf(sugerido) }
    var fecha by remember { mutableStateOf<LocalDate?>(vm.hoy()) }
    var nota by remember { mutableStateOf("") }
    // recibos y tarjetas: se paga completo aunque el valor cambie, o como abono
    val esRecibo = sel is Seleccion.Pago && sel.obligacion.tipoOrigen != Origen.PRESUPUESTO
    var completo by remember { mutableStateOf(true) }
    // evita guardar dos veces con doble toque
    var guardado by remember { mutableStateOf(false) }
    var cuenta by remember { mutableStateOf(Saldos.porDefecto(datos)) }
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        Encabezado(if (esIngreso) "Nuevo ingreso" else "Nuevo gasto", alVolver)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 34.sp)
                Spacer(Modifier.width(10.dp))
                Text(titulo, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            if (sel is Seleccion.Pago) {
                Text("Se paga el ${Formato.fecha(Fechas.iso(sel.obligacion.fecha))} · presupuestado ${Formato.pesos(sel.obligacion.estimado)} · falta ${Formato.pesos(sel.obligacion.falta)}", fontSize = 13.sp)
            }
            if (sel is Seleccion.Etiqueta) {
                sel.etiqueta.presupuestoId?.let { pid ->
                    Obligaciones.instanciaPresupuesto(datos, pid, fecha ?: vm.hoy())?.let { inst ->
                        Spacer(Modifier.height(6.dp))
                        Text("${inst.concepto}: ${Formato.pesos(inst.pagado)} de ${Formato.pesos(inst.presupuestado)}", fontSize = 13.sp)
                        BarraProgreso(inst.pagado + (monto ?: 0), inst.presupuestado)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            CampoPesos("¿Cuánto fue?", monto, { monto = it }, Modifier.focusRequester(foco), grande = true)
            if (esRecibo) {
                Text("¿Cómo quedó este pago?", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                SelectorChips(listOf("si" to "✓ Pago completo", "no" to "Es un abono"), if (completo) "si" else "no") { completo = it == "si" }
                Text(
                    if (completo) "Queda pagado aunque el valor sea distinto (ej. recibo de luz o tarjeta que llegó por otro valor)."
                    else "Queda debiendo lo que falta para completar ${Formato.pesos((sel as Seleccion.Pago).obligacion.falta)}.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SelectorCuenta(datos, if (esIngreso) "¿A qué cuenta entró?" else "¿De dónde salió?", cuenta) { cuenta = it }
            CampoFecha("Fecha", fecha, { fecha = it })
            CampoTexto("Nota (opcional)", nota, { nota = it })
            Spacer(Modifier.height(16.dp))
            Button(
                enabled = (monto ?: 0) > 0 && fecha != null && !guardado,
                onClick = {
                    if (guardado) return@Button
                    val m = monto ?: return@Button
                    val f = fecha ?: return@Button
                    guardado = true
                    if (esIngreso) vm.registrarIngreso(sel, m, f, nota, cuenta) else vm.registrarGasto(sel, m, f, nota, esRecibo && completo, cuenta)
                    vm.seleccion = null
                    alGuardar((if (esIngreso) "Ingreso" else "Gasto") + " guardado: ${Formato.pesos(m)} · $titulo")
                },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (esIngreso) Colores.verde else MaterialTheme.colorScheme.primary),
            ) { Text("Guardar", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
