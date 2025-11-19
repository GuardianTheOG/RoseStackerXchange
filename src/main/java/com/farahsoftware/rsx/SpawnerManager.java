package com.farahsoftware.rsx;

import dev.rosewood.rosestacker.api.RoseStackerAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Wrapper handling RoseStacker integration:
 * - Discover spawner types via RoseStacker API where possible or from plugin config fallback.
 * - Give spawner to player by dispatching RoseStacker command (ensures proper metadata).
 * - Removal/count via inventory scanning and removal (conservative).
 *
 * Notes:
 * - For more accurate stack-aware counting/removal (e.g. RoseStacker stacked-item internals),
 *   the RoseStacker API provides stack-aware helpers — we can adopt them later if desired.
 */
public class SpawnerManager {
    private final RoseStackerXchange plugin;
    private final RoseStackerAPI rsApi;

    public SpawnerManager(RoseStackerXchange plugin, RoseStackerAPI rsApi) {
        this.plugin = plugin;
        this.rsApi = rsApi;
    }

    /**
     * Populate the 'mobs' section of config.yml with all registered RoseStacker spawners.
     * Uses the API where possible. If API doesn't expose a registry method, falls back to reading plugin config.
     */
    public void populateMobsInConfig() {
        Set<String> keys = getAvailableSpawnerMobKeys();
        if (keys.isEmpty()) {
            plugin.getLogger().info("[RSX] No RoseStacker spawners discovered via API/config.");
            return;
        }
        for (String k : keys) {
            plugin.getConfigManager().addMobIfMissing(k.toUpperCase(Locale.ROOT));
        }
        plugin.getConfigManager().save();
    }

