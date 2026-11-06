# Verificación de la interfaz

`CapturaPantallas` abre el juego real en una ventana oculta, recorre las pantallas y guarda una captura de cada una: menú, crear, unirse, prueba local, sala, carrera en red, carrera con pantalla dividida y resultados. La sala y la carrera en red usan el servidor y una partida llena de clientes de verdad en los puertos del juego, así que no tiene que haber otra partida abierta en la PC. Tarda unos 20 segundos.

Se corre desde la raíz del proyecto, en PowerShell:

```powershell
.\gradlew lwjgl3:classes
$cp = (.\gradlew --offline -q --init-script tools\circuitos\verificacion\classpath.gradle lwjgl3:printCp | Select-String '^CP=').Line.Substring(3)
$cp = "core\build\classes\java\main;$cp"
New-Item -ItemType Directory -Force build\verificacion,build\capturas | Out-Null
javac -encoding UTF-8 -cp $cp -d build\verificacion tools\interfaz\CapturaPantallas.java
java -cp "$cp;build\verificacion" CapturaPantallas "$PWD\build\capturas"
```

Las fuentes de `assets/fuentes` se generan con `tools/fuentes/generar_fuentes.py`; su encabezado explica de dónde bajar los `.ttf`.

Con un segundo argumento, `clasificacion` o `torneo`, corre solo esa partida contra bots, en tiempo real (unos 2 y 4 minutos).
