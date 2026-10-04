package me.nea.neabans.proxy.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import me.nea.neabans.proxy.shared.ProxyDatabase;
import org.yaml.snakeyaml.Yaml;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Clase principal del plugin NeaBansProxy para <b>Velocity</b>.
 *
 * <p>Velocity descubre este plugin gracias a la anotación {@link Plugin} que genera
 * un {@code velocity-plugin.json} en tiempo de compilación (via
 * {@code velocity-annotationprocessor}).</p>
 *
 * <h2>Flujo de arranque</h2>
 * <ol>
 *   <li>{@code @Inject} inyecta {@link ProxyServer}, {@link Logger} y el directorio de datos.</li>
 *   <li>En {@link #onProxyInitialize}: copia config.yml si no existe, lo lee con SnakeYAML.</li>
 *   <li>Inicializa {@link ProxyDatabase} y registra {@link VelocityLoginListener}.</li>
 * </ol>
 */
@Plugin(
        id          = "neabansproxy",
        name        = "NeaBansProxy",
        version     = "2.0.0",
        description = "Plugin de proxy para NeaBans - bloqueo de bans a nivel de red",
        authors     = {"Nea"}
)
public final class NeaBansProxyVelocity {

    // =========================================================================
    // Inyecciones de Velocity
    // =========================================================================

    private final ProxyServer server;
    private final Logger      logger;
    private final Path        dataDirectory;

    // =========================================================================
    // Estado interno
    // =========================================================================

    private ProxyDatabase database;
    private String        appealLink;
    private String        banScreenPermanent;
    private String        banScreenTemp;
    private String        ipBanScreen;

    // =========================================================================
    // Constructor inyectado
    // =========================================================================

    @Inject
    public NeaBansProxyVelocity(
            ProxyServer  server,
            Logger       logger,
            @DataDirectory Path dataDirectory
    ) {
        this.server        = server;
        this.logger        = logger;
        this.dataDirectory = dataDirectory;
    }

    // =========================================================================
    // Ciclo de vida
    // =========================================================================

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        printBanner();

        // 1. Copiar config.yml por defecto si no existe
        saveDefaultConfig();

        // 2. Leer configuración con SnakeYAML (shadeado)
        Map<String, Object> config = loadConfig();
        if (config == null) {
            logger.severe("[NeaBansProxy] No se pudo leer config.yml. El plugin no se activará.");
            return;
        }

        // Valores de base de datos
        Map<String, Object> dbSection = getSection(config, "database");
        String host   = getString(dbSection, "host",         "localhost");
        int    port   = getInt   (dbSection, "port",         3306);
        String db     = getString(dbSection, "database",     "neabans");
        String user   = getString(dbSection, "username",     "root");
        String pass   = getString(dbSection, "password",     "password");
        int    pool   = getInt   (dbSection, "pool-size",    5);
        String prefix = getString(dbSection, "table-prefix", "nb_");

        // Valores de settings
        Map<String, Object> settings = getSection(config, "settings");
        appealLink         = getString(settings, "appeal-link",          "https://discord.gg/YcjMtCPuKH");
        banScreenPermanent = getString(settings, "ban-screen-permanent",  null);
        banScreenTemp      = getString(settings, "ban-screen-temp",       null);
        ipBanScreen        = getString(settings, "ip-ban-screen",         null);

        // 3. Inicializar base de datos
        database = new ProxyDatabase(host, port, db, user, pass, pool, prefix);
        try {
            database.connect();
            logger.info("[NeaBansProxy] Conexión a MySQL establecida correctamente.");
        } catch (Exception e) {
            logger.log(Level.SEVERE,
                    "[NeaBansProxy] Error al conectar con MySQL. El plugin no bloqueará logins.", e);
            // Fail-open: el servidor sigue funcionando aunque no haya BD
        }

        // 4. Registrar listener de login
        server.getEventManager().register(this, new VelocityLoginListener(this));

        logger.info("[NeaBansProxy] ¡Plugin activado! Versión 2.0.0");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (database != null && database.isConnected()) {
            database.disconnect();
        }
        logger.info("[NeaBansProxy] Plugin desactivado. Pool MySQL cerrado.");
    }

    // =========================================================================
    // Config helpers
    // =========================================================================

    private void saveDefaultConfig() {
        try {
            Files.createDirectories(dataDirectory);
            Path configPath = dataDirectory.resolve("config.yml");
            if (!Files.exists(configPath)) {
                try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                    if (in != null) {
                        Files.copy(in, configPath);
                        logger.info("[NeaBansProxy] config.yml creado en " + configPath);
                    }
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING,
                    "[NeaBansProxy] No se pudo crear el directorio de datos: " + e.getMessage(), e);
        }
    }

    /**
     * Carga el {@code config.yml} con SnakeYAML (shadeado y relocado).
     *
     * @return Mapa raíz del YAML, o {@code null} si falla la lectura.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> loadConfig() {
        Path configPath = dataDirectory.resolve("config.yml");
        try (InputStream in = Files.newInputStream(configPath)) {
            Yaml yaml = new Yaml();
            return (Map<String, Object>) yaml.load(in);
        } catch (IOException e) {
            logger.log(Level.SEVERE,
                    "[NeaBansProxy] Error leyendo config.yml: " + e.getMessage(), e);
            return null;
        }
    }

    // ── Utilidades de navegación del mapa YAML ──────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> getSection(Map<String, Object> parent, String key) {
        if (parent == null) return Map.of();
        Object val = parent.get(key);
        if (val instanceof Map) return (Map<String, Object>) val;
        return Map.of();
    }

    private String getString(Map<String, Object> map, String key, String def) {
        if (map == null) return def;
        Object val = map.get(key);
        return val != null ? val.toString() : def;
    }

    private int getInt(Map<String, Object> map, String key, int def) {
        if (map == null) return def;
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val != null) {
            try { return Integer.parseInt(val.toString()); }
            catch (NumberFormatException ignored) {}
        }
        return def;
    }

    // =========================================================================
    // Banner ASCII
    // =========================================================================

    private void printBanner() {
        logger.info("  _   _            ____                   ");
        logger.info(" | \\ | | ___  __ _| __ )  __ _ _ __  ___ ");
        logger.info(" |  \\| |/ _ \\/ _` |  _ \\ / _` | '_ \\/ __|");
        logger.info(" | |\\  |  __/ (_| | |_) | (_| | | | \\__ \\");
        logger.info(" |_| \\_|\\___|\\__,_|____/ \\__,_|_| |_|___/");
        logger.info("   Proxy Edition v2.0.0  [Velocity]       ");
        logger.info("   github.com/nea / NeaBans                ");
    }

    // =========================================================================
    // Getters
    // =========================================================================

    public ProxyServer getServer()            { return server; }
    public Logger      getLogger()            { return logger; }
    public ProxyDatabase getDatabase()        { return database; }
    public String getAppealLink()             { return appealLink != null ? appealLink : "https://discord.gg/YcjMtCPuKH"; }
    public String getBanScreenPermanent()     { return banScreenPermanent; }
    public String getBanScreenTemp()          { return banScreenTemp; }
    public String getIpBanScreen()            { return ipBanScreen; }
}
