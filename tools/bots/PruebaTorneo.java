import ar.edu.et35.racing.juego.CargadorCircuito;
import ar.edu.et35.racing.juego.Catalogo;
import ar.edu.et35.racing.juego.FilaTorneo;
import ar.edu.et35.racing.juego.NivelBot;
import ar.edu.et35.racing.juego.ResultadoJugador;
import ar.edu.et35.racing.juego.Torneo;
import ar.edu.et35.racing.red.EstadoTorneo;
import ar.edu.et35.racing.red.Protocolo;
import ar.edu.et35.racing.red.Protocolo.Mensaje;
import ar.edu.et35.racing.red.cliente.ClientePartida;
import ar.edu.et35.racing.red.servidor.ServidorPartida;
import ar.edu.et35.racing.util.Config;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Programa de verificación (fuera del juego) del torneo. Primero, sin red: los puntos y los desempates. Después, contra el
 * servidor real y con clientes por TCP: el calendario en la sala, un torneo de 3 carreras en el que alguien se va, y otro con
 * clasificación, en tiempo real (unos 8 minutos). Si algo falla, sale con error. Ver tools/bots/LEEME.md.
 */
public class PruebaTorneo {
    private static final long ESPERA_MS = 3000;
    private static final long ESPERA_FASE_MS = 200_000;

    public static void main(String[] args) throws Exception {
        probarPuntos();
        probarDesempates();
        probarAbandono();
        probarServidor(Path.of(args[0]));
        System.out.println("TODO OK");
        System.exit(0);
    }

    private static void probarPuntos() {
        comprobar(reparto(2).equals(List.of(2, 1)), "con 2 autos: 2 y 1");
        comprobar(reparto(4).equals(List.of(6, 4, 2, 1)), "con 4 autos: 6, 4, 2 y 1");
        comprobar(reparto(8).equals(List.of(15, 12, 10, 8, 6, 4, 2, 1)), "con 8 autos: 15, 12, 10, 8, 6, 4, 2 y 1");
        comprobar(Config.PUNTOS_DESDE_EL_ULTIMO.length == Config.MAX_AUTOS_TABLA_INVERSA, "la tabla invertida cubre hasta 8 autos");
        comprobar(reparto(12).equals(List.of(25, 18, 15, 12, 10, 8, 6, 4, 2, 1, 0, 0)), "con más de 8 autos: tabla de la F1, puntúan los 10 primeros");
        List<Integer> veintidos = reparto(Config.MAX_JUGADORES);
        comprobar(veintidos.size() == 22 && veintidos.get(0) == 25 && veintidos.get(21) == 0, "con 22 autos: gana 25 y el último no suma");
    }

    private static List<Integer> reparto(int autos) {
        List<Integer> puntos = new ArrayList<>();
        for (int posicion = 1; posicion <= autos; posicion++) {
            puntos.add(Torneo.puntosPorPosicion(posicion, autos));
        }
        return puntos;
    }

