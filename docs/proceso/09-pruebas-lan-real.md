# Etapa 9: Pruebas en LAN real

## Objetivo

Comprobar que el juego funciona entre computadoras distintas y no solo con varias ventanas en una misma PC. La guía pide generar el ejecutable, copiarlo a cada PC, probar primero con equipos propios (router u hotspot de un celular) y después en las PC del colegio, con 2, 3 y 5 jugadores, y también con pérdida de paquetes UDP simulada (`Config.PERDIDA_UDP_SIMULADA` en 0,1).

Esta etapa no tuvo código nuevo: es una prueba manual hecha el 29/09/2026 en el colegio. Por eso se documenta con lo que se vio y lo que quedó sin probar.

## Qué se hizo

1. Se llevó el juego en un pendrive, como ejecutable (`.exe`) generado con el proyecto, y se copió a las notebooks.
2. Se intentó jugar en la red del colegio, con las computadoras conectadas a la LAN.
3. Como no se pudo, se repitió la prueba con dos notebooks del colegio conectadas al punto de acceso (hotspot) de un celular.
4. En esa red se jugó una carrera completa y se probó la pérdida de paquetes simulada.

## Resultados

| Prueba | Resultado |
|---|---|
| PC del colegio conectadas a la LAN | **No funcionó.** La partida se podía crear, pero al unirse desde otra PC el juego respondía que no se podía encontrar la IP y no se llegaba al lobby |
| 2 notebooks en el hotspot de un celular | **Funcionó.** Una hizo de host y la otra se unió. Se jugó una carrera completa, con resultados y revancha, sin lag ni tirones que se notaran |
| Windows Defender Firewall en las notebooks del hotspot | No apareció ningún cartel pidiendo permiso para Java en la red |
| Pérdida UDP simulada | Con pérdidas parciales el juego siguió normal. Con 100 % (corte total), el jugador afectado salió de la partida por falta de datos, como está diseñado en la [etapa 8](08-robustez-de-red.md), y después volvió a jugar con normalidad |

## Qué pasó en el colegio

Se pudo crear la partida, pero nadie pudo unirse. Eso es lo que se espera cuando algo bloquea las conexiones entrantes hacia la PC del host: el host se conecta a sí mismo por `127.0.0.1`, que no sale a la red, así que crear la partida anda igual. Del lado del invitado, el intento de conexión TCP al puerto 7777 no llega y falla con el mensaje de "No se pudo conectar...".

Las computadoras tenían firewall y el grupo no tenía permisos de administrador en ellas, así que no se pudo desactivar ni crear una regla para los puertos 7777 (TCP) y 7778 (UDP). Con los datos que hay no se puede distinguir si lo que bloquea es el firewall de cada equipo o un aislamiento de la red del colegio entre equipos (la guía menciona las dos posibilidades). No se probó cuál de los dos era.

## Cómo se resolvió

Se usó el hotspot de un celular, que es el plan B que preveía la guía. Ahí no hizo falta tocar ningún firewall (Windows ni siquiera mostró el cartel de permiso) y la partida funcionó.

**Conclusión práctica para la presentación:** la demo en red se puede hacer con el hotspot de un celular. Para usar la red del colegio haría falta que alguien con permisos de administrador abra los puertos TCP 7777 y UDP 7778 en el firewall de cada PC (o que el colegio permita el tráfico entre equipos).

## Qué quedó sin probar

- **Con 3 y con 5 jugadores.** Solo se probó con 2. El caso de 5 es el que más exige al servidor y a la red, así que sigue pendiente.
- **La red del colegio.** No se pudo comprobar en ninguna configuración.
- **Los números de la prueba.** No se midieron latencia ni consumo de red: el resultado "sin lag ni tirones" es una observación al jugar, no una medición.

## Cómo repetir la prueba

1. Generar el ejecutable y copiarlo a cada PC (necesita Java 17 o superior, salvo que el ejecutable lo incluya).
2. Conectar todas las PC a la misma red (router o hotspot de un celular).
3. En la PC del host, `ipconfig` y anotar la dirección IPv4 de la placa conectada a esa red.
4. El host crea la partida; los demás se unen escribiendo esa IP.
5. Para probar pérdida de paquetes, poner `PERDIDA_UDP_SIMULADA` en 0,1 y volver a generar el ejecutable, o usar la tecla F8 en carrera si `DEBUG_RED` está activo (0 %, 20 %, 50 % y 100 %).
6. Si el invitado no puede unirse, revisar primero el firewall del host (TCP 7777 y UDP 7778) y que las dos PC estén en la misma red y no aisladas entre sí.

## Commits

- Los commits de la etapa están en la rama `docs/pruebas-lan`. No hay cambios de código.
