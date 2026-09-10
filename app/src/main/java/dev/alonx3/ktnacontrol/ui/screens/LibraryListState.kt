package dev.alonx3.ktnacontrol.ui.screens

import dev.alonx3.ktnacontrol.library.LibraryEntry

/**
 * **Qué mostrar en la lista de la Biblioteca**, sin ningún dato de Compose — Kotlin puro, con
 * tests JVM, siguiendo el mismo patrón que [ShellState] o [AmpDomain] (CLAUDE.md §4.7, Fase 4).
 *
 * Existe porque antes de esto **no había forma de distinguir "todavía no se leyó el disco" de
 * "se leyó y está vacía"**: `LibraryPane` solo miraba `entries.isEmpty()`, y una lista vacía por
 * las dos razones se veía exactamente igual — el mensaje "Biblioteca vacía" salía también en el
 * primer instante tras abrir la app, antes de que `LibraryViewModel.refresh()` hubiera terminado
 * de leer nada. Era falso solo durante una ventana corta, pero era falso.
 */
internal sealed interface LibraryListState {

    /** Aún no llegó el primer resultado de disco. No se sabe todavía si hay algo o no. */
    data object Loading : LibraryListState

    /** Se leyó, y no hay ningún `.tsl` importado todavía. */
    data object Empty : LibraryListState

    /**
     * Hay entradas que enseñar. **No filtra nada**: un fichero que no parsea sigue aquí dentro,
     * con su [LibraryEntry.error] — la lista lo pinta con su motivo (`library_entry_unreadable`),
     * no lo descarta en silencio. Esa parte ya existía antes de esta fase; lo que faltaba era
     * [Loading] y [Empty].
     */
    data class Loaded(val entries: List<LibraryEntry>) : LibraryListState
}

/**
 * Decide el estado a partir de lo que expone [LibraryViewModel]: si hay una carga inicial en
 * curso ([loading]) y las entradas ([entries]).
 *
 * ⚠️ **`loading` solo gana mientras la lista está vacía.** Si ya hay entradas y llega una
 * recarga en segundo plano (tras importar, borrar o guardar), la pantalla se queda con lo que
 * ya tenía en vez de parpadear a un estado de carga — parpadear ahí sería peor que no decir
 * nada, porque la lista de verdad no desaparece en ningún momento real (§4.4, "la caché no se
 * borra antes de la respuesta" es la misma idea aplicada aquí a ficheros en vez de al dump).
 */
internal fun libraryListStateOf(
    loading: Boolean,
    entries: List<LibraryEntry>,
): LibraryListState = when {
    loading && entries.isEmpty() -> LibraryListState.Loading
    entries.isEmpty() -> LibraryListState.Empty
    else -> LibraryListState.Loaded(entries)
}
