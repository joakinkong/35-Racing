package ar.edu.et35.racing.red.cliente;

import ar.edu.et35.racing.juego.EntradaAuto;
import ar.edu.et35.racing.red.ConexionTcp;
import ar.edu.et35.racing.red.EstadoLobby;
import ar.edu.et35.racing.red.PaquetesUdp;
import ar.edu.et35.racing.red.PerdidaSimulada;
import ar.edu.et35.racing.red.Protocolo;
import ar.edu.et35.racing.red.Protocolo.Mensaje;
import ar.edu.et35.racing.red.RegistroRed;
import ar.edu.et35.racing.util.Config;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Cliente de la partida (lobby por TCP, docs/PROTOCOLO.md secciones 3, 7 y 8). Lo usan todos los jugadores, el
 * host también (conectado a 127.0.0.1).
 *
 * <p>Hilos: uno que se conecta y después lee lo que manda el servidor por TCP, y otro que recibe los ESTADO por UDP.
 * Los dos solo dejan lo que reciben en colas thread-safe; <b>la pantalla las vacía en su hilo de render</b> con
 * {@link #sondear()} y {@link #sondearEstado()}, así nunca se toca nada de LibGDX desde un hilo de red. El resto de
 * los métodos (enviar, actualizar, getters) se llaman desde el hilo de render.
 */
public class ClientePartida {
    private final String nombre;
    private final String origen;
    private final ConcurrentLinkedQueue<Mensaje> recibidos = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<PaquetesUdp.Estado> estados = new ConcurrentLinkedQueue<>();
    private volatile ConexionTcp conexion;
    /** El socket mientras se está conectando: cerrar() lo cierra para cortar el intento sin esperar el timeout. */
    private volatile Socket conectando;
    private volatile DatagramSocket socketUdp;
    /** A dónde se mandan las ENTRADA; también es el único origen del que se aceptan ESTADO. */
    private volatile InetSocketAddress servidorUdp;
    private volatile boolean cerrando;
    /** Paquetes UDP descartados por venir de otra dirección o estar mal formados (para diagnóstico). */
    private volatile int udpAjenos;

    // Estado que se va actualizando con lo que llega. Solo se toca desde el hilo de render.
    private int miId = -1;
    private int token;
    private boolean esHost;
    private EstadoLobby lobby = EstadoLobby.vacio();
    private long ultimoPingNanos = System.nanoTime();
    /** Secuencia de las ENTRADA: sube de a 1 y nunca vuelve atrás, ni entre carreras. */
    private int secuenciaEntrada;

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
            conectando = socket;
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
        conectando = null;
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
        } catch (RuntimeException e) {
            RegistroRed.log(origen, "Error inesperado en la conexión: " + e);
            avisarPerdida("Error inesperado en la conexión con el anfitrión");
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
            abrirUdp(mensaje.entero(3, Config.PUERTO_UDP));
        } else if (mensaje.es(Protocolo.LOBBY)) {
            lobby = EstadoLobby.desde(mensaje);
        }
    }

    // ------------------------------------------------------------------ UDP

    /**
     * Abre el socket UDP en un puerto libre (no el del servidor: en la PC del host ya lo ocupa él, y así además se
     * pueden abrir varias instancias en una PC) y arranca el hilo que recibe los ESTADO.
     */
    private void abrirUdp(int puertoServidor) {
        ConexionTcp actual = conexion;
        if (cerrando || actual == null || socketUdp != null) {
            return;
        }
        try {
            DatagramSocket socket = new DatagramSocket();
            servidorUdp = new InetSocketAddress(actual.direccionIp(), puertoServidor);
            socketUdp = socket;
            RegistroRed.log(origen, "UDP local en el puerto " + socket.getLocalPort() + ", servidor en " + servidorUdp);
            Thread hilo = new Thread(() -> recibirUdp(socket), "cliente-udp-" + nombre);
            hilo.setDaemon(true);
            hilo.start();
        } catch (IOException e) {
            RegistroRed.log(origen, "No se pudo abrir el socket UDP: " + e.getMessage());
        }
    }

    /** Hilo receptor UDP: acepta solo ESTADO bien formados que vengan del servidor, y los encola. */
    private void recibirUdp(DatagramSocket socket) {
        byte[] bufer = new byte[PaquetesUdp.LARGO_MAXIMO + 64];
        DatagramPacket paquete = new DatagramPacket(bufer, bufer.length);
        while (!cerrando) {
            try {
                paquete.setLength(bufer.length);
                socket.receive(paquete);
            } catch (IOException e) {
                return; // el socket se cerró
            }
            if (!paquete.getSocketAddress().equals(servidorUdp)) {
                udpAjenos++;
                continue;
            }
            if (PerdidaSimulada.descartar()) {
                continue;
            }
            try {
                PaquetesUdp.Estado estado = PaquetesUdp.leerEstado(bufer, paquete.getLength());
                if (estado == null) {
                    udpAjenos++;
                    continue;
                }
                estados.add(estado);
            } catch (RuntimeException e) {
                udpAjenos++; // un paquete raro no debe detener la recepción
            }
        }
    }

    /** Manda lo que está apretando el jugador. Se llama desde el hilo de render, 60 veces por segundo. */
    public void enviarEntrada(EntradaAuto entrada) {
        DatagramSocket socket = socketUdp;
        InetSocketAddress destino = servidorUdp;
        if (socket == null || destino == null || miId < 0) {
            return;
        }
        byte[] datos = PaquetesUdp.armarEntrada(miId, token, secuenciaEntrada++, PaquetesUdp.botones(entrada));
        try {
            socket.send(new DatagramPacket(datos, datos.length, destino));
        } catch (IOException e) {
            // UDP no avisa si el paquete llegó: si falla el envío, el siguiente (17 ms después) lo reemplaza.
        }
    }

    /** Saca el próximo ESTADO recibido, o null si no hay. Lo llama la pantalla en el hilo de render. */
    public PaquetesUdp.Estado sondearEstado() {
        return estados.poll();
    }

    /** Descarta los ESTADO que hayan quedado sin leer (por ejemplo, de una carrera anterior). */
    public void descartarEstadosPendientes() {
        estados.clear();
    }

    /** Puerto UDP local, o -1 si todavía no se abrió. Sirve para diagnóstico y para las pruebas. */
    public int puertoUdpLocal() {
        DatagramSocket socket = socketUdp;
        return socket != null ? socket.getLocalPort() : -1;
    }

    public int udpAjenos() {
        return udpAjenos;
    }

    // ------------------------------------------------------------------ TCP

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

    public void revancha() {
        enviar(Protocolo.armar(Protocolo.REVANCHA));
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
        Socket enCurso = conectando;
        if (enCurso != null) {
            try {
                enCurso.close(); // el hilo que está en connect() recibe una excepción y termina
            } catch (IOException e) {
                // Ya estaba cerrado.
            }
        }
        ConexionTcp actual = conexion;
        if (actual != null) {
            try {
                enviarLinea(Protocolo.armar(Protocolo.SALIR));
            } catch (IOException e) {
                // Si no se puede avisar, igual se cierra.
            }
            actual.cerrar();
        }
        DatagramSocket udp = socketUdp;
        if (udp != null) {
            udp.close(); // destraba al hilo receptor UDP
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
