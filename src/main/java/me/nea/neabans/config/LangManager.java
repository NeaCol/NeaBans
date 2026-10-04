package me.nea.neabans.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import me.nea.neabans.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Gestor de mensajes del plugin cargados desde lang.yml.
 *
 * Carga el archivo lang.yml del dataFolder del plugin (copiando el recurso
 * por defecto si aún no existe). Soporta:
 *  - Mensajes simples con colores HEX y códigos &
 *  - Listas de mensajes
 *  - Placeholders {key} -> valor
 *  - Envío con prefijo a CommandSender y broadcast a todos los jugadores online
 */
public final class LangManager {

    private final JavaPlugin plugin;
    private File langFile;
    private FileConfiguration lang;

    // ─────────────────────────────────────────
    //  Constructor
    // ─────────────────────────────────────────

    /**
     * Inicializa el LangManager y carga el lang.yml.
     *
     * @param plugin Instancia del plugin principal
     */
    public LangManager(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    // ─────────────────────────────────────────
    //  Carga / Recarga
    // ─────────────────────────────────────────

    /**
     * Carga el lang.yml. Si no existe en el dataFolder, copia el recurso por defecto.
     */
    /**
     * Carga el archivo de idioma desde la carpeta langs/ del dataFolder.
     * El idioma se lee de config.yml en la clave settings.language (default: es).
     * Si el archivo no existe lo extrae del JAR.
     */
    private void load() {
        String language = plugin
            .getConfig()
            .getString("settings.language", "es");
        File langsDir = new File(plugin.getDataFolder(), "langs");
        if (!langsDir.exists()) langsDir.mkdirs();

        langFile = new File(langsDir, language + ".yml");

        // Si no existe en el dataFolder, intentar copiar desde el JAR
        if (!langFile.exists()) {
            String resourcePath = "langs/" + language + ".yml";
            try (var stream = plugin.getResource(resourcePath)) {
                if (stream != null) {
                    plugin.saveResource(resourcePath, false);
                } else {
                    // Fallback a español si el idioma no existe en el JAR
                    plugin
                        .getLogger()
                        .warning(
                            "[LangManager] Idioma '" +
                                language +
                                "' no encontrado. Usando 'es'."
                        );
                    langFile = new File(langsDir, "es.yml");
                    if (!langFile.exists()) {
                        plugin.saveResource("langs/es.yml", false);
                    }
                }
            } catch (java.io.IOException e) {
                plugin
                    .getLogger()
                    .warning(
                        "[LangManager] Error al copiar lang: " + e.getMessage()
                    );
            }
        }

        lang = YamlConfiguration.loadConfiguration(langFile);

        // Merging con defaults del JAR para keys nuevas
        String resourcePath = "langs/" + language + ".yml";
        var defStream = plugin.getResource(resourcePath);
        if (defStream == null) defStream = plugin.getResource("langs/es.yml");
        if (defStream != null) {
            var defConfig = YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(
                    defStream,
                    java.nio.charset.StandardCharsets.UTF_8
                )
            );
            lang.setDefaults(defConfig);
        }
    }

    /**
     * Recarga el lang.yml desde disco.
     */
    public void reload() {
        load();
        plugin
            .getLogger()
            .info(
                "[LangManager] Idioma recargado: " +
                    plugin.getConfig().getString("settings.language", "es")
            );
    }

    // ─────────────────────────────────────────
    //  get / getList
    // ─────────────────────────────────────────

    /**
     * Obtiene un mensaje del lang.yml con los colores ya aplicados.
     *
     * @param key Ruta YAML (p.ej. "general.no-permission")
     * @return Mensaje coloreado, o la key entre &lt;&gt; si no existe
     */
    public String get(String key) {
        String value = lang.getString(key);
        if (value == null) {
            plugin
                .getLogger()
                .log(
                    Level.WARNING,
                    "[LangManager] Clave no encontrada: {0}",
                    key
                );
            return ColorUtils.color("&#FF4D4D<" + key + ">");
        }
        return ColorUtils.color(value);
    }

    /**
     * Obtiene un mensaje con colores y placeholders aplicados.
     *
     * @param key          Ruta YAML
     * @param placeholders Mapa {clave} -> valor
     * @return Mensaje coloreado con placeholders reemplazados
     */
    public String get(String key, Map<String, String> placeholders) {
        String value = lang.getString(key);
        if (value == null) {
            plugin
                .getLogger()
                .log(
                    Level.WARNING,
                    "[LangManager] Clave no encontrada: {0}",
                    key
                );
            return ColorUtils.color("&#FF4D4D<" + key + ">");
        }
        return ColorUtils.color(value, placeholders);
    }

    /**
     * Obtiene una lista de mensajes con colores aplicados.
     *
     * @param key Ruta YAML
     * @return Lista coloreada, o lista vacía si no existe
     */
    public List<String> getList(String key) {
        List<String> list = lang.getStringList(key);
        if (list.isEmpty() && !lang.contains(key)) {
            plugin
                .getLogger()
                .log(
                    Level.WARNING,
                    "[LangManager] Lista no encontrada: {0}",
                    key
                );
            return new ArrayList<>();
        }
        return ColorUtils.colorList(list);
    }

