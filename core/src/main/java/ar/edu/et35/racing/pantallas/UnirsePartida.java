package ar.edu.et35.racing.pantallas;

import ar.edu.et35.racing.Main;
import ar.edu.et35.racing.red.Protocolo;
import ar.edu.et35.racing.red.Protocolo.Mensaje;
import ar.edu.et35.racing.red.SesionRed;
import ar.edu.et35.racing.red.cliente.ClientePartida;
import ar.edu.et35.racing.red.servidor.ServidorPartida;
import ar.edu.et35.racing.util.Config;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import java.io.IOException;

/**
 * Formulario para crear una partida (solo el nombre) o unirse a una (nombre e IP del anfitrión). La conexión se
 * hace en segundo plano; esta pantalla espera la respuesta del servidor y, si sale bien, pasa al lobby.
 */
public class UnirsePartida extends PantallaBase {
    private static final float ANCHO_CAMPO = 240f;
    private static final float ALTO_CAMPO = 26f;

    private final boolean crear;
    private final TextField campoNombre;
    private final TextField campoIp;
    private final TextButton botonAccion;
    private final Label estado = new Label("", skin, "gris");

    /** La partida que se está creando o a la que nos estamos conectando; null si no hay ningún intento en curso. */
    private SesionRed pendiente;

    public UnirsePartida(Main juego, boolean crear) {
        super(juego);
        this.crear = crear;

        campoNombre = new TextField("", skin);
        campoNombre.setMessageText("Tu nombre (hasta " + Config.LARGO_MAXIMO_NOMBRE + " letras)");
        campoNombre.setMaxLength(Config.LARGO_MAXIMO_NOMBRE);
        campoNombre.setTextFieldListener((campo, tecla) -> {
            if (tecla == '\r' || tecla == '\n') {
                intentar();
            }
        });
        campoIp = new TextField("", skin);
        campoIp.setMessageText("IP del anfitrión (ej. 192.168.0.12)");
        campoIp.setTextFieldListener((campo, tecla) -> {
            if (tecla == '\r' || tecla == '\n') {
                intentar();
            }
        });
        botonAccion = boton(crear ? "CREAR" : "CONECTAR", this::intentar);

        contenido.add(new Label(crear ? "CREAR PARTIDA" : "UNIRSE", skin, "titulo")).padBottom(12).row();
        contenido.add(campoNombre).width(ANCHO_CAMPO).height(ALTO_CAMPO).pad(3).row();
        if (!crear) {
            contenido.add(campoIp).width(ANCHO_CAMPO).height(ALTO_CAMPO).pad(3).row();
        }
        contenido.add(botonAccion).width(ANCHO_CAMPO).height(30).padTop(8).row();
        contenido.add(estado).padTop(8).row();
        contenido.add(new Label("ESC para volver", skin, "gris")).padTop(4);

        escena.setKeyboardFocus(campoNombre);
    }

    private void intentar() {
        if (pendiente != null) {
            return; // ya hay un intento en curso
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

    /** El host levanta su servidor y se conecta a él por 127.0.0.1, como un cliente más. */
    private void crearPartida(String nombre) {
        ServidorPartida servidor;
        try {
            servidor = ServidorPartida.crear(Config.PUERTO_TCP);
        } catch (IOException e) {
            mostrarError("No se pudo crear la partida: el puerto " + Config.PUERTO_TCP
                + " está ocupado. ¿Ya hay una partida creada en esta PC?");
            return;
        }
        ClientePartida cliente = new ClientePartida(nombre);
        cliente.conectar("127.0.0.1", servidor.puerto());
        esperarRespuesta(new SesionRed(servidor, cliente), "Creando la partida...");
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
        botonAccion.setDisabled(true);
        estado.setStyle(skin.get("gris", Label.LabelStyle.class));
        estado.setText(texto);
    }

    private void mostrarError(String texto) {
        estado.setStyle(skin.get("rojo", Label.LabelStyle.class));
        estado.setText(texto);
    }

    /** Cancela el intento en curso (cierra el cliente y, si se estaba creando, el servidor) y deja volver a probar. */
    private void cancelarIntento(String motivo) {
        pendiente.cerrar();
        pendiente = null;
        botonAccion.setDisabled(false);
        mostrarError(motivo);
    }

    /** Cada cuadro mira si el servidor ya contestó. Lo que llega por la red se procesa acá, en el hilo de render. */
    @Override
    protected void actualizar(float delta) {
        if (pendiente == null) {
            return;
        }
        ClientePartida cliente = pendiente.cliente();
        cliente.actualizar();
        Mensaje mensaje;
        while ((mensaje = cliente.sondear()) != null) {
            switch (mensaje.nombre()) {
                case Protocolo.BIENVENIDA:
                    // Se deja el resto de los mensajes en la cola: los procesa el lobby.
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
