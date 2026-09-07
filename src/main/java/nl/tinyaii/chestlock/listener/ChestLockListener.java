package nl.tinyaii.chestlock.listener;

import nl.tinyaii.chestlock.ChestLockPlugin;
import nl.tinyaii.chestlock.data.LockManager;
import nl.tinyaii.chestlock.util.Messages;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * 箱子锁/门锁核心：
 *  - 蹲下贴告示牌在容器/门旁边 → 自动上锁（牌子自动写 [锁] + [主人名]，不弹编辑界面）
 *  - 门锁：牌子贴在门的前后左右任意方块上 → 锁住相邻的门；主人右键开门需权限
 *  - 权限：锁主开箱/开门正常，别人被拦
 *  - 防破坏：锁箱/锁门防 TNT/爆炸
 *  - 防漏斗：锁箱防漏斗抽取/投入
 */
public class ChestLockListener implements Listener {

    private final ChestLockPlugin plugin;
    private final LockManager manager;

    public ChestLockListener(ChestLockPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getLockManager();
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

    private boolean isDoor(Material m) {
        return m != null && m.name().endsWith("_DOOR");
    }

    /** 门是上下两格，统一取底部方块作为锁的 key（防数据错乱） */
    private Block doorBottom(Block block) {
        if (!isDoor(block.getType())) return block;
        try {
            org.bukkit.block.data.type.Door door = (org.bukkit.block.data.type.Door) block.getBlockData();
            if (door.getHalf() == org.bukkit.block.data.type.Door.Half.BOTTOM) return block;
            Block below = block.getRelative(BlockFace.DOWN);
            if (below.getType() == block.getType()) return below;
        } catch (Exception ignored) {}
        return block;
    }

    /** 可锁方块 = 容器 ∪ 门 */
    private boolean isLockable(Material m) {
        return isContainer(m) || isDoor(m);
    }

    private boolean isSign(Material m) {
        return m.name().endsWith("_SIGN") || m.name().endsWith("_WALL_SIGN");
    }

    /**
     * 蹲下贴牌建锁：玩家蹲下放告示牌在容器/门侧面 → 正常放置（客户端可见），延迟1tick自动写 [锁]+[主人名] 并建锁。
     * 不取消 BlockPlaceEvent（避免客户端不同步导致牌子消失）。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (!isSign(e.getBlock().getType())) return;        // 必须放告示牌
        Block placed = e.getBlockPlaced();

        // 找相邻容器/门
        Block target = findAttachedLockable(placed);
        if (target == null || !isLockable(target.getType())) return;
        // 箱子需要蹲下才锁，门不需要蹲下
        if (isContainer(target.getType()) && !p.isSneaking()) return;
        // 已锁 → 若主人贴 = 扩展牌
        if (manager.isLocked(target)) {
            UUID owner = manager.getOwner(target);
            if (owner != null && owner.equals(p.getUniqueId())) {
                // 主人扩展牌：第1行写 【...】，其余留空（等主人编辑写授权玩家名）
                final Block extSign = placed;
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!isSign(extSign.getType())) return;
                    Sign sign = (Sign) extSign.getState();
                    sign.setLine(0, Messages.color("&7【...】"));
                    sign.setLine(1, "");
                    sign.setLine(2, "");
                    sign.setLine(3, "");
                    sign.update(true, false);
                    p.sendMessage(Messages.color("&a已添加扩展牌，右键编辑在【】内写玩家名即可授权。"));
                }, 1L);
            } else {
                String label = isDoor(target.getType()) ? "门" : "箱子";
                e.setCancelled(true);
                p.sendMessage(Messages.color(plugin.getConfig().getString("messages.prefix", "&7[&c锁&7] &r")
                        + "&c这个" + label + "已上锁。"));
            }
            return;
        }
        // 不取消放置；延迟 1 tick 写文字 + 建锁（等牌子 TileState 就绪）
        final UUID ownerUuid = p.getUniqueId();
        final String ownerName = p.getName();
        final Block lockTarget = target;
        final Block signBlock = placed;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // 牌子被拆了/换了 → 跳过
            if (!isSign(signBlock.getType())) return;
            Sign sign = (Sign) signBlock.getState();
            String ownerLine = plugin.getConfig().getString("settings.owner-line", "&7[&e{player}&7]")
                    .replace("{player}", ownerName);
            sign.setLine(0, Messages.color("&c[锁]"));
            sign.setLine(1, Messages.color(ownerLine));
            sign.setLine(2, Messages.color("&7【...】"));   // 自动补授权行
            sign.update(true, false);
            manager.lock(lockTarget, ownerUuid);
            Player online = plugin.getServer().getPlayer(ownerUuid);
            if (online != null) {
                online.sendMessage(Messages.color(plugin.getConfig().getString("messages.locked", "&a箱子已上锁，主人: &e{player}")
                        .replace("{player}", ownerName)));
            }
        }, 1L);
    }

    /**
     * 编辑锁牌/扩展牌：第1/2行锁死（[锁]/[主人名]），第3/4行可写授权玩家名。
     * 授权名单 = 容器周围所有锁牌/扩展牌第3/4行名字的并集（编辑任意牌不丢其他牌的授权）。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignEdit(org.bukkit.event.block.SignChangeEvent e) {
        Block signBlock = e.getBlock();
        if (!isSign(signBlock.getType())) return;
        if (!(signBlock.getState() instanceof Sign)) return;
        // 只处理锁牌（第1行 [锁]）或扩展牌（第1行 【...】）
        String line0 = e.getLine(0);
        boolean isLockSign = line0 != null && line0.contains("锁");
        boolean isExtSign = line0 != null && line0.contains("【");
        if (!isLockSign && !isExtSign) return;

        Block target = findAttachedLockable(signBlock);
        if (target == null || !manager.isLocked(target)) return;

        // 只有主人能编辑授权
        Player p = e.getPlayer();
        UUID owner = manager.getOwner(target);
        if (owner == null || !owner.equals(p.getUniqueId())) {
            if (!(p.isOp() || p.hasPermission("chestlock.bypass"))) {
                e.setCancelled(true);
                p.sendMessage(Messages.color("&c只有主人能管理授权玩家。"));
                return;
            }
        }
        // 锁死第1/2行（强制写回锁牌格式）
        e.setLine(0, Messages.color("&c[锁]"));
        e.setLine(1, Messages.color(plugin.getConfig().getString("settings.owner-line", "&7[&e{player}&7]")
                .replace("{player}", plugin.getServer().getOfflinePlayer(owner).getName())));

        // 收集容器周围所有锁牌/扩展牌的第3/4行名字（含正在编辑的这张，用 e 的当前行）
        java.util.Set<UUID> allAccess = new java.util.HashSet<>();
        // 1) 当前编辑的牌子（用 e 的行）
        collectAccessFromLines(allAccess, e.getLine(2), e.getLine(3));
        // 2) 其他相邻锁牌/扩展牌（用实体 Sign 的行）
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
                BlockFace.UP, BlockFace.DOWN};
        for (BlockFace f : faces) {
            Block rel = target.getRelative(f);
            if (rel.equals(signBlock)) continue;   // 当前编辑的已处理
            if (rel.getState() instanceof Sign) {
                Sign s = (Sign) rel.getState();
                String l0 = s.getLine(0);
                if (l0 != null && (l0.contains("锁") || l0.contains("【"))) {
                    collectAccessFromLines(allAccess, s.getLine(2), s.getLine(3));
                }
            }
        }
        // 写回授权名单
        manager.clearAccess(target);
        for (UUID u : allAccess) manager.addAccessDirect(target, u);
        manager.save();
        manager.syncSignAccess(target);
        p.sendMessage(Messages.color("&a授权玩家已更新。"));
    }

    /** 从两行文本收集授权玩家名（支持【名】[名] 名 格式，空格分隔多个） */
    private void collectAccessFromLines(java.util.Set<UUID> out, String... lines) {
        for (String line : lines) {
            if (line == null) continue;
            String clean = net.md_5.bungee.api.ChatColor.stripColor(line)
                    .replace("【", "").replace("】", "").replace("[", "").replace("]", "").trim();
            if (clean.isEmpty() || clean.equals("...") || clean.equals("…")) continue;
            for (String name : clean.split(" ")) {
                name = name.trim();
                if (name.isEmpty() || name.equals("...") || name.equals("…")) continue;
                org.bukkit.OfflinePlayer op = plugin.getServer().getOfflinePlayer(name);
                if (op != null && op.getUniqueId() != null) out.add(op.getUniqueId());
            }
        }
    }

    // ===== 权限：开箱/开门 =====
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = e.getClickedBlock();
        if (clicked == null || !isLockable(clicked.getType())) return;
        // 门：统一用底部方块作 key
        Block target = isDoor(clicked.getType()) ? doorBottom(clicked) : clicked;
        if (!manager.isLocked(target)) return;
        Player p = e.getPlayer();
        UUID owner = manager.getOwner(target);
        if (owner != null && owner.equals(p.getUniqueId())) return;   // 主人开箱/开门放行
        if (manager.hasAccess(target, p.getUniqueId())) return;       // 授权玩家开箱/开门放行
        if (p.isOp() || p.hasPermission("chestlock.bypass")) return;
        e.setCancelled(true);
        String label = isDoor(clicked.getType()) ? "门" : "箱子";
        String ownerName = owner != null ? plugin.getServer().getOfflinePlayer(owner).getName() : "?";
        p.sendMessage(Messages.color(plugin.getConfig().getString("messages.denied", "&c这个{label}已上锁（主人: &e{player}&c）。")
                .replace("{label}", label).replace("{player}", ownerName)));
    }

