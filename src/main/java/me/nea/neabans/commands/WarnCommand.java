package me.nea.neabans.commands;

import me.nea.neabans.NeaBans;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Comando /warn <jugador> [razón...]
 * Advierte a un jugador y lleva registro en el historial.
 * Si llega al máximo de advertencias, ejecuta la acción configurada.
 */
public class WarnCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "neabans.warn";
    private static final String DEFAULT_REASON = "No especificada";

    private final NeaBans plugin;

    public WarnCommand(NeaBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!sender.hasPermission(PERMISSION)) {
            plugin.getLang().send(sender, "no-permission");
            return true;
        }

        if (args.length < 1) {
            plugin.getLang().send(sender, "warn.usage");
            return true;
        }

        final String targetName = args[0];
        final String reason = args.length > 1
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : DEFAULT_REASON;

        final String staffName = sender instanceof Player p ? p.getName() : "CONSOLE";
        final UUID staffUuid = sender instanceof Player p ? p.getUniqueId() : null;

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
                        String dbName = plugin.getDatabase().getLastName(targetUuid);
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

                int id = plugin.getPunishmentManager().warn(
                        staffUuid, staffName,
                        finalUuid, finalName,
                        reason, null, 0
                );

                // Contar advertencias totales del jugador (filtrando solo WARNs del historial)
                int totalWarns = 0;
                try {
                    int historyCount = plugin.getDatabase().getHistoryCount(finalUuid);
                    // Contamos página a página hasta tener todos los warns
                    int perPage = 50;
                    int pages = (int) Math.ceil((double) historyCount / perPage);
                    for (int i = 1; i <= pages; i++) {
                        List<me.nea.neabans.model.Punishment> page =
                                plugin.getDatabase().getHistory(finalUuid, i, perPage);
                        for (me.nea.neabans.model.Punishment p : page) {
                            if (p.getType() == me.nea.neabans.model.PunishmentType.WARN) {
                                totalWarns++;
                            }
                        }
                    }
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING,
                            "[WarnCommand] No se pudo contar advertencias de " + finalName, ex);
                }

                final int finalTotalWarns = totalWarns;
                final int maxWarns = plugin.getConfig().getInt("settings.max-warns", 0);
                final String warnAction = plugin.getConfig().getString("settings.max-warns-action", "");

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (id == -1) {
                        // Aunque warn no debería retornar -1, manejarlo por robustez
                        plugin.getLang().send(sender, "error.generic");
                        return;
                    }

                    Map<String, String> ph = new HashMap<>();
                    ph.put("{player}", finalName);
                    ph.put("{reason}", reason);
                    ph.put("{staff}", staffName);
                    ph.put("{id}", String.valueOf(id));
                    ph.put("{warns}", String.valueOf(finalTotalWarns));
                    ph.put("{max}", String.valueOf(maxWarns));

                    // Notificar al jugador advertido si está online
                    Player online = Bukkit.getPlayerExact(finalName);
                    if (online != null) {
                        plugin.getLang().send(online, "warn.notify-target", ph);
                    }

                    plugin.getLang().send(sender, "warn.success", ph);
                    plugin.getLang().broadcast("warn.broadcast", ph);

                    // Verificar si llega al máximo de advertencias
                    if (maxWarns > 0 && finalTotalWarns >= maxWarns && !warnAction.isBlank()) {
                        String actionResolved = warnAction.replace("{player}", finalName);
                        plugin.getLogger().info("[WarnCommand] " + finalName
                                + " alcanzó " + finalTotalWarns + " warns. Ejecutando: " + actionResolved);
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), actionResolved);
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[WarnCommand] Error al advertir a " + targetName, e);
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.getLang().send(sender, "error.generic"));
            }
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String label,
                                      @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) return List.of();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
