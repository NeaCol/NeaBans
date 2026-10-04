package me.nea.neabans.database;

import java.util.List;
import java.util.UUID;
import me.nea.neabans.model.Punishment;
import me.nea.neabans.model.PunishmentType;

/**
 * Contrato de la capa de persistencia de NeaBans.
 *
 * <p>Toda la interacción con la base de datos debe realizarse a través de
 * este interface. Esto permite intercambiar transparentemente la implementación
 * subyacente (MySQL, SQLite, etc.) sin modificar el resto del plugin.</p>
 *
 * <h2>Implementaciones disponibles</h2>
 * <ul>
 *   <li>{@link MySQLDatabase}  — Para servidores de producción con MySQL/MariaDB.</li>
 *   <li>{@link SQLiteDatabase} — Para desarrollo local o servidores pequeños.</li>
 * </ul>
 *
 * <h2>Ciclo de vida esperado</h2>
 * <pre>
 *   database.connect();      // al habilitar el plugin
 *   database.createTables(); // una vez conectado
 *   // ... uso normal ...
 *   database.disconnect();   // al deshabilitar el plugin
 * </pre>
 */
public interface Database {
    // =========================================================================
    // Ciclo de vida de la conexión
    // =========================================================================

    /**
     * Establece la conexión con la base de datos e inicializa el pool
     * de conexiones (HikariCP).
     *
     * @throws Exception Si no se puede establecer la conexión
     *                   (credenciales incorrectas, host inaccesible, driver ausente, etc.).
     */
    void connect() throws Exception;

    /**
     * Cierra el pool de conexiones de forma ordenada y libera todos los recursos.
     * <p>Debe invocarse en {@code JavaPlugin#onDisable()}.</p>
     */
    void disconnect();

    /**
     * Crea las tablas necesarias ({@code nb_punishments}, {@code nb_players})
     * si no existen todavía.
     *
     * <p>Es seguro llamarlo en cada inicio del plugin; el DDL usa
     * {@code CREATE TABLE IF NOT EXISTS}.</p>
     *
     * @throws Exception Si ocurre un error de SQL al crear las tablas.
     */
    void createTables() throws Exception;

    /**
     * Indica si el pool de conexiones está activo y disponible.
     *
     * @return {@code true} si la conexión está establecida.
     */
    boolean isConnected();

    // =========================================================================
    // Escritura de sanciones
    // =========================================================================

    /**
     * Persiste una nueva sanción en la base de datos.
     *
     * <p>Al terminar, el campo {@code id} del objeto {@code punishment}
     * queda actualizado con el ID generado por la DB.</p>
     *
     * @param punishment La sanción a insertar. Su {@code id} debe ser {@code -1}.
     * @return El ID autoincremental asignado por la base de datos.
     */
    int addPunishment(Punishment punishment);

    /**
     * Marca una sanción específica como inactiva (removida manualmente).
     *
     * <p>Registra también {@code removed_by} y {@code removed_at}
     * con el nombre proporcionado y el instante actual.</p>
     *
     * @param id        ID de la sanción a desactivar.
     * @param removedBy Nombre del staff o {@code "CONSOLE"} que ejecuta la remoción.
     */
    void deactivatePunishment(int id, String removedBy);

    /**
     * Desactiva todas las sanciones activas del tipo indicado para un jugador.
     *
     * <p>Útil para operaciones como {@code /unban} o {@code /unmute}, donde
     * se quiere revocar el castigo vigente sin conocer su ID exacto.</p>
     *
     * @param uuid      UUID del jugador objetivo.
     * @param type      Tipo de sanción a desactivar (e.g. {@link PunishmentType#BAN}).
     * @param removedBy Nombre del staff o {@code "CONSOLE"}.
     */
    void deactivateByType(UUID uuid, PunishmentType type, String removedBy);

    // =========================================================================
    // Consultas de sanciones activas
    // =========================================================================

    /**
     * Devuelve el ban activo (permanente o temporal no expirado) de un jugador.
     *
     * <p>Evalúa tanto {@link PunishmentType#BAN} como {@link PunishmentType#TEMP_BAN}.
     * Si hay varios activos (no debería ocurrir en condiciones normales),
     * devuelve el más reciente.</p>
     *
     * @param uuid UUID del jugador.
     * @return El {@link Punishment} activo, o {@code null} si no está baneado.
     */
    Punishment getActiveBan(UUID uuid);

    /**
     * Devuelve el mute activo (permanente o temporal no expirado) de un jugador.
     *
     * <p>Evalúa tanto {@link PunishmentType#MUTE} como {@link PunishmentType#TEMP_MUTE}.</p>
     *
     * @param uuid UUID del jugador.
     * @return El {@link Punishment} activo, o {@code null} si no está muteado.
     */
    Punishment getActiveMute(UUID uuid);

    /**
     * Devuelve todas las sanciones actualmente activas de un jugador
     * (de cualquier tipo).
     *
     * @param uuid UUID del jugador.
     * @return Lista (posiblemente vacía) de sanciones activas.
     */
    List<Punishment> getActivePunishments(UUID uuid);

    // =========================================================================
    // Historial y paginación
    // =========================================================================

