package me.nea.neabans.database;

import com.zaxxer.hikari.HikariConfig;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Implementación de {@link AbstractDatabase} para MySQL y MariaDB.
 *
 * <p>Pensada para entornos de producción con servidores de red (BungeeCord,
 * Velocity) donde múltiples instancias del servidor acceden a la misma base
 * de datos de forma concurrente.</p>
 *
 * <h2>Características</h2>
 * <ul>
 *   <li>Pool de conexiones HikariCP con tamaño configurable.</li>
 *   <li>Prepared statement cache activado para máximo rendimiento.</li>
 *   <li>Reconexión automática ante caídas de la red.</li>
 *   <li>DDL compatible con MySQL 5.7+ y MariaDB 10.3+.</li>
 * </ul>
 *
 * <h2>Uso típico</h2>
 * <pre>{@code
 * Database db = new MySQLDatabase(plugin, "localhost", 3306,
 *         "neabans_db", "root", "password", 10);
 * db.connect();
 * db.createTables();
 * }</pre>
 */
public class MySQLDatabase extends AbstractDatabase {

    // =========================================================================
    // Parámetros de conexión
    // =========================================================================

    /** Hostname o IP del servidor MySQL. */
    private final String host;

    /** Puerto TCP del servidor MySQL (por defecto 3306). */
    private final int port;

    /** Nombre de la base de datos (schema) a utilizar. */
    private final String database;

    /** Usuario con permisos sobre la base de datos. */
    private final String username;

    /** Contraseña del usuario. */
    private final String password;

    /** Número máximo de conexiones en el pool HikariCP. */
    private final int poolSize;

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Crea una nueva instancia de la implementación MySQL.
     *
     * @param plugin   Instancia del plugin principal de Paper.
     * @param host     Hostname o IP del servidor MySQL.
     * @param port     Puerto TCP (normalmente 3306).
     * @param database Nombre del schema/base de datos.
     * @param username Usuario de la DB.
     * @param password Contraseña del usuario.
     * @param poolSize Tamaño máximo del pool de conexiones HikariCP.
     *                 Valores recomendados: entre 5 y 20.
     */
    public MySQLDatabase(
        JavaPlugin plugin,
        String host,
        int port,
        String database,
        String username,
        String password,
        int poolSize
    ) {
        super(plugin);
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.poolSize = poolSize;
    }

    // =========================================================================
    // Configuración de HikariCP para MySQL
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * <p>Configura HikariCP con:</p>
     * <ul>
     *   <li>JDBC URL con parámetros de rendimiento y compatibilidad.</li>
     *   <li>Driver explícito: {@code com.mysql.cj.jdbc.Driver}.</li>
     *   <li>Pool con tamaño fijado al valor de {@code poolSize}.</li>
     *   <li>Propiedades de caché de PreparedStatements del lado del servidor.</li>
     *   <li>Codificación UTF-8 para soporte de caracteres especiales.</li>
     * </ul>
     */
    @Override
    protected void configureHikari(HikariConfig config) {
        // --- URL de conexión JDBC ---
        // useSSL=false para evitar warnings en entornos locales;
        // cambiar a true en producción con certificados SSL configurados.
        String jdbcUrl = String.format(
            "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true" +
                "&characterEncoding=UTF-8&useUnicode=true",
            host,
            port,
            database
        );
        config.setJdbcUrl(jdbcUrl);

        // --- Driver y credenciales ---
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setUsername(username);
        config.setPassword(password);

        // --- Tamaño del pool ---
        // maximumPoolSize y minimumIdle iguales → pool de tamaño fijo
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(Math.max(1, poolSize / 2));

        // --- Timeouts ---
        // Tiempo máximo esperando una conexión libre del pool (30s)
        config.setConnectionTimeout(30_000L);
        // Tiempo máximo que una conexión puede estar ociosa antes de cerrarla (10min)
        config.setIdleTimeout(600_000L);
        // Tiempo de vida máximo de una conexión en el pool (30min)
        config.setMaxLifetime(1_800_000L);
        // Intervalo de latidos para mantener conexiones activas (1min)
        config.setKeepaliveTime(60_000L);

        // --- Nombre del pool para logging ---
        config.setPoolName("NeaBans-MySQL-Pool");

        // --- Propiedades de rendimiento MySQL ---
        // cachePrepStmts: activa la caché de PreparedStatements en el driver
        config.addDataSourceProperty("cachePrepStmts", "true");
        // prepStmtCacheSize: número de statements cacheados por conexión
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        // prepStmtCacheSqlLimit: longitud máxima de SQL a cachear
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        // useServerPrepStmts: delega la preparación al servidor MySQL para
        // mayor eficiencia y prevención de injection a nivel de protocolo
        config.addDataSourceProperty("useServerPrepStmts", "true");
        // autoReconnect: reconexión automática ante caídas de red
        config.addDataSourceProperty("autoReconnect", "true");
        // rewriteBatchedStatements: mejora el rendimiento de batches
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        // Zona horaria del servidor MySQL
        config.addDataSourceProperty("serverTimezone", "UTC");
    }

