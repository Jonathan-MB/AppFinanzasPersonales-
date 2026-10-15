package com.jonathan.miscuentas.dominio

import androidx.room.Entity
import androidx.room.PrimaryKey

// tablas de la app, montos en pesos enteros y fechas AAAA-MM-DD

@Entity(tableName = "config")
data class Config(
    @PrimaryKey val id: String = "unica",
    val fechaInicio: String,
    val plataInicial: Long = 0,
    val diasAviso: Int = 2,
    val horaAviso: String = "07:00",
    val semanasPorMes: Double = 52.0 / 12.0,
    val recordatorioDiario: Boolean = false,
    val saldoReal: Long? = null,
    val fechaSaldoReal: String? = null,
    val modoPlan: String = ModoPlan.BOLA_NIEVE,
    val notaPlan: String = "",
    val ultimoRespaldo: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Entity(tableName = "deudas")
data class Deuda(
    @PrimaryKey val id: String,
    val nombre: String,
    val entidad: String = "",
    val tipo: String = TipoDeuda.OTRO,
    val saldoInicial: Long = 0,
    val saldoExtracto: Long? = null,
    val fechaSaldoExtracto: String? = null,
    val cuotaMensual: Long = 0,
    val diaPago: Int? = null,
    val tasaEa: Double? = null,
    val cuotasTotales: Int? = null,
    val cuotasPagadas: Int? = null,
    val generaIntereses: Boolean = true,
    val fechaFin: String? = null,
    val activa: Boolean = true,
    val notas: String = "",
    val fijadaArriba: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Entity(tableName = "deuda_saldos")
data class DeudaSaldo(
    @PrimaryKey val id: String,
    val deudaId: String,
    val fecha: String,
    val saldo: Long,
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Entity(tableName = "gastos_fijos")
data class GastoFijo(
    @PrimaryKey val id: String,
    val nombre: String,
    val valor: Long,
    val frecuencia: String = Frecuencia.MENSUAL,
    val diaPago: Int? = null,
    val diaSemana: Int? = null,
    val quincenaAsignada: String = Quincena.Q30,
    val activo: Boolean = true,
    val notas: String = "",
    val diaConfirmado: Boolean = true,
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Entity(tableName = "presupuestos")
data class Presupuesto(
    @PrimaryKey val id: String,
    val nombre: String,
    val monto: Long,
    val periodicidad: String = Periodicidad.QUINCENAL,
    val fecha: String? = null,
    val fechaDesde: String? = null,
    val fechaHasta: String? = null,
    val categoria: String = "",
    val activo: Boolean = true,
    val notas: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Entity(tableName = "ingresos_programados")
data class IngresoProgramado(
    @PrimaryKey val id: String,
    val nombre: String,
    val monto: Long,
    val frecuencia: String = FrecuenciaIngreso.QUINCENAL_DIA,
    val dia: Int? = null,
    val fecha: String? = null,
    val tipo: String = TipoIngreso.SALARIO,
    val esEstimado: Boolean = false,
    val activo: Boolean = true,
    val notas: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Entity(tableName = "eventos")
data class Evento(
    @PrimaryKey val id: String,
    val nombre: String,
    val monto: Long,
    val fecha: String,
    val categoria: String = "",
    val deudaId: String? = null,
    val notas: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Entity(tableName = "movimientos")
data class Movimiento(
    @PrimaryKey val id: String,
    val fecha: String,
    val monto: Long,
    val nota: String = "",
    val claveObligacion: String? = null,
    val tipo: String = TipoMovimiento.NO_PLANEADO,
    val categoria: String = "",
    val deudaId: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    // pago completo aunque el valor sea distinto al presupuestado
    val liquida: Boolean = false,
    // de que cuenta salio; vacio es la cuenta principal
    val cuentaId: String? = null,
)

@Entity(tableName = "ingresos")
data class Ingreso(
    @PrimaryKey val id: String,
    val fecha: String,
    val monto: Long,
    val tipo: String = TipoIngreso.OTRO,
    val nota: String = "",
    val claveIngresoProgramado: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    // a que cuenta entro; vacio es la cuenta principal
    val cuentaId: String? = null,
)

// donde esta la plata: davivienda, nequi, efectivo...
@Entity(tableName = "cuentas")
data class Cuenta(
    @PrimaryKey val id: String,
    val nombre: String,
    val emoji: String = "🏦",
    val orden: Int = 0,
    val activa: Boolean = true,
    val createdAt: String = "",
    val updatedAt: String = "",
)

// plata que se mueve sin ser gasto ni ingreso: envios entre cuentas, retiros y ajustes al cuadrar
@Entity(tableName = "cuenta_movs")
data class CuentaMov(
    @PrimaryKey val id: String,
    val fecha: String,
    val cuentaId: String,
    // positivo entra a la cuenta, negativo sale
    val monto: Long,
    val tipo: String,
    // las filas de un mismo envio o de un mismo cuadre
    val grupo: String? = null,
    val nota: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
)

object TipoCuentaMov {
    const val TRASLADO = "traslado"
    const val AJUSTE = "ajuste"
    // marca del cuadre con el desfase total, no mueve plata
    const val CUADRE = "cuadre"
}

// las etiquetas que se tocan para registrar un gasto o un ingreso
@Entity(tableName = "categorias")
data class Categoria(
    @PrimaryKey val id: String,
    val nombre: String,
    val esSistema: Boolean = false,
    val emoji: String = "🏷",
    val tipo: String = TipoEtiqueta.GASTO,
    val orden: Int = 0,
    val presupuestoId: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)

object TipoEtiqueta {
    const val GASTO = "gasto"
    const val INGRESO = "ingreso"
    const val SISTEMA = "sistema"
}

// todos los datos juntos para hacer los calculos
data class Datos(
    val config: Config,
    val deudas: List<Deuda> = emptyList(),
    val deudaSaldos: List<DeudaSaldo> = emptyList(),
    val gastosFijos: List<GastoFijo> = emptyList(),
    val presupuestos: List<Presupuesto> = emptyList(),
    val ingresosProgramados: List<IngresoProgramado> = emptyList(),
    val eventos: List<Evento> = emptyList(),
    val movimientos: List<Movimiento> = emptyList(),
    val ingresos: List<Ingreso> = emptyList(),
    val categorias: List<Categoria> = emptyList(),
    val cuentas: List<Cuenta> = emptyList(),
    val cuentaMovs: List<CuentaMov> = emptyList(),
)

object TipoDeuda {
    const val TARJETA = "tarjeta"
    const val ROTATIVO = "rotativo"
    const val LIBRE_INVERSION = "libre_inversion"
    const val BNPL = "buy_now_pay_later"
    const val PERSONA = "persona"
    const val OTRO = "otro"
    val todos = listOf(TARJETA, ROTATIVO, LIBRE_INVERSION, BNPL, PERSONA, OTRO)
}

object Frecuencia {
    const val MENSUAL = "mensual"
    const val SEMANAL = "semanal"
    const val QUINCENAL = "quincenal"
    val todas = listOf(MENSUAL, SEMANAL, QUINCENAL)
}

object Quincena {
    const val Q15 = "15"
    const val Q30 = "30"
    const val DIVIDIDO = "dividido"
    val todas = listOf(Q15, Q30, DIVIDIDO)
}

object Periodicidad {
    const val QUINCENAL = "quincenal"
    const val MENSUAL = "mensual"
    const val UNICO = "unico"
    val todas = listOf(QUINCENAL, MENSUAL, UNICO)
}

object FrecuenciaIngreso {
    const val QUINCENAL_DIA = "quincenal_dia"
    const val MENSUAL = "mensual"
    const val UNICO = "unico"
    val todas = listOf(QUINCENAL_DIA, MENSUAL, UNICO)
}

object TipoIngreso {
    const val SALARIO = "salario"
    const val MOTO = "moto"
    const val PRIMA = "prima"
    const val PRESTAMO = "prestamo"
    const val FREELANCE = "freelance"
    const val OTRO = "otro"
    val todos = listOf(SALARIO, MOTO, PRIMA, PRESTAMO, FREELANCE, OTRO)
}

object TipoMovimiento {
    const val PAGO_PROGRAMADO = "pago_programado"
    const val NO_PLANEADO = "gasto_no_planeado"
    const val ABONO_EXTRA = "abono_extra"
    val todos = listOf(PAGO_PROGRAMADO, NO_PLANEADO, ABONO_EXTRA)
}

object ModoPlan {
    const val BOLA_NIEVE = "bola_nieve"
    const val AVALANCHA = "avalancha"
}

object Categorias {
    const val DEUDA = "Deuda"
    const val GASTO_FIJO = "Gasto fijo"
    const val SEMANAL = "Semanal"
    const val COMIDA_TRANSPORTE = "Comida y transporte"
    const val PAREJA = "Pareja"
    const val ESTUDIO = "Estudio"
    const val ABONO_EXTRA = "Abono extra"
    const val NO_PLANEADO = "No planeado"
    const val INGRESO = "Ingreso"
    val sistema = listOf(DEUDA, GASTO_FIJO, SEMANAL, COMIDA_TRANSPORTE, PAREJA, ESTUDIO, ABONO_EXTRA, NO_PLANEADO, INGRESO)

    // categorias para gastos no planeados
    val noPlaneado = listOf("comida_fuera", "transporte", "ocio", "salud", "ropa", "hogar", "regalos", "otros")
}
