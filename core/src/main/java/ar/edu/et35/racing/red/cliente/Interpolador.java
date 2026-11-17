package ar.edu.et35.racing.red.cliente;

import ar.edu.et35.racing.juego.FotoAuto;
import ar.edu.et35.racing.juego.FotoCarrera;
import ar.edu.et35.racing.util.Config;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Interpolador {
    private static final double SALTO_RELOJ = 0.25;
    private static final double CORRECCION_RELOJ = 0.1;
    private static final double MEMORIA = 1.0;

    private record Muestra(double tiempo, FotoCarrera foto) {
    }

    private final double retraso;
    private final ArrayDeque<Muestra> muestras = new ArrayDeque<>();
    private boolean hayTick;
    private int ultimoTick;
    private double reloj = Double.NaN;

    public Interpolador(float retraso) {
        this.retraso = retraso;
    }

    public boolean agregar(int tick, FotoCarrera foto) {
        if (hayTick && tick <= ultimoTick) {
            return false;
        }
        hayTick = true;
        ultimoTick = tick;
        double t = tick / (double) Config.TICKS_POR_SEGUNDO;
        muestras.addLast(new Muestra(t, foto));
        if (Double.isNaN(reloj) || Math.abs(t - reloj) > SALTO_RELOJ) {
            reloj = t;
        } else {
            reloj += (t - reloj) * CORRECCION_RELOJ;
        }
        while (muestras.size() > 2 && muestras.peekFirst().tiempo() < reloj - retraso - MEMORIA) {
            muestras.removeFirst();
        }
        return true;
    }

    public void avanzar(float delta) {
        if (!Double.isNaN(reloj)) {
            reloj += delta;
        }
    }

    public double tiempoDibujado() {
        return reloj - retraso;
    }

    public FotoCarrera foto() {
        if (muestras.isEmpty()) {
            return null;
        }
        double tDibujo = reloj - retraso;
        Muestra anterior = null;
        Muestra posterior = null;
        for (Muestra m : muestras) {
            if (m.tiempo() <= tDibujo) {
                anterior = m;
            } else {
                posterior = m;
                break;
            }
        }
        FotoCarrera ultima = muestras.peekLast().foto();
        List<FotoAuto> autos = new ArrayList<>();
        for (FotoAuto actual : ultima.autos()) {
            autos.add(interpolar(actual, anterior, posterior, tDibujo));
        }
        return new FotoCarrera(ultima.estado(), ultima.reloj(), ultima.cierreRestante(), autos);
    }

    private FotoAuto interpolar(FotoAuto actual, Muestra anterior, Muestra posterior, double tDibujo) {
        if (anterior == null || posterior == null) {
            Muestra cercana = anterior != null ? anterior : posterior;
            FotoAuto a = cercana.foto().auto(actual.id());
            if (a == null) {
                return actual;
            }
            float[] velocidad = velocidadFinal(actual.id());
            return actual.conMovimiento(a.x(), a.y(), a.angulo(), velocidad[0], velocidad[1]);
        }
        FotoAuto a = anterior.foto().auto(actual.id());
        FotoAuto b = posterior.foto().auto(actual.id());
        if (a == null || b == null) {
            return actual;
        }
        double dt = posterior.tiempo() - anterior.tiempo();
        float alfa = (float) ((tDibujo - anterior.tiempo()) / dt);
        float x = a.x() + (b.x() - a.x()) * alfa;
        float y = a.y() + (b.y() - a.y()) * alfa;
        float angulo = a.angulo() + diferenciaAngular(a.angulo(), b.angulo()) * alfa;
        return actual.conMovimiento(x, y, angulo, (float) ((b.x() - a.x()) / dt), (float) ((b.y() - a.y()) / dt));
    }

    private float[] velocidadFinal(int id) {
        if (muestras.size() < 2) {
            return new float[] {0f, 0f};
        }
        Iterator<Muestra> desdeElFinal = muestras.descendingIterator();
        Muestra ultima = desdeElFinal.next();
        Muestra penultima = desdeElFinal.next();
        FotoAuto b = ultima.foto().auto(id);
        FotoAuto a = penultima.foto().auto(id);
        double dt = ultima.tiempo() - penultima.tiempo();
        if (a == null || b == null || dt <= 0) {
            return new float[] {0f, 0f};
        }
        return new float[] {(float) ((b.x() - a.x()) / dt), (float) ((b.y() - a.y()) / dt)};
    }

    private static float diferenciaAngular(float desde, float hasta) {
        float d = (hasta - desde) % 360f;
        if (d > 180f) {
            d -= 360f;
        } else if (d < -180f) {
            d += 360f;
        }
        return d;
    }
}
