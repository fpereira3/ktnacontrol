> Archivado desde CLAUDE.md — §2 "Estado actual" y "Visión de alcance" (versión del 2026-09-10), §4.2 "Capas", §4.3, §4.4, §4.5 y §8 "Instrucciones generales"
> Fecha de archivado: 2026-09-10. Registro fiel: copiado tal cual, sin reescribir ni corregir.

## 2. Estado actual

- [usb/](app/src/main/java/dev/alonx3/ktnacontrol/usb/) — **transporte funcionando y
  verificado contra el amplificador real** (§4.1): `KatanaUsbScanner` (enumerar, permiso),
  `KatanaUsbTransport` (`claimInterface(3)`, bulk sobre `0x03`/`0x84`, handshake) y
  `UsbMidiPacket` (empaquetado de 4 bytes, con tests JVM).
- [protocol/](app/src/main/java/dev/alonx3/ktnacontrol/protocol/) — Kotlin puro, con tests
  JVM y **verificado contra el amplificador real**: `MidiBytes` (7 bits), `Address`,
  `RolandSysEx` (GET/SET, checksum, parseo), `SysExFramer`, `RolandExchange`
  (petición↔respuesta correlada por dirección) y `KatanaAddresses`.
- [ui/screens/DebugConnectionScreen.kt](app/src/main/java/dev/alonx3/ktnacontrol/ui/screens/DebugConnectionScreen.kt)
  — pantalla de diagnóstico, que funciona como **monitor en vivo**: se conecta sola al
  enchufar el amp, registra todo lo que llega, y tiene botones para handshake, Identity
  Request, nombre del dispositivo, nombres de preset y edit mode.
- [device/](app/src/main/java/dev/alonx3/ktnacontrol/device/) — `KatanaLink` (puerto estrecho
  para poder testear con un falso), `KatanaParameter` (un parámetro de un byte: dirección,
  rango, caché, lectura, escritura optimista con debounce de ~100 ms y actualización desde
  los mensajes espontáneos), `KatanaEnumParameter` (un selector: una opción de una lista de
  valores) y `KatanaRepository`, que los registra. **Los once niveles continuos del panel
  están confirmados con audio real** (`60 00 06 51`–`06 5B`), todos con lectura, escritura y
  actualización desde la perilla física. Los **selectores** —tipo de amplificador, color por
  efecto, on/off por efecto— también están **confirmados**, con una excepción: la variación
  del amplificador se lee en `60 00 06 5C` pero se escribe por el modelo (§4.3, §5).
- El paquete `midi/` (enfoque `MidiManager`) está **eliminado**; ver §4.1 para el porqué.
- **Falta**: el parseo del dump en parámetros y la UI real de control. La pantalla de diagnóstico tiene dos secciones tras un menú
  hamburguesa —**Logs** (acciones + consola) y **Sliders** (sección de amplificador + una
  tarjeta por efecto)—, con el toggle de Edit Mode visible en las dos.

## Visión de alcance

Esto es la meta de **producto final**, no una lista de tareas listas para ejecutar — el
roadmap de bloques de trabajo está en `BACKLOG.md`, "Por hacer".

El objetivo final del proyecto es una **alternativa completa a Boss Tone Studio**: controlar
el Katana Mk2 al 100% desde el celular por USB, sin necesitar nunca conectar el amplificador
a una PC. Más allá de lo ya implementado (niveles, on/off, color, canal activo), eso incluye:

1. **Selección de TIPO de efecto por slot de color**, no solo nivel/on-off/color. Cada efecto
   (Booster, Mod, FX, Delay, Reverb) tiene un catálogo fijo de modelos ya definidos por Boss
   (p. ej. Booster: Clean Boost, T-Screamer/TubeScreamer, Distortion tipo RAT, etc.) — el
   catálogo ya existe en el firmware / Boss Tone Studio, no hay que modelarlo desde cero, solo
   descubrir sus direcciones e IDs. **El color (verde/rojo/amarillo) es un slot que guarda qué
   tipo de efecto tiene asignado, no un efecto en sí mismo.** Cada slot de color tiene además
   sus propios parámetros específicos según el tipo asignado (un Booster tipo TubeScreamer
   tendrá Gain/Level/Tone; un tipo distinto puede tener parámetros distintos). Esto es **por
   preset**: el booster asignado al color rojo en el preset 1A puede ser distinto al asignado
   al color rojo en el preset 2A.
