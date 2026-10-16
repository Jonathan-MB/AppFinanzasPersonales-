package com.jonathan.miscuentas.dominio

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class ResultadoImport(val datos: Datos, val errores: List<String>, val seccionesLeidas: Set<String>)

object Txt {

    private val cabeceras = mapOf(
        "DEUDAS" to "id|nombre|entidad|tipo|saldo_inicial|saldo_extracto|fecha_saldo_extracto|cuota_mensual|dia_pago|tasa_ea|cuotas_totales|cuotas_pagadas|genera_intereses|activa|saldo_estimado|pagado_desde_inicio|notas|fecha_fin|fijada_arriba",
        "DEUDA_SALDOS" to "deuda_id|fecha|saldo|id",
        "GASTOS_FIJOS" to "id|nombre|valor|frecuencia|dia_pago|dia_semana|quincena_asignada|activo|notas|dia_confirmado",
        "PRESUPUESTOS" to "id|nombre|monto|periodicidad|fecha|fecha_desde|fecha_hasta|categoria|activo|notas",
        "INGRESOS_PROGRAMADOS" to "id|nombre|monto|frecuencia|dia|fecha|tipo|es_estimado|activo|notas|es_incierto",
        "EVENTOS" to "id|nombre|monto|fecha|categoria|deuda_id|notas",
        "CALENDARIO" to "clave|fecha|concepto|categoria|presupuestado|pagado|falta|estado|fecha_original",
        "MOVIMIENTOS" to "id|fecha|monto|tipo|clave_obligacion|categoria|deuda_id|nota|liquida|cuenta_id|tarjeta_id|cuotas|fecha_pago_puente",
        "INGRESOS" to "id|fecha|monto|tipo|clave_ingreso_programado|nota|cuenta_id",
        "CATEGORIAS" to "nombre|es_sistema|emoji|tipo|orden|presupuesto_id|id",
        "CUENTAS" to "id|nombre|emoji|orden|activa|tipo",
        "CUENTA_MOVS" to "id|fecha|cuenta_id|monto|tipo|grupo|nota",
        "TARJETAS" to "id|deuda_id|nombre|cupo_total|cupo_disponible|fecha_cupo|interes_1_cuota|cuotas_por_defecto|uso_permitido|solo_tiendas|preferida_puente|dia_corte|notas",
        "PAUSAS" to "id|tipo_origen|origen_id|desde|hasta|nota",
        "POSPUESTOS" to "clave|fecha_nueva|nota",
    )

    private fun limpio(v: Any?): String = when (v) {
        null -> ""
        is Boolean -> if (v) "si" else "no"
        else -> v.toString().replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ').replace('|', '/')
    }

    private fun fila(vararg campos: Any?) = campos.joinToString("|") { limpio(it) }

    fun nombreArchivo(momento: LocalDateTime): String =
        "finanzas_" + momento.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")) + ".txt"

