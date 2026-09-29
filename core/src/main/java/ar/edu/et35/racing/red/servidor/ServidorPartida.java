package ar.edu.et35.racing.red.servidor;

import ar.edu.et35.racing.red.ConexionTcp;
import ar.edu.et35.racing.red.EstadoLobby;
import ar.edu.et35.racing.red.EstadoLobby.JugadorLobby;
import ar.edu.et35.racing.red.Protocolo;
import ar.edu.et35.racing.red.Protocolo.Mensaje;
import ar.edu.et35.racing.red.RegistroRed;
import ar.edu.et35.racing.util.Config;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Servidor de la partida, embebido en el proceso del host. Implementa el lobby por TCP de docs/PROTOCOLO.md
 * (secciones 2, 3, 7 y 8).
 *
 * <p>Hilos: uno que acepta conexiones, uno lector por cada cliente y uno de lógica. <b>Solo el hilo de lógica
 * toca el estado de la partida</b> (jugadores, fase): los lectores le dejan eventos en una cola y así no hacen
 * falta locks sobre ese estado. La única excepción es el PING, que el lector contesta en el momento porque no
 * toca ningún estado.
 *
 * <p>El primer jugador que se une es el host: es el que creó la partida y se conecta enseguida a su propio servidor.
 */
public class ServidorPartida {
    private static final String ORIGEN = "SERVIDOR";
    /** Cada cuánto despierta el hilo de lógica aunque no lleguen eventos (para controlar la cuenta regresiva). */
    private static final long ESPERA_EVENTOS_MS = 20;

    /** Fase de la partida (sección 2 del protocolo). Todavía no hay RESULTADOS: eso llega con la carrera en red. */
    private enum Fase { LOBBY, CUENTA_REGRESIVA, CARRERA }

    /** Una conexión aceptada. Todavía no es un jugador hasta que manda UNIRSE y se lo acepta. */
    private static final class Cliente {
        final ConexionTcp conexion;
        volatile Jugador jugador; // lo asigna el hilo de lógica

        Cliente(ConexionTcp conexion) {
            this.conexion = conexion;
        }

        String nombreParaLog() {
            Jugador j = jugador;
            return j != null ? "#" + j.id + " " + j.nombre : conexion.direccionRemota();
        }
    }

    /** Un jugador aceptado. Solo lo modifica el hilo de lógica. */
    private static final class Jugador {
        final int id;
        final String nombre;
        final int token;
        final Cliente cliente;
        int auto;
        boolean listo;

        Jugador(int id, String nombre, int token, Cliente cliente, int auto) {
            this.id = id;
            this.nombre = nombre;
            this.token = token;
            this.cliente = cliente;
            this.auto = auto;
        }
    }

    private interface Evento {
    }

    private record Recibido(Cliente cliente, Mensaje mensaje) implements Evento {
    }

    private record Desconectado(Cliente cliente, String motivo) implements Evento {
    }

    private final ServerSocket socketServidor;
    private final BlockingQueue<Evento> eventos = new LinkedBlockingQueue<>();
    private final List<Cliente> conexiones = new CopyOnWriteArrayList<>();
    private final AtomicBoolean activo = new AtomicBoolean(true);
    private final SecureRandom azar = new SecureRandom();

    // Estado de la partida: solo lo toca el hilo de lógica
    private final Map<Integer, Jugador> jugadores = new LinkedHashMap<>();
    private Fase fase = Fase.LOBBY;
    private int idHost = -1;
    private long largadaEnNanos;

    private ServidorPartida(ServerSocket socketServidor) {
        this.socketServidor = socketServidor;
    }

    /**
     * Crea el servidor y lo deja escuchando.
     *
     * @param puerto puerto TCP; 0 deja que el sistema elija uno libre (se usa en las pruebas)
     * @throws IOException si el puerto ya está ocupado (por ejemplo, otra partida creada en esta PC)
     */
    public static ServidorPartida crear(int puerto) throws IOException {
        ServidorPartida servidor = new ServidorPartida(new ServerSocket(puerto));
        servidor.iniciarHilos();
        RegistroRed.log(ORIGEN, "Escuchando TCP en el puerto " + servidor.puerto());
        return servidor;
    }

    public int puerto() {
        return socketServidor.getLocalPort();
    }

