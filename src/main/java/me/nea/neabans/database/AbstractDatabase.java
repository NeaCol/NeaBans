package me.nea.neabans.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.nea.neabans.model.Punishment;
import me.nea.neabans.model.PunishmentType;
import me.nea.neabans.web.model.WebUser;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Implementación base de {@link Database} que contiene toda la lógica SQL
 * compartida entre MySQL y SQLite.
 *
 * <h2>Diseño</h2>
 * <ul>
 *   <li>Usa <strong>HikariCP</strong> para pooling de conexiones eficiente.</li>
 *   <li>Todos los accesos a la DB son a través de {@link PreparedStatement}
 *       para prevenir SQL injection.</li>
 *   <li>Los detalles que difieren entre motores (DDL, driver) son delegados
 *       a subclases mediante métodos abstractos.</li>
 * </ul>
 *
 * <h2>Subclases</h2>
 * <ul>
 *   <li>{@link MySQLDatabase}  — Producción con MySQL/MariaDB.</li>
 *   <li>{@link SQLiteDatabase} — Desarrollo o servidores pequeños.</li>
 * </ul>
 */
public abstract class AbstractDatabase implements WebDatabase {

    // =========================================================================
    // Campos
    // =========================================================================

    /** Logger del plugin para reportar errores y estado de la DB. */
    protected final Logger logger;

    /** Pool de conexiones HikariCP. Inicializado en {@link #connect()}. */
    protected HikariDataSource dataSource;

    /**
     * Referencia al plugin principal.
     * Usada por subclases (e.g. SQLite necesita el data folder).
     */
    protected final JavaPlugin plugin;

    // =========================================================================
    // Queries SQL (constantes compartidas entre MySQL y SQLite)
    // =========================================================================

    // --- Inserts / Updates ---

    private static final String SQL_INSERT_PUNISHMENT =
        "INSERT INTO nb_punishments (uuid, name, type, reason, category, level, " +
        "staff_uuid, staff_name, server, start_time, end_time, active, removed_by, removed_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, NULL, 0)";

    private static final String SQL_DEACTIVATE_BY_ID =
        "UPDATE nb_punishments SET active = 0, removed_by = ?, removed_at = ? " +
        "WHERE id = ?";

    private static final String SQL_DEACTIVATE_BY_TYPE =
        "UPDATE nb_punishments SET active = 0, removed_by = ?, removed_at = ? " +
        "WHERE uuid = ? AND type = ? AND active = 1";

    private static final String SQL_CLEAN_EXPIRED =
        "UPDATE nb_punishments SET active = 0 " +
        "WHERE active = 1 AND end_time != -1 AND end_time <= ?";

    // --- Consultas de sanciones activas ---

    /**
     * Consulta el ban activo más reciente de un jugador.
     * Filtra BAN y TEMP_BAN; excluye sanciones expiradas en el nivel SQL.
     */
    private static final String SQL_GET_ACTIVE_BAN =
        "SELECT * FROM nb_punishments " +
        "WHERE uuid = ? AND type IN ('BAN','TEMP_BAN') AND active = 1 " +
        "AND (end_time = -1 OR end_time > ?) " +
        "ORDER BY start_time DESC LIMIT 1";

    /** Igual que el ban pero para MUTE y TEMP_MUTE. */
    private static final String SQL_GET_ACTIVE_MUTE =
        "SELECT * FROM nb_punishments " +
        "WHERE uuid = ? AND type IN ('MUTE','TEMP_MUTE') AND active = 1 " +
        "AND (end_time = -1 OR end_time > ?) " +
        "ORDER BY start_time DESC LIMIT 1";

    /** Todas las sanciones activas y no expiradas de cualquier tipo. */
    private static final String SQL_GET_ACTIVE_PUNISHMENTS =
        "SELECT * FROM nb_punishments " +
        "WHERE uuid = ? AND active = 1 AND (end_time = -1 OR end_time > ?) " +
        "ORDER BY start_time DESC";

    // --- Historial ---

    private static final String SQL_GET_HISTORY =
        "SELECT * FROM nb_punishments WHERE uuid = ? " +
        "ORDER BY start_time DESC LIMIT ? OFFSET ?";

    private static final String SQL_GET_HISTORY_COUNT =
        "SELECT COUNT(*) FROM nb_punishments WHERE uuid = ?";

    private static final String SQL_GET_CATEGORY_COUNT =
        "SELECT COUNT(*) FROM nb_punishments WHERE uuid = ? AND category = ?";

    // --- Warns ---

