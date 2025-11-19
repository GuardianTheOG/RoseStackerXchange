package com.farahsoftware.rsx;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class GuiManager {
    private final RoseStackerXchange plugin;
    private final ConfigManager cfg;
    private final NamespacedKey guiLockKey;
    private final NamespacedKey mobKey;

    public GuiManager(RoseStackerXchange plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
        this.guiLockKey = new NamespacedKey(plugin, "rsx_gui_locked");
        this.mobKey = new NamespacedKey(plugin, "rsx_mob");
    }

    /* Selection GUI (hard-coded first GUI, alphabetical, paginated) */
    public Inventory buildSelectionPage(int page) {
        Map<String,Integer> mobMap = cfg.getMobMap();
        List<String> all = new ArrayList<>(mobMap.keySet());
        Collections.sort(all, String.CASE_INSENSITIVE_ORDER);

        int perPage = 28;
        int totalPages = Math.max(1, (all.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, totalPages - 1));

        String title = ChatColor.translateAlternateColorCodes('&', "&6RSX - Select Spawner") + " (" + (page+1) + "/" + totalPages + ")";
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Fill with filler locked items
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta(); fm.setDisplayName(" "); filler.setItemMeta(fm);
        for (int i = 0; i < 54; i++) {
            ItemStack copy = filler.clone();
            ItemMeta cm = copy.getItemMeta();
            cm.getPersistentDataContainer().set(guiLockKey, PersistentDataType.STRING, "true");
            copy.setItemMeta(cm);
            inv.setItem(i, copy);
        }

        List<Integer> contentSlots = Arrays.asList(10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43);
        int start = page * perPage;
        for (int i = 0; i < contentSlots.size(); i++) {
            int idx = start + i;
            if (idx >= all.size()) break;
            String mob = all.get(idx);
            int rate = mobMap.getOrDefault(mob, 1);
            ItemStack item = buildSelectionItem(mob, rate);
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(guiLockKey, PersistentDataType.STRING, "true"); // lock
            meta.getPersistentDataContainer().set(mobKey, PersistentDataType.STRING, mob);
            item.setItemMeta(meta);
            inv.setItem(contentSlots.get(i), item);
        }

        // nav arrows
        ItemStack prev = new ItemStack(Material.ARROW);
        ItemStack next = new ItemStack(Material.ARROW);
        ItemMeta pm = prev.getItemMeta(); pm.setDisplayName(ChatColor.YELLOW + "Previous Page"); prev.setItemMeta(pm);
        ItemMeta nm = next.getItemMeta(); nm.setDisplayName(ChatColor.YELLOW + "Next Page"); next.setItemMeta(nm);
        pm.getPersistentDataContainer().set(guiLockKey, PersistentDataType.STRING, "true");
        nm.getPersistentDataContainer().set(guiLockKey, PersistentDataType.STRING, "true");
        prev.setItemMeta(pm); next.setItemMeta(nm);
        inv.setItem(45, prev);
        inv.setItem(53, next);

        return inv;
    }

    private ItemStack buildSelectionItem(String mobKeyStr, int rate) {
        Material icon = Material.SPAWNER;
        try {
            String head = mobKeyStr.toUpperCase() + "_HEAD";
            if (Material.matchMaterial(head) != null) icon = Material.matchMaterial(head);
        } catch (Exception ignored) {}
        try {
            String egg = mobKeyStr.toUpperCase() + "_SPAWN_EGG";
            if (Material.matchMaterial(egg) != null) icon = Material.matchMaterial(egg);
        } catch (Exception ignored) {}

        ItemStack is = new ItemStack(icon);
        ItemMeta meta = is.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + mobKeyStr);
        meta.setLore(Collections.singletonList(ChatColor.GRAY + "Rate: " + rate));
        is.setItemMeta(meta);
        return is;
    }

    /* Exchange GUI (second GUI) */
    public Inventory buildExchangeInventory(Player player, String mobKeyStr, int required) {
        int baseRows = cfg.getGuiRows();
        int neededInputRows = Math.max(1, (required + 8) / 9);
        int rows = Math.max(baseRows, Math.min(6, 1 + neededInputRows));
        if (rows < 2) rows = 2; // ensure at least one content row besides controls
        int size = rows * 9;
        String title = cfg.getGuiTitle() + " - " + mobKeyStr;
        Inventory inv = Bukkit.createInventory(null, size, title);

        // filler
        ItemStack filler = new ItemStack(cfg.getFillerMaterial());
        ItemMeta fm = filler.getItemMeta(); fm.setDisplayName(cfg.getFillerName()); filler.setItemMeta(fm);
        for (int i = 0; i < size; i++) {
            ItemStack copy = filler.clone();
            ItemMeta cm = copy.getItemMeta();
            cm.getPersistentDataContainer().set(guiLockKey, PersistentDataType.STRING, "true");
            copy.setItemMeta(cm);
            inv.setItem(i, copy);
        }

        // choose input slots dynamically based on rows & required
        List<Integer> candidates = generateDynamicCandidates(rows, required % 2 == 1);
        List<Integer> chosen = new ArrayList<>();
        for (int i = 0; i < Math.min(required, candidates.size()); i++) chosen.add(candidates.get(i));

        // clear chosen slots so players can insert items
        for (int slot : chosen) inv.setItem(slot, null);

        // cancel
        ItemStack cancel = new ItemStack(cfg.getItemMaterial("items.cancel", Material.RED_WOOL));
        ItemMeta cm = cancel.getItemMeta(); cm.setDisplayName(cfg.getItemName("items.cancel")); cancel.setItemMeta(cm);
        cm.getPersistentDataContainer().set(guiLockKey, PersistentDataType.STRING, "true");
        cancel.setItemMeta(cm);
        inv.setItem(cfg.getCancelSlot(), cancel);

        // confirm (disabled)
        ItemStack confirmDisabled = new ItemStack(cfg.getItemMaterial("items.confirm-disabled", Material.GRAY_WOOL));
        ItemMeta cdm = confirmDisabled.getItemMeta(); cdm.setDisplayName(cfg.getItemName("items.confirm-disabled")); confirmDisabled.setItemMeta(cdm);
        cdm.getPersistentDataContainer().set(guiLockKey, PersistentDataType.STRING, "true");
        confirmDisabled.setItemMeta(cdm);
        inv.setItem(cfg.getConfirmSlot(), confirmDisabled);

        // marker (locked) - store mob & required
        if (size > 4) {
            ItemStack marker = new ItemStack(Material.PAPER);
            ItemMeta mm = marker.getItemMeta();
            mm.setDisplayName("rsx_marker:" + mobKeyStr + ":" + required);
            mm.getPersistentDataContainer().set(new NamespacedKey(plugin, "rsx_marker"), PersistentDataType.STRING, mobKeyStr + ":" + required);
            mm.getPersistentDataContainer().set(guiLockKey, PersistentDataType.STRING, "true");
            marker.setItemMeta(mm);
            inv.setItem(4, marker);
        }

        return inv;
    }

    private List<Integer> generateDynamicCandidates(int rows, boolean oddPreferred) {
        List<Integer> out = new ArrayList<>();
        int contentStartRow = 1; // row 0 reserved for controls
        int contentRows = Math.max(0, rows - contentStartRow);
        if (contentRows <= 0) return out;

        List<Integer> rowOrder = new ArrayList<>();
        int first = contentStartRow;
        int last = rows - 1;
        int mid = first + (last - first) / 2;
        rowOrder.add(mid);
        for (int offset = 1; ; offset++) {
            boolean added = false;
            int up = mid - offset;
            int down = mid + offset;
            if (up >= first) { rowOrder.add(up); added = true; }
            if (down <= last) { rowOrder.add(down); added = true; }
            if (!added) break;
        }

        int[] colOrderOdd = new int[] {4,3,5,2,6,1,7,0,8};
        int[] colOrderEven = new int[] {3,5,2,6,1,7,0,8,4}; // center last for even
        int[] colOrder = oddPreferred ? colOrderOdd : colOrderEven;

        int cancelSlot = cfg.getCancelSlot();
        int confirmSlot = cfg.getConfirmSlot();
        int markerSlot = 4; // reserved marker slot in controls row

        for (int r : rowOrder) {
            for (int c : colOrder) {
                int idx = r * 9 + c;
                if (idx == cancelSlot || idx == confirmSlot || idx == markerSlot) continue;
                out.add(idx);
            }
        }

        return out;
    }

    /**
     * Choose input slots from candidates, obey center-even/odd rule.
     * If required is odd -> include middle candidate index if available.
     * If required is even -> avoid middle.
     */
    private List<Integer> chooseInputSlots(List<Integer> candidates, int required) {
        List<Integer> res = new ArrayList<>();
        if (required <= 0) return res;
        int n = candidates.size();
        if (required >= n) {
            res.addAll(candidates.subList(0, Math.min(required, n)));
            return res;
        }
        int mid = n / 2;
        if (required % 2 == 1) {
            int half = required / 2;
            int start = Math.max(0, mid - half);
            int end = Math.min(n, start + required);
            if (end - start < required) start = Math.max(0, end - required);
            for (int i = start; i < end; i++) res.add(candidates.get(i));
            return res;
        } else {
            int half = required / 2;
            int leftStart = Math.max(0, mid - half);
            int rightStart = leftStart + half;
            if (rightStart + half > n) {
                rightStart = Math.max(half, n - half);
                leftStart = rightStart - half;
            }
            for (int i = leftStart; i < leftStart + half; i++) res.add(candidates.get(i));
            for (int i = rightStart; i < rightStart + half; i++) res.add(candidates.get(i));
            return res;
        }
    }
}
