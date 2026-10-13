package com.jonathan.miscuentas.dominio

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID

object Formato {

    // $1.234.567 sin decimales
    fun pesos(valor: Long): String {
        val negativo = valor < 0
        val digitos = kotlin.math.abs(valor).toString()
        val sb = StringBuilder()
        digitos.reversed().forEachIndexed { i, c ->
            if (i > 0 && i % 3 == 0) sb.append('.')
            sb.append(c)
        }
        return (if (negativo) "-$" else "$") + sb.reverse().toString()
    }

    // quita todo lo que no sea numero
    fun leerPesos(texto: String): Long? {
        val limpio = texto.filter { it.isDigit() }
        if (limpio.isEmpty()) return null
        return limpio.take(15).toLongOrNull()
    }

    private val formatoPantalla = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val meses = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")
    private val mesesLargos = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
    )
    private val diasSemana = listOf("dom", "lun", "mar", "mié", "jue", "vie", "sáb")

    fun fecha(iso: String?): String {
        val f = Fechas.leer(iso) ?: return ""
        return f.format(formatoPantalla)
    }

    fun fechaCorta(f: LocalDate): String = "${f.dayOfMonth} ${meses[f.monthValue - 1]}"

    fun mes(ym: YearMonth): String = "${mesesLargos[ym.monthValue - 1].replaceFirstChar { it.uppercase() }} ${ym.year}"

    fun mesCorto(ym: YearMonth): String = "${meses[ym.monthValue - 1].replaceFirstChar { it.uppercase() }} ${ym.year % 100}"

    fun diaSemana(f: LocalDate): String = diasSemana[Fechas.indiceDiaSemana(f)]

    fun nombreDiaSemana(indice: Int): String = listOf("domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado")[indice]

    fun porcentaje(valor: Double): String = String.format(java.util.Locale.US, "%.1f", valor * 100).replace('.', ',') + " %"
}

object Fechas {
    fun hoy(): LocalDate = LocalDate.now()

    fun iso(f: LocalDate): String = f.toString()

    fun leer(texto: String?): LocalDate? {
        if (texto.isNullOrBlank()) return null
        return try {
            LocalDate.parse(texto.trim())
        } catch (e: Exception) {
            null
        }
    }

    // acepta 13/10/2026 o 2026-10-13
    fun leerPantalla(texto: String): LocalDate? {
        val t = texto.trim()
        leer(t)?.let { return it }
        val partes = t.split('/', '-', '.')
        if (partes.size != 3) return null
        return try {
            LocalDate.of(partes[2].toInt(), partes[1].toInt(), partes[0].toInt())
        } catch (e: Exception) {
            null
        }
    }

    // si el dia no existe en el mes se usa el ultimo dia
    fun diaEnMes(ym: YearMonth, dia: Int): LocalDate = ym.atDay(dia.coerceIn(1, ym.lengthOfMonth()))

    // 0 = domingo ... 6 = sabado
    fun indiceDiaSemana(f: LocalDate): Int = f.dayOfWeek.value % 7

    fun diaSemanaDesdeIndice(i: Int): DayOfWeek = if (i == 0) DayOfWeek.SUNDAY else DayOfWeek.of(i)

    fun mesesEntre(desde: YearMonth, hasta: YearMonth): Int =
        (hasta.year - desde.year) * 12 + (hasta.monthValue - desde.monthValue)

    fun ahora(): String = java.time.LocalDateTime.now().withNano(0).toString()
}

fun nuevoId(): String = UUID.randomUUID().toString()
