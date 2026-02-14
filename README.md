# Transportation Plugin

Transportation 是一个功能强大的 Minecraft 载具管理插件，旨在为服务器提供完善的载具购买、存储、召唤、管理及日志记录系统。

插件支持 **Spigot**, **Paper** 以及 **Folia** 服务端，并集成了 Vault 经济系统。

## ✨ 核心特性

*   **多平台支持**: 完美支持 Spigot/Paper 以及 Folia 的区域化多线程架构。
*   **车库系统**: 玩家可以拥有自己的虚拟车库，随时存取载具。
*   **载具绑定**: 将任意生物实体（如马、猪、羊驼等）绑定为私有载具。
*   **钥匙系统**: 将载具绑定到特定物品（钥匙），持有钥匙即可召唤/召回，支持右键快捷操作。
*   **经济集成**: 支持 Vault，管理员可配置可购买的载具及其价格。
*   **权限管理**: 完善的权限系统，支持转让、冻结、解冻载具。
*   **数据持久化**: 使用 SQLite 数据库存储载具数据，支持属性保存（如速度、跳跃力、马铠、鞍等）。
*   **详细日志**: 记录所有关键操作（购买、损毁、召唤、交易等），方便管理员查询。
*   **模型ID支持**: 支持通过 `model:id` 格式区分同类型不同变种的载具（如 `HORSE:fly1`）。

## 🚀 安装指南

1.  **前置插件**: 确保服务器已安装 [Vault](https://www.spigotmc.org/resources/vault.34315/) 以及任意兼容的经济插件 (如 EssentialsX)。
2.  **下载插件**: 将 `Transportation-1.0.6.jar` (版本号可能随更新变化) 放入服务器的 `plugins` 文件夹。
3.  **启动服务器**: 插件会自动加载，并生成 `transportation.db` 数据库文件和默认配置文件。
4.  **配置文件**:
    *   `config.yml`: 配置车库上限、光线追踪距离、冷却时间等。
    *   `messages.yml`: 自定义所有提示消息（默认中文）。

## 📖 使用文档

*   **我是玩家**: 请阅读 [玩家指南 (PLAYER_GUIDE.md)](PLAYER_GUIDE.md)，了解如何绑定、召唤和管理你的私人坐骑。
*   **我是管理员**: 请参阅下方的管理员手册章节。

## 🔐 权限节点 (Permissions)

| 权限节点 | 描述 | 默认拥有者 |
| :--- | :--- | :--- |
| `transportation.use` | 允许使用基础指令 (buy, bind, out, in 等) | 所有人 |
| `transportation.admin` | 允许使用管理指令 (freeze, rekey, reload) | OP |
| `transportation.limit.bypass` | 允许无视车库数量上限 | OP |
| `transportation.cooldown.bypass` | 允许无视召唤冷却时间 | OP |

## 🛠️ 管理指令

以下指令需要 `transportation.admin` 权限：

### 1. 载具监管
*   **冻结载具**: `/tra freeze <model>`
    *   **作用**: 强制冻结指定载具。冻结后，**任何人（包括主人）** 都无法召唤、骑乘或移动该载具。
    *   **场景**: 处理违规命名的载具、利用载具卡BUG的玩家，或暂时封禁某个玩家的交通工具。
*   **解冻载具**: `/tra unfreeze <model>`
    *   **作用**: 恢复载具的正常使用权。

### 2. 身份码重置
*   **重置印记**: `/tra rekey <model>`
    *   **作用**: 强制生成新的身份码 (Identity Code)。
    *   **后果**: 该载具之前绑定的所有物理钥匙（Key Items）将**立即失效**。
    *   **场景**: 玩家举报钥匙被盗、或者需要强制回收某些流失在外的钥匙使用权时。

### 3. 插件维护
*   **重载配置**: `/tra reload`
    *   **作用**: 热重载 `config.yml` 和 `messages.yml`。

## ⚙️ 配置文件详解

### `config.yml` 关键配置
```yaml
# 默认车库上限（玩家最多能拥有几辆车）
garage-limit: 5

# 召唤冷却时间（秒）
summon-cooldown: 3

# 允许绑定的实体类型白名单
allowed-entities:
  - HORSE
  - DONKEY
  - MULE
  - LLAMA
  - PIG
  - STRIDER

# 射线检测距离（用于 /tra bind 等指令瞄准实体）
raytrace-distance: 10.0
```

### `messages.yml` 本地化
所有提示信息均支持 MiniMessage 格式（支持 RGB 颜色），例如 `<color:#FF0000>错误信息`。

## 🔧 故障排查

**问题 1: 玩家无法绑定特定生物**
*   **检查**: `config.yml` 中的 `allowed-entities` 列表。
*   **解决**: 确保目标生物类型（如 `SKELETON_HORSE`）在白名单中。

**问题 2: Folia 服下实体位置异常**
*   **机制**: 插件使用 Folia 的 `RegionScheduler` 进行跨区块操作。
*   **注意**: 尽量避免在强加载区域边缘进行频繁的召唤/召回操作。

**问题 3: 数据库性能问题**
*   **机制**: 默认使用 SQLite (`transportation.db`)。
*   **建议**: 对于大型服务器（玩家数 > 50），建议在未来版本支持 MySQL 时迁移数据（目前仅支持 SQLite）。

## 📝 日志查询
插件会记录详细的操作日志到数据库的 `logs` 表中，包括：
*   `PURCHASE`: 购买记录
*   `DESTRUCTION`: 损毁记录
*   `GARAGE_OP`: 进出库记录
*   `OWNERSHIP`: 权限变更记录

如有争议，可通过查询数据库文件进行追溯。

## 🛠️ 开发与构建

本项目使用 Gradle 进行构建。

### 构建命令

```bash
./gradlew build
```

构建产物位于 `build/libs/` 目录下。

### 模块结构

*   `com.github.cinnaio.transportation.database`: 数据库连接及 DAO 层。
*   `com.github.cinnaio.transportation.model`: 数据模型 (POJO)。
*   `com.github.cinnaio.transportation.manager`: 核心业务逻辑 (Manager)。
*   `com.github.cinnaio.transportation.command`: 命令处理 (CommandExecutor)。
*   `com.github.cinnaio.transportation.scheduler`: 跨平台调度器适配 (Folia/Paper)。
*   `com.github.cinnaio.transportation.listener`: 事件监听器。

## 📝 待办计划

*   [x] 基础载具管理 (召唤/召回/车库)
*   [x] Vault 经济系统对接
*   [x] 实体交互与钥匙绑定
*   [x] Folia 服务端支持
*   [x] 载具属性持久化 (速度/跳跃/装备)
*   [x] 命名牌保护与权限控制
*   [ ] 更多自定义模型支持 (ModelEngine 等)
*   [ ] 跨服数据同步 (MySQL)
