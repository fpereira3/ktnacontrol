> Archivado desde CLAUDE.md — §4.2 (decisiones de UI: partición de `SlidersPane`, su cierre, navegación sin `NavHost`), §4.6 sistema de diseño, §4.7 Fase 4, §4.9 Fase 5 y §4.10 QA con el teléfono
> Fecha de archivado: 2026-09-10. Registro fiel: copiado tal cual, sin reescribir ni corregir.

#### Cómo se parte `SlidersPane` en pantallas de dominio (decidido el 2026-09-08)

El bloque 6 del BACKLOG —"UI real de control"— arranca por `AmpScreen`, y lo primero que había
que resolver no era la pantalla sino **cómo extraerla sin romper la edición offline**: hoy
`SlidersPane` sirve a la vez a la pantalla en vivo y al editor de la Biblioteca (`offline = true`,
§4.5), y esa reutilización es deliberada — un solo juego de tarjetas para las dos cosas, en vez
de una copia que se separa de la otra cada vez que se cablea un control nuevo.

✅ **Decisión: extraer los composables del dominio de amplificador y que los llamen los dos.**
Ni pantalla nueva duplicada, ni `SlidersPane` congelado como "solo offline".

**Lo que hizo fácil la decisión fue mirar cómo está partido ya el código**: `SlidersPane` es, de
arriba abajo, *acciones de preset* → *bloque de amplificador* → *cinco tarjetas de efecto* →
`NoPanelPane`. Y resulta que **todo lo que no es la sección de efectos es dominio de
amplificador**: el canal, el modelo, los seis niveles del panel, y `NoPanelPane` entero (Noise
Gate, Contour, EQ1, EQ2, cadena). O sea que no hubo que trocear nada transversal — solo sacar el
bloque contiguo del medio a `AmpSection` y **reutilizar `NoPanelPane`, que ya era un composable
aparte desde el 2026-09-06**.

```
AmpScreen (en vivo)         SlidersPane (en vivo y offline)
   AmpSection      ←──────────  AmpSection        ← el mismo composable
   NoPanelPane     ←──────────  EffectCard ×5
                                NoPanelPane       ← el mismo composable
```

**Por qué no las otras dos opciones:**

- **Duplicar el bloque de amplificador en una `AmpScreen` propia y dejar `SlidersPane` intacto**
  se descarta por lo de siempre con las copias: dos sitios que hay que acordarse de tocar cada
  vez que se cablea un parámetro, y el proyecto tiene por delante los cientos de direcciones de
  Mod/FX. La ventaja que ofrecía —no tocar nada de lo que ya funciona— resultó no hacer falta:
  ver abajo.
- **Congelar `SlidersPane` como pantalla solo-offline hasta tener todas las pantallas de dominio**
  deja al editor de la Biblioteca sin recibir ninguna mejora de las pantallas nuevas durante todo
  el bloque 6, que no es corto. Y al final habría que reescribir el editor entero de golpe.

⚠️ **El orden de `SlidersPane` NO cambia, y eso fue un criterio explícito.** `AmpSection` es solo
el bloque contiguo del medio; `NoPanelPane` se sigue llamando **donde estaba, al final**, después
de los efectos. Se podría haber juntado todo el dominio de amplificador de un tirón, pero eso
habría reordenado una pantalla que el usuario ya usa con el amplificador delante — y reordenar la
UI no era la tarea. La señal de que la reutilización no se partió es que **ningún test existente
cambió**.

**Dónde vive cada cosa:**

| | Qué es |
| --- | --- |
| `ui/screens/AmpDomain.kt` | **Kotlin puro, sin Compose**: qué `LevelId`/`SelectorId`/`NoPanelParamId` son del dominio de amplificador y cuáles de efectos. Con tests JVM. |
| `ui/screens/AmpScreen.kt` | `AmpSection` (el bloque reutilizado) y `AmpScreen` (la pantalla en vivo). |
| `ui/screens/EffectsScreen.kt` | `EffectsSection` (las cinco tarjetas) y `EffectsScreen` (la pantalla en vivo). Segunda aplicación del mismo patrón, 2026-09-08 — sin lista nueva: usa `EffectId.entries` y `AmpDomain.EFFECT_SELECTORS`, que ya existían. |
| `DebugConnectionScreen.kt` | `SlidersPane` sigue donde estaba, ahora llamando a `AmpSection` y `EffectsSection`. |

**`AmpDomain` no es una lista decorativa**: el reparto de controles entre pantallas es
precisamente lo que se puede olvidar al añadir un parámetro nuevo —y quedaría invisible en las
dos pantallas sin que nada falle—, así que se expresa como dato y **hay un test JVM que exige que
cada `SelectorId` esté clasificado en exactamente un sitio**. Es la misma idea del
`AMP_LEVELS = LevelId.entries - efectos` que ya existía, extendida a lo demás y comprobada.

⚠️ **La cadena de efectos se queda en `AmpScreen`, no espera a una pantalla propia.** El criterio:
una pantalla de dominio tiene que tener un dominio detrás, y "la cadena" hoy son **cuatro
selectores de enrutado** (tipo de cadena, posición del loop, de EQ1 y del Pedal/FX) más un
diagrama de solo lectura — y el reordenado manual, que era lo único que la habría hecho una
pantalla de verdad, **se retiró el 2026-09-06 por no funcionar contra el amplificador**. Son
ajustes de por dónde pasa la señal *del amplificador*, no de ningún efecto concreto, así que su
sitio natural mientras tanto es con el amplificador. Si algún día `EffectsScreen` necesita
reordenar la cadena, se mueve; hasta entonces, una pantalla con un selector y un dibujo sería una
entrada de menú que no se gana su sitio.

#### El cierre: `SlidersPane` se retira entera (decidido el 2026-09-09)

Con `PresetsScreen` —la tercera pantalla de dominio— `SlidersPane` se queda sin nada que hacer, y
la pregunta era si conservarla como UI del editor offline (`offline = true`) o borrarla del todo.

✅ **Decisión: borrarla del todo.** Y no fue una decisión de gusto: **el propio código ya la había
vaciado**. Después de extraer `AmpSection` y `EffectsSection`, el cuerpo de `SlidersPane` quedó así:

```kotlin
if (!offline) { EditModeToggle …; divider }
if (!offline) { Guardar preset · Exportar · Releer; diálogos; selector de canal }
AmpSection(…)        ← siempre
EffectsSection(…)    ← siempre
NoPanelPane(…)       ← siempre
```

O sea: **la rama offline es exactamente esos tres composables en fila**, y todo lo demás está tras
un `if (!offline)`. `SlidersPane` había dejado de ser una pantalla con contenido para ser *una
cáscara de chrome en vivo alrededor de tres piezas que ya se reutilizan por separado*. Lo que la
justificaba —compartir las tarjetas entre lo de en vivo y lo offline— hoy lo hacen `AmpSection`,
`EffectsSection` y `NoPanelPane` directamente.

**Por qué no conservarla solo para el editor offline** (la opción de menos churn):

- El parámetro `offline` es un booleano que hace que **un composable signifique dos cosas**, y con
  el modo en vivo retirado dejaría de tener un segundo valor: se quedaría permanentemente en
  `true`, con la mitad del cuerpo muerto y sin forma de notarlo.
- **El llamador offline paga un peaje que solo existe por la firma compartida.** Para editar un
  fichero hay que pasarle `state = UsbConnectionState.Idle`, `editMode = false`,
  `onEditModeChanged = {}`, `presetSaveInFlight = false`, `onSavePreset = { _, _ -> }`,
  `diagnostics = OFFLINE_DIAGNOSTICS` (un objeto entero de callbacks que nunca se llaman) y una
  docena de `onRead… = {}` — argumentos inertes cuyo único propósito era satisfacer a la mitad en
  vivo. Al llamar a los tres composables directamente, **todo eso desaparece**, incluido el
  `OFFLINE_DIAGNOSTICS` que existía solo para rellenar un hueco.

El editor offline pasa a tener su propio cuerpo, `PresetEditorBody`, **privado y junto a su único
llamador** (`PresetEditorScreen`, en `LibraryPane.kt`). No se pone en `ui/screens/` junto a
`AmpSection`: lo que merece vivir suelto es lo que se **reutiliza**, y esas tres piezas ya están
ahí; un composable con un solo llamador pertenece al lado de ese llamador.

⚠️ **Y esto sí borra código en vez de moverlo**: se van el `offline: Boolean`, las tres ramas
`if (!offline)`, `OFFLINE_DIAGNOSTICS` y ~60 parámetros de firma. La señal de siempre —**ningún
test hizo falta tocarlo**— se mantiene, y aquí vale doble: si algún test hubiera dependido de
`SlidersPane`, este borrado lo habría roto. Ninguno lo hacía, porque lo que se prueba en JVM es
`PresetEditor` (el estado) y `AmpDomain` (el reparto), no el árbol de Compose.

**El menú queda en cuatro entradas**: Logs · Amplificador · Efectos · Presets. "Sliders" y
"Biblioteca" desaparecen — la primera porque su contenido está repartido entre las dos pantallas
de dominio, la segunda porque es la mitad de abajo de Presets.

| | Qué es |
| --- | --- |
| `ui/screens/PresetsScreen.kt` | `PresetsScreen` (guardar en canal + exportar + la Biblioteca entera) y el diálogo de guardado, traído desde `DebugConnectionScreen`. |
| `ui/screens/LibraryPane.kt` | La mitad "Biblioteca" de esa pantalla, sin cambios de comportamiento; su editor ahora usa `PresetEditorBody` en vez de `SlidersPane`. |
| ~~`SlidersPane`~~ | **Borrada.** Su contenido vive en `AmpSection` + `EffectsSection` + `NoPanelPane`. |

##### Cómo se distingue "en vivo" de "Biblioteca" dentro de `PresetsScreen`

Juntar las dos cosas en una pantalla es exactamente donde se podían mezclar sin querer: arriba hay
botones que **escriben en el amplificador ahora mismo** y abajo una lista de **ficheros del
teléfono**. La separación es deliberada y tiene cuatro señales, no una:

1. **Dos secciones con encabezado y subtítulo propios**, y el subtítulo dice sobre qué actúa cada
   una ("el amplificador conectado ahora mismo" contra "ficheros `.tsl` de este teléfono").
