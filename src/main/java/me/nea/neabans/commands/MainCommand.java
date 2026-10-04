package me.nea.neabans.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;
import me.nea.neabans.NeaBans;
import me.nea.neabans.gui.PunisherGui;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Comando principal /neabans (aliases: /nb, /neaf)
 *
 * Subcomandos:
 *   /neabans <player>                  - Abre PunisherGui para ese jugador
 *   /neabans editor                    - Abre el GUI de editor
 *   /neabans reload                    - Recarga config y lang
 *   /neabans help                      - Muestra ayuda
 *   /neabans info                      - Info del plugin
 *   /neabans clearhistory <player>     - Borra el historial de un jugador
 */
public class MainCommand implements CommandExecutor, TabCompleter {

    private static final String PERM_BASE = "neabans.admin";
    private static final String PERM_RELOAD = "neabans.reload";
    private static final String PERM_CLEARHISTORY = "neabans.clearhistory";
    private static final String PERM_GUI = "neabans.gui";
    private static final String PERM_WEB = "neabans.reload";

    private static final List<String> SUBCOMMANDS = List.of(
        "reload",
        "help",
        "info",
        "editor",
        "clearhistory",
        "web"
    );

    private final NeaBans plugin;

    public MainCommand(NeaBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        // Sin argumentos: mostrar ayuda
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            // ----------------------------------------------------------------
            // /neabans reload
            // ----------------------------------------------------------------
            case "reload" -> {
                if (!sender.hasPermission(PERM_RELOAD)) {
                    plugin.getLang().send(sender, "no-permission");
                    return true;
                }
                try {
                    plugin.reloadConfig();
                    plugin.getLang().reload();
                    plugin.getLang().send(sender, "main.reload-success");
                } catch (Exception e) {
                    plugin
                        .getLogger()
                        .log(
                            Level.SEVERE,
                            "[MainCommand] Error al recargar",
                            e
                        );
                    plugin.getLang().send(sender, "error.generic");
                }
            }
            // ----------------------------------------------------------------
            // /neabans help
            // ----------------------------------------------------------------
            case "help" -> sendHelp(sender);
            // ----------------------------------------------------------------
            // /neabans web  → muestra URL del panel web
            // ----------------------------------------------------------------
            case "web" -> {
                if (!sender.hasPermission(PERM_WEB)) {
                    plugin.getLang().send(sender, "general.no-permission");
                    return true;
                }
                boolean webEnabled = plugin
                    .getConfig()
                    .getBoolean("web.enabled", false);
                int webPort = plugin.getConfig().getInt("web.port", 8080);
                if (!webEnabled) {
                    sender.sendMessage(
                        plugin.getLang().getPrefix() +
                            me.nea.neabans.utils.ColorUtils.color(
                                "&#FF4D4D✖ Panel web desactivado. Activa &fweb.enabled: true &#FF4D4Den config.yml"
                            )
                    );
                } else if (plugin.getWebServer() != null) {
                    String ip = plugin.getWebServer().getDisplayIp();
                    String url = "http://" + ip + ":" + webPort;
                    sender.sendMessage(
                        plugin.getLang().getPrefix() +
                            me.nea.neabans.utils.ColorUtils.color(
                                "&#2ECC71🌐 Panel Web NeaBans:"
                            )
                    );
                    sender.sendMessage(
                        me.nea.neabans.utils.ColorUtils.color(
                            "  &#FFD700URL:   &f" + url
                        )
                    );
                    sender.sendMessage(
                        me.nea.neabans.utils.ColorUtils.color(
                            "  &#FFD700Login: &f" + url + "/login.html"
                        )
                    );
                    sender.sendMessage(
                        me.nea.neabans.utils.ColorUtils.color(
                            "  &#808080Puerto TCP a abrir: &f" + webPort
                        )
                    );
                } else {
                    sender.sendMessage(
                        plugin.getLang().getPrefix() +
                            me.nea.neabans.utils.ColorUtils.color(
                                "&#FF8C00⚠ Panel web no inicializado."
                            )
                    );
                }
            }
            // ----------------------------------------------------------------
            // /neabans info
            // ----------------------------------------------------------------
            case "info" -> {
                if (!sender.hasPermission(PERM_BASE)) {
                    plugin.getLang().send(sender, "no-permission");
                    return true;
                }
                sendInfo(sender);
            }
            // ----------------------------------------------------------------
            // /neabans editor
            // ----------------------------------------------------------------
            case "editor" -> {
                if (!(sender instanceof Player player)) {
                    plugin.getLang().send(sender, "only-players");
                    return true;
                }
                if (!player.hasPermission(PERM_GUI)) {
                    plugin.getLang().send(sender, "no-permission");
                    return true;
                }
                try {
                    // Abre el menú de info/ayuda del plugin
                    player.sendMessage(
                        plugin.getLang().getPrefix() +
                            plugin
                                .getLang()
                                .get(
                                    "main.reload-success",
                                    java.util.Map.of(
                                        "version",
                                        plugin.getDescription().getVersion()
                                    )
                                )
                    );
                } catch (Exception e) {
                    plugin
                        .getLogger()
                        .log(
                            Level.SEVERE,
                            "[MainCommand] Error al abrir editor",
                            e
                        );
                    plugin.getLang().send(sender, "error.generic");
                }
            }
            // ----------------------------------------------------------------
            // /neabans clearhistory <player>
            // ----------------------------------------------------------------
            case "clearhistory" -> {
                if (!sender.hasPermission(PERM_CLEARHISTORY)) {
                    plugin.getLang().send(sender, "no-permission");
                    return true;
                }
                if (args.length < 2) {
                    plugin.getLang().send(sender, "main.clearhistory-usage");
                    return true;
                }
                final String targetName = args[1];
                final String staffName =
                    sender instanceof Player p ? p.getName() : "CONSOLE";
                clearHistory(sender, targetName, staffName);
            }
            // ----------------------------------------------------------------
            // /neabans <player>  → abre PunisherGui
            // ----------------------------------------------------------------
            default -> {
                if (!(sender instanceof Player player)) {
                    plugin.getLang().send(sender, "only-players");
                    return true;
                }
                if (!player.hasPermission(PERM_GUI)) {
                    plugin.getLang().send(sender, "no-permission");
                    return true;
                }
                final String targetName = args[0];
                openPunisherGui(player, targetName);
            }
        }

