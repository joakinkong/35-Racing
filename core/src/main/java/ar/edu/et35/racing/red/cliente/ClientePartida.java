package ar.edu.et35.racing.red.cliente;

import ar.edu.et35.racing.juego.EntradaAuto;
import ar.edu.et35.racing.juego.ModoGrilla;
import ar.edu.et35.racing.juego.NivelBot;
import ar.edu.et35.racing.red.ConexionTcp;
import ar.edu.et35.racing.red.EstadoLobby;
import ar.edu.et35.racing.red.EstadoTorneo;
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

public class ClientePartida {
    private final String nombre;
    private final String origen;
    private final ConcurrentLinkedQueue<Mensaje> recibidos = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<PaquetesUdp.Estado> estados = new ConcurrentLinkedQueue<>();
    private volatile ConexionTcp conexion;
    private volatile Socket conectando;
    private volatile DatagramSocket socketUdp;
    private volatile InetSocketAddress servidorUdp;
    private volatile boolean cerrando;
    private volatile int udpAjenos;

    private int miId = -1;
    private int token;
    private boolean esHost;
    private EstadoLobby lobby = EstadoLobby.vacio();
    private EstadoTorneo torneo;
    private long ultimoPingNanos = System.nanoTime();
    private int secuenciaEntrada;

    public ClientePartida(String nombre) {
        this.nombre = nombre;
        this.origen = "CLIENTE " + nombre;
    }

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
                if (!mensaje.es(Protocolo.PONG)) {
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
            torneo = null;
        } else if (mensaje.es(Protocolo.TORNEO)) {
            torneo = EstadoTorneo.desde(mensaje);
        }
    }

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

    private void recibirUdp(DatagramSocket socket) {
        byte[] bufer = new byte[PaquetesUdp.LARGO_MAXIMO + 64];
        DatagramPacket paquete = new DatagramPacket(bufer, bufer.length);
        while (!cerrando) {
            try {
                paquete.setLength(bufer.length);
                socket.receive(paquete);
            } catch (IOException e) {
                return;
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
                udpAjenos++;
            }
        }
    }

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
        }
    }

    public PaquetesUdp.Estado sondearEstado() {
        return estados.poll();
    }

    public void descartarEstadosPendientes() {
        estados.clear();
    }

    public int puertoUdpLocal() {
        DatagramSocket socket = socketUdp;
        return socket != null ? socket.getLocalPort() : -1;
    }

    public int udpAjenos() {
        return udpAjenos;
    }

    public void actualizar() {
        long ahora = System.nanoTime();
        if (conexion != null && ahora - ultimoPingNanos >= Config.INTERVALO_PING_MS * 1_000_000L) {
            ultimoPingNanos = ahora;
            enviar(Protocolo.armar(Protocolo.PING, ahora / 1_000_000L));
        }
    }

    public void elegirPiloto(int piloto) {
        enviar(Protocolo.armar(Protocolo.ELEGIR_PILOTO, piloto));
    }

    public void elegirCircuito(int circuito) {
        enviar(Protocolo.armar(Protocolo.ELEGIR_CIRCUITO, circuito));
    }

    public void elegirVueltas(int vueltas) {
        enviar(Protocolo.armar(Protocolo.ELEGIR_VUELTAS, vueltas));
    }

    public void elegirGrilla(ModoGrilla modo) {
        enviar(Protocolo.armar(Protocolo.ELEGIR_GRILLA, modo.ordinal()));
    }

    public void elegirClasificacion(boolean hay) {
        elegirGrilla(hay ? ModoGrilla.CLASIFICACION : ModoGrilla.MANUAL);
    }

    public void moverEnGrilla(int id, int lugares) {
        enviar(Protocolo.armar(Protocolo.MOVER_GRILLA, id, lugares));
    }

    public void sortearGrilla() {
        enviar(Protocolo.armar(Protocolo.GRILLA_AL_AZAR));
    }

    public void elegirTorneo(boolean torneo) {
        enviar(Protocolo.armar(Protocolo.ELEGIR_MODO, torneo ? 1 : 0));
    }

    public void agregarCircuito(int circuito) {
        enviar(Protocolo.armar(Protocolo.AGREGAR_CIRCUITO, circuito));
    }

    public void quitarCircuito(int lugar) {
        enviar(Protocolo.armar(Protocolo.QUITAR_CIRCUITO, lugar));
    }

    public void siguienteCarrera() {
        enviar(Protocolo.armar(Protocolo.SIGUIENTE_CARRERA));
    }

    public void agregarBot(NivelBot nivel) {
        agregarBot(nivel, -1);
    }

    public void agregarBot(NivelBot nivel, int piloto) {
        enviar(Protocolo.armar(Protocolo.AGREGAR_BOT, nivel.ordinal(), piloto));
    }

    public void quitarBot(int id) {
        enviar(Protocolo.armar(Protocolo.QUITAR_BOT, id));
    }

    public void cambiarNivelBot(int id, NivelBot nivel) {
        enviar(Protocolo.armar(Protocolo.NIVEL_BOT, id, nivel.ordinal()));
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

    public void cerrar() {
        if (cerrando) {
            return;
        }
        cerrando = true;
        Socket enCurso = conectando;
        if (enCurso != null) {
            try {
                enCurso.close();
            } catch (IOException e) {
            }
        }
        ConexionTcp actual = conexion;
        if (actual != null) {
            try {
                enviarLinea(Protocolo.armar(Protocolo.SALIR));
            } catch (IOException e) {
            }
            actual.cerrar();
        }
        DatagramSocket udp = socketUdp;
        if (udp != null) {
            udp.close();
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

    public EstadoTorneo torneo() {
        return torneo;
    }

    public int miPiloto() {
        EstadoLobby.JugadorLobby yo = lobby.jugador(miId);
        return yo != null ? yo.piloto() : 0;
    }
}
