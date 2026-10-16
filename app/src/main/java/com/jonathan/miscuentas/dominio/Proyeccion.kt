package com.jonathan.miscuentas.dominio

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// un dia de la proyeccion: con cuanto cierra y que entro y salio ese dia
data class DiaProyectado(
    val fecha: LocalDate,
    val saldoAlCierre: Long,
    val entradas: Long,
    val salidas: Long,
    val conIngresoEstimado: Boolean = false,
)

data class Proyeccion(
    val dias: List<DiaProyectado>,
    val minimo: Long,
    val fechaMinimo: LocalDate,
    val proximoSueldo: LocalDate,
    val faltanteHastaSueldo: Long,
    val libreHastaSueldo: Long,
    val puedesGastarPorDia: Long,
    val diasHastaSueldo: Int,
    // plata de emergencia que no se cuenta como libre
    val colchon: Long,
    // saldos si llegara lo incierto (moto); vacio si no hay nada incierto en el horizonte
    val conIncierto: List<Long>,
    val llegasConIncierto: Long?,
) {
    // saldo con el que se llega al dia anterior al sueldo
    val llegasCon: Long get() = dias.lastOrNull { it.fecha < proximoSueldo }?.saldoAlCierre ?: dias.firstOrNull()?.saldoAlCierre ?: 0
}

// presupuesto de hoy contra lo que de verdad alcanza
data class PresupuestoReal(
    val obligacion: Obligacion,
    val restante: Long,
    val realDisponible: Long,
    val porDia: Long,
    val noAlcanza: Boolean,
)

// abono extra sugerido o plata para completar el colchon
data class Sugerencia(
    val alColchon: Long,
    val abono: Long,
    val deuda: EstadoDeuda?,
    val mesesMenos: Int?,
    val ahorroIntereses: Long?,
    val terminaAntes: YearMonth?,
    val terminaDespues: YearMonth?,
)

object Proyecciones {

    fun proximoSueldo(datos: Datos, hoy: LocalDate): LocalDate = Tarjetas.proximoSueldo(datos, hoy)

    // por defecto hasta el dia anterior al segundo sueldo, para ver tambien la quincena que viene
    fun horizonte(datos: Datos, hoy: LocalDate): LocalDate = Obligaciones.periodo(datos, proximoSueldo(datos, hoy)).hasta

    fun colchon(datos: Datos): Long = Saldos.deTipo(datos, TipoCuenta.COLCHON)

    // periodo que cubre una instancia de presupuesto
    fun periodoDe(datos: Datos, o: Obligacion): Pair<LocalDate, LocalDate> {
        val p = datos.presupuestos.firstOrNull { it.id == o.origenId }
        return when (p?.periodicidad) {
            Periodicidad.QUINCENAL -> if (Obligaciones.diasSueldo(datos).size == 2) {
                Obligaciones.periodo(datos, o.fecha).let { it.desde to it.hasta }
            } else {
                val ym = YearMonth.from(o.fecha)
                if (o.fecha.dayOfMonth <= 15) ym.atDay(1) to o.fecha else ym.atDay(16) to ym.atEndOfMonth()
            }
            Periodicidad.MENSUAL -> {
                val ym = YearMonth.from(o.fecha)
                ym.atDay(1) to ym.atEndOfMonth()
            }
            else -> {
                val d = Fechas.leer(p?.fechaDesde) ?: o.fecha
                val h = Fechas.leer(p?.fechaHasta) ?: d
                d to h
            }
        }
    }

    // reparte un monto en partes iguales por dia sin perder pesos por redondeo
    private fun repartir(total: Long, desde: LocalDate, hasta: LocalDate, sumar: (LocalDate, Long) -> Unit) {
        if (total <= 0 || hasta < desde) return
        val n = ChronoUnit.DAYS.between(desde, hasta) + 1
        for (i in 0 until n) {
            val parte = total * (i + 1) / n - total * i / n
            sumar(desde.plusDays(i), parte)
        }
    }

