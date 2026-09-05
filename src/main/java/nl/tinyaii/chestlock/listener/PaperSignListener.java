package nl.tinyaii.chestlock.listener;

import nl.tinyaii.chestlock.ChestLockPlugin;
import nl.tinyaii.chestlock.data.LockManager;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSignOpenEvent;

/**
 * 拦截原版牌子编辑框弹出（Paper 1.20+ PlayerSignOpenEvent）。
 * 目的：蹲下贴牌自动锁箱后，不弹输入文字界面（锁牌文字自动写好）。
 * 只拦截"紧邻容器/已是锁牌"的牌子，普通牌子照常可编辑。
 */
public class PaperSignListener implements Listener {

    private final ChestLockPlugin plugin;
    private final LockManager manager;

    public PaperSignListener(ChestLockPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getLockManager();
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignOpen(PlayerSignOpenEvent e) {
        if (e.getCause() != PlayerSignOpenEvent.Cause.PLACE) return;   // 只在放置时拦截，右击编辑不拦
        Block signBlock = e.getSign().getBlock();
        if (!(signBlock.getState() instanceof Sign)) return;
        // 锁牌（已含 [锁]）或 紧邻容器的牌子 → 取消编辑框
        Sign sign = (Sign) signBlock.getState();
        if (sign.getLine(0) != null && sign.getLine(0).contains("锁")) {
            e.setCancelled(true);
            return;
        }
        // 紧邻容器（刚蹲下贴的牌，自动写锁文字前弹框 → 拦掉）
        Block container = findAttachedContainer(signBlock);
        if (container != null && isContainer(container.getType())) {
            e.setCancelled(true);
        }
    }

    private Block findAttachedContainer(Block signBlock) {
        org.bukkit.block.BlockFace[] faces = {
                org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST,
                org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN};
        for (org.bukkit.block.BlockFace f : faces) {
            Block rel = signBlock.getRelative(f);
            if (isContainer(rel.getType())) return rel;
        }
        return null;
    }

    private boolean isContainer(org.bukkit.Material m) {
        return m == org.bukkit.Material.CHEST || m == org.bukkit.Material.TRAPPED_CHEST
                || m == org.bukkit.Material.BARREL
                || m == org.bukkit.Material.FURNACE || m == org.bukkit.Material.BLAST_FURNACE
                || m == org.bukkit.Material.SMOKER || m == org.bukkit.Material.BREWING_STAND
                || m == org.bukkit.Material.HOPPER || m == org.bukkit.Material.DISPENSER
                || m == org.bukkit.Material.DROPPER
                || m == org.bukkit.Material.ANVIL || m == org.bukkit.Material.CHIPPED_ANVIL
                || m == org.bukkit.Material.DAMAGED_ANVIL
                || (plugin.getConfig().getBoolean("settings.allow-shulker", true)
                    && m.name().endsWith("_SHULKER_BOX"));
    }
}
