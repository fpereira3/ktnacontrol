package dev.alonx3.ktnacontrol.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.alonx3.ktnacontrol.R
import dev.alonx3.ktnacontrol.protocol.ModFxType
import kotlin.math.roundToInt
import dev.alonx3.ktnacontrol.ui.theme.KTNAControlTheme
import dev.alonx3.ktnacontrol.ui.theme.Spacing
import dev.alonx3.ktnacontrol.usb.UsbConnectionState

/**
 * **Las cinco tarjetas de efecto**, sin nada del dominio de amplificador.
 *
 * Es el segundo composable extraído con el patrón que fijó `AmpSection` (CLAUDE.md §4.2, "Cómo
 * se parte `SlidersPane`"): [EffectsScreen] lo usa como su cuerpo, y `SlidersPane` —que sigue
 * sirviendo al editor offline de la Biblioteca— lo llama **en el sitio exacto** donde antes
 * tenía este código, así que su orden no cambia.
 *
 * ⚠️ **Aquí no hizo falta ninguna lista nueva**: [EffectId.entries] ya era la fuente de verdad
 * para "qué es un efecto" —es la razón de que `AmpDomain.LEVELS` fuera una resta desde el
 * principio—, y los tres selectores de corte de frecuencia que pertenecen a Delay/Reverb ya
 * estaban en [AmpDomain.EFFECT_SELECTORS]. Repartir controles entre pantallas es trabajo que
 * `AmpDomain` ya hizo; esta pantalla solo lo consume.
 */
