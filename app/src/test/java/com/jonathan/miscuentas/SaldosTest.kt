package com.jonathan.miscuentas

import com.jonathan.miscuentas.dominio.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class SaldosTest {

    private val base = DatosPrueba.datos()
    private val dav = "c-davivienda"
    private val neq = "c-nequi"
    private val efe = "c-efectivo"

    private fun gasto(id: String, monto: Long, cuenta: String?) = Movimiento(id, "2026-10-15", monto, tipo = TipoMovimiento.NO_PLANEADO, cuentaId = cuenta, createdAt = "2026-10-15T10:0$id")
    private fun ingreso(id: String, monto: Long, cuenta: String?) = Ingreso(id, "2026-10-15", monto, TipoIngreso.SALARIO, cuentaId = cuenta, createdAt = "2026-10-15T09:00")

    @Test
    fun cadaGastoBajaSuCuenta() {
        val d = base.copy(
            config = base.config.copy(plataInicial = 100000),
            ingresos = listOf(ingreso("i1", 1000000, dav)),
            movimientos = listOf(gasto("1", 50000, neq), gasto("2", 20000, null)),
        )
        val s = Saldos.saldos(d)
        // sin cuenta va a la primera, igual que la plata inicial
        assertEquals(100000L + 1000000 - 20000, s[dav])
        assertEquals(-50000L, s[neq])
        assertEquals(0L, s[efe])
        // la suma de las cuentas es lo que deberia tener
        assertEquals(Calculos.deberiaTener(d), s.values.sum())
    }

    @Test
    fun retiroNoCambiaElTotal() {
        val d = base.copy(ingresos = listOf(ingreso("i1", 500000, dav)))
        val conRetiro = d.copy(cuentaMovs = Saldos.trasladar(dav, efe, 200000, "2026-10-15"))
        val s = Saldos.saldos(conRetiro)
        assertEquals(300000L, s[dav])
        assertEquals(200000L, s[efe])
        assertEquals(Calculos.deberiaTener(d), Calculos.deberiaTener(conRetiro))
    }

    @Test
    fun cuadrarDejaTodoIgualAlRealYAnotaElDesfase() {
        val d = base.copy(
            ingresos = listOf(ingreso("i1", 1000000, dav)),
            movimientos = listOf(gasto("1", 100000, dav)),
        )
        // segun lo registrado hay 900.000 en davivienda; de verdad hay 800.000 ahi, 30.000 en nequi y 20.000 en efectivo
        val reales = mapOf(dav to 800000L, neq to 30000L, efe to 20000L)
        val filas = Saldos.cuadrar(d, reales, "2026-10-15")
        val despues = d.copy(cuentaMovs = filas)
        assertEquals(reales, Saldos.saldos(despues))
        assertEquals(850000L, Calculos.deberiaTener(despues))
        val cuadre = Saldos.ultimoCuadre(despues)!!
        assertEquals(-50000L, cuadre.desfase)
        // la marca no mueve plata
        assertEquals(850000L, Saldos.saldos(despues).values.sum())
    }

    @Test
    fun gastoDespuesDeCuadrarSigueBajando() {
        val d = base.copy(ingresos = listOf(ingreso("i1", 500000, dav)))
        val cuadrado = d.copy(cuentaMovs = Saldos.cuadrar(d, mapOf(dav to 400000L, neq to 0L, efe to 100000L), "2026-10-15"))
        val conGasto = cuadrado.copy(movimientos = listOf(gasto("1", 30000, efe)))
        assertEquals(70000L, Saldos.saldos(conGasto)[efe])
        assertEquals(470000L, Calculos.deberiaTener(conGasto))
    }

    @Test
    fun cuentaPorDefectoEsLaUltimaUsada() {
        val d = base.copy(movimientos = listOf(gasto("1", 1000, neq), gasto("2", 1000, efe)))
        assertEquals(efe, Saldos.porDefecto(d))
        assertEquals(dav, Saldos.porDefecto(base))
    }

    @Test
    fun cuentasEnElTxtIdaYVuelta() {
        val d = base.copy(
            ingresos = listOf(ingreso("i1", 500000, neq)),
            movimientos = listOf(gasto("1", 1000, efe)),
            cuentaMovs = Saldos.trasladar(neq, efe, 50000, "2026-10-15", "retiro"),
        )
        val txt = Txt.exportar(d, LocalDate.of(2026, 10, 15), LocalDateTime.of(2026, 10, 15, 8, 0))
        val res = Txt.importar(txt, Datos(config = Config(fechaInicio = "2000-01-01")))
        assertTrue(res.errores.toString(), res.errores.isEmpty())
        assertEquals(Saldos.saldos(d), Saldos.saldos(res.datos))
        assertEquals(efe, res.datos.movimientos.single().cuentaId)
    }

    @Test
    fun txtViejoSinCuentasConservaLasActuales() {
        val viejo = "#FINANZAS_EXPORT v1\n[MOVIMIENTOS]\nid|fecha|monto|tipo|clave_obligacion|categoria|deuda_id|nota|liquida\n" +
            "m1|2026-10-15|1000|gasto_no_planeado||Ocio|||no\n#FIN\n"
        val res = Txt.importar(viejo, base)
        assertEquals(base.cuentas, res.datos.cuentas)
        assertNull(res.datos.movimientos.single().cuentaId)
    }
}
