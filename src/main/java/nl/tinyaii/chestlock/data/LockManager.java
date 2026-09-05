package nl.tinyaii.chestlock.data;

import nl.tinyaii.chestlock.ChestLockPlugin;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 锁箱数据：容器坐标 → 主人UUID + 授权玩家列表，data.yml 持久化。
 */
public class LockManager {

    private final ChestLockPlugin plugin;
    private final Map<String, UUID> locks = new HashMap<>();   // "world,x,y,z" → owner
    private final Map<String, List<UUID>> access = new HashMap<>();  // "world,x,y,z" → 授权玩家
    private File file;

    public LockManager(ChestLockPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        locks.clear();
        access.clear();
        file = new File(plugin.getDataFolder(), "data.yml");
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yml.getConfigurationSection("locks");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    locks.put(key, UUID.fromString(sec.getString(key)));
                } catch (Exception ignored) {}
            }
        }
        ConfigurationSection acc = yml.getConfigurationSection("access");
        if (acc != null) {
            for (String key : acc.getKeys(false)) {
                try {
                    List<UUID> list = new ArrayList<>();
                    for (String s : acc.getStringList(key)) {
                        try { list.add(UUID.fromString(s)); } catch (Exception ignored) {}
                    }
                    access.put(key, list);
                } catch (Exception ignored) {}
            }
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, UUID> e : locks.entrySet()) {
            yml.set("locks." + e.getKey(), e.getValue().toString());
        }
        for (Map.Entry<String, List<UUID>> e : access.entrySet()) {
            List<String> list = new ArrayList<>();
            for (UUID u : e.getValue()) list.add(u.toString());
            yml.set("access." + e.getKey(), list);
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("保存锁箱数据失败: " + ex.getMessage());
        }
    }

    private String key(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public boolean isLocked(Block container) {
        return locks.containsKey(key(container.getLocation()));
    }

    public UUID getOwner(Block container) {
        return locks.get(key(container.getLocation()));
    }

    public void lock(Block container, UUID owner) {
        locks.put(key(container.getLocation()), owner);
        save();
    }

    public boolean unlock(Block container) {
        boolean ok = locks.remove(key(container.getLocation())) != null;
        access.remove(key(container.getLocation()));
        if (ok) save();
        return ok;
    }

    // ===== 授权玩家（只有打开权，不能拆）=====
    public List<UUID> getAccessList(Block container) {
        return new ArrayList<>(access.getOrDefault(key(container.getLocation()), new ArrayList<>()));
    }

    public boolean hasAccess(Block container, UUID uuid) {
        List<UUID> list = access.get(key(container.getLocation()));
        return list != null && list.contains(uuid);
    }

    public void addAccessDirect(Block container, UUID uuid) {
        List<UUID> list = access.computeIfAbsent(key(container.getLocation()), k -> new ArrayList<>());
        if (!list.contains(uuid)) list.add(uuid);
    }

    public void clearAccess(Block container) {
        access.remove(key(container.getLocation()));
    }

    /** 移除单个授权玩家（拆扩展牌时移除该牌上的授权名） */
    public void removeAccessDirect(Block container, UUID uuid) {
        List<UUID> list = access.get(key(container.getLocation()));
        if (list != null) {
            list.remove(uuid);
            if (list.isEmpty()) access.remove(key(container.getLocation()));
        }
    }

    /** 把授权玩家名拼成一行（【名1 名2 ...】） */
    public String joinAccessNames(Block container) {
        List<UUID> list = getAccessList(container);
        if (list.isEmpty()) return "【...】";
        StringBuilder sb = new StringBuilder("【");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(" ");
            String name = plugin.getServer().getOfflinePlayer(list.get(i)).getName();
            sb.append(name == null ? "?" : name);
        }
        sb.append("】");
        return sb.toString();
    }

    /** 同步容器相邻锁牌/扩展牌第3行显示授权名 */
    public void syncSignAccess(Block container) {
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
                BlockFace.UP, BlockFace.DOWN};
        for (BlockFace f : faces) {
            Block rel = container.getRelative(f);
            if (rel.getState() instanceof Sign) {
                Sign sign = (Sign) rel.getState();
                String line0 = sign.getLine(0);
                // 锁牌（含 [锁]）或扩展牌（含 【）→ 更新第3行授权
                if (line0 != null && (line0.contains("锁") || line0.contains("【"))) {
                    sign.setLine(2, org.bukkit.ChatColor.translateAlternateColorCodes('&',
                            joinAccessNames(container)));
                    sign.update(true, false);
                }
            }
        }
    }
}
