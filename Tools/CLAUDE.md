# Tool 專案說明

## 專案簡介

給開發者使用的 Java Swing 桌面工具箱，用於輔助 AM（資產管理）系統開發。
功能集中在程式碼產生、SQL 轉換、資料查詢等重複性工作的自動化。

- **JDK 17**
- **主程式入口**：`src/tool/Main.java`（JFrame + JTabbedPane，每個 Tab 為一個獨立工具）
- **設定檔**：`jdbc.properties`（DB 帳密，密碼用 jasypt 加密）、`spring.xml`（Spring DataSource bean）

---

## 套件結構

| 套件 | 用途 |
|------|------|
| `tool` | 主程式入口（Main.java） |
| `tool.logic` | 各工具的業務邏輯 |
| `tool.swing` | 各工具的 Swing UI 面板 |
| `tool.helper` | 共用工具類別 |
| `redmine` | Redmine 整合（UpdateRedmine） |
| `utils` | Excel 工具（AmExcelUtils） |

---

## 各 Tab 工具

| Tab 標題 | UI 類別 | 邏輯類別 | 功能說明 |
|----------|---------|---------|---------|
| SQL 格式化 | `SqlFmtMain` | `SqlFormat` | SQL 格式化、自動補參數宣告（CONTR_AUT_OWN_USR_CDE 等固定 mapping） |
| 加密/解密 | `Encrypt` | - | 文字加密/解密 |
| 資料備份 | `TableBackupMain` | `TableBackup` | 資料表備份 |
| 報表LOG | `RptLogToolMain` | `RptLogTool` | 查詢報表執行 LOG |
| DTO產生 | `DtoMakerMain` | `DtoMaker` | 從 JSP 表單欄位產生 DTO 類別（支援 Struts 1/2、HTML input） |
| get/setter註解 | `GetterSetterCommentMain` | `GetterSetterComment` | 將 field 的 JavaDoc/行內註解自動補到 getter/setter 上 |
| Bean產生 | `BeanMakerMain` | `BeanMaker` | 從 SQL SELECT 產生 Java Bean（含欄位型別推斷） |
| daoSQL轉換 | `DaoSqlMain` | `DaoSqlConverter` | 舊 DAO（StringBuffer 風格）轉換為 SQLQueryBuilder 風格 |
| BeanToSQLColumns | `BeanToSqlColumnsMain` | `BeanToSqlColumns` | 從 Bean private 欄位產生 SQL SELECT columns 與 scalarXxx 行 |

---

## 共用 Helper

### `ConnectionHelper`
- Singleton，管理各 DB 的連線（懶載入）
- Spring XML 載入 DataSource bean，`@` 替換成 DB 名稱對應多個 MSSQL 資料庫
- 支援的資料來源（定義在 `Main.java`）：永豐、玉山、台新、中信、中信個人、LINEBANK
- 密碼用 jasypt 解密，password key 來自 `jdbc.properties` 的 `username` 欄位

### `StringHelper`
- `scalarMethod(String type)` — 將 Java 型別或 `StandardBasicTypes` 名稱對應至 `SQLQueryBuilder` 的 scalar 方法名稱
  - `String` → `scalarString`、`BigDecimal` → `scalarDecimal`、`Integer/int` → `scalarInteger`、`Timestamp/Date` → `scalarTime`

---

## DaoSqlConverter 核心邏輯

- **自動偵測輸入類型**：含 `StringBuffer`/`StringBuilder`/`SQLQueryBuilder`/`.append(` → DAO 模式；否則 → 純 SQL 模式
- **DAO 模式**：兩遍掃描（第一遍收 lang var 和 values.add()，第二遍轉換）
- **純 SQL 模式**：SELECT/WHERE/FROM 段落分開處理
- **別名處理**：`camelCaseAlias` 參數控制是否轉駝峰（UI 上有 checkbox）
- **LANG 欄位**：偵測 `_LANG\d+` suffix，自動轉為 `.appendLang("BASE_COL")` 寫法
- **'ParmX' 參數**：WHERE 條件中的 `'Parm1'`、`'ParmAbc'` 等會被替換為 `.param(parmX)` 呼叫
- **Bean 型別對應 scalar**：UI 提供 Bean路徑 + Bean名稱欄位，填入後轉換時自動讀取 bean 的 private 欄位型別，依型別決定 `scalarString`/`scalarDecimal`/`scalarInteger`/`scalarTime`；Bean路徑透過 `java.util.prefs.Preferences` 持久化

## BeanFieldParser 核心邏輯

- 在指定目錄（含子目錄）尋找 `BeanName.java`，解析 `private TYPE field;` 取得 fieldName→javaType
- 追蹤 `extends` 繼承鏈，遇到 `StandardEntity` 停止（不往上解析）
- 型別轉換委派給 `StringHelper.scalarMethod()`，支援 `String/BigDecimal/Integer/int/Timestamp/Date`
- FIELD_PATTERN 排除 `static` 欄位，允許 `final/transient/volatile` 修飾符及欄位初始值（`= ...`）
- scalar 比對用的 key 是駝峰 alias（與 bean fieldName 相同），由 `resolveScalarMethod(alias)` 查 `beanFieldTypes`
- 轉換輸出頂端會顯示診斷注解：讀到幾個欄位及前幾個欄位名稱，未找到時顯示警告
- **繼承鏈定位**：從 bean 的 `package` 宣告反推 source root；追蹤父類別時先查 `import` 精確定位，找不到再從 source root 遞迴搜尋；遇到 `StandardEntity` 停止

## BeanToSqlColumns 核心邏輯

- 從 `private TYPE fieldName;`（含選用 Javadoc）解析欄位
- 若欄位以 `Snam`/`Nam` 結尾 → 產生 `.appendLang()` 寫法
- 可傳入已存在的 SQL SELECT 段落，自動跳過已有相同別名的欄位

## DtoMaker 核心邏輯

- 支援 Struts 1/2、標準 HTML 表單元素
- `criteria.contrNo` → DTO key=criteria，field=contrNo
- `amFeeCfgMst.cycleList[0].dtlFeeItem` → 子 List DTO，key=amFeeCfgMst.cycleList
- 欄位型別優先由 entityFieldTypes 決定，次由命名慣例推斷（Amt/Cost→BigDecimal、Lsd/Days→Integer 等）

---

## 重要設定

- `jdbc.properties` 需放在執行目錄（非 classpath），由 `Paths.get("").toAbsolutePath()` 讀取
- DB 連線採 lazy 初始化，只有點選需要 DB 的功能才會連線
- `Main.tableNamesMap` / `Main.tableColumnsMap` — 全域 Map，存放從 DB 查到的資料表與欄位說明（供 DTO/Bean 產生工具使用）
