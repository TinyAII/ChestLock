package nl.tinyaii.chestlock;

import nl.tinyaii.chestlock.data.LockManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ChestLockPlugin extends JavaPlugin {

    private LockManager lockManager;

    @Override
    public void onEnable() {
        // TinyAII 品牌横幅 —— 必须在所有初始化逻辑之前输出（与 AutoBackup 完全一致）
        getLogger().info(" _____ _                _    ___ ___");
        getLogger().info("|_   _(_)_ __  _   _   / \\  |_ _|_ _|");
        getLogger().info("  | | | | '_ \\| | | | / _ \\  | | | |");
        getLogger().info("  | | | | | | | |_| |/ ___ \\ | | | |");
        getLogger().info("  |_| |_|_| |_|\\__, /_/   \\_\\___|___|");
        getLogger().info("               |___/");
        getLogger().info("ChestLock 箱子锁 v" + getDescription().getVersion() + " - TinyAII 出品");

        saveDefaultConfig();
        lockManager = new LockManager(this);
        lockManager.load();

        getServer().getPluginManager().registerEvents(new nl.tinyaii.chestlock.listener.ChestLockListener(this), this);

        // Paper 拦截牌子编辑框（1.21+ PlayerSignOpenEvent，1.21 起在 Bukkit 包），Spigot 无此事件 → 反射注册保护
        try {
            Class.forName("org.bukkit.event.player.PlayerSignOpenEvent");
            Class<?> hook = Class.forName("nl.tinyaii.chestlock.listener.PaperSignListener");
            java.lang.reflect.Constructor<?> ctor = hook.getConstructor(ChestLockPlugin.class);
            org.bukkit.event.Listener listener = (org.bukkit.event.Listener) ctor.newInstance(this);
            getServer().getPluginManager().registerEvents(listener, this);
            getLogger().info("[锁牌] 已启用编辑框拦截（1.21+）");
        } catch (Throwable t) {
            getLogger().info("[锁牌] 编辑框拦截不可用（非 Paper 或版本低），锁牌仍会正常上锁");
        }

        getLogger().info("箱子锁已启用。放告示牌(第一行 lock)在箱子侧面即可上锁。");
    }

    @Override
    public void onDisable() {
        if (lockManager != null) lockManager.save();
    }

    public LockManager getLockManager() { return lockManager; }
}