    public boolean activo() {
        return activo.get();
    }

    private void iniciarHilos() {
        hilo("servidor-aceptacion", this::aceptar);
        hilo("servidor-logica", this::correrLogica);
    }

    private static Thread hilo(String nombre, Runnable tarea) {
        Thread hilo = new Thread(tarea, nombre);
        // Daemon: si el programa termina, estos hilos no lo mantienen vivo.
        hilo.setDaemon(true);
        hilo.start();
        return hilo;
    }

    // ------------------------------------------------------------------ hilo de aceptación y lectores

    private void aceptar() {
        while (activo.get()) {
            try {
                Socket socket = socketServidor.accept();
                Cliente cliente = new Cliente(new ConexionTcp(socket));
                conexiones.add(cliente);
                RegistroRed.log(ORIGEN, "Conexión entrante de " + cliente.conexion.direccionRemota());
                hilo("servidor-lector-" + cliente.conexion.direccionRemota(), () -> leer(cliente));
            } catch (IOException e) {
                if (activo.get() && !socketServidor.isClosed()) {
                    RegistroRed.log(ORIGEN, "Error al aceptar una conexión: " + e.getMessage());
                } else {
                    return;
                }
            }
        }
    }

    /** Hilo lector de un cliente: convierte lo que llega en eventos para el hilo de lógica. */
    private void leer(Cliente cliente) {
        String motivo = "cerró la conexión";
        try {
            String linea;
            while ((linea = cliente.conexion.leerLinea()) != null) {
                Mensaje mensaje = Protocolo.leer(linea);
                if (mensaje == null) {
                    continue;
                }
                RegistroRed.log(ORIGEN, "<- " + cliente.nombreParaLog() + ": " + linea);
                if (mensaje.es(Protocolo.PING)) {
                    enviar(cliente, Protocolo.armar(Protocolo.PONG, mensaje.campo(0)));
                } else {
                    eventos.add(new Recibido(cliente, mensaje));
                }
            }
        } catch (SocketTimeoutException e) {
            motivo = "sin respuesta durante " + Config.TIMEOUT_TCP_MS / 1000 + " s";
        } catch (IOException e) {
            motivo = cliente.conexion.cerrada() ? "conexión cerrada" : "error de red: " + e.getMessage();
        } finally {
            cliente.conexion.cerrar();
            eventos.add(new Desconectado(cliente, motivo));
        }
    }

    // ------------------------------------------------------------------ hilo de lógica

