package com.farahsoftware.rsx;

import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExchangeManager {
    private final RoseStackerXchange plugin;
    private final SpawnerManager spawnerManager;
    private final ConfigManager config;
    private final NamespacedKey guiLockKey;
    private final NamespacedKey markerKey;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public ExchangeManager(RoseStackerXchange plugin, SpawnerManager spawnerManager, ConfigManager config) {
        this.plugin = plugin;
        this.spawnerManager = spawnerManager;
        this.config = config;
        this.guiLockKey = new NamespacedKey(plugin, "rsx_gui_locked");
        this.markerKey = new NamespacedKey(plugin, "rsx_marker");
    }

    public void startSession(Player p, String mobKey) {
        int required = config.getMobMap().getOrDefault(mobKey, 1);
        sessions.put(p.getUniqueId(), new Session(mobKey, required));
        p.openInventory(plugin.getGuiManager().buildExchangeInventory(p, mobKey, required));
        // Refresh UI immediately to show Provided/Required on marker and correct confirm state
        Bukkit.getScheduler().runTask(plugin, () -> refreshConfirmState(p));
    }

    public void cancelSession(Player p) {
        // Return any non-locked items from the top inventory back to the player's inventory
        InventoryView view = p.getOpenInventory();
        if (view != null) {
            Inventory top = view.getTopInventory();
            if (top != null) {
                for (int i = 0; i < top.getSize(); i++) {
                    ItemStack it = top.getItem(i);
                    if (it == null) continue;
                    ItemMeta meta = it.getItemMeta();
                    if (meta != null && meta.getPersistentDataContainer().has(guiLockKey, PersistentDataType.STRING)) continue; // skip locked GUI items
                    // remove from GUI
                    top.setItem(i, null);
                    // try to add back to player; drop overflow
                    Map<Integer, ItemStack> leftover = p.getInventory().addItem(it);
                    if (!leftover.isEmpty()) {
                        World w = p.getWorld();
                        for (ItemStack item : leftover.values()) w.dropItemNaturally(p.getLocation(), item);
                    }
                }
            }
        }
        sessions.remove(p.getUniqueId());
    }

    public Session getSession(Player p) {
        return sessions.get(p.getUniqueId());
    }

    /**
     * Recompute provided points, toggle confirm button (gray/green), and update the marker lore with Provided/Required.
     */
    public void refreshConfirmState(Player p) {
        Session s = getSession(p);
        if (s == null) return;
        Inventory top = p.getOpenInventory().getTopInventory();
        if (top == null) return;

        int totalPoints = 0;
        Map<String,Integer> mobMap = config.getMobMap();
        for (int i = 0; i < top.getSize(); i++) {
            ItemStack it = top.getItem(i);
            if (it == null) continue;
            ItemMeta meta = it.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(guiLockKey, PersistentDataType.STRING)) continue;
            String match = matchItemToMob(it, mobMap.keySet());
            if (match != null) {
                int rate = mobMap.getOrDefault(match, 1);
                int effectiveCount = getEffectiveStackCount(it);
                totalPoints += rate * effectiveCount;
            }
        }

        // Toggle confirm button
        int confirmSlot = config.getConfirmSlot();
        ItemStack button;
        if (totalPoints == s.required) {
            button = new ItemStack(config.getItemMaterial("items.confirm-enabled", Material.GREEN_WOOL));
            ItemMeta bm = button.getItemMeta(); bm.setDisplayName(config.getItemName("items.confirm-enabled")); button.setItemMeta(bm);
            bm.getPersistentDataContainer().set(guiLockKey, PersistentDataType.STRING, "true");
            button.setItemMeta(bm);
        } else {
            button = new ItemStack(config.getItemMaterial("items.confirm-disabled", Material.GRAY_WOOL));
            ItemMeta bm = button.getItemMeta(); bm.setDisplayName(config.getItemName("items.confirm-disabled")); button.setItemMeta(bm);
            bm.getPersistentDataContainer().set(guiLockKey, PersistentDataType.STRING, "true");
            button.setItemMeta(bm);
        }
        top.setItem(confirmSlot, button);

        // Update marker lore with Provided/Required for quick feedback
        int markerIndex = -1;
        ItemStack marker = top.getItem(4);
        if (marker != null && marker.hasItemMeta()) {
            ItemMeta mm = marker.getItemMeta();
            if (mm.getPersistentDataContainer().has(markerKey, PersistentDataType.STRING)) markerIndex = 4;
        }
        if (markerIndex == -1) {
            for (int i = 0; i < top.getSize(); i++) {
                ItemStack it = top.getItem(i);
                if (it == null || !it.hasItemMeta()) continue;
                ItemMeta mt = it.getItemMeta();
                if (mt.getPersistentDataContainer().has(markerKey, PersistentDataType.STRING)) {
                    markerIndex = i;
                    marker = it;
                    break;
                }
            }
        }
        if (markerIndex != -1 && marker != null) {
            ItemMeta mm = marker.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Required: " + s.required);
            lore.add(ChatColor.GRAY + "Provided: " + totalPoints);
            mm.setLore(lore);
            mm.getPersistentDataContainer().set(guiLockKey, PersistentDataType.STRING, "true");
            // preserve marker key
            marker.setItemMeta(mm);
            top.setItem(markerIndex, marker);
        }
    }

    /**
     * Called when confirm clicked: validate content of inventory, remove items, and give target spawner via RoseStacker.
     */
    public boolean tryComplete(Player p) {
        Session s = getSession(p);
        if (s == null) return false;
        Inventory top = p.getOpenInventory().getTopInventory();
        if (top == null) return false;

        int totalPoints = 0;
        Map<Integer, ItemStack> matched = new LinkedHashMap<>();
        Map<String,Integer> mobMap = config.getMobMap();

        for (int i = 0; i < top.getSize(); i++) {
            ItemStack it = top.getItem(i);
            if (it == null) continue;
            ItemMeta meta = it.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(guiLockKey, PersistentDataType.STRING)) continue;
            String match = matchItemToMob(it, mobMap.keySet());
            if (match != null) {
                int rate = mobMap.getOrDefault(match, 1);
                int effectiveCount = getEffectiveStackCount(it);
                totalPoints += rate * effectiveCount;
                matched.put(i, it);
            }
        }

        if (totalPoints < s.required) {
            p.sendMessage(ChatColor.RED + "Not enough points. Required: " + s.required + " — provided: " + totalPoints);
            return false;
        }

        // Consume items greedily until required met
        int needed = s.required;
        for (int slot : new ArrayList<>(matched.keySet())) {
            if (needed <= 0) break;
            ItemStack is = top.getItem(slot);
            if (is == null) continue;
            String matchedMob = matchItemToMob(is, mobMap.keySet());
            int rate = mobMap.getOrDefault(matchedMob, 1);
            int amt = getEffectiveStackCount(is);
            int stackWorth = amt * rate;
            if (stackWorth <= needed) {
                // consume entire stack/item from GUI
                top.setItem(slot, null);
                needed -= stackWorth;
            } else {
                // Partial consumption: take only what is needed based on rate, return leftover to player via RoseStacker give
                int itemsToTake = (needed + rate - 1) / rate; // ceil
                int remaining = Math.max(0, amt - itemsToTake);

                // Remove item from GUI regardless; we will return leftover to player inventory
                top.setItem(slot, null);

                // If there is leftover, give it back to the player using RoseStacker command to preserve metadata
                if (remaining > 0 && matchedMob != null) {
                    boolean gaveBack = spawnerManager.giveSpawnerViaCommand(p, matchedMob, remaining);
                    if (!gaveBack) {
                        // Fallback: attempt plain spawner items with display name
                        ItemStack leftover = new ItemStack(Material.SPAWNER, remaining);
                        ItemMeta mt = leftover.getItemMeta();
                        if (mt != null) {
                            mt.setDisplayName(matchedMob + " Spawner");
                            leftover.setItemMeta(mt);
                        }
                        Map<Integer, ItemStack> left = p.getInventory().addItem(leftover);
                        if (!left.isEmpty()) {
                            World w = p.getWorld();
                            for (ItemStack item : left.values()) w.dropItemNaturally(p.getLocation(), item);
                        }
                    }
                }

                needed -= itemsToTake * rate;
            }
        }

        // Give target spawner via RoseStacker command (ensures correct metadata)
        boolean gave = spawnerManager.giveSpawnerViaCommand(p, s.mobKey, 1);
        if (!gave) {
            // fallback: create plain SPAWNER item named
            ItemStack target = new ItemStack(Material.SPAWNER, 1);
            ItemMeta mt = target.getItemMeta();
            mt.setDisplayName(s.mobKey + " Spawner");
            target.setItemMeta(mt);
            Map<Integer, ItemStack> leftover = p.getInventory().addItem(target);
            if (!leftover.isEmpty()) {
                World w = p.getWorld();
                for (ItemStack item : leftover.values()) w.dropItemNaturally(p.getLocation(), item);
            }
        }

        p.sendMessage(ChatColor.GREEN + "Exchange completed for " + s.mobKey + "!");
        sessions.remove(p.getUniqueId());
        p.closeInventory();
        return true;
    }

    /**
     * Try to obtain the RoseStacker stacked item count from the ItemStack via API reflection.
     * Fallback to ItemStack#getAmount() if API is unavailable or the item is not stacked.
     */
    private int getEffectiveStackCount(ItemStack it) {
        if (it == null) return 0;
        try {
            Object api = plugin.getRsApi();
            if (api != null) {
                // Try common method names across RoseStacker versions
                java.lang.reflect.Method m = null;
                try {
                    m = api.getClass().getMethod("getStackedItem", ItemStack.class);
                } catch (NoSuchMethodException ignored) {}
                if (m != null) {
                    Object stackedItem = m.invoke(api, it);
                    if (stackedItem != null) {
                        try {
                            java.lang.reflect.Method getSize = stackedItem.getClass().getMethod("getStackSize");
                            Object sizeObj = getSize.invoke(stackedItem);
                            if (sizeObj instanceof Integer) {
                                int sz = (Integer) sizeObj;
                                if (sz > 0) return sz;
                            }
                        } catch (NoSuchMethodException ignored) {
                            // try alternate names
                            try {
                                java.lang.reflect.Method getAmount = stackedItem.getClass().getMethod("getAmount");
                                Object sizeObj = getAmount.invoke(stackedItem);
                                if (sizeObj instanceof Integer) {
                                    int sz = (Integer) sizeObj;
                                    if (sz > 0) return sz;
                                }
                            } catch (NoSuchMethodException ignored2) {}
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        // Heuristic fallback for RoseStacker-style display names
        try {
            if (it.getType() == Material.SPAWNER && it.hasItemMeta()) {
                ItemMeta meta = it.getItemMeta();
                String dn = meta.hasDisplayName() ? ChatColor.stripColor(meta.getDisplayName()) : "";
                if (dn != null && !dn.isEmpty()) {
                    // Patterns: "6 x Zombie Spawner" (prefix) or "Zombie Spawner x6" (suffix)
                    Pattern prefix = Pattern.compile("^\\s*(\\d+)\\s*(?:x|X|×)\\s+.*");
                    Matcher mp = prefix.matcher(dn);
                    if (mp.matches()) {
                        int val = Integer.parseInt(mp.group(1));
                        if (val > 0) return val;
                    }
                    Pattern suffix = Pattern.compile(".*(?:x|X|×)\\s*(\\d+)\\s*$");
                    Matcher ms = suffix.matcher(dn);
                    if (ms.matches()) {
                        int val = Integer.parseInt(ms.group(1));
                        if (val > 0) return val;
                    }
                }
                // Check lore lines for any integer as a last resort
                if (meta.hasLore()) {
                    for (String line : meta.getLore()) {
                        String stripped = ChatColor.stripColor(line);
                        Matcher mNum = Pattern.compile(".*?(\\d+).*?").matcher(stripped);
                        if (mNum.matches()) {
                            int val = Integer.parseInt(mNum.group(1));
                            if (val > 0) return val;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        // Fallback to vanilla amount
        return it.getAmount();
    }

    private String matchItemToMob(ItemStack it, Set<String> mobKeys) {
        if (it == null) return null;
        // Tighten: only count SPAWNER items to avoid eggs/other icons
        if (it.getType() != Material.SPAWNER) return null;
        if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) {
            String dn = it.getItemMeta().getDisplayName().toUpperCase(Locale.ROOT);
            for (String mob : mobKeys) {
                if (dn.contains(mob)) return mob;
            }
        }
        return null;
    }

    public static class Session {
        public final String mobKey;
        public final int required;
        public Session(String mobKey, int required) {
            this.mobKey = mobKey;
            this.required = required;
        }
    }
}