@Composable
internal fun EffectsSection(
    levels: Map<LevelId, Int?>,
    effectColors: Map<EffectId, Int?>,
    effectEnabled: Map<EffectId, Boolean?>,
    effectTypes: Map<EffectId, Int?>,
    /** Todo lo de estas tarjetas es un parámetro, así que todo cae bajo Edit Mode. */
    canEdit: Boolean,
    onLevelChanged: (LevelId, Int) -> Unit,
    onEffectColorChanged: (EffectId, Int) -> Unit,
    onEffectEnabledChanged: (EffectId, Boolean) -> Unit,
    onEffectTypeChanged: (EffectId, Int) -> Unit,
    boosterParams: Map<BoosterParamId, Int?>,
    boosterSoloEnabled: Boolean?,
    onBoosterParamChanged: (BoosterParamId, Int) -> Unit,
    onBoosterSoloEnabledChanged: (Boolean) -> Unit,
    delayParams: Map<DelayParamId, Int?>,
    reverbParams: Map<ReverbParamId, Int?>,
    /**
     * ⚠️ El mapa completo de selectores, no filtrado. `EffectCard` solo lee de aquí
     * [AmpDomain.EFFECT_SELECTORS] (los cortes de Delay/Reverb) — pasarle el mapa entero es lo
     * mismo que ya hacía `SlidersPane` antes de esta extracción, así que ningún control cambia
     * de comportamiento por el troceado.
     */
    selectors: Map<SelectorId, Int?>,
    onSelectorChanged: (SelectorId, Int) -> Unit,
    onDelayParamChanged: (DelayParamId, Int) -> Unit,
    onReverbParamChanged: (ReverbParamId, Int) -> Unit,
    reverbTime: Double?,
    onReverbTimeChanged: (Double) -> Unit,
    modChorusPreDelayLow: Double?,
    modChorusPreDelayHigh: Double?,
    onModChorusPreDelayLowChanged: (Double) -> Unit,
    onModChorusPreDelayHighChanged: (Double) -> Unit,
    modInternalRaw: Map<ModFxType, Map<String, Int?>>,
    fxInternalRaw: Map<ModFxType, Map<String, Int?>>,
    onModParamChanged: (ModFxType, String, Double) -> Unit,
    onFxParamChanged: (ModFxType, String, Double) -> Unit,
    /** El nivel del Solo del amplificador — ahora una tarjeta más de aquí. Ver [SoloCard]. */
    ampSoloLevel: Int?,
    onAmpSoloLevelChanged: (Int) -> Unit,
    /**
     * La tarjeta de diagnóstico del Solo, o **null para no pintarla**: pregunta cosas que solo el
     * hardware puede contestar, así que offline no tiene sentido. Viajaba con `AmpSection` y se
     * muda con el Solo, que es de lo que trata.
     */
    diagnostics: SoloDiagnostics?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.section_effects))

        EffectId.entries.forEach { effect ->
            EffectCard(
                effect = effect,
                level = levels[effect.level],
                color = effectColors[effect],
                enabled = effectEnabled[effect],
                type = effectTypes[effect],
                canEdit = canEdit,
                onLevelChanged = onLevelChanged,
                onColorChanged = onEffectColorChanged,
                onEnabledChanged = onEffectEnabledChanged,
                onTypeChanged = onEffectTypeChanged,
                boosterParams = boosterParams,
                boosterSoloEnabled = boosterSoloEnabled,
                onBoosterParamChanged = onBoosterParamChanged,
                onBoosterSoloEnabledChanged = onBoosterSoloEnabledChanged,
                delayParams = delayParams,
                reverbParams = reverbParams,
                selectors = selectors,
                onSelectorChanged = onSelectorChanged,
                onDelayParamChanged = onDelayParamChanged,
                onReverbParamChanged = onReverbParamChanged,
                reverbTime = reverbTime,
                onReverbTimeChanged = onReverbTimeChanged,
                modChorusPreDelayLow = modChorusPreDelayLow,
                modChorusPreDelayHigh = modChorusPreDelayHigh,
                onModChorusPreDelayLowChanged = onModChorusPreDelayLowChanged,
                onModChorusPreDelayHighChanged = onModChorusPreDelayHighChanged,
                modInternalRaw = modInternalRaw,
                fxInternalRaw = fxInternalRaw,
                onModParamChanged = onModParamChanged,
                onFxParamChanged = onFxParamChanged,
            )
        }

        // ⚠️ **Solo va al final, después de Reverb** (QA 2026-09-09, bloque C). No es uno de los
        // cinco efectos del panel, así que no entra en el bucle de [EffectId] — pero sí es un
        // efecto en la forma que importa aquí: se enciende, tiene nivel, y (documentado, sin
        // cablear) su propio EQ. Ver [AmpDomain.EFFECT_SELECTORS] para el criterio.
        SoloCard(
            enabled = selectors[SelectorId.AMP_SOLO] == SWITCH_ON_VALUE,
            level = ampSoloLevel,
            canEdit = canEdit,
            onEnabledChanged = { on ->
                onSelectorChanged(
                    SelectorId.AMP_SOLO,
                    if (on) SWITCH_ON_VALUE else SWITCH_OFF_VALUE,
                )
            },
            onLevelChanged = onAmpSoloLevelChanged,
            diagnostics = diagnostics,
        )
    }
}

