# CustomMilestone

在 Paper 26.x 以上的伺服器建立自訂成就。自訂成就會註冊成真正的原版成就：達成時右上角跳出原版彈窗、聊天室出現原版格式的廣播，按 `L` 打開的成就畫面裡也有自己的分頁，DiscordSRV 會自動同步到 Discord。玩家用箱子選單查看進度、排行榜與待領取獎勵，管理員可以在遊戲內直接新增與修改成就。

## 功能特性

- **真正的原版成就**：
  - 原版 Toast 彈窗、原版聊天室廣播（遵守 `announceAdvancements` 遊戲規則）、原版成就畫面分頁。
  - DiscordSRV 本來就會同步原版成就，不需要額外設定。
  - 伺服器不支援註冊成就時自動切換成降級模式：插件自己廣播聊天室訊息、標題與音效，降級模式下也會直接送到 DiscordSRV 主頻道。
  - 只重新註冊有變更的成就，重載時不會讓線上玩家重讀整份成就資料。
- **28 種觸發類型**：
  - 破壞／放置方塊、擊殺生物／玩家、死亡、合成、熔煉、附魔、釀造、鐵砧、釣魚、繁殖、馴服、剪毛、食用、村民交易、取得物品、經驗等級、遊玩時間、登入天數、連續登入、進入世界、進入生物群系、到達座標範圍、任何原版統計、完成指定原版成就、完成自訂成就數、手動（指令）。
  - 目標支援物品／方塊／生物 ID 與標籤（例如 `#minecraft:logs`）。
  - 前置成就：完成前置後才開始計算進度。
- **村民交易條件**：
  - 可指定換到的物品、物品細節（例如經驗修補附魔書）、付出的物品、村民或流浪商人、職業、最低等級。
- **取得物品與認領記號**：
  - 玩家取得的物品計入進度時，會在物品上寫入隱藏的認領記號；別人拿到這個物品不算，自己丟掉再撿回來也不會重複計算。
  - 只標記計入進度的那幾個，成就完成後不再標記。
  - 可設定只算哪些來源：撿起、從容器拿出、合成、熔煉、釣魚、交易。
- **獎勵**：
  - 物品支援 Data Components 寫法（例如 `diamond_sword[enchantments={sharpness:5}]`），也可在編輯器把手上的物品直接設為獎勵，完整保留所有資料。
  - 後台指令（支援 `%player%`、`%uuid%`、`%milestone%`）與經驗值。
  - 背包滿時存進「待領取獎勵」，在選單裡領取，不會遺失。
- **箱子選單**：
  - 自訂分類與原版 5 個分類、進度條、隱藏成就顯示為 `???`、排行榜（可切換是否含原版）、待領取獎勵。
  - 全部左鍵操作；所有介面都有插件簽署（`InventoryHolder` 與按鈕 PDC 標籤 `custommilestone:gui`）。
- **遊戲內編輯器**：
  - 新增、修改、刪除分類與成就；名稱、說明、圖示、外框、隱藏、觸發類型、目標、數量、前置、獎勵。
  - 文字用聊天室輸入，有逾時、取消關鍵字、長度與格式驗證。修改立即寫回 YAML。
- **效能**：
  - 高頻事件在最外層先檢查「有沒有成就用到這個類型」。
  - 不監聽玩家移動；生物群系、座標、統計等用低頻定時檢查並分批處理。
  - 玩家資料用記憶體快取＋髒資料標記，定時以單一執行緒批次寫入 SQLite，登入前非同步預載。
- **其他**：PlaceholderAPI 變數、GitHub 更新檢查、`config.yml` 與 `messages.yml` 自動補齊、所有文字都可在 `messages.yml` 修改。

## 指令與權限

