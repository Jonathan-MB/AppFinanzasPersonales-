package com.jonathan.miscuentas

import com.jonathan.miscuentas.dominio.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalculosTest {

    private val hoy = LocalDate.of(2026, 10, 20)
    private val datos = DatosPrueba.datos()

    @Test
    fun estados() {
        val f = LocalDate.of(2026, 10, 15)
        assertEquals(Estado.SIN_VALOR, Calculos.estado(0, 0, f, hoy))
        assertEquals(Estado.PAGADO, Calculos.estado(100, 100, f, hoy))
        assertEquals(Estado.VENCIDO_PARCIAL, Calculos.estado(100, 50, f, hoy))
        assertEquals(Estado.VENCIDO, Calculos.estado(100, 0, f, hoy))
        assertEquals(Estado.PARCIAL, Calculos.estado(100, 50, hoy.plusDays(3), hoy))
        assertEquals(Estado.PENDIENTE, Calculos.estado(100, 0, hoy.plusDays(3), hoy))
    }

    @Test
    fun formatoPesos() {
        assertEquals("$1.234.567", Formato.pesos(1234567))
        assertEquals("$0", Formato.pesos(0))
        assertEquals("$999", Formato.pesos(999))
        assertEquals("-$50.000", Formato.pesos(-50000))
        assertEquals(1234567L, Formato.leerPesos("$1.234.567"))
    }

    @Test
    fun deberiaTener() {
        val d = datos.copy(
            config = datos.config.copy(plataInicial = 500000),
            ingresos = listOf(Ingreso("i1", "2026-10-15", 1500000, TipoIngreso.SALARIO)),
            movimientos = listOf(Movimiento("m1", "2026-10-15", 150000, tipo = TipoMovimiento.PAGO_PROGRAMADO)),
        )
        assertEquals(500000 + 1500000 - 150000L, Calculos.deberiaTener(d))
    }

    @Test
    fun pagoLigadoCambiaEstado() {
        val clave = Obligaciones.clave(Origen.DEUDA, "d-a", LocalDate.of(2026, 10, 15))
        val d = datos.copy(movimientos = listOf(Movimiento("m1", "2026-10-15", 100000, claveObligacion = clave, tipo = TipoMovimiento.PAGO_PROGRAMADO, deudaId = "d-a")))
        val o = Obligaciones.generar(d, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)).first { it.clave == clave }
        assertEquals(100000L, o.pagado)
        assertEquals(Estado.PAGADO, o.estado(hoy))
        assertEquals(2000000L - 100000, Calculos.saldoEstimado(d.deudas.first { it.id == "d-a" }, d, hoy))
    }

    @Test
    fun pagoCompletoConOtroValorQuedaPagado() {
        // recibo presupuestado en 600.000 que llego por 580.000
        val clave = Obligaciones.clave(Origen.GASTO, "g-arriendo", LocalDate.of(2026, 11, 1))
        val completo = datos.copy(movimientos = listOf(Movimiento("m1", "2026-11-01", 580000, claveObligacion = clave, tipo = TipoMovimiento.PAGO_PROGRAMADO, liquida = true)))
        val o = Obligaciones.generar(completo, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).first { it.clave == clave }
        assertEquals(Estado.PAGADO, o.estado(hoy))
        assertEquals(0L, o.falta)
        assertEquals(580000L, o.presupuestado)
        assertEquals(600000L, o.estimado)

        // el mismo valor como abono queda debiendo
        val abono = datos.copy(movimientos = listOf(Movimiento("m1", "2026-11-01", 580000, claveObligacion = clave, tipo = TipoMovimiento.PAGO_PROGRAMADO)))
        val p = Obligaciones.generar(abono, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).first { it.clave == clave }
        assertEquals(20000L, p.falta)
    }

    @Test
    fun pagosDeLaQuincena() {
        val (desde, hasta) = Obligaciones.quincena(LocalDate.of(2026, 11, 20))
        assertEquals(LocalDate.of(2026, 11, 16), desde)
        assertEquals(LocalDate.of(2026, 11, 30), hasta)
        val pagos = Obligaciones.pagosQuincena(datos, LocalDate.of(2026, 11, 3))
        assertTrue(pagos.all { it.fecha.dayOfMonth <= 15 && it.tipoOrigen != Origen.PRESUPUESTO })
        assertTrue(pagos.any { it.concepto == "Arriendo" })
    }

    @Test
    fun calendarioFuturoYFechasInexistentes() {
        val marzo = Obligaciones.generar(datos, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 31))
        assertTrue(marzo.any { it.concepto == "Arriendo" })
        // febrero no tiene 30: la quincena 30 cae el 28
        val feb = Obligaciones.ingresosEsperados(datos, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 28))
        assertTrue(feb.any { it.fecha == LocalDate.of(2027, 2, 28) && it.programadoId == "ip-30" })
        // la deuda corta termina en enero 2027
        assertFalse(marzo.any { it.origenId == "d-e" })
        val enero = Obligaciones.generar(datos, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31))
        assertTrue(enero.any { it.origenId == "d-e" })
    }

    @Test
    fun cambiarDiaConservaPagos() {
        val clave = Obligaciones.clave(Origen.GASTO, "g-arriendo", LocalDate.of(2026, 11, 1))
        val conPago = datos.copy(movimientos = listOf(Movimiento("m1", "2026-11-01", 600000, claveObligacion = clave, tipo = TipoMovimiento.PAGO_PROGRAMADO)))
        val movido = conPago.copy(gastosFijos = conPago.gastosFijos.map { if (it.id == "g-arriendo") it.copy(diaPago = 5) else it })
        val nov = Obligaciones.generar(movido, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).first { it.origenId == "g-arriendo" }
        assertEquals(LocalDate.of(2026, 11, 5), nov.fecha)
        assertEquals(600000L, nov.pagado)
    }

    @Test
    fun tasaYPlazo() {
        val tm = Calculos.tasaMensual(0.2859)!!
        assertEquals(0.021166, tm, 0.0001)
        val p = Calculos.plazo(1000000, 100000, tm)
        assertEquals(12, p.meses)
        assertEquals(1200000L, p.totalPagar)
        val sin = Calculos.plazo(1000000, 300000, null)
        assertEquals(4, sin.meses)
        assertTrue(sin.sinTasa)
        // cuota menor que el interes: no baja
        assertTrue(Calculos.plazo(10000000, 100000, 0.03).noBaja)
    }

    @Test
    fun cuotaParaMeses() {
        val tm = Calculos.tasaMensual(0.2859)!!
        val cuota = Calculos.cuotaParaMeses(1000000, tm, 12)
        assertEquals(12, Calculos.plazo(1000000, cuota, tm).meses)
        assertEquals(250000L, Calculos.cuotaParaMeses(1000000, null, 4))
    }

    @Test
    fun endeudamientoPrueba() {
        val pct = Calculos.endeudamiento(datos)!!
        assertEquals(0.25, pct, 0.001)
        assertEquals("verde", Calculos.semaforo(pct))
        assertEquals("rojo", Calculos.semaforo(0.41))
        assertEquals("verde", Calculos.semaforo(0.2))
        assertEquals("amarillo", Calculos.semaforo(0.32))
    }

    @Test
    fun avanceExcluyeExtras() {
        val obl = listOf(
            Obligacion("a", Origen.GASTO, "x", hoy, "A", "", 100, pagado = 100),
            Obligacion("b", Origen.GASTO, "y", hoy, "B", "", 100, pagado = 150),
        )
        assertEquals(1.0, Calculos.avance(obl)!!, 0.0001)
    }

    @Test
    fun reparto() {
        val q = Calculos.reparto(datos, YearMonth.of(2026, 11))
        assertEquals(1500000L, q.ingresos15)
        assertEquals(1500000L, q.ingresos30)
        // dias 15 y 16 van en la quincena 15
        assertEquals(100000L + 300000, q.deudas15)
        assertEquals(200000L, q.presupuestos15)
    }

    @Test
    fun planBolaDeNieve() {
        val plan = Calculos.planDeAtaque(datos, ModoPlan.BOLA_NIEVE, hoy).map { it.deuda.id }
        assertEquals("d-d", plan.first())
        assertFalse(plan.contains("d-e"))
        assertTrue(plan.indexOf("d-f") > plan.indexOf("d-b"))
    }

    @Test
    fun prestamoEnCeroAntesDeRecibirlo() {
        val d = datos.deudas.first { it.id == "d-g" }
        assertEquals(0L, Calculos.saldoEstimado(d, datos, hoy))
        assertEquals(1000000L, Calculos.saldoEstimado(d, datos, LocalDate.of(2026, 11, 26)))
    }
}
