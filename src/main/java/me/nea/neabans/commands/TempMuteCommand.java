package me.nea.neabans.commands;

import me.nea.neabans.NeaBans;
import me.nea.neabans.utils.TimeUtils;
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
 * Comando /tempmute <jugador> <duración> [razón...]
 * Silencia temporalmente a un jugador.
 */
public class TempMuteCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "neabans.tempmute";
    private static final String DEFAULT_REASON = "No especificada";

    private final NeaBans plugin;

    public TempMuteCommand(NeaBans plugin) {
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

        if (args.length < 2) {
            plugin.getLang().send(sender, "tempmute.usage");
            return true;
        }

        final String targetName = args[0];
        final String durationStr = args[1];
        final long durationMillis = TimeUtils.parse(durationStr);

        if (durationMillis == -1L) {
            Map<String, String> ph = new HashMap<>();
            ph.put("{input}", durationStr);
            plugin.getLang().send(sender, "invalid-duration", ph);
            return true;
        }

        final String reason = args.length > 2
                ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                : DEFAULT_REASON;

        final String staffName = sender instanceof Player p ? p.getName() : "CONSOLE";
        final UUID staffUuid = sender instanceof Player p ? p.getUniqueId() : null;
        final String formattedDuration = TimeUtils.format(durationMillis);

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

                if (plugin.getPunishmentManager().isMuted(finalUuid)) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Map<String, String> ph = new HashMap<>();
                        ph.put("{player}", finalName);
                        plugin.getLang().send(sender, "mute.already-muted", ph);
                    });
                    return;
                }

                int id = plugin.getPunishmentManager().tempMute(
                        staffUuid, staffName,
                        finalUuid, finalName,
                        durationMillis, reason, null, 0
                );

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (id == -1) {
                        Map<String, String> ph = new HashMap<>();
                        ph.put("{player}", finalName);
                        plugin.getLang().send(sender, "mute.already-muted", ph);
                        return;
                    }

                    Map<String, String> ph = new HashMap<>();
                    ph.put("{player}", finalName);
                    ph.put("{reason}", reason);
                    ph.put("{staff}", staffName);
                    ph.put("{id}", String.valueOf(id));
                    ph.put("{duration}", formattedDuration);

                    Player online = Bukkit.getPlayerExact(finalName);
                    if (online != null) {
                        plugin.getLang().send(online, "mute.notify-target", ph);
                    }

                    plugin.getLang().send(sender, "tempmute.success", ph);
                    plugin.getLang().broadcast("tempmute.broadcast", ph);
                });

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[TempMuteCommand] Error al temp-mutear a " + targetName, e);
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

        if (args.length == 2) {
            String partial = args[1].toLowerCase();
            return List.of("10m", "30m", "1h", "6h", "12h", "1d", "3d", "7d").stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}