    private static final String SQL_GET_ACTIVE_WARNS =
        "SELECT * FROM nb_punishments " +
        "WHERE uuid = ? AND type = 'WARN' AND active = 1 " +
        "ORDER BY start_time DESC";

    private static final String SQL_GET_WARN_COUNT =
        "SELECT COUNT(*) FROM nb_punishments " +
        "WHERE uuid = ? AND type = 'WARN' AND active = 1";

    // --- Jugadores ---

    /**
     * UPSERT por defecto (MySQL/MariaDB).
     * Las subclases pueden sobrescribir {@link #getSavePlayerSQL()} para
     * usar la sintaxis propia de su motor (e.g. {@code INSERT OR REPLACE} en SQLite).
     */
    private static final String SQL_SAVE_PLAYER_MYSQL =
        "INSERT INTO nb_players (uuid, name, last_seen) VALUES (?, ?, ?) " +
        "ON DUPLICATE KEY UPDATE name = VALUES(name), last_seen = VALUES(last_seen)";

    private static final String SQL_GET_UUID_BY_NAME =
        "SELECT uuid FROM nb_players WHERE LOWER(name) = LOWER(?) LIMIT 1";

    private static final String SQL_GET_NAME_BY_UUID =
        "SELECT name FROM nb_players WHERE uuid = ? LIMIT 1";

    // --- Estadísticas ---

    private static final String SQL_TOTAL_PUNISHMENTS =
        "SELECT COUNT(*) FROM nb_punishments";

    private static final String SQL_TOTAL_ACTIVE_BANS =
        "SELECT COUNT(*) FROM nb_punishments " +
        "WHERE type IN ('BAN','TEMP_BAN') AND active = 1 " +
        "AND (end_time = -1 OR end_time > ?)";

    // --- Web Users ---
    private static final String SQL_INSERT_WEB_USER =
        "INSERT INTO nb_web_users (username, password_hash, ban_players, unban_players, " +
        "delete_ban_history, mute_players, unmute_players, delete_mute_history, " +
        "edit_accounts, is_admin, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
    private static final String SQL_GET_WEB_USER_BY_NAME =
        "SELECT * FROM nb_web_users WHERE username = ? LIMIT 1";
    private static final String SQL_GET_WEB_USER_BY_ID =
        "SELECT * FROM nb_web_users WHERE id = ? LIMIT 1";
    private static final String SQL_GET_ALL_WEB_USERS =
        "SELECT * FROM nb_web_users ORDER BY id";
    private static final String SQL_UPDATE_WEB_USER =
        "UPDATE nb_web_users SET ban_players=?, unban_players=?, delete_ban_history=?, " +
        "mute_players=?, unmute_players=?, delete_mute_history=?, " +
        "edit_accounts=?, is_admin=? WHERE id=?";
    private static final String SQL_UPDATE_WEB_USER_PASSWORD =
        "UPDATE nb_web_users SET password_hash=? WHERE id=?";
    private static final String SQL_DELETE_WEB_USER =
        "DELETE FROM nb_web_users WHERE id=?";
    private static final String SQL_WEB_USER_EXISTS =
        "SELECT COUNT(*) FROM nb_web_users WHERE username = ?";
    private static final String SQL_COUNT_WEB_USERS =
        "SELECT COUNT(*) FROM nb_web_users";
    // --- Mantenimiento web ---
    private static final String SQL_CLEAR_HISTORY =
        "DELETE FROM nb_punishments WHERE uuid = ?";
    // --- Estadísticas extendidas ---
    private static final String SQL_TOTAL_MUTES =
        "SELECT COUNT(*) FROM nb_punishments WHERE type IN ('MUTE','TEMP_MUTE')";
    private static final String SQL_TOTAL_ACTIVE_MUTES =
        "SELECT COUNT(*) FROM nb_punishments " +
        "WHERE type IN ('MUTE','TEMP_MUTE') AND active = 1 " +
        "AND (end_time = -1 OR end_time > ?)";
    private static final String SQL_TOTAL_KICKS =
        "SELECT COUNT(*) FROM nb_punishments WHERE type = 'KICK'";
    private static final String SQL_TOTAL_WARNS =
        "SELECT COUNT(*) FROM nb_punishments WHERE type = 'WARN'";
    private static final String SQL_RECENT_PUNISHMENTS =
        "SELECT * FROM nb_punishments ORDER BY start_time DESC LIMIT ?";
    private static final String SQL_DISTINCT_SERVERS =
        "SELECT DISTINCT server FROM nb_punishments ORDER BY server";

