package me.nea.neabans.proxy.shared;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Capa de acceso a la base de datos MySQL para el plugin de proxy NeaBans.
 *
 * <p>Se conecta a la <strong>misma</strong> base de datos que el plugin de Paper,
 * usando HikariCP shadeado para evitar conflictos de classpath.</p>
 *
 * <h2>Tablas utilizadas</h2>
 * <ul>
 *   <li>{@code {prefix}punishments} – tabla principal creada por el plugin de Paper.
 *       El proxy añade la columna {@code ip} mediante una migración automática.</li>
 *   <li>{@code {prefix}players}     – tabla de caché UUID ↔ nombre (Paper).</li>
 *   <li>{@code {prefix}player_ips}  – tabla creada por el proxy para registrar
 *       la última IP conocida de cada jugador.</li>
 * </ul>
 *
 * <h2>Migraciones automáticas</h2>
 * Al llamar a {@link #connect()}, el proxy ejecuta:
 * <ol>
 *   <li>ALTER TABLE para añadir la columna {@code ip} si no existe.</li>
 *   <li>Creación del índice {@code idx_ip} sobre esa columna.</li>
 *   <li>CREATE TABLE IF NOT EXISTS para {@code {prefix}player_ips}.</li>
 * </ol>
 */
public class ProxyDatabase {

    // =========================================================================
    // Parámetros de conexión
    // =========================================================================

    private final String host;
    private final int    port;
    private final String database;
    private final String user;
    private final String pass;
    private final int    poolSize;

    /**
     * Prefijo de tabla compartido con el plugin principal (e.g. {@code "nb_"}).
     * Todas las consultas lo anteponen al nombre lógico de la tabla.
     */
    private final String tablePrefix;

    private HikariDataSource dataSource;
    private final Logger     logger;

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * @param host        Hostname o IP del servidor MySQL.
     * @param port        Puerto TCP (normalmente 3306).
     * @param database    Nombre del schema/base de datos.
     * @param user        Usuario de la DB.
     * @param pass        Contraseña del usuario.
     * @param poolSize    Tamaño máximo del pool HikariCP.
     * @param tablePrefix Prefijo de tabla (debe coincidir con el del plugin Paper).
     */
    public ProxyDatabase(
            String host,
            int    port,
            String database,
            String user,
            String pass,
            int    poolSize,
            String tablePrefix
    ) {
        this.host        = host;
        this.port        = port;
        this.database    = database;
        this.user        = user;
        this.pass        = pass;
        this.poolSize    = poolSize;
        this.tablePrefix = tablePrefix;
        this.logger      = Logger.getLogger("NeaBansProxy.Database");
    }

    // =========================================================================
    // Conexión
    // =========================================================================

    /**
     * Inicializa el pool HikariCP y ejecuta las migraciones necesarias.
     *
     * @throws Exception si la conexión a MySQL falla.
     */
    public void connect() throws Exception {
        HikariConfig cfg = new HikariConfig();

        String jdbcUrl = String.format(
                "jdbc:mysql://%s:%d/%s" +
                "?useSSL=false&allowPublicKeyRetrieval=true" +
                "&characterEncoding=UTF-8&useUnicode=true" +
                "&serverTimezone=UTC",
                host, port, database
        );

        cfg.setJdbcUrl(jdbcUrl);
        cfg.setDriverClassName("com.mysql.cj.jdbc.Driver");
        cfg.setUsername(user);
        cfg.setPassword(pass);

        // Pool de tamaño fijo para el proxy (carga baja, consultas puntuales)
        cfg.setMaximumPoolSize(poolSize);
        cfg.setMinimumIdle(Math.max(1, poolSize / 2));
        cfg.setConnectionTimeout(30_000L);
        cfg.setIdleTimeout(600_000L);
        cfg.setMaxLifetime(1_800_000L);
        cfg.setKeepaliveTime(60_000L);
        cfg.setPoolName("NeaBansProxy-Pool");

        // Optimizaciones del driver MySQL
        cfg.addDataSourceProperty("cachePrepStmts",          "true");
        cfg.addDataSourceProperty("prepStmtCacheSize",       "100");
        cfg.addDataSourceProperty("prepStmtCacheSqlLimit",   "2048");
        cfg.addDataSourceProperty("useServerPrepStmts",      "true");
        cfg.addDataSourceProperty("rewriteBatchedStatements","true");
        cfg.addDataSourceProperty("autoReconnect",           "true");

        dataSource = new HikariDataSource(cfg);
        logger.info("[NeaBansProxy] Pool MySQL iniciado: " + host + ":" + port + "/" + database);

        runMigrations();
    }

    /** Cierra el pool de conexiones de forma limpia. */
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("[NeaBansProxy] Pool MySQL cerrado.");
        }
    }

    /** @return {@code true} si el pool está abierto y operativo. */
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    // =========================================================================
    // Migraciones de esquema
    // =========================================================================

    /**
     * Ejecuta las migraciones necesarias para extender el esquema del plugin
     * principal con las columnas e índices que el proxy necesita.
     *
     * <p>Las migraciones son idempotentes: si la columna o el índice ya existen,
     * el error SQL se captura silenciosamente (código 1060 o 1061 de MySQL).</p>
     */
    private void runMigrations() {
        String punishments = "`" + tablePrefix + "punishments`";

        // 1. Añadir columna ip a la tabla de sanciones
        try (Connection c = dataSource.getConnection();
             Statement  s = c.createStatement()) {
            s.execute("ALTER TABLE " + punishments +
                      " ADD COLUMN `ip` VARCHAR(45) DEFAULT NULL");
            logger.info("[NeaBansProxy] Columna 'ip' añadida a " + punishments);
        } catch (SQLException e) {
            // 1060 = Duplicate column name → ya existe, OK
            if (e.getErrorCode() != 1060) {
                logger.log(Level.WARNING,
                        "[NeaBansProxy] No se pudo añadir columna 'ip' (puede estar ya presente): "
                        + e.getMessage());
            }
        }

        // 2. Crear índice sobre la columna ip
        try (Connection c = dataSource.getConnection();
             Statement  s = c.createStatement()) {
            s.execute("ALTER TABLE " + punishments +
                      " ADD INDEX `idx_proxy_ip` (`ip`)");
            logger.info("[NeaBansProxy] Índice 'idx_proxy_ip' creado en " + punishments);
        } catch (SQLException e) {
            // 1061 = Duplicate key name → ya existe, OK
            if (e.getErrorCode() != 1061) {
                logger.log(Level.WARNING,
                        "[NeaBansProxy] No se pudo crear índice 'idx_proxy_ip': "
                        + e.getMessage());
            }
        }

        // 3. Tabla de IPs de jugadores (exclusiva del proxy)
        String createPlayerIps =
                "CREATE TABLE IF NOT EXISTS `" + tablePrefix + "player_ips` (" +
                "  `uuid`      VARCHAR(36) NOT NULL," +
                "  `name`      VARCHAR(16) NOT NULL," +
                "  `ip`        VARCHAR(45) NOT NULL," +
                "  `last_seen` BIGINT      NOT NULL," +
                "  PRIMARY KEY (`uuid`)," +
                "  INDEX `idx_pip_ip` (`ip`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

        try (Connection c = dataSource.getConnection();
             Statement  s = c.createStatement()) {
            s.execute(createPlayerIps);
            logger.info("[NeaBansProxy] Tabla '" + tablePrefix + "player_ips' verificada.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE,
                    "[NeaBansProxy] Error creando tabla player_ips: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // Consultas de sanciones
    // =========================================================================

    /**
     * Obtiene el ban activo (BAN o TEMP_BAN) de un jugador por su UUID.
     *
     * <p>La query excluye automáticamente los bans expirados gracias a la
     * condición {@code (end_time=-1 OR end_time > now)}.</p>
     *
     * @param uuid UUID del jugador.
     * @return La sanción activa, o {@code null} si no hay ninguna.
     */
    public ProxyPunishment getActiveBan(UUID uuid) {
        String sql =
                "SELECT * FROM `" + tablePrefix + "punishments`" +
                " WHERE `uuid` = ?" +
                "   AND `type` IN ('BAN', 'TEMP_BAN')" +
                "   AND `active` = 1" +
                "   AND (`end_time` = -1 OR `end_time` > ?)" +
                " ORDER BY `start_time` DESC LIMIT 1";

        try (Connection c  = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ps.setLong(2, System.currentTimeMillis());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapPunishment(rs);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE,
                    "[NeaBansProxy] Error en getActiveBan(" + uuid + "): " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Obtiene el IP-ban activo (IP_BAN o TEMP_IP_BAN) para una dirección IP.
     *
     * <p>Requiere que la columna {@code ip} exista en {@code {prefix}punishments},
     * lo cual es garantizado por la migración automática de {@link #connect()}.</p>
     *
     * @param ip Dirección IP en formato string (e.g. {@code "192.168.1.1"}).
     * @return La sanción activa, o {@code null} si no hay ninguna.
     */
    public ProxyPunishment getActiveIpBan(String ip) {
        String sql =
                "SELECT * FROM `" + tablePrefix + "punishments`" +
                " WHERE `ip` = ?" +
                "   AND `type` IN ('IP_BAN', 'TEMP_IP_BAN')" +
                "   AND `active` = 1" +
                "   AND (`end_time` = -1 OR `end_time` > ?)" +
                " ORDER BY `start_time` DESC LIMIT 1";

        try (Connection c  = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, ip);
            ps.setLong(2, System.currentTimeMillis());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapPunishment(rs);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE,
                    "[NeaBansProxy] Error en getActiveIpBan(" + ip + "): " + e.getMessage(), e);
        }
        return null;
    }

    // =========================================================================
    // Registro de IPs de jugadores
    // =========================================================================

    /**
     * Guarda o actualiza la última IP conocida de un jugador en la tabla
     * {@code {prefix}player_ips}. Se llama en cada login exitoso.
     *
     * <p>Usa UPSERT de MySQL ({@code ON DUPLICATE KEY UPDATE}) para ser
     * idempotente en llamadas repetidas.</p>
     *
     * @param uuid UUID del jugador.
     * @param name Nombre del jugador en el momento del login.
     * @param ip   Dirección IP del jugador.
     */
    public void savePlayerIp(UUID uuid, String name, String ip) {
        String sql =
                "INSERT INTO `" + tablePrefix + "player_ips`" +
                " (`uuid`, `name`, `ip`, `last_seen`) VALUES (?, ?, ?, ?)" +
                " ON DUPLICATE KEY UPDATE" +
                "   `name` = VALUES(`name`)," +
                "   `ip`   = VALUES(`ip`)," +
                "   `last_seen` = VALUES(`last_seen`)";

        try (Connection c  = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, ip);
            ps.setLong(4,   System.currentTimeMillis());
            ps.executeUpdate();

        } catch (SQLException e) {
            logger.log(Level.WARNING,
                    "[NeaBansProxy] Error en savePlayerIp(" + uuid + ", " + ip + "): "
                    + e.getMessage(), e);
        }
    }

    // =========================================================================
    // Utilidades de lookup
    // =========================================================================

    /**
     * Busca el UUID de un jugador por su nombre en la tabla {@code {prefix}players}
     * (tabla de caché compartida con el plugin Paper).
     *
     * @param name Nombre del jugador (case-insensitive en MySQL).
     * @return UUID del jugador, o {@code null} si no está en la cache.
     */
    public UUID getUUID(String name) {
        String sql =
                "SELECT `uuid` FROM `" + tablePrefix + "players`" +
                " WHERE `name` = ? LIMIT 1";

        try (Connection c  = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String uuidStr = rs.getString("uuid");
                    if (uuidStr != null && !uuidStr.isEmpty()) {
                        return UUID.fromString(uuidStr);
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING,
                    "[NeaBansProxy] Error en getUUID(" + name + "): " + e.getMessage(), e);
        }
        return null;
    }

    // =========================================================================
    // Mapper interno
    // =========================================================================

    /**
     * Mapea una fila de {@code ResultSet} al POJO {@link ProxyPunishment}.
     *
     * <p>La columna {@code ip} se lee con tolerancia a fallos: si no existe en el
     * ResultSet (e.g. migración pendiente), se asigna {@code null} sin lanzar
     * excepción.</p>
     *
     * @param rs ResultSet posicionado en la fila a mapear.
     * @return Nueva instancia de {@link ProxyPunishment}.
     * @throws SQLException si falla la lectura de cualquier columna requerida.
     */
    private ProxyPunishment mapPunishment(ResultSet rs) throws SQLException {
        UUID playerUuid;
        try {
            playerUuid = UUID.fromString(rs.getString("uuid"));
        } catch (IllegalArgumentException e) {
            playerUuid = UUID.randomUUID(); // fallback de seguridad
        }

        // Columna ip puede no existir antes de la migración
        String ip = null;
        try {
            ip = rs.getString("ip");
        } catch (SQLException ignored) {
            // Columna no presente todavía → ip = null
        }

        return new ProxyPunishment(
                playerUuid,
                rs.getString("name"),
                rs.getString("type"),
                rs.getString("reason"),
                rs.getString("staff_name"),
                rs.getString("server"),
                rs.getLong("start_time"),
                rs.getLong("end_time"),
                rs.getInt("active") == 1,
                ip
        );
    }

    // =========================================================================
    // Helpers internos
    // =========================================================================

    /** Obtiene una conexión del pool. Lanza {@link RuntimeException} si no hay pool. */
    private Connection getConnection() throws SQLException {
        if (!isConnected()) {
            throw new SQLException("El pool de NeaBansProxy no está inicializado.");
        }
        return dataSource.getConnection();
    }

    @Override
    public String toString() {
        return "ProxyDatabase{host='" + host + "', port=" + port +
               ", db='" + database + "', prefix='" + tablePrefix + "'}";
    }
}
