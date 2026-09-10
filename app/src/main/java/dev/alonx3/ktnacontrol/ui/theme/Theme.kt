package dev.alonx3.ktnacontrol.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * El esquema de color de la app. **Uno solo** — ver [KTNAControlTheme] para el porqué.
 *
 * Se construye sobre `darkColorScheme` y no sobre `lightColorScheme` porque eso es lo que hace
 * que los componentes de Material que no se tocan aquí (menús, diálogos, `Snackbar`) calculen
 * bien sus elevaciones tonales sobre un fondo oscuro.
 */
private val ChassisColorScheme = darkColorScheme(
    primary = EmberPrimary,
    onPrimary = EmberOnPrimary,
    primaryContainer = EmberContainer,
    onPrimaryContainer = EmberOnContainer,
    secondary = SteelSecondary,
    onSecondary = ChassisBackground,
    secondaryContainer = SteelContainer,
    onSecondaryContainer = SteelOnContainer,
    // El terciario no se usa como acento propio: se iguala al secundario para que ningún
    // componente de Material saque de la manga un tercer color que nadie ha elegido.
    tertiary = SteelSecondary,
    onTertiary = ChassisBackground,
    tertiaryContainer = SteelContainer,
    onTertiaryContainer = SteelOnContainer,
    background = ChassisBackground,
    onBackground = ChassisOnSurface,
    surface = ChassisSurface,
    onSurface = ChassisOnSurface,
    surfaceVariant = ChassisSurfaceVariant,
    onSurfaceVariant = ChassisOnSurfaceVariant,
    surfaceContainer = ChassisSurface,
    surfaceContainerHigh = ChassisSurfaceVariant,
    outline = ChassisOutline,
    outlineVariant = ChassisOutlineVariant,
    error = WarningRed,
    onError = ChassisBackground,
    errorContainer = WarningContainer,
    onErrorContainer = WarningRed,
)

/**
 * **El tema de la app: chasis oscuro con un acento ámbar.** La dirección y las alternativas
 * descartadas están en CLAUDE.md §4.6.
 *
 * ⚠️ **No tiene parámetro `darkTheme` ni `dynamicColor`, y las dos ausencias son deliberadas.**
 *
 *  - **Siempre oscuro**: los tres colores de slot de efecto (verde/rojo/amarillo) son un hecho
 *    del dispositivo y se eligieron para contrastar contra grafito. Una versión clara obligaría a
 *    afinar un segundo juego —o a aceptar que en claro el amarillo se pierde—. Un panel de
 *    instrumento no cambia de color con la hora del día.
 *  - **Sin color dinámico**: hasta la Fase 3 estaba en `true`, así que en API ≥ 31 la paleta la
 *    decidía **el fondo de pantalla** y la del proyecto no se usaba. Se retira el parámetro
 *    entero en vez de dejarlo en `false`: un esquema derivado del wallpaper puede poner una
 *    superficie amarillenta justo debajo de un slot amarillo, y un diseño donde tres tonos tienen
 *    que seguir siendo inequívocos no puede delegar su paleta.
 */
@Composable
fun KTNAControlTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChassisColorScheme,
        typography = Typography,
        content = content,
    )
}
