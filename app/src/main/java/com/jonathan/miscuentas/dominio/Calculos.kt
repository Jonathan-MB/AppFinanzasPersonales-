package com.jonathan.miscuentas.dominio

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong

data class Plazo(val meses: Int?, val totalPagar: Long?, val intereses: Long?, val sinTasa: Boolean, val noBaja: Boolean)

data class EstadoDeuda(
    val deuda: Deuda,
    val saldoEstimado: Long,
    val pagadoDesdeInicio: Long,
    val tasaMensual: Double?,
    val interesMes: Long?,
    val abonoCapital: Long?,
    val noBaja: Boolean,
    val plazo: Plazo,
    val porcentajePagado: Double,
)

data class Quincenas(
    val ingresos15: Long, val deudas15: Long, val gastos15: Long, val presupuestos15: Long,
    val ingresos30: Long, val deudas30: Long, val gastos30: Long, val presupuestos30: Long,
) {
    val sobra15 get() = ingresos15 - deudas15 - gastos15 - presupuestos15
    val sobra30 get() = ingresos30 - deudas30 - gastos30 - presupuestos30
}

object Calculos {

    // regla 3.1
    fun estado(presupuestado: Long, pagado: Long, fecha: LocalDate, hoy: LocalDate): String = when {
        presupuestado == 0L -> Estado.SIN_VALOR
        pagado >= presupuestado -> Estado.PAGADO
        fecha < hoy && pagado > 0 -> Estado.VENCIDO_PARCIAL
        fecha < hoy -> Estado.VENCIDO
        pagado > 0 -> Estado.PARCIAL
        else -> Estado.PENDIENTE
    }

    // regla 3.4
    fun deberiaTener(datos: Datos): Long =
        datos.config.plataInicial + datos.ingresos.sumOf { it.monto } - datos.movimientos.sumOf { it.monto }

    // pagos que bajan una deuda
    fun pagosDeuda(deuda: Deuda, movimientos: List<Movimiento>): List<Movimiento> =
        movimientos.filter { it.deudaId == deuda.id || it.claveObligacion?.startsWith("${Origen.DEUDA}:${deuda.id}:") == true }

    // regla 3.5
    fun saldoEstimado(deuda: Deuda, datos: Datos, hoy: LocalDate): Long {
        val inicio = Fechas.leer(datos.config.fechaInicio) ?: hoy
        val extractos = datos.deudaSaldos.filter { it.deudaId == deuda.id }
            .mapNotNull { s -> Fechas.leer(s.fecha)?.let { it to s.saldo } }.toMutableList()
        val fe = Fechas.leer(deuda.fechaSaldoExtracto)
        if (deuda.saldoExtracto != null && fe != null) extractos += fe to deuda.saldoExtracto
        val ultimo = extractos.filter { it.first <= hoy }.maxByOrNull { it.first }
        val pagos = pagosDeuda(deuda, datos.movimientos)
        return if (ultimo != null) {
            val despues = pagos.filter { (Fechas.leer(it.fecha) ?: inicio) > ultimo.first }.sumOf { it.monto }
            maxOf(0, ultimo.second - despues)
        } else {
            val despues = pagos.filter { (Fechas.leer(it.fecha) ?: inicio) >= inicio }.sumOf { it.monto }
            maxOf(0, deuda.saldoInicial - despues)
        }
    }

    // regla 3.6
    fun tasaMensual(tasaEa: Double?): Double? = tasaEa?.takeIf { it > 0 }?.let { (1 + it).pow(1.0 / 12.0) - 1 }

    // regla 3.7
    fun plazo(saldo: Long, cuota: Long, tm: Double?): Plazo {
        if (saldo <= 0) return Plazo(0, 0, 0, tm == null, false)
        if (cuota <= 0) return Plazo(null, null, null, tm == null, tm != null)
        if (tm == null || tm == 0.0) {
            val n = ceil(saldo.toDouble() / cuota).toInt()
            return Plazo(n, n.toLong() * cuota, 0, true, false)
        }
        val x = 1 - tm * saldo / cuota
        if (x <= 0) return Plazo(null, null, null, false, true)
        val n = ceil(-ln(x) / ln(1 + tm)).toInt()
        val total = n.toLong() * cuota
        return Plazo(n, total, total - saldo, false, false)
    }

    // regla 3.8
    fun cuotaParaMeses(saldo: Long, tm: Double?, meses: Int): Long {
        if (meses <= 0) return saldo
        if (tm == null || tm == 0.0) return ceil(saldo.toDouble() / meses).toLong()
        return (saldo * tm / (1 - (1 + tm).pow(-meses))).roundToLong()
    }

    // cuanto ahorra en intereses si abona algo extra hoy
    fun ahorroPorAbono(saldo: Long, cuota: Long, tm: Double?, abono: Long): Pair<Long?, Int?> {
        val antes = plazo(saldo, cuota, tm)
        val despues = plazo(maxOf(0, saldo - abono), cuota, tm)
        val ahorro = if (antes.intereses != null && despues.intereses != null) antes.intereses - despues.intereses else null
        val mesesMenos = if (antes.meses != null && despues.meses != null) antes.meses - despues.meses else null
        return ahorro to mesesMenos
    }

