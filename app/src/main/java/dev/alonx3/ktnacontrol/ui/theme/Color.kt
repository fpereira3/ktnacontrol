package dev.alonx3.ktnacontrol.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * **La única lista de colores del proyecto.** Ningún composable escribe un `Color(0x…)`
 * (CLAUDE.md §6); todos salen de aquí a través de `MaterialTheme.colorScheme` o de
 * [EffectSlotColors].
 *
 * La dirección visual y el porqué de cada decisión están en CLAUDE.md §4.6. En resumen:
 * **chasis de equipo** —grafitos neutros, sin tinte— con **un solo acento cálido**. El grafito
 * no es gusto: los tres colores de slot de efecto son un hecho del dispositivo y necesitan un
 * fondo neutro para leerse como señal en vez de como decoración.
 *
 * ✅ **Pase de contraste de la Fase 5 (CLAUDE.md §4.9): los pares reales de esta paleta cumplen
 * los dos umbrales, y quedan pineados en `ContrastTest`.** Contraste WCAG 2 calculado con
 * [contrastRatio], no estimado a ojo — texto ≥ `4.5:1`, componente/objeto gráfico no textual
 * ≥ `3:1` ([ContrastThreshold]):
 *
 * | Par | Contraste | Umbral |
 * | --- | --- | --- |
 * | [ChassisOnSurface] / [ChassisSurface] (texto principal) | 14.48:1 | texto |
 * | [ChassisOnSurfaceVariant] / [ChassisSurface] (texto secundario) | 7.94:1 | texto |
 * | [EmberOnPrimary] / [EmberPrimary] (botón relleno) | 7.22:1 | texto |
 * | [EmberOnContainer] / [EmberContainer] | 9.18:1 | texto |
 * | [SteelOnContainer] / [SteelContainer] | 9.35:1 | texto |
 * | [SteelSecondary] / [ChassisSurface] (encabezados de sección) | 6.61:1 | texto |
 * | [WarningRed] / [ChassisSurface] (texto de aviso) | 6.37:1 | texto |
 * | [EmberPrimary] / [ChassisSurfaceVariant] (relleno de perilla/slider) | 5.83:1 | componente |
 * | [EffectSlotColors.Green] / [ChassisSurface] (franja+punto) | 8.71:1 | componente |
 * | [EffectSlotColors.Red] / [ChassisSurface] (franja+punto) | 5.01:1 | componente |
 * | [EffectSlotColors.Yellow] / [ChassisSurface] (franja+punto) | 12.45:1 | componente |
 *
 * **Ninguno quedó por debajo del umbral, así que ningún color de slot se movió.** Era la
 * decisión explícita a tomar si hubiera hecho falta —congelar y documentar el conflicto en vez
 * de resolverlo en silencio, porque el valor del slot es un hecho del dispositivo, no un color
 * a elegir (§4.6)— y no hizo falta tomarla: los tres ya estaban elegidos por legibilidad sobre
 * grafito desde la Fase 3, y el cálculo solo lo confirma con un número en vez de una intuición.
 * El más ajustado de los tres es el rojo (`5.01:1`), y sigue con margen de sobra sobre el `3:1`
 * que le corresponde por ser franja+punto, no texto.
 *
 * ⚠️ **[EffectSlotColors.Unknown] (= [ChassisOutline]) no entra en esta tabla, y es a
 * propósito.** Da `1.67:1` contra `ChassisSurface` — por debajo del umbral de componente— pero
 * no es un fallo: es el gris de borde que se pinta **cuando no hay color que mostrar todavía**,
 * o sea que su bajo contraste es exactamente el punto — un slot sin leer no debe llamar la
 * atención como si tuviera una respuesta. WCAG 1.4.11 exige contraste a lo que **transmite
 * información**; `Unknown` transmite justo lo contrario, la ausencia de ella.
 */

// ---------------------------------------------------------------------------------------------
// El chasis: grafitos neutros. Ninguno lleva tinte de color a propósito.
// ---------------------------------------------------------------------------------------------

/** El fondo, lo más oscuro del conjunto. */
internal val ChassisBackground = Color(0xFF0E0F11)

/** Barra superior, barra inferior y tarjetas: un escalón por encima del fondo. */
internal val ChassisSurface = Color(0xFF16181B)

/** Pistas de slider, discos de perilla, tarjetas de aviso. */
internal val ChassisSurfaceVariant = Color(0xFF232629)

/** Bordes, separadores y **todo lo que está apagado**. */
internal val ChassisOutline = Color(0xFF3A3F44)

