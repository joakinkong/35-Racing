package ar.edu.et35.racing.juego;

import com.badlogic.gdx.math.MathUtils;
import java.util.Collection;
import java.util.Random;

/**
 * El piloto de un bot: mira el auto y arma una {@link EntradaAuto}, igual que un jugador con el teclado. No toca la física
 * ni la posición, así que un bot no puede hacer nada que un jugador no pueda: apretar acelerar, frenar, izquierda o derecha.
 * Lo usa el servidor, igual que las entradas que llegan por UDP.
 */
public final class PilotoBot {
    /** Lo que mira adelante para doblar: un mínimo más una parte de lo que recorre por segundo. */
    private static final float MIRA_MINIMA = 34f;
    private static final float MIRA_POR_VELOCIDAD = 0.16f;
    /** Lo que se anticipa para decidir la velocidad, en segundos: da tiempo a que los frenos hagan efecto. */
    private static final float ANTICIPACION_VELOCIDAD = 0.05f;
    private static final float GRADOS_SIN_CORREGIR = 2f;
    private static final float TOLERANCIA_FRENO = 1.02f;
    /** Muestras hacia atrás y hacia adelante en que busca dónde está, para no saltar a otro tramo de la pista. */
    private static final int BUSQUEDA_ATRAS = 10;
    private static final int BUSQUEDA_ADELANTE = 40;
    /** Si está más lejos que esto del camino lo volvió a buscar en todo el circuito (lo sacaron de su tramo a los golpes). */
    private static final float DISTANCIA_PERDIDO = 260f;

    // Trabado contra algo: marcha atrás un rato.
    private static final float VELOCIDAD_TRABADO = 15f;
    private static final float SEGUNDOS_PARA_TRABADO = 1f;
    private static final float SEGUNDOS_DE_REVERSA = 0.9f;

    // Otros autos
    private static final float VENTANA_AUTOS_MINIMA = 50f;
    private static final float VENTANA_AUTOS_POR_VELOCIDAD = 0.4f;
    private static final float ANCHO_AUTO = 2f * ParametrosAuto.RADIO_COLISION + 6f;
    private static final float CORRIMIENTO_ESQUIVE = 40f;
    private static final float DISTANCIA_PEGADO = 42f;

    /** Pequeña diferencia entre bots del mismo nivel, para que no corran todos en fila india. */
    private static final float VARIACION_RITMO = 0.015f;
    /** Mucho menor en el difícil: alcanza para que dos no hagan exactamente el mismo tiempo en la clasificación. */
    private static final float VARIACION_RITMO_DIFICIL = 0.002f;

    private final RecorridoBot recorrido;
    private final NivelBot nivel;
    private final Random azar;
    private final float ritmo;
    private int indice = -1;
    private float tiempoTrabado;
    private float tiempoReversa;
    private float proximoError;
    private float tiempoError;
    private float desvioError;

    public PilotoBot(RecorridoBot recorrido, NivelBot nivel, long semilla) {
        this.recorrido = recorrido;
        this.nivel = nivel;
        this.azar = new Random(semilla);
        float variacion = nivel == NivelBot.DIFICIL ? VARIACION_RITMO_DIFICIL : VARIACION_RITMO;
        this.ritmo = 1f - variacion + azar.nextFloat() * 2f * variacion;
        programarError();
    }

    private void programarError() {
        // Exponencial: los errores llegan cuando quieren, no a intervalos fijos.
        proximoError = nivel.segundosEntreErrores() <= 0f ? Float.MAX_VALUE
            : -nivel.segundosEntreErrores() * (float) Math.log(1f - azar.nextFloat());
    }

