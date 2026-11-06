# 35 Racing

Videojuego de carreras 2D multijugador en red local. Entre 2 y 8 jugadores compiten simultáneamente desde sus propias computadoras, cada uno controlando su auto mediante un servidor autoritativo con sincronización por TCP/UDP. Vista cenital (top-down), con la estética de PIT WALL, la página de estadísticas de Fórmula 1 del proyecto.

**Integrantes:** Joaquin Barreira, Madai Andacaba, Antonella Vivacqua, Joaquin Rodrigues, Mateo Guinsburg

## Tecnologías

- **Lenguaje:** Java 17 (LTS)
- **Framework:** LibGDX (generado con gdx-liftoff)
- **Plataformas:** Windows, Linux, macOS (LWJGL3)
- **Red:** Sockets TCP/UDP de java.net (sin dependencias externas)
- **Control de versiones:** Git y GitHub (repositorio, wiki, CHANGELOG)
- **IDE:** Eclipse

## Cómo compilar y ejecutar

### Requisitos

- JDK 17 o superior
- Git
- Eclipse IDE for Java Developers (o ejecutar por consola con Gradle)

### En Eclipse

1. Cloná el repositorio:
   ```
   git clone https://github.com/joakinkong/35-Racing.git
   ```

2. Importá el proyecto: File > Import > Gradle > Existing Gradle Project, seleccioná la carpeta raíz.

3. Esperá a que termine el "Importing Gradle project".

4. Hacé clic derecho en lwjgl3 > Run As > Java Application y seleccioná Lwjgl3Launcher.

### Por consola

```
gradlew lwjgl3:run
```

O en PowerShell:

```
.\gradlew lwjgl3:run
```

## Documentación

- [Propuesta del Proyecto](https://github.com/joakinkong/35-Racing/wiki/Propuesta-del-Proyecto-%E2%80%90-35-Racing): descripción, alcance, arquitectura y tácticas de trabajo.
- [Protocolo de red](docs/PROTOCOLO.md): cómo se comunican las computadoras durante una partida (TCP, UDP, mensajes, hilos y fallas).
- [Cómo fuimos trabajando](docs/proceso/README.md): documentación del proceso de desarrollo, etapa por etapa, con decisiones, problemas, pruebas y capturas. También está reunida en un [PDF](docs/proceso/proceso-de-desarrollo.pdf).
- [CLAUDE.md](CLAUDE.md): contexto del proyecto, stack, arquitectura y convenciones de código.
- [CHANGELOG.md](CHANGELOG.md): historial de cambios y versiones.

## Estado actual

Etapa 10 completada: circuitos vectoriales, física más exigente y HUD estilo Fórmula 1. La etapa 8 había dejado lista la red: la carrera se juega en red y las fallas de red se avisan con mensajes claros. Se crea una partida, los demás se unen por IP, esperan en un lobby en vivo y corren una carrera sincronizada (el servidor la simula y cada PC dibuja lo que recibe), con resultados y revancha. Hay menú y pantallas navegables con estética de Fórmula 1, un auto con física arcade sobre un circuito provisorio y las reglas de una carrera completa (cuenta regresiva, vueltas por checkpoints, posiciones, choques y resultados). Se puede probar con 2 jugadores en el mismo teclado desde "Prueba local" (J1 con WASD y J2 con flechas).

El protocolo de red ya está diseñado en [docs/PROTOCOLO.md](docs/PROTOCOLO.md). Los 24 circuitos de la temporada 2026 ya son definitivos (archivos vectoriales generados desde trazados SVG, ver la [etapa 10](docs/proceso/10-circuitos-vectoriales-y-hud.md)). Lo que falta: las pruebas en varias PC de la red del colegio y los sprites. La red ya avisa con un mensaje claro de cada falla (ver la [etapa 8](docs/proceso/08-robustez-de-red.md)). El detalle de cada etapa está en [docs/proceso](docs/proceso/README.md).

## Licencia

Los recursos de terceros utilizados son de licencia libre (CC0) y se acreditan en el repositorio.
