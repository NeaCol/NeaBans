package me.nea.neabans.database;

import me.nea.neabans.model.Punishment;
import me.nea.neabans.web.model.WebUser;

import java.util.List;
import java.util.UUID;

/**
 * Extensión de {@link Database} con operaciones adicionales para el panel web.
 *
 * <p>Añade:</p>
 * <ul>
 *   <li>CRUD completo para la tabla {@code nb_web_users} (cuentas del panel web).</li>
 *   <li>Métodos de estadísticas extendidos (mutes, kicks, warns, recientes).</li>
 *   <li>Consultas paginadas y filtradas sobre {@code nb_punishments}.</li>
 *   <li>{@link #clearHistory(UUID)} para borrar el historial completo de un jugador.</li>
 * </ul>
 *
 * <h2>DDL de nb_web_users</h2>
 * <pre>
 * CREATE TABLE IF NOT EXISTS nb_web_users (
 *     id                   INTEGER PRIMARY KEY AUTOINCREMENT,
 *     username             VARCHAR(32)  NOT NULL UNIQUE,
 *     password_hash        VARCHAR(255) NOT NULL,
 *     ban_players          INTEGER      DEFAULT 0,
 *     unban_players        INTEGER      DEFAULT 0,
 *     delete_ban_history   INTEGER      DEFAULT 0,
 *     mute_players         INTEGER      DEFAULT 0,
 *     unmute_players       INTEGER      DEFAULT 0,
 *     delete_mute_history  INTEGER      DEFAULT 0,
 *     edit_accounts        INTEGER      DEFAULT 0,
 *     is_admin             INTEGER      DEFAULT 0,
 *     created_at           BIGINT       NOT NULL
 * )
 * </pre>
 */
public interface WebDatabase extends Database {

    // =========================================================================
    // Ciclo de vida de las tablas web
    // =========================================================================

    /**
     * Crea la tabla {@code nb_web_users} si no existe.
     * Debe invocarse una vez, al inicializar el panel web.
     *
     * @throws Exception Si ocurre un error SQL al crear la tabla.
     */
    void createWebTables() throws Exception;

    // =========================================================================
    // CRUD de usuarios web
    // =========================================================================

    /**
     * Inserta un nuevo usuario web en la base de datos.
     *
     * <p>El campo {@code id} del usuario pasado se ignora; la DB asigna
     * un nuevo ID autoincremental. El campo {@code createdAt} se fija al
     * instante actual si no está establecido.</p>
     *
     * @param user WebUser con username, passwordHash y permisos configurados.
     *             El campo {@code id} se actualiza con el ID generado por la DB.
     * @return El {@link WebUser} con el {@code id} asignado.
     */
    WebUser createWebUser(WebUser user);

    /**
     * Busca un usuario web por su nombre de usuario (case-sensitive).
     *
     * @param username Nombre de usuario a buscar.
     * @return El {@link WebUser} encontrado, o {@code null} si no existe.
     */
    WebUser getWebUser(String username);

    /**
     * Busca un usuario web por su ID numérico.
     *
     * @param id ID del usuario en la DB.
     * @return El {@link WebUser} encontrado, o {@code null} si no existe.
     */
    WebUser getWebUserById(int id);

    /**
     * Devuelve todos los usuarios web registrados.
     *
     * @return Lista (posiblemente vacía) de todos los {@link WebUser}.
     */
    List<WebUser> getAllWebUsers();

    /**
     * Actualiza los permisos de un usuario web existente.
     *
     * <p>Solo actualiza los campos de permisos y el rol {@code isAdmin}.
     * No modifica {@code username}, {@code passwordHash} ni {@code createdAt}.</p>
     *
     * @param user WebUser con el {@code id} correcto y los nuevos permisos.
     */
    void updateWebUser(WebUser user);

    /**
     * Actualiza la contraseña de un usuario web.
     *
     * @param id           ID del usuario a actualizar.
     * @param passwordHash Nuevo hash BCrypt de la contraseña.
     */
    void updateWebUserPassword(int id, String passwordHash);