internal val ChassisOutlineVariant = Color(0xFF2A2E33)

internal val ChassisOnSurface = Color(0xFFE6E8EA)

/** Etiquetas y texto secundario: legible, pero claramente por detrás del principal. */
internal val ChassisOnSurfaceVariant = Color(0xFFA8AEB4)

// ---------------------------------------------------------------------------------------------
// El acento: ámbar-naranja de válvula. Es "lo que se puede tocar".
// ---------------------------------------------------------------------------------------------

/**
 * ⚠️ **Cae entre el rojo y el amarillo de [EffectSlotColors], y eso es inevitable**: cualquier
 * naranja lo hace. La ambigüedad se resuelve por la **forma**, no por el tono — el acento rellena
 * controles, el color de slot es una franja de borde y un punto, y además el slot siempre lleva
 * su nombre escrito al lado. Ver CLAUDE.md §4.6.
 */
internal val EmberPrimary = Color(0xFFFF7A1A)

/** Sobre el ámbar va texto oscuro: con blanco no hay contraste suficiente. */
internal val EmberOnPrimary = Color(0xFF1B0F00)

internal val EmberContainer = Color(0xFF4A2A00)

internal val EmberOnContainer = Color(0xFFFFD1A3)

/** Acero frío: lo secundario que no debe competir con el acento. */
internal val SteelSecondary = Color(0xFF8FA0AD)

internal val SteelContainer = Color(0xFF2B3238)

internal val SteelOnContainer = Color(0xFFD3DCE3)

/**
 * ⚠️ Comparte tono con el rojo de slot, y por eso **solo tiñe texto de aviso**, nunca una franja
 * ni un punto. Aclarado respecto de un rojo puro para que se lea sobre grafito.
 */
internal val WarningRed = Color(0xFFFF6B60)

internal val WarningContainer = Color(0xFF4A1512)

// ---------------------------------------------------------------------------------------------
// Los tres slots de color de un efecto.
// ---------------------------------------------------------------------------------------------

/**
 * El color con el que se pinta la tarjeta de un efecto según **el slot que tenga activo**.
 *
 * ⚠️ **Separar lo que es hecho y lo que es elección importa aquí** (CLAUDE.md §4.6):
 *  - **Hecho del dispositivo**: que `60 00 06 39`–`06 3D` valgan `00` verde, `01` rojo, `02`
 *    amarillo. Está documentado en tres fuentes independientes y comprobado sobre un `.tsl` real
 *    (5 efectos de 5). Lo codifica
 *    [EffectColor][dev.alonx3.ktnacontrol.protocol.EffectColor], no esta tabla.
 *  - **Elección de diseño, sin fuente**: los hex de abajo. Ninguna fuente dice qué verde enciende
 *    el LED del panel —es luz, no un `#RRGGBB`—, así que son tres tonos de LED elegidos por
 *    legibilidad sobre grafito.
 *
 * ⚠️ **Ningún asset de `reference/`** (§7): esto son seis números escritos a mano.
 */
internal object EffectSlotColors {

    /** `EffectColor.GREEN` (`0x00`). */
    val Green = Color(0xFF3ECF5C)

    /** `EffectColor.RED` (`0x01`). */
    val Red = Color(0xFFFF3B30)

    /** `EffectColor.YELLOW` (`0x02`). */
    val Yellow = Color(0xFFFFD426)

    /**
     * Lo que se pinta cuando **el amplificador todavía no ha dicho** de qué color está el slot.
     * Es el gris de borde, no un color: un slot sin leer no puede fingir tener uno.
     */
    val Unknown = ChassisOutline
}

/**
 * El puente hacia [contrastRatio] (Fase 5, CLAUDE.md §4.9): empaqueta los tres canales de un
 * [Color] en el mismo `0xRRGGBB` que ya usan los literales de este fichero, para poder citar el
 * contraste real de cada par en su KDoc y comprobarlo en `ContrastTest`.
 *
 * ⚠️ **`contrastRatio` sigue sin conocer `Color` ni Compose, a propósito** (mismo criterio que
 * `protocol/`, CLAUDE.md §6): esta es la única línea del proyecto que cruza de uno a otro, y
 * vive aquí — junto al `Color` que empaqueta — en vez de en `Contrast.kt`, que se queda
 * Kotlin puro.
 */
internal val Color.hex: Long
    get() = (Math.round(red * 255) shl 16 or
        (Math.round(green * 255) shl 8) or
        Math.round(blue * 255)).toLong()
