package ar.edu.et35.racing.juego;

/**
 * Un piloto de la grilla.
 *
 * @param codigo    las tres letras con que se lo muestra en el HUD y los resultados
 * @param pais      código ISO de dos letras en minúscula, que es también el nombre del archivo de su bandera
 * @param escuderia índice de su escudería en {@link Catalogo#ESCUDERIAS}
 */
public record Piloto(String nombre, String codigo, int numero, String pais, int escuderia) {

    /** El apellido, para los botones y listas donde no entra el nombre completo ("Carlos Sainz Jr." da "Sainz"). */
    public String apellido() {
        String[] partes = nombre.split(" ");
        boolean termina = partes.length > 2 && partes[partes.length - 1].equals("Jr.");
        return partes[termina ? partes.length - 2 : partes.length - 1];
    }
}