2. **Un divisor entre ambas**, para que no se lean como una lista de botones continua.
3. ⚠️ **Reglas de habilitación distintas y visibles, que es la señal que no miente**: la mitad de
   arriba se apaga sin cable y —para guardar— sin Edit Mode, con su aviso; la de abajo **funciona
   con el cable desenchufado**, que es justo su razón de ser (§4.5). La asimetría no es estética:
   es la diferencia real entre escribir en hardware y leer ficheros.
4. **Abrir un preset ocupa la pantalla entera** y el bloque en vivo desaparece: dentro de un
   preset, la única acción hacia el amplificador es "Enviar al amplificador…", que trata de *ese*
   preset y ya tiene sus dos confirmaciones.

⚠️ **"Releer" no se lleva a `PresetsScreen`**, aunque estuviera en la misma fila de botones de
`SlidersPane`. Repuebla la caché de controles, o sea **lo que enseñan `AmpScreen` y
`EffectsScreen`** —y las dos ya lo tienen—; aquí no hay ningún control que refrescar. Y exportar no
lo necesita: `KatanaRepository.exportImage` lee su propio dump completo, no la caché.

#### La navegación: por qué NO hay `NavHost` (decidido el 2026-09-09)

Fase 2 de la UI: shell de verdad —barra superior con el estado de conexión, barra inferior con
las tres pantallas de dominio, Logs degradado a entrada secundaria—. La pregunta previa era si
adoptar Navigation Compose o seguir con el `when` sobre un enum de sección.

✅ **Decisión: seguir sin `NavHost`.** Scaffold + `NavigationBar` que setea el mismo enum, más un
`BackHandler` para el back. **Sin dependencia nueva.**

**El argumento que lo decidió no es "es más barato", es que el detalle de un preset no es estado
de ruta.** La ventaja principal de la opción con `NavHost` era plantar la vista de detalle/editor
de la Biblioteca como su propia ruta con back del sistema. Pero lo que hay que "navegar" ahí es
un `LibraryViewModel.EditingSession`, y dentro lleva un `PresetEditor` que **posee un
`KatanaRepository` vivo sobre un `OfflineKatanaLink`, creado con `viewModelScope`** (§4.5). Un
argumento de ruta es **dato serializable**; esto es un objeto vivo con su propio scope de
corrutinas. Convertirlo en ruta deja dos salidas, las dos peores que lo que ya hay:

1. **Dos fuentes de verdad** para "qué preset está abierto" —la ruta y el ViewModel—, que es
   justo la clase de duplicación que este proyecto evita en todas partes.
2. **Mantener el ViewModel igual** y usar la ruta solo como disparador: el mismo `when` de ahora
   con un `NavController` encima.

⚠️ **Y hay un pie de banco concreto**: `viewModel()` dentro de un `NavBackStackEntry` se scopea
**por ruta**, así que la lista y el editor recibirían `LibraryViewModel` **distintos** salvo que
se scopee explícitamente al padre. Fallaría en silencio exactamente donde compartir importa —el
editor abierto dejaría de verse desde la lista— y el síntoma sería "a veces se pierde lo que
estaba editando". Es un riesgo real a cambio de una ventaja que resultó no aplicar.

**Lo que sí se toma de la opción con `NavHost`, sin adoptarla**: el comportamiento del back, con
un `BackHandler` y ~10 líneas.

| Gesto | Qué hace |
| --- | --- |
| Back con un preset abierto | lo cierra y vuelve a la lista (con confirmación si hay cambios sin guardar) |
| Back en una pestaña que no es la de inicio | vuelve a **Amplificador**, la de inicio |
| Back en Amplificador | no se consume: cierra la app, como espera cualquiera |

Es el mismo comportamiento que un `NavHost` con `popUpTo(startDestination)`, escrito a mano
porque son tres ramas — y **vive en `ShellNavigation`, Kotlin puro con tests JVM**, en vez de
dentro de un composable donde no se podría probar.

**Cuándo reabrir esto**: si aparecen deep links de verdad (abrir un `.tsl` desde el gestor de
archivos es un `Intent`, no una ruta), si los destinos pasan de un puñado, o si hiciera falta
un back stack independiente por pestaña. Hoy son tres destinos y una entrada secundaria.

##### Qué ve cada pantalla según la conexión, en una función probada

Hasta ahora `canEdit = connected && editMode` estaba escrito **tres veces** —`AmpScreen`,
`EffectsScreen` y el bloque en vivo de `PresetsScreen`— y sin cable los controles se quedaban
grises sin decir por qué. Las dos cosas se arreglan con el mismo cambio: `ShellState`
(`ui/screens/ShellState.kt`, **Kotlin puro, sin Compose**) tiene la única definición:

```
availabilityOf(state, editMode) →  NoAmp(state)      no hay cable: se explica qué falta
                                   NeedsEditMode     hay cable, falta Edit Mode: controles grises + aviso
                                   Ready             se puede editar
```

⚠️ **`NoAmp` deja de ser "todo gris"**: la pantalla enseña **qué falta y qué hacer** —buscando,
esperando permiso, no encontrado, fallo— con el botón de buscar a mano. Un panel gris sin
explicación se lee como un bug; una frase lo convierte en una regla.

⚠️ **La Biblioteca no entra en esto y es deliberado** (§4.5): funciona desenchufada, que es su
razón de ser. Lo que sí recibe el mismo trato es el bloque **en vivo** de `PresetsScreen`
(guardar en canal, exportar), que sin cable no puede hacer nada.

##### Cambios sin guardar al cambiar de pestaña

⚠️ **No se pierden, y no por suerte.** La sesión de edición vive en `LibraryViewModel`, no en el
composable, porque tiene que sobrevivir a la recomposición para poder poseer su repositorio
(§4.5). Consecuencia: irse a Efectos y volver a Presets **reencuentra el editor tal cual**, con
su marca de "Cambios sin guardar". El criterio elegido es **no tocar eso**: preservar en silencio
es lo que menos sorprende, y avisar al cambiar de pestaña convertiría un gesto barato en un
diálogo.

**El único camino que descarta trabajo es "Volver" dentro del editor**, y por eso **ese sí
pregunta** cuando hay cambios sin guardar (el back del sistema hace lo mismo, que para el usuario
es el mismo gesto). Sin esa confirmación la app sería incoherente: preserva al navegar y borra al
volver, sin decirlo.

**Notas de `android.hardware.usb` concretas para este proyecto:**

- Requiere OTG/USB host. En el manifest hace falta `android.hardware.usb.host`; el
  `<uses-feature android:name="android.software.midi">` **ya no aplica** y hay que quitarlo.
- El `device_filter` de `USB_DEVICE_ATTACHED` sigue siendo válido y ahora es la vía natural
  para lanzar la app al conectar el amplificador. Falta rellenarlo con el vendor-id /
  product-id reales (en **decimal**).
- Secuencia de apertura: `UsbManager.deviceList` para encontrarlo →
  `requestPermission()` si aún no se tiene (**diálogo del sistema, es asíncrono, llega por
  `PendingIntent`**; es un paso que con MIDI no existía) → `openDevice()` →
  `claimInterface(interfaz 3, force = true)` → `bulkTransfer()` sobre `0x03` y `0x84`.
- `bulkTransfer()` es **bloqueante**: va en `Dispatchers.IO`, nunca en el hilo principal. La
  lectura es un bucle propio con timeout que alimenta un `Flow<ByteArray>`; no hay callback
  como el que daba `MidiReceiver`.
- `bulkTransfer()` devuelve el número de bytes transferidos, o `-1` en error o timeout. Un
  timeout sin datos es normal en el bucle de lectura y **no** es una desconexión: solo hay
  que tratarlo como "nada que leer".
- Liberar siempre: `releaseInterface()` y `close()` al desconectar o al destruir el
  `ViewModel`, y atender `USB_DEVICE_DETACHED`.
- **`UsbDevice.getInterfaceCount()` no es `bNumInterfaces`.** Android crea un `UsbInterface`
  por cada par (interfaz, alternate setting): el Katana declara 4 interfaces pero devuelve 7.
  Para contar interfaces reales hay que quedarse con los `id` distintos. Por lo mismo, buscar
  la interfaz de control por `id == 3` **y** `alternateSetting == 0`; filtrar solo por `id`
  puede devolver el alt 1, que es interrupt y ni siquiera tiene el endpoint `0x84`.



---

### 4.6 El sistema de diseño: chasis oscuro con un acento (decidido el 2026-09-09)

Fase 3 de la UI. Hasta aquí el tema era **la plantilla de Android Studio sin tocar** —los
`Purple80`/`Pink40` recién generados— y encima `dynamicColor = true`, así que en el teléfono de
pruebas (API ≥ 31) **ni siquiera se usaba esa paleta**: los colores salían del fondo de pantalla.
La app no tenía tema, tenía lo que Android le pusiera.

✅ **Decisión: paleta oscura tipo chasis de equipo, con un solo acento ámbar-naranja.** Y no es
una preferencia estética: es lo que hace legible el punto siguiente.

**El argumento que la decidió es el color de los efectos.** Cada efecto tiene tres *slots* de
color —verde, rojo, amarillo— y eso es **un hecho del dispositivo**, no una elección de diseño
(§5.2): son los colores que el propio panel enciende. Si la app va a teñir la tarjeta de cada
efecto con su color activo, esos tres tonos tienen que leerse como **señal**, y para eso
necesitan un fondo neutro y de poca saturación. Una paleta Material 3 sembrada con un ámbar (la
opción B) tiñe **todas** las superficies de un marrón-ámbar tonal: un punto amarillo sobre una
superficie ámbar deja de ser un punto amarillo. Grafito neutro es el único fondo que no compite
con los tres.

Lo secundario, pero cierto: esta app se usa con el amplificador delante, muchas veces en una sala
a media luz, y una pantalla blanca a 300 nits en ese contexto molesta.

⚠️ **La app es siempre oscura: no sigue el ajuste claro/oscuro del sistema, y es deliberado.**
`KTNAControlTheme` perdió su parámetro `darkTheme`. El motivo es el mismo de arriba: los tres
colores de slot se eligieron para contrastar contra grafito, y una versión clara obligaría a
tener dos juegos afinados por separado —o a aceptar que en modo claro el amarillo desaparece—.
Un panel de instrumento no cambia de color con la hora del día. **Lo que se pierde**: quien tenga
el teléfono en modo claro verá esta app oscura igualmente. Es el precio, y se paga a sabiendas.