/**
 * **La tarjeta de Solo**, la sexta de [EffectsSection] (QA 2026-09-09, bloque C).
 *
 * ✅ **Opción B del encargo: una tarjeta con la misma forma visual que las otras cinco, pero sin
 * forzarla a la estructura de [EffectCard].** El encargo ofrecía también volverla un
 * "pseudo-efecto" que implementara lo mismo que Booster/Mod/FX/Delay/Reverb, y se descarta porque
 * **la forma de sus datos es distinta de verdad, no solo más pobre**:
 *
 * | | Los cinco efectos | Solo |
 * | --- | --- | --- |
 * | Slot de color verde/rojo/amarillo | sí, es lo que guarda el tipo | **no existe** |
 * | Catálogo de tipos | sí, de 7 a 30 según el efecto | **no existe** |
 * | Nivel | sí, con escala `Off + 1..101` | sí, pero directa `0..100` |
 *
 * Meterlo por [EffectId] obligaría a que ese enum admitiera una entrada **sin color y sin tipo**,
 * y a que `EffectCard` creciera ramas `if (effect == SOLO)` en el sitio que hoy es regular para
 * los cinco. Sería pagar complejidad en el camino común para ahorrar una tarjeta de veinte
 * líneas — justo al revés de lo que conviene, y por eso `EffectId` se queda con cinco entradas y
 * los tests de dominio que cuentan cinco siguen valiendo.
 *
 * ⚠️ **Lo que falta y está anotado**: el bloque SOLO EQ (`60 00 0F 10`–`0F 19`), localizado
 * documentalmente en el bloque B.1 de este mismo reporte, con sus diez parámetros y sus dos
 * testigos en `midi.xml`. **No se cablea todavía** porque cae fuera del dump y serían diez GET de
 * respaldo en serie por recarga — ver `SoloEqParams.OUTSIDE_DUMP`. Cuando se resuelva eso, va
 * aquí dentro, en un bloque colapsable.
 */
@Composable
private fun SoloCard(
    enabled: Boolean,
    level: Int?,
    canEdit: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onLevelChanged: (Int) -> Unit,
    diagnostics: SoloDiagnostics?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        colors = blockCardColors(),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            // El interruptor va en el encabezado, no en una fila propia: si no, la tarjeta
            // decía "Solo" dos veces seguidas. Ver [BlockHeader].
            BlockHeader(
                title = stringResource(R.string.amp_solo),
                checked = enabled,
                enabled = canEdit,
                onCheckedChange = onEnabledChanged,
            )
            // La misma tira que las otras cinco tarjetas, aunque hoy solo lleve un control: es
            // lo que hará que el bloque SOLO EQ entre aquí sin rediseñar nada cuando se cablee
            // (`SoloEqParams`, bloque B.1 del mismo QA).
            PagedVerticalParams(
                params = listOf(
                    VerticalParam(
                        label = stringResource(R.string.short_solo_level),
                        value = level?.toDouble(),
                        valueText = level?.toString(),
                        range = 0.0..100.0,
                        onValueChanged = { value -> onLevelChanged(value.roundToInt()) },
                    )
                ),
                canEdit = canEdit,
            )
            diagnostics?.let { DiagnosticsCard(diagnostics = it, canEdit = canEdit) }
        }
    }
}

/**
 * **La pantalla de efectos en vivo**: la segunda pantalla de dominio del bloque 6
 * (BACKLOG.md).
 *
 * Reúne las cinco tarjetas de efecto y **nada del dominio de amplificador** — ni el canal, ni el
 * modelo, ni Noise Gate, ni Contour, ni EQ, ni la cadena. Sigue exactamente el patrón que fijó
 * `AmpScreen`: el cuerpo es [EffectsSection], el mismo composable que sigue usando `SlidersPane`
 * en su sitio de siempre. No fue necesario reabrir la decisión de arquitectura de CLAUDE.md
 * §4.2 — solo aplicarla una segunda vez.
 *
 * **Todo bajo `canEdit`, sin excepciones** — a diferencia de `AmpScreen`, aquí no hay ningún
 * control que sea la excepción del contrato de Edit Mode (§4.2): eso es solo el canal, que es
 * del amplificador y no vive aquí.
 *
 * ⚠️ **Lo que deliberadamente NO trae, igual que `AmpScreen`**: guardar en canal y exportar a
 * `.tsl`. Son del preset entero, no de los efectos, y esperan a `PresetsScreen`. "Releer" sí
 * está, porque es de solo lectura y repuebla justo lo que esta pantalla enseña.
 */
