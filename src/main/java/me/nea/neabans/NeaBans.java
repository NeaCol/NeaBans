package me.nea.neabans;

import java.util.Objects;
import java.util.logging.Level;
import me.nea.neabans.cache.PunishmentCache;
import me.nea.neabans.commands.*;
import me.nea.neabans.config.LangManager;
import me.nea.neabans.database.Database;
import me.nea.neabans.database.MySQLDatabase;
import me.nea.neabans.database.SQLiteDatabase;
import me.nea.neabans.listeners.ChatListener;
import me.nea.neabans.listeners.GuiListener;
import me.nea.neabans.listeners.LoginListener;
import me.nea.neabans.manager.DiscordWebhookManager;
import me.nea.neabans.manager.NetworkManager;
import me.nea.neabans.manager.PunishmentManager;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

/**
 *  ███╗   ██╗███████╗ █████╗     ██████╗  █████╗ ███╗   ██╗███████╗
 *  ████╗  ██║██╔════╝██╔══██╗    ██╔══██╗██╔══██╗████╗  ██║██╔════╝
 *  ██╔██╗ ██║█████╗  ███████║    ██████╔╝███████║██╔██╗ ██║███████╗
 *  ██║╚██╗██║██╔══╝  ██╔══██║    ██╔══██╗██╔══██║██║╚██╗██║╚════██║
 *  ██║ ╚████║███████╗██║  ██║    ██████╔╝██║  ██║██║ ╚████║███████║
 *  ╚═╝  ╚═══╝╚══════╝╚═╝  ╚═╝   ╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═══╝╚══════╝
 *
 * <p>Plugin de sanciones completo para redes grandes de Minecraft.</p>
 * <ul>
 *   <li>Sistema nativo: ban, tempban, ipban, mute, tempmute, kick, warn</li>
 *   <li>BD dual: MySQL (HikariCP) + SQLite (WAL)</li>
 *   <li>Caché en memoria: ConcurrentHashMap con TTL</li>
 *   <li>Panel web embebido con JWT + BCrypt</li>
 *   <li>Notificaciones Discord Webhook</li>
 *   <li>Sincronización de red: BungeeCord plugin messaging</li>
 *   <li>Multi-idioma: carpeta langs/</li>
 * </ul>
 *
 * @author Nea#9654
 * @version 2.0.0
 */
public final class NeaBans extends JavaPlugin {

    private static NeaBans instance;

    // ─── Subsistemas ──────────────────────────────────
    private Database database;
    private PunishmentCache cache;
    private LangManager lang;
    private PunishmentManager punishmentManager;
    private NetworkManager networkManager;
    private DiscordWebhookManager discordWebhook;
    private me.nea.neabans.web.WebServer webServer;

    // ─────────────────────────────────────────────────
    //  ENABLE
    // ─────────────────────────────────────────────────

