package ar.edu.et35.racing.juego;

import ar.edu.et35.racing.util.Config;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reglas y estado de una carrera: cuenta regresiva, vueltas, posiciones, choques entre autos y fin.
 * Es la clase que después simula el servidor, así que no lee el teclado ni usa clases gráficas: recibe
 * las entradas de cada auto por id y avanza un paso de simulación.
 */
public class Carrera {
    private static final EntradaAuto SIN_ENTRADA = new EntradaAuto();

    private final Circuito circuito;
    private final int vueltasTotales;
    private final Map<Integer, Participante> participantes = new LinkedHashMap<>();
    private final Vector2 auxiliar = new Vector2();

    private EstadoCarrera estado = EstadoCarrera.CUENTA_REGRESIVA;
    private float cuentaRestante = Config.SEGUNDOS_CUENTA_REGRESIVA;
    private float tiempoCarrera;
    private boolean cierreActivo;
    private float cierreRestante;

    public Carrera(Circuito circuito, int vueltasTotales) {
        this.circuito = circuito;
        this.vueltasTotales = vueltasTotales;
    }

    /** Suma un auto en el próximo lugar libre de la largada. Solo antes de que arranque la carrera. */
    public Participante agregarAuto(int id, String nombre) {
        if (estado != EstadoCarrera.CUENTA_REGRESIVA) {
            throw new IllegalStateException("No se pueden sumar autos con la carrera empezada");
        }
        if (participantes.size() >= Config.MAX_JUGADORES) {
            throw new IllegalStateException("Máximo de " + Config.MAX_JUGADORES + " autos");
        }
        if (participantes.containsKey(id)) {
            throw new IllegalArgumentException("Ya hay un auto con el id " + id);
        }
        Vector2 lugar = circuito.posicionesLargada().get(participantes.size());
        Auto auto = new Auto(lugar.x, lugar.y, circuito.anguloLargada());
        Participante participante = new Participante(id, nombre, auto, circuito.checkpoints().size());
        participantes.put(id, participante);
        return participante;
    }

    /** Avanza la carrera dt segundos. Un id sin entrada en el mapa equivale a soltar todos los controles. */
    public void actualizar(Map<Integer, EntradaAuto> entradas, float dt) {
        if (participantes.isEmpty()) {
            return;
        }
        switch (estado) {
            case CUENTA_REGRESIVA:
                // Hasta el "¡YA!" se ignoran las entradas y los autos no se mueven.
                cuentaRestante -= dt;
                if (cuentaRestante <= 0f) {
                    estado = EstadoCarrera.EN_CURSO;
                }
                break;
            case EN_CURSO:
                avanzar(entradas, dt);
                break;
            case TERMINADA:
            default:
                break;
        }
    }

    private void avanzar(Map<Integer, EntradaAuto> entradas, float dt) {
        tiempoCarrera += dt;
        for (Participante p : participantes.values()) {
            if (p.desconectado()) {
                continue; // su auto salió de la pista: no se simula
            }
            // Un auto que ya terminó deja de recibir entradas y se va frenando solo.
            EntradaAuto entrada = p.terminado() ? SIN_ENTRADA : entradas.getOrDefault(p.id(), SIN_ENTRADA);
            p.auto().actualizar(entrada, circuito, dt);
            p.sumarTiempo(dt);
            comprobarCheckpoint(p);
        }
        resolverChoques();
        actualizarFin(dt);
    }

    /**
     * Solo cuenta si el auto cruza el checkpoint que le toca yendo hacia adelante (no en reversa) y en el
     * sentido de la pista (no en contramano).
     */
    private void comprobarCheckpoint(Participante p) {
        if (p.terminado()) {
            return;
        }
        int indice = p.siguienteCheckpoint();
        Rectangle zona = circuito.checkpoints().get(indice);
        Auto auto = p.auto();
        if (!zona.contains(auto.x(), auto.y()) || auto.velocidadAdelante() <= 0f) {
            return;
        }
        Vector2 sentido = circuito.sentidoCheckpoint(indice);
        if (auto.velocidad().x * sentido.x + auto.velocidad().y * sentido.y > 0f) {
            p.pasarPorCheckpoint(tiempoCarrera, vueltasTotales);
        }
    }

