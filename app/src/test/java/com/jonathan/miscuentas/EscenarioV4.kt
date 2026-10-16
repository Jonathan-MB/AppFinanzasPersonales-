package com.jonathan.miscuentas

import com.jonathan.miscuentas.dominio.*
import java.time.LocalDate

// datos inventados con la misma forma del caso de aceptacion de la version 4
object EscenarioV4 {
    val hoy: LocalDate = LocalDate.of(2026, 10, 16)

    fun datos(): Datos {
        val base = Semilla.datos("2026-10-13T08:00", "2026-10-13")
        return base.copy(
            // con lo gastado en comida quedan 50.000 en las cuentas
            config = base.config.copy(plataInicial = 200000, bolsilloCuenta = "c-bolsillo", bolsilloMonto = 200000, bolsilloDia = 30,
                metaColchon = 300000, colchonCuenta = "c-colchon"),
            cuentas = base.cuentas + listOf(
                Cuenta("c-bolsillo", "Bolsillo", "🔒", 3, true, tipo = TipoCuenta.BOLSILLO),
                Cuenta("c-colchon", "Colchón", "🛟", 4, true, tipo = TipoCuenta.COLCHON),
            ),
            ingresosProgramados = listOf(
                IngresoProgramado("ip-15", "Salario 15", 1600000, FrecuenciaIngreso.QUINCENAL_DIA, dia = 15, tipo = TipoIngreso.SALARIO),
                IngresoProgramado("ip-30", "Salario 30", 1600000, FrecuenciaIngreso.QUINCENAL_DIA, dia = 30, tipo = TipoIngreso.SALARIO),
            ),
            // el sueldo del 15 ya llego
            ingresos = listOf(Ingreso("i-15", "2026-10-15", 1600000, TipoIngreso.SALARIO, claveIngresoProgramado = "ingreso:ip-15:2026-10-15", cuentaId = "c-davivienda")),
            gastosFijos = listOf(
                GastoFijo("g-perros", "Comida perros", 30000, Frecuencia.SEMANAL, diaSemana = 6, quincenaAsignada = Quincena.DIVIDIDO),
                GastoFijo("g-playa", "Juego", 16000, Frecuencia.SEMANAL, diaSemana = 6, quincenaAsignada = Quincena.DIVIDIDO),
                GastoFijo("g-voley", "Deporte", 140000, Frecuencia.MENSUAL, diaPago = 15, quincenaAsignada = Quincena.Q15),
                GastoFijo("g-luz", "Luz", 100000, Frecuencia.MENSUAL, diaPago = 20, quincenaAsignada = Quincena.Q15),
            ),
            presupuestos = listOf(Presupuesto("p-comida", "Comida", 250000, Periodicidad.QUINCENAL, categoria = Categorias.COMIDA_TRANSPORTE)),
            // ya se gasto 1.600.000 del sueldo en otras cosas y 150.000 de comida
            movimientos = listOf(
                Movimiento("m-varios", "2026-10-15", 1600000, tipo = TipoMovimiento.NO_PLANEADO, categoria = "Otros", cuentaId = "c-davivienda"),
                Movimiento("m-comida", "2026-10-15", 150000, claveObligacion = "presupuesto:p-comida:2026-10-15", tipo = TipoMovimiento.PAGO_PROGRAMADO, categoria = "Comida"),
            ),
            deudas = listOf(
                Deuda("d-tc", "Tarjeta X", "Banco X", TipoDeuda.TARJETA, saldoInicial = 1100000, saldoExtracto = 1000000, fechaSaldoExtracto = "2026-10-16",
                    cuotaMensual = 0, diaPago = 28, tasaEa = 0.2859),
                Deuda("d-rot", "Rotativo Y", "Banco Y", TipoDeuda.ROTATIVO, saldoInicial = 7000000, cuotaMensual = 400000, diaPago = 2, tasaEa = 0.30),
            ),
            tarjetas = listOf(
                Tarjeta("t-tc", "d-tc", "Tarjeta X", cupoTotal = 1400000, cupoDisponible = 400000, fechaCupo = "2026-10-16", interes1Cuota = false,
                    cuotasPorDefecto = 1, preferidaPuente = true),
                Tarjeta("t-rot", "d-rot", "Rotativo Y", cupoTotal = 7100000, interes1Cuota = true, cuotasPorDefecto = 36, usoPermitido = false),
                Tarjeta("t-tienda", null, "Tienda Z", cupoTotal = 600000, cuotasPorDefecto = 4, soloTiendas = true),
            ),
            pausas = listOf(
                Pausa("pa-playa", Origen.GASTO, "g-playa", "2026-10-17", "2026-12-31"),
                Pausa("pa-voley", Origen.GASTO, "g-voley", "2026-11-01", "2026-12-31"),
            ),
            pospuestos = listOf(
                Pospuesto("gasto:g-voley:2026-10-15", "2026-10-30"),
                Pospuesto("gasto:g-luz:2026-10-20", "2026-10-30"),
            ),
        )
    }
}
