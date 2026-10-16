package com.jonathan.miscuentas.dominio

import java.time.LocalDate
import java.time.YearMonth

object Origen {
    const val DEUDA = "deuda"
    const val GASTO = "gasto"
    const val PRESUPUESTO = "presupuesto"
    const val EVENTO = "evento"
    const val INGRESO = "ingreso"
    // lo que hay que pagar de una compra con tarjeta
    const val PUENTE = "puente"
}

object Estado {
    const val SIN_VALOR = "—"
    const val PAGADO = "Pagado"
    const val VENCIDO_PARCIAL = "Vencido parcial"
    const val VENCIDO = "Vencido"
    const val PARCIAL = "Parcial"
    const val PENDIENTE = "Pendiente"
    val todos = listOf(PAGADO, VENCIDO_PARCIAL, VENCIDO, PARCIAL, PENDIENTE)
}

data class Obligacion(
    val clave: String,
    val tipoOrigen: String,
    val origenId: String,
    val fecha: LocalDate,
    val concepto: String,
    val categoria: String,
    val presupuestado: Long,
    val pagado: Long = 0,
    val deudaId: String? = null,
    val aviso: String? = null,
    // lo que se pensaba pagar antes de pagarlo completo por otro valor
    val estimado: Long = presupuestado,
    val liquidada: Boolean = false,
    // si se pospuso, la fecha que tenia antes
    val fechaOriginal: LocalDate? = null,
) {
    val mes: YearMonth get() = YearMonth.from(fecha)
    val falta: Long get() = maxOf(0, presupuestado - pagado)

    fun estado(hoy: LocalDate): String = Calculos.estado(presupuestado, pagado, fecha, hoy)
}

// rango de fechas que se paga con un sueldo
data class Periodo(val desde: LocalDate, val hasta: LocalDate, val diaSueldo: Int?)

data class IngresoEsperado(
    val clave: String,
    val programadoId: String,
    val fecha: LocalDate,
    val concepto: String,
    val tipo: String,
    val monto: Long,
    val recibido: Long,
    val esEstimado: Boolean,
    // moto y similares: puede no llegar
    val esIncierto: Boolean = false,
)

object Obligaciones {

    fun clave(tipo: String, id: String, fecha: LocalDate) = "$tipo:$id:${Fechas.iso(fecha)}"

    // genera los pagos del rango, nunca antes de la fecha de inicio
    fun generar(datos: Datos, desde: LocalDate, hasta: LocalDate): List<Obligacion> {
        val inicio = Fechas.leer(datos.config.fechaInicio) ?: desde
        val d = maxOf(desde, inicio)
        if (hasta < d) return emptyList()
        // se arma el mes completo para que los pagos caigan en su semana o quincena y no en la del rango
        val md = maxOf(YearMonth.from(d).atDay(1), inicio)
        val mh = YearMonth.from(hasta).atEndOfMonth()
        // orden: mes completo, quitar pausados, traer pospuestos, cruzar pagos, mover fechas y recortar
        val base = generarBase(datos, md, mh, inicio).filterNot { pausado(datos, it) }.toMutableList()
        val claves = base.map { it.clave }.toSet()
        datos.pospuestos.forEach { p ->
            val nueva = Fechas.leer(p.fechaNueva) ?: return@forEach
            val original = Fechas.leer(p.clave.substringAfterLast(':')) ?: return@forEach
            if (p.clave in claves || nueva !in md..mh || original < inicio) return@forEach
            val ym = YearMonth.from(original)
            generarBase(datos, maxOf(ym.atDay(1), inicio), ym.atEndOfMonth(), inicio)
                .firstOrNull { it.clave == p.clave && !pausado(datos, it) }?.let { base += it }
        }
        val nuevas = datos.pospuestos.associate { it.clave to Fechas.leer(it.fechaNueva) }
        return conPagos(base, datos.movimientos)
            .map { o -> nuevas[o.clave]?.let { f -> o.copy(fecha = f, fechaOriginal = o.fecha) } ?: o }
            .filter { it.fecha in d..hasta }
            .sortedWith(compareBy({ it.fecha }, { it.concepto }))
    }

