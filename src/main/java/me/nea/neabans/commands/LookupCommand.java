package me.nea.neabans.commands;

import me.nea.neabans.NeaBans;
import me.nea.neabans.model.Punishment;
import me.nea.neabans.utils.ColorUtils;
import me.nea.neabans.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /lookup <jugador> — muestra un resumen rápido en el chat de las sanciones activas
 * del jugador: UUID, bans, mutes, warns y total.
 *
 * <p>Alias: /check</p>
 */
public final class LookupCommand implements CommandExecutor, TabCompleter {

    private final NeaBans plugin;

    public LookupCommand(NeaBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("neabans.lookup")) {
            plugin.getLang().send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 1) {
            plugin.getLang().send(sender, "lookup.usage");
            return true;
        }

        String targetName = args[0];

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Intentar resolver UUID
            UUID uuid = null;
            Player online = Bukkit.getPlayerExact(targetName);
            if (online != null) uuid = online.getUniqueId();
            if (uuid == null)   uuid = plugin.getDatabase().getUUID(targetName);

            if (uuid == null) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.getLang().send(sender, "general.player-not-found",
                                java.util.Map.of("player", targetName)));
                return;
            }

            final UUID finalUuid = uuid;
            final String finalName = online != null ? online.getName()
                    : (plugin.getDatabase().getLastName(uuid) != null
                            ? plugin.getDatabase().getLastName(uuid)
                            : targetName);

            // Consultar datos
            Punishment activeBan  = plugin.getDatabase().getActiveBan(uuid);
            Punishment activeMute = plugin.getDatabase().getActiveMute(uuid);
            int warnCount  = plugin.getDatabase().getWarnCount(uuid);
            int totalCount = plugin.getDatabase().getHistoryCount(uuid);
            List<Punishment> activePunishments = plugin.getDatabase().getActivePunishments(uuid);
            String lastIp  = plugin.getDatabase().getLastIp(uuid);

            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(ColorUtils.color(
                        "&#4E5C5E&m        &r &#FFD700&lLookup: &f" + finalName + " &#4E5C5E&m        &r"));
                sender.sendMessage(ColorUtils.color(
                        "&#808080UUID: &f" + finalUuid));

                if (lastIp != null && sender.hasPermission("neabans.lookup.ip")) {
                    sender.sendMessage(ColorUtils.color(
                            "&#808080Última IP: &f" + lastIp));
                }

                sender.sendMessage(ColorUtils.color(
                        "&#808080Sanciones totales: &f" + totalCount
                        + " &#808080| Activas: &f" + activePunishments.size()
                        + " &#808080| Warns: &f" + warnCount));

                // Ban activo
                if (activeBan != null && !activeBan.isExpired()) {
                    String dur = activeBan.isPermanent()
                            ? "&cPermanente"
                            : "&e" + TimeUtils.format(activeBan.getRemainingMillis());
                    sender.sendMessage(ColorUtils.color(
                            "&#FF4D4D⛔ Ban activo: &f" + activeBan.getReason()
                            + " &#808080| Staff: &f" + activeBan.getStaffName()
                            + " &#808080| Duración: " + dur));
                } else {
                    sender.sendMessage(ColorUtils.color("&#2ECC71✔ Sin ban activo"));
                }

                // Mute activo
                if (activeMute != null && !activeMute.isExpired()) {
                    String dur = activeMute.isPermanent()
                            ? "&cPermanente"
                            : "&e" + TimeUtils.format(activeMute.getRemainingMillis());
                    sender.sendMessage(ColorUtils.color(
                            "&#FFD700🔇 Mute activo: &f" + activeMute.getReason()
                            + " &#808080| Duración: " + dur));
                } else {
                    sender.sendMessage(ColorUtils.color("&#2ECC71✔ Sin mute activo"));
                }

                sender.sendMessage(ColorUtils.color(
                        "&#4E5C5E&m                                        &r"));
            });
        });

        return true;
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
        return List.of();
    }
}
