package com.jonathan.miscuentas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jonathan.miscuentas.dominio.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// saldo proyectado dia a dia con la linea del cero; punteada si llegara la moto
@Composable
fun LineaSaldo(proy: Proyeccion, color: Color) {
    val valores = proy.dias.map { it.saldoAlCierre }
    if (valores.size < 2) return
    val otros = proy.conIncierto
    val todos = valores + otros + 0L
    val maximo = todos.max()
    val minimo = todos.min()
    val rango = (maximo - minimo).coerceAtLeast(1)
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val iSueldo = proy.dias.indexOfFirst { it.fecha == proy.proximoSueldo }
    Column {
        Canvas(Modifier.fillMaxWidth().height(90.dp).padding(vertical = 4.dp)) {
            val paso = size.width / (valores.size - 1)
            fun y(v: Long) = size.height - size.height * (v - minimo) / rango
            drawLine(gris, Offset(0f, y(0)), Offset(size.width, y(0)), strokeWidth = 2f)
            if (iSueldo >= 0) drawLine(gris.copy(alpha = 0.4f), Offset(iSueldo * paso, 0f), Offset(iSueldo * paso, size.height), strokeWidth = 2f)
            fun camino(lista: List<Long>) = Path().apply {
                lista.forEachIndexed { i, v -> if (i == 0) moveTo(0f, y(v)) else lineTo(i * paso, y(v)) }
            }
            if (otros.size == valores.size) {
                drawPath(camino(otros), Colores.azul, style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
            }
            drawPath(camino(valores), color, style = Stroke(width = 5f))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(Formato.fechaCorta(proy.dias.first().fecha), fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text("sueldo ${Formato.fechaCorta(proy.proximoSueldo)}", fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(Formato.fechaCorta(proy.dias.last().fecha), fontSize = 11.sp)
        }
        if (otros.isNotEmpty()) Leyenda(listOf("Seguro" to color, "Con moto" to Colores.azul))
    }
}

// la tarjeta grande: llegas o no llegas al proximo sueldo
@Composable
fun TarjetaProyeccion(datos: Datos, proy: Proyeccion, hoy: LocalDate, irPosponer: () -> Unit, irPuente: () -> Unit, irMoto: () -> Unit) {
    val falta = proy.faltanteHastaSueldo
    val dia = proy.proximoSueldo.dayOfMonth
    val color = if (falta > 0) Colores.rojo else Colores.verde
    Tarjeta(color = color.copy(alpha = 0.12f)) {
        if (falta > 0) {
            val peor = proy.dias.filter { it.fecha < proy.proximoSueldo }.minByOrNull { it.saldoAlCierre }
            Text("Te faltan ${Formato.pesos(falta)} para llegar al $dia", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Colores.rojo)
            peor?.let { Text("El ${Formato.fecha(Fechas.iso(it.fecha))} te quedas en ${Formato.pesos(it.saldoAlCierre)}", fontSize = 13.sp) }
        } else {
            Text("Llegas al $dia con ${Formato.pesos(proy.libreHastaSueldo)} libres", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Colores.verde)
            Text("Puedes gastar ${Formato.pesos(proy.puedesGastarPorDia)} por día", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Proyecciones.libreSinBolsillo(datos, hoy, proy)?.let { Text("Libre sin contar el bolsillo: ${Formato.pesos(it)}", fontSize = 13.sp) }
        proy.llegasConIncierto?.let { Text("Con moto: llegas con ${Formato.pesos(it)}", fontSize = 13.sp, color = Colores.azul) }
        if (proy.colchon > 0) Text("Tienes ${Formato.pesos(proy.colchon)} de emergencia (no cuenta como libre)", fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        LineaSaldo(proy, color)
        if (falta > 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = irPosponer, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Posponer un pago", fontSize = 12.sp) }
                OutlinedButton(onClick = irPuente, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Usar tarjeta", fontSize = 12.sp) }
                OutlinedButton(onClick = irMoto, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Registrar moto", fontSize = 12.sp) }
            }
            Text("Si haces moto: cada ${Formato.pesos(50000)} reducen el faltante", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// bolsillo por apartar, llego el sueldo, moto, colchon y cupos
@Composable
fun TarjetasDelPlan(vm: AppViewModel, datos: Datos, proy: Proyeccion, hoy: LocalDate, irSueldo: () -> Unit, completo: Boolean = true) {
    val c = datos.config
    Proyecciones.bolsilloPorApartar(datos, hoy)?.let { monto ->
        val nombre = datos.cuentas.firstOrNull { it.id == c.bolsilloCuenta }?.nombre ?: "bolsillo"
        Spacer(Modifier.height(8.dp))
        Tarjeta(color = Colores.amarillo.copy(alpha = 0.15f)) {
            Text("Aparta ${Formato.pesos(monto)} al $nombre", fontWeight = FontWeight.Bold)
            Text("Es para la quincena siguiente, que carga más pagos.", fontSize = 12.sp)
            Button(onClick = { vm.apartar(c.bolsilloCuenta!!, monto) }, Modifier.padding(top = 4.dp)) { Text("Apartar ahora") }
        }
    }
    val p = Obligaciones.periodo(datos, hoy)
    if (Proyecciones.sueldoRecibido(datos, p)) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = irSueldo, modifier = Modifier.fillMaxWidth()) {
            Text("💼 Llegó el sueldo" + (p.diaSueldo?.let { " del $it" } ?: "") + ": qué pagar y en qué orden")
        }
    }
    if (!completo) return
    val mes = YearMonth.from(hoy)
    val moto = datos.ingresos.filter { Fechas.leer(it.fecha)?.let { f -> YearMonth.from(f) == mes } == true && it.tipo == TipoIngreso.MOTO }.sumOf { it.monto }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Cifra("Moto este mes", Formato.pesos(moto), Colores.azul, Modifier.weight(1f))
        Cifra("Moto para cubrir el faltante", Formato.pesos(proy.faltanteHastaSueldo), if (proy.faltanteHastaSueldo > 0) Colores.rojo else Colores.verde, Modifier.weight(1f))
    }
    if (c.metaColchon > 0) {
        Spacer(Modifier.height(8.dp))
        Tarjeta {
            Text("🛟 Colchón ${Formato.pesos(proy.colchon)} de ${Formato.pesos(c.metaColchon)}", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            BarraProgreso(proy.colchon, c.metaColchon, avance = true)
        }
    }
    val conCupo = datos.tarjetas.filter { it.cupoTotal != null }
    if (conCupo.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Tarjeta {
            Text("💳 Cupos", fontWeight = FontWeight.Bold)
            conCupo.forEach { t -> FilaCupo(datos, t, hoy) }
        }
    }
}

@Composable
fun FilaCupo(datos: Datos, t: Tarjeta, hoy: LocalDate) {
    val libre = Tarjetas.cupoDisponible(datos, t, hoy)
    val uso = Tarjetas.uso(datos, t, hoy)
    val rojo = (uso ?: 0.0) > 0.8
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t.nombre + if (!t.usoPermitido) " 🔒" else "", Modifier.weight(1f), fontSize = 13.sp)
        Text("${libre?.let { Formato.pesos(it) } ?: "?"} de ${t.cupoTotal?.let { Formato.pesos(it) } ?: "?"}", fontSize = 13.sp)
        uso?.let { Text("  ${Formato.porcentaje(it)}", fontSize = 12.sp, color = if (rojo) Colores.rojo else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold) }
    }
}

// elegir un pago pendiente y moverlo a otra fecha
@Composable
fun PantallaPosponer(vm: AppViewModel, datos: Datos, alVolver: () -> Unit) {
    val hoy = vm.hoy()
    val per = Obligaciones.periodo(datos, hoy)
    val inicio = Fechas.leer(datos.config.fechaInicio) ?: hoy
    val pendientes = Obligaciones.generar(datos, inicio, per.hasta).filter { it.falta > 0 && it.tipoOrigen != Origen.PRESUPUESTO }
    var elegido by remember { mutableStateOf<Obligacion?>(null) }
    var fecha by remember { mutableStateOf<LocalDate?>(per.hasta.plusDays(1)) }
    var nota by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Encabezado("Posponer un pago", alVolver)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("El pago conserva lo que ya abonaste; solo cambia la fecha en que lo cuentas.", fontSize = 13.sp)
            elegido?.let { o ->
                Tarjeta(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(o.concepto, fontWeight = FontWeight.Bold)
                    Text("Hoy cae el ${Formato.fecha(Fechas.iso(o.fecha))} · falta ${Formato.pesos(o.falta)}", fontSize = 13.sp)
                    CampoFecha("Nueva fecha", fecha, { fecha = it }, atajos = false)
                    CampoTexto("Nota (opcional)", nota, { nota = it })
                    Row {
                        TextButton(onClick = { elegido = null }) { Text("Cancelar") }
                        Spacer(Modifier.weight(1f))
                        Button(enabled = fecha != null, onClick = { vm.posponer(o.clave, fecha!!, nota); elegido = null }) { Text("Posponer") }
                    }
                }
            }
            TituloSeccion("Pendientes hasta el ${Formato.fechaCorta(per.hasta)}")
            if (pendientes.isEmpty()) Vacio("No hay pagos pendientes.")
            pendientes.forEach { o -> FilaPago(o, hoy) { elegido = o; nota = "" } }
            val movidos = datos.pospuestos
            if (movidos.isNotEmpty()) {
                TituloSeccion("Pagos pospuestos")
                movidos.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${p.clave.split(':').getOrNull(1) ?: p.clave} · del ${Formato.fecha(p.clave.substringAfterLast(':'))} al ${Formato.fecha(p.fechaNueva)}", Modifier.weight(1f), fontSize = 13.sp)
                        TextButton(onClick = { vm.borrar(p) }) { Text("Quitar") }
                    }
                }
            }
            Spacer(Modifier.height(60.dp))
        }
    }
}

// lista de verificacion el dia que llega el sueldo
@Composable
fun PantallaSueldo(vm: AppViewModel, datos: Datos, alVolver: () -> Unit, irMonto: () -> Unit, irPosponer: () -> Unit) {
    fun irPagar(o: Obligacion) { vm.seleccion = Seleccion.Pago(o); irMonto() }
    val hoy = vm.hoy()
    val per = Obligaciones.periodo(datos, hoy)
    val proy = remember(datos) { Proyecciones.calcular(datos, hoy) }
    val confirmar = LocalConfirmador.current
    val inicio = Fechas.leer(datos.config.fechaInicio) ?: hoy
    val delPeriodo = Obligaciones.pagosQuincena(datos, hoy).filter { it.falta > 0 }
    val puentes = delPeriodo.filter { it.tipoOrigen == Origen.PUENTE }
    val atrasados = Obligaciones.generar(datos, inicio, per.desde.minusDays(1)).filter { it.falta > 0 && it.tipoOrigen != Origen.PRESUPUESTO }
    val pospuestos = delPeriodo.filter { it.fechaOriginal != null && it.tipoOrigen != Origen.PUENTE }
    val resto = delPeriodo.filter { it.tipoOrigen != Origen.PUENTE && it.fechaOriginal == null }
    val bolsillo = Proyecciones.bolsilloPorApartar(datos, hoy)
    Column(Modifier.fillMaxSize()) {
        Encabezado("Llegó el sueldo" + (per.diaSueldo?.let { " del $it" } ?: ""), alVolver)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Hazlo en este orden. Toca un pago para registrarlo (puedes cambiar el valor).", fontSize = 13.sp)
            Paso(1, "Apartar el bolsillo", bolsillo == null) {
                if (bolsillo != null) {
                    Text("Aparta ${Formato.pesos(bolsillo)} para la otra quincena.", fontSize = 13.sp)
                    Button(onClick = { vm.apartar(datos.config.bolsilloCuenta!!, bolsillo) }) { Text("Apartar ${Formato.pesos(bolsillo)}") }
                } else Text("No aplica o ya está apartado.", fontSize = 13.sp)
            }
            Paso(2, "Pagar los puentes de tarjeta", puentes.isEmpty()) {
                if (puentes.isEmpty()) Text("No hay puentes por pagar antes del próximo sueldo.", fontSize = 13.sp)
                puentes.forEach { o -> FilaPago(o, hoy) { irPagar(o) } }
            }
            Paso(3, "Atrasados y pospuestos", atrasados.isEmpty() && pospuestos.isEmpty()) {
                if (atrasados.isEmpty() && pospuestos.isEmpty()) Text("Nada atrasado.", fontSize = 13.sp)
                (atrasados + pospuestos).forEach { o -> FilaPago(o, hoy) { irPagar(o) } }
            }
            Paso(4, "Pagos de esta quincena", resto.isEmpty()) {
                if (resto.isEmpty()) Text("Todo pagado.", fontSize = 13.sp)
                resto.forEach { o -> FilaPago(o, hoy) { irPagar(o) } }
                val todos = puentes + atrasados + pospuestos + resto
                if (todos.size > 1) OutlinedButton(onClick = {
                    confirmar.preguntar("Pagar todo", "Se registran ${todos.size} pagos por ${Formato.pesos(todos.sumOf { it.falta })} con la fecha de hoy.", "Pagar todo") {
                        vm.pagarTodo(todos, hoy)
                    }
                }, Modifier.fillMaxWidth()) { Text("Pagar todo lo pendiente") }
                TextButton(onClick = irPosponer) { Text("Posponer alguno") }
            }
            Paso(5, "Lo que queda", false) {
                val presu = datos.presupuestos.filter { it.activo }.mapNotNull { Proyecciones.presupuestoReal(datos, it.id, hoy, proy) }
                val queda = proy.libreHastaSueldo + presu.sumOf { it.realDisponible }
                Text("Te quedan ${Formato.pesos(queda)} para el día a día (presupuestos y lo libre) hasta el ${Formato.fechaCorta(proy.proximoSueldo.minusDays(1))}.", fontWeight = FontWeight.Bold)
                if (proy.faltanteHastaSueldo > 0) Text("Ojo: te faltan ${Formato.pesos(proy.faltanteHastaSueldo)} para llegar al sueldo.", color = Colores.rojo, fontSize = 13.sp)
            }
            Proyecciones.sugerencia(datos, hoy, proy)?.let { s ->
                Paso(6, if (s.alColchon > 0) "Completar el colchón" else "Abono extra", false) {
                    if (s.alColchon > 0) {
                        Text("Aparta ${Formato.pesos(s.alColchon)} al colchón antes de abonar a deudas.", fontSize = 13.sp)
                        datos.config.colchonCuenta?.let { cc -> Button(onClick = { vm.apartar(cc, s.alColchon) }) { Text("Apartar al colchón") } }
                    } else s.deuda?.let { e ->
                        Text("Abono extra sugerido a ${e.deuda.nombre}: ${Formato.pesos(s.abono)}", fontWeight = FontWeight.Bold)
                        val antes = s.terminaDespues?.let { Formato.mes(it) }
                        val ahora = s.terminaAntes?.let { Formato.mes(it) }
                        if (ahora != null && antes != null) Text("Terminas en $ahora (antes $antes)" + (s.ahorroIntereses?.let { " y ahorras ${Formato.pesos(it)} de intereses" } ?: ""), fontSize = 13.sp)
                        Button(onClick = { vm.seleccion = Seleccion.AbonoDeuda(e.deuda); irMonto() }) { Text("Registrar abono") }
                    }
                }
            }
            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
private fun Paso(n: Int, titulo: String, listo: Boolean, contenido: @Composable ColumnScope.() -> Unit) {
    Tarjeta(Modifier.padding(vertical = 5.dp)) {
        Text("${if (listo) "✓" else "$n."} $titulo", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (listo) Colores.verde else Color.Unspecified)
        Spacer(Modifier.height(4.dp))
        contenido()
    }
}

// pausas de un gasto fijo o presupuesto
@Composable
fun SeccionPausas(vm: AppViewModel, datos: Datos, tipo: String, id: String) {
    val hoy = vm.hoy()
    val pausas = datos.pausas.filter { it.tipoOrigen == tipo && it.origenId == id }
    var abierto by remember { mutableStateOf(false) }
    var desde by remember { mutableStateOf<LocalDate?>(hoy) }
    var hasta by remember { mutableStateOf<LocalDate?>(YearMonth.from(hoy).plusMonths(1).atEndOfMonth()) }
    TituloSeccion("Pausas")
    pausas.forEach { p ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pausado del ${Formato.fecha(p.desde)} al ${Formato.fecha(p.hasta)}" + if (p.nota.isNotBlank()) " · ${p.nota}" else "", Modifier.weight(1f), fontSize = 13.sp)
            TextButton(onClick = { vm.borrar(p) }) { Text("Quitar pausa") }
        }
    }
    if (!abierto) TextButton(onClick = { abierto = true }) { Text("⏸ Pausar") }
    else {
        CampoFecha("Desde", desde, { desde = it }, atajos = false)
        CampoFecha("Hasta", hasta, { hasta = it }, atajos = false)
        Row {
            TextButton(onClick = { abierto = false }) { Text("Cancelar") }
            Button(enabled = desde != null && hasta != null && hasta!! >= desde!!, onClick = { vm.pausar(tipo, id, desde!!, hasta!!, ""); abierto = false }) { Text("Pausar") }
        }
    }
}

// administrar tarjetas: cupo, cuotas, si cobra interes y si esta permitida
@Composable
fun PantallaTarjetas(vm: AppViewModel, datos: Datos, alVolver: () -> Unit) {
    val hoy = vm.hoy()
    val confirmar = LocalConfirmador.current
    var editando by remember { mutableStateOf<Tarjeta?>(null) }
    var nueva by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Encabezado("Tarjetas", alVolver) { TextButton(onClick = { nueva = true; editando = null }) { Text("+ Nueva") } }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Una compra con tarjeta no saca plata de tus cuentas: sube la deuda y deja un pago (puente) para después.", fontSize = 13.sp)
            if (nueva || editando != null) key(editando?.id) {
                FormTarjeta(datos, editando, alGuardar = { vm.guardar(it); nueva = false; editando = null }, alCancelar = { nueva = false; editando = null },
                    alBorrar = { t -> confirmar.preguntar("Borrar tarjeta", "¿Borrar ${t.nombre}? Las compras ya registradas se conservan.") { vm.borrar(t); editando = null } })
            }
            datos.tarjetas.sortedBy { it.nombre }.forEach { t ->
                Card(onClick = { editando = t; nueva = false }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(t.nombre + when {
                            !t.usoPermitido -> " · 🔒 bloqueada"
                            t.preferidaPuente -> " · ⭐ para puentes"
                            t.soloTiendas -> " · solo tiendas"
                            else -> ""
                        }, fontWeight = FontWeight.Bold)
                        FilaCupo(datos, t, hoy)
                        Text((if (t.interes1Cuota) "Cobra interés aun a 1 cuota" else "1 cuota sin interés") + " · por defecto ${t.cuotasPorDefecto ?: 1} cuotas", fontSize = 12.sp)
                        if (t.notas.isNotBlank()) Text(t.notas, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
private fun FormTarjeta(datos: Datos, inicial: Tarjeta?, alGuardar: (Tarjeta) -> Unit, alCancelar: () -> Unit, alBorrar: (Tarjeta) -> Unit) {
    var nombre by remember { mutableStateOf(inicial?.nombre ?: "") }
    var deuda by remember { mutableStateOf(inicial?.deudaId ?: "") }
    var cupoTotal by remember { mutableStateOf(inicial?.cupoTotal) }
    var cupoDisp by remember { mutableStateOf(inicial?.cupoDisponible) }
    var interes by remember { mutableStateOf(inicial?.interes1Cuota ?: true) }
    var cuotas by remember { mutableStateOf((inicial?.cuotasPorDefecto ?: 1).toString()) }
    var permitida by remember { mutableStateOf(inicial?.usoPermitido ?: true) }
    var tiendas by remember { mutableStateOf(inicial?.soloTiendas ?: false) }
    var puente by remember { mutableStateOf(inicial?.preferidaPuente ?: false) }
    var notas by remember { mutableStateOf(inicial?.notas ?: "") }
    Tarjeta(Modifier.padding(vertical = 6.dp)) {
        Text(if (inicial == null) "Nueva tarjeta" else "Editar tarjeta", fontWeight = FontWeight.Bold)
        CampoTexto("Nombre", nombre, { nombre = it })
        Text("Deuda donde se suma lo que compras", fontSize = 13.sp)
        SelectorChips(listOf("" to "Ninguna") + datos.deudas.map { it.id to it.nombre }, deuda) { deuda = it }
        CampoPesos("Cupo total", cupoTotal, { cupoTotal = it })
        CampoPesos("Cupo disponible hoy (del banco)", cupoDisp, { cupoDisp = it })
        CampoTexto("Cuotas por defecto", cuotas, { cuotas = it.filter { c -> c.isDigit() }.take(2) }, numerico = true)
        Interruptor("Cobra interés aunque sea a 1 cuota", interes) { interes = it }
        Interruptor("Uso permitido en mi plan", permitida) { permitida = it }
        Interruptor("Solo sirve en tiendas", tiendas) { tiendas = it }
        Interruptor("Es la que uso como puente", puente) { puente = it }
        CampoTexto("Notas", notas, { notas = it }, lineas = 2)
        Row {
            TextButton(onClick = alCancelar) { Text("Cancelar") }
            if (inicial != null) TextButton(onClick = { alBorrar(inicial) }) { Text("Borrar", color = Colores.rojo) }
            Spacer(Modifier.weight(1f))
            Button(enabled = nombre.isNotBlank(), onClick = {
                val cupoCambio = cupoDisp != inicial?.cupoDisponible
                alGuardar((inicial ?: Tarjeta(nuevoId(), nombre = nombre.trim())).copy(
                    nombre = nombre.trim(), deudaId = deuda.ifBlank { null }, cupoTotal = cupoTotal, cupoDisponible = cupoDisp,
                    fechaCupo = if (cupoCambio) Fechas.iso(LocalDate.now()) else inicial?.fechaCupo,
                    interes1Cuota = interes, cuotasPorDefecto = cuotas.toIntOrNull()?.coerceIn(1, 72), usoPermitido = permitida,
                    soloTiendas = tiendas, preferidaPuente = puente, notas = notas,
                ))
            }) { Text("Guardar") }
        }
    }
}

// dias entre hoy y una fecha, minimo 1
fun diasHasta(hoy: LocalDate, f: LocalDate): Int = maxOf(1, ChronoUnit.DAYS.between(hoy, f).toInt())

// presupuesto con lo que queda y lo que de verdad alcanza hasta el sueldo
@Composable
fun TarjetaPresupuesto(r: PresupuestoReal, proy: Proyeccion, onClick: () -> Unit) {
    val p = r.obligacion
    Tarjeta(Modifier.padding(vertical = 3.dp), onClick = onClick) {
        Text("${p.concepto}: ${Formato.pesos(p.pagado)} de ${Formato.pesos(p.presupuestado)}", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        if (r.noAlcanza) BarraRoja(p.pagado, p.presupuestado) else BarraProgreso(p.pagado, p.presupuestado)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("Queda ${Formato.pesos(r.restante)}", Modifier.weight(1f), fontSize = 13.sp)
            Text("Real disponible ${Formato.pesos(r.realDisponible)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (r.noAlcanza) Colores.rojo else Colores.verde)
        }
        if (r.noAlcanza) Text("Te quedan ${Formato.pesos(r.restante)} de presupuesto, pero solo alcanza para ${Formato.pesos(r.realDisponible)} (${Formato.pesos(r.porDia)} por día)",
            fontSize = 12.sp, color = Colores.rojo)
        if (proy.faltanteHastaSueldo > 0) Text("No alcanza: cualquier gasto aquí sale de tarjeta o de moto", fontSize = 12.sp, color = Colores.rojo, fontWeight = FontWeight.Bold)
        if (p.pagado > p.presupuestado) Text("Te pasaste por ${Formato.pesos(p.pagado - p.presupuestado)}", color = Colores.rojo, fontSize = 13.sp)
    }
}

@Composable
private fun BarraRoja(usado: Long, total: Long) {
    val pct = if (total > 0) (usado.toFloat() / total).coerceIn(0f, 1f) else 0f
    LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth().height(10.dp), color = Colores.rojo, trackColor = Colores.rojo.copy(alpha = 0.2f))
}

// con que se pago: una cuenta (sale plata) o una tarjeta (queda de puente)
@Composable
fun SelectorMedio(datos: Datos, cuenta: String?, tarjetaId: String?, alElegir: (String?, String?) -> Unit) {
    val cuentas = Saldos.activas(datos)
    val opciones = cuentas.map { "c:${it.id}" to "${it.emoji} ${it.nombre}" } +
        datos.tarjetas.sortedBy { !it.preferidaPuente }.map { "t:${it.id}" to "💳 ${it.nombre}" + if (!it.usoPermitido) " 🔒" else "" }
    val actual = tarjetaId?.let { "t:$it" } ?: cuenta?.let { "c:$it" }
    Text("¿Con qué pagaste?", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    SelectorChips(opciones, actual) { v ->
        if (v.startsWith("t:")) alElegir(null, v.removePrefix("t:")) else alElegir(v.removePrefix("c:"), null)
    }
}

// avisos y costo de pagar con tarjeta
@Composable
fun DetalleTarjeta(
    datos: Datos, t: Tarjeta, monto: Long, cuotas: Int, alCuotas: (Int) -> Unit,
    fechaPago: LocalDate?, alFecha: (LocalDate?) -> Unit,
    entiendo: Boolean, alEntiendo: (Boolean) -> Unit, seguro: Boolean, alSeguro: (Boolean) -> Unit,
) {
    val aviso = Tarjetas.aviso(datos, t, monto)
    if (aviso.bloqueada) {
        Tarjeta(Modifier.padding(vertical = 6.dp), color = Colores.rojo.copy(alpha = 0.15f)) {
            Text("🔒 Tarjeta bloqueada", fontWeight = FontWeight.Bold, color = Colores.rojo, fontSize = 17.sp)
            Text(aviso.mensaje ?: "", color = Colores.rojo)
            Interruptor("Entiendo lo que cuesta", entiendo, alEntiendo)
            if (entiendo) Interruptor("Sí, quiero usarla igual", seguro, alSeguro)
        }
    } else if (aviso.soloTiendas) {
        Text("⚠ ${aviso.mensaje}", color = Colores.amarillo, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
    Text("Cuotas", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    val opciones = (listOf(1, 2, 3, 6, 12, 24, 36) + (t.cuotasPorDefecto ?: 1)).distinct().sorted()
    SelectorChips(opciones.map { it.toString() to it.toString() }, cuotas.toString()) { alCuotas(it.toInt()) }
    if (monto > 0) {
        val c = Tarjetas.costo(datos, t, monto, cuotas)
        if (cuotas == 1 && c.sinInteres) {
            Text("Sin intereses si pagas ${Formato.pesos(monto)}" + (fechaPago?.let { " antes del ${Formato.fechaCorta(it)}" } ?: ""),
                fontSize = 13.sp, color = Colores.verde, fontWeight = FontWeight.Bold)
        } else {
            Text("Cuota ${Formato.pesos(c.cuota)} · total ${Formato.pesos(c.total)} · te cuesta ${Formato.pesos(c.intereses)} más",
                fontSize = 13.sp, color = if (c.intereses > 0) Colores.rojo else Color.Unspecified, fontWeight = FontWeight.Bold)
        }
    }
    if (cuotas == 1) CampoFecha("¿Cuándo lo vas a pagar?", fechaPago, alFecha, atajos = false)
    else Text("Se crean $cuotas pagos mensuales en el día de pago de la tarjeta.", fontSize = 12.sp)
    Text("No sale plata de tus cuentas hoy: sube la deuda de ${t.nombre} y queda el pago como puente.", fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
