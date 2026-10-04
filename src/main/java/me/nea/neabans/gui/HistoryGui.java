package me.nea.neabans.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.nea.neabans.NeaBans;
import me.nea.neabans.model.Punishment;
import me.nea.neabans.model.PunishmentType;
import me.nea.neabans.utils.ColorUtils;
import me.nea.neabans.utils.ItemBuilder;
import me.nea.neabans.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * GUI de historial de sanciones de un jugador.
 *
 * <p>Muestra 45 entradas por página (5 filas × 9), con la fila 6 para
 * navegación y el slot central para cerrar.</p>
 */
public final class HistoryGui {

    private static final int ENTRIES_PER_PAGE = 45;

    private HistoryGui() {}

    /**
     * Abre el historial (async query, sync open).
     */
    public static void open(
        Player viewer,
        UUID targetUuid,
        String targetName,
        int page
    ) {
        NeaBans plugin = NeaBans.getInstance();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Punishment> entries = plugin
                .getDatabase()
                .getHistory(targetUuid, page + 1, ENTRIES_PER_PAGE);
            int total = plugin.getDatabase().getHistoryCount(targetUuid);

            Bukkit.getScheduler().runTask(plugin, () ->
                buildAndOpen(
                    viewer,
                    targetUuid,
                    targetName,
                    page,
                    entries,
                    total,
                    plugin
                )
            );
        });
    }

    private static void buildAndOpen(
        Player viewer,
        UUID targetUuid,
        String targetName,
        int page,
        List<Punishment> entries,
        int total,
        NeaBans plugin
    ) {
        String title = ColorUtils.color(
            plugin
                .getLang()
                .get("gui.history-title", Map.of("player", targetName))
        );

        Inventory inv = Bukkit.createInventory(
            new GuiHolder("HISTORY", targetUuid, targetName, null, page),
            54,
            title
        );

        // Relleno en filas 5 (barra de nav)
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE)
            .name(" ")
            .build();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        // Entradas de historial (slots 0-44)
        for (int i = 0; i < entries.size() && i < ENTRIES_PER_PAGE; i++) {
            inv.setItem(i, buildEntry(entries.get(i), plugin));
        }

        // Página anterior — slot 48
        if (page > 0) {
            inv.setItem(
                48,
                new ItemBuilder(Material.ARROW)
                    .name(plugin.getLang().get("gui.prev-page-name"))
                    .build()
            );
        }

        // Cerrar — slot 49
        inv.setItem(
            49,
            new ItemBuilder(Material.BARRIER)
                .name(plugin.getLang().get("gui.close-button-name"))
                .build()
        );

        // Página siguiente — slot 50
        int totalPages = (int) Math.ceil((double) total / ENTRIES_PER_PAGE);
        if (page + 1 < totalPages) {
            inv.setItem(
                50,
                new ItemBuilder(Material.ARROW)
                    .name(plugin.getLang().get("gui.next-page-name"))
                    .build()
            );
        }

        // Info de página — slot 53
        inv.setItem(
            53,
            new ItemBuilder(Material.PAPER)
                .name(
                    ColorUtils.color(
                        "&#808080Página " +
                            (page + 1) +
                            "/" +
                            totalPages +
                            " | Total: " +
                            total
                    )
                )
                .build()
        );

        viewer.openInventory(inv);
    }

    private static ItemStack buildEntry(Punishment p, NeaBans plugin) {
        Material mat = materialForType(p.getType());

        String statusColor;
        String statusLabel;
        if (p.isActive() && !p.isExpired()) {
            statusColor = "&#FF4D4D";
            statusLabel = "Activo";
        } else if (p.getRemovedBy() != null) {
            statusColor = "&#2ECC71";
            statusLabel = "Removido por " + p.getRemovedBy();
        } else {
            statusColor = "&#808080";
            statusLabel = "Expirado";
        }

        String duration = p.isPermanent()
            ? plugin.getLang().get("general.permanent")
            : TimeUtils.format(p.getEndTime() - p.getStartTime());

        List<String> lore = new ArrayList<>();
        lore.add(ColorUtils.color("&#808080Razón: &f" + p.getReason()));
        lore.add(ColorUtils.color("&#808080Staff: &f" + p.getStaffName()));
        lore.add(ColorUtils.color("&#808080Duración: &f" + duration));
        lore.add(ColorUtils.color("&#808080Servidor: &f" + p.getServer()));
        lore.add(
            ColorUtils.color(
                "&#808080Fecha: &f" + TimeUtils.formatDate(p.getStartTime())
            )
        );
        if (p.getCategory() != null) {
            lore.add(
                ColorUtils.color("&#808080Categoría: &f" + p.getCategory())
            );
        }
        lore.add("");
        lore.add(ColorUtils.color(statusColor + "● " + statusLabel));
        if (p.getRemovedBy() != null && p.getRemovedAt() > 0) {
            lore.add(
                ColorUtils.color(
                    "&#808080Removido: &f" +
                        TimeUtils.formatDate(p.getRemovedAt())
                )
            );
        }

        String name = ColorUtils.color(
            p.getType().getColor() +
                p.getType().getDisplayName() +
                " &8#" +
                p.getId()
        );

        return new ItemBuilder(mat).name(name).lore(lore).hideFlags().build();
    }

    private static Material materialForType(PunishmentType type) {
        return switch (type) {
            case BAN, TEMP_BAN -> Material.BARRIER;
            case MUTE, TEMP_MUTE -> Material.PAPER;
            case KICK -> Material.IRON_BOOTS;
            case WARN -> Material.BOOK;
            case IP_BAN, TEMP_IP_BAN -> Material.NETHER_BRICK;
        };
    }
}
