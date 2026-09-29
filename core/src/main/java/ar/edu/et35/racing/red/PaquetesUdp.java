package ar.edu.et35.racing.red;

import ar.edu.et35.racing.juego.EntradaAuto;
import ar.edu.et35.racing.juego.EstadoCarrera;
import ar.edu.et35.racing.juego.FotoAuto;
import ar.edu.et35.racing.juego.FotoCarrera;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Arma y lee los dos paquetes UDP del juego, byte por byte como en docs/PROTOCOLO.md (sección 4). ByteBuffer usa
 * big-endian por defecto, igual en el servidor y en el cliente.
 */
public final class PaquetesUdp {
    private PaquetesUdp() {
    }

    public static final byte TIPO_ENTRADA = 1;
    public static final byte TIPO_ESTADO = 2;
    public static final int LARGO_ENTRADA = 11;
    public static final int LARGO_CABECERA_ESTADO = 12;
    public static final int LARGO_AUTO = 24;
    /** Tamaño más grande que puede tener un paquete (ESTADO con 5 autos). Los búferes de recepción lo superan. */
    public static final int LARGO_MAXIMO = LARGO_CABECERA_ESTADO + 5 * LARGO_AUTO;
    /** Valor del campo "cierre" cuando todavía nadie terminó. */
    private static final int SIN_CIERRE = 255;

    private static final int BIT_ACELERAR = 1;
    private static final int BIT_FRENAR = 1 << 1;
    private static final int BIT_IZQUIERDA = 1 << 2;
    private static final int BIT_DERECHA = 1 << 3;
    private static final int BIT_TERMINO = 1;
    private static final int BIT_DESCONECTADO = 1 << 1;

    /** Una ENTRADA recibida. */
    public record Entrada(int id, int token, int secuencia, int botones) {
    }

    /** Un ESTADO recibido: el tick del servidor (que hace de secuencia) y la foto de la carrera. */
    public record Estado(int tick, FotoCarrera foto) {
    }

    // ------------------------------------------------------------------ ENTRADA (11 bytes)

    /** Los botones apretados como bits: 0 acelerar, 1 frenar, 2 izquierda, 3 derecha. */
    public static int botones(EntradaAuto entrada) {
        int bits = 0;
        if (entrada.acelerar) {
            bits |= BIT_ACELERAR;
        }
        if (entrada.frenar) {
            bits |= BIT_FRENAR;
        }
        if (entrada.giro > 0) {
            bits |= BIT_IZQUIERDA;
        }
        if (entrada.giro < 0) {
            bits |= BIT_DERECHA;
        }
        return bits;
    }

    /** Pasa los bits a una EntradaAuto. Con izquierda y derecha apretadas a la vez, el giro es 0. */
    public static void aEntrada(int botones, EntradaAuto destino) {
        destino.acelerar = (botones & BIT_ACELERAR) != 0;
        destino.frenar = (botones & BIT_FRENAR) != 0;
        int izquierda = (botones & BIT_IZQUIERDA) != 0 ? 1 : 0;
        int derecha = (botones & BIT_DERECHA) != 0 ? 1 : 0;
        destino.giro = izquierda - derecha;
    }

    public static byte[] armarEntrada(int id, int token, int secuencia, int botones) {
        return ByteBuffer.allocate(LARGO_ENTRADA)
            .put(TIPO_ENTRADA)
            .put((byte) id)
            .putInt(token)
            .putInt(secuencia)
            .put((byte) botones)
            .array();
    }

    /** Lee una ENTRADA. Devuelve null si el tamaño o el tipo no corresponden. */
    public static Entrada leerEntrada(byte[] datos, int largo) {
        if (largo != LARGO_ENTRADA || datos[0] != TIPO_ENTRADA) {
            return null;
        }
        ByteBuffer b = ByteBuffer.wrap(datos, 0, largo);
        b.get(); // tipo
        return new Entrada(b.get() & 0xFF, b.getInt(), b.getInt(), b.get() & 0xFF);
    }

    // ------------------------------------------------------------------ ESTADO (12 + 24 por auto)

    public static byte[] armarEstado(int tick, FotoCarrera foto) {
        List<FotoAuto> autos = foto.autos();
        ByteBuffer b = ByteBuffer.allocate(LARGO_CABECERA_ESTADO + LARGO_AUTO * autos.size());
        b.put(TIPO_ESTADO);
        b.putInt(tick);
        b.put((byte) foto.estado().ordinal()); // 0 cuenta regresiva, 1 en curso, 2 terminada
        b.putFloat(foto.reloj());
        int cierre = foto.cierreActivo() ? Math.min(SIN_CIERRE - 1, Math.max(0, (int) Math.ceil(foto.cierreRestante())))
            : SIN_CIERRE;
        b.put((byte) cierre);
        b.put((byte) autos.size());
        for (FotoAuto a : autos) {
            b.put((byte) a.id());
            b.putFloat(a.x());
            b.putFloat(a.y());
            b.putFloat(a.angulo());
            b.put((byte) a.vueltas());
            b.put((byte) a.posicion());
            b.put((byte) ((a.terminado() ? BIT_TERMINO : 0) | (a.desconectado() ? BIT_DESCONECTADO : 0)));
            b.putFloat(a.tiempo());
            b.putFloat(a.mejorVuelta());
        }
        return b.array();
    }

    /** Lee un ESTADO. Devuelve null si el tipo, el estado o el tamaño no corresponden (paquete dañado o ajeno). */
    public static Estado leerEstado(byte[] datos, int largo) {
        if (largo < LARGO_CABECERA_ESTADO || datos[0] != TIPO_ESTADO) {
            return null;
        }
        try {
            ByteBuffer b = ByteBuffer.wrap(datos, 0, largo);
            b.get(); // tipo
            int tick = b.getInt();
            int indiceEstado = b.get() & 0xFF;
            float reloj = b.getFloat();
            int cierre = b.get() & 0xFF;
            int cantidad = b.get() & 0xFF;
            if (indiceEstado >= EstadoCarrera.values().length || largo != LARGO_CABECERA_ESTADO + LARGO_AUTO * cantidad) {
                return null;
            }
            List<FotoAuto> autos = new ArrayList<>(cantidad);
            for (int i = 0; i < cantidad; i++) {
                int id = b.get() & 0xFF;
                float x = b.getFloat();
                float y = b.getFloat();
                float angulo = b.getFloat();
                int vueltas = b.get() & 0xFF;
                int posicion = b.get() & 0xFF;
                int banderas = b.get() & 0xFF;
                float tiempo = b.getFloat();
                float mejor = b.getFloat();
                // La velocidad no viaja: la estima el cliente al interpolar.
                autos.add(new FotoAuto(id, x, y, angulo, 0f, 0f, vueltas, posicion, (banderas & BIT_TERMINO) != 0,
                    (banderas & BIT_DESCONECTADO) != 0, tiempo, mejor));
            }
            FotoCarrera foto = new FotoCarrera(EstadoCarrera.values()[indiceEstado], reloj,
                cierre == SIN_CIERRE ? Float.NaN : cierre, autos);
            return new Estado(tick, foto);
        } catch (BufferUnderflowException e) {
            return null;
        }
    }
}
