package me.nea.neabans.gui;

import me.nea.neabans.NeaBans;
import me.nea.neabans.model.Punishment;
import me.nea.neabans.model.PunishmentType;
import me.nea.neabans.utils.ColorUtils;
import me.nea.neabans.utils.ItemBuilder;
import me.nea.neabans.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * GUI principal del Punisher — muestra todas las categorías de infracciones
 * y permite aplicar sanciones por nivel directamente.
 *
 * <p>Layout (54 slots / 6 filas):
 * <pre>
 * [F][F][F][F][HEAD][F][F][F][F]  fila 0
 * [C1][C2][C3][C4][C5][C6][C7][C8][C9]  fila 1
 * [C10][C11][C12][C13][C14][C15][C16][C17][C18]  fila 2
 * [F][F][F][F][F][F][F][F][F]  fila 3
 * [F][F][F][F][F][F][F][F][F]  fila 4
 * [F][HIST][F][PREV][CLOSE][NEXT][F][F][F]  fila 5
 * </pre>
 * </p>
 */
public final class PunisherGui {

    private static final int CATS_PER_PAGE = 18;

    private PunisherGui() {}

    // ──────────────────────────────────────────────────
    //  APERTURA DEL MENÚ
    // ──────────────────────────────────────────────────

    /**
     * Busca al jugador objetivo (async) y abre el menú en el main thread.
     */
    public static void open(Player staff, String targetName) {
        NeaBans plugin = NeaBans.getInstance();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID targetUuid = plugin.getDatabase().getUUID(targetName);
            if (targetUuid == null) {
                // intentar con jugador online
                Player online = Bukkit.getPlayerExact(targetName);
                targetUuid = online != null ? online.getUniqueId() : null;
            }
            final UUID finalUuid = targetUuid;
            Bukkit.getScheduler().runTask(plugin, () ->
                    open(staff, finalUuid, targetName, 0)
            );
        });
    }

    /**
     * Abre el menú con UUID ya resuelto (sync, debe llamarse en main thread).
     */
    public static void open(Player staff, UUID targetUuid, String targetName, int page) {
        NeaBans plugin = NeaBans.getInstance();
        String title = ColorUtils.color(
                Objects.requireNonNull(plugin.getConfig().getString(
                        "settings.gui-title", "&#FF4D4D{player}"))
                        .replace("{player}", targetName)
                        .replace("%player%", targetName)
        );

        Inventory inv = Bukkit.createInventory(
                new GuiHolder("PUNISHER", targetUuid, targetName, null, page),
                54, title
        );
        populate(inv, staff, targetUuid, targetName, page);
        staff.openInventory(inv);
    }

    // ──────────────────────────────────────────────────
    //  RELLENADO DEL INVENTARIO
    // ──────────────────────────────────────────────────

    private static void populate(Inventory inv, Player staff,
                                  UUID targetUuid, String targetName, int page) {
        NeaBans plugin = NeaBans.getInstance();

        // 1. Relleno
        fillFiller(inv, plugin);

        // 2. Cabeza del jugador (slot 4)
        inv.setItem(4, buildPlayerHead(targetUuid, targetName, plugin));

        // 3. Categorías
        List<String> catKeys = new ArrayList<>(
                Objects.requireNonNull(plugin.getConfig().getConfigurationSection("categories"))
                        .getKeys(false)
        );
        int start = page * CATS_PER_PAGE;
        int end   = Math.min(start + CATS_PER_PAGE, catKeys.size());

        int[] catSlots = buildCategorySlots();
        for (int i = start; i < end; i++) {
            String key  = catKeys.get(i);
            int    slot = catSlots[i - start];
            inv.setItem(slot, buildCategoryItem(key, plugin));
        }

        // 4. Botones de navegación y acción (fila 5)
        // Historial — slot 46
        inv.setItem(46, new ItemBuilder(Material.BOOK)
                .name(plugin.getLang().get("gui.history-button-name"))
                .lore(ColorUtils.color("&#808080Click para ver el historial"))
                .hideFlags().build());

        // Página anterior — slot 48
        if (page > 0) {
            inv.setItem(48, new ItemBuilder(Material.ARROW)
                    .name(plugin.getLang().get("gui.prev-page-name")).build());
        }

        // Cerrar — slot 49
        ConfigurationSection closeCfg =
                plugin.getConfig().getConfigurationSection("gui.close-button");
        Material closemat = closeCfg != null
                ? parseMaterial(closeCfg.getString("item", "BARRIER")) : Material.BARRIER;
        inv.setItem(49, new ItemBuilder(closemat)
                .name(plugin.getLang().get("gui.close-button-name"))
                .lore(plugin.getLang().getList("gui.close-button-lore")).build());

        // Página siguiente — slot 50
        if (end < catKeys.size()) {
            inv.setItem(50, new ItemBuilder(Material.ARROW)
                    .name(plugin.getLang().get("gui.next-page-name")).build());
        }
    }

    // ──────────────────────────────────────────────────
    //  APERTURA DEL SUB-MENÚ DE CATEGORÍA
    // ──────────────────────────────────────────────────

    public static void openCategory(Player staff, UUID targetUuid,
                                     String targetName, String categoryKey) {
        NeaBans plugin  = NeaBans.getInstance();
        ConfigurationSection cfg =
                plugin.getConfig().getConfigurationSection("categories." + categoryKey);
        if (cfg == null) return;

        String catName = ColorUtils.color(cfg.getString("name", categoryKey));
        String title   = ColorUtils.color(plugin.getLang().get("gui.category-title")
                .replace("{category}", catName));

        Inventory inv = Bukkit.createInventory(
                new GuiHolder("CATEGORY", targetUuid, targetName, categoryKey, 0),
                27, title
        );

        // Relleno
        ItemStack filler = buildFiller(plugin);
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        // Info de categoría — slot 4
        Material catMat = parseMaterial(cfg.getString("icon", "PAPER"));
        List<String> catLore = cfg.getStringList("lore");
        inv.setItem(4, new ItemBuilder(catMat)
                .name(catName)
                .lore(catLore).build());

        // Niveles — slots 10, 13, 16
        ConfigurationSection levels = cfg.getConfigurationSection("levels");
        if (levels != null) {
            int[] levelSlots = {10, 13, 16};
            int idx = 0;
            for (String levelKey : levels.getKeys(false)) {
                if (idx >= levelSlots.length) break;
                ConfigurationSection lvl = levels.getConfigurationSection(levelKey);
                if (lvl == null) continue;

                Material lvlMat  = parseMaterial(lvl.getString("icon", "PAPER"));
                String   lvlName = ColorUtils.color(lvl.getString("name", "Nivel " + levelKey));
                String   duration = lvl.getString("duration", "");
                List<String> cmds  = lvl.getStringList("commands");

                List<String> lore = new ArrayList<>();
                lore.add(ColorUtils.color("&#808080Jugador: &f" + targetName));
                if (!duration.isEmpty() && !duration.equalsIgnoreCase("permanent")) {
                    lore.add(ColorUtils.color("&#808080Duración: &e" + duration));
                } else if (duration.equalsIgnoreCase("permanent")) {
                    lore.add(ColorUtils.color("&#808080Duración: &cPermanente"));
                }
                lore.add("");
                lore.add(ColorUtils.color("&#2ECC71▶ Click para aplicar"));

                inv.setItem(levelSlots[idx], new ItemBuilder(lvlMat)
                        .name(lvlName)
                        .lore(lore)
                        .hideFlags().build());
                idx++;
            }
        }

        // Volver — slot 22
        inv.setItem(22, new ItemBuilder(Material.ARROW)
                .name(plugin.getLang().get("gui.back-name")).build());

        // Cerrar — slot 26
        inv.setItem(26, new ItemBuilder(Material.BARRIER)
                .name(plugin.getLang().get("gui.close-button-name")).build());

        staff.openInventory(inv);
    }

    // ──────────────────────────────────────────────────
    //  APLICAR SANCIÓN DESDE GUI
    // ──────────────────────────────────────────────────

    /**
     * Aplica los comandos del nivel seleccionado como si los ejecutara el staff.
     * Corre en hilo async si hay queries de DB; los comandos en main thread.
     */
    public static void applyLevel(Player staff, UUID targetUuid,
                                   String targetName, String categoryKey,
                                   int levelIndex) {
        NeaBans plugin = NeaBans.getInstance();
        ConfigurationSection levels = plugin.getConfig()
                .getConfigurationSection("categories." + categoryKey + ".levels");
        if (levels == null) return;

        List<String> levelKeys = new ArrayList<>(levels.getKeys(false));
        if (levelIndex < 0 || levelIndex >= levelKeys.size()) return;

        ConfigurationSection lvl = levels.getConfigurationSection(levelKeys.get(levelIndex));
        if (lvl == null) return;

        List<String> commands = lvl.getStringList("commands");
        String duration = lvl.getString("duration", "");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID staffUuid = staff.getUniqueId();
            String staffName = staff.getName();
            String reason = ColorUtils.strip(lvl.getString("name", "Sanción"))
                    .replaceAll("[^\\x20-\\x7E]", "").trim();

            int catCount = plugin.getDatabase().getCategoryCount(targetUuid, categoryKey) + 1;
            String finalReason = reason + " (" + catCount + "ª infracción)";

            Bukkit.getScheduler().runTask(plugin, () -> {
                // Ejecutar comandos del nivel con placeholder {player}
                for (String cmd : commands) {
                    String resolved = cmd.replace("{player}", targetName)
                                        .replace("%player%", targetName);
                    // Si empieza por "neabans", dispatch como comando del plugin
                    Bukkit.dispatchCommand(staff, resolved.startsWith("/")
                            ? resolved.substring(1) : resolved);
                }

                staff.closeInventory();
                staff.sendMessage(plugin.getLang().getPrefix()
                        + plugin.getLang().get("gui.punishment-applied",
                        Map.of("player", targetName)));
            });
        });
    }

    // ──────────────────────────────────────────────────
    //  Helpers privados
    // ──────────────────────────────────────────────────

    private static ItemStack buildPlayerHead(UUID targetUuid, String targetName, NeaBans plugin) {
        OfflinePlayer offline = targetUuid != null
                ? Bukkit.getOfflinePlayer(targetUuid)
                : Bukkit.getOfflinePlayer(targetName);

        int total = 0, bans = 0, warns = 0;
        if (targetUuid != null) {
            try {
                total = plugin.getDatabase().getHistoryCount(targetUuid);
                bans  = plugin.getDatabase().getActivePunishments(targetUuid).stream()
                        .filter(p -> p.getType().isBan()).mapToInt(p -> 1).sum();
                warns = plugin.getDatabase().getWarnCount(targetUuid);
            } catch (Exception ignored) {}
        }

        List<String> headLore = plugin.getLang().getList("gui.player-info-lore",
                Map.of(
                        "uuid",  targetUuid != null ? targetUuid.toString() : "Desconocido",
                        "total", String.valueOf(total),
                        "bans",  String.valueOf(bans),
                        "warns", String.valueOf(warns)
                ));

        return new ItemBuilder(Material.PLAYER_HEAD)
                .name(plugin.getLang().get("gui.player-info-name",
                        Map.of("player", targetName)))
                .lore(headLore)
                .skullOwner(offline)
                .build();
    }

    private static ItemStack buildCategoryItem(String key, NeaBans plugin) {
        ConfigurationSection cfg =
                plugin.getConfig().getConfigurationSection("categories." + key);
        if (cfg == null) return new ItemStack(Material.PAPER);

        Material mat   = parseMaterial(cfg.getString("icon", "PAPER"));
        String   name  = ColorUtils.color(cfg.getString("name", key));
        List<String> lore = new ArrayList<>(ColorUtils.colorList(cfg.getStringList("lore")));
        lore.addAll(plugin.getLang().getList("gui.category-apply-lore"));

        return new ItemBuilder(mat).name(name).lore(lore).build();
    }

    private static ItemStack buildFiller(NeaBans plugin) {
        String matName = plugin.getConfig().getString("gui.filler.item", "BLACK_STAINED_GLASS_PANE");
        String fillName = plugin.getConfig().getString("gui.filler.name", " ");
        return new ItemBuilder(parseMaterial(matName)).name(fillName).build();
    }

    private static void fillFiller(Inventory inv, NeaBans plugin) {
        ItemStack filler = buildFiller(plugin);
        // Rellena filas 0, 3, 4, 5 (pero no los slots de botones de acción)
        for (int i = 0; i < 9; i++) inv.setItem(i, filler);
        for (int i = 27; i < 54; i++) inv.setItem(i, filler);
    }

    private static int[] buildCategorySlots() {
        // Filas 1 y 2 (slots 9-26) para 18 categorías
        int[] slots = new int[18];
        for (int i = 0; i < 18; i++) slots[i] = 9 + i;
        return slots;
    }

    private static Material parseMaterial(String name) {
        if (name == null) return Material.PAPER;
        try { return Material.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return Material.PAPER; }
    }
}
