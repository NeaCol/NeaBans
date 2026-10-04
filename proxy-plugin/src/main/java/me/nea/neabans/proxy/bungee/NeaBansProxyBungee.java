package me.nea.neabans.proxy.bungee;

import me.nea.neabans.proxy.shared.ProxyDatabase;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.logging.Level;

/**
 * Clase principal del plugin NeaBansProxy para <b>BungeeCord</b>.
 *
 * <p>Se carga a través de {@code bungee.yml} cuando BungeeCord arranca.
 * Su única responsabilidad es conectar con la BD compartida y registrar
 * el {@link BungeeLoginListener} que intercepta los logins.</p>
 *
 * <h2>Flujo de arranque</h2>
 * <ol>
 *   <li>Copia {@code config.yml} al dataFolder si no existe.</li>
 *   <li>Lee la configuración con la API nativa de BungeeCord.</li>
 *   <li>Inicializa {@link ProxyDatabase} y ejecuta las migraciones SQL.</li>
 *   <li>Registra {@link BungeeLoginListener}.</li>
 * </ol>
 */
public final class NeaBansProxyBungee extends Plugin {

    // =========================================================================
    // Singleton
    // =========================================================================

    private static NeaBansProxyBungee instance;

    public static NeaBansProxyBungee getInstance() {
        return instance;
    }

    // =========================================================================
    // Estado interno
    // =========================================================================

    private ProxyDatabase database;

    /** URL de apelación mostrada en el ban screen. */
    private String appealLink;

    /** Plantillas de ban screen cargadas desde config.yml (pueden ser null). */
    private String banScreenPermanent;
    private String banScreenTemp;
    private String ipBanScreen;

    // =========================================================================
    // Ciclo de vida
    // =========================================================================

    @Override
    public void onEnable() {
        instance = this;
        printBanner();

        // 1. Crear dataFolder y copiar config.yml si no existe
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        saveDefaultConfig();

        // 2. Leer configuración
        Configuration config = loadConfig();
        if (config == null) {
            getLogger().severe("[NeaBansProxy] No se pudo leer config.yml. Desactivando.");
            return;
        }

        // Valores de base de datos
        String host   = config.getString("database.host",     "localhost");
        int    port   = config.getInt   ("database.port",     3306);
        String db     = config.getString("database.database", "neabans");
        String user   = config.getString("database.username", "root");
        String pass   = config.getString("database.password", "password");
        int    pool   = config.getInt   ("database.pool-size", 5);
        String prefix = config.getString("database.table-prefix", "nb_");

        // Valores de settings
        appealLink        = config.getString("settings.appeal-link",         "https://discord.gg/YcjMtCPuKH");
        banScreenPermanent = config.getString("settings.ban-screen-permanent", null);
        banScreenTemp      = config.getString("settings.ban-screen-temp",      null);
        ipBanScreen        = config.getString("settings.ip-ban-screen",        null);

        // 3. Inicializar base de datos
        database = new ProxyDatabase(host, port, db, user, pass, pool, prefix);
        try {
            database.connect();
            getLogger().info("[NeaBansProxy] Conexión a MySQL establecida correctamente.");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE,
                    "[NeaBansProxy] Error al conectar con MySQL. El plugin no bloqueará logins.", e);
            // Continúa sin BD — los jugadores podrán entrar (fail-open por seguridad del servidor)
        }

        // 4. Registrar listener de login
        getProxy().getPluginManager()
                  .registerListener(this, new BungeeLoginListener(this));

        getLogger().info("[NeaBansProxy] ¡Plugin activado! Versión 2.0.0");
    }

    @Override
    public void onDisable() {
        if (database != null && database.isConnected()) {
            database.disconnect();
        }
        getLogger().info("[NeaBansProxy] Plugin desactivado. Pool MySQL cerrado.");
    }

    // =========================================================================
    // Config helpers
    // =========================================================================

    /**
     * Copia el {@code config.yml} embebido al dataFolder si no existe ya.
     */
    private void saveDefaultConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            try (InputStream in = getResourceAsStream("config.yml")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath());
                    getLogger().info("[NeaBansProxy] config.yml creado en " + configFile.getPath());
                }
            } catch (IOException e) {
                getLogger().log(Level.WARNING,
                        "[NeaBansProxy] No se pudo copiar config.yml por defecto.", e);
            }
        }
    }

    /**
     * Carga el {@code config.yml} desde el dataFolder usando la API de BungeeCord.
     *
     * @return La {@link Configuration} cargada, o {@code null} en caso de error.
     */
    private Configuration loadConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        try {
            return ConfigurationProvider
                    .getProvider(YamlConfiguration.class)
                    .load(configFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE,
                    "[NeaBansProxy] Error leyendo config.yml: " + e.getMessage(), e);
            return null;
        }
    }

    // =========================================================================
    // Banner ASCII
    // =========================================================================

    private void printBanner() {
        getLogger().info("  _   _            ____                   ");
        getLogger().info(" | \\ | | ___  __ _| __ )  __ _ _ __  ___ ");
        getLogger().info(" |  \\| |/ _ \\/ _` |  _ \\ / _` | '_ \\/ __|");
        getLogger().info(" | |\\  |  __/ (_| | |_) | (_| | | | \\__ \\");
        getLogger().info(" |_| \\_|\\___|\\__,_|____/ \\__,_|_| |_|___/");
        getLogger().info("   Proxy Edition v2.0.0  [BungeeCord]     ");
        getLogger().info("   github.com/nea / NeaBans                ");
    }

    // =========================================================================
    // Getters
    // =========================================================================

    public ProxyDatabase getDatabase() {
        return database;
    }

    public String getAppealLink() {
        return appealLink != null ? appealLink : "https://discord.gg/YcjMtCPuKH";
    }

    public String getBanScreenPermanent() {
        return banScreenPermanent;
    }

    public String getBanScreenTemp() {
        return banScreenTemp;
    }

    public String getIpBanScreen() {
        return ipBanScreen;
    }
}