⚠️ **`dynamicColor` se retira, no se deja en `false`.** Dejar el parámetro sería dejar una vía
para que el fondo de pantalla vuelva a decidir la paleta, que es exactamente el fallo que esto
corrige: un esquema derivado del wallpaper puede poner una superficie amarillenta justo debajo de
un slot amarillo, y no hay forma de preverlo. Un diseño donde tres tonos concretos tienen que
seguir siendo inequívocos no puede delegar su paleta.

#### La colisión entre el acento y los slots, que es el problema real

El acento naranja (`#FF7A1A`) cae **entre** el rojo (`#FF3B30`) y el amarillo (`#FFD426`) de los
slots. Es el riesgo que trae elegir un acento cálido, y no se resuelve moviendo el tono: cualquier
naranja está entre los dos.

✅ **Se resuelve por la forma, no por el tono.** Los colores de slot y el acento **nunca aparecen
en el mismo rol**:

| | Dónde aparece | Con qué forma |
| --- | --- | --- |
| Acento (`primary`) | sliders, chips seleccionados, botones, indicador de pestaña | relleno de un control |
| Color de slot | tarjeta del efecto | **franja de 4 dp en el borde izquierdo** + punto junto al nombre |

Y encima, **el color del slot nunca depende solo del tono**: el `ChipSelector` de la tarjeta
sigue diciendo «Verde / Rojo / Amarillo» con palabras. El tinte es un refuerzo de lo que ya está
escrito, no la única vía para saberlo. Es lo que hace que la ambigüedad naranja/rojo/amarillo no
llegue a importar.

⚠️ **`error` también es rojo**, y comparte tono con el slot rojo. Misma respuesta: `error` solo
tiñe **texto** de aviso, nunca una franja ni un punto.

#### Los tres colores de slot: qué es hecho del dispositivo y qué es elección

Hay que separarlos, porque el encargo pedía confirmar los colores en `reference/` "porque es un
hecho del dispositivo":

- **Hecho del dispositivo** (documentado, ver abajo): que el parámetro `60 00 06 39`–`06 3D` vale
  `00` = **verde**, `01` = **rojo**, `02` = **amarillo**.
- **Elección de diseño** (mía, sin fuente): los hex concretos. Ninguna fuente dice qué verde
  enciende el LED del panel, y no la habría: es luz, no un `#RRGGBB`. Se eligieron tres tonos de
  LED legibles sobre grafito — `#3ECF5C`, `#FF3B30`, `#FFD426`.

##### La correspondencia valor→color: `midi.xml` se contradice a sí mismo y pierde

Esta era la única duda real, y se resolvió sin amplificador.

`reference/FxFloorboard/midi.xml` dice **las dos cosas**:

| Dónde | Qué dice |
| --- | --- |
| `midi.xml:43961-43963` (el `<DATA value="39">`, el propio parámetro de color) | `00` RED · `01` GREEN · `02` YELLOW |
| `midi.xml:50863-50865` (bloque de conversión) | lo mismo: `desc="00"` RED, `01` GREEN, `02` YELLOW |
| `midi.xml:43567/43592/43617` (los slots de tipo `06 24`/`25`/`26`) | `06 24` = **GREEN**, `06 25` = RED, `06 26` = YELLOW |

Las dos primeras filas dicen `00` = rojo; la tercera implica lo contrario en cuanto se cruza con
el modelo de §5.2 ("el tipo activo refleja el slot del color encendido").

✅ **El desempate sale de un fichero real, y es 5 de 5.** Sobre
`reference/FxFloorboard/default_mk2.tsl`, los cinco efectos tienen el color en `00`, y en los
cinco el **tipo activo** coincide con el del **primer** slot (`06 24`/`27`/`2A`/`2D`/`30`), el que
`midi.xml` etiqueta GREEN — con los otros dos slots en valores distintos, así que no es
casualidad:

| Efecto | slot G | slot R | slot Y | tipo activo | coincide con |
| --- | --- | --- | --- | --- | --- |
| Booster | `0A` | `0B` | `0E` | `0A` | **verde** |
| Mod | `1D` | `23` | `24` | `1D` | **verde** |
| FX | `15` | `00` | `27` | `15` | **verde** |
| Delay 1 | `00` | `07` | `08` | `00` | **verde** |
| Reverb | `04` | `05` | `03` | `04` | **verde** |

Y lo corrobora una observación independiente: `Adresses.txt:72-74` anota, para el amplificador de
su autor, `60 00 00 11: [0A|0B|0E]` como boost **verde/rojo/amarillo** — los mismos tres bytes en
el mismo orden que las tres columnas de arriba. Más
`katana-midi-bridge/parameters/color_assign.json`, que da `[0,1,2]` → `green, red, yellow`.

**Tres testigos contra una tabla que se contradice sola.** `EffectColor` ya codificaba
`GREEN(0x00), RED(0x01), YELLOW(0x02)`: no había que cambiar nada, pero ahora está justificado en
vez de heredado. ⚠️ Queda **una mirada al panel** como confirmación —poner el color en verde
desde la app y ver qué LED se enciende— en BACKLOG.md, "Pendiente por probar". No bloquea nada:
si estuviera al revés, lo que cambia es el orden de tres constantes.

#### La paleta, la tipografía y el espaciado, en un solo sitio

`ui/theme/Color.kt` es **la única lista de colores del proyecto**; ningún composable escribe un
`Color(0x…)`. Ya era cierto antes de esta fase —no había un solo literal de color en
`ui/screens/`— y sigue siéndolo.

| Rol | Hex | Qué es |
| --- | --- | --- |
| `background` | `#0E0F11` | el fondo del chasis |
| `surface` | `#16181B` | barra superior, barra inferior, tarjetas |
| `surfaceVariant` | `#232629` | pistas de slider, discos de perilla, avisos |
| `outline` | `#3A3F44` | bordes y controles apagados |
| `primary` | `#FF7A1A` | **el acento**: lo que se puede tocar |
| `secondary` | `#8FA0AD` | acero frío, para lo secundario que no compite |
| `error` | `#FF6B60` | solo texto de aviso |

**Tipografía: la de la plataforma, y a propósito.** Ninguna fuente propia — meter un `.ttf`
sería un asset más que mantener y §6 pide justificar cada añadido; y **copiar una de
`reference/` está prohibido** (§7). Lo que sí se define es la **escala entera** en `Type.kt`
(antes solo estaba `bodyLarge`, o sea que el resto eran los tamaños por defecto de Material sin
que nadie los hubiera mirado): tamaños algo más compactos, porque estas pantallas son densas, y
`titleSmall` con `letterSpacing` de 0.6 sp, que es lo que da el aire de etiqueta serigrafiada de
un panel sin necesidad de otra familia.

⚠️ **`Spacing` (`ui/theme/Spacing.kt`) es nuevo y es la fuente única del espaciado**: `xs` 4,
`sm` 8, `md` 12, `lg` 16, `xl` 24 dp. **No se ha propagado a todo el proyecto**, y eso es una
decisión, no un despiste: hay ~108 literales `.dp` repartidos, y la mayoría son tamaños de
componente (el diámetro de una perilla, la altura de una barra de EQ) que **no son espaciado** y
no pertenecen a esta escala. Se aplicó donde esta fase tocaba —las tres pantallas de dominio, el
shell y la tarjeta de efecto— y el resto se irá pasando cuando se toque, no en un barrido.

⚠️ **`themes.xml` también cambia, y hace falta**: su padre era
`android:Theme.Material.Light.NoActionBar`, o sea que **la ventana era blanca antes de que
Compose pintara nada**. Con una app siempre oscura eso es un fogonazo blanco en cada arranque.
Pasa a `Theme.Material.NoActionBar` con `windowBackground` igual al `background` del chasis.

#### El ícono y el nombre

✅ **Ícono vectorial propio, dibujado a mano en `res/drawable/`**, sin un solo byte de
`reference/` (§7 y la instrucción explícita de esta tarea: los colores no son un problema,
cualquier asset visual sí).

Es **una perilla**: un arco de 270° con el hueco abajo y una aguja, en el ámbar del acento, sobre
el grafito del chasis con unas líneas de rejilla tenues. El barrido de 270° empezando en 135° no
es decorativo: es **exactamente el mismo recorrido que dibuja `KnobDial`** en la app
(`KNOB_START_DEGREES` / `KNOB_SWEEP_DEGREES`), así que el ícono es literalmente el control que
hay dentro.

⚠️ **Los `.webp` de `mipmap-*dpi` (el robot verde de la plantilla) se quedan y no se usan.** Con
`minSdk 26` el `mipmap-anydpi` adaptativo gana siempre, así que son inertes; borrarlos movería el
recuento de `UnusedResources` de la baseline de lint por un cambio que no aporta nada. Si algún
día molestan, ahí están.

✅ **El nombre se queda en «KTNA Control»**, que es lo que ya decía `app_name`. Se consideró
«Katana Control», que se lee mejor de primeras — y se descarta: **Katana es una marca de Boss**,
esta app no es oficial (§1: proyecto personal, sin ánimo de lucro), y ponerle el nombre del
producto a una app de terceros invita a confundirla con una de Boss. La abreviatura es
deliberada, no un capricho de programador.

**Qué NO cambió, y es la señal de que esto fue una fase visual**: ni un test, ni una firma de
composable de lógica, ni un byte de `protocol/` o `device/`. Los tests siguen en 433 y ninguno
hizo falta tocarlo — igual que en las tres fases anteriores.

### 4.7 Fase 4: estados de la Biblioteca y consistencia de los diálogos destructivos (decidido el 2026-09-09)

Sin amplificador. Dos partes: qué enseña la Biblioteca cuando no está en el caso feliz, y si los
tres flujos que escriben algo sin poder deshacerlo comparten un componente o solo un estilo.

#### La decisión chica: un solo componente para dos de los tres, no para los tres

`PresetsScreen` guarda en canal, `LibraryPane` manda al amplificador y `LibraryPane` guarda/
guarda-como en la Biblioteca. Antes de tocar nada había que mirar **qué forma tiene cada uno**,
no asumir que las tres son la misma cosa con ropa distinta.