    fun exportar(datos: Datos, hoy: LocalDate, momento: LocalDateTime): String {
        val sb = StringBuilder()
        fun linea(s: String) = sb.append(s).append('\n')
        val c = datos.config
        linea("#FINANZAS_EXPORT v1")
        linea("#generado=" + momento.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
        linea("#moneda=COP")
        linea("#separador=|")
        linea("#nota=montos en pesos enteros, fechas AAAA-MM-DD")
        linea("")

        // resumen
        val mes = YearMonth.from(hoy)
        val obligMes = Obligaciones.generar(datos, mes.atDay(1), mes.atEndOfMonth())
        val obligHasta = Obligaciones.generar(datos, LocalDate.of(2000, 1, 1), hoy.plusDays(7))
        val vencidos = obligHasta.filter { it.fecha < hoy && it.falta > 0 }
        val proximos = obligHasta.filter { it.fecha >= hoy && it.fecha <= hoy.plusDays(7) && it.falta > 0 }
        fun enMes(f: String) = Fechas.leer(f)?.let { YearMonth.from(it) == mes } ?: false
        linea("[RESUMEN]")
        linea(fila("hoy", Fechas.iso(hoy)))
        linea(fila("deberia_tener", Calculos.deberiaTener(datos)))
        val saldos = Saldos.saldos(datos)
        datos.cuentas.sortedBy { it.orden }.forEach { k -> linea(fila("saldo_" + k.nombre.lowercase(), saldos[k.id] ?: 0)) }
        Saldos.ultimoCuadre(datos)?.let { linea(fila("ultimo_cuadre", it.fecha, it.desfase)) }
        linea(fila("ingresos_recibidos_mes", datos.ingresos.filter { enMes(it.fecha) }.sumOf { it.monto }))
        linea(fila("pagado_mes", obligMes.sumOf { it.pagado }))
        linea(fila("no_planeado_mes", datos.movimientos.filter { enMes(it.fecha) && it.tipo == TipoMovimiento.NO_PLANEADO }.sumOf { it.monto }))
        linea(fila("moto_mes", datos.ingresos.filter { enMes(it.fecha) && it.tipo == TipoIngreso.MOTO }.sumOf { it.monto }))
        linea(fila("vencidos_cantidad", vencidos.size))
        linea(fila("vencidos_monto", vencidos.sumOf { it.falta }))
        linea(fila("proximos_7_dias", proximos.sumOf { it.falta }))
        linea(fila("deuda_total_estimada", Calculos.deudaTotal(datos, hoy)))
        linea(fila("cuotas_mensuales", Calculos.cuotasMensuales(datos)))
        linea(fila("endeudamiento_pct", Calculos.endeudamiento(datos)?.let { String.format(java.util.Locale.US, "%.1f", it * 100) }))
        val proy = Proyecciones.calcular(datos, hoy)
        linea(fila("proximo_sueldo", Fechas.iso(proy.proximoSueldo)))
        linea(fila("faltante_hasta_sueldo", proy.faltanteHastaSueldo))
        linea(fila("libre_hasta_sueldo", proy.libreHastaSueldo))
        linea(fila("puedes_gastar_dia", proy.puedesGastarPorDia))
        linea(fila("minimo_proyectado", proy.minimo, Fechas.iso(proy.fechaMinimo)))
        linea(fila("moto_para_cubrir", proy.faltanteHastaSueldo))
        datos.tarjetas.sortedBy { it.id }.forEach { t -> linea(fila("cupo_disponible_" + t.id.removePrefix("t-"), Tarjetas.cupoDisponible(datos, t, hoy))) }
        linea(fila("colchon", proy.colchon))
        linea(fila("bolsillo", Saldos.deTipo(datos, TipoCuenta.BOLSILLO)))
        linea("")

        linea("[CONFIG]")
        linea("campo|valor")
        linea(fila("fecha_inicio", c.fechaInicio))
        linea(fila("plata_inicial", c.plataInicial))
        linea(fila("dias_aviso", c.diasAviso))
        linea(fila("hora_aviso", c.horaAviso))
        linea(fila("semanas_por_mes", c.semanasPorMes))
        linea(fila("recordatorio_diario", c.recordatorioDiario))
        linea(fila("saldo_real", c.saldoReal))
        linea(fila("fecha_saldo_real", c.fechaSaldoReal))
        linea(fila("modo_plan", c.modoPlan))
        linea(fila("nota_plan", c.notaPlan))
        linea(fila("ultimo_respaldo", c.ultimoRespaldo))
        linea(fila("meta_colchon", c.metaColchon))
        linea(fila("colchon_cuenta", c.colchonCuenta))
        linea(fila("bolsillo_cuenta", c.bolsilloCuenta))
        linea(fila("bolsillo_monto", c.bolsilloMonto))
        linea(fila("bolsillo_dia", c.bolsilloDia))
        linea("")

        linea("[DEUDAS]"); linea(cabeceras.getValue("DEUDAS"))
        datos.deudas.forEach { d ->
            val est = Calculos.estadoDeuda(d, datos, hoy)
            linea(fila(d.id, d.nombre, d.entidad, d.tipo, d.saldoInicial, d.saldoExtracto, d.fechaSaldoExtracto, d.cuotaMensual,
                d.diaPago, d.tasaEa, d.cuotasTotales, d.cuotasPagadas, d.generaIntereses, d.activa, est.saldoEstimado,
                est.pagadoDesdeInicio, d.notas, d.fechaFin, d.fijadaArriba))
        }
        linea("")
        linea("[DEUDA_SALDOS]"); linea(cabeceras.getValue("DEUDA_SALDOS"))
        datos.deudaSaldos.forEach { linea(fila(it.deudaId, it.fecha, it.saldo, it.id)) }
        linea("")
        linea("[GASTOS_FIJOS]"); linea(cabeceras.getValue("GASTOS_FIJOS"))
        datos.gastosFijos.forEach { g ->
            linea(fila(g.id, g.nombre, g.valor, g.frecuencia, g.diaPago, g.diaSemana, g.quincenaAsignada, g.activo, g.notas, g.diaConfirmado))
        }
        linea("")
        linea("[PRESUPUESTOS]"); linea(cabeceras.getValue("PRESUPUESTOS"))
        datos.presupuestos.forEach { p ->
            linea(fila(p.id, p.nombre, p.monto, p.periodicidad, p.fecha, p.fechaDesde, p.fechaHasta, p.categoria, p.activo, p.notas))
        }
        linea("")
        linea("[INGRESOS_PROGRAMADOS]"); linea(cabeceras.getValue("INGRESOS_PROGRAMADOS"))
        datos.ingresosProgramados.forEach { i ->
            linea(fila(i.id, i.nombre, i.monto, i.frecuencia, i.dia, i.fecha, i.tipo, i.esEstimado, i.activo, i.notas, i.esIncierto))
        }
        linea("")
        linea("[EVENTOS]"); linea(cabeceras.getValue("EVENTOS"))
        datos.eventos.forEach { e -> linea(fila(e.id, e.nombre, e.monto, e.fecha, e.categoria, e.deudaId, e.notas)) }
        linea("")
        linea("[CALENDARIO]"); linea(cabeceras.getValue("CALENDARIO"))
        val inicio = Fechas.leer(c.fechaInicio) ?: hoy
        val finCal = YearMonth.from(hoy).plusMonths(3).atEndOfMonth()
        Obligaciones.generar(datos, inicio, finCal).forEach { o ->
            linea(fila(o.clave, Fechas.iso(o.fecha), o.concepto, o.categoria, o.presupuestado, o.pagado, o.falta, o.estado(hoy), o.fechaOriginal?.let { Fechas.iso(it) }))
        }
        linea("")
        linea("[MOVIMIENTOS]"); linea(cabeceras.getValue("MOVIMIENTOS"))
        datos.movimientos.sortedWith(compareBy({ it.fecha }, { it.id })).forEach { m ->
            linea(fila(m.id, m.fecha, m.monto, m.tipo, m.claveObligacion, m.categoria, m.deudaId, m.nota, m.liquida, m.cuentaId, m.tarjetaId, m.cuotas, m.fechaPagoPuente))
        }
        linea("")
        linea("[INGRESOS]"); linea(cabeceras.getValue("INGRESOS"))
        datos.ingresos.sortedWith(compareBy({ it.fecha }, { it.id })).forEach { i ->
            linea(fila(i.id, i.fecha, i.monto, i.tipo, i.claveIngresoProgramado, i.nota, i.cuentaId))
        }
        linea("")
        linea("[CATEGORIAS]"); linea(cabeceras.getValue("CATEGORIAS"))
        datos.categorias.sortedBy { it.orden }.forEach { k ->
            linea(fila(k.nombre, k.esSistema, k.emoji, k.tipo, k.orden, k.presupuestoId, k.id))
        }
        linea("")
        linea("[CUENTAS]"); linea(cabeceras.getValue("CUENTAS"))
        datos.cuentas.sortedBy { it.orden }.forEach { k -> linea(fila(k.id, k.nombre, k.emoji, k.orden, k.activa, k.tipo)) }
        linea("")
        linea("[CUENTA_MOVS]"); linea(cabeceras.getValue("CUENTA_MOVS"))
        datos.cuentaMovs.sortedWith(compareBy({ it.fecha }, { it.id })).forEach { m ->
            linea(fila(m.id, m.fecha, m.cuentaId, m.monto, m.tipo, m.grupo, m.nota))
        }
        linea("")
        linea("[TARJETAS]"); linea(cabeceras.getValue("TARJETAS"))
        datos.tarjetas.sortedBy { it.id }.forEach { t ->
            linea(fila(t.id, t.deudaId, t.nombre, t.cupoTotal, t.cupoDisponible, t.fechaCupo, t.interes1Cuota, t.cuotasPorDefecto,
                t.usoPermitido, t.soloTiendas, t.preferidaPuente, t.diaCorte, t.notas))
        }
        linea("")
        linea("[PAUSAS]"); linea(cabeceras.getValue("PAUSAS"))
        datos.pausas.sortedBy { it.id }.forEach { p -> linea(fila(p.id, p.tipoOrigen, p.origenId, p.desde, p.hasta, p.nota)) }
        linea("")
        linea("[POSPUESTOS]"); linea(cabeceras.getValue("POSPUESTOS"))
        datos.pospuestos.sortedBy { it.clave }.forEach { p -> linea(fila(p.clave, p.fechaNueva, p.nota)) }
        linea("")
        linea("#FIN")
        return sb.toString()
    }

    // toma del archivo solo lo que se planea (deudas, extractos, gastos, presupuestos, ingresos, eventos,
    // tarjetas, pausas, pospuestos, cuentas y la configuracion del plan) y deja lo registrado como esta
    fun actualizarBase(texto: String, actuales: Datos, ahora: String = Fechas.ahora()): ResultadoImport {
        val res = importar(texto, actuales, ahora)
        val d = res.datos
        val c = d.config
        // las cuentas del archivo reemplazan por id; las que solo existen en la app se quedan
        val cuentas = d.cuentas + actuales.cuentas.filter { a -> d.cuentas.none { it.id == a.id } }
        return res.copy(datos = actuales.copy(
            config = actuales.config.copy(
                diasAviso = c.diasAviso, horaAviso = c.horaAviso, semanasPorMes = c.semanasPorMes,
                recordatorioDiario = c.recordatorioDiario, modoPlan = c.modoPlan, notaPlan = c.notaPlan,
                metaColchon = c.metaColchon, colchonCuenta = c.colchonCuenta, bolsilloCuenta = c.bolsilloCuenta,
                bolsilloMonto = c.bolsilloMonto, bolsilloDia = c.bolsilloDia, updatedAt = ahora,
            ),
            deudas = d.deudas, deudaSaldos = d.deudaSaldos, gastosFijos = d.gastosFijos, presupuestos = d.presupuestos,
            ingresosProgramados = d.ingresosProgramados, eventos = d.eventos,
            tarjetas = d.tarjetas, pausas = d.pausas, pospuestos = d.pospuestos, cuentas = cuentas,
        ))
    }

    // lee el txt; las secciones que no vienen se dejan como estan
    fun importar(texto: String, actuales: Datos, ahora: String = Fechas.ahora()): ResultadoImport {
        val secciones = mutableMapOf<String, MutableList<Pair<Int, List<String>>>>()
        var actual: String? = null
        var saltarCabecera = false
        val lineas = texto.replace("\r\n", "\n").split('\n')
        if (lineas.none { it.trim() == "#FINANZAS_EXPORT v1" }) {
            return ResultadoImport(actuales, listOf("El archivo no tiene el encabezado #FINANZAS_EXPORT v1"), emptySet())
        }
        lineas.forEachIndexed { i, crudo ->
            val l = crudo.trimEnd()
            when {
                l.isBlank() || l.startsWith("#") -> {}
                l.startsWith("[") && l.contains("]") -> {
                    actual = l.substring(1, l.indexOf(']')).trim().uppercase()
                    secciones.getOrPut(actual!!) { mutableListOf() }
                    saltarCabecera = actual != "RESUMEN"
                }
                actual != null -> {
                    if (saltarCabecera) saltarCabecera = false
                    else secciones.getValue(actual!!).add((i + 1) to l.split('|'))
                }
            }
        }

        val errores = mutableListOf<String>()
        fun <T> leer(nombre: String, parse: (List<String>) -> T): List<T>? {
            val filas = secciones[nombre] ?: return null
            return filas.mapNotNull { (n, campos) ->
                try {
                    parse(campos)
                } catch (e: Exception) {
                    errores += "[$nombre] línea $n: ${e.message ?: "formato inválido"}"
                    null
                }
            }
        }

        fun List<String>.s(i: Int) = getOrElse(i) { "" }
        fun List<String>.sn(i: Int) = s(i).ifBlank { null }
        fun List<String>.l(i: Int) = s(i).trim().toLong()
        fun List<String>.ln(i: Int) = sn(i)?.trim()?.toLong()
        fun List<String>.i(i: Int) = sn(i)?.trim()?.toInt()
        fun List<String>.d(i: Int) = sn(i)?.trim()?.replace(',', '.')?.toDouble()
        fun List<String>.b(i: Int, def: Boolean) = when (s(i).trim().lowercase()) {
            "si", "sí", "true", "1" -> true
            "no", "false", "0" -> false
            else -> def
        }
        fun requerido(v: String, campo: String): String { require(v.isNotBlank()) { "falta $campo" }; return v }
        fun fechaOk(v: String?): String? { if (v != null) requireNotNull(Fechas.leer(v)) { "fecha inválida $v" }; return v }

        var config = actuales.config
        secciones["CONFIG"]?.let { filas ->
            val mapa = filas.associate { (_, c) -> c.s(0).trim() to c.s(1) }
            try {
                config = config.copy(
                    fechaInicio = fechaOk(mapa["fecha_inicio"]?.ifBlank { null }) ?: config.fechaInicio,
                    plataInicial = mapa["plata_inicial"]?.trim()?.toLongOrNull() ?: config.plataInicial,
                    diasAviso = mapa["dias_aviso"]?.trim()?.toIntOrNull() ?: config.diasAviso,
                    horaAviso = mapa["hora_aviso"]?.ifBlank { null } ?: config.horaAviso,
                    semanasPorMes = mapa["semanas_por_mes"]?.trim()?.toDoubleOrNull() ?: config.semanasPorMes,
                    recordatorioDiario = mapa["recordatorio_diario"]?.let { it.trim() == "si" } ?: config.recordatorioDiario,
                    saldoReal = if (mapa.containsKey("saldo_real")) mapa["saldo_real"]?.trim()?.toLongOrNull() else config.saldoReal,
                    fechaSaldoReal = if (mapa.containsKey("fecha_saldo_real")) mapa["fecha_saldo_real"]?.ifBlank { null } else config.fechaSaldoReal,
                    modoPlan = mapa["modo_plan"]?.ifBlank { null } ?: config.modoPlan,
                    notaPlan = mapa["nota_plan"] ?: config.notaPlan,
                    ultimoRespaldo = if (mapa.containsKey("ultimo_respaldo")) mapa["ultimo_respaldo"]?.ifBlank { null } else config.ultimoRespaldo,
                    metaColchon = mapa["meta_colchon"]?.trim()?.toLongOrNull() ?: config.metaColchon,
                    colchonCuenta = if (mapa.containsKey("colchon_cuenta")) mapa["colchon_cuenta"]?.ifBlank { null } else config.colchonCuenta,
                    bolsilloCuenta = if (mapa.containsKey("bolsillo_cuenta")) mapa["bolsillo_cuenta"]?.ifBlank { null } else config.bolsilloCuenta,
                    bolsilloMonto = mapa["bolsillo_monto"]?.trim()?.toLongOrNull() ?: config.bolsilloMonto,
                    bolsilloDia = if (mapa.containsKey("bolsillo_dia")) mapa["bolsillo_dia"]?.trim()?.toIntOrNull() else config.bolsilloDia,
                    updatedAt = ahora,
                )
            } catch (e: Exception) {
                errores += "[CONFIG] ${e.message}"
            }
        }

        val deudas = leer("DEUDAS") { c ->
            Deuda(
                id = requerido(c.s(0), "id"), nombre = requerido(c.s(1), "nombre"), entidad = c.s(2),
                tipo = c.s(3).ifBlank { TipoDeuda.OTRO }, saldoInicial = c.ln(4) ?: 0, saldoExtracto = c.ln(5),
                fechaSaldoExtracto = fechaOk(c.sn(6)), cuotaMensual = c.ln(7) ?: 0, diaPago = c.i(8), tasaEa = c.d(9),
                cuotasTotales = c.i(10), cuotasPagadas = c.i(11), generaIntereses = c.b(12, true), activa = c.b(13, true),
                notas = c.s(16), fechaFin = fechaOk(c.sn(17)), fijadaArriba = c.b(18, false), createdAt = ahora, updatedAt = ahora,
            )
        }
        val saldos = leer("DEUDA_SALDOS") { c ->
            DeudaSaldo(c.sn(3) ?: nuevoId(), requerido(c.s(0), "deuda_id"), fechaOk(requerido(c.s(1), "fecha"))!!, c.l(2), ahora, ahora)
        }
        val gastos = leer("GASTOS_FIJOS") { c ->
            GastoFijo(requerido(c.s(0), "id"), requerido(c.s(1), "nombre"), c.l(2), c.s(3).ifBlank { Frecuencia.MENSUAL }, c.i(4), c.i(5),
                c.s(6).ifBlank { Quincena.Q30 }, c.b(7, true), c.s(8), c.b(9, true), ahora, ahora)
        }
        val presupuestos = leer("PRESUPUESTOS") { c ->
            Presupuesto(requerido(c.s(0), "id"), requerido(c.s(1), "nombre"), c.l(2), c.s(3).ifBlank { Periodicidad.MENSUAL },
                fechaOk(c.sn(4)), fechaOk(c.sn(5)), fechaOk(c.sn(6)), c.s(7), c.b(8, true), c.s(9), ahora, ahora)
        }
        val programados = leer("INGRESOS_PROGRAMADOS") { c ->
            IngresoProgramado(requerido(c.s(0), "id"), requerido(c.s(1), "nombre"), c.l(2), c.s(3).ifBlank { FrecuenciaIngreso.MENSUAL },
                c.i(4), fechaOk(c.sn(5)), c.s(6).ifBlank { TipoIngreso.OTRO }, c.b(7, false), c.b(8, true), c.s(9), ahora, ahora,
                // la moto siempre es incierta
                esIncierto = c.b(10, false) || c.s(6).trim() == TipoIngreso.MOTO)
        }
        val eventos = leer("EVENTOS") { c ->
            Evento(requerido(c.s(0), "id"), requerido(c.s(1), "nombre"), c.l(2), fechaOk(requerido(c.s(3), "fecha"))!!, c.s(4), c.sn(5), c.s(6), ahora, ahora)
        }
        val movimientos = leer("MOVIMIENTOS") { c ->
            Movimiento(requerido(c.s(0), "id"), fechaOk(requerido(c.s(1), "fecha"))!!, c.l(2), c.s(7), c.sn(4),
                c.s(3).ifBlank { TipoMovimiento.NO_PLANEADO }, c.s(5), c.sn(6), ahora, ahora, liquida = c.b(8, false), cuentaId = c.sn(9),
                tarjetaId = c.sn(10), cuotas = c.i(11), fechaPagoPuente = fechaOk(c.sn(12)))
        }
        val ingresos = leer("INGRESOS") { c ->
            Ingreso(requerido(c.s(0), "id"), fechaOk(requerido(c.s(1), "fecha"))!!, c.l(2), c.s(3).ifBlank { TipoIngreso.OTRO }, c.s(5), c.sn(4), ahora, ahora, cuentaId = c.sn(6))
        }
        val cuentas = leer("CUENTAS") { c ->
            Cuenta(requerido(c.s(0), "id"), requerido(c.s(1), "nombre"), c.s(2).ifBlank { "🏦" }, c.i(3) ?: 0, c.b(4, true), ahora, ahora,
                c.s(5).trim().takeIf { it in TipoCuenta.todos } ?: TipoCuenta.NORMAL)
        }
        val cuentaMovs = leer("CUENTA_MOVS") { c ->
            // la marca del cuadre no tiene cuenta
            CuentaMov(requerido(c.s(0), "id"), fechaOk(requerido(c.s(1), "fecha"))!!, c.s(2), c.l(3), requerido(c.s(4), "tipo"), c.sn(5), c.s(6), ahora, ahora)
        }
        val tarjetas = leer("TARJETAS") { c ->
            Tarjeta(requerido(c.s(0), "id"), c.sn(1), requerido(c.s(2), "nombre"), c.ln(3), c.ln(4), fechaOk(c.sn(5)), c.b(6, true), c.i(7),
                c.b(8, true), c.b(9, false), c.b(10, false), c.i(11), c.s(12), ahora, ahora)
        }
        val pausas = leer("PAUSAS") { c ->
            Pausa(requerido(c.s(0), "id"), requerido(c.s(1), "tipo_origen"), requerido(c.s(2), "origen_id"),
                fechaOk(requerido(c.s(3), "desde"))!!, fechaOk(requerido(c.s(4), "hasta"))!!, c.s(5), ahora, ahora)
        }
        val pospuestos = leer("POSPUESTOS") { c ->
            Pospuesto(requerido(c.s(0), "clave"), fechaOk(requerido(c.s(1), "fecha_nueva"))!!, c.s(2), ahora, ahora)
        }
        val categorias = leer("CATEGORIAS") { c ->
            Categoria(c.sn(6) ?: nuevoId(), requerido(c.s(0), "nombre"), c.b(1, false), c.s(2).ifBlank { "🏷" },
                c.s(3).ifBlank { TipoEtiqueta.GASTO }, c.i(4) ?: 0, c.sn(5), ahora, ahora)
        }

        val datos = Datos(
            config = config,
            deudas = deudas ?: actuales.deudas,
            deudaSaldos = saldos ?: actuales.deudaSaldos,
            gastosFijos = gastos ?: actuales.gastosFijos,
            presupuestos = presupuestos ?: actuales.presupuestos,
            ingresosProgramados = programados ?: actuales.ingresosProgramados,
            eventos = eventos ?: actuales.eventos,
            movimientos = movimientos ?: actuales.movimientos,
            ingresos = ingresos ?: actuales.ingresos,
            categorias = categorias ?: actuales.categorias,
            cuentas = cuentas ?: actuales.cuentas,
            cuentaMovs = cuentaMovs ?: actuales.cuentaMovs,
            tarjetas = tarjetas ?: actuales.tarjetas,
            pausas = pausas ?: actuales.pausas,
            pospuestos = pospuestos ?: actuales.pospuestos,
        )
        return ResultadoImport(datos, errores, secciones.keys)
    }
}
