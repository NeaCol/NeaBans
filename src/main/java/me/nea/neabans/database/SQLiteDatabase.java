package me.nea.neabans.database;

import com.zaxxer.hikari.HikariConfig;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Implementación de {@link AbstractDatabase} para SQLite.
 *
 * <p>Ideal para servidores pequeños, entornos de desarrollo o como respaldo
 * cuando no hay un servidor MySQL disponible. No requiere instalación de
 * ningún servicio externo: los datos se almacenan en un único archivo
 * {@code .db} en el data folder del plugin.</p>
 *
 * <h2>Características</h2>
 * <ul>
 *   <li>Pool de 1 sola conexión (SQLite no soporta concurrencia real).</li>
 *   <li>{@code PRAGMA journal_mode=WAL} activado para mejor rendimiento
 *       en lecturas concurrentes y menor contención de escritura.</li>
 *   <li>Driver bundled: {@code org.xerial:sqlite-jdbc} (shadeado en el jar).</li>
 *   <li>El archivo se crea automáticamente si no existe.</li>
 * </ul>
 *
 * <h2>Limitaciones respecto a MySQL</h2>
 * <ul>
 *   <li>No apto para múltiples servidores accediendo a la misma DB.</li>
 *   <li>Sin soporte nativo de {@code TINYINT(1)}: se usa {@code INTEGER}.</li>
 *   <li>{@code AUTO_INCREMENT} se expresa como {@code INTEGER PRIMARY KEY AUTOINCREMENT}.</li>
 * </ul>
 *
 * <h2>Uso típico</h2>
 * <pre>{@code
 * Database db = new SQLiteDatabase(plugin, "punishments.db");
 * db.connect();
 * db.createTables();
 * }</pre>
 */
public class SQLiteDatabase extends AbstractDatabase {

    // =========================================================================
    // Campos
    // =========================================================================

    /**
     * Nombre del archivo de base de datos SQLite.
     * Se creará dentro del data folder del plugin.
     * Ejemplo: {@code "punishments.db"}.
     */
    private final String fileName;

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Crea una nueva instancia de la implementación SQLite.
     *
     * @param plugin   Instancia del plugin principal de Paper.
     * @param fileName Nombre del archivo {@code .db} a crear/usar dentro del
     *                 data folder del plugin (e.g. {@code "punishments.db"}).
     */
    public SQLiteDatabase(JavaPlugin plugin, String fileName) {
        super(plugin);
        this.fileName = fileName;
    }

    // =========================================================================
    // Configuración de HikariCP para SQLite
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * <p>Configura HikariCP con:</p>
     * <ul>
     *   <li>JDBC URL apuntando al archivo en el data folder del plugin.</li>
     *   <li>Driver {@code org.sqlite.JDBC}.</li>
     *   <li>Pool de exactamente <strong>1 conexión</strong> (SQLite no soporta
     *       escrituras concurrentes desde múltiples conexiones).</li>
     *   <li>Sin validación de conexión innecesaria (SQLite es local).</li>
     * </ul>
     */
    @Override
    protected void configureHikari(HikariConfig config) {
        // Asegurarse de que el data folder del plugin exista
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        // Ruta absoluta al archivo SQLite
        File dbFile = new File(dataFolder, fileName);
        String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.sqlite.JDBC");

        // SQLite no necesita usuario/contraseña
        config.setUsername(null);
        config.setPassword(null);

        // Pool de UNA sola conexión para evitar problemas de concurrencia
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);

        // Timeouts generosos ya que es acceso local
        config.setConnectionTimeout(30_000L);
        // Sin idle timeout para mantener la conexión siempre lista
        config.setIdleTimeout(0L);
        config.setMaxLifetime(0L);

        // Nombre del pool para logging
        config.setPoolName("NeaBans-SQLite-Pool");

        // Propiedad para habilitar transacciones en SQLite
        config.addDataSourceProperty("journal_mode", "WAL");

