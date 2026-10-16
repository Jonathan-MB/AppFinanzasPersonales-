package com.jonathan.miscuentas.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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

// deberias tener, cuanto hay en cada cuenta, cuadre y envios entre cuentas
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TarjetaPlata(vm: AppViewModel, datos: Datos, irDeudas: () -> Unit) {
    val hoy = vm.hoy()
    val deberia = Calculos.deberiaTener(datos)
    val debo = Calculos.deudaTotal(datos, hoy)
    val cuentas = Saldos.activas(datos)
    val saldos = Saldos.saldos(datos)
    val cuadre = Saldos.ultimoCuadre(datos)
    var abierto by remember { mutableStateOf("") }
    val sobre = MaterialTheme.colorScheme.onPrimaryContainer

    Tarjeta(color = MaterialTheme.colorScheme.primaryContainer) {
        Text("Deberías tener hoy", color = sobre)
        Text(Formato.pesos(deberia), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = sobre)
        Text("Lo que tenías + lo que te han pagado − lo que has gastado", fontSize = 12.sp, color = sobre)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                cuentas.forEach { c ->
                    val s = saldos[c.id] ?: 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${c.emoji} ${c.nombre}", fontSize = 13.sp, color = sobre, modifier = Modifier.weight(1f))
                        Text(Formato.pesos(s), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (s < 0) Colores.rojo else sobre)
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.combinedClickable(onClick = irDeudas)) {
                Text("Debo", fontSize = 12.sp, color = sobre)
                Text(Formato.pesos(debo), fontWeight = FontWeight.Bold, color = Colores.rojo)
                Text("ver deudas ›", fontSize = 11.sp, color = sobre)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                cuadre == null -> "Aún no has cuadrado con tus cuentas"
                cuadre.desfase == 0L -> "Último cuadre ${Formato.fecha(cuadre.fecha)}: cuadró exacto"
                cuadre.desfase < 0 -> "Último cuadre ${Formato.fecha(cuadre.fecha)}: desfase de ${Formato.pesos(-cuadre.desfase)} de menos"
                else -> "Último cuadre ${Formato.fecha(cuadre.fecha)}: desfase de ${Formato.pesos(cuadre.desfase)} de más"
            },
            fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = when {
                cuadre == null || cuadre.desfase == 0L -> sobre
                cuadre.desfase < 0 -> Colores.rojo
                else -> Colores.verde
            },
        )
        Row {
            TextButton(onClick = { abierto = if (abierto == "cuadrar") "" else "cuadrar" }, contentPadding = PaddingValues(end = 12.dp)) { Text("⚖️ Cuadrar con mis cuentas") }
            TextButton(onClick = { abierto = if (abierto == "mover") "" else "mover" }) { Text("🔁 Mover plata") }
        }
        when (abierto) {
            "cuadrar" -> FormCuadre(datos, saldos) { reales -> vm.cuadrar(reales); abierto = "" }
            "mover" -> FormMover(datos) { desde, hacia, monto, fecha, nota -> vm.mover(desde, hacia, monto, fecha, nota); abierto = "" }
        }
    }
}