✅ **Guardar en canal y enviar al amplificador tienen exactamente la misma forma: un primer paso
que junta datos (nombre+canal, o la revisión de qué bloques se omiten) y un segundo paso que
solo confirma, sin pedir nada nuevo.** Ese segundo paso —título, cuerpo en tono de aviso, botón
"Sí, `<verbo>`", botón "Atrás"— es idéntico salvo las palabras, así que se extrae a
`DestructiveConfirmDialog` (`ui/screens/Controls.kt`), y los dos lo llaman en vez de construir
su propio `AlertDialog`.

⚠️ **Guardar/Guardar como NO entra en esa extracción, y no por descuido: es de un solo paso, y
seguirlo siendo es la decisión.** No hay una "primera pantalla" separada de la confirmación —el
campo de nombre y el aviso de sobrescritura viven en el mismo `AlertDialog`
(`PresetNameDialog`)— así que forzarlo a la forma de dos pasos habría significado **añadirle un
paso que hoy no tiene**, cambiando comportamiento a cambio de una consistencia que no hacía
falta. La razón de fondo es la misma que ya separa el bloque en vivo de la Biblioteca en
`PresetsScreen` (§4.2, "Cómo se distingue"): sobrescribir un fichero de la app pisa un preset que
el usuario tenía guardado, pero **no escribe en hardware sin confirmación del amplificador**, que
es la cautela que de verdad exige la segunda confirmación de los otros dos.

**Lo que sí se homologa en `PresetNameDialog`, sin tocar su forma**: cuando de verdad va a
sobrescribir un fichero existente, el botón dice **"Sí, sobrescribir"** en vez de "Guardar" — el
mismo patrón "Sí, `<verbo>`" que ya usaban los otros dos (`preset_save_confirm_action`,
`library_send_confirm_action`) — y el aviso lleva el mismo margen (`Spacing.xs`) y el mismo color
(`MaterialTheme.colorScheme.error`) que ellos. Creando un preset nuevo ("Guardar como" sin
fichero de origen) el botón se queda en "Guardar": ahí no se pisa nada, y ponerle "Sí, guardar"
sería un aviso sin motivo.

**Por qué no las otras dos opciones:**

- **Un componente único para los tres** se descartó por lo de arriba: `PresetNameDialog` no
  tiene un "paso 1" y un "paso 2" separables sin inventar uno. Forzarlo habría sido la clase de
  cambio de comportamiento que la tarea pedía evitar.
- **Reskinnear los tres por separado, sin extraer nada**, era la opción de menos riesgo pero
  dejaba el problema real sin resolver: guardar en canal y enviar al amplificador **son la misma
  pantalla escrita dos veces**, y la próxima vez que cambie el tono de un aviso destructivo habría
  que acordarse de tocar los dos sitios. Con `DestructiveConfirmDialog` solo hay un sitio.

#### Los cuatro estados de la Biblioteca

1. **Lista vacía** — ya existía (`library_empty`), pero **no distinguía "vacía de verdad" de
   "todavía no se leyó el disco"**: las dos deshabilitaban `entries.isEmpty()`. Es el fallo real
   que esta fase corrige, no un estado nuevo de adorno.
2. ✅ **Carga inicial — nueva, `LibraryListState.Loading`.** `LibraryViewModel` gana `loading:
   StateFlow<Boolean>`, en `true` hasta que `refresh()` (llamado una vez, en `init`) termina su
   primera lectura. `libraryListStateOf(loading, entries)` — Kotlin puro, con tests JVM, en
   `ui/screens/LibraryListState.kt` — decide entre `Loading`/`Empty`/`Loaded` a partir de los dos.
   ⚠️ **`loading` solo gana mientras la lista está vacía**: si ya hay entradas y llega una recarga
   en segundo plano (tras importar, borrar o guardar), la pantalla no parpadea a "Cargando…" —
   se queda con lo que ya tenía. El mensaje elegido, "Cargando…", sigue el mismo patrón corto que
   ya usaba "Importando…" para la otra operación de disco.
3. **Un `.tsl` que no parsea** — ya estaba resuelto, y se confirma en esta fase en vez de
   reabrirse: `PresetLibrary.read()` nunca lanza, guarda el `LibraryEntry` con su `error` y
   `LibraryEntryCard` lo lista con `library_entry_unreadable`, sin descartarlo. `libraryListStateOf`
   no filtra nada —un `Loaded` lleva legibles e ilegibles igual— y hay un test que lo fija:
   `libraryListStateOf` no es solo "vacío o no", es "lo que hay, tal cual viene".
4. ✅ **Bloques `DISPUTED` sin cargar, ahora también en el editor.** `TslPreset.unavailable` ya
   existía y ya se enseñaba en la vista de solo lectura (`PresetDetail`, con `UnavailableSection`)
   y en la revisión previa al envío (`SendReviewDialog`, Hecho 2026-09-08). Lo que faltaba era
   **el editor** (`PresetEditorScreen`): ahora `session.source?.unavailable` se pinta con el mismo
   `UnavailableSection`, justo bajo el nombre del preset — quien edita ve la misma lista que vería
   al mirar el fichero o al mandarlo, en vez de enterarse recién al intentar enviarlo.

**Por qué la lógica de estado no vive en Compose**: `libraryListStateOf` es una función pura que
recibe `loading` y `entries` y devuelve un `sealed interface`, sin `@Composable` ni nada de
`androidx.compose.*`. Es el mismo patrón que `ShellState.availabilityOf` (§4.2) — la decisión se
prueba en JVM, la Composable solo pinta lo que le dicen.

**Qué no cambió, y es la señal de que esto se quedó dentro de alcance**: ni `PresetSendFlow`, ni
`PresetEditor`, ni el árbol de Compose de `LibraryPane`/`PresetsScreen` cambiaron de
comportamiento — solo de aspecto y de qué se enseña cuando antes no se enseñaba nada. Ningún test
existente hizo falta tocarlo.

### 4.9 Fase 5: pase de accesibilidad y calidad mínima (decidido el 2026-09-09)

Sin amplificador, la última de las cinco fases del plan de UI (§4.2, "El shell", §4.6, §4.7).
**No reabre el tema oscuro fijo de la Fase 3** — sigue siendo el único tema, sin variante clara
— es sobre ese único tema: `contentDescription`, contraste, área táctil y escala de fuente.

#### 1. `contentDescription`: la auditoría encontró cero íconos reales

⚠️ **El hallazgo real de este punto no es "qué íconos les falta descripción", es que la app no
tiene ningún ícono.** Barrido explícito de `Icon(`, `IconButton(`, `Image(` y `contentDescription`
en todo `ui/`: **cero coincidencias**, en las cuatro. Es consecuencia directa de la decisión de
la Fase 2 (§4.2, "La navegación") de no añadir `material-icons-core`: cada sitio que en otra app
tendría un pictograma —la barra de navegación, los botones de acción— tiene aquí un `Text` con
la palabra escrita. Eso ya es, por definición, lo que `contentDescription` intenta conseguir:
que TalkBack tenga algo que leer.

Con eso descartado, lo único visual sin texto propio en los sitios que pedía la tarea es el
**punto y la franja de color de `EffectCard`** (CLAUDE.md §4.6):

- **El punto** (`Box` con `.background(slotColor)`) se marca explícitamente decorativo con
  `Modifier.clearAndSetSemantics {}`: el nombre del efecto va en texto justo al lado, y el color
  en sí ya se lee con palabras en el `ChipSelector` de abajo («Verde»/«Rojo»/«Amarillo»).
  Duplicarlo con una descripción sería TalkBack leyendo el mismo color dos veces en la misma
  tarjeta.
- **La franja** no necesitó la misma marca: se pinta con `drawBehind` sobre el `Modifier` de la
  columna de contenido, no con un composable propio, así que nunca tuvo un nodo de semántica que
  limpiar — son solo píxeles, invisibles para TalkBack desde antes de esta fase.

⚠️ **`DestructiveConfirmDialog` no llevaba ningún ícono que auditar** (§4.7): es título + cuerpo
+ dos botones de texto, la misma conclusión que el resto de la app.

#### 2. Contraste: calculado, no estimado — y ningún color de slot se movió

✅ **`contrastRatio` (`ui/theme/Contrast.kt`), Kotlin puro sin `Color` ni `android.*`** (mismo
criterio que `protocol/`, §6), implementa la fórmula del propio estándar WCAG 2 —luminancia
relativa por canal + `(L1+0.05)/(L2+0.05)`—, no una aproximación. `Color.hex` (`Color.kt`) es el
único puente hacia `Color` de Compose, para poder citar el número real en el KDoc de la paleta
y fijarlo en `ContrastTest`. Los resultados completos, con los dos umbrales usados
(**texto ≥ `4.5:1`** WCAG AA; **componente/objeto gráfico no textual ≥ `3:1`** WCAG 1.4.11 —
la franja+punto de `EffectCard` y el relleno de acento de perillas/sliders contra su pista),
están en el KDoc de cabecera de `Color.kt`.

✅ **Los once pares reales de la paleta cumplen su umbral, el más ajustado con margen de sobra**
(el rojo de slot, `5.01:1` contra el `3:1` que le toca). **Ningún color se movió.**

⚠️ **La decisión explícita que pedía la tarea —frenar y documentar si algún ajuste obligaba a
mover un color de slot fuera de lo fijado como hecho del dispositivo— no hizo falta tomarla,
y eso también se documenta**: no es que se evitara el conflicto, es que no lo hubo. Los tres
colores de slot ya se habían elegido por legibilidad sobre grafito en la Fase 3 (§4.6); este
cálculo solo lo confirma con un número verificable en vez de una intuición del que los eligió.

⚠️ **`EffectSlotColors.Unknown` (`= ChassisOutline`, `1.67:1`) queda fuera de la tabla de
cumplimiento a propósito, y no es una excepción que se cuela**: es el gris que se pinta cuando
el amplificador **todavía no ha dicho** de qué color está el slot, así que su bajo contraste es
el punto — WCAG 1.4.11 exige contraste a lo que transmite información, y `Unknown` transmite
justamente la ausencia de ella. Exigirle `3:1` sería pedirle que llamara la atención por algo
que no sabe.

#### 3. Área táctil: un solo sitio hecho a mano, y ya no depende de un efecto lateral