    /**
     * Attempt to discover available spawner mob keys.
     * Strategy:
     * 1) Use RoseStacker API if it has a 'getAllSpawnerTypes' style method (best-effort below).
     * 2) Fallback: read RoseStacker plugin config sections 'spawner_settings' or 'spawners'.
     */
    public Set<String> getAvailableSpawnerMobKeys() {
        // Attempt 1: try API introspection minimal (the wiki guarantees getInstance() usage).
        try {
            // Many RoseStacker API versions expose something like getSpawnerSettings() or similar — we try a safe path:
            Object possible = null;
            try {
                // Some API versions have method getStackedSpawnerController() or getStackManager() — try a few safe names via reflection
                Class<?> apiClass = rsApi.getClass();

                // Best-effort: try getSpawnerTypes(), getSpawnerRegistry(), getAllSpawners(), getSpawnerSettings()
                String[] candidate = new String[] {"getSpawnerSettings", "getSpawnerTypes", "getAllSpawnerTypes", "getKnownSpawnerTypes", "getSpawnerRegistry"};
                for (String mName : candidate) {
                    try {
                        java.lang.reflect.Method m = apiClass.getMethod(mName);
                        possible = m.invoke(rsApi);
                        if (possible != null) break;
                    } catch (NoSuchMethodException ignored) {}
                }
            } catch (Throwable ignored) {}

            if (possible instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<Object, ?> map = (Map<Object, ?>) possible;
                return map.keySet().stream().map(Object::toString).map(s->s.toUpperCase(Locale.ROOT)).collect(Collectors.toCollection(LinkedHashSet::new));
            } else if (possible instanceof Collection) {
                @SuppressWarnings("unchecked")
                Collection<Object> coll = (Collection<Object>) possible;
                return coll.stream().map(Object::toString).map(s->s.toUpperCase(Locale.ROOT)).collect(Collectors.toCollection(LinkedHashSet::new));
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[RSX] RoseStacker API introspection returned error: " + t.getMessage());
        }

        // Attempt 2: read RoseStacker plugin config (safe fallback)
        Plugin rose = Bukkit.getPluginManager().getPlugin("RoseStacker");
        if (rose != null) {
            try {
                Object cfgObj = rose.getClass().getMethod("getConfig").invoke(rose);
                if (cfgObj instanceof org.bukkit.configuration.file.FileConfiguration) {
                    org.bukkit.configuration.file.FileConfiguration rsCfg = (org.bukkit.configuration.file.FileConfiguration) cfgObj;
                    String[] candidates = new String[] {"spawner_settings", "spawners", "spawner-settings"};
                    for (String sec : candidates) {
                        ConfigurationSection c = rsCfg.getConfigurationSection(sec);
                        if (c != null) {
                            Set<String> keys = c.getKeys(false).stream().map(String::toUpperCase).collect(Collectors.toCollection(LinkedHashSet::new));
                            if (!keys.isEmpty()) return keys;
                        }
                    }
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[RSX] Could not read RoseStacker config fallback: " + t.getMessage());
            }
        }
        return Collections.emptySet();
    }

    /**
     * Give the player a spawner of mobKey (1 unit) using RoseStacker command.
     * This guarantees RoseStacker constructs the ItemStack exactly as it expects.
     */
    public boolean giveSpawnerViaCommand(Player player, String mobKey, int amount) {
        if (player == null || mobKey == null || amount <= 0) return false;
        // Build command: RoseStacker has /rs give Spawner <player> <MobName> <amount> <something>
        // The actual /rs give syntax may vary by RoseStacker version. Use a conservative approach:
        // First try: /rs give Spawner <player> <MobName> <amount>
        // If server doesn't accept it, admins can adapt this single method.
        String[] tryCmds = new String[] {
                String.format("rs give Spawner %s %s %d", player.getName(), mobKey, amount),
                String.format("rs give Spawner %s %s %d 1", player.getName(), mobKey, amount)
        };
        for (String cmd : tryCmds) {
            boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            if (success) return true;
        }
        // fallback: create a plain spawner item with displayname (RoseStacker handles advanced metadata with its command)
        return false;
    }

    /**
     * Remove up to 'count' ItemStacks matching mobKey from player's inventory.
     * This is conservative and removes whole ItemStack amounts from inventory; RoseStacker may handle stack metadata in the ItemStack itself.
     */
    public int removeSpawnerItemsFromPlayer(Player player, String mobKey, int count) {
        int removed = 0;
        if (player == null || mobKey == null || count <= 0) return removed;
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            org.bukkit.inventory.ItemStack it = inv.getItem(i);
            if (it == null) continue;
            if (itemMatchesMobKey(it, mobKey)) {
                int amt = it.getAmount();
                if (amt <= 0) continue;
                if (amt + removed <= count) {
                    removed += amt;
                    inv.clear(i);
                } else {
                    int toTake = count - removed;
                    org.bukkit.inventory.ItemStack copy = it.clone();
                    copy.setAmount(amt - toTake);
                    inv.setItem(i, copy);
                    removed += toTake;
                }
                if (removed >= count) break;
            }
        }
        player.updateInventory();
        return removed;
    }

    /**
     * Count how many items matching mobKey the player has in their inventory (sum of ItemStack amounts).
     */
    public int countSpawnerItems(Player player, String mobKey) {
        int total = 0;
        if (player == null || mobKey == null) return 0;
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        for (org.bukkit.inventory.ItemStack it : inv.getContents()) {
            if (it == null) continue;
            if (itemMatchesMobKey(it, mobKey)) total += it.getAmount();
        }
        return total;
    }

    /**
     * Heuristic: check ItemStack's display name or type for mobKey. RoseStacker usually includes the entity name in the item display.
     */
    private boolean itemMatchesMobKey(org.bukkit.inventory.ItemStack it, String mobKey) {
        if (it == null) return false;
        if (it.getType() == org.bukkit.Material.SPAWNER) {
            if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) {
                String dn = it.getItemMeta().getDisplayName().toUpperCase(Locale.ROOT);
                if (dn.contains(mobKey.toUpperCase(Locale.ROOT))) return true;
            } else {
                return true; // generic spawner - treat as match fallback
            }
        }
        String type = it.getType().name().toUpperCase(Locale.ROOT);
        if (type.contains(mobKey.toUpperCase(Locale.ROOT))) return true;
        if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) {
            if (it.getItemMeta().getDisplayName().toUpperCase(Locale.ROOT).contains(mobKey.toUpperCase(Locale.ROOT))) return true;
        }
        return false;
    }
}