    /**
     * Elimina un usuario web de la base de datos.
     *
     * @param id ID del usuario a eliminar.
     */
    void deleteWebUser(int id);

    /**
     * Comprueba si existe un usuario web con el nombre dado.
     *
     * @param username Nombre de usuario a verificar.
     * @return {@code true} si existe al menos un usuario con ese nombre.
     */
    boolean webUserExists(String username);

    /**
     * Cuenta el total de usuarios web registrados.
     * Útil para el modo bootstrap (primer usuario admin).
     *
     * @return Número de filas en {@code nb_web_users}.
     */
    int countWebUsers();

    // =========================================================================
    // Mantenimiento de historial
    // =========================================================================

    /**
     * Elimina <strong>todo el historial</strong> de sanciones de un jugador.
     *
     * <p>Operación destructiva e irreversible. Requiere permiso
     * {@code is_admin} en el panel web.</p>
     *
     * @param uuid UUID del jugador cuyo historial se eliminará.
     */
    void clearHistory(UUID uuid);

    // =========================================================================
    // Estadísticas extendidas para el dashboard
    // =========================================================================

    /**
     * Cuenta el total de mutes registrados (activos e inactivos).
     *
     * @return Número total de filas con tipo {@code MUTE} o {@code TEMP_MUTE}.
     */
    int getTotalMutes();

    /**
     * Cuenta los mutes actualmente activos y no expirados.
     *
     * @return Número de mutes vigentes.
     */
    int getTotalActiveMutes();

    /**
     * Cuenta el total de kicks registrados.
     *
     * @return Número total de filas con tipo {@code KICK}.
     */
    int getTotalKicks();

    /**
     * Cuenta el total de advertencias (warns) registradas.
     *
     * @return Número total de filas con tipo {@code WARN}.
     */
    int getTotalWarns();

    /**
     * Devuelve las {@code limit} sanciones más recientes (de cualquier tipo).
     *
     * @param limit Número máximo de resultados.
     * @return Lista de sanciones ordenada de más reciente a más antigua.
     */
    List<Punishment> getRecentPunishments(int limit);

    /**
     * Devuelve las {@code limit} sanciones más recientes con filtros opcionales.
     *
     * @param limit  Número máximo de resultados.
     * @param type   Tipo de sanción a filtrar (p.ej. {@code "BAN"}),
     *               o {@code null}/{@code ""} para todos los tipos.
     * @param search Término de búsqueda sobre nombre de jugador o staff,
     *               o {@code null}/{@code ""} para no filtrar.
     * @return Lista filtrada de sanciones.
     */
    List<Punishment> getRecentPunishments(int limit, String type, String search);

    /**
     * Cuenta el total de sanciones que cumplen los filtros dados.
     * Útil para calcular la paginación en el frontend.
     *
     * @param type   Tipo de sanción a filtrar, o {@code null}/{@code ""}.
     * @param search Término de búsqueda, o {@code null}/{@code ""}.
     * @return Número de sanciones que cumplen los criterios.
     */
    int getTotalPunishmentsFiltered(String type, String search);

    /**
     * Devuelve una página de sanciones con filtros opcionales.
     *
     * @param page    Página a recuperar (basada en 1).
     * @param perPage Número de resultados por página.
     * @param type    Tipo de sanción a filtrar, o {@code null}/{@code ""}.
     * @param search  Término de búsqueda, o {@code null}/{@code ""}.
     * @return Lista de sanciones en el rango solicitado.
     */
    List<Punishment> getPaginatedPunishments(int page, int perPage, String type, String search);

    /**
     * Devuelve la lista de servidores únicos presentes en la tabla de sanciones.
     * Útil para el selector de servidores en el dashboard.
     *
     * @return Lista de nombres de servidor distintos, ordenados alfabéticamente.
     */
    List<String> getDistinctServers();
}
