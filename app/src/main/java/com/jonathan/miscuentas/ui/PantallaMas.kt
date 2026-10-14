package com.jonathan.miscuentas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jonathan.miscuentas.dominio.*
import java.time.LocalDate
import java.time.YearMonth

object TipoLista {
    const val GASTO = "gasto"
    const val PRESUPUESTO = "presupuesto"
    const val PROGRAMADO = "programado"
    const val EVENTO = "evento"
    const val ETIQUETA = "etiqueta"

    fun titulo(t: String) = when (t) {
        GASTO -> "Gastos fijos"
        PRESUPUESTO -> "Presupuestos"
        PROGRAMADO -> "Ingresos programados"
        EVENTO -> "Eventos"
        else -> "Etiquetas"
    }
}

@Composable
fun PantallaMas(irDeudas: () -> Unit, irLista: (String) -> Unit, irReparto: () -> Unit, irConfig: () -> Unit, irDatos: () -> Unit, irFechas: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Más", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        OpcionMenu("📆", "Fechas de pago", "Ver y corregir el día de pago de todo", irFechas)
        OpcionMenu("💳", "Deudas", "Saldos, simulador y plan de ataque", irDeudas)
        OpcionMenu("🏠", "Gastos fijos", "Arriendo, servicios, semanales…") { irLista(TipoLista.GASTO) }
        OpcionMenu("🎯", "Presupuestos", "Topes por quincena, mes o rango") { irLista(TipoLista.PRESUPUESTO) }
        OpcionMenu("💼", "Ingresos programados", "Salario, prima, préstamos…") { irLista(TipoLista.PROGRAMADO) }
        OpcionMenu("📅", "Eventos", "Pagos únicos con fecha") { irLista(TipoLista.EVENTO) }
        OpcionMenu("🏷", "Etiquetas", "Las que tocas para registrar") { irLista(TipoLista.ETIQUETA) }
        OpcionMenu("➗", "Reparto por quincena", "Cuánto sobra en la 15 y en la 30", irReparto)
        OpcionMenu("⚙", "Configuración", "Plata inicial, avisos, borrar todo", irConfig)
        OpcionMenu("📤", "Exportar / importar TXT", "Compartir todo o cargar un archivo", irDatos)
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun OpcionMenu(emoji: String, titulo: String, detalle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 24.sp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(titulo, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(detalle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PantallaLista(vm: AppViewModel, datos: Datos, tipo: String, alVolver: () -> Unit, irEditar: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Encabezado(TipoLista.titulo(tipo), alVolver) { TextButton(onClick = { irEditar("nuevo") }) { Text("+ Nuevo") } }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            when (tipo) {
                TipoLista.GASTO -> {
                    if (datos.gastosFijos.isEmpty()) Vacio("No hay gastos fijos.")
                    datos.gastosFijos.forEach { g ->
                        val cuando = when (g.frecuencia) {
                            Frecuencia.SEMANAL -> "cada ${Formato.nombreDiaSemana(g.diaSemana ?: 6)}"
                            Frecuencia.QUINCENAL -> "días 15 y 30"
                            else -> "día ${g.diaPago ?: 1}"
                        }
                        ItemLista(g.nombre, "${Formato.pesos(g.valor)} · $cuando · quincena ${g.quincenaAsignada}" +
                            (if (!g.diaConfirmado) " · ⚠ confirma este día" else "") + (if (!g.activo) " · inactivo" else "")) { irEditar(g.id) }
                    }
                }
                TipoLista.PRESUPUESTO -> {
                    if (datos.presupuestos.isEmpty()) Vacio("No hay presupuestos.")
                    datos.presupuestos.forEach { p ->
                        val cuando = when (p.periodicidad) {
                            Periodicidad.UNICO -> "${Formato.fecha(p.fechaDesde ?: p.fecha)} a ${Formato.fecha(p.fechaHasta ?: p.fecha)}"
                            else -> p.periodicidad
                        }
                        ItemLista(p.nombre, "${Formato.pesos(p.monto)} · $cuando · ${p.categoria}" + if (!p.activo) " · inactivo" else "") { irEditar(p.id) }
                    }
                }
                TipoLista.PROGRAMADO -> {
                    if (datos.ingresosProgramados.isEmpty()) Vacio("No hay ingresos programados.")
                    datos.ingresosProgramados.forEach { i ->
                        val cuando = if (i.frecuencia == FrecuenciaIngreso.UNICO) Formato.fecha(i.fecha) else "día ${i.dia}"
                        ItemLista(i.nombre, "${Formato.pesos(i.monto)} · $cuando · ${i.tipo}" + (if (i.esEstimado) " · estimado" else "") + if (!i.activo) " · inactivo" else "") { irEditar(i.id) }
                    }
                }
                TipoLista.EVENTO -> {
                    if (datos.eventos.isEmpty()) Vacio("No hay eventos.")
                    datos.eventos.sortedBy { it.fecha }.forEach { e ->
                        ItemLista(e.nombre, "${Formato.pesos(e.monto)} · ${Formato.fecha(e.fecha)} · ${e.categoria}") { irEditar(e.id) }
                    }
                }
                else -> {
                    Text("Gastos", fontWeight = FontWeight.Bold)
                    datos.categorias.filter { it.tipo == TipoEtiqueta.GASTO }.forEach { c ->
                        val pres = c.presupuestoId?.let { pid -> datos.presupuestos.firstOrNull { it.id == pid }?.nombre }
                        ItemLista("${c.emoji} ${c.nombre}", pres?.let { "descuenta de $it" } ?: "") { irEditar(c.id) }
                    }
                    Text("Ingresos", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                    datos.categorias.filter { it.tipo == TipoEtiqueta.INGRESO }.forEach { c -> ItemLista("${c.emoji} ${c.nombre}", "") { irEditar(c.id) } }
                    Text("Del sistema (no se borran)", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                    Text(datos.categorias.filter { it.esSistema }.joinToString(" · ") { it.nombre }, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
private fun ItemLista(titulo: String, detalle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(titulo, fontWeight = FontWeight.Bold)
            if (detalle.isNotBlank()) Text(detalle, fontSize = 13.sp)
        }
    }
}

private fun categoriasElegibles(datos: Datos): List<Pair<String, String>> =
    datos.categorias.filter { it.esSistema || it.tipo == TipoEtiqueta.GASTO }.map { it.nombre to it.nombre }.distinctBy { it.first }

@Composable
fun PantallaEditar(vm: AppViewModel, datos: Datos, tipo: String, id: String, alVolver: () -> Unit) {
    val confirmar = LocalConfirmador.current
    Column(Modifier.fillMaxSize()) {
        Encabezado((if (id == "nuevo") "Nuevo: " else "Editar: ") + TipoLista.titulo(tipo).lowercase(), alVolver)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            when (tipo) {
                TipoLista.GASTO -> EditarGasto(vm, datos.gastosFijos.firstOrNull { it.id == id }, alVolver) { g ->
                    confirmar.preguntar("Borrar", "¿Borrar ${g.nombre}?") { vm.borrar(g); alVolver() }
                }
                TipoLista.PRESUPUESTO -> EditarPresupuesto(vm, datos, datos.presupuestos.firstOrNull { it.id == id }, alVolver) { p ->
                    confirmar.preguntar("Borrar", "¿Borrar ${p.nombre}?") { vm.borrar(p); alVolver() }
                }
                TipoLista.PROGRAMADO -> EditarProgramado(vm, datos.ingresosProgramados.firstOrNull { it.id == id }, alVolver) { i ->
                    confirmar.preguntar("Borrar", "¿Borrar ${i.nombre}?") { vm.borrar(i); alVolver() }
                }
                TipoLista.EVENTO -> EditarEvento(vm, datos, datos.eventos.firstOrNull { it.id == id }, alVolver) { e ->
                    confirmar.preguntar("Borrar", "¿Borrar ${e.nombre}?") { vm.borrar(e); alVolver() }
                }
                else -> {
                    val c = datos.categorias.firstOrNull { it.id == id }
                    var tipoEt by remember { mutableStateOf(c?.tipo ?: TipoEtiqueta.GASTO) }
                    if (c == null) SelectorChips(listOf(TipoEtiqueta.GASTO to "Gasto", TipoEtiqueta.INGRESO to "Ingreso"), tipoEt) { tipoEt = it }
                    key(tipoEt) {
                        FormNuevaEtiqueta(datos, tipoEt, c, alGuardar = { vm.guardar(it); alVolver() }, alCancelar = alVolver,
                            alBorrar = { et -> confirmar.preguntar("Borrar", "¿Borrar ${et.nombre}?") { vm.borrar(et); alVolver() } })
                    }
                }
            }
            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
private fun BotonesGuardar(puede: Boolean, existe: Boolean, guardar: () -> Unit, borrar: () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Button(enabled = puede, onClick = guardar, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Guardar") }
    if (existe) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = borrar, modifier = Modifier.fillMaxWidth()) { Text("Borrar", color = Colores.rojo) }
    }
}

@Composable
private fun EditarGasto(vm: AppViewModel, g: GastoFijo?, alVolver: () -> Unit, borrar: (GastoFijo) -> Unit) {
    var nombre by remember { mutableStateOf(g?.nombre ?: "") }
    var valor by remember { mutableStateOf(g?.valor) }
    var frecuencia by remember { mutableStateOf(g?.frecuencia ?: Frecuencia.MENSUAL) }
    var dia by remember { mutableStateOf(g?.diaPago?.toString() ?: "1") }
    var diaSemana by remember { mutableStateOf((g?.diaSemana ?: 6).toString()) }
    var quincena by remember { mutableStateOf(g?.quincenaAsignada ?: Quincena.Q30) }
    var activo by remember { mutableStateOf(g?.activo ?: true) }
    var notas by remember { mutableStateOf(g?.notas ?: "") }
    if (g != null && !g.diaConfirmado) Text("⚠ Confirma el día de pago y guarda para quitar este aviso", color = Colores.amarillo)
    CampoTexto("Nombre", nombre, { nombre = it })
    CampoPesos("Valor", valor, { valor = it })
    Text("Frecuencia", fontSize = 13.sp)
    SelectorChips(Frecuencia.todas.map { it to it }, frecuencia) { frecuencia = it }
    when (frecuencia) {
        Frecuencia.SEMANAL -> {
            Text("Día de la semana", fontSize = 13.sp)
            SelectorChips((0..6).map { it.toString() to Formato.nombreDiaSemana(it) }, diaSemana) { diaSemana = it }
        }
        Frecuencia.MENSUAL -> CampoTexto("Día de pago (1-31)", dia, { dia = it.filter { c -> c.isDigit() }.take(2) }, numerico = true)
        else -> Text("Se paga los días 15 y 30 (o el último día del mes)", fontSize = 13.sp)
    }
    Text("Quincena asignada", fontSize = 13.sp)
    SelectorChips(Quincena.todas.map { it to it }, quincena) { quincena = it }
    Interruptor("Activo", activo) { activo = it }
    CampoTexto("Notas", notas, { notas = it }, lineas = 2)
    BotonesGuardar(nombre.isNotBlank() && (valor ?: 0) > 0, g != null, {
        vm.guardar((g ?: GastoFijo(nuevoId(), nombre, 0)).copy(
            nombre = nombre.trim(), valor = valor ?: 0, frecuencia = frecuencia,
            diaPago = if (frecuencia == Frecuencia.MENSUAL) dia.toIntOrNull()?.coerceIn(1, 31) ?: 1 else null,
            diaSemana = if (frecuencia == Frecuencia.SEMANAL) diaSemana.toIntOrNull() else null,
            quincenaAsignada = quincena, activo = activo, notas = notas, diaConfirmado = true,
        ))
        alVolver()
    }) { g?.let(borrar) }
}

@Composable
private fun EditarPresupuesto(vm: AppViewModel, datos: Datos, p: Presupuesto?, alVolver: () -> Unit, borrar: (Presupuesto) -> Unit) {
    var nombre by remember { mutableStateOf(p?.nombre ?: "") }
    var monto by remember { mutableStateOf(p?.monto) }
    var periodicidad by remember { mutableStateOf(p?.periodicidad ?: Periodicidad.QUINCENAL) }
    var desde by remember { mutableStateOf(Fechas.leer(p?.fechaDesde ?: p?.fecha)) }
    var hasta by remember { mutableStateOf(Fechas.leer(p?.fechaHasta)) }
    var categoria by remember { mutableStateOf(p?.categoria ?: Categorias.COMIDA_TRANSPORTE) }
    var activo by remember { mutableStateOf(p?.activo ?: true) }
    var notas by remember { mutableStateOf(p?.notas ?: "") }
    CampoTexto("Nombre", nombre, { nombre = it })
    CampoPesos("Monto", monto, { monto = it })
    Text("Periodicidad", fontSize = 13.sp)
    SelectorChips(Periodicidad.todas.map { it to it }, periodicidad) { periodicidad = it }
    CampoFecha(if (periodicidad == Periodicidad.UNICO) "Fecha (o desde)" else "Desde (opcional)", desde, { desde = it }, permitirVacio = true, atajos = false)
    CampoFecha("Hasta (opcional)", hasta, { hasta = it }, permitirVacio = true, atajos = false)
    Text("Categoría", fontSize = 13.sp)
    SelectorChips(categoriasElegibles(datos), categoria) { categoria = it }
    Interruptor("Activo", activo) { activo = it }
    CampoTexto("Notas", notas, { notas = it }, lineas = 2)
    BotonesGuardar(nombre.isNotBlank() && (monto ?: 0) > 0 && (periodicidad != Periodicidad.UNICO || desde != null), p != null, {
        vm.guardar((p ?: Presupuesto(nuevoId(), nombre, 0)).copy(
            nombre = nombre.trim(), monto = monto ?: 0, periodicidad = periodicidad,
            fecha = if (periodicidad == Periodicidad.UNICO && hasta == null) desde?.let { Fechas.iso(it) } else null,
            fechaDesde = desde?.let { Fechas.iso(it) }, fechaHasta = hasta?.let { Fechas.iso(it) },
            categoria = categoria, activo = activo, notas = notas,
        ))
        alVolver()
    }) { p?.let(borrar) }
}

@Composable
private fun EditarProgramado(vm: AppViewModel, i: IngresoProgramado?, alVolver: () -> Unit, borrar: (IngresoProgramado) -> Unit) {
    var nombre by remember { mutableStateOf(i?.nombre ?: "") }
    var monto by remember { mutableStateOf(i?.monto) }
    var frecuencia by remember { mutableStateOf(i?.frecuencia ?: FrecuenciaIngreso.QUINCENAL_DIA) }
    var dia by remember { mutableStateOf(i?.dia?.toString() ?: "15") }
    var fecha by remember { mutableStateOf(Fechas.leer(i?.fecha)) }
    var tipo by remember { mutableStateOf(i?.tipo ?: TipoIngreso.SALARIO) }
    var estimado by remember { mutableStateOf(i?.esEstimado ?: false) }
    var activo by remember { mutableStateOf(i?.activo ?: true) }
    var notas by remember { mutableStateOf(i?.notas ?: "") }
    CampoTexto("Nombre", nombre, { nombre = it })
    CampoPesos("Monto", monto, { monto = it })
    Text("Frecuencia", fontSize = 13.sp)
    SelectorChips(listOf(FrecuenciaIngreso.QUINCENAL_DIA to "cada mes en un día (quincena)", FrecuenciaIngreso.MENSUAL to "mensual", FrecuenciaIngreso.UNICO to "único"), frecuencia) { frecuencia = it }
    if (frecuencia == FrecuenciaIngreso.UNICO) CampoFecha("Fecha", fecha, { fecha = it }, atajos = false)
    else CampoTexto("Día del mes (ej. 15 o 30)", dia, { dia = it.filter { c -> c.isDigit() }.take(2) }, numerico = true)
    Text("Tipo", fontSize = 13.sp)
    SelectorChips(TipoIngreso.todos.map { it to it }, tipo) { tipo = it }
    Interruptor("Es estimado", estimado) { estimado = it }
    Interruptor("Activo", activo) { activo = it }
    CampoTexto("Notas", notas, { notas = it }, lineas = 2)
    BotonesGuardar(nombre.isNotBlank() && (monto ?: 0) > 0 && (frecuencia != FrecuenciaIngreso.UNICO || fecha != null), i != null, {
        vm.guardar((i ?: IngresoProgramado(nuevoId(), nombre, 0)).copy(
            nombre = nombre.trim(), monto = monto ?: 0, frecuencia = frecuencia,
            dia = if (frecuencia == FrecuenciaIngreso.UNICO) null else dia.toIntOrNull()?.coerceIn(1, 31) ?: 15,
            fecha = if (frecuencia == FrecuenciaIngreso.UNICO) fecha?.let { Fechas.iso(it) } else null,
            tipo = tipo, esEstimado = estimado, activo = activo, notas = notas,
        ))
        alVolver()
    }) { i?.let(borrar) }
}

@Composable
private fun EditarEvento(vm: AppViewModel, datos: Datos, e: Evento?, alVolver: () -> Unit, borrar: (Evento) -> Unit) {
    var nombre by remember { mutableStateOf(e?.nombre ?: "") }
    var monto by remember { mutableStateOf(e?.monto) }
    var fecha by remember { mutableStateOf(Fechas.leer(e?.fecha)) }
    var categoria by remember { mutableStateOf(e?.categoria ?: Categorias.NO_PLANEADO) }
    var deuda by remember { mutableStateOf(e?.deudaId ?: "") }
    var notas by remember { mutableStateOf(e?.notas ?: "") }
    CampoTexto("Nombre", nombre, { nombre = it })
    CampoPesos("Monto", monto, { monto = it })
    CampoFecha("Fecha", fecha, { fecha = it }, atajos = false)
    Text("Categoría", fontSize = 13.sp)
    SelectorChips(categoriasElegibles(datos), categoria) { categoria = it }
    Text("¿Es pago de una deuda?", fontSize = 13.sp)
    SelectorChips(listOf("" to "No") + datos.deudas.map { it.id to it.nombre }, deuda) { deuda = it }
    CampoTexto("Notas", notas, { notas = it }, lineas = 2)
    BotonesGuardar(nombre.isNotBlank() && (monto ?: 0) > 0 && fecha != null, e != null, {
        vm.guardar((e ?: Evento(nuevoId(), nombre, 0, Fechas.iso(fecha!!))).copy(
            nombre = nombre.trim(), monto = monto ?: 0, fecha = Fechas.iso(fecha!!), categoria = categoria,
            deudaId = deuda.ifBlank { null }, notas = notas,
        ))
        alVolver()
    }) { e?.let(borrar) }
}

@Composable
fun PantallaReparto(vm: AppViewModel, datos: Datos, alVolver: () -> Unit) {
    var mes by remember { mutableStateOf(YearMonth.from(vm.hoy())) }
    val q = Calculos.reparto(datos, mes)
    Column(Modifier.fillMaxSize()) {
        Encabezado("Reparto por quincena", alVolver)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { mes = mes.minusMonths(1) }) { Text("‹") }
                Text(Formato.mes(mes), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                TextButton(onClick = { mes = mes.plusMonths(1) }) { Text("›") }
            }
            BloqueQuincena("Quincena 15", q.ingresos15, q.deudas15, q.gastos15, q.presupuestos15, q.sobra15)
            Spacer(Modifier.height(10.dp))
            BloqueQuincena("Quincena 30", q.ingresos30, q.deudas30, q.gastos30, q.presupuestos30, q.sobra30)
            Text("Deudas con día de pago entre el 3 y el 17 van en la quincena 15; el resto en la 30. Los gastos semanales se calculan con ${String.format(java.util.Locale.US, "%.2f", datos.config.semanasPorMes)} semanas por mes.",
                fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun BloqueQuincena(titulo: String, ing: Long, deu: Long, gas: Long, pre: Long, sobra: Long) {
    Tarjeta {
        Text(titulo, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        FilaDato("Ingresos", Formato.pesos(ing), Colores.verde)
        FilaDato("− Deudas", Formato.pesos(deu))
        FilaDato("− Gastos fijos", Formato.pesos(gas))
        FilaDato("− Presupuestos", Formato.pesos(pre))
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        FilaDato("Sobra", Formato.pesos(sobra), if (sobra >= 0) Colores.verde else Colores.rojo, negrita = true)
    }
}

@Composable
fun PantallaConfig(vm: AppViewModel, datos: Datos, alVolver: () -> Unit, pedirPermiso: () -> Unit) {
    val c = datos.config
    val confirmar = LocalConfirmador.current
    var plata by remember { mutableStateOf<Long?>(c.plataInicial) }
    var inicio by remember { mutableStateOf(Fechas.leer(c.fechaInicio)) }
    var dias by remember { mutableStateOf(c.diasAviso.toString()) }
    var hora by remember { mutableStateOf(c.horaAviso) }
    var noche by remember { mutableStateOf(c.recordatorioDiario) }
    var semanas by remember { mutableStateOf(String.format(java.util.Locale.US, "%.4f", c.semanasPorMes)) }
    Column(Modifier.fillMaxSize()) {
        Encabezado("Configuración", alVolver)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            CampoPesos("Plata que tenías en la fecha de inicio", plata, { plata = it })
            CampoFecha("Fecha de inicio del control", inicio, { inicio = it }, atajos = false)
            CampoTexto("Días antes del pago para avisar", dias, { dias = it.filter { ch -> ch.isDigit() }.take(2) }, numerico = true)
            CampoTexto("Hora del aviso (HH:MM)", hora, { hora = it.take(5) })
            CampoTexto("Semanas por mes", semanas, { semanas = it }, numerico = true)
            Interruptor("Aviso a las 9:00 p. m.: ¿registraste tus gastos?", noche) { noche = it }
            TextButton(onClick = pedirPermiso) { Text("Permitir notificaciones") }
            Spacer(Modifier.height(10.dp))
            Button(modifier = Modifier.fillMaxWidth().height(52.dp), onClick = {
                // acepta 7:00 o 07:00 y lo guarda como 07:00
                val horaOk = runCatching { java.time.LocalTime.parse(hora.trim(), java.time.format.DateTimeFormatter.ofPattern("H:mm")) }.getOrNull()
                vm.guardar(c.copy(
                    plataInicial = plata ?: 0, fechaInicio = inicio?.let { Fechas.iso(it) } ?: c.fechaInicio,
                    diasAviso = dias.toIntOrNull() ?: c.diasAviso, horaAviso = horaOk?.toString() ?: c.horaAviso,
                    semanasPorMes = semanas.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: c.semanasPorMes, recordatorioDiario = noche,
                ))
                alVolver()
            }) { Text("Guardar") }
            Spacer(Modifier.height(30.dp))
            Text("Zona de peligro", fontWeight = FontWeight.Bold, color = Colores.rojo)
            OutlinedButton(onClick = {
                confirmar.preguntar("Borrar todo", "Se borrarán todos los movimientos, ingresos y cambios. ¿Seguro?", "Sí, continuar") {
                    confirmar.preguntar("¿Completamente seguro?", "Esta acción no se puede deshacer. Antes se guarda un respaldo automático.", "Borrar todo") {
                        vm.borrarTodo(); alVolver()
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Borrar todo", color = Colores.rojo) }
        }
    }
}

@Composable
fun PantallaDatos(exportar: () -> Unit, importar: () -> Unit, resultado: String?, respaldos: List<String>, alVolver: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Encabezado("Exportar / importar", alVolver)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Tarjeta {
                Text("Exportar todo", fontWeight = FontWeight.Bold)
                Text("Genera un TXT con todos tus datos y abre el menú de compartir (WhatsApp, Drive, correo…).", fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Button(onClick = exportar, Modifier.fillMaxWidth()) { Text("📤 Exportar todo") }
            }
            Spacer(Modifier.height(12.dp))
            Tarjeta {
                Text("Importar", fontWeight = FontWeight.Bold)
                Text("Reemplaza todo lo actual con el contenido del archivo. Antes se guarda un respaldo automático.", fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = importar, Modifier.fillMaxWidth()) { Text("📥 Importar TXT") }
            }
            resultado?.let {
                Spacer(Modifier.height(12.dp))
                Tarjeta(color = MaterialTheme.colorScheme.secondaryContainer) { Text(it, fontSize = 13.sp) }
            }
            TituloSeccion("Respaldos automáticos en la app")
            if (respaldos.isEmpty()) Vacio("Aún no hay respaldos.")
            respaldos.forEach { Text("• $it", fontSize = 13.sp) }
        }
    }
}

// todas las fechas de pago en un solo lugar para confirmarlas o cambiarlas
@Composable
fun PantallaFechasPago(vm: AppViewModel, datos: Datos, alVolver: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Encabezado("Fechas de pago", alVolver)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Cambia el día y toca Guardar. Los pagos ya hechos se conservan en su mes.", fontSize = 13.sp)
            val pendientesConfirmar = datos.gastosFijos.count { it.activo && !it.diaConfirmado }
            if (pendientesConfirmar > 0) {
                Text("⚠ $pendientesConfirmar gastos con el día sin confirmar", color = Colores.amarillo, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            }

            TituloSeccion("Deudas")
            datos.deudas.filter { it.activa }.sortedBy { it.diaPago ?: 99 }.forEach { d ->
                when {
                    d.cuotaMensual > 0 -> FilaDia(d.nombre, Formato.pesos(d.cuotaMensual), d.diaPago, true) { dia ->
                        vm.guardar(d.copy(diaPago = dia))
                    }
                    d.fechaFin != null -> FilaDato(d.nombre, "pago único ${Formato.fecha(d.fechaFin)}")
                    else -> FilaDato(d.nombre, "sin fecha fija")
                }
            }

            TituloSeccion("Gastos fijos")
            datos.gastosFijos.filter { it.activo }.forEach { g ->
                when (g.frecuencia) {
                    Frecuencia.MENSUAL -> FilaDia(g.nombre, Formato.pesos(g.valor), g.diaPago, g.diaConfirmado) { dia ->
                        vm.guardar(g.copy(diaPago = dia, diaConfirmado = true))
                    }
                    else -> {
                        val cuando = if (g.frecuencia == Frecuencia.SEMANAL) "cada ${Formato.nombreDiaSemana(g.diaSemana ?: 6)}" else "días 15 y 30"
                        if (g.diaConfirmado) FilaDato(g.nombre, cuando)
                        else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(g.nombre, fontWeight = FontWeight.Bold)
                                Text("$cuando · ⚠ confirma este día", fontSize = 12.sp, color = Colores.amarillo)
                            }
                            TextButton(onClick = { vm.guardar(g.copy(diaConfirmado = true)) }) { Text("Confirmar") }
                        }
                    }
                }
            }

            TituloSeccion("Ingresos")
            datos.ingresosProgramados.filter { it.activo }.forEach { i ->
                if (i.frecuencia == FrecuenciaIngreso.UNICO) FilaDato(i.nombre, Formato.fecha(i.fecha))
                else FilaDia(i.nombre, Formato.pesos(i.monto), i.dia, true) { dia -> vm.guardar(i.copy(dia = dia)) }
            }

            TituloSeccion("Eventos")
            datos.eventos.sortedBy { it.fecha }.forEach { e -> FilaDato(e.nombre, Formato.fecha(e.fecha)) }
            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
private fun FilaDia(nombre: String, valor: String, dia: Int?, confirmado: Boolean, guardar: (Int) -> Unit) {
    var texto by remember(dia) { mutableStateOf(dia?.toString() ?: "") }
    val nuevo = texto.toIntOrNull()?.takeIf { it in 1..31 }
    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(nombre, fontWeight = FontWeight.Bold)
                Text(valor + if (!confirmado) " · ⚠ confirma este día" else "", fontSize = 12.sp,
                    color = if (!confirmado) Colores.amarillo else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = texto, onValueChange = { texto = it.filter { c -> c.isDigit() }.take(2) },
                label = { Text("día") }, singleLine = true, modifier = Modifier.width(76.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            )
            TextButton(enabled = nuevo != null && (nuevo != dia || !confirmado), onClick = { nuevo?.let(guardar) }) {
                Text(if (!confirmado && nuevo == dia) "Confirmar" else "Guardar")
            }
        }
    }
}