    /**
     * Choque entre autos: dos círculos de igual masa. Se separan por el solapamiento (si un muro le impide
     * moverse a uno, el otro absorbe lo que falta) y se reparten la velocidad a lo largo de la línea que los une.
     */
    private void resolverChoques() {
        List<Participante> lista = new ArrayList<>();
        for (Participante p : participantes.values()) {
            if (!p.desconectado()) {
                lista.add(p);
            }
        }
        float distanciaMinima = 2f * ParametrosAuto.RADIO_COLISION;
        for (int i = 0; i < lista.size(); i++) {
            for (int j = i + 1; j < lista.size(); j++) {
                Auto a = lista.get(i).auto();
                Auto b = lista.get(j).auto();
                float dx = b.x() - a.x();
                float dy = b.y() - a.y();
                float distancia = (float) Math.sqrt(dx * dx + dy * dy);
                if (distancia >= distanciaMinima) {
                    continue;
                }
                // Con los centros superpuestos no hay dirección: se elige una cualquiera para poder separarlos.
                float nx = distancia > 1e-4f ? dx / distancia : 1f;
                float ny = distancia > 1e-4f ? dy / distancia : 0f;
                separar(a, b, nx, ny, distanciaMinima - distancia);
                repartirVelocidad(a, b, nx, ny);
            }
        }
    }

    private void separar(Auto a, Auto b, float nx, float ny, float solapamiento) {
        float mitad = solapamiento / 2f;
        float ax = a.x();
        float ay = a.y();
        a.desplazar(-nx * mitad, -ny * mitad, circuito);
        float movioA = (ax - a.x()) * nx + (ay - a.y()) * ny;
        b.desplazar(nx * (solapamiento - movioA), ny * (solapamiento - movioA), circuito);
    }

    private void repartirVelocidad(Auto a, Auto b, float nx, float ny) {
        Vector2 va = a.velocidad();
        Vector2 vb = b.velocidad();
        float relativa = (vb.x - va.x) * nx + (vb.y - va.y) * ny;
        if (relativa >= 0f) {
            return; // ya se están alejando
        }
        float impulso = -(1f + ParametrosAuto.RESTITUCION_CHOQUE) * relativa / 2f;
        va.x -= impulso * nx;
        va.y -= impulso * ny;
        vb.x += impulso * nx;
        vb.y += impulso * ny;
    }

    /**
     * Cuando el primero termina empieza el cierre; la carrera acaba cuando terminaron todos los que siguen
     * conectados o al vencer el cierre.
     */
    private void actualizarFin(float dt) {
        boolean alguno = false;
        boolean todos = true;
        for (Participante p : participantes.values()) {
            alguno |= p.terminado();
            if (!p.desconectado()) {
                todos &= p.terminado();
            }
        }
        if (alguno && !cierreActivo) {
            cierreActivo = true;
            cierreRestante = Config.TIEMPO_CIERRE;
        } else if (cierreActivo) {
            cierreRestante -= dt;
        }
        if (todos || (cierreActivo && cierreRestante <= 0f)) {
            estado = EstadoCarrera.TERMINADA;
        }
    }

    /**
     * Participantes ordenados por posición: primero los que terminaron (por tiempo), después los que siguen en
     * carrera (por avance) y al final los que abandonaron sin terminar.
     */
    public List<Participante> clasificacion() {
        List<Participante> lista = new ArrayList<>(participantes.values());
        lista.sort(this::comparar);
        return lista;
    }

    private int comparar(Participante a, Participante b) {
        boolean abandonoA = a.desconectado() && !a.terminado();
        boolean abandonoB = b.desconectado() && !b.terminado();
        if (abandonoA != abandonoB) {
            return abandonoA ? 1 : -1;
        }
        if (a.terminado() != b.terminado()) {
            return a.terminado() ? -1 : 1;
        }
        if (a.terminado()) {
            return Float.compare(a.tiempoTotal(), b.tiempoTotal());
        }
        int porAvance = Integer.compare(b.progreso(), a.progreso());
        if (porAvance != 0) {
            return porAvance;
        }
        return Float.compare(distanciaAlProximoCheckpoint(a), distanciaAlProximoCheckpoint(b));
    }

