# Transportation Plugin Design

本插件旨在提供一个完善的载具管理系统，涵盖载具购买、存储、召唤、销毁以及详细的日志记录功能。插件支持 **Spigot/Paper** 以及 **Folia** 服务端。

## 1. 核心功能

### 1.1 车库数据库 (Garage Database)
核心存储玩家拥有的载具信息。
- **记录时机**: 购买、绑定/解绑/转让、召唤/召回
- **字段**:
  - `id`: 主键
  - `owner_id`: 主人 UUID
  - `owner_name`: 主人 ID
  - `vehicle_identity_code`: 载具身份码 (唯一)
  - `vehicle_model`: 载具型号
  - `vehicle_model_id`: 载具型号编号
  - `stats_original`: 原版数值信息 (生命值/速度/颜色等)
  - `stats_extended`: 扩展数值信息 (涂装/皮肤/改装)
  - `is_in_garage`: 是否入库
  - `is_frozen`: 是否冻结
  - `is_destroyed`: 是否损毁

### 1.2 服务器在售载具 (Server Vehicles)
管理员维护的商店载具列表。
- **字段**:
  - `id`: 商品 ID
  - `vehicle_name`: 载具名称
  - `vehicle_model`: 型号
  - `stats_original`: 原版数值
  - `stats_extended`: 扩展数值
  - `price`: 价格

### 1.3 多平台支持
- **Paper/Spigot**: 标准 Bukkit API 支持。
- **Folia**: 完整的 Folia 区域化线程支持，使用 `RegionScheduler` 和 `AsyncScheduler` 优化性能。

### 1.4 经济系统集成
- **Vault 支持**: 购买载具时自动扣除玩家余额。

## 2. 指令系统 (Commands)

主指令: `/transportation` 或 `/tra`

| 指令 | 描述 | 权限 |
| --- | --- | --- |
| `/tra buy <型号>` | 购买指定型号的载具 | 默认 |
| `/tra bind` | 将准星对准的载具绑定为自己的私家车 | 默认 |
| `/tra unbind` | 将准星对准的私家车解绑（变为无主载具） | 默认 |
| `/tra transfer <玩家>` | 将准星对准的载具转让给指定玩家 | 默认 |
| `/tra freeze` | (管理员) 冻结准星对准的载具 | `transportation.admin` |
| `/tra unfreeze` | (管理员) 解冻准星对准的载具 | `transportation.admin` |
| `/tra out <身份码>` | 强制从车库召唤载具（需持有钥匙） | 默认 |
| `/tra in` | 将准星对准的载具收入车库 | 默认 |
| `/tra rekey` | (管理员) 重置载具身份码 | `transportation.admin` |
| `/tra help` | 查看帮助菜单 | 默认 |

**交互操作**:
- **召唤**: 右键点击绑定过载具的钥匙 (NBT数据匹配)。
- **召回**: Shift + 右键点击钥匙可远程召回载具。

## 3. 日志系统

本插件记录详尽的操作日志，确保所有行为可追溯。

### 3.1 玩家购买载具日志 (Purchase Logs)
- **触发**: 玩家购买时
- **内容**: 玩家信息、载具类型、金额、结果、失败原因

### 3.2 载具损毁日志 (Destruction Logs)
- **触发**: 载具死亡/销毁
- **内容**: 身份码、死亡时间、坐标、原因

### 3.3 载具出入库日志 (Garage Operations)
- **触发**: 召唤 / 召回
- **内容**: 操作人、载具 UUID、操作类型、时间、结果

### 3.4 载具所有权操作日志 (Ownership Operations)
- **触发**: 购买、绑定、转让、冻结等
- **内容**: 操作人、身份码、操作类别、内容、结果

### 3.5 载具使用权操作日志 (Usage Operations)
- **触发**: 骑乘载具
- **内容**: 玩家、身份码、坐标、是否有权限

### 3.6 载具身份码变更日志 (Rekey Logs)
- **触发**: `/vehicle rekey`
- **内容**: 原身份码、新身份码

## 4. 技术实现

### 4.1 数据库
使用 **SQLite** 作为默认存储（可扩展至 MySQL）。
利用 `HikariCP` 进行连接池管理，确保高性能和稳定性。

### 4.2 模块结构
- `com.github.cinnaio.transportation.database`: 负责数据库连接及 DAO 操作。
- `com.github.cinnaio.transportation.model`: 数据模型 (POJO)。
- `com.github.cinnaio.transportation.manager`: 业务逻辑层，处理购买、召唤等操作。
- `com.github.cinnaio.transportation.command`: 指令处理层。
- `com.github.cinnaio.transportation.scheduler`: 多平台调度器抽象层。

## 5. 安装与使用
1. **前置插件**: 安装 **Vault** 以及任意兼容的经济插件 (如 EssentialsX)。
2. **安装**: 将 `Transportation-1.0.0.jar` 放入 `plugins` 文件夹。
3. **启动**: 启动服务器，插件会自动生成 `transportation.db` 数据库文件和配置文件。
4. **配置**: 
   - `plugins/Transportation/config.yml`: 修改车库限制、冷却时间等。
   - `plugins/Transportation/messages.yml`: 修改语言文件（支持中文）。

## 6. 开发计划
- [x] 核心数据库设计
- [x] 基础载具管理 (召唤/召回)
- [x] 对接 Vault 经济系统
- [x] 实现指令系统 (/tra)
- [x] 支持 Folia 服务端
- [x] 交互与按键绑定
- [ ] 更多载具模型支持 (ModelEngine 等)
- [ ] 跨服同步 (MySQL)
