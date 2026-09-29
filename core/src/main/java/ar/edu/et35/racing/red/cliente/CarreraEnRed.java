package ar.edu.et35.racing.red.cliente;

import ar.edu.et35.racing.juego.EntradaAuto;
import ar.edu.et35.racing.juego.FotoCarrera;
import ar.edu.et35.racing.juego.FuenteCarrera;
import ar.edu.et35.racing.juego.ResultadoJugador;
import ar.edu.et35.racing.red.EstadoLobby;
import ar.edu.et35.racing.red.EstadoLobby.JugadorLobby;
import ar.edu.et35.racing.red.PaquetesUdp;
import ar.edu.et35.racing.util.Config;
import java.util.List;
import java.util.Map;

/**
 * La carrera vista desde un cliente en red. No simula nada: manda lo que aprieta el jugador (ENTRADA, 60 veces
 * por segundo) y dibuja lo que manda el servidor (ESTADO), interpolado. Los nombres, los colores y el orden de la
 * grilla salen del lobby con el que se largó la carrera.
 *
 * <p>Se usa solo desde el hilo de render.
 */
public class CarreraEnRed implements FuenteCarrera {
    private static final float PASO_ENTRADA = 1f / Config.ENVIOS_ENTRADA_POR_SEGUNDO;
    private static final EntradaAuto SIN_ENTRADA = new EntradaAuto();

    private final ClientePartida cliente;
    private final EstadoLobby lobby;
    private final int vueltas;
    private final Interpolador interpolador = new Interpolador(Config.RETRASO_INTERPOLACION);
    private float acumuladorEntrada;
    private List<ResultadoJugador> resultados;
    private int estadosRecibidos;
    private int estadosDescartados;
    private long ultimoEstadoNanos;

    public CarreraEnRed(ClientePartida cliente, EstadoLobby lobby, int vueltas) {
        this.cliente = cliente;
        this.lobby = lobby;
        this.vueltas = vueltas;
    }

    @Override
    public void avanzar(float delta, Map<Integer, EntradaAuto> entradasLocales) {
        // 1. Lo que aprieta el jugador sale 60 veces por segundo. Si la pantalla va más lenta que eso, se manda una
        //    por cuadro (no tiene sentido mandar dos iguales seguidas).
        acumuladorEntrada += delta;
        if (acumuladorEntrada >= PASO_ENTRADA) {
            cliente.enviarEntrada(entradasLocales.getOrDefault(cliente.miId(), SIN_ENTRADA));
            acumuladorEntrada = Math.min(acumuladorEntrada - PASO_ENTRADA, PASO_ENTRADA);
        }
        // 2. Los ESTADO que llegaron desde el cuadro anterior pasan al interpolador; los atrasados o duplicados se
        //    descartan por su número de tick.
        PaquetesUdp.Estado estado;
        while ((estado = cliente.sondearEstado()) != null) {
            if (interpolador.agregar(estado.tick(), estado.foto())) {
                estadosRecibidos++;
                ultimoEstadoNanos = System.nanoTime();
            } else {
                estadosDescartados++;
            }
        }
        interpolador.avanzar(delta);
    }

    @Override
    public FotoCarrera foto() {
        return interpolador.foto();
    }

    /** Lo llama la pantalla cuando llega el mensaje TCP RESULTADOS. */
    public void fijarResultados(List<ResultadoJugador> lista) {
        resultados = List.copyOf(lista);
    }

    @Override
    public List<ResultadoJugador> resultados() {
        return resultados;
    }

    @Override
    public List<Integer> idsLocales() {
        return List.of(cliente.miId());
    }

    @Override
    public String nombre(int id) {
        JugadorLobby jugador = lobby.jugador(id);
        return jugador != null ? jugador.nombre() : "?";
    }

    @Override
    public int color(int id) {
        JugadorLobby jugador = lobby.jugador(id);
        return jugador != null ? jugador.auto() : id;
    }

    @Override
    public int lugarDeLargada(int id) {
        List<JugadorLobby> jugadores = lobby.jugadores();
        for (int i = 0; i < jugadores.size(); i++) {
            if (jugadores.get(i).id() == id) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public int vueltasTotales() {
        return vueltas;
    }

    public int estadosRecibidos() {
        return estadosRecibidos;
    }

    public int estadosDescartados() {
        return estadosDescartados;
    }

    /** Milisegundos desde el último ESTADO aceptado, o -1 si todavía no llegó ninguno. */
    public long msDesdeUltimoEstado() {
        return ultimoEstadoNanos == 0 ? -1 : (System.nanoTime() - ultimoEstadoNanos) / 1_000_000L;
    }

    public Interpolador interpolador() {
        return interpolador;
    }
}