// pide cuanto hay de verdad en cada cuenta y muestra el desfase antes de guardar
@Composable
private fun FormCuadre(datos: Datos, saldos: Map<String, Long>, guardar: (Map<String, Long>) -> Unit) {
    val cuentas = Saldos.activas(datos)
    val reales = remember { mutableStateMapOf<String, Long?>().apply { cuentas.forEach { put(it.id, saldos[it.id]?.takeIf { s -> s >= 0 }) } } }
    Text("¿Cuánto tienes de verdad en cada una?", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
    cuentas.forEach { c ->
        CampoPesos("${c.emoji} ${c.nombre}", reales[c.id], { reales[c.id] = it })
    }
    val total = cuentas.sumOf { reales[it.id] ?: 0 }
    val segun = cuentas.sumOf { saldos[it.id] ?: 0 }
    val desfase = total - segun
    FilaDato("Total real", Formato.pesos(total), negrita = true)
    FilaDato("Según lo registrado", Formato.pesos(segun))
    Text(
        when {
            desfase == 0L -> "Cuadra exacto"
            desfase < 0 -> "Desfase de ${Formato.pesos(-desfase)} de menos: gastaste algo sin registrar"
            else -> "Desfase de ${Formato.pesos(desfase)} de más: te entró algo sin registrar"
        },
        fontWeight = FontWeight.Bold, color = when {
            desfase == 0L -> Colores.verde
            desfase < 0 -> Colores.rojo
            else -> Colores.verde
        },
    )
    Text("Al guardar, lo que deberías tener queda igual a lo que tienes y el desfase queda anotado en el historial.", fontSize = 12.sp)
    Button(onClick = { guardar(cuentas.associate { it.id to (reales[it.id] ?: 0L) }) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text("Guardar y cuadrar")
    }
}

// envio entre cuentas o retiro (de una cuenta a efectivo)
@Composable
private fun FormMover(datos: Datos, guardar: (String, String, Long, LocalDate, String) -> Unit) {
    val cuentas = Saldos.activas(datos)
    var desde by remember { mutableStateOf(cuentas.firstOrNull()?.id) }
    var hacia by remember { mutableStateOf(cuentas.getOrNull(1)?.id) }
    var monto by remember { mutableStateOf<Long?>(null) }
    var fecha by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    var nota by remember { mutableStateOf("") }
    Text("Desde", fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    SelectorChips(cuentas.map { it.id to "${it.emoji} ${it.nombre}" }, desde) { desde = it; if (hacia == it) hacia = null }
    Text("Hacia", fontSize = 13.sp)
    SelectorChips(cuentas.filter { it.id != desde }.map { it.id to "${it.emoji} ${it.nombre}" }, hacia) { hacia = it }
    CampoPesos("¿Cuánto?", monto, { monto = it })
    CampoFecha("Fecha", fecha, { fecha = it })
    CampoTexto("Nota (opcional)", nota, { nota = it })
    val ok = desde != null && hacia != null && desde != hacia && (monto ?: 0) > 0 && fecha != null
    Button(enabled = ok, onClick = { guardar(desde!!, hacia!!, monto!!, fecha!!, nota) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text("Mover")
    }
}

// para elegir de que cuenta salio o a cual entro
@Composable
fun SelectorCuenta(datos: Datos, titulo: String, seleccion: String?, alElegir: (String) -> Unit) {
    val cuentas = Saldos.activas(datos)
    if (cuentas.isEmpty()) return
    Text(titulo, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    SelectorChips(cuentas.map { it.id to "${it.emoji} ${it.nombre}" }, seleccion, alElegir)
}

private val emojisCuenta = listOf("🏦", "📱", "💵", "💳", "🐷", "💰", "🪙", "🏧")

// agregar, renombrar o desactivar cuentas
@Composable
fun PantallaListaCuentas(vm: AppViewModel, datos: Datos, alVolver: () -> Unit) {
    val confirmar = LocalConfirmador.current
    val saldos = Saldos.saldos(datos)
    var editando by remember { mutableStateOf<Cuenta?>(null) }
    var nueva by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Encabezado("Cuentas", alVolver) { TextButton(onClick = { nueva = true; editando = null }) { Text("+ Nueva") } }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Donde tienes la plata. La primera recibe lo que no tiene cuenta.", fontSize = 13.sp)
            if (nueva || editando != null) {
                key(editando?.id) {
                    FormCuenta(editando, datos, alGuardar = { vm.guardar(it); nueva = false; editando = null }, alCancelar = { nueva = false; editando = null })
                }
            }
            datos.cuentas.sortedBy { it.orden }.forEach { c ->
                val usada = datos.movimientos.any { it.cuentaId == c.id } || datos.ingresos.any { it.cuentaId == c.id } || datos.cuentaMovs.any { it.cuentaId == c.id }
                Card(onClick = { editando = c; nueva = false }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${c.emoji} ${c.nombre}" + (if (c.tipo != TipoCuenta.NORMAL) " · ${c.tipo}" else "") + if (!c.activa) " (inactiva)" else "", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(Formato.pesos(saldos[c.id] ?: 0))
                        if (!usada) TextButton(onClick = { confirmar.preguntar("Borrar cuenta", "¿Borrar ${c.nombre}?") { vm.borrar(c) } }) { Text("✕", color = Colores.rojo) }
                    }
                }
            }
            Spacer(Modifier.height(60.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormCuenta(inicial: Cuenta?, datos: Datos, alGuardar: (Cuenta) -> Unit, alCancelar: () -> Unit) {
    var nombre by remember { mutableStateOf(inicial?.nombre ?: "") }
    var emoji by remember { mutableStateOf(inicial?.emoji ?: emojisCuenta.first()) }
    var activa by remember { mutableStateOf(inicial?.activa ?: true) }
    var tipo by remember { mutableStateOf(inicial?.tipo ?: TipoCuenta.NORMAL) }
    Tarjeta(Modifier.padding(vertical = 6.dp)) {
        Text(if (inicial == null) "Nueva cuenta" else "Editar cuenta", fontWeight = FontWeight.Bold)
        CampoTexto("Nombre", nombre, { nombre = it })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            emojisCuenta.forEach { e -> FilterChip(selected = e == emoji, onClick = { emoji = e }, label = { Text(e, fontSize = 18.sp) }) }
        }
        Text("Tipo", fontSize = 13.sp)
        SelectorChips(listOf(TipoCuenta.NORMAL to "Normal", TipoCuenta.BOLSILLO to "🔒 Bolsillo", TipoCuenta.COLCHON to "🛟 Colchón"), tipo) { tipo = it }
        if (inicial != null) Interruptor("Activa", activa) { activa = it }
        Row {
            TextButton(onClick = alCancelar) { Text("Cancelar") }
            Spacer(Modifier.weight(1f))
            Button(enabled = nombre.isNotBlank(), onClick = {
                val orden = (datos.cuentas.maxOfOrNull { it.orden } ?: -1) + 1
                alGuardar((inicial ?: Cuenta(nuevoId(), nombre.trim(), emoji, orden)).copy(nombre = nombre.trim(), emoji = emoji, activa = activa, tipo = tipo))
            }) { Text("Guardar") }
        }
    }
}