    // =========================================================================
    // DDL específico de MySQL
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * <p>Usa la sintaxis MySQL con:</p>
     * <ul>
     *   <li>{@code AUTO_INCREMENT} (sin la palabra "KEY" adicional de SQLite).</li>
     *   <li>{@code ENGINE=InnoDB} para soporte de transacciones y FK.</li>
     *   <li>{@code DEFAULT CHARSET=utf8mb4} para soporte completo de Unicode.</li>
     * </ul>
     */
    @Override
    protected String getCreatePunishmentsTableSQL() {
        return """
        CREATE TABLE IF NOT EXISTS `nb_punishments` (
            `id`          INT             NOT NULL AUTO_INCREMENT,
            `uuid`        VARCHAR(36)     NOT NULL,
            `name`        VARCHAR(16)     NOT NULL,
            `type`        VARCHAR(16)     NOT NULL,
            `reason`      TEXT            NOT NULL,
            `category`    VARCHAR(64)     DEFAULT NULL,
            `level`       INT             NOT NULL DEFAULT 0,
            `staff_uuid`  VARCHAR(36)     DEFAULT NULL,
            `staff_name`  VARCHAR(16)     NOT NULL,
            `server`      VARCHAR(64)     NOT NULL DEFAULT 'global',
            `start_time`  BIGINT          NOT NULL,
            `end_time`    BIGINT          NOT NULL DEFAULT -1,
            `active`      TINYINT(1)      NOT NULL DEFAULT 1,
            `removed_by`  VARCHAR(16)     DEFAULT NULL,
            `removed_at`  BIGINT          NOT NULL DEFAULT 0,
            PRIMARY KEY (`id`),
            INDEX `idx_uuid`        (`uuid`),
            INDEX `idx_active`      (`active`),
            INDEX `idx_type_active` (`type`, `active`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Tabla de caché UUID ↔ nombre de jugador compatible con MySQL.</p>
     */
    @Override
    protected String getCreatePlayersTableSQL() {
        return """
        CREATE TABLE IF NOT EXISTS `nb_players` (
            `uuid`        VARCHAR(36)     NOT NULL,
            `name`        VARCHAR(16)     NOT NULL,
            `last_seen`   BIGINT          NOT NULL,
            PRIMARY KEY (`uuid`),
            INDEX `idx_name` (`name`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """;
    }

    /**
     * {@inheritDoc}
     *
     * <p>MySQL usa {@code ON DUPLICATE KEY UPDATE} para el upsert.</p>
     */
    @Override
    protected String getSavePlayerSQL() {
        return (
            "INSERT INTO nb_players (uuid, name, last_seen) VALUES (?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE name = VALUES(name), last_seen = VALUES(last_seen)"
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>DDL para MySQL con {@code AUTO_INCREMENT} y {@code ENGINE=InnoDB}.</p>
     */
    @Override
    protected String getCreateWebUsersTableSQL() {
        return """
        CREATE TABLE IF NOT EXISTS `nb_web_users` (
            `id`                  INT             NOT NULL AUTO_INCREMENT,
            `username`            VARCHAR(32)     NOT NULL,
            `password_hash`       VARCHAR(255)    NOT NULL,
            `ban_players`         TINYINT(1)      NOT NULL DEFAULT 0,
            `unban_players`       TINYINT(1)      NOT NULL DEFAULT 0,
            `delete_ban_history`  TINYINT(1)      NOT NULL DEFAULT 0,
            `mute_players`        TINYINT(1)      NOT NULL DEFAULT 0,
            `unmute_players`      TINYINT(1)      NOT NULL DEFAULT 0,
            `delete_mute_history` TINYINT(1)      NOT NULL DEFAULT 0,
            `edit_accounts`       TINYINT(1)      NOT NULL DEFAULT 0,
            `is_admin`            TINYINT(1)      NOT NULL DEFAULT 0,
            `created_at`          BIGINT          NOT NULL,
            PRIMARY KEY (`id`),
            UNIQUE KEY `uk_username` (`username`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """;
    }

    // =========================================================================
    // toString para logging
    // =========================================================================

    @Override
    public String toString() {
        return (
            "MySQLDatabase{host='" +
            host +
            "', port=" +
            port +
            ", database='" +
            database +
            "', user='" +
            username +
            "', poolSize=" +
            poolSize +
            "}"
        );
    }
}
