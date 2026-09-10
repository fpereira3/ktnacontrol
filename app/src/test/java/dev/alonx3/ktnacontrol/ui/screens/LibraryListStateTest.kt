package dev.alonx3.ktnacontrol.ui.screens

import dev.alonx3.ktnacontrol.library.LibraryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [libraryListStateOf] (CLAUDE.md §4.7, Fase 4).
 *
 * El caso que importa de verdad es el segundo: sin él, "todavía cargando" y "ya se leyó y está
 * vacía" eran indistinguibles y el mensaje de "Biblioteca vacía" salía falsamente durante la
 * ventana entre abrir la app y que `refresh()` terminara.
 */
class LibraryListStateTest {

    private fun readable(name: String) = LibraryEntry(
        file = File(name),
        fileName = name,
        presets = listOf(fakePreset(name)),
        error = null,
    )

    private fun unreadable(name: String, reason: String) = LibraryEntry(
        file = File(name),
        fileName = name,
        presets = emptyList(),
        error = reason,
    )

    @Test
    fun `cargando y sin entradas todavia es Loading, no Empty`() {
        val state = libraryListStateOf(loading = true, entries = emptyList())
        assertEquals(LibraryListState.Loading, state)
    }

    @Test
    fun `sin cargar y sin entradas es Empty`() {
        val state = libraryListStateOf(loading = false, entries = emptyList())
        assertEquals(LibraryListState.Empty, state)
    }

    @Test
    fun `con entradas es Loaded, cargando o no`() {
        val entries = listOf(readable("a.tsl"))
        assertEquals(LibraryListState.Loaded(entries), libraryListStateOf(loading = false, entries = entries))
        // Una recarga en segundo plano (tras borrar, importar o guardar) no debe hacer
        // desaparecer una lista que ya tenía contenido real.
        assertEquals(LibraryListState.Loaded(entries), libraryListStateOf(loading = true, entries = entries))
    }

    @Test
    fun `un fichero que no parsea sigue en Loaded, con su motivo, no se filtra`() {
        val broken = unreadable("roto.tsl", "el fichero no es JSON válido")
        val state = libraryListStateOf(loading = false, entries = listOf(broken))

        assertTrue(state is LibraryListState.Loaded)
        val loaded = state as LibraryListState.Loaded
        assertEquals(1, loaded.entries.size)
        assertEquals("el fichero no es JSON válido", loaded.entries.single().error)
        assertTrue(!loaded.entries.single().isReadable)
    }

    @Test
    fun `una lista mixta conserva legibles e ilegibles en el mismo orden`() {
        val entries = listOf(readable("bueno.tsl"), unreadable("malo.tsl", "clave desconocida"))
        val state = libraryListStateOf(loading = false, entries = entries)
        assertEquals(LibraryListState.Loaded(entries), state)
    }

    private fun fakePreset(name: String) = dev.alonx3.ktnacontrol.protocol.tsl.TslPreset(
        name = name,
        memo = "",
        memory = dev.alonx3.ktnacontrol.protocol.MemoryDump.from(emptyList()),
        unavailable = emptyList(),
    )
}
