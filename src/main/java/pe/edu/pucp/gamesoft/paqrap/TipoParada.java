package pe.edu.pucp.gamesoft.paqrap;

/** Estructura de datos compartida por ambos algoritmos (ISA apartado 4.1.1).
 *  RECARGA y TRASVASE solo aparecen en la simulación con estado (Etapas 10-12);
 *  en Main y en el modo estático todas las rutas salen cargadas del central. */
enum TipoParada { ENTREGA, RECARGA, TRASVASE }