    // --- IP-Ban ---
    private static final String SQL_INSERT_PUNISHMENT_IP =
        "INSERT INTO nb_punishments (uuid, name, type, reason, category, level, " +
        "staff_uuid, staff_name, server, start_time, end_time, active, removed_by, removed_at, ip) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, NULL, 0, ?)";

    private static final String SQL_GET_ACTIVE_IP_BAN =
        "SELECT * FROM nb_punishments " +
        "WHERE ip = ? AND type IN ('IP_BAN','TEMP_IP_BAN') AND active = 1 " +
        "AND (end_time = -1 OR end_time > ?) " +
        "ORDER BY start_time DESC LIMIT 1";

    private static final String SQL_DEACTIVATE_IP_BAN =
        "UPDATE nb_punishments SET active = 0, removed_by = ?, removed_at = ? " +
        "WHERE ip = ? AND type IN ('IP_BAN','TEMP_IP_BAN') AND active = 1";

    private static final String SQL_SAVE_PLAYER_IP_MYSQL =
        "INSERT INTO nb_players (uuid, name, last_ip, last_seen) VALUES (?, ?, ?, ?) " +
        "ON DUPLICATE KEY UPDATE name = VALUES(name), last_ip = VALUES(last_ip), last_seen = VALUES(last_seen)";