    /**
     * Obtiene una lista de mensajes con colores y placeholders aplicados.
     *
     * @param key          Ruta YAML
     * @param placeholders Mapa {clave} -> valor
     * @return Lista coloreada con placeholders reemplazados
     */
    public List<String> getList(String key, Map<String, String> placeholders) {
        List<String> list = lang.getStringList(key);
        if (list.isEmpty() && !lang.contains(key)) {
            plugin
                .getLogger()
                .log(
                    Level.WARNING,
                    "[LangManager] Lista no encontrada: {0}",
                    key
                );
            return new ArrayList<>();
        }
        return ColorUtils.colorList(list, placeholders);
    }

    // ─────────────────────────────────────────
    //  Prefix
    // ─────────────────────────────────────────

    /**
     * Devuelve el prefijo del plugin definido en lang.yml (con colores aplicados).
     *
     * @return Prefijo coloreado
     */
    public String getPrefix() {
        return ColorUtils.color(
            lang.getString("prefix", "&#FF4D4D&lNeaBans &#4E5C5E» &r")
        );
    }

    // ─────────────────────────────────────────
    //  send
    // ─────────────────────────────────────────

    /**
     * Envía un mensaje (prefijo + contenido) a un CommandSender.
     *
     * @param sender Destinatario
     * @param key    Clave del mensaje en lang.yml
     */
    public void send(CommandSender sender, String key) {
        sender.sendMessage(getPrefix() + get(key));
    }

    /**
     * Envía un mensaje con placeholders (prefijo + contenido) a un CommandSender.
     *
     * @param sender       Destinatario
     * @param key          Clave del mensaje en lang.yml
     * @param placeholders Mapa {clave} -> valor
     */
    public void send(
        CommandSender sender,
        String key,
        Map<String, String> placeholders
    ) {
        sender.sendMessage(getPrefix() + get(key, placeholders));
    }

    /**
     * Envía una lista de mensajes a un CommandSender (sin prefijo, útil para headers).
     *
     * @param sender Destinatario
     * @param key    Clave de la lista en lang.yml
     */
    public void sendList(CommandSender sender, String key) {
        getList(key).forEach(sender::sendMessage);
    }

    /**
     * Envía una lista de mensajes con placeholders a un CommandSender.
     *
     * @param sender       Destinatario
     * @param key          Clave de la lista en lang.yml
     * @param placeholders Mapa {clave} -> valor
     */
    public void sendList(
        CommandSender sender,
        String key,
        Map<String, String> placeholders
    ) {
        getList(key, placeholders).forEach(sender::sendMessage);
    }

    // ─────────────────────────────────────────
    //  broadcast
    // ─────────────────────────────────────────

    /**
     * Envía un mensaje (prefijo + contenido) a todos los jugadores online y a la consola.
     *
     * @param key          Clave del mensaje en lang.yml
     * @param placeholders Mapa {clave} -> valor
     */
    public void broadcast(String key, Map<String, String> placeholders) {
        String message = getPrefix() + get(key, placeholders);
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(message));
        Bukkit.getConsoleSender().sendMessage(message);
    }

    /**
     * Envía un mensaje a todos los jugadores online y a la consola.
     *
     * @param key Clave del mensaje en lang.yml
     */
    public void broadcast(String key) {
        String message = getPrefix() + get(key);
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(message));
        Bukkit.getConsoleSender().sendMessage(message);
    }

    /**
     * Envía un mensaje a todos los jugadores con un permiso específico.
     *
     * @param key          Clave del mensaje en lang.yml
     * @param placeholders Mapa {clave} -> valor
     * @param permission   Permiso requerido para recibir el mensaje
     */
    public void broadcastPermission(
        String key,
        Map<String, String> placeholders,
        String permission
    ) {
        String message = getPrefix() + get(key, placeholders);
        Bukkit.getOnlinePlayers()
            .stream()
            .filter(p -> p.hasPermission(permission))
            .forEach(p -> p.sendMessage(message));
        Bukkit.getConsoleSender().sendMessage(message);
    }

    // ─────────────────────────────────────────
    //  Acceso interno a la configuración
    // ─────────────────────────────────────────

    /**
     * Devuelve la FileConfiguration subyacente.
     * Útil si otra clase necesita acceder directamente a valores del lang.yml.
     *
     * @return FileConfiguration de lang.yml
     */
    public FileConfiguration getConfig() {
        return lang;
    }

    /**
     * Guarda los cambios realizados en memoria al archivo lang.yml en disco.
     */
    public void save() {
        try {
            lang.save(langFile);
        } catch (IOException e) {
            plugin
                .getLogger()
                .log(
                    Level.SEVERE,
                    "[LangManager] No se pudo guardar lang.yml",
                    e
                );
        }
    }
}
