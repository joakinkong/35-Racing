package ar.edu.et35.racing.red;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Las direcciones IPv4 de esta PC en la red local, para que el host se las dicte a los demás. */
public final class DireccionesLocales {
    private DireccionesLocales() {
    }

    /**
     * Devuelve las IPv4 de las placas de red activas, sin la de loopback. Se descartan las de máquinas virtuales
     * y del subsistema de Linux, que no sirven para conectarse desde otra PC.
     */
    public static List<String> listar() {
        List<String> direcciones = new ArrayList<>();
        try {
            for (NetworkInterface placa : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!placa.isUp() || placa.isLoopback() || placa.isVirtual() || esVirtual(placa.getDisplayName())) {
                    continue;
                }
                for (InetAddress direccion : Collections.list(placa.getInetAddresses())) {
                    if (direccion instanceof Inet4Address && !direccion.isLoopbackAddress()
                        && !direccion.isLinkLocalAddress()) {
                        direcciones.add(direccion.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            RegistroRed.log("RED", "No se pudieron leer las placas de red: " + e.getMessage());
        }
        return direcciones;
    }

    private static boolean esVirtual(String nombre) {
        String minusculas = nombre == null ? "" : nombre.toLowerCase();
        return minusculas.contains("virtual") || minusculas.contains("vmware") || minusculas.contains("vethernet")
            || minusculas.contains("hyper-v") || minusculas.contains("wsl") || minusculas.contains("bluetooth");
    }
}
