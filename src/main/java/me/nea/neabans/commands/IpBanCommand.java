package me.nea.neabans.commands;

import me.nea.neabans.NeaBans;
import me.nea.neabans.model.PunishmentType;
import me.nea.neabans.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /ipban  <jugador|ip> [razón]         — IP-Ban permanente
 * /iptempban <jugador|ip> <dur> [razón] — IP-Ban temporal
 */
public final class IpBanCommand implements CommandExecutor, TabCompleter {

    private final NeaBans  plugin;
    private final boolean  isTemp;   // true = /iptempban

    public IpBanCommand(NeaBans plugin, boolean isTemp) {
        this.plugin = plugin;
        this.isTemp = isTemp;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {

        String perm = isTemp ? "neabans.iptempban" : "neabans.ipban";
        if (!sender.hasPermission(perm)) {
            plugin.getLang().send(sender, "general.no-permission");
            return true;
        }

        // /ipban <jugador|ip> [razón]
        // /iptempban <jugador|ip> <duración> [razón]
        int minArgs = isTemp ? 2 : 1;
        if (args.length < minArgs) {
            String usage = isTemp ? "/iptempban <jugador|ip> <duración> [razón]"
                                  : "/ipban <jugador|ip> [razón]";
            plugin.getLang().send(sender, "general.invalid-args",
                    Map.of("usage", usage));
            return true;
        }

        String target = args[0];
        long duration = -1L;
        int reasonStart = 1;

        if (isTemp) {
            duration = TimeUtils.parse(args[1]);
            if (duration <= 0) {
                plugin.getLang().send(sender, "general.invalid-duration");
                return true;
            }
            reasonStart = 2;
        }

        String reason = reasonStart < args.length
                ? String.join(" ", java.util.Arrays.copyOfRange(args, reasonStart, args.length))
                : "No especificada";

        UUID   staffUuid = sender instanceof Player p ? p.getUniqueId() : null;
        String staffName = sender instanceof Player p ? p.getName() : "CONSOLE";
        final long finalDuration = duration;

        // ¿Es una IP directa (contiene punto) o un nombre de jugador?
        if (isIpAddress(target)) {
            applyIpBan(sender, staffUuid, staffName, target, null, reason, finalDuration);
        } else {
            // Buscar la IP del jugador
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                UUID targetUuid = null;
                Player online = Bukkit.getPlayerExact(target);
                if (online != null) targetUuid = online.getUniqueId();
                if (targetUuid == null) targetUuid = plugin.getDatabase().getUUID(target);

                if (targetUuid == null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            plugin.getLang().send(sender, "general.player-not-found",
                                    Map.of("player", target)));
                    return;
                }

                String ip = plugin.getDatabase().getLastIp(targetUuid);
                if (ip == null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            sender.sendMessage(plugin.getLang().getPrefix()
                                    + plugin.getLang().get("ipban.no-ip",
                                    Map.of("player", target))));
                    return;
                }

                final String finalName = target;
                final UUID   finalUuid = targetUuid;
                Bukkit.getScheduler().runTask(plugin, () ->
                        applyIpBan(sender, staffUuid, staffName, ip, finalName, reason, finalDuration));
            });
        }
        return true;
    }

    private void applyIpBan(CommandSender sender, UUID staffUuid, String staffName,
                             String ip, String playerName, String reason, long duration) {
        int id = isTemp
                ? plugin.getPunishmentManager().tempIpBan(staffUuid, staffName, ip, playerName, duration, reason)
                : plugin.getPunishmentManager().ipBan(staffUuid, staffName, ip, playerName, reason);

        if (id == -1) {
            sender.sendMessage(plugin.getLang().getPrefix()
                    + plugin.getLang().get("ipban.already-banned",
                    Map.of("ip", ip)));
            return;
        }

        String display = playerName != null ? playerName + " (" + ip + ")" : ip;
        String durStr  = duration <= 0 ? "Permanente" : TimeUtils.format(duration);

        plugin.getLang().send(sender, "ipban.success",
                Map.of("target", display, "reason", reason, "duration", durStr));
        plugin.getLang().broadcastPermission(
                isTemp ? "ipban.broadcast-temp" : "ipban.broadcast",
                Map.of("target", display, "staff", staffName,
                       "reason", reason, "duration", durStr),
                "neabans.staff.notify");
    }

    private static boolean isIpAddress(String s) {
        return s.matches("^(\\d{1,3}\\.){3}\\d{1,3}$");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (isTemp && args.length == 2) {
            return List.of("1h", "6h", "12h", "1d", "3d", "7d", "30d");
        }
        return List.of();
    }
}
