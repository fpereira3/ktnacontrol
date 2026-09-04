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

## En progreso


## Pendiente por probar

Lo que está **implementado pero todavía no confirmado contra el amplificador real**. Cada
entrada dice en qué consiste la prueba y qué resultado cuenta como éxito, siguiendo la
disciplina de siempre (CLAUDE.md §5): nada se da por bueno hasta oírlo o verlo en el amp.

1. **Tipo de MOD** (`60 00 01 01`, catálogo `ModFxType`, 31 valores).
   - **Prueba**: con el efecto MOD encendido, cambiar su tipo desde el desplegable de la
     tarjeta de la app (por ejemplo, de "2x2 Chorus" a "Phaser"). Por separado, cambiar el
     color de MOD directamente con el botón físico del panel.
   - **Resultado esperado si funciona** (mismo patrón que Booster, ya confirmado):
     - El carácter del efecto cambia de forma audible y reconocible al tipo elegido — un
       Chorus se oye como modulación en coro, un Phaser como un barrido de fase. Esto es lo
       único que decide; un GET que devuelve lo escrito no prueba nada (§5).
     - Al pulsar el botón físico de color de MOD, el desplegable de tipo de la app cambia
       solo al tipo que tiene asignado ese color (reporte espontáneo, con edit mode activo).
   - **Si falla** (no cambia el sonido, o el valor rebota al anterior): es la firma de una
     dirección de solo reporte, igual que pasó con `60 00 06 5C` — documentar y reabrir la
     duda de si hay que escribir en el slot de color (`60 00 06 27`/`28`/`29`) en su lugar.

2. **Tipo de FX** (`60 00 03 01`, mismo catálogo `ModFxType`).
   - **Prueba y resultado esperado**: igual que el punto 1, pero con el efecto FX y sus
     colores (`60 00 06 2A`/`2B`/`2C`).

3. **Tipo de Delay 1** (`60 00 05 01`, catálogo `DelayType`, 11 valores).
   - **Prueba**: con el efecto Delay encendido, cambiar su tipo desde el desplegable de la
     app (por ejemplo, de "Digital" a "Analog"). Por separado, cambiar el color de Delay con
     el botón físico del panel.
   - **Resultado esperado si funciona**: un delay analógico suena claramente distinto a uno
     digital — más cálido, con más pérdida de tonos agudos en las repeticiones —, así que el
     cambio de carácter debe ser reconocible al oído. El desplegable debe sincronizarse solo
     al cambiar de color en el panel físico (reporte espontáneo).
   - **Si falla**: mismo diagnóstico que en los puntos 1 y 2 — sospechar de dirección de solo
     reporte y considerar el slot de color (`60 00 06 2D`/`2E`/`2F`) como alternativa.

4. **Tipo de Reverb** (`60 00 05 41`, catálogo `ReverbType`, 7 valores).
   - **Prueba**: con el efecto Reverb encendido, cambiar su tipo desde el desplegable de la
     app (por ejemplo, de "Plate" a "Hall 1"). Por separado, cambiar el color de Reverb con
     el botón físico del panel.
   - **Resultado esperado si funciona**: un reverb de plate suena metálico y denso desde el
     principio; uno de hall tiene una cola más larga y difusa, con un ataque más suave — la
     diferencia debe notarse de inmediato al escuchar. Igual que arriba, el desplegable debe
     seguir los cambios de color hechos en el panel físico.
   - **Si falla**: mismo diagnóstico; la alternativa sería el slot de color
     (`60 00 06 30`/`31`/`32`).

5. **Los 9 parámetros internos de Booster** (`60 00 00 10`–`18`, CLAUDE.md §5.2). On/Off y
   Type ya están confirmados; lo que sigue prueba los siete nuevos.
   - **Drive** (`60 00 00 12`, `0..120`).
     - **Prueba**: mover el slider de Drive de 0 a 120 con Booster encendido.
     - **Resultado esperado si funciona**: el nivel de distorsión/saturación del Booster
       aumenta de forma audible y progresiva — de un sonido limpio a uno claramente saturado.
   - **Bottom y Tone** (`60 00 00 13`/`14`, `-50..+50` centrados en cero).
     - **Prueba**: mover cada slider de un extremo a otro por separado, con Booster encendido
       y a un Drive intermedio para que el timbre se note.
     - **Resultado esperado si funciona**: Bottom cambia la coloración de graves del boost
       (más grave hacia `-50`, menos hacia `+50`, o viceversa); Tone hace lo mismo con los
       agudos. En `0` debería sonar como el punto neutro documentado por las fuentes.
   - **Solo Sw y Solo Level** (`60 00 00 15`/`16`).
     - **Prueba**: activar el switch de Solo y mover el slider de Solo Level de 0 a 100.
     - **Resultado esperado si funciona**: activar Solo cambia el nivel de salida del Booster
       de forma audible (es el "modo solo" para resaltar el instrumento), y el slider ajusta
       cuánto sube ese nivel.
   - **Effect Level y Direct Mix** (`60 00 00 17`/`18`).
     - **Prueba**: con Booster encendido, mover Effect Level de 0 a 100 y por separado Direct
       Mix de 0 a 100.
     - **Resultado esperado si funciona**: Effect Level cambia cuánta señal procesada por el
       Booster se oye (en 0, el efecto casi desaparece); Direct Mix cambia el balance entre
       la señal procesada y la señal limpia sin procesar — en un extremo debería oírse solo
       una, en el otro solo la otra.
   - **Si falla cualquiera** (no cambia el sonido, o el valor rebota al anterior): mismo
     diagnóstico que en los demás casos — dirección de solo reporte, como `60 00 06 5C`.

6. **Recarga completa del estado al cambiar de canal.**
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
     en `watchChannelChanges`, no en la lectura del canal en sí.

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
   Mod y FX"), con los 31 bloques indexados, cuatro tipos detallados parámetro a parámetro y
   la regla `FX = Mod + 0x0200` verificada. Cablear un tipo es ahora trabajo mecánico, salvo
   por las seis anomalías que la extracción dejó señaladas — la primera de las cuales,
   `Pre Delay` en pasos de 0,5 ms, **obliga a extender `LevelScale` antes** de tocar 2x2
   Chorus, Pitch Shifter o Harmonist.
3. **Controles sin perilla física en el panel**: Noise Gate, Solo, Contour, posición IN/OUT de
   EQ1 y EQ2 (antes o después del preamp), selección de cadena de efectos (chain).
4. **Guardado de presets** — escribir el estado actual editado a un canal específico (p. ej.
   1A), equivalente a grabar un preset desde el panel pero hecho desde la app. `AmpState` ya
   cubre la lectura del estado actual; falta el guardado (`7F 00 01 04`, sin investigar).
5. **Import/export de archivos `.tsl`**, usando los offsets de
   `reference/TuxKatana/params/presets_addrs.yaml`.
6. **UI real de control** — pantallas por dominio (`AmpScreen`, `EffectsScreen`,
   `PresetsScreen`, ver CLAUDE.md §4.2) en vez de la pantalla de diagnóstico actual, sin
   exponer direcciones SysEx a la capa de Compose. A intercalar una vez que el bloque 2 tenga
   al menos 2-3 efectos completos, para no rehacer la UI repetidamente.

## Notas y decisiones técnicas

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
