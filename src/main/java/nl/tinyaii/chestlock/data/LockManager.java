package nl.tinyaii.chestlock.data;

import nl.tinyaii.chestlock.ChestLockPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
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
 * 锁箱/锁门数据：容器/门坐标 → 主人UUID + 授权玩家列表，data.yml 持久化。
 * 门是上下两格，统一用底部方块作为 key，避免数据错乱。
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
            plugin.getLogger().severe("保存锁数据失败: " + ex.getMessage());
        }
    }

    /** 用坐标字符串作 key（调用方需传入归一化后的方块，门统一用底部） */
    private String key(Block block) {
        Location loc = block.getLocation();
        return loc.getWorld().getName() + ","
                + loc.getBlockX() + ","
                + loc.getBlockY() + ","
                + loc.getBlockZ();
    }

    /** 门 → 取底部方块；非门 → 原方块 */
    public Block doorBottom(Block block) {
        if (!isDoor(block.getType())) return block;
        try {
            org.bukkit.block.data.type.Door door = (org.bukkit.block.data.type.Door) block.getBlockData();
            if (door.getHalf() == org.bukkit.block.data.type.Door.Half.BOTTOM) return block;
            Block below = block.getRelative(BlockFace.DOWN);
            if (below.getType() == block.getType()) return below;
        } catch (Exception ignored) {}
        return block;
    }

    private boolean isDoor(Material m) {
        return m != null && m.name().endsWith("_DOOR");
    }

    public boolean isLockable(Block block) {
        return isContainer(block.getType()) || isDoor(block.getType());
    }

    private boolean isContainer(Material m) {
        return m == Material.CHEST || m == Material.TRAPPED_CHEST || m == Material.BARREL
                || m == Material.FURNACE || m == Material.BLAST_FURNACE || m == Material.SMOKER
                || m == Material.BREWING_STAND || m == Material.HOPPER
                || m == Material.DISPENSER || m == Material.DROPPER
                || m == Material.ANVIL || m == Material.CHIPPED_ANVIL || m == Material.DAMAGED_ANVIL
                || (plugin.getConfig().getBoolean("settings.allow-shulker", true) && isShulker(m));
    }

    private boolean isShulker(Material m) {
        return m.name().endsWith("_SHULKER_BOX");
    }

    public boolean isLocked(Block block) {
        return locks.containsKey(key(block));
    }

    public UUID getOwner(Block block) {
        return locks.get(key(block));
    }

    public void lock(Block block, UUID owner) {
        locks.put(key(block), owner);
        save();
    }

    public boolean unlock(Block block) {
        boolean ok = locks.remove(key(block)) != null;
        access.remove(key(block));
        if (ok) save();
        return ok;
    }

    // ===== 授权玩家（只有打开权，不能拆）=====
    public List<UUID> getAccessList(Block block) {
        return new ArrayList<>(access.getOrDefault(key(block), new ArrayList<>()));
    }

    public boolean hasAccess(Block block, UUID uuid) {
        List<UUID> list = access.get(key(block));
        return list != null && list.contains(uuid);
    }

    public void addAccessDirect(Block block, UUID uuid) {
        List<UUID> list = access.computeIfAbsent(key(block), k -> new ArrayList<>());
        if (!list.contains(uuid)) list.add(uuid);
    }

    public void clearAccess(Block block) {
        access.remove(key(block));
    }

    /** 移除单个授权玩家（拆扩展牌时移除该牌上的授权名） */
    public void removeAccessDirect(Block block, UUID uuid) {
        List<UUID> list = access.get(key(block));
        if (list != null) {
            list.remove(uuid);
            if (list.isEmpty()) access.remove(key(block));
        }
    }

    /** 把授权玩家名拼成一行（【名1 名2 ...】） */
    public String joinAccessNames(Block block) {
        List<UUID> list = getAccessList(block);
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

    /** 同步容器/门相邻锁牌/扩展牌第3行显示授权名 */
    public void syncSignAccess(Block block) {
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
                BlockFace.UP, BlockFace.DOWN};
        for (BlockFace f : faces) {
            Block rel = block.getRelative(f);
            if (rel.getState() instanceof Sign) {
                Sign sign = (Sign) rel.getState();
                String line0 = sign.getLine(0);
                // 锁牌（含 [锁]）或扩展牌（含 【）→ 更新第3行授权
                if (line0 != null && (line0.contains("锁") || line0.contains("【"))) {
                    sign.setLine(2, org.bukkit.ChatColor.translateAlternateColorCodes('&',
                            joinAccessNames(block)));
                    sign.update(true, false);
                }
            }
        }
    }

    /** 从牌子反查相邻可锁目标（容器+门），返回其主人UUID */
    public UUID getOwnerFromSign(Block signBlock) {
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
                BlockFace.UP, BlockFace.DOWN};
        for (BlockFace f : faces) {
            Block rel = signBlock.getRelative(f);
            if (isLockable(rel) && isLocked(rel)) {
                return getOwner(rel);
            }
        }
        return null;
    }
}
