package com.jonathan.miscuentas.dominio

// datos de arranque sin informacion personal
// los datos propios van en assets/datos_personales.txt que no se sube a git
object Semilla {

    const val ARCHIVO_PERSONAL = "datos_personales.txt"

    fun datos(ahora: String = Fechas.ahora(), fechaInicio: String = ahora.take(10)): Datos {
        val config = Config(fechaInicio = fechaInicio, createdAt = ahora, updatedAt = ahora)
        return Datos(config = config, categorias = etiquetas(ahora))
    }

    fun etiquetas(ahora: String = Fechas.ahora()): List<Categoria> {
        var orden = 0
        fun gasto(id: String, nombre: String, emoji: String) =
            Categoria("et-$id", nombre, false, emoji, TipoEtiqueta.GASTO, orden++, null, ahora, ahora)
        fun ingreso(id: String, nombre: String, emoji: String) =
            Categoria("et-$id", nombre, false, emoji, TipoEtiqueta.INGRESO, orden++, null, ahora, ahora)
        val sistema = Categorias.sistema.mapIndexed { i, n -> Categoria("sis-$i", n, true, "⚙", TipoEtiqueta.SISTEMA, 100 + i, null, ahora, ahora) }
        return listOf(
            gasto("comida", "Comida", "🍔"),
            gasto("mercado", "Mercado", "🛒"),
            gasto("gasolina", "Gasolina", "⛽"),
            gasto("aseo", "Aseo", "🧴"),
            gasto("transporte", "Transporte", "🚌"),
            gasto("pareja", "Salidas pareja", "❤️"),
            gasto("comida-fuera", "Comida fuera", "🍕"),
            gasto("ocio", "Ocio", "🎮"),
            gasto("salud", "Salud", "💊"),
            gasto("ropa", "Ropa", "👕"),
            gasto("hogar", "Hogar", "🏠"),
            gasto("perros", "Perros", "🐶"),
            gasto("regalos", "Regalos", "🎁"),
            gasto("otros", "Otros", "📦"),
            ingreso("salario", "Salario", "💼"),
            ingreso("moto", "Moto", "🏍"),
            ingreso("prima", "Prima", "🎄"),
            ingreso("prestamo", "Préstamo", "🤝"),
            ingreso("freelance", "Freelance", "💻"),
            ingreso("otro-ingreso", "Otro", "💵"),
        ) + sistema
    }

    // tipo de ingreso segun la etiqueta
    fun tipoIngreso(etiqueta: String): String = when (etiqueta) {
        "Salario" -> TipoIngreso.SALARIO
        "Moto" -> TipoIngreso.MOTO
        "Prima" -> TipoIngreso.PRIMA
        "Préstamo" -> TipoIngreso.PRESTAMO
        "Freelance" -> TipoIngreso.FREELANCE
        else -> TipoIngreso.OTRO
    }
}
