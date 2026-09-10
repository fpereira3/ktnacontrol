package dev.alonx3.ktnacontrol.ui.theme

import androidx.compose.ui.unit.dp

/**
 * **La escala de espaciado**, fuente única (CLAUDE.md §4.6).
 *
 * Cinco pasos y no más: con una escala larga cada hueco acaba eligiéndose a ojo, que es
 * exactamente lo que había antes de la Fase 3 —`4.dp`, `8.dp` y `12.dp` sueltos por todas
 * partes, sin que nadie hubiera decidido cuándo va cada uno.
 *
 * ⚠️ **Es espaciado, no tamaños.** El diámetro de una perilla o la altura de una barra de EQ son
 * medidas del componente y **no pertenecen aquí**: siguen siendo constantes con nombre junto a su
 * composable. Por eso esta escala no se ha propagado a los ~108 literales `.dp` del proyecto —
 * la mayoría no son huecos.
 */
internal object Spacing {

    /** Separación mínima: entre una etiqueta y su control. */
    val xs = 4.dp

    /** El hueco normal entre elementos de una misma fila o columna. */
    val sm = 8.dp

    /** Relleno interior de una tarjeta. */
    val md = 12.dp

    /** Margen lateral de una pantalla. */
    val lg = 16.dp

    /** Separación entre bloques que no tienen nada que ver entre sí. */
    val xl = 24.dp
}
