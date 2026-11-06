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

Etapa 16 completada. Se crea una partida, los demás se unen por IP, esperan en una sala en vivo y corren una carrera sincronizada: el servidor la simula y cada PC dibuja lo que recibe. La carrera termina con resultados y revancha. También se puede probar sin red desde "Prueba local", con 1 jugador o con 2 en el mismo teclado (J1 con WASD y J2 con flechas).

- **Hasta 8 autos**, con grilla escalonada como en la F1.
- **Bots** en tres niveles (fácil, medio y difícil) para completar la grilla. Se puede jugar una persona contra bots.
- **Clasificación opcional** de una vuelta: todos corren a la vez, cada uno sin ver a los demás, y los tiempos arman la grilla.
- **Torneos** con el calendario que elija el host y puntos acumulados. El último de cada carrera siempre suma 1.
- **24 circuitos** de la temporada 2026, con torre de tiempos y mapa en vivo.
- **Estética de PIT WALL** en toda la interfaz.

Falta probar la clasificación, los bots y los torneos entre varias PC y con personas de verdad, y ajustar el nivel difícil con esos tiempos. El protocolo está en [docs/PROTOCOLO.md](docs/PROTOCOLO.md) y el detalle de cada etapa, en [docs/proceso](docs/proceso/README.md).

## Licencia

Los recursos de terceros utilizados son de licencia libre (CC0) y se acreditan en el repositorio.
