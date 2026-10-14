package com.jonathan.miscuentas

import com.jonathan.miscuentas.dominio.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

// casos de borde que salieron en la revision
class AuditoriaTest {

    private val datos = DatosPrueba.datos()

    private fun pago(id: String, clave: String, monto: Long, liquida: Boolean = false) =
        Movimiento(id, clave.takeLast(10), monto, claveObligacion = clave, tipo = TipoMovimiento.PAGO_PROGRAMADO, liquida = liquida)

    @Test
    fun pagoDeOtraSemanaNoCuentaEnRangoCorto() {
        // sabados de noviembre 2026: 7, 14, 21, 28
        val d = datos.copy(movimientos = listOf(pago("m1", "gasto:g-semanal:2026-11-07", 20000)))
        val dia14 = Obligaciones.generar(d, LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 14)).first { it.origenId == "g-semanal" }
        assertEquals(0L, dia14.pagado)
        val dia7 = Obligaciones.generar(d, LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 7)).first { it.origenId == "g-semanal" }
        assertEquals(20000L, dia7.pagado)
    }

    @Test
    fun pagoDeLaPrimeraQuincenaNoCuentaEnLaSegunda() {
        val d = datos.copy(movimientos = listOf(pago("m1", "presupuesto:p-comida:2026-11-15", 200000)))
        val segunda = Obligaciones.generar(d, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 30)).first { it.origenId == "p-comida" }
        assertEquals(0L, segunda.pagado)
    }

    @Test
    fun cambioDeDiaSigueReasignandoEnRangoCorto() {
        // pago hecho el dia 1 y luego el arriendo se movio al 5
        val d = datos.copy(
            movimientos = listOf(pago("m1", "gasto:g-arriendo:2026-11-01", 600000)),
            gastosFijos = datos.gastosFijos.map { if (it.id == "g-arriendo") it.copy(diaPago = 5) else it },
        )
        val o = Obligaciones.generar(d, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5)).first { it.origenId == "g-arriendo" }
        assertEquals(600000L, o.pagado)
    }

    @Test(timeout = 2000)
    fun diaSemanaFueraDeRangoNoSeCuelga() {
        val d = datos.copy(gastosFijos = listOf(GastoFijo("g-x", "Raro", 1000, Frecuencia.SEMANAL, diaSemana = 9)))
        assertTrue(Obligaciones.generar(d, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).isNotEmpty())
    }

    @Test
    fun liquidaIdaYVuelta() {
        val d = datos.copy(movimientos = listOf(pago("m1", "gasto:g-arriendo:2026-11-01", 580000, liquida = true)))
        val txt = Txt.exportar(d, LocalDate.of(2026, 11, 2), LocalDateTime.of(2026, 11, 2, 8, 0))
        val res = Txt.importar(txt, Datos(config = Config(fechaInicio = "2000-01-01")))
        assertTrue(res.datos.movimientos.single().liquida)
    }

    @Test
    fun txtViejoSinColumnaLiquida() {
        val txt = "#FINANZAS_EXPORT v1\n[MOVIMIENTOS]\nid|fecha|monto|tipo|clave_obligacion|categoria|deuda_id|nota\n" +
            "m1|2026-11-01|580000|pago_programado|gasto:g-arriendo:2026-11-01|Gasto fijo||\n#FIN\n"
        val res = Txt.importar(txt, datos)
        assertTrue(res.errores.isEmpty())
        assertFalse(res.datos.movimientos.single().liquida)
    }

    @Test
    fun abonoYLuegoPagoCompleto() {
        val clave = "gasto:g-arriendo:2026-11-01"
        val d = datos.copy(movimientos = listOf(pago("m1", clave, 300000), pago("m2", clave, 250000, liquida = true)))
        val o = Obligaciones.generar(d, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).first { it.clave == clave }
        assertEquals(550000L, o.pagado)
        assertEquals(0L, o.falta)
        assertEquals(600000L, o.estimado)
    }

    @Test
    fun quincenaEnFebreroYFinDeAnio() {
        assertEquals(LocalDate.of(2027, 2, 28), Obligaciones.quincena(LocalDate.of(2027, 2, 20)).second)
        assertEquals(LocalDate.of(2028, 2, 29), Obligaciones.quincena(LocalDate.of(2028, 2, 16)).second)
        assertEquals(LocalDate.of(2026, 12, 16) to LocalDate.of(2026, 12, 31), Obligaciones.quincena(LocalDate.of(2026, 12, 31)))
        assertEquals(LocalDate.of(2026, 12, 1) to LocalDate.of(2026, 12, 15), Obligaciones.quincena(LocalDate.of(2026, 12, 15)))
    }

    @Test
    fun nadaAntesDeLaFechaDeInicio() {
        val o = Obligaciones.generar(datos, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 12))
        assertTrue(o.isEmpty())
    }

    @Test
    fun deudaConCuotasTerminaATiempo() {
        // 36 cuotas con 6 pagadas: quedan 30 desde octubre 2026
        val todas = Obligaciones.generar(datos, LocalDate.of(2026, 10, 13), LocalDate.of(2030, 12, 31)).filter { it.origenId == "d-b" }
        assertEquals(30, todas.size)
    }
}
