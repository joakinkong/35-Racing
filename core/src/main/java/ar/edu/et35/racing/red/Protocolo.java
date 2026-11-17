package ar.edu.et35.racing.red;

import ar.edu.et35.racing.juego.ResultadoJugador;
import ar.edu.et35.racing.util.Config;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public final class Protocolo {
    private Protocolo() {
    }

    public static final String UNIRSE = "UNIRSE";
    public static final String ELEGIR_PILOTO = "ELEGIR_PILOTO";
    public static final String ELEGIR_CIRCUITO = "ELEGIR_CIRCUITO";
    public static final String ELEGIR_VUELTAS = "ELEGIR_VUELTAS";
    public static final String ELEGIR_GRILLA = "ELEGIR_GRILLA";
    public static final String MOVER_GRILLA = "MOVER_GRILLA";
    public static final String GRILLA_AL_AZAR = "GRILLA_AL_AZAR";
    public static final String ELEGIR_MODO = "ELEGIR_MODO";
    public static final String AGREGAR_CIRCUITO = "AGREGAR_CIRCUITO";
    public static final String QUITAR_CIRCUITO = "QUITAR_CIRCUITO";
    public static final String SIGUIENTE_CARRERA = "SIGUIENTE_CARRERA";
    public static final String AGREGAR_BOT = "AGREGAR_BOT";
    public static final String QUITAR_BOT = "QUITAR_BOT";
    public static final String NIVEL_BOT = "NIVEL_BOT";
    public static final String LISTO = "LISTO";
    public static final String INICIAR = "INICIAR";
    public static final String REVANCHA = "REVANCHA";
    public static final String SALIR = "SALIR";
    public static final String PING = "PING";

    public static final String BIENVENIDA = "BIENVENIDA";
    public static final String LOBBY = "LOBBY";
    public static final String CUENTA_REGRESIVA = "CUENTA_REGRESIVA";
    public static final String GRILLA = "GRILLA";
    public static final String TORNEO = "TORNEO";
    public static final String LARGADA = "LARGADA";
    public static final String SALIO = "SALIO";
    public static final String RESULTADOS = "RESULTADOS";
    public static final String ERROR = "ERROR";
    public static final String CERRADA = "CERRADA";
    public static final String PONG = "PONG";

    public static final String CONEXION_FALLIDA = "_CONEXION_FALLIDA";
    public static final String CONEXION_PERDIDA = "_CONEXION_PERDIDA";

    public static final String PARTIDA_LLENA = "PARTIDA_LLENA";
    public static final String CARRERA_EN_CURSO = "CARRERA_EN_CURSO";
    public static final String NOMBRE_REPETIDO = "NOMBRE_REPETIDO";
    public static final String VERSION_DISTINTA = "VERSION_DISTINTA";
    public static final String NOMBRE_INVALIDO = "NOMBRE_INVALIDO";
    public static final String PILOTO_OCUPADO = "PILOTO_OCUPADO";
    public static final String NO_PERMITIDO = "NO_PERMITIDO";
    public static final String FALTAN_JUGADORES = "FALTAN_JUGADORES";
    public static final String CIRCUITO_NO_CARGO = "CIRCUITO_NO_CARGO";

    private static final Pattern NOMBRE = Pattern.compile("[\\p{L}\\p{N} ]+");

    public record Mensaje(String nombre, List<String> campos) {
        public String campo(int indice) {
            return indice < campos.size() ? campos.get(indice) : "";
        }

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

    public static String armar(String nombre, Object... campos) {
        StringBuilder linea = new StringBuilder(nombre);
        for (Object campo : campos) {
            linea.append(';').append(campo);
        }
        return linea.toString();
    }

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

    public static String armarResultados(List<ResultadoJugador> resultados) {
        return armarTabla(RESULTADOS, resultados);
    }

    public static String armarGrilla(List<ResultadoJugador> clasificacion) {
        return armarTabla(GRILLA, clasificacion);
    }

    private static String armarTabla(String nombre, List<ResultadoJugador> resultados) {
        StringBuilder linea = new StringBuilder(armar(nombre, resultados.size()));
        for (ResultadoJugador r : resultados) {
            linea.append(';').append(r.posicion()).append(';').append(r.id()).append(';').append(r.nombre())
                .append(';').append(aMilisegundos(r.tiempoTotal())).append(';').append(aMilisegundos(r.mejorVuelta()));
        }
        return linea.toString();
    }

    public record Cuenta(String circuito, int vueltas, boolean clasificacion, List<Integer> grilla) {
    }

    public static String armarCuenta(String circuito, int vueltas, int segundos, boolean clasificacion, List<Integer> grilla) {
        StringBuilder linea = new StringBuilder(armar(CUENTA_REGRESIVA, circuito, vueltas, segundos, clasificacion ? 1 : 0, grilla.size()));
        for (int id : grilla) {
            linea.append(';').append(id);
        }
        return linea.toString();
    }

    public static Cuenta leerCuenta(Mensaje mensaje) {
        int cantidad = Math.min(mensaje.entero(4, 0), Config.MAX_JUGADORES);
        List<Integer> grilla = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            grilla.add(mensaje.entero(5 + i, -1));
        }
        return new Cuenta(mensaje.campo(0), mensaje.entero(1, Config.VUELTAS), mensaje.campo(3).equals("1"), List.copyOf(grilla));
    }

    public static List<ResultadoJugador> leerResultados(Mensaje mensaje) {
        int cantidad = Math.min(mensaje.entero(0, 0), Config.MAX_JUGADORES);
        List<ResultadoJugador> lista = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            int base = 1 + i * 5;
            if (base + 4 >= mensaje.campos().size()) {
                break;
            }
            lista.add(new ResultadoJugador(mensaje.entero(base, i + 1), mensaje.entero(base + 1, -1),
                mensaje.campo(base + 2), aSegundos(mensaje.entero(base + 3, -1)), aSegundos(mensaje.entero(base + 4, -1))));
        }
        return lista;
    }

    private static int aMilisegundos(float segundos) {
        return Float.isNaN(segundos) ? -1 : Math.round(segundos * 1000f);
    }

    private static float aSegundos(int milisegundos) {
        return milisegundos < 0 ? Float.NaN : milisegundos / 1000f;
    }

    public static String limpiarTexto(String texto) {
        return texto.replace(';', ',').replace('\n', ' ').replace('\r', ' ');
    }
}
