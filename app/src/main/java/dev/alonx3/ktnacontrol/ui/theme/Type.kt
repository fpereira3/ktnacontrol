package dev.alonx3.ktnacontrol.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * **La escala tipográfica**, fuente única (CLAUDE.md §4.6).
 *
 * Antes aquí solo estaba `bodyLarge`: los otros catorce estilos eran los de Material sin que
 * nadie los hubiera mirado, y las pantallas los usaban igual. Ahora están todos los que la app
 * usa, con tamaños **algo más compactos** que los de Material — estas pantallas son densas (una
 * tarjeta de efecto son cinco controles apilados) y el defecto de 16 sp para `bodyLarge` gasta
 * altura en texto que casi siempre es una etiqueta corta.
 *
 * ⚠️ **Sin fuente propia, y es una decisión.** Un `.ttf` es un asset más que mantener y §6 pide
 * justificar cada añadido; y **copiar una de `reference/` está prohibido** (§7). El aire de
 * "etiqueta serigrafiada de panel" se consigue con `letterSpacing` en [Typography.titleSmall] —
 * que es el estilo de los encabezados de sección— sin cambiar de familia.
 */
val Typography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    // El encabezado de sección: es lo que da el aire de panel, con espaciado entre letras en vez
    // de otra familia tipográfica.
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.6.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.3.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    // El más pequeño de todos: valores de perilla y nombres de banda de EQ, donde no cabe más.
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
    ),
)
