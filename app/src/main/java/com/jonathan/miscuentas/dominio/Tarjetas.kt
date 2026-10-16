package com.jonathan.miscuentas.dominio

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToLong

// lo que cuesta una compra con tarjeta segun las cuotas
data class CostoCompra(val cuotas: Int, val cuota: Long, val total: Long, val intereses: Long, val sinInteres: Boolean)

// por que hay que avisar antes de usar una tarjeta
data class AvisoTarjeta(val bloqueada: Boolean, val soloTiendas: Boolean, val mensaje: String?)

object Tarjetas {

    fun de(datos: Datos, id: String?): Tarjeta? = id?.let { i -> datos.tarjetas.firstOrNull { it.id == i } }

    private fun deuda(datos: Datos, t: Tarjeta): Deuda? = t.deudaId?.let { d -> datos.deudas.firstOrNull { it.id == d } }

    // tasa mensual de la deuda de la tarjeta, si genera intereses
    private fun tasa(datos: Datos, t: Tarjeta): Double? = deuda(datos, t)?.takeIf { it.generaIntereses }?.let { Calculos.tasaMensual(it.tasaEa) }

    // a 1 cuota sin interes si la tarjeta no cobra; si no, cuota con la regla 3.8
    fun costo(datos: Datos, t: Tarjeta, monto: Long, cuotas: Int): CostoCompra {
        val n = maxOf(1, cuotas)
        val tm = tasa(datos, t)
        val cobra = n > 1 || t.interes1Cuota
        if (!cobra || tm == null || tm == 0.0) {
            val cuota = Calculos.cuotaParaMeses(monto, null, n)
            return CostoCompra(n, cuota, monto, 0, !cobra)
        }
        val cuota = Calculos.cuotaParaMeses(monto, tm, n)
        val total = cuota * n
        return CostoCompra(n, cuota, total, total - monto, false)
    }

    fun cuotasSugeridas(t: Tarjeta): Int = if (t.preferidaPuente) 1 else (t.cuotasPorDefecto ?: 1)

    // bloqueada pide doble confirmacion; solo tiendas avisa que es deuda nueva
    fun aviso(datos: Datos, t: Tarjeta, monto: Long): AvisoTarjeta {
        if (!t.usoPermitido) {
            val c = costo(datos, t, monto, t.cuotasPorDefecto ?: 1)
            val tiendas = if (t.soloTiendas) "Solo sirve en tiendas; esto es deuda nueva. " else ""
            return AvisoTarjeta(true, t.soloTiendas,
                tiendas + "Esta tarjeta está bloqueada en tu plan. A ${c.cuotas} cuotas esta compra te cuesta ${Formato.pesos(c.intereses)} en intereses.")
        }
        if (t.soloTiendas) return AvisoTarjeta(false, true, "Solo sirve en tiendas; esto es deuda nueva.")
        return AvisoTarjeta(false, false, null)
    }

    fun requiereConfirmacion(t: Tarjeta): Boolean = !t.usoPermitido

    // pagos que deja una compra con tarjeta: uno a 1 cuota o N mensuales en el dia de pago de la deuda
    fun puentes(datos: Datos, m: Movimiento): List<Obligacion> {
        val t = de(datos, m.tarjetaId) ?: return emptyList()
        val compra = Fechas.leer(m.fecha) ?: return emptyList()
        val n = maxOf(1, m.cuotas ?: 1)
        val c = costo(datos, t, m.monto, n)
        val concepto = "Puente ${t.nombre} · " + m.nota.ifBlank { m.categoria.ifBlank { "compra" } }
        fun ob(f: LocalDate, valor: Long) = Obligacion(
            Obligaciones.clave(Origen.PUENTE, m.id, f), Origen.PUENTE, m.id, f, concepto, Categorias.DEUDA, valor, deudaId = t.deudaId,
        )
        if (n == 1) {
            val f = Fechas.leer(m.fechaPagoPuente) ?: proximoSueldo(datos, compra)
            return listOf(ob(f, c.cuota))
        }
        val dia = deuda(datos, t)?.diaPago ?: compra.dayOfMonth
        var ym = YearMonth.from(compra)
        if (Fechas.diaEnMes(ym, dia) <= compra) ym = ym.plusMonths(1)
        return (0 until n).map { i -> ob(Fechas.diaEnMes(ym.plusMonths(i.toLong()), dia), c.cuota) }
    }

    // fecha del siguiente sueldo despues de una fecha
    fun proximoSueldo(datos: Datos, fecha: LocalDate): LocalDate = Obligaciones.periodo(datos, fecha).hasta.plusDays(1)

    // compras con tarjeta que suben la deuda desde una fecha
    fun compras(datos: Datos, deudaId: String): List<Movimiento> {
        val ids = datos.tarjetas.filter { it.deudaId == deudaId }.map { it.id }.toSet()
        return datos.movimientos.filter { it.tarjetaId != null && it.tarjetaId in ids }
    }

    // cupo libre: el reportado si es mas nuevo que el ultimo movimiento; si no, cupo total menos saldo
    fun cupoDisponible(datos: Datos, t: Tarjeta, hoy: LocalDate): Long? {
        val d = deuda(datos, t)
        val reportado = t.cupoDisponible
        val fechaCupo = Fechas.leer(t.fechaCupo)
        val ultimo = d?.let { dd ->
            (Calculos.pagosDeuda(dd, datos.movimientos) + compras(datos, dd.id)).mapNotNull { Fechas.leer(it.fecha) }.maxOrNull()
        }
        if (reportado != null && fechaCupo != null && (ultimo == null || fechaCupo > ultimo)) return reportado
        if (t.cupoTotal != null && d != null) return t.cupoTotal - Calculos.saldoEstimado(d, datos, hoy)
        if (reportado != null && fechaCupo != null && d != null) {
            val gastado = compras(datos, d.id).filter { (Fechas.leer(it.fecha) ?: hoy) >= fechaCupo }.sumOf { it.monto }
            val pagado = Calculos.pagosDeuda(d, datos.movimientos).filter { (Fechas.leer(it.fecha) ?: hoy) > fechaCupo }.sumOf { it.monto }
            return reportado - gastado + pagado
        }
        return reportado ?: t.cupoTotal
    }

    // porcentaje usado del cupo (0 a 1)
    fun uso(datos: Datos, t: Tarjeta, hoy: LocalDate): Double? {
        val total = t.cupoTotal ?: return null
        if (total <= 0) return null
        val libre = cupoDisponible(datos, t, hoy) ?: return null
        return ((total - libre).toDouble() / total).coerceIn(0.0, 1.0)
    }

    fun redondearMiles(v: Long): Long = (v / 1000.0).toLong() * 1000

    fun pesosPorDia(total: Long, dias: Int): Long = (total.toDouble() / maxOf(1, dias)).roundToLong()
}