| 指令 | 說明 | 執行者 | 權限節點 | 預設 |
|---|---|---|---|---|
| `/cm` | 開啟成就選單 | 玩家 | `custommilestone.use` | 所有人 |
| `/cm help` | 指令說明 | 玩家／後台 | `custommilestone.help` | 所有人 |
| `/cm top` | 排行榜 | 玩家 | `custommilestone.top` | 所有人 |
| `/cm view <玩家>` | 查看其他玩家的成就 | 玩家 | `custommilestone.view.others` | OP |
| `/cm edit` | 遊戲內編輯器 | 玩家 | `custommilestone.admin.edit` | OP |
| `/cm grant <玩家> <成就>` | 給予成就 | 玩家／後台 | `custommilestone.admin.grant` | OP |
| `/cm revoke <玩家> <成就>` | 撤銷成就 | 玩家／後台 | `custommilestone.admin.grant` | OP |
| `/cm progress <玩家> <成就> <add\|set> <數量>` | 調整進度 | 玩家／後台 | `custommilestone.admin.progress` | OP |
| `/cm reset <玩家> [成就]` | 重置成就資料 | 玩家／後台 | `custommilestone.admin.reset` | OP |
| `/cm reload` | 重新載入設定與成就 | 玩家／後台 | `custommilestone.admin.reload` | OP |
| `/cm version` | 檢查更新 | 玩家／後台 | `custommilestone.admin.version` | OP |
| — | 全部管理權限 | — | `custommilestone.admin` | OP |

> 主指令 `/custommilestone`，別名 `/cm`。若其他插件也使用 `/cm`，可改用 `/custommilestone` 或 `/custommilestone:cm`。
> 擁有 `custommilestone.admin.version` 的管理員，在有新版本時登入 3 秒後會收到提醒。
> `custommilestone.admin.edit` 可以設定「以後台身分執行的獎勵指令」，請只給信任的管理員。
> 給予、撤銷、調整進度、重置都可以對離線玩家使用；離線玩家的物品獎勵會存進待領取獎勵。

## 使用範例

### 1. 在遊戲內建立成就

1. 輸入 `/cm edit`，左鍵「新增分類」，依序在聊天室輸入分類 ID 與名稱。
2. 進入分類後左鍵「新增成就」，輸入成就 ID。
3. 在成就編輯畫面設定名稱、說明、觸發類型、目標與數量。
4. 進入「獎勵」，把物品拿在手上左鍵「加入手上的物品」，或加入指令、經驗值。

```bash
/cm edit
```

### 2. 用 YAML 建立成就

1. 在 `plugins/CustomMilestone/milestones/` 新增 `mining.yml`（檔名就是分類 ID）。
2. 寫入下面的內容後輸入 `/cm reload`。

```yaml
category:
  name: "&b挖礦"
  icon: diamond_pickaxe
  order: 3
milestones:
  deep_miner:
    name: "&b深層礦工"
    description: ["挖掘 100 個深板岩"]
    icon: deepslate
    frame: goal
    trigger:
      type: BREAK_BLOCK
      targets: [deepslate]
      amount: 100
      options:
        natural-only: true
    rewards:
      exp: 200
      items:
        - "diamond_pickaxe[enchantments={efficiency:4}] x1"
      commands:
        - "say %player% 成為深層礦工！"
```

### 3. 管理玩家的成就

1. 直接給予或撤銷：

```bash
/cm grant Steve deep_miner
/cm revoke Steve deep_miner
```

2. 調整進度（適合 NPC 或其他插件串接 `MANUAL` 類型）：

```bash
/cm progress Steve deep_miner add 10
/cm progress Steve deep_miner set 50
```

3. 重置：

```bash
/cm reset Steve deep_miner
/cm reset Steve
```

## 觸發類型

