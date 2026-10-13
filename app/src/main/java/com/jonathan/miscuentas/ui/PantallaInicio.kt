package com.jonathan.miscuentas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jonathan.miscuentas.dominio.*
import java.time.YearMonth

@Composable
fun PantallaInicio(vm: AppViewModel, datos: Datos, irMonto: () -> Unit, irDeudas: () -> Unit, irCalendario: () -> Unit) {
    val hoy = vm.hoy()
    val mes = YearMonth.from(hoy)
    fun enMes(f: String) = Fechas.leer(f)?.let { YearMonth.from(it) == mes } ?: false
    val todas = Obligaciones.generar(datos, mes.minusMonths(3).atDay(1), mes.plusMonths(2).atEndOfMonth())
    val vencidos = Obligaciones.generar(datos, Fechas.leer(datos.config.fechaInicio) ?: hoy, hoy.minusDays(1)).filter { it.falta > 0 }
    val proximos7 = todas.filter { it.fecha >= hoy && it.fecha <= hoy.plusDays(7) && it.falta > 0 }
    val delMes = todas.filter { it.mes == mes }
    val noPlaneado = datos.movimientos.filter { enMes(it.fecha) && it.tipo == TipoMovimiento.NO_PLANEADO }.sumOf { it.monto }
    val moto = datos.ingresos.filter { enMes(it.fecha) && it.tipo == TipoIngreso.MOTO }.sumOf { it.monto }
    val endeuda = Calculos.endeudamiento(datos)
    val avance = Calculos.avance(delMes.filter { it.fecha <= hoy || it.pagado > 0 }.ifEmpty { delMes })

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Tarjeta(color = MaterialTheme.colorScheme.primaryContainer) {
            Text("Deberías tener hoy", color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(Formato.pesos(Calculos.deberiaTener(datos)), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Cifra("Vencidos", "${vencidos.size} · ${Formato.pesos(vencidos.sumOf { it.falta })}", if (vencidos.isNotEmpty()) Colores.rojo else Colores.verde, Modifier.weight(1f), irCalendario)
            Cifra("Próximos 7 días", Formato.pesos(proximos7.sumOf { it.falta }), Colores.amarillo, Modifier.weight(1f), irCalendario)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Cifra("No planeado del mes", Formato.pesos(noPlaneado), Colores.morado, Modifier.weight(1f))
            Cifra("Moto del mes", Formato.pesos(moto), Colores.azul, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Cifra("Endeudamiento", endeuda?.let { Formato.porcentaje(it) } ?: "—", Colores.semaforo(Calculos.semaforo(endeuda)), Modifier.weight(1f), irDeudas)
            Cifra("Avance del mes", avance?.let { Formato.porcentaje(it) } ?: "—", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        }

        // presupuestos de esta quincena
        val presupuestos = datos.presupuestos.filter { it.activo }.mapNotNull { Obligaciones.instanciaPresupuesto(datos, it.id, hoy) }
        if (presupuestos.isNotEmpty()) {
            TituloSeccion("Presupuestos")
            presupuestos.forEach { p ->
                Tarjeta(Modifier.padding(vertical = 3.dp)) {
                    Text("${p.concepto}: ${Formato.pesos(p.pagado)} de ${Formato.pesos(p.presupuestado)}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    BarraProgreso(p.pagado, p.presupuestado)
                    if (p.pagado > p.presupuestado) Text("Te pasaste por ${Formato.pesos(p.pagado - p.presupuestado)}", color = Colores.rojo, fontSize = 13.sp)
                }
            }
        }

        // proximos pagos pendientes
        val pendientes = (vencidos + todas.filter { it.fecha >= hoy && it.falta > 0 && it.tipoOrigen != Origen.PRESUPUESTO }).distinctBy { it.clave }.take(10)
        TituloSeccion("Próximos 10 pagos pendientes")
        if (pendientes.isEmpty()) Vacio("No tienes pagos pendientes.")
        pendientes.forEach { o -> FilaPago(o, hoy) { vm.seleccion = Seleccion.Pago(o); irMonto() } }

        // graficas
        TituloSeccion("Presupuestado vs pagado")
        Tarjeta {
            Leyenda(listOf("Presupuestado" to MaterialTheme.colorScheme.surfaceVariant, "Pagado" to MaterialTheme.colorScheme.primary))
            val grupos = (-2..2).map { mes.plusMonths(it.toLong()) }.map { ym ->
                val del = todas.filter { it.mes == ym }
                Triple(Formato.mesCorto(ym), del.sumOf { it.presupuestado }, del.sumOf { it.pagado })
            }
            BarrasDobles(grupos, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.primary)
        }

        val porCategoria = datos.movimientos.filter { enMes(it.fecha) }.groupBy { it.categoria.ifBlank { "Sin categoría" } }
            .map { (k, v) -> k to v.sumOf { it.monto } }.sortedByDescending { it.second }
        TituloSeccion("Gastos por categoría (${Formato.mes(mes)})")
        Tarjeta {
            if (porCategoria.isEmpty()) Vacio("Aún no hay gastos este mes.") else BarrasHorizontales(porCategoria)
        }

        val deudas = datos.deudas.filter { it.activa }.map { Calculos.estadoDeuda(it, datos, hoy) }
            .filter { it.saldoEstimado > 0 || it.pagadoDesdeInicio > 0 }
        TituloSeccion("Deudas: pagado vs saldo")
        Tarjeta(onClick = irDeudas) {
            Leyenda(listOf("Pagado" to Colores.verde, "Saldo" to Colores.rojo.copy(alpha = 0.4f)))
            if (deudas.isEmpty()) Vacio("No tienes deudas registradas.")
            else BarrasDeuda(deudas.map { Triple(it.deuda.nombre, it.pagadoDesdeInicio, it.saldoEstimado) })
        }

        TituloSeccion("Deuda total en el tiempo")
        Tarjeta {
            val inicio = YearMonth.from(Fechas.leer(datos.config.fechaInicio) ?: hoy)
            val puntos = generateSequence(inicio) { it.plusMonths(1) }.takeWhile { it < mes }
                .map { Formato.mesCorto(it) to Calculos.deudaTotal(datos, it.atEndOfMonth()) }.toList() +
                listOf("Hoy" to Calculos.deudaTotal(datos, hoy))
            val conInicio = listOf(Formato.mesCorto(inicio) + " inicio" to Calculos.deudaTotal(datos, inicio.atDay(1).coerceAtLeast(Fechas.leer(datos.config.fechaInicio) ?: hoy))) + puntos
            Linea(conInicio, Colores.rojo)
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
fun Cifra(titulo: String, valor: String, color: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Tarjeta(modifier, onClick = onClick) {
        Text(titulo, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, fontWeight = FontWeight.Bold, color = color, fontSize = 16.sp)
    }
}
