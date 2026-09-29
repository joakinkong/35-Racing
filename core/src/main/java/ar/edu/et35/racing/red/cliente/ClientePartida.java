package ar.edu.et35.racing.red.cliente;

import ar.edu.et35.racing.red.ConexionTcp;
import ar.edu.et35.racing.red.EstadoLobby;
import ar.edu.et35.racing.red.Protocolo;
import ar.edu.et35.racing.red.Protocolo.Mensaje;
import ar.edu.et35.racing.red.RegistroRed;
import ar.edu.et35.racing.util.Config;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Cliente de la partida (lobby por TCP, docs/PROTOCOLO.md secciones 3, 7 y 8). Lo usan todos los jugadores, el
 * host también (conectado a 127.0.0.1).
 *
 * <p>Hilos: uno que se conecta y después lee lo que manda el servidor. Ese hilo solo deja los mensajes en una cola
 * thread-safe; <b>la pantalla los procesa en su hilo de render</b> llamando a {@link #sondear()}, así nunca se toca
 * nada de LibGDX desde un hilo de red. El resto de los métodos (enviar, actualizar, getters) se llaman desde el hilo
 * de render.
 */
public class ClientePartida {
    private final String nombre;
    private final String origen;
    private final ConcurrentLinkedQueue<Mensaje> recibidos = new ConcurrentLinkedQueue<>();
    private volatile ConexionTcp conexion;
    private volatile boolean cerrando;

    // Estado que se va actualizando con lo que llega. Solo se toca desde el hilo de render.
    private int miId = -1;
    private int token;
    private boolean esHost;
    private EstadoLobby lobby = EstadoLobby.vacio();
    private long ultimoPingNanos = System.nanoTime();

    public ClientePartida(String nombre) {
        this.nombre = nombre;
        this.origen = "CLIENTE " + nombre;
    }

    /**
     * Se conecta en segundo plano (con el timeout de {@link Config#TIMEOUT_CONEXION_MS}) y manda UNIRSE. El
     * resultado llega como mensajes por {@link #sondear()}: BIENVENIDA o ERROR si sale bien o mal, y
     * {@link Protocolo#CONEXION_FALLIDA} si no se pudo ni conectar.
     */
    public void conectar(String direccion, int puerto) {
        Thread hilo = new Thread(() -> conectarYLeer(direccion, puerto), "cliente-tcp-" + nombre);
        hilo.setDaemon(true);
        hilo.start();
    }

    private void conectarYLeer(String direccion, int puerto) {
        ConexionTcp nueva;
        try {
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress(direccion, puerto), Config.TIMEOUT_CONEXION_MS);
                nueva = new ConexionTcp(socket);
            } catch (IOException e) {
                socket.close();
                throw e;
            }
        } catch (IOException | IllegalArgumentException e) {
            RegistroRed.log(origen, "No se pudo conectar a " + direccion + ":" + puerto + " (" + e.getMessage() + ")");
            if (!cerrando) {
                recibidos.add(new Mensaje(Protocolo.CONEXION_FALLIDA, List.of("No se pudo conectar a " + direccion)));
            }
            return;
        }
        conexion = nueva;
        if (cerrando) {
            nueva.cerrar();
            return;
        }
        RegistroRed.log(origen, "Conectado a " + nueva.direccionRemota());
        try {
            enviarLinea(Protocolo.armar(Protocolo.UNIRSE, Config.VERSION_PROTOCOLO, nombre));
            leer(nueva);
        } catch (IOException e) {
            avisarPerdida("Se perdió la conexión con el anfitrión");
        } finally {
            nueva.cerrar();
        }
    }

    private void leer(ConexionTcp conexionActual) throws IOException {
        try {
            String linea;
            while ((linea = conexionActual.leerLinea()) != null) {
                Mensaje mensaje = Protocolo.leer(linea);
                if (mensaje == null) {
                    continue;
                }
                RegistroRed.log(origen, "<- " + linea);
                if (!mensaje.es(Protocolo.PONG)) { // el PONG solo sirve para que el timeout no venza
                    recibidos.add(mensaje);
                }
            }
            avisarPerdida("El anfitrión cerró la conexión");
        } catch (SocketTimeoutException e) {
            avisarPerdida("Se perdió la conexión con el anfitrión (no responde)");
        }
    }

    private void avisarPerdida(String motivo) {
        if (!cerrando) {
            recibidos.add(new Mensaje(Protocolo.CONEXION_PERDIDA, List.of(motivo)));
        }
    }

    // ------------------------------------------------------------------ hilo de render

    /** Saca el próximo mensaje recibido (o null si no hay) y actualiza el estado del cliente con él. */
    public Mensaje sondear() {
        Mensaje mensaje = recibidos.poll();
        if (mensaje != null) {
            aplicar(mensaje);
        }
        return mensaje;
    }

    private void aplicar(Mensaje mensaje) {
        if (mensaje.es(Protocolo.BIENVENIDA)) {
            miId = mensaje.entero(0, -1);
            token = mensaje.entero(1, 0);
            esHost = mensaje.campo(2).equals("1");
        } else if (mensaje.es(Protocolo.LOBBY)) {
            lobby = EstadoLobby.desde(mensaje);
        }
    }

    /** Se llama una vez por cuadro: manda el PING cuando corresponde, para que ni el servidor ni el cliente den por caída la conexión. */
    public void actualizar() {
        long ahora = System.nanoTime();
        if (conexion != null && ahora - ultimoPingNanos >= Config.INTERVALO_PING_MS * 1_000_000L) {
            ultimoPingNanos = ahora;
            enviar(Protocolo.armar(Protocolo.PING, ahora / 1_000_000L));
        }
    }

    public void elegirAuto(int auto) {
        enviar(Protocolo.armar(Protocolo.ELEGIR_AUTO, auto));
    }

    public void marcarListo(boolean listo) {
        enviar(Protocolo.armar(Protocolo.LISTO, listo ? 1 : 0));
    }

    public void iniciar() {
        enviar(Protocolo.armar(Protocolo.INICIAR));
    }

    private void enviar(String linea) {
        try {
            enviarLinea(linea);
        } catch (IOException e) {
            avisarPerdida("Se perdió la conexión con el anfitrión");
        }
    }

    private void enviarLinea(String linea) throws IOException {
        ConexionTcp actual = conexion;
        if (actual == null || actual.cerrada()) {
            return;
        }
        actual.enviar(linea);
        RegistroRed.log(origen, "-> " + linea);
    }

    /** Avisa que se va (SALIR) y cierra la conexión. Los hilos que estén esperando terminan solos. */
    public void cerrar() {
        if (cerrando) {
            return;
        }
        cerrando = true;
        ConexionTcp actual = conexion;
        if (actual != null) {
            try {
                enviarLinea(Protocolo.armar(Protocolo.SALIR));
            } catch (IOException e) {
                // Si no se puede avisar, igual se cierra.
            }
            actual.cerrar();
        }
    }

    public String nombre() {
        return nombre;
    }

    public int miId() {
        return miId;
    }

    public int token() {
        return token;
    }

    public boolean esHost() {
        return esHost;
    }

    public EstadoLobby lobby() {
        return lobby;
    }

    /** El auto que tiene asignado este jugador según el último LOBBY, o 0 si todavía no llegó. */
    public int miAuto() {
        EstadoLobby.JugadorLobby yo = lobby.jugador(miId);
        return yo != null ? yo.auto() : 0;
    }
}
