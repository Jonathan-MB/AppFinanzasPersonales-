package com.jonathan.miscuentas.datos

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jonathan.miscuentas.dominio.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanzasDao {
    @Query("SELECT * FROM config LIMIT 1") fun config(): Flow<Config?>
    @Query("SELECT * FROM config LIMIT 1") suspend fun configActual(): Config?
    @Query("SELECT * FROM deudas ORDER BY nombre") fun deudas(): Flow<List<Deuda>>
    @Query("SELECT * FROM deuda_saldos ORDER BY fecha") fun deudaSaldos(): Flow<List<DeudaSaldo>>
    @Query("SELECT * FROM gastos_fijos ORDER BY nombre") fun gastosFijos(): Flow<List<GastoFijo>>
    @Query("SELECT * FROM presupuestos ORDER BY nombre") fun presupuestos(): Flow<List<Presupuesto>>
    @Query("SELECT * FROM ingresos_programados ORDER BY nombre") fun ingresosProgramados(): Flow<List<IngresoProgramado>>
    @Query("SELECT * FROM eventos ORDER BY fecha") fun eventos(): Flow<List<Evento>>
    @Query("SELECT * FROM movimientos ORDER BY fecha DESC, createdAt DESC") fun movimientos(): Flow<List<Movimiento>>
    @Query("SELECT * FROM ingresos ORDER BY fecha DESC, createdAt DESC") fun ingresos(): Flow<List<Ingreso>>
    @Query("SELECT * FROM categorias ORDER BY orden, nombre") fun categorias(): Flow<List<Categoria>>
    @Query("SELECT * FROM cuentas ORDER BY orden, nombre") fun cuentas(): Flow<List<Cuenta>>
    @Query("SELECT * FROM cuenta_movs ORDER BY fecha DESC, createdAt DESC") fun cuentaMovs(): Flow<List<CuentaMov>>

    @Upsert suspend fun guardar(v: Config)
    @Upsert suspend fun guardar(v: Deuda)
    @Upsert suspend fun guardar(v: DeudaSaldo)
    @Upsert suspend fun guardar(v: GastoFijo)
    @Upsert suspend fun guardar(v: Presupuesto)
    @Upsert suspend fun guardar(v: IngresoProgramado)
    @Upsert suspend fun guardar(v: Evento)
    @Upsert suspend fun guardar(v: Movimiento)
    @Upsert suspend fun guardar(v: Ingreso)
    @Upsert suspend fun guardar(v: Categoria)
    @Upsert suspend fun guardar(v: Cuenta)
    @Upsert suspend fun guardar(v: CuentaMov)

    @Delete suspend fun borrar(v: Deuda)
    @Delete suspend fun borrar(v: DeudaSaldo)
    @Delete suspend fun borrar(v: GastoFijo)
    @Delete suspend fun borrar(v: Presupuesto)
    @Delete suspend fun borrar(v: IngresoProgramado)
    @Delete suspend fun borrar(v: Evento)
    @Delete suspend fun borrar(v: Movimiento)
    @Delete suspend fun borrar(v: Ingreso)
    @Delete suspend fun borrar(v: Categoria)
    @Delete suspend fun borrar(v: Cuenta)
    @Delete suspend fun borrar(v: CuentaMov)

    @Upsert suspend fun guardarDeudas(v: List<Deuda>)
    @Upsert suspend fun guardarDeudaSaldos(v: List<DeudaSaldo>)
    @Upsert suspend fun guardarGastos(v: List<GastoFijo>)
    @Upsert suspend fun guardarPresupuestos(v: List<Presupuesto>)
    @Upsert suspend fun guardarProgramados(v: List<IngresoProgramado>)
    @Upsert suspend fun guardarEventos(v: List<Evento>)
    @Upsert suspend fun guardarMovimientos(v: List<Movimiento>)
    @Upsert suspend fun guardarIngresos(v: List<Ingreso>)
    @Upsert suspend fun guardarCategorias(v: List<Categoria>)
    @Upsert suspend fun guardarCuentas(v: List<Cuenta>)
    @Upsert suspend fun guardarCuentaMovs(v: List<CuentaMov>)

    @Query("DELETE FROM config") suspend fun vaciarConfig()
    @Query("DELETE FROM deudas") suspend fun vaciarDeudas()
    @Query("DELETE FROM deuda_saldos") suspend fun vaciarDeudaSaldos()
    @Query("DELETE FROM gastos_fijos") suspend fun vaciarGastos()
    @Query("DELETE FROM presupuestos") suspend fun vaciarPresupuestos()
    @Query("DELETE FROM ingresos_programados") suspend fun vaciarProgramados()
    @Query("DELETE FROM eventos") suspend fun vaciarEventos()
    @Query("DELETE FROM movimientos") suspend fun vaciarMovimientos()
    @Query("DELETE FROM ingresos") suspend fun vaciarIngresos()
    @Query("DELETE FROM categorias") suspend fun vaciarCategorias()
    @Query("DELETE FROM cuentas") suspend fun vaciarCuentas()
    @Query("DELETE FROM cuenta_movs") suspend fun vaciarCuentaMovs()

    // cambia todo de una vez, si algo falla no se guarda nada
    @Transaction
    suspend fun reemplazarTodo(d: Datos) {
        vaciarConfig(); vaciarDeudas(); vaciarDeudaSaldos(); vaciarGastos(); vaciarPresupuestos()
        vaciarProgramados(); vaciarEventos(); vaciarMovimientos(); vaciarIngresos(); vaciarCategorias()
        vaciarCuentas(); vaciarCuentaMovs()
        guardar(d.config)
        guardarDeudas(d.deudas); guardarDeudaSaldos(d.deudaSaldos); guardarGastos(d.gastosFijos)
        guardarPresupuestos(d.presupuestos); guardarProgramados(d.ingresosProgramados); guardarEventos(d.eventos)
        guardarMovimientos(d.movimientos); guardarIngresos(d.ingresos); guardarCategorias(d.categorias)
        guardarCuentas(d.cuentas); guardarCuentaMovs(d.cuentaMovs)
    }
}

