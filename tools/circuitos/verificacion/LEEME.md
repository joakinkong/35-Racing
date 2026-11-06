# Verificación de circuitos

Programas descartables (sin paquete, fuera del juego) para comprobar los circuitos generados en `assets/circuitos`.
Usan las clases reales del juego, así que hay que compilarlo primero (`.\gradlew compileJava`). Corren desde la raíz del
proyecto, en PowerShell.

```powershell
.\gradlew compileJava
$gdx = (Get-ChildItem -Recurse "$env:USERPROFILE\.gradle\caches\modules-2" -Filter "gdx-1.14.2.jar" | Select-Object -First 1).FullName
$cp = "core\build\classes\java\main;$gdx"
New-Item -ItemType Directory -Force build\verificacion | Out-Null
javac -cp $cp -d build\verificacion tools\circuitos\verificacion\PruebaCircuitos.java tools\circuitos\verificacion\PruebaAtajos.java tools\circuitos\verificacion\PruebaVuelta.java tools\circuitos\verificacion\PruebaCruce.java tools\circuitos\verificacion\PruebaEntorno.java
```

| Programa | Qué comprueba | Cómo se corre |
|---|---|---|
| `PruebaCircuitos` | Los circuitos cargan; los lugares de largada (`Config.MAX_JUGADORES`) y toda la línea central caen sobre pista y sin tocar muro, y dos lugares no se encima; los centros de los checkpoints caen en pista | `java -cp "$cp;build\verificacion" PruebaCircuitos assets\circuitos` |
| `PruebaAtajos` | Con un auto (círculo de radio 10) y la grilla de colisión, la distancia más corta desde la largada a cada punto no es mucho menor que la que hay por la pista. Solo Suzuka da atajos, por el cruce | `java -cp "$cp;build\verificacion" PruebaAtajos assets\circuitos` |
| `PruebaVuelta` | Un bot sigue la línea central con la física real de `Auto` y mide cuánto tarda una vuelta; sirve para calibrar el largo de los circuitos (`SEGUNDOS_VUELTA` en el generador). Un segundo argumento (nombre de un circuito) muestra dónde choca | `java -cp "$cp;build\verificacion" PruebaVuelta assets\circuitos` |
| `PruebaCruce` | En Suzuka, un auto queda en su nivel al recorrer cada tramo, dos autos en niveles distintos no se chocan y dos en el mismo sí; los otros 23 circuitos no tienen zona de cruce. Sale con error si algo falla | `java -cp "$cp;build\verificacion" PruebaCruce assets\circuitos` |
| `PruebaEntorno` | En los 24 circuitos, ninguna decoración toca la pista ni su muro, se sale del mapa o pisa a otra, y cada tipo de circuito tiene lo suyo. Sale con error si algo falla | `java -cp "$cp;build\verificacion" PruebaEntorno assets\circuitos` |

`CapturaAutos` guarda un auto de cada escudería, grande y a la escala de la carrera. Se compila y se corre como `CapturaVista`, con la carpeta de salida como único argumento.

Si cambió algo en `assets`, antes de capturar corré `.\gradlew lwjgl3:classes`: el classpath de abajo lee la copia de `lwjgl3\build\resources`.

`CapturaVista` abre la ventana del juego oculta (con antialiasing) y guarda una captura de la `VistaCircuito` real
centrada en un punto de la línea central. Necesita el classpath completo del módulo `lwjgl3`:

```powershell
$cp = (.\gradlew --offline -q --init-script tools\circuitos\verificacion\classpath.gradle lwjgl3:printCp | Select-String '^CP=').Line.Substring(3)
$cp = "core\build\classes\java\main;$cp"
javac -cp $cp -d build\verificacion tools\circuitos\verificacion\CapturaVista.java
# argumentos: carpeta de circuitos, carpeta de salida, y uno o más circuito:índice_de_muestra[:zoom[:abajo]]
# con "abajo", el auto de referencia se dibuja debajo del puente (suzuka:1322:1.25:abajo)
java -cp "$cp;build\verificacion" CapturaVista assets\circuitos build\capturas monaco:772 monza:494
```
