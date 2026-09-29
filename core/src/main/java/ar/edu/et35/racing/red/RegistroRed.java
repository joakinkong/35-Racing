package ar.edu.et35.racing.red;

import ar.edu.et35.racing.util.Config;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Log en consola de cada mensaje que se envía o se recibe. Solo escribe si {@link Config#DEBUG_RED} está activo. */
public final class RegistroRed {
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private RegistroRed() {
    }

    public static void log(String origen, String texto) {
        if (Config.DEBUG_RED) {
            System.out.println(LocalTime.now().format(HORA) + " [" + origen + "] " + texto);
        }
    }
}