    /** Un resultado en ese orden de llegada, con tiempos cualesquiera. */
    private static List<ResultadoJugador> llegada(int... ids) {
        List<ResultadoJugador> lista = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            lista.add(new ResultadoJugador(i + 1, ids[i], "J" + ids[i], 60f + i, 20f + i));
        }
        return lista;
    }

    private static void probarDesempates() {
        Torneo suma = torneoDeCuatro();
        suma.sumarCarrera(llegada(0, 1, 2, 3), Set.of());
        suma.sumarCarrera(llegada(3, 2, 1, 0), Set.of());
        comprobar(puntosDe(suma, 0) == 6 + 1 && puntosDe(suma, 3) == 1 + 6, "las carreras se acumulan: 6 + 1");
        comprobar(puntosDe(suma, 1) == 4 + 2 && puntosDe(suma, 2) == 2 + 4, "las carreras se acumulan: 4 + 2");

        Torneo puntaje = torneoDeCuatro();
        puntaje.sumarCarrera(llegada(1, 2, 3, 0), Set.of());
        puntaje.sumarCarrera(llegada(0, 2, 3, 1), Set.of());
        puntaje.sumarCarrera(llegada(3, 2, 1, 0), Set.of());
        FilaTorneo lider = puntaje.tabla().get(0);
        comprobar(lider.id() == 2 && lider.puntos() == 12 && lider.victorias() == 0, "manda el puntaje: 12 puntos sin ganar van primeros");

        // En los dos desempates, el que debe ir adelante se inscribió después: no puede estar ahí por casualidad.
        Torneo victoria = torneoDeCuatro();
        victoria.sumarCarrera(llegada(2, 1, 3, 0), Set.of());
        victoria.sumarCarrera(llegada(3, 1, 0, 2), Set.of());
        List<FilaTorneo> porVictoria = victoria.tabla();
        comprobar(porVictoria.get(0).id() == 3 && porVictoria.get(1).id() == 1 && porVictoria.get(0).puntos() == 8 && porVictoria.get(1).puntos() == 8,
            "a igual puntaje, va adelante el que ganó una carrera");

        Torneo segundo = torneoDeCuatro();
        segundo.sumarCarrera(llegada(0, 3, 1, 2), Set.of());
        segundo.sumarCarrera(llegada(0, 2, 1, 3), Set.of());
        segundo.sumarCarrera(llegada(0, 2, 1, 3), Set.of());
        List<FilaTorneo> porSegundo = segundo.tabla();
        comprobar(porSegundo.get(2).id() == 3 && porSegundo.get(3).id() == 1 && porSegundo.get(2).puntos() == 6 && porSegundo.get(3).puntos() == 6,
            "a igual puntaje y victorias, va adelante el que tiene más segundos puestos");

        Torneo tercero = torneoDe(5);
        tercero.sumarCarrera(llegada(0, 1, 4, 2, 3), Set.of());
        tercero.sumarCarrera(llegada(0, 1, 3, 2, 4), Set.of());
        tercero.sumarCarrera(llegada(0, 1, 3, 2, 4), Set.of());
        List<FilaTorneo> porTercero = tercero.tabla();
        comprobar(porTercero.get(3).id() == 4 && porTercero.get(4).id() == 2 && porTercero.get(3).puntos() == 6 && porTercero.get(4).puntos() == 6,
            "si también empatan en segundos, va adelante el que tiene más terceros");

        Torneo cuarto = torneoDe(6);
        cuarto.sumarCarrera(llegada(0, 1, 2, 5, 3, 4), Set.of());
        cuarto.sumarCarrera(llegada(0, 1, 2, 4, 3, 5), Set.of());
        cuarto.sumarCarrera(llegada(0, 1, 2, 4, 3, 5), Set.of());
        List<FilaTorneo> porCuarto = cuarto.tabla();
        comprobar(porCuarto.get(4).id() == 5 && porCuarto.get(5).id() == 3 && porCuarto.get(4).puntos() == 6 && porCuarto.get(5).puntos() == 6,
            "y así sigue: si empatan en terceros, va adelante el que tiene más cuartos");

        Torneo iguales = torneoDeCuatro();
        iguales.sumarCarrera(llegada(2, 1, 0, 3), Set.of());
        iguales.sumarCarrera(llegada(1, 2, 0, 3), Set.of());
        List<FilaTorneo> porEntrada = iguales.tabla();
        comprobar(porEntrada.get(0).id() == 1 && porEntrada.get(1).id() == 2, "iguales en todo, va adelante el que entró antes a la sala");
    }

    private static Torneo torneoDeCuatro() {
        return torneoDe(4);
    }

    private static Torneo torneoDe(int autos) {
        Torneo torneo = new Torneo(List.of(0, 1, 2));
        for (int id = 0; id < autos; id++) {
            torneo.inscribir(id, "J" + id);
        }
        return torneo;
    }

    private static int puntosDe(Torneo torneo, int id) {
        return torneo.tabla().stream().filter(f -> f.id() == id).findFirst().orElseThrow().puntos();
    }

    private static void probarAbandono() {
        Torneo torneo = torneoDeCuatro();
        torneo.sumarCarrera(llegada(0, 1, 2, 3), Set.of());
        // El 0 ya tenía 6; se va y queda último en la siguiente, que largaron los 4.
        torneo.sumarCarrera(llegada(1, 2, 3, 0), Set.of(0));
        comprobar(puntosDe(torneo, 0) == 6, "el que abandona conserva sus puntos y esta carrera no le suma");
        comprobar(puntosDe(torneo, 1) == 10, "el reparto es por los que largaron: 4 + 6");
        // Ya no corre más: los que quedan siguen sumando según cuántos largan ahora, que son 3.
        torneo.sumarCarrera(llegada(3, 2, 1), Set.of());
        comprobar(puntosDe(torneo, 3) == 1 + 2 + 4, "con 3 autos el ganador suma 4");
        comprobar(puntosDe(torneo, 0) == 6, "el que se fue sigue en la tabla");
    }

    private static void probarServidor(Path carpeta) throws Exception {
        ServidorPartida servidor = ServidorPartida.crear(0, 0,
            archivo -> CargadorCircuito.cargarDesdeTexto(Files.readString(carpeta.resolve(archivo + ".json")), Config.MAX_JUGADORES));
        ClientePartida host = new ClientePartida("Host");
        host.conectar("127.0.0.1", servidor.puerto());
        esperar(host, () -> host.lobby().jugadores().size() == 1, "el host entra");
        ClientePartida invitado = new ClientePartida("Invitado");
        invitado.conectar("127.0.0.1", servidor.puerto());
        esperar(invitado, () -> invitado.lobby().jugadores().size() == 2, "el invitado entra");

        int spielberg = Catalogo.indiceDeCircuito("spielberg");
        int monza = Catalogo.indiceDeCircuito("monza");
        host.elegirCircuito(spielberg);
        host.elegirTorneo(true);
        esperar(host, () -> host.lobby().torneo() && host.lobby().calendario().equals(List.of(spielberg)), "el calendario arranca con el circuito elegido");

        invitado.agregarCircuito(monza);
        invitado.elegirTorneo(false);
        host.agregarCircuito(monza);
        host.agregarCircuito(spielberg);
        host.agregarCircuito(monza);
        esperar(host, () -> host.lobby().calendario().equals(List.of(spielberg, monza, spielberg, monza)), "se puede repetir un circuito y un invitado no toca el calendario");
        comprobar(host.lobby().torneo(), "un invitado no cambia el modo");
        host.quitarCircuito(3);
        host.quitarCircuito(99);
        esperar(host, () -> host.lobby().calendario().equals(List.of(spielberg, monza, spielberg)), "quitar saca ese lugar e ignora uno que no existe");
        esperar(invitado, () -> invitado.lobby().calendario().equals(List.of(spielberg, monza, spielberg)), "el invitado ve el calendario");

        host.agregarBot(NivelBot.DIFICIL);
        host.agregarBot(NivelBot.FACIL);
        host.elegirVueltas(Config.MIN_VUELTAS);
        host.marcarListo(true);
        invitado.marcarListo(true);
        esperar(host, () -> host.lobby().sePuedeIniciar() && host.lobby().jugadores().size() == 4 && host.lobby().vueltas() == Config.MIN_VUELTAS, "la sala está lista");
        esperar(invitado, () -> invitado.lobby().jugadores().size() == 4, "el invitado ve a los bots");
        int idHost = host.miId();
        int idInvitado = invitado.miId();

        host.iniciar();
        Protocolo.Cuenta primera = Protocolo.leerCuenta(mensaje(host, invitado, Protocolo.CUENTA_REGRESIVA));
        comprobar(host.torneo() != null && host.torneo().carrera() == 1 && host.torneo().total() == 3, "el cliente sabe que es la carrera 1 de 3");
        comprobar(primera.circuito().equals("spielberg"), "la primera carrera es la primera del calendario");
        comprobar(host.torneo().tabla().size() == 4 && host.torneo().tabla().get(0).puntos() == 0, "todos arrancan con 0 puntos");

        invitado.revancha();
        invitado.siguienteCarrera();
        List<ResultadoJugador> resultados1 = Protocolo.leerResultados(mensaje(host, invitado, Protocolo.RESULTADOS));
        EstadoTorneo despues1 = host.torneo();
        comprobar(despues1.carrera() == 1 && despues1.siguiente() == monza, "la tabla llega antes que los resultados y dice cuál sigue");
        comprobar(despues1.tabla().get(0).puntos() == 6, "el ganador de una carrera de 4 suma 6");
        comprobar(despues1.fila(resultados1.get(0).id()).victorias() == 1, "al ganador se le cuenta la victoria");
        comprobar(despues1.fila(resultados1.get(3).id()).puntos() == 1, "el último suma 1");

        host.revancha();
        host.iniciar();
        esperarError(host, "no hay revancha ni nuevo inicio en medio de un torneo");
        invitado.siguienteCarrera();
        esperarError(invitado, "un invitado no larga la carrera siguiente");

        // Se va el invitado entre carreras: sigue en la tabla con sus puntos, pero no larga.
        int puntosInvitado = despues1.fila(idInvitado).puntos();
        invitado.cerrar();
        esperarSalida(host);
        host.siguienteCarrera();
        Protocolo.Cuenta segunda = Protocolo.leerCuenta(mensaje(host, null, Protocolo.CUENTA_REGRESIVA));
        comprobar(segunda.circuito().equals("monza"), "la segunda carrera es la que sigue en el calendario");
        comprobar(segunda.grilla().size() == 3 && !segunda.grilla().contains(idInvitado), "el que se fue no larga la carrera siguiente");
        comprobar(host.torneo().carrera() == 2, "el cliente sabe que es la carrera 2");

        Protocolo.leerResultados(mensaje(host, null, Protocolo.RESULTADOS));
        EstadoTorneo despues2 = host.torneo();
        comprobar(despues2.fila(idInvitado).puntos() == puntosInvitado && despues2.fila(idInvitado).puntosCarrera() == 0, "el que se fue conserva sus puntos");
        comprobar(despues2.tabla().stream().mapToInt(FilaTorneo::puntosCarrera).sum() == 4 + 2 + 1, "con 3 autos se reparten 4, 2 y 1");

        host.siguienteCarrera();
        Protocolo.Cuenta tercera = Protocolo.leerCuenta(mensaje(host, null, Protocolo.CUENTA_REGRESIVA));
        comprobar(tercera.circuito().equals("spielberg"), "un circuito repetido se corre de nuevo");
        Protocolo.leerResultados(mensaje(host, null, Protocolo.RESULTADOS));
        comprobar(host.torneo().esUltimaCarrera(), "la tercera es la última");
        int total = host.torneo().tabla().stream().mapToInt(FilaTorneo::puntos).sum();
        comprobar(total == (6 + 4 + 2 + 1) + (4 + 2 + 1) + (4 + 2 + 1), "la tabla final suma todo lo repartido: " + total);

        host.siguienteCarrera();
        esperarError(host, "terminado el torneo no hay carrera siguiente");
        host.revancha();
        esperar(host, () -> host.torneo() == null && host.lobby().torneo(), "la revancha vuelve a la sala");
        comprobar(host.lobby().calendario().equals(List.of(spielberg, monza, spielberg)), "la revancha conserva el calendario");
        comprobar(idHost == host.miId(), "el host sigue siendo el mismo");

        // Un torneo con clasificación: da la grilla, pero no da puntos.
        host.quitarCircuito(2);
        host.quitarCircuito(1);
        host.elegirClasificacion(true);
        host.marcarListo(true);
        esperar(host, () -> host.lobby().calendario().size() == 1 && host.lobby().clasificacion() && host.lobby().sePuedeIniciar(), "la sala queda lista para otro torneo");
        host.iniciar();
        comprobar(Protocolo.leerCuenta(mensaje(host, null, Protocolo.CUENTA_REGRESIVA)).clasificacion(), "cada carrera del torneo empieza por la clasificación");
        List<ResultadoJugador> grilla = Protocolo.leerResultados(mensaje(host, null, Protocolo.GRILLA));
        comprobar(host.torneo().tabla().stream().mapToInt(FilaTorneo::puntos).sum() == 0, "la clasificación no da puntos");
        Protocolo.Cuenta carrera = Protocolo.leerCuenta(mensaje(host, null, Protocolo.CUENTA_REGRESIVA));
        comprobar(carrera.grilla().equals(grilla.stream().map(ResultadoJugador::id).toList()), "la carrera larga en el orden de su clasificación");
        Protocolo.leerResultados(mensaje(host, null, Protocolo.RESULTADOS));
        comprobar(host.torneo().tabla().stream().mapToInt(FilaTorneo::puntos).sum() == 4 + 2 + 1, "la carrera sí da puntos");
        host.cerrar();
        servidor.cerrar();
    }

    private static void comprobar(boolean condicion, String regla) {
        if (!condicion) {
            System.out.println("FALLA: " + regla);
            System.exit(1);
        }
        System.out.println("OK: " + regla);
    }

    private interface Condicion {
        boolean vale();
    }

    private static void esperar(ClientePartida cliente, Condicion condicion, String regla) throws InterruptedException {
        long limite = System.currentTimeMillis() + ESPERA_MS;
        while (System.currentTimeMillis() < limite) {
            cliente.actualizar();
            while (cliente.sondear() != null) {
                // el lobby y el torneo los guarda el cliente solo
            }
            if (condicion.vale()) {
                comprobar(true, regla);
                return;
            }
            Thread.sleep(10);
        }
        comprobar(false, regla);
    }

    private static void esperarError(ClientePartida cliente, String regla) throws InterruptedException {
        long limite = System.currentTimeMillis() + ESPERA_MS;
        while (System.currentTimeMillis() < limite) {
            cliente.actualizar();
            Mensaje mensaje;
            while ((mensaje = cliente.sondear()) != null) {
                if (mensaje.es(Protocolo.ERROR)) {
                    comprobar(true, regla);
                    return;
                }
                if (mensaje.es(Protocolo.CUENTA_REGRESIVA) || mensaje.es(Protocolo.LOBBY)) {
                    comprobar(false, regla);
                }
            }
            Thread.sleep(10);
        }
        comprobar(false, regla);
    }

    private static void esperarSalida(ClientePartida cliente) throws InterruptedException {
        long limite = System.currentTimeMillis() + ESPERA_MS;
        while (System.currentTimeMillis() < limite) {
            cliente.actualizar();
            Mensaje mensaje;
            while ((mensaje = cliente.sondear()) != null) {
                if (mensaje.es(Protocolo.SALIO)) {
                    return;
                }
            }
            Thread.sleep(10);
        }
        comprobar(false, "llega el aviso de que se fue el invitado");
    }

    /** Espera ese mensaje en el primero, manteniendo viva la conexión del otro (null si ya no hay). */
    private static Mensaje mensaje(ClientePartida cliente, ClientePartida otro, String nombre) throws InterruptedException {
        long limite = System.currentTimeMillis() + ESPERA_FASE_MS;
        while (System.currentTimeMillis() < limite) {
            cliente.actualizar();
            if (otro != null) {
                otro.actualizar();
                while (otro.sondear() != null) {
                    // al que acompaña solo le importa seguir conectado y tener la tabla al día
                }
            }
            Mensaje mensaje;
            while ((mensaje = cliente.sondear()) != null) {
                if (mensaje.es(nombre)) {
                    return mensaje;
                }
            }
            Thread.sleep(10);
        }
        comprobar(false, "llega " + nombre);
        return null;
    }
}
