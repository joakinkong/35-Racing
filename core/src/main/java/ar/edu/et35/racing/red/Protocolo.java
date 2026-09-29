package ar.edu.et35.racing.red;

import ar.edu.et35.racing.util.Config;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Nombres de los mensajes TCP y cómo se arman y se leen. Es la traducción a código de docs/PROTOCOLO.md
 * (sección 3): texto UTF-8, un mensaje por línea, campos separados por ";".
 */
public final class Protocolo {
    private Protocolo() {
    }

    // Cliente -> servidor
    public static final String UNIRSE = "UNIRSE";
    public static final String ELEGIR_AUTO = "ELEGIR_AUTO";
    public static final String LISTO = "LISTO";
    public static final String INICIAR = "INICIAR";
    public static final String REVANCHA = "REVANCHA";
    public static final String SALIR = "SALIR";
    public static final String PING = "PING";

    // Servidor -> cliente
    public static final String BIENVENIDA = "BIENVENIDA";
    public static final String LOBBY = "LOBBY";
    public static final String CUENTA_REGRESIVA = "CUENTA_REGRESIVA";
    public static final String LARGADA = "LARGADA";
    public static final String SALIO = "SALIO";
    public static final String RESULTADOS = "RESULTADOS";
    public static final String ERROR = "ERROR";
    public static final String CERRADA = "CERRADA";
    public static final String PONG = "PONG";

    // Mensajes que el cliente genera por su cuenta (no viajan por la red) para avisarle a la pantalla
    // que la conexión falló o se perdió. El "_" del principio evita que se confundan con uno del protocolo.
    public static final String CONEXION_FALLIDA = "_CONEXION_FALLIDA";
    public static final String CONEXION_PERDIDA = "_CONEXION_PERDIDA";

    // Códigos de ERROR
    public static final String PARTIDA_LLENA = "PARTIDA_LLENA";
    public static final String CARRERA_EN_CURSO = "CARRERA_EN_CURSO";
    public static final String NOMBRE_REPETIDO = "NOMBRE_REPETIDO";
    public static final String VERSION_DISTINTA = "VERSION_DISTINTA";
    public static final String NOMBRE_INVALIDO = "NOMBRE_INVALIDO";
    public static final String AUTO_OCUPADO = "AUTO_OCUPADO";
    public static final String NO_PERMITIDO = "NO_PERMITIDO";
    public static final String FALTAN_JUGADORES = "FALTAN_JUGADORES";

    /** Solo letras (con acentos y ñ), números y espacios: así el nombre nunca trae ";" ni saltos de línea. */
    private static final Pattern NOMBRE = Pattern.compile("[\\p{L}\\p{N} ]+");

    /** Un mensaje ya separado: su nombre y sus campos (sin el nombre). */
    public record Mensaje(String nombre, List<String> campos) {
        public String campo(int indice) {
            return indice < campos.size() ? campos.get(indice) : "";
        }

        /** Campo como entero, o el valor por defecto si falta o no es un número. */
        public int entero(int indice, int pordefecto) {
            try {
                return Integer.parseInt(campo(indice).trim());
            } catch (NumberFormatException e) {
                return pordefecto;
            }
        }

        public boolean es(String nombreEsperado) {
            return nombre.equals(nombreEsperado);
        }
    }

    /** Arma la línea de un mensaje (sin el "\n" final). */
    public static String armar(String nombre, Object... campos) {
        StringBuilder linea = new StringBuilder(nombre);
        for (Object campo : campos) {
            linea.append(';').append(campo);
        }
        return linea.toString();
    }

    /** Separa una línea recibida. El -1 conserva los campos vacíos del final. Devuelve null si la línea está vacía. */
    public static Mensaje leer(String linea) {
        if (linea == null || linea.isBlank()) {
            return null;
        }
        String[] partes = linea.strip().split(";", -1);
        return new Mensaje(partes[0], Arrays.asList(partes).subList(1, partes.length));
    }

    public static boolean nombreValido(String nombre) {
        return nombre != null && !nombre.isBlank() && nombre.equals(nombre.strip())
            && nombre.length() <= Config.LARGO_MAXIMO_NOMBRE && NOMBRE.matcher(nombre).matches();
    }

    /** El texto del ERROR va en un solo campo: no puede llevar ";" ni saltos de línea. */
    public static String limpiarTexto(String texto) {
        return texto.replace(';', ',').replace('\n', ' ').replace('\r', ' ');
    }
}