    fun calcular(datos: Datos, hoy: LocalDate, hasta: LocalDate = horizonte(datos, hoy)): Proyeccion {
        val fin = maxOf(hasta, hoy)
        val proximo = proximoSueldo(datos, hoy)
        val entradas = mutableMapOf<LocalDate, Long>()
        val salidas = mutableMapOf<LocalDate, Long>()
        val inciertas = mutableMapOf<LocalDate, Long>()
        val estimados = mutableSetOf<LocalDate>()
        fun mas(m: MutableMap<LocalDate, Long>, f: LocalDate, v: Long) { m[f] = (m[f] ?: 0) + v }

        // ingresos que faltan por llegar; lo incierto va aparte
        Obligaciones.ingresosEsperados(datos, hoy, fin).filter { it.recibido < it.monto }.forEach { e ->
            val v = e.monto - e.recibido
            if (e.esIncierto) mas(inciertas, e.fecha, v)
            else {
                mas(entradas, e.fecha, v)
                if (e.esEstimado) estimados += e.fecha
            }
        }
        // pagos con su fecha efectiva (pausas y pospuestos ya aplicados)
        Obligaciones.generar(datos, hoy, fin).filter { it.tipoOrigen != Origen.PRESUPUESTO && it.falta > 0 }
            .forEach { mas(salidas, it.fecha, it.falta) }
        // lo vencido se resta hoy
        val inicio = Fechas.leer(datos.config.fechaInicio) ?: hoy
        if (inicio < hoy) {
            Obligaciones.generar(datos, inicio, hoy.minusDays(1)).filter { it.tipoOrigen != Origen.PRESUPUESTO && it.falta > 0 }
                .forEach { mas(salidas, hoy, it.falta) }
        }
        // presupuestos: lo que falta del vigente repartido hasta el fin de su periodo; los futuros completos
        Obligaciones.generar(datos, hoy.minusDays(62), fin).filter { it.tipoOrigen == Origen.PRESUPUESTO }.forEach { o ->
            val (pd, ph) = periodoDe(datos, o)
            if (ph < hoy || pd > fin) return@forEach
            val desde = maxOf(pd, hoy)
            val total = if (pd <= hoy) o.falta else o.presupuestado
            repartir(total, desde, ph) { f, v -> if (f <= fin) mas(salidas, f, v) }
        }

        val arranque = Calculos.deberiaTener(datos) - colchon(datos)
        var saldo = arranque
        var conMoto = arranque
        val dias = mutableListOf<DiaProyectado>()
        val incierto = mutableListOf<Long>()
        var f = hoy
        while (f <= fin) {
            val e = entradas[f] ?: 0
            val s = salidas[f] ?: 0
            saldo += e - s
            conMoto += e - s + (inciertas[f] ?: 0)
            dias += DiaProyectado(f, saldo, e, s, f in estimados)
            incierto += conMoto
            f = f.plusDays(1)
        }
        val min = dias.minBy { it.saldoAlCierre }
        val antes = dias.filter { it.fecha < proximo }.ifEmpty { dias.take(1) }
        val menor = antes.minOf { it.saldoAlCierre }
        val nDias = maxOf(1, ChronoUnit.DAYS.between(hoy, proximo).toInt())
        val libre = maxOf(0, menor)
        val hayIncierto = inciertas.isNotEmpty()
        val idxAntes = dias.indexOfLast { it.fecha < proximo }.takeIf { it >= 0 }
        return Proyeccion(
            dias = dias, minimo = min.saldoAlCierre, fechaMinimo = min.fecha, proximoSueldo = proximo,
            faltanteHastaSueldo = maxOf(0, -menor), libreHastaSueldo = libre, puedesGastarPorDia = libre / nDias,
            diasHastaSueldo = nDias, colchon = colchon(datos),
            conIncierto = if (hayIncierto) incierto else emptyList(),
            llegasConIncierto = if (hayIncierto) idxAntes?.let { incierto[it] } else null,
        )
    }

    // lo que queda del presupuesto y lo que de verdad alcanza hasta el sueldo
    fun presupuestoReal(datos: Datos, presupuestoId: String, hoy: LocalDate, proy: Proyeccion): PresupuestoReal? {
        val o = Obligaciones.instanciaPresupuesto(datos, presupuestoId, hoy) ?: return null
        val restante = maxOf(0, o.presupuestado - o.pagado)
        // la proyeccion ya descuenta lo que queda del presupuesto, asi que lo que no alcanza es el faltante
        val real = maxOf(0, restante - proy.faltanteHastaSueldo)
        val (_, ph) = periodoDe(datos, o)
        val dias = maxOf(1, ChronoUnit.DAYS.between(hoy, minOf(ph, proy.proximoSueldo.minusDays(1))).toInt() + 1)
        return PresupuestoReal(o, restante, real, real / dias, real < restante)
    }

    // promedio gastado en los ultimos periodos completos (hasta 3)
    fun promedioGastado(datos: Datos, presupuestoId: String, hoy: LocalDate): Long? {
        val inicio = Fechas.leer(datos.config.fechaInicio) ?: return null
        val completos = Obligaciones.generar(datos, inicio, hoy)
            .filter { it.tipoOrigen == Origen.PRESUPUESTO && it.origenId == presupuestoId }
            .filter { periodoDe(datos, it).second < hoy && periodoDe(datos, it).first >= inicio }
            .sortedByDescending { it.fecha }.take(3)
        if (completos.isEmpty()) return null
        return completos.sumOf { it.pagado } / completos.size
    }

