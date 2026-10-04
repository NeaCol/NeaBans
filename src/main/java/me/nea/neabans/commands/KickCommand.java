package me.nea.neabans.commands;

import me.nea.neabans.NeaBans;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
 * Comando /kick <jugador> [razón...]
 * Expulsa a un jugador online del servidor.
 */
public class KickCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "neabans.kick";
    private static final String DEFAULT_REASON = "No especificada";

    private final NeaBans plugin;

    public KickCommand(NeaBans plugin) {
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
            plugin.getLang().send(sender, "kick.usage");
            return true;
        }

        final String targetName = args[0];
        final String reason = args.length > 1
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : DEFAULT_REASON;

        // El jugador DEBE estar online para ser kickeado
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("{player}", targetName);
            plugin.getLang().send(sender, "kick.not-online", ph);
            return true;
        }

        final String finalName = target.getName();
        final UUID finalUuid = target.getUniqueId();
        final String staffName = sender instanceof Player p ? p.getName() : "CONSOLE";
        final UUID staffUuid = sender instanceof Player p ? p.getUniqueId() : null;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int id = plugin.getPunishmentManager().kick(
                        staffUuid, staffName,
                        finalUuid, finalName,
                        reason, null, 0
                );

                Bukkit.getScheduler().runTask(plugin, () -> {
                    Map<String, String> ph = new HashMap<>();
                    ph.put("{player}", finalName);
                    ph.put("{reason}", reason);
                    ph.put("{staff}", staffName);
                    ph.put("{id}", String.valueOf(id));

                    // Kickear con pantalla personalizada
                    Player online = Bukkit.getPlayer(finalUuid);
                    if (online != null) {
                        String kickScreen = plugin.getLang().get("kick.kick-screen", ph);
                        Component kickComponent = LegacyComponentSerializer.legacyAmpersand()
                                .deserialize(kickScreen);
                        online.kick(kickComponent);
                    }

                    plugin.getLang().send(sender, "kick.success", ph);
                    plugin.getLang().broadcast("kick.broadcast", ph);
                });

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[KickCommand] Error al kickear a " + finalName, e);
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
