package com.farahsoftware.rsx;

import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.ChatColor;
import org.bukkit.inventory.Inventory;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.Bukkit;

public class InventoryListener implements Listener {
    private final RoseStackerXchange plugin;
    private final NamespacedKey guiLockKey;
    private final NamespacedKey mobKey;

    public InventoryListener(RoseStackerXchange plugin) {
        this.plugin = plugin;
        this.guiLockKey = new NamespacedKey(plugin, "rsx_gui_locked");
        this.mobKey = new NamespacedKey(plugin, "rsx_mob");
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        InventoryView view = e.getView();
        if (view == null) return;
        String title = view.getTitle();

        // Protect GUI-generated items (tagged)
        ItemStack current = e.getCurrentItem();
        if (current != null && current.hasItemMeta()) {
            ItemMeta meta = current.getItemMeta();
            if (meta.getPersistentDataContainer().has(guiLockKey, PersistentDataType.STRING)) {
                e.setCancelled(true);
                // Do not return here; allow click handlers below to process GUI actions
            }
        }

        // Selection GUI handling
        if (title != null && ChatColor.stripColor(title).startsWith("RSX - Select Spawner")) {
            e.setCancelled(true);
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null) return;
            if (clicked.getType() == org.bukkit.Material.ARROW && clicked.hasItemMeta()) {
                String dn = clicked.getItemMeta().getDisplayName();
                if (dn != null && dn.contains("Previous")) {
                    int page = extractPageNumber(title);
                    p.openInventory(plugin.getGuiManager().buildSelectionPage(Math.max(0, page - 1)));
                    return;
                } else if (dn != null && dn.contains("Next")) {
                    int page = extractPageNumber(title);
                    p.openInventory(plugin.getGuiManager().buildSelectionPage(page + 1));
                    return;
                }
            }
            if (clicked.hasItemMeta() && clicked.getItemMeta().getPersistentDataContainer().has(mobKey, PersistentDataType.STRING)) {
                String mob = clicked.getItemMeta().getPersistentDataContainer().get(mobKey, PersistentDataType.STRING);
                plugin.getExchangeManager().startSession(p, mob);
            }
            return;
        }

        // Exchange GUI handling
        if (title != null && title.startsWith(plugin.getConfigManager().getGuiTitle())) {
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null) {
                // Schedule UI refresh after the click to reflect inventory changes
                Bukkit.getScheduler().runTask(plugin, () -> plugin.getExchangeManager().refreshConfirmState(p));
                return;
            }
            if (clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()) {
                String dn = clicked.getItemMeta().getDisplayName();
                String cancel = plugin.getConfigManager().getItemName("items.cancel");
                String confirmDisabled = plugin.getConfigManager().getItemName("items.confirm-disabled");
                String confirmEnabled = plugin.getConfigManager().getItemName("items.confirm-enabled");
                if (dn.equals(confirmEnabled)) {
                    e.setCancelled(true);
                    plugin.getExchangeManager().tryComplete(p);
                    return;
                }
                if (dn.equals(cancel)) {
                    e.setCancelled(true);
                    plugin.getExchangeManager().cancelSession(p);
                    p.closeInventory();
                    p.sendMessage(ChatColor.YELLOW + "Exchange cancelled.");
                    return;
                }
            }
            // Schedule UI refresh after the click to reflect inventory changes
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getExchangeManager().refreshConfirmState(p));
        }
    }

    private int extractPageNumber(String title) {
        int s = title.lastIndexOf('(');
        int e = title.lastIndexOf(')');
        if (s != -1 && e != -1 && e > s) {
            String inside = title.substring(s+1, e);
            if (inside.contains("/")) inside = inside.split("/")[0];
            try {
                int n = Integer.parseInt(inside);
                return Math.max(0, n - 1);
            } catch (Exception ignored) {}
        }
        return 0;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = (Player) e.getPlayer();
        String title = e.getView().getTitle();
        if (title != null && title.startsWith(plugin.getConfigManager().getGuiTitle())) {
            plugin.getExchangeManager().cancelSession(p);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        InventoryView view = e.getView();
        if (view == null) return;
        String title = view.getTitle();
        if (title != null && title.startsWith(plugin.getConfigManager().getGuiTitle())) {
            // Prevent dragging items into locked slots of the top inventory
            Inventory top = view.getTopInventory();
            int topSize = top.getSize();
            for (int rawSlot : e.getRawSlots()) {
                if (rawSlot < topSize) {
                    ItemStack it = top.getItem(rawSlot);
                    if (it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(guiLockKey, PersistentDataType.STRING)) {
                        e.setCancelled(true);
                        break;
                    }
                }
            }
            // Schedule UI refresh after the drag to reflect inventory changes
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getExchangeManager().refreshConfirmState(p));
        }
    }
}