@Composable
internal fun EffectsScreen(
    levels: Map<LevelId, Int?>,
    effectColors: Map<EffectId, Int?>,
    effectEnabled: Map<EffectId, Boolean?>,
    effectTypes: Map<EffectId, Int?>,
    state: UsbConnectionState,
    editMode: Boolean,
    onEditModeChanged: (Boolean) -> Unit,
    onLevelChanged: (LevelId, Int) -> Unit,
    onEffectColorChanged: (EffectId, Int) -> Unit,
    onEffectEnabledChanged: (EffectId, Boolean) -> Unit,
    onEffectTypeChanged: (EffectId, Int) -> Unit,
    boosterParams: Map<BoosterParamId, Int?>,
    boosterSoloEnabled: Boolean?,
    onBoosterParamChanged: (BoosterParamId, Int) -> Unit,
    onBoosterSoloEnabledChanged: (Boolean) -> Unit,
    delayParams: Map<DelayParamId, Int?>,
    reverbParams: Map<ReverbParamId, Int?>,
    selectors: Map<SelectorId, Int?>,
    onSelectorChanged: (SelectorId, Int) -> Unit,
    onDelayParamChanged: (DelayParamId, Int) -> Unit,
    onReverbParamChanged: (ReverbParamId, Int) -> Unit,
    reverbTime: Double?,
    onReverbTimeChanged: (Double) -> Unit,
    modChorusPreDelayLow: Double?,
    modChorusPreDelayHigh: Double?,
    onModChorusPreDelayLowChanged: (Double) -> Unit,
    onModChorusPreDelayHighChanged: (Double) -> Unit,
    modInternalRaw: Map<ModFxType, Map<String, Int?>>,
    fxInternalRaw: Map<ModFxType, Map<String, Int?>>,
    onModParamChanged: (ModFxType, String, Double) -> Unit,
    onFxParamChanged: (ModFxType, String, Double) -> Unit,
    ampSoloLevel: Int?,
    onAmpSoloLevelChanged: (Int) -> Unit,
    diagnostics: SoloDiagnostics,
    reloadInFlight: Boolean,
    onRefreshClicked: () -> Unit,
    onScanClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val availability = ShellState.availabilityOf(state, editMode)
    val canEdit = availability.canEdit

    Column(
        modifier = modifier
            .fillMaxSize()
            .knobAwareVerticalScroll()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        // ⚠️ Mismo trato que en [AmpScreen]: sin cable se explica qué falta, no se enseñan cinco
        // tarjetas grises sin motivo.
        if (availability is ControlAvailability.NoAmp) {
            NoAmpNotice(state = availability.state, onScan = onScanClicked)
            return@Column
        }

        // ⚠️ **El único GET de la pantalla** (2026-09-10): antes cada parámetro llevaba el suyo
        // dentro de la celda. Este relee el estado **entero** del amplificador de una vez —un
        // dump, no cincuenta peticiones sueltas— y por eso va arriba del todo: es lo primero que
        // se busca cuando lo que se ve en pantalla y lo que suena no coinciden. CLAUDE.md §4.10.
        //
        // Releer **no es destructivo** —solo lee— así que no cae bajo `canEdit`: aquí ya se sabe
        // que hay cable, porque sin él esta pantalla no llega.
        OutlinedButton(
            onClick = onRefreshClicked,
            enabled = !reloadInFlight,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.sm),
        ) {
            Text(
                if (reloadInFlight) stringResource(R.string.refresh_in_flight)
                else stringResource(R.string.refresh_state)
            )
        }

        // El toggle va en cada pantalla que edite: sin él, quien vea los controles apagados
        // tendría que cambiar de sección para saber por qué.
        EditModeToggle(
            editMode = editMode,
            enabled = true,
            onEditModeChanged = onEditModeChanged,
        )
        if (!editMode) EditModeNotice()

        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))

        EffectsSection(
            levels = levels,
            effectColors = effectColors,
            effectEnabled = effectEnabled,
            effectTypes = effectTypes,
            canEdit = canEdit,
            onLevelChanged = onLevelChanged,
            onEffectColorChanged = onEffectColorChanged,
            onEffectEnabledChanged = onEffectEnabledChanged,
            onEffectTypeChanged = onEffectTypeChanged,
            boosterParams = boosterParams,
            boosterSoloEnabled = boosterSoloEnabled,
            onBoosterParamChanged = onBoosterParamChanged,
            onBoosterSoloEnabledChanged = onBoosterSoloEnabledChanged,
            delayParams = delayParams,
            reverbParams = reverbParams,
            selectors = selectors,
            onSelectorChanged = onSelectorChanged,
            onDelayParamChanged = onDelayParamChanged,
            onReverbParamChanged = onReverbParamChanged,
            reverbTime = reverbTime,
            onReverbTimeChanged = onReverbTimeChanged,
            modChorusPreDelayLow = modChorusPreDelayLow,
            modChorusPreDelayHigh = modChorusPreDelayHigh,
            onModChorusPreDelayLowChanged = onModChorusPreDelayLowChanged,
            onModChorusPreDelayHighChanged = onModChorusPreDelayHighChanged,
            modInternalRaw = modInternalRaw,
            fxInternalRaw = fxInternalRaw,
            onModParamChanged = onModParamChanged,
            onFxParamChanged = onFxParamChanged,
            ampSoloLevel = ampSoloLevel,
            onAmpSoloLevelChanged = onAmpSoloLevelChanged,
            diagnostics = diagnostics,
        )
    }
}