2. **Parámetros internos completos de cada tipo de efecto en cada slot** (Gain, Level, Tone,
   Bottom, etc. según corresponda al tipo).
3. **Controles sin perilla física en el panel** pero presentes en Boss Tone Studio: Noise
   Gate, Solo, Contour, posición IN/OUT de EQ1 y EQ2 (antes o después del preamp), selección
   de cadena de efectos (chain). ✅ **Los cinco están localizados documentalmente**
   (2026-09-05, §5 "Controles sin perilla física"), con una sola ambigüedad —Solo tiene dos
   direcciones candidatas— y ninguno probado todavía contra el amplificador.
4. **Guardado de presets**: escribir el estado actual editado a un canal específico (p. ej.
   1A), equivalente a grabar un preset desde el panel del amplificador pero hecho desde la
   app.
5. **Import/export de archivos `.tsl`** (formato de preset de Boss Tone Studio), usando los
   offsets ya documentados en
   [reference/TuxKatana/params/presets_addrs.yaml](reference/TuxKatana/params/presets_addrs.yaml).
6. **UI real de control** reemplazando la pantalla de diagnóstico actual, con pantallas por
   dominio, una vez que la investigación de protocolo de los puntos anteriores esté
   suficientemente avanzada.

El desarrollo sigue siendo **incremental**, no un salto a por todo esto de golpe: cada pieza
se investiga y se confirma con audio o con el amplificador real antes de darla por buena,
exactamente con la misma disciplina ya aplicada a reverb y al resto de efectos (§5) — nunca
asumir una dirección por analogía sin probarla.


---

### 4.2 Capas

Tres capas, con una regla dura: **el protocolo es Kotlin puro y testeable en JVM**.

```
dev.alonx3.ktnacontrol/
├── MainActivity.kt
├── protocol/          ← Kotlin puro. CERO imports de android.*
│   ├── MidiBytes.kt        conversiones int <-> bytes de 7 bits
│   ├── Address.kt          dirección de 4 bytes + aritmética (suma de offsets)
│   ├── RolandSysEx.kt      header, checksum, construcción de mensajes GET/SET, parseo
│   ├── KatanaAddresses.kt  constantes de direcciones (ver §5)
│   ├── SysExFramer.kt      reensambla F0..F7 desde trozos arbitrarios de bytes
│   ├── AmpType.kt          tablas de valores: AmpType, AmpCategory, EffectColor
│   ├── LevelScale.kt       crudo ↔ mostrado de un nivel continuo
│   ├── MemoryDump.kt       los trozos del dump, indexados por dirección
│   └── params/             tablas de parámetros: dirección, tamaño, tipo, rango, etiquetas
├── usb/               ← único lugar que toca android.hardware.usb
│   ├── KatanaUsbScanner.kt     UsbManager.deviceList + permiso + USB_DEVICE_ATTACHED
│   └── KatanaUsbTransport.kt   claimInterface(3), bulkTransfer sobre 0x03 / 0x84,
│                               envío y Flow<ByteArray> de entrada
├── device/            ← estado del amplificador (fuente única de verdad)
│   ├── KatanaControl.kt      base sellada + KatanaParameter (continuo) y
│   │                         KatanaEnumParameter (selector). Ver §4.3
│   ├── KatanaRepository.kt   caché por dirección, coalescing, carga desde el dump
│   └── model/AmpState.kt     instantánea legible de los 24 parámetros
└── ui/
    ├── theme/
    └── screens/              DebugConnectionScreen, AmpScreen, EffectsScreen, PresetsScreen...
```

`SysExFramer` se mueve a `protocol/`: ya no depende de la API de MIDI de Android, solo
convierte un flujo de bytes en mensajes completos, así que gana tests JVM.

**Flujo de datos (unidireccional):**

```
UI (Compose) → ViewModel → KatanaRepository → KatanaUsbTransport → [Katana]
                                ↑                bulk 0x03 ↑↓ 0x84    │
                          StateFlow<AmpState>  ←  parse ← SysExFramer ┘
```

Reglas:

