package com.jonathan.miscuentas.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jonathan.miscuentas.avisos.Avisos
import com.jonathan.miscuentas.datos.Repositorio
import com.jonathan.miscuentas.dominio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

// lo que se eligio en la pantalla de etiquetas antes de poner el monto
sealed class Seleccion {
    data class Etiqueta(val etiqueta: Categoria) : Seleccion()
    data class Pago(val obligacion: Obligacion) : Seleccion()
    data class IngresoEtiqueta(val etiqueta: Categoria) : Seleccion()
    data class IngresoEsperadoSel(val esperado: IngresoEsperado) : Seleccion()
    data class AbonoDeuda(val deuda: Deuda) : Seleccion()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val repo = Repositorio(app)
    val datos: StateFlow<Datos?> = repo.datos.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var seleccion: Seleccion? = null

    // el siguiente gasto propone la tarjeta preferida para puente
    var usarPuente = false

    // despues de guardar un sueldo se abre la pantalla de llego el sueldo
    var abrirSueldo = false

    fun hoy(): LocalDate = LocalDate.now()

    init {
        viewModelScope.launch {
            repo.iniciar()
            val d = repo.datos.filterNotNull().first()
            Avisos.programar(getApplication(), d.config)
            repo.respaldoSemanalSiToca(d)
        }
    }

    fun guardar(v: Any) = viewModelScope.launch {
        repo.guardar(v)
        if (v is Config) Avisos.programar(getApplication(), v)
    }

    fun borrar(v: Any) = viewModelScope.launch { repo.borrar(v) }

    // arma el movimiento segun lo que se toco
    // con tarjeta: no sale de ninguna cuenta y deja el puente para pagarlo despues
    fun registrarGasto(
        sel: Seleccion, monto: Long, fecha: LocalDate, nota: String, completo: Boolean = false, cuentaId: String? = null,
        tarjetaId: String? = null, cuotas: Int? = null, fechaPagoPuente: LocalDate? = null,
    ) = viewModelScope.launch {
        val d = datos.value ?: return@launch
        val iso = Fechas.iso(fecha)
        var claveRevisar: Obligacion? = null
        val mov = when (sel) {
            is Seleccion.Pago -> {
                val o = sel.obligacion
                if (o.tipoOrigen == Origen.PRESUPUESTO) claveRevisar = o
                Movimiento(nuevoId(), iso, monto, nota, o.clave, TipoMovimiento.PAGO_PROGRAMADO, o.categoria, o.deudaId,
                    liquida = completo && o.tipoOrigen != Origen.PRESUPUESTO)
            }
            is Seleccion.AbonoDeuda ->
                Movimiento(nuevoId(), iso, monto, nota, null, TipoMovimiento.ABONO_EXTRA, Categorias.ABONO_EXTRA, sel.deuda.id)
            is Seleccion.Etiqueta -> {
                val et = sel.etiqueta
                val inst = et.presupuestoId?.let { Obligaciones.instanciaPresupuesto(d, it, fecha) }
                if (inst != null) {
                    claveRevisar = inst
                    Movimiento(nuevoId(), iso, monto, nota.ifBlank { et.nombre }, inst.clave, TipoMovimiento.PAGO_PROGRAMADO, et.nombre)
                } else {
                    Movimiento(nuevoId(), iso, monto, nota, null, TipoMovimiento.NO_PLANEADO, et.nombre)
                }
            }
            else -> return@launch
        }
        repo.guardar(
            if (tarjetaId != null) mov.copy(cuentaId = null, tarjetaId = tarjetaId, cuotas = cuotas ?: 1,
                fechaPagoPuente = if ((cuotas ?: 1) <= 1) fechaPagoPuente?.let { Fechas.iso(it) } else null,
                // pagar con tarjeta no baja la deuda que se esta pagando
                deudaId = if (mov.tipo == TipoMovimiento.ABONO_EXTRA) mov.deudaId else null)
            else mov.copy(cuentaId = cuentaId ?: Saldos.porDefecto(d))
        )
        claveRevisar?.let { antes ->
            val despues = antes.copy(pagado = antes.pagado + monto)
            Avisos.revisarPresupuesto(getApplication(), antes, despues)
        }
    }

    fun registrarIngreso(sel: Seleccion, monto: Long, fecha: LocalDate, nota: String, cuentaId: String? = null) {
        // se marca antes de guardar para que al volver se abra la pantalla del sueldo
        val tipo = when (sel) {
            is Seleccion.IngresoEsperadoSel -> sel.esperado.tipo
            is Seleccion.IngresoEtiqueta -> Semilla.tipoIngreso(sel.etiqueta.nombre)
            else -> null
        }
        if (tipo == TipoIngreso.SALARIO) abrirSueldo = true
        guardarIngreso(sel, monto, fecha, nota, cuentaId)
    }

