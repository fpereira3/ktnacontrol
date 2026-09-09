package dev.alonx3.ktnacontrol.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.alonx3.ktnacontrol.R
import dev.alonx3.ktnacontrol.protocol.ModFxType
import dev.alonx3.ktnacontrol.ui.theme.KTNAControlTheme
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
    onReadLevelClicked: (LevelId) -> Unit,
    onEffectColorChanged: (EffectId, Int) -> Unit,
    onEffectEnabledChanged: (EffectId, Boolean) -> Unit,
    onEffectTypeChanged: (EffectId, Int) -> Unit,
    boosterParams: Map<BoosterParamId, Int?>,
    boosterSoloEnabled: Boolean?,
    onBoosterParamChanged: (BoosterParamId, Int) -> Unit,
    onReadBoosterParamClicked: (BoosterParamId) -> Unit,
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
    onReadDelayParamClicked: (DelayParamId) -> Unit,
    onReverbParamChanged: (ReverbParamId, Int) -> Unit,
    onReadReverbParamClicked: (ReverbParamId) -> Unit,
    reverbTime: Double?,
    onReverbTimeChanged: (Double) -> Unit,
    onReadReverbTimeClicked: () -> Unit,
    modChorusPreDelayLow: Double?,
    modChorusPreDelayHigh: Double?,
    onModChorusPreDelayLowChanged: (Double) -> Unit,
    onReadModChorusPreDelayLowClicked: () -> Unit,
    onModChorusPreDelayHighChanged: (Double) -> Unit,
    onReadModChorusPreDelayHighClicked: () -> Unit,
    modInternalRaw: Map<ModFxType, Map<String, Int?>>,
    fxInternalRaw: Map<ModFxType, Map<String, Int?>>,
    onModParamChanged: (ModFxType, String, Double) -> Unit,
    onReadModParamClicked: (ModFxType, String) -> Unit,
    onFxParamChanged: (ModFxType, String, Double) -> Unit,
    onReadFxParamClicked: (ModFxType, String) -> Unit,
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
                onReadLevelClicked = onReadLevelClicked,
                onColorChanged = onEffectColorChanged,
                onEnabledChanged = onEffectEnabledChanged,
                onTypeChanged = onEffectTypeChanged,
                boosterParams = boosterParams,
                boosterSoloEnabled = boosterSoloEnabled,
                onBoosterParamChanged = onBoosterParamChanged,
                onReadBoosterParamClicked = onReadBoosterParamClicked,
                onBoosterSoloEnabledChanged = onBoosterSoloEnabledChanged,
                delayParams = delayParams,
                reverbParams = reverbParams,
                selectors = selectors,
                onSelectorChanged = onSelectorChanged,
                onDelayParamChanged = onDelayParamChanged,
                onReadDelayParamClicked = onReadDelayParamClicked,
                onReverbParamChanged = onReverbParamChanged,
                onReadReverbParamClicked = onReadReverbParamClicked,
                reverbTime = reverbTime,
                onReverbTimeChanged = onReverbTimeChanged,
                onReadReverbTimeClicked = onReadReverbTimeClicked,
                modChorusPreDelayLow = modChorusPreDelayLow,
                modChorusPreDelayHigh = modChorusPreDelayHigh,
                onModChorusPreDelayLowChanged = onModChorusPreDelayLowChanged,
                onReadModChorusPreDelayLowClicked = onReadModChorusPreDelayLowClicked,
                onModChorusPreDelayHighChanged = onModChorusPreDelayHighChanged,
                onReadModChorusPreDelayHighClicked = onReadModChorusPreDelayHighClicked,
                modInternalRaw = modInternalRaw,
                fxInternalRaw = fxInternalRaw,
                onModParamChanged = onModParamChanged,
                onReadModParamClicked = onReadModParamClicked,
                onFxParamChanged = onFxParamChanged,
                onReadFxParamClicked = onReadFxParamClicked,
            )
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
    onReadLevelClicked: (LevelId) -> Unit,
    onEffectColorChanged: (EffectId, Int) -> Unit,
    onEffectEnabledChanged: (EffectId, Boolean) -> Unit,
    onEffectTypeChanged: (EffectId, Int) -> Unit,
    boosterParams: Map<BoosterParamId, Int?>,
    boosterSoloEnabled: Boolean?,
    onBoosterParamChanged: (BoosterParamId, Int) -> Unit,
    onReadBoosterParamClicked: (BoosterParamId) -> Unit,
    onBoosterSoloEnabledChanged: (Boolean) -> Unit,
    delayParams: Map<DelayParamId, Int?>,
    reverbParams: Map<ReverbParamId, Int?>,
    selectors: Map<SelectorId, Int?>,
    onSelectorChanged: (SelectorId, Int) -> Unit,
    onDelayParamChanged: (DelayParamId, Int) -> Unit,
    onReadDelayParamClicked: (DelayParamId) -> Unit,
    onReverbParamChanged: (ReverbParamId, Int) -> Unit,
    onReadReverbParamClicked: (ReverbParamId) -> Unit,
    reverbTime: Double?,
    onReverbTimeChanged: (Double) -> Unit,
    onReadReverbTimeClicked: () -> Unit,
    modChorusPreDelayLow: Double?,
    modChorusPreDelayHigh: Double?,
    onModChorusPreDelayLowChanged: (Double) -> Unit,
    onReadModChorusPreDelayLowClicked: () -> Unit,
    onModChorusPreDelayHighChanged: (Double) -> Unit,
    onReadModChorusPreDelayHighClicked: () -> Unit,
    modInternalRaw: Map<ModFxType, Map<String, Int?>>,
    fxInternalRaw: Map<ModFxType, Map<String, Int?>>,
    onModParamChanged: (ModFxType, String, Double) -> Unit,
    onReadModParamClicked: (ModFxType, String) -> Unit,
    onFxParamChanged: (ModFxType, String, Double) -> Unit,
    onReadFxParamClicked: (ModFxType, String) -> Unit,
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // ⚠️ Mismo trato que en [AmpScreen]: sin cable se explica qué falta, no se enseñan cinco
        // tarjetas grises sin motivo.
        if (availability is ControlAvailability.NoAmp) {
            NoAmpNotice(state = availability.state, onScan = onScanClicked)
            return@Column
        }

        // El toggle va en cada pantalla que edite: sin él, quien vea los controles apagados
        // tendría que cambiar de sección para saber por qué.
        EditModeToggle(
            editMode = editMode,
            enabled = true,
            onEditModeChanged = onEditModeChanged,
        )
        if (!editMode) EditModeNotice()

        // Releer **no es destructivo** —solo lee— así que no cae bajo `canEdit`.
        OutlinedButton(
            onClick = onRefreshClicked,
            enabled = !reloadInFlight,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text(
                if (reloadInFlight) stringResource(R.string.refresh_in_flight)
                else stringResource(R.string.refresh_state)
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        EffectsSection(
            levels = levels,
            effectColors = effectColors,
            effectEnabled = effectEnabled,
            effectTypes = effectTypes,
            canEdit = canEdit,
            onLevelChanged = onLevelChanged,
            onReadLevelClicked = onReadLevelClicked,
            onEffectColorChanged = onEffectColorChanged,
            onEffectEnabledChanged = onEffectEnabledChanged,
            onEffectTypeChanged = onEffectTypeChanged,
            boosterParams = boosterParams,
            boosterSoloEnabled = boosterSoloEnabled,
            onBoosterParamChanged = onBoosterParamChanged,
            onReadBoosterParamClicked = onReadBoosterParamClicked,
            onBoosterSoloEnabledChanged = onBoosterSoloEnabledChanged,
            delayParams = delayParams,
            reverbParams = reverbParams,
            selectors = selectors,
            onSelectorChanged = onSelectorChanged,
            onDelayParamChanged = onDelayParamChanged,
            onReadDelayParamClicked = onReadDelayParamClicked,
            onReverbParamChanged = onReverbParamChanged,
            onReadReverbParamClicked = onReadReverbParamClicked,
            reverbTime = reverbTime,
            onReverbTimeChanged = onReverbTimeChanged,
            onReadReverbTimeClicked = onReadReverbTimeClicked,
            modChorusPreDelayLow = modChorusPreDelayLow,
            modChorusPreDelayHigh = modChorusPreDelayHigh,
            onModChorusPreDelayLowChanged = onModChorusPreDelayLowChanged,
            onReadModChorusPreDelayLowClicked = onReadModChorusPreDelayLowClicked,
            onModChorusPreDelayHighChanged = onModChorusPreDelayHighChanged,
            onReadModChorusPreDelayHighClicked = onReadModChorusPreDelayHighClicked,
            modInternalRaw = modInternalRaw,
            fxInternalRaw = fxInternalRaw,
            onModParamChanged = onModParamChanged,
            onReadModParamClicked = onReadModParamClicked,
            onFxParamChanged = onFxParamChanged,
            onReadFxParamClicked = onReadFxParamClicked,
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
            onReadLevelClicked = {},
            onEffectColorChanged = { _, _ -> },
            onEffectEnabledChanged = { _, _ -> },
            onEffectTypeChanged = { _, _ -> },
            boosterParams = BoosterParamId.entries.associateWith { 0 },
            boosterSoloEnabled = false,
            onBoosterParamChanged = { _, _ -> },
            onReadBoosterParamClicked = {},
            onBoosterSoloEnabledChanged = {},
            delayParams = DelayParamId.entries.associateWith { 0 },
            reverbParams = ReverbParamId.entries.associateWith { 0 },
            selectors = AmpDomain.EFFECT_SELECTORS.associateWith { 0 },
            onSelectorChanged = { _, _ -> },
            onDelayParamChanged = { _, _ -> },
            onReadDelayParamClicked = {},
            onReverbParamChanged = { _, _ -> },
            onReadReverbParamClicked = {},
            reverbTime = 1.0,
            onReverbTimeChanged = {},
            onReadReverbTimeClicked = {},
            modChorusPreDelayLow = 0.0,
            modChorusPreDelayHigh = 0.0,
            onModChorusPreDelayLowChanged = {},
            onReadModChorusPreDelayLowClicked = {},
            onModChorusPreDelayHighChanged = {},
            onReadModChorusPreDelayHighClicked = {},
            modInternalRaw = emptyMap(),
            fxInternalRaw = emptyMap(),
            onModParamChanged = { _, _, _ -> },
            onReadModParamClicked = { _, _ -> },
            onFxParamChanged = { _, _, _ -> },
            onReadFxParamClicked = { _, _ -> },
            reloadInFlight = false,
            onRefreshClicked = {},
            onScanClicked = {},
        )
    }
}
