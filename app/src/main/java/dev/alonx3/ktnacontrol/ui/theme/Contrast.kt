package dev.alonx3.ktnacontrol.ui.theme

/**
 * **Contraste WCAG 2 entre dos colores sRGB**, Kotlin puro — sin `androidx.compose.ui.graphics.Color`
 * ni nada de `android.*`, a propósito, igual que `protocol/` (CLAUDE.md §6): así el cálculo se
 * puede probar en JVM sin arrastrar el runtime de Compose, y `Color.kt` puede citar el resultado
 * en su KDoc en vez de solo afirmarlo. Ver CLAUDE.md §4.9, Fase 5.
 *
 * La fórmula es la del propio estándar (relative luminance + `(L1+0.05)/(L2+0.05)`), no una
 * aproximación: https://www.w3.org/TR/WCAG21/#dfn-relative-luminance. Con eso el número que
 * enseña este fichero es el mismo que reportaría cualquier verificador de contraste externo,
 * comprobable — el test lo fija contra blanco/negro (21.00:1) y contra sí mismo (1.00:1), los
 * dos casos de libro del estándar.
 *
 * Recibe los colores como tres canales `Int` de 0 a 255, no como un `Long` empaquetado: es la
 * forma más difícil de teclear mal (`contrastRatio(0xFF, 0x7A, 0x1A, ...)` en vez de adivinar en
 * qué orden van los bytes de un `0xFFRRGGBB`), y la que no depende de qué tipo de color use quien
 * llama.
 */
internal fun contrastRatio(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Double {
    val l1 = relativeLuminance(r1, g1, b1)
    val l2 = relativeLuminance(r2, g2, b2)
    val lighter = maxOf(l1, l2)
    val darker = minOf(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)
}

/**
 * La misma cuenta, a partir de un hex de 24 o 32 bits (`0xRRGGBB` o `0xAARRGGBB`, como los que ya
 * escribe `Color.kt`). Solo mira los 24 bits bajos, así que un `0xFF3ECF5C` con byte de alfa
 * delante se lee igual que un `0x3ECF5C` sin él — no hace falta desnudar el literal a mano en
 * cada llamada.
 */
internal fun contrastRatio(hex1: Long, hex2: Long): Double = contrastRatio(
    r1 = (hex1 shr 16 and 0xFF).toInt(),
    g1 = (hex1 shr 8 and 0xFF).toInt(),
    b1 = (hex1 and 0xFF).toInt(),
    r2 = (hex2 shr 16 and 0xFF).toInt(),
    g2 = (hex2 shr 8 and 0xFF).toInt(),
    b2 = (hex2 and 0xFF).toInt(),
)

private fun relativeLuminance(r: Int, g: Int, b: Int): Double {
    fun channel(c: Int): Double {
        val cs = c / 255.0
        return if (cs <= 0.03928) cs / 12.92 else Math.pow((cs + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
}

/**
 * Los dos umbrales que usa el barrido de la Fase 5 (CLAUDE.md §4.9), citados aquí en vez de
 * escritos sueltos donde se comparan: **texto normal, WCAG AA — `4.5:1`** (todo el texto de
 * este proyecto cae ahí; no hay texto grande de 18pt+ que pudiera relajarse a 3:1, así que no
 * hace falta el segundo umbral de la norma) y **componente/objeto gráfico no textual, WCAG
 * 1.4.11 — `3:1`** (la franja y el punto de color de `EffectCard`, y el relleno de acento de
 * sliders/perillas contra su pista).
 */
internal object ContrastThreshold {
    const val TEXT = 4.5
    const val NON_TEXT_COMPONENT = 3.0
}
