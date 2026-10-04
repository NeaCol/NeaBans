package me.nea.neabans.commands;

import me.nea.neabans.NeaBans;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Comando /unban <jugador>
 * Remueve el ban activo de un jugador (puede estar offline).
 */
public class UnbanCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "neabans.unban";

    private final NeaBans plugin;

    public UnbanCommand(NeaBans plugin) {
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
            plugin.getLang().send(sender, "unban.usage");
            return true;
        }

        final String targetName = args[0];
        final String staffName = sender instanceof Player p ? p.getName() : "CONSOLE";

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Para unban es válido buscar solo en DB (puede estar offline)
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

                boolean success = plugin.getPunishmentManager().unban(staffName, finalUuid);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    Map<String, String> ph = new HashMap<>();
                    ph.put("{player}", finalName);
                    ph.put("{staff}", staffName);

                    if (success) {
                        plugin.getLang().send(sender, "unban.success", ph);
                        plugin.getLang().broadcast("unban.broadcast", ph);
                    } else {
                        plugin.getLang().send(sender, "unban.not-banned", ph);
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[UnbanCommand] Error al desbanear a " + targetName, e);
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
