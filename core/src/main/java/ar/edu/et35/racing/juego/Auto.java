package ar.edu.et35.racing.juego;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

/**
 * Auto con física arcade: posición, ángulo (grados, 0 = derecha) y velocidad como vector de mundo.
 * La velocidad se separa en una componente hacia adelante y otra lateral respecto del ángulo del auto;
 * la lateral se va perdiendo de a poco, y de ahí sale el derrape leve.
 */
public class Auto {
    private final Vector2 posicion = new Vector2();
    private final Vector2 velocidad = new Vector2();
    private float angulo;

    // Estado del tick anterior, para interpolar el dibujo entre dos ticks de la simulación
    private float xPrevia;
    private float yPrevia;
    private float anguloPrevio;

    public Auto(float x, float y, float anguloGrados) {
        posicion.set(x, y);
        angulo = anguloGrados;
        guardarPrevio();
    }

    /** Avanza la simulación un paso de dt segundos. */
    public void actualizar(EntradaAuto entrada, Circuito circuito, float dt) {
        guardarPrevio();

        float radianes = angulo * MathUtils.degreesToRadians;
        float adelanteX = MathUtils.cos(radianes);
        float adelanteY = MathUtils.sin(radianes);
        float lateralX = -adelanteY;
        float lateralY = adelanteX;

        float vAdelante = velocidad.x * adelanteX + velocidad.y * adelanteY;
        float vLateral = velocidad.x * lateralX + velocidad.y * lateralY;

        boolean enPasto = circuito.superficieEn(posicion.x, posicion.y) == Superficie.PASTO;
        float velocidadMaxima = enPasto ? ParametrosAuto.VELOCIDAD_MAX * ParametrosAuto.FACTOR_VELOCIDAD_PASTO
            : ParametrosAuto.VELOCIDAD_MAX;

        vAdelante = aplicarPedales(entrada, vAdelante, velocidadMaxima, dt);
        vAdelante = aplicarRozamiento(entrada, vAdelante, enPasto, dt);
        vLateral *= Math.max(0f, 1f - ParametrosAuto.AGARRE * dt);

        // La velocidad se reconstruye con el ángulo viejo; al girar, parte de ella pasa a ser lateral.
        velocidad.set(adelanteX * vAdelante + lateralX * vLateral, adelanteY * vAdelante + lateralY * vLateral);

        angulo += velocidadDeGiro(entrada, vAdelante) * dt;

        mover(circuito, dt);
    }

    private float aplicarPedales(EntradaAuto entrada, float vAdelante, float velocidadMaxima, float dt) {
        if (entrada.acelerar && vAdelante < velocidadMaxima) {
            vAdelante = Math.min(velocidadMaxima, vAdelante + ParametrosAuto.ACELERACION * dt);
        }
        if (entrada.frenar) {
            if (vAdelante > ParametrosAuto.UMBRAL_PARADO) {
                vAdelante = Math.max(0f, vAdelante - ParametrosAuto.FRENADO * dt);
            } else {
                vAdelante = Math.max(-ParametrosAuto.VELOCIDAD_REVERSA,
                    vAdelante - ParametrosAuto.ACELERACION_REVERSA * dt);
            }
        }
        // Si se pasa del máximo (por ejemplo al entrar al pasto a toda velocidad), se frena de a poco.
        if (vAdelante > velocidadMaxima) {
            vAdelante = Math.max(velocidadMaxima, vAdelante - ParametrosAuto.DESACELERACION_EXCESO * dt);
        }
        return vAdelante;
    }

    /**
     * Solo hay rozamiento cuando no se toca ningún pedal: el auto se va frenando solo, y más rápido en
     * el pasto. Con un pedal apretado no se aplica, para que el pasto no impida arrancar ni ir en reversa;
     * ahí lo que limita al auto en el pasto es la velocidad máxima.
     */
    private float aplicarRozamiento(EntradaAuto entrada, float vAdelante, boolean enPasto, float dt) {
        if (entrada.acelerar || entrada.frenar) {
            return vAdelante;
        }
        float rozamiento = ParametrosAuto.ROZAMIENTO + (enPasto ? ParametrosAuto.ROZAMIENTO_PASTO : 0f);
        float perdida = rozamiento * dt;
        if (Math.abs(vAdelante) <= perdida) {
            return 0f;
        }
        return vAdelante - Math.signum(vAdelante) * perdida;
    }

    /** Grados por segundo. Parado no gira; en reversa gira al revés. */
    private float velocidadDeGiro(EntradaAuto entrada, float vAdelante) {
        float factor = MathUtils.clamp(Math.abs(vAdelante) / ParametrosAuto.VELOCIDAD_GIRO_COMPLETO, 0f, 1f);
        float sentido = vAdelante >= 0f ? 1f : -1f;
        return entrada.giro * ParametrosAuto.GIRO_MAX * factor * sentido;
    }

    /**
     * Mueve el auto un eje por vez. Si el círculo del auto tocaría un muro en ese eje, no se mueve y la
     * velocidad de ese eje rebota (con pérdida). Así no atraviesa el muro ni se traba en él.
     */
    private void mover(Circuito circuito, float dt) {
        float nuevaX = posicion.x + velocidad.x * dt;
        if (circuito.chocaConMuro(nuevaX, posicion.y, ParametrosAuto.RADIO_COLISION)) {
            velocidad.x = -velocidad.x * ParametrosAuto.REBOTE;
            velocidad.y *= 1f - ParametrosAuto.PERDIDA_POR_ROCE;
        } else {
            posicion.x = nuevaX;
        }

        float nuevaY = posicion.y + velocidad.y * dt;
        if (circuito.chocaConMuro(posicion.x, nuevaY, ParametrosAuto.RADIO_COLISION)) {
            velocidad.y = -velocidad.y * ParametrosAuto.REBOTE;
            velocidad.x *= 1f - ParametrosAuto.PERDIDA_POR_ROCE;
        } else {
            posicion.y = nuevaY;
        }
    }

    private void guardarPrevio() {
        xPrevia = posicion.x;
        yPrevia = posicion.y;
        anguloPrevio = angulo;
    }

    public float x() {
        return posicion.x;
    }

    public float y() {
        return posicion.y;
    }

    public float angulo() {
        return angulo;
    }

    public Vector2 velocidad() {
        return velocidad;
    }

    /** Módulo de la velocidad (px/s). */
    public float rapidez() {
        return velocidad.len();
    }

    /** Posición entre el tick anterior (alfa = 0) y el actual (alfa = 1), para dibujar sin saltos. */
    public float xInterpolada(float alfa) {
        return MathUtils.lerp(xPrevia, posicion.x, alfa);
    }

    public float yInterpolada(float alfa) {
        return MathUtils.lerp(yPrevia, posicion.y, alfa);
    }

    public float anguloInterpolado(float alfa) {
        return MathUtils.lerp(anguloPrevio, angulo, alfa);
    }
}
