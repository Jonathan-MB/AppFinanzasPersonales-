package com.jonathan.miscuentas

import com.jonathan.miscuentas.dominio.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TxtTest {

    private val hoy = LocalDate.of(2026, 10, 20)
    private val momento = LocalDateTime.of(2026, 10, 20, 8, 15)

    private fun conMovimientos(): Datos {
        val d = DatosPrueba.datos()
        return d.copy(
            config = d.config.copy(plataInicial = 300000, saldoReal = 1200000, fechaSaldoReal = "2026-10-20"),
            movimientos = listOf(
                Movimiento("m1", "2026-10-15", 100000, "pago | tarjeta\ncon salto", "deuda:d-a:2026-10-15", TipoMovimiento.PAGO_PROGRAMADO, Categorias.DEUDA, "d-a"),
                Movimiento("m2", "2026-10-16", 35000, "almuerzo", null, TipoMovimiento.NO_PLANEADO, "Comida fuera"),
            ),
            ingresos = listOf(Ingreso("i1", "2026-10-15", 1500000, TipoIngreso.SALARIO, "", "ingreso:ip-15:2026-10-15")),
            deudaSaldos = listOf(DeudaSaldo("s1", "d-d", "2026-10-18", 480000)),
        )
    }

    @Test
    fun exportaTodasLasSecciones() {
        val txt = Txt.exportar(conMovimientos(), hoy, momento)
        listOf("#FINANZAS_EXPORT v1", "#generado=2026-10-20 08:15", "[RESUMEN]", "[CONFIG]", "[DEUDAS]", "[DEUDA_SALDOS]",
            "[GASTOS_FIJOS]", "[PRESUPUESTOS]", "[INGRESOS_PROGRAMADOS]", "[EVENTOS]", "[CALENDARIO]", "[MOVIMIENTOS]",
            "[INGRESOS]", "[CATEGORIAS]", "#FIN").forEach { assertTrue("falta $it", txt.contains(it)) }
        // sin saltos ni pipes dentro de los campos
        assertTrue(txt.contains("pago / tarjeta con salto"))
        assertEquals("finanzas_2026-10-20_0815.txt", Txt.nombreArchivo(momento))
    }

    @Test
    fun idaYVueltaDaLoMismo() {
        val original = conMovimientos()
        val primero = Txt.exportar(original, hoy, momento)
        val vacio = Datos(config = Config(fechaInicio = "2000-01-01"))
        val res = Txt.importar(primero, vacio)
        assertTrue(res.errores.toString(), res.errores.isEmpty())
        val segundo = Txt.exportar(res.datos, hoy, momento.plusHours(1))
        fun sinGenerado(s: String) = s.lines().filterNot { it.startsWith("#generado=") }
        assertEquals(sinGenerado(primero), sinGenerado(segundo))
    }

    @Test
    fun lineasMalasSeReportanSinAbortar() {
        val txt = Txt.exportar(conMovimientos(), hoy, momento)
            .replace("m2|2026-10-16|35000", "m2|fecha-mala|35000")
        val res = Txt.importar(txt, conMovimientos())
        assertEquals(1, res.errores.size)
        assertEquals(1, res.datos.movimientos.size)
    }

    @Test
    fun seccionQueFaltaSeConserva() {
        val actuales = conMovimientos()
        val txt = "#FINANZAS_EXPORT v1\n[EVENTOS]\nid|nombre|monto|fecha|categoria|deuda_id|notas\ne1|Nuevo|1000|2026-12-01|Otros||\n#FIN\n"
        val res = Txt.importar(txt, actuales)
        assertEquals(1, res.datos.eventos.size)
        assertEquals(actuales.deudas.size, res.datos.deudas.size)
        assertEquals(actuales.movimientos, res.datos.movimientos)
    }
}
