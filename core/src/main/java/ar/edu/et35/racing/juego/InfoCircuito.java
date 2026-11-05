package ar.edu.et35.racing.juego;

/**
 * Los datos para mostrar de un circuito. El trazado en sí está en {@code assets/circuitos/<archivo>.json}.
 *
 * @param archivo nombre del .json sin la extensión; es lo que identifica al circuito en la red
 * @param gp      nombre del Gran Premio
 * @param pais    código ISO de dos letras en minúscula, que es también el nombre del archivo de su bandera
 * @param km      largo real de una vuelta
 * @param curvas  cantidad de curvas reales
 */
public record InfoCircuito(String archivo, String gp, String nombre, String ciudad, String pais, float km, int curvas) {
}
