package ar.edu.et35.racing.juego;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

public class Auto {
    private final Vector2 posicion = new Vector2();
    private final Vector2 velocidad = new Vector2();
    private float angulo;

    private float xPrevia;
    private float yPrevia;
    private float anguloPrevio;

    public Auto(float x, float y, float anguloGrados) {
        posicion.set(x, y);
        angulo = anguloGrados;
        guardarPrevio();
    }

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

        float giro = velocidadDeGiro(entrada, vAdelante);
        vAdelante -= vAdelante * ParametrosAuto.PERDIDA_POR_GIRO * Math.abs(giro) * MathUtils.degreesToRadians * dt;

        velocidad.set(adelanteX * vAdelante + lateralX * vLateral, adelanteY * vAdelante + lateralY * vLateral);

        angulo += giro * dt;

        mover(circuito, dt);
    }

    private float aplicarPedales(EntradaAuto entrada, float vAdelante, float velocidadMaxima, float dt) {
        if (entrada.acelerar && vAdelante < velocidadMaxima) {
            float fraccion = MathUtils.clamp(vAdelante / ParametrosAuto.VELOCIDAD_MAX, 0f, 1f);
            float aceleracion = ParametrosAuto.ACELERACION * (1f - ParametrosAuto.PERDIDA_ACELERACION * fraccion);
            vAdelante = Math.min(velocidadMaxima, vAdelante + aceleracion * dt);
        }
        if (entrada.frenar) {
            if (vAdelante > ParametrosAuto.UMBRAL_PARADO) {
                vAdelante = Math.max(0f, vAdelante - ParametrosAuto.FRENADO * dt);
            } else {
                vAdelante = Math.max(-ParametrosAuto.VELOCIDAD_REVERSA,
                    vAdelante - ParametrosAuto.ACELERACION_REVERSA * dt);
            }
        }
        if (vAdelante > velocidadMaxima) {
            vAdelante = Math.max(velocidadMaxima, vAdelante - ParametrosAuto.DESACELERACION_EXCESO * dt);
        }
        return vAdelante;
    }

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

    private float velocidadDeGiro(EntradaAuto entrada, float vAdelante) {
        float velocidad = Math.abs(vAdelante);
        float factor = MathUtils.clamp(velocidad / ParametrosAuto.VELOCIDAD_GIRO_COMPLETO, 0f, 1f);
        float giro = ParametrosAuto.GIRO_MAX * factor;
        if (velocidad > 0f) {
            giro = Math.min(giro, ParametrosAuto.ACELERACION_LATERAL / velocidad * MathUtils.radiansToDegrees);
        }
        float sentido = vAdelante >= 0f ? 1f : -1f;
        return entrada.giro * giro * sentido;
    }

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

    public void desplazar(float dx, float dy, Circuito circuito) {
        if (!circuito.chocaConMuro(posicion.x + dx, posicion.y, ParametrosAuto.RADIO_COLISION)) {
            posicion.x += dx;
        }
        if (!circuito.chocaConMuro(posicion.x, posicion.y + dy, ParametrosAuto.RADIO_COLISION)) {
            posicion.y += dy;
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

    public float velocidadAdelante() {
        float radianes = angulo * MathUtils.degreesToRadians;
        return velocidad.x * MathUtils.cos(radianes) + velocidad.y * MathUtils.sin(radianes);
    }

    public float rapidez() {
        return velocidad.len();
    }

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