    private fun guardarIngreso(sel: Seleccion, monto: Long, fecha: LocalDate, nota: String, cuentaId: String?) = viewModelScope.launch {
        val d = datos.value ?: return@launch
        val iso = Fechas.iso(fecha)
        val ingreso = when (sel) {
            is Seleccion.IngresoEsperadoSel -> Ingreso(nuevoId(), iso, monto, sel.esperado.tipo, nota.ifBlank { sel.esperado.concepto }, sel.esperado.clave)
            is Seleccion.IngresoEtiqueta -> Ingreso(nuevoId(), iso, monto, Semilla.tipoIngreso(sel.etiqueta.nombre), nota.ifBlank { sel.etiqueta.nombre })
            else -> return@launch
        }
        repo.guardar(ingreso.copy(cuentaId = cuentaId ?: Saldos.porDefecto(d)))
    }

    // paga de una vez todo lo pendiente por lo que falta
    fun pagarTodo(lista: List<Obligacion>, fecha: LocalDate) = viewModelScope.launch {
        val cuenta = datos.value?.let { Saldos.porDefecto(it) }
        lista.filter { it.falta > 0 }.forEach { o ->
            repo.guardar(Movimiento(nuevoId(), Fechas.iso(fecha), o.falta, "", o.clave, TipoMovimiento.PAGO_PROGRAMADO, o.categoria, o.deudaId, liquida = true, cuentaId = cuenta))
        }
    }

    fun moto(monto: Long, fecha: LocalDate) = viewModelScope.launch {
        repo.guardar(Ingreso(nuevoId(), Fechas.iso(fecha), monto, TipoIngreso.MOTO, "Moto", cuentaId = datos.value?.let { Saldos.porDefecto(it) }))
    }

    // deja cada cuenta igual a lo que se tiene de verdad y anota el desfase
    fun cuadrar(reales: Map<String, Long>) = viewModelScope.launch {
        val d = datos.value ?: return@launch
        val total = reales.values.sum()
        repo.guardar(Saldos.cuadrar(d, reales, Fechas.iso(hoy())))
        repo.guardar(d.config.copy(saldoReal = total, fechaSaldoReal = Fechas.iso(hoy())))
    }

    // mueve un pago a otra fecha; si la fecha es la original se quita el pospuesto
    fun posponer(clave: String, fecha: LocalDate, nota: String) = viewModelScope.launch {
        val original = Fechas.leer(clave.substringAfterLast(':'))
        val actual = datos.value?.pospuestos?.firstOrNull { it.clave == clave }
        if (original == fecha) actual?.let { repo.borrar(it) }
        else repo.guardar(Pospuesto(clave, Fechas.iso(fecha), nota, actual?.createdAt ?: ""))
    }

    fun pausar(tipo: String, id: String, desde: LocalDate, hasta: LocalDate, nota: String) = viewModelScope.launch {
        repo.guardar(Pausa(nuevoId(), tipo, id, Fechas.iso(desde), Fechas.iso(hasta), nota))
    }

    // aparta plata de una cuenta al bolsillo o al colchon
    fun apartar(hacia: String, monto: Long) = viewModelScope.launch {
        val d = datos.value ?: return@launch
        val desde = Saldos.porDefecto(d)?.takeIf { it != hacia } ?: Saldos.principal(d) ?: return@launch
        if (desde == hacia) return@launch
        repo.guardar(Saldos.trasladar(desde, hacia, monto, Fechas.iso(hoy()), "apartado"))
    }

    // envio entre cuentas o retiro, no cambia el total
    fun mover(desde: String, hacia: String, monto: Long, fecha: LocalDate, nota: String) = viewModelScope.launch {
        repo.guardar(Saldos.trasladar(desde, hacia, monto, Fechas.iso(fecha), nota))
    }

    fun actualizarSaldoExtracto(deuda: Deuda, saldo: Long, fecha: LocalDate) = viewModelScope.launch {
        val iso = Fechas.iso(fecha)
        repo.guardar(deuda.copy(saldoExtracto = saldo, fechaSaldoExtracto = iso))
        repo.guardar(DeudaSaldo(nuevoId(), deuda.id, iso, saldo))
    }

    suspend fun exportar(): File? = datos.value?.let { repo.crearExportacion(it) }

    suspend fun importar(uri: Uri): ResultadoImport? = withContext(Dispatchers.IO) {
        val texto = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: return@withContext null
        repo.importar(texto)
    }

    suspend fun actualizarDesdeArchivo(): ResultadoImport? = repo.actualizarDesdeArchivo()

    fun borrarTodo() = viewModelScope.launch {
        datos.value?.let { repo.guardarRespaldo(it, "antes_de_borrar") }
        repo.borrarTodo()
    }
}