        logger.info(
            "[NeaBans] SQLite: usando archivo '" +
                dbFile.getAbsolutePath() +
                "'"
        );
    }

    // =========================================================================
    // Configuración post-conexión: PRAGMA WAL
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * <p>Además de llamar al {@code connect()} padre, aplica el PRAGMA
     * {@code journal_mode=WAL} para mejorar el rendimiento de lecturas
     * concurrentes y reducir la contención en escrituras.</p>
     *
     * <p><strong>WAL (Write-Ahead Logging)</strong> permite que lecturas
     * y escrituras ocurran sin bloquearse mutuamente, lo que es esencial
     * en un servidor Minecraft donde múltiples threads pueden consultar la DB
     * mientras el main thread escribe.</p>
     *
     * @throws Exception Si la conexión falla o no se puede aplicar el PRAGMA.
     */
    @Override
    public void connect() throws Exception {
        // Inicializar el pool mediante el método padre
        super.connect();

        // Aplicar PRAGMAs de rendimiento una sola vez tras la conexión
        applyPragmas();
    }

    /**
     * Aplica los PRAGMAs de SQLite para optimizar el rendimiento.
     *
     * <p>PRAGMAs aplicados:</p>
     * <ul>
     *   <li>{@code journal_mode=WAL} — Modo write-ahead logging: lecturas no
     *       bloquean escrituras y viceversa. Mejor rendimiento que el modo
     *       DELETE por defecto.</li>
     *   <li>{@code synchronous=NORMAL} — Equilibrio entre seguridad y velocidad.
     *       ({@code FULL} es más seguro pero más lento; {@code OFF} es peligroso).</li>
     *   <li>{@code cache_size=-64000} — Caché de páginas en memoria: 64MB.
     *       El signo negativo indica kilobytes.</li>
     *   <li>{@code foreign_keys=ON} — Habilita la verificación de foreign keys
     *       (desactivado por defecto en SQLite).</li>
     * </ul>
     */
    private void applyPragmas() {
        try (
            Connection conn = getConnection();
            Statement stmt = conn.createStatement()
        ) {
            // WAL mode: la mejora de rendimiento más significativa para SQLite
            stmt.execute("PRAGMA journal_mode=WAL");
            // Sincronización normal: persiste en disco periódicamente (no en cada write)
            stmt.execute("PRAGMA synchronous=NORMAL");
            // Caché de páginas: 64MB en memoria para reducir I/O de disco
            stmt.execute("PRAGMA cache_size=-64000");
            // Forzar verificación de integridad referencial
            stmt.execute("PRAGMA foreign_keys=ON");

            logger.info(
                "[NeaBans] SQLite PRAGMAs aplicados (WAL mode activo)."
            );
        } catch (SQLException e) {
            logger.warning(
                "[NeaBans] No se pudieron aplicar los PRAGMAs de SQLite: " +
                    e.getMessage()
            );
        }
    }

    // =========================================================================
    // DDL específico de SQLite
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * <p>Usa la sintaxis SQLite con:</p>
     * <ul>
     *   <li>{@code INTEGER PRIMARY KEY AUTOINCREMENT} para el ID autonumérico.</li>
     *   <li>{@code INTEGER} en lugar de {@code TINYINT(1)} para el campo {@code active}.</li>
     *   <li>Sin {@code ENGINE} ni {@code CHARSET} (no aplica en SQLite).</li>
     *   <li>Sin tamaños en los tipos ({@code VARCHAR(36)} es válido pero SQLite
     *       los ignora; se mantienen por legibilidad).</li>
     * </ul>
     */
    @Override
    protected String getCreatePunishmentsTableSQL() {
        return """
        CREATE TABLE IF NOT EXISTS `nb_punishments` (
            `id`          INTEGER         PRIMARY KEY AUTOINCREMENT,
            `uuid`        VARCHAR(36)     NOT NULL,
            `name`        VARCHAR(16)     NOT NULL,
            `type`        VARCHAR(16)     NOT NULL,
            `reason`      TEXT            NOT NULL,
            `category`    VARCHAR(64)     DEFAULT NULL,
            `level`       INTEGER         NOT NULL DEFAULT 0,
            `staff_uuid`  VARCHAR(36)     DEFAULT NULL,
            `staff_name`  VARCHAR(16)     NOT NULL,
            `server`      VARCHAR(64)     NOT NULL DEFAULT 'global',
            `start_time`  BIGINT          NOT NULL,
            `end_time`    BIGINT          NOT NULL DEFAULT -1,
            `active`      INTEGER         NOT NULL DEFAULT 1,
            `removed_by`  VARCHAR(16)     DEFAULT NULL,
            `removed_at`  BIGINT          NOT NULL DEFAULT 0
        )
        """;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Tabla de caché UUID ↔ nombre de jugador compatible con SQLite.</p>
     */
    @Override
    protected String getCreatePlayersTableSQL() {
        return """
        CREATE TABLE IF NOT EXISTS `nb_players` (
            `uuid`        VARCHAR(36)     PRIMARY KEY,
            `name`        VARCHAR(16)     NOT NULL,
            `last_seen`   BIGINT          NOT NULL
        )
        """;
    }

    /**
     * {@inheritDoc}
     *
     * <p>SQLite no tiene {@code ON DUPLICATE KEY UPDATE}; usa
     * {@code INSERT OR REPLACE} en su lugar.</p>
     */
    @Override
    protected String getSavePlayerSQL() {
        return "INSERT OR REPLACE INTO nb_players (uuid, name, last_seen) VALUES (?, ?, ?)";
    }

    /**
     * SQLite usa {@code INSERT OR REPLACE} en lugar de {@code ON DUPLICATE KEY UPDATE}.
     */
    @Override
    protected String getSavePlayerIpSQL() {
        return "INSERT OR REPLACE INTO nb_players (uuid, name, last_ip, last_seen) VALUES (?, ?, ?, ?)";
    }

    /**
     * {@inheritDoc}
     *
     * <p>SQLite usa {@code INTEGER PRIMARY KEY AUTOINCREMENT} y {@code UNIQUE} constraint.</p>
     */
    @Override
    protected String getCreateWebUsersTableSQL() {
        return """
        CREATE TABLE IF NOT EXISTS nb_web_users (
            id                   INTEGER     PRIMARY KEY AUTOINCREMENT,
            username             VARCHAR(32) NOT NULL UNIQUE,
            password_hash        VARCHAR(255) NOT NULL,
            ban_players          INTEGER     NOT NULL DEFAULT 0,
            unban_players        INTEGER     NOT NULL DEFAULT 0,
            delete_ban_history   INTEGER     NOT NULL DEFAULT 0,
            mute_players         INTEGER     NOT NULL DEFAULT 0,
            unmute_players       INTEGER     NOT NULL DEFAULT 0,
            delete_mute_history  INTEGER     NOT NULL DEFAULT 0,
            edit_accounts        INTEGER     NOT NULL DEFAULT 0,
            is_admin             INTEGER     NOT NULL DEFAULT 0,
            created_at           BIGINT      NOT NULL
        )
        """;
    }

    // =========================================================================
    // toString para logging
    // =========================================================================

    @Override
    public String toString() {
        return (
            "SQLiteDatabase{file='" +
            new File(plugin.getDataFolder(), fileName).getAbsolutePath() +
            "'}"
        );
    }
}
