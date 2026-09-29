# Registro de Cambios

Todos los cambios significativos de este proyecto se documentan en este archivo.

El formato se basa en [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/), y este proyecto se adhiere al [Versionado Semántico](https://semver.org/lang/es/).

## [Sin publicar]

### Agregado

- Conexión y lobby por TCP según `docs/PROTOCOLO.md`: paquete `red` con `ServidorPartida` (hilo de aceptación, un lector por cliente y un hilo de lógica que es el único que toca el estado), `ClientePartida` (conexión en segundo plano, cola thread-safe de mensajes y `PING` cada 2 s), `Protocolo`, `ConexionTcp`, `EstadoLobby`, `SesionRed` y `DireccionesLocales`.
- "Crear partida" (pide el nombre y levanta el servidor), "Unirse" (nombre e IP, con mensajes de error claros) y lobby en vivo con elección de auto sin repetir, "listo", botón de iniciar solo para el host y las IPs de la red del host. Al iniciar, cada jugador corre su propia carrera local con su nombre y color.
- Log en consola de cada mensaje de red (`Config.DEBUG_RED`) y constantes de red en `Config`.
- Diseño del protocolo de red en `docs/PROTOCOLO.md`: arquitectura con servidor autoritativo en el host, máquina de estados de la partida, mensajes TCP de texto con sus errores, paquetes UDP binarios `ENTRADA` (11 bytes) y `ESTADO` (132 bytes con 5 autos) con números de secuencia, frecuencias y ancho de banda, interpolación en el cliente, hilos, manejo de fallas y diagramas. Todavía sin código.
- Esqueleto del juego con estética Fórmula 1 (fondo carbono, rojo de largada, bandera de cuadros y pianos): `Main` extiende `Game` y hay cinco pantallas navegables (`MenuPrincipal`, `UnirsePartida`, `Lobby`, `Carrera`, `Resultados`), con ESC para volver.
- `Config` con las constantes globales, `Paleta` con los colores y `Recursos` que centraliza el `AssetManager`, el `SpriteBatch` y el `Skin` armado por código.
- Resolución virtual de 640x360 con `FitViewport` y filtro Nearest; ventana inicial de 1280x720 redimensionable.
- Paquetes `juego`, `vista`, `red`, `pantallas` y `util`, cada uno con su `package-info.java`.
- Circuito provisorio `assets/circuitos/circuito1.tmx` (óvalo de 32x32 con tileset propio en pixel art, 10 checkpoints y 5 lugares de largada), a reemplazar por el de arte manteniendo el formato.
- Modelo del juego sin dependencias gráficas: `Circuito`, `CargadorCircuito` (TiledMap a Circuito), `Superficie`, `EntradaAuto`, `ParametrosAuto` y `Auto` con física arcade (aceleración, freno, reversa, giro según velocidad, derrape leve, pasto y rebote contra muros).
- Vista de la carrera: `VistaCircuito`, `VistaAuto` (monoplaza dibujado con formas) y `CamaraSeguimiento`.
- `Carrera` con un auto manejable (flechas o WASD): simulación a paso fijo con acumulador, dibujo interpolado y HUD con la velocidad.
- Reglas de carrera en `juego` (sin dependencias gráficas, para que las simule el servidor): `Carrera` con estados `CUENTA_REGRESIVA`, `EN_CURSO` y `TERMINADA`, de 1 a 5 autos por id, cuenta regresiva de 3 s con los autos quietos, vueltas validadas por checkpoints en orden (no suma ir marcha atrás ni en contramano), tiempo por vuelta, mejor vuelta y tiempo total, posiciones por vueltas, checkpoint y distancia, cierre de 30 s después de que el primero termina y choques entre autos que se separan y se reparten la velocidad. Se suman `Participante`, `EstadoCarrera` y `ResultadoJugador`.
- Prueba local para 2 jugadores en el mismo teclado (J1 con WASD, J2 con flechas) con pantalla dividida (una cámara por jugador, siempre en zoom 2x) y HUD por jugador (vuelta, posición, tiempo actual y mejor vuelta), cartel de cuenta regresiva y "¡YA!".
- Resultados con datos reales (posición, piloto, tiempo total y mejor vuelta), que se muestran solos al terminar la carrera.
- `Tiempo` para formatear tiempos de carrera y una paleta de colores para distinguir hasta 5 autos.

- Documentación del proceso de desarrollo en `docs/proceso/`: una página por etapa (objetivo, qué se hizo, decisiones, problemas y su resolución, verificación, commits y capturas) y una línea de tiempo, más un PDF que las reúne (`docs/proceso/proceso-de-desarrollo.pdf`) y el script que lo regenera (`generar_pdf.py`).

### Corregido

- Los checkpoints del circuito provisorio van de muro a muro (pista más pasto de escape) y no solo sobre la pista: un auto que se sale al pasto igual los cruza y sus vueltas cuentan, con la desventaja de ir más lento.
- En la prueba local, cada jugador tiene su propia cámara con zoom fijo de 2x y la pantalla queda siempre dividida en dos mitades. Antes había una cámara compartida que seguía al que iba primero cuando se separaban, y el otro quedaba fuera de pantalla.

### Cambiado

- La pantalla `Carrera` pasa a llamarse `PantallaCarrera`, para no chocar con el modelo `juego.Carrera`. "Prueba local" abre 2 jugadores y "Iniciar" en el Lobby, 1 jugador (hasta que haya red).

## [0.1.0] - 2026-09-28

### Agregado

- Proyecto generado con gdx-liftoff (módulos core y lwjgl3).
- Repositorio en GitHub (rama main estable, rama develop para integración, ramas feature/* para funcionalidades).
- Wiki de GitHub con propuesta del proyecto en Markdown.
- README.md con descripción, tecnologías, instrucciones de compilación y ejecución.
- CHANGELOG.md (este archivo).
- .gitignore completo para Gradle, Java, Eclipse, IntelliJ y archivos del sistema.
- CLAUDE.md con contexto, arquitectura y convenciones del proyecto.
- Documentación de propuesta en docs/propuesta/ (PDF y figuras).

### Cambios

- Compilación configurada para Java 17 (LTS).