✅ **Todo lo demás de la app usa componentes de Material 3 de fábrica** (`Button`,
`OutlinedButton`, `TextButton`, `Switch`, `FilterChip`, `NavigationBarItem`), que desde hace
varias versiones garantizan un mínimo de **48×48 dp** de área táctil por su cuenta —
`LocalMinimumInteractiveComponentEnforcement`, activado por defecto y **nunca desactivado en
este proyecto** (comprobado por grep: cero referencias)—, sin importar el tamaño visual del
control. Con la Compose BOM `2026.02.01` que usa el proyecto (§3) ese comportamiento es el
vigente. No hizo falta tocar ni un botón ni la barra de navegación.

⚠️ **El único `Modifier.clickable` hecho a mano de todo el proyecto** —la fila de un preset
dentro de `LibraryEntryCard`, en `LibraryPane.kt`— es el hallazgo real de este punto. Antes de
la Fase 5, su altura **dependía de un efecto lateral**: el `TextButton` de «Editar» que vive
dentro de la misma fila ya empuja esa fila a 48 dp por su propia garantía de Material 3, así que
el área táctil terminaba cumpliendo el mínimo, pero **por casualidad de qué hubiera al lado, no
por diseño**. Se le añadió `Modifier.heightIn(min = 48.dp)` explícito (`MIN_TOUCH_TARGET`,
documentado en el propio fichero) para que el mínimo sea cierto aunque cambie el contenido de al
lado en el futuro.

⚠️ **El otro `combinedClickable` del proyecto** (`ConsoleLog`, Logs) se descartó tras mirarlo:
cubre el panel de consola entero para el gesto de mantener-pulsado-para-copiar, así que su área
táctil ya es enorme por construcción — no hay nada que agrandar.

⚠️ **Perillas y sliders quedan fuera, como pedía la tarea**: `KnobControl` y `VerticalBarControl`
tienen su propio tamaño por diseño (`KNOB_SIZE`/`KNOB_SLOT_WIDTH`/`barHeight`, `Controls.kt`),
pensado para el gesto de arrastre, no para un toque puntual — auditarlos con la misma regla del
48 dp habría sido aplicar el criterio equivocado a un control que no es de ese tipo.

#### 4. Escala de fuente del sistema: auditado, sin el patrón que se buscaba

✅ **`Type.kt` usa `.sp` en `fontSize`/`lineHeight`/`letterSpacing` en los diez estilos, sin una
sola excepción** — así se escribió ya en la Fase 3 (§4.6), y este barrido lo vuelve a comprobar
en vez de darlo por sentado. Barrido de `fontSize`/`lineHeight`/`letterSpacing`/`TextStyle(` en
todo `ui/`: la única definición fuera de `Type.kt` es el `fontSize = 11.sp` de `ConsoleLog`
(el monoespaciado del log), y ya está en `.sp`. **El patrón que pedía buscar la tarea —`.dp`
donde iría `.sp`— no aparece en ningún sitio del proyecto.**

⚠️ **Y la distinción contraria importa igual de explícita**: los `.dp` que sí existen por todas
partes —tamaños de perilla, espaciado, anchos de franja— están bien como `.dp` y **no deberían**
pasar a `.sp`. La preferencia de tamaño de letra del sistema (`fontScale`) solo tiene que mover
el texto; que también estirara el diámetro de una perilla o el ancho de una franja de color
sería el error contrario al que se buscaba, y estos valores nunca dependieron de una fuente para
empezar.

#### Qué no cambió

Ni un test existente, ni comportamiento: `clearAndSetSemantics {}` no cambia qué se pinta,
`heightIn(min = 48.dp)` no cambia qué pasa al tocar la fila, y el cálculo de contraste no movió
ningún color. Los 13 tests nuevos de `ContrastTest` son los únicos añadidos de esta fase; los
puntos 1, 3 y 4 son cambios directos sin lógica que probar en JVM, así que se dan por buenos con
la compilación y el `@Preview`/la instalación en el teléfono — igual que el resto de la Fase 5,
lo único que TalkBack y el tamaño de fuente del sistema pueden confirmar de verdad es un
dispositivo real (BACKLOG.md, "Pendiente por probar").

**Con esto se cierran las cinco fases del plan de UI sin amplificador** (Fase 1: pantallas de
dominio, §4.2 "Cómo se parte"; Fase 2: shell y navegación, §4.2 "La navegación"; Fase 3: sistema
de diseño, §4.6; Fase 4: estados de la Biblioteca y diálogos destructivos, §4.7; Fase 5: este
punto). Lo que queda de todo el bloque 6 es exclusivamente lo que necesita hardware.

### 4.10 QA con el teléfono y el amplificador: tres bugs y un rediseño (2026-09-09)

Primer reporte de QA hecho **en el teléfono real, con el amplificador conectado**, después de
cerrar las cinco fases de UI. Encontró tres bugs, pidió una investigación documental y abrió el
rediseño visual. Solo el punto A.1 necesitó el amplificador para *encontrarse*; arreglarlo, no.

#### A.1 La variación solo funcionaba en Crunch, y no había forma de apagarla

**Síntomas**: el switch salía bloqueado en Acoustic/Clean/Lead/Brown, y una vez puesta una
variación no se podía volver al modelo base.

⚠️ **La tabla de modelos no tenía nada malo.** `midi.xml:37311-37341` da los cinco `[CANAL]` y
los cinco `Var [Canal]` exactamente como ya estaban en `AmpCategory`. El fallo era que **la misma
pregunta se contestaba con tres fuentes distintas**, repartidas por la UI:

| Quién preguntaba | Qué miraba | Qué salía mal |
| --- | --- | --- |
| el switch, para pintarse | `60 00 06 5C` | es el **LED**, y es de solo lectura |
| `ampVariationApplies` | el modelo `60 00 00 21` | se apagaba con cualquier *sneaky amp* activo |
| `onAmpVariationChanged` | la perilla `60 00 06 50` | podía escribir el canal de otra categoría |

De ahí los dos síntomas, y ninguno era lo que parecía:

1. **El bloqueo no era "en cuatro canales", era "con un sneaky amp puesto".** `Pro Crunch`,
   `VO Lead` y compañía no tienen gemelo `Var [...]`, así que `AmpType.category` daba null y el
   switch se apagaba. Crunch "funcionaba" solo porque ahí el modelo era el `[CRUNCH]` pelado.
2. **El camino de apagado existía; lo que no llegaba era el gesto.** El switch se pintaba desde
   `06 5C`, que reporta el **botón físico**: al cambiar la variación escribiendo el modelo ese LED
   no se mueve, así que el control se quedaba visualmente en OFF y el siguiente toque volvía a
   mandar `onCheckedChange(true)` — encender otra vez, para siempre.

✅ **La corrección es un solo sitio: `AmpVariation` en `protocol/`, Kotlin puro y con tests.**
`categoryOf` mira **primero el modelo y cae a la perilla** —que siempre está en una de las cinco
posiciones, así que la variación siempre tiene a qué referirse—; `isOn` deriva el estado **del
modelo**, que es justo lo que la app acaba de escribir; y `modelFor` da el byte a mandar en las
**dos** direcciones. `AmpVariationUi` empaqueta las dos respuestas juntas, para que no puedan
volver a separarse. **Los diez casos —encender y apagar en cada canal— están en
`AmpVariationTest`**, más los dos síntomas escritos como regresiones.

⚠️ **El precio, dicho explícitamente**: con un sneaky amp activo, tocar la variación **cambia el
modelo** (de `Pro Crunch` a `[CRUNCH]` o `Var [Crunch]`). Es lo que hace el botón VARIATION del
panel —la variación es propiedad de la posición de la perilla, no del modelo suelto— pero conviene
saberlo: la alternativa era dejar el switch muerto en cuanto hubiera un sneaky, que es el bug que
se estaba arreglando.

#### A.2 El diagrama de la cadena: no se actualizaba y sobraba un bloque

Dos fallos independientes bajo el mismo síntoma.

**El valor crudo de cada cadena no estaba en ninguna fuente que el proyecto usara.** `midi.xml`
da `06 20` como un escueto `range 00/06/00/06` — siete valores **sin un solo nombre**—, y ni
`Adresses.txt` ni los YAML ni `katana-midi-bridge` mencionan la cadena del Mk2.

✅ **Sale de `reference/FxFloorboard/floorBoard.cpp:490-599`**: siete funciones `chain_N_Set()`,
cada una escribiendo su valor en `Structure 06 00 20` y acto seguido las veinte ranuras, con los
nombres de los botones en `floorBoardDisplay.cpp:158-177`. ⚠️ **El orden crudo no es el que
sugieren los nombres**: las dos familias van seguidas (`2-1 3-1 4-1`, luego `2-2 3-2 4-2`), no
intercaladas. Está en `ChainPreset`, con las veinte ranuras de cada una transcritas y un
`require` que rechaza una transcripción que no sea permutación completa.

✅ **Y cuadra con Boss Tone Studio a la primera**, que es el oráculo que el reporte aportó: las
siete secuencias coinciden bloque a bloque, incluido que **Booster y Mod intercambian** cuál va
primero entre familias y que **AMP es lo único que se desplaza** de fila en fila. Dos fuentes
independientes —código de un editor de PC y la pantalla del editor oficial— de acuerdo en las
siete.

⚠️ **`FXLOOP` se retira del vocabulario del diagrama.** Mientras se dibujaba, **ninguna** de las
siete coincidía con BTS. No es que el loop no exista: es un **punto de inserción**, no un bloque
de tono, y tiene su propio selector (`06 21`). El diagrama pasa de ocho bloques a siete.

⚠️ **`diagramSequence` corta en `CAB`.** El diagrama sustituye el cabinet por un `SPEAKER` fijo al
final, así que todo lo que el array pusiera *después* del cabinet se dibujaba *antes* de ese
`SPEAKER` — afirmando lo contrario de lo que dice el amplificador. Con las siete cadenas de
fábrica no se veía porque tras `CAB` solo quedan bloques que no se dibujan: era cierto **por
casualidad, no por diseño**. Ahora el corte es explícito y hay un test.

✅ **Y el diagrama vuelve a leerse al cambiar de cadena**, que era el "no cambia nunca". Elegir una
cadena reescribe `06 00`–`06 13` dentro del amplificador y nada volvía a leerlas. Se resuelve
**reusando el coordinador de recargas** (`ReloadRequest.ChainChanged`) en vez de inventar un
camino nuevo — que es la lección cara de §4.4.