@Database(
    entities = [Config::class, Deuda::class, DeudaSaldo::class, GastoFijo::class, Presupuesto::class,
        IngresoProgramado::class, Evento::class, Movimiento::class, Ingreso::class, Categoria::class, Cuenta::class, CuentaMov::class],
    version = 3,
    exportSchema = true,
)
abstract class BaseDatos : RoomDatabase() {
    abstract fun dao(): FinanzasDao

    companion object {
        // cambios de version de la base
        private val DE_1_A_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE movimientos ADD COLUMN liquida INTEGER NOT NULL DEFAULT 0")
            }
        }

        // cuentas (davivienda, nequi, efectivo) y movimientos entre ellas
        private val DE_2_A_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `cuentas` (`id` TEXT NOT NULL, `nombre` TEXT NOT NULL, `emoji` TEXT NOT NULL, `orden` INTEGER NOT NULL, `activa` INTEGER NOT NULL, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `cuenta_movs` (`id` TEXT NOT NULL, `fecha` TEXT NOT NULL, `cuentaId` TEXT NOT NULL, `monto` INTEGER NOT NULL, `tipo` TEXT NOT NULL, `grupo` TEXT, `nota` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("ALTER TABLE movimientos ADD COLUMN cuentaId TEXT")
                db.execSQL("ALTER TABLE ingresos ADD COLUMN cuentaId TEXT")
                val ahora = Fechas.ahora()
                Saldos.cuentasPorDefecto(ahora).forEach { c ->
                    db.execSQL(
                        "INSERT INTO cuentas (id, nombre, emoji, orden, activa, createdAt, updatedAt) VALUES (?, ?, ?, ?, 1, ?, ?)",
                        arrayOf<Any>(c.id, c.nombre, c.emoji, c.orden, ahora, ahora),
                    )
                }
            }
        }

        val MIGRACIONES: Array<Migration> = arrayOf(DE_1_A_2, DE_2_A_3)

        @Volatile private var instancia: BaseDatos? = null

        fun obtener(context: Context): BaseDatos = instancia ?: synchronized(this) {
            instancia ?: Room.databaseBuilder(context.applicationContext, BaseDatos::class.java, "mis_cuentas.db")
                .addMigrations(*MIGRACIONES)
                .build().also { instancia = it }
        }
    }
}