/**
 * ⚠️ **Esto es lo único que sustituye a un test de renderizado, y no lo sustituye del todo** —
 * misma salvedad que en `AmpScreenPreview`. La prueba a mano está en BACKLOG.md, "Pendiente por
 * probar".
 */
@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun EffectsScreenPreview() {
    KTNAControlTheme {
        EffectsScreen(
            levels = AmpDomain.EFFECT_LEVELS.associateWith { 50 },
            effectColors = EffectId.entries.associateWith { 0 },
            effectEnabled = EffectId.entries.associateWith { true },
            effectTypes = EffectId.entries.associateWith { 0 },
            state = UsbConnectionState.Connected("KATANA"),
            editMode = true,
            onEditModeChanged = {},
            onLevelChanged = { _, _ -> },
            onEffectColorChanged = { _, _ -> },
            onEffectEnabledChanged = { _, _ -> },
            onEffectTypeChanged = { _, _ -> },
            boosterParams = BoosterParamId.entries.associateWith { 0 },
            boosterSoloEnabled = false,
            onBoosterParamChanged = { _, _ -> },
            onBoosterSoloEnabledChanged = {},
            delayParams = DelayParamId.entries.associateWith { 0 },
            reverbParams = ReverbParamId.entries.associateWith { 0 },
            selectors = AmpDomain.EFFECT_SELECTORS.associateWith { 0 },
            onSelectorChanged = { _, _ -> },
            onDelayParamChanged = { _, _ -> },
            onReverbParamChanged = { _, _ -> },
            reverbTime = 1.0,
            onReverbTimeChanged = {},
            modChorusPreDelayLow = 0.0,
            modChorusPreDelayHigh = 0.0,
            onModChorusPreDelayLowChanged = {},
            onModChorusPreDelayHighChanged = {},
            modInternalRaw = emptyMap(),
            fxInternalRaw = emptyMap(),
            onModParamChanged = { _, _, _ -> },
            onFxParamChanged = { _, _, _ -> },
            ampSoloLevel = 60,
            onAmpSoloLevelChanged = {},
            diagnostics = SoloDiagnostics(
                panelEnabled = null,
                panelLevel = null,
                onPanelEnabledChanged = {},
                onPanelLevelChanged = {},
                onProbeSoloPreamp = {},
                onProbeSoloPanel = {},
            ),
            reloadInFlight = false,
            onRefreshClicked = {},
            onScanClicked = {},
        )
    }
}
