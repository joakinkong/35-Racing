package ar.edu.et35.racing.red;

import ar.edu.et35.racing.util.Config;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ConexionTcp {
    private final Socket socket;
    private final BufferedReader entrada;
    private final Writer salida;
    private volatile boolean cerrada;

    public ConexionTcp(Socket socket) throws IOException {
        this.socket = socket;
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(Config.TIMEOUT_TCP_MS);
        this.entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.salida = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
    }

    public String leerLinea() throws IOException {
        return entrada.readLine();
    }

    public synchronized void enviar(String linea) throws IOException {
        salida.write(linea);
        salida.write('\n');
        salida.flush();
    }

    public boolean cerrada() {
        return cerrada;
    }

    public void cerrar() {
        cerrada = true;
        try {
            socket.close();
        } catch (IOException e) {
        }
    }

    public InetAddress direccionIp() {
        return socket.getInetAddress();
    }

    public boolean esLocal() {
        return socket.getInetAddress().isLoopbackAddress();
    }

    public String direccionRemota() {
        return socket.getRemoteSocketAddress() == null ? "?" : socket.getRemoteSocketAddress().toString();
    }
}
