package me.nea.neabans.listeners;

import me.nea.neabans.NeaBans;
import me.nea.neabans.gui.GuiHolder;
import me.nea.neabans.gui.HistoryGui;
import me.nea.neabans.gui.PunisherGui;
import me.nea.neabans.utils.ColorUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * Listener que maneja todos los clicks en los inventarios de NeaBans.
 */
public final class GuiListener implements Listener {

    private final NeaBans plugin;

    public GuiListener(NeaBans plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof GuiHolder gui)) return;

        event.setCancelled(true); // siempre cancelar en nuestros GUIs

        if (!(event.getWhoClicked() instanceof Player staff)) return;
        if (event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType().isAir()) return;

        int slot = event.getRawSlot();

        switch (gui.getType()) {
            case "PUNISHER" -> handlePunisherClick(staff, gui, slot);
            case "CATEGORY" -> handleCategoryClick(staff, gui, slot);
            case "HISTORY"  -> handleHistoryClick(staff, gui, slot);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Reservado para acciones al cerrar inventarios (p.ej. animaciones)
    }

    // ──────────────────────────────────────────────────
    //  PUNISHER MENU
    // ──────────────────────────────────────────────────

    private void handlePunisherClick(Player staff, GuiHolder gui, int slot) {
        int closeSlot = plugin.getConfig().getInt("gui.close-button.slot", 49);
        int nextSlot  = plugin.getConfig().getInt("gui.next-page.slot",   50);
        int prevSlot  = plugin.getConfig().getInt("gui.prev-page.slot",   48);
        int histSlot  = plugin.getConfig().getInt("gui.history-button.slot", 46);
        int headSlot  = plugin.getConfig().getInt("gui.player-head.slot", 4);

        if (slot == closeSlot) {
            staff.closeInventory();
            return;
        }

        if (slot == histSlot || slot == headSlot) {
            staff.closeInventory();
            if (gui.getTargetUuid() != null) {
                HistoryGui.open(staff, gui.getTargetUuid(), gui.getTargetName(), 0);
            }
            return;
        }

        if (slot == nextSlot) {
            staff.closeInventory();
            PunisherGui.open(staff, gui.getTargetUuid(), gui.getTargetName(), gui.getPage() + 1);
            return;
        }

        if (slot == prevSlot && gui.getPage() > 0) {
            staff.closeInventory();
            PunisherGui.open(staff, gui.getTargetUuid(), gui.getTargetName(), gui.getPage() - 1);
            return;
        }

        // Click en categoría (filas 1 y 2, slots 9-26)
        if (slot >= 9 && slot <= 26) {
            int catIndex = (gui.getPage() * 18) + (slot - 9);
            List<String> catKeys = new ArrayList<>(
                    plugin.getConfig().getConfigurationSection("categories") != null
                    ? plugin.getConfig().getConfigurationSection("categories").getKeys(false)
                    : List.of()
            );
            if (catIndex < catKeys.size()) {
                String catKey = catKeys.get(catIndex);
                staff.closeInventory();
                PunisherGui.openCategory(staff, gui.getTargetUuid(), gui.getTargetName(), catKey);
            }
        }
    }

    // ──────────────────────────────────────────────────
    //  CATEGORY SUB-MENU
    // ──────────────────────────────────────────────────

    private void handleCategoryClick(Player staff, GuiHolder gui, int slot) {
        // Volver — slot 22 | Cerrar — slot 26
        if (slot == 22) {
            staff.closeInventory();
            PunisherGui.open(staff, gui.getTargetUuid(), gui.getTargetName(), 0);
            return;
        }
        if (slot == 26) {
            staff.closeInventory();
            return;
        }

        // Niveles en slots 10, 13, 16
        int levelIndex = switch (slot) {
            case 10 -> 0;
            case 13 -> 1;
            case 16 -> 2;
            default -> -1;
        };

        if (levelIndex >= 0 && gui.getCategory() != null && gui.getTargetUuid() != null) {
            staff.closeInventory();
            PunisherGui.applyLevel(staff, gui.getTargetUuid(), gui.getTargetName(),
                    gui.getCategory(), levelIndex);
        }
    }

    // ──────────────────────────────────────────────────
    //  HISTORY GUI
    // ──────────────────────────────────────────────────

    private void handleHistoryClick(Player staff, GuiHolder gui, int slot) {
        if (slot == 49) { // Cerrar
            staff.closeInventory();
            return;
        }
        if (slot == 48 && gui.getPage() > 0) { // Anterior
            staff.closeInventory();
            HistoryGui.open(staff, gui.getTargetUuid(), gui.getTargetName(), gui.getPage() - 1);
            return;
        }
        if (slot == 50) { // Siguiente
            staff.closeInventory();
            HistoryGui.open(staff, gui.getTargetUuid(), gui.getTargetName(), gui.getPage() + 1);
        }
    }
}