    // ===== 防拆：箱子/门/锁牌 =====
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        boolean isDoor = isDoor(b.getType());

        // 拆门：统一用底部方块作 key
        Block target = isDoor ? doorBottom(b) : b;

        // 拆箱子/门（含潜影盒）
        if (isLockable(b.getType()) && manager.isLocked(target)) {
            UUID owner = manager.getOwner(target);
            if (owner != null && owner.equals(p.getUniqueId())) { manager.unlock(target); return; }
            if (p.isOp() || p.hasPermission("chestlock.bypass")) { manager.unlock(target); return; }
            e.setCancelled(true);
            String label = isDoor ? "门" : "箱子";
            String ownerName = owner != null ? plugin.getServer().getOfflinePlayer(owner).getName() : "?";
            p.sendMessage(Messages.color(plugin.getConfig().getString("messages.denied", "&c这个{label}已上锁（主人: &e{player}&c）。")
                    .replace("{label}", label).replace("{player}", ownerName)));
            return;
        }
        // 拆锁牌/扩展牌：主人才能拆。
        // 规则（老板定）：
        //  - 有多个牌子 → 主锁牌（第1行 [锁]）不能拆（避免拆错，从根源杜绝）
        //  - 扩展牌（第1行 【...】）可拆 → 拆了该牌上的授权玩家解除权限
        //  - 只剩主锁牌 → 可拆 → 解锁整个容器
        if (isSign(b.getType()) && b.getState() instanceof Sign) {
            Sign sign = (Sign) b.getState();
            boolean isLockSign = sign.getLine(0) != null && sign.getLine(0).contains("锁");
            boolean isExtSign = sign.getLine(0) != null && sign.getLine(0).contains("【");
            if (isLockSign || isExtSign) {
                // 门上的牌子 → 找相邻门；普通容器 → 找相邻容器
                Block attached = findAttachedLockable(b);
                if (attached != null && manager.isLocked(attached)) {
                    UUID owner = manager.getOwner(attached);
                    boolean canBreak = owner != null && owner.equals(p.getUniqueId())
                            || p.isOp() || p.hasPermission("chestlock.bypass");
                    if (!canBreak) {
                        e.setCancelled(true);
                        p.sendMessage(Messages.color("&c不能拆除他人锁牌。"));
                        return;
                    }
                    if (isLockSign) {
                        // 主锁牌：检查容器周围是否还有扩展牌
                        if (hasExtSign(attached, b)) {
                            // 还有扩展牌 → 主锁牌不能拆（避免拆错，从根源杜绝）
                            e.setCancelled(true);
                            p.sendMessage(Messages.color("&c这是主锁牌，请先拆除其他扩展牌。"));
                            return;
                        }
                        // 没有扩展牌 → 拆主锁牌 = 解锁
                        manager.unlock(attached);
                        return;
                    }
                    // 扩展牌：拆了 → 该牌上的授权玩家解除权限（重新计算剩余牌授权并集）
                    removeAccessFromSign(attached, sign);
                    manager.save();
                    manager.syncSignAccess(attached);
                }
            }
        }
    }

    /** 检查容器/门周围是否还有其他扩展牌（第1行含【，排除被拆的 b） */
    private boolean hasExtSign(Block target, Block exclude) {
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
                BlockFace.UP, BlockFace.DOWN};
        for (BlockFace f : faces) {
            Block rel = target.getRelative(f);
            if (rel.equals(exclude)) continue;
            if (rel.getState() instanceof Sign) {
                Sign sign = (Sign) rel.getState();
                String line0 = sign.getLine(0);
                if (line0 != null && line0.contains("【")) return true;
            }
        }
        return false;
    }

    /** 从被拆的扩展牌第3/4行移除对应授权玩家 */
    private void removeAccessFromSign(Block target, Sign sign) {
        java.util.Set<UUID> toRemove = new java.util.HashSet<>();
        collectAccessFromLines(toRemove, sign.getLine(2), sign.getLine(3));
        for (UUID u : toRemove) manager.removeAccessDirect(target, u);
    }

    // ===== 防破坏：爆炸 =====
    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> isLockable(b.getType()) && manager.isLocked(doorBottom(b)));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> isLockable(b.getType()) && manager.isLocked(doorBottom(b)));
    }

    // ===== 防漏斗 =====
    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent e) {
        if (e.getSource().getLocation() == null || e.getDestination().getLocation() == null) return;
        Block src = e.getSource().getLocation().getBlock();
        Block dst = e.getDestination().getLocation().getBlock();
        if ((isLockable(src.getType()) && manager.isLocked(doorBottom(src)))
                || (isLockable(dst.getType()) && manager.isLocked(doorBottom(dst)))) {
            e.setCancelled(true);
        }
    }

    // ===== 遍历牌子相邻方块找可锁目标（容器 + 门），多门时按优先级只选一个 =====
    private Block findAttachedLockable(Block signBlock) {
        // 先收集所有相邻可锁方块
        java.util.List<Block> candidates = new java.util.ArrayList<>();
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
                BlockFace.UP, BlockFace.DOWN};
        for (BlockFace f : faces) {
            Block rel = signBlock.getRelative(f);
            if (isLockable(rel.getType())) candidates.add(rel);
        }
        if (candidates.isEmpty()) return null;
        // 有已上锁的候选 → 优先返回已上锁的（防止解牌时找错目标）
        for (Block b : candidates) {
            Block norm = isDoor(b.getType()) ? doorBottom(b) : b;
            if (manager.isLocked(norm)) return norm;
        }
        // 都没锁 → 按优先级选一个（放置时用）
        java.util.Map<BlockFace, Integer> priority = new java.util.HashMap<>();
        priority.put(BlockFace.WEST, 1);   // 左
        priority.put(BlockFace.NORTH, 2);  // 前
        priority.put(BlockFace.SOUTH, 2);  // 后
        priority.put(BlockFace.EAST, 3);   // 右
        priority.put(BlockFace.UP, 4);     // 上（牌子在门上方 → 锁下面的门）
        priority.put(BlockFace.DOWN, 5);   // 下
        Block best = candidates.get(0);
        int bestP = 999;
        for (int i = 0; i < candidates.size(); i++) {
            Block b = candidates.get(i);
            BlockFace dir = faceOf(signBlock, b);
            int p = priority.getOrDefault(dir, 5);
            if (p < bestP) {
                bestP = p;
                best = b;
            }
        }
        return best;
    }

    /** 返回 rel 相对于 origin 的 BlockFace（仅水平4方向 + 上下） */
    private BlockFace faceOf(Block origin, Block rel) {
        int dx = rel.getX() - origin.getX();
        int dy = rel.getY() - origin.getY();
        int dz = rel.getZ() - origin.getZ();
        if (dx == 1) return BlockFace.EAST;
        if (dx == -1) return BlockFace.WEST;
        if (dz == 1) return BlockFace.SOUTH;
        if (dz == -1) return BlockFace.NORTH;
        if (dy == 1) return BlockFace.UP;
        if (dy == -1) return BlockFace.DOWN;
        return BlockFace.SELF;
    }

    /** 旧方法保留（兼容外部调用） */
    @Deprecated
    private Block findAttachedContainer(Block signBlock) {
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
                BlockFace.UP, BlockFace.DOWN};
        for (BlockFace f : faces) {
            Block rel = signBlock.getRelative(f);
            if (isContainer(rel.getType())) return rel;
        }
        return null;
    }
}