    private static final String SQL_GET_LAST_IP =
        "SELECT last_ip FROM nb_players WHERE uuid = ? LIMIT 1";

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Constructor base que recibe la instancia del plugin principal.
     *
     * @param plugin Instancia del plugin de Bukkit/Paper.
     */
    protected AbstractDatabase(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    // =========================================================================
    // Métodos abstractos que deben implementar las subclases
    // =========================================================================

    /**
     * Configura el {@link HikariConfig} con los parámetros específicos del
     * motor de base de datos (URL JDBC, driver class, credenciales, etc.).
     *
     * <p>Al retornar, el config debe estar listo para ser pasado al constructor
     * de {@link HikariDataSource}.</p>
     *
     * @param config Objeto de configuración de HikariCP a poblar.
     */
    protected abstract void configureHikari(HikariConfig config);

    /**
     * Devuelve el SQL DDL para crear la tabla {@code nb_punishments}.
     *
     * <p>Difiere entre MySQL (AUTO_INCREMENT, ENGINE=InnoDB) y
     * SQLite (AUTOINCREMENT con INTEGER PRIMARY KEY).</p>
     *
     * @return String con el DDL {@code CREATE TABLE IF NOT EXISTS}.
     */
    protected abstract String getCreatePunishmentsTableSQL();

    /**
     * Devuelve el SQL DDL para crear la tabla {@code nb_players}.
     *
     * @return String con el DDL {@code CREATE TABLE IF NOT EXISTS}.
     */
    protected abstract String getCreatePlayersTableSQL();

    /**
     * Devuelve el SQL apropiado para el UPSERT en {@code nb_players},
     * ya que MySQL y SQLite usan sintaxis diferente.
     *
     * @return SQL de upsert listo para preparar.
     */
    protected String getSavePlayerSQL() {
        // Sobreescribir en SQLiteDatabase para usar INSERT OR REPLACE
        return SQL_SAVE_PLAYER_MYSQL;
    }

    // =========================================================================
    // Implementación de Database — Ciclo de vida
    // =========================================================================

    /** {@inheritDoc} */
    @Override
    public void connect() throws Exception {
        HikariConfig config = new HikariConfig();
        // Las subclases rellenan el config con sus parámetros específicos
        configureHikari(config);
        dataSource = new HikariDataSource(config);
        logger.info("[NeaBans] Pool de conexiones inicializado correctamente.");
    }

    /** {@inheritDoc} */
    @Override
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("[NeaBans] Pool de conexiones cerrado.");
        }
    }

    /** {@inheritDoc} */
    @Override
    public void createTables() throws Exception {
        try (
            Connection conn = getConnection();
            Statement stmt = conn.createStatement()
        ) {
            stmt.execute(getCreatePunishmentsTableSQL());
            stmt.execute(getCreatePlayersTableSQL());

            // ── Migración de esquema (idempotente) ──────────────────────────────
            // Añade la columna 'ip' a nb_punishments si no existe todavía.
            // Necesaria para soportar IP_BAN y TEMP_IP_BAN en instalaciones previas.
            try {
                stmt.execute(
                    "ALTER TABLE nb_punishments ADD COLUMN ip VARCHAR(45) DEFAULT NULL"
                );
            } catch (Exception ignored) {
                /* columna ya existe */
            }

            // Añade last_ip a nb_players si no existe
            try {
                stmt.execute(
                    "ALTER TABLE nb_players ADD COLUMN last_ip VARCHAR(45) DEFAULT NULL"
                );
            } catch (Exception ignored) {
                /* columna ya existe */
            }

            logger.info("[NeaBans] Tablas verificadas/creadas correctamente.");
        }
    }

    /** {@inheritDoc} */
    @Override
    public void createWebTables() throws Exception {
        try (
            Connection conn = getConnection();
            Statement stmt = conn.createStatement()
        ) {
            stmt.execute(getCreateWebUsersTableSQL());
            logger.info(
                "[NeaBans] Tabla nb_web_users verificada/creada correctamente."
            );
        }
    }

    /**
     * DDL para crear la tabla {@code nb_web_users}.
     * La sintaxis AUTO_INCREMENT/AUTOINCREMENT difiere entre MySQL y SQLite.
     */
    protected abstract String getCreateWebUsersTableSQL();

    /** {@inheritDoc} */
    @Override
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    // =========================================================================
    // Implementación de Database — Escritura de sanciones
    // =========================================================================

    /** {@inheritDoc} */
    @Override
    public int addPunishment(Punishment punishment) {
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(
                SQL_INSERT_PUNISHMENT,
                Statement.RETURN_GENERATED_KEYS
            )
        ) {
            ps.setString(1, punishment.getPlayerUuid().toString());
            ps.setString(2, punishment.getPlayerName());
            ps.setString(3, punishment.getType().name());
            ps.setString(4, punishment.getReason());
            ps.setString(5, punishment.getCategory()); // puede ser null → SQL NULL
            ps.setInt(6, punishment.getLevel());
            // staffUuid puede ser null (consola)
            ps.setString(
                7,
                punishment.getStaffUuid() != null
                    ? punishment.getStaffUuid().toString()
                    : null
            );
            ps.setString(8, punishment.getStaffName());
            ps.setString(9, punishment.getServer());
            ps.setLong(10, punishment.getStartTime());
            ps.setLong(11, punishment.getEndTime());

            ps.executeUpdate();

            // Recuperar el ID generado y asignarlo al objeto
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    punishment.setId(id);
                    return id;
                }
            }
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al insertar sanción: " + punishment,
                e
            );
        }
        return -1;
    }

    /** {@inheritDoc} */
    @Override
    public void deactivatePunishment(int id, String removedBy) {
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_DEACTIVATE_BY_ID)
        ) {
            ps.setString(1, removedBy);
            ps.setLong(2, System.currentTimeMillis());
            ps.setInt(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al desactivar sanción id=" + id,
                e
            );
        }
    }

    /** {@inheritDoc} */
    @Override
    public void deactivateByType(
        UUID uuid,
        PunishmentType type,
        String removedBy
    ) {
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_DEACTIVATE_BY_TYPE)
        ) {
            ps.setString(1, removedBy);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, uuid.toString());
            ps.setString(4, type.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al desactivar sanciones de tipo " +
                    type +
                    " para " +
                    uuid,
                e
            );
        }
    }

    // =========================================================================
    // Implementación de Database — Consultas de sanciones activas
    // =========================================================================

    /** {@inheritDoc} */
    @Override
    public Punishment getActiveBan(UUID uuid) {
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_GET_ACTIVE_BAN)
        ) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, System.currentTimeMillis());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapPunishment(rs);
            }
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al obtener ban activo de " + uuid,
                e
            );
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Punishment getActiveMute(UUID uuid) {
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_GET_ACTIVE_MUTE)
        ) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, System.currentTimeMillis());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapPunishment(rs);
            }
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al obtener mute activo de " + uuid,
                e
            );
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public List<Punishment> getActivePunishments(UUID uuid) {
        List<Punishment> list = new ArrayList<>();
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(
                SQL_GET_ACTIVE_PUNISHMENTS
            )
        ) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, System.currentTimeMillis());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapPunishment(rs));
            }
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al obtener sanciones activas de " + uuid,
                e
            );
        }
        return list;
    }

    // =========================================================================
    // Implementación de Database — Historial y paginación
    // =========================================================================

    /** {@inheritDoc} */
    @Override
    public List<Punishment> getHistory(UUID uuid, int page, int perPage) {
        List<Punishment> list = new ArrayList<>();
        // Convertir página basada en 1 a offset basado en 0
        int offset = (Math.max(1, page) - 1) * perPage;

        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_GET_HISTORY)
        ) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, perPage);
            ps.setInt(3, offset);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapPunishment(rs));
            }
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al obtener historial de " + uuid,
                e
            );
        }
        return list;
    }

    /** {@inheritDoc} */
    @Override
    public int getHistoryCount(UUID uuid) {
        return querySingleInt(SQL_GET_HISTORY_COUNT, uuid.toString());
    }

    /** {@inheritDoc} */
    @Override
    public int getCategoryCount(UUID uuid, String category) {
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_GET_CATEGORY_COUNT)
        ) {
            ps.setString(1, uuid.toString());
            ps.setString(2, category);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al contar sanciones de categoría '" +
                    category +
                    "' para " +
                    uuid,
                e
            );
        }
        return 0;
    }

    // =========================================================================
    // Implementación de Database — Warns
    // =========================================================================

    /** {@inheritDoc} */
    @Override
    public List<Punishment> getActiveWarns(UUID uuid) {
        List<Punishment> list = new ArrayList<>();
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_GET_ACTIVE_WARNS)
        ) {
            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapPunishment(rs));
            }
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al obtener warns de " + uuid,
                e
            );
        }
        return list;
    }

    /** {@inheritDoc} */
    @Override
    public int getWarnCount(UUID uuid) {
        return querySingleInt(SQL_GET_WARN_COUNT, uuid.toString());
    }

    // =========================================================================
    // Implementación de Database — Datos de jugadores
    // =========================================================================

    /** {@inheritDoc} */
    @Override
    public void savePlayerData(UUID uuid, String name) {
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(getSavePlayerSQL())
        ) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al guardar datos del jugador " +
                    name +
                    " (" +
                    uuid +
                    ")",
                e
            );
        }
    }

    /** {@inheritDoc} */
    @Override
    public UUID getUUID(String name) {
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_GET_UUID_BY_NAME)
        ) {
            ps.setString(1, name);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String raw = rs.getString("uuid");
                    return raw != null ? UUID.fromString(raw) : null;
                }
            }
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al buscar UUID de '" + name + "'",
                e
            );
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public String getLastName(UUID uuid) {
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_GET_NAME_BY_UUID)
        ) {
            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("name");
            }
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al buscar nombre de " + uuid,
                e
            );
        }
        return null;
    }

    // =========================================================================
    // Implementación de Database — IP-Ban
    // =========================================================================

    /** {@inheritDoc} */
    @Override
    public Punishment getActiveIpBan(String ip) {
        if (ip == null) return null;
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_GET_ACTIVE_IP_BAN)
        ) {
            ps.setString(1, ip);
            ps.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapPunishment(rs);
            }
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al buscar IP-ban de " + ip,
                e
            );
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void deactivateIpBan(String ip, String removedBy) {
        if (ip == null) return;
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_DEACTIVATE_IP_BAN)
        ) {
            ps.setString(1, removedBy);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, ip);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al desactivar IP-ban de " + ip,
                e
            );
        }
    }

    /** {@inheritDoc} */
    @Override
    public void savePlayerIp(UUID uuid, String name, String ip) {
        // Usar SQL con ip; hacer fallback a savePlayerData si ip es null
        if (ip == null) {
            savePlayerData(uuid, name);
            return;
        }
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(getSavePlayerIpSQL())
        ) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, ip);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al guardar IP de " + name,
                e
            );
        }
    }

    /**
     * SQL para guardar IP del jugador. Las subclases pueden sobrescribir.
     * Por defecto usa la sintaxis MySQL (ON DUPLICATE KEY UPDATE).
     */
    protected String getSavePlayerIpSQL() {
        return SQL_SAVE_PLAYER_IP_MYSQL;
    }

    /** {@inheritDoc} */
    @Override
    public String getLastIp(UUID uuid) {
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_GET_LAST_IP)
        ) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("last_ip");
            }
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al obtener IP de " + uuid,
                e
            );
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void cleanExpired() {
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_CLEAN_EXPIRED)
        ) {
            ps.setLong(1, System.currentTimeMillis());
            int updated = ps.executeUpdate();

            if (updated > 0) {
                logger.info(
                    "[NeaBans] cleanExpired: " +
                        updated +
                        " sanción(es) expirada(s) marcada(s) como inactivas."
                );
            }
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error durante cleanExpired()",
                e
            );
        }
    }

    /** {@inheritDoc} */
    @Override
    public int getTotalPunishments() {
        return querySingleInt(SQL_TOTAL_PUNISHMENTS);
    }

    /** {@inheritDoc} */
    @Override
    public int getTotalActiveBans() {
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_TOTAL_ACTIVE_BANS)
        ) {
            ps.setLong(1, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al contar banes activos",
                e
            );
        }
        return 0;
    }

    // =========================================================================
    // Implementacion de WebDatabase - Usuarios web
    // =========================================================================

    /** {@inheritDoc} */
    @Override
    public WebUser createWebUser(WebUser user) {
        if (user.getCreatedAt() == 0) user.setCreatedAt(
            System.currentTimeMillis()
        );
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(
                SQL_INSERT_WEB_USER,
                Statement.RETURN_GENERATED_KEYS
            )
        ) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setInt(3, user.isBanPlayers() ? 1 : 0);
            ps.setInt(4, user.isUnbanPlayers() ? 1 : 0);
            ps.setInt(5, user.isDeleteBanHistory() ? 1 : 0);
            ps.setInt(6, user.isMutePlayers() ? 1 : 0);
            ps.setInt(7, user.isUnmutePlayers() ? 1 : 0);
            ps.setInt(8, user.isDeleteMuteHistory() ? 1 : 0);
            ps.setInt(9, user.isEditAccounts() ? 1 : 0);
            ps.setInt(10, user.isAdmin() ? 1 : 0);
            ps.setLong(11, user.getCreatedAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) user.setId(keys.getInt(1));
            }
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al crear usuario web: " + user.getUsername(),
                e
            );
        }
        return user;
    }

    /** {@inheritDoc} */
    @Override
    public WebUser getWebUser(String username) {
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(
                SQL_GET_WEB_USER_BY_NAME
            )
        ) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapWebUser(rs);
            }
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al buscar usuario web: " + username,
                e
            );
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public WebUser getWebUserById(int id) {
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_GET_WEB_USER_BY_ID)
        ) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapWebUser(rs);
            }
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al buscar usuario web por id: " + id,
                e
            );
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public List<WebUser> getAllWebUsers() {
        List<WebUser> users = new ArrayList<>();
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_GET_ALL_WEB_USERS);
            ResultSet rs = ps.executeQuery()
        ) {
            while (rs.next()) {
                WebUser u = mapWebUser(rs);
                if (u != null) users.add(u);
            }
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al listar usuarios web",
                e
            );
        }
        return users;
    }

    /** {@inheritDoc} */
    @Override
    public void updateWebUser(WebUser user) {
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_WEB_USER)
        ) {
            ps.setInt(1, user.isBanPlayers() ? 1 : 0);
            ps.setInt(2, user.isUnbanPlayers() ? 1 : 0);
            ps.setInt(3, user.isDeleteBanHistory() ? 1 : 0);
            ps.setInt(4, user.isMutePlayers() ? 1 : 0);
            ps.setInt(5, user.isUnmutePlayers() ? 1 : 0);
            ps.setInt(6, user.isDeleteMuteHistory() ? 1 : 0);
            ps.setInt(7, user.isEditAccounts() ? 1 : 0);
            ps.setInt(8, user.isAdmin() ? 1 : 0);
            ps.setInt(9, user.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al actualizar usuario web id=" + user.getId(),
                e
            );
        }
    }

    /** {@inheritDoc} */
    @Override
    public void updateWebUserPassword(int id, String passwordHash) {
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(
                SQL_UPDATE_WEB_USER_PASSWORD
            )
        ) {
            ps.setString(1, passwordHash);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al actualizar contrasena usuario web id=" + id,
                e
            );
        }
    }

    /** {@inheritDoc} */
    @Override
    public void deleteWebUser(int id) {
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_DELETE_WEB_USER)
        ) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al eliminar usuario web id=" + id,
                e
            );
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean webUserExists(String username) {
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_WEB_USER_EXISTS)
        ) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al verificar usuario web: " + username,
                e
            );
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public int countWebUsers() {
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_COUNT_WEB_USERS);
            ResultSet rs = ps.executeQuery()
        ) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al contar usuarios web",
                e
            );
        }
        return 0;
    }

    // =========================================================================
    // Implementacion de WebDatabase - Historial y estadisticas
    // =========================================================================

    /** {@inheritDoc} */
    @Override
    public void clearHistory(UUID uuid) {
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_CLEAR_HISTORY)
        ) {
            ps.setString(1, uuid.toString());
            int rows = ps.executeUpdate();
            logger.info(
                "[NeaBans] Historial borrado para " +
                    uuid +
                    " (" +
                    rows +
                    " registros)."
            );
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al limpiar historial de: " + uuid,
                e
            );
        }
    }

    /** {@inheritDoc} */
    @Override
    public int getTotalMutes() {
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_TOTAL_MUTES);
            ResultSet rs = ps.executeQuery()
        ) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al contar mutes totales",
                e
            );
        }
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public int getTotalActiveMutes() {
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_TOTAL_ACTIVE_MUTES)
        ) {
            ps.setLong(1, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al contar mutes activos",
                e
            );
        }
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public int getTotalKicks() {
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_TOTAL_KICKS);
            ResultSet rs = ps.executeQuery()
        ) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[NeaBans] Error al contar kicks", e);
        }
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public int getTotalWarns() {
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_TOTAL_WARNS);
            ResultSet rs = ps.executeQuery()
        ) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[NeaBans] Error al contar warns", e);
        }
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public List<Punishment> getRecentPunishments(int limit) {
        List<Punishment> list = new ArrayList<>();
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_RECENT_PUNISHMENTS)
        ) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Punishment p = mapPunishment(rs);
                    if (p != null) list.add(p);
                }
            }
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al obtener sanciones recientes",
                e
            );
        }
        return list;
    }

    /** {@inheritDoc} */
    @Override
    public List<Punishment> getRecentPunishments(
        int limit,
        String type,
        String search
    ) {
        List<Punishment> list = new ArrayList<>();
        boolean hasType = type != null && !type.isBlank();
        boolean hasSearch = search != null && !search.isBlank();
        StringBuilder sql = new StringBuilder("SELECT * FROM nb_punishments");
        if (hasType || hasSearch) {
            sql.append(" WHERE");
            if (hasType) sql.append(" type = ?");
            if (hasType && hasSearch) sql.append(" AND");
            if (hasSearch) sql.append(
                " (LOWER(name) LIKE ? OR LOWER(staff_name) LIKE ?)"
            );
        }
        sql.append(" ORDER BY start_time DESC LIMIT ?");
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(sql.toString())
        ) {
            int idx = 1;
            if (hasType) ps.setString(idx++, type.toUpperCase());
            if (hasSearch) {
                String like = "%" + search.toLowerCase() + "%";
                ps.setString(idx++, like);
                ps.setString(idx++, like);
            }
            ps.setInt(idx, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Punishment p = mapPunishment(rs);
                    if (p != null) list.add(p);
                }
            }
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al obtener sanciones recientes filtradas",
                e
            );
        }
        return list;
    }

    /** {@inheritDoc} */
    @Override
    public int getTotalPunishmentsFiltered(String type, String search) {
        boolean hasType = type != null && !type.isBlank();
        boolean hasSearch = search != null && !search.isBlank();
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM nb_punishments"
        );
        if (hasType || hasSearch) {
            sql.append(" WHERE");
            if (hasType) sql.append(" type = ?");
            if (hasType && hasSearch) sql.append(" AND");
            if (hasSearch) sql.append(
                " (LOWER(name) LIKE ? OR LOWER(staff_name) LIKE ?)"
            );
        }
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(sql.toString())
        ) {
            int idx = 1;
            if (hasType) ps.setString(idx++, type.toUpperCase());
            if (hasSearch) {
                String like = "%" + search.toLowerCase() + "%";
                ps.setString(idx++, like);
                ps.setString(idx, like);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al contar sanciones filtradas",
                e
            );
        }
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public List<Punishment> getPaginatedPunishments(
        int page,
        int perPage,
        String type,
        String search
    ) {
        List<Punishment> list = new ArrayList<>();
        boolean hasType = type != null && !type.isBlank();
        boolean hasSearch = search != null && !search.isBlank();
        int offset = (Math.max(1, page) - 1) * perPage;
        StringBuilder sql = new StringBuilder("SELECT * FROM nb_punishments");
        if (hasType || hasSearch) {
            sql.append(" WHERE");
            if (hasType) sql.append(" type = ?");
            if (hasType && hasSearch) sql.append(" AND");
            if (hasSearch) sql.append(
                " (LOWER(name) LIKE ? OR LOWER(staff_name) LIKE ?)"
            );
        }
        sql.append(" ORDER BY start_time DESC LIMIT ? OFFSET ?");
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(sql.toString())
        ) {
            int idx = 1;
            if (hasType) ps.setString(idx++, type.toUpperCase());
            if (hasSearch) {
                String like = "%" + search.toLowerCase() + "%";
                ps.setString(idx++, like);
                ps.setString(idx++, like);
            }
            ps.setInt(idx++, perPage);
            ps.setInt(idx, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Punishment p = mapPunishment(rs);
                    if (p != null) list.add(p);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[NeaBans] Error al paginar sanciones", e);
        }
        return list;
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getDistinctServers() {
        List<String> servers = new ArrayList<>();
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_DISTINCT_SERVERS);
            ResultSet rs = ps.executeQuery()
        ) {
            while (rs.next()) servers.add(rs.getString("server"));
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error al obtener servidores distintos",
                e
            );
        }
        return servers;
    }

    /**
     * Mapea una fila de {@code nb_web_users} al modelo {@link WebUser}.
     */
    protected WebUser mapWebUser(ResultSet rs) throws SQLException {
        return new WebUser(
            rs.getInt("id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getInt("ban_players") == 1,
            rs.getInt("unban_players") == 1,
            rs.getInt("delete_ban_history") == 1,
            rs.getInt("mute_players") == 1,
            rs.getInt("unmute_players") == 1,
            rs.getInt("delete_mute_history") == 1,
            rs.getInt("edit_accounts") == 1,
            rs.getInt("is_admin") == 1,
            rs.getLong("created_at")
        );
    }

    // =========================================================================
    // Helpers internos
    // =========================================================================

    /**
     * Obtiene una conexión del pool HikariCP.
     *
     * @return Conexión JDBC lista para usar.
     * @throws SQLException Si el pool no puede proveer una conexión.
     */
    protected Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException(
                "[NeaBans] El pool de conexiones no está disponible."
            );
        }
        return dataSource.getConnection();
    }

    /**
     * Convierte una fila de {@code ResultSet} al modelo {@link Punishment}.
     *
     * <p>Este método es el único punto de conversión DB → objeto, garantizando
     * que todos los campos sean mapeados de forma consistente.</p>
     *
     * @param rs ResultSet posicionado en la fila a mapear.
     * @return Objeto {@link Punishment} hidratado con los datos de la fila.
     * @throws SQLException Si ocurre un error al leer alguna columna.
     */
    protected Punishment mapPunishment(ResultSet rs) throws SQLException {
        // Parseo del UUID del jugador
        UUID playerUuid = UUID.fromString(rs.getString("uuid"));

        // staffUuid puede ser NULL en la DB (acción de consola)
        String rawStaffUuid = rs.getString("staff_uuid");
        UUID staffUuid = (rawStaffUuid != null && !rawStaffUuid.isBlank())
            ? UUID.fromString(rawStaffUuid)
            : null;

        // Parseo del tipo de sanción con manejo de valores desconocidos
        PunishmentType type = PunishmentType.fromString(rs.getString("type"));
        if (type == null) {
            logger.warning(
                "[NeaBans] Tipo de sanción desconocido en DB: '" +
                    rs.getString("type") +
                    "' — se omitirá este registro."
            );
            // Retornamos null; el caller debe verificar para no añadir nulls a la lista
            return null;
        }

        // active se guarda como TINYINT(1): 1 = true, 0 = false
        boolean active = rs.getInt("active") == 1;

        Punishment p = new Punishment(
            rs.getInt("id"),
            playerUuid,
            rs.getString("name"),
            type,
            rs.getString("reason"),
            rs.getString("category"),
            rs.getInt("level"),
            staffUuid,
            rs.getString("staff_name"),
            rs.getString("server"),
            rs.getLong("start_time"),
            rs.getLong("end_time"),
            active,
            rs.getString("removed_by"),
            rs.getLong("removed_at")
        );
        // Leer el campo ip (puede no existir en tablas antiguas — usar try/catch)
        try {
            p.setIp(rs.getString("ip"));
        } catch (SQLException ignored) {}
        return p;
    }

    /**
     * Ejecuta una query SQL que devuelve un único entero (p.ej. {@code COUNT(*)})
     * sin parámetros adicionales.
     *
     * @param sql Consulta SQL a ejecutar.
     * @return El entero devuelto, o {@code 0} en caso de error.
     */
    private int querySingleInt(String sql) {
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()
        ) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error en querySingleInt: " + sql,
                e
            );
        }
        return 0;
    }

    /**
     * Ejecuta una query SQL que devuelve un único entero, con un parámetro String.
     *
     * @param sql   Consulta SQL con un placeholder {@code ?}.
     * @param param Valor del parámetro.
     * @return El entero devuelto, o {@code 0} en caso de error.
     */
    private int querySingleInt(String sql, String param) {
        try (
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.log(
                Level.SEVERE,
                "[NeaBans] Error en querySingleInt(param): " + sql,
                e
            );
        }
        return 0;
    }
}
