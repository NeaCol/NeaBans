package me.nea.neabans.commands;

import me.nea.neabans.NeaBans;
import me.nea.neabans.model.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** /ipunban <ip> — elimina el IP-ban activo de una dirección. */
public final class IpUnbanCommand implements CommandExecutor, TabCompleter {

    private final NeaBans plugin;

    public IpUnbanCommand(NeaBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("neabans.ipunban")) {
            plugin.getLang().send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 1) {
            plugin.getLang().send(sender, "general.invalid-args",
                    Map.of("usage", "/ipunban <ip>"));
            return true;
        }

        String ip        = args[0];
        String staffName = sender instanceof Player p ? p.getName() : "CONSOLE";

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean success = plugin.getPunishmentManager().unIpBan(staffName, ip);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    plugin.getLang().send(sender, "ipban.removed",
                            Map.of("ip", ip));
                    plugin.getLang().broadcastPermission("ipban.broadcast-unban",
                            Map.of("ip", ip, "staff", staffName),
                            "neabans.staff.notify");
                } else {
                    plugin.getLang().send(sender, "ipban.not-banned",
                            Map.of("ip", ip));
                }
            });
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String label, @NotNull String[] args) {
        return List.of();
    }
}