⚠️ **Y ahí apareció un bug que el test destapó antes que el hardware.** A diferencia del canal
—que vive **fuera** del dump y se repuebla con un GET de respaldo al final—, `06 20` está
**dentro** del dump y se repuebla **a mitad** de la recarga. Sin guard, esa repoblación hacía que
`collectLatest` **cancelara la recarga en vuelo** y lanzara otra: un dump de más en cada conexión,
con el primero tirado a medio aplicar. El trigger se apaga mientras `_reloadInFlight` está en
true. Lo que se pierde: un cambio de cadena hecho *durante* una recarga no dispara la suya; es
recuperable con el botón de refresco y mucho más barato que el bucle que evita.

⚠️ **NO se reabrió el reordenado manual del array de veinte**, retirado el 2026-09-06 por no
comportarse contra el amplificador. Solo se arregló el diagrama, que es de lectura.

#### A.3 La Biblioteca no aparecía: `weight(1f)` puede medir cero

El reporte decía "no se ve / no se encuentra". Estaba, y bien cableada: `PresetsScreen` es una
entrada primaria de la barra y renderiza `LibraryPane`. **El fallo era de medición.**

`PresetsScreen` era una `Column` con el bloque en vivo arriba y `LibraryPane` abajo con
`Modifier.weight(1f)`. En una `Column` de Compose, un hijo con `weight` recibe **lo que sobra**
después de medir a los que no la tienen — y si no sobra nada, recibe **altura cero**. El bloque en
vivo no tenía tope ni scroll propio (encabezado + subtítulo + toggle de Edit Mode + su aviso + dos
botones + el aviso de "falta Edit Mode"), así que en una pantalla corta o con la escala de fuente
grande se quedaba con todo el alto. Y como el contenedor de fuera **tampoco hacía scroll**, no
había forma de llegar a la lista: ni verla ni desplazarse a ella.

✅ **La corrección es un solo contenedor con scroll**: `LibraryPane` gana una ranura `header` y el
bloque en vivo se pinta **dentro** de su columna. Así empuja la lista hacia abajo en vez de
borrarla. Las cuatro señales que separan "esto escribe en hardware" de "esto son ficheros" (§4.2)
no cambian; y el `browsingLibrary` del llamador desaparece, porque `LibraryPane` ya vuelve antes
de pintar la ranura cuando hay un preset abierto.

⚠️ **No se pudo reproducir sin el dispositivo**, así que queda en "Pendiente por probar" — pero el
mecanismo es cierto por construcción, no una hipótesis: un `weight` sin espacio sobrante mide cero.

#### B.1 SOLO EQ: encontrado, con dos testigos, y deliberadamente **sin cablear**

✅ **`60 00 0F 10`–`0F 19`, diez parámetros**: Position, Off/On, Low Cut, Low Gain, Mid Freq,
Mid Q, Mid Gain, Hi Gain, Hi Cut y Level. Está en `SoloEqParams`, con la tabla completa.

**Confianza `midi.xml` ×2**, el nivel más alto que da una sola fuente (§5): aparece en la **tabla
de destinos de asignación** (`midi.xml:3432-3441`, con el par `desc`/`customdesc` = 3.er/4.º byte
que el proyecto ya verificó contra tres direcciones confirmadas por audio) **y** en el bloque
`<Structure>` (`midi.xml:49929-50024`), con sus rangos. Las dos coinciden en las diez direcciones
y en el orden. **No hay `DISPUTED` aquí**: no hay dos fuentes que se contradigan, hay una que se
confirma por dos vías y ninguna que la niegue.

✅ **Y de paso cierra un cabo suelto del `.tsl`**: `60 00 0F 10`–`0F 25` es exactamente
`UserPatch%Patch_Mk2V2`, los 22 bytes que §5 tenía anotados como *"añadidos del firmware 2"* **sin
desglosar**. Son este SOLO EQ (10 bytes) más un SOLO DELAY (12, `0F 1A`–`0F 25`, localizado pero
no extraído). Eso explica también por qué ninguna otra fuente lo menciona: TuxKatana no llegó a
ese bloque y `katana-midi-bridge` es MK1.

⚠️ **Las cuatro ganancias y el Level son de paso fraccionario**: `range 00/30/-12.0/+12.0 dB` son
**0,5 dB por paso**, la escala del EQ **gráfico** de EQ1/EQ2 y **no** la entera del paramétrico
(`00/28/-20/+20`). Confundirlas daría el doble de recorrido con la mitad de resolución.

⚠️ **No se registra como control, y es una decisión con número.** Las diez direcciones caen
**fuera del dump** (que llega a `60 00 0E 43`), igual que los Contour por slot. `loadFromDump`
recupera lo que el dump no cubre con **un GET individual en serie**, hasta 800 ms cada uno. Hoy
hay **seis** controles así —y CLAUDE.md ya documenta que eso vale hasta 4,8 s por recarga si esa
región no contesta, con un test que **fija ese número en seis**. Registrar estos diez lo llevaría
a **dieciséis**, o sea ~12,8 s en el peor caso, **en cada conexión y cada cambio de canal**. Es
una regresión peor que la ausencia del control. Desbloquearlo es una decisión aparte y ya
identificada en §5: ampliar el rango del dump para cubrir `60 00 0F xx`, o paralelizar los GET de
respaldo. Ninguna se hace a ciegas antes de saber si esa región contesta.

⚠️ **De paso, el desempate `6.00k` / `6.30K` va 4 a 1.** `SoloEqParams` es el **cuarto** sitio
independiente de `midi.xml` que dice `6.00k` en `0x0A`, contra el único que dice `6.30K`
(`DelayHighCutFrequency`). No cambia la decisión de mantener los dos enums separados —sigue sin
haber prueba de hardware— pero el peso de la evidencia ya no está repartido.

#### C — El rediseño: qué se hizo y qué se decidió aplazar

⚠️ **Este bloque va a medias a propósito, con permiso explícito del encargo.** Lo que se hizo son
las piezas con lógica que se puede probar y los cambios acotados; lo que falta es el rediseño de
sliders, que toca las quince tarjetas a la vez y merece su propia sesión. El detalle está en
BACKLOG.md.

##### ✅ Solo pasa a ser una tarjeta de `EffectsScreen` — **opción B**

El encargo ofrecía dos caminos: (A) volverlo un "pseudo-efecto" que implemente lo mismo que
Booster/Mod/FX/Delay/Reverb, o (B) una tarjeta distinta al final de la lista.

**Se elige B, y por un motivo de datos, no de estética**: la forma de Solo **es distinta de
verdad**, no solo más pobre.

| | Los cinco efectos | Solo |
| --- | --- | --- |
| Slot de color verde/rojo/amarillo | sí, es lo que guarda el tipo | **no existe** |
| Catálogo de tipos | sí, de 7 a 30 según el efecto | **no existe** |
| Nivel | sí, escala `Off + 1..101` | sí, pero directa `0..100` |

Meterlo por `EffectId` obligaría a que ese enum admitiera una entrada **sin color y sin tipo**, y a
que `EffectCard` creciera ramas `if (effect == SOLO)` justo en el sitio que hoy es regular para los
cinco — pagar complejidad en el camino común para ahorrar una tarjeta de veinte líneas. `EffectId`
se queda con cinco entradas y los tests que cuentan cinco siguen valiendo.

⚠️ **Lo que sí cambia es el dominio, y eso es dato con test**: `AMP_SOLO` se mueve de
`AmpDomain.SELECTORS` a `AmpDomain.EFFECT_SELECTORS`. El criterio es el de siempre —**qué trata el
control, no dónde estaba**—: Solo no ajusta el amplificador, es un realce conmutable con su propio
nivel y su propio EQ; en la pantalla de amplificador era un huérfano entre Gain y Bass. Y de uso:
Solo se pisa a la vez que el Booster, no a la vez que el modelo. La tarjeta de diagnóstico del
Solo se muda con él, que es de lo que trata.

##### ✅ AMP TYPE / SNEAKY AMPS: dos páginas, con `HorizontalPager`

Lo que había —chips de categoría **más** un desplegable de los treinta modelos **más** el switch en
su propia fila— tenía dos problemas: el desplegable escondía veinte modelos tras un toque y un
scroll, y los cinco canales aparecían **dos veces**, como chip y como entrada del desplegable, sin
que nada dijera que eran lo mismo.

| Página | Qué ofrece | Switch de variación |
| --- | --- | --- |
| `AMP TYPE` | los **cinco canales** de la perilla | **sí**, botón redondo en la fila del título |
| `SNEAKY AMPS` | los **veinte** individuales, rejilla de 3 | **no existe** — ni gris ni oculto |

✅ **`HorizontalPager` de `androidx.compose.foundation.pager`, y no añade dependencia**:
`foundation` ya está en el classpath por Material 3, así que §6 se respeta sin discusión. La
alternativa era un `Row` con `horizontalScroll` y un `snapFlingBehavior` a mano, o sea
reimplementar el mismo componente peor.

⚠️ **La rejilla de veinte es `Row`s a mano, no `LazyVerticalGrid`**, y no por gusto: vive dentro de
una columna que ya hace scroll vertical, y anidar dos scrolls en el mismo eje es un error de
medición en Compose. Con veinte elementos fijos, trocear en filas de tres cuesta una línea.

⚠️ **Qué modelo cae en qué página es dato, no árbol de Compose**: `AmpModelPage`, Kotlin puro con
tests, derivado de `AmpType.category` en vez de una lista a mano. Un modelo que se quedara fuera de
las dos páginas **no daría error**, solo sería inelegible para siempre — exactamente lo que hay que
probar en JVM. Y **los cinco `Var` no se ofrecen como chip**: son el switch. Con `Var [Crunch]`
activo el chip marcado es **Crunch**.

##### ✅ Barra de navegación y títulos

- **Las tres entradas miden lo mismo.** Antes solo la activa llevaba la pastilla del indicador, así
  que la seleccionada se veía más grande y la barra "saltaba" al cambiar de pestaña. Ahora todas
  llevan la misma caja —mismo ancho mínimo, alto y borde— y lo único que cambia con la selección es
  el color. El borde visible en las tres es además lo que las hace leer como botones de panel.
- **Los títulos de sección van en mayúscula**, y la transformación está en `SectionHeader`, **no en
  `strings.xml`**: ponerlo en el recurso obligaría a gritar en cada cadena y las dejaría
  inservibles para otro uso. Con `Locale.ROOT`, porque es presentación y no lengua — con el locale
  del dispositivo el turco convertiría la `i` en `İ`.

