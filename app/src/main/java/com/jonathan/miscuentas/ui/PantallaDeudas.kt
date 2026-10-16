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

private const val AVISO_ESTIMADO = "estimado: los intereses lo hacen mayor; actualiza con el extracto"

private fun tasaTexto(t: Double?) = t?.let { Formato.porcentaje(it) + " E.A." } ?: "sin tasa"

@Composable
fun PantallaDeudas(vm: AppViewModel, datos: Datos, alVolver: (() -> Unit)?, irDetalle: (String) -> Unit, irNueva: () -> Unit, irPlan: () -> Unit) {
    val hoy = vm.hoy()
    val estados = datos.deudas.map { Calculos.estadoDeuda(it, datos, hoy) }
        .sortedWith(compareByDescending<EstadoDeuda> { it.deuda.activa }.thenByDescending { it.saldoEstimado })
    Column(Modifier.fillMaxSize()) {
        Encabezado("Deudas", alVolver)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Cifra("Deuda total", Formato.pesos(Calculos.deudaTotal(datos, hoy)), Colores.rojo, Modifier.weight(1f))
                Cifra("Cuotas al mes", Formato.pesos(Calculos.cuotasMensuales(datos)), Colores.amarillo, Modifier.weight(1f))
            }
            Text(AVISO_ESTIMADO, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            if (datos.config.notaPlan.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Tarjeta(color = MaterialTheme.colorScheme.secondaryContainer) { Text("📌 " + datos.config.notaPlan, fontSize = 13.sp) }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = irPlan, Modifier.weight(1f)) { Text("Plan de ataque") }
                OutlinedButton(onClick = irNueva, Modifier.weight(1f)) { Text("+ Nueva deuda") }
            }
            Spacer(Modifier.height(8.dp))
            if (estados.isEmpty()) Vacio("No tienes deudas registradas.")
            estados.forEach { e ->
                val d = e.deuda
                Tarjeta(Modifier.padding(vertical = 4.dp), onClick = { irDetalle(d.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(d.nombre + if (!d.activa) " (inactiva)" else "", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(Formato.pesos(e.saldoEstimado), fontWeight = FontWeight.Bold, color = Colores.rojo)
                    }
                    Text(
                        "Cuota ${Formato.pesos(d.cuotaMensual)}" + (d.diaPago?.let { " · día $it" } ?: "") + " · " + tasaTexto(d.tasaEa),
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    BarraProgreso((e.porcentajePagado * 1000).toLong(), 1000)
                    Row(Modifier.padding(top = 4.dp)) {
                        Text("${Formato.porcentaje(e.porcentajePagado)} pagado", Modifier.weight(1f), fontSize = 12.sp)
                        Text(
                            when {
                                e.plazo.noBaja -> "no baja"
                                e.plazo.meses != null -> "${e.plazo.meses} meses" + if (e.plazo.sinTasa) " (sin tasa)" else ""
                                else -> "sin cuota"
                            }, fontSize = 12.sp,
                        )
                    }
                    e.interesMes?.takeIf { it > 0 }?.let { Text("Interés aprox. del mes: ${Formato.pesos(it)}", fontSize = 12.sp) }
                    datos.tarjetas.filter { it.deudaId == d.id }.forEach { t -> FilaCupo(datos, t, hoy) }
                    if (e.noBaja) Text("⚠ Con esta cuota la deuda no baja", color = Colores.rojo, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
fun PantallaDetalleDeuda(vm: AppViewModel, datos: Datos, id: String, alVolver: () -> Unit, irEditar: () -> Unit, irMonto: () -> Unit) {
    val hoy = vm.hoy()
    val d = datos.deudas.firstOrNull { it.id == id } ?: run { LaunchedEffect(Unit) { alVolver() }; return }
    val e = Calculos.estadoDeuda(d, datos, hoy)
    val confirmar = LocalConfirmador.current
    var nuevoSaldo by remember { mutableStateOf<Long?>(null) }
    var fechaSaldo by remember { mutableStateOf<LocalDate?>(hoy) }
    var meses by remember { mutableStateOf("12") }
    var abono by remember { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxSize()) {
        Encabezado(d.nombre, alVolver) { TextButton(onClick = irEditar) { Text("Editar") } }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Tarjeta {
                Text("Saldo estimado", fontSize = 13.sp)
                Text(Formato.pesos(e.saldoEstimado), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Colores.rojo)
                Text(AVISO_ESTIMADO, fontSize = 11.sp)
                Calculos.saldoConIntereses(d, datos, hoy)?.let { Text("Estimado con intereses: ${Formato.pesos(it)}", fontSize = 13.sp, color = Colores.rojo) }
                datos.tarjetas.filter { it.deudaId == d.id }.forEach { t -> FilaCupo(datos, t, hoy) }
                Spacer(Modifier.height(8.dp))
                FilaDato("Entidad", d.entidad.ifBlank { "—" })
                FilaDato("Tipo", d.tipo)
                FilaDato("Cuota mensual", Formato.pesos(d.cuotaMensual))
                FilaDato("Día de pago", d.diaPago?.toString() ?: "sin día fijo")
                FilaDato("Tasa", tasaTexto(d.tasaEa))
                d.cuotasTotales?.let { FilaDato("Cuotas", "${d.cuotasPagadas ?: "?"} de $it") }
                d.fechaFin?.let { FilaDato("Fecha fin", Formato.fecha(it)) }
                FilaDato("Pagado desde el inicio", Formato.pesos(e.pagadoDesdeInicio), Colores.verde)
                e.interesMes?.let { FilaDato("Interés aprox. del mes", Formato.pesos(it)) }
                e.abonoCapital?.let { FilaDato("Abono real a capital", Formato.pesos(it)) }
                if (e.noBaja) Text("⚠ Con esta cuota la deuda no baja", color = Colores.rojo, fontWeight = FontWeight.Bold)
                e.plazo.meses?.let { FilaDato("Meses para pagar", "$it" + if (e.plazo.sinTasa) " (sin tasa)" else "") }
                e.plazo.totalPagar?.let { FilaDato("Total a pagar", Formato.pesos(it)) }
                e.plazo.intereses?.takeIf { !e.plazo.sinTasa }?.let { FilaDato("Intereses totales", Formato.pesos(it)) }
                if (d.notas.isNotBlank()) Text("📝 " + d.notas, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { vm.seleccion = Seleccion.AbonoDeuda(d); irMonto() }, Modifier.fillMaxWidth()) { Text("Registrar abono extra") }

            TituloSeccion("Actualizar saldo con extracto")
            Tarjeta {
                CampoPesos("Saldo del extracto", nuevoSaldo, { nuevoSaldo = it })
                CampoFecha("Fecha del extracto", fechaSaldo, { fechaSaldo = it })
                Button(enabled = nuevoSaldo != null && fechaSaldo != null, onClick = {
                    vm.actualizarSaldoExtracto(d, nuevoSaldo!!, fechaSaldo!!); nuevoSaldo = null
                }) { Text("Guardar saldo") }
            }

            TituloSeccion("Historial de saldos")
            val historial = datos.deudaSaldos.filter { it.deudaId == d.id }.sortedByDescending { it.fecha }
            if (historial.isEmpty()) Vacio("Sin extractos registrados.")
            historial.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Formato.fecha(s.fecha), Modifier.weight(1f))
                    Text(Formato.pesos(s.saldo), fontWeight = FontWeight.Bold)
                    TextButton(onClick = { confirmar.preguntar("Borrar saldo", "¿Borrar el saldo del ${Formato.fecha(s.fecha)}?") { vm.borrar(s) } }) { Text("✕") }
                }
            }

            TituloSeccion("Pagos y abonos")
            val pagos = Calculos.pagosDeuda(d, datos.movimientos).sortedByDescending { it.fecha }
            if (pagos.isEmpty()) Vacio("Sin pagos todavía.")
            pagos.forEach { m -> FilaDato("${Formato.fecha(m.fecha)} · ${if (m.tipo == TipoMovimiento.ABONO_EXTRA) "abono" else "cuota"}", Formato.pesos(m.monto)) }

            TituloSeccion("Simulador")
            Tarjeta {
                CampoTexto("¿En cuántos meses quieres terminar?", meses, { meses = it.filter { c -> c.isDigit() }.take(3) }, numerico = true)
                meses.toIntOrNull()?.takeIf { it > 0 }?.let { x ->
                    val cuota = Calculos.cuotaParaMeses(e.saldoEstimado, e.tasaMensual, x)
                    Text("Cuota necesaria: ${Formato.pesos(cuota)} al mes", fontWeight = FontWeight.Bold)
                    if (e.tasaMensual == null) Text("sin tasa: se calcula sin intereses", fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                CampoPesos("Si abono extra hoy", abono, { abono = it })
                abono?.let { y ->
                    val (ahorro, menos) = Calculos.ahorroPorAbono(e.saldoEstimado, d.cuotaMensual, e.tasaMensual, y)
                    if (ahorro != null) Text("Ahorras ${Formato.pesos(ahorro)} en intereses" + (menos?.let { " y terminas $it meses antes" } ?: ""), fontWeight = FontWeight.Bold)
                    else Text("Con la cuota actual no se puede calcular el ahorro.", fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = {
                confirmar.preguntar("Borrar deuda", "¿Borrar ${d.nombre}? Los pagos ya registrados se conservan.") { vm.borrar(d); alVolver() }
            }, Modifier.fillMaxWidth()) { Text("Borrar deuda", color = Colores.rojo) }
            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
fun PantallaEditarDeuda(vm: AppViewModel, datos: Datos, id: String, alVolver: () -> Unit) {
    val actual = datos.deudas.firstOrNull { it.id == id }
    var nombre by remember { mutableStateOf(actual?.nombre ?: "") }
    var entidad by remember { mutableStateOf(actual?.entidad ?: "") }
    var tipo by remember { mutableStateOf(actual?.tipo ?: TipoDeuda.TARJETA) }
    var saldoInicial by remember { mutableStateOf(actual?.saldoInicial) }
    var cuota by remember { mutableStateOf(actual?.cuotaMensual) }
    var dia by remember { mutableStateOf(actual?.diaPago?.toString() ?: "") }
    var tasa by remember { mutableStateOf(actual?.tasaEa?.let { (it * 100).toString().replace('.', ',') } ?: "") }
    var totales by remember { mutableStateOf(actual?.cuotasTotales?.toString() ?: "") }
    var pagadas by remember { mutableStateOf(actual?.cuotasPagadas?.toString() ?: "") }
    var intereses by remember { mutableStateOf(actual?.generaIntereses ?: true) }
    var fin by remember { mutableStateOf(Fechas.leer(actual?.fechaFin)) }
    var activa by remember { mutableStateOf(actual?.activa ?: true) }
    var fijada by remember { mutableStateOf(actual?.fijadaArriba ?: false) }
    var notas by remember { mutableStateOf(actual?.notas ?: "") }

    Column(Modifier.fillMaxSize()) {
        Encabezado(if (actual == null) "Nueva deuda" else "Editar deuda", alVolver)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            CampoTexto("Nombre", nombre, { nombre = it })
            CampoTexto("Entidad", entidad, { entidad = it })
            Text("Tipo", fontSize = 13.sp)
            SelectorChips(TipoDeuda.todos.map { it to it.replace('_', ' ') }, tipo) { tipo = it; if (it == TipoDeuda.PERSONA) intereses = false }
            CampoPesos("Saldo inicial (en la fecha de inicio)", saldoInicial, { saldoInicial = it })
            CampoPesos("Cuota mensual (0 si no tiene)", cuota, { cuota = it })
            CampoTexto("Día de pago (1-31, vacío si no tiene)", dia, { dia = it.filter { c -> c.isDigit() }.take(2) }, numerico = true)
            CampoTexto("Tasa E.A. en % (ej. 28,59)", tasa, { tasa = it.filter { c -> c.isDigit() || c == ',' || c == '.' } }, numerico = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoTexto("Cuotas totales", totales, { totales = it.filter { c -> c.isDigit() } }, Modifier.weight(1f), numerico = true)
                CampoTexto("Cuotas pagadas", pagadas, { pagadas = it.filter { c -> c.isDigit() } }, Modifier.weight(1f), numerico = true)
            }
            CampoFecha("Fecha fin (opcional, ej. pago único)", fin, { fin = it }, permitirVacio = true, atajos = false)
            Interruptor("Genera intereses", intereses) { intereses = it }
            Interruptor("Activa", activa) { activa = it }
            Interruptor("Fijar arriba en el plan de ataque", fijada) { fijada = it }
            CampoTexto("Notas", notas, { notas = it }, lineas = 3)
            Spacer(Modifier.height(12.dp))
            Button(enabled = nombre.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp), onClick = {
                val base = actual ?: Deuda(nuevoId(), nombre)
                vm.guardar(
                    base.copy(
                        nombre = nombre.trim(), entidad = entidad.trim(), tipo = tipo, saldoInicial = saldoInicial ?: 0,
                        cuotaMensual = cuota ?: 0, diaPago = dia.toIntOrNull()?.coerceIn(1, 31),
                        tasaEa = tasa.replace(',', '.').toDoubleOrNull()?.let { it / 100 },
                        cuotasTotales = totales.toIntOrNull(), cuotasPagadas = pagadas.toIntOrNull(), generaIntereses = intereses,
                        fechaFin = fin?.let { Fechas.iso(it) }, activa = activa, fijadaArriba = fijada, notas = notas,
                    )
                )
                alVolver()
            }) { Text("Guardar") }
        }
    }
}

@Composable
fun PantallaPlan(vm: AppViewModel, datos: Datos, alVolver: () -> Unit) {
    val hoy = vm.hoy()
    val modo = datos.config.modoPlan
    val plan = Calculos.planDeAtaque(datos, modo, hoy)
    var nota by remember(datos.config.notaPlan) { mutableStateOf(datos.config.notaPlan) }
    Column(Modifier.fillMaxSize()) {
        Encabezado("Plan de ataque", alVolver)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            SelectorChips(listOf(ModoPlan.BOLA_NIEVE to "Bola de nieve (menor saldo)", ModoPlan.AVALANCHA to "Avalancha (mayor tasa)"), modo) {
                vm.guardar(datos.config.copy(modoPlan = it))
            }
            Text("Las deudas sin intereses van al final, salvo que las fijes arriba. Las de pago corto con fecha fin terminan solas.", fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            if (plan.isEmpty()) Vacio("No hay deudas para el plan.")
            plan.forEachIndexed { i, e ->
                Tarjeta(
                    Modifier.padding(vertical = 4.dp),
                    color = if (i == 0) MaterialTheme.colorScheme.primaryContainer else null,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}.", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.deuda.nombre, fontWeight = FontWeight.Bold)
                            Text("${Formato.pesos(e.saldoEstimado)} · ${tasaTexto(e.deuda.tasaEa)}", fontSize = 12.sp)
                        }
                        TextButton(onClick = { vm.guardar(e.deuda.copy(fijadaArriba = !e.deuda.fijadaArriba)) }) {
                            Text(if (e.deuda.fijadaArriba) "Soltar" else "Fijar")
                        }
                    }
                    if (i == 0) Text("👉 Aquí va cada peso extra", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            TituloSeccion("Nota del plan")
            CampoTexto("Nota", nota, { nota = it }, lineas = 3)
            Button(onClick = { vm.guardar(datos.config.copy(notaPlan = nota)) }) { Text("Guardar nota") }
            Spacer(Modifier.height(60.dp))
        }
    }
}
