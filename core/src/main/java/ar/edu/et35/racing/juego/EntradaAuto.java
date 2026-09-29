package ar.edu.et35.racing.juego;

/**
 * Todo lo que un jugador hace con su auto en un instante. Es lo único que viaja por red del cliente
 * al servidor.
 */
public class EntradaAuto {
    public boolean acelerar;
    /** Con el auto en movimiento frena; con el auto parado es reversa. */
    public boolean frenar;
    /** -1 = derecha, 0 = recto, 1 = izquierda (sentido antihorario, como los ángulos). */
    public int giro;
}
