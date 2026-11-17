package ar.edu.et35.racing.pantallas;

import ar.edu.et35.racing.Main;
import ar.edu.et35.racing.juego.CargadorCircuito;
import ar.edu.et35.racing.juego.Circuito;
import ar.edu.et35.racing.red.Protocolo;
import ar.edu.et35.racing.red.Protocolo.Mensaje;
import ar.edu.et35.racing.red.SesionRed;
import ar.edu.et35.racing.red.cliente.ClientePartida;
import ar.edu.et35.racing.red.servidor.ServidorPartida;
import ar.edu.et35.racing.util.Config;
import ar.edu.et35.racing.util.Paleta;
import ar.edu.et35.racing.util.Recursos;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class UnirsePartida extends PantallaBase {
    private static final float ANCHO_COLUMNA = 560f;
    private static final float ALTO_CAMPO = 48f;
    private static final float ALTO_BOTON = 52f;
    private static final float ANCHO_FLECHA = 56f;
    private static final int CAPACIDAD_INICIAL = 8;

    private final boolean crear;
    private final TextField campoNombre;
    private final TextField campoIp;
    private final TextButton botonAccion;
    private final Label estado = textoLargo("", "suave");
    private final Label valorCapacidad = new Label("", skin, "numeros");
    private int capacidad = CAPACIDAD_INICIAL;

    private SesionRed pendiente;
    private float esperando;

    public UnirsePartida(Main juego, boolean crear) {
        super(juego);
        this.crear = crear;

        campoNombre = new TextField("", skin);
        campoNombre.setMessageText("Hasta " + Config.LARGO_MAXIMO_NOMBRE + " letras");
        campoNombre.setMaxLength(Config.LARGO_MAXIMO_NOMBRE);
        campoNombre.setTextFieldListener((campo, tecla) -> {
            if (tecla == '\r' || tecla == '\n') {
                intentar();
            }
        });
        campoIp = new TextField("", skin);
        campoIp.setMessageText("Por ejemplo, 192.168.0.12");
        campoIp.setTextFieldListener((campo, tecla) -> {
            if (tecla == '\r' || tecla == '\n') {
                intentar();
            }
        });
        botonAccion = botonPrimario(crear ? "CREAR" : "CONECTAR", this::intentar);

        Table formulario = tarjeta(Paleta.SECUNDARIO);
        formulario.add(new Label("TU NOMBRE", skin, "etiqueta")).left().padBottom(6).row();
        formulario.add(campoNombre).growX().height(ALTO_CAMPO).padBottom(14).row();
        if (!crear) {
            formulario.add(new Label("IP DEL ANFITRIÓN", skin, "etiqueta")).left().padBottom(6).row();
            formulario.add(campoIp).growX().height(ALTO_CAMPO).padBottom(14).row();
        }
        if (crear) {
            formulario.add(new Label("AUTOS EN LA SALA", skin, "etiqueta")).left().padBottom(6).row();
            formulario.add(selectorCapacidad()).growX().height(ALTO_CAMPO).padBottom(4).row();
            formulario.add(new Label("Personas y bots juntos. Hasta " + Config.MAX_PERSONAS + " personas; el resto, bots.",
                skin, "apagado")).left().padBottom(14).row();
        }
        formulario.add(botonAccion).growX().height(ALTO_BOTON);

        contenido.add(titular("MULTIJUGADOR  ·  RED LOCAL", crear ? "CREAR PARTIDA" : "UNIRSE A UNA PARTIDA")).left().padBottom(16).row();
        contenido.add(formulario).width(ANCHO_COLUMNA).row();
        contenido.add(estado).width(ANCHO_COLUMNA).padTop(12).row();
        contenido.add(new Label("ESC PARA VOLVER", skin, "etiqueta")).left().padTop(8);

        escena.setKeyboardFocus(campoNombre);
    }

    private Table selectorCapacidad() {
        Table fila = new Table();
        fila.add(boton("<", () -> cambiarCapacidad(capacidad - 1))).size(ANCHO_FLECHA, ALTO_CAMPO);
        fila.add(valorCapacidad).expandX();
        fila.add(boton(">", () -> cambiarCapacidad(capacidad + 1))).size(ANCHO_FLECHA, ALTO_CAMPO);
        cambiarCapacidad(capacidad);
        return fila;
    }

    private void cambiarCapacidad(int nueva) {
        capacidad = Math.max(Config.MIN_JUGADORES, Math.min(Config.MAX_JUGADORES, nueva));
        valorCapacidad.setText(String.valueOf(capacidad));
    }

    private void intentar() {
        if (pendiente != null) {
            return;
        }
        String nombre = campoNombre.getText().strip();
        if (!Protocolo.nombreValido(nombre)) {
            mostrarError("El nombre debe tener de 1 a " + Config.LARGO_MAXIMO_NOMBRE + " letras, números o espacios");
            return;
        }
        if (crear) {
            crearPartida(nombre);
        } else {
            unirse(nombre);
        }
    }

    private void crearPartida(String nombre) {
        ServidorPartida servidor;
        try {
            servidor = ServidorPartida.crear(Config.PUERTO_TCP, Config.PUERTO_UDP, capacidad, this::cargarCircuitoEnRender);
        } catch (IOException e) {
            mostrarError("No se pudo crear la partida: los puertos " + Config.PUERTO_TCP + " y " + Config.PUERTO_UDP
                + " están ocupados. ¿Ya hay una partida creada en esta PC?");
            return;
        }
        ClientePartida cliente = new ClientePartida(nombre);
        cliente.conectar("127.0.0.1", servidor.puerto());
        esperarRespuesta(new SesionRed(servidor, cliente), "Creando la partida...");
    }

    private Circuito cargarCircuitoEnRender(String archivo) throws Exception {
        CompletableFuture<Circuito> futuro = new CompletableFuture<>();
        Gdx.app.postRunnable(() -> {
            try {
                futuro.complete(CargadorCircuito.cargar(Gdx.files.internal(Recursos.rutaCircuito(archivo)), Config.MAX_JUGADORES));
            } catch (RuntimeException e) {
                futuro.completeExceptionally(e);
            }
        });
        return futuro.get(Config.TIMEOUT_CARGA_CIRCUITO_S, TimeUnit.SECONDS);
    }

    private void unirse(String nombre) {
        String ip = campoIp.getText().strip();
        if (ip.isEmpty()) {
            mostrarError("Escribí la IP del anfitrión");
            return;
        }
        ClientePartida cliente = new ClientePartida(nombre);
        cliente.conectar(ip, Config.PUERTO_TCP);
        esperarRespuesta(new SesionRed(null, cliente), "Conectando a " + ip + "...");
    }

    private void esperarRespuesta(SesionRed sesion, String texto) {
        pendiente = sesion;
        esperando = 0f;
        botonAccion.setDisabled(true);
        estado.setStyle(skin.get("suave", Label.LabelStyle.class));
        estado.setText(texto);
    }

    private void mostrarError(String texto) {
        estado.setStyle(skin.get("alerta", Label.LabelStyle.class));
        estado.setText(texto);
    }

    private void cancelarIntento(String motivo) {
        pendiente.cerrar();
        pendiente = null;
        botonAccion.setDisabled(false);
        mostrarError(motivo);
    }

    @Override
    protected void actualizar(float delta) {
        if (pendiente == null) {
            return;
        }
        esperando += delta;
        if (esperando > Config.TIMEOUT_CONEXION_MS / 1000f + 2f) {
            cancelarIntento("No se pudo conectar (sin respuesta). Revisá la IP y que el anfitrión haya creado la partida");
            return;
        }
        ClientePartida cliente = pendiente.cliente();
        cliente.actualizar();
        Mensaje mensaje;
        while ((mensaje = cliente.sondear()) != null) {
            switch (mensaje.nombre()) {
                case Protocolo.BIENVENIDA:
                    SesionRed lista = pendiente;
                    pendiente = null;
                    juego.setSesion(lista);
                    juego.irA(new Lobby(juego));
                    return;
                case Protocolo.ERROR:
                    cancelarIntento(mensaje.campo(1));
                    return;
                case Protocolo.CONEXION_FALLIDA:
                    cancelarIntento(mensaje.campo(0) + ". Revisá la IP y que el anfitrión haya creado la partida");
                    return;
                case Protocolo.CONEXION_PERDIDA:
                    cancelarIntento(mensaje.campo(0));
                    return;
                default:
                    break;
            }
        }
    }

    @Override
    protected void volver() {
        if (pendiente != null) {
            pendiente.cerrar();
            pendiente = null;
        }
        juego.irA(new MenuPrincipal(juego));
    }

    @Override
    public void dispose() {
        if (pendiente != null) {
            pendiente.cerrar();
            pendiente = null;
        }
        super.dispose();
    }
}