    // pagos del rango sin pausas, pospuestos ni lo pagado
    private fun generarBase(datos: Datos, md: LocalDate, mh: LocalDate, inicio: LocalDate): List<Obligacion> {
        val base = mutableListOf<Obligacion>()
        datos.deudas.filter { it.activa }.forEach { base += deThisDeuda(it, md, mh, inicio) }
        datos.gastosFijos.filter { it.activo }.forEach { base += deGasto(it, md, mh, datos.config) }
        val dias = diasSueldo(datos)
        datos.presupuestos.filter { it.activo }.forEach { base += dePresupuesto(it, md, mh, dias) }
        datos.eventos.forEach { ev ->
            val f = Fechas.leer(ev.fecha) ?: return@forEach
            if (f in md..mh) {
                base += Obligacion(
                    clave(Origen.EVENTO, ev.id, f), Origen.EVENTO, ev.id, f, ev.nombre,
                    ev.categoria.ifBlank { Categorias.NO_PLANEADO }, ev.monto, deudaId = ev.deudaId
                )
            }
        }
        datos.movimientos.filter { it.tarjetaId != null }.forEach { m ->
            base += Tarjetas.puentes(datos, m).filter { it.fecha in md..mh }
        }
        return base
    }

    fun pausado(datos: Datos, o: Obligacion): Boolean = datos.pausas.any { p ->
        p.tipoOrigen == o.tipoOrigen && p.origenId == o.origenId &&
            (Fechas.leer(p.desde)?.let { o.fecha >= it } ?: true) && (Fechas.leer(p.hasta)?.let { o.fecha <= it } ?: true)
    }

    // la pausa vigente o proxima de un origen, para mostrar "pausado hasta"
    fun pausaDe(datos: Datos, tipo: String, id: String, hoy: LocalDate): Pausa? = datos.pausas
        .filter { it.tipoOrigen == tipo && it.origenId == id && (Fechas.leer(it.hasta)?.let { h -> h >= hoy } ?: true) }
        .minByOrNull { it.desde }

    private fun meses(desde: LocalDate, hasta: LocalDate): Sequence<YearMonth> =
        generateSequence(YearMonth.from(desde)) { it.plusMonths(1) }.takeWhile { it <= YearMonth.from(hasta) }

    private fun deThisDeuda(deuda: Deuda, desde: LocalDate, hasta: LocalDate, inicio: LocalDate): List<Obligacion> {
        val lista = mutableListOf<Obligacion>()
        val fin = Fechas.leer(deuda.fechaFin)
        if (deuda.cuotaMensual > 0 && deuda.diaPago != null) {
            // primera cuota desde la fecha de inicio para contar las que faltan
            val primerMes = YearMonth.from(inicio).let { ym ->
                if (Fechas.diaEnMes(ym, deuda.diaPago) < inicio) ym.plusMonths(1) else ym
            }
            val restantes = if (deuda.cuotasTotales != null && deuda.cuotasPagadas != null)
                deuda.cuotasTotales - deuda.cuotasPagadas else null
            for (ym in meses(desde, hasta)) {
                val f = Fechas.diaEnMes(ym, deuda.diaPago)
                if (f < desde || f > hasta || f < inicio) continue
                if (fin != null && f > fin) continue
                if (restantes != null && Fechas.mesesEntre(primerMes, ym) >= restantes) continue
                lista += Obligacion(
                    clave(Origen.DEUDA, deuda.id, f), Origen.DEUDA, deuda.id, f,
                    deuda.nombre, Categorias.DEUDA, deuda.cuotaMensual, deudaId = deuda.id
                )
            }
        } else if (fin != null && fin in desde..hasta) {
            // pago unico en la fecha fin
            val monto = deuda.saldoExtracto ?: deuda.saldoInicial
            if (monto > 0) {
                lista += Obligacion(
                    clave(Origen.DEUDA, deuda.id, fin), Origen.DEUDA, deuda.id, fin,
                    deuda.nombre, Categorias.DEUDA, monto, deudaId = deuda.id
                )
            }
        }
        return lista
    }

