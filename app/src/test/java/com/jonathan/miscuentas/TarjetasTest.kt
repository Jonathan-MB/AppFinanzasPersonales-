package com.jonathan.miscuentas

import com.jonathan.miscuentas.dominio.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TarjetasTest {

    private val hoy = EscenarioV4.hoy
    private val d = EscenarioV4.datos()
    private val t = d.tarjetas.first { it.id == "t-tc" }

    // paga los perros del 17 con la tarjeta a 1 cuota y lo piensa pagar el 30
    private fun conCompra() = d.copy(movimientos = d.movimientos + Movimiento(
        "m-tc", "2026-10-16", 30000, claveObligacion = "gasto:g-perros:2026-10-17", tipo = TipoMovimiento.PAGO_PROGRAMADO,
        categoria = "Perros", liquida = true, tarjetaId = "t-tc", cuotas = 1, fechaPagoPuente = "2026-10-30",
    ))

    @Test
    fun compraNoBajaLaPlataPeroSubeLaDeuda() {
        val c = conCompra()
        assertEquals(Calculos.deberiaTener(d), Calculos.deberiaTener(c))
        assertEquals(Saldos.saldos(d), Saldos.saldos(c))
        val deuda = c.deudas.first { it.id == "d-tc" }
        assertEquals(1030000L, Calculos.saldoEstimado(deuda, c, hoy))
        assertEquals(370000L, Tarjetas.cupoDisponible(c, t, hoy))
    }

    @Test
    fun compraCreaElPuenteYBajaElFaltante() {
        val c = conCompra()
        val puente = Obligaciones.generar(c, LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 30)).single { it.tipoOrigen == Origen.PUENTE }
        assertEquals(30000L, puente.presupuestado)
        assertEquals("puente:m-tc:2026-10-30", puente.clave)
        assertTrue(puente.concepto.startsWith("Puente Tarjeta X"))
        // los perros del 17 quedan pagados con la tarjeta
        val perros = Obligaciones.generar(c, hoy, LocalDate.of(2026, 10, 17)).first { it.origenId == "g-perros" }
        assertEquals(0L, perros.falta)
        assertEquals(Proyecciones.calcular(d, hoy).faltanteHastaSueldo - 30000, Proyecciones.calcular(c, hoy).faltanteHastaSueldo)
    }

    @Test
    fun pagarElPuenteBajaLaDeuda() {
        val c = conCompra()
        val pagado = c.copy(movimientos = c.movimientos + Movimiento(
            "m-pago", "2026-10-30", 30000, claveObligacion = "puente:m-tc:2026-10-30", tipo = TipoMovimiento.PAGO_PROGRAMADO,
            categoria = Categorias.DEUDA, deudaId = "d-tc", cuentaId = "c-davivienda",
        ))
        val deuda = pagado.deudas.first { it.id == "d-tc" }
        assertEquals(1000000L, Calculos.saldoEstimado(deuda, pagado, LocalDate.of(2026, 10, 30)))
        val puente = Obligaciones.generar(pagado, LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 30)).single { it.tipoOrigen == Origen.PUENTE }
        assertEquals(0L, puente.falta)
    }

    @Test
    fun variasCuotasCreanVariosPagosConLaCuotaDeLaRegla38() {
        val m = Movimiento("m-n", "2026-10-16", 300000, tipo = TipoMovimiento.NO_PLANEADO, categoria = "Ropa", tarjetaId = "t-tc", cuotas = 3)
        val c = d.copy(movimientos = d.movimientos + m)
        val puentes = Tarjetas.puentes(c, m)
        assertEquals(3, puentes.size)
        assertEquals(listOf(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 28), LocalDate.of(2026, 12, 28)), puentes.map { it.fecha })
        val cuota = Calculos.cuotaParaMeses(300000, Calculos.tasaMensual(0.2859), 3)
        assertTrue(puentes.all { it.presupuestado == cuota })
        val costo = Tarjetas.costo(c, t, 300000, 3)
        assertEquals(cuota * 3 - 300000, costo.intereses)
    }

    @Test
    fun unaCuotaSinInteresNoCuestaNada() {
        val c = Tarjetas.costo(d, t, 100000, 1)
        assertTrue(c.sinInteres)
        assertEquals(0L, c.intereses)
        assertEquals(100000L, c.cuota)
    }

    @Test
    fun tarjetaBloqueadaPideConfirmacionYMuestraElCosto() {
        val rot = d.tarjetas.first { it.id == "t-rot" }
        assertTrue(Tarjetas.requiereConfirmacion(rot))
        val aviso = Tarjetas.aviso(d, rot, 100000)
        assertTrue(aviso.bloqueada)
        assertTrue(aviso.mensaje!!.contains("36 cuotas"))
        assertFalse(Tarjetas.requiereConfirmacion(t))
        val tienda = Tarjetas.aviso(d, d.tarjetas.first { it.id == "t-tienda" }, 100000)
        assertTrue(tienda.soloTiendas)
        assertEquals("Solo sirve en tiendas; esto es deuda nueva.", tienda.mensaje)
    }

    @Test
    fun cupoReportadoSeUsaSiEsMasNuevo() {
        assertEquals(400000L, Tarjetas.cupoDisponible(d, t, hoy))
    }
}