- La UI **nunca** construye bytes SysEx ni conoce direcciones. Habla de parámetros de dominio.
- `KatanaRepository` mantiene la copia local de la memoria del amp (el dump de `60 00 00 00`)
  y la expone como `StateFlow`. Escribir un parámetro = actualizar caché + enviar SysEx.
- **Al conectar se lee el dump, no 24 GET.** Ver §4.4.
- El amplificador **también responde con cambios** (perillas físicas, parámetros derivados),
  así que la caché se actualiza tanto desde la UI como desde el endpoint de entrada. Hay que
  evitar bucles de eco: al aplicar un mensaje entrante no se reenvía al amp.
- **Anti-flood**: arrastrar un slider genera decenas de eventos. Coalescer por dirección con un
  debounce de ~100 ms antes de enviar (TuxKatana hace exactamente esto en `lib/anti_flood.py`).
- **Edit mode** (`7F 00 00 01 → 01`): ✅ **confirmado contra el amplificador real**
  (2026-09-02). Sin él, mover una perilla física del Katana **no** genera ningún mensaje;
  con él activo, **sí** los genera y el bucle de lectura los recibe. Es lo que hace que el
  amp reporte cambios derivados, así que **debe activarse como parte del flujo normal de
  conexión**, no solo desde el botón de diagnóstico: sin eso la UI nunca vería lo que se
  toca en el propio amplificador.
  - Es *fire-and-forget* (semántica DT1 de Roland): no devuelve confirmación por SysEx.
  - Altera el estado del amp y TuxKatana advierte de guardar los presets antes, así que
    tratarlo como un ajuste explícito y visible, nunca como algo silencioso — y ofrecer
    siempre la forma de desactivarlo.
  - **El contrato de la UI (2026-09-04): edit mode apagado deja cambiar de canal y nada más.**
    Sin edit mode el amplificador no reporta, así que la app **no puede confirmar** ningún
    parámetro que escriba; dejar los controles activos sería aceptar el gesto y no poder decir
    si sirvió de algo. `SlidersPane` calcula `canEdit = connected && editMode` y deshabilita
    con él todos los parámetros, con un aviso que explica por qué. **El selector de canal es la
    única excepción** —sigue con `enabled = connected`—: cambiar de canal es un comando
    básico, no un ajuste fino.
  - ⚠️ **Nunca ha habido, ni debe haber, un gate de edit mode en `device/` o `protocol/`.**
    Esas capas no saben qué es el edit mode y el SET sale al cable siempre; quién puede editar
    lo decide la UI. Hay un test JVM que lo fija. Queda **por confirmar con el amplificador**
    si el hardware acepta el SET del canal con edit mode apagado (BACKLOG.md, "Pendiente por
    probar" y "El canal depende de Edit Mode: no es una regresión").


---

### 4.3 Dos tipos de control: continuo y selector

Casi todo lo que la app controla es **un byte en una dirección** —salvo el canal activo, que
son 2 (§5.1)—, así que todo comparte la misma maquinaria: caché, GET, SET optimista, regla
anti-eco, con un `byteWidth` configurable (por defecto 1) para ese único caso. Eso vive en
`device/KatanaControl.kt`, una clase sellada de la que cuelgan exactamente dos formas:

| | `KatanaParameter` | `KatanaEnumParameter` |
| --- | --- | --- |
| Valores | una `LevelScale` (crudo ↔ mostrado) | una `List<Int>` con huecos |
| Fuera de rango | **clampea** al extremo más cercano | **rechaza**: ni envía ni toca la caché |
| Debounce | ~100 ms, se arrastra | ninguno, es un toque |
| Ejemplos | los once niveles del panel | amp type, color, on/off |

Las dos diferencias salen de la misma raíz: **los valores de un selector no son contiguos**.
`AmpType` va del `0x00` al `0x20` saltándose el `0x19`, así que "el valor legal más cercano"
no significa nada y adivinar sería peor que no hacer nada. Importa sobre todo en la dirección
de entrada: si el amplificador reporta un valor que no está en la tabla, lo correcto es
**dejar la UI como está** y que el valor aparezca en el log, no mover el control a un vecino
inventado. El debounce se cae solo: sin arrastre no hay avalancha que coalescer, y esperar
100 ms para un botón solo añadiría lag.

**Un nivel tiene dos caras y hay que usar la correcta.** `state` / `set` hablan en **bytes
crudos**, como todo control; `displayValue` / `setLevel` hablan en **lo que se muestra**. No
son el mismo número en los cinco niveles de efecto, donde el crudo va desplazado en uno
porque su `0` significa Off — ver `LevelScale`. Cualquier cosa que mueva un slider quiere la
segunda cara; el log y los tests razonan en bytes, que es la única forma honesta de hablar de
lo que el amplificador guarda.

Las tablas de valores (`AmpType`, `AmpCategory`, `EffectColor`) y `LevelScale` viven en
`protocol/`, son Kotlin puro y tienen tests JVM. La UI las consume por sus enums (`LevelId`, `EffectId`,
`SelectorId`) y **nunca ve una dirección**, igual que con los niveles.

### 4.4 Poblar el estado: un dump en vez de 24 GET

Al abrir la conexión hay que averiguar en qué estado está el amplificador. La primera versión
mandaba **un GET por control** —24 peticiones en serie, cada una esperando hasta 800 ms su
propia respuesta— porque era lo simple mientras había un solo parámetro. Con veinticuatro es
absurdo: el amp contesta el dump de `60 00 00 00` con **todos** los rangos ocupados de golpe,
y todas las direcciones que la app controla viven dentro de él.

```
KatanaRepository.loadFromDump()
  → GET 60 00 00 00, tamaño 00 00 0F 00
  ← varios mensajes Roland, cada uno con su dirección base y su longitud
    (se recogen hasta que el amp se calla ~250 ms, no una ventana fija)
  → MemoryDump.from(mensajes)          protocol/, puro, con tests JVM
  → por cada control: dump.byteAt(su dirección) → applyDumpValue
  → lo que el dump no cubra: un GET individual, en serie, como respaldo
  → AmpState.from(dump)                instantánea legible para el log y la UI futura
```

**El dump llega en varios mensajes, cada uno con su base.** Ni el número de mensajes ni el
total de bytes son fijos —HOW.md traza 6, el Mk2 real dio 8— así que `MemoryDump` es una
**búsqueda por dirección**, no un índice sobre un array plano. Una dirección que no aparece
es una respuesta normal ("el amp no mandó ese rango"), no un error: queda en `null` y su
control cae al GET de respaldo.

⚠️ **Corregido el 2026-09-04: los trozos SÍ son contiguos entre sí.** Aquí se decía que "el
amplificador se salta los huecos donde no hay parámetros", y no es lo que hace: manda **un
bloque contiguo troceado en mensajes de 241 bytes de datos**, y lo que hace con los huecos es
**parar antes** (1860 de 1920), no dejar agujeros en medio. Comprobado por cuatro vías que
cuadran a la vez —las tres bases que documenta HOW.md, la única base del Mk2 anotada
(`60 00 05 53` = trozo 3 = offset 723 = 3×241), el offset 125 de las perillas dentro de ese
trozo, y el reparto 7×241 + 173 = 1860—. Detalle y tabla en BACKLOG.md, "Propuesta de diseño".

Esto **no cambia el diseño de `MemoryDump`**, que sigue siendo una búsqueda por dirección y
sigue sin asumir número ni total. Importa para otra cosa: **filtrar la respuesta del dump por
contigüidad sería una mala idea igualmente** —si algún día el amp sí dejara un hueco, un filtro
así descartaría en silencio todo lo posterior—, así que el criterio de aceptación es el rango
pedido, y la contigüidad solo vale como comprobación de diagnóstico.

Dos tipos, y cada uno tiene su razón de existir:

- **`MemoryDump`** (protocol/, puro) responde "¿qué byte hay en esta dirección?". La
  aritmética es base 128, que es exactamente como el amp coloca los bytes.
- **`AmpState`** (device/model/) es la instantánea de dominio: los 24 parámetros con nombre,
  ya en unidades de presentación —el desplazamiento de los niveles de efecto se aplica aquí—
  y con `null` donde el dump no llegó. Lo consume hoy el log de diagnóstico; lo consumirá la
  UI real.

**Aplicar el dump a los controles no pasa por `AmpState`**, sino que recorre la lista de
controles del repositorio preguntando por la dirección de cada uno. Es deliberado: así añadir
un parámetro no puede olvidarse de actualizar el volcado, porque no hay nada que actualizar.

**La recogida termina por silencio, no por ventana fija.** No se sabe de antemano cuántos
mensajes vendrán, así que `sendAndCollectUntilQuiet` corta tras ~250 ms sin nada nuevo (el
hueco observado entre mensajes es de ~30 ms) con un tope absoluto de 3 s por si no llega
nada. Con la ventana fija los datos estaban a los 275 ms pero los controles no se poblaban
hasta los 3 s — **más lento que los 24 GET a los que sustituye**, que tardaban ~540 ms.

✅ **Verificado contra el amplificador (2026-09-03):** 8 mensajes, 1860 B, **24 de 24
controles poblados del dump y 0 GET de respaldo**, con los mismos valores que daba la lectura
individual. El bloque de perillas `06 50`–`06 5B` cae en el trozo que empieza en
`60 00 05 53`, a partir del offset 125.

**También se relee al cambiar de canal, no solo al conectar.** Cada canal (1A–4A, 1B–4B,
PANEL) tiene sus propios valores para todo: niveles, modelo de amplificador, colores, on/off,
tipos de efecto. Sin releer al cambiar de canal la app seguiría mostrando los del canal
anterior, que es peor que mostrar nada — parecería que el amplificador dice una cosa cuando
dice otra.

⚠️ **La primera versión de esto (2026-09-03) tenía un bug de cuatro fallos encadenados,
reproducido en JVM el 2026-09-04 y ya arreglado — pendiente de confirmar con el amplificador
real** (BACKLOG.md, "Pendiente por probar", punto 6). Vale la pena documentar la cadena
porque es un recordatorio de lo mismo que §5 repite para las direcciones: **un argumento
estructural convincente no es una comprobación**. Se razonaba que "no hay bucle porque el
canal vive fuera del dump" y que "1A→2A→3A recarga una sola vez" — las dos frases sonaban
bien y las dos eran falsas, y bastaba una traza para verlo:

- **Sí había bucle, y sí se reescribía el canal.** El argumento era que `00 01 00 00` vive
  fuera del dump. Cierto para la *petición*, pero `sendAndCollectUntilQuiet` no filtraba por
  dirección: recogía todo lo que pasara por el stream mientras durara la ventana, así que un
  reporte espontáneo de cambio de canal que llegara durante un dump entraba en la lista y
  acababa como un `Chunk` de `MemoryDump`. Y `applyDumpValue` era de **un solo byte** y no
  conocía `byteWidth`, así que le daba al canal su byte alto, `0` — el canal se convertía en
  Panel.
- **Y por eso 1A→2A→3A no recargaba una vez: no recargaba ninguna.** Ese `0` espurio
  reiniciaba el `collectLatest` y, como `reload` ya había dejado `loadedChannel = 0`, el guard
  descartaba la recarga pendiente.
- Además la recarga de conexión era un `launch` aparte sin relación con el `collectLatest`
  del canal: se midieron **dos dumps simultáneos**, cada colector comiéndose los mensajes del
  otro, así que ninguno alcanzaba el silencio de 250 ms y ambos agotaban el tope de 3 s.

**La corrección, en `KatanaRepository` y en el ViewModel:**

- `sendAndCollectUntilQuiet` gana un predicado `accept: (RolandMessage.Data) -> Boolean`
  (`protocol/RolandExchange.kt`) y devuelve `QuietCollection(accepted, rejected,
  invalidCount)` en vez de la lista cruda. `loadFromDump` lo llama con
  `blockReplyIn(MEMORY_DUMP, MEMORY_DUMP_SIZE)`: dentro del rango pedido **y** con un payload
  mayor que `MAX_CONTROL_PAYLOAD` (2, el mismo umbral que ya usaba `onIncoming` al revés). Un
  mensaje rechazado **no reinicia el contador de silencio** — antes sí, y era la causa directa
  de que la ventana se estirara hasta el tope. Los rechazos se registran agrupados en
  `DumpLoad.rejectedDuringWindow`, no línea por mensaje.
- `MemoryDump` gana `bytesAt(address, width)`, y `KatanaControl.applyDumpValue` pasó de
  recibir un `Int` ya extraído a recibir el `MemoryDump` entero y leer sus propios
  `byteWidth` bytes — todo o nada, nunca un byte alto suelto.
- En el ViewModel, la recarga de conexión y el watcher de canal dejaron de ser dos caminos
  independientes: los dos alimentan el mismo `MutableStateFlow<ReloadRequest>` conflado,
  consumido por un único `collectLatest` (`startReloadCoordinator`), con un `Mutex` como red
  de seguridad alrededor de `loadFromDump()` y el guard de canal reevaluado **después** del
  margen de 300 ms, no solo antes.

✅ **Comprobado en JVM** (10 tests nuevos, 208 en total) reproduciendo el escenario exacto que
falló: 1A→2A→3A en 100 ms produce un solo dump adicional y dan canal correcto en caché; una
recarga de conexión deliberadamente lenta que se solapa con un cambio de canal nunca deja dos
dumps en vuelo a la vez; y un reporte espontáneo emitido durante la ventana del dump aparece
en `rejected`, no en `accepted`. **Sin probar contra el amplificador todavía.**

### 4.5 Editar sin amplificador: el backend de un control es el `KatanaLink`

Decidido el 2026-09-06, antes de escribir la edición offline de la Biblioteca. La pregunta era
cómo hacer que **los mismos sliders y selectores** que hoy escriben al amplificador puedan
editar un preset de un fichero, sin cable y sin hardware.

✅ **La decisión: una implementación de `KatanaLink` que en vez de un cable es una imagen de
memoria en RAM.** No hace falta ninguna abstracción nueva — `KatanaLink` **ya era** el backend
de un parámetro, solo que hasta ahora tenía una sola implementación.

```
en vivo:   KatanaControl → KatanaLink(USB)      → [amplificador]
offline:   KatanaControl → KatanaLink(MemoryImage) → [bytes en RAM]
                            ↑ mismos controles, mismo KatanaRepository, misma UI
```

`KatanaLink` tiene exactamente dos operaciones (`send(mensaje)` y `incoming: Flow<ByteArray>`),
y `KatanaControl` no usa nada más: un SET es `link.send(RolandSysEx.set(...))` y un GET es
`awaitRolandReply(link.incoming, ...)`. Así que una implementación que **guarde los bytes de un
SET en un mapa dirección→byte y conteste a un GET desde ese mismo mapa** hace funcionar todo el
juego de controles sin tocar `KatanaControl`, `KatanaRepository` ni un solo composable.

**Cuatro cosas del código ya existente hacen que esto encaje sin forzar nada:**

1. **`awaitRolandReply` y `sendAndCollectUntilQuiet` se suscriben con
   `CoroutineStart.UNDISPATCHED` *antes* de llamar a `send`** (§4.4). Estaba puesto para no
   perder una respuesta rápida del amplificador; resulta que es exactamente lo que hace falta
   para que una respuesta **síncrona**, emitida desde dentro de `send`, no se pierda.
2. **`KatanaRepository` ya se testea contra un `KatanaLink` falso** (§6). El link offline no es
   un tipo nuevo de cosa: es el mismo patrón que los tests llevan usando desde el principio,
   ascendido a código de producción.
3. **`MemoryDump` es una búsqueda por dirección**, no un índice sobre un array plano (§4.4), y
   la imagen mutable es su gemela escribible. Convertir una en otra es directo, así que
   `AmpState.from(...)` y el parseo de `.tsl` siguen funcionando sin cambios.
4. **Las escrituras ya son fire-and-forget optimistas** (§4.2). Offline, "el amplificador no
   confirma" deja de ser una limitación y pasa a ser trivialmente cierto.

#### Por qué no las otras dos opciones

**Opción A —abstraer un "backend de parámetro" dentro de `KatanaControl`, con dos
implementaciones— se descarta porque duplicaría un seam que ya existe.** `KatanaLink` está una
capa más abajo y ya separa exactamente lo mismo. Meter una segunda costura significaría que
`read`, `set`, `probeWrite` y `applyIncoming` crezcan cada uno una rama, en la clase que
concentra las reglas más delicadas del proyecto (la caché optimista, el debounce, la regla
anti-eco). Más código y más riesgo para el mismo resultado.

**Opción B —`AmpState` como fuente única de verdad, con los controles editándolo siempre y un
observador sincronizando hacia el amplificador— se descarta por un dato concreto y medible:
`AmpState` tiene 24 campos y la app controla hoy varios cientos de direcciones.** Solo
`ModFxInternalParams` son 192 `ParamSpec`, más 48 de EQ1/EQ2, 20 ranuras de cadena, los Contour,
el Noise Gate. Hacer de `AmpState` la fuente de verdad obliga a hacerlo crecer a cientos de
campos, uno por parámetro, y a mantener esa lista a mano cada vez que se cablea un tipo nuevo —
justo lo que §4.4 evita deliberadamente al aplicar el dump recorriendo la lista de controles en
vez de los campos de `AmpState`.

⚠️ **Y tiene un problema peor, que es el que la descarta del todo: un modelo de dominio pierde
lo que no modela.** Un `.tsl` son 1141 bytes; `AmpState` entiende 24 valores. Si la edición
pasara por `AmpState`, importar un preset, cambiarle el Gain y volver a exportarlo **borraría
todos los parámetros internos de Mod/FX, el EQ y la cadena**, sustituidos por lo que `AmpState`
no supo conservar. Con la imagen de bytes, lo que la app no entiende **se conserva intacto
porque nunca se toca**. Para un formato de intercambio esa propiedad no es un detalle, es el
requisito.

Además invertiría el flujo de datos que hoy está confirmado contra hardware real, y reabriría
la clase de bug que costó un día entero de depuración (§4.4, "los cuatro fallos encadenados":
eco, reentrada y dumps solapados).

#### Qué es cada cosa, ahora que hay dos orígenes

| | Qué es | Mutable | Fidelidad |
| --- | --- | --- | --- |
| `MemoryImage` | mapa dirección→byte, la memoria editable de un preset | **sí** | **total**: guarda bytes que nadie interpreta |
| `MemoryDump` | lo que el amplificador contestó, indexado por dirección | no | total, pero de solo lectura |
| `AmpState` | instantánea legible de 24 parámetros con nombre | no | **lossy a propósito**: es para leer, no para guardar |

**La regla que sale de aquí, y que conviene no olvidar: lo que se guarda y se transporta son
bytes; `AmpState` es para enseñar.** Cualquier camino de exportación que pase por `AmpState`
está perdiendo información aunque no lo parezca.

⚠️ **`device/` sigue sin saber si está en vivo o no**, igual que no sabe qué es el edit mode
(§4.2). Un control hace su SET siempre; quién es el destinatario lo decide quién construye el
`KatanaRepository`. Esa es la misma frontera de siempre, y no se mueve.

⚠️ **Actualización del 2026-09-09: el editor offline ya no pasa por `SlidersPane`.** Cuando se
escribió esto, la UI del editor era `SlidersPane(offline = true)` — la misma pantalla que la de en
vivo, con un booleano. Con las tres pantallas de dominio hechas, `SlidersPane` se borró y el editor
usa `PresetEditorBody` (`AmpSection` + `EffectsSection` + `NoPanelPane`), que es lo que esa rama
`offline` ya era en realidad. **Nada de lo de arriba cambia**: el backend sigue siendo un
`KatanaLink` sobre `MemoryImage`, los controles siguen siendo los mismos, y `device/` sigue sin
saber si está en vivo. Lo que cambió es qué composable los pinta. Ver §4.2, "El cierre".


---

## 8. Instrucciones generales

- [BACKLOG.md](BACKLOG.md) es el registro de avance del proyecto, con cinco secciones:
  **Hecho**, **En progreso**, **Por hacer**, **Pendiente por probar** y **Notas y decisiones técnicas**.
- **Cada vez que completes una tarea de desarrollo**, actualiza `BACKLOG.md` de inmediato:
  mueve el ítem correspondiente a "Hecho" (y quítalo de "En progreso"/"Por hacer") y, si la
  tarea implicó alguna decisión técnica relevante (elección de dirección SysEx, trade-off de
  arquitectura, por qué se descartó una alternativa, etc.), agrégala en "Notas y decisiones
  técnicas". Si la tarea incluye alguna prueba con el amplificador físico, añade dicha prueba a "Pendiente por probar", junto al resultado esperado en caso de realizarse exitosamente.
- Esto se hace **sin que se te tenga que pedir explícitamente en cada sesión** — es parte de
  terminar la tarea, igual que compilar o testear.
