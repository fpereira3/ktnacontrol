package dev.alonx3.ktnacontrol.ui.screens

import dev.alonx3.ktnacontrol.protocol.KatanaAddresses
import dev.alonx3.ktnacontrol.protocol.MemoryImage
import dev.alonx3.ktnacontrol.protocol.tsl.TslParseResult
import dev.alonx3.ktnacontrol.protocol.tsl.TslParser
import dev.alonx3.ktnacontrol.protocol.tsl.TslPreset
import dev.alonx3.ktnacontrol.protocol.tsl.TslWriter
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La edición offline de punta a punta: abrir un `.tsl`, cambiarlo con los mismos controles que
 * usa la pantalla en vivo, y volver a escribirlo (CLAUDE.md §4.5).
 *
 * Lo que de verdad se comprueba aquí es que **editar no pierde nada**: el fallo que la
 * arquitectura de bytes existe para evitar es cambiar el Gain y que se borren de paso los
 * parámetros internos de Mod, que ningún modelo de dominio del proyecto representa.
 */
class PresetEditorTest {

    private fun reference(): TslPreset {
        val text = File("../reference/FxFloorboard/default_mk2.tsl").readText()
        return (TslParser.parse(text) as TslParseResult.Parsed).presets.single()
    }

    private fun paramSet(json: String) =
        Json.parseToJsonElement(json).jsonObject["data"]!!.jsonArray[0]
            .jsonArray[0].jsonObject["paramSet"]!!.jsonObject

    private fun hex(json: String, key: String): List<String> =
        paramSet(json)[key]!!.jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun `editar el gain cambia ese valor y no toca el resto del preset`() = runBlocking {
        val original = reference()
        val before = TslWriter.write(
            MemoryImage.from(original.memory), original.name, source = original,
        ).json

        val editor = PresetEditor(MemoryImage.from(original.memory), this)
        editor.onLevelChanged(LevelId.GAIN, 77)
        delay(50)

        val after = TslWriter.write(editor.image, original.name, source = original).json

        assertEquals("77 en hex", "4D", hex(after, "UserPatch%Status")[1])
        assertNotEquals(hex(before, "UserPatch%Status"), hex(after, "UserPatch%Status"))
        // Y lo que la app no modeló sigue intacto: es la propiedad que justifica editar bytes.
        listOf("UserPatch%Fx(1)", "UserPatch%Fx(2)", "UserPatch%Eq(2)", "UserPatch%Patch_2")
            .forEach { key ->
                assertEquals("$key no debería cambiar", hex(before, key), hex(after, key))
            }
        editor.close()
    }

    @Test
    fun `lo editado se puede volver a abrir y da los mismos valores`() = runBlocking {
        val original = reference()
        val editor = PresetEditor(MemoryImage.from(original.memory), this)
        editor.onLevelChanged(LevelId.GAIN, 12)
        editor.onLevelChanged(LevelId.BASS, 34)
        editor.onSelectorChanged(SelectorId.AMP_TYPE, 0x0F)
        delay(50)

        val json = TslWriter.write(editor.image, "Editado", source = original).json
        val reopened = (TslParser.parse(json) as TslParseResult.Parsed).presets.single()
        val second = PresetEditor(MemoryImage.from(reopened.memory), this)

        assertEquals(12, second.state.value.levels[LevelId.GAIN])
        assertEquals(34, second.state.value.levels[LevelId.BASS])
        assertEquals(0x0F, second.state.value.selectors[SelectorId.AMP_TYPE])
        assertEquals("Editado", reopened.name)
        editor.close()
        second.close()
    }

    @Test
    fun `el editor arranca con los valores del fichero`() = runBlocking {
        val editor = PresetEditor(MemoryImage.from(reference().memory), this)

        // Los mismos que TslParserTest fija sobre el fichero de referencia.
        assertEquals(50, editor.state.value.levels[LevelId.GAIN])
        assertEquals(0x08, editor.state.value.selectors[SelectorId.AMP_TYPE])
        assertFalse("recién abierto no hay cambios", editor.state.value.dirty)
        editor.close()
    }

    @Test
    fun `una edicion marca el preset como sucio y guardar lo limpia`() = runBlocking {
        val editor = PresetEditor(MemoryImage.from(reference().memory), this)

        editor.onLevelChanged(LevelId.VOLUME, 5)
        assertTrue("tras editar debería estar sucio", editor.state.value.dirty)

        editor.markSaved()
        assertFalse("tras guardar debería estar limpio", editor.state.value.dirty)
        editor.close()
    }

    @Test
    fun `un preset nuevo se edita y se guarda como un tsl valido`() = runBlocking {
        val editor = PresetEditor(TslWriter.blank("Desde cero"), this)
        editor.onLevelChanged(LevelId.GAIN, 60)
        editor.onSelectorChanged(SelectorId.AMP_TYPE, 0x0B)
        delay(50)

        val json = TslWriter.write(editor.image, "Desde cero").json
        val result = TslParser.parse(json)

        assertTrue("debería ser un .tsl válido", result is TslParseResult.Parsed)
        val reopened = (result as TslParseResult.Parsed).presets.single()
        assertEquals("Desde cero", reopened.name)
        val second = PresetEditor(MemoryImage.from(reopened.memory), this)
        assertEquals(60, second.state.value.levels[LevelId.GAIN])
        assertEquals(0x0B, second.state.value.selectors[SelectorId.AMP_TYPE])
        editor.close()
        second.close()
    }

    @Test
    fun `el nombre del preset se lee del bloque 60 00 00 00`() = runBlocking {
        val editor = PresetEditor(TslWriter.blank("Mi Tono"), this)
        assertEquals("Mi Tono", editor.currentName())
        assertEquals(
            KatanaAddresses.PRESET_NAME_SIZE,
            editor.image.bytesAt(
                KatanaAddresses.CURRENT_PRESET_NAME,
                KatanaAddresses.PRESET_NAME_SIZE,
            )?.size,
        )
        editor.close()
    }

    /**
     * ⚠️ El motivo por el que el repositorio offline va con `debounceMillis = 0`.
     *
     * Con el debounce de ~100 ms que usa el camino en vivo, mover un slider y guardar acto
     * seguido escribiría el fichero **sin ese último cambio**. Este test lo fija: guardar
     * inmediatamente después de editar ya ve el byte nuevo.
     */
    @Test
    fun `guardar inmediatamente despues de editar no pierde el cambio`() = runBlocking {
        val editor = PresetEditor(MemoryImage.from(reference().memory), this)

        editor.onLevelChanged(LevelId.GAIN, 91)
        // Sin ningún delay a propósito, más allá de dejar correr la corrutina del SET.
        delay(10)
        val json = TslWriter.write(editor.image, "Rapido").json

        assertEquals("91 en hex", "5B", hex(json, "UserPatch%Status")[1])
        editor.close()
    }
}