| 類型 | 說明 | 目標 | 進階條件（`trigger.options`） |
|---|---|---|---|
| `BREAK_BLOCK` | 破壞方塊 | 方塊、#標籤 | `natural-only`：自己放的不算 |
| `PLACE_BLOCK` | 放置方塊 | 方塊、#標籤 | |
| `KILL_ENTITY` | 擊殺生物 | 生物、#標籤 | `exclude-spawner`：排除生怪磚／生怪蛋（預設 true） |
| `KILL_PLAYER` | 擊殺玩家 | | |
| `DEATH` | 死亡 | 死亡原因（`FALL`、`LAVA`…） | |
| `CRAFT` | 合成（Shift 合成會正確計算數量） | 物品 | |
| `SMELT` | 從熔爐取出 | 物品 | |
| `ENCHANT` | 附魔台附魔 | 物品 | `enchantments`：`[sharpness:5]` |
| `BREW` | 釀造 | `potion` 等 | `potion-types`：`[strength]` |
| `ANVIL` | 從鐵砧取出 | 物品 | |
| `FISH` | 釣魚 | 物品 | `item`：物品細節 |
| `BREED` / `TAME` / `SHEAR` | 繁殖、馴服、剪毛 | 生物 | |
| `CONSUME` | 食用 | 物品 | `item` |
| `TRADE` | 村民交易 | 換到的物品 | `item`、`merchants`（`villager`、`wandering_trader`）、`professions`、`min-level`、`costs` |
| `ACQUIRE_ITEM` | 取得物品（認領記號） | 物品 | `sources`（`PICKUP`、`CONTAINER`、`CRAFT`、`SMELT`、`FISH`、`TRADE`）、`item` |
| `LEVEL` | 經驗等級 | | |
| `PLAYTIME` | 遊玩時間（分鐘） | | |
| `LOGIN_DAYS` / `LOGIN_STREAK` | 登入天數／連續登入 | | |
| `ENTER_WORLD` | 進入世界 | 世界名稱 | |
| `ENTER_BIOME` | 進入生物群系 | 生物群系 | |
| `REACH_REGION` | 到達座標範圍 | | `world`、`min`、`max`（`x,y,z`） |
| `STATISTIC` | 任何原版統計 | | `statistic`（例如 `JUMP`）、`sub` |
| `ADVANCEMENT` | 完成指定原版成就 | 成就 ID（`story/mine_diamond`） | |
| `MILESTONE_COUNT` | 完成自訂成就數 | | `category` |
| `MANUAL` | 只能用指令推進 | | |

> 目標不填代表不限。創造與旁觀模式預設不累積進度。用 `/give` 拿到的物品不會計入「取得物品」。

## 設定檔

| `config.yml` | 說明 |
|---|---|
| `settings.timezone` | 登入天數與連續登入使用的時區 |
| `settings.ignore-creative` | 創造／旁觀模式不累積進度 |
| `display.mode` | `auto`（原版成就，失敗時降級）或 `fallback`（一律降級） |
| `display.fallback.show-title` / `sound` | 降級模式的標題與音效 |
| `storage.save-interval-seconds` | 批次寫入資料庫的間隔 |
| `rewards.pending-limit` | 每位玩家待領取獎勵上限，超過才掉在腳邊 |
| `periodic-check.interval-seconds` / `players-per-tick` | 定時檢查的間隔與每 tick 處理人數 |
| `leaderboard.refresh-minutes` / `size` | 排行榜更新間隔與顯示名次 |
| `item-claim.enabled` | 取得物品的認領記號 |
| `anti-abuse.placed-blocks-per-chunk` | 每個區塊記錄的玩家放置方塊上限 |
| `chat-input.timeout-seconds` / `cancel-keywords` | 編輯器聊天輸入的逾時與取消關鍵字 |
| `discordsrv.fallback-sync` | 降級模式時直接送到 DiscordSRV |
| `update-checker.check-on-startup` / `notify-on-join` | 啟動檢查更新與登入提醒 |

| PlaceholderAPI | 說明 |
|---|---|
| `%custommilestone_completed%` / `_total` / `_percent` | 自訂成就完成數、總數、百分比 |
| `%custommilestone_vanilla_completed%` / `_vanilla_total` | 原版成就完成數、總數 |
| `%custommilestone_progress_<id>%` / `_target_<id>` / `_done_<id>` | 某成就的進度、目標、是否完成 |
| `%custommilestone_pending%` | 待領取獎勵數 |
| `%custommilestone_login_days%` / `_login_streak` | 登入天數、連續登入 |
| `%custommilestone_top_<名次>_name%` / `_value` | 排行榜（僅自訂） |
| `%custommilestone_top_vanilla_<名次>_name%` / `_value` | 排行榜（含原版） |