    private fun deGasto(g: GastoFijo, desde: LocalDate, hasta: LocalDate, config: Config): List<Obligacion> {
        val lista = mutableListOf<Obligacion>()
        val aviso = if (!g.diaConfirmado) "confirma este día" else null
        val categoria = if (g.frecuencia == Frecuencia.SEMANAL) Categorias.SEMANAL else Categorias.GASTO_FIJO
        fun agregar(f: LocalDate) {
            if (f in desde..hasta) lista += Obligacion(
                clave(Origen.GASTO, g.id, f), Origen.GASTO, g.id, f, g.nombre, categoria, g.valor, aviso = aviso
            )
        }
        when (g.frecuencia) {
            Frecuencia.SEMANAL -> {
                val dia = (g.diaSemana ?: 6).mod(7)
                var f = desde
                while (Fechas.indiceDiaSemana(f) != dia) f = f.plusDays(1)
                while (f <= hasta) {
                    agregar(f); f = f.plusWeeks(1)
                }
            }
            Frecuencia.QUINCENAL -> meses(desde, hasta).forEach { ym ->
                agregar(Fechas.diaEnMes(ym, 15)); agregar(ym.atEndOfMonth())
            }
            else -> meses(desde, hasta).forEach { ym -> agregar(Fechas.diaEnMes(ym, g.diaPago ?: 1)) }
        }
        return lista
    }

    // con dos sueldos fijos el quincenal arranca en cada dia de sueldo y dura hasta el siguiente
    private fun dePresupuesto(p: Presupuesto, desde: LocalDate, hasta: LocalDate, dias: List<Int>): List<Obligacion> {
        val lista = mutableListOf<Obligacion>()
        val pd = Fechas.leer(p.fechaDesde)
        val ph = Fechas.leer(p.fechaHasta)
        val categoria = p.categoria.ifBlank { Categorias.NO_PLANEADO }
        fun agregar(f: LocalDate) {
            if (f !in desde..hasta) return
            if (p.periodicidad != Periodicidad.UNICO) {
                if (pd != null && f < pd) return
                if (ph != null && f > ph) return
            }
            lista += Obligacion(clave(Origen.PRESUPUESTO, p.id, f), Origen.PRESUPUESTO, p.id, f, p.nombre, categoria, p.monto)
        }
        when (p.periodicidad) {
            Periodicidad.QUINCENAL -> meses(desde, hasta).forEach { ym ->
                if (dias.size == 2) dias.forEach { agregar(Fechas.diaEnMes(ym, it)) }
                else { agregar(Fechas.diaEnMes(ym, 15)); agregar(ym.atEndOfMonth()) }
            }
            Periodicidad.MENSUAL -> meses(desde, hasta).forEach { ym -> agregar(Fechas.diaEnMes(ym, pd?.dayOfMonth ?: 1)) }
            else -> (Fechas.leer(p.fecha) ?: pd)?.let { agregar(it) }
        }
        return lista
    }

    // suma los pagos de cada obligacion; si cambio el dia del origen se pasan al mismo mes
    fun conPagos(base: List<Obligacion>, movimientos: List<Movimiento>): List<Obligacion> {
        val porClave = base.associateBy { it.clave }
        val pagos = mutableMapOf<String, Long>()
        val liquidadas = mutableSetOf<String>()
        movimientos.forEach { m ->
            val c = m.claveObligacion ?: return@forEach
            val destino = if (porClave.containsKey(c)) c else reasignar(c, base)
            if (destino != null) {
                pagos[destino] = (pagos[destino] ?: 0) + m.monto
                if (m.liquida) liquidadas += destino
            }
        }
        // pago completo: lo pagado pasa a ser el valor real del mes
        return base.map {
            val pagado = pagos[it.clave] ?: 0
            if (it.clave in liquidadas) it.copy(pagado = pagado, presupuestado = pagado, estimado = it.presupuestado, liquidada = true)
            else it.copy(pagado = pagado)
        }
    }

    private fun reasignar(clave: String, base: List<Obligacion>): String? {
        val partes = clave.split(':')
        if (partes.size != 3) return null
        val f = Fechas.leer(partes[2]) ?: return null
        val candidatas = base.filter { it.tipoOrigen == partes[0] && it.origenId == partes[1] && it.mes == YearMonth.from(f) }
        return candidatas.minByOrNull { kotlin.math.abs(it.fecha.toEpochDay() - f.toEpochDay()) }?.clave
    }

    // quincena de una fecha: del 1 al 15 o del 16 al fin de mes
    fun quincena(fecha: LocalDate): Pair<LocalDate, LocalDate> {
        val ym = YearMonth.from(fecha)
        return if (fecha.dayOfMonth <= 15) ym.atDay(1) to ym.atDay(15) else ym.atDay(16) to ym.atEndOfMonth()
    }

