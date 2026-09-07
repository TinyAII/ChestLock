package nl.tinyaii.chestlock.listener;

import nl.tinyaii.chestlock.ChestLockPlugin;
import nl.tinyaii.chestlock.data.LockManager;
import nl.tinyaii.chestlock.util.Messages;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSignOpenEvent;

import java.util.UUID;

/**
 * 拦截原版牌子编辑框弹出（Paper 1.20+ PlayerSignOpenEvent）。
 * 目的：蹲下贴牌自动锁箱/门后，不弹输入文字界面（锁牌文字自动写好）。
 * 锁牌/扩展牌：只有主人能编辑（第3/4行授权），非主人完全打不开编辑框。
 * 普通牌子照常可编辑。
 */
public class PaperSignListener implements Listener {

    private final ChestLockPlugin plugin;
    private final LockManager manager;

    public PaperSignListener(ChestLockPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getLockManager();
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSignOpen(PlayerSignOpenEvent e) {
        // 只拦截放置时自动弹出的框（Cause.PLACE），右击已有牌子打开编辑不拦（玩家主动操作）
        if (e.getCause() != PlayerSignOpenEvent.Cause.PLACE) return;
        Block signBlock = e.getSign().getBlock();
        if (!(signBlock.getState() instanceof Sign)) return;
        Sign sign = (Sign) signBlock.getState();

        // 已含 [锁] 的锁牌 → 非主人不能打开编辑框
        if (sign.getLine(0) != null && sign.getLine(0).contains("锁")) {
            Player p = e.getPlayer();
            UUID owner = manager.getOwnerFromSign(signBlock);
            if (owner != null && !owner.equals(p.getUniqueId())
                    && !p.isOp() && !p.hasPermission("chestlock.bypass")) {
                e.setCancelled(true);
                p.sendMessage(Messages.color("&c不能编辑他人锁牌。"));
            }
            // 主人放行（允许打开编辑框改第3/4行授权名）
            return;
        }

        // 紧邻容器/门的牌子（刚放的锁牌，文字1 tick后才自动写好 → 先拦掉编辑框）
        // 主人放的也拦（蹲下贴牌自动上锁，不需要弹编辑框）
        Block target = findAttachedLockable(signBlock);
        if (target != null) {
            e.setCancelled(true);
        }
    }

    /** 找相邻可锁目标（容器 + 门） */
    private Block findAttachedLockable(Block signBlock) {
        org.bukkit.block.BlockFace[] faces = {
                org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST,
                org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN};
        for (org.bukkit.block.BlockFace f : faces) {
            Block rel = signBlock.getRelative(f);
            if (isLockable(rel.getType())) return rel;
        }
        return null;
    }

    private boolean isLockable(org.bukkit.Material m) {
        return isContainer(m) || isDoor(m);
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

    private boolean isDoor(org.bukkit.Material m) {
        return m != null && m.name().endsWith("_DOOR");
    }
}