##### ✅ Sliders verticales con paginado por tarjeta — cerrado el 2026-09-09

Con esto se cierra el bloque C entero, y con él el QA. Lo que sigue es el registro de las tres
decisiones que había que tomar y de lo que se rompió por el camino.

**El componente que faltaba es `PagedControls`** (`ui/screens/Controls.kt`), y la evaluación
previa acertó: `VerticalBarControl` ya existía y el gesto de arrastre ya estaba resuelto en
`KnobControl`; lo que no había era el **contenedor**. Reimplementar
`detectDragGesturesAfterLongPress` y `knobValueAt` por tarjeta habría sido lo contrario de lo que
pedía el análisis.

La cadena de piezas queda así:

| | Qué es |
| --- | --- |
| `ControlPaging` | **Kotlin puro, con tests**: cuántas columnas caben y qué controles van en cada página |
| `PagedControls` | el contenedor: `HorizontalPager` + puntos de página, genérico sobre el widget |
| `VerticalParam` | un parámetro continuo como **dato** (etiqueta, valor, rango, callbacks) |
| `PagedVerticalParams` | los dos de arriba juntos, que es lo que usan las tarjetas |

⚠️ **`VerticalParam` es lo que hizo barata la conversión de las once tarjetas.** Cada bloque
—Booster, Delay, Reverb, Noise Gate, Contour, los 31 tipos de Mod/FX— construye una `List` con
los datos que ya tenía y no sabe nada de páginas. Sin ese paso intermedio, cada tarjeta habría
tenido su propia versión del mismo `Row` con su propio reparto: exactamente las quince copias que
la evaluación quería evitar. Los constructores (`boosterVerticalParams`, `delayVerticalParams`, …)
son `@Composable` que **devuelven** una lista en vez de emitir UI — legal mientras no emitan, y lo
único que permite leer `stringResource` ahí dentro.

###### ⚠️ Decisión 1: cómo conviven el paginado y el arrastre del control

Era la pregunta obligatoria, y con motivo: el gesto de las perillas ya fue inutilizable una vez
por vivir dentro de un contenedor con scroll (§4.6 y el KDoc de `KnobControl`). Un pager es el
mismo tipo de contenedor. **Confirmado que conviven, por dos mecanismos que se refuerzan**, no por
suponerlo:

1. **Separación en el eje, y para la perilla también en el tiempo.**
   - `KnobControl` arrastra en horizontal **pero solo tras mantener pulsado**; el pager arranca con
     un arrastre horizontal **inmediato**. No compiten por el mismo evento: soltar-y-arrastrar
     pagina, mantener-y-arrastrar ajusta. Es la separación más fuerte de las dos porque **no
     depende de la dirección que tome el dedo** — la intuición del encargo era correcta.
   - `VerticalBarControl` arrastra en vertical y el pager en horizontal. Compose lo resuelve por
     *touch slop*: gana quien cruce primero el umbral **en su propio eje**, y quien gana consume el
     puntero. Es el caso ortogonal estándar, no una carrera.
2. **Y el pager se congela mientras se ajusta**, que es lo que el eje **no** resuelve. Es el mismo
   agujero que ya documentaba `KnobInteraction` para los scrolls: reclamar el puntero impide que el
   contenedor *robe* el arrastre, pero el dedo sigue apoyado encima y una desviación horizontal a
   media cuenta pasaría de página con el control a medio mover. `userScrollEnabled = !adjusting` lo
   cierra.

⚠️ **Para que el punto 2 funcionara, `VerticalBarControl` tuvo que empezar a avisar a
`KnobInteraction`**: era el **único control con arrastre del proyecto que no participaba**. Nunca
se había notado porque solo vivía en el EQ gráfico, dentro de un `Row` con scroll horizontal
mientras él arrastraba en vertical — otra vez ortogonal, otra vez cierto por casualidad. Lleva
además el mismo `DisposableEffect` de seguro que `KnobControl`, para que una barra que desaparezca
a media pulsación no deje el pager congelado para siempre.

⚠️ **Lo que esto no demuestra**: que se *sienta* bien. Que el umbral de la pulsación no moleste al
pasar de página, o que la barra no se dispare con un roce, solo lo dice un dedo sobre el cristal.

###### ⚠️ Decisión 2: cuántos controles por página — **derivado del ancho**, no fijo

Las dos opciones eran válidas y se elige derivarlo, porque **un número fijo son dos experiencias
distintas según el dispositivo**:

| Ancho útil | Con 4 fijos | Derivado |
| --- | --- | --- |
| ~250 dp (móvil estrecho, letra grande) | 62 dp por control: apretado | 3 |
| ~330 dp (móvil normal) | 82 dp: bien | 4 |
| ~600 dp (horizontal o tablet) | 4 y media pantalla vacía, **con páginas que no hacían falta** | 6 |

La última fila es la que decide: con un número fijo, un EQ de 11 bandas se pagina en 3 páginas
**incluso en una pantalla donde caben todas**. Y derivarlo no cuesta incertidumbre — el ancho ya lo
mide el contenedor (`BoxWithConstraints`), así que la cuenta es determinista y está probada.

Los topes también son decisión: **mínimo 3** respeta el suelo del encargo y evita que un ancho muy
pequeño deje una tira de 2 que ya no se lee como tira (se fuerza aunque no quepa: las celdas
encogen con `weight` en vez de multiplicar las páginas justo donde más cuesta navegarlas).
**Máximo 6** para que una pantalla ancha no convierta la tira en un muro de controles diminutos.

###### ⚠️ Decisión 3: qué va a la tira y qué se queda fuera

**Los parámetros continuos van a la tira; los selectores no.** Un `Wave: SAW/SQUARE` o un
`Mode: Picking/Auto` se eligen **leyendo**, no ajustando al oído, y una barra vertical de dos
posiciones sería una forma peor de decir lo mismo. Se quedan como chips o desplegable debajo, igual
que estaban.

⚠️ **La excepción deliberada es el EQ paramétrico**, donde las frecuencias y la Q **siguen en
perilla**: ahí el barrido es el gesto correcto —es lo que hace Boss Tone Studio y lo que hace el
aparato físico— así que convertirlas en barras habría cambiado un gesto acertado por consistencia
con la tarjeta de al lado. Lo que sí gana es el paginado, que era el problema que compartía con las
barras. `ParametricEqKnobs` usa `PagedControls` con `KnobControl` dentro: el contenedor es genérico
justo para esto.

⚠️ **Y esto reordena las tarjetas respecto de la fuente.** Antes cada parámetro salía en el orden
de `midi.xml`; ahora los continuos van juntos arriba y los selectores debajo. Es a propósito: el
orden del XML es el del **mapa de direcciones**, no el que agrupa lo que se toca igual.

###### Otras decisiones del pase

- **Una sola tira por tarjeta, con el nivel del efecto el primero.** El nivel es un parámetro
  continuo más de ese efecto; sacarlo aparte lo habría convertido en una excepción visual cuyo
  único motivo era ser el que ya estaba.
- **Los tres Freq Shift del Contour van juntos en una tira**, no uno debajo de otro con su
  encabezado: son el mismo parámetro repetido tres veces y lo que se quiere es **compararlos**.
- **Nombres cortos nuevos (`short_*` en `strings.xml`) en vez de reutilizar los `*_level`.**
  Aquellos llevan el valor dentro ("Drive: 42") y una celda de 80 dp solo tiene sitio para el
  nombre. Quitarles el valor a mano dejaba restos ("Time:  s" en los que llevan unidad), así que
  son dos recursos distintos porque son dos textos distintos. ⚠️ **No se reutilizó `logName`**, que
  habría salido gratis: es una cadena hardcodeada en Kotlin y §6 exige que los textos de UI vivan
  en `strings.xml`.
- **El botón de "leer" sobrevive, dentro de la celda.** Es el motivo de que el ancho de celda
  subiera de 44 a 80 dp. `VerticalParam.onRead` es **nullable** y no un booleano porque no todos
  los controles tuvieron nunca uno —las bandas del EQ gráfico se cablearon sin él—, así que null
  conserva exactamente el comportamiento de antes control por control.
- **Pase visual**: las tarjetas del panel (Noise Gate, Contour, EQ1, EQ2, cadena) y la de Solo
  pasan a tener el mismo encabezado que las de efecto (`BlockHeader`), los mismos colores
  (`blockCardColors`) y `Spacing` en vez de literales. ⚠️ Poner el interruptor **en el encabezado**
  quita una duplicación real: la tarjeta decía "Noise Gate" en el título y otra vez en su switch.
- **Puntos de página solo con más de una página**, y **la última página se rellena con huecos**:
  con 11 controles de 4 en 4, los 3 de la última se repartirían el ancho entero y quedarían al
  triple de tamaño que los de las páginas anteriores.

###### Lo que se borró

⚠️ **`FractionalLevelControl` y `knobAwareHorizontalScroll` se van, no se quedan "por si acaso".**
El primero se quedó sin usuarios cuando Reverb Time y los Pre Delay del 2x2 Chorus pasaron a la
tira (un `VerticalParam` ya lleva `Double`). El segundo tenía exactamente dos usuarios —las filas
con scroll del EQ gráfico y del paramétrico— y los dos son ahora `PagedControls`: **un pager no es
un scroll**, lee `LocalKnobInteraction.adjusting` por su cuenta. Dejar el helper habría sido una
invitación a reintroducir el scroll libre que el paginado vino a sustituir.

###### Qué se probó y qué no

✅ **`ControlPagingTest`, 11 tests**: los topes de columnas, el techo de la división, y la
comprobación que de verdad importa —**cada control cae en exactamente una página**, verificada
sobre 16 tamaños × 4 anchos de página—. Más los casos reales del proyecto: ninguna tarjeta pasa de
3 páginas en un móvil normal.

⚠️ **La sensación del gesto no se puede confirmar sin dispositivo**, y es lo único que queda
(BACKLOG.md, "Pendiente por probar"): que pasar de página no dispare una perilla, que mantener
pulsado no se sienta lento, y que las tiras de 3-4 se lean bien con el amplificador delante.

✅ **Ningún test existente hizo falta tocarlo** — los 498 anteriores siguen pasando tal cual. Es la
señal de siempre: lo que se prueba en JVM es la lógica (`ControlPaging`, `AmpDomain`, `knobValueAt`)
y no el árbol de Compose, así que un rediseño visual no debería moverlos, y no los movió.

