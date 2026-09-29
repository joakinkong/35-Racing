# 35 Racing

Videojuego de carreras 2D multijugador en red local. Entre 2 y 5 jugadores compiten simultáneamente desde sus propias computadoras, cada uno controlando su auto mediante un servidor autoritativo con sincronización por TCP/UDP. Estética arcade retro con vista cenital (top-down).

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
- [CLAUDE.md](CLAUDE.md): contexto del proyecto, stack, arquitectura y convenciones de código.
- [CHANGELOG.md](CHANGELOG.md): historial de cambios y versiones.

## Estado actual

Configuración inicial y estructura del proyecto. Se han generado los módulos core y lwjgl3 con Gradle, configurado el repositorio, creada la Wiki con la propuesta y establecidas las convenciones de desarrollo. El juego no tiene lógica ni contenido aún.

## Licencia

Los recursos de terceros utilizados son de licencia libre (CC0) y se acreditan en el repositorio.
