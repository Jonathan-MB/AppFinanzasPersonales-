package com.jonathan.miscuentas

import com.jonathan.miscuentas.dominio.*

// datos inventados solo para las pruebas
object DatosPrueba {
    fun datos(): Datos = Semilla.datos("2026-10-13T08:00", "2026-10-13").copy(
        ingresosProgramados = listOf(
            IngresoProgramado("ip-15", "Salario 15", 1500000, FrecuenciaIngreso.QUINCENAL_DIA, dia = 15, tipo = TipoIngreso.SALARIO),
            IngresoProgramado("ip-30", "Salario 30", 1500000, FrecuenciaIngreso.QUINCENAL_DIA, dia = 30, tipo = TipoIngreso.SALARIO),
            IngresoProgramado("ip-bono", "Bono", 900000, FrecuenciaIngreso.UNICO, fecha = "2026-12-20", tipo = TipoIngreso.PRIMA, esEstimado = true),
        ),
        deudas = listOf(
            Deuda("d-a", "Tarjeta A", "Banco A", TipoDeuda.TARJETA, saldoInicial = 2000000, cuotaMensual = 100000, diaPago = 15, cuotasTotales = 24),
            Deuda("d-b", "Crédito B", "Banco B", TipoDeuda.LIBRE_INVERSION, saldoInicial = 5000000, cuotaMensual = 300000, diaPago = 16, cuotasTotales = 36, cuotasPagadas = 6),
            Deuda("d-c", "Rotativo C", "Banco C", TipoDeuda.ROTATIVO, saldoInicial = 3000000, cuotaMensual = 200000, diaPago = 2),
            Deuda("d-d", "Tarjeta D", "Banco D", TipoDeuda.TARJETA, saldoInicial = 500000, cuotaMensual = 50000, diaPago = 21),
            Deuda("d-e", "Pago corto E", "Tienda E", TipoDeuda.BNPL, saldoInicial = 400000, cuotaMensual = 100000, diaPago = 19, fechaFin = "2027-01-19"),
            Deuda("d-f", "Amigo F", "", TipoDeuda.PERSONA, saldoInicial = 800000, generaIntereses = false),
            Deuda("d-g", "Préstamo G", "", TipoDeuda.PERSONA, saldoInicial = 0, saldoExtracto = 1000000, fechaSaldoExtracto = "2026-11-25", generaIntereses = false),
        ),
        gastosFijos = listOf(
            GastoFijo("g-arriendo", "Arriendo", 600000, Frecuencia.MENSUAL, diaPago = 1, quincenaAsignada = Quincena.Q30),
            GastoFijo("g-semanal", "Servicio semanal", 20000, Frecuencia.SEMANAL, diaSemana = 6, quincenaAsignada = Quincena.DIVIDIDO),
        ),
        presupuestos = listOf(
            Presupuesto("p-comida", "Comida", 200000, Periodicidad.QUINCENAL, categoria = Categorias.COMIDA_TRANSPORTE),
        ),
        eventos = listOf(
            Evento("e-curso", "Curso", 700000, "2026-11-28", Categorias.ESTUDIO),
        ),
    )
}