    private void correrLogica() {
        try {
            while (activo.get()) {
                Evento evento = eventos.poll(ESPERA_EVENTOS_MS, TimeUnit.MILLISECONDS);
                if (evento != null) {
                    procesar(evento);
                }
                controlarCuentaRegresiva();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void procesar(Evento evento) {
        if (evento instanceof Desconectado d) {
            Jugador jugador = d.cliente().jugador;
            if (jugador != null) {
                RegistroRed.log(ORIGEN, "Se desconectó " + jugador.nombre + " (" + d.motivo() + ")");
                quitar(jugador);
            }
            conexiones.remove(d.cliente());
        } else if (evento instanceof Recibido r) {
            procesarMensaje(r.cliente(), r.mensaje());
        }
    }

    private void procesarMensaje(Cliente cliente, Mensaje mensaje) {
        Jugador jugador = cliente.jugador;
        if (jugador == null) {
            // Antes de UNIRSE no se acepta ningún otro mensaje.
            if (mensaje.es(Protocolo.UNIRSE)) {
                unir(cliente, mensaje);
            } else {
                RegistroRed.log(ORIGEN, "Se ignora " + mensaje.nombre() + " de una conexión que todavía no se unió");
            }
            return;
        }
        switch (mensaje.nombre()) {
            case Protocolo.ELEGIR_AUTO:
                elegirAuto(jugador, mensaje);
                break;
            case Protocolo.LISTO:
                marcarListo(jugador, mensaje);
                break;
            case Protocolo.INICIAR:
                iniciar(jugador);
                break;
            case Protocolo.SALIR:
                quitar(jugador);
                break;
            case Protocolo.REVANCHA:
                // Todavía no hay resultados a los que responder con una revancha.
                error(cliente, Protocolo.NO_PERMITIDO, "La revancha todavía no está disponible");
                break;
            default:
                RegistroRed.log(ORIGEN, "Mensaje desconocido de " + jugador.nombre + ": " + mensaje.nombre());
                break;
        }
    }

    private void unir(Cliente cliente, Mensaje mensaje) {
        int version = mensaje.entero(0, -1);
        String nombre = mensaje.campo(1);
        if (version != Config.VERSION_PROTOCOLO) {
            rechazar(cliente, Protocolo.VERSION_DISTINTA,
                "Tu juego es de otra versión (" + version + ", la de la partida es " + Config.VERSION_PROTOCOLO + ")");
        } else if (fase != Fase.LOBBY) {
            rechazar(cliente, Protocolo.CARRERA_EN_CURSO, "La carrera ya empezó");
        } else if (jugadores.size() >= Config.MAX_JUGADORES) {
            rechazar(cliente, Protocolo.PARTIDA_LLENA, "La partida está llena");
        } else if (!Protocolo.nombreValido(nombre)) {
            rechazar(cliente, Protocolo.NOMBRE_INVALIDO,
                "El nombre debe tener de 1 a " + Config.LARGO_MAXIMO_NOMBRE + " letras, números o espacios");
        } else if (nombreEnUso(nombre)) {
            rechazar(cliente, Protocolo.NOMBRE_REPETIDO, "Ya hay un jugador con ese nombre");
        } else {
            aceptar(cliente, nombre);
        }
    }

    private void aceptar(Cliente cliente, String nombre) {
        boolean esHost = jugadores.isEmpty();
        int id = primerIdLibre();
        Jugador jugador = new Jugador(id, nombre, azar.nextInt(), cliente, primerAutoLibre());
        jugadores.put(id, jugador);
        cliente.jugador = jugador;
        if (esHost) {
            idHost = id;
        }
        RegistroRed.log(ORIGEN, nombre + " se unió con el id " + id + (esHost ? " (anfitrión)" : ""));
        enviar(cliente, Protocolo.armar(Protocolo.BIENVENIDA, id, jugador.token, esHost ? 1 : 0, Config.PUERTO_UDP));
        difundirLobby();
    }

    private void elegirAuto(Jugador jugador, Mensaje mensaje) {
        if (fase != Fase.LOBBY) {
            error(jugador.cliente, Protocolo.NO_PERMITIDO, "Ya no se puede cambiar de auto");
            return;
        }
        int auto = mensaje.entero(0, -1);
        if (auto < 0 || auto >= Config.MAX_JUGADORES) {
            RegistroRed.log(ORIGEN, "Auto inválido de " + jugador.nombre + ": " + mensaje.campo(0));
            return;
        }
        for (Jugador otro : jugadores.values()) {
            if (otro != jugador && otro.auto == auto) {
                error(jugador.cliente, Protocolo.AUTO_OCUPADO, "Ese auto ya lo eligió otro jugador");
                return;
            }
        }
        jugador.auto = auto;
        difundirLobby();
    }

    private void marcarListo(Jugador jugador, Mensaje mensaje) {
        if (fase != Fase.LOBBY) {
            error(jugador.cliente, Protocolo.NO_PERMITIDO, "La partida ya empezó");
            return;
        }
        jugador.listo = mensaje.campo(0).equals("1");
        difundirLobby();
    }

    private void iniciar(Jugador jugador) {
        if (jugador.id != idHost || fase != Fase.LOBBY) {
            error(jugador.cliente, Protocolo.NO_PERMITIDO, "Solo el anfitrión puede iniciar, y solo desde el lobby");
            return;
        }
        if (!estadoLobby().sePuedeIniciar()) {
            error(jugador.cliente, Protocolo.FALTAN_JUGADORES,
                "Hacen falta al menos " + Config.MIN_JUGADORES + " jugadores y que todos estén listos");
            return;
        }
        fase = Fase.CUENTA_REGRESIVA;
        largadaEnNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(Config.SEGUNDOS_CUENTA_REGRESIVA);
        difundir(Protocolo.armar(Protocolo.CUENTA_REGRESIVA, Config.CIRCUITO, Config.VUELTAS,
            Config.SEGUNDOS_CUENTA_REGRESIVA));
    }

    /** Cuando termina la cuenta regresiva se manda el "¡YA!" y la partida pasa a CARRERA. */
    private void controlarCuentaRegresiva() {
        if (fase == Fase.CUENTA_REGRESIVA && System.nanoTime() >= largadaEnNanos) {
            fase = Fase.CARRERA;
            difundir(Protocolo.armar(Protocolo.LARGADA));
        }
    }

    /**
     * Saca a un jugador de la partida. Si era el host, la partida termina para todos. En el lobby se avisa con
     * un LOBBY nuevo; con la carrera empezada, con SALIO.
     */
    private void quitar(Jugador jugador) {
        if (jugadores.remove(jugador.id) == null) {
            return;
        }
        jugador.cliente.jugador = null;
        jugador.cliente.conexion.cerrar();
        if (jugador.id == idHost) {
            cerrar("El anfitrión abandonó la partida");
        } else if (fase == Fase.LOBBY) {
            difundirLobby();
        } else {
            difundir(Protocolo.armar(Protocolo.SALIO, jugador.id));
        }
    }

    // ------------------------------------------------------------------ envío

    private EstadoLobby estadoLobby() {
        List<JugadorLobby> lista = new ArrayList<>();
        for (Jugador j : jugadores.values()) {
            lista.add(new JugadorLobby(j.id, j.nombre, j.auto, j.listo));
        }
        return new EstadoLobby(idHost, lista);
    }

    private void difundirLobby() {
        difundir(estadoLobby().aLinea());
    }

    private void difundir(String linea) {
        for (Jugador jugador : jugadores.values()) {
            enviar(jugador.cliente, linea);
        }
    }

    private void error(Cliente cliente, String codigo, String detalle) {
        enviar(cliente, Protocolo.armar(Protocolo.ERROR, codigo, Protocolo.limpiarTexto(detalle)));
    }

    /** Manda el ERROR y cierra la conexión, para que el cliente pueda leer el motivo antes de que se corte. */
    private void rechazar(Cliente cliente, String codigo, String detalle) {
        error(cliente, codigo, detalle);
        cliente.conexion.cerrar();
    }

    private void enviar(Cliente cliente, String linea) {
        try {
            cliente.conexion.enviar(linea);
            RegistroRed.log(ORIGEN, "-> " + cliente.nombreParaLog() + ": " + linea);
        } catch (IOException e) {
            // El lector de ese cliente va a ver la conexión rota y lo va a dar de baja.
            RegistroRed.log(ORIGEN, "No se pudo enviar a " + cliente.nombreParaLog() + ": " + e.getMessage());
            cliente.conexion.cerrar();
        }
    }

    private int primerIdLibre() {
        int id = 0;
        while (jugadores.containsKey(id)) {
            id++;
        }
        return id;
    }

    private int primerAutoLibre() {
        for (int auto = 0; auto < Config.MAX_JUGADORES; auto++) {
            boolean libre = true;
            for (Jugador j : jugadores.values()) {
                libre &= j.auto != auto;
            }
            if (libre) {
                return auto;
            }
        }
        return 0; // no pasa: hay tantos autos como jugadores máximos
    }

    private boolean nombreEnUso(String nombre) {
        for (Jugador j : jugadores.values()) {
            if (j.nombre.equalsIgnoreCase(nombre)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ cierre

    /** Cierra la partida (lo llama el host al salir). Se puede llamar desde cualquier hilo y más de una vez. */
    public void cerrar() {
        cerrar("El anfitrión cerró la partida");
    }

    /**
     * Avisa con CERRADA, cierra todos los sockets (eso destraba los hilos que están esperando) y detiene el
     * hilo de lógica. Solo usa la lista de conexiones, que es segura entre hilos, y no el estado de la partida.
     */
    private void cerrar(String motivo) {
        if (!activo.compareAndSet(true, false)) {
            return;
        }
        RegistroRed.log(ORIGEN, "Cerrando la partida: " + motivo);
        String linea = Protocolo.armar(Protocolo.CERRADA, Protocolo.limpiarTexto(motivo));
        for (Cliente cliente : conexiones) {
            try {
                cliente.conexion.enviar(linea);
            } catch (IOException e) {
                // Si no se puede avisar, igual se cierra.
            }
            cliente.conexion.cerrar();
        }
        try {
            socketServidor.close();
        } catch (IOException e) {
            // Ya estaba cerrado.
        }
    }
}