    /**
     * Devuelve una página del historial completo de sanciones de un jugador.
     *
     * <p>Incluye sanciones activas, expiradas y removidas manualmente,
     * ordenadas de más reciente a más antiguo.</p>
     *
     * @param uuid    UUID del jugador.
     * @param page    Página a recuperar (basada en 1).
     * @param perPage Número de entradas por página.
     * @return Lista de sanciones en el rango solicitado.
     */
    List<Punishment> getHistory(UUID uuid, int page, int perPage);

    /**
     * Cuenta el total de sanciones en el historial de un jugador.
     * Útil para calcular el número total de páginas.
     *
     * @param uuid UUID del jugador.
     * @return Cantidad total de sanciones (activas + inactivas).
     */
    int getHistoryCount(UUID uuid);

    /**
     * Cuenta las sanciones del jugador dentro de una categoría específica.
     *
     * <p>Usado por el sistema de sanciones escalonadas para determinar en qué
     * nivel se encuentra el jugador dentro de una categoría de infracción.</p>
     *
     * @param uuid     UUID del jugador.
     * @param category Nombre de la categoría (e.g. {@code "hacking"}).
     * @return Número de sanciones registradas en esa categoría.
     */
    int getCategoryCount(UUID uuid, String category);

    // =========================================================================
    // Advertencias (warns)
    // =========================================================================

    /**
     * Devuelve todas las advertencias activas del jugador.
     *
     * @param uuid UUID del jugador.
     * @return Lista de warns activos, o lista vacía si no tiene ninguno.
     */
    List<Punishment> getActiveWarns(UUID uuid);

    /**
     * Cuenta las advertencias activas del jugador.
     *
     * @param uuid UUID del jugador.
     * @return Número de warns activos.
     */
    int getWarnCount(UUID uuid);

    // =========================================================================
    // Datos de jugadores (caché UUID ↔ nombre)
    // =========================================================================

    /**
     * Inserta o actualiza el registro de nombre e UUID de un jugador
     * en la tabla {@code nb_players}.
     *
     * <p>Debe llamarse cuando el jugador se conecta al servidor para
     * mantener actualizado el caché de nombres.</p>
     *
     * @param uuid UUID del jugador.
     * @param name Nombre actual del jugador.
     */
    void savePlayerData(UUID uuid, String name);

    /**
     * Busca el UUID de un jugador a partir de su nombre de forma
     * <strong>case-insensitive</strong>.
     *
     * @param name Nombre del jugador (no sensible a mayúsculas).
     * @return El {@link UUID} del jugador, o {@code null} si no existe en la caché.
     */
    UUID getUUID(String name);

    /**
     * Devuelve el último nombre registrado para un UUID.
     *
     * @param uuid UUID del jugador.
     * @return Nombre del jugador, o {@code null} si el UUID no está en la caché.
     */
    String getLastName(UUID uuid);

    // =========================================================================
    // Mantenimiento
    // =========================================================================

    /**
     * Marca como inactivas todas las sanciones temporales cuyo
     * {@code end_time} haya pasado.
     *
     * <p>Este método puede programarse como tarea periódica para mantener
     * coherente el estado en la base de datos, aunque la lógica de la
     * aplicación ya evalúa expiración en tiempo real via {@link Punishment#isExpired()}.</p>
     */
    void cleanExpired();

    /**
     * Devuelve el total de sanciones registradas en la base de datos
     * (activas e inactivas).
     *
     * @return Número total de filas en {@code nb_punishments}.
     */
    int getTotalPunishments();

    /**
     * Devuelve el número de banes actualmente activos en la base de datos.
     *
     * @return Número de banes activos (permanentes + temporales no expirados).
     */
    int getTotalActiveBans();

    // =========================================================================
    // IP-Ban
    // =========================================================================

    /**
     * Devuelve el IP-ban activo para una dirección IP.
     *
     * @param ip Dirección IP (e.g. "192.168.1.1").
     * @return El {@link Punishment} activo, o {@code null} si no hay IP-ban.
     */
    Punishment getActiveIpBan(String ip);

    /**
     * Desactiva el IP-ban activo de una IP.
     *
     * @param ip        Dirección IP a desbanear.
     * @param removedBy Nombre del staff o "CONSOLE".
     */
    void deactivateIpBan(String ip, String removedBy);

    /**
     * Guarda o actualiza la última IP conocida de un jugador.
     * Llamar al hacer login.
     *
     * @param uuid UUID del jugador.
     * @param name Nombre del jugador.
     * @param ip   Dirección IP.
     */
    void savePlayerIp(UUID uuid, String name, String ip);

    /**
     * Devuelve la última IP conocida de un jugador.
     *
     * @param uuid UUID del jugador.
     * @return IP en formato String, o {@code null} si no hay registro.
     */
    String getLastIp(UUID uuid);

    // =========================================================================
    // Historial
    // =========================================================================

    /**
     * Elimina TODOS los registros de sanciones de un jugador.
     * Acción irreversible — solo para admins.
     *
     * @param uuid UUID del jugador.
     */
    void clearHistory(UUID uuid);
}