    /** Decide qué "teclas" aprieta en este paso de la simulación. */
    public void decidir(Participante yo, Collection<Participante> todos, EntradaAuto salida, float dt) {
        Auto auto = yo.auto();
        ubicarse(auto);
        float rapidez = auto.rapidez();
        actualizarError(dt);

        // Punto al que apuntar: sobre el camino, un poco más adelante según la velocidad.
        int mira = recorrido.indice(indice + Math.round((MIRA_MINIMA + MIRA_POR_VELOCIDAD * rapidez) / recorrido.paso()));
        float objetivoX = recorrido.x(mira);
        float objetivoY = recorrido.y(mira);
        float velocidadDeseada = recorrido.velocidad(recorrido.indice(indice
            + Math.round(rapidez * ANTICIPACION_VELOCIDAD / recorrido.paso()))) * ritmo;

        if (tiempoError > 0f) {
            float lado = recorrido.ladoDe(mira, objetivoX, objetivoY) + desvioError * recorrido.limite(mira);
            objetivoX = recorrido.xCorrido(mira, lado);
            objetivoY = recorrido.yCorrido(mira, lado);
        }

        // Otros autos: si hay uno en el camino, se corre para el lado con más lugar y, si está encima, va a su ritmo.
        float radianes = auto.angulo() * MathUtils.degreesToRadians;
        float adelanteX = MathUtils.cos(radianes);
        float adelanteY = MathUtils.sin(radianes);
        float ventana = Math.max(VENTANA_AUTOS_MINIMA, VENTANA_AUTOS_POR_VELOCIDAD * rapidez);
        for (Participante otro : todos) {
            if (otro == yo || otro.desconectado()) {
                continue;
            }
            float dx = otro.auto().x() - auto.x();
            float dy = otro.auto().y() - auto.y();
            float frente = dx * adelanteX + dy * adelanteY;
            float costado = -dx * adelanteY + dy * adelanteX;
            if (frente <= 0f || frente > ventana || Math.abs(costado) > ANCHO_AUTO) {
                continue;
            }
            // El otro tiene que ser más lento: si va más rápido, se está yendo solo.
            if (otro.auto().rapidez() >= rapidez) {
                continue;
            }
            float ladoObjetivo = recorrido.ladoDe(mira, objetivoX, objetivoY);
            float ladoOtro = recorrido.ladoDe(mira, otro.auto().x(), otro.auto().y());
            float sentido = ladoOtro > ladoObjetivo ? -1f : 1f;
            // Si de ese lado no hay lugar, por el otro.
            if (Math.abs(ladoOtro + sentido * CORRIMIENTO_ESQUIVE) > recorrido.limite(mira)) {
                sentido = -sentido;
            }
            float nuevoLado = ladoOtro + sentido * CORRIMIENTO_ESQUIVE;
            objetivoX = recorrido.xCorrido(mira, nuevoLado);
            objetivoY = recorrido.yCorrido(mira, nuevoLado);
            // Solo si lo tiene justo adelante: si ya está de costado, lo está pasando.
            if (frente < DISTANCIA_PEGADO && Math.abs(costado) < 2f * ParametrosAuto.RADIO_COLISION) {
                velocidadDeseada = Math.min(velocidadDeseada, Math.max(otro.auto().rapidez(), VELOCIDAD_TRABADO * 2f));
            }
        }

        float dx = objetivoX - auto.x();
        float dy = objetivoY - auto.y();
        float angulo = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
        float diferencia = ((angulo - auto.angulo()) % 360f + 540f) % 360f - 180f;

        if (rapidez < VELOCIDAD_TRABADO) {
            tiempoTrabado += dt;
        } else {
            tiempoTrabado = 0f;
        }
        if (tiempoTrabado > SEGUNDOS_PARA_TRABADO) {
            tiempoTrabado = 0f;
            tiempoReversa = SEGUNDOS_DE_REVERSA;
        }
        if (tiempoReversa > 0f) {
            tiempoReversa -= dt;
            salida.acelerar = false;
            salida.frenar = true;
            // En reversa el volante va al revés: al retroceder, la trompa queda apuntando al camino.
            salida.giro = diferencia > GRADOS_SIN_CORREGIR ? -1 : diferencia < -GRADOS_SIN_CORREGIR ? 1 : 0;
            return;
        }

        salida.giro = diferencia > GRADOS_SIN_CORREGIR ? 1 : diferencia < -GRADOS_SIN_CORREGIR ? -1 : 0;
        salida.acelerar = rapidez < velocidadDeseada && tiempoError <= 0f;
        salida.frenar = rapidez > velocidadDeseada * TOLERANCIA_FRENO;
    }

    /** Busca la muestra más cercana cerca de la anterior: en un circuito que pasa dos veces por el mismo lugar, no salta de tramo. */
    private void ubicarse(Auto auto) {
        if (indice < 0) {
            indice = masCercana(auto, 0, recorrido.muestras());
            return;
        }
        int encontrada = masCercana(auto, indice - BUSQUEDA_ATRAS, BUSQUEDA_ATRAS + BUSQUEDA_ADELANTE);
        float distancia = (float) Math.hypot(recorrido.x(encontrada) - auto.x(), recorrido.y(encontrada) - auto.y());
        indice = distancia > DISTANCIA_PERDIDO ? masCercana(auto, 0, recorrido.muestras()) : encontrada;
    }

    private int masCercana(Auto auto, int desde, int cantidad) {
        int mejor = recorrido.indice(desde);
        float mejorDistancia = Float.MAX_VALUE;
        for (int k = 0; k < cantidad; k++) {
            int i = recorrido.indice(desde + k);
            float dx = recorrido.x(i) - auto.x();
            float dy = recorrido.y(i) - auto.y();
            float distancia = dx * dx + dy * dy;
            if (distancia < mejorDistancia) {
                mejorDistancia = distancia;
                mejor = i;
            }
        }
        return mejor;
    }

    private void actualizarError(float dt) {
        if (tiempoError > 0f) {
            tiempoError -= dt;
            return;
        }
        proximoError -= dt;
        if (proximoError <= 0f) {
            tiempoError = nivel.duracionError();
            desvioError = (azar.nextBoolean() ? 1f : -1f) * nivel.desvioError();
            programarError();
        }
    }
}
