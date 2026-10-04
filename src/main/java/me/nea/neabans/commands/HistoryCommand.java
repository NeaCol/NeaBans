package me.nea.neabans.commands;

import me.nea.neabans.NeaBans;
import me.nea.neabans.model.Punishment;
import me.nea.neabans.model.PunishmentType;
import me.nea.neabans.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Comando /history <jugador> [página]
 * Muestra el historial de sanciones de un jugador con paginación.
 * 5 entradas por página, formato en chat.
 */
public class HistoryCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION    = "neabans.history";
    private static final int    ENTRIES_PAGE  = 5;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    private final NeaBans plugin;

    public HistoryCommand(NeaBans plugin) {
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
            plugin.getLang().send(sender, "history.usage");
            return true;
        }

        final String targetName = args[0];
        int requestedPage = 1;

        if (args.length >= 2) {
            try {
                requestedPage = Integer.parseInt(args[1]);
                if (requestedPage < 1) requestedPage = 1;
            } catch (NumberFormatException e) {
                Map<String, String> ph = new HashMap<>();
                ph.put("{input}", args[1]);
                plugin.getLang().send(sender, "history.invalid-page", ph);
                return true;
            }
        }

        final int page = requestedPage;

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

                int totalEntries = plugin.getDatabase().getHistoryCount(finalUuid);
                int totalPages   = Math.max(1, (int) Math.ceil((double) totalEntries / ENTRIES_PAGE));
                int clampedPage  = Math.min(page, totalPages);

                List<Punishment> entries = plugin.getDatabase()
                        .getHistory(finalUuid, clampedPage, ENTRIES_PAGE);

                final int fp = clampedPage;
                final int tp = totalPages;
                final int te = totalEntries;

                Bukkit.getScheduler().runTask(plugin, () -> {
                    // ---- Cabecera ----
                    Map<String, String> headerPh = new HashMap<>();
                    headerPh.put("{player}", finalName);
                    headerPh.put("{page}", String.valueOf(fp));
                    headerPh.put("{total_pages}", String.valueOf(tp));
                    headerPh.put("{total}", String.valueOf(te));
                    plugin.getLang().send(sender, "history.header", headerPh);

                    if (entries.isEmpty()) {
                        plugin.getLang().send(sender, "history.empty");
                    } else {
                        for (Punishment p : entries) {
                            String typeColor = p.getType().getColor();
                            String typeName  = p.getType().getDisplayName();

                            String duration;
                            if (p.getType() == PunishmentType.KICK || p.getType() == PunishmentType.WARN) {
                                duration = "-";
                            } else if (p.isPermanent()) {
                                duration = "Permanente";
                            } else {
                                duration = TimeUtils.format(p.getEndTime() - p.getStartTime());
                            }

                            String dateStr = DATE_FORMAT.format(new Date(p.getStartTime()));

                            Map<String, String> entryPh = new HashMap<>();
                            entryPh.put("{id}",       String.valueOf(p.getId()));
                            entryPh.put("{type}",     typeName);
                            entryPh.put("{color}",    typeColor);
                            entryPh.put("{reason}",   p.getReason() != null ? p.getReason() : "N/A");
                            entryPh.put("{staff}",    p.getStaffName());
                            entryPh.put("{date}",     dateStr);
                            entryPh.put("{duration}", duration);
                            entryPh.put("{active}",   p.isActive() ? plugin.getLang().get("history.active-label")
                                                                    : plugin.getLang().get("history.inactive-label"));

                            plugin.getLang().send(sender, "history.entry", entryPh);
                        }
                    }

                    // ---- Footer / paginación ----
                    Map<String, String> footerPh = new HashMap<>();
                    footerPh.put("{player}", finalName);
                    footerPh.put("{page}", String.valueOf(fp));
                    footerPh.put("{total_pages}", String.valueOf(tp));
                    footerPh.put("{prev_page}", String.valueOf(Math.max(1, fp - 1)));
                    footerPh.put("{next_page}", String.valueOf(Math.min(tp, fp + 1)));
                    plugin.getLang().send(sender, "history.footer", footerPh);
                });

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "[HistoryCommand] Error al cargar historial de " + targetName, e);
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
            // Sugerir números de página
            String partial = args[1];
            return List.of("1", "2", "3", "4", "5").stream()
                    .filter(n -> n.startsWith(partial))
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}