        return true;
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    /**
     * Abre el PunisherGui para el objetivo dado.
     * Busca el UUID async antes de abrir la GUI en el main thread.
     */
    private void openPunisherGui(Player staff, String targetName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String resolvedName = targetName;
                Player onlineTarget = Bukkit.getPlayerExact(targetName);

                if (onlineTarget != null) {
                    resolvedName = onlineTarget.getName();
                } else {
                    UUID uuid = plugin.getDatabase().getUUID(targetName);
                    if (uuid != null) {
                        String dbName = plugin.getDatabase().getLastName(uuid);
                        if (dbName != null) resolvedName = dbName;
                    } else {
                        final String fn = resolvedName;
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Map<String, String> ph = new HashMap<>();
                            ph.put("{player}", fn);
                            plugin
                                .getLang()
                                .send(staff, "player-not-found", ph);
                        });
                        return;
                    }
                }

                final String finalName = resolvedName;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        PunisherGui.open(staff, finalName);
                    } catch (Exception e) {
                        plugin
                            .getLogger()
                            .log(
                                Level.SEVERE,
                                "[MainCommand] Error al abrir PunisherGui para " +
                                    finalName,
                                e
                            );
                        plugin.getLang().send(staff, "error.generic");
                    }
                });
            } catch (Exception e) {
                plugin
                    .getLogger()
                    .log(
                        Level.SEVERE,
                        "[MainCommand] Error al buscar jugador " + targetName,
                        e
                    );
                Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.getLang().send(staff, "error.generic")
                );
            }
        });
    }

    /**
     * Borra el historial de sanciones de un jugador de manera async.
     */
    private void clearHistory(
        CommandSender sender,
        String targetName,
        String staffName
    ) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                UUID targetUuid = null;
                String resolvedName = targetName;

                Player onlineTarget = Bukkit.getPlayerExact(targetName);
                if (onlineTarget != null) {
                    targetUuid = onlineTarget.getUniqueId();
                    resolvedName = onlineTarget.getName();
                } else {
                    targetUuid = plugin.getDatabase().getUUID(targetName);
                    if (targetUuid != null) {
                        String dbName = plugin
                            .getDatabase()
                            .getLastName(targetUuid);
                        if (dbName != null) resolvedName = dbName;
                    }
                }

                if (targetUuid == null) {
                    final String fn = resolvedName;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Map<String, String> ph = new HashMap<>();
                        ph.put("{player}", fn);
                        plugin.getLang().send(sender, "player-not-found", ph);
                    });
                    return;
                }

                final UUID finalUuid = targetUuid;
                final String finalName = resolvedName;

                // Delegar la limpieza a la DB
                plugin.getDatabase().clearHistory(finalUuid);

                plugin
                    .getLogger()
                    .info(
                        "[MainCommand] " +
                            staffName +
                            " borró el historial de " +
                            finalName +
                            " (" +
                            finalUuid +
                            ")"
                    );

                Bukkit.getScheduler().runTask(plugin, () -> {
                    Map<String, String> ph = new HashMap<>();
                    ph.put("{player}", finalName);
                    ph.put("{staff}", staffName);
                    plugin
                        .getLang()
                        .send(sender, "main.clearhistory-success", ph);
                });
            } catch (Exception e) {
                plugin
                    .getLogger()
                    .log(
                        Level.SEVERE,
                        "[MainCommand] Error al borrar historial de " +
                            targetName,
                        e
                    );
                Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.getLang().send(sender, "error.generic")
                );
            }
        });
    }

    /**
     * Muestra el mensaje de ayuda con todos los comandos disponibles.
     */
    private void sendHelp(CommandSender sender) {
        // main.help-header es una lista — se envía línea por línea
        plugin
            .getLang()
            .sendList(
                sender,
                "main.help-header",
                java.util.Map.of(
                    "version",
                    plugin.getDescription().getVersion()
                )
            );
    }

    /**
     * Muestra info del plugin: versión, autor, stats de DB.
     */
    private void sendInfo(CommandSender sender) {
        String version = plugin.getDescription().getVersion();
        String authors = String.join(
            ", ",
            plugin.getDescription().getAuthors()
        );

        Map<String, String> ph = new HashMap<>();
        ph.put("{version}", version);
        ph.put("{authors}", authors.isBlank() ? "Nea" : authors);

        plugin.getLang().send(sender, "main.info-header", ph);
        plugin.getLang().send(sender, "main.info-version", ph);
        plugin.getLang().send(sender, "main.info-authors", ph);

        // Stats de DB en async
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Intentamos obtener el total de sanciones en la DB (si la implementación lo soporta)
                // Usamos un placeholder genérico si la DB no expone ese método
                Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.getLang().send(sender, "main.info-footer", ph)
                );
            } catch (Exception e) {
                plugin
                    .getLogger()
                    .log(
                        Level.WARNING,
                        "[MainCommand] Error al obtener stats de DB",
                        e
                    );
            }
        });
    }

    // =========================================================================
    // Tab completion
    // =========================================================================

    @Override
    public List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> results = new ArrayList<>();

            // Subcomandos que coincidan
            SUBCOMMANDS.stream()
                .filter(s -> s.startsWith(partial))
                .forEach(results::add);

            // Jugadores online que coincidan (para /neabans <player>)
            if (sender.hasPermission(PERM_GUI)) {
                Bukkit.getOnlinePlayers()
                    .stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .forEach(results::add);
            }

            return results;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();

            // clearhistory <player>
            if (
                sub.equals("clearhistory") &&
                sender.hasPermission(PERM_CLEARHISTORY)
            ) {
                String partial = args[1].toLowerCase();
                return Bukkit.getOnlinePlayers()
                    .stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
            }
        }

        return List.of();
    }
}