    @Override
    public void onEnable() {
        instance = this;
        printBanner();

        saveDefaultConfig();

        // 1. Idiomas
        lang = new LangManager(this);
        getLogger().info(
            "Idioma: " +
                getConfig().getString("settings.language", "es").toUpperCase()
        );

        // 2. Base de datos
        if (!initDatabase()) {
            getLogger().severe(
                "No se pudo conectar a la BD. Deshabilitando NeaBans."
            );
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 3. Caché en memoria (sin dependencias, siempre disponible)
        cache = new PunishmentCache();

        // 4. Managers
        punishmentManager = new PunishmentManager(this);
        networkManager = new NetworkManager(this);
        discordWebhook = new DiscordWebhookManager(this);
        networkManager.register();

        // 4.5 Panel web embebido
        webServer = new me.nea.neabans.web.WebServer(this);
        webServer.start();

        // 5. Listeners
        getServer()
            .getPluginManager()
            .registerEvents(new LoginListener(this), this);
        getServer()
            .getPluginManager()
            .registerEvents(new ChatListener(this), this);
        getServer()
            .getPluginManager()
            .registerEvents(new GuiListener(this), this);

        // 6. Comandos
        registerCommands();

        // 7. Tareas periódicas
        // Limpiar sanciones expiradas en BD cada 5 minutos
        getServer()
            .getScheduler()
            .runTaskTimerAsynchronously(
                this,
                () -> database.cleanExpired(),
                20L * 30,
                20L * 60 * 5
            );

        // Evict de entradas expiradas en caché cada 2 minutos (evitar memory leaks)
        getServer()
            .getScheduler()
            .runTaskTimerAsynchronously(
                this,
                () -> cache.evictExpired(),
                20L * 60,
                20L * 60 * 2
            );

        getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        getLogger().info(
            "  NeaBans v" + getDescription().getVersion() + " habilitado."
        );
        getLogger().info(
            "  BD: " +
                getConfig().getString("database.type", "sqlite").toUpperCase()
        );
        getLogger().info(
            "  Red: " +
                (networkManager.isEnabled() ? "HABILITADA" : "Deshabilitada")
        );
        getLogger().info(
            "  Discord: " +
                (discordWebhook.isEnabled() ? "HABILITADO" : "Deshabilitado")
        );
        getLogger().info("  Caché: " + cache.stats());
        getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    // ─────────────────────────────────────────────────
    //  DISABLE — sin memory leaks
    // ─────────────────────────────────────────────────

    @Override
    public void onDisable() {
        // Cancelar todas las tareas pendientes del plugin
        getServer().getScheduler().cancelTasks(this);

        // Detener panel web (cierra sesiones WebSocket y el servidor HTTP)
        if (webServer != null) webServer.stop();

        // Desregistrar canales de red
        if (networkManager != null) networkManager.unregister();

        // Limpiar caché (libera referencias y permite GC)
        if (cache != null) cache.clear();

        // Cerrar pool de conexiones de BD
        if (database != null) database.disconnect();

        instance = null; // evitar reference leak en el singleton
        getLogger().info("NeaBans deshabilitado correctamente.");
    }

    // ─────────────────────────────────────────────────
    //  DATABASE
    // ─────────────────────────────────────────────────

    private boolean initDatabase() {
        String type = getConfig()
            .getString("database.type", "sqlite")
            .toLowerCase();
        try {
            if ("mysql".equals(type)) {
                database = new MySQLDatabase(
                    this,
                    getConfig().getString("database.mysql.host", "localhost"),
                    getConfig().getInt("database.mysql.port", 3306),
                    getConfig().getString("database.mysql.database", "neabans"),
                    getConfig().getString("database.mysql.username", "root"),
                    getConfig().getString("database.mysql.password", ""),
                    getConfig().getInt("database.mysql.pool-size", 10)
                );
            } else {
                database = new SQLiteDatabase(
                    this,
                    getConfig().getString("database.sqlite.file", "neabans.db")
                );
            }
            database.connect();
            database.createTables();
            getLogger().info(
                "BD [" + type.toUpperCase() + "] conectada correctamente."
            );
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error al inicializar la BD", e);
            return false;
        }
    }

    // ─────────────────────────────────────────────────
    //  COMANDOS
    // ─────────────────────────────────────────────────

    private void registerCommands() {
        // Comando principal
        MainCommand main = new MainCommand(this);
        PluginCommand nbCmd = Objects.requireNonNull(getCommand("neabans"));
        nbCmd.setExecutor(main);
        nbCmd.setTabCompleter(main);

        // Sanciones
        bind("ban", new BanCommand(this));
        bind("tempban", new TempBanCommand(this));
        bind("unban", new UnbanCommand(this));
        bind("mute", new MuteCommand(this));
        bind("tempmute", new TempMuteCommand(this));
        bind("unmute", new UnmuteCommand(this));
        bind("kick", new KickCommand(this));
        bind("warn", new WarnCommand(this));
        bind("history", new HistoryCommand(this));

        // IP-Ban
        bind("ipban", new IpBanCommand(this, false));
        bind("iptempban", new IpBanCommand(this, true));
        bind("ipunban", new IpUnbanCommand(this));

        // Lookup
        bind("lookup", new LookupCommand(this));
    }

    private <T extends CommandExecutor & TabCompleter> void bind(
        String name,
        T handler
    ) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Comando /" + name + " no está en plugin.yml");
            return;
        }
        cmd.setExecutor(handler);
        cmd.setTabCompleter(handler);
    }

    // ─────────────────────────────────────────────────
    //  BANNER
    // ─────────────────────────────────────────────────

    private void printBanner() {
        getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        getLogger().info(" ███╗   ██╗███████╗ █████╗     ██████╗  ███████╗");
        getLogger().info(" ████╗  ██║██╔════╝██╔══██╗    ██╔══██╗██╔════╝ ");
        getLogger().info(" ██╔██╗ ██║█████╗  ███████║    ██████╔╝███████╗  ");
        getLogger().info(" ██║╚██╗██║██╔══╝  ██╔══██║    ██╔══██╗╚════██║  ");
        getLogger().info(" ██║ ╚████║███████╗██║  ██║    ██████╔╝███████║  ");
        getLogger().info(" ╚═╝  ╚═══╝╚══════╝╚═╝  ╚═╝   ╚═════╝ ╚══════╝  ");
        getLogger().info("  v2.0.0 | Paper 1.21+ | by Nea#9654");
        getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    // ─────────────────────────────────────────────────
    //  GETTERS
    // ─────────────────────────────────────────────────

    public static NeaBans getInstance() {
        return instance;
    }

    public Database getDatabase() {
        return database;
    }

    public PunishmentCache getCache() {
        return cache;
    }

    public PunishmentManager getPunishmentManager() {
        return punishmentManager;
    }

    public LangManager getLang() {
        return lang;
    }

    public NetworkManager getNetworkManager() {
        return networkManager;
    }

    public DiscordWebhookManager getDiscordWebhook() {
        return discordWebhook;
    }

    public me.nea.neabans.web.WebServer getWebServer() {
        return webServer;
    }
}