    // dias del mes en que llega el sueldo (ej. 15 y 30), si hay dos
    fun diasSueldo(datos: Datos): List<Int> = datos.ingresosProgramados
        .filter { it.activo && it.frecuencia != FrecuenciaIngreso.UNICO && it.tipo == TipoIngreso.SALARIO }
        .mapNotNull { it.dia }.distinct().sorted()

    // lo que se paga con cada sueldo: con el del 15 hasta el 29 y con el del 30 hasta el 14
    // sin dos sueldos fijos se usa la quincena del calendario
    fun periodo(datos: Datos, fecha: LocalDate): Periodo {
        val dias = diasSueldo(datos)
        if (dias.size != 2) {
            val (d, h) = quincena(fecha)
            return Periodo(d, h, null)
        }
        val inicios = (-1L..1L).flatMap { m ->
            val ym = YearMonth.from(fecha).plusMonths(m)
            dias.map { Fechas.diaEnMes(ym, it) to it }
        }.distinctBy { it.first }.sortedBy { it.first }
        val actual = inicios.last { it.first <= fecha }
        val siguiente = inicios.first { it.first > fecha }
        return Periodo(actual.first, siguiente.first.minusDays(1), actual.second)
    }

    // pagos del periodo sin contar los presupuestos de gasto
    fun pagosQuincena(datos: Datos, fecha: LocalDate): List<Obligacion> {
        val p = periodo(datos, fecha)
        return generar(datos, p.desde, p.hasta).filter { it.tipoOrigen != Origen.PRESUPUESTO && it.presupuestado > 0 }
    }

    // la quincena o periodo del presupuesto al que cae un gasto de esa fecha
    fun instanciaPresupuesto(datos: Datos, presupuestoId: String, fecha: LocalDate): Obligacion? {
        val p = datos.presupuestos.firstOrNull { it.id == presupuestoId && it.activo } ?: return null
        val ym = YearMonth.from(fecha)
        val delMes = generar(datos, ym.atDay(1), ym.atEndOfMonth()).filter { it.tipoOrigen == Origen.PRESUPUESTO && it.origenId == p.id }
        return when (p.periodicidad) {
            Periodicidad.QUINCENAL -> if (diasSueldo(datos).size == 2) {
                val ini = periodo(datos, fecha).desde
                generar(datos, ini, ini).firstOrNull { it.tipoOrigen == Origen.PRESUPUESTO && it.origenId == p.id }
            } else delMes.firstOrNull { if (fecha.dayOfMonth <= 15) it.fecha.dayOfMonth == 15 else it.fecha.dayOfMonth > 15 }
            Periodicidad.MENSUAL -> delMes.firstOrNull()
            else -> {
                val desde = Fechas.leer(p.fechaDesde) ?: Fechas.leer(p.fecha)
                val hasta = Fechas.leer(p.fechaHasta) ?: desde
                if (desde != null && hasta != null && fecha in desde..hasta) {
                    generar(datos, desde, desde).firstOrNull { it.origenId == p.id }
                } else null
            }
        }
    }

    // ingresos programados del rango con lo que ya se recibio
    fun ingresosEsperados(datos: Datos, desde: LocalDate, hasta: LocalDate): List<IngresoEsperado> {
        val inicio = Fechas.leer(datos.config.fechaInicio) ?: desde
        val d = maxOf(desde, inicio)
        val lista = mutableListOf<IngresoEsperado>()
        datos.ingresosProgramados.filter { it.activo }.forEach { ip ->
            val fechas = when (ip.frecuencia) {
                FrecuenciaIngreso.UNICO -> listOfNotNull(Fechas.leer(ip.fecha))
                else -> meses(d, hasta).map { Fechas.diaEnMes(it, ip.dia ?: 30) }.toList()
            }
            fechas.filter { it in d..hasta }.forEach { f ->
                val c = clave(Origen.INGRESO, ip.id, f)
                val recibido = datos.ingresos.filter { it.claveIngresoProgramado == c }.sumOf { it.monto }
                lista += IngresoEsperado(c, ip.id, f, ip.nombre, ip.tipo, ip.monto, recibido, ip.esEstimado, ip.esIncierto || ip.tipo == TipoIngreso.MOTO)
            }
        }
        return lista.sortedBy { it.fecha }
    }
}