    private float distanciaAlProximoCheckpoint(Participante p) {
        circuito.checkpoints().get(p.siguienteCheckpoint()).getCenter(auxiliar);
        return auxiliar.dst(p.auto().x(), p.auto().y());
    }

    /** Posición actual del auto (1 = puntero), o 0 si el id no existe. */
    public int posicionDe(int id) {
        List<Participante> orden = clasificacion();
        for (int i = 0; i < orden.size(); i++) {
            if (orden.get(i).id() == id) {
                return i + 1;
            }
        }
        return 0;
    }

    public List<ResultadoJugador> resultados() {
        List<ResultadoJugador> lista = new ArrayList<>();
        List<Participante> orden = clasificacion();
        for (int i = 0; i < orden.size(); i++) {
            Participante p = orden.get(i);
            lista.add(new ResultadoJugador(i + 1, p.id(), p.nombre(), p.tiempoTotal(), p.mejorVuelta()));
        }
        return lista;
    }

    /**
     * El jugador se fue (por ejemplo, se cortó su conexión): su auto sale de la pista, no choca, no cuenta para
     * "todos terminaron" y queda al final de la clasificación si no había terminado.
     */
    public void marcarDesconectado(int id) {
        Participante p = participantes.get(id);
        if (p != null) {
            p.marcarDesconectado();
        }
    }

    /** Segundos de carrera; durante la cuenta regresiva es negativo (-2,4 = faltan 2,4 s para largar). */
    public float relojCarrera() {
        return estado == EstadoCarrera.CUENTA_REGRESIVA ? -Math.max(0f, cuentaRestante) : tiempoCarrera;
    }

    /**
     * Foto de la carrera para dibujarla o mandarla por red. Las posiciones se interpolan entre el tick anterior y
     * el actual (alfa entre 0 y 1); el servidor usa alfa = 1.
     */
    public FotoCarrera foto(float alfa) {
        List<FotoAuto> autos = new ArrayList<>();
        List<Participante> orden = clasificacion();
        for (int i = 0; i < orden.size(); i++) {
            Participante p = orden.get(i);
            Auto a = p.auto();
            autos.add(new FotoAuto(p.id(), a.xInterpolada(alfa), a.yInterpolada(alfa), a.anguloInterpolado(alfa),
                a.velocidad().x, a.velocidad().y, p.vueltas(), i + 1, p.terminado(), p.desconectado(),
                p.terminado() ? p.tiempoTotal() : p.tiempoVuelta(), p.mejorVuelta()));
        }
        return new FotoCarrera(estado, relojCarrera(), cierreActivo ? cierreRestante() : Float.NaN, autos);
    }

    public Participante participante(int id) {
        return participantes.get(id);
    }

    public Collection<Participante> participantes() {
        return Collections.unmodifiableCollection(participantes.values());
    }

    public EstadoCarrera estado() {
        return estado;
    }

    public int vueltasTotales() {
        return vueltasTotales;
    }

    /** Número que se muestra en la cuenta regresiva (3, 2, 1). */
    public int segundosCuentaRegresiva() {
        return Math.max(0, (int) Math.ceil(cuentaRestante));
    }

    /** Verdadero durante el instante en que se muestra el cartel "¡YA!". */
    public boolean mostrarYa() {
        return estado == EstadoCarrera.EN_CURSO && tiempoCarrera < Config.SEGUNDOS_CARTEL_YA;
    }

    public float tiempoCarrera() {
        return tiempoCarrera;
    }

    public boolean cierreActivo() {
        return cierreActivo;
    }

    /** Segundos que le quedan al resto para terminar, una vez que el primero terminó. */
    public float cierreRestante() {
        return Math.max(0f, cierreRestante);
    }
}