##### Un solo GET por pantalla, arriba del todo (2026-09-10)

Hasta aquí **cada parámetro llevaba su propio botón de "leer"** dentro de la celda —el `GET` que
hacía un `read()` de esa única dirección—. Eran, contados, **catorce callbacks distintos** cableados
desde el `ViewModel` hasta el último slider. Se retiran todos y queda **uno por pantalla**, el que
ya existía (`Releer del amplificador`), movido al principio de la columna.

⚠️ **Lo que decide que esto no pierde nada es que el GET global es un superconjunto del individual,
no un sustituto aproximado.** El botón dispara `loadFromDump()` (§4.4), que relee el bloque
`60 00 00 00` entero y aplica el resultado control por control; y **lo que el dump no cubre —los
Contour por slot— ya cae solo en el GET individual de respaldo** que ese mismo camino hace. O sea:
un toque repuebla todo lo que catorce botones repoblaban de uno en uno, incluido lo de fuera del
dump. Un dump son ~275 ms; cincuenta GET en serie, hasta 800 ms **cada uno**.

Lo que se va, y por qué cada cosa:

| Qué | Por qué |
| --- | --- |
| El botón dentro de la celda de [VerticalBarControl] | era el motivo de que la celda midiera 80 dp; ahora la tira respira |
| El de [LevelControl] | mismo caso, en el slider horizontal |
| `LocalReadButtonsVisible` | **nunca tuvo un `Provider`**: se escribió para esconder los botones offline y quedó siempre en `true`, así que offline se pintaban igual — con un `onRead = {}` que no hacía nada |
| 14 parámetros `onRead…Clicked` y 13 funciones del `ViewModel` | sin llamador; ~190 líneas de firma y ~200 de implementación |
| El `onReadPanelClicked` de `SoloDiagnostics` | el mismo argumento: los `ProbeRow` de esa tarjeta ya leen de vuelta por su cuenta (`WriteProbe.verdict`), y el nivel candidato está **dentro** del dump |
| `R.string.debug_connection_read_level` (`"GET"`) | sin usuarios |

⚠️ **Y arregla de paso un botón muerto en el editor offline**: `PresetEditorBody` recibía los
catorce callbacks como `{}` (§4.2, "El cierre" ya lo señalaba como peaje de la firma compartida),
así que la Biblioteca enseñaba botones de "leer del amplificador" que **no podían hacer nada** —no
hay amplificador, se edita un fichero—. Ahora no hay ninguno.

✅ **El botón global aparece solo donde hay amplificador que releer**, y no por una condición nueva:
vive en [AmpScreen] y [EffectsScreen] —las pantallas en vivo—, y `PresetEditorBody` **no las usa**,
usa `AmpSection` + `EffectsSection` + `NoPanelPane` (§4.2). La misma frontera que ya separaba lo de
en vivo de lo offline decide esto sin un `if` de más. Dentro de la pantalla en vivo tampoco hace
falta más guarda: sin cable se sale antes por `ControlAvailability.NoAmp`.

⚠️ **No cae bajo `canEdit`, y es deliberado**: leer no es escribir. Con Edit Mode apagado los
controles siguen grises pero el GET funciona — que es justamente cuando más se quiere, para ver qué
tiene puesto el amplificador sin tocarlo.

##### El remate: los niveles del panel y el aspecto de la barra (2026-09-10)

El pase anterior dejó **la pantalla de amplificador hablando dos idiomas**, y se veía en cuanto se
usaba con el aparato delante: Noise Gate, Contour y los dos EQ ya eran tiras verticales, y
Gain/Volume/Bass/Middle/Treble/Presence seguían siendo seis `LevelRow` horizontales apilados, uno
debajo de otro. **Es el mismo tipo de parámetro** —un nivel continuo de 0 a 100— con dos formas
distintas en la misma pantalla, y en el editor offline igual.

✅ **Se cierra sin pieza nueva**: `ampLevelParams` (`AmpScreen.kt`) construye la `List<VerticalParam>`
que ya sabe consumir `PagedVerticalParams`. Seis niveles a cuatro columnas dan dos páginas en un
móvil normal y **una sola** en horizontal o tablet, porque el número de columnas sale del ancho
([ControlPaging]) y no de una constante. Se usan los nombres cortos (`shortLabelRes`) por lo mismo
que en las tarjetas de efecto: `labelRes` lleva el valor incrustado ("Gain: 42") y en una celda de
80 dp solo cabe el nombre.

⚠️ **`LevelRow` se borra** —era su único llamador—, y `LevelControl` **se queda**: sus dos usuarios
restantes no son controles editables de panel, sino el detalle **de solo lectura** de un preset
(`LibraryPane`) y la tarjeta de diagnóstico. Un slider horizontal sigue siendo la forma correcta de
enseñar un valor que no se toca dentro de una lista.

###### El pase visual sobre `VerticalBarControl`

El control nació documentado como *"interfaz mínima y deliberadamente sin pulir"*, para poder probar
el EQ gráfico contra el amplificador con el rediseño aplazado a propósito. Con la conversión de las
once tarjetas y ahora los seis niveles, **es el control continuo de toda la app**, así que el pase
dejó de ser opcional. Todo sale de tokens del tema; no hay un `Color(0x…)` nuevo (§4.6).

| Qué se le añadió | Por qué |
| --- | --- |
| Pista con extremos redondeados y **filo propio** (`outlineVariant`) | define el hueco sin pintarlo; un rectángulo plano no se leía como recorrido |
| Relleno en **degradado** (ámbar claro arriba → `primary` abajo) | la columna se lee como iluminada en vez de como un rectángulo pintado |
| **Tapa de fader** de 3 dp (`onPrimaryContainer`) | el borde superior del degradado se difumina contra la pista y a ojo se perdían los últimos pasos |
| El número en una **pastilla** que se tiñe con el acento al arrastrar | ancho estable (la tira no baila al pasar de "9" a "100") **y** la única confirmación de que el gesto se cogió |
| El filo se enciende en `primary` mientras se arrastra | segunda señal de lo mismo, en el sitio donde está el dedo |

⚠️ **La pastilla que cambia de color no es adorno.** Con el número suelto, arrastrar y no ver nada
era **indistinguible de que el pager se hubiera llevado el gesto** — justo la duda que el bloque C
dejó abierta para el dispositivo. Ahora el propio control dice quién ganó el puntero.

⚠️ **El relleno tiene un alto mínimo de 3 dp, y por eso se calcula en dp a mano en vez de con
`fillMaxHeight(ratio)`** (que no admite suelo). Sin él, un valor en `0` mide cero y se pinta como
una barra vacía **idéntica a la de un control sin leer** (`value == null`, el `—`). Son dos estados
muy distintos —"está en cero" contra "no sé cuánto vale"— y la barra era el único sitio donde se
confundían.

⚠️ **El degradado se eligió con el contraste en la mano, no a ojo** (§4.9): sus dos extremos son
`primary` y `onPrimaryContainer`, los dos claros sobre el grafito de la pista, así que el relleno no
baja del `3:1` que WCAG 1.4.11 exige a un objeto gráfico. Un degradado que terminara en
`primaryContainer` —el marrón oscuro— habría roto ese umbral en la mitad superior de la barra sin
que nada avisara.

✅ **Otra vez cero tests tocados**, y esta vez tampoco hubo tests nuevos: no se añadió lógica, solo
un llamador más de la que ya existe. Lo que sí queda es la comprobación con el dedo (BACKLOG.md,
"Pendiente por probar", punto 10).

###### El orden de las dos páginas, y por qué esta tira sí fija sus columnas (2026-09-10)

Probado en el teléfono, el orden heredado de [AmpDomain.LEVELS] resultó estar mal para esta tira, y
**el motivo es estructural, no de gusto**: `LEVELS` es una resta sobre `LevelId.entries` (§4.2), o
sea que su orden es el del **enum**, que a su vez sigue el mapa de direcciones `06 51`..`06 56`
(Gain, Volume, Bass, Middle, Treble, Presence). Repartido de tres en tres, eso **parte la
ecualización entre dos páginas**: `Gain · Volume · Bass` y `Middle · Treble · Presence`.

✅ **El orden de pintado pasa a ser dato propio, `AmpDomain.PANEL_LEVEL_ORDER`**, y cada página es
un grupo que se ajusta junto:

| Página | Controles | Qué es |
| --- | --- | --- |
| 1 | `BASS · MIDDLE · TREBLE` | la ecualización, que se ajusta **comparando las tres entre sí** |
| 2 | `GAIN · VOLUME · PRESENCE` | cuánto satura, cuánto suena y el brillo de arriba |

⚠️ **Es la primera lista del proyecto que sí se escribe a mano donde antes había una derivada**, y
por eso lleva red: un `init` con `require` de que sea **permutación exacta** de `LEVELS`, más dos
tests JVM. Sin eso, reordenar y dejarse un nivel fuera **no daría ningún error** — la pantalla
simplemente pintaría cinco controles, que es exactamente el fallo silencioso que `AmpDomain` existe
para evitar. La derivada sigue mandando en **quién** está; lo escrito a mano es solo el orden.

⚠️ **Y esta tira fija sus columnas en 3 (`ControlPaging.columnsFor(fixedColumns = …)`), que es una
excepción explícita a la decisión 2 del bloque C.** El criterio para saltarse la regla, escrito en
el KDoc de esa función: **vale solo cuando el reparto en páginas significa algo**. Aquí las dos
páginas son dos tríadas; derivar del ancho las fundiría en una sola fila en cuanto la pantalla
diera para 6 —justo lo contrario de lo que se busca—. Para una tira donde los controles solo van
uno detrás de otro (las once tarjetas de efecto, los EQ), pasar un número fijo sería volver al
problema que la decisión 2 resolvió, y hay un test que fija que `fixedColumns = null` es idéntico
al comportamiento anterior.

✅ **Y la barra del panel se agranda: 176 dp de alto y 34 de ancho**, contra los 108×24 del resto.
`VerticalBarControl` gana `barWidth` junto al `barHeight` que ya tenía, **con los valores de
siempre por defecto**, así que las tarjetas de efecto no se mueven. Puede ser más grande
precisamente porque son tres por página y porque son las que más se ajustan al oído; con las 11
bandas de un EQ gráfico en la misma tarjeta, este tamaño no cabría. El estilo no cambia: misma
pista, mismo degradado, misma tapa — solo las dos medidas.

