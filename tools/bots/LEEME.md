# Verificación de los bots

Programas fuera del juego. Corren desde la raíz del proyecto, en PowerShell:

```powershell
.\gradlew core:classes
$gdx = (Get-ChildItem -Recurse "$env:USERPROFILE\.gradle\caches\modules-2" -Filter "gdx-1.14.2.jar" | Select-Object -First 1).FullName
$cp = "core\build\classes\java\main;$gdx"
New-Item -ItemType Directory -Force build\verificacion | Out-Null
javac -encoding UTF-8 -cp $cp -d build\verificacion tools\bots\PruebaBots.java tools\bots\PruebaSalaBots.java tools\bots\PruebaClasificacion.java tools\bots\PruebaTorneo.java
```

| Programa | Qué comprueba | Cómo se corre |
|---|---|---|
| `PruebaBots` | Mide cada nivel solo en cada circuito (mejor vuelta y golpes contra el muro) y corre una carrera de 8 bots mezclados, con las reglas reales de `Carrera`, sin red y más rápido que el tiempo real. Tarda unos 15 segundos. Un segundo argumento filtra por nombre de circuito | `java '-Dfile.encoding=UTF-8' -cp "$cp;build\verificacion" PruebaBots assets\circuitos` |
| `PruebaSalaBots` | Las reglas de los bots en la sala, contra el servidor real y con clientes reales por TCP: quién puede agregarlos, prioridad de las personas, cambio de escudería y revancha. Corre una carrera en tiempo real, así que tarda más de un minuto. Sale con error si una regla falla | `java '-Dfile.encoding=UTF-8' -cp "$cp;build\verificacion" PruebaSalaBots assets\circuitos` |

`PruebaClasificacion` comprueba que 8 autos en una clasificación no se molesten y recorre con el servidor real sala, clasificación, grilla, carrera y revancha. Tarda más de 3 minutos, en tiempo real: `java '-Dfile.encoding=UTF-8' -cp "$cp;build\verificacion" PruebaClasificacion assets\circuitos`

`PruebaTorneo` comprueba los puntos y los desempates y recorre con el servidor real el calendario, un torneo de 3 carreras en el que alguien se va y otro con clasificación. Tarda unos 8 minutos, en tiempo real: `java '-Dfile.encoding=UTF-8' -cp "$cp;build\verificacion" PruebaTorneo assets\circuitos`

La dificultad se ajusta en `juego/NivelBot`.
