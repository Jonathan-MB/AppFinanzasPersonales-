package com.jonathan.miscuentas

import com.jonathan.miscuentas.dominio.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ProyeccionTest {

    private val hoy = EscenarioV4.hoy
    private val d = EscenarioV4.datos()

    @Test
    fun saldoQueNoAlcanzaDaElFaltante() {
        val p = Proyecciones.calcular(d, hoy)
        assertEquals(50000L, Calculos.deberiaTener(d))
        assertEquals(LocalDate.of(2026, 10, 30), p.proximoSueldo)
        // perros 17 y 24 + lo que queda de comida - lo que hay
        assertEquals(60000L + 100000 - 50000, p.faltanteHastaSueldo)
        assertEquals(0L, p.libreHastaSueldo)
        assertEquals(0L, p.puedesGastarPorDia)
        // el horizonte llega al dia anterior al segundo sueldo
        assertEquals(LocalDate.of(2026, 11, 14), p.dias.last().fecha)
    }

    @Test
    fun pausadoYPospuestoNoEntranAntesDelSueldo() {
        val antes = Obligaciones.generar(d, hoy, LocalDate.of(2026, 10, 29))
        assertFalse(antes.any { it.origenId == "g-playa" })
        assertFalse(antes.any { it.origenId == "g-voley" || it.origenId == "g-luz" })
        val el30 = Obligaciones.generar(d, LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 30))
        assertEquals(setOf("g-voley", "g-luz"), el30.filter { it.tipoOrigen == Origen.GASTO }.map { it.origenId }.toSet())
        assertEquals(LocalDate.of(2026, 10, 15), el30.first { it.origenId == "g-voley" }.fechaOriginal)
    }

    @Test
    fun motoNoCuentaPeroSaleComoEscenario() {
        val conMoto = d.copy(ingresosProgramados = d.ingresosProgramados +
            IngresoProgramado("ip-moto", "Moto", 50000, FrecuenciaIngreso.MENSUAL, dia = 20, tipo = TipoIngreso.MOTO))
        val sin = Proyecciones.calcular(d, hoy)
        val con = Proyecciones.calcular(conMoto, hoy)
        assertEquals(sin.faltanteHastaSueldo, con.faltanteHastaSueldo)
        assertEquals(sin.llegasCon + 50000, con.llegasConIncierto)
        assertTrue(sin.conIncierto.isEmpty())
    }

    @Test
    fun motoRegistradaSiCuenta() {
        val conMoto = d.copy(ingresos = d.ingresos + Ingreso("i-moto", "2026-10-16", 40000, TipoIngreso.MOTO, cuentaId = "c-nequi"))
        assertEquals(Proyecciones.calcular(d, hoy).faltanteHastaSueldo - 40000, Proyecciones.calcular(conMoto, hoy).faltanteHastaSueldo)
    }

    @Test
    fun primaEstimadaEntraYSeMarca() {
        val conPrima = d.copy(ingresosProgramados = d.ingresosProgramados +
            IngresoProgramado("ip-prima", "Prima", 500000, FrecuenciaIngreso.UNICO, fecha = "2026-10-16", tipo = TipoIngreso.PRIMA, esEstimado = true))
        val p = Proyecciones.calcular(conPrima, hoy)
        assertTrue(p.dias.first { it.fecha == LocalDate.of(2026, 10, 16) }.conIngresoEstimado)
        assertEquals(0L, p.faltanteHastaSueldo)
    }

    @Test
    fun presupuestoSeReparteHastaElFinDelPeriodo() {
        val p = Proyecciones.calcular(d.copy(gastosFijos = emptyList()), hoy)
        // del 16 al 29 son 14 dias para los 100.000 que quedan
        val periodo = p.dias.filter { it.fecha < LocalDate.of(2026, 10, 30) }
        assertEquals(100000L, periodo.sumOf { it.salidas })
        assertTrue(periodo.all { it.salidas == 7142L || it.salidas == 7143L })
    }

    @Test
    fun vencidosSeRestanHoy() {
        val conVencido = d.copy(eventos = listOf(Evento("e1", "Atrasado", 10000, "2026-10-14")))
        val p = Proyecciones.calcular(conVencido, hoy)
        assertEquals(Proyecciones.calcular(d, hoy).dias.first().salidas + 10000, p.dias.first().salidas)
    }

    @Test
    fun horizonteCruzaFebrero() {
        val feb = LocalDate.of(2027, 2, 20)
        val p = Proyecciones.calcular(d, feb)
        assertEquals(LocalDate.of(2027, 2, 28), p.proximoSueldo)
        assertEquals(LocalDate.of(2027, 3, 14), p.dias.last().fecha)
        // el 28 llega el sueldo del 30
        assertEquals(1600000L, p.dias.first { it.fecha == LocalDate.of(2027, 2, 28) }.entradas)
    }

    @Test
    fun presupuestoRealSegunLoQueAlcanza() {
        val p = Proyecciones.calcular(d, hoy)
        val r = Proyecciones.presupuestoReal(d, "p-comida", hoy, p)!!
        assertEquals(100000L, r.restante)
        assertEquals(0L, r.realDisponible)
        assertTrue(r.noAlcanza)
        // con plata de sobra alcanza todo lo que queda
        val rico = d.copy(config = d.config.copy(plataInicial = 2000000))
        val r2 = Proyecciones.presupuestoReal(rico, "p-comida", hoy, Proyecciones.calcular(rico, hoy))!!
        assertEquals(100000L, r2.realDisponible)
        assertFalse(r2.noAlcanza)
    }

    @Test
    fun avisoDePagoAnticipado() {
        val p = Proyecciones.calcular(d, hoy)
        val rot = Obligaciones.generar(d, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3)).first { it.origenId == "d-rot" }
        assertNotNull(Proyecciones.avisoAnticipado(d, rot, 400000, LocalDate.of(2026, 10, 28), p))
        val perros = Obligaciones.generar(d, hoy, LocalDate.of(2026, 10, 17)).first { it.origenId == "g-perros" }
        assertNull(Proyecciones.avisoAnticipado(d, perros, 30000, hoy, p))
    }

    @Test
    fun sugerenciaPrimeroColchonLuegoAbono() {
        val rico = d.copy(config = d.config.copy(plataInicial = 2000000))
        val s = Proyecciones.sugerencia(rico, hoy, Proyecciones.calcular(rico, hoy))!!
        assertEquals(300000L, s.alColchon)
        val conColchon = rico.copy(cuentaMovs = Saldos.trasladar("c-davivienda", "c-colchon", 300000, "2026-10-16"))
        val s2 = Proyecciones.sugerencia(conColchon, hoy, Proyecciones.calcular(conColchon, hoy))!!
        assertEquals(0L, s2.alColchon)
        assertTrue(s2.abono > 0 && s2.abono % 1000 == 0L)
        assertNull(Proyecciones.sugerencia(d, hoy, Proyecciones.calcular(d, hoy)))
    }
}
