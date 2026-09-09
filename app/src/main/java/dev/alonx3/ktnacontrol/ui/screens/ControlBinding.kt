package dev.alonx3.ktnacontrol.ui.screens

import dev.alonx3.ktnacontrol.device.KatanaEnumParameter
import dev.alonx3.ktnacontrol.device.KatanaParameter
import dev.alonx3.ktnacontrol.device.KatanaRepository

/**
 * Qué control del repositorio hay detrás de cada identificador de la UI.
 *
 * Son mapeos puros `(repositorio, id) → control`, sin estado ni efectos. Viven aquí, y no
 * dentro de un ViewModel, porque **hay dos pantallas que necesitan los mismos**: la de
 * diagnóstico, que habla con el amplificador, y la de edición de la Biblioteca, que habla con
 * un preset de fichero (CLAUDE.md §4.5). Los dos casos difieren únicamente en qué
 * [KatanaRepository] se les pasa.
 *
 * ⚠️ **Tenerlos en un solo sitio es lo que impide que las dos pantallas se separen.** Con una
 * copia por ViewModel, cablear un control nuevo y actualizar solo una de las dos daría una
 * pantalla que edita algo que la otra no — y sin que nada fallara al compilar.
 */
internal object ControlBinding {

    fun selector(repository: KatanaRepository, id: SelectorId): KatanaEnumParameter =
        when (id) {
            SelectorId.AMP_CATEGORY -> repository.ampCategory
            SelectorId.AMP_TYPE -> repository.ampType
            SelectorId.AMP_VARIATION -> repository.ampVariation
            SelectorId.ACTIVE_CHANNEL -> repository.channel
            SelectorId.AMP_BRIGHT -> repository.ampBright
            SelectorId.AMP_GAIN_SW -> repository.ampGainSw
            SelectorId.AMP_SOLO -> repository.ampSoloEnabled
            SelectorId.DELAY_HIGH_CUT -> repository.delayHighCut
            SelectorId.REVERB_LOW_CUT -> repository.reverbLowCut
            SelectorId.REVERB_HIGH_CUT -> repository.reverbHighCut
            SelectorId.NOISE_GATE -> repository.noiseGateEnabled
            SelectorId.CONTOUR -> repository.contourEnabled
            SelectorId.CONTOUR_SELECT -> repository.contourSelect
            SelectorId.EQ1_POSITION -> repository.eq1Position
            SelectorId.EQ2_POSITION -> repository.eq2Position
            SelectorId.CHAIN_TYPE -> repository.chainType
            SelectorId.LOOP_POSITION -> repository.loopPosition
            SelectorId.PEDAL_FX_POSITION -> repository.pedalFxPosition
        }

    fun color(repository: KatanaRepository, effect: EffectId): KatanaEnumParameter =
        when (effect) {
            EffectId.BOOST -> repository.boostColor
            EffectId.MOD -> repository.modColor
            EffectId.FX -> repository.fxColor
            EffectId.DELAY -> repository.delayColor
            EffectId.REVERB -> repository.reverbColor
        }

    fun enabled(repository: KatanaRepository, effect: EffectId): KatanaEnumParameter =
        when (effect) {
            EffectId.BOOST -> repository.boostEnabled
            EffectId.MOD -> repository.modEnabled
            EffectId.FX -> repository.fxEnabled
            EffectId.DELAY -> repository.delayEnabled
            EffectId.REVERB -> repository.reverbEnabled
        }

    fun typeControl(repository: KatanaRepository, effect: EffectId): KatanaEnumParameter =
        when (effect) {
            EffectId.BOOST -> repository.boostTypeActive
            EffectId.MOD -> repository.modTypeActive
            EffectId.FX -> repository.fxTypeActive
            EffectId.DELAY -> repository.delayTypeActive
            EffectId.REVERB -> repository.reverbTypeActive
        }

    fun level(repository: KatanaRepository, id: LevelId): KatanaParameter =
        when (id) {
            LevelId.GAIN -> repository.gainLevel
            LevelId.VOLUME -> repository.volumeLevel
            LevelId.BASS -> repository.bassLevel
            LevelId.MIDDLE -> repository.middleLevel
            LevelId.TREBLE -> repository.trebleLevel
            LevelId.REVERB -> repository.reverbLevel
            LevelId.PRESENCE -> repository.presenceLevel
            LevelId.BOOST -> repository.boostLevel
            LevelId.MOD -> repository.modLevel
            LevelId.FX -> repository.fxLevel
            LevelId.DELAY -> repository.delayLevel
        }

    fun boosterParam(repository: KatanaRepository, id: BoosterParamId): KatanaParameter =
        when (id) {
            BoosterParamId.DRIVE -> repository.boostDrive
            BoosterParamId.BOTTOM -> repository.boostBottom
            BoosterParamId.TONE -> repository.boostTone
            BoosterParamId.SOLO_LEVEL -> repository.boostSoloLevel
            BoosterParamId.EFFECT_LEVEL -> repository.boostEffectLevel
            BoosterParamId.DIRECT_MIX -> repository.boostDirectMix
        }

    fun delayParam(repository: KatanaRepository, id: DelayParamId): KatanaParameter =
        when (id) {
            DelayParamId.TIME -> repository.delayTime
            DelayParamId.FEEDBACK -> repository.delayFeedback
            DelayParamId.EFFECT_LEVEL -> repository.delayEffectLevel
            DelayParamId.DIRECT_MIX -> repository.delayDirectMix
        }

    fun reverbParam(repository: KatanaRepository, id: ReverbParamId): KatanaParameter =
        when (id) {
            ReverbParamId.PRE_DELAY -> repository.reverbPreDelay
            ReverbParamId.DENSITY -> repository.reverbDensity
            ReverbParamId.DIRECT_MIX -> repository.reverbDirectMix
        }

    fun noPanelParam(repository: KatanaRepository, id: NoPanelParamId): KatanaParameter =
        when (id) {
            NoPanelParamId.NOISE_GATE_THRESHOLD -> repository.noiseGateThreshold
            NoPanelParamId.NOISE_GATE_RELEASE -> repository.noiseGateRelease
            NoPanelParamId.CONTOUR_FREQ_SHIFT -> repository.contourFreqShift
        }
}
