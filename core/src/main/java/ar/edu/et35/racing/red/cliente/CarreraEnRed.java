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

public class CarreraEnRed implements FuenteCarrera {
    private static final float PASO_ENTRADA = 1f / Config.ENVIOS_ENTRADA_POR_SEGUNDO;
    private static final EntradaAuto SIN_ENTRADA = new EntradaAuto();

    private final ClientePartida cliente;
    private final EstadoLobby lobby;
    private final int vueltas;
    private final List<Integer> grilla;
    private final Interpolador interpolador = new Interpolador(Config.RETRASO_INTERPOLACION);
    private float acumuladorEntrada;
    private List<ResultadoJugador> resultados;
    private int estadosRecibidos;
    private int estadosDescartados;
    private long ultimoEstadoNanos;

    public CarreraEnRed(ClientePartida cliente, EstadoLobby lobby, int vueltas, List<Integer> grilla) {
        this.grilla = grilla;
        this.cliente = cliente;
        this.lobby = lobby;
        this.vueltas = vueltas;
    }

    @Override
    public void avanzar(float delta, Map<Integer, EntradaAuto> entradasLocales) {
        acumuladorEntrada += delta;
        if (acumuladorEntrada >= PASO_ENTRADA) {
            cliente.enviarEntrada(entradasLocales.getOrDefault(cliente.miId(), SIN_ENTRADA));
            acumuladorEntrada = Math.min(acumuladorEntrada - PASO_ENTRADA, PASO_ENTRADA);
        }
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
    public int piloto(int id) {
        JugadorLobby jugador = lobby.jugador(id);
        return jugador != null ? jugador.piloto() : 0;
    }

    @Override
    public int lugarDeLargada(int id) {
        return Math.max(0, grilla.indexOf(id));
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

    public long msDesdeUltimoEstado() {
        return ultimoEstadoNanos == 0 ? -1 : (System.nanoTime() - ultimoEstadoNanos) / 1_000_000L;
    }

    public Interpolador interpolador() {
        return interpolador;
    }
}
