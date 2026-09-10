# BACKLOG

Registro de avance de KTNA Control, estilo backlog de sprint. Ver [CLAUDE.md](CLAUDE.md)
para arquitectura, mapeo del protocolo y convenciones — este archivo es histórico y de
seguimiento, no repite lo que ya está ahí.

## Hecho

- **2026-09-01** — [CLAUDE.md](CLAUDE.md): arquitectura planeada, mapeo del protocolo SysEx
  dentro de `reference/` y convenciones de Kotlin/Compose para el proyecto.
- **2026-09-01** — Configuración base de acceso USB MIDI en el manifest: `uses-feature` de
  `android.software.midi` (required) y `android.hardware.usb.host` (no required),
  intent-filter `USB_DEVICE_ATTACHED` en `MainActivity` con su `meta-data` apuntando a
  `@xml/device_filter`, y `res/xml/device_filter.xml` creado vacío. Verificado con
  `:app:assembleDebug` y revisando el manifest fusionado.
- **2026-09-02** — Transporte USB vendor-specific, reemplazando por completo el paquete
  `midi/` (borrado): `usb/UsbDiagnostics` (ids, endpoints y modelo puro),
  `usb/KatanaUsbScanner` (enumerar, filtrar por 1410/472, permiso vía `PendingIntent`) y
  `usb/KatanaUsbTransport` (`claimInterface(3)`, `sendRaw` / `receiveRaw` sobre `0x03` y
  `0x84`, `close()` que libera la interfaz). Manifest corregido —fuera
  `android.software.midi`, `usb.host` a `required="true"`— y `device_filter.xml` completado
  con 1410 / 472. La pantalla de diagnóstico ahora lista dispositivos USB y añade el botón
  «Enviar Identity Request». Compila, pasa lint e instalado en el celular.
- **2026-09-02** — Empaquetado USB-MIDI de 4 bytes y handshake, en `usb/UsbMidiPacket.kt`
  (`packUsbMidi` / `unpackUsbMidi`, Kotlin puro) y `usb/KatanaUsbTransport` (`sendRaw`
  empaqueta, `receiveRaw` desempaqueta, `receiveWire` devuelve el cable tal cual para
  diagnóstico, y `sendHandshake()` envía la trama dos veces con ~4 ms). Constantes del
  handshake en `KatanaHandshake`, con `MODEL_ID_MS3` al lado para intercambiar. Botón
  «Enviar Handshake» en la pantalla. **9 tests JVM** en `UsbMidiPacketTest` cubren los tres
  restos módulo 3, el round trip de 1 a 64 bytes, el relleno y los CIN de no-SysEx; los 10
  tests del proyecto pasan. Corregido de paso el conteo de interfaces (4 reales, 7 alt
  settings).
- **2026-09-02 — 🎉 HITO: primera comunicación end-to-end con el amplificador real.**
  Probado en el celular con el Katana por OTG. El dispositivo se enumera y se abre sin
  problema (`KATANA`, BOSS, vendorId 1410 / productId 472, clase 255, 4 interfaces / 7 alt
  settings — el conteo corregido salió bien), se reclama la interfaz 3 y **el Identity
  Request obtiene respuesta**:

  ```
  → sysex: F0 7E 7F 06 01 F7
    wire:  04 F0 7E 7F 07 06 01 F7          (8 bytes escritos)
  ← wire:  20 bytes
    desempaquetado: F0 7E 00 06 02 41 33 03 00 00 06 00 00 00 F7
  ```

  Es un Identity Reply estándar y coincide con el que documenta
  [HOW.md](reference/TuxKatana/HOW.md). Queda validado de una sola vez: la interfaz y los
  endpoints (3, `0x03`/`0x84`), el empaquetado de 4 bytes en ambos sentidos, y que el amp
  atiende a broadcast (`7F`) respondiendo como device ID `00` con model id `33`.

- **2026-09-02** — Arranque de `protocol/`, Kotlin puro sin `android.*`: `MidiBytes`
  (aritmética de 7 bits), `Address` (4 bytes con acarreo en base 128), `RolandSysEx`
  (construcción GET/SET, checksum y parseo con validación) y `KatanaAddresses` (por ahora
  solo `DEVICE_NAME`). **22 tests JVM nuevos** contra los vectores reales de HOW.md y
  katana_sysex.txt: el GET del nombre del dispositivo, los de presets 1 y 2, el dump de
  memoria, el SET de edit mode, el ejemplo de checksum de la spec, y el parseo de la
  respuesta real con su checksum `76` → `"KATANA Mk2"`. 32 tests en total, todos pasan.
  Botón «Leer nombre del dispositivo» en la pantalla de diagnóstico.
- **2026-09-02 — ✅ Primer GET real confirmado contra el amplificador: `RolandSysEx` funciona
  de punta a punta.** Pulsando «Leer nombre del dispositivo» el amp devolvió **30 bytes**
  (el mensaje SysEx completo, ya desempaquetado), con **checksum válido**, dirección
  `10 00 00 00` y 16 bytes de datos que se leen como **`"KATANA Mk2"`**.
  - Los 30 bytes cuadran exactamente con lo previsto: 7 de cabecera + 1 de comando + 4 de
    dirección + 16 de datos + 1 de checksum + 1 de terminador.
  - Esto valida lo único que el Identity Request no ejercitaba —ese es un mensaje universal,
    sin prefijo Roland ni checksum—: **construcción de la dirección de 4 bytes, cálculo del
    checksum al enviar, y validación del checksum más extracción del payload al recibir.**
    Sumado al hito anterior, la pila entera (USB → empaquetado → SysEx Roland) está
    confirmada contra hardware real.

- **2026-09-02** — Direcciones de los nombres de los 8 presets en `KatanaAddresses`,
  **generadas con la aritmética de `Address`** (`FIRST_PRESET_NAME + i * stride`, donde el
  stride se escribe como `Address(0x00, 0x01, 0x00, 0x00).value` para no dejar un 16384
  mágico) en vez de escribir las 8 a mano, más `presetName(1..8)`. Botón «Leer nombres de
  presets» que las recorre en secuencia. 5 tests nuevos (37 en total, todos pasan).

- **2026-09-02 — ✅ Lectura de los 8 nombres de preset confirmada contra el amplificador.**
  Los 8 GET devolvieron respuesta con **checksum válido en todos**. Los 3 primeros presets
  traían nombres modificados —de botones pulsados directamente en el amplificador en pruebas
  anteriores— y los 5 restantes el nombre de fábrica `"KATANA Mk2"`.
  - Lo interesante es justo eso: **la app lee el estado real del hardware, no un valor
    cacheado ni por defecto**. Que los nombres editados a mano aparezcan y los intactos no,
    es la mejor confirmación posible de que las 8 direcciones generadas con la aritmética de
    `Address` apuntan cada una a su preset.

- **2026-09-02** — Bucle de lectura continuo: la pantalla de diagnóstico pasa a ser un
  **monitor en vivo**. `protocol/SysExFramer` (Kotlin puro, 9 tests) reensambla mensajes
  `F0…F7` desde trozos arbitrarios; `KatanaUsbTransport.incomingMessages()` expone un
  `Flow<ByteArray>` que corre en `Dispatchers.IO` con timeout de 100 ms y trata `-1` como
  "sin datos, seguir escuchando". Los botones **solo envían**; las respuestas llegan por el
  flow, igual que cualquier mensaje espontáneo del amplificador. `KatanaUsbScanner` atiende
  `USB_DEVICE_ATTACHED` / `DETACHED`, así que conectar el amp abre la conexión solo y
  desconectarlo la cierra. 46 tests en total, todos pasan; instalado en el celular.

- **2026-09-02** — Corregida la condición de carrera al leer varias direcciones seguidas.
  Con las respuestas llegando de forma asíncrona, enviar los 8 GET de presets sin esperar
  correlaba mal si el amp contestaba desordenado. Ahora `protocol/RolandExchange.kt`
  (`awaitRolandReply`, Kotlin puro) empareja cada petición con el primer mensaje Roland cuya
  dirección coincide, con timeout de 800 ms, y la secuencia sigue con la siguiente dirección
  aunque una no conteste. Lo usan «Leer nombre del dispositivo» y «Leer nombres de presets»;
  «Identity Request» y «Handshake» siguen igual, que son peticiones sueltas.
  6 tests nuevos (52 en total, todos pasan).

- **2026-09-02** — Edit Mode aislado: `KatanaAddresses.EDIT_MODE` (`7F 00 00 01`) con
  `EDIT_MODE_ON` / `OFF`, y botones «Edit Mode ON» / «OFF» en la pantalla. Como un SET no se
  confirma (ver nota abajo), no se correlaciona por dirección: se usa `sendAndCollect`, que
  envía y observa el stream 1,5 s registrando lo que llegue, o dice explícitamente que no
  llegó nada. El botón OFF existe para no dejar el amp en un estado distinto tras las
  pruebas. 4 tests nuevos (56 en total, todos pasan).

- **2026-09-02 — ✅ HIPÓTESIS CONFIRMADA: Edit Mode es lo que habilita los reportes
  espontáneos.** Probado en el amplificador real, en las dos direcciones:
  - Con Edit Mode **desactivado**, mover una perilla física del Katana no genera ningún
    mensaje en `incomingMessages()`.
  - Con Edit Mode **activado** (SET a `7F 00 00 01` con dato `0x01`), mover una perilla
    física **sí** genera mensajes espontáneos, y el monitor en vivo los registra bien.

  Queda cerrado el propósito de Edit Mode y **separado definitivamente del handshake**: son
  cosas distintas, y el silencio del handshake ya no tiene nada que ver con esto. También
  confirma lo que `HOW.md` y `controller.py` predecían antes de la prueba: el SET es
  fire-and-forget y no devuelve confirmación.

- **2026-09-02** — Lectura del dump de memoria: `KatanaAddresses.MEMORY_DUMP`
  (`60 00 00 00`, rango 1920) y botón «Leer dump de memoria», que envía el GET y recoge la
  respuesta durante 3 s, contando trozos, bytes de datos, checksums válidos e inválidos, y
  mostrando los primeros 16 bytes como el nombre del preset activo. Test nuevo del framer
  con un mensaje real de 1934 bytes troceado en lecturas de 512 (57 tests, todos pasan).

- **2026-09-02 — ✅ Dump de memoria confirmado contra el amplificador. Cierra el bloque de
  validación de transporte + protocolo.** Resultado: **8 mensajes, los 8 con checksum
  válido**, 1972 bytes en el cable y **1860 de datos** — cuadra exacto:
  1972 − 8×14 de overhead por mensaje = 1860. El nombre de preset del primer trozo
  **coincidió con el canal activo** en el amplificador en ese momento.
  - Con esto queda validada de punta a punta la pila completa contra hardware real:
    Identity Request → nombre del dispositivo → nombres de los 8 presets → edit mode →
    dump de memoria. Transporte, empaquetado, framing, direcciones y checksum, todo.

- **2026-09-02** — Primer control interactivo real y arranque de `device/`:
  `KatanaLink` (puerto estrecho para poder testear con un falso), `KatanaRepository`
  (`StateFlow<Int?>` del reverb level, lectura por GET, escritura optimista y suscripción a
  los mensajes espontáneos) y un `Slider` en la pantalla. **9 tests nuevos** contra un
  `FakeLink`, sin `UsbManager` (66 en total, todos pasan).

- **2026-09-02 — ✅ PRIMER CONTROL INTERACTIVO REAL, confirmado por OÍDO.** Mover el slider
  de la app **cambia el sonido del amplificador**. No es una confirmación por checksum ni por
  coincidencia de bytes: es audio real. Y los mensajes espontáneos al girar la perilla física
  llegan por esa misma dirección y mueven el slider solo, sin tocarlo.
  - **Dirección definitiva del nivel de reverb en el Mk2: `60 00 06 5B`, rango 0..100**, de
    **lectura y escritura a la vez** — no es "la de reporte" de un par.
  - Costó **tres candidatas** y solo el audio las distinguió. El proceso queda escrito en
    CLAUDE.md §5 para no repetirlo a ciegas con los cinco efectos que faltan.

- **2026-09-02** — `KatanaRepository` generalizado: cada parámetro controlable es un
  `device/KatanaParameter` que encapsula dirección, rango, `StateFlow` de caché, GET, SET
  optimista y la regla anti-eco. Añadir el siguiente es **una línea** en el repositorio, no
  otra copia de toda la lógica. Incluye el **debounce de 100 ms** que pedía CLAUDE.md §4.2:
  cada escritura cancela el envío pendiente y reinicia el temporizador, así que arrastrar el
  slider manda **un solo SET** con el valor final, mientras la caché se actualiza al
  instante. Reverb migrado sin cambio de comportamiento. Las cinco candidatas para los otros
  efectos quedan en `KatanaAddresses.LevelCandidates`, marcadas **sin verificar**.
  11 tests en `KatanaRepositoryTest` (69 en total, todos pasan).
  - ✅ **Confirmado con el amplificador real**: al arrastrar el slider ya no salen valores
    intermedios — solo el final, ~100 ms después de soltar, y el sonido resultante es el
    correcto.
  - ✅ **La perilla física sigue actualizando el slider en tiempo real.** Ese camino
    (mensaje espontáneo → caché) no pasa por el debounce y el cambio no lo afectó, que era
    justo lo que había que comprobar: el debounce solo debe frenar lo que la app **envía**,
    nunca lo que **recibe**.

- **2026-09-02** — Auto-conexión en los tres caminos posibles. El fallo era que
  `MainActivity` **ignoraba el intent** con el que Android la abría: leía el `USB_DEVICE_ATTACHED`
  para arrancar, pero nunca el `EXTRA_DEVICE` que viene dentro, así que la app se abría sola
  y se quedaba esperando el botón. Ahora:
  - `MainActivity` lee el `UsbDevice` del intent en `onCreate` y `onNewIntent`, y lo pasa al
    ViewModel (`onDeviceAttachedByIntent`). Manifest con `launchMode="singleTop"` para que
    reconectar con la app en primer plano no cree una segunda pantalla.
  - El ViewModel escanea al arrancar (`autoConnect`), que cubre abrir la app con el amp ya
    enchufado — ahí no hay ni intent ni broadcast que dispare nada.
  - El broadcast de `USB_DEVICE_ATTACHED` ya funcionaba y no se tocó.
  - `connect()` es ahora el punto único de los tres caminos, con guard de idempotencia.
  «Buscar dispositivo» sigue estando como respaldo manual.
  - ✅ **Confirmado en el amplificador real, en los tres escenarios**: (1) app cerrada,
    reconectar el amp saca el diálogo de selección de Android y al aceptar la app abre **ya
    conectada**; (2) app abierta en segundo plano, conectar el amp la conecta sola; (3) abrir
    la app desde el launcher con el amp ya puesto, también se conecta sola. En ninguno hizo
    falta tocar «Buscar dispositivo».

- **2026-09-02** — **Presence controlable, `60 00 06 56`.** ✅ **Confirmado con audio real**:
  mover el slider cambia el brillo/presencia del sonido de forma audible, y la perilla física
  reporta por esa misma dirección con Edit Mode activo. Lectura y escritura consistentes.
  - **A la primera, esta vez.** La candidata "alta" del bloque `60 00 06 5x` funcionó sin
    necesidad de descartar la baja (`60 00 00 27`), que queda **sin probar**, documentada solo
    como candidata no verificada por si Presence diera problemas más adelante — no debería
    hacer falta.
  - El rango `0..100` es una **suposición declarada**, no un dato: ver la nota técnica de
    abajo. Anotado en el KDoc de `PRESENCE_LEVEL_RANGE` como recordatorio.
  - Segundo parámetro sobre `KatanaParameter`: fue una línea en `KatanaRepository` más el
    slider. El modelo genérico se sostiene.
  - **Quedan 4 efectos por probar: Boost, Mod, FX, Delay.** Cada uno con su candidata alta del
    bloque `60 00 06 5x` a probar primero (`06 57`, `06 58`, `06 59`, `06 5A`), **sin asumir
    que el patrón se sostiene solo porque funcionó dos veces seguidas**.

- **2026-09-02** — **Boost (`60 00 06 57`) y Mod (`60 00 06 58`) controlables.**
  ✅ **Confirmados con audio real**: mover cada slider cambia el sonido, y **ambos se
  actualizan al girar la perilla física** del amplificador (con Edit Mode activo). Con esto
  son **cuatro de seis** niveles confirmados: reverb, Presence, Boost y Mod.
  - El `Unimplemented:` con que `booster.yaml` repite `60 00 06 57` resultó ser ruido:
    TuxKatana no la usa, el amplificador sí la acepta. Ver la nota técnica.
  - Quedan FX y Delay, en «En progreso».

- **2026-09-02** — **FX (`60 00 06 59`) y Delay (`60 00 06 5A`) cableados**, pendientes de
  confirmar por oído. Con ellos, `LevelCandidates` se queda vacío y **se elimina**: los seis
  niveles tienen ya constante propia documentada.

- **2026-09-02** — **UI reorganizada en dos secciones con menú hamburguesa.**
  - **El bug**: la consola de logs había desaparecido de la pantalla. Causa: todo vivía en una
    sola `Column`; con ocho botones y cuatro sliders de altura fija, al `weight(1f)` de la
    consola no le quedaba espacio que repartir. No era un fallo de la consola sino del
    reparto de altura.
  - **La solución**: `ModalNavigationDrawer` con dos secciones —**Logs** (acciones + consola +
    copiar al portapapeles) y **Sliders** (los seis niveles, con scroll)—. Al no competir por
    la altura, la consola vuelve a tener sitio, y añadir el séptimo slider no puede volver a
    romperla.
  - **Edit Mode pasa de dos botones a un `Switch`**, que es lo que siempre fue: un estado, no
    dos acciones. Sigue siendo explícito y visible con vuelta atrás obvia (CLAUDE.md §4.2).
  - Botones acortados (Buscar, Handshake, Identity, Nombre, Presets, Dump) y colocados en un
    `FlowRow` que envuelve solo, en vez de filas hechas a mano.
  - Sin dependencias nuevas: el icono ☰ es un glifo en `strings.xml`, no `material-icons`.

- **2026-09-03** — **FX (`60 00 06 59`) y Delay (`60 00 06 5A`) confirmados con audio real.**
  Con ellos, **los seis niveles de efecto del Katana Mk2 están cerrados**: reverb, Presence,
  Boost, Mod, FX y Delay, los seis con lectura, escritura audible y actualización desde la
  perilla física con Edit Mode activo.
  - Sobre Delay escribí que quedaba "descartada" la sospecha del `glob_mix_lvl`. **Era
    incorrecto**: ver la entrada del 2026-09-03. La prueba de audio se hizo con el delay 2
    apagado, y así el mix global y el nivel del delay 1 son indistinguibles.
  - **Ninguna de las direcciones "bajas" llegó a hacer falta.** Se conservan documentadas
    (`FX_LEVEL_LOW`, `DELAY_LEVEL_LOW`, etc.) por si algún parámetro diera problemas más
    adelante, pero las seis candidatas altas acertaron a la primera.
  - Seis de seis para el patrón del bloque de perillas. Sigue sin ser una regla: ver la nota
    técnica sobre lo que eso permite y lo que no.

- **2026-09-03** — **Toggle de Edit Mode en las dos secciones.** Estaba solo en Logs, que es
  el sitio equivocado para el caso de uso real: los sliders **dejan de seguir a las perillas
  físicas** sin Edit Mode, y tener que cambiar de sección para descubrir por qué no se mueve
  nada era un pequeño bug de usabilidad en sí mismo. Un único `EditModeToggle` compartido
  sobre un solo `StateFlow`, así que mover cualquiera de los dos interruptores mueve el otro.

- **2026-09-03** — **Rangos de los sliders probados contra el amplificador y aceptados.** En
  las seis perillas el slider llega a 100 cuando la perilla física está *a punto* del tope,
  quedando un tramo mínimo de recorrido. **No está establecido** si el 100 real está en el
  tope físico o si ese resto es holgura mecánica: la diferencia entre 98 y 100 es inaudible y
  no se puede zanjar de oído. **Decisión del dueño del amplificador: se da el rango por
  bueno** y `0..100` se queda. Anotado en los seis `*_LEVEL_RANGE`, junto con la prueba que sí
  lo zanjaría si algún día importa —girar la perilla al tope con edit mode activo y leer el
  valor que reporta el amp—, que no necesita el oído.

- **2026-09-03** — **Corrección: `60 00 06 5A` es el mix global de los dos delays**, no el
  nivel del delay 1. El Katana Mk2 tiene **dos** delays y **una sola perilla DELAY** en el
  panel, y esa perilla ajusta el mix entre ambos. Sigue en el mismo sitio de la app y el
  slider es correcto; lo que cambia es lo que dice la documentación que hace.

- **2026-09-03** — **Gain, Volume, Bass, Middle y Treble cableados** (`60 00 06 51`–`06 55`),
  pendientes de confirmar por oído. Once niveles en total sobre `KatanaParameter`; la pantalla
  de Sliders no necesitó ni un cambio porque itera `LevelId.entries`.
  - **`LevelId` reordenado al orden físico del panel**, que resulta ser también el de las
    direcciones: Gain, Volume, Bass, Middle, Treble, Presence, Boost, Mod, FX, Delay, Reverb
    = `06 51` … `06 5B`. Antes empezaba por Reverb, que era el orden en que se fueron
    descubriendo, no el del amplificador.
  - **Amp Type (`60 00 06 50`) queda fuera a propósito**: es un selector de tipo de
    amplificador, no un nivel continuo. Documentado en `KatanaAddresses.AMP_TYPE` para que su
    ausencia no parezca un olvido.

- **2026-09-03 — ✅ Gain, Volume, Bass, Middle y Treble confirmados con audio real.** Las
  cinco cambian el sonido audiblemente (ganancia, volumen, graves, medios, agudos) y las
  cinco perillas físicas actualizan sus sliders con Edit Mode activo. Probadas las cinco
  juntas en una sesión, sin necesitar ir de a una — cada una tiene una firma auditiva
  inconfundible.
  - **Gain y Volume acertaron pese al conflicto de fuentes.** `Adresses.txt` afirmaba, con
    flechas explícitas, que `60 00 06 51` era *read status* y `60 00 00 22` la de escritura —
    justo el patrón "escritura baja / reporte alto" que ya había fallado con el reverb. El
    audio dice que la alta es la de control, como en las otras nueve. Las direcciones bajas
    (`GAIN_LEVEL_LOW`, `VOLUME_LEVEL_LOW`, `BASS_LEVEL_LOW`, `MIDDLE_LEVEL_LOW`,
    `TREBLE_LEVEL_LOW`) quedan documentadas sin haber hecho falta ninguna.
  - **Con esto, los once niveles continuos del bloque de perillas del panel están cerrados**
    (`60 00 06 51`–`06 5B`): Gain, Volume, Bass, Middle, Treble, Presence, Boost, Mod, FX,
    Delay, Reverb. Solo queda `60 00 06 50` (Amp Type), que no es un nivel — ver «Por hacer».

- **2026-09-03** — **Investigación completa de los controles tipo enum**, con cita de fichero
  y línea para cada dato (CLAUDE.md §5). Resultados que corrigen o amplían lo que había:
  - **Amp Type son dos direcciones, no una.** `60 00 06 50` es la **posición de la perilla**
    (`00`..`04`, cinco categorías) y `60 00 00 21` el **modelo** (30 valores con huecos).
    `amplifier.yaml:3` llama a la primera `am_num`. Se cablean las dos para distinguirlo en
    una sola sesión.
  - **La tabla de tipos de `midi.xml` tiene 30 entradas, no 29**: a `amplifier.yaml` y a
    `Adresses.txt` les falta **BG Lead (`0x10`)**. Hay un 31.º, `0x19` "Custom", que solo
    aparece en el bloque de conversión y no en la tabla de la dirección — documentado y
    excluido.
  - **`60 00 06 39` es de Boost, no de Reverb.** No era un error de transcripción:
    `midi.xml` lo llama `Booster GRY color select` y `Adresses.txt:70` lo pone dentro de la
    sección BOOSTER. El color de Reverb en Mk2 es `60 00 06 3D`.
  - **Nada del color estaba confirmado**, al contrario de lo que se creía al empezar. La
    dirección que había en el proyecto como `REVERB_ACTIVE_COLOR` (`60 00 12 14`) resultó ser
    del mapa **MK1** — `color_assign.json:74-84` la define como parte de `colorActiveIndex`,
    `60 00 12 10`–`14`—, y solo se usaba como vector de checksum en un test. Renombrada a
    `EFFECT_COLOR_MK1` y conservada como plan B.
  - **On/off de reverb encontrado donde no lo había**: `60 00 05 40`, que `Adresses.txt` no
    menciona y solo dan `reverb.yaml:2` y `midi.xml`.

- **2026-09-03** — **`KatanaEnumParameter`, el segundo tipo de control** (CLAUDE.md §4.3).
  Se extrajo de `KatanaParameter` la maquinaria común a una base sellada `KatanaControl`
  —caché, GET, SET optimista, regla anti-eco— y cuelgan de ella los dos tipos: continuo
  (clampea, con debounce) y selector (rechaza lo que no está en su lista, sin debounce).
  Tablas de valores `AmpType` / `AmpCategory` / `EffectColor` en `protocol/`, Kotlin puro.
  **18 tests nuevos** (98 en total, todos pasan): tablas bien formadas, los bytes de cada
  familia de SET, rechazo de valores ilegales, y que selectores y niveles no se pisen.

- **2026-09-03** — **UI de la sección Sliders reorganizada**: una sección de amplificador
  (categoría en chips, modelo en desplegable, variación, y los seis niveles de amp/EQ) y una
  **tarjeta por efecto** con su on/off, sus tres chips de color y su slider. Un banner dice
  que los selectores están sin confirmar, para que un control que no responde no se lea como
  app rota. Ningún selector aparece preseleccionado hasta que el amp responde: con una
  dirección sin confirmar, el estado honesto es "desconocido".

- **2026-09-03 — ✅ Los selectores confirmados con el amplificador, salvo uno.** Funcionan
  **on/off y color de los cinco efectos** (y reportan: pulsar el botón físico actualiza la
  app), **el cambio de canal por perilla** (`60 00 06 50`, cinco categorías) y **la lista
  completa de modelos** (`60 00 00 21`, los cinco base más variaciones más "sneaky amps").
  - **Dos reglas de trabajo implícitas quedan derogadas.** Se había asumido que solo el
    bloque alto `60 00 06 5x` era escribible, porque era lo único confirmado hasta ahora. No
    es así: las diez direcciones de color y on/off, más `60 00 00 21`, son todas "bajas" y
    funcionan. Ese bloque no tiene nada de especial — es simplemente donde están las
    **perillas del panel**.
  - Y queda resuelta la duda del sentido de los on/off: **`00` apaga, `01` enciende**, como
    en todo lo demás. La lectura literal de `Adresses.txt:81` (`[00|01] # [ON|OFF]`) era
    engañosa.

- **2026-09-03 — ❌ `60 00 06 5C` (variación) es de SOLO LECTURA, y arreglado por otra vía.**
  Reporta bien —el botón físico actualiza la app al instante— pero ignora la escritura.
  - **El síntoma es reconocible y conviene recordarlo**: al mover el switch se encendía y
    **volvía solo a apagado**. No era un fallo del control sino la app funcionando bien: la
    escritura optimista pone la caché en `01`, el amp ignora el SET y sigue reportando su `00`
    real por esa misma dirección, y el camino de mensajes espontáneos lo aplica. **Un valor
    que rebota solo es la firma de una dirección de solo reporte.** Solo se ve porque el edit
    mode y la actualización desde el amp están cableados.
  - `midi.xml:44107-44110` la etiqueta `abbr="led state"` —estado de un LED, no un control—,
    que en retrospectiva ya lo decía. Lo mismo cabe esperar de `06 5D`–`06 61`.
  - **Arreglo sin buscar otra dirección de variación**: los cinco canales base tienen su
    gemelo `Var [...]` (`0x1C`–`0x20`) en la lista de modelos, que sí acepta escritura. El
    switch ahora **lee `06 5C` y escribe `00 21`**. Es un caso real de "leer en una dirección
    y escribir en otra" — el patrón que se descartó para el reverb por ser una suposición; la
    diferencia es que aquí está **medido** en las dos direcciones.
  - `AmpCategory` gana `baseValue` / `variationValue` / `typeValue(variation)` y `AmpType`
    gana `category` / `isVariation`. **6 tests nuevos** (104 en total, todos pasan).

- **2026-09-03 — ✅ Switch de VARIATION funcionando**, escribiendo por el modelo
  (`60 00 00 21`) en vez de por `60 00 06 5C`. Confirmado con el amplificador. Se mantiene
  deshabilitado con los "sneaky amps" seleccionados — y resultó ser lo correcto por una razón
  que no conocía al decidirlo: **esos modelos se rompen si se les activa la variación o se
  cambia de canal**, según el dueño del amplificador.

- **2026-09-03 — ✅ Corregida la escala de los cinco niveles de efecto: `0` = Off y `1..101`.**
  `LevelScale` (Kotlin puro, 9 tests) traduce entre el byte crudo y lo que se muestra, y
  `KatanaParameter` gana la cara de display (`displayValue` / `setLevel`) junto a la cruda
  (`state` / `set`). Los seis niveles de amp/EQ siguen con crudo = mostrado.
  - **Cierra el síntoma del rango que se había atribuido a holgura mecánica**: el slider
    llegaba a 100 con la perilla física un pelo antes del tope porque el crudo 100 que mandaba
    la app es el 99 del amplificador. La "suposición razonada" del rango lo era, y estaba
    desplazada en uno.
  - Corroborado de forma independiente por la UI de PC de **Boss Tone Studio**, que muestra
    esos cinco como "Off" y luego 0..100, además de por `midi.xml:44088-44106`.
  - Los once `*_LEVEL_RANGE` se sustituyen por dos escalas, `PANEL_LEVEL_SCALE` y
    `EFFECT_LEVEL_SCALE`: la discusión del rango estaba repetida once veces y ahora vive en
    dos sitios. **117 tests, todos pasan.**

- **2026-09-03 — ✅ La escala corregida verificada en el amplificador.** Con el slider al 100,
  el hueco que quedaba hasta el tope de la perilla física **se redujo**, que es exactamente lo
  que predice haber quitado un paso de desfase.
  - **Y queda explicado lo que sobra.** El hueco no desapareció del todo, pero no hace falta
    un segundo desfase para justificarlo: **Presence (`60 00 06 56`) tiene escala directa,
    nunca estuvo desplazado, y mostraba el mismo hueco desde el principio**. Eran dos cosas
    sumadas —un desfase de software y holgura mecánica del potenciómetro— y solo la primera
    era arreglable.
  - Con esto se cierra una duda que llevaba abierta desde el 2026-09-03 por la mañana, cuando
    se decidió "dar el rango por bueno" sin poder distinguir las dos causas.

- **2026-09-03 — Decisión cerrada: no se usa kshoji/USB-MIDI-Driver ni ninguna librería MIDI
  de terceros** (CLAUDE.md §6). Llevaba tiempo como "recomendación pendiente de confirmar";
  ahora es una constatación, no una predicción, porque **el transporte y el protocolo enteros
  están implementados y probados contra el amplificador sin ella**.
  - Lo que aportaría —enumeración, permisos, `claimInterface`, bucles de `bulkTransfer`,
    empaquetado USB-MIDI— son unas decenas de líneas en `usb/`, con tests JVM propios.
  - **El handshake es específico de Boss** y no lo cubre ninguna librería genérica: habría que
    añadirlo por fuera igual.
  - Añadiría su abstracción de "puertos MIDI" encima de `KatanaLink` / `KatanaControl`, que ya
    existe y es más pequeña.
  - No se descarta por principio: si algún día hay que hablar con otros dispositivos MIDI
    class-compliant, la evaluación se rehace.

- **2026-09-03 — ✅ La escala `Off + 1..101` confirmada por lo que el amplificador reporta**,
  no solo por documentación. En la lectura inicial al conectar:
  - **Reverb reportó crudo `101`** (`0x65`) → mostrado 100. El tope del rango es real y es el
    que dice `midi.xml`. Esta era exactamente la prueba anotada como pendiente desde el
    principio —"girar la perilla al tope con edit mode y leer lo que reporta"—, resuelta sin
    tener que girar nada.
  - **El `0` es Off de verdad**: los tres efectos con nivel crudo `0` (Boost, Mod, FX)
    reportaron su on/off en `0`, y los dos con nivel distinto de cero (Delay 78, Reverb 101)
    lo reportaron en `1`. Cinco pares de direcciones independientes con correlación perfecta.
    Es una sola muestra: corrobora fuerte, no demuestra. Falta ver un efecto **encendido y al
    mínimo**, que debería reportar crudo `1`.

- **2026-09-03 — Handshake: investigación CERRADA.** Se probó la última hipótesis —la trama
  con los bytes de versión de firmware que el propio amplificador reporta, byte a byte
  idéntica a su Identity Reply— y **tampoco respondió**:

  ```
  → F0 7E 00 06 02 41 33 03 00 00 06 00 00 00 F7   (x2, ~4 ms, 20 B en el cable cada una)
  ← nada
  ```

  - El criterio de cierre estaba **fijado antes de ver el resultado**, que es lo que hace que
    este "no" cuente. Queda cerrado, **no** en "baja prioridad": ningún flujo implementado lo
    necesita, y no se retoma salvo que aparezca una razón concreta.
  - El botón «Handshake v-real» se conserva en la pantalla de diagnóstico para poder
    reproducir la prueba, no como tarea pendiente.
  - Ramas sin explorar, anotadas por si algún día hace falta: que el Katana simplemente no lo
    necesite (el handshake viene de `MS3.h`, para el **MS-3**, con model id `3B`), que
    responda por un endpoint o interfaz que no miramos, o que la trama lleve algo más que no
    está en la librería de referencia.

- **2026-09-03 — ✅ HECHO Y VERIFICADO: el estado se puebla con un dump, no con 24 GET en
  serie.** `protocol/MemoryDump.kt` (Kotlin puro, 9 tests) indexa por dirección los trozos que
  devuelve el amp; `device/model/AmpState.kt` (10 tests) es la instantánea de dominio de los
  24 parámetros; `KatanaRepository.loadFromDump()` los junta y deja un GET individual solo
  para lo que el dump no cubra. **143 tests, todos pasan.**

  **Resultado contra el amplificador real:**
  - **24 de 24 controles poblados del dump, 0 con GET de respaldo.** 8 mensajes, 1860 B.
  - **Valores idénticos a los de la lectura individual previa**: gain 58, volume 100,
    EQ 38/43/45/65, delay 77, reverb 100, Brown, delay on/amarillo, reverb on/verde. El
    bloque de perillas `06 50`–`06 5B` cae en el trozo que empieza en `60 00 05 53`, offset
    125.
  - Se resuelven dos incógnitas que se habían anotado antes de probar: **los on/off y el
    modelo de amplificador sí vienen en el dump**, pese a ser direcciones "bajas".

  **Latencia, en tres actos:**

  | | Peticiones | Tiempo hasta poblar |
  | --- | --- | --- |
  | 24 GET en serie | 24 | 539 ms |
  | Dump, 1.ª versión | 1 | **3013 ms** ❌ |
  | Dump, corregido | 1 | **~300 ms** ✅ |

  La primera versión esperaba siempre la ventana fija de 3 s aunque los 8 mensajes llegaran
  en 275 ms — o sea que **empeoraba** justo la métrica que quería mejorar.
  `sendAndCollectUntilQuiet` corta tras ~250 ms de silencio (el hueco entre mensajes es de
  ~30 ms) con tope de 3 s. Confirmado en el amplificador: el resumen aparece a los ~300 ms.

  **Detalles de diseño que conviene no perder:**
  - **El dump no es un bloque contiguo** y el código no lo asume: varios mensajes con su
    propia base y longitud, y ni el número ni el total son fijos. Una dirección ausente es
    `null` —respuesta normal—, no un error.
  - **Aplicar el dump recorre la lista de controles del repositorio**, no los campos de
    `AmpState`. Así añadir un parámetro no puede dejarse a medias: no hay volcado que
    actualizar.
  - Los niveles salen de `AmpState` ya en unidades de presentación, con el desplazamiento de
    los cinco efectos aplicado.
  - **Menos ruido en el log**: los trozos del dump ya no salen como `entrante … sin control
    asociado`. Un control es un byte; un mensaje más largo es un bloque.

- **2026-09-03 — ✅ Selector de canal/preset activo, confirmado con el amplificador real en
  las dos direcciones.** Dirección `00 01 00 00` (`KatanaAddresses.ACTIVE_CHANNEL`), 9 valores
  `0`..`8` (Panel + Banco A 1-4 + Banco B 1-4), siguiendo al pie la investigación de CLAUDE.md
  §5.1. **Escritura**: elegir un chip en la app cambia el canal real del amplificador, en los
  8 canales (1A–4A, 1B–4B) más Panel. **Lectura/reporte**: cambiar de canal físicamente en el
  amplificador actualiza el chip seleccionado en la app. Confirma de una vez la dirección, los
  9 valores, que el SET de 2 bytes funciona (nunca se probó uno de 1) y que el reporte
  espontáneo llega igual que con el resto de selectores.
  - Reutiliza `KatanaEnumParameter` —caché, GET, SET optimista, regla anti-eco— igual que el
    resto de selectores; chip en la sección Sliders, sección propia "Canal", sin botón GET
    explícito (igual que amp category/type/variation: se puebla por el GET de respaldo del
    dump y por el reporte espontáneo).
  - **Generalizado `KatanaControl` a un `byteWidth` configurable** en vez de crear un tipo
    nuevo. Es el único control de todo el proyecto cuyo dato son 2 bytes
    (`CURRENT_PRESET_LEN = 0x02` en `globals.py:15`, frente a 1 byte en todo lo demás), así
    que la maquinaria de caché/GET/SET/anti-eco se generalizó con un parámetro `byteWidth: Int
    = 1` (por defecto 1, cero cambios de comportamiento para los 24 controles existentes) en
    vez de duplicar esa maquinaria en una clase aparte. `read()`/`set()`/`applyIncoming()`
    pasaron de `byteArrayOf(x.toByte())` / `data.firstOrNull()` a
    `MidiBytes.encode/decode(_, byteWidth)`, que ya eran genéricos en N bytes y no necesitaron
    tocarse. El filtro de `KatanaRepository.onIncoming` que descartaba bloques largos pasó de
    "`!= 1` byte" a "`!in 1..2` bytes".
  - **Resuelto**: el SET de 2 bytes funciona tal cual, así que la duda de si 1 byte también
    habría bastado queda cerrada sin necesidad de probarlo — no hacía falta.

- **2026-09-03 — ✅ Tipo de efecto por slot de color: resuelto en Booster, extendido a Mod y
  FX.** Era el bloque 1 del roadmap y bloqueaba todo el tuning avanzado.
  - **La duda que ninguna fuente respondía —¿se escribe en el slot de color (`60 00 06 24`) o
    en el "tipo activo" (`60 00 00 11`)?— la contestó el amplificador: manda la de tipo
    activo.** Confirmado con audio: cambiar el tipo cambia el sonido, se corresponde con el
    color encendido en el panel, y sincroniza en las dos direcciones (tocar el color o el tipo
    en el amp actualiza la app, y al revés).
  - El precedente que obligaba a instrumentar las dos —`60 00 06 5C`, del mismo bloque `06 xx`
    y de solo lectura— **no se repitió**, y encima al revés de lo esperado: allí mandaba la
    alta, aquí la baja. Tercera vez que dos direcciones plausibles solo se distinguen probando
    (reverb, Gain, y ahora esta).
  - **Implementado**: `BoostType` (23 valores, hueco real en `0x07`) y `ModFxType` (31 valores,
    diez huecos), este último **compartido por Mod y FX porque son literalmente la misma lista**
    en las dos fuentes — `mod.yaml:11-42` = `fx.yaml:11-42`, y los bloques de `midi.xml`
    diffean limpio. Nueve controles nuevos de slot de color (3 efectos × 3 colores) que no se
    escriben pero vienen en el dump y reportan.
  - **UI**: desplegable de tipo dentro de la tarjeta de cada efecto, justo bajo los chips de
    color, porque son lo mismo visto de dos maneras: el color elige el slot y el tipo dice qué
    hay dentro. Se retiró el andamiaje del experimento (los cuatro GET y el botón de reintento)
    ahora que la pregunta está cerrada.
  - ⚠️ Mod y FX **sin confirmar todavía**: usan la dirección gemela de la de Booster. Pruebas
    concretas en "Pendiente por probar".

- **2026-09-04 — ⚠️ Tipo de efecto extendido a Delay y Reverb — implementado, sin confirmar
  todavía.** Mismo patrón exacto que Booster: se escribe en la dirección de "tipo activo"
  (`DELAY_TYPE_ACTIVE` `60 00 05 01`, `REVERB_TYPE_ACTIVE` `60 00 05 41`), no en el slot de
  color. `DelayType` (11 valores, `00`-`0A`, sin huecos) y `ReverbType` (7 valores, `00`-`06`,
  sin huecos), los dos con dos fuentes de Mk2 de acuerdo. Slots de color por si acaso
  (`DELAY_TYPE_BY_COLOR` `06 2D`-`2F`, `REVERB_TYPE_BY_COLOR` `06 30`-`32`), mismo desplegable
  en la tarjeta de cada efecto que ya tenían Booster/Mod/FX. Con esto **los cinco efectos
  tienen tipo cableado**; no se tocaron los parámetros internos de ningún tipo (tarea aparte).
  - ⚠️ **Sin confirmar todavía**: es la misma extrapolación que ya se hizo con Mod y FX —
    dirección gemela de la confirmada, sin haberla probado. Prueba concreta en "Pendiente por
    probar".

- **2026-09-03 — ⚠️ Recarga completa del estado al cambiar de canal — implementado, sin
  confirmar todavía.** Cada canal (1A–4A, 1B–4B, PANEL) tiene sus propios valores, así que sin
  esto la app seguía mostrando los del canal anterior — peor que no mostrar nada, porque
  parece que el amplificador dice una cosa cuando dice otra. Al detectar un cambio en el canal
  activo se relee **el dump entero**, que ya cubre de una sola petición todos los controles
  registrados (niveles, modelo de amplificador, colores, on/off y tipos de efecto). Prueba
  concreta en "Pendiente por probar".
  - **Da igual quién cambió el canal**: se observa el estado del control, así que entra tanto
    el cambio desde la app como el del footswitch físico.
  - **No hay bucle**: el canal vive en `00 01 00 00`, fuera del dump, así que recargar no lo
    reescribe; y un `StateFlow` no reemite un valor igual, así que el GET de respaldo que lo
    relee no dispara otra recarga.
  - **`collectLatest` + 300 ms de margen**: pasar 1A→2A→3A rápido hace **una** recarga, la del
    canal donde te quedaste. El margen además le da tiempo al amp a cambiar de canal de verdad
    antes de preguntarle en qué estado quedó.

- **2026-09-04 — ⚠️ Los 9 parámetros internos de Booster (`60 00 00 10`–`18`) — implementados,
  pendientes de confirmar con audio.** Drive, Bottom, Tone, Solo Sw, Solo Level, Effect Level
  y Direct Mix son nuevos (On/Off y Type ya estaban). CLAUDE.md §5.2, dos fuentes de Mk2 de
  acuerdo en las siete direcciones (`booster.yaml:4-9`, `midi.xml:37109-37304`).
  - **`60 00 00 12` estaba mal etiquetada desde antes**: se documentaba como `BOOST_LEVEL_LOW`,
    "alternativa baja de la perilla del panel, sin probar". Con el bloque interno completo
    entendido, no es una alternativa a nada — es Drive, el parámetro de distorsión del propio
    Booster. Renombrada a `BOOST_DRIVE`, sin cambiar la dirección.
  - **Bottom/Tone no necesitaron tocar `LevelScale`**: son un rango centrado (`00/64` mostrado
    `-50..+50`, `midi.xml`), y el `rawOffset` que ya tenían `direct`/`offThenOneBased` es
    exactamente `raw = display + 50`. Solo hizo falta una tercera factoría,
    `LevelScale.centered(radius)`, sin añadir ningún campo a la clase — confirma la sospecha
    con la que se pidió esta tarea.
  - **UI**: los seis sliders (Drive 0-120, Bottom/Tone -50..+50, Solo/Effect/Direct Mix 0-100)
    y el switch de Solo se añadieron dentro de la tarjeta de Booster, debajo de su nivel de
    panel — visibles solo para ese efecto, ya que ningún otro tiene sus parámetros internos
    cableados todavía (bloque 2 del roadmap). `LevelControl` ganó un parámetro `valueRange`
    con default `0f..100f`, así que los sliders existentes no cambiaron.
  - **Custom Type y sus cinco parámetros (`60 00 00 19`–`1E`) quedan sin implementar a
    propósito**: es el modo "pedal custom", con su propio sub-catálogo, y menos prioritario.

- **2026-09-05 — Instrumentación de diagnóstico para tres controles que no hicieron nada:
  Solo del amplificador, Bright y Gain SW.** ⚠️ **Es solo instrumentación: no se corrigió
  ningún control ni se cambió ninguna dirección de las que ya estaban cableadas.** Mismo
  método que la investigación del reverb (CLAUDE.md §5).
  - **Parte 1 — la segunda candidata del Solo, aparte y sin sustituir a la primera.** Los dos
    controles del bloque PREAMP (`60 00 00 2B`/`2C`) **siguen exactamente donde estaban**; se
    añaden `ampSoloEnabledPanel` (`60 00 06 14`) y `ampSoloLevelPanel` (`60 00 06 15`) como
    controles registrados más, para poder probar las dos direcciones en la misma sesión y
    comparar. Registrarlos como controles normales —en vez de un envío suelto— es lo que les
    da gratis lo que hace falta en esta prueba: GET, escritura, población desde el dump (las
    dos direcciones caen dentro) y, sobre todo, **la aplicación de los reportes espontáneos**,
    que es lo que delata una dirección de solo lectura por el valor que "rebota solo"
    (§5, `60 00 06 5C`).
  - **Parte 2 — `KatanaControl.probeWrite`: SET seguido de GET inmediato.** Contesta la
    pregunta que el camino normal no puede: cuando un control no suena, ¿es que la dirección
    es correcta y el parámetro está inerte, o es que la dirección no acepta la escritura? Las
    dos se ven igual desde fuera. Es el mismo chequeo que resolvió `60 00 05 48` con el nivel
    de reverb. Devuelve un `WriteProbe` con `before`/`requested`/`after` y un `verdict` que
    nombra las cuatro conclusiones posibles (no contesta al GET · el valor cambió · el valor
    no se movió · ya estaba ahí, prueba no concluyente).
  - **Se aparta del camino normal en tres cosas, todas deliberadas**: no actualiza la caché
    con el valor pedido (`set` sí es optimista, y aquí eso falsearía el resultado — solo el
    GET mueve el estado), no aplica el debounce, y **cancela cualquier escritura pendiente**
    para que no se cuele entre los dos GET.
  - **UI**: una tarjeta "Diagnóstico (temporal)" en la sección de amplificador, con el switch
    y el nivel de la candidata 2, su GET, y botones de prueba SET+GET para Bright, Gain SW
    (los tres valores) y **las dos candidatas del Solo**. Dos botones por switch a propósito:
    con uno solo, la prueba no distingue nada si el amplificador ya estaba en ese valor.
  - **8 tests JVM nuevos (258 en total, 0 fallos)**, uno por cada conclusión que el
    diagnóstico tiene que saber distinguir, más los tres invariantes que hacen fiable la
    prueba: que no toca la caché, que cancela lo pendiente, y que un selector rechaza un valor
    ilegal sin mandar nada. `lintDebug` limpio.
  - ⚠️ **Nada de esto está probado con el amplificador todavía** — ver "Pendiente por probar",
    punto 10. La instrumentación existe precisamente para poder hacer esa prueba.

- **2026-09-05 — Formato `.tsl` mapeado: es JSON, y `presets_addrs.yaml` es el puente a las
  direcciones SysEx.** Tarea **sin código**, documental. Documentado en CLAUDE.md §5,
  "Formato `.tsl`". Verificado sobre un fichero real (`reference/FxFloorboard/default_mk2.tsl`).
  - ⚠️ **Corrección de la premisa: un `.tsl` no es un volcado binario, es JSON de texto.** No
    existen "offsets en bytes desde el inicio del fichero": los datos van en **claves de un
    objeto JSON** (`paramSet`), y cada valor es un array de strings hex de dos caracteres, un
    string por byte. La unidad de direccionamiento es el nombre de la clave.
  - ✅ **`presets_addrs.yaml` mapea clave → dirección SysEx + tamaño**, que es justo el "mapa de
    offsets" que hacía falta. 22 claves, 1141 bytes en total. **21 de los 22 tamaños del yaml
    coinciden exactamente** con el fichero real.
  - **Respuesta a "¿volcado directo o envuelto?": las dos cosas, por capas.** Los bytes de
    dentro **sí son 1:1 con SysEx** (crudos, sin checksum ni compresión ni escapado), pero el
    fichero **no es una imagen de memoria**: son 22 bloques con nombre, con **huecos reales de
    direcciones entre ellos** (8 B, 35 B, 12 B, 206 B…), y guarda **1141 de los 1992 bytes** que
    el editor pide en un volcado de patch (57 %). Un parser que asuma continuidad se desalinea
    en el segundo bloque.
  - ❌ **Fallo real del yaml: `UserPatch%Patch_1` dice `size: 50` y son 91.** Con 50 se
    perderían **el bloque de cadena entero, Solo, Contour y la posición de EQ2**. Confirmado por
    dos vías: el fichero real trae 91 bytes, y `sysxWriter.cpp:363-366` lo compone como
    `64 + 27`. 91 bytes desde `60 00 05 40` llegan exactamente a `60 00 06 1A`.
  - ✅ **Resueltas las nueve claves con `addr` vacío en el yaml** (TuxKatana sabía su tamaño pero
    no dónde vivían), con la aritmética de `sysxWriter.cpp:377-390` sobre el volcado de patch:
    `KnobAsgn` → `60 00 07 00`, `ExpPedalAsgn` → `08 00`, `GafcExp1Asgn` → `09 00`,
    `GafcExp2Asgn` → `0A 00`, `FsAsgn` → `0F 08`, `Patch_Mk2V2` → `0F 10`, y los tres `Contour`.
  - ⚠️ **Dos discrepancias que quedan como TBD**: `GafcExp1AsgnMinMax` (el yaml dice `09 30`, la
    aritmética `09 34`; sus dos hermanas caen en `08 30` y `0A 30`, y el yaml marca esa línea
    —solo esa— con `# ⚠️ Sequence not valuable +1`); y **los tres `Contour`**, donde
    FxFloorboard da `0F 2E`/`36`/`3E` y `midi.xml` da `0F 30`/`38`/`40`. Se documenta la de
    `midi.xml` por ser una afirmación directa frente a una aritmética que ya falla en el otro
    caso, pero hay que comprobarlo con un `.tsl` real de Boss Tone Studio.
  - ⚠️ **El `.tsl` no guarda Pedal Bend de Mod ni de FX**: `Fx(1)` acaba en `60 00 02 5C` y el
    bloque de Mod sigue hasta `02 60`. Son 8 direcciones documentadas que un export/import
    perdería. Podrían estar en `Patch_Mk2V2` (22 bytes sin desglosar), pero **es conjetura**.
  - ✅ **Dos comprobaciones de consistencia que salen bien y validan modelos previos**, ambas
    sobre datos reales del fichero: (1) el color de Booster vale `00` = verde, el tipo del slot
    verde vale `0A` y el tipo activo vale también `0A` — **el tipo activo refleja el slot del
    color encendido**, tal como predice §5.2; (2) la cadena por defecto es una **permutación
    completa de los 20 identificadores** en orden musicalmente sensato
    (`PDL → OD → FX1 → FX2 → EQ1 → EQ2 → CH_A → … → RV → CAB → …`), lo que confirma con un dato
    real el "array de permutación" que §5 dedujo de `midi.xml`.
  - **`device` y `formatRev` son discriminantes, no decoración**: `tsl.py:35` rechaza el fichero
    si `device != "KATANA MkII"`, y la revisión `"0002"` es la que indica que existe el bloque
    `Patch_Mk2V2`. ⚠️ **No todo `.tsl` es de Katana**: el otro fichero del repo tiene
    `device: "GT"` y un esquema completamente distinto (`liveSetData`/`patchList`).
  - ⚠️ **No hay fecha ni versión de firmware** en el formato. Si la app quiere fechar un export,
    el único campo libre es `memo`.

- **2026-09-05 — Guardado de presets: `7F 00 01 04` confirmado para el Mk2, y aparece una
  segunda vía por Control Change.** Tarea **sin código**, documental. Documentado en CLAUDE.md
  §5, "Guardado de presets". **Nada probado contra el amplificador.**
  - ✅ **`7F 00 01 04` es correcto y vale para el Mk2, no solo para el MK1.** Ya no es una
    anotación heredada de `katana_sysex.txt`: `reference/FxFloorboard/patchWriteDialog.cpp:346`
    (app de Mk2, en código) construye el mensaje literal
    `"F0410000000033127F00010400"+addr+"00F7"`. Se manda un **commit**, no los datos del
    preset: el amplificador escribe su búfer de edición actual en el canal destino.
  - ✅ **La numeración del canal destino es la misma de `00 01 00 00`** (§5.1): `00` PANEL,
    `01`..`08` los ocho canales. Se deduce cruzando dos sitios de FxFloorboard —la lista
    etiqueta la fila 0 como PANEL y la 8 como CH B4 (`:174-190`), y el envío manda el índice de
    fila tal cual (`:327`, `:341`)— y lo corrobora su ajuste para el Katana 50, que salta de
    `02` a `05` para los canales B en vez de renumerarlos. **Eso permite reutilizar el enum de
    canal que el proyecto ya tiene.**
  - ⚠️ **Corregido un rango incompleto de §5**: la tabla de direcciones clave decía
    "`00 xx`, xx = 01..04". Ese `01..04` era el rango del MK1 copiado tal cual (solo tenía
    cuatro canales). No era una contradicción —coinciden en el tramo compartido— pero se
    quedaba corto para el Mk2.
  - ⚠️ **El checksum que aparece en la fuente es un marcador.** El `00` del template lo
    recalcula `midiIO.cpp:504-512` antes de enviar, con la fórmula `128 - suma % 128` desde el
    byte 8 (el primero de la dirección, `checksumOffset = 8`). O sea, **el mismo checksum que
    ya calcula `RolandSysEx`**: no hay nada especial que implementar.
  - 🆕 **Segunda vía, por Control Change, que ninguna nota previa mencionaba**: CC#8 (valor
    127) guarda **en el canal actual** y CC#9 (valores 1..8) en uno concreto. Dos fuentes de
    Mk2 coinciden byte a byte: el PDF `MIDX_20_KatanaMKIIV1.pdf` y `midi.yaml:67-71` (que los
    tiene comentados, como `booster.yaml` tenía `60 00 06 57`, que funcionó igual).
    ⚠️ Dos cautelas: el PDF es del **MIDX-20, un puente MIDI de terceros**, no la
    implementación oficial de Boss, así que CC#8/#9 podrían ser cosa del puente; y **el
    proyecto hoy no puede mandar CC** (`packUsbMidi` solo emite los CIN `0x4`–`0x7`), haría
    falta una segunda ruta de empaquetado. **Por eso la vía SysEx es la que conviene
    implementar primero.**
  - **El guardado son dos pasos, no uno**: el nombre del preset (16 bytes ASCII en
    `60 00 00 00`) va **antes** del commit. El MK1 lo pone como paso explícito de la secuencia
    (`katana_sysex.txt:145-147`, y otra vez en `:1989`); FxFloorboard llega a lo mismo por otro
    camino, escribiendo el nombre como un parámetro normal (`renameWidget.cpp:68` con
    `nameAddress = "00"`). Coincide además con `presets_addrs.yaml:1-3` y `HOW.md:10`.
  - ❌ **Lo que ninguna fuente documenta**, y queda como TBD: si el guardado responde algo
    (todo apunta a fire-and-forget — FxFloorboard tiene el enganche a `sysxReply`
    **comentado** y cierra el diálogo al mandar); si `00` (PANEL) es un destino legal; si hay
    un intervalo mínimo entre guardados (el único retardo documentado, ~50 ms por dos fuentes,
    es alrededor del **edit mode**, no del guardado); y **qué dirección reporta "Saving in
    progress..."** — se buscó `saving`, `in progress`, `busy` y `write in progress` en las
    cuatro fuentes y no hay nada, la hipótesis es que es solo del panel.
  - **TuxKatana no sabe guardar**: sus `save()` son todos `.tsl` y volcados locales, y su
    `HOW.md:90` llega a decir "First write a preset ... with some other app". Solo conoce
    `7F 00 00 01` (edit mode). `katana-midi-bridge` tampoco implementa guardado.

- **2026-09-05 — Controles sin perilla física localizados: Noise Gate, Solo, Contour, EQ1/EQ2
  y la cadena de efectos.** Tarea **sin código**, documental. Cierra el punto 3 de la "Visión
  de alcance" a nivel de investigación. Documentado en CLAUDE.md §5, "Controles sin perilla
  física". **Nada probado contra el amplificador.**
  - **Los cinco grupos existen y tienen dirección**, todos en el espacio por preset `60 00 xx`:
    Noise Gate `60 00 05 66`–`68` (on/off + threshold + release), Contour `60 00 06 16`/`17`
    más tres slots en `60 00 0F 30`/`38`/`40`, posición de EQ1 `60 00 06 22` y de EQ2
    `60 00 06 19`, bloques internos de EQ1 `60 00 00 40`–`57` y EQ2 `60 00 00 60`–`77`, y la
    cadena en `60 00 06 00`–`06 13` más `60 00 06 20`.
  - **El hallazgo que lo desbloqueó todo: la tabla de destinos de asignación de `midi.xml`
    (`:3700-3995`) codifica direcciones.** Cada destino lleva `desc` = 3.er byte y
    `customdesc` = 4.º byte, o sea `60 00 <desc> <customdesc>`. ✅ **Verificado contra tres
    direcciones que este proyecto ya confirmó por audio** (Gain `06 51`, color de Booster
    `06 39`, Reverb `06 5B`), y cada dirección así obtenida reaparece como `<DATA>` en
    `<Structure>` con su rango — dos sitios independientes del mismo fichero que concuerdan.
    Es la razón de que la mayoría de estas direcciones tengan dos testigos y no uno.
  - ⚠️ **Solo es la única ambigüedad real: dos candidatas y ninguna fuente que desempate.**
    `60 00 06 14`/`15` (bloque `panel`, `name="Solo"`) contra `60 00 00 2B`/`2C` (bloque
    `PREAMP`). Misma forma las dos (on/off + nivel `0..100`), las dos distintas del Solo del
    Booster ya implementado. **Ninguna aparece en la tabla de asignación** —se comprobó
    explícitamente—, así que aquí falta el segundo testigo y solo el audio decide. Es el mismo
    patrón que costó tres intentos con el nivel de reverb.
  - ⚠️ **Los Contour por slot (`60 00 0F 3x`/`4x`) caen FUERA del dump**, y serían los primeros
    controles del proyecto en hacerlo: el dump pide hasta `60 00 0E 7F` y el amplificador
    devolvió hasta `60 00 0E 43`; ellos están en el offset 1968-1985. Caerían al GET individual
    de respaldo, que ya existe, pero conviene no descubrirlo como un bug. Todo lo demás de la
    sección sí cae dentro, comprobado offset por offset.
  - ⚠️ **La cadena SÍ se puede reordenar, y de dos maneras**: un selector de 7 cadenas
    predefinidas (`60 00 06 20`) y un **array de permutación de 20 direcciones**
    (`60 00 06 00`–`06 13`), cada una un selector de los mismos 20 identificadores de bloque
    (`OD` Booster, `FX1` Mod, `FX2` FX, `DD1`/`DD2`, `RV`, `EQ1`/`EQ2`, `NS_1`/`NS_2`, `LP`,
    `PDL`, `FV`, `CAB`, `CS`…). **Aquí el MK1 no sirve ni como referencia de estructura**: da
    la cadena como una sola dirección con tres valores.
  - ⚠️ **El EQ gráfico usa paso de 0,5 dB** (`range 00/30/-12.0/+12.0`, 49 valores crudos sobre
    24,0 dB), así que sus 22 bandas necesitan `FractionalLevelScale`, no `LevelScale.centered`.
    **No es el mismo que el Graphic EQ de Mod/FX**, que es `00/28/-20/+20` entero.
  - **La posición IN/OUT es de dos valores, no de tres**, y las dos direcciones usan palabras
    distintas para lo mismo ("Amp In/Out" contra "PreAmp In/Pre Amp Out"). El selector de
    **cuatro** posiciones (Input/Output/Line Out Only/Speaker Out Only) existe pero es del **EQ
    global**, que vive en el espacio `<System>` (`00 00 00 xx`) y no por preset.
  - ✅ **Resuelto de paso un cabo suelto de §5**: `60 00 06 18`, la primera candidata fallida
    del nivel de reverb cuyo GET "devolvía `07` fijo", es **`FS2 Func: Function`**, un selector
    de 8 valores. El `07` era el valor 8, no basura: la dirección siempre estuvo viva y lo que
    estaba mal era suponer qué había en ella.
  - **Qué aporta cada fuente**: `midi.xml` es la única que cubre los cinco. `Adresses.txt` no
    menciona ninguno (su sección `EQ:` es el Bass/Middle/Treble del panel). De TuxKatana solo
    sirve `presets_addrs.yaml`, y para dos cosas: `UserPatch%Eq(2)` da `60 00 00 60` + `size 24`
    —única dirección de la sección confirmada por una fuente distinta— y `UserPatch%Contour(1..3)`
    da `size: 2` por slot con el campo `addr` **vacío** (sabe que existen y cuánto ocupan, no
    dónde están). `katana-midi-bridge` es MK1: corrobora la estructura del Noise Gate (tres
    campos idénticos en `60 00 06 63`) pero no su dirección.

- **2026-09-05 — Extracción de Mod/FX terminada: los 31 tipos, parámetro a parámetro.**
  Segunda y última tanda de la tarea documental empezada el 2026-09-04 (entrada de abajo), que
  había dejado el índice de los 31 bloques pero solo cuatro tipos detallados. Tarea **sin
  código**, por encargo explícito. Documentado en CLAUDE.md §5.2, "Extracción completa de los
  31 tipos" y "Catálogo de valores de los selectores internos".
  - **Los 27 tipos restantes extraídos** con dirección, nombre, rango crudo↔mostrado, la
    `LevelScale` que les corresponde y la línea de `midi.xml` de cada `<DATA>`. Cablear
    cualquiera de los 31 es ahora copiar una tabla, sin reabrir el XML.
  - **Catálogo completo de los selectores internos** (36 parámetros, 33 listas distintas —
    tres repiten catálogo entre las dos voces). ✅ **Todos corren sin huecos**, comprobado por
    programa — a diferencia de `ModFxType` (10 huecos),
    `BoostType` (`07`) y `AmpType` (`19`).
  - **Regla `FX = Mod + 0x0200` reverificada, y con un resultado más fuerte**: comparando los
    237 nodos de cada bloque, **0 diferencias de dirección, 0 de nombre de parámetro y 0 de
    rango o lista de valores**. Las 205 diferencias de `desc` son 191 del prefijo `MOD `→`FX `
    más las 14 ya conocidas. Que el 14 salga igual en dos extracciones independientes es lo
    que permite seguir usando la regla sin comprobarla tipo por tipo.
  - ❌ **Corregidas dos afirmaciones de la primera tanda**, las dos por agrupar por el nombre
    del parámetro en vez de por su rango:
    - **Los `Pre Delay` de Pitch Shifter y Harmonist NO son de paso fraccionario.** Son
      `00/7F/00/127 ms` + `128/255` + `256/300`: milisegundos enteros, paso 1. La anomalía del
      paso de 0,5 ms afecta **solo** a 2x2 Chorus (`60 00 02 3A` y `02 3E`) — que es,
      justamente, el único que se cableó con `FractionalLevelScale`. La decisión tomada fue
      correcta; la justificación escrita era más amplia de lo que los datos sostenían.
    - **Los parámetros de 2 bytes son cinco, no cuatro.** Faltaba el `Repeat Rate` de DC30
      (`60 00 02 54`+`55`), que además es el más raro del lote: va en **rpm**, no en ms, y su
      **mínimo crudo es `0x28` (40), no cero** — una escala que dé por hecho que el recorrido
      empieza en 0 se sale por abajo. Su bloque anidado está **mal etiquetado en la fuente**
      (`midi.xml` lo llama `SDD:Delay Time(LSB)`, copiado del bloque de delay); la etiqueta
      miente, la dirección y el rango no.
  - **Tres anomalías nuevas** además de esa: el `Off/On` interno de Vibrato (`60 00 02 28`),
    distinto del on/off del slot; **tres radios de escala centrada** conviviendo (`±50` con
    crudo `00`..`64`, `±20` dB con crudo `00`..`28`, `±24` con crudo `00`..`30`), que solo el
    ancho del crudo distingue, no la etiqueta; y `Step Rate` del Phaser como **único**
    `offThenOneBased` de los 31 tipos.
  - ⚠️ **Bonus, sin cambiar nada todavía**: `MOD PEQ: Hi Cut Off` (`60 00 01 35`) es una
    tercera aparición de la lista de 15 frecuencias que el proyecto tiene duplicada en
    `DelayHighCutFrequency` / `ReverbHighCutFrequency` por una contradicción de `midi.xml`
    (`0A` = `"6.30K"` vs `"6.00k"`). Este tercer sitio dice **`6.00k`**: dos contra una. No se
    tocan los dos enums —sigue sin haber prueba de hardware— pero queda anotado por si algún
    día hay que apostar.

- **2026-09-04 — Mapa de parámetros internos de Mod y FX: extracción documental completa.**
  Tarea **sin código** por encargo explícito: dejar el XML leído de una vez para que cablear
  cada tipo después sea mecánico. Documentado en CLAUDE.md §5.2, "El mapa de parámetros
  internos de Mod y FX". Cierra el punto 2 de "lo que las fuentes no responden" de esa sección.
  - **Los 31 bloques indexados**, en el mismo orden que `ModFxType` (comprobado entrada por
    entrada contra `ModFxType.kt`, no supuesto), con dirección inicial y final, número de
    parámetros y el `desc` que los agrupa en `midi.xml`.
  - **Cuatro tipos extraídos parámetro a parámetro** con dirección, nombre, rango y línea:
    Tremolo (el caso mínimo, 4 niveles directos), Phaser, Flanger y 2x2 Chorus.
  - **Regla FX = Mod + `0x0200`**, verificada mecánicamente sobre los 237 nodos de cada bloque
    —no inferida de unas muestras—: comparados `(LSB relativa, 4.º byte, tipo, parámetro)`,
    los dos salen idénticos salvo 14 diferencias **solo de etiqueta**. Extraer FX aparte sería
    trabajo tirado.
  - **Seis anomalías señaladas explícitamente**, que hay que resolver antes de cablear los
    tipos afectados. La que más pesa: **`Pre Delay` (`range 00/50/0.0/40.0`, en pasos de
    0,5 ms) no es representable con la `LevelScale` actual**, que solo sabe desplazar con un
    entero — habría que darle un factor de escala. Las otras: parámetros de 2 bytes en Pitch
    Shifter y Harmonist (ya cubiertos por el `byteWidth = 2` del canal), las 24 direcciones de
    escala de usuario del Harmonist, Acu Processor cruzando el límite de página `01 7F`→`02 00`,
    y los nombres repetidos sin desambiguar en 2x2 Chorus y DC30.

- **2026-09-04 — Arreglado el bug de recarga al cambiar de canal**, implementando el diseño
  documentado el mismo día (ver "Notas y decisiones técnicas" para el detalle de cada pieza).
  Cierra los cuatro fallos encadenados de "Hallazgos de diagnóstico" (ya retirado de ahí):
  el filtro por dirección+tamaño en `sendAndCollectUntilQuiet`, `applyDumpValue` respetando
  `byteWidth`, y un único punto de disparo de recarga en el ViewModel con mutex y guard
  reevaluado tras el margen.
  - **Los tres criterios que antes fallaban ahora se comprueban en JVM**, con el mismo
    reproductor que diagnosticó el bug: un solo dump para 1A→2A→3A, máximo un dump en vuelo
    (incluso haciendo la recarga de conexión deliberadamente lenta para forzar el solape), y
    el canal en caché igual al del amplificador al final. Más un cuarto criterio nuevo: un
    reporte espontáneo emitido durante la ventana del dump aparece en `rejected`, no en
    `accepted`. 10 tests nuevos (208 en total, todos verdes), tres reruns sin fallos.
  - ⚠️ **Sin confirmar contra el amplificador real todavía** — ver "Pendiente por probar".
  - La fase 2 opcional (`Set` de direcciones tocadas durante el dump, para que un reporte
    espontáneo en vuelo no se pise con un valor del bloque tomado antes) **queda sin
    implementar a propósito**: no bloqueaba el arreglo principal.

- **2026-09-04 — ✅ Confirmado con audio real: los tipos de efecto de Mod, FX, Delay y
  Reverb, y los 9 parámetros internos de Booster.** Todo probado con Edit Mode activado.
  Cierra de golpe cinco entradas de "Pendiente por probar" (ya retiradas de ahí).
  - **Tipos de efecto** (`60 00 01 01` Mod, `60 00 03 01` FX, `60 00 05 01` Delay 1,
    `60 00 05 41` Reverb): la dirección de "tipo activo" acepta la escritura en los cuatro,
    igual que ya se había confirmado en Booster el 2026-09-03. **Los cinco efectos siguen el
    mismo patrón**, así que la analogía que se implementó a propósito sin darla por buena
    (CLAUDE.md §5.2) resultó correcta — esta vez.
  - **Los 9 parámetros internos de Booster** (`60 00 00 10`–`18`): Drive, Bottom, Tone,
    Solo Sw, Solo Level, Effect Level y Direct Mix, más el On/Off y el Type que ya estaban.
    Confirma de paso las dos cosas que se habían deducido de `midi.xml` sin probarlas: que
    `60 00 00 12` es Drive y no una "alternativa baja" de la perilla del panel, y que
    Bottom/Tone son la escala centrada `-50..+50` que se modeló con `LevelScale.centered(50)`.

- **2026-09-04 — Contrato de Edit Mode en la UI: apagado deja cambiar de canal y nada más.**
  Con edit mode apagado el amplificador no manda reportes espontáneos, así que la app no puede
  confirmar ningún parámetro que escriba; mostrar los controles como si funcionaran era
  mentirle al usuario. Ver el contrato en CLAUDE.md §4.2 y la prueba pendiente 2.
  - `SlidersPane` calcula `canEdit = connected && editMode` y lo pasa a **todo** menos al
    selector de canal y al propio interruptor de Edit Mode. Los parámetros —sliders del
    amplificador, categoría/modelo/variación, y las cinco tarjetas de efecto con su color,
    tipo, on/off, nivel y los internos de Booster— quedan grises y no responden.
  - **El selector de canal sigue habilitado a propósito**, con solo `connected`: es la
    excepción explícita del contrato. Un test JVM nuevo fija además que el SET del canal sale
    al cable sin condiciones, para que nadie meta un gate de edit mode en `device/` — donde el
    concepto no existe ni debe existir.
  - Un aviso en rojo bajo el interruptor explica por qué está todo gris. Un control
    deshabilitado sin explicación se lee como un bug; con una línea de texto se lee como una
    regla.
  - **Nuevo aviso en el log cuando el amplificador ignora un cambio de canal**
    (`reportIgnoredChannelWrite`): si la relectura dice que el amp sigue en otro canal, lo
    dice explícitamente y menciona si Edit Mode estaba apagado. Sin eso el síntoma es que el
    selector "vuelve solo" sin ninguna pista — la misma firma que delató que `60 00 06 5C`
    era de solo lectura (CLAUDE.md §5).

- **2026-09-04 — Cableados los cuatro controles que faltaban del bloque PREAMP**
  (`60 00 00 29`–`2C`): Bright, Gain SW, Solo Sw y Solo Level. Cierra la parte accionable de
  "Cambiar el tipo de amplificador no recarga nada" — ver esa nota para la tabla completa y
  la pregunta que queda abierta (si el amp recalcula o conserva al cambiar de tipo, que sigue
  sin implementación a propósito).
  - `KatanaAddresses.AMP_BRIGHT`/`AMP_GAIN_SW`/`AMP_SOLO_ENABLED`/`AMP_SOLO_LEVEL`, todas de
    una sola fuente Mk2 (`midi.xml:37483-37496`, ninguna otra fuente documenta este tramo del
    bloque). Bright y Solo Sw son switches (`SWITCH_VALUES`); Gain SW es el primer selector
    de tres posiciones del proyecto (`GAIN_SW_VALUES`, Low/Middle/High); Solo Level reutiliza
    `PANEL_LEVEL_SCALE`, la misma escala directa `0..100` de los paneles.
  - **Gain/Bass/Middle/Treble/Presence/Volume del preamp (`00 22`, `00 24`–`28`) no se
    duplicaron.** Ya tenían alias `*_LEVEL_LOW` documentados y sin usar; las direcciones
    altas (`06 51`–`06 56`) llevan confirmadas con audio desde 2026-09-03 y siguen siendo la
    fuente de verdad. Cablear las bajas habría sido un control repetido sin ningún beneficio.
  - En la UI, las cuatro filas nuevas van en `SlidersPane` justo debajo del switch de
    Variación, dentro del mismo bloque de amplificador — no en las tarjetas de efecto, porque
    son parámetros del preamp, no de un efecto. Todas caen bajo `canEdit` como el resto.
  - **Ya vienen en el dump** (`60 00 00 2x` cae de lleno en `60 00 00 00` + 1920 B): no hizo
    falta tocar el rango del GET, solo registrar los controles — confirmado leyendo el código,
    no hace falta probarlo aparte.
  - 2 tests JVM nuevos (211 en total): el SET de los cuatro sale con su checksum correcto y no
    se pisan entre sí, y `AMP_GAIN_SW` rechaza un valor fuera de sus tres posiciones sin
    escribir nada — mismo patrón de rechazo que el resto de selectores del proyecto.

- **2026-09-05 — Parámetros internos fijos de Delay 1 y Reverb** (CLAUDE.md §5.2, "DSP
  simple"), siguiendo el mismo patrón que Booster. ⚠️ Implementado, pendiente de confirmar con
  audio — ver "Pendiente por probar", punto 4.
  - **Delay 1**: Time (`60 00 05 02`–`03`, 2 bytes, `1..2000` ms), Feedback (`05 04`,
    `0..100`), High Cut (`05 05`, selector de 15 frecuencias), Effect Level (`05 06`,
    `0..120`) y Direct Mix (`05 07`, `0..100`).
  - **Reverb**: Pre Delay (`05 43`–`44`, 2 bytes, `0..500` ms), Low Cut (`05 45`, selector de
    18 frecuencias), High Cut (`05 46`, selector de 15 frecuencias), Density (`05 47`,
    `0..10` — no `0..100`, la excepción del bloque) y Direct Mix (`05 49`, `0..100`).
  - **Fuente única**: `reference/FxFloorboard/midi.xml`, bloques `desc="DD1:"` (Delay 1) y
    `desc="REV:"`/`"REVERB:"` (Reverb) — ninguna otra fuente de Mk2 documenta estos
    parámetros. Cada dirección lleva su cita de línea en el KDoc de `KatanaAddresses`.
  - **Dos direcciones deliberadamente NO cableadas**: Reverb Time (`60 00 05 42`) tiene un
    paso de 0.1s que `LevelScale` no puede representar sin extenderse (misma anomalía que el
    Pre Delay de Mod, sin resolver todavía); Reverb Effect Level (`60 00 05 48`) resultó ser
    la misma dirección que `REVERB_LEVEL_DERIVED`, ya probada como no funcional en la
    investigación del reverb (2026-09-02) — identificar qué era no la reabre como candidata.
  - **Un hallazgo de la propia fuente, documentado y no bloqueante**: el tramo `MSB=0x0E` de
    Delay Time (`midi.xml:42468`) da un ancho de crudo que no cuadra con el ancho mostrado —
    los otros 15 tramos son limpios, y el `REVERB_PRE_DELAY` gemelo también es limpio en los
    4 suyos. Lectura más probable: un error de copia en esa fila, no una resolución real
    distinta. Se cableó igual con `LevelScale.direct(1..2000)`; queda pendiente confirmar con
    audio específicamente el tramo `1792`-`1999` ms — ver punto 4.
  - **Dos catálogos nuevos que parecen la misma lista y no lo son**: `DelayHighCutFrequency`
    y `ReverbHighCutFrequency` (15 valores cada uno) difieren en `0x0A` (`"6.30K"` vs
    `"6.00k"`) — la propia fuente se contradice entre sus dos bloques. Se mantienen como
    catálogos separados a propósito. `ReverbLowCutFrequency` (18 valores) no tiene gemela.
  - UI: nuevas filas en las tarjetas de Delay y Reverb (`DelayInternalParams`/
    `ReverbInternalParams`), con los selectores de frecuencia como `DropdownSelector` — igual
    que el modelo de amplificador, por el número de opciones. Todo bajo `canEdit`.
  - 22 tests JVM nuevos (233 en total): checksums de cada SET nuevo, rechazo de los tres
    selectores de frecuencia fuera de catálogo, no-colisión entre los bloques adyacentes de
    Delay y Reverb, y los tres catálogos (tamaño, sin huecos, valores únicos).

- **2026-09-05 — `FractionalLevelScale`: desbloquea Reverb Time y el Pre Delay de 2x2 Chorus
  en Mod.** ⚠️ Implementado, pendiente de confirmar con audio — ver "Pendiente por probar",
  punto 5. Los dos habían quedado documentados y sin cablear porque `LevelScale` solo sabe
  sumar un desplazamiento entero, no dividir, y ninguno de los dos usa "un byte crudo = una
  unidad mostrada": Reverb Time tiene paso de 0.1 s, el Pre Delay de 0.5 ms.
  - **Tipo nuevo en vez de ampliar `LevelScale` a `Double`**: los ~230 tests que ya existían
    asumen `Int` en todo lo demás, así que se creó `FractionalLevelScale` aparte (con su
    `KatanaFractionalParameter` gemelo en `device/`) en vez de tocar el contrato del resto de
    controles. La fórmula `mostrado = displayRange.start + (crudo − rawRange.first) × step`
    no necesitó un campo de desplazamiento aparte para el `+1` de Reverb Time: sale solo de
    que `displayRange` empiece en `0.1`.
  - **Sin arrastre de error de punto flotante**: cada conversión ida-y-vuelta arranca desde
    un entero limpio en vez de acumular sobre la anterior. Test dedicado que repite el viaje
    50 veces sobre el mismo valor y comprueba que no se mueve del crudo original.
  - **Reverb Time (`60 00 05 42`)**: DSP simple, cableado sin condiciones, igual que el resto
    del bloque de Reverb ya implementado.
  - **Pre Delay de 2x2 Chorus (`60 00 02 3A` banda Low, `60 00 02 3E` banda High)**: primer
    parámetro interno de Mod cableado en código. A diferencia de Booster/Delay/Reverb ("DSP
    simple"), Mod es "DSP complejo" — cada tipo activo tiene su propio bloque de direcciones
    — así que estas dos direcciones **solo significan "Pre Delay" con el tipo activo en 2x2
    Chorus**; la UI oculta los sliders y muestra un aviso cuando el tipo activo es otro, en
    vez de dejarlos visibles y potencialmente mal etiquetados.
  - UI: `FractionalLevelControl` (gemelo de `LevelControl` para `Double`), añadido a la
    tarjeta de Reverb (antes de Pre Delay) y a una nueva tarjeta condicional de Mod.
  - 17 tests JVM nuevos (250 en total): bordes exactos del rango (incluido el máximo real de
    Reverb Time, `10.0` s, y de 2x2 Chorus Pre Delay, `40.0` ms), redondeo sin arrastre en
    conversiones repetidas, clamping en los dos sentidos, y no-colisión con los bloques
    adyacentes (tipo de reverb, pre delay de 2 bytes de reverb).

- **2026-09-06 — Los 27 tipos restantes de Mod/FX, cableados de golpe con la tabla de
  CLAUDE.md §5.2** en vez de una a una (⚠️ implementados, pendientes de confirmar con audio —
  ver "Pendiente por probar", punto 6, reescrito para esto). Con esto los 31 tipos completos
  del catálogo de `ModFxType` tienen sus parámetros internos cableados, tanto en Mod como en
  FX (`+ FX_OFFSET`).
  - **La premisa de partida de la tarea estaba equivocada, y se corrigió antes de escribir
    nada**: de los "4 tipos ya implementados" que se daban por hechos (2x2 Chorus, Flanger,
    Phaser, Tremolo), en realidad solo existían en código los dos parámetros de Pre Delay de
    2x2 Chorus — Flanger, Phaser y Tremolo estaban documentados en CLAUDE.md pero nunca
    cableados. Se avisa aquí porque de otro modo el número "27 restantes" no habría cuadrado
    con los 31 tipos reales del catálogo.
  - **`ModFxInternalParams` (protocol/, puro, sin dependencias de Android)**: un
    `object` con `byType: Map<ModFxType, List<ModFxParamSpec>>`, 192 entradas — no 194,
    porque las 2 del Pre Delay de 2x2 Chorus siguen fuera a propósito (ver la nota de
    arquitectura de abajo) y las 24 direcciones de escala de usuario de Harmonist se dejan sin
    cablear, igual que el Custom Type de Booster. Cada entrada es dirección (en Mod),
    etiqueta y un `ModFxParamKind` sellado con las 6 formas de escala del proyecto: `Direct`,
    `Centered`, `OffThenOneBased`, `TwoByteDirect`, `Fractional` (sin usar en esta tabla,
    reservado para cuando haga falta) y `Enum`.
  - **Las cinco anomalías que pedía la tarea, todas verificadas, no solo transcritas**:
    - Vibrato tiene su propio `Off/On` interno en `60 00 02 28`, distinto del on/off del slot
      de Mod (`60 00 01 00`) — cableado como una entrada más de la tabla, sin caso especial.
    - Tres radios de escala centrada conviven sin mezclarse: `±50` (crudo `00..64`, Tone/Low/
      High/Fine/Bass/Middle/Treble/Presence), `±20` dB (crudo `00..28`, todo el Graphic EQ y
      las ganancias del Parametric EQ) y `±24` (crudo `00..30`, Pitch de Pitch Shifter y de
      Pedal Bend). Verificado con un test (`los tres radios de escala centrada conviven sin
      confundirse`) que extrae los radios usados de la tabla entera y comprueba que son
      exactamente esos tres, ni más ni menos.
    - El `Repeat Rate` de DC30 (`60 00 02 54`+`55`) es `TwoByteDirect(40..600)`, no
      `LevelScale.direct` asumiendo un mínimo en 0 — `LevelScale.direct(40..600)` ya fuerza
      ese mínimo por construcción (raw=display sin desplazamiento), así que no hizo falta un
      caso especial, solo pasarle el rango correcto. Verificado con test dedicado.
    - `Step Rate` del Phaser (`60 00 02 08`) es el único `OffThenOneBased` de los 31 tipos —
      verificado por test, filtrando la tabla entera por ese `ModFxParamKind` y comprobando
      que sale un único resultado.
    - La regla `FX = Mod + 0x0200` se aplicó como `FX_OFFSET = 2 * 128` (256 en base 128 de
      `Address.value`), **no** el literal hexadecimal `0x0200` (512 en decimal) — son
      notaciones para lo mismo pero un entero equivocado ahí habría escrito cada parámetro de
      FX 256 posiciones más allá de donde toca, sin que compilase mal ni fallase ningún test
      que no comparase contra una dirección conocida. Verificado por test
      (`FX_OFFSET no hace que ninguna direccion de FX choque con una de Mod`).
  - **Arquitectura de tabla en vez de 192 propiedades con nombre — desviación deliberada del
    patrón literal de Booster/Delay/Reverb, justificada por escala.** El patrón existente
    (una `val` por parámetro, un enum `XParamId` con `labelRes`/`displayRange`, una función
    `onXParamChanged`/`onReadXParamClicked` por dominio) funciona bien hasta los ~10
    parámetros de un bloque "DSP simple". Con 192 repartidos en 31 formas que cambian según
    el tipo activo, replicarlo habría significado ~600 líneas de KDoc y wiring casi idéntico
    31 veces, y 192 entradas de `strings.xml` solo para nombres como "Rate" o "Depth" que ya
    son legibles tal cual. Se optó por:
    - `protocol/ModFxInternalParams.kt`: la tabla, con `rawToDisplay`/`displayToRaw`/
      `displayBounds` como funciones de extensión sobre `ModFxParamKind` — un espejo
      deliberado de `LevelScale`/`FractionalLevelScale` para un raw byte que todavía no tiene
      un `KatanaControl` construido delante (la UI genérica solo tiene el `Int?` que le llega
      del `StateFlow`, no el control).
    - `KatanaRepository.modInternalParams`/`fxInternalParams: Map<ModFxType, Map<String,
      KatanaControl>>`: construidos una vez desde la tabla con `modFxControl()` (un `when`
      sobre `ModFxParamKind` que llama a los mismos `parameter()`/`selector()`/
      `fractionalParameter()` privados que ya registran cualquier otro control del proyecto
      — **el anti-eco, el debounce, el dump y la caché no tienen ni un caso especial para
      estos 192**, salen gratis de reutilizar la maquinaria existente). El Pre Delay de 2x2
      Chorus se mezcla aquí desde las instancias ya existentes (`modChorusPreDelayLow`/
      `High`), no se duplica.
    - `DebugConnectionViewModel.modFxInternalRaw`/`fxInternalRaw:
      StateFlow<Map<ModFxType, Map<String, Int?>>>`: un colector por control (192 × 2 = 384
      `launch`), igual que ya hacía el proyecto para `DelayParamId.entries.forEach`, solo que
      generado desde el mapa del repositorio en vez de un enum a mano. `onModFxParamChanged`/
      `onReadModFxParamClicked` buscan el control por `(isFx, type, label)` y usan
      `ModFxParamKind.displayToRaw`/`rawToDisplay` para la conversión — el mismo contrato que
      `setLevel`/`displayValue` de siempre, sin un tipo de control nuevo.
    - `DebugConnectionScreen.ModFxGenericParams`: una función que recorre
      `ModFxInternalParams.byType[tipoActivo]` y renderiza cada `ModFxParamKind` con los
      widgets que ya existían (`LevelControl`, `ChipSelector` si el enum tiene ≤4 opciones,
      `DropdownSelector` si tiene más — el mismo criterio que ya usa el proyecto para
      `AmpType` frente a `EffectColor`). Vive **al lado** de `ModInternalParams` (el Pre Delay
      de Chorus), no la reemplaza: `ModFxInternalParams.byType` no incluye esas 2 direcciones
      a propósito, así que las dos secciones nunca se pisan.
  - **Riesgo aceptado y por qué**: sin `strings.xml` por parámetro, las 192 etiquetas
    (`"Rate"`, `"Depth"`, `"Tone"`…) están hardcodeadas en `ModFxInternalParams.kt`, lo que
    técnicamente se sale de la regla de CLAUDE.md §6 ("textos de UI siempre en `strings.xml`,
    nunca hardcodeados"). Se decidió así porque son nombres de parámetro provenientes
    directamente de la fuente (`midi.xml`), no prosa de interfaz, y la alternativa —192
    entradas de `strings.xml` con nombres como `mod_touch_wah_sens` solo para volver a decir
    "Sens"— no habría añadido nada traducible de verdad (el proyecto no tiene otro idioma
    además del castellano/inglés mezclado que ya usa en el resto de la UI de diagnóstico) a
    cambio de 192 líneas más de indirección. Si el proyecto pasa algún día a i18n real, esta
    tabla es el primer sitio a revisar.
  - **12 tests JVM nuevos** (`ModFxInternalParamsTest`, 270 en total): 31 tipos y 192
    parámetros exactos; ninguna dirección de Mod se repite entre tipos (ni siquiera vecinos);
    un parámetro de 2 bytes no pisa la dirección del siguiente (comprobación por rango
    ocupado, no solo por dirección base — es la que de verdad prueba "no colisiona con un
    parámetro de otro tipo en el mismo bloque", el requisito explícito de la tarea);
    `FX_OFFSET` no hace chocar ninguna dirección de FX con una de Mod; cada `Enum` tiene
    tantos `labels` como `values` y ninguno fuera de 7 bits; el `SET` de cada uno de los 192
    parámetros —en Mod y en FX— tiene checksum correcto y se reparsea con la misma dirección y
    el mismo dato; `rawToDisplay`/`displayToRaw` son inversas en los dos extremos de cada
    rango; y las cuatro afirmaciones puntuales de arriba (Vibrato, los tres radios, DC30,
    Phaser) verificadas por separado en vez de confiar en la lectura manual de la tabla.
  - `./gradlew :app:compileDebugKotlin`, `:app:testDebugUnitTest` (270/270) y `:app:lintDebug`
    (13 warnings preexistentes, ninguno en los ficheros tocados) limpios.

- **2026-09-06 — Controles sin perilla física: Noise Gate, Contour, posiciones de EQ1/EQ2, los
  dos bloques de EQ y la cadena de efectos** (CLAUDE.md §5). ⚠️ **85 controles nuevos,
  implementados y pendientes de confirmar con audio** — ver "Pendiente por probar", punto 11.
  El **Solo del amplificador se dejó fuera a propósito**: sigue en instrumentación de
  diagnóstico con sus dos candidatas sin desempatar, y cablearlo antes de saber cuál responde
  sería elegir una al azar.
  - **Qué se cableó**: Noise Gate (on/off + threshold + release), Contour (on/off, selector de
    slot activo, freq shift del activo, y los 3 slots con Shape + Freq Shift), posición de EQ1
    (`06 22`) y EQ2 (`06 19`), los dos bloques internos de EQ (24 parámetros cada uno) y la
    cadena de efectos (7 cadenas predefinidas + array de 20 posiciones + posiciones de Loop y
    Pedal/FX).
  - **Verificado por programa contra `midi.xml` antes de escribir una línea**, no por
    relectura de las notas: **EQ2 = EQ1 + `0x20`** byte a byte (24 nodos, 0 diferencias de
    dirección, de nombre de parámetro ni de rango); las **20 posiciones de la cadena ofrecen el
    mismo catálogo** de 20 identificadores y van seguidas de `00` a `13`; y los **seis
    catálogos de frecuencia/Q del EQ coinciden byte a byte** con los del Parametric EQ de
    Mod/FX (test que lo fija, para que una divergencia futura no pase inadvertida).
  - ⚠️ **`EQ2 = EQ1 + 0x20` y `FX = Mod + 0x0200` se escriben igual y NO significan lo mismo.**
    FX mueve el **tercer** byte de la dirección, que en base 128 son `2 × 128 = 256`; EQ2 mueve
    el **cuarto**, el de menor peso, así que ahí `0x20` sí es literalmente 32. Cada una tiene
    su constante y su test, precisamente porque la notación invita a copiar la regla de una a
    la otra.
  - ⚠️ **El EQ gráfico es fraccionario y el Graphic EQ de Mod/FX no**, pese a llamarse igual:
    `00/30/-12.0/+12.0` (49 valores crudos sobre 24,0 dB → pasos de 0,5 dB,
    `FractionalLevelScale`) contra `00/28/-20/+20` (entero, `LevelScale.centered(20)`).
    Confundirlos daría el doble de rango con la mitad de resolución. Hay un test que compara
    los dos y falla si alguien los unifica.
  - 🔬 **Un hallazgo que la investigación documental no anticipaba: los 6 controles de Contour
    por slot cuestan 6 GET de respaldo EN SERIE en cada recarga.** Se sabía que
    `60 00 0F 3x`/`4x` cae fuera del dump y que "caerían al GET individual de respaldo, que ya
    existe y funciona"; lo que no se había medido es que `loadFromDump` hace esos GET **uno tras
    otro**, cada uno esperando hasta `DEFAULT_REPLY_TIMEOUT_MS` (800 ms). Si esa región no
    contesta, son **hasta 4,8 s añadidos a cada conexión y a cada cambio de canal** — el cambio
    de canal dispara la misma recarga (§4.4). Si contesta rápido, el coste es despreciable: lo
    que decide entre una cosa y otra es exactamente lo que está sin probar.
    - **Cómo apareció**: un test de regresión de concurrencia que ya existía
      (`regression - at most one dump is ever in flight`) empezó a agotar su margen de 800 ms.
      El invariante que prueba —nunca dos dumps a la vez— **seguía cumpliéndose**; lo que
      cambió fue cuánto tarda una recarga completa. Se le subió el margen a 4 s **con el motivo
      escrito al lado**, en vez de dejarlo como un número mayor sin explicación.
    - Y se añadió un test que **fija en seis** el número de controles fuera del dump, y
      comprueba que todo lo demás de esta tanda (Noise Gate, Contour general, posiciones, los
      48 del EQ, las 20 de la cadena) sí cae dentro, para que un séptimo no se cuele sin que
      nadie vea lo que cuesta.
    - **No se arregló todavía**, a propósito: las salidas son ampliar el rango del dump o
      paralelizar los GET de respaldo, las dos son cambios reales al camino de recarga, y
      hacerlas antes de saber si hacen falta sería optimizar a ciegas.
  - 🐛 **Corregido un error de la propia CLAUDE.md que cazó un test**: §5 decía "los cinco
    'Gain' y el 'Level'" del EQ paramétrico, que da seis escalas centradas. Son **cuatro** Gain
    (Low, Lo Mid, Hi Mid, Hi) más el Level: **cinco**. El código siempre tuvo cinco —sale de la
    extracción, no de la prosa— así que lo que estaba mal era la nota. Es el segundo caso del
    proyecto en que un test caza una cuenta mal hecha en la documentación.
  - **Refactor de apoyo**: `ModFxParamKind`/`ModFxParamSpec` pasaron a llamarse
    `ParamKind`/`ParamSpec` y viven en `protocol/ParamSpec.kt`, porque el bloque de EQ tiene
    exactamente la misma forma que los de Mod/FX (una tabla de "etiqueta + dirección + tipo de
    escala") y tener dos vocabularios paralelos para lo mismo habría sido peor que renombrar.
    El campo `modAddress` pasó a `address` por lo mismo. En la UI, el renderizador genérico se
    partió en `TableParams` (recorre cualquier `List<ParamSpec>`) y `ModFxGenericParams` (elige
    la lista según el tipo activo), así que EQ y Mod/FX comparten widgets sin duplicarlos.
  - **UI**: sección propia "Sin perilla física" al final de Sliders, no repartida por las
    tarjetas de efecto — no pertenecen a ningún efecto, y meterlas en la tarjeta de, digamos,
    Reverb sugeriría una relación que no existe. Las dos mitades de cada EQ (paramétrico y
    gráfico) se muestran **siempre**, con un aviso en la que no está seleccionada: las 24
    direcciones existen y aceptan escritura a la vez, lo que cambia con `Selection` es cuál
    suena, y ocultar la mitad inactiva escondería que su valor sigue ahí. Todo bajo `canEdit`.
  - 25 tests JVM nuevos (295 en total): direcciones contra `midi.xml`, EQ2 sin solaparse con
    EQ1, checksum de los 48 SET del EQ, las 49 posiciones del paso de 0,5 dB yendo y volviendo
    sin perder ninguna, **el redondeo sin arrastre en 50 viajes repetidos** (mismo chequeo que
    se hizo para el Pre Delay de 2x2 Chorus y Reverb Time), clamping, los catálogos contra los
    de Mod/FX, y el recuento de controles fuera del dump.
  - `./gradlew :app:compileDebugKotlin`, `:app:testDebugUnitTest` (295/295) y `:app:lintDebug`
    (13 warnings, los mismos preexistentes: los 2 que introdujo esta tanda —un falso positivo
    de "EQ1" y un guion donde iba una raya— se corrigieron en vez de suprimirlos) limpios.

- **2026-09-06 — Guardado de presets: el estado editado se puede escribir a cualquiera de los 8
  canales** (CLAUDE.md §5, "Guardado de presets"). ⚠️ **Implementado, pendiente de confirmar con
  el amplificador** — ver "Pendiente por probar", punto 8. 🔴 **Es la primera operación
  destructiva e irreversible del proyecto: probarla primero en un canal sin nada importante
  guardado, y exportar o anotar los presets antes.**
  - **Dos mensajes, ninguno lleva el sonido dentro**: el amplificador ya tiene el estado editado
    en su búfer, así que guardar es escribir el nombre en `60 00 00 00` (16 bytes ASCII) y
    después el commit en `7F 00 01 04` con el canal destino como dato de 2 bytes. El commit
    viene confirmado para el Mk2 **en código**, no por analogía con el MK1
    (`patchWriteDialog.cpp:346`).
  - **El orden importa y por eso hay un test que lo fija**: el commit copia lo que haya en el
    búfer, así que un commit que saliera antes que el nombre guardaría el nombre viejo — y como
    no hay confirmación por SysEx, **nadie se enteraría** hasta abrir el preset semanas después.
  - **Tres decisiones, todas por lo mismo (es destructivo y no se confirma)**:
    - **PANEL (`00`) no se ofrece como destino.** Sigue siendo TBD qué hace el amplificador con
      él. `commitMessage` **rechaza** cualquier valor fuera de `01`..`08` en vez de clampear:
      clampear elegiría un canal por su cuenta en algo que no tiene deshacer.
    - **No se toca el edit mode**, aunque la secuencia del MK1 lo ponga como paso 1: es un
      ajuste explícito del usuario (§4.2) y encenderlo de tapadillo sería moverle un interruptor
      por la espalda. En su lugar la UI lo **exige** — el botón cae bajo `canEdit`, como
      cualquier parámetro y a diferencia del selector de canal.
    - **No se permiten dos guardados solapados** ([presetSaveInFlight]): ninguna fuente dice
      cuál es el intervalo mínimo entre dos, así que solaparlos sería meterse en territorio
      desconocido con una operación irreversible.
  - **La verificación es releer el nombre del canal destino** (`10 0N 00 00`) y compararlo con
    lo que se pidió guardar — lo más parecido a una confirmación que existe, y encima ya estaba
    implementado (es el mismo GET del botón "Presets"). ⚠️ **Y se informa con cuidado**: que el
    nombre no cuadre **no prueba** que el guardado fallara —puede que el amp tarde en actualizar
    esa tabla, cosa que ninguna fuente aclara—, así que el log dice "o el guardado no entró, o
    el amp no actualizó todavía esa tabla", no "falló". Hay un test por cada uno de los tres
    desenlaces (coincide / no coincide / no contesta).
  - ⚠️ **El margen de 50 ms entre el nombre y el commit es criterio propio, no un dato.** Los
    ~50 ms que sí están documentados son alrededor del **edit mode**, que es otra cosa; se
    reutiliza el número por analogía y así está escrito en el KDoc de `SAVE_SETTLE_MS`.
  - 🐛 **Un detalle que habría roto el guardado con nombres en castellano**: los datos SysEx son
    de 7 bits y `RolandSysEx.set` rechaza cualquier byte por encima de `0x7F`, así que una eñe o
    un acento en el nombre habrían hecho **fallar el guardado entero** con una excepción.
    `PresetSave.encodeName` sanea a ASCII imprimible (lo demás pasa a `?`), recorta a 16 y
    rellena con espacios; la UI enseña el nombre resultante **antes** de confirmar, para que
    nadie descubra el cambio después. Y `savePreset` compara contra el nombre **ya saneado**:
    comparar contra el original habría reportado como fallido para siempre un guardado correcto.
  - **UI**: botón "Guardar preset…" arriba de la pantalla de Sliders (actúa sobre todo lo de la
    pantalla, no sobre una sección) que abre un diálogo de **dos pasos**. El primero pide nombre
    y canal destino, avisa en rojo si el destino no es el canal que se está editando, y muestra
    qué se va a guardar exactamente; el segundo repite en palabras qué se va a sobrescribir y
    dónde. Dos pasos y no uno a propósito: lo que se quiere evitar no es un error de datos, es
    un descuido — con un solo botón, un toque de más sobre el canal equivocado se lleva por
    delante un preset que igual costó una tarde.
  - 21 tests JVM nuevos (316 en total): el mensaje completo de CH A1 y CH B3 **byte a byte**
    —incluido el checksum calculado a mano en el comentario—, los ocho commits reparseados, que
    cada canal produzca un mensaje distinto (si `commitMessage` ignorara su argumento se
    guardaría siempre en el mismo sitio y casi todo lo demás seguiría pasando), el rechazo de
    `0`/`9`, el nombre con acentos/eñe/caracteres de control/vacío/demasiado largo, las
    etiquetas `A1`..`B4`, y en `device/` la secuencia completa: orden de los dos SET, que no se
    toque ningún otro canal, y los tres desenlaces de la verificación.
  - `./gradlew :app:compileDebugKotlin`, `:app:testDebugUnitTest` (316/316) y `:app:lintDebug`
    (13 warnings, los preexistentes: los 2 que introdujo esta tanda se corrigieron —contador de
    caracteres sin plural y `mutableIntStateOf` para el canal— en vez de suprimirlos) limpios.

- **2026-09-06 — Biblioteca de presets, primera mitad: importar `.tsl` y verlos en solo
  lectura** (CLAUDE.md §5, "Formato `.tsl`"). ⚠️ **Implementado, pendiente de probar** — ver
  "Pendiente por probar", punto 9. **Y esta vez la prueba no necesita amplificador**: es lectura
  de fichero de punta a punta, así que se puede hacer ahora mismo, sin esperar a la próxima
  sesión con guitarra. ⚠️ **Solo lectura**: ni edición ni exportación, que es trabajo aparte.
  - 🎯 **Lo que hizo esto barato fue no escribir casi nada nuevo.** Un `.tsl` y un dump de
    memoria terminan en la misma forma —trozos de bytes, cada uno con su dirección base—, así
    que el parser construye un `MemoryDump` y `AmpState.from(dump)` lo lee **sin cambiar una
    línea**. Ni `AmpState` ni `MemoryDump` se tocaron. De regalo salió el requisito de "desconocido
    en este fichero": `MemoryDump.byteAt` ya devolvía `null` para una dirección que no cubre, y
    `AmpState` ya tenía todos sus campos anulables, porque el dump del amplificador tampoco llega
    entero. Lo que en un diseño nuevo habría sido un caso especial, aquí ya estaba resuelto.
  - **Las claves en disputa NO se cargan; se listan como no disponibles.** Cuatro de las 22
    tienen dos direcciones candidatas y ninguna forma de desempatarlas sin un fichero de prueba:
    los tres `Contour` (`midi.xml` dice `0F 30`/`38`/`40`, la aritmética de FxFloorboard
    `0F 2E`/`36`/`3E`) y `GafcExp1AsgnMinMax` (`09 30` vs `09 34`). `TslConfidence` las marca
    `DISPUTED` y el parser las salta. **Un desfase de dos pondría el Freq Shift donde va el
    Shape y lo enseñaría como si fuera bueno** — y un valor equivocado con pinta de correcto es
    peor que un hueco declarado. Son 82 de 1141 bytes; los otros 1059 sí se cargan.
  - **`Patch_1` se lee con 91 bytes, no con los 50 del yaml**, que es lo que hace que la cadena
    de efectos entera, Solo, Contour general y la posición de EQ2 **sí** lleguen. Hay un test que
    comprueba que las 20 posiciones de la cadena vienen completas: con 50 bytes ese test cae, que
    es justo lo que se quería que pasara si alguien "corrige" el 91 de vuelta al valor del yaml.
  - **El tamaño del fichero manda sobre el del mapa.** Si un bloque trae otra longitud se cargan
    los bytes del fichero y se avisa, en vez de truncar o rellenar: otra revisión del formato
    podría traer bloques distintos, y ajustarlos a ciegas sería inventar bytes que nadie escribió.
  - **Nada rompe el parseo entero.** Clave que falta, hex corrupto, longitud rara, clave
    desconocida, `formatRev` inesperado: todo acaba como un aviso en la lista de "no disponible"
    y el resto del preset se carga igual. Lo **único** que se rechaza de plano es
    `device != "KATANA MkII"` — un `.tsl` de la serie GT tiene un esquema completamente distinto
    (`liveSetData`/`patchList` en vez de `paramSet`) y solo comparte la extensión, así que leerlo
    daría basura con pinta de preset.
  - **Se copia el fichero, no se guarda su `Uri`.** Un `Uri` del Storage Access Framework es un
    préstamo: el permiso puede caducar y el usuario puede borrar el original de Descargas sin
    saber que la app dependía de él. La copia va a `filesDir/presets` —privado, sin permisos— y
    cuesta unos 6 KB por preset. **Y se copia antes de parsear**, a propósito: un fichero que no
    se entiende queda igualmente guardado y listado con su motivo, en vez de desaparecer sin
    dejar rastro de qué se intentó importar.
  - **UI**: sección "Biblioteca" en el menú hamburguesa, junto a Logs y Sliders. Lista de
    importados con el nombre del preset, botón "Importar" que abre el selector del sistema, y al
    tocar uno, la vista de detalle en **solo lectura** reutilizando los mismos sliders y
    selectores de Sliders con `enabled = false`. Se reutilizan los **widgets hoja**, no las
    tarjetas: las tarjetas llevan dentro botones de GET que consultan al amplificador, y sobre un
    fichero eso no significa nada.
  - 🧰 **Los seis widgets compartidos se extrajeron a `ui/screens/Controls.kt`** (`SectionHeader`,
    `ChipSelector`, `DropdownSelector`, `SwitchRow`, `LevelControl`, `FractionalLevelControl`) para
    que las dos pantallas los usen sin que la de Biblioteca dependa de la de diagnóstico.
    ⚠️ **La extracción se intentó con un script y salió mal**: una expresión regular con
    `(/\*\*.*?\*/)?` en modo `DOTALL` se llevó ~1700 líneas —las dos pantallas enteras— a
    `Controls.kt`, y no se podía deshacer con `git checkout` porque el fichero de origen tenía
    todo el trabajo sin commitear de la sesión. Se reparó a mano en tres pasadas y los 332 tests
    quedaron pasando. La lección es la de siempre en este proyecto, aplicada a otra cosa: **un
    script sobre código que no está commiteado necesita el commit primero**, no la confianza en
    la expresión regular.
  - ⚠️ **`kotlinx-serialization-json` añadido** (`1.9.0`, en el version catalog como manda §3).
    §6 lo permitía "solo si el parseo de presets `.tsl` lo justifica" y apuntaba a que el
    `org.json` de la plataforma podía bastar; **no basta, y por una razón concreta**: `org.json`
    está *stubbed* en los tests JVM —sus métodos devuelven valores por defecto o lanzan— y el
    parseo del `.tsl` es justamente lo que hay que poder probar sin amplificador y sin
    dispositivo. Habría que haber elegido entre tests o librería de la plataforma; se eligieron
    los tests.
  - 16 tests JVM nuevos (332 en total) sobre el fichero real
    `reference/FxFloorboard/default_mk2.tsl`: el `AmpState` que produce (Gain 50, modelo Clean,
    color de Booster verde, tipo activo Blues Drive coincidiendo con el del slot verde), las 20
    posiciones de la cadena, el rechazo del `.tsl` de la serie GT, y que cada clase de fichero
    roto acaba en un aviso y no en un fallo. ⚠️ **Comprobado que no pasaban en vacío**: se
    saboteó a propósito el `assertEquals(50, state.gain)` para ver que el test fallaba de verdad
    antes de darlo por bueno.
  - `./gradlew :app:compileDebugKotlin`, `:app:testDebugUnitTest` (332/332) y `:app:assembleDebug`
    limpios. `:app:lintDebug`, 15 warnings: los 13 preexistentes más 2 avisos de
    `NewerVersionAvailable` que trae la dependencia nueva. Los 4 que sí introdujo esta tanda se
    corrigieron —dos `PluralsCandidate` con `<plurals>` de verdad, no acortando el texto— en vez
    de suprimirlos.

- **2026-09-06 — Edición offline de presets y exportación a `.tsl`** (CLAUDE.md §4.5 y §5,
  "Formato `.tsl`"). ⚠️ **Implementado, pendiente de probar** — ver "Pendiente por probar",
  punto 11. **La edición offline se prueba sin amplificador**; la exportación desde el amp sí
  lo necesita.
  - 🎯 **La decisión de arquitectura, y por qué no fue ninguna de las dos propuestas.** La
    pregunta era cómo hacer que los mismos sliders escriban en un preset de fichero. La
    respuesta resultó ser **que ya había un sitio para eso**: `KatanaLink` es exactamente "el
    backend de un parámetro", solo que tenía una única implementación. `OfflineKatanaLink`
    guarda los SET en un mapa dirección→byte y contesta a los GET desde él, y con eso **el
    juego entero de controles funciona sin cable, sin tocar `KatanaControl`,
    `KatanaRepository` ni un composable**.
    - Descartada la **opción A** (un "backend" nuevo dentro de `KatanaControl`): duplicaría
      una costura que ya existe una capa más abajo, y haría crecer una rama en `read`, `set`,
      `probeWrite` y `applyIncoming` — justo la clase que concentra la caché optimista, el
      debounce y la regla anti-eco.
    - Descartada la **opción B** (`AmpState` como fuente única de verdad) por un dato medible:
      **`AmpState` tiene 24 campos y la app controla varios cientos de direcciones** (solo
      `ModFxInternalParams` son 192 `ParamSpec`). Y por algo peor: **un modelo de dominio
      pierde lo que no modela**. Un `.tsl` son 1141 bytes; editar el Gain vía `AmpState` y
      reexportar **borraría** los internos de Mod/FX, el EQ y la cadena. Hay un test que fija
      justo eso.
  - 🐛 **`loadFromDump` no vale offline, y se descubrió colgando un test.** Cae a un GET
    individual **en serie, hasta 800 ms cada uno**, por cada control que el dump no cubra. Con
    una imagen en RAM eso es a la vez **inútil** (se pregunta otra vez a la misma fuente que
    acaba de no tener el valor) y **carísimo**: con cientos de controles y un preset casi
    vacío son minutos. De ahí `loadFromImage`, que aplica la imagen y **no hace ni un GET**.
    Hay un test que mide que tarda menos de 500 ms y que no se preguntó nada.
  - 🐛 **El debounce habría perdido el último cambio al guardar.** El repositorio offline va
    con `debounceMillis = 0` — y es **corrección, no rendimiento**: los ~100 ms existen para no
    inundar el cable USB (§4.2), pero offline el destino es un `HashMap` y el retardo solo
    causaría que mover un slider y tocar "Guardar" acto seguido escribiera el `.tsl` **sin ese
    cambio**. Un preset al que le falta justo lo último que tocaste, y en silencio. Test propio.
  - **Tres decisiones al escribir, ninguna un valor por defecto callado** (tabla completa en
    CLAUDE.md §5): los bytes que la imagen tiene **se escriben**; los que no, pero venían del
    fichero de origen, **se copian verbatim**; y lo que no está en ninguno de los dos **se
    omite y se avisa**.
    - **La copia verbatim es lo que hace que editar no pierda nada**: los cuatro bloques en
      disputa (82 bytes) no se cargan a propósito, así que sin este paso abrir y guardar los
      **borraría** — el mismo fallo que la política de importación quería evitar, pero en la
      otra dirección.
    - **Omitir en vez de escribir ceros**: un Contour a ceros es un valor legal e
      indistinguible de uno elegido a mano, que al cargarlo cambiaría el sonido en silencio.
      Entre un fallo ruidoso y uno callado, el ruidoso.
  - **Preset en blanco**: todo a cero salvo dos excepciones documentadas — la **cadena de
    efectos**, que es un array de permutación y con veinte ceros sería "el compresor veinte
    veces" (se siembra con la identidad), y el nombre. ✅ Que ningún selector con huecos
    rechace el `0x00` está **comprobado montando el repositorio real sobre el preset en
    blanco**, no leyendo los catálogos: `KatanaEnumParameter` rechaza en silencio, así que un
    valor inválido no daría error, solo dejaría el control vacío.
  - ✅ **La UI es literalmente `SlidersPane`**, la misma de la pantalla en vivo, con
    `offline = true`. Era el objetivo: un solo juego de tarjetas y sliders para las dos cosas,
    en vez de una copia que se separa cada vez que se cablea un control. El modo offline oculta
    las cuatro cosas que solo tienen sentido con hardware delante (Edit Mode, guardar-a-canal,
    selector de canal, tarjeta de diagnóstico) y los botones de "leer", vía un
    `CompositionLocal` — un parámetro habría obligado a tocar medio centenar de puntos de
    llamada para expresar una decisión que es de la pantalla entera.
  - 🧰 **Los nueve mapeos `id → control` se extrajeron a `ControlBinding`**, compartido por las
    dos pantallas. Con una copia por ViewModel, cablear un control nuevo y actualizar solo una
    daría una pantalla que edita algo que la otra no, **sin que nada fallara al compilar**.
  - **Exportar no es destructivo y por eso no cae bajo `canEdit`**: solo lee el dump y escribe
    un fichero nuevo en la biblioteca. Basta con estar conectado; no necesita Edit Mode, porque
    §4.2 va de poder *confirmar* escrituras y aquí no se escribe nada en el amplificador.
  - 29 tests JVM nuevos (361 en total): que un `SET` offline acaba en la imagen y un `GET` se
    contesta desde ella; que un SET **no** genera tráfico entrante (la regla anti-eco se cumple
    sola); la ida y vuelta **byte a byte** sobre `default_mk2.tsl` con las 22 claves; que editar
    el Gain cambia un solo byte y deja `Fx(1)`, `Fx(2)`, `Eq(2)` y `Patch_2` intactos; y el
    preset nuevo, editado, guardado y reabierto. ⚠️ **Comprobado que no pasan en vacío**: se
    saboteó `TslWriter` a propósito para escribir un byte mal y **7 tests fallaron**.
  - `./gradlew :app:testDebugUnitTest` (361/361), `:app:assembleDebug` y `:app:installDebug`
    limpios. `:app:lintDebug`, **15 warnings — el mismo baseline de antes de esta tanda**: los 4
    que introduje (`EmptySuperCall`, dos `ModifierParameter` y un string sin usar) se
    corrigieron en vez de suprimirlos.

- **2026-09-06 — Cinco cambios de UI tras la primera sesión de pruebas con el amplificador**.
  Todos salen de usar la app contra el amp, no de investigación nueva.
  - ❌ **Bright (`60 00 00 29`) y Gain SW (`60 00 00 2A`) fuera de la UI: probados y sin
    efecto.** Ni sonido ni cambio de estado interno — el SET+GET inmediato dice que el valor no
    se mueve. Un control que acepta el gesto y no hace nada es peor que ninguno.
    - ⚠️ **Las direcciones se conservan registradas y documentadas en `KatanaAddresses`**, con
      la nota de qué se probó y cómo. El precedente del reverb (CLAUDE.md §5) es que una
      dirección plausible puede estar simplemente mal identificada, y el día que aparezca otra
      candidata conviene saber qué ya se descartó. Borrarlas perdería justamente eso.
    - Se quitaron también sus **sondas de diagnóstico**: eran para contestar esta pregunta y ya
      está contestada. Las de Solo se quedan, que siguen abiertas.
  - 🎛️ **EQ rediseñado siguiendo la interfaz de Boss Tone Studio** (mínima, sin pulir):
    - **Gráfico → barras verticales.** Un EQ gráfico **se lee por la forma de la curva**, y esa
      forma solo existe si las bandas están una al lado de otra; con diez sliders horizontales
      apilados no hay curva, solo diez números.
    - **Paramétrico → perillas.** Las once, incluidas las frecuencias y la Q: la perilla mueve
      el **índice** del selector y el texto de debajo enseña la etiqueta real (`1.60k`, `0.5`).
      Es lo que hace BTS y lo que hace un paramétrico físico.
    - ✅ **Aplica a los dos sitios donde hay un EQ**, no solo a uno: EQ1/EQ2 del amplificador y
      los tipos `GRAPHIC_EQ`/`PARAMETRIC_EQ` de Mod/FX. Comprobado en el mapa ya extraído que el
      paramétrico de EQ1/EQ2 y el `PARAMETRIC_EQ` de Mod/FX **son los mismos once parámetros**;
      hay un test que lo fija.
    - ⚠️ **Pero sus escalas NO son las mismas y eso ya estaba resuelto**: el gráfico de EQ1/EQ2
      va en pasos de 0,5 dB sobre ±12 y el de Mod/FX es entero sobre ±20. Cada `ParamSpec` trae
      la suya, así que los widgets no necesitan saberlo — hay un test que comprueba que siguen
      siendo de clases distintas.
    - 🐛 **Un test destapó un detalle que habría roto la mitad del EQ**: el reparto usa índices
      fijos sobre `EqParams.SPECS` (`take(2)` / `subList(2,13)` / `drop(13)`), así que reordenar
      la tabla movería controles de una mitad a la otra **sin fallar al compilar**. El test lo
      fija por etiqueta. De paso confirmó por qué el Level del gráfico se llama
      `"Level (gráfico)"`: los valores viajan en un mapa por etiqueta y dos "Level" se pisarían.
  - ❌ **Fuera el reordenado manual de la cadena** (el array de veinte, `60 00 06 00`–`06 13`):
    no se comporta bien contra el amplificador. Queda **solo el selector de las siete cadenas
    predefinidas** (`60 00 06 20`), que es un valor y no veinte.
    - ✅ **Pero el orden se sigue leyendo y enseñando**, en un diagrama de texto
      `INPUT → PDL → OD → … → SPEAKER`, **generado de los valores reales de las veinte
      direcciones**. No es una lista fija: el orden cambia con la cadena elegida, así que un
      diagrama hardcodeado sería falso en seis de los siete casos. Las ranuras sin leer salen
      como `?` en vez de saltarse, para no hacer parecer la cadena más corta de lo que es.
    - Los veinte controles **siguen registrados en el repositorio para leerlos**; lo que se
      quitó es el camino de escritura, de la UI al ViewModel.
  - ➕ **Botón "Releer estado"**: relee el dump completo del canal actual y repuebla todo.
    ✅ **Reutiliza el coordinador de recargas tal cual** —el mismo `StateFlow` conflado y el
    mismo `Mutex` que la recarga de conexión y la de cambio de canal— en vez de llamar a
    `loadFromDump` por su cuenta. Eso es lo que impide que un refresco a mano se solape con una
    recarga automática, que es exactamente el bug de los dumps simultáneos que costó un día
    (CLAUDE.md §4.4).
    - ⚠️ **Detalle que lo habría dejado funcionando una sola vez**: el coordinador es un
      `MutableStateFlow`, que **descarta un valor igual al que ya tiene**. Dos pulsaciones
      seguidas son la misma petición, así que `ReloadRequest.Manual` lleva un contador que sube
      en cada una. Y no pasa por el guard de canal: el usuario pide releer precisamente cuando
      sospecha que el estado no cuadra.
  - ❌ **Fuera el aviso "Solo disponible con el tipo 2x2 Chorus"** de la tarjeta de Mod. Tenía
    sentido cuando esos dos sliders eran lo único cableado de Mod; ahora los 31 tipos están
    cableados y la tarjeta de debajo muestra los del tipo activo, así que el aviso decía "no hay
    nada para ti aquí" **encima de una tarjeta llena de controles**. Los dos sliders siguen
    ocultándose con otro tipo activo, que eso sí sigue siendo correcto: son el Pre Delay
    fraccionario de 2x2 Chorus y con otro tipo esas direcciones son otra cosa.
  - 3 tests JVM nuevos (364 en total). `./gradlew :app:testDebugUnitTest` (364/364),
    `:app:assembleDebug`, `:app:installDebug` y `:app:lintDebug` (**15 warnings, el mismo
    baseline**: los 10 que introdujo esta tanda eran strings huérfanos de los controles
    retirados, y se borraron) limpios.

- **2026-09-06 — Dos correcciones de UX sobre lo de esta misma fecha**: el gesto de las perillas
  y el vocabulario del diagrama de la cadena. Ninguna toca protocolo ni direcciones.
  - 🎛️ **El gesto de las perillas era inutilizable, y el motivo era estructural, no de
    sensibilidad.** Las perillas viven dentro de un `Row` con **scroll horizontal**: cualquier
    arrastre que empiece sin más se lo lleva el contenedor, así que la perilla apenas respondía.
    Subir o bajar la sensibilidad no lo habría arreglado nunca.
    - **Gesto nuevo: mantener + arrastrar en horizontal.** Exigir una pulsación mantenida es lo
      que hace que el gesto **gane frente al scroll** (`detectDragGesturesAfterLongPress`
      reclama el puntero), y de paso da el momento natural para agrandar la perilla.
    - **La perilla agrandada se proyecta hacia arriba del dedo**, no debajo: con el dedo encima
      no se puede leer lo que se está ajustando, que es justo lo que hace falta ver. Va en un
      `Popup` y no con un desplazamiento, porque la fila recorta a sus límites y una perilla
      grande dibujada dentro saldría cortada.
    - **Derecha sube, izquierda baja, y es relativo** (delta acumulado): la posición X del dedo
      no significa un valor, así que se puede seguir ajustando aunque el dedo se salga.
    - ✅ **Avanza de paso en paso sin saltarse ninguno**, que es la propiedad que pedía el punto
      12 de "Pendiente por probar". El valor se recalcula **siempre desde el que había al
      empezar el gesto** más un número entero de pasos — nunca desde el último valor emitido,
      que iría acumulando el redondeo hasta separarse del dedo. Hay un test que barre el
      arrastre píxel a píxel y comprueba que salen las 28 frecuencias, consecutivas y sin
      huecos, y otro que comprueba que ir y volver cierra exacto.
    - 🧰 **La matemática se extrajo a `knobValueAt`, fuera del composable, para poder probarla.**
      Es la parte que puede estar mal de una forma que **no se ve mirando la pantalla**, y la
      única de todo el gesto que no exige un dedo.
    - **Sensibilidad**: `KNOB_PIXELS_PER_STEP = 32f`, elegido por el lado lento como se pidió.
      No hay ninguna referencia que diga cuál es el bueno — solo el dedo — así que está en una
      constante con nombre y un comentario que dice hacia dónde moverla.
  - 🔗 **El diagrama de la cadena pasa a un vocabulario de diez bloques**: `INPUT`, `BOOSTER`,
    `FX`, `MOD`, `AMP`, `FXLOOP`, `DELAY`, `DELAY2`, `REVERB`, `SPEAKER`. Enseñar `CN_S`, `CH_B`
    o `USB` en un diagrama de señal no informa de nada: son ruteo interno.
    - ✅ **El mapeo está confirmado contra un editor de Mk2, no supuesto.** `FxFloorboard` hace
      exactamente este trabajo en `summaryDialog.cpp:89-105` y de ahí salen `OD`→Booster,
      `FX1`→MOD, `FX2`→FX, `DD1`→Delay 1, `DD2`→Delay 2 y `RV`→Reverb — los seis que ya se
      habían documentado, ahora con fuente.
    - 🆕 **Y resuelve los dos que faltaban**, que no se pueden adivinar del nombre crudo:
      **`CH_A` es el PreAmp**, o sea `AMP` (`stompBox.cpp:827` le pone literalmente
      `fxName = tr("PreAmp")`, y `summaryDialog.cpp` lo sustituye por `[PreAmp]`); y **`LP` es
      el Send/Return**, o sea `FXLOOP`. Hasta ahora `CH_A`/`CH_B` estaban documentados como
      "Canal A/B", que era una lectura del nombre y no un dato — **corregido**.
    - ✅ **Las omisiones también coinciden con esa fuente**: ese mismo código sustituye por
      cadena vacía `CH_B`, `CN_S`, `CN_M`, `NS_2`, `CS` y `USB`. Un editor de Mk2 llegó por su
      cuenta a la misma conclusión.
    - ⚠️ **`CAB` se omite aunque FxFloorboard sí lo dibuje**, y es decisión propia: el diagrama
      termina en un `SPEAKER` fijo, que es lo que `CAB` significa. Pintar los dos sería decir lo
      mismo dos veces.
    - **La mejor señal de que el mapeo no está cruzado**: filtrar la cadena por defecto del
      `.tsl` real da `BOOSTER → MOD → FX → AMP → FXLOOP → DELAY → DELAY2 → REVERB` — booster
      antes del previo, loop y delays después, reverb al final. Sale algo con sentido musical, y
      hay un test que lo fija.
    - El filtro vive en `ChainBlock.diagramSequence`, Kotlin puro: es lo único de este cambio
      que se puede comprobar sin amplificador.
  - 13 tests JVM nuevos (377 en total). `./gradlew :app:testDebugUnitTest` (377/377),
    `:app:assembleDebug`, `:app:installDebug` y `:app:lintDebug` (**15 warnings, el mismo
    baseline**) limpios.

- **2026-09-06 — Tres correcciones al widget de perilla.** ⚠️ **Aplican al componente genérico,
  no solo a las perillas del EQ**: `KnobControl` es *la* perilla del proyecto de aquí en
  adelante, así que lo que se decida aquí vale para cualquier control que se cablee con ella.
  - ⬆️ **La proyección sube mucho más, y ahora en `dp`.** El conjunto ampliado se levanta 240 dp
    sobre la perilla original, que deja el borde de abajo unos 50 dp por encima del punto donde
    está el dedo — sitio de sobra para el dedo **y la mano**.
    - 🐛 **El cambio que de verdad importaba no es el número, es la unidad.** La primera versión
      lo tenía en **píxeles crudos**, y eso en una pantalla densa se traduce en *menos distancia
      física* — justo la magnitud que aquí cuenta, porque lo que tiene que caber debajo mide lo
      que mide en milímetros. En `dp` la separación es la misma en cualquier teléfono.
  - 🔤 **El valor pasa arriba del todo, fuera de la perilla.** Antes iba debajo del disco, dentro
    del recuadro; ahora es una etiqueta propia, destacada, **encima de todo el conjunto**. Es lo
    que hay que leer mientras se ajusta y es la parte que más lejos queda de la mano. El nombre
    del parámetro se queda debajo del disco, donde no estorba.
  - 🚫 **El scroll de la pantalla se congela mientras dura el ajuste**, en los dos ejes, y se
    restaura al soltar.
    - ⚠️ **Hacía falta aunque el gesto ya reclamara el puntero.** `detectDragGesturesAfterLongPress`
      impide que el scroll *robe* el arrastre, pero el dedo sigue apoyado sobre un contenedor
      desplazable: basta un movimiento vertical —que en este gesto no significa nada— para que
      la pantalla se vaya sola por debajo. Son dos problemas distintos y el primero solo
      resolvía uno.
    - Se implementa con un `KnobInteraction` compartido y dos helpers,
      `Modifier.knobAwareVerticalScroll()` / `knobAwareHorizontalScroll()`, que sustituyen a los
      cinco scrolls de las dos pantallas. **Helpers y no llamadas sueltas a propósito**: un
      contenedor que se olvide de congelarse arruina el ajuste, y el síntoma —"el valor salta
      raro"— no señala al scroll. ✅ Comprobado que no queda ningún `verticalScroll`/
      `horizontalScroll` crudo en `ui/screens/`.
    - 🐛 **El riesgo que abre esto es el scroll trabado para siempre**, si una perilla empieza un
      gesto y desaparece de la composición antes de soltarlo (un cambio de tipo de efecto a
      media pulsación). Lo suelta un `DisposableEffect` en `KnobControl`, y hay un test de que
      soltar de más es inofensivo, que es lo que ese camino necesita.
    - ✅ **El scroll normal fuera del ajuste no cambia**: el helper solo pasa
      `enabled = !adjusting` al mismo modificador de siempre, y `adjusting` es false salvo entre
      el hold y el soltar.
  - 3 tests JVM nuevos (380 en total), todos sobre lo que se puede comprobar sin dedo: la
    máquina de estados del congelado. `./gradlew :app:testDebugUnitTest` (380/380),
    `:app:assembleDebug`, `:app:installDebug` y `:app:lintDebug` (**15 warnings, el mismo
    baseline**) limpios.

- **2026-09-06 — El menú hamburguesa se abría al arrastrar una perilla.** `ModalNavigationDrawer`
  trae por defecto el gesto de deslizar desde el borde para abrirse, y ese gesto competía
  directamente con el arrastre lateral del rediseño de knobs de esta misma fecha: bajar un
  valor con el dedo también podía leerse como "abrir el menú".
  - ✅ **Arreglado con `gesturesEnabled = false`** en el `ModalNavigationDrawer`. El botón
    hamburguesa sigue abriéndolo igual —llama a `drawerState.open()` explícitamente, no depende
    del gesto—, así que el menú pasa a abrirse **solo** con el botón, nunca deslizando.
  - No hizo falta tocar el gesto de la perilla ni el `KnobInteraction` de la corrección
    anterior: el conflicto era con un gesto **ajeno** a la perilla, del contenedor de más
    arriba en el árbol de composición, no con el scroll que ya se congela.
  - `./gradlew :app:testDebugUnitTest` (380/380), `:app:assembleDebug`, `:app:installDebug` y
    `:app:lintDebug` (15 warnings, el mismo baseline) limpios — cambio de un parámetro, sin
    tests nuevos porque no hay lógica que probar en JVM (es una propiedad de un composable de
    Material 3).

- **2026-09-06 — La perilla grande desaparecía a mitad de arrastre: `pointerInput` estaba
  clavado en el propio valor que el gesto cambia.** El modificador era
  `Modifier.pointerInput(enabled, value, range, step) { ... }`, y dentro de ese bloque
  `onDrag` llama a `onValueChanged`, que hace que `value` cambie. **Cada cambio de `value` es
  un cambio de key**, y Compose reinicia la corrutina del detector de gestos cuando una key
  cambia — lo que cancela el arrastre en curso (`onDragCancel`) casi en el acto. El síntoma
  encajaba exacto: mueve un poco, y la perilla grande se cierra sola.
  - ✅ **Arreglado con `rememberUpdatedState`** para `value`, `range` y `step`: el detector se
    monta una sola vez (la key de `pointerInput` queda solo en `enabled`, que sí debe
    reiniciarlo para atar o soltar el gesto) y lee siempre la versión más reciente de los tres
    sin que su cambio reinicie nada.
  - `./gradlew :app:testDebugUnitTest` (380/380), `:app:assembleDebug`, `:app:installDebug` y
    `:app:lintDebug` (15 warnings, el mismo baseline) limpios — no hay tests nuevos porque
    `knobValueAt` no cambió; lo que se corrigió es cuándo Compose reinicia el detector, que no
    es lógica que un test JVM pueda ejercitar.

- **2026-09-06 — Sensibilidad de la perilla ajustada tras probarla con el dedo, ya con el gesto
  funcionando.** `KNOB_PIXELS_PER_STEP` baja de 32 a 24: con las 28 frecuencias del paramétrico
  siguen siendo ~670 px para recorrer la lista entera (más de lo que mide la pantalla, así que
  cada posición se sigue apuntando sin pelearse), pero gira algo más rápido. Confirmado con el
  dedo real, es el primer ajuste de este número que llega con esa confirmación en vez de ser
  una elección conservadora de partida.
  - `./gradlew :app:testDebugUnitTest` (380/380), `:app:assembleDebug`, `:app:installDebug` y
    `:app:lintDebug` (15 warnings, el mismo baseline) limpios — un solo número, sin tests
    nuevos: el que ya cubría el barrido de las 28 posiciones usa un margen fijo que no depende
    del valor exacto de la constante.

- **2026-09-08 — Troceado de los SET largos: ya se puede mandar un preset `.tsl` entero al
  amplificador.** Era el punto que quedaba abierto del bloque 5 junto con el envío en sí. Los
  bloques `UserPatch%Fx(1)` y `Fx(2)` miden 221 bytes y no cabían en un SET del tamaño que este
  proyecto está dispuesto a mandar; ahora se parten en **128 + 93**, cada trozo a su propia
  dirección.
  - **`RolandSysEx.setChunked(address, data, chunkSize = 128)`** (protocol/, puro): parte el
    payload y construye un SET por trozo, sumando la dirección **en base 128** con
    `Address.plus`. Eso no es cosmética: `Fx(1)` empieza en `60 00 01 00` y su segundo trozo cae
    en `60 00 02 00`, **no** en `60 00 01 80`, que ni siquiera es una dirección legal. El último
    trozo va sin relleno, y un payload vacío no produce ningún mensaje.
    También se expuso `RolandSysEx.OVERHEAD` / `payloadSizeOf(message)`, para contar bytes
    escritos sin volver a partir el payload por fuera.
  - **`protocol/tsl/TslTransfer`** (nuevo, puro): convierte una `MemoryImage` en los SET que la
    escriben, bloque a bloque y en el orden del mapa. Dos reglas heredadas y no inventadas:
    **todo o nada por bloque** (si la imagen no trae los N bytes enteros no se manda, porque
    media escritura mezclaría dos presets en uno), y **los bloques `DISPUTED` no se mandan
    nunca** — los tres `Contour` y `GafcExp1AsgnMinMax`. Aquí equivocarse de dirección no
    enseñaría un número raro: **escribiría encima de otro parámetro del amplificador**. Lo que
    no se manda sale en `skipped`, que es información para el usuario y no diagnóstico interno:
    esas direcciones **se quedan con lo que hubiera antes**, así que lo que suena es el preset
    del fichero mezclado con restos del anterior.
  - **`KatanaRepository.sendPreset(image)`** (device/): manda los ~20 mensajes en orden con
    ~30 ms entre medio y devuelve un `PresetSendResult`. Hereda entera la disciplina del
    guardado de presets (CLAUDE.md §5): **no toca el edit mode** (es un ajuste explícito del
    usuario, §4.2), **no actualiza la caché** —dar por buenos los valores del fichero sería
    enseñar lo que se pidió, no lo que el amp aceptó— y el `verdict` **no afirma éxito**: dice
    que los mensajes salieron por el cable y que un SET no se confirma. Un fallo de envío corta
    en seco y nombra el bloque y el trozo.
  - ⚠️ **No hay botón todavía**: `sendPreset` existe y está probado, pero ninguna pantalla lo
    llama. Mandar un preset al amplificador es destructivo y sigue siendo el otro punto abierto
    del bloque 5; cablear el botón va con su confirmación, y sin hardware para probarlo no tenía
    sentido adelantarlo.
  - ❌ **De paso, una corrección a CLAUDE.md**: decía que un SET de 221 bytes "no cabe en un solo
    paquete USB de 512 B una vez empaquetado" y **es falso**. `14 + 221 = 235` bytes de mensaje
    dan `ceil(235/3) × 4 = 316` bytes en el cable — caben de sobra. **Trocear no lo obliga el
    transporte**; ver "Notas y decisiones técnicas".
  - **17 tests JVM nuevos (397 en total)**, en tres ficheros: `SetChunkingTest` (7 — los 221
    bytes en dos mensajes con el acarreo de página, el múltiplo exacto sin mensaje vacío, el
    bloque corto idéntico al `set()` de siempre, la concatenación que reconstruye el payload y
    la contigüidad para siete tamaños de trozo distintos), `TslTransferTest` (6 — sobre
    `default_mk2.tsl`: solo `Fx(1)`/`Fx(2)` se trocean, los `DISPUTED` no generan ningún mensaje
    y sí aparecen en `skipped`, y un bloque incompleto no se manda a medias) y `PresetSendTest`
    (4 — la **ida y vuelta contra un `KatanaLink` falso**: lo enviado se remonta en una
    `MemoryImage` que coincide byte a byte con la de partida, más el corte por fallo de cable y
    el veredicto que no afirma nada). `./gradlew :app:testDebugUnitTest` (397/397) y
    `:app:assembleDebug` limpios.
  - ⚠️ **Nada probado contra el amplificador**: ver "Pendiente por probar", punto 15.

- **2026-09-08 — El botón que faltaba: "Enviar al amplificador" desde la Biblioteca.**
  `KatanaRepository.sendPreset` existía desde esta misma fecha pero no lo llamaba nadie; ahora lo
  llama la vista de detalle y la de edición de un preset. **Implementado y probado en JVM; el
  envío real contra el amplificador sigue en "Pendiente por probar", punto 15, sin cambios.**
  - **`ui/screens/PresetSendFlow.kt` (nuevo): toda la lógica de estado, sin Compose y sin
    `android.*`.** Es la respuesta a "esto no puede vivir dentro del composable": qué diálogo
    toca, qué bloques se avisan, si se admite un segundo envío y cómo se cuenta un corte a mitad
    son decisiones, y las decisiones se prueban. En Compose queda **solo** pintar el estado y
    llamar a los métodos.
  - **Las cuatro cautelas son las mismas del guardado en canal**, no un diseño nuevo:
    **dos confirmaciones** (revisión → confirmación final, y desde la revisión `onConfirmed` no
    hace nada), **bajo `canEdit`** (conectado + Edit Mode, igual que el resto de la escritura
    destructiva), **nunca dos envíos a la vez** (`request` devuelve false y **no encola**: un
    segundo envío destructivo que el usuario ya no ve venir es peor que ignorarlo), y **un
    resultado que no afirma éxito**, tomado tal cual de `PresetSendResult.verdict`.
  - **Lo que NO se va a escribir se enseña ANTES**, en el paso 1: los cuatro bloques `DISPUTED`
    y cualquiera incompleto, con su motivo. Esas direcciones **se quedan con lo que ya haya en
    el amplificador**, así que lo que suena es una mezcla; enterarse después sería tarde. Si el
    plan no tiene ni un bloque completo, el botón de continuar se deshabilita.
  - **Los dos casos feos, decididos explícitamente** (ver "Notas y decisiones técnicas"):
    cancelar produce un resultado visible **"no se mandó nada"** en vez de un cierre silencioso;
    y un corte a mitad **nunca dice que algo se guardara** — distingue además "el cable falló"
    (se sabe cuántos mensajes salieron y en qué bloque y trozo paró) de "el USB desapareció"
    (ni eso se sabe, y el texto lo admite).
  - Wiring: `DebugConnectionViewModel.presetSend` construye el flujo con el envío real —que
    devuelve **null si no hay transporte**, distinto de fallar— y `DebugConnectionScreen` le pasa
    a `LibraryPane` un `PresetSendControls`. **Null oculta el botón entero** (previews), no lo
    enseña apagado sin explicación; apagado por falta de Edit Mode sí, y con su aviso.
  - **13 tests JVM nuevos (410 en total)** en `PresetSendFlowTest`: los dos pasos y que hacen
    falta los dos síes, volver atrás sin cancelar, cancelar en cualquiera de los dos pasos sin
    mandar nada, los `DISPUTED` y un bloque incompleto listados antes de mandar, el segundo envío
    rechazado mientras hay uno en curso, cancelar sin abortar el que ya salió, y los cinco
    finales (completo, corte, excepción, sin transporte, cancelado).
    ⚠️ **Lo único que ningún test cubre está dicho en el propio fichero**: que el botón se vea y
    que tocarlo llame a quien debe. Eso necesita dispositivo, y es justo por eso que la lógica se
    sacó fuera del composable.
  - `./gradlew :app:testDebugUnitTest` (410/410), `:app:assembleDebug` y `:app:lintDebug`
    (**15 warnings, el mismo baseline** — las seis `PluralsCandidate` que introdujeron los
    textos nuevos se quitaron reescribiéndolos como "mensajes: %d" en vez de "%d mensajes") limpios.

- **2026-09-08 — Arranca el bloque 6: `AmpScreen`, la primera pantalla de dominio.**
  **Implementado y pendiente de probar** (extracción y wiring dados por buenos con los tests JVM
  y la compilación; lo de hardware sigue siendo lo de siempre — audio y direcciones, no esta
  pantalla).
  - **La decisión de arquitectura se tomó y se documentó antes de escribir código**
    (CLAUDE.md §4.2, "Cómo se parte `SlidersPane`"): **extraer los composables del dominio de
    amplificador y que los llamen los dos**, en vez de duplicar una pantalla nueva o congelar
    `SlidersPane` como solo-offline. Lo que la hizo fácil fue mirar cómo estaba partido el código:
    `SlidersPane` es *acciones de preset* → *bloque de amplificador* → *cinco tarjetas de efecto*
    → `NoPanelPane`, y **todo lo que no es la sección de efectos ya era dominio de amplificador**.
    Así que solo hubo que sacar el bloque contiguo del medio a `AmpSection` y reutilizar
    `NoPanelPane`, que era un composable aparte desde el 2026-09-06.
  - ⚠️ **`SlidersPane` no cambia de orden, y fue criterio explícito**: `NoPanelPane` se sigue
    llamando al final, después de los efectos, en vez de juntar todo el dominio de un tirón.
    Juntarlo habría reordenado una pantalla que ya se usa con el amplificador delante, y
    reordenar la UI no era la tarea. ✅ **La señal de que la reutilización no se partió: ningún
    test existente hizo falta tocarlo** (el único diff en `app/src/test` es el margen de
    `KatanaRepositoryTest` del 2026-09-06, de antes de esto).
  - **`AmpDomain.kt` (nuevo, Kotlin puro sin Compose)**: qué `LevelId`/`SelectorId`/
    `NoPanelParamId` son del amplificador, cuáles de un efecto y cuáles están **retirados**
    (Bright y Gain SW, probados sin efecto el 2026-09-06 — marcarlos evita confundirlos con un
    olvido). Los niveles siguen siendo una **resta** (`LevelId.entries - efectos`), no una lista a
    mano.
  - **`AmpScreen` reúne todo lo del amplificador y ni un control de efecto**: canal, modelo y
    variación, los seis niveles del panel, Solo, Noise Gate, Contour, EQ1 y EQ2 (gráfico +
    paramétrico) y la cadena con su diagrama. Todo bajo `canEdit`, **salvo el selector de canal**,
    que es la excepción de siempre del contrato de Edit Mode (§4.2). Lleva también "Releer", que
    es de solo lectura y repuebla justo lo que la pantalla enseña.
  - ⚠️ **Lo que deliberadamente NO trae**: guardar preset en un canal y exportar a `.tsl`. Son
    operaciones sobre **el preset entero**, no sobre el amplificador; duplicarlas por pantalla
    de dominio sería tener el mismo botón destructivo en cuatro sitios. Se quedan en Sliders
    hasta que exista `PresetsScreen`.
  - **La cadena se queda en `AmpScreen`** y no espera a pantalla propia — criterio en CLAUDE.md
    §4.2 y en "Notas y decisiones técnicas" de aquí abajo.
  - **Navegación mínima**: una entrada más en el menú hamburguesa (`DebugSection.AMP`), que es
    todo lo que hace falta mientras las otras pantallas de dominio no existan.
  - **7 tests JVM nuevos (417 en total)** en `AmpDomainTest`: que **todo `SelectorId` esté
    clasificado exactamente una vez** (el que protege de verdad contra el olvido silencioso), que
    los niveles se repartan sin solaparse y sumen los once, que la pantalla de amp no reclame
    ningún control de efecto, que la cadena y las posiciones de EQ sean del amplificador, y que
    Bright/Gain SW consten como retirados y no como sin clasificar.
    ⚠️ **Lo que ningún test cubre y está dicho en el propio fichero**: que la pantalla se vea
    bien y quepa. Para eso hay un `@Preview` y la prueba a mano del punto 16.
  - `./gradlew :app:testDebugUnitTest` (417/417), `:app:assembleDebug`, `:app:installDebug` y
    `:app:lintDebug` (**15 warnings, el mismo baseline**) limpios.

- **2026-09-08 — Segunda pantalla de dominio: `EffectsScreen`.** **Implementado y pendiente de
  probar** (extracción y wiring dados por buenos con los tests JVM y la compilación; lo de
  hardware sigue siendo lo de siempre). Es la aplicación literal del patrón que fijó
  `AmpScreen` unas horas antes (CLAUDE.md §4.2) — no hizo falta reabrir ninguna decisión de
  arquitectura, solo aplicarla una segunda vez.
  - **`EffectsSection`** (nuevo, en `ui/screens/EffectsScreen.kt`): las cinco tarjetas de
    efecto, sacadas del mismo bloque contiguo de `SlidersPane` donde ya estaban. `EffectCard`
    pasó de `private` a `internal`, igual que hizo `AmpSection` con sus vecinos. `SlidersPane`
    llama a `EffectsSection` **en el sitio exacto** donde tenía este código: entre `AmpSection`
    y `NoPanelPane`, sin mover nada.
  - ✅ **No hizo falta ninguna lista nueva de reparto.** `EffectId.entries` ya era la fuente de
    verdad de "qué es un efecto" —es lo que hacía que `AmpDomain.LEVELS` fuera una resta desde
    el principio—, y los tres selectores de corte de Delay/Reverb ya vivían en
    `AmpDomain.EFFECT_SELECTORS`. `EffectsScreen` **consume** lo que `AmpDomain` ya clasificó,
    no añade una clasificación propia.
  - **`EffectsScreen` reúne las cinco tarjetas y ni un control de amplificador**: sin canal, sin
    modelo, sin EQ, sin Noise Gate, sin Contour, sin cadena — todo eso se queda en `AmpScreen`.
    **Todo bajo `canEdit`, sin la excepción del canal** que sí tiene `AmpScreen` (aquí no hay
    ningún control que sea esa excepción). Lleva "Releer", igual que `AmpScreen`, porque no es
    destructivo. **No lleva** guardar en canal ni exportar, por la misma razón que tampoco los
    lleva `AmpScreen`: son del preset entero y esperan a `PresetsScreen`.
  - **Navegación**: una entrada más en el menú hamburguesa (`DebugSection.EFFECTS`), entre
    `AMP` y `SLIDERS`.
  - **4 tests JVM nuevos (421 en total)** en `EffectsDomainTest`: la mirada simétrica a
    `AmpDomainTest` — que los cinco niveles de efecto no se solapen con `AmpDomain.LEVELS`, que
    los tres selectores de corte no se solapen con `AmpDomain.SELECTORS` ni con los retirados,
    que ningún `NoPanelParamId` sea de efecto (los tres son del amplificador), y que
    `EffectsScreen` no tenga ninguna lista de reparto propia —reutiliza `EffectId.entries` y
    `AmpDomain`—.
    ✅ **Señal de que la reutilización no se rompió**: el único diff en `app/src/test` sigue
    siendo el margen de `KatanaRepositoryTest` del 2026-09-06, anterior a esto — ningún test de
    `SlidersPane` ni del editor offline hizo falta tocarlo.
  - ⚠️ **Lo que ningún test cubre y está dicho en el propio fichero**: que la pantalla se vea
    bien y quepa. `@Preview` y la prueba a mano del punto 16 de "Pendiente por probar".
  - `./gradlew :app:testDebugUnitTest` (421/421), `:app:assembleDebug` y `:app:lintDebug`
    (**15 warnings, el mismo baseline**) limpios.

- **2026-09-09 — `PresetsScreen` y el borrado de `SlidersPane`: cerrada la Fase 1 de la UI.**
  **Implementado y pendiente de probar** (reubicación y wiring dados por buenos con los tests JVM
  y la compilación; lo de hardware sigue siendo lo de siempre — guardar en canal, enviar al
  amplificador y el troceado de 128 bytes).
  - **La decisión de arquitectura se tomó antes de escribir código** (CLAUDE.md §4.2, "El
    cierre"): **`SlidersPane` se borra entera**, en vez de conservarla como UI del editor offline.
    Y la decidió el propio código: tras extraer `AmpSection` y `EffectsSection`, **su rama offline
    era literalmente `AmpSection` + `EffectsSection` + `NoPanelPane`**, con todo lo demás tras un
    `if (!offline)`. Había dejado de ser una pantalla para ser una cáscara.
  - **Lo que se borra, no se mueve**: el parámetro `offline` (un booleano que hacía que un
    composable significara dos cosas), sus tres ramas, `OFFLINE_DIAGNOSTICS` —un objeto de
    callbacks vacíos que existía solo para rellenar un hueco de la firma— y ~60 parámetros. El
    editor offline pasa a `PresetEditorBody`, privado y junto a su único llamador.
  - ⚠️ **Un detalle que casi se cuela: el scroll.** `SlidersPane` aportaba el
    `knobAwareVerticalScroll()` del editor en su Column exterior; al quitarla, el editor se
    habría quedado sin poder bajar. `PresetEditorBody` reproduce esa Column tal cual —scroll,
    padding y todo—, que es lo que hace que el editor se vea **exactamente igual que antes**.
  - **`PresetsScreen` reúne tres cosas ya implementadas, sin reimplementar ninguna**: guardar el
    estado en un canal, exportar a `.tsl`, y la Biblioteca entera (`LibraryPane` tal cual, con su
    lista, import, detalle, editor offline, "Guardar"/"Guardar como" y "Enviar al amplificador…").
  - **Cómo se distingue "en vivo" de "Biblioteca"**, que era la decisión explícita a tomar:
    **cuatro señales, no una** — dos encabezados con subtítulo que dicen sobre qué actúa cada uno,
    un divisor, **reglas de habilitación distintas y visibles** (arriba se apaga sin cable y —para
    guardar— sin Edit Mode; abajo funciona desenchufada, que es su razón de ser), y que **abrir un
    preset ocupa la pantalla entera** y hace desaparecer el bloque en vivo. Detalle en CLAUDE.md
    §4.2.
  - ⚠️ **"Releer" no se llevó a `PresetsScreen`** pese a estar en la misma fila de botones que
    guardar y exportar: repuebla la caché de controles, o sea lo que enseñan `AmpScreen` y
    `EffectsScreen` —que ya lo tienen—, y aquí no hay ningún control que refrescar. Exportar no lo
    necesita: `exportImage` lee su propio dump completo.
  - **Menú en cuatro entradas**: Logs · Amplificador · Efectos · **Presets**. "Sliders" y
    "Biblioteca" desaparecen, y con ellas sus dos strings (lint sigue en su baseline de 15, sin
    recursos huérfanos nuevos).
  - **421/421 tests, sin tocar ni uno.** Es la misma señal de las dos extracciones anteriores y
    aquí vale doble: **este cambio borra código**, así que un test que dependiera de `SlidersPane`
    lo habría delatado. Ninguno lo hacía — lo que se prueba en JVM es `PresetEditor` (el estado),
    `PresetSendFlow` (el envío) y `AmpDomain` (el reparto), no el árbol de Compose. El único diff
    en `app/src/test` sigue siendo el margen de `KatanaRepositoryTest` del 2026-09-06.
  - ⚠️ **El `@Preview` cubre solo la mitad en vivo**, y está dicho en el fichero: `PresetsScreen`
    entera necesita un `LibraryViewModel` (`AndroidViewModel` que lee disco), que no se puede
    instanciar en una preview. Lo que sí se ve es lo único que la pantalla añade.
  - `./gradlew :app:testDebugUnitTest` (421/421), `:app:assembleDebug` y `:app:lintDebug`
    (**15 warnings, el mismo baseline**) limpios.

- **2026-09-09 — Fase 2 de la UI: shell de navegación, estado de conexión siempre visible y Logs
  degradado a secundario.** **Implementado y pendiente de probar** (shell, navegación y estados de
  conexión dados por buenos con los tests JVM, la compilación y el `@Preview`; lo de hardware
  sigue siendo lo de siempre).
  - **La decisión de arquitectura se tomó y se documentó antes de escribir código**
    (CLAUDE.md §4.2, "La navegación"): **seguir sin `NavHost`**, con `Scaffold` + `NavigationBar` +
    `BackHandler`. **Sin dependencia nueva.**
  - **El argumento que lo decidió no fue el coste, fue que el detalle de un preset no es estado de
    ruta**: lo que habría que "navegar" es un `EditingSession` que **posee un `KatanaRepository`
    vivo** sobre `OfflineKatanaLink`, creado con `viewModelScope` (§4.5). Un argumento de ruta es
    dato serializable; esto es un objeto con su propio scope. Convertirlo en ruta deja dos fuentes
    de verdad, o el mismo `when` con un `NavController` encima. ⚠️ Y `viewModel()` dentro de un
    `NavBackStackEntry` se scopea **por ruta**: la lista y el editor recibirían `LibraryViewModel`
    distintos, fallando en silencio justo donde compartir importa.
  - **Lo que sí se tomó de esa opción**: el back. `ShellNavigation` (Kotlin puro, con tests)
    replica `popUpTo(startDestination)` — desde cualquier pestaña vuelve a Amplificador, y en
    Amplificador **no consume** el back para que cierre la app.
  - **Barra superior con el estado de conexión en las cuatro pantallas.** Antes solo se sabía
    yendo a Logs: la respuesta a "¿por qué no se mueve nada?" estaba en otra pantalla.
  - **Barra inferior con las tres pantallas de dominio; Logs pasa a la barra superior** como
    "Avanzado", entero y con la misma funcionalidad. Recorre `DebugSection.PRIMARY`, así que
    añadir una pantalla la pone en la barra — y olvidarse de clasificarla la deja inalcanzable,
    que es lo que fija un test.
  - ⚠️ **"Sin amplificador" deja de ser un panel gris.** `AmpScreen` y `EffectsScreen` (y el
    bloque **en vivo** de `PresetsScreen`) enseñan qué falta —una frase por estado: buscando, sin
    permiso, no encontrado, fallo— con el botón de buscar a mano. **La Biblioteca no cambia y es
    deliberado**: funciona desenchufada, que es su razón de ser (§4.5).
  - ✅ **`canEdit` pasa a tener una sola definición.** Estaba escrito tres veces como
    `connected && editMode`; ahora sale de `ShellState.availabilityOf`, con un test que **fija la
    equivalencia con la fórmula vieja** para que centralizarla no le cambie el significado a la
    condición que gobierna toda la escritura destructiva.
  - **Cambios sin guardar al cambiar de pestaña: se conservan, y no por suerte.** La sesión vive
    en el ViewModel porque tiene que poseer su repositorio (§4.5), así que irse a Efectos y volver
    reencuentra el editor tal cual. ⚠️ **El único camino que descartaba trabajo era "Volver", y
    ahora pregunta** — sin eso la app preservaría al navegar y borraría al volver, sin decirlo. El
    back del sistema hace lo mismo, que para el usuario es el mismo gesto.
  - ⚠️ **Sin iconos en la barra inferior, y es decisión**: `material-icons-core` no está en el
    classpath y añadir una dependencia por tres pictogramas contradice §6. Tres entradas con su
    nombre escrito se entienden igual.
  - **12 tests JVM nuevos (433 en total)** en `ShellStateTest`: el reparto de destinos, que toda
    sección sea alcanzable, las tres ramas del back, que los seis estados de conexión tengan
    indicador, y las tres de `availabilityOf` más la equivalencia con la fórmula vieja.
    ✅ **Ningún test existente hizo falta tocarlo** — `AmpScreen`, `EffectsScreen`, `PresetsScreen`,
    `PresetEditor` y `PresetSendFlow` siguen igual; el único diff en `app/src/test` sigue siendo el
    margen de `KatanaRepositoryTest` del 2026-09-06.
  - `./gradlew :app:testDebugUnitTest` (433/433), `:app:assembleDebug` y `:app:lintDebug`
    (**15 warnings, el mismo baseline**, sin recursos huérfanos: el string del hamburguesa se fue
    con el menú) limpios.

- **2026-09-09 — Fase 3 de la UI: sistema de diseño propio, aplicado a las tres pantallas, más
  ícono y nombre.** **Implementado y pendiente de mirarlo en el teléfono** — es una fase visual, y
  lo visual no lo confirma ni un test ni un `@Preview` (ver "Pendiente por probar", punto 19).
  - **La dirección se eligió y se documentó antes de escribir una línea** (CLAUDE.md §4.6):
    **Opción A, chasis oscuro con un solo acento ámbar**. ⚠️ **Y no ganó por gusto**: los tres
    colores de slot de efecto son un hecho del dispositivo, y sobre una paleta Material sembrada
    con ámbar (Opción B) **un punto amarillo sobre una superficie ámbar deja de ser un punto
    amarillo**. Grafito neutro es el único fondo que no compite con verde/rojo/amarillo.
  - ⚠️ **Antes de esta fase la app no tenía tema.** `Color.kt` seguía siendo la plantilla de
    Android Studio (`Purple80`/`Pink40`) y encima `dynamicColor = true`: en el teléfono de pruebas
    (API ≥ 31) **la paleta salía del fondo de pantalla**, y la del proyecto no se usaba nunca.
  - **Tres decisiones que quitan opciones a propósito**, todas en CLAUDE.md §4.6:
    - **`KTNAControlTheme` pierde `darkTheme`**: la app es siempre oscura. Los colores de slot se
      afinaron contra grafito; una versión clara pediría un segundo juego afinado aparte.
    - **`dynamicColor` se retira entero, no se pone en `false`**: dejar el parámetro deja la vía
      para que el wallpaper vuelva a decidir la paleta.
    - **`themes.xml` deja de heredar de `…Material.Light.NoActionBar`**, que pintaba **la ventana
      en blanco antes de que Compose arrancara** — un fogonazo en cada apertura.
  - ✅ **Los colores de efecto: la duda se cerró sin amplificador, y `midi.xml` se contradice a sí
    mismo.** Dice `00` = RED en dos sitios (`:43961` y el bloque de conversión `:50863`) y a la vez
    etiqueta el primer slot de tipo (`06 24`) como **GREEN** (`:43567`). El desempate salió de
    `default_mk2.tsl`: los cinco efectos tienen color `00` y en los cinco **el tipo activo coincide
    con el del primer slot** (`0A`/`1D`/`15`/`00`/`04`), con los otros dos slots en valores
    distintos — **5 de 5**. Más `Adresses.txt:72-74` (`[0A|0B|0E]` = verde/rojo/amarillo) y
    `color_assign.json` (`[0,1,2]` → green/red/yellow). **`EffectColor` ya era correcto**
    (`GREEN(0x00)`); lo que cambia es que ahora está justificado en vez de heredado.
  - **`EffectCard` se tiñe por su slot activo**: franja de 4 dp en el borde izquierdo + punto junto
    al nombre. ⚠️ **El tinte nunca es la única pista** — el selector de debajo sigue diciendo
    «Verde/Rojo/Amarillo» con palabras, y eso es lo que hace que el parecido entre el ámbar del
    acento y el amarillo del slot no llegue a importar. Un valor desconocido (o `null`, que es el
    caso normal antes del primer dump) cae en gris de borde: **no se adivina un color**.
  - ⚠️ **La franja va con `drawBehind`, no con un `Box` de altura intrínseca**: dentro de la
    tarjeta hay `FlowRow` y sliders, y pedirles medidas intrínsecas es de las cosas que revientan
    en tiempo de ejecución y no al compilar.
  - **Fuente única de verdad, en tres ficheros**: `Color.kt` (paleta entera, y ya no había ni un
    `Color(0x…)` en `ui/screens/`), `Type.kt` (la escala completa; antes solo estaba `bodyLarge` y
    los otros catorce eran los de Material sin que nadie los hubiera mirado) y **`Spacing.kt`,
    nuevo**. ⚠️ **`Spacing` no se propagó a los ~108 literales `.dp` del proyecto y es decisión**:
    la mayoría son **tamaños de componente** (el diámetro de una perilla, la altura de una barra de
    EQ), que no son espaciado y no pertenecen a esa escala. Se aplicó donde esta fase tocaba.
  - ✅ **Ícono vectorial propio**, dibujado a mano: una perilla con un arco de **270° empezando en
    135°**, que es **exactamente el recorrido que dibuja `KnobDial`** dentro de la app. Con capa
    monocroma aparte —la de delante no vale, el sistema la reteñiría entera y el cuerpo gris taparía
    el arco—. ⚠️ **Ni un byte de `reference/`**, según la instrucción explícita: los colores no son
    un problema, cualquier asset visual sí.
  - ✅ **El nombre se queda en «KTNA Control»**. Se consideró «Katana Control», que se lee mejor, y
    se descarta: **Katana es marca de Boss** y esta app no es oficial. La abreviatura es
    deliberada.
  - **Ni un test tocado, y aquí era el criterio de la tarea**: `./gradlew :app:testDebugUnitTest`
    **433/433**, el mismo número que dejó la Fase 2; `git diff --stat -- app/src/test` sigue vacío.
    `:app:assembleDebug` y `:app:lintDebug` limpios, **15 warnings con exactamente el mismo
    reparto** (8 UnusedResources, 3 NewerVersionAvailable, 2 AndroidGradlePluginVersion, 1
    RedundantLabel, 1 GradleDependency).
  - **Instalado en el teléfono por adb inalámbrico** (`installDebug`, "Installed on 1 device").

- **2026-09-09 — Fase 4 de la UI: estados de la Biblioteca y consistencia de los tres diálogos
  destructivos.** **Implementado y pendiente de probar** (dado por bueno con los 5 tests JVM
  nuevos, la compilación y el arranque en el teléfono; el aspecto real hay que mirarlo).
  - **La decisión chica se resolvió antes de tocar código** (CLAUDE.md §4.7): guardar en canal y
    enviar al amplificador **son la misma pantalla escrita dos veces** —primer paso que junta
    datos, segundo paso que solo confirma— así que ese segundo paso se extrae a
    `DestructiveConfirmDialog` (`ui/screens/Controls.kt`) y los dos lo llaman. "Guardar"/"Guardar
    como" **no** entra en la extracción: es de un solo paso porque no escribe en hardware sin
    confirmación, y forzarlo a dos pasos habría sido añadirle un paso que hoy no tiene — cambiar
    comportamiento a cambio de una consistencia que no hacía falta.
  - ✅ **Homologación sin forzar la forma**: cuando "Guardar" va a sobrescribir un fichero
    existente, su botón pasa a decir **"Sí, sobrescribir"** —el mismo patrón "Sí, `<verbo>`" que
    ya usaban los otros dos— y su aviso lleva el mismo margen (`Spacing.xs`) y el mismo color
    que ellos. Creando un preset nuevo se queda en "Guardar": no hay nada que pisar.
  - ✅ **`LibraryListState` (`ui/screens/LibraryListState.kt`), Kotlin puro, con tests JVM**:
    `Loading`/`Empty`/`Loaded`, decidido por `libraryListStateOf(loading, entries)`. Corrige un
    fallo real —antes "todavía no leí nada" y "leí y no hay nada" se veían exactamente igual,
    las dos con `entries.isEmpty()`—. `LibraryViewModel` gana `loading: StateFlow<Boolean>`,
    `true` hasta que la primera lectura de disco (`refresh()` en `init`) termina.
  - ⚠️ **`loading` solo gana mientras la lista está vacía**: una recarga en segundo plano (tras
    importar, borrar o guardar) con entradas ya cargadas no hace parpadear la pantalla a
    "Cargando…" — se queda con lo que ya tenía.
  - ✅ **Un `.tsl` que no parsea**: ya estaba resuelto (`LibraryEntry.error`, listado con
    `library_entry_unreadable`, sin descartarse); se confirma con un test que fija que
    `libraryListStateOf` no filtra nada, ni legibles ni ilegibles.
  - ✅ **Bloques `DISPUTED` en el editor, no solo en la revisión previa al envío**:
    `session.source?.unavailable` se pinta con el mismo `UnavailableSection` que ya usaba la
    vista de solo lectura, justo bajo el nombre del preset en `PresetEditorScreen`.
  - **5 tests JVM nuevos (438 en total)**, en `LibraryListStateTest`: cargando+vacío es
    `Loading`, sin cargar+vacío es `Empty`, con entradas es `Loaded` cargando o no, un fichero
    roto sigue en `Loaded` con su motivo, y una lista mixta conserva el orden.
    ✅ **Ningún test existente hizo falta tocarlo** — `PresetSendFlow`, `PresetEditor` y el
    resto de la suite siguen igual; `git diff --stat -- app/src/test` solo tiene ficheros nuevos.
  - `./gradlew :app:testDebugUnitTest` (438/438), `:app:assembleDebug` y `:app:lintDebug`
    (**15 warnings, el mismo baseline**) limpios. Instalado y arrancado en el teléfono sin
    excepciones en `logcat`.

- **2026-09-09 — Fase 5 de la UI, la última: pase de accesibilidad y calidad mínima.**
  **Implementado y pendiente de probar** (dado por bueno con los 13 tests JVM nuevos de
  contraste, la compilación y la instalación en el teléfono; TalkBack y el tamaño de fuente del
  sistema solo se confirman con un dispositivo real).
  - ✅ **`contentDescription`: la auditoría (grep de `Icon(`/`IconButton(`/`Image(` en todo
    `ui/`) confirmó que la app **no tiene ningún ícono** —consecuencia de la Fase 2, que evitó
    `material-icons-core` (§4.2)— así que no había nada que describir. Lo único visual sin texto
    propio, el punto de color de `EffectCard`, se marca explícitamente decorativo con
    `Modifier.clearAndSetSemantics {}`: el nombre del efecto y el color ya están en texto al
    lado. La franja de color no necesitó la misma marca — se pinta con `drawBehind`, nunca tuvo
    nodo de semántica.
  - ✅ **Contraste calculado, no estimado**: `contrastRatio` (`ui/theme/Contrast.kt`), Kotlin
    puro con la fórmula real de WCAG 2, y `Color.hex` como único puente hacia `Color` de
    Compose. Los once pares reales de la paleta cumplen su umbral (texto ≥ `4.5:1`, componente
    no textual ≥ `3:1`); el más ajustado es el rojo de slot con `5.01:1`. **Ningún color de slot
    se movió** — la decisión explícita de frenar y documentar un conflicto no hizo falta
    tomarla, porque no hubo conflicto. Tabla completa con los once pares, en CLAUDE.md §4.9 y en
    el KDoc de cabecera de `Color.kt`.
  - ✅ **Área táctil**: los botones y la barra de navegación ya cumplen 48 dp por defecto de
    Material 3 (comprobado que el proyecto nunca desactiva esa garantía). El único
    `Modifier.clickable` hecho a mano —la fila de un preset en `LibraryEntryCard`— dependía de
    un efecto lateral (el `TextButton` de al lado empujándola a 48 dp); ahora lleva
    `Modifier.heightIn(min = 48.dp)` explícito.
  - ✅ **Escala de fuente**: auditado `Type.kt` y el resto de `ui/` buscando `.dp` donde iría
    `.sp` — no apareció ni un caso; todo lo que define tamaño de texto ya usaba `.sp` desde la
    Fase 3.
  - **13 tests JVM nuevos (451 en total)**, en `ContrastTest`: los dos casos de referencia del
    propio estándar WCAG (blanco/negro = 21:1, un color contra sí mismo = 1:1) y los once pares
    reales de la paleta pineados contra su umbral.
    ✅ **Ningún test existente hizo falta tocarlo.**
  - `./gradlew :app:testDebugUnitTest` (451/451), `:app:assembleDebug` y `:app:lintDebug`
    (**15 warnings, el mismo baseline**) limpios. Instalado y arrancado en el teléfono sin
    excepciones en `logcat`.
  - **Con esto se cierran las cinco fases del plan de UI sin amplificador** (Fase 1 a Fase 5).
    Lo que queda del bloque 6 es exclusivamente lo que necesita hardware — ver "Pendiente por
    probar", puntos 16 a 21.

- **2026-09-09** — **QA en el teléfono con el amplificador: bloques A y B cerrados, C a medias**
  (CLAUDE.md §4.10). Primer reporte de QA sobre hardware real después de cerrar las cinco fases
  de UI. **498 tests JVM, 0 fallos, lint limpio** — 47 nuevos sobre los 451 que había al empezar.
  - **A.1 ✅ La variación funciona en los cinco canales y se puede apagar.** El fallo no era la
    tabla de modelos —`midi.xml:37311-37341` siempre estuvo bien— sino que **la misma pregunta se
    contestaba con tres fuentes distintas**: el switch se pintaba desde el LED `06 5C` (que es de
    solo lectura, así que nunca se encendía y por eso no había camino de vuelta), "¿aplica?"
    miraba el modelo (y se apagaba con cualquier *sneaky amp*, no "en cuatro canales") y la
    escritura miraba la perilla. Ahora hay un solo sitio: `AmpVariation` + `AmpVariationUi` en
    `protocol/`, Kotlin puro. **`AmpVariationTest` cubre los diez casos** (encender y apagar en
    cada canal) más los dos síntomas como regresiones.
  - **A.2 ✅ El diagrama de la cadena: los siete valores crudos, el bloque que sobraba y la
    relectura.** Los valores de `06 20` **no estaban en ninguna fuente que el proyecto usara**
    (`midi.xml` solo da `range 00/06/00/06`, sin nombres); salen de
    `FxFloorboard/floorBoard.cpp:490-599` + `floorBoardDisplay.cpp:158-177`, ahora en
    `ChainPreset`. **Cuadran con Boss Tone Studio bloque a bloque en las siete** una vez retirado
    `FXLOOP` del vocabulario (es un punto de inserción con su propio selector, no un bloque de
    tono — mientras se dibujaba, ninguna de las siete coincidía). `diagramSequence` además
    **corta en `CAB`**: lo que el array pusiera tras el cabinet se dibujaba antes del `SPEAKER`
    fijo, que afirma lo contrario de lo que dice el amplificador. Y el diagrama **vuelve a
    leerse al cambiar de cadena** (`ReloadRequest.ChainChanged`, reusando el coordinador de §4.4).
  - **A.3 ✅ La Biblioteca vuelve a ser alcanzable.** Estaba cableada y era una entrada primaria;
    el fallo era de medición: `LibraryPane` recibía `Modifier.weight(1f)` bajo un bloque en vivo
    sin tope ni scroll, y **un `weight` sin espacio sobrante mide cero**. Al no hacer scroll el
    contenedor de fuera, no había forma de llegar a ella. Ahora el bloque en vivo se pinta dentro
    de la columna de `LibraryPane` por una ranura `header`: un solo scroll, y empuja la lista en
    vez de borrarla.
  - **B.1 ✅ SOLO EQ localizado: `60 00 0F 10`–`0F 19`, diez parámetros, confianza `midi.xml` ×2**
    (tabla de asignación `:3432-3441` **y** bloque `<Structure>` `:49929-50024`, de acuerdo en las
    diez direcciones y el orden). Sin `DISPUTED`: no hay fuente que lo contradiga. De paso
    **desglosa `UserPatch%Patch_Mk2V2`**, los 22 bytes del firmware 2 que estaban anotados sin
    contenido = este SOLO EQ (10) + un SOLO DELAY (12, localizado, no extraído). En
    `SoloEqParams`, con tests. ⚠️ **Deliberadamente sin registrar como control** — ver "Por
    hacer".
  - **C ✅ a medias, con permiso explícito del encargo.** Hecho: **Solo pasa a ser una tarjeta de
    `EffectsScreen`** después de Reverb (opción B, con `AMP_SOLO` movido a
    `AmpDomain.EFFECT_SELECTORS` y test que lo fija); **el selector de modelo pasa a dos páginas
    deslizables** `AMP TYPE` / `SNEAKY AMPS` con `HorizontalPager` (sin dependencia nueva) y la
    clasificación en `AmpModelPage`, Kotlin puro con 10 tests; **la barra de navegación** tiene
    las tres entradas del mismo tamaño y con borde; **los títulos de sección van en mayúscula**.
    ⏸️ Pendiente: el rediseño de sliders verticales con paginado — ver "Por hacer".

- **2026-09-09** — **Sliders verticales con paginado por tarjeta: cerrado el bloque C del QA**
  (CLAUDE.md §4.10, "Sliders verticales… cerrado"). **509 tests JVM, 0 fallos, lint limpio** — 11
  nuevos de `ControlPagingTest` y **ningún test existente hizo falta tocarlo**.
  - ✅ **La pieza que faltaba es `PagedControls`**, y la evaluación previa acertó: `VerticalBarControl`
    y el gesto de `KnobControl` ya existían, faltaba el **contenedor**. La cadena queda
    `ControlPaging` (puro, con tests) → `PagedControls` (el pager) → `VerticalParam` (el parámetro
    como dato) → `PagedVerticalParams` (lo que usan las tarjetas). ⚠️ `VerticalParam` es lo que hizo
    barata la conversión: cada bloque construye una `List` y **no sabe nada de páginas**, así que no
    hubo quince copias del mismo `Row`.
  - ✅ **Convertidas**: las seis tarjetas de efecto (Booster, Mod, FX, Delay, Reverb, Solo) y las
    del panel que eran sliders (Noise Gate, Contour, EQ1 gráfico y paramétrico, los tres Contour de
    slot). El nivel de cada efecto entra en la **misma tira** que sus parámetros internos.
  - ✅ **Paginado y arrastre conviven, y está razonado, no supuesto** (la decisión que pedía el
    encargo): la perilla exige mantener pulsado y el pager arranca con arrastre inmediato —separados
    en el **tiempo**, no solo en el eje—; la barra arrastra en vertical y el pager en horizontal
    —ortogonales—; y además `userScrollEnabled = !adjusting` congela el pager mientras se ajusta,
    que es lo que el eje no resuelve. ⚠️ Para eso **`VerticalBarControl` tuvo que empezar a avisar a
    `KnobInteraction`**: era el único control con arrastre que no participaba.
  - ✅ **Los controles por página se derivan del ancho real** (3 a 6), no son un número fijo: con 4
    fijos, un EQ de 11 bandas se pagina en 3 páginas *incluso en una pantalla donde caben todas*.
  - ✅ **Pase visual** en el mismo pase: `BlockHeader` + `blockCardColors` unifican las tarjetas del
    panel con las de efecto, con el interruptor **en el encabezado** (antes el Noise Gate decía su
    nombre dos veces).
  - ⚠️ **Borrados**: `FractionalLevelControl` y `knobAwareHorizontalScroll`, sin usuarios tras la
    conversión. Un pager no es un scroll.

- **2026-09-10** — **Los seis niveles del panel pasan a la tira vertical, y el control se
  estiliza** (CLAUDE.md §4.10, "El remate: los niveles del panel…"). **509 tests JVM, 0 fallos,
  lint sin novedades** — cero tests nuevos y **ningún test existente hizo falta tocarlo**, que es
  lo esperable de un cambio que no añade lógica: la matemática del paginado ya estaba cubierta por
  `ControlPagingTest` y esto solo la usa desde un sitio más.
  - ⚠️ **El bloque C había dejado la pantalla de amplificador hablando dos idiomas.** Convirtió
    las once tarjetas de efecto y las del panel sin perilla física, pero
    Gain/Volume/Bass/Middle/Treble/Presence seguían siendo un `LevelRow` horizontal cada uno,
    apilados — el **mismo tipo de parámetro** (nivel continuo 0..100) con dos formas distintas en
    la misma pantalla, y en el editor offline igual. Ahora son un `PagedVerticalParams` más,
    construido con `ampLevelParams` (`AmpScreen.kt`): seis niveles a cuatro columnas dan dos
    páginas en un móvil normal y una sola en horizontal, porque el número sale del ancho.
  - ⚠️ **`LevelRow` se borra**: era su único llamador. `LevelControl` (el slider horizontal) se
    queda, con dos usuarios que **no son controles editables de panel** — el detalle de solo
    lectura de un preset (`LibraryPane`) y la tarjeta de diagnóstico.
  - ✅ **Pase visual sobre `VerticalBarControl`**, que nació como "interfaz mínima y sin pulir"
    para probar el EQ y hoy es **el control continuo de toda la app**: pista con extremos
    redondeados y filo propio, relleno en **degradado** (ámbar claro arriba → acento abajo, los
    dos extremos del tema), **tapa de fader** que marca el valor exacto, y el número en una
    **pastilla que se tiñe con el acento mientras dura el arrastre** — la única confirmación
    visual de que el gesto se cogió y no se lo llevó el pager.
  - ⚠️ **El alto mínimo del relleno (3 dp) no es cosmético**: sin él, un valor en 0 se pinta como
    una barra vacía **idéntica a la de un control sin leer** (`value == null`), y son dos estados
    muy distintos. Por lo mismo el relleno se calcula en dp a mano en vez de con
    `fillMaxHeight(ratio)`, que no admite suelo.

- **2026-09-10** — **El orden y el tamaño de la tira del panel, tras probarla en el teléfono**
  (CLAUDE.md §4.10, "El orden de las dos páginas…"). **514 tests JVM, 0 fallos, lint sin
  novedades** — 5 nuevos, ninguno existente tocado.
  - ⚠️ **El orden heredado partía la ecualización entre dos páginas.** `AmpDomain.LEVELS` es una
    derivada sobre `LevelId.entries`, así que su orden es el del **mapa de direcciones**
    (`06 51`..`06 56`) y de tres en tres daba `Gain · Volume · Bass` + `Middle · Treble · Presence`.
    Ahora hay un orden de pintado propio, `AmpDomain.PANEL_LEVEL_ORDER`:
    **`BASS · MIDDLE · TREBLE`** y **`GAIN · VOLUME · PRESENCE`**, dos tríadas que se ajustan cada
    una junta.
  - ⚠️ **Es una lista a mano donde antes había una derivada, así que lleva red**: un `init` con
    `require` de permutación exacta de `LEVELS` + dos tests. Reordenar dejándose un nivel fuera no
    daría error por ningún lado — la pantalla pintaría cinco.
  - ⚠️ **Esta tira fija las columnas en 3**, excepción explícita a la decisión 2 del bloque C
    (`ControlPaging.columnsFor(fixedColumns = …)`). El criterio: se permite **solo cuando el
    reparto en páginas significa algo**; en una pantalla ancha, derivar fundiría las dos tríadas en
    una fila. Un test fija que `null` sigue siendo idéntico al comportamiento de antes, que es lo
    que usan las once tarjetas de efecto.
  - ✅ **Barra del panel a 176×34 dp** (contra 108×24 del resto): `VerticalBarControl` gana
    `barWidth` junto a `barHeight`, **con los valores de siempre por defecto**, así que los efectos
    no se mueven. Mismo estilo, solo más grande.

- **2026-09-10** — **Un solo GET por pantalla, en vez de uno por parámetro** (CLAUDE.md §4.10,
  "Un solo GET por pantalla…"). **514 tests JVM, 0 fallos, lint sin novedades** — ningún test tocado
  (lo que se prueba en JVM es la lógica, y esto es cableado de UI).
  - ✅ **Se van los catorce botones de "leer" por parámetro** y queda el global que ya existía,
    ahora **arriba del todo** de las dos pantallas en vivo y a ancho completo, con la etiqueta
    «Releer del amplificador».
  - ⚠️ **No se pierde ninguna capacidad, y ese es el argumento**: el botón dispara
    `loadFromDump()`, que relee el bloque entero y **además** hace el GET individual de respaldo
    para lo que el dump no cubre (los Contour por slot). Un dump son ~275 ms; cincuenta GET en
    serie, hasta 800 ms cada uno.
  - ⚠️ **`LocalReadButtonsVisible` se borra: nunca tuvo `Provider`.** Se había escrito para
    esconder esos botones en el editor offline y se quedó siempre en `true`, así que la Biblioteca
    enseñaba botones de "leer del amplificador" cableados a `{}` — un gesto aceptado que no hacía
    nada. Eso queda arreglado de paso.
  - ⚠️ **También se retira el "leer" de la tarjeta de diagnóstico del Solo**, y no por descuido:
    sus `ProbeRow` ya leen de vuelta por su cuenta (`WriteProbe.verdict`) y el nivel candidato está
    dentro del dump. Los dos botones de prueba —lo que de verdad desempata las dos direcciones
    candidatas (§5)— **siguen intactos**.
  - **Borrado**: ~190 líneas de firmas, 13 funciones del `ViewModel`, el `CompositionLocal` y la
    cadena `R.string.debug_connection_read_level`.
  - ✅ **El botón solo existe donde hay amplificador**, sin condición nueva: vive en `AmpScreen` y
    `EffectsScreen`, y el editor offline no las usa (usa las tres secciones sueltas, §4.2).
- **2026-09-10** — CLAUDE.md adelgazado de 3943 a 599 líneas (260 KB → 44 KB): el detalle temático
  se movió tal cual a `docs/referencia/` (8 archivos) y la narrativa a `docs/historial/` (3), con
  verificación de pérdida cero; CLAUDE.md queda con reglas vigentes, "Lecciones del protocolo",
  "Incógnitas abiertas", el mapa de `docs/` y un tope de 600 líneas en §8.

## En progreso


## Pendiente por probar

Lo que está **implementado pero todavía no confirmado contra el amplificador real**. Cada
entrada dice en qué consiste la prueba y qué resultado cuenta como éxito, siguiendo la
disciplina de siempre (CLAUDE.md §5): nada se da por bueno hasta oírlo o verlo en el amp.

1. ⚠️ **Recarga completa del estado al cambiar de canal — arreglado y probado en JVM
   (2026-09-04), pendiente de confirmar con el amplificador real.** El bug de los cuatro
   fallos encadenados —filtro por dirección+tamaño en el dump, `byteWidth` en
   `applyDumpValue`, único punto de disparo con mutex, guard reevaluado tras el margen— está
   arreglado; ver "Hecho". Los tres criterios de éxito de abajo se comprueban con un
   reproductor JVM, no todavía con el amplificador delante.
   - **Prueba**: con presets distintos guardados en al menos dos canales (por ejemplo 1A y
     2A con modelos de amplificador o efectos distintos), cambiar de canal desde el selector
     de la app y observar el log y los controles. Repetir cambiando de canal con el
     footswitch físico del amplificador, sin tocar la app. Repetir una tercera vez pasando
     rápido por varios canales seguidos (p. ej. 1A→2A→3A sin pausas).
   - **Resultado esperado si funciona**:
     - Tras cada cambio aparece en el log `↻ Canal X: releyendo todo el estado...` seguido
       del resumen del dump (mensajes, bytes, controles poblados).
     - Todos los sliders y selectores —modelo de amplificador, colores, on/off, tipos de
       efecto— muestran los valores reales del canal nuevo, no los del anterior.
     - El cambio por footswitch físico dispara la misma recarga que el cambio desde la app.
     - Pasar rápido por varios canales produce **una sola** recarga, la del canal donde se
       queda al final — no una por cada canal intermedio.
   - **Si falla**: revisar en el log si `repository.channel.state` sí cambia (el chip de
     canal de la app se mueve) pero no aparece la línea de recarga — apuntaría a un problema
     en `startReloadCoordinator`, no en la lectura del canal en sí.

2. ⚠️ **El contrato de Edit Mode: apagado deja cambiar de canal, encendido deja todo.**
   Implementado el 2026-09-04 en la UI (`SlidersPane`); falta la parte que solo el
   amplificador puede responder — si acepta el SET de canal con edit mode apagado. Ver
   "Hallazgos de diagnóstico" para por qué esto **no** es una regresión del fix de recarga.
   - **Prueba A, con Edit Mode APAGADO**: cambiar de canal desde el selector de la app.
     - **Éxito**: el amplificador cambia de canal de verdad (se oye, y el panel lo muestra),
       y el selector de la app se queda en el canal nuevo sin volver atrás.
     - **Si el amplificador NO cambia**: el selector volverá solo al canal real —eso es la
       app funcionando bien, no un bug— y en el log debe aparecer
       `! Se pidió el canal X pero el amplificador sigue en Y. Edit Mode está apagado.`
       Ese mensaje es la confirmación de que **el amplificador exige edit mode para aceptar
       la escritura del canal**, que es la hipótesis que queda por decidir. Si sale, el
       contrato "cambiar de canal siempre" no se puede cumplir solo con SysEx a
       `00 01 00 00`: habría que replantearlo con Program Change (§5.1) o encendiendo edit
       mode para la escritura, y las dos cosas tienen coste propio.
   - **Prueba B, con Edit Mode APAGADO**: mirar el resto de la pantalla de Sliders.
     - **Éxito**: sliders, selectores de amplificador y las cinco tarjetas de efecto se ven
       **grises y no responden**, con el aviso "Edit Mode apagado: solo se puede cambiar de
       canal" justo debajo del interruptor. El selector de canal es el único que sigue vivo.
   - **Prueba C, con Edit Mode ENCENDIDO**: todo vuelve a estar habilitado y sigue
     funcionando como hasta ahora — el aviso desaparece.

3. ⚠️ **Los cuatro controles restantes del bloque PREAMP** (`60 00 00 29`–`2C`), implementados
   el 2026-09-04, pendientes de confirmar con audio. Con Edit Mode encendido, en la tarjeta de
   Amplificador, justo debajo del switch de Variación:
   - **Bright** (`60 00 00 29`, switch on/off): activarlo y desactivarlo mientras suena una
     nota sostenida. **Éxito esperado**: cambia el brillo/color tonal del sonido de forma
     audible — más agudos presentes con Bright activado.
   - **Gain SW** (`60 00 00 2A`, tres posiciones Low/Middle/High): mover el selector con la
     misma nota sostenida. **Éxito esperado**: cambia el rango o la sensibilidad de la
     perilla GAIN — probablemente el techo de ganancia disponible, a la manera de un control
     de "gama" del preamp, pero **verificar el efecto exacto contra `katana_sysex.txt` o
     `Adresses.txt`** antes de dar por buena la interpretación, porque ninguna fuente de Mk2
     lo explica más allá del nombre literal.
   - **Solo Sw / Solo Level** (`60 00 00 2B`/`2C`): mismo comportamiento ya confirmado en
     Booster (2026-09-04) — activar Solo Sw debe aislar la señal para ajuste de nivel, y
     mover Solo Level debe cambiar audiblemente ese nivel mientras Solo Sw está activo.
   - **Si alguno falla en silencio** (el slider se mueve pero no pasa nada, sin que el valor
     rebote solo): revisar primero si acepta el SET con un GET inmediato después — el
     precedente es `60 00 06 5C` (variación), que aceptaba el SET pero el amplificador lo
     ignoraba y seguía reportando su valor real; aquí en cambio el síntoma de "no pasa nada"
     sin rebote apuntaría a que la dirección sí escribe pero no está conectada a nada audible,
     que sería un caso nuevo no visto todavía en este proyecto.

4. ⚠️ **Parámetros internos fijos de Delay 1 y Reverb** (CLAUDE.md §5.2), implementados el
   2026-09-05, pendientes de confirmar con audio. Con Edit Mode encendido, en las tarjetas de
   Delay y Reverb:
   - **Delay Time** (`60 00 05 02`, `1..2000` ms): mover el slider por todo el rango con una
     nota repetida (o un loop) sonando. **Éxito esperado**: el espaciado entre repeticiones
     del eco cambia de forma audible y proporcional al valor. **Presta atención especial al
     tramo `1792`-`1999` ms** (cerca del máximo): es donde `midi.xml` tiene la irregularidad
     documentada (CLAUDE.md §5.2) — si en ese tramo el espaciado deja de ser proporcional al
     valor mostrado (p. ej. si moverse de 1800 a 1900 ms no cambia nada, o cambia mucho más de
     lo esperado), confirmaría que el amplificador de verdad usa ahí una resolución más
     gruesa, y habría que revisar la escala solo para ese tramo.
   - **Delay Feedback** (`05 04`, `0..100`): con el delay activo, subir y bajar. **Éxito**:
     cambia el número de repeticiones audibles antes de apagarse — más feedback, más
     repeticiones.
   - **Delay High Cut** (`05 05`, 15 frecuencias): mover el selector de `630Hz` a `FLAT` con
     el delay sonando. **Éxito**: las repeticiones se oyen más oscuras/apagadas en el extremo
     bajo (630Hz) y más brillantes/sin filtrar en FLAT.
   - **Delay Effect Level / Direct Mix** (`05 06`/`05 07`, `0..120`/`0..100`): mismo patrón
     que Booster — Effect Level cambia el volumen del delay procesado, Direct Mix cambia el
     balance con la señal seca.
   - **Reverb Pre Delay** (`60 00 05 43`, `0..500` ms): con la reverb sonando, subir el valor.
     **Éxito**: se nota un hueco creciente entre el ataque de la nota y el inicio de la cola
     de reverb.
   - **Reverb Low Cut / High Cut** (`05 45`/`05 46`, 18/15 frecuencias): mover cada selector
     por su rango. **Éxito**: Low Cut quita graves de la cola de reverb al subir desde FLAT;
     High Cut quita agudos al bajar desde FLAT — direcciones de filtro opuestas, como está
     documentado (CLAUDE.md §5.2, "FLAT es el primero" vs "FLAT es el último").
   - **Reverb Density** (`05 47`, `0..10`): subir y bajar. **Éxito**: la cola de reverb se
     nota más densa/continua en vez de discreta o "granulada" al subir.
   - **Reverb Direct Mix** (`05 49`, `0..100`): mismo patrón que Effect Level de Booster —
     cambia el balance entre señal seca y reverb.
   - **Si algún selector de frecuencia no cambia nada audible pero tampoco rebota**: revisar
     con un GET si el valor se guarda de verdad — el precedente de "acepta el SET pero no
     hace nada audible" en este bloque es `60 00 05 48` (Reverb Effect Level / la vieja
     `REVERB_LEVEL_DERIVED`), que por eso se dejó fuera del cableado en vez de arriesgarse a
     repetir el mismo resultado sin necesidad de probarlo de nuevo.

5. ⚠️ **Los dos parámetros con paso fraccionario** (`FractionalLevelScale`, CLAUDE.md §5.2),
   implementados el 2026-09-05, pendientes de confirmar con audio. Con Edit Mode encendido:
   - **Reverb Time** (`60 00 05 42`, `0.1..10.0` s en pasos de 0.1): en la tarjeta de Reverb,
     mover el slider por todo el rango con la reverb sonando. **Éxito esperado**: la duración
     de la cola de reverb cambia en incrementos perceptibles y finos — de 0.1 s en 0.1 s, no a
     saltos bruscos de 1 s como daría una escala entera sin el paso fraccionario.
   - **Pre Delay de 2x2 Chorus, bandas Low y High** (`60 00 02 3A`/`3E`, `0.0..40.0` ms en
     pasos de 0.5): primero, **poner el tipo activo de Mod en 2x2 Chorus** — los sliders solo
     aparecen en la tarjeta de Mod con ese tipo seleccionado, y con cualquier otro tipo debe
     verse el aviso "Solo disponible con el tipo 2x2 Chorus" en su lugar. Con 2x2 Chorus
     activo y el efecto sonando, mover cada banda por separado. **Éxito esperado**: el eco de
     la modulación (el chorus) se adelanta o atrasa con precisión fina, de forma audible e
     independiente entre banda Low y banda High.
   - **Si el paso se siente "a saltos" en vez de fino**: comprobar con un GET inmediato tras
     el SET que el crudo que vuelve es el esperado (`scale.toRaw` del valor mostrado) — si
     coincide pero el oído no distingue los pasos finos, puede ser una limitación perceptiva
     normal a esa escala de tiempo, no un fallo de la implementación.
   - **Si Reverb Time no cambia nada audible pero tampoco rebota**: mismo patrón de
     diagnóstico que el resto del bloque de Reverb — comprobar con GET si el valor se guarda.

 6. ⚠️ **Los 31 tipos de Mod/FX, implementados el 2026-09-06 (CLAUDE.md §5.2,
    `ModFxInternalParams`), pendientes de confirmar con audio uno por uno.** Con Edit Mode
    encendido, poner el tipo activo de Mod (o FX) en el que toque probar y mover sus
    parámetros con el efecto sonando. Las tres afirmaciones que esta lista reemplaza —Repeat
    Rate de DC30 desde `0x28`, Pre Delay de PS/Harmonist en ms enteros no fraccionarios, y
    `FX = Mod + 256`— ya están verificadas por test JVM (ver "Hecho", 2026-09-06) y no hace
    falta repetirlas aquí; lo que sigue es **una prueba mínima por tipo**, agrupada por
    familia para no repetir el mismo párrafo 31 veces. El criterio de éxito general es el de
    siempre (CLAUDE.md §5): un cambio audible y proporcional al mover el control, sin que la
    UI rebote sola (la firma de una dirección de solo lectura, `60 00 06 5C`).

    **Wah y envolventes** — Touch Wah: mover *Freq* con la nota sonando, se oye el filtro
    barrer. Auto Wah: subir *Rate*, el barrido automático se acelera. Pedal Wah: mover *Pedal
    Pos* de *Pedal Min* a *Pedal Max*, se oye como un wah de pedal real. Slow Gear: tocar una
    nota con *Sens* alto y bajo, cambia cuánto se retrasa la entrada del volumen.

    **Dinámica y EQ** — Compressor: subir *Sustain* con una nota que decae, se sostiene más.
    Limiter: bajar *Thresh*, la señal se comprime más agresivamente en los picos. Graphic EQ:
    subir una banda central (p. ej. *1KHz*) al máximo, se oye ese rango realzado. Parametric
    EQ: mover *Lo Mid Freq* con *Lo Mid Gain* alto, el realce se desplaza en frecuencia.

    **Modulación clásica** — Phaser: subir *Rate*, el barrido se acelera; con *Step Rate*
    distinto de Off, el barrido salta en escalones en vez de deslizarse. Flanger: subir
    *Manual*, cambia el punto central del barrido. Tremolo: subir *Depth*, el efecto de
    trémolo se hace más marcado. Rotary: cambiar *Speed* de Slow a Fast, el Doppler del
    altavoz rotatorio se acelera de forma audible. Uni-V: subir *Rate*, la modulación de
    vibrato/chorus se acelera. Vibrato: activar su *Off/On* interno (`02 28`, no el del slot)
    y subir *Depth* — confirma que las dos direcciones son controles distintos. 2x2 Chorus:
    mover *Rate (banda baja)* y *Rate (banda alta)* por separado, deben sonar como dos LFOs
    independientes.

    **Efectos de voz y pitch** — Octave: subir *Octave*, se oye la voz añadida una octava
    abajo/arriba según *Range*. Pitch Shifter: mover *Pitch (voz 1)* en semitonos, el cambio
    de afinación es audible y en pasos de un semitono. Harmonist: cambiar *Harmony (voz 1)* de
    Unison a +5th, aparece un intervalo armónico reconocible. Wave Synth: subir *Cutoff Freq*
    con *Reson.* alto, se oye un filtro tipo sintetizador que abre. Heavy Octave: subir
    *Octave -1*, aparece una octava grave gruesa característica del pedal.

    **Espaciales y de textura** — Slicer: cambiar *Pattern*, el patrón de rítmico de corte
    cambia de forma reconocible. Ring Modulate: subir *Freq* en modo Normal, aparecen
    armónicos metálicos/inarmónicos. Humanizer: subir *Sens* en modo Picking, la vocal
    formante ( *Vowel 1* ) se abre con la intensidad del ataque. AC Guitar Sim / Guitar Sim:
    cambiar *Type*, el timbre pasa de eléctrico a acústico simulado de forma clara. Acu
    Processor: subir *Presence*, se oye más brillo tipo simulador acústico.

    **Emulaciones de pedal concretas ("sneaky", pocos parámetros)** — Phaser 90E: activar
    *Script* y subir *Speed*. Flanger 117E: subir *Speed* con *Width* alto. Wah 95E: mover
    *Pedal Pos* de *Pedal Min* a *Pedal Max*. DC30: cambiar *Selector* de Chorus a Echo —
    deben sonar como dos efectos distintos, no una variación del mismo — y subir *Repeat Rate*
    en modo Echo (ver la nota de los 40 rpm mínimos arriba). Pedal Bend: subir *Pitch* con
    *Pedal Posn* alto, se oye un bend de afinación tipo pedal steel.

    **Si algún tipo no suena pero tampoco rebota**: mismo diagnóstico que el resto del
    proyecto — GET inmediato tras el SET (`KatanaControl.probeWrite`, ya usado con Solo/
    Bright/Gain SW) para separar "la dirección es correcta y el parámetro no hace nada" de
    "la dirección no acepta la escritura". Con 31 tipos compartiendo el mismo espacio de
    direcciones, comprobar primero que el tipo activo real (reportado por el amp) coincide con
    el que se cree estar probando — un desfase ahí haría parecer "roto" un parámetro que en
    realidad pertenece a otro tipo.

 7. ⚠️ **Controles sin perilla física, cableados el 2026-09-06 (CLAUDE.md §5): qué probar, por
    grupo.** Con Edit Mode encendido, en la sección **"Sin perilla física"** al final de
    Sliders. El **Solo no está aquí**: sigue en el punto 10, sin desempatar entre sus dos
    candidatas.

    **Orden recomendado, y no es arbitrario**: primero Contour por slot (es lo único que puede
    hacer *lento* todo lo demás), luego Noise Gate (es lo que valida la regla de extracción de
    la que salen todas las direcciones de esta tanda), y el resto después.

    - 🔴 **Contour por slot (`60 00 0F 30`/`38`/`40`) — lo primero, y no por su sonido.** Pulsar
      el GET de cada uno de los tres slots.
      **Éxito**: devuelven valor y los controles se pueblan.
      ⚠️ **Lo que de verdad se está midiendo es el tiempo**: estos 6 controles caen fuera del
      dump y `loadFromDump` los pide **en serie**, hasta 800 ms cada uno. Si **no** contestan,
      son ~4,8 s añadidos a cada conexión **y a cada cambio de canal**. Así que mirar también
      **cuánto tarda un cambio de canal** antes y después de esta tanda: si se nota más lento,
      la causa es esta y hay que ampliar el rango del dump o paralelizar los GET de respaldo
      (ninguna de las dos está hecha, a propósito).
      **Si el GET no responde**: la región `60 00 0F xx` no es legible, sería la primera vez en
      el proyecto, y habría que replantear cómo leer los Contour por slot.
    - 🔴 **Noise Gate (`60 00 05 66`–`68`) — valida la regla de extracción de toda la sección.**
      Con el ampli a ganancia alta y sin tocar la guitarra, activar el switch y subir
      *Threshold*. **Éxito**: el zumbido de fondo se corta al superar el umbral, y *Release*
      cambia cuánto tarda en cerrarse.
      ⚠️ Importa más de lo que parece: la regla `desc`/`customdesc` de la tabla de asignación,
      de la que sale **toda** esta sección, solo está verificada contra direcciones del bloque
      `06 5x`. Ésta es del bloque `05`. **Si el Noise Gate no responde, toda la sección pasa a
      ser sospechosa** y hay que volver a los `<DATA>` de `<Structure>`.
    - **Contour general (`06 16`/`17`/`1A`)**: activar el switch, cambiar el slot activo entre
      1/2/3 y mover *Freq Shift*. **Éxito**: el switch cambia audiblemente el carácter de medios
      (es un realce/hundido de medios preajustado), cambiar de slot suena distinto entre los
      tres, y Freq Shift desplaza en frecuencia lo que el Contour hace.
      **Comprobación cruzada gratis**: al cambiar el slot activo, el *Freq Shift (activo)*
      (`06 1A`) debería pasar a mostrar el valor del slot recién elegido — es el mismo parámetro
      visto desde el slot activo, así que si no coinciden, uno de los dos está mal identificado.
    - **Posición de EQ1 (`06 22`) y EQ2 (`06 19`)**: con el EQ correspondiente encendido y una
      curva **extrema** puesta (p. ej. Low Gain a +20 y Hi Gain a −20, que es lo que hace el
      cambio audible), alternar Amp In / Amp Out. **Éxito**: el timbre cambia — ecualizar antes
      o después del previo no suena igual, y con una curva plana no se notaría nada aunque
      funcionase, que es la trampa de esta prueba.
    - **Bloques internos de EQ1 y EQ2, mitad paramétrica**: con *Selection* en `Parametric`,
      subir *Low Gain* al máximo y bajar *Hi Gain* al mínimo. **Éxito**: cambio de timbre obvio.
      Luego mover *Lo Mid Freq* con *Lo Mid Gain* alto: el realce debe desplazarse en
      frecuencia. **Y comprobar que EQ1 y EQ2 son independientes**: mover EQ1 no debe alterar
      EQ2 — es lo que confirma de verdad el `+ 0x20`.
    - ⚠️ **Bloques internos de EQ, mitad gráfica — el paso de 0,5 dB.** Con *Selection* en
      `Graphic`, recorrer una banda (p. ej. *1KHz*) de un extremo al otro.
      **Éxito**: cubre −12,0..+12,0 dB en 49 pasos, sin saltarse la mitad del recorrido ni
      doblar el rango.
      **Si el recorrido llega solo a la mitad o se pasa al doble**, la lectura del rango estaba
      mal y hay que revisarla — es exactamente el fallo que ya tuvo el tramo `1792`-`1999` ms de
      Delay Time, y por eso esta prueba es específica y no "mover el slider a ver".
    - ⚠️ **Cadena de efectos, array de 20 (`06 00`–`06 13`) — el de más riesgo del lote.**
      **Hacerlo en PANEL y con los presets exportados o anotados**: es el control con más
      potencial de dejar un preset raro. Mover `OD` (Booster) a una posición detrás de `RV`
      (reverb). **Éxito**: el carácter de la distorsión cambia de forma clara — distorsionar
      antes o después de la reverb no suena igual.
      ⚠️ La app **no impone** que la cadena siga siendo una permutación (poner un bloque en dos
      sitios lo deja duplicado): eso sería decidir por el amplificador antes de saber qué hace
      con un duplicado. **Parte de la prueba es averiguar precisamente eso**: poner el mismo
      bloque en dos posiciones y ver si el amp lo corrige solo, lo acepta, o hace algo raro.
    - **Cadena predefinida (`06 20`), Loop (`06 21`) y Pedal/FX (`06 23`)**: recorrer las 7
      cadenas y alternar las dos posiciones de cada selector. **Éxito**: cambiar de cadena
      predefinida reordena de verdad —y debería **reflejarse en las 20 posiciones del array**,
      que es la comprobación cruzada que dice si los dos controles hablan de lo mismo—; el Loop
      solo se nota con algo conectado al send/return.
    - **Si algo no suena pero tampoco rebota**: el diagnóstico de siempre, GET inmediato tras el
      SET (`KatanaControl.probeWrite`), para separar "la dirección es correcta y el parámetro no
      hace nada" de "la dirección no acepta la escritura".

 8. 🔴 **Guardado de presets, cableado el 2026-09-06 (CLAUDE.md §5). LA PRUEBA MÁS DELICADA DEL
    PROYECTO: es destructiva e irreversible sobre el amplificador.**

    **Antes de tocar nada:**
    - **Exportar los 8 presets a `.tsl` desde Boss Tone Studio, o anotarlos.** No hay deshacer, y
      la app no puede recuperar lo que sobrescriba.
    - **Elegir un canal de usar y tirar** (p. ej. B4 si no se usa) y hacer **todas** las primeras
      pruebas ahí. No probar sobre un canal con algo que importe hasta que la secuencia esté
      confirmada.
    - Edit Mode encendido: la UI no deja pulsar Guardar sin él.

    - **La prueba básica.** Editar algo bien audible en PANEL o en el canal actual (p. ej. Gain
      al máximo y un color de efecto distinto), pulsar "Guardar preset…", poner un nombre
      reconocible ("PRUEBA 1"), elegir el canal de usar y tirar, y confirmar los dos pasos.
      **Éxito**: el log dice `el nombre entró, así que el commit llegó`; al cambiar a ese canal
      y volver, el sonido editado sigue ahí — es decir, **sobrevive a la recarga por dump**
      (§4.4), que es lo que distingue "guardado" de "sigue en el búfer".
      **Si el log dice que el amp no contestó al GET del nombre**: el guardado puede haber
      funcionado igual; comprobarlo en el propio amplificador antes de concluir nada.
    - **¿Hace falta el nombre, o basta el commit?** El MK1 pone el nombre como paso previo
      obligatorio; el Mk2 podría no necesitarlo. Guardar una vez con un nombre nuevo y otra
      dejando el mismo. **Éxito**: las dos guardan el sonido. **Si solo funciona la primera**,
      el nombre es de verdad un requisito y no un extra, y conviene anotarlo.
    - **¿Responde algo?** Mirar el log durante el guardado. **Éxito esperado (hipótesis)**:
      **nada** — fire-and-forget. **Si llegara una respuesta correlada**, es un dato que ninguna
      fuente tiene y hay que documentarlo.
    - **¿Qué reporta el "Saving in progress..." del panel?** Con Edit Mode encendido, mirar si
      llega algo por el endpoint de entrada mientras el panel lo muestra. **Éxito esperado**:
      nada, es solo visual. **Si llega algo, anotar la dirección** — sería un hallazgo nuevo.
    - **El intervalo mínimo entre dos guardados.** La app impide solaparlos, pero no impone una
      espera. Guardar en dos canales distintos con margen decreciente (1 s, 500 ms, y lo más
      rápido que deje la UI). **Éxito**: los dos canales quedan con lo suyo. **Si el segundo se
      pierde o corrompe el primero**, ahí está el intervalo mínimo que ninguna fuente documenta,
      y habría que meterlo en `SAVE_SETTLE_MS` o bloquear el botón un rato.
    - **Guardar en el canal que se está editando.** Es el caso por defecto del diálogo. **Éxito**:
      igual que guardar en otro, sin nada raro — pero conviene probarlo aparte porque el
      amplificador podría tratar "guardar donde ya estoy" de otra manera.
    - ⚠️ **PANEL (`00`) sigue sin probarse y la app no lo ofrece.** Si algún día se quiere
      resolver el TBD, hay que mandar el commit con dato `00 00` a mano (no desde la UI) y **con
      los presets ya exportados**: lo que importa es descartar que haga algo destructivo.
    - **Qué NO prueba nada de esto**: que el nombre coincida confirma que el commit llegó, no
      que el **sonido** se haya guardado bien. Lo único que confirma eso es cambiar de canal,
      volver, y escuchar.

 9. 📁 **Formato `.tsl`: la importación ya está cableada (2026-09-06) y esto es lo que hay
    que comprobar** (CLAUDE.md §5 "Formato `.tsl`"). ✅ **Casi todo se puede probar SIN
    amplificador**, porque es lectura de fichero — así que esto no espera a la próxima sesión con
    guitarra. La prueba de la importación, primero, y en este orden:
    - **Importar un `.tsl` de verdad exportado desde Boss Tone Studio**, desde Descargas o de
      donde esté. **Éxito esperado**: aparece en la lista de Biblioteca con el nombre del preset
      (no el del fichero), y al abrirlo los sliders enseñan los valores del fichero, todos
      deshabilitados.
    - **Borrar el fichero original** desde el explorador del celular y volver a abrir la app.
      **Éxito esperado**: el preset importado sigue ahí y se sigue abriendo — es lo que compra
      copiar el fichero en vez de guardar su `Uri`.
    - **Mirar la sección "No disponible en este fichero"** del detalle. **Éxito esperado**: lista
      los tres `Contour` y `GafcExp1AsgnMinMax` (las cuatro claves en disputa, que a propósito no
      se cargan) y menciona el hueco de Pedal Bend de Mod/FX, que el formato no guarda. **Lo que
      NO debe pasar** es que aparezca un valor cualquiera en su lugar.
    - **Importar algo que no sea un `.tsl` de Katana** —un `.tsl` de la serie GT, o directamente
      un fichero de texto—. **Éxito esperado**: se importa igual pero se lista con el motivo por
      el que no se pudo leer; la app no se cae ni se queda en blanco.
    - **Comparar contra Boss Tone Studio** un par de valores concretos del mismo preset (Gain, el
      modelo de amplificador, el color de un efecto). **Éxito esperado**: coinciden. Si alguno no
      coincide, el sitio por donde empezar a mirar es el mapa de 22 claves, no el parser.

    Lo que sigue **abierto**, y en su mayor parte ya es trabajo de la segunda mitad (edición y
    exportación):
    - ⚠️ **La dirección de los tres `Contour`.** `midi.xml` dice `60 00 0F 30`/`38`/`40`;
      FxFloorboard da `0F 2E`/`36`/`3E`. Hoy están marcados `DISPUTED` y **no se cargan**.
      **Éxito esperado**: exportar un `.tsl` desde Boss Tone Studio con los tres Contour puestos
      a valores distintos y reconocibles, mirar el bloque con `TslBlockMap` en la mano y ver cuál
      de las dos lecturas produce los valores correctos. Es comprobable **sin amplificador**,
      solo con un fichero. Cuando se resuelva, pasan a `CONFIRMED` y se cargan solas.
    - ⚠️ **`GafcExp1AsgnMinMax`: `09 30` o `09 34`.** Mismo método, mismo fichero.
    - ⚠️ **Si el amplificador acepta un SET de 221 bytes de una vez** (`Fx(1)`/`Fx(2)`), o si
      hay que partirlo. No cabe en un paquete USB de 512 B una vez empaquetado en tramas de 4
      bytes. **Éxito esperado**: o lo acepta troceado en el transporte de forma transparente, o
      hay que partirlo en SET de 128 bytes alineados a página. **Si el amp ignora el mensaje
      largo en silencio**, es el mismo síntoma que una dirección muerta y hay que descartar esa
      causa antes de buscar otra.
    - ⚠️ **Qué hay en `Patch_Mk2V2`** (22 bytes, `60 00 0F 10`–`0F 25`, sin desglosar en ninguna
      fuente). La conjetura es que ahí viven los Pedal Bend de Mod y FX que el `.tsl` no guarda
      en `Fx(1)`/`Fx(2)`. **Cómo comprobarlo sin amplificador**: exportar dos `.tsl` desde BTS
      que solo se diferencien en el Pitch del Pedal Bend de Mod y comparar ese bloque byte a
      byte.
    - **Ida y vuelta completa**, cuando haya código: exportar el estado actual a `.tsl`,
      cambiarlo todo en el amplificador, reimportar. **Éxito esperado**: el sonido vuelve a ser
      el de antes. **Lo que NO debe volver** son los Pedal Bend de Mod/FX, que el formato no
      guarda — si vuelven, la conjetura de `Patch_Mk2V2` era correcta.

11. 📂 **Edición offline y exportación a `.tsl`** (2026-09-06, CLAUDE.md §4.5). La
    edición **no necesita amplificador**; la exportación sí. En este orden:
    - **Editar un preset importado.** Abrir uno de la Biblioteca con "Editar", mover el Gain y
      cambiar el modelo de amplificador, y darle a "Guardar". **Éxito esperado**: al reabrirlo
      salen los valores nuevos, y el aviso dice cuántos bloques se copiaron sin interpretar.
    - **Que editar no pierda nada** — es la propiedad que justifica toda la arquitectura.
      Exportar el mismo preset desde Boss Tone Studio antes y después de editarlo con la app, y
      comparar los bloques `Fx(1)`/`Fx(2)`: **deben ser idénticos** si solo se tocó el Gain.
      Está fijado por un test, pero el test compara contra el propio escritor de la app; esta
      prueba lo compara contra BTS.
    - **Crear uno desde cero** con "Nuevo preset", ponerle valores y guardarlo. **Éxito
      esperado**: se abre como cualquier otro. ⚠️ **Y lo interesante es cargarlo en BTS**: si
      BTS lo rechaza, lo más probable es que sea por las claves omitidas (los bloques en
      disputa), y entonces habría que decidir entre escribir ceros o resolver primero la
      dirección de los Contour.
    - **"Guardar como"** sobre un preset abierto. **Éxito esperado**: aparece una entrada nueva
      y la original **no** cambia.
    - ⚠️ **Exportar desde el amplificador** (botón "Exportar a .tsl…" en Sliders). **Requiere
      hardware.** **Éxito esperado**: el fichero se crea, se abre en la Biblioteca con los
      valores que tenía el amp, y el log dice qué claves quedaron fuera — se espera que sean
      **los tres `Contour`**, que caen fuera del rango del dump. **Si aparece alguna más**, el
      dump devolvió menos de lo esperado y eso es lo primero a mirar.
    - **Comparar un `.tsl` exportado por la app contra uno exportado por BTS** del mismo sonido.
      Es la prueba más informativa de todas y la que cerraría de una vez si el formato que
      escribimos es intercambiable de verdad.

12. 🎛️ **El EQ rediseñado: barras y perillas** (2026-09-06). ⚠️ **Ninguna de las cuatro
    mitades estaba confirmada con audio antes del rediseño y sigue sin estarlo** — lo que
    cambió es cómo se dibujan, no si funcionan. **Requiere amplificador y Edit Mode.**
    - **EQ1/EQ2 gráfico (barras)**: poner el `Selection` del bloque en `Graphic`, subir a tope
      la banda de `125Hz` y bajar a fondo la de `4KHz`. **Éxito esperado**: el sonido se
      enturbia y pierde brillo de forma obvia, y la forma de las barras en pantalla coincide
      con lo que se oye. ⚠️ **Y comprobar el paso de 0,5 dB**: el valor de encima de la barra
      debe moverse de medio en medio, no de uno en uno — es una lectura de `midi.xml` que nunca
      se ha verificado (BACKLOG, punto 8).
    - **EQ1/EQ2 paramétrico (perillas)**: con `Selection` en `Parametric`, poner `Lo Mid Freq`
      en `500Hz` y bajar `Lo Mid Gain` a `-20`. **Éxito esperado**: se ahueca el medio. Luego
      mover `Lo Mid Q` de `0.5` a `16`: el mismo corte se vuelve mucho más estrecho.
      ⚠️ **Lo específico de la perilla**: que al girarla sobre un selector el texto recorra las
      28 frecuencias **una a una y sin saltarse ninguna**. Si salta, el redondeo al índice más
      cercano está mal.
      ✅ **Esto pasó a estar cubierto por un test JVM** con el gesto nuevo del 2026-09-06
      (`KnobGestureTest`), así que aquí solo hay que confirmar que lo que se ve en pantalla
      coincide con lo que el test dice — y el gesto en sí es el punto 14.
    - **Graphic EQ de Mod/FX**: con el tipo `Graphic EQ` activo en Mod, la misma prueba de
      bandas. **Éxito esperado**: suena, y sus valores llegan a ±20 dB **enteros** — si se
      mueven de medio en medio, se le está aplicando la escala de EQ1/EQ2 y están cruzadas.
    - **Parametric EQ de Mod/FX**: ídem con el tipo `Parametric EQ`.
    - ⚠️ **La prueba que separa un fallo de UI de uno de dirección**: si una barra o perilla no
      hace nada, mirar el log — si el SET sale con la dirección esperada, el problema es del
      amplificador o de la dirección, no del widget.

13. 🔁 **El diagrama de la cadena y el botón de releer** (2026-09-06). **Requiere
    amplificador.**
    - **Diagrama**: cambiar el selector de cadena entre las siete predefinidas. **Éxito
      esperado**: el diagrama `INPUT → … → SPEAKER` **cambia de orden** con cada una. Si sale
      siempre igual, o el array de veinte no se está releyendo, o las siete cadenas no lo
      tocan — y eso último sería un hallazgo, porque contradiría el modelo de §5.
    - ⚠️ **Y compararlo contra el orden que enseña Boss Tone Studio** para la misma cadena:
      es lo que dice si estamos leyendo las veinte direcciones en el orden correcto.
      ⚠️ **Desde el 2026-09-06 esta prueba cubre también el filtro al vocabulario de diez.**
      El mapeo está confirmado contra `FxFloorboard` y hay tests JVM del filtro, pero lo que
      ninguna de las dos cosas puede decir es si el **orden** que leemos es el real: eso solo
      lo dice comparar contra BTS. Concretamente, si BTS enseña el previo en otro sitio del que
      la app pone `AMP`, el sospechoso es `CH_A`.
    - **Botón "Releer estado"**: mover una perilla física del amplificador con Edit Mode
      **apagado** (así la app no se entera), y luego pulsarlo. **Éxito esperado**: el slider
      correspondiente salta al valor nuevo. Es la prueba de que relee de verdad y no repinta lo
      que ya tenía.
    - **Pulsarlo dos veces seguidas**: la segunda debe volver a leer. Si no hace nada, el
      contador de `ReloadRequest.Manual` no está cumpliendo su función.

14. 👆 **El gesto de las perillas: mantener + arrastrar** (2026-09-06, con tres
    correcciones el mismo día). ⚠️ **Esta prueba es con el dedo y no hay forma de sustituirla**:
    los tests cubren la matemática (que no se salte pasos, que no acumule error) y la máquina de
    estados del congelado del scroll, pero **"se siente bien", "la mano no la tapa" y "el scroll
    no se mueve" no se pueden comprobar en JVM**. No requiere amplificador — basta abrir un
    preset de la Biblioteca en modo edición, donde las perillas funcionan sin cable.
    ⚠️ **Y aplica al componente de perilla en general**, no solo al EQ paramétrico: lo que se
    ajuste aquí vale para cualquier control que se cablee con `KnobControl`.
    - **Que la perilla ampliada quede claramente por encima del dedo y la mano.** Es la
      corrección principal. **Si todavía la tapa**, subir `KNOB_LIFT` (está en `dp`, así que
      el número significa lo mismo en cualquier teléfono).
    - **Que el valor se lea arriba del todo**, separado del conjunto, y cambie mientras se
      arrastra sin tener que levantar el dedo para verlo.
    - **Que la pantalla no se desplace ni un píxel durante el ajuste**, ni en vertical ni en
      horizontal — y que **vuelva a desplazarse normalmente en cuanto se suelta**. Probar las
      dos cosas seguidas: ajustar, soltar, y scrollear la pantalla.
    - ⚠️ **El caso raro que puede dejar el scroll trabado**: mantener pulsada una perilla de
      Mod/FX y, sin soltar, que el tipo activo cambie (o salir de la pantalla). **Éxito
      esperado**: el scroll sigue funcionando. Si se queda trabado, el `DisposableEffect` no
      está cubriendo ese camino.
    - ✅ **Añadido tras un fallo real (2026-09-06): que arrastrar una perilla no abra el menú
      hamburguesa.** Deslizar hacia la derecha sobre una perilla, cerca del borde izquierdo de
      la pantalla, con harta velocidad. **Éxito esperado**: el menú **no** se abre; solo el
      valor de la perilla cambia. El menú debe abrirse **únicamente** con el botón hamburguesa.
    - **Que el gesto gane frente al scroll**: mantener el dedo sobre una perilla y arrastrar en
      horizontal. **Éxito esperado**: la perilla se agranda y el valor cambia; **la fila NO se
      desplaza**. Este era el fallo original, así que es lo primero a mirar.
    - **Que la perilla grande quede por encima del dedo** y se lea entera mientras se ajusta.
      **Si aparece debajo o el dedo la tapa**, hay que subir `KNOB_LIFT_PX`.
    - **Que las perillas de los bordes de la fila no salgan cortadas** al agrandarse — es lo
      que el `Popup` debería estar resolviendo.
    - **Dirección**: derecha sube, izquierda baja.
    - **Sensibilidad**: recorrer las 28 frecuencias de `Lo Mid Freq` de punta a punta.
      **Éxito esperado**: se llega a todas sin pelearse, en dos o tres arrastres. Si se hace
      eterno, bajar `KNOB_PIXELS_PER_STEP`; si se pasa de largo, subirlo. **Está puesto a 32
      por el lado lento a propósito**, así que lo más probable es que haya que bajarlo.
    - **Soltar y volver a coger**: el valor no debe saltar al soltar.

15. 📤 **Mandar un preset `.tsl` al amplificador, con los SET troceados** (2026-09-08).
    **Requiere hardware y Edit Mode**, y ⚠️ **es destructivo**: sobrescribe el estado en edición
    del amplificador, así que lo que haya sin guardar se pierde. ✅ **Ya hay botón** (Biblioteca →
    abrir o editar un preset → "Enviar al amplificador…", con sus dos confirmaciones); el wiring y
    la lógica de estado están cubiertos por tests JVM, **lo de aquí abajo no lo está y no cambia**.
    Conviene hacerlo con un sonido de usar y tirar, no sobre uno que importe.
    - **Que un SET de 128 bytes entre siquiera.** Mandar un preset de la Biblioteca y mirar si
      cambia el sonido. **Éxito esperado**: el amplificador suena como el preset del fichero.
      **Si no cambia nada**, el primer sospechoso es el tamaño: probar bajando `chunkSize` a 64
      o a 32 antes que dar por muerta la vía entera.
    - ⚠️ **Si 221 bytes de una vez también funcionan, el troceado sobra** (no estorba, pero
      dejaría de estar justificado). Es la pregunta que esta implementación **no** resuelve:
      mandar `Fx(1)` con `chunkSize = 221` y comparar. **Éxito esperado de la prueba**: saber
      cuál es el tamaño máximo real, que ninguna fuente documenta. Si 221 entra, anotarlo y
      dejar 128 igual — no hay premio por mandar menos mensajes.
    - **Que los 128 bytes lleguen a la dirección correcta y no dos veces.** Tras mandar, releer
      con el dump y comparar `60 00 01 00`–`02 5C` contra lo que se mandó. **Éxito esperado**:
      coinciden byte a byte. **Si el segundo trozo aparece desplazado**, el acarreo de página
      está mal en el cable aunque el test JVM lo dé por bueno.
    - **Que el margen de ~30 ms entre mensajes baste.** Mandar el preset entero (~20 mensajes,
      ~0,6 s). **Éxito esperado**: entran todos. **Si se pierden mensajes intermedios** —el
      síntoma sería un preset a trozos, con unos efectos actualizados y otros no— subir el
      margen; si va sobrado, bajarlo. Es criterio propio, sin ningún dato detrás.
    - **Qué pasa con los cuatro bloques que no se mandan.** Comprobar en el panel que los
      Contour del preset anterior **siguen ahí** tras cargar uno nuevo. **Éxito esperado**: sí,
      y es lo correcto mientras su dirección esté en disputa — pero conviene verlo, porque es
      justo la mezcla que el usuario podría no esperar.
    - **Y después del envío, el guardado**: `sendPreset` solo llena el búfer de edición. Para
      dejarlo fijo hace falta además `savePreset` a un canal, que es la prueba del punto 4 del
      bloque de guardado y sigue igual de pendiente.
    - 👆 **Y lo que ningún test JVM puede ver: la pantalla.** Que el botón salga en las dos
      vistas (detalle y edición), que esté apagado con su aviso sin Edit Mode, que los dos
      diálogos se lean bien —sobre todo la lista de bloques omitidos, que puede ser larga— y que
      "Enviando…" no deje pulsar otra vez. **Éxito esperado**: se puede leer todo sin que se
      corte, y no hay forma de mandar dos veces seguidas sin querer.

16. 🖥️ **`AmpScreen`: que la pantalla nueva se vea y se comporte** (2026-09-08). La
    extracción está cubierta por tests JVM y por la compilación; **lo que no se puede probar sin
    dispositivo es el renderizado**, y de eso va este punto. **Requiere amplificador** para las
    dos últimas comprobaciones.
    - **Que la pantalla exista y se llegue**: menú hamburguesa → "Amplificador". **Éxito
      esperado**: sale canal, modelo, variación, Solo, los seis niveles, Noise Gate, Contour,
      EQ1, EQ2 y la cadena con su diagrama — **y ni un control de efecto**.
    - **Que quepa y se lea**: bajar la pantalla entera. Es larga (dos bloques de EQ con sus 11
      perillas cada uno). **Éxito esperado**: nada cortado, el scroll no se pelea con el gesto de
      las perillas (el congelado de `knobAwareVerticalScroll` sigue aplicando).
    - ⚠️ **Que la Biblioteca no se haya movido**: abrir un preset en "Editar" y comprobar que el
      editor offline se ve **igual que antes** — mismo orden: amplificador, efectos y al final
      Noise Gate/Contour/EQ/cadena. **Éxito esperado**: idéntico. Si algo cambió de sitio, la
      extracción reordenó lo que no debía.
    - **Que el contrato de Edit Mode siga en pie** (requiere amplificador): con Edit Mode
      apagado, **solo** el selector de canal debe quedar activo; todo lo demás gris con su aviso.
    - **Que los controles sigan funcionando desde la pantalla nueva** (requiere amplificador y
      audio): mover Gain desde `AmpScreen` y comprobar que suena, igual que desde Sliders. Es la
      comprobación de que la extracción no perdió ningún cable por el camino — el resto de
      direcciones sigue con el estado de confirmación que ya tenía, esta pantalla no cambia nada
      de eso.
    - **Lo mismo, ahora para `EffectsScreen`** (2026-09-08): menú hamburguesa → "Efectos" debe
      enseñar las cinco tarjetas y **nada más** — ni canal, ni EQ, ni Noise Gate, ni Contour, ni
      cadena. Comprobar también que el editor offline de la Biblioteca sigue viéndose igual que
      antes: amplificador, luego efectos, luego Noise Gate/Contour/EQ/cadena, en ese orden.
      **Éxito esperado**: idéntico a antes de las dos extracciones. Con amplificador y audio,
      mover un parámetro de efecto (por ejemplo el nivel de Reverb) desde `EffectsScreen` y
      comprobar que suena igual que desde Sliders.

17. 🗂️ **`PresetsScreen` y la desaparición de "Sliders": que no se haya perdido nada por el
    camino** (2026-09-09). La reubicación está cubierta por la compilación y los tests; **lo que
    no se puede comprobar sin dispositivo es la pantalla**. Las dos primeras no necesitan
    amplificador.
    - **Que el menú tenga cuatro entradas** —Logs, Amplificador, Efectos, Presets— y que
      "Presets" abra el bloque en vivo arriba y la Biblioteca abajo, **claramente separados**.
      **Éxito esperado**: se lee de un vistazo qué actúa sobre el amplificador y qué son ficheros.
    - ⚠️ **Que el editor offline se vea EXACTAMENTE igual que antes** (es lo que más riesgo tenía
      al borrar `SlidersPane`): abrir un preset con "Editar" y comprobar orden —amplificador,
      efectos, Noise Gate/Contour/EQ/cadena— **y sobre todo que se pueda bajar hasta el final**,
      que es el scroll que aportaba la pantalla borrada. **Éxito esperado**: idéntico, y el
      arrastre de una perilla no mueve el scroll.
    - **Que abrir un preset esconda el bloque en vivo**: dentro del detalle o del editor no debe
      verse "Guardar preset…" ni "Exportar"; la única acción hacia el amp es "Enviar al
      amplificador…". **Éxito esperado**: así es.
    - **Con amplificador**: que "Guardar preset…" siga apagado sin Edit Mode y encendido con él,
      que "Exportar" funcione **sin** Edit Mode (solo lee), y que las dos operaciones hagan lo
      mismo que hacían desde la pantalla vieja. Es reubicación, así que **lo que se prueba aquí es
      que no se rompió el cableado**, no las operaciones en sí — esas siguen con sus pruebas
      propias (puntos 15 y el guardado en canal).

18. 🧭 **El shell de la Fase 2: navegación, estado de conexión y el aviso de "sin
    amplificador"** (2026-09-09). Las tres primeras **no necesitan amplificador** —de hecho, lo
    interesante es probarlas **desenchufado**—.
    - **Sin cable**: abrir la app debe caer en **Amplificador** (ya no en Logs), la barra superior
      debe decir "Sin amplificador", y Amplificador y Efectos deben enseñar **el aviso con la
      explicación y el botón de buscar**, no controles grises. **Éxito esperado**: se entiende qué
      falta sin ir a ninguna otra pantalla.
    - **Que la Biblioteca siga entera sin cable**: en Presets, el bloque de arriba avisa y la
      lista de abajo funciona igual — importar, abrir, editar. **Éxito esperado**: sí.
    - **El back**: desde Efectos o Presets vuelve a Amplificador; desde Amplificador cierra la
      app; con un preset abierto lo cierra primero. **Éxito esperado**: en ese orden.
    - ⚠️ **Cambios sin guardar**: editar un preset, cambiar a Efectos y volver a Presets →
      **el editor sigue ahí con los cambios**. Y pulsar "Volver" (o el back) con cambios → sale el
      diálogo de descartar. **Éxito esperado**: las dos cosas; si al volver de la pestaña se
      hubiera perdido lo editado, es el fallo más grave de esta tanda.
    - **Girar la pantalla** en cada pestaña: debe quedarse donde estaba (lo guarda el `Saver` de
      `ShellNavigation`).
    - **Con amplificador**: que la barra superior pase a "Conectado: KATANA…", que el aviso
      desaparezca solo, y que "Buscar amplificador" del aviso haga lo mismo que el botón de Logs.

10. 🔬 **Diagnóstico de Solo, Bright y Gain SW — la prueba para la que se hizo la
    instrumentación** (2026-09-05). Los tres no produjeron ningún efecto al probarlos. La
    tarjeta "Diagnóstico (temporal)" de la sección de amplificador tiene todo lo necesario.
    **Requisito**: Edit Mode encendido (sin él la app no puede confirmar nada, §4.2).
    - **Solo, cuál de las dos candidatas responde.** Con el efecto sonando, mover el switch y
      el nivel de "candidata 2" (`60 00 06 14`/`15`). **Éxito esperado**: sube el volumen de
      forma audible y el nivel lo gradúa — sería la respuesta de toda la Parte 1.
      **Si la UI se enciende y vuelve sola a apagado**, es la firma de una dirección de solo
      reporte (§5, `60 00 06 5C`): la candidata 2 tampoco es, y el Solo del amplificador pasa a
      no tener dirección conocida.
    - **Las dos candidatas del Solo, con SET+GET.** Botones "Solo cand.1 ON/OFF" y
      "Solo cand.2 ON/OFF", cada uno en sus dos valores. **Éxito esperado**: el log dice, para
      cada una, cuál de las cuatro conclusiones aplica. **Lo más informativo sería que la
      candidata 1 diga "el valor interno NO se movió" y la 2 "la escritura SÍ entra"** — eso
      cerraría la pregunta de una vez.
    - **Bright (`60 00 00 29`) y Gain SW (`60 00 00 2A`), con SET+GET.** Es el objetivo de la
      Parte 2 y lo que separa las dos hipótesis:
      - **"el valor interno cambió"** → la dirección es correcta y la escritura entra. Que no
        suene apunta entonces a un **parámetro inerte** en este modelo (Bright, por ejemplo,
        solo existe en algunos canales según `midi.xml`), no a una dirección equivocada.
        Siguiente paso sería repetir la prueba **con cada tipo de amplificador**, porque el
        efecto puede depender del canal activo.
      - **"el valor interno NO se movió"** → la dirección acepta el mensaje y lo descarta:
        está mal identificada o es de solo lectura, y habría que buscar otra candidata como se
        hizo con el reverb.
      - **"no contesta al GET"** → la dirección ni siquiera se lee, que sería el peor caso y el
        más informativo: el bloque PREAMP entero pasaría a ser sospechoso.
    - ⚠️ **Probar cada switch en sus dos valores.** Con uno solo, si el amplificador ya estaba
      en ese valor la prueba dice "no concluyente" y no distingue nada — el propio `verdict` lo
      avisa, pero conviene saberlo antes de sacar conclusiones.
    - **Cuando haya resultado**: quitar la tarjeta de diagnóstico y, según lo que diga, o
      cambiar el Solo a la candidata 2, o documentar las tres direcciones como no funcionales
      con el mismo detalle que `60 00 05 48` en §5.

17. 🎙️ **Grabación de audio USB: los tres pasos que deciden si la feature es viable**
    (2026-09-08, investigación en "Por hacer" punto 7). **No necesita NDK ni ninguna
    dependencia nueva**: `claimInterface` y `setInterface` están en la API pública, y estos
    tres pasos se hacen con lo que la app ya usa. Son deliberadamente lo primero, porque
    **pueden matar la idea entera en cinco minutos** y ahorrarse la discusión del NDK.
    - **Paso 0 — descartar la vía fácil.** Con el amp enchufado, listar
      `AudioManager.getDevices(GET_DEVICES_INPUTS)` y buscar un `TYPE_USB_DEVICE`.
      **Éxito esperado: que NO aparezca.** Android exige conformidad con USB Audio Class y el
      Katana declara `bInterfaceClass 255`, así que debería estar ausente por el mismo motivo
      que `MidiManager` nunca lo vio (§4.1). ⚠️ **Si apareciera**, sobra todo lo demás —
      `AudioRecord` bastaría y la feature pasa de "meses y código nativo" a "un día".
    - **Paso 1 — que el teléfono vea lo mismo que vio el PC.** Leer
      `UsbDeviceConnection.getRawDescriptors()` y buscar los bytes de `FORMAT_TYPE_I` de la
      interfaz 2. **Éxito esperado**: aparece `0b 24 02 01 04 04 18 01 44 ac 00` tal cual —
      4 canales, contenedor de 32 bits, 24 bits reales, 44100 Hz. Confirma de paso que la
      decodificación de "Por hacer" §7.3 es correcta y que no hace falta deducir el formato.
    - **Paso 2 — LA prueba que decide.** `claimInterface(interfaz 2, force = true)` y después
      `setInterface(alt 1)`. **Éxito esperado**: las dos devuelven `true` **y la interfaz 3
      sigue funcionando** (el MIDI no se cae, los parámetros se siguen leyendo y escribiendo).
      ⚠️ **Si falla, el tema se cierra ahí y no ha costado nada**: sin poder activar el alt
      setting 1 no hay endpoint isócrono al que pedirle nada, y ninguna cantidad de libusb lo
      arregla. Documentarlo entonces con el mismo detalle que el handshake de §4.1.
    - ⚠️ **Comprobar explícitamente que el MIDI sobrevive**, no solo que las llamadas devuelven
      `true`. Tomar una segunda interfaz del mismo dispositivo mientras la 3 está tomada es
      justo lo que podría romperse, y sería un mal cambio a cambio de una feature secundaria.
    - **Solo si el paso 2 pasa** tiene sentido plantear los pasos 3-5 del plan (prototipo con
      libusb, mapeo de canales, y si la captura funciona con la salida parada) — y el NDK es
      entonces **una decisión aparte y explícita**, según CLAUDE.md §6, no una consecuencia
      automática.

19. 🎨 **La Fase 3, que es lo único de este proyecto que un test no puede aprobar** (2026-09-09).
    Es todo visual: **no hace falta amplificador para casi nada**, hace falta mirarlo.
    - **Que la app abra oscura y sin fogonazo blanco.** Abrirla y cerrarla varias veces.
      **Éxito esperado**: nunca se ve un destello claro antes de la primera pantalla. Si se ve, el
      `windowBackground` de `themes.xml` no está entrando.
    - ⚠️ **Con el teléfono en modo claro.** Poner el sistema en tema claro y abrir la app.
      **Éxito esperado**: sigue oscura, entera y sin partes claras sueltas. Un diálogo o un menú
      desplegable que salga blanco es la señal de que algún componente de Material se está
      saltando el esquema.
    - **Que el acento sea de la app y no del fondo de pantalla.** Cambiar el wallpaper por uno de
      un color muy distinto (azul, verde) y volver a abrir. **Éxito esperado**: nada cambia — el
      naranja sigue siendo el mismo. Era justo lo que fallaba antes de esta fase.
    - ⚠️ **La prueba que de verdad importa, y necesita amplificador: el color de la tarjeta contra
      el LED del panel.** Con Edit Mode, poner el color de Booster en **Verde** desde la app.
      **Éxito esperado**: se enciende el LED **verde** del panel y la franja de la tarjeta se pone
      verde. Repetir con Rojo y Amarillo.
      ⚠️ **Si saliera al revés** (verde en la app enciende el rojo del panel), lo que está mal es
      el orden de `EffectColor` —`midi.xml` tendría razón en su tabla de valores y no en su
      etiquetado de slots— y el arreglo son **tres constantes**, no un cambio de diseño. Ver
      CLAUDE.md §4.6 para la evidencia de por qué se apostó por verde = `00`.
    - **Que se distinga el amarillo del slot del naranja del acento.** Poner un efecto en Amarillo
      y mirar la tarjeta con un slider seleccionado al lado. **Éxito esperado**: se distinguen; y
      aunque no se distinguieran de un vistazo, el selector lo dice con palabras. Si en el
      teléfono resultan indistinguibles, lo que hay que mover es el ámbar del acento
      (`EmberPrimary`), no el amarillo del slot — el slot es el hecho, el acento es la elección.
    - **Legibilidad de las cinco tarjetas y de los encabezados de sección** en las tres pantallas,
      con el brillo bajo. **Éxito esperado**: el gris de `onSurfaceVariant` y el acero de los
      encabezados se leen; si no, subir su luminosidad en `Color.kt` — es un cambio de una línea.
    - **El ícono en el lanzador**: que se vea la perilla y no un cuadro negro, con la máscara que
      use el teléfono (círculo, cuadrado redondeado). Y con **íconos temáticos** activados
      (Android 13+), que se vea el arco y no un disco relleno.
    - **El nombre debajo del ícono**: que quepa «KTNA Control» sin cortarse.

20. 🎨 **La Fase 4, visual como la 3**: los estados de la Biblioteca y los tres diálogos
    destructivos (2026-09-09).
    - **Abrir la Biblioteca justo al arrancar la app.** **Éxito esperado**: se ve «Cargando…»
      una fracción de segundo (o nada, si el disco responde antes de que se note) y nunca el
      mensaje de "no hay presets" mientras todavía no se sabe si hay algo.
    - **Con presets ya importados, borrar uno y volver a la lista.** **Éxito esperado**: la
      lista no parpadea a "Cargando…" en ningún momento — sigue mostrando el resto sin hueco.
    - **Abrir un preset con bloques `DISPUTED`** (cualquier `.tsl` real de Boss Tone Studio los
      trae: los tres `Contour` y `GafcExp1AsgnMinMax`) **en el editor, no solo al mirarlo o al
      enviarlo.** **Éxito esperado**: la tarjeta de "no disponible" aparece bajo el nombre,
      igual que en la vista de solo lectura.
    - **Los tres diálogos destructivos, uno detrás de otro**: guardar en canal, enviar al
      amplificador, y sobrescribir un preset de la Biblioteca. **Éxito esperado**: el paso final
      de los dos primeros se ve idéntico salvo las palabras (mismo tono, mismo patrón "Sí,
      `<verbo>`"), y el de la Biblioteca —de un solo paso— usa "Sí, sobrescribir" cuando pisa un
      fichero y "Guardar" cuando no.

21. ♿ **La Fase 5, la última visual: accesibilidad y calidad mínima** (2026-09-09). Como la
    Fase 3, casi todo necesita mirarlo, no medirlo — el contraste ya está calculado en frío en
    CLAUDE.md §4.9, así que aquí lo que falta es la parte que ningún cálculo confirma.
    - **TalkBack en la barra de navegación y en `EffectCard`.** Activar TalkBack y recorrer las
      tres pestañas y una tarjeta de efecto con gestos de exploración. **Éxito esperado**: cada
      elemento se lee una sola vez, con el texto que ya se ve en pantalla — ninguno se lee dos
      veces (el punto de color no debería anunciarse aparte del nombre del efecto ni del
      selector «Verde/Rojo/Amarillo»).
    - **La fila de un preset en la Biblioteca, con TalkBack.** Tocar la fila entera, no solo el
      botón «Editar». **Éxito esperado**: TalkBack la anuncia como un elemento tocable completo,
      y el área que responde al toque se siente igual de generosa que el resto de botones de la
      app — no una franja fina alrededor del texto.
    - **Tamaño de fuente del sistema al máximo** (Ajustes → Accesibilidad → Tamaño de fuente).
      Recorrer las tres pantallas de dominio. **Éxito esperado**: el texto crece y sigue
      cabiendo sin cortarse ni solaparse; los tamaños de perilla, franja y espaciado **no**
      cambian — si cambiaran, sería la señal de que algo quedó en `.sp` por error.
    - **Contraste a simple vista, con luz de sala normal y con poca luz.** Mirar el texto
      secundario (gris de `onSurfaceVariant`) y los tres colores de slot sobre una tarjeta.
      **Éxito esperado**: todo se lee sin esfuerzo — es la confirmación visual de los números ya
      calculados en CLAUDE.md §4.9, no una medición nueva.

### 2026-09-09 — QA: lo que hay que volver a comprobar con el amplificador

Todo lo de esta tanda pasa los tests JVM; lo que sigue necesita el cable y el oído.

1. **La variación, en los cinco canales y en las dos direcciones** (A.1). En cada uno de
   Acoustic/Clean/Crunch/Lead/Brown: encender la variación → el sonido cambia y el LED del panel
   se enciende; apagarla → vuelve al modelo base. **Resultado esperado**: el switch ya no se
   queda clavado en OFF y se puede apagar. ⚠️ Probar además con un **sneaky amp** activo (p. ej.
   `Pro Crunch`): el switch debe estar **habilitado**, y tocarlo cambia el modelo al `[CRUNCH]` /
   `Var [Crunch]` de la perilla — es el comportamiento del botón VARIATION del panel, pero
   conviene verlo y confirmar que no sorprende.
2. **Las siete cadenas predefinidas** (A.2). Recorrer `CHAIN 1 · 2-1 · 3-1 · 4-1 · 2-2 · 3-2 ·
   4-2` y comparar el diagrama con Boss Tone Studio. **Resultado esperado**: las siete secuencias
   exactas del reporte de QA, y **el diagrama cambia al cambiar de cadena** (antes no cambiaba
   nunca). Ojo también a que **no haya un segundo dump** por cada conexión: el log lo dice.
3. **Que los valores crudos `00`..`06` sean los correctos** (A.2). Salen de código de FxFloorboard,
   no de una prueba. Si alguna cadena sale cruzada, el orden de `ChainPreset` es lo primero a
   mirar — en particular que las dos familias van seguidas, no intercaladas.
4. **La Biblioteca, en el teléfono real** (A.3). Abrir **Presets**: deben verse el bloque en vivo
   arriba, el divisor, el encabezado «BIBLIOTECA» y la lista, **desplazándose todo junto**.
   ⚠️ Probar con la **escala de fuente del sistema al máximo**, que es donde el bug original se
   manifestaba con más fuerza. **Resultado esperado**: la lista siempre se alcanza.
5. **El selector de modelo en dos páginas** (C). Deslizar entre `AMP TYPE` y `SNEAKY AMPS`; que el
   botón de variación **no aparezca** en la segunda; que al cambiar de canal el selector salte
   solo a la página del modelo activo; y que con `Var [X]` puesto el chip marcado sea **X**.
6. **La tarjeta de Solo en la pantalla de Efectos** (C), después de Reverb: que el on/off y el
   nivel sigan haciendo lo mismo que hacían en la pantalla de amplificador.
7. **La sensación del gesto con las tiras paginadas** (bloque C, cerrado el 2026-09-09).
   **Implementado, pendiente de probar**: la matemática del paginado está cubierta por
   `ControlPagingTest` y la compilación, pero *cómo se siente* solo lo dice el dedo. Qué mirar:
   que **pasar de página no dispare una perilla ni una barra** (el conflicto que la decisión dice
   estar resuelto por el eje + `KnobInteraction`); que **mantener pulsado para ajustar no se sienta
   lento** ahora que el mismo dedo también pagina; que **3-4 controles por tira se lean** con el
   amplificador delante y la letra del sistema en su tamaño normal; y que en la última página los
   controles **no se estiren** (van con hueco de relleno a propósito).
   **Resultado esperado**: arrastrar en horizontal pagina, mantener-y-arrastrar ajusta, y ninguno
   de los dos se cuela en el otro.
8. **El pase visual de las tarjetas** (mismo bloque): que `BlockHeader` con el interruptor a la
   derecha se lea bien en Noise Gate, Contour, EQ1/EQ2, cadena y Solo, y que los nombres cortos
   (`short_*`) no queden cortados en una celda de 80 dp con la letra grande.
9. **SOLO EQ: ¿contesta la región `60 00 0F 1x` a un GET?** (B.1). Es la pregunta que decide si
   cablear el bloque es barato o inaceptable — la misma que sigue abierta para los Contour por
   slot. **Resultado esperado si va bien**: el GET contesta rápido y los diez controles se pueden
   registrar sin ampliar el dump ni paralelizar los respaldos.

11. **El GET global, y que no falte ninguno de los individuales** (2026-09-10).
   **Implementado, pendiente de probar.** Qué mirar: que el botón de arriba **repueble todo** tras
   mover perillas en el amplificador con Edit Mode apagado (que es cuando más se usa), que diga
   «Releyendo…» mientras dura, y que en la **Biblioteca** (editor offline) no aparezca ningún botón
   de leer, ni el global ni dentro de las celdas. ⚠️ Comprobar en particular los **Contour por
   slot**, que son los únicos que caen fuera del dump y dependen del GET de respaldo en serie — si
   esa región no contesta, es donde se notaría la pérdida del botón individual.
   **Resultado esperado**: un toque deja la pantalla igual que una reconexión.

10. **Los seis niveles del panel como tira vertical, y el aspecto nuevo de las barras**
   (2026-09-10). **Implementado, pendiente de probar.** Qué mirar: que en **Amplificador** y en el
   **editor de la Biblioteca** los seis se lean y se ajusten igual de bien que antes en horizontal
   —Gain sigue siendo Gain—, que la página 1 sea **`Bass · Middle · Treble`** y la 2
   **`Gain · Volume · Presence`** (y que sigan siendo tres por página en horizontal, donde cabrían
   más), que la barra más alta y gruesa **no obligue a desplazarse** para ver el resto de la
   pantalla, que **pasar de la página 1 a la 2** no dispare ninguna barra, y que la tapa del fader y la pastilla del valor se distingan con el amplificador
   delante y a media luz. ⚠️ Comprobar también el caso que motiva el suelo de 3 dp: **un nivel en
   0 tiene que verse distinto de uno sin leer** (`—`). **Resultado esperado**: una sola forma de
   control continuo en toda la app, y ningún gesto perdido.

## Hallazgos de diagnóstico

Bugs **investigados y reproducidos pero todavía sin arreglar**, con la evidencia que los
sostiene. De esto depende cómo se diseña la corrección; no se toca código hasta decidirlo.

### 2026-09-04 — El canal depende de Edit Mode: **no es una regresión del fix de recarga**

Reportado como "antes del fix de hoy cambiar de canal funcionaba; ahora con Edit Mode apagado
no tiene efecto y revierte al canal real". Investigado comparando el código contra `HEAD`.

**No hay, ni ha habido nunca, un gate de Edit Mode en el camino de escritura.** Comprobado por
grep sobre todo `main/`: `editMode` aparece **solo** en la pantalla y en el ViewModel que lo
enciende; `device/` y `protocol/` no saben qué es. `onSelectorChanged` → `KatanaEnumParameter.set()`
construye el SysEx y lo manda sin mirar nada más que el valor. El refactor de hoy no añadió
ninguna condición: se limitó a mover *quién dispara la recarga*.

**Y el camino que hace "revertir" el canal es byte a byte el mismo que antes del refactor.**
Las dos piezas relevantes son idénticas en `HEAD` y ahora:

| Pieza | Antes (`HEAD`) | Ahora |
| --- | --- | --- |
| Disparador de recarga | `channel.state` → `collectLatest` → `delay(300)` → `reload()` | igual, con la petición pasando por `ReloadRequest` |
| El canal en el dump | `dump.byteAt(00 01 00 00)` = null → `missing` → `read()` | `dump.bytesAt(...)` = null → `missing` → `read()` |

O sea: **antes y ahora**, escribir el canal desde la app deja la caché optimista en el valor
nuevo, dispara una recarga 300 ms después, y esa recarga relee `00 01 00 00` con un GET de
respaldo —porque esa dirección vive fuera del dump y siempre cae ahí— y pisa la caché con lo
que conteste el amplificador. Si el amplificador ignoró la escritura, el selector vuelve solo.

⚠️ **Entonces esto es comportamiento preexistente, no una regresión — y encaja con que nunca
se hubiera notado**: todas las confirmaciones anteriores (canal el 2026-09-03, tipos de efecto
y parámetros de Booster el 2026-09-04) se hicieron **con Edit Mode ya encendido**. Con edit
mode activo el amplificador acepta la escritura y además la reporta, así que la relectura
confirma el canal nuevo y no hay nada que ver.

**Lo que el código no puede decidir**: si el amplificador exige edit mode para *aceptar* el SET
de `00 01 00 00`. Los síntomas reportados encajan con que sí lo exige —ignora la escritura pero
sigue contestando el GET, que es lo que permite a la app enterarse—, y CLAUDE.md ya recogía que
`katana_sysex.txt` pide edit mode para **leer** el canal. Pero eso es una afirmación sobre el
hardware y **necesita la prueba real**, no una lectura de código: ver "Pendiente por probar",
punto 2, que incluye el mensaje de log exacto que lo confirmaría.

**Si se confirma que el amplificador lo exige**, el contrato "cambiar de canal siempre, con o
sin edit mode" no se puede cumplir mandando SysEx a `00 01 00 00`, y las dos salidas tienen
coste propio: **Program Change** (§5.1) necesita una segunda ruta de empaquetado USB-MIDI que
hoy no existe y depende del canal MIDI configurado en `00 02 00 00`; **encender edit mode para
la escritura** altera el estado del amplificador, que §4.2 exige que sea siempre explícito y
visible, nunca silencioso. Ninguna de las dos se ha implementado a la espera de la prueba.

### 2026-09-04 — Cambiar el tipo de amplificador no recarga nada (y los Sneaky Amp no tienen bloque propio)

Reportado como "cambiar Clean→Lead o Brown→MS1959 no trae parámetros del amp".

**Causa inmediata, y no es una race condition:** el único disparador de recarga que existe
observa `repository.channel.state`. Cambiar el modelo de amplificador **no cambia el canal**,
así que **no se dispara ninguna relectura, por diseño**. No hay bug de parseo aquí: no hay
parseo, porque no hay lectura.

⚠️ **La hipótesis de que los Sneaky Amp guardan sus parámetros en otro rango no se sostiene.**
Comprobado en `midi.xml`: existe **un único bloque PREAMP**, `60 00 00 21`–`2C`, y lo comparten
los 30 modelos, incluidos los cinco `Var [...]` (`0x1C`–`0x20`) que son los "sneaky":

| Dirección | Parámetro | midi.xml | ¿en la app? |
| --- | --- | --- | --- |
| `60 00 00 21` | Type (30 modelos) | 37310 | ✅ `AMP_TYPE_FULL` |
| `60 00 00 22` | Gain | 37342 | Alias `GAIN_LEVEL_LOW`, sin usar — la alta (`06 51`) ya está confirmada y es la fuente de verdad |
| `60 00 00 23` | *(sin nombre)* `range 00/14/-10/+10` | 37465 | Ni documentado ni implementado — no hay ninguna alta que lo cubra |
| `60 00 00 24`–`27` | Bass · Middle · Treble · Presence | 37468–37477 | Alias `*_LEVEL_LOW`, sin usar — mismas altas confirmadas |
| `60 00 00 28` | Volume | 37480 | Alias `VOLUME_LEVEL_LOW`, sin usar — misma alta confirmada |
| `60 00 00 29` | Bright | 37483 | ⚠️ **implementado 2026-09-04** (`AMP_BRIGHT`), sin confirmar |
| `60 00 00 2A` | Gain SW | 37487 | ⚠️ **implementado 2026-09-04** (`AMP_GAIN_SW`), sin confirmar |
| `60 00 00 2B`–`2C` | Solo Sw · Solo Level | 37492–37496 | ⚠️ **implementados 2026-09-04** (`AMP_SOLO_ENABLED`/`AMP_SOLO_LEVEL`), sin confirmar |

**Actualización 2026-09-04: los cuatro que faltaban ya están cableados.** Bright, Gain SW,
Solo Sw y Solo Level tienen ahora su `KatanaControl`, su fila en `SlidersPane` (bajo el switch
de Variación) y sus tests JVM — ver "Hecho" y "Pendiente por probar". Las direcciones "bajas"
de Gain/Bass/Middle/Treble/Presence/Volume (`00 22`, `00 24`–`28`) **no se duplican**: siguen
documentadas como alias sin usar, porque las altas (`06 51`–`06 56`) ya están confirmadas con
audio y son la fuente de verdad. `60 00 00 23` sigue sin nombre y sin ninguna alta que lo
cubra, así que queda fuera — no hay con qué contrastarlo.

O sea: el amplificador es **"DSP simple"** como el Booster —un juego fijo de parámetros, el
tipo solo cambia el modelo— y no "DSP complejo" como Mod/FX. Cambiar de tipo no mueve el mapa
de memoria.

**Todo ese bloque sí viene en el dump** (`60 00 00 2x` cae de lleno en `60 00 00 00` + 1920 B),
así que **no hace falta ampliar el rango del GET**: los bytes ya están llegando y se están
tirando. La app lee las perillas por las direcciones "altas" `60 00 06 51`–`56` (§5) y de este
bloque solo registra `00 21`. Lo que falta es registrar los controles, no pedir más memoria.

**Lo que queda por resolver con el amplificador delante**, porque ninguna fuente lo dice y no
se puede deducir del XML: si al cambiar el modelo el amplificador **recalcula** los valores de
Gain/EQ del preset (y entonces hace falta releer al cambiar de tipo, igual que al cambiar de
canal) o los **conserva** (y entonces no hace falta releer nada, solo implementar los
controles que faltan). Es la misma pregunta abierta que ya está anotada para los tipos de
efecto: "si cambiar el tipo resetea los parámetros del slot o los conserva" (CLAUDE.md §5.2).

**Prueba propuesta** para decidirlo, a añadir a "Pendiente por probar" cuando se ataque:
poner Gain al 20 en Clean, cambiar a Lead desde el panel del amplificador, y mirar en la
pantalla de diagnóstico si llega algún mensaje espontáneo por `60 00 06 51` o `60 00 00 22`.
Si llega, el amp recalcula y hay que releer; si no llega nada, conserva y basta con cablear
los controles.

## Por hacer

Roadmap de **bloques de trabajo** hacia la visión de alcance completo de CLAUDE.md ("Visión
de alcance"): una alternativa completa a Boss Tone Studio. Son bloques, no tareas atómicas
listas para ejecutar — cada uno probablemente se descompone en varias tareas más pequeñas
cuando le toque investigarse, con la misma disciplina de siempre (nada se da por bueno sin
confirmarlo con audio o con el amplificador real).

1. ✅ **Hecho y confirmado (2026-09-03) en Booster; extendido a los cinco efectos
   (2026-09-04).** Se escribe en la dirección de **tipo activo**; ver CLAUDE.md §5.2. Mod, FX,
   Delay y Reverb usan la misma dirección gemela pero están **pendientes de confirmar** — ver
   "Pendiente por probar".
2. ✅ **Booster hecho (2026-09-04), pendiente de confirmar con audio** — ver "Pendiente por
   probar". Sus 9 parámetros fijos (`60 00 00 10`–`18`) están todos cableados. **Falta Delay y
   Reverb**, que comparten la misma forma de "DSP simple" (un juego fijo, no un bloque por
   tipo) y deberían ser un trabajo parecido de encontrar sus propias direcciones. **Mod y FX
   siguen siendo harina de otro costal** —un bloque de direcciones distinto por cada uno de
   sus 31 tipos, con una sola fuente de Mk2— pero ya **no hay que leer el XML**: el mapa
   completo está extraído y documentado (CLAUDE.md §5.2, "El mapa de parámetros internos de
   Mod y FX"), con **los 31 bloques detallados parámetro a parámetro** (extracción terminada el
   2026-09-05), el catálogo de sus 36 selectores y la regla `FX = Mod + 0x0200` verificada
   con 0 diferencias de dirección. Cablear un tipo es ahora copiar una tabla, salvo por las
   nueve anomalías que la extracción dejó señaladas. Ya **no hay ninguna que bloquee**: la del
   paso de 0,5 ms se resolvió con `FractionalLevelScale` y afecta solo a 2x2 Chorus, y los
   parámetros de 2 bytes (los cuatro `Pre Delay` de Pitch Shifter y Harmonist más el
   `Repeat Rate` de DC30) los cubre el `byteWidth = 2` que ya existe. La que más cuidado pide
   al cablear es el `Repeat Rate` de DC30: va en rpm y **empieza en el crudo `0x28`, no en 0**.
3. **Controles sin perilla física en el panel**: Noise Gate, Solo, Contour, posición IN/OUT de
   EQ1 y EQ2 (antes o después del preamp), selección de cadena de efectos (chain).
   ⚠️ **Actualización del 2026-09-06 tras probar con el amplificador**: el reordenado manual de
   la cadena se retiró (no funciona; queda el selector de las siete predefinidas más un diagrama
   de solo lectura), y Bright/Gain SW resultaron no tener efecto y salieron de la UI. Lo que
   sigue abierto de este bloque es **el Solo**, con sus dos candidatas sin desempatar.
   ✅ **Investigación documental terminada el 2026-09-05**: los cinco están localizados con
   dirección, tipo y rango en CLAUDE.md §5, "Controles sin perilla física". Cablearlos es
   trabajo mecánico salvo por tres cosas: **Solo tiene dos direcciones candidatas** y hay que
   probar cuál responde; **los Contour por slot caen fuera del dump** y dependen del GET de
   respaldo; y **el EQ gráfico necesita `FractionalLevelScale`** por su paso de 0,5 dB.
4. **Guardado de presets** — escribir el estado actual editado a un canal específico (p. ej.
   1A), equivalente a grabar un preset desde el panel pero hecho desde la app. `AmpState` ya
   cubre la lectura del estado actual. ✅ **Investigación documental terminada el 2026-09-05**
   (CLAUDE.md §5, "Guardado de presets"): `7F 00 01 04` confirmado para el Mk2 en código de
   FxFloorboard, con dato de 2 bytes `00 xx` en la numeración de canal que el proyecto ya
   maneja. Implementarlo no necesita nada nuevo del transporte ni del checksum; son dos SETs
   (nombre en `60 00 00 00`, después el commit). Lo que falta es medir contra el amplificador:
   si responde, si PANEL es destino legal, y cuánto esperar entre guardados.
5. ✅ **Import/export de archivos `.tsl` — cableado entero el 2026-09-06**, pendiente de
   probar. Importar, ver, **editar sin amplificador**, crear desde cero y **exportar** el estado
   del amp funcionan; ver "Hecho" y "Pendiente por probar", punto 11. La estructura del formato
   se mapeó el 2026-09-05 (CLAUDE.md §5) y el `size: 50` de `Patch_1` está corregido a 91 en
   código, con un test que lo fija.
   ✅ **El troceado de los SET de 221 bytes está hecho (2026-09-08)**, junto con el camino de
   escritura entero: `RolandSysEx.setChunked`, `protocol/tsl/TslTransfer` y
   `KatanaRepository.sendPreset`, con 17 tests JVM. Ver "Hecho".
   ✅ **Y el botón está cableado (2026-09-08)**: "Enviar al amplificador…" en la vista de
   detalle y en la de edición, bajo `canEdit`, con las mismas cautelas del guardado en canal y
   con la lógica de estado en `PresetSendFlow` (13 tests JVM). Ver "Hecho".
   **Lo que sigue abierto es solo probarlo contra el amplificador** ("Pendiente por probar",
   punto 15): si acepta un SET de 128 bytes de datos, si 221 de una vez también habrían valido,
   y si el margen de ~30 ms entre mensajes basta.

6. **UI real de control** — pantallas por dominio (`AmpScreen`, `EffectsScreen`,
   `PresetsScreen`, ver CLAUDE.md §4.2) en vez de la pantalla de diagnóstico actual, sin
   exponer direcciones SysEx a la capa de Compose.
   ✅ **Arrancado el 2026-09-08 por `AmpScreen`** (la condición de entrada —2-3 efectos
   completos— estaba de sobra cumplida). Con él quedó fijada **la forma de extraer las demás**:
   sacar los composables del dominio y que los llamen tanto la pantalla nueva como el
   `SlidersPane` que sigue sirviendo al editor offline, sin reordenar este último (CLAUDE.md
   §4.2). Ver "Hecho" y "Pendiente por probar", punto 16.
   ✅ **`EffectsScreen` también arrancada (2026-09-08)**, con la misma extracción sin reabrir
   la decisión de arquitectura — ver "Hecho". No hizo falta ninguna lista de reparto nueva:
   `EffectId.entries` y `AmpDomain.EFFECT_SELECTORS` ya bastaban.
   ✅ **`PresetsScreen` hecha y `SlidersPane` retirada (2026-09-09)** — ver "Hecho". Con eso
   **la Fase 1 (lo que se puede hacer sin amplificador) queda cerrada**: las tres pantallas de
   dominio existen, el menú tiene cuatro entradas y no queda ninguna pantalla redundante.
   ✅ **Fase 2 hecha el 2026-09-09**: shell con barra superior (estado de conexión siempre
   visible) y barra inferior, Logs como entrada secundaria, y el estado explícito de "sin
   amplificador" en vez de controles grises — sin adoptar Navigation Compose, por las razones de
   CLAUDE.md §4.2. Ver "Hecho" y "Pendiente por probar", punto 18.
   ✅ **Fase 3 hecha el 2026-09-09**: sistema de diseño propio —chasis oscuro con un acento
   ámbar, paleta/tipografía/espaciado como fuente única en `ui/theme/`— aplicado a las tres
   pantallas, la barra superior y la inferior; `EffectCard` teñida por su **slot de color activo**;
   ícono adaptativo vectorial propio; y el nombre confirmado en «KTNA Control». La dirección y sus
   renuncias, en CLAUDE.md §4.6. Ver "Hecho" y "Pendiente por probar", punto 19.
   ✅ **Fase 4 hecha el 2026-09-09**: los cuatro estados de la Biblioteca (vacía de verdad,
   cargando, fichero que no parsea, bloques `DISPUTED` también en el editor) y la consistencia
   entre los tres diálogos destructivos —`DestructiveConfirmDialog` compartido por "Guardar en
   canal" y "Enviar al amplificador"; "Guardar"/"Guardar como" se queda de un paso, homologado
   solo en el verbo del botón cuando sobrescribe—. La decisión y por qué no se forzó un solo
   componente para los tres, en CLAUDE.md §4.7. Ver "Hecho" y "Pendiente por probar", punto 20.
   ✅ **Fase 5 hecha el 2026-09-09, y con ella se cierran las cinco fases del plan de UI sin
   amplificador**: pase de accesibilidad y calidad mínima sobre el único tema —`contentDescription`
   (la auditoría encontró que la app no tiene ningún ícono real, consecuencia de la Fase 2),
   contraste calculado con la fórmula de WCAG 2 (once pares de la paleta, todos por encima de su
   umbral, ningún color de slot se movió), área táctil (un solo `clickable` hecho a mano, ya no
   depende de un efecto lateral) y escala de fuente (auditado, sin el patrón `.dp`/`.sp` que se
   buscaba). La decisión y los once contrastes, en CLAUDE.md §4.9. Ver "Hecho" y "Pendiente por
   probar", punto 21.
   **Lo que sigue de este bloque es exclusivamente lo que necesita hardware**: probar las
   pantallas contra el amplificador ("Pendiente por probar", puntos 16 a 21). Ya no quedan
   composables que repartir, ni shell que montar, ni tema que definir, ni estados de Biblioteca
   sin cubrir, ni pase de accesibilidad pendiente; lo siguiente es pulir lo que las pruebas
   señalen.

7. **Grabación de audio USB — investigación (2026-09-08)**. Biblioteca de grabaciones
   —grabar, listar, reproducir, renombrar, borrar—, **explícitamente sin edición ni
   multipista**. Investigación documental terminada; **nada implementado**. Conclusión corta:
   **el formato de los datos ya está resuelto y no hace falta deducirlo**, el camino técnico
   existe y no necesita root, pero **exige el primer código nativo del proyecto**. Recomendación
   al final: **adelante, pero condicionado a una prueba de 5 minutos que se puede hacer HOY con
   la API que ya usamos** — ver "La prueba que decide".

   #### 7.1 Las tres referencias con código: ninguna toca las interfaces 1/2 ✅

   Hipótesis **confirmada**: ninguna maneja audio isócrono, todas delegan en el sistema
   operativo. Comprobado por grep de `isochron|libusb|pyusb|usb.core|snd_pcm|ioctl|/dev/bus/usb`
   sobre los tres repos.

   | Repo | Qué hace con el audio | Evidencia |
   | --- | --- | --- |
   | TuxKatana | `sounddevice` (PortAudio) contra el dispositivo **`pulse`** del sistema | `widgets/tuner_dialog.py:11,180-186`; `widgets/settings.py:151-168` |
   | katana-midi-bridge | nada de audio; MIDI por `mido` → rtmidi → ALSA **seq** | `katana.py:1-15` |
   | FxFloorboard | nada de audio; RtMidi con `__LINUX_ALSA__` | `RtMidi.h:71`; `RtMidi.cpp` |

   - ⚠️ **El único uso de pyusb que hay no es lo que parece.** `katana_bridge_start:15,65-77`
     llama a `usb.core.find()` **solo para comprobar que el dispositivo está enchufado** por
     VID/PID, antes de lanzar el puente. No hay `claim`, ni transferencias, ni endpoints. Y
     `show_interfaces` lista **puertos MIDI**, no interfaces USB, pese al nombre.
   - ✅ **En FxFloorboard la separación es medible**: `snd_seq` (secuenciador MIDI) aparece
     **149 veces** en `RtMidi.cpp` y `snd_pcm` (audio PCM) **0**.
   - 🆕 **Dato regalado por el README de TuxKatana** (`README.md:65-73`): lo que ALSA/PulseAudio
     expone del Katana son **cuatro streams `s32le 2ch 44100Hz`** — `Line1`/`Line2` como sinks y
     `Line3`/`Line4` como sources — y dice cuál es cuál: **`Line4` es Direct Capture (seco) y
     `Line3` el procesado por efectos**. Es la primera fuente que documenta la semántica de los
     canales de captura.
   - 🆕 **Y un cabo suelto que se cierra de paso**: `katana_bridge_start:34-35` da
     `katana_vid = 0x0582`, `katana_pids = (0x01d8, 0x0000)`. Coincide con el descriptor real
     (`ID 0582:01d8`), así que el `device_filter` que CLAUDE.md §4.1 deja pendiente de rellenar
     son **1410 / 472 en decimal**.

   #### 7.2 Android: la API pública no puede, y el camino alternativo no necesita root

   ✅ **Confirmado contra `android.jar` de la API 37** (no de memoria): `UsbDeviceConnection`
   expone **únicamente** `bulkTransfer` y `controlTransfer`, y `UsbRequest.queue()` cubre bulk e
   interrupt. No hay ninguna forma de someter una URB isócrona.

   ⚠️ **Ojo con un falso amigo: `UsbConstants.USB_ENDPOINT_XFER_ISOC` SÍ existe.** Permite
   *identificar* un endpoint isócrono con `UsbEndpoint.getType()`, pero no hay nada que lo
   sepa *conducir*. Ver el endpoint y poder leerlo son cosas distintas, y la constante invita a
   pensar lo contrario.

   ❌ **La vía fácil también está cerrada, y por la misma razón que el MIDI (§4.1).**
   `AudioRecord` + `AudioDeviceInfo.TYPE_USB_DEVICE` funciona "sin código USB"… pero
   [Android exige conformidad con USB Audio Class](https://source.android.com/docs/core/audio/usb)
   ("design for audio class compliance; currently Android targets class 1"), y el Katana declara
   `bInterfaceClass 255`. **Es el mismo muro que tumbó `MidiManager`**: no es permisos ni OTG, es
   que el dispositivo no se presenta como lo que Android sabe adoptar.
   ⚠️ **Esto está inferido de la documentación, no probado.** Es trivial de comprobar y está en
   el plan de prueba como paso 0 — si por lo que fuera apareciera, sobra todo lo demás.

   **libusb por NDK es el camino real, y no hace falta root.** El
   [`android/README` de libusb](https://github.com/libusb/libusb/tree/master/android) documenta
   la secuencia: `UsbManager.openDevice()` en Java → pasar el descriptor de fichero al nativo →
   `libusb_wrap_sys_device()`, con `LIBUSB_OPTION_NO_DEVICE_DISCOVERY` para saltarse la
   enumeración. ✅ El `getFileDescriptor()` que hace falta **existe** en `UsbDeviceConnection`
   (verificado en el `android.jar`). El permiso es **el mismo diálogo USB que la app ya pide**.

   ⚠️ **Dos fuentes de libusb se contradicen sobre el root, y gana la nueva.** La
   [FAQ del wiki](https://github.com/libusb/libusb/wiki/FAQ) dice que Android "usually requires
   a 'rooted' device"; el `android/README` del propio repo describe el camino sin root. La FAQ
   está desactualizada —`libusb_wrap_sys_device` es posterior— y el precedente de abajo funciona
   sin root en producción. Se documenta la discrepancia en vez de esconderla.

   **Coste concreto:**

   | | |
   | --- | --- |
   | Permisos | el permiso USB normal. **Sin root.** |
   | Tamaño | ~132 KB por ABI (medido sobre `libusb-1.0.so.0.6.0` local, stripped); ~260 KB con arm64-v8a + armeabi-v7a |
   | minSdk 26 | **no es obstáculo**; libusb apunta mucho más abajo |
   | Coste real | ⚠️ **el primero nativo**: hoy `app/build.gradle.kts` no tiene `externalNativeBuild`, ni `ndk`, ni `jniLibs`. Es una cadena NDK/CMake entera en un proyecto que hoy es Kotlin puro |

   **Precedente: existe el mecanismo, no existe el caso.**
   - ✅ [`saki4510t/UVCCamera`](https://github.com/saki4510t/UVCCamera) hace **transferencias
     isócronas en Android sin root**, con un libusb bifurcado cuyo backend
     `libusb/os/android_usbfs.c` implementa `submit_iso_transfer()` y `handle_iso_completion()`,
     y cuyo `op_set_device_fd()` lleva el comentario que resume todo el asunto: *"native code can
     not open USB device on Android when without root so we need to defer real open/close
     operation to Java code"*. **Pero es vídeo (UVC), no audio.**
   - ❌ **No se encontró ningún proyecto Android open source que capture audio isócrono de un
     dispositivo vendor-specific de Roland/Boss ni equivalente.** El precedente más cercano es
     comercial y cerrado —[USB Audio Recorder PRO de eXtream](https://www.extreamsd.com/index.php/technology/usb-audio-driver),
     que embarca su propio driver USB de audio en espacio de usuario— y **su driver es de
     *clase*** (UAC), así que tampoco cubriría un `bInterfaceClass 255` sin trabajo específico.

   #### 7.3 El formato NO hay que deducirlo: ya está declarado ✅

   🆕 **Hallazgo principal de esta investigación, y cambia el tamaño del problema.** El plan
   preveía deducir sample rate, canales, profundidad y endianness probando con el amplificador.
   **No hace falta: está todo en `~/katana_usb_descriptor.txt`**, el volcado de `lsusb -v` del
   2026-09-01 que ya teníamos.

   Los bloques que `lsusb` imprime como `** UNRECOGNIZED **` en las interfaces 1 y 2 **son
   descriptores UAC1 estándar**. `lsusb` no los decodifica porque solo interpreta `CS_INTERFACE`
   cuando `bInterfaceClass` es AUDIO (1), y aquí es 255 — el mismo disfraz vendor-specific de
   §4.1, y la razón de que en Linux baste un quirk de `snd-usb-audio` para adoptarlas.

   Decodificados a mano (interfaz 2 alt 1 = **captura**, EP `0x8e` IN):

   ```
   07 24 01 07 00 01 00           AS_GENERAL:     bTerminalLink=7, wFormatTag=0x0001 (PCM)
   0b 24 02 01 04 04 18 01 44 ac 00   FORMAT_TYPE_I
               ^^ bNrChannels     = 4 canales
                  ^^ bSubframeSize   = 4 B -> contenedor de 32 bits
                     ^^ bBitResolution = 0x18 = 24 bits reales
                        ^^ bSamFreqType  = 1 frecuencia discreta
                           ^^^^^^^^ tSamFreq = 0x00ac44 = 44100 Hz
   ```

   ✅ **Y la aritmética cuadra exactamente con el endpoint, que es la comprobación cruzada que
   lo convierte en dato y no en lectura optimista**: 4 canales × 4 B = **16 B por frame**;
   `wMaxPacketSize 112 / 16 = 7 frames por paquete`; a High Speed con `bInterval 1` son 8000
   microframes/s y `44100 / 8000 = 5,5125` frames por microframe de media, así que 7 es
   justo el margen que pide un endpoint **Asynchronous**. Los 112 bytes que §4.1 anotaba sin
   explicar quedan explicados.

   - **La interfaz 1 alt 1 (reproducción, EP `0x0d` OUT) declara los mismos bytes de
     `FORMAT_TYPE_I`**, así que entrada y salida comparten formato.
   - **Orden de bytes**: UAC1 PCM es complemento a dos **little-endian**, con los 24 bits
     **justificados a la izquierda** del contenedor de 32. Lo corrobora la propia documentación
     de Android ("24 bits of useful audio data are left-justified within the most significant
     bits of the 32-bit word") y, de forma independiente, que PulseAudio reporte **`s32le`** para
     este amplificador (§7.1).
   - **Caudal**: `44100 × 16 B` = **705 600 B/s** ≈ 0,67 MiB/s → **40,4 MiB por minuto** en crudo
     a 4 canales.

   ⚠️ **Lo único que el descriptor NO dice: qué canal es cuál.** Sabemos que son 4 y que ALSA los
   parte en `Line3` (procesado) y `Line4` (directo), pero **ninguna fuente dice el orden dentro
   del frame de 16 bytes**. Es la única incógnita de formato, y se resuelve de oído en un minuto,
   no investigando.

   ⚠️ **Segunda incógnita, esta sí estructural**: el endpoint de captura está declarado
   `Usage Type: Implicit feedback Data`, o sea que **también hace de realimentación implícita
   para el stream de salida** de la interfaz 1. Ninguna fuente dice si se puede capturar con la
   salida parada. Es el tipo de acoplamiento que solo el hardware aclara.

   #### 7.4 El fichero: WAV a mano, sin ninguna librería ✅ (punto cerrado)

   ✅ **Confirmado por los dos lados, y no hay que reabrirlo:**
   - **Escribir**: una cabecera **RIFF/WAVE son 44 bytes** de campos fijos. No necesita librería
     ninguna — la única cautela es que dos campos llevan tamaños que no se saben hasta el final,
     así que se rellenan al cerrar (o se escribe la cabecera al final).
   - **Reproducir**: `MediaPlayer` de plataforma reproduce WAV sin configuración especial.

   ⚠️ **Pero la profundidad de bits importa, y es una trampa real.** La
   [tabla oficial de formatos](https://developer.android.com/media/platform/supported-formats)
   dice que el decodificador de plataforma cubre **PCM lineal de 8 y 16 bits** (con 44100 Hz
   listado explícitamente); **24 y 32 bits no están**. Media3/ExoPlayer llega a 24, y con 32 hay
   fallos documentados (`Unsupported WAV format type: 3`). O sea: **escribir tal cual los 32 bits
   que da el amplificador es el camino más corto al fichero que la app no puede reproducir.**

   ✅ **Decisión propuesta: guardar WAV de 16 bits, estéreo, 44 100 Hz.** Se reproduce con
   `MediaPlayer` pelado —sin añadir Media3 ni nada—, encaja con el alcance declarado (sin edición
   ni multipista) y ocupa **4× menos**: 10,1 MiB/min en vez de 40,4. La conversión desde lo que da
   el cable es aritmética, sin librería: elegir 2 de los 4 canales y quedarse con los 16 bits
   altos de cada palabra de 32 (desplazamiento a la derecha de 16).
   ⚠️ Eso **descarta el material que no se guarda** (los otros 2 canales y 8 bits de resolución).
   Es deliberado y coherente con el alcance; si algún día se quisiera el crudo íntegro, el sitio
   de la decisión es este párrafo.

   #### 7.5 La prueba que decide (y por qué no hace falta NDK para hacerla)

   🆕 **Se puede falsar la idea entera antes de escribir una línea de código nativo.**
   `claimInterface()` y `setInterface()` **sí están en la API pública** (verificado en el
   `android.jar`); lo único que falta es la transferencia. Así que la pregunta que de verdad
   decide —*¿deja el teléfono siquiera tomar la interfaz de audio?*— se responde con lo que el
   proyecto ya usa.

   **Plan de prueba, en orden de coste creciente. No ejecutado.**

   0. **Descartar la vía fácil**: enchufar el amp y listar `AudioManager.getDevices(GET_DEVICES_INPUTS)`.
      *Esperado*: **no aparece** ningún `TYPE_USB_DEVICE` (§7.2). Si apareciera, todo lo demás
      sobra y la feature es de un día.
   1. **Leer los descriptores desde la propia app** con `UsbDeviceConnection.getRawDescriptors()`
      y comprobar que los bytes de `FORMAT_TYPE_I` son los de §7.3.
      *Esperado*: `04 04 18 01 44 ac 00` tal cual. Confirma que lo que se leyó en el PC es lo que
      el teléfono ve, y de paso valida la decodificación.
   2. **La prueba que decide**: `claimInterface(interfaz 2, force = true)` y después
      `setInterface(alt 1)`.
      *Esperado*: ambas devuelven `true`, **y la interfaz 3 sigue funcionando** (el MIDI no se
      cae). ⚠️ **Si esto falla, el tema se cierra aquí y no cuesta nada**: sin poder activar el
      alt setting 1 no hay endpoint isócrono al que pedirle nada, y ninguna cantidad de libusb lo
      arregla.
   3. Solo si 2 pasa: prototipo nativo con libusb —fuera de la app, un ejecutable de prueba— que
      someta URBs isócronas al EP `0x8e` y vuelque los bytes a fichero.
      *Esperado*: paquetes de 112 B a ~8000/s. Se valida sin oír nada: **si el caudal medido es
      ≈705 600 B/s, el formato de §7.3 es correcto**.
   4. Volcar 10 s a `.wav` de 4 canales y abrirlo en el PC para **resolver el mapeo de canales**
      (§7.3): tocar la guitarra con un efecto muy marcado y ver qué par lo lleva → ese es `Line3`.
   5. Comprobar si la captura funciona con la salida parada (la duda de la realimentación
      implícita).

   #### 7.6 Recomendación: adelante, pero con la puerta condicionada al paso 2

   **No se cierra como el handshake (§4.1), y la diferencia importa.** Aquel se cerró porque era
   **innecesario** —nada de lo implementado lo necesitaba— y porque **no se entendía** por qué
   fallaba. Aquí es al revés: es una feature nueva de verdad, y **sí se entiende** lo que habría
   que hacer, paso a paso. Cerrarlo por analogía sería aplicar la forma de aquella decisión sin
   su fondo.

   Lo que ha cambiado con esta investigación es que **el riesgo se ha movido de sitio**. Se
   temía no saber qué son los bytes; resulta que están declarados y comprobados por aritmética
   (§7.3). Lo que queda no es una incógnita de protocolo sino **una apuesta de plataforma**: que
   el stack USB del teléfono deje hacer isócrono a una app sin root sobre una interfaz clase 255.

   ⚠️ **El coste honesto, que no es el tamaño del `.so`**: son ~260 KB, pero lo caro es que
   introduce **NDK, CMake y un libusb vendorizado** en un proyecto cuyo §6 mantiene la lista de
   dependencias corta a propósito y cuyo valor está en que `protocol/` es Kotlin puro con tests
   JVM. **El streaming isócrono es justamente lo que no se puede testear en JVM ni sin el
   amplificador delante** — sería la primera pieza del proyecto sin red de seguridad.

   Por eso la recomendación es **condicional y barata de ejecutar**:

   - ✅ **Hacer los pasos 0-2 del plan**, que no cuestan ni una dependencia nueva ni código
     nativo, y que **pueden matar la idea en cinco minutos** si el teléfono no cede la interfaz.
   - ⚠️ **Y solo entonces decidir el NDK, como decisión explícita y aparte**, según la regla de
     §6 ("añadir algo requiere justificación explícita"). Que el paso 2 salga bien **no
     autoriza** el paso 3 por inercia: autoriza a plantearlo con un dato en la mano en vez de con
     una esperanza.
   - ❌ **Lo que no conviene es empezar por el libusb.** Es el orden que garantiza descubrir tarde
     y caro lo que el paso 2 dice pronto y gratis — y es exactamente el error que §5 lleva todo
     el proyecto evitando: *un argumento estructural convincente no es una comprobación.*

   **Prioridad sugerida: por detrás del bloque 6 (UI real).** Es una feature ortogonal al
   propósito declarado del proyecto —una alternativa a Boss Tone Studio, que edita presets— y
   ninguno de los bloques 1-6 depende de ella.

### Del QA del 2026-09-09 (CLAUDE.md §4.10)

- ⏸️ **Cablear el bloque SOLO EQ** (`60 00 0F 10`–`0F 19`, ya documentado en `SoloEqParams`).
  ⚠️ **Bloqueado por una decisión de rendimiento, no por falta de datos**: las diez direcciones
  caen fuera del dump, y `loadFromDump` recupera lo que falta con **un GET en serie de hasta
  800 ms por control**. Hoy hay seis controles así (los Contour por slot) y ya cuestan hasta 4,8 s
  por recarga; diez más lo llevarían a ~12,8 s **en cada conexión y cada cambio de canal**. Las
  dos salidas ya identificadas en §5 son **ampliar el rango del dump para cubrir `60 00 0F xx`** o
  **paralelizar los GET de respaldo**. Ninguna se hace antes de saber si esa región contesta
  (ver "Pendiente por probar", punto 7). Hay un test que fija en seis el número de controles fuera
  del dump, así que esto no puede colarse por descuido.
- ⏸️ **Extraer el bloque SOLO DELAY** (`60 00 0F 1A`–`0F 25`, `midi.xml:50021-50048`): Delay Sw,
  Delay Time (2 bytes), Feedback, Effect, Direct, Filter, High Cut, Modulation, Rate y Depth.
  Localizado al investigar el SOLO EQ; no se extrajo porque el encargo era el EQ y anotarlo a
  medias sería peor que anotar dónde está. Con él, `UserPatch%Patch_Mk2V2` queda desglosado entero.

## Notas y decisiones técnicas

- **2026-09-09 — Fase 5: por qué "cero íconos" es la respuesta al punto 1, y por qué ningún
  color de slot se movió en el punto 2.**
  - **El barrido de `contentDescription` fue un barrido, no una suposición.** Antes de tocar
    nada se hizo grep de `Icon(`, `IconButton(`, `Image(` y `contentDescription` en todo `ui/` —
    cero coincidencias en las cuatro. No es que la app tuviera pocos íconos sin describir: no
    tiene ninguno, porque la Fase 2 ya había evitado `material-icons-core` sustituyendo cada
    pictograma por su palabra en `Text`. El trabajo de este punto se redujo a confirmar eso con
    datos y a marcar decorativo lo único que sí es puramente visual —el punto de color de
    `EffectCard`— en vez de darle una descripción redundante con el texto que ya tiene al lado.
  - ⚠️ **El contraste se calculó con la fórmula real de WCAG 2, no con una tabla de "colores
    seguros" ni a ojo** (`contrastRatio`, `ui/theme/Contrast.kt`): luminancia relativa por canal
    más `(L1+0.05)/(L2+0.05)`, la misma que usa cualquier verificador externo. Se aplicó a los
    once pares que de verdad aparecen en la app —no a combinaciones hipotéticas— y los once
    superan su umbral, con el rojo de slot como el más ajustado (`5.01:1` contra el `3:1` que le
    toca por ser franja+punto y no texto). **La instrucción explícita era frenar y documentar si
    algún ajuste obligaba a mover un color de slot fuera de lo fijado como hecho del
    dispositivo en la Fase 3 — no hizo falta, y se documenta igual que se habría documentado el
    conflicto**: los tres colores ya se habían elegido por legibilidad sobre grafito, y este
    cálculo lo confirma con un número en vez de dejarlo en la intuición de quien los eligió.
  - **`EffectSlotColors.Unknown` queda fuera de la tabla de cumplimiento a propósito, no por
    descuido**: da `1.67:1`, por debajo del umbral de componente, pero es el gris que se pinta
    cuando el amplificador todavía no dijo de qué color está el slot — su bajo contraste es la
    señal correcta, no un fallo. WCAG 1.4.11 exige contraste a lo que transmite información, y
    `Unknown` transmite la ausencia de ella.
  - **El único `Modifier.clickable` hecho a mano de todo el proyecto pasó de "cumple por
    casualidad" a "cumple por diseño"**: antes su altura mínima dependía de que el `TextButton`
    de al lado lo empujara a 48 dp con la garantía de Material 3; ahora lleva
    `Modifier.heightIn(min = 48.dp)` explícito, así que seguirá cumpliendo aunque cambie lo que
    hay al lado. Todo lo demás de la app ya cumplía por los componentes de fábrica de Material 3
    (`LocalMinimumInteractiveComponentEnforcement`, activado por defecto y nunca desactivado en
    este proyecto — comprobado por grep).

- **2026-09-09 — Fase 4: por qué solo dos de los tres diálogos comparten componente, y qué
  fallo real corrigen los estados de la Biblioteca.**
  - **La pregunta no era "¿los tres se ven parecido?", era "¿los tres tienen la misma forma?".**
    Guardar en canal y enviar al amplificador sí: un paso que junta datos y un paso que solo
    confirma. Guardar/Guardar como no: es un único `AlertDialog` con el nombre y el aviso juntos.
    Se extrajo `DestructiveConfirmDialog` para los dos que coinciden, y se dejó el tercero como
    estaba —homologando solo el verbo del botón ("Sí, sobrescribir") cuando de verdad pisa algo—
    en vez de forzarle un segundo paso que no tenía. Añadir un paso a un diálogo que no lo
    necesitaba habría sido la clase de cambio de comportamiento que la tarea pedía evitar por
    conseguir una consistencia que no hacía falta.
  - ⚠️ **El fallo que corrigen los estados no era cosmético: era una ambigüedad real.**
    `entries.isEmpty()` decía lo mismo en dos situaciones distintas —"todavía no leí nada" y "leí
    y no hay nada"— así que el mensaje "Biblioteca vacía" salía, aunque brevemente, siendo falso.
    `LibraryListState` (`Loading`/`Empty`/`Loaded`) es la misma idea que `ShellState.availabilityOf`
    aplicada a ficheros en vez de al amplificador: una función pura, con tests JVM, que decide
    qué enseñar a partir de lo que expone el ViewModel — la Composable no mira `loading` a pelo.
  - **`loading` no gana si ya hay entradas**, a propósito: una recarga en segundo plano no debe
    hacer parpadear una lista que ya tenía contenido real, porque la lista de verdad no
    desaparece en ningún momento — parpadear ahí mentiría más que no decir nada.
  - **Los bloques `DISPUTED` ya tenían dos sitios donde enseñarse** (la vista de solo lectura y
    la revisión previa al envío) **y les faltaba el tercero, el editor**, que es justo donde más
    tiempo pasa quien está editando un preset real. Reutilizar `UnavailableSection` en los tres
    sitios es la misma regla de siempre: lo que se comparte de verdad se extrae una vez, no se
    reescribe donde haga falta.

- **2026-09-09 — Fase 3: por qué chasis oscuro, y qué parte del color de un efecto es un hecho.**
  - **La dirección visual la decidió el color de los efectos, no el gusto.** Cada efecto tiene tres
    slots —verde/rojo/amarillo— y eso lo enciende el propio panel: es **hecho del dispositivo**.
    Para que esos tres tonos se lean como señal necesitan un fondo neutro. La Opción B (Material 3
    sembrado con ámbar) tiñe **todas** las superficies de marrón-ámbar, y un punto amarillo sobre
    ámbar deja de ser un punto amarillo. Grafito es el único fondo que no compite con los tres.
  - ⚠️ **La app pasa a ser siempre oscura y sin color dinámico, y las dos son renuncias.** Quien
    tenga el teléfono en claro verá esta app oscura igual. A cambio: los colores de slot solo hay
    que afinarlos contra un fondo, y ningún wallpaper puede meter una superficie amarillenta debajo
    de un slot amarillo. `dynamicColor` se **retira**, no se pone en `false` — dejar el parámetro
    es dejar la puerta.
  - ⚠️ **El acento naranja cae entre el rojo y el amarillo de los slots, y eso no tiene arreglo por
    tono**: cualquier naranja lo hace. Se resuelve **por la forma** — el acento rellena controles,
    el slot es una franja de borde y un punto — y sobre todo porque **el slot siempre lleva su
    nombre escrito al lado**. El color es refuerzo, nunca la única vía. Lo mismo con `error`, que
    comparte tono con el slot rojo y por eso solo tiñe texto.
  - ✅ **La correspondencia valor→color estaba en duda y se cerró con un fichero real.**
    `midi.xml` dice **las dos cosas**: `00` = RED en la tabla del propio parámetro (`:43961`) y en
    su bloque de conversión (`:50863`), pero etiqueta el primer slot de tipo (`06 24`) como
    **GREEN** (`:43567`). Cruzándolo con el modelo de §5.2 —el tipo activo refleja el slot
    encendido— las dos afirmaciones no pueden ser ciertas a la vez.
    El desempate: en `default_mk2.tsl` los cinco efectos tienen color `00`, y en **los cinco** el
    tipo activo coincide con el del **primer** slot, con los otros dos en valores distintos. **5 de
    5**, contra 3 candidatos cada uno. Más dos testigos independientes (`Adresses.txt:72-74` y
    `color_assign.json`). **`EffectColor` ya era correcto**; lo que gana es una justificación.
    ⚠️ Queda **una mirada al panel** ("Pendiente por probar", punto 19); si estuviera al revés, el
    arreglo son tres constantes.
  - **Lo que es hecho y lo que es elección, separado a propósito**: hecho = qué valor es cada
    color; elección = los hex (`#3ECF5C` / `#FF3B30` / `#FFD426`). **Ninguna fuente dice qué verde
    enciende el LED**, ni la habría — es luz, no un `#RRGGBB`.
  - ⚠️ **Un slot que la app no reconoce no se pinta de ningún color**, cae en gris de borde. Es la
    misma regla de `KatanaEnumParameter` (§4.3) llevada a lo visual: adivinar un color sería
    enseñar como confirmado algo que el amplificador no ha dicho. Y es el caso normal antes del
    primer dump.
  - **`Spacing` no se propagó a todo el proyecto, y es decisión.** De los ~108 literales `.dp`, la
    mayoría son **tamaños de componente** —el diámetro de una perilla, la altura de una barra de
    EQ—, que no son espaciado. Una escala de espaciado que absorba tamaños deja de significar algo.
  - **El ícono repite el gesto del código**: 270° desde 135°, los mismos `KNOB_START_DEGREES` /
    `KNOB_SWEEP_DEGREES` que dibuja `KnobDial`. Y capa monocroma aparte, porque el reteñido de los
    íconos temáticos convertiría la de delante en un disco liso.
  - **El nombre no se "mejora" a «Katana Control»**: Katana es marca de Boss y esta app no es
    oficial (§1). Ponerle el nombre del producto a una app de terceros invita a confundirla.

- **2026-09-09 — Fase 2: por qué no entró Navigation Compose, y qué se hizo con los cambios sin
  guardar.**
  - **La opción con `NavHost` se descartó por un choque concreto con la arquitectura, no por
    pereza.** Su ventaja era plantar el detalle/editor de la Biblioteca como ruta con back del
    sistema; pero eso exige que el destino sea **dato**, y aquí es un objeto vivo: la
    `EditingSession` posee un `KatanaRepository` sobre `OfflineKatanaLink` atado a
    `viewModelScope` (§4.5). O se duplica la verdad (ruta + ViewModel), o el `NavController` queda
    de adorno sobre el mismo `when`.
  - ⚠️ **Y el pie de banco que lo remató**: `viewModel()` dentro de un `NavBackStackEntry` se
    scopea por ruta. La lista y el editor tendrían `LibraryViewModel` distintos salvo scoping
    explícito al padre — un fallo silencioso ("a veces se pierde lo que estaba editando") en el
    sitio exacto donde compartir es el punto.
  - **Lo que sí se copió**: el back de una barra inferior (`popUpTo(startDestination)`), tres
    ramas escritas a mano **en Kotlin puro con tests**, en vez de dentro de un composable donde no
    se podrían probar.
  - **Cambios sin guardar al navegar: se conservan.** No hubo que implementarlo —la sesión ya
    vivía en el ViewModel por otra razón (§4.5)— pero sí **decidirlo y escribirlo**, porque
    "funciona por casualidad" y "es el comportamiento elegido" se parecen mucho hasta que alguien
    refactoriza. Preservar en silencio es lo que menos sorprende; avisar al cambiar de pestaña
    convertiría un gesto barato en un diálogo.
  - **El corolario que sí hubo que implementar**: "Volver" desde el editor era el único camino que
    borraba trabajo, y lo hacía sin preguntar. Con el resto de la app preservando, eso era
    incoherente. Ahora pregunta **solo si hay cambios** — un diálogo que sale siempre deja de
    leerse.
  - **`canEdit` tenía tres copias.** Centralizarlo en `ShellState.availabilityOf` no es cosmética:
    es la condición que decide si se puede escribir en el amplificador, y tres copias son tres
    sitios donde olvidar una condición nueva. El test que **fija la equivalencia con la fórmula
    vieja** es el que permite centralizar sin cambiarle el significado por accidente.
  - **Sin iconos en la barra inferior.** `material-icons-core` no está en el classpath; añadir una
    dependencia por tres pictogramas contradice la regla de §6, y una barra de tres entradas con
    el nombre escrito se lee igual de bien —mejor que un icono que haya que adivinar—. Si algún
    día la barra crece, se reabre.
  - **Logs sigue entero, solo deja de competir.** Degradarlo a "Avanzado" en la barra superior no
    le quita nada: es la pantalla de diagnóstico que ha resuelto media investigación de este
    proyecto, y ahí sigue. Lo que cambia es que quien abre la app aterriza en el amplificador.

- **2026-09-09 — Cierre de la Fase 1: borrar `SlidersPane` salió más barato que conservarla, y la
  razón es que el código ya la había vaciado.**
  - **La decisión no fue de gusto sino de constatación.** Al mirar el cuerpo de `SlidersPane` tras
    las dos extracciones anteriores, su rama offline eran tres llamadas y **todo lo demás estaba
    tras `if (!offline)`**. Conservarla "solo para el editor" habría dejado un booleano
    permanentemente en `true` con la mitad del cuerpo muerto y sin forma de notarlo.
  - **El argumento que desempató fue el peaje del llamador.** Para editar un fichero había que
    pasarle `state = UsbConnectionState.Idle`, `editMode = false`, `onSavePreset = { _, _ -> }`,
    un `OFFLINE_DIAGNOSTICS` entero de callbacks que nunca se llaman y una docena de
    `onRead… = {}`. Argumentos inertes cuyo único motivo era satisfacer a la otra mitad. Llamando
    a los tres composables directamente, desaparecen — **el borrado deja menos código, no más**.
  - ⚠️ **Lo que casi se pierde en el borrado: el scroll.** `SlidersPane` ponía
    `knobAwareVerticalScroll()` en su Column exterior, así que el editor lo heredaba sin que
    nadie lo pensara. `PresetEditorBody` reproduce esa Column entera a propósito. Es el riesgo
    típico de borrar un envoltorio: lo que se pierde no es lo que la función *hacía*, es lo que
    aportaba **su contenedor**.
  - **Y otro de layout, del lado nuevo**: `LibraryPane` es `fillMaxSize()`, y dentro de una Column
    con un encabezado encima eso pide la altura **de la pantalla**, no la que queda libre — el
    final de la lista se habría salido por abajo. Va con `weight(1f)`.
  - **La separación "en vivo" / "Biblioteca" no se resolvió con estética.** La señal que de verdad
    distingue las dos mitades es que **tienen reglas de habilitación distintas y honestas**:
    arriba se apaga sin cable, abajo funciona desenchufada. Los encabezados y el divisor ayudan a
    leerlo, pero lo que impide confundirlas es que se comportan distinto porque *son* distintas.
    La cuarta señal —abrir un preset esconde el bloque en vivo— es la que evita el caso feo:
    "Guardar preset en el amplificador" flotando encima de un editor de fichero.
  - **Qué NO se llevó `PresetsScreen`**: "Releer", porque repuebla lo que enseñan las otras dos
    pantallas y aquí no hay controles; y el selector de canal y el resto de parámetros, que son de
    `AmpScreen`. La regla de las tres pantallas se mantiene: cada una recibe **solo lo suyo**.
  - **Balance de la Fase 1, en tres extracciones seguidas sin tocar un test**: `AmpScreen` fijó el
    patrón, `EffectsScreen` demostró que generalizaba, y `PresetsScreen` lo cerró **borrando** la
    pantalla que las tres sustituyen. Que las tres veces la señal fuera la misma —ningún test
    existente hizo falta cambiarlo— es lo que permite dar por buena la reutilización sin
    dispositivo delante.

- **2026-09-08 — `EffectsScreen`: la segunda vez que se aplica el patrón de `AmpScreen` no dejó
  ninguna decisión nueva que tomar, y eso en sí es la nota.**
  - **No hubo que decidir dónde vive la lógica de reparto** porque ya existía: `EffectId.entries`
    llevaba desde el principio siendo la lista canónica de "qué es un efecto" (es lo que hacía
    que `AmpDomain.LEVELS` fuera `LevelId.entries - efectos` y no una lista a mano), y
    `AmpDomain.EFFECT_SELECTORS` ya agrupaba los tres cortes de frecuencia de Delay/Reverb. La
    instrucción de "que sea una resta sobre lo que ya clasificó `AmpDomain`, no una lista nueva"
    resultó ser exactamente lo que ya había — la señal de que `AmpDomain` se diseñó bien la
    primera vez es que la segunda pantalla no necesitó tocarlo.
  - **`EffectsDomainTest` es la comprobación simétrica, no una repetición.** `AmpDomainTest` ya
    prueba la cobertura completa (cada `SelectorId` en un sitio, los niveles sin solape); lo que
    faltaba era una prueba que fallara **si `EffectsScreen` empezara a pintar algo del
    amplificador** — la misma protección que `AmpDomainTest` da del lado contrario. Las cuatro
    pruebas nuevas comprueban eso, no vuelven a comprobar la cobertura.
  - **`EffectsScreen` no tiene la excepción de Edit Mode que sí tiene `AmpScreen`.** El canal es
    el único control que se salta el contrato (§4.2), y el canal es del amplificador — no hay
    ningún control de efecto en esa situación, así que `EffectsScreen` es `canEdit` de principio
    a fin, sin ninguna rama especial.
  - **La comprobación de que la reutilización sigue intacta ya no es solo argumental**: dos
    extracciones seguidas (`AmpScreen` y `EffectsScreen`) sin tocar un solo test existente de
    `SlidersPane` o del editor offline es más señal que una — es que el patrón generaliza, no que
    la primera vez tuvo suerte.

- **2026-09-08 — Arranque del bloque 6: qué se extrajo, qué no, y dónde acabó la cadena.**
  - **La arquitectura elegida y por qué** está en CLAUDE.md §4.2 con el detalle; el resumen es
    que **no hizo falta elegir entre "duplicar" y "congelar"** porque el código ya estaba partido
    por donde había que partirlo: todo lo que no es la sección de efectos es dominio de
    amplificador, y `NoPanelPane` ya era un composable aparte. La extracción fue **un bloque
    contiguo**, no una cirugía transversal.
  - **El criterio que decidió el resto: no reordenar `SlidersPane`.** Se podía haber juntado
    todo el dominio de amplificador de un tirón y habría quedado más "limpio"; habría movido
    Noise Gate, Contour, EQ y cadena por delante de los efectos en la pantalla que el usuario ya
    usa y en el editor offline. Un refactor que reordena una UI que funciona hace dos cosas a la
    vez y luego no se sabe cuál rompió qué. `AmpSection` se llama **en el sitio exacto** donde
    estaba su código.
  - ⚠️ **La cadena de efectos se queda en `AmpScreen`.** El criterio: **una pantalla de dominio
    necesita un dominio detrás**, y la cadena hoy son cuatro selectores de enrutado (tipo de
    cadena, posición del loop, de EQ1 y del Pedal/FX) más un diagrama de solo lectura — porque el
    reordenado manual, que era lo único que la habría hecho una pantalla de verdad, **se retiró
    el 2026-09-06 tras probarlo contra el amplificador**. Además, lo que enrutan es la señal *del
    amplificador*, no la de ningún efecto concreto. Si algún día `EffectsScreen` necesita
    reordenar la cadena, se mueve; hoy sería una entrada de menú con un selector y un dibujo.
  - **`AmpDomain` existe porque el olvido es silencioso.** Cablear un parámetro nuevo y no
    pintarlo en ninguna pantalla **no falla por ningún lado**: el control existe, se puebla desde
    el dump, y no se ve. Escribir el reparto como dato permite un test que exige dueño para cada
    `SelectorId` — es la misma idea del `AMP_LEVELS = LevelId.entries - efectos` que ya existía
    (una resta, no una lista a mano), extendida a lo demás y comprobada.
  - **Bright y Gain SW se marcan `RETIRED`, no se omiten.** Si simplemente faltaran de las
    listas, el test de cobertura no podría distinguir "probado y sin efecto" de "se olvidaron".
  - **Qué NO se llevó `AmpScreen`, y por qué**: guardar en canal y exportar a `.tsl` son
    operaciones sobre el preset entero. Repetirlas en cada pantalla de dominio significaría el
    mismo botón destructivo en cuatro sitios; esperan a `PresetsScreen`. "Releer" sí se lleva,
    porque no es destructivo y repuebla exactamente lo que la pantalla enseña.
  - **`AmpScreen` calcula su propio `canEdit`**, igual que `SlidersPane`, en vez de recibirlo.
    El gate de Edit Mode sigue viviendo **solo en la UI** (§4.2): `device/` no sabe qué es, y una
    pantalla nueva no es motivo para mover esa frontera.

- **2026-09-08 — El botón de enviar un preset: dónde vive la lógica, y qué se le dice al usuario
  cuando la cosa sale mal.** Ninguna de estas tres decisiones tiene un dato detrás; son criterio,
  y por eso van escritas.
  - **La lógica se separó en `ui/screens/PresetSendFlow.kt`, Kotlin puro.** El criterio para
    trazar la línea fue: *lo único que un test JVM no puede ejercitar es el dedo sobre la
    pantalla*, así que todo lo demás sale del composable. Queda dentro solo pintar el estado.
    El sitio no es nuevo: `PresetEditor` y `knobValueAt` ya viven en `ui/screens` con tests JVM.
    El flujo recibe el envío como lambda (`suspend (MemoryImage) -> PresetSendResult?`), que es
    lo que permite probarlo entero sin repositorio, sin USB y sin Compose.
  - **`null` en esa lambda significa "no hay amplificador", y es un caso distinto de un fallo.**
    Sin cable no salió nada y el amplificador está intacto; un fallo a mitad lo deja con el
    preset a medias. Meterlos en el mismo saco habría hecho imposible escribir el mensaje
    correcto para cada uno.
  - **Cancelar produce un resultado visible, no un cierre en silencio.** "Cancelado: no se mandó
    nada al amplificador." Cerrar el diálogo sin más habría sido más limpio de programar y deja
    al usuario preguntándose si le dio al botón antes de arrepentirse — y con una operación
    destructiva esa duda no es gratis. **Cancelar un envío ya en curso no existe**: los mensajes
    que salieron no se pueden devolver, y parar a la mitad dejaría al amplificador con un preset
    incompleto sin que nadie lo haya pedido.
  - **El mensaje del corte a mitad, que era la pregunta explícita: nunca dice que nada "se
    guardara bien".** Son dos textos distintos porque son dos situaciones distintas:
    - **El cable falla** (`link.send` devuelve false): *"El envío se cortó — mensajes enviados: N
      de M. ⚠️ No se guardó nada. El amplificador ha quedado con una mezcla del preset anterior y
      de este. Vuelve a enviarlo, o cambia de canal en el amplificador para que recargue su
      preset guardado."* — más la línea de en qué bloque y trozo paró. Se puede ser preciso
      porque `sendPreset` corta en seco y lleva la cuenta.
    - **El USB desaparece** (excepción): *"⚠️ No se sabe cuántos mensajes llegaron…"*. Aquí la
      excepción se lleva por delante la cuenta, y **inventar un número sería peor que admitir que
      no se sabe**. La salida sugerida es la misma: cambiar de canal, que hace que el
      amplificador recargue su preset guardado y deshaga la mezcla.
    En los dos casos se dice además que **no queda guardado en ningún canal**, porque
    `sendPreset` solo llena el búfer de edición y esa confusión es fácil de tener.
  - **Y el mensaje del envío completo tampoco afirma éxito**: dice cuántos mensajes salieron y
    acto seguido que *"el amplificador no confirma un SET, así que esto no prueba que los
    aceptara"*. Es la misma regla que ya rige `PresetSaveResult.verdict`.
  - **Los textos van en `strings.xml` y el flujo devuelve estructura, no prosa.** Así los tests
    afirman sobre el tipo de resultado y sus datos —no sobre una frase que cualquiera puede
    reescribir— y la copy sigue donde manda CLAUDE.md §6. La traducción de resultado a frase es
    un único `when` en `LibraryPane`, que es lo que no se puede probar sin dispositivo.

- **2026-09-08 — Tamaño de trozo (128 bytes) y margen entre trozos (~30 ms) al mandar un preset:
  las dos son decisiones propias, y conviene saber cuánta evidencia hay detrás de cada una.**
  - **128 bytes de datos por SET.** Ninguna fuente dice cuál es el máximo que acepta el
    amplificador — sigue siendo TBD. Lo que sí hay es **el único tamaño de SET masivo que se
    observa en una fuente de Mk2**: el volcado de patch de
    `reference/FxFloorboard/sysxWriter.cpp:377-390` es una tira de mensajes de 128 bytes de datos
    (12 de cabecera + 128 + checksum + `F7` = 142). Si el editor de PC parte ahí, 128 es el
    tamaño del que consta que alguien habla así con este amplificador. Es un precedente, no una
    medida.
  - ❌ **Y el argumento que había en CLAUDE.md para trocear era aritméticamente falso.** Decía
    que un SET de 221 bytes "no cabe en un solo paquete USB de 512 B una vez empaquetado en
    tramas de 4 bytes (≈300+ en el cable)". La cuenta real: `14 + 221 = 235` bytes de mensaje,
    `ceil(235/3) = 79` paquetes de 4 bytes = **316 bytes**, contra 512 de `wMaxPacketSize`.
    **Cabe.** Así que trocear **no lo obliga el transporte**; se hace por no ser el primero en
    probar si el amplificador digiere 221 bytes de una sentada, que es una razón más floja y hay
    que decirlo como tal. La corrección está en CLAUDE.md §5.
    Es otra vuelta de la lección de siempre: un argumento estructural convincente no es una
    comprobación, y este ni siquiera necesitaba hardware para caerse — bastaba hacer la división.
  - **~30 ms entre mensajes.** Tampoco hay fuente. La analogía es **la única cadencia que este
    proyecto ha medido** contra el hardware: el hueco de ~30 ms entre los mensajes con que el
    propio amplificador contesta un dump (§4.4). Si ese es el ritmo al que él habla, es un ritmo
    razonable al que hablarle. Coste: ~0,6 s por preset completo, que para una operación
    destructiva de una sola vez no es nada.
  - **Por qué el troceado vive en `RolandSysEx` y no en `TslTransfer`.** Partir un SET largo es
    aritmética de direcciones, no algo del formato `.tsl`: cualquier otro bloque grande que
    aparezca (un `Patch_Mk2V2` ampliado, un futuro dump escribible) lo necesita igual. `.tsl` es
    hoy el único cliente, pero no es su dueño.
  - **Por qué `sendPreset` no actualiza la caché de controles.** Sería gratis y quedaría bonito
    en la UI, y es exactamente lo que no hay que hacer: enseñaría **lo que se pidió**, no lo que
    el amplificador aceptó, y un SET no se confirma. Quien quiera saber qué entró tiene que
    releer con `loadFromDump` y comparar — que además es la forma de probar todo esto (ver
    "Pendiente por probar", punto 15).

- **2026-09-01 — El Katana MK2 NO es un dispositivo USB MIDI class-compliant.** Confirmado
  con `lsusb -v` sobre el amplificador real, después de que la app no detectara nada: el
  dispositivo entero se enumera con `bDeviceClass 255` (Vendor Specific) y ninguna de sus 4
  interfaces usa las clases Audio / MIDIStreaming estándar. Por tanto **`MidiManager` nunca
  lo detectará**, en ningún Android ni con ningún kernel. Transporte real: **interfaz 3**
  (`bInterfaceClass 255`, `bInterfaceSubClass 3`, `bInterfaceProtocol 0`), **alternate
  setting 0**, endpoint **`0x03` Bulk OUT** para enviar y **`0x84` Bulk IN** para recibir,
  `wMaxPacketSize` **512 bytes**. Las interfaces 1 y 2 son de audio isócrono (44100 Hz) y no
  intervienen; la interfaz 0 no tiene endpoints de datos. Detalle completo en CLAUDE.md §4.1.
  - **Consecuencia**: la arquitectura pasa de `android.media.midi` a
    `android.hardware.usb.UsbManager` + `claimInterface(3)` + `bulkTransfer()`. El paquete
    `midi/` queda como implementación descartada; el protocolo SysEx de §5 no cambia.
  - **Por qué TuxKatana y katana-midi-bridge sí funcionan**: en Linux `snd-usb-audio` trae
    quirks para Roland/Boss que exponen esas interfaces vendor-specific como puertos ALSA
    MIDI. Android no hace esa traducción. Las referencias siguen valiendo para el protocolo,
    no para el transporte.
  - ~~Pendiente de confirmar: formato en el cable.~~ **RESUELTO el 2026-09-02**, ver abajo.
- **2026-09-02 — RESUELTO: el formato en el cable son paquetes USB-MIDI de 4 bytes.**
  Fuente: [MrHaroldA/MS3](https://github.com/MrHaroldA/MS3) (Arduino + USB Host Shield,
  controla el Boss MS-3 y el Katana), fichero `MS3.h`: el manejo de paquetes en `receive()`
  y la función `setEditorMode()`. Aunque la interfaz sea vendor-specific, los datos sobre
  `0x03` / `0x84` van empaquetados en el formato USB-MIDI Class estándar:
  `byte 0 = (cable << 4) | CIN`, `bytes 1-3` = hasta 3 bytes MIDI reales rellenados con
  `0x00`. Un SysEx se trocea de 3 en 3, con CIN `0x4` para los paquetes intermedios
  ("empieza/continúa") y `0x5` / `0x6` / `0x7` para el último según lleve 1, 2 o 3 bytes.
  Es capa de transporte: `protocol/` sigue viendo mensajes `F0…F7` limpios.
- **2026-09-02 — Handshake obligatorio antes de cualquier comando.** Misma fuente
  (`setEditorMode()`): el amplificador no responde a nada hasta recibir **dos veces
  seguidas**, con ~4 ms de delay entre medio, la trama fija
  `F0 7E 00 06 02 41 3B 03 00 00 00 00 00 00 F7`. No es el Identity Request universal
  (`F0 7E 7F 06 01 F7`), que probamos como primer contacto sin éxito; ese sigue valiendo,
  pero *después* del handshake.
  - ⚠️ **A verificar**: la trama tiene forma de Identity *Reply* y `3B` es el model id del
    MS-3; el del Katana es `33`. Si el handshake literal no funciona, probar con `33`.
- **2026-09-02 — kshoji/USB-MIDI-Driver: sigue pendiente de decidir, pero ya hay datos.**
  El dato que bloqueaba la decisión (formato en el cable) está resuelto. Recomendación:
  **no usarla** — empaquetar/desempaquetar 4 bytes son unas decenas de líneas, y el
  handshake es específico de Boss, así que ninguna librería genérica lo cubre. Falta que se
  confirme explícitamente, como pide CLAUDE.md §6.
- **Decisión pendiente**: usar o no [kshoji/USB-MIDI-Driver](https://github.com/kshoji/USB-MIDI-Driver).
  La vieja regla "sin librerías MIDI de terceros" se apoyaba en que `android.media.midi`
  cubría el caso, y ya no lo cubre, así que hay que decidirla de nuevo a propósito en vez de
  heredarla. Pros y contras en CLAUDE.md §6; mientras tanto se implementa a mano.
- **2026-09-02 — Bug corregido: el log decía "interfaces: 7" y el descriptor dice 4.**
  Confirmada la hipótesis: `UsbDevice.getInterfaceCount()` **no** devuelve `bNumInterfaces`,
  sino un `UsbInterface` por cada par (interfaz, alternate setting). Contrastado contra
  `~/katana_usb_descriptor.txt`, que tiene `bNumInterfaces 4` y 7 descriptores de interfaz:
  iface 0 (1 alt), iface 1 (2 alts), iface 2 (2 alts), iface 3 (2 alts) = 7. El conteo real
  se obtiene quedándose con los `id` distintos; el log ahora muestra ambos números
  (`interfaces: 4 (7 alt settings)`), que para diagnóstico es más útil que solo el correcto.
  - Efecto colateral útil: el descriptor confirma que **la interfaz 3 alt 1 expone los
    mismos endpoints como interrupt y con IN en `0x85`, no `0x84`**. `KatanaUsbTransport`
    ya filtraba por `id == 3 && alternateSetting == 0`, así que estaba bien; pero filtrar
    solo por `id` habría cogido a veces el alt 1 y `findEndpoint(0x84)` habría fallado.
  - También matiza lo que decíamos de las interfaces 1 y 2: son isócronas (audio, 112 bytes
    por paquete) pero **vendor-specific**, no audio class — coherente con que ninguna
    interfaz del dispositivo use clases estándar.
- **2026-09-02 — Duda abierta (baja prioridad, NO bloqueante): el handshake no responde.**
  Con model id `0x33`, los dos envíos y la lectura posterior de 1 s no devolvieron nada. No
  es fallo del transporte: en la misma sesión el Identity Request sí respondió. Hipótesis:
  el handshake **activa el modo de edición en el amp sin confirmar por SysEx**, a diferencia
  del Identity Request, que es una consulta con respuesta esperada. Sólo se puede comprobar
  de forma indirecta, viendo si las lecturas de parámetros cambian de comportamiento con y
  sin él — así que se retoma cuando haya lecturas reales (punto 1 de "Por hacer").
  - No se llegó a probar `0x3B` (MS-3), porque `0x33` no falló de forma que lo justificara:
    no hubo error, simplemente silencio, que es lo que la hipótesis predice.
  - Pista para más adelante: nuestra trama difiere del Identity Reply real del amp en **un
    solo byte**, el de versión de firmware (`00` donde el amp dice `06`). Ver CLAUDE.md §4.1.
- **2026-09-02 — ⚠️ Verificar en la próxima ejecución: inconsistencia en el log transcrito.**
  En la transcripción de la prueba, las líneas `wire` y `desempaquetado` del Identity Reply
  no son coherentes entre sí. El wire transcrito era
  `04 F0 7E 00 | 04 06 02 41 | 04 33 03 00 | 00 06 00 00 | 07 00 00 F7`: su cuarto paquete
  empieza por `00`, o sea CIN `0x0`, que no lleva payload, así que `unpackUsbMidi` habría
  producido 12 bytes (`F0 7E 00 06 02 41 33 03 00 00 00 F7`) y no los 15 que muestra la
  línea de desempaquetado. Para dar esos 15 bytes, ese paquete tendría que ser `04 00 06 00`.
  - Lo más probable con diferencia es un desliz al copiar el log a mano: la app calcula una
    línea a partir de la otra en el mismo código, así que una ejecución real no puede
    producirlas incoherentes.
  - Pero **si el amplificador realmente enviara paquetes con CIN `0x0`**, `unpackUsbMidi`
    estaría descartando bytes en silencio, y eso sí sería un bug. Confirmar mirando el log
    en pantalla la próxima vez que se envíe un Identity Request.
  - No afecta a la conclusión: la respuesta de 15 bytes coincide exactamente con el Identity
    Reply documentado en HOW.md, así que el hito se sostiene igual.
- **2026-09-02 — Un SET (`0x12`) no se confirma: es fire-and-forget.** Comprobado en dos
  fuentes independientes de `reference/`, no supuesto:
  - `TuxKatana/HOW.md` traza el write de edit mode con solo la flecha de envío (`◀`), sin la
    de respuesta (`▶`) que sí llevan todos los GET de esa misma secuencia.
  - `TuxKatana/lib/controller.py`: `set_edit_mode()` envía y sigue, mientras que `get_name()`
    y `get_presets()` llaman a `wait_msg()`.

  Por eso el Edit Mode **no** usa `awaitRolandReply` (no hay nada que correlacionar por
  dirección) sino `sendAndCollect`, que solo observa. Silencio tras el SET es el resultado
  esperado, no un fallo.
- **2026-09-02 — Edit Mode va en el flujo normal de conexión, no solo en diagnóstico.**
  Confirmado que es lo único que hace que el amp reporte cambios derivados (perillas
  físicas, parámetros que se mueven solos), así que sin activarlo la UI final nunca podría
  reflejar lo que se toca en el propio amplificador. Se activará al conectar, respetando lo
  que ya pedía CLAUDE.md §4.2: ajuste **explícito y visible**, con forma de desactivarlo,
  nunca silencioso — altera el estado del amp.
- **2026-09-02 — Dos hipótesis descartadas para el nivel de reverb, ambas por prueba con el
  amplificador, no por lectura de documentación:**
  - `60 00 06 18` (mapa de `katana_sysex.txt`, que documenta el **MK1** de 2017): **ningún**
    efecto en el Mk2, ni audible ni en estado interno; el GET devolvía un `07` fijo que
    tampoco se movía al girar la perilla. Queda como `REVERB_LEVEL_MK1`, `@Deprecated`.
  - `60 00 05 48` (`re_effect_lvl` de `reverb.yaml:17`, en la sección `SEND` de una fuente
    **de Mk2**): escribir ahí **tampoco hace nada** — tras mandar 100 repetidamente, un GET a
    `60 00 06 5B` seguía en 0, y sin cambio audible. Sí reporta por su cuenta al mover la
    perilla, pero con un valor relacionado y **retardado** respecto al real (desfase de ~15).
    Hipótesis: es el nivel de efecto ya aplicado, con inercia — algo que el amp **deriva**,
    no un punto de control. Queda como `REVERB_LEVEL_DERIVED`, `@Deprecated`.
- **2026-09-02 — No dar por bueno el patrón estructural sin probarlo.** `Adresses.txt`
  empareja cada efecto con una dirección baja y una del bloque `60 00 06 5x`, lo que sugería
  un patrón "escritura baja / reporte alto". **Para reverb resultó al revés**: la alta es la
  de control, y la baja ni siquiera acepta escritura. Los cinco efectos restantes —Presence
  `60 00 00 27`/`06 56`, Boost `60 00 00 12`/`06 57`, Mod `60 00 02 38`/`06 58`, FX
  `60 00 04 14`/`06 59`, Delay `60 00 05 06`/`06 5A`— hay que probarlos **uno a uno con
  audio**. Las direcciones bajas siguen siendo candidatas legítimas a probar primero, por
  estar citadas como `SEND`, pero nada más que eso.
- **2026-09-02 — `60 00 12 14` NO es un nivel de reverb.** El vector de checksum de
  `katana_sysex.txt` (`60 00 12 14 --> 01`, checksum `79`) viene anotado como "set Katana
  Reverb type 'red'", lo que sugería un parámetro de reverb. Al buscarlo en el documento
  resulta estar en **Color Button Management → `[Select Active Color]`**: elige qué botón de
  color está activo para el reverb, con valores `00` verde / `01` rojo / `02` amarillo. Es
  un enum de 3 valores, no un continuo, así que un slider no le pega.
  - El parámetro continuo correcto es **`60 00 06 18` = Reverb Level, `0..100`**, confirmado
    en dos fuentes: `katana_sysex.txt` (Reverb → `[Parameters]` → "Level (0..100)") y
    `simple_dsp.json` (`reverb.effectLevel`, offset 8 sobre `baseAddr [96,0,6,16]`,
    `byteRange [0,100]`). Es el que usa el slider.
  - Ambas direcciones quedan en `KatanaAddresses` y la del color lleva el aviso, para que el
    vector de test no se vuelva a confundir con un nivel.
- **2026-09-02 — Escritura optimista, y sin bucle de eco.** Un SET no se confirma, así que
  esperar confirmación solo haría que el slider fuera por detrás del dedo: la caché se
  actualiza antes de enviar. Y la regla anti-eco vive en `KatanaRepository`, la única clase
  que escribe: lo que llega del amp actualiza la caché y **nunca** se reenvía. Hay un test
  que lo fija.
- **2026-09-02 — El tamaño del dump depende del estado del amp, no es una constante.** La
  prueba real dio **8 mensajes / 1860 bytes de datos**, mientras que el ejemplo de
  `HOW.md` documenta 6 / 1344. **No es una discrepancia**: confirma que el dump devuelve
  solo los rangos de parámetros efectivamente ocupados en el estado actual del
  amplificador, y que el 1920 del GET es un **techo de rango, no una cantidad fija** — justo
  lo que ya decía katana_sysex.txt. Ningún código debe asumir un número fijo de mensajes ni
  de bytes.
- **2026-09-02 — El dump NO llega en un solo mensaje, así que no se puede correlacionar por
  dirección.** `awaitRolandReply` empareja *un* mensaje con *una* dirección; pero
  `reference/TuxKatana/HOW.md` traza la respuesta al dump como **6 mensajes** (5 de 241 bytes
  de datos y uno final de 139), cada uno con su propia dirección (`60 00 00 00`,
  `60 00 01 71`, `60 00 03 62`, …). Usar `awaitRolandReply` habría devuelto en silencio solo
  el primer trozo. Por eso el dump usa `sendAndCollect` con ventana de 3 s y reporta el
  conjunto.
  - Relacionado: los 1920 del GET son un **límite de rango, no un número de bytes**. El amp
    salta las zonas sin parámetros definidos, así que vuelven menos (katana_sysex.txt lo dice
    explícitamente). No hay que tratar "llegaron menos de 1920" como un error.
  - `SysExFramer.DEFAULT_MAX_MESSAGE_SIZE` = 4096: sobra incluso para el peor caso
    (1920 + 14 de overhead = 1934), así que no hizo falta tocarlo.
- **2026-09-02 — El debounce es *trailing*, no throttle.** Cada `set()` cancela el envío
  pendiente y reinicia los 100 ms, así que durante un arrastre continuo no se manda nada
  hasta que el dedo se detiene. Es lo que pide CLAUDE.md §4.2 ("solo el último valor"), y
  difiere de `anti_flood.py` de TuxKatana, que además va soltando valores intermedios cada
  100 ms. Si algún día se quiere que el amp siga el arrastre en vivo, habría que cambiar a
  throttle — pero entonces vuelve el riesgo de inundarlo que motivó la nota original.
- **2026-09-03 — Fijar el criterio de cierre antes de ver el resultado es lo que permitió
  cerrar.** El handshake llevaba desde el 2026-09-02 como "duda de baja prioridad", que es la
  etiqueta con la que las investigaciones se quedan abiertas indefinidamente. Se acotó a **una**
  hipótesis concreta —la trama con los bytes de versión reales del amplificador, el único byte
  en que difería de su Identity Reply— y se escribió de antemano qué significaría cada
  resultado. Salió que no, y se cerró sin discusión. Sin ese compromiso previo lo natural
  habría sido inventar la siguiente hipótesis.
- **2026-09-03 — `stateIn` en un scope que nadie cancela cuelga los tests y fuga en
  producción.** Al derivar la cara de display de un nivel se usó
  `state.map{}.stateIn(scope, Eagerly)`. En los tests, `runBlocking` espera a sus hijos y esa
  corrutina no termina nunca: la suite se colgó diez minutos. En la app no se colgaba nada,
  pero el repositorio se reconstruye en cada reconexión y esos colectores se habrían ido
  acumulando en `viewModelScope`. La versión buena es una propiedad calculada
  (`displayValue`) más la conversión donde se colecta; **una derivación pura no necesita una
  corrutina**.
- **2026-09-03 — Medir la mejora es parte de hacerla.** Cambiar 24 peticiones por una parecía
  una mejora evidente, y en número de mensajes lo era: 24 → 1. Pero el log mostró que los
  controles tardaban **3 s** en poblarse frente a los 540 ms de antes, porque la recogida
  esperaba una ventana fija en vez de cortar cuando el amplificador se calla. Corregido, el
  ciclo completo quedó en **539 ms → 3013 ms → ~300 ms**: sin mirar los tiempos del log se
  habría dado por buena una regresión de 2,5 s vendida como mejora de 24×. **Contar mensajes
  no es medir latencia**, y la métrica que importa es la que ve el usuario —cuándo se pueblan
  los controles—, no cuántas peticiones se ahorran.
- **2026-09-03 — ⚠️ Curiosidad sin investigar: los primeros 16 bytes de `60 00 00 00` no
  parecen el nombre del preset.** El botón de dump los muestra como `"{?"` — bytes
  `7B 3F` seguidos de catorce espacios (`20`)—, que no es ASCII imprimible plausible para un
  nombre. Los nombres que sí se leyeron bien están en `10 01 00 00`…`10 08 00 00` y salían
  como `"KATANA Mk2"`, así que el formato de nombre existe y se sabe reconocer.
  - **Hipótesis**: que `60 00 00 00` no contenga en el Mk2 lo mismo que en el mapa MK1 del
    que se heredó (`katana_sysex.txt`, cuya primera línea dice "Boss Katana 100 Combo — v1.7 -
    2017-03-23"). Sería el mismo patrón que ya mordió con las direcciones de parámetros — con
    un matiz incómodo: **hasta ahora las direcciones "de sistema" (`10 xx`, `60 00 00 00`,
    `7F 00 00 01`) se habían dado por fiables** precisamente porque las de parámetros no lo
    eran. Esta es la primera grieta en esa suposición.
  - **Nada depende de ello**: el parseo de parámetros no usa esos bytes, y el dump entero
    funciona. Queda anotado por si algún día se implementa el nombre del preset actual, no
    como tarea.
- **2026-09-03 — Un valor que rebota solo delata una dirección de solo reporte.** Es el
  diagnóstico más barato que ha dado este proyecto: si al mover un control la UI se pone en el
  valor nuevo y vuelve sola al anterior en un instante, la dirección **reporta pero no acepta
  escritura**. El rebote es el mensaje espontáneo del amplificador diciendo su estado real, y
  solo es visible porque el edit mode y la actualización desde el amp están cableados. Antes
  de buscar otra dirección, mirar si la que hay está etiquetada como estado (`led state` en
  `midi.xml`).
- **2026-09-03 — El bloque `60 00 06 5x` no era especial.** Once confirmaciones seguidas ahí
  habían creado la regla implícita de que solo lo alto se escribe. Las once direcciones bajas
  confirmadas este día (color ×5, on/off ×5, modelo de amplificador) la derogan. Lo que tiene
  de particular ese bloque es que es **el de las perillas del panel**, no el de lo escribible.
- **2026-09-03 — Un selector rechaza; un nivel clampea.** Es la única diferencia real entre
  los dos tipos de control, y sale de que los valores de un selector **no son contiguos**:
  `AmpType` va del `0x00` al `0x20` saltándose el `0x19`, así que "el legal más cercano" no
  significa nada. Importa sobre todo al recibir: si el amplificador reporta un valor fuera de
  la tabla, lo correcto es dejar la UI quieta y que salga en el log, no mover el control a un
  vecino inventado. El debounce se cae solo: sin arrastre no hay avalancha que coalescer.
- **2026-09-03 — El sentido de los on/off: `00` off, `01` on.** Estaba anotado como
  suposición porque `Adresses.txt:81` escribe `[00|01] # [ON|OFF]`, que leído en orden diría
  lo contrario. Confirmado con el amplificador el mismo día: es como en todo lo demás, y esa
  anotación simplemente lista los valores y las etiquetas en órdenes distintos.
- **2026-09-03 — Kotlin anida los comentarios de bloque.** Escribir `params/*.yaml` dentro de
  un KDoc abre un comentario anidado que no cierra nunca, y el error que sale
  ("Unclosed comment" al final del fichero, más decenas de "Unresolved reference" en
  cascada) no señala la línea culpable. Pasó dos veces en esta sesión. Al citar rutas con
  comodín en un KDoc, no dejar la secuencia barra-asterisco.
- **2026-09-03 — La anotación más explícita de `Adresses.txt` resultó ser la equivocada.**
  Con flechas de dirección inequívocas —único sitio del fichero anotado así— afirmaba que
  `60 00 06 51` era de solo lectura y `60 00 00 22` la de escritura. El audio dice lo
  contrario. Ni una anotación sin ambigüedad sustituye la prueba; once de once direcciones
  altas confirmadas es la evidencia que manda.
- **2026-09-03 — Las perillas de amp/EQ tienen peor documentación que los efectos.** En los
  seis efectos, la fuente de Mk2 (`reverb.yaml`, `booster.yaml`, `mod.yaml`, `fx.yaml`,
  `delay.yaml`) citaba la dirección **alta** bajo `SEND`. En `amplifier.yaml` no: solo Gain y
  Volume aparecen con la alta, y Bass/Middle/Treble aparecen con la **baja**, sin mencionar
  siquiera `06 53`–`06 55`. Y `Adresses.txt:36-41` va más lejos y afirma explícitamente
  —único sitio del fichero con flechas de dirección— que `06 51` es *read status* y `00 22` la
  de escritura. Así que el patrón que acertó seis de seis choca aquí con la anotación más
  explícita que tiene ninguna fuente. Se prueban igual las altas primero, pero **esperar más
  fallos que en los efectos es razonable**, y si fallan no significa que el patrón fuera
  falso: significa que el bloque de perillas puede no ser homogéneo.
- **2026-09-03 — Confirmar por audio confirma *que responde*, no *qué es*.** El caso de
  `60 00 06 5A` lo enseña: la prueba de oído dijo "esto es el nivel del delay" y en realidad
  es el mix global de los dos delays. Con el delay 2 apagado —que es como estaba— las dos
  cosas son indistinguibles. La lección no invalida el método (el oído sigue siendo lo único
  que distingue una dirección viva de una muerta), pero acota lo que demuestra: **una prueba
  de audio con el resto de la cadena en un estado concreto solo prueba el comportamiento en
  ese estado.** Cuando una fuente diga algo distinto de lo que parece oírse, no darla por
  muerta: puede estar describiendo el caso que la prueba no cubrió.
- **2026-09-03 — Seis de seis, y aun así el patrón no es una regla.** Las seis candidatas
  "altas" del bloque `60 00 06 5x` acertaron, todas en la posición que predecía la tabla de
  `midi.xml`. Lo que eso justifica: **empezar por ahí** al atacar las seis perillas de arriba
  del bloque (Amp Type, Gain, Volume, Bass, Middle, Treble). Lo que no justifica: darlas por
  buenas. La lista de `midi.xml` es de destinos de *assign* —dice qué controla cada perilla,
  no que la dirección acepte escritura por SysEx— y `60 00 05 48` sigue siendo el
  contraejemplo: acepta el mensaje y no hace nada. Además, esas seis no son todas del mismo
  tipo: `60 00 06 50` (Amp Type) es un **selector**, no un nivel continuo, así que ni el
  rango `0..100` se le puede suponer.
- **2026-09-03 — Los rangos: la duda del tramo sobrante era dos causas, no una.** Se anotó
  que el slider llegaba a 100 poco antes del tope físico y que de oído no se podía distinguir
  entre holgura mecánica y un rango mal fijado. Resultó ser **las dos cosas a la vez**: los
  cinco niveles de efecto tenían un desfase de uno, y encima hay holgura real. Buscar *una*
  explicación era el error; lo que lo zanjó fue notar que Presence, sin desfase posible,
  tenía el mismo síntoma.
- **2026-09-02 — Las anotaciones de las fuentes de Mk2 no predicen nada.** `60 00 05 48`
  estaba limpiamente en `SEND` de `reverb.yaml` y **no** funciona; `60 00 06 57` está repetida
  bajo `Unimplemented:` en `booster.yaml` y **sí** funciona. `SEND`, `RECV` y `Unimplemented`
  describen lo que hace TuxKatana, no lo que acepta el amplificador. Sirven para *elegir qué
  probar*, nunca para decidir.
- **2026-09-02 — La UI se colapsó por reparto de altura, no por un fallo de la consola.**
  Cuatro sliders y ocho botones de altura fija en una sola `Column` dejaron al `weight(1f)`
  de la consola sin espacio que repartir, así que desapareció sin ningún error. Partir la
  pantalla en dos secciones lo arregla de raíz: lo que no comparte columna no puede
  desplazarse. Es la razón de que el botón GET de cada nivel viva junto a su nombre y no en
  una fila común que crece con cada parámetro.
- **2026-09-02 — Un mapa `LevelId → valor` en vez de seis `StateFlow`.** El ViewModel tenía un
  campo, un job espejo, dos handlers y un slider por parámetro; con seis, el archivo era
  copia-pega. Ahora hay un `StateFlow<Map<LevelId, Int?>>` y dos handlers que reciben el
  `LevelId`. Añadir el séptimo nivel es una entrada en el enum, una línea en
  `KatanaRepository` y nada más. La UI sigue sin conocer direcciones (CLAUDE.md §4.2).
- **2026-09-02 — El bloque `60 00 06 50`–`60 00 06 5B` es la lista de perillas del panel.**
  `reference/FxFloorboard/midi.xml:3981-3992` lo enumera entero y **en el orden físico del
  panel**: Amp Type, Gain, Volume, Bass, Middle, Treble, Presence (`06 56`), Booster
  (`06 57`), MOD (`06 58`), FX (`06 59`), Delay 1 (`06 5A`), Reverb/Delay2 (`06 5B`). Eso
  explica de una vez por qué la dirección "alta" es la de control: **es la posición de la
  perilla**, no un valor derivado. Dos de las doce ya están confirmadas por oído (Presence y
  reverb) y caen justo donde la tabla predice. Sigue siendo una hipótesis con buen respaldo,
  **no** una licencia para dar por buenas las otras diez: esa lista es de destinos de
  *assign*, que dice qué controla cada perilla, no que la dirección acepte escritura por
  SysEx.
- **2026-09-02 — Boost es una perilla continua, no un botón on/off.** Importa para saber qué
  esperar en el log. En el panel del Katana MkII, BOOSTER es una perilla —`midi.xml:3988` la
  llama literalmente `Panel Knob: Booster`—, así que girarla debe producir un chorro de
  valores por `60 00 06 57`. Lo que **no** es la perilla: el botón de color de debajo
  (`60 00 06 39`, `bo_bank_sel`, verde/rojo/amarillo) y el on/off del efecto
  (`60 00 00 10`), cada uno en su propia dirección. Si al pulsar el botón de color no llega
  nada por `06 57`, eso es lo correcto, no un fallo de la dirección.
- **2026-09-02 — Presence: `midi.xml` la nombra literalmente, y aun así no basta.** De las
  cinco candidatas que quedaban, Presence es la que llega con mejor aval: en
  `reference/FxFloorboard/midi.xml:3987` aparece como
  `<PARAM value="3C" name="Panel Knob: Presence" desc="06" customdesc="56"/>` — o sea
  `60 00 06 56` — en la **misma lista** donde `06 5B` es `Panel Knob: Reverb/Delay2`, la
  dirección ya confirmada por oído. Eso es bastante más que la analogía estructural de
  `Adresses.txt:134-138`. Pero esa lista es de **destinos de assign**, no una prueba de que la
  dirección acepte escritura, y con el reverb ya falló `60 00 05 48` estando documentada
  explícitamente como `SEND` en una fuente de Mk2. Se cableó para poder probarla — y **se
  oyó**: confirmada el mismo día. Pero el orden importa y se deja anotado: primero la prueba,
  después la conclusión.
- **2026-09-02 — El rango `0..100` de Presence es una suposición, no un dato.** Ninguna
  fuente de Mk2 documenta el rango de `60 00 06 56`. Lo que hay:
  `reference/katana-midi-bridge/parameters/amplifier.json:68-72` da `presence` como
  `byteRange [0, 100]` pero es el mapa del **MK1**; y el formato `normal` de
  `reference/TuxKatana/params/slider_formats.yaml:1-3`, el que usan los niveles de panel,
  también es `0..100`. Coincide con el rango del reverb, ya verificado por los extremos, así
  que se asume. **La prueba de oído confirmó la dirección, no el rango**: los extremos del
  slider suenan, pero nada garantiza que `0..100` sea exactamente el recorrido real de la
  perilla. Anotado porque es justo el tipo de cosa que luego se recuerda como si fuera un
  hecho documentado. Si algún día el amplificador ignora valores cerca de 100, o el slider no
  cubre todo el rango de la perilla física, el sospechoso es este rango, no la dirección.
  Mismo caso con Boost.
- **2026-09-02 — El bug de la auto-conexión no estaba en el manifest sino en el código.**
  `MainActivity` tenía el `intent-filter` de `USB_DEVICE_ATTACHED` correctamente declarado
  —por eso Android sí sugería la app al conectar el amplificador— pero **nunca leía el
  intent**: ni el action ni el `EXTRA_DEVICE` que Android mete dentro. La app se abría sola
  y se quedaba esperando el botón. Corregido leyendo `EXTRA_DEVICE` en `onCreate` y
  `onNewIntent`. Moraleja: que Android abra la app no significa que la app se entere de por
  qué la abrieron.
- **2026-09-02 — Hizo falta `launchMode="singleTop"`.** Sin él `onNewIntent` no se dispara
  nunca, y reconectar el amp con la app en primer plano crearía una **segunda instancia** de
  la pantalla en vez de reusar la que ya está.
- **2026-09-02 — Guard de idempotencia en `connect()`.** Los tres caminos —intent de
  arranque, broadcast, escaneo inicial— pueden dispararse con milisegundos de diferencia.
  Sin el guard se reclamaría la interfaz dos veces o saldrían dos diálogos de permiso.
- **2026-09-02 — Abrir la app por el intent-filter concede el permiso USB implícitamente.**
  Por eso el camino "Android sugiere la app al conectar" no debería mostrar el diálogo de
  permiso, mientras que abrirla desde el launcher con el amp ya puesto sí puede pedirlo la
  primera vez. Son caminos distintos y conviene no confundir un diálogo esperado con un fallo.
- **2026-09-02 — Los tres caminos de conexión pueden dispararse a la vez.** Abrir por intent
  y el escaneo inicial ocurren con milisegundos de diferencia, así que `connect()` lleva un
  guard: si ya hay transporte, o si hay un permiso en vuelo, no hace nada. Sin él se
  reclamaría la interfaz dos veces o saldrían dos diálogos de permiso.
- **Un solo lector del endpoint, siempre.** `KatanaUsbTransport.incomingMessages()` es un
  flow *frío*: cada colector arrancaría su propio bucle de `bulkTransfer` y se robarían
  mensajes entre sí. El ViewModel lo colecta **una vez** y reparte por un `MutableSharedFlow`;
  el log y las peticiones que esperan respuesta se suscriben a ese. Por lo mismo,
  `sendHandshake()` ya no lee.
- **`awaitRolandReply` se suscribe con `CoroutineStart.UNDISPATCHED` antes de enviar.** Si se
  suscribiera después, una respuesta rápida se perdería. No es teórico: quitando ese flag,
  5 de los 6 tests de `RolandExchangeTest` fallan.
- **El empaquetado USB-MIDI vive en `usb/`, no en `protocol/`.** Es formato de cable, no de
  protocolo: `protocol/` seguirá viendo solo mensajes `F0…F7` limpios. Aun así `packUsbMidi`
  / `unpackUsbMidi` son Kotlin puro y tienen tests JVM, porque un bug ahí haría ilegible
  cualquier prueba con el amplificador.
- **`sendHandshake()` es `suspend`, a diferencia del resto del transporte.** Los ~4 ms entre
  los dos envíos son parte del protocolo y `delay` los respeta sin bloquear un hilo. Además,
  la lectura intermedia usa un timeout muy corto (5 ms) a propósito: leer con el timeout
  normal de 1 s entre los dos envíos convertiría el hueco de 4 ms en uno de más de un
  segundo, que es justo lo que el handshake no admite. El precio es que una respuesta lenta
  al primer envío no se ve ahí, sino en la lectura posterior — que el ViewModel hace y
  registra como "respuesta tardía".
- `protocol/` debe quedar libre de imports de `android.*` para poder testear en JVM puro.
  `SysExFramer` se mueve ahí: ya no depende de la API MIDI, solo trocea un flujo de bytes.
- **Pendiente en el manifest**: quitar `<uses-feature android:name="android.software.midi">`
  (ya no aplica) y reconsiderar si `android.hardware.usb.host` debe pasar a `required="true"`,
  ahora que USB host es el único transporte posible.
- En `device_filter.xml`, `vendor-id` y `product-id` van en **decimal**, no en hexadecimal.
  Roland = `1410` (0x0582). Con el fichero vacío ningún dispositivo hace match, así que el
  intent-filter `USB_DEVICE_ATTACHED` no dispara hasta que se rellene. Ahora es la vía
  natural para lanzar la app al conectar el amplificador.
- `UsbManager.requestPermission()` abre un diálogo del sistema y responde por `PendingIntent`:
  es un paso asíncrono que con el framework MIDI no existía. `bulkTransfer()` bloquea, así
  que va en `Dispatchers.IO`; devuelve `-1` en timeout, que en el bucle de lectura es normal
  y no significa desconexión.
- El texto de las líneas de log **no** va en `strings.xml`: son diagnósticos técnicos
  generados por la capa MIDI, no copy de UI. Lo que sí está en `strings.xml` es el chrome de
  la pantalla (título, botón, estado).
- `DebugConnectionViewModel` es un `AndroidViewModel` para que rotar la pantalla no vuelva a
  abrir los puertos MIDI; se cierran en `onCleared`.
- **Entorno**: Android Studio está instalado como Flatpak, así que corre en su propio mount
  namespace con un `/tmp` privado. Si existe un daemon de Gradle iniciado desde el host (por
  la extensión Gradle de VS Code, o por `./gradlew` en una terminal), Studio lo reutiliza a
  través de `~/.gradle/daemon/registry.bin` (el home sí es compartido) y falla el sync con
  `The specified initialization script '/tmp/ijMapper1.gradle' does not exist`, porque el
  daemon del host no ve el `/tmp` del sandbox. Solución: `./gradlew --stop` y volver a
  sincronizar, o separar los `GRADLE_USER_HOME` de host y Studio.
- **2026-09-03 — Investigación (sin implementar): el canal/preset activo es un solo byte de
  8+1 valores en `00 01 00 00`, no banco+canal separados.** Documentado en CLAUDE.md §5.1 con
  cita de archivo y línea. Resumen de la evidencia: `switcher.py:75-88` indexa un único
  `ch_num` 1..8 (≤4 banco A, resto banco B); `config.yaml:8-17` lista los ocho valores con su
  Program Change comentado al lado; `globals.py:14-15` fija `CURRENT_PRESET_LEN = 0x02` (dos
  bytes de dato, no uno). Se puede **leer** por dos vías: GET explícito
  (`katana_sysex.txt:180-198`, exige edit mode) o reporte espontáneo
  (`controller.py:95-98`, cualquier entrante a esa dirección se trata como cambio de canal —
  ya cableado en la app vía `applyIncoming`, solo falta que algo escuche esa dirección).
  **Descartado Program Change como mecanismo principal**: la tabla existe y es real
  (`midi.yaml:23-34`, `midi.xml:945-947` confirma un patch number de Mk2 en esa misma
  posición del mapa), pero viajar por PC exigiría una segunda ruta de empaquetado USB-MIDI
  (CIN `0xC`, 2 bytes) que hoy no existe — `packUsbMidi` solo emite SysEx — y PC depende del
  canal MIDI configurado en `00 02 00 00`, una variable más que SysEx no tiene. Recomendación:
  SysEx a `00 01 00 00`, igual que todo lo demás ya implementado.

- **2026-09-04 — El `desc` de `midi.xml` es lo que hace extraíble el mapa de Mod/FX, y la
  simetría FX = Mod + `0x0200` ahorra la mitad del trabajo.** Dos hallazgos de método, no de
  protocolo, que conviene no volver a descubrir.
  - Cada `<DATA>` del bloque lleva un **prefijo por tipo** en su atributo `desc` (`MOD PH:`,
    `MOD FL:`, `MOD 2CE:`…) y los nodos de un tipo son consecutivos. Agrupar por `desc`
    delimita los 31 bloques solo, sin adivinar dónde acaba uno y empieza el siguiente — que
    era el problema real de leer 2.300 líneas de XML a ojo.
  - El bloque FX **repite el de Mod byte a byte** con el tercer byte de dirección +2. No se
    dio por bueno con cuatro muestras: se compararon los 237 nodos de cada uno por
    `(LSB relativa, 4.º byte, tipo, nombre del parámetro)` y salen idénticos, con 14
    diferencias que son **solo de etiqueta**. Dos de esas etiquetas son errores de la fuente:
    `midi.xml` llama `ACS:` a dos tipos distintos dentro del bloque Mod (Compressor `01 16` y
    AC Guitar Sim `02 41`) mientras que en FX sí los desambigua (`AGS:`), y las etiquetas de
    los `Pre Delay` anidados de FX están copiadas mal. Ninguna afecta a una dirección.
  - **La verificación mecánica es lo que da confianza aquí**, porque para Mod/FX hay **una
    sola fuente de Mk2** y no se puede contrastar con una segunda, como sí se hizo con los
    catálogos de tipos de Booster, Delay y Reverb. Comprobar la coherencia interna de la
    fuente es lo mejor disponible — y sigue sin sustituir a la prueba con audio, que está
    pendiente para todo este bloque.

- **2026-09-04 — Un predicado, no una lista de direcciones, es lo que separa un trozo de
  dump de un reporte espontáneo.** Se descartó `List<Address>` para `sendAndCollectUntilQuiet`
  porque no se sabe de antemano cuántos trozos vendrán ni en qué bases (§4.4); pasar el rango
  por separado también se descartó porque invitaría a que se desincronizara del propio GET.
  Un predicado (`accept: (RolandMessage.Data) -> Boolean`) deja `protocol/` genérico y puro,
  y `blockReplyIn(base, size)` es la única factoría que hizo falta.
  - **Ninguna de las dos condiciones basta sola.** El rango solo no sirve: 37 de los 38
    controles registrados viven dentro de `[60 00 00 00, +1920)`, así que casi cualquier
    reporte espontáneo lo cumple. El tamaño solo tampoco: las respuestas de nombre de
    dispositivo/preset son de 16 bytes, por encima del umbral de control (2) pero fuera del
    rango del dump. Las dos en AND es lo que de verdad discrimina.
  - **`applyDumpValue` pasó de recibir un `Int` a recibir el `MemoryDump` entero.** Antes el
    llamador ya había extraído un solo byte con `dump.byteAt(control.address)`; ahora el
    control lee sus propios `byteWidth` bytes con el nuevo `MemoryDump.bytesAt(address,
    width)`, todo o nada. Sin esto el filtro de dirección+tamaño habría cerrado la vía por la
    que el canal se corrompía, pero habría dejado la misma trampa armada para el próximo
    control multibyte que caiga dentro del rango (Pitch Shifter/Harmonist, §5.2).
  - **Un solo `MutableStateFlow<ReloadRequest>` conflado, no dos caminos.** La recarga de
    conexión y el watcher de canal alimentan el mismo `collectLatest`
    (`DebugConnectionViewModel.startReloadCoordinator`), así que una recarga en vuelo siempre
    cancela a la anterior — es lo que de raíz impide los dos dumps simultáneos, no el `Mutex`
    que se añadió alrededor de `loadFromDump()`. Ese `Mutex` es deliberadamente una red de
    seguridad redundante: antes de esto no había ningún guard de concurrencia en el proyecto,
    y un futuro tercer disparador (el cambio de tipo de amplificador, pendiente) no debería
    poder reabrir el problema por descuido.
  - **No se pudo probar la ViewModel directamente.** `DebugConnectionViewModel` extiende
    `AndroidViewModel` y necesita `android.app.Application`; el proyecto no tiene Robolectric
    (§6 mantiene la lista de dependencias corta a propósito). Los tests de regresión
    reconstruyen la lógica de `startReloadCoordinator` contra un `KatanaRepository` real —el
    mismo reproductor que diagnosticó el bug, ahora verificando el diseño corregido— en vez de
    contra la ViewModel en sí. Es una limitación conocida, no una elección de diseño: el día
    que haga falta cubrir la ViewModel con más detalle, esa pieza de coordinación es
    candidata a subir a `device/`, donde sí se puede testear sin Android (ver la decisión
    pendiente de alcance en la nota de diseño original).

- **2026-09-06 — Tabla de datos en vez de 192 propiedades con nombre, para los parámetros
  internos de los 31 tipos de Mod/FX.** El razonamiento completo, con las cuatro capas
  afectadas (`protocol/ModFxInternalParams`, `KatanaRepository`, el `ViewModel` y
  `DebugConnectionScreen.ModFxGenericParams`), está en "Hecho", 2026-09-06 — esta entrada es
  solo el resumen para quien busque la decisión aquí. En corto: el patrón de una `val` +
  enum `XParamId` + función `onXParamChanged` por parámetro, que funciona bien para los
  bloques "DSP simple" de Booster/Delay/Reverb (≤10 parámetros cada uno), se sale de escala
  con 192 parámetros en 31 formas que además cambian según el tipo activo del slot — así que
  se generalizó a una tabla pura en `protocol/` y un renderizador genérico en la UI, ambos
  cableados sobre la misma maquinaria de `KatanaControl` que ya tenía el resto del proyecto
  (sin gate de Edit Mode en `device/`, sin caso especial de anti-eco, sin caso especial de
  dump). El coste aceptado: 192 etiquetas de parámetro hardcodeadas en vez de en
  `strings.xml`, por ser nombres de la fuente (`midi.xml`) y no prosa de interfaz — ver el
  detalle en "Hecho" si esto necesita revisarse el día que el proyecto tenga i18n real.

- **2026-09-06 — Un solo vocabulario de tabla (`ParamKind`/`ParamSpec`) para Mod/FX y EQ, en vez
  de dos paralelos.** Al cablear los bloques de EQ apareció que tienen exactamente la misma
  forma que los de Mod/FX: una lista de "etiqueta + dirección + tipo de escala", con un gemelo
  desplazado (EQ2 = EQ1 + `0x20`, como FX = Mod + `0x0200`). Se renombró
  `ModFxParamKind`/`ModFxParamSpec` a `ParamKind`/`ParamSpec` y se movieron a
  `protocol/ParamSpec.kt` en lugar de crear un `EqParamKind` idéntico al lado. El renombrado
  costó un `sed` sobre cinco ficheros y lo verificó la suite entera; mantener dos vocabularios
  habría costado cada vez que se toque cualquiera de los dos. En la UI, `TableParams` recorre
  cualquier `List<ParamSpec>` y `ModFxGenericParams` solo elige la lista, así que EQ y Mod/FX
  comparten widgets sin duplicarlos.
  - ⚠️ **Lo que NO se unificó, a propósito: los catálogos de frecuencia.** Los seis del EQ son
    byte a byte los del Parametric EQ de Mod/FX, y aun así se definen por separado en
    `EqParams`, con un test que compara los dos. Es la misma decisión que ya se tomó con
    `DelayHighCutFrequency` y `ReverbHighCutFrequency` (§5.2), y por la misma razón: compartir
    la constante escondería una divergencia futura entre bloques distintos del amplificador, y
    en este proyecto la fuente ya se ha contradicho consigo misma al menos una vez.

- **2026-09-06 — Los GET de respaldo son en serie, y eso ahora tiene un precio medible.**
  `loadFromDump` termina con `missing.forEach { control.read() }`: secuencial, hasta 800 ms por
  control. Daba igual mientras el dump cubría todo (0 respaldos en la lectura real de
  2026-09-03); con los 6 Contour por slot fuera del dump son 6 esperas encadenadas en **cada**
  recarga, y la recarga se dispara también en cada cambio de canal.
  - **No se paralelizó ni se amplió el rango del dump**, que son las dos salidas obvias. Las dos
    cambian el camino de recarga —el mismo que costó el bug de los cuatro fallos encadenados de
    2026-09-04— y **todavía no se sabe si hacen falta**: si `60 00 0F 3x` contesta rápido, el
    coste es despreciable. Hacerlo antes de medirlo sería optimizar a ciegas y arriesgar una
    zona delicada por una suposición.
  - Lo que sí se hizo es **dejarlo medido y acotado**: un test fija en seis los controles fuera
    del dump y comprueba que el resto de la tanda cae dentro, y el test de concurrencia que lo
    destapó lleva escrito al lado por qué su margen pasó de 800 ms a 4 s.

- **2026-09-06 — En una operación irreversible, rechazar es mejor que clampear.** Todo control
  del proyecto o clampea (un nivel) o rechaza (un selector), y la razón hasta ahora era la forma
  de los valores: los de un selector tienen huecos y "el más cercano" no significa nada (§4.3).
  El commit de guardado añade una segunda razón, distinta: **`PresetSave.commitMessage` rechaza
  un canal fuera de `01`..`08` porque clampear elegiría un canal destino por su cuenta**, y aquí
  eso significa sobrescribir un preset que nadie pidió tocar, sin deshacer y sin confirmación.
  Un `0` colado por descuido sería además el PANEL, cuyo efecto nadie ha comprobado.
  - Por lo mismo la UI no ofrece PANEL, pide **dos** confirmaciones en vez de una, y no deja dos
    guardados solapados. Ninguna de las tres cosas la exige el protocolo; las tres salen de que
    el coste de un descuido es asimétrico — perder un preset cuesta una tarde, un toque de más
    cuesta un segundo.
- **2026-09-06 — El nombre se sanea a ASCII y se compara ya saneado.** Los datos SysEx son de 7
  bits, así que una eñe en el nombre habría hecho fallar el guardado entero con una excepción
  desde `RolandSysEx.set` — y "guardar un preset con nombre en castellano" es el caso normal
  aquí, no un borde. `encodeName` sustituye lo que no sea ASCII imprimible, y la UI **enseña el
  resultado antes de confirmar** en vez de cambiarlo por detrás.
  - El detalle que se habría escapado sin el test: `savePreset` compara la lectura de vuelta
    contra el nombre **ya saneado**. Comparando contra el original, un guardado perfectamente
    correcto de "Distorsión" se habría reportado como fallido para siempre, porque el amp
    devuelve "Distorsi?n".

- **2026-09-06 — El `.tsl` no necesitó un modelo de datos propio, y eso no fue suerte.**
  `MemoryDump` es "una lista de trozos de bytes, cada uno con su dirección base" y el `paramSet`
  de un `.tsl` es exactamente eso mismo escrito en JSON. Así que el parser produce un
  `MemoryDump` y `AmpState.from(dump)` lo lee sin enterarse de que los bytes vinieron de un
  fichero. **Cero cambios en `AmpState` y cero en `MemoryDump`.**
  - Lo que hizo que encajara es una decisión vieja: `MemoryDump` se diseñó como **búsqueda por
    dirección** y no como índice sobre un array plano (§4.4), porque el amplificador manda el
    dump en un número de mensajes que no se sabe de antemano. Un `.tsl` es el caso extremo de lo
    mismo —22 bloques con huecos reales entre ellos, que el fichero simplemente no guarda— y la
    misma abstracción lo cubre sin tocarla.
  - De ahí sale gratis el requisito de "no disponible en este fichero": `byteAt` ya devolvía
    `null` para lo que no cubre, y los campos de `AmpState` ya eran anulables porque el dump real
    tampoco llega entero. Un requisito que en un diseño nuevo habría sido un caso especial aquí
    ya estaba implementado.

- **2026-09-06 — Ante dos direcciones candidatas, no cargar es mejor que elegir.** Cuatro claves
  del `.tsl` tienen dos direcciones posibles y nada que las desempate sin un fichero de prueba.
  `TslConfidence.DISPUTED` las marca y el parser las salta, listándolas como no disponibles.
  - El razonamiento es el mismo que lleva a `KatanaEnumParameter` a rechazar un valor que no está
    en su tabla (§4.3), aplicado a direcciones: **un Freq Shift enseñado donde va un Shape tiene
    exactamente la misma pinta que un valor correcto**, así que el error no se detecta mirando la
    pantalla. Un hueco declarado sí se ve.
  - Y es reversible en una línea: cuando la prueba con un `.tsl` real diga cuál es, la clave pasa
    a `CONFIRMED` y se carga sola. Elegir ahora no adelantaría ese trabajo, solo taparía el
    hueco mientras tanto.

- **2026-09-06 — `kotlinx-serialization-json` entra, y el motivo es la testabilidad, no el
  gusto.** §6 dejaba la puerta abierta "solo si el parseo de presets `.tsl` lo justifica" y
  apuntaba a que el `org.json` de la plataforma podía bastar. **No basta**: `org.json` viene
  *stubbed* en los tests JVM de Android —sus métodos devuelven valores por defecto o lanzan— y el
  parseo del `.tsl` es justamente lo que hay que poder probar sin amplificador y sin dispositivo,
  que es la razón por la que esta mitad se hizo antes que la otra.
  - Alternativas descartadas: escribir un parser de JSON a mano (más código propio que el que
    ahorra la librería, y con sus propios bugs) o mover los tests a `androidTest` (dejarían de
    correr en `./gradlew :app:testDebugUnitTest`, que es donde el proyecto los mira).

- **2026-09-06 — Un script de refactor sobre código sin commitear se llevó por delante dos
  pantallas.** Extrayendo seis widgets compartidos a `Controls.kt`, una expresión regular con
  `(/**...*/)?` opcional en modo `DOTALL` movió ~1700 líneas —las dos pantallas enteras— en vez
  de seis bloques. **`git checkout` no era una opción**: el fichero de origen tenía todo el
  trabajo del día sin commitear. Se reparó a mano en tres pasadas.
  - La regla que sale de aquí es corta: **commit antes de correr un script sobre el código**, y
    no la confianza en la expresión regular. El coste de commitear es cero y el de no hacerlo
    fue, esta vez, una hora de cirugía.

- **2026-09-06 — La costura para editar sin amplificador ya existía: `KatanaLink`.** La tarea
  planteaba dos opciones —abstraer un backend dentro de `KatanaControl`, o invertir el flujo
  para que todo pasara por `AmpState`— y la respuesta resultó ser ninguna de las dos: `KatanaLink`
  **es** el backend de un parámetro, con `send` y `incoming` y nada más. Una implementación que
  guarda los SET en un mapa y contesta los GET desde él da el juego completo de controles
  offline con **cero cambios** en `KatanaControl`, `KatanaRepository` y la UI.
  - Cuatro decisiones viejas son las que hacen que encaje, y ninguna se tomó pensando en esto:
    la suscripción `UNDISPATCHED` **antes** de `send` (§4.4, puesta para no perder respuestas
    rápidas del amp) es justo lo que permite contestar de forma síncrona; `KatanaRepository` ya
    se testeaba contra un link falso (§6); `MemoryDump` ya era una búsqueda por dirección y no
    un array plano; y las escrituras ya eran fire-and-forget.
  - **La lección**: antes de añadir una abstracción, mirar si la que ya hay separa exactamente
    lo mismo una capa más abajo.

- **2026-09-06 — Un modelo de dominio pierde lo que no modela, y por eso se editan bytes.**
  El argumento decisivo contra hacer de `AmpState` la fuente de verdad no fue de diseño sino
  aritmético: **`AmpState` tiene 24 campos y un `.tsl` son 1141 bytes**. Editar el Gain de un
  preset importado a través del modelo de dominio y volver a exportarlo **borraría** los
  parámetros internos de Mod/FX, el EQ y la cadena, sustituidos por lo que `AmpState` no supo
  conservar. Con una imagen de bytes, lo que la app no entiende sobrevive por la vía más simple
  que hay: nunca se toca.
  - Hay un test que fija justo eso: mover el Gain y comprobar que `Fx(1)`, `Fx(2)`, `Eq(2)` y
    `Patch_2` salen byte a byte idénticos.
  - La regla corta, para no volver a discutirlo: **lo que se guarda y se transporta son bytes;
    `AmpState` es para enseñar.**

- **2026-09-06 — Dos bugs que solo aparecieron al montar el camino offline entero.** Los dos
  eran invisibles en el diseño y visibles en cuanto se ejecutó:
  - **`loadFromDump` colgó un test.** Offline, sus GET de respaldo —en serie, 800 ms cada uno—
    se disparan por cada control que la imagen no cubra; con cientos de controles y un preset
    casi vacío, son minutos. Y además son **inútiles**: preguntan otra vez a la misma fuente que
    acaba de no tener el valor. De ahí `loadFromImage`, sin respaldo, con un test que mide el
    tiempo.
  - **El debounce habría perdido el último cambio.** Los ~100 ms existen para no inundar el
    cable (§4.2); offline el destino es un `HashMap`, y el único efecto sería que mover un
    slider y guardar acto seguido escribiera el fichero **sin ese cambio**. `debounceMillis = 0`
    en el repositorio offline es corrección, no rendimiento.
  - Lo que tienen en común: **una decisión correcta para el cable deja de serlo cuando el
    destino cambia**, aunque el código sea literalmente el mismo.

- **2026-09-06 — Reutilizar la UI de verdad exigía sacar los mapeos a un sitio común.** La
  pantalla de edición offline es **literalmente `SlidersPane`** con `offline = true`, no una
  copia. Para eso hubo que extraer los nueve mapeos `id → control` a `ControlBinding`, que las
  dos pantallas comparten.
  - El riesgo que evita es concreto: con una copia por ViewModel, cablear un control nuevo y
    actualizar solo una de las dos daría una pantalla que edita algo que la otra no, **sin que
    nada fallara al compilar**. Es el mismo tipo de divergencia silenciosa que ya obligó a
    mantener `DelayHighCutFrequency` y `ReverbHighCutFrequency` separados a propósito.
  - Los botones de "leer" se ocultan con un `CompositionLocal` y no con un parámetro: son medio
    centenar de puntos de llamada para expresar una decisión que es de la pantalla entera.

- **2026-09-06 — Una dirección que no hace nada se retira de la UI pero no se borra del mapa.**
  Bright y Gain SW aceptan el mensaje y no mueven ni el sonido ni su propio estado interno. Se
  van de la pantalla —un control que traga el gesto y no hace nada es peor que no tenerlo— pero
  siguen documentadas en `KatanaAddresses` con qué se probó y cómo.
  - El motivo es el precedente del nivel de reverb (CLAUDE.md §5): allí hicieron falta **tres**
    candidatas, y las dos primeras parecían igual de plausibles. Si algún día aparece otra
    dirección para Bright, lo que hará falta saber es qué ya se descartó y con qué prueba.
    Borrarlas dejaría el mismo trabajo por hacer dos veces.
  - La misma lógica se aplicó a sus sondas de diagnóstico, pero al revés: **esas sí se
    quitaron**, porque existían para contestar una pregunta y la pregunta está contestada. Lo
    que se conserva es el dato, no la herramienta.

- **2026-09-06 — La forma del control es parte de la información, no decoración.** El EQ pasó de
  una lista de sliders a barras verticales (gráfico) y perillas (paramétrico) siguiendo a Boss
  Tone Studio, y la razón no es estética: **un EQ gráfico se lee por la forma de la curva**, y
  diez sliders horizontales apilados no forman ninguna curva. La misma información, ilegible.
  - El paramétrico va en perillas **incluidas las frecuencias y la Q, que son selectores**: la
    perilla mueve el índice y el texto enseña `1.60k`. Un desplegable por frecuencia ocuparía
    media pantalla para algo que se ajusta girando y escuchando.
  - Lo que hizo esto barato fue `ParamSpec`: los widgets nuevos reciben la lista de specs y no
    saben nada de direcciones ni de escalas. Por eso el mismo par de widgets sirve para los
    **cuatro** ecualizadores del proyecto (EQ1, EQ2, y los tipos Graphic/Parametric de Mod/FX)
    aunque sus escalas sean distintas — cada spec trae la suya.

- **2026-09-06 — Un `MutableStateFlow` conflado deduplica, y eso rompe un botón.** El botón de
  "Releer estado" reutiliza el coordinador de recargas, que es un `MutableStateFlow` — y un
  `StateFlow` **descarta un valor igual al que ya tiene**. Dos pulsaciones seguidas son la misma
  petición, así que la segunda no habría disparado nada.
  - Se arregla con un contador dentro de la petición, que las hace distintas. Es un detalle de
    una línea con un síntoma desconcertante: "el botón funciona una vez y luego deja de ir".
  - Lo que **no** se hizo es llamar a `loadFromDump` directamente desde el botón, que habría
    sido más corto: pasar por el coordinador es lo que garantiza que un refresco a mano no se
    solape con una recarga automática, que es el bug de los dumps simultáneos de §4.4.

- **2026-09-06 — Un gesto puede estar roto por dónde vive, no por cómo está calibrado.** Las
  perillas del EQ no respondían, y la tentación era tocar la sensibilidad. El problema real era
  que están dentro de un `Row` con scroll horizontal: **el contenedor se quedaba con el
  arrastre antes de que la perilla lo viera**. Ninguna constante lo habría arreglado.
  - La solución —exigir pulsación mantenida— no es una preferencia de diseño sino lo que hace
    falta para que el gesto reclame el puntero. Que además dé el momento natural para agrandar
    la perilla es suerte, no diseño.
  - La regla que sale: **antes de calibrar un gesto, comprobar quién más lo está escuchando.**

- **2026-09-06 — Lo que se puede probar de un gesto, y lo que no.** `knobValueAt` se sacó fuera
  del composable a propósito: la parte del gesto que puede estar mal **sin que se note mirando
  la pantalla** —saltarse una posición de una lista de 28, acumular error de redondeo— es
  aritmética pura y se prueba en JVM. Lo demás ("se siente bien", "el dedo no la tapa") no se
  puede, y está en "Pendiente por probar" como tal en vez de fingir cobertura.
  - El detalle que el test protege: recalcular **siempre desde el valor de partida del gesto**,
    nunca desde el último emitido. Lo segundo parece equivalente y va acumulando el redondeo
    hasta que el valor se separa del dedo.

- **2026-09-06 — Una nota vieja que era una lectura del nombre, corregida con una fuente.**
  `CH_A`/`CH_B` estaban documentados como "Canal A/B" desde que se extrajo la lista de veinte —
  una traducción del identificador, no un dato. Al necesitar saber cuál era el `AMP` del
  diagrama, dos ficheros de FxFloorboard lo dijeron: **`CH_A` es el PreAmp**
  (`stompBox.cpp:827`, `summaryDialog.cpp:94`).
  - Es el mismo patrón que ya avisa §5 sobre las direcciones, aplicado a un nombre: **una
    lectura plausible de una etiqueta no es una confirmación**. Y esta vez la confirmación
    estaba a un grep de distancia desde el principio.
  - `CH_B` sigue sin explicación —FxFloorboard simplemente lo borra del diagrama— y ahora está
    anotado como "punto de ruteo interno; no se dibuja", que es lo único que se sabe.

- **2026-09-06 — Un gesto sobre un contenedor desplazable tiene dos problemas, no uno.** El
  primero es que el contenedor **robe** el arrastre, y se resuelve exigiendo pulsación mantenida
  para que el gesto reclame el puntero. El segundo es que el contenedor **siga desplazándose**
  con el dedo apoyado encima: el movimiento vertical no significa nada para la perilla, pero el
  scroll sí lo entiende, y la pantalla se va sola justo mientras se intenta afinar un valor.
  - Se arreglan por separado. El primero es del gesto; el segundo hay que decírselo al
    contenedor, que no tiene forma de saber que alguien está ajustando algo dentro.
  - Se resolvió con helpers (`knobAwareVerticalScroll`) en vez de repetir
    `enabled = !adjusting` en cada sitio, y no por comodidad: **el fallo de olvidarse en un
    contenedor no se manifiesta como "falta congelar el scroll", sino como "el valor salta
    raro"**, que apunta al sitio equivocado.
  - Y el arreglo abre su propio riesgo —la bandera encendida para siempre deja el scroll muerto—
    que se cubre con un `DisposableEffect`. Un interruptor global siempre necesita quien lo
    apague cuando su dueño desaparece.

- **2026-09-06 — Una distancia que se mide en dedos no puede estar en píxeles.** La proyección
  de la perilla se levantaba una cantidad fija de **píxeles**, que en una pantalla densa es
  menos distancia física — y lo que tiene que caber debajo es un dedo y una mano, que miden
  milímetros. Pasarlo a `dp` es lo que hace que la separación sea la misma en cualquier
  teléfono; subir el número era lo secundario.
  - Regla corta: **si la magnitud se define por el cuerpo de quien usa la app, va en `dp`.**

- **2026-09-06 — Un gesto puede chocar con otro que no tiene nada que ver con él.** El arrastre
  lateral de las perillas competía con el swipe-to-open del menú hamburguesa, que vive varias
  capas más arriba en el árbol de composición (`ModalNavigationDrawer` envuelve toda la
  pantalla). No era un problema del gesto de la perilla en sí — el `KnobInteraction` que congela
  el scroll no tenía nada que decir aquí, porque el drawer no es un `Scrollable` que ese
  mecanismo cubra.
  - Se resolvió apagando el gesto del drawer entero (`gesturesEnabled = false`) en vez de
    intentar que la perilla también le ganara al drawer como le gana al scroll: el proyecto
    solo tiene un punto de entrada al menú que importe (el botón), así que quitarle el gesto no
    pierde ninguna forma de abrirlo que alguien fuera a usar.
  - La lección para la próxima vez que un gesto nuevo no se comporte: **mirar no solo el
    contenedor inmediato, sino todo lo que envuelve la pantalla** — un `Scaffold`, un
    `Pager`, un `Drawer` — porque cualquiera de ellos puede traer gestos activados por defecto
    que compitan sin que el código de la perilla tenga nada que ver.

- **2026-09-06 — Una key de `pointerInput` no puede depender de algo que el propio gesto
  modifica.** `Modifier.pointerInput(vararg keys)` reinicia su corrutina —y con ella cualquier
  `detectDragGestures*` en curso— cada vez que una key cambia de valor, sea cual sea la razón.
  Si el gesto llama a un callback que hace que una de esas keys cambie, el gesto se cancela a sí
  mismo en marcha.
  - Es fácil de escribir sin darse cuenta: `value` parecía la key correcta porque el detector
    necesita "el valor actual" para calcular el ancla del arrastre — pero **necesitarlo para
    leerlo no es lo mismo que necesitarlo como key**. `rememberUpdatedState` es exactamente la
    herramienta para lo primero sin pagar lo segundo.
  - La regla general: **cualquier valor que un gesto pueda modificar por su propia acción nunca
    debe ser key de `pointerInput`** — solo debe leerse a través de un `rememberUpdatedState`
    (o capturarse una vez al empezar el gesto, si eso basta).
