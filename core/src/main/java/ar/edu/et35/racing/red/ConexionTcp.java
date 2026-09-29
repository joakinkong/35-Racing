package ar.edu.et35.racing.red;

import ar.edu.et35.racing.util.Config;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Un Socket TCP con mensajes de texto por línea (UTF-8). Lo usan igual el servidor (una por cliente) y el
 * cliente. Se puede leer desde un hilo y escribir desde otro; las escrituras están sincronizadas.
 */
public class ConexionTcp {
    private final Socket socket;
    private final BufferedReader entrada;
    private final Writer salida;
    private volatile boolean cerrada;

    public ConexionTcp(Socket socket) throws IOException {
        this.socket = socket;
        // Mensajes chicos: sin esto el sistema operativo puede retenerlos unos ms para juntarlos.
        socket.setTcpNoDelay(true);
        // Si pasa este tiempo sin recibir nada, readLine() lanza SocketTimeoutException y la conexión se da por perdida.
        socket.setSoTimeout(Config.TIMEOUT_TCP_MS);
        this.entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.salida = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
    }

    /** Espera una línea. Devuelve null si el otro lado cerró la conexión. */
    public String leerLinea() throws IOException {
        return entrada.readLine();
    }

    /** Manda una línea. Se agrega "\n" a mano: println escribiría "\r\n" en Windows. */
    public synchronized void enviar(String linea) throws IOException {
        salida.write(linea);
        salida.write('\n');
        salida.flush();
    }

    public boolean cerrada() {
        return cerrada;
    }

    /** Cierra el socket. Los hilos que estén esperando en leerLinea() reciben una excepción y terminan. */
    public void cerrar() {
        cerrada = true;
        try {
            socket.close();
        } catch (IOException e) {
            // Ya estaba cerrado o no se pudo: no hay nada más que hacer.
        }
    }

    public boolean esLocal() {
        return socket.getInetAddress().isLoopbackAddress();
    }

    /** IP y puerto del otro lado, para los logs. */
    public String direccionRemota() {
        return socket.getRemoteSocketAddress() == null ? "?" : socket.getRemoteSocketAddress().toString();
    }
}
