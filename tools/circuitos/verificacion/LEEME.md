# Verificación de circuitos

Programas descartables (sin paquete, fuera del juego) para comprobar los circuitos generados en `assets/circuitos`.
Usan las clases reales del juego, así que hay que compilarlo primero (`.\gradlew compileJava`). Corren desde la raíz del
proyecto, en PowerShell.

```powershell
.\gradlew compileJava
$gdx = (Get-ChildItem -Recurse "$env:USERPROFILE\.gradle\caches\modules-2" -Filter "gdx-1.14.2.jar" | Select-Object -First 1).FullName
$cp = "core\build\classes\java\main;$gdx"
New-Item -ItemType Directory -Force build\verificacion | Out-Null
javac -cp $cp -d build\verificacion tools\circuitos\verificacion\PruebaCircuitos.java tools\circuitos\verificacion\PruebaAtajos.java tools\circuitos\verificacion\PruebaVuelta.java
```

| Programa | Qué comprueba | Cómo se corre |
|---|---|---|
| `PruebaCircuitos` | Los circuitos cargan; los 5 lugares de largada y toda la línea central caen sobre pista y sin tocar muro; los centros de los checkpoints caen en pista | `java -cp "$cp;build\verificacion" PruebaCircuitos assets\circuitos` |
| `PruebaAtajos` | Con un auto (círculo de radio 10) y la grilla de colisión, la distancia más corta desde la largada a cada punto no es mucho menor que la que hay por la pista. Solo Suzuka da atajos, por el cruce | `java -cp "$cp;build\verificacion" PruebaAtajos assets\circuitos` |
| `PruebaVuelta` | Un bot sigue la línea central con la física real de `Auto` y mide cuánto tarda una vuelta; sirve para calibrar el largo de los circuitos (`SEGUNDOS_VUELTA` en el generador). Un segundo argumento (nombre de un circuito) muestra dónde choca | `java -cp "$cp;build\verificacion" PruebaVuelta assets\circuitos` |

`CapturaVista` abre la ventana del juego oculta (con antialiasing) y guarda una captura de la `VistaCircuito` real
centrada en un punto de la línea central. Necesita el classpath completo del módulo `lwjgl3`:

```powershell
$cp = (.\gradlew --offline -q --init-script tools\circuitos\verificacion\classpath.gradle lwjgl3:printCp | Select-String '^CP=').Line.Substring(3)
$cp = "core\build\classes\java\main;$cp"
javac -cp $cp -d build\verificacion tools\circuitos\verificacion\CapturaVista.java
# argumentos: carpeta de circuitos, carpeta de salida, y uno o más circuito:índice_de_muestra[:zoom]
java -cp "$cp;build\verificacion" CapturaVista assets\circuitos build\capturas monaco:772 monza:494
```
