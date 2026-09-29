package ar.edu.et35.racing.juego;

/** Tipo de terreno de un tile del circuito. */
public enum Superficie {
    PISTA,
    PASTO,
    MURO;

    /** Convierte la propiedad "superficie" de Tiled (pista, pasto o muro) al enum. */
    public static Superficie desdeTexto(String texto) {
        if (texto == null) {
            throw new IllegalArgumentException("El tile no tiene la propiedad 'superficie'");
        }
        switch (texto.trim().toLowerCase()) {
            case "pista":
                return PISTA;
            case "pasto":
                return PASTO;
            case "muro":
                return MURO;
            default:
                throw new IllegalArgumentException("Superficie desconocida: '" + texto + "'");
        }
    }
}