> 自訂成就會以 datapack 形式存在 `world/datapacks/bukkit/data/custommilestone/`。要完全移除插件時，連同這個資料夾一起刪除。

---

# CustomMilestone (English)

Create custom advancements on Paper 26.x and later. Custom milestones are registered as real vanilla advancements: the vanilla toast, the vanilla chat announcement, their own tab in the `L` screen, and automatic DiscordSRV sync. Players use chest menus for progress, leaderboards and pending rewards; admins can create and edit milestones in game.

## Features

- **Real vanilla advancements**:
  - Vanilla toast, vanilla chat announcement (respects `announceAdvancements`), vanilla advancement tab.
  - DiscordSRV picks them up automatically.
  - Falls back to a plugin-side announcement (chat, title, sound, DiscordSRV) if the server cannot register advancements.
  - Only changed advancements are re-registered.
- **28 trigger types** with item/block/entity IDs and tags, plus prerequisites.
- **Villager trade filters**: result item and item details, cost items, villager or wandering trader, profession, minimum level.
- **Item acquisition with claim marks**: counted items get a hidden claim mark, so the same item does not count for anyone else or twice; only the counted items are marked.
- **Rewards**: items with Data Components or the exact item from your hand, console commands, experience. Rewards that do not fit go to a pending-rewards list.
- **Chest menus**: custom and vanilla categories, progress bars, hidden milestones, leaderboards, pending rewards. Left click only; every menu is signed (`InventoryHolder` and button PDC `custommilestone:gui`).
- **In-game editor** with chat input (timeout, cancel keyword, length and format validation).
- **Performance**: early returns on hot events, no move listener, batched periodic checks, in-memory cache with dirty flags and batched SQLite writes on a single thread.
- **Other**: PlaceholderAPI, GitHub update checker, auto-filled `config.yml` / `messages.yml`.

## Commands & Permissions

| Command | Description | Executor | Permission | Default |
|---|---|---|---|---|
| `/cm` | Open the menu | Player | `custommilestone.use` | Everyone |
| `/cm help` | Help | Player / Console | `custommilestone.help` | Everyone |
| `/cm top` | Leaderboard | Player | `custommilestone.top` | Everyone |
| `/cm view <player>` | View another player | Player | `custommilestone.view.others` | OP |
| `/cm edit` | In-game editor | Player | `custommilestone.admin.edit` | OP |
| `/cm grant <player> <id>` | Grant | Player / Console | `custommilestone.admin.grant` | OP |
| `/cm revoke <player> <id>` | Revoke | Player / Console | `custommilestone.admin.grant` | OP |
| `/cm progress <player> <id> <add\|set> <n>` | Change progress | Player / Console | `custommilestone.admin.progress` | OP |
| `/cm reset <player> [id]` | Reset | Player / Console | `custommilestone.admin.reset` | OP |
| `/cm reload` | Reload | Player / Console | `custommilestone.admin.reload` | OP |
| `/cm version` | Check for updates | Player / Console | `custommilestone.admin.version` | OP |

> Main command `/custommilestone`, alias `/cm`. `custommilestone.admin.edit` can set console reward commands; give it only to trusted staff.

## Usage Examples

### 1. Create a milestone in game

1. Run `/cm edit` and left-click "新增分類" (new category).
2. Open the category and left-click "新增成就" (new milestone).
3. Set the name, trigger, targets, amount and rewards.

```bash
/cm edit
```

### 2. Manage players

```bash
/cm grant Steve deep_miner
/cm progress Steve deep_miner add 10
/cm reset Steve
```

## Configurable Keys

| `config.yml` | Description |
|---|---|
| `display.mode` | `auto` (vanilla advancements, fall back on failure) or `fallback` |
| `storage.save-interval-seconds` | Batch write interval |
| `rewards.pending-limit` | Pending reward cap per player |
| `periodic-check.interval-seconds` | Periodic check interval |
| `leaderboard.refresh-minutes` | Leaderboard refresh interval |
| `item-claim.enabled` | Claim marks for acquired items |
| `update-checker.check-on-startup` | Check GitHub on startup |
