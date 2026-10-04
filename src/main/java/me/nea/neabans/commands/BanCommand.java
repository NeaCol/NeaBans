package me.nea.neabans.commands;

import me.nea.neabans.NeaBans;
import me.nea.neabans.model.Punishment;
import me.nea.neabans.utils.TimeUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Comando /ban <jugador> [razón...]
 * Aplica un ban permanente a un jugador.
 */
public class BanCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "neabans.ban";
    private static final String DEFAULT_REASON = "No especificada";

    private final NeaBans plugin;

    public BanCommand(NeaBans plugin) {
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
            plugin.getLang().send(sender, "ban.usage");
            return true;
        }

        final String targetName = args[0];
        final String reason = args.length > 1
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                : DEFAULT_REASON;

        final String staffName = sender instanceof Player p ? p.getName() : "CONSOLE";
        final UUID staffUuid = sender instanceof Player p ? p.getUniqueId() : null;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Buscar UUID: primero online, luego en DB
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

                // Verificar si ya está baneado
                if (plugin.getPunishmentManager().isBanned(finalUuid)) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Map<String, String> ph = new HashMap<>();
                        ph.put("{player}", finalName);
                        plugin.getLang().send(sender, "ban.already-banned", ph);
                    });
                    return;
                }

                int id = plugin.getPunishmentManager().ban(
                        staffUuid, staffName,
                        finalUuid, finalName,
                        reason, null, 0
                );

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (id == -1) {
                        Map<String, String> ph = new HashMap<>();
                        ph.put("{player}", finalName);
                        plugin.getLang().send(sender, "ban.already-banned", ph);
                        return;
                    }

                    Map<String, String> ph = new HashMap<>();
                    ph.put("{player}", finalName);
                    ph.put("{reason}", reason);
                    ph.put("{staff}", staffName);
                    ph.put("{id}", String.valueOf(id));
                    ph.put("{duration}", "Permanente");

                    // Kickear al jugador si está online
                    Player online = Bukkit.getPlayerExact(finalName);
                    if (online != null) {
                        String banScreen = plugin.getLang().get("ban.ban-screen", ph);
                        Component banComponent = LegacyComponentSerializer.legacyAmpersand()
                                .deserialize(banScreen);
                        online.kick(banComponent);
                    }

                    // Feedback al ejecutor
                    plugin.getLang().send(sender, "ban.success", ph);

                    // Broadcast
                    plugin.getLang().broadcast("ban.broadcast", ph);
                });

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[BanCommand] Error al banear a " + targetName, e);
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