    // libre sin contar el bolsillo, en la quincena del sueldo que lo alimenta
    fun libreSinBolsillo(datos: Datos, hoy: LocalDate, proy: Proyeccion): Long? {
        val dia = datos.config.bolsilloDia ?: return null
        if (Obligaciones.periodo(datos, hoy).diaSueldo != dia) return null
        return maxOf(0, proy.libreHastaSueldo - Saldos.deTipo(datos, TipoCuenta.BOLSILLO))
    }

    // el sueldo del dia del bolsillo ya llego en este periodo y todavia no se aparto
    fun bolsilloPorApartar(datos: Datos, hoy: LocalDate): Long? {
        val c = datos.config
        val dia = c.bolsilloDia ?: return null
        val cuenta = c.bolsilloCuenta ?: return null
        if (c.bolsilloMonto <= 0 || datos.cuentas.none { it.id == cuenta }) return null
        val p = Obligaciones.periodo(datos, hoy)
        if (p.diaSueldo != dia) return null
        if (!sueldoRecibido(datos, p)) return null
        val apartado = datos.cuentaMovs.filter {
            it.cuentaId == cuenta && it.tipo == TipoCuentaMov.TRASLADO && it.monto > 0 && (Fechas.leer(it.fecha)?.let { f -> f in p.desde..p.hasta } ?: false)
        }.sumOf { it.monto }
        val falta = c.bolsilloMonto - apartado
        return falta.takeIf { it > 0 }
    }

    // el sueldo de un periodo ya se registro
    fun sueldoRecibido(datos: Datos, p: Periodo): Boolean {
        val dia = p.diaSueldo ?: return false
        val ids = datos.ingresosProgramados.filter { it.activo && it.tipo == TipoIngreso.SALARIO && it.dia == dia }.map { it.id }
        val claves = ids.map { Obligaciones.clave(Origen.INGRESO, it, p.desde) }.toSet()
        return datos.ingresos.any { it.claveIngresoProgramado in claves } ||
            datos.ingresos.any { it.tipo == TipoIngreso.SALARIO && (Fechas.leer(it.fecha)?.let { f -> f in p.desde..p.hasta } ?: false) }
    }

    // primero el colchon; si esta completo y alcanza, abono extra a la primera deuda del plan
    fun sugerencia(datos: Datos, hoy: LocalDate, proy: Proyeccion): Sugerencia? {
        if (proy.faltanteHastaSueldo > 0) return null
        val sobra = Tarjetas.redondearMiles(proy.libreHastaSueldo)
        if (sobra <= 0) return null
        val meta = datos.config.metaColchon
        if (meta > 0 && proy.colchon < meta) {
            return Sugerencia(minOf(sobra, meta - proy.colchon), 0, null, null, null, null, null)
        }
        val e = Calculos.planDeAtaque(datos, datos.config.modoPlan, hoy).firstOrNull() ?: return null
        val (ahorro, menos) = Calculos.ahorroPorAbono(e.saldoEstimado, e.deuda.cuotaMensual, e.tasaMensual, sobra)
        val antes = Calculos.plazo(e.saldoEstimado, e.deuda.cuotaMensual, e.tasaMensual).meses
        val despues = Calculos.plazo(maxOf(0, e.saldoEstimado - sobra), e.deuda.cuotaMensual, e.tasaMensual).meses
        val mes = YearMonth.from(hoy)
        return Sugerencia(0, sobra, e, menos, ahorro, despues?.let { mes.plusMonths(it.toLong()) }, antes?.let { mes.plusMonths(it.toLong()) })
    }

    // pagar hoy algo que vence con el siguiente sueldo saca plata de esta quincena
    fun avisoAnticipado(datos: Datos, o: Obligacion, monto: Long, hoy: LocalDate, proy: Proyeccion): String? {
        val actual = Obligaciones.periodo(datos, hoy)
        if (o.fecha <= actual.hasta) return null
        val del = Obligaciones.periodo(datos, o.fecha)
        val falta = maxOf(0, monto - proy.libreHastaSueldo) + proy.faltanteHastaSueldo
        val sueldo = del.diaSueldo?.let { " con el sueldo del $it" } ?: ""
        val cola = if (falta > 0) " y te faltará ${Formato.pesos(falta)}" else ""
        return "Este pago vence el ${Formato.fechaCorta(o.fecha)}$sueldo. Si lo pagas hoy, sale de la plata de esta quincena$cola."
    }
}
