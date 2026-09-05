# 箱子锁 ChestLock

贴告示牌即锁箱！蹲下（潜行）手持告示牌贴在箱子/熔炉/铁砧等容器侧面 → 自动上锁并显示主人名。授权玩家（只有打开权）、多牌扩展授权、防爆炸/防漏斗、主人保护。全中文界面，零依赖。

## 功能特性

- **蹲下贴牌锁箱**：蹲下 + 手持告示牌贴在容器侧面 → 自动上锁，牌子自动写 `[锁]` / `[主人名]`，不弹编辑界面
- **支持多种容器**：箱子/陷阱箱/木桶/潜影盒 + 熔炉/高炉/烟熏炉 + 酿造台 + 漏斗 + 发射器/投掷器 + 铁砧
- **主人保护**：只有主人能开/拆/管理；第 1/2 行锁死（主人也改不了）
- **授权玩家**：`【玩家名】` 授权（只有打开权，不能拆）；一行一个名，多牌扩展
- **多牌扩展**：蹲下再贴牌 = 扩展牌 `【...】`，继续写授权名
- **主锁牌保护**：有扩展牌时主锁牌不能拆（避免拆错）；拆扩展牌 = 解除该牌授权；只剩主锁牌才可拆 = 解锁
- **防破坏**：锁箱防 TNT/苦力怕/床爆炸、防活塞推
- **防漏斗**：锁箱防漏斗抽取/投入
- **数据持久化**：data.yml（容器→主人 + 授权名单），重启不丢

## 使用

1. 蹲下（Shift）+ 手持告示牌 → 右键贴在容器侧面 → 自动上锁，牌子变 `[锁]` / `[主人名]` / `【...】`
2. 右键锁牌编辑 → 第 3/4 行写 `【小明】` → 小明获得打开权
3. 蹲下再贴一块牌 = 扩展牌，继续写授权名
4. 主人拆扩展牌 = 解除该牌授权；拆光扩展牌后拆主锁牌 = 解锁

## 权限

- `chestlock.bypass`：无视锁（默认 OP）

## 配置（plugins/ChestLock/config.yml）

```yaml
settings:
  owner-line: "&7[&e{player}&7]"   # 主人名显示格式
  allow-shulker: true              # 潜影盒也可锁
```

## 安装

1. 下载 jar 放入 `plugins/` 目录
2. 重启服务器（或 reload）
3. 启动日志显示 TinyAII 横幅 + 箱子锁已启用

> 需要 Java 17+，支持 Paper/Spigot 1.16 ~ 26.2。零依赖。

---

# ChestLock - Sign Lock System

Place a sign to lock! Sneak + place sign on container side → auto-lock, sign shows `[锁]` / `[Owner]`. Authorized players (open-only), multi-sign expansion, explosion/hopper protection, owner protection. Zero dependency.

## Features

- Sneak + place sign on container → auto-lock, auto-write `[锁]` / `[Owner]`, no edit UI
- Containers: chest/trapped chest/barrel/shulker + furnace/blast furnace/smoker + brewing stand + hopper + dispenser/dropper + anvil
- Owner-only: open/break/manage; line 1/2 locked
- Authorize `【Player】` (open-only, cannot break); one per line, multi-sign
- Main lock sign protected while ext signs exist (avoid wrong break)
- Anti-explosion, anti-hopper
- data.yml persistence

## Usage

1. Sneak + place sign on container side → auto-lock
2. Right-click lock sign to edit → line 3/4 write `【Name】` to authorize
3. Sneak + place another sign = extension sign
4. Owner breaks ext sign = revoke its players; after all ext removed, break main sign = unlock

## Permission

- `chestlock.bypass`: bypass lock (default OP)

## Install

1. Put jar into `plugins/`
2. Restart server (or reload)
3. Startup log shows TinyAII banner + chest lock enabled

> Java 17+, Paper/Spigot 1.16 ~ 26.2. Zero dependency.

## License

MIT License - free, open source. TinyAII brand banner preserved.
