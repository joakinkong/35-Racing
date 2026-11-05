---
name: circuitos
description: Crear, regenerar, ajustar y verificar los circuitos de 35 Racing (JSON vectoriales en assets/circuitos, generados desde trazados SVG con tools/circuitos/generar_circuitos.py). Usar cuando pidan un circuito nuevo, cambiar el ancho de la pista, el pasto, los callejeros o la duración de la vuelta, o comprobar que los circuitos estén bien.
---

# Circuitos de 35 Racing

Los circuitos son archivos JSON vectoriales, no mapas de tiles. El formato está en `docs/FORMATO_CIRCUITO.md`: léelo
antes de tocar nada. Resumen: una línea central cerrada (un punto cada 8 px), el ancho de asfalto y el de pasto en cada
punto, los checkpoints y los 5 lugares de largada. El juego los dibuja con círculos apilados (`VistaCircuito`) y rasteriza
una grilla de colisión de 4 px al cargarlos (`CargadorCircuito`). Ya no se usa Tiled.

## Regla de trabajo

Cambiá solo lo que piden. La forma de los circuitos (el trazado y cómo se ven los bordes) es sensible: ya hubo un cambio
"de mejora" no pedido que empeoró Mónaco y hubo que deshacerlo. Si algo parece mejorable, proponelo con números y
esperá la confirmación. No hagas commits ni push (los hace cada integrante).

## Flujo habitual

1. **Generar** (desde la raíz del proyecto, en PowerShell):
   ```powershell
   python tools\circuitos\generar_circuitos.py                 # los 24
   python tools\circuitos\generar_circuitos.py monaco monza    # solo algunos
   python tools\circuitos\generar_circuitos.py --vista tools\circuitos\vistas   # además un PNG por circuito
   ```
   Los SVG salen por defecto de `~/Desktop/proyecto f1/f1-circuits-svg-main/circuits/detailed/white` (`--svg` para
   cambiarlo). Solo usa la biblioteca estándar de Python. Tarda unos 10 segundos.
2. **Mirar** el resultado. Una línea por circuito con tamaño, ancho mínimo y máximo de pista, largo y checkpoints, y un
   `AVISO` si dos tramos quedan con los bordes a menos de lo que mide el muro. Abrí los PNG de `--vista`: el pasto es
   verde claro, el muro rojo, el asfalto gris, la meta blanca y los checkpoints amarillos.
3. **Verificar** con los programas de `tools/circuitos/verificacion` (ver su `LEEME.md`): que carguen, que no haya atajos
   (solo Suzuka tiene, por su cruce), que un bot dé vueltas de unos 60 s de promedio, y capturas de la vista real.
4. **Compilar y probar** en el juego: `.\gradlew compileJava`, y desde "Prueba local" elegir el circuito.

## Parámetros del generador (arriba de `generar_circuitos.py`)

| Parámetro | Qué hace |
|---|---|
| `ANCHO_MAX` / `ANCHO_MIN` | Ancho del asfalto: lo más ancho que permita el circuito, y nunca menos que el mínimo (se escalan en los cortos) |
| `FACTOR_ANCHO_CORTO`, `KM_CORTO`, `KM_LARGO` | Los circuitos de `KM_CORTO` o menos tienen esa fracción del ancho; desde `KM_LARGO`, el ancho completo |
| `ESCAPE` | Pasto máximo a cada lado. Es lo que sobra entre el asfalto y el muro; donde no sobra, vale 0 y el muro queda pegado |
| `MURO` | Grosor de la línea de muro (solo se dibuja; hacia afuera todo es sólido en la colisión) |
| `SEPARACION_MINIMA`, `ARCO_MINIMO`, `ARCO_REPULSION` | Tramos más cerca que `ANCHO_MIN + SEPARACION_MINIMA` entre ejes se separan; los que están a menos de `ARCO_MINIMO` px de arco son la misma curva |
| `FACTOR_CURVA` | El ancho no pasa de ese múltiplo del radio de la curva, para que el borde de adentro no se pliegue |
| `CALLEJEROS` | Los 9 circuitos callejeros: el pasto pasa a ser asfalto y el muro queda pegado. Se achican con `FACTOR_ANCHO_CALLEJERO` |
| `SEGUNDOS_VUELTA`, `SEGUNDOS_POR_KM`, `VELOCIDAD_MEDIA` | Largo del circuito en px = duración × velocidad media. `VELOCIDAD_MEDIA` es 0,78 de `ParametrosAuto.VELOCIDAD_MAX` |

Si se cambia la física del auto (`ParametrosAuto`) o los anchos, la duración de la vuelta cambia: volvé a correr
`PruebaVuelta` y ajustá `SEGUNDOS_VUELTA` hasta que el promedio dé cerca de 60 s. Después de tocar cualquier parámetro hay
que regenerar los 24 circuitos.

## Agregar un circuito nuevo

1. Conseguir el trazado SVG "detailed" (f1-circuits-svg, de Jules Roy, CC BY 4.0: mantené la atribución).
2. Sumarlo a la lista `CIRCUITOS` del generador con su id de SVG y sus kilómetros reales. El nombre del archivo sale del
   id (`spa-francorchamps-4` da `spa_francorchamps`). Si es callejero, agregarlo también a `CALLEJEROS`.
3. Sumar su `InfoCircuito` a `Catalogo.CIRCUITOS` (mismo `archivo`, Gran Premio, ciudad, país, km y curvas): de ahí salen el
   lobby, la prueba local y el servidor. El orden de esa lista es el que viaja por red.
4. Generar, mirar la vista previa, verificar y probarlo en el juego.

## Qué no hacer

- No editar los JSON a mano: se pisan al regenerar. Todo cambio va en el generador.
- No agregar pasto a los callejeros ni quitárselo a los permanentes sin que lo pidan.
- No abrir ni "suavizar" esquinas del trazado: se probó (limitar la curvatura) y empeoró el dibujo.
