package com.jonathan.miscuentas.dominio

// lo que dejo un cuadre: cuando fue y cuanto faltaba (negativo) o sobraba (positivo)
data class Cuadre(val fecha: String, val desfase: Long, val grupo: String)

// saldo de cada cuenta segun lo registrado
object Saldos {

    fun activas(datos: Datos): List<Cuenta> = datos.cuentas.filter { it.activa }.sortedBy { it.orden }

    // la primera cuenta recibe lo que no tiene cuenta y la plata inicial
    fun principal(datos: Datos): String? = activas(datos).firstOrNull()?.id ?: datos.cuentas.minByOrNull { it.orden }?.id

    private fun cuentaDe(datos: Datos, id: String?): String? {
        if (id != null && datos.cuentas.any { it.id == id }) return id
        return principal(datos)
    }

    // la cuenta que se propone al registrar: la ultima que se uso
    fun porDefecto(datos: Datos): String? {
        val usadas = datos.movimientos.filter { it.cuentaId != null }.map { (it.createdAt.ifBlank { it.fecha }) to it.cuentaId } +
            datos.ingresos.filter { it.cuentaId != null }.map { (it.createdAt.ifBlank { it.fecha }) to it.cuentaId }
        val ultima = usadas.maxByOrNull { it.first }?.second
        return if (ultima != null && activas(datos).any { it.id == ultima }) ultima else principal(datos)
    }

    fun saldos(datos: Datos): Map<String, Long> {
        val p = principal(datos) ?: return emptyMap()
        val s = datos.cuentas.associate { it.id to 0L }.toMutableMap()
        s[p] = (s[p] ?: 0) + datos.config.plataInicial
        datos.ingresos.forEach { i -> cuentaDe(datos, i.cuentaId)?.let { s[it] = (s[it] ?: 0) + i.monto } }
        datos.movimientos.forEach { m -> cuentaDe(datos, m.cuentaId)?.let { s[it] = (s[it] ?: 0) - m.monto } }
        datos.cuentaMovs.filter { it.tipo != TipoCuentaMov.CUADRE }.forEach { c ->
            cuentaDe(datos, c.cuentaId)?.let { s[it] = (s[it] ?: 0) + c.monto }
        }
        return s
    }

    // lo que hay que sumar a cada cuenta para que quede igual a lo que se tiene de verdad
    fun ajustes(datos: Datos, reales: Map<String, Long>): Map<String, Long> {
        val actuales = saldos(datos)
        return reales.mapValues { (id, real) -> real - (actuales[id] ?: 0) }
    }

    // filas a guardar al cuadrar: un ajuste por cuenta que cambia y una marca con el desfase total
    fun cuadrar(datos: Datos, reales: Map<String, Long>, fecha: String, grupo: String = nuevoId(), ahora: String = Fechas.ahora()): List<CuentaMov> {
        val difs = ajustes(datos, reales)
        val filas = difs.filter { it.value != 0L }.map { (id, dif) ->
            CuentaMov(nuevoId(), fecha, id, dif, TipoCuentaMov.AJUSTE, grupo, "cuadre", ahora, ahora)
        }
        val marca = CuentaMov(nuevoId(), fecha, "", difs.values.sum(), TipoCuentaMov.CUADRE, grupo, "", ahora, ahora)
        return filas + marca
    }

    // un envio o retiro sale de una cuenta y entra a otra con el mismo grupo
    fun trasladar(desde: String, hacia: String, monto: Long, fecha: String, nota: String = "", ahora: String = Fechas.ahora()): List<CuentaMov> {
        val grupo = nuevoId()
        return listOf(
            CuentaMov(nuevoId(), fecha, desde, -monto, TipoCuentaMov.TRASLADO, grupo, nota, ahora, ahora),
            CuentaMov(nuevoId(), fecha, hacia, monto, TipoCuentaMov.TRASLADO, grupo, nota, ahora, ahora),
        )
    }

    fun ultimoCuadre(datos: Datos): Cuadre? = datos.cuentaMovs
        .filter { it.tipo == TipoCuentaMov.CUADRE }
        .maxWithOrNull(compareBy({ it.fecha }, { it.createdAt }))
        ?.let { Cuadre(it.fecha, it.monto, it.grupo ?: it.id) }

    fun cuentasPorDefecto(ahora: String = Fechas.ahora()): List<Cuenta> = listOf(
        Cuenta("c-davivienda", "Davivienda", "🏦", 0, true, ahora, ahora),
        Cuenta("c-nequi", "Nequi", "📱", 1, true, ahora, ahora),
        Cuenta("c-efectivo", "Efectivo", "💵", 2, true, ahora, ahora),
    )
}
