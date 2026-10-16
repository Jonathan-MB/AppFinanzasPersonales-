package com.jonathan.miscuentas

import com.jonathan.miscuentas.dominio.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class PausasBolsilloTest {

    private val hoy = EscenarioV4.hoy
    private val d = EscenarioV4.datos()

    @Test
    fun pausadoNoSeGeneraYAlQuitarLaPausaVuelve() {
        val nov = Obligaciones.generar(d, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31))
        assertFalse(nov.any { it.origenId == "g-voley" || it.origenId == "g-playa" })
        val sinPausas = d.copy(pausas = emptyList())
        val nov2 = Obligaciones.generar(sinPausas, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31))
        assertEquals(2, nov2.count { it.origenId == "g-voley" })
        assertTrue(nov2.any { it.origenId == "g-playa" })
    }

    @Test
    fun pospuestoCambiaDePeriodoYConservaLoPagado() {
        val pagado = d.copy(movimientos = d.movimientos + Movimiento("m-v", "2026-10-30", 70000, claveObligacion = "gasto:g-voley:2026-10-15",
            tipo = TipoMovimiento.PAGO_PROGRAMADO, categoria = Categorias.GASTO_FIJO))
        val periodo30 = Obligaciones.pagosQuincena(pagado, LocalDate.of(2026, 10, 30))
        val voley = periodo30.first { it.origenId == "g-voley" }
        assertEquals(70000L, voley.pagado)
        assertEquals("gasto:g-voley:2026-10-15", voley.clave)
        assertFalse(Obligaciones.pagosQuincena(pagado, LocalDate.of(2026, 10, 16)).any { it.origenId == "g-voley" })
        // el estado se calcula con la fecha nueva
        assertEquals(Estado.PARCIAL, voley.estado(LocalDate.of(2026, 10, 20)))
    }

    @Test
    fun trasladoAlBolsilloNoCambiaElTotal() {
        val conBolsillo = d.copy(cuentaMovs = Saldos.trasladar("c-davivienda", "c-bolsillo", 20000, "2026-10-16"))
        assertEquals(Calculos.deberiaTener(d), Calculos.deberiaTener(conBolsillo))
        assertEquals(20000L, Saldos.deTipo(conBolsillo, TipoCuenta.BOLSILLO))
    }

    @Test
    fun libreSinBolsilloSoloEnLaQuincenaDelBolsillo() {
        val rico = d.copy(config = d.config.copy(plataInicial = 2000000))
        // el 16 es la quincena del 15: no aplica
        assertNull(Proyecciones.libreSinBolsillo(rico, hoy, Proyecciones.calcular(rico, hoy)))
        val el31 = LocalDate.of(2026, 10, 31)
        val conBolsillo = rico.copy(cuentaMovs = Saldos.trasladar("c-davivienda", "c-bolsillo", 200000, "2026-10-31"))
        val p = Proyecciones.calcular(conBolsillo, el31)
        assertEquals(maxOf(0, p.libreHastaSueldo - 200000), Proyecciones.libreSinBolsillo(conBolsillo, el31, p))
    }

    @Test
    fun bolsilloPorApartarCuandoLlegaElSueldoDelDia() {
        val el30 = LocalDate.of(2026, 10, 30)
        assertNull(Proyecciones.bolsilloPorApartar(d, el30))
        val conSueldo = d.copy(ingresos = d.ingresos + Ingreso("i-30", "2026-10-30", 1600000, TipoIngreso.SALARIO,
            claveIngresoProgramado = "ingreso:ip-30:2026-10-30", cuentaId = "c-davivienda"))
        assertEquals(200000L, Proyecciones.bolsilloPorApartar(conSueldo, el30))
        val apartado = conSueldo.copy(cuentaMovs = Saldos.trasladar("c-davivienda", "c-bolsillo", 200000, "2026-10-30"))
        assertNull(Proyecciones.bolsilloPorApartar(apartado, el30))
    }

    @Test
    fun colchonNoCuentaComoLibre() {
        val conColchon = d.copy(config = d.config.copy(plataInicial = 2000000),
            cuentaMovs = Saldos.trasladar("c-davivienda", "c-colchon", 300000, "2026-10-16"))
        val sin = d.copy(config = d.config.copy(plataInicial = 2000000))
        assertEquals(300000L, Proyecciones.calcular(conColchon, hoy).colchon)
        assertEquals(Proyecciones.calcular(sin, hoy).libreHastaSueldo - 300000, Proyecciones.calcular(conColchon, hoy).libreHastaSueldo)
    }

    @Test
    fun txtIdaYVueltaConLoNuevo() {
        val c = d.copy(movimientos = d.movimientos + Movimiento("m-tc", "2026-10-16", 30000, tipo = TipoMovimiento.NO_PLANEADO,
            categoria = "Perros", tarjetaId = "t-tc", cuotas = 1, fechaPagoPuente = "2026-10-30"))
        val momento = LocalDateTime.of(2026, 10, 16, 12, 0)
        val txt = Txt.exportar(c, hoy, momento)
        val res = Txt.importar(txt, Datos(config = Config(fechaInicio = "2000-01-01")))
        assertTrue(res.errores.toString(), res.errores.isEmpty())
        assertEquals(c.tarjetas.map { it.copy(createdAt = "", updatedAt = "") }.sortedBy { it.id }, res.datos.tarjetas.map { it.copy(createdAt = "", updatedAt = "") }.sortedBy { it.id })
        assertEquals(2, res.datos.pausas.size)
        assertEquals(2, res.datos.pospuestos.size)
        assertEquals(TipoCuenta.COLCHON, res.datos.cuentas.first { it.id == "c-colchon" }.tipo)
        assertEquals(200000L, res.datos.config.bolsilloMonto)
        fun sinGenerado(s: String) = s.lines().filterNot { it.startsWith("#generado=") }
        assertEquals(sinGenerado(txt), sinGenerado(Txt.exportar(res.datos, hoy, momento.plusHours(1))))
    }

    @Test
    fun txtV3ViejoSeLeeSinErrores() {
        val v3 = "#FINANZAS_EXPORT v1\n[CUENTAS]\nid|nombre|emoji|orden|activa\nc-a|A|🏦|0|si\n" +
            "[INGRESOS_PROGRAMADOS]\nid|nombre|monto|frecuencia|dia|fecha|tipo|es_estimado|activo|notas\nip-m|Moto|50000|mensual|20||moto|no|si|\n" +
            "[MOVIMIENTOS]\nid|fecha|monto|tipo|clave_obligacion|categoria|deuda_id|nota|liquida|cuenta_id\nm1|2026-10-15|1000|gasto_no_planeado||Ocio|||no|c-a\n#FIN\n"
        val res = Txt.importar(v3, d)
        assertTrue(res.errores.toString(), res.errores.isEmpty())
        assertEquals(TipoCuenta.NORMAL, res.datos.cuentas.single().tipo)
        assertTrue(res.datos.ingresosProgramados.single().esIncierto)
        assertNull(res.datos.movimientos.single().tarjetaId)
    }

    @Test
    fun actualizarBaseTomaTarjetasPausasYConservaLoRegistrado() {
        val archivo = Txt.exportar(d, hoy, LocalDateTime.of(2026, 10, 16, 12, 0))
        val actuales = d.copy(tarjetas = emptyList(), pausas = emptyList(), pospuestos = emptyList(),
            cuentaMovs = Saldos.trasladar("c-davivienda", "c-nequi", 1000, "2026-10-16"))
        val res = Txt.actualizarBase(archivo, actuales).datos
        assertEquals(3, res.tarjetas.size)
        assertEquals(2, res.pausas.size)
        assertEquals(actuales.cuentaMovs, res.cuentaMovs)
        assertEquals(actuales.movimientos, res.movimientos)
    }
}