    fun estadoDeuda(deuda: Deuda, datos: Datos, hoy: LocalDate): EstadoDeuda {
        val saldo = saldoEstimado(deuda, datos, hoy)
        val tm = if (deuda.generaIntereses) tasaMensual(deuda.tasaEa) else 0.0
        val interes = tm?.let { (saldo * it).roundToLong() }
        val abono = interes?.let { deuda.cuotaMensual - it }
        val noBaja = interes != null && deuda.cuotaMensual > 0 && deuda.cuotaMensual <= interes && saldo > 0
        val pagado = pagosDeuda(deuda, datos.movimientos).sumOf { it.monto }
        val base = maxOf(deuda.saldoInicial, deuda.saldoExtracto ?: 0)
        val pct = if (base > 0) (base - saldo).toDouble() / base else if (saldo == 0L) 1.0 else 0.0
        return EstadoDeuda(deuda, saldo, pagado, tm, interes, abono, noBaja, plazo(saldo, deuda.cuotaMensual, tm), pct.coerceIn(0.0, 1.0))
    }

    // ingresos fijos que entran cada mes
    fun ingresosFijosMensuales(datos: Datos): Long =
        datos.ingresosProgramados.filter { it.activo && it.frecuencia != FrecuenciaIngreso.UNICO }.sumOf { it.monto }

    fun cuotasMensuales(datos: Datos): Long = datos.deudas.filter { it.activa }.sumOf { it.cuotaMensual }

    // regla 3.9
    fun endeudamiento(datos: Datos): Double? {
        val ingresos = ingresosFijosMensuales(datos)
        if (ingresos <= 0) return null
        return cuotasMensuales(datos).toDouble() / ingresos
    }

    fun semaforo(pct: Double?): String = when {
        pct == null -> "gris"
        pct < 0.30 -> "verde"
        pct <= 0.35 -> "amarillo"
        else -> "rojo"
    }

    // regla 3.10
    fun avance(obligaciones: List<Obligacion>): Double? {
        val total = obligaciones.sumOf { it.presupuestado }
        if (total <= 0) return null
        return obligaciones.sumOf { minOf(it.pagado, it.presupuestado) }.toDouble() / total
    }

    // regla 3.11
    fun reparto(datos: Datos, ym: YearMonth): Quincenas {
        val activos = datos.ingresosProgramados.filter { it.activo && it.frecuencia != FrecuenciaIngreso.UNICO }
        val ing15 = activos.filter { (it.dia ?: 30) <= 15 }.sumOf { it.monto }
        val ing30 = activos.filter { (it.dia ?: 30) > 15 }.sumOf { it.monto }
        val deudas = datos.deudas.filter { it.activa && it.cuotaMensual > 0 && it.diaPago != null }
        // con dos sueldos fijos va con el primero lo que cae entre los dos dias (15 al 29)
        val dias = Obligaciones.diasSueldo(datos)
        fun conPrimero(dia: Int) = if (dias.size == 2) dia >= dias[0] && dia < dias[1] else dia in 3..17
        val d15 = deudas.filter { conPrimero(it.diaPago!!) }.sumOf { it.cuotaMensual }
        val d30 = deudas.filter { !conPrimero(it.diaPago!!) }.sumOf { it.cuotaMensual }
        var g15 = 0L
        var g30 = 0L
        datos.gastosFijos.filter { it.activo }.forEach { g ->
            val mensual = when (g.frecuencia) {
                Frecuencia.SEMANAL -> (g.valor * datos.config.semanasPorMes).roundToLong()
                Frecuencia.QUINCENAL -> g.valor * 2
                else -> g.valor
            }
            when (g.quincenaAsignada) {
                Quincena.Q15 -> g15 += mensual
                Quincena.Q30 -> g30 += mensual
                else -> {
                    g15 += mensual / 2; g30 += mensual - mensual / 2
                }
            }
        }
        val p = datos.presupuestos.filter { it.activo && it.periodicidad == Periodicidad.QUINCENAL }
            .filter { pr ->
                val desde = Fechas.leer(pr.fechaDesde); val hasta = Fechas.leer(pr.fechaHasta)
                (desde == null || ym.atEndOfMonth() >= desde) && (hasta == null || ym.atDay(1) <= hasta)
            }.sumOf { it.monto }
        return Quincenas(ing15, d15, g15, p, ing30, d30, g30, p)
    }

    // plan de ataque: orden de pago sugerido
    fun planDeAtaque(datos: Datos, modo: String, hoy: LocalDate): List<EstadoDeuda> {
        val estados = datos.deudas
            .filter { it.activa && !(it.tipo == TipoDeuda.BNPL && it.fechaFin != null) }
            .map { estadoDeuda(it, datos, hoy) }
            .filter { it.saldoEstimado > 0 }
        val (conInteres, sinInteres) = estados.partition { it.deuda.generaIntereses || it.deuda.fijadaArriba }
        val fijadas = conInteres.filter { it.deuda.fijadaArriba }
        val resto = conInteres.filter { !it.deuda.fijadaArriba }
        val ordenadas = when (modo) {
            ModoPlan.AVALANCHA -> resto.sortedWith(compareByDescending<EstadoDeuda> { it.deuda.tasaEa ?: -1.0 }.thenBy { it.saldoEstimado })
            else -> resto.sortedBy { it.saldoEstimado }
        }
        return fijadas + ordenadas + sinInteres.sortedBy { it.saldoEstimado }
    }

    fun deudaTotal(datos: Datos, hoy: LocalDate): Long =
        datos.deudas.filter { it.activa }.sumOf { saldoEstimado(it, datos, hoy) }

    // regla 3.12 se maneja en Fechas.diaEnMes
}
