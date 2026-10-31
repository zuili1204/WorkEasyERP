# WorkEasyERP

> 一套自研的全栈 **ERP + OA 一体化**企业管理平台（演示 / 学习用途）。
> 核心理念：**业务流驱动财务流** —— 以可视化工作流引擎串联 HR、OA 审批、进销存、财务与 CRM，并实现业财自动核算。

---

## ✨ 特性

- 🔧 **可视化工作流 / BPM 引擎**：节点拖拽设计、条件分支、审批人按角色/规则解析，支撑复杂审批链。
- 💰 **业财一体化**：出入库审核自动生成应收 / 应付 / 库存 / 成本，业务与财务数据闭环。
- 👥 **HR 全链路**：员工档案、部门、请假、考勤（打卡/班次/排班）、人事事件、薪资、个人中心。
- 📋 **OA 审批协同**：待办中心、消息中心、通用假勤（加班/补卡/外出/出差）、流程配置与单据打印（PDF）。
- 📦 **供应链**：采购申请 → 采购/销售订单 → 出入库履约 → 退货红冲、库存现量、盘点、批次效期预警。
- 🤝 **CRM**：客户、线索、商机、合同，阶段推进与转化闭环。
- 📱 **B2B 订货商城**：微信小程序（uni-app）+ ERP 商城后台，商品 / 购物车 / 订单 / 账期与微信支付 / 对账单 / 售后全链路。
- 🔐 **字段级权限脱敏**：成本 / 毛利 / 薪资等敏感字段按角色脱敏。
- 🌐 **多币种汇率**：多币种维护与汇率换算，适配外贸场景。
- 📊 **自定义报表 + 仪表盘**：操作审计、灵活报表、工作台看板。

---

## 🖼️ 界面预览

> 以下截图取自真实运行环境（演示种子数据），界面统一采用「墨青工业风」设计令牌：墨青深色导航、纸白底纹与铜橙强调色，数字统一等宽呈现。

![工作台](screenshots/we-dashboard.png)

**工作台** · 经营概览卡片、近 6 月营收毛利趋势、应收 Top5

| | |
|---|---|
| ![登录页](screenshots/we-login.png) | ![可视化流程设计器](screenshots/we-workflow-designer.png) |
| **登录页** · 角色卡片一键体验 | **可视化流程设计器** · 节点库 / 流程画布 / 节点属性 |
| ![员工档案](screenshots/we-employees.png) | ![排班日历](screenshots/we-duty-calendar.png) |
| **员工档案** · 数据范围按角色切换 | **排班 / 值班日历** · 月度视图与人员值班 |
| ![多币种汇率](screenshots/we-fx.png) | ![角色与权限](screenshots/we-role-perm.png) |
| **多币种汇率** · 币种卡片与汇率维护 | **角色与权限** · 按角色勾选授权 |

### 📱 小程序 · B2B 订货商城

> 微信小程序（uni-app）沿用同一套「墨青工业风」令牌：墨青会员卡、纸白商品卡、铜橙价格与等宽数字；价格由后端按协议价 / 等级价实时计算。以下截图取自 uni-app H5 渲染（同一套代码运行于微信小程序）。

| | |
|---|---|
| ![微信登录](screenshots/we-mall-login.png) | ![选择商户](screenshots/we-mall-tenant.png) |
| **微信登录** · 授权后进入 | **选择商户** · 多租户切换 / 邀请码加入 |
| ![商城首页](screenshots/we-mall-home.png) | ![商品详情](screenshots/we-mall-product.png) |
| **商城首页** · 轮播、分类与专属价 | **商品详情** · 起订量、步进与可售量 |
| ![购物车](screenshots/we-mall-cart.png) | ![确认订单](screenshots/we-mall-confirm.png) |
| **购物车** · 勾选与实时小计 | **确认订单** · 微信 / 账期 / 线下打款 |
| ![支付结果](screenshots/we-mall-result.png) | ![我的订单](screenshots/we-mall-orders.png) |
| **支付结果** · 支付成功即生成销售订单 | **我的订单** · 全状态筛选 |
| ![对账单](screenshots/we-mall-statement.png) | ![收货地址](screenshots/we-mall-address.png) |
| **对账单** · 授信额度与账龄 | **收货地址** · 默认地址与多地址管理 |

![我的](screenshots/we-mall-mine.png)

**我的** · 墨青会员卡、订单快捷入口与对账单 / 售后 / 地址菜单

---

## 🧱 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3 · Vite · TypeScript · Vue Router · Axios · 原生 CSS（设计令牌 `web/src/styles/tokens.css`） |
| 小程序 | uni-app（Vue 3）· 微信小程序 · SCSS 设计变量 `miniapp/uni.scss` |
| 后端 | Kotlin · Spring Boot 3.4 · Spring Data JPA · Flyway · JWT |
| 数据库 | PostgreSQL 15 |
| 其他 | Apache PDFBox（单据打印）· Apache POI（Excel 导出）· spring-security-crypto（口令加密） |

---

## 📁 目录结构

```text
WorkEasyERP/
├── web/                 # 前端：Vue 3 + Vite + TypeScript
│   └── src/
│       ├── views/       # 业务视图
│       ├── components/  # 通用组件（TableShell / AppModal / AppPager / EmptyState …）
│       ├── utils/       # 表单注册表、菜单配置、格式化与状态字典
│       └── styles/      # 设计令牌 tokens.css
├── miniapp/             # B2B 订货商城：uni-app（Vue 3）微信小程序
│   └── pages/           # 首页 / 商品 / 购物车 / 订单 / 对账单 / 售后 / 地址 …
├── server/              # 后端：Kotlin + Spring Boot（Gradle 包装器已自带）
│   └── src/main/resources/db/migration/
│       ├── platform/    # 平台库迁移（租户注册 / 平台会员 / 渠道配置）
│       └── tenant/      # 租户库迁移 V1–V29（含种子数据，每个租户库各跑一次）
├── screenshots/    # README 界面预览截图
├── docker-compose.yml   # PostgreSQL 15 一键起库
└── README.md
```

---

## 🚀 快速开始

### 环境要求
- **JDK 21**
- **Node.js** 18+（建议 20+）
- **PostgreSQL 15**（可直接用下方 docker-compose）
- Gradle（项目已内置 wrapper，无需单独安装）

### 1. 启动数据库
```bash
docker compose up -d
```
> 数据库名、用户名等默认值见仓库内 `docker-compose.yml` 与 `server/src/main/resources/application.yml`，**生产部署请务必修改**。

### 2. 启动后端
```bash
cd server
./gradlew bootRun
```
- 默认端口：`18080`，上下文路径：`/api`
- 首次启动自动创建平台库 `workeasy_platform`（复用 `spring.datasource` 账号，需 **CREATEDB** 权限；Docker 默认账号为超级用户，无需额外配置），登记 `default` 租户，再执行租户库 `db/migration/tenant` 迁移与种子数据。
- 已有 `workeasy` 库会原地升级：迁移文件搬家不改变版本号与内容，`flyway_schema_history` 不会重复执行。

### 3. 启动前端
```bash
cd web
npm install
npm run dev
```
- 默认端口：`5180`，已配置 `/api` 代理到后端 `18080`。
- 浏览器访问：<http://127.0.0.1:5180>

### 演示数据
首次启动由服务端自动初始化演示组织、部门、员工与账号（见 `server` 端 `DataInitializer`，**仅 `dev` profile 且用户表为空时执行**）。生产环境设置 `SPRING_PROFILES_ACTIVE=prod` 即关闭演示数据，改由租户开通接口创建管理员。**演示口令为开发默认值，生产环境请立即修改。**

### 生产构建
```bash
# 前端：产物输出到 web/dist/
cd web && npm run build

# 后端：产物输出到 server/build/libs/
cd server && ./gradlew bootJar
```

---

## 🧩 模块概览

| 域 | 主要模块 |
|---|---|
| HR / 人事 | 员工、部门、请假、考勤、人事事件、薪资、个人中心 |
| OA / 审批 | 待办审批、消息中心、通用假勤、流程配置与可视化设计器、单据打印 |
| 财务 | 应收/应付台账、收付款核销、发票、采购/销售对账、报销 |
| 供应链 | 采购申请、采购/销售订单、出入库履约、退货、库存、盘点、批次效期 |
| CRM | 客户、线索、商机、合同 |
| B2B 商城 | 小程序订货（商品 / 购物车 / 订单 / 售后）、商城后台（绑定审核 / 商城订单 / 支付流水 / Banner / 邀请码） |
| 系统 | 角色权限、操作审计、基础数据、多币种汇率、附件 |

---

## 🏗️ 架构要点

- **工作流引擎**：可视化节点设计 + 条件分支 + 审批人解析，承载复杂审批链与可审计的节点权限/时效。
- **业财一体化**：业务动作（如入库/出库审核）触发财务凭证自动生成，形成「业务流 → 财务流 → 数据流」闭环。
- **安全**：JWT 鉴权；字段级脱敏（成本/毛利/薪资按角色）；统一异常处理不向客户端泄露堆栈与内部细节。

### 多租户（SaaS 阶段一）
- **一租户一库**：平台库 `workeasy_platform` 只存租户注册、平台会员与支付渠道配置；业务表全部落在租户自己的 database。
- **运行时路由**：`TenantContext`（请求线程）→ `TenantRoutingDataSource` → 租户 Hikari 池；`default` 租户复用 `spring.datasource`，单租户部署行为不变。
- **租户解析顺序**：员工 JWT 的 `tid`（权威）> 未登录接口的 `X-Tenant-Code` > 开发默认 `default`。带 `tid` 的请求若显式传其他 `X-Tenant-Code` 一律 403，禁止仅凭客户端参数跨租户。
- **两套迁移**：`db/migration/platform` 与 `db/migration/tenant` 由 `TenantBootstrap` 显式执行，启动顺序不依赖 Flyway 自动配置。
- **附件隔离**：非 default 租户落盘到 `data/uploads/{tenantCode}/`，default 沿用原目录，历史附件可继续读取。

租户开通（平台令牌鉴权，幂等、可重试，过程写 `tenant_provision_log`）：

```bash
curl -X POST http://127.0.0.1:18080/api/platform/tenants \
  -H "Content-Type: application/json" \
  -H "X-Platform-Token: workeasy-dev-platform-token" \
  -d '{"code":"acme","name":"Acme 贸易","adminUsername":"admin","adminPassword":"acme123456"}'

# 登录新租户（也可在请求体传 tenantCode）
curl -X POST http://127.0.0.1:18080/api/auth/login \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Code: acme" \
  -d '{"username":"admin","password":"acme123456"}'
```

### 前端工程化
- **声明式表单**：各类「新建 / 发起」表单的字段、校验与提交登记在 `web/src/utils/entityForms.ts`，由统一的 `EntityCreateView` 渲染，避免每个视图重复实现一套表单逻辑。
- **统一列表外壳**：`components/TableShell.vue` 收口「表头 + 空态 + 加载态 + 分页」，各视图只提供自身列与行，全站表格结构与反馈一致。
- **菜单单点配置**：`web/src/utils/menu.ts` 是菜单唯一数据源，侧边栏渲染与顶栏全局搜索共用，避免与路由双份维护。
- **设计令牌**：颜色、间距、字号、圆角、阴影与层级统一定义在 `web/src/styles/tokens.css`。

---

## ⚠️ 部署前注意事项（重要）

1. **修改敏感配置**：上线前务必替换 `application.yml` 中的 JWT 签名密钥、数据库口令、平台库口令与 `workeasy.platform.admin-token`（当前为开发默认值）。
2. **外部化配置**：建议通过环境变量或配置中心注入密钥/口令，避免提交到仓库；平台库可用 `PLATFORM_DB_URL` / `PLATFORM_DB_USERNAME` / `PLATFORM_DB_PASSWORD` 覆盖，租户口令密文密钥用 `TENANT_DB_SECRET`。
3. **忽略构建产物**：上传仓库前请添加 `.gitignore`，忽略 `server/build/`、`server/data/`、`server/boot.log`、`web/node_modules/`、`web/dist/` 等。
4. **开发脚本**：根目录下的 `*.sql` 编码修复脚本与 `endpoints.txt` 为本地排查用产物，请勿纳入生产部署。

---

## 🗺️ 下一阶段开发计划

定位从「通用 ERP」收敛为 **商业进销存 + B2B 订货商城 + SaaS 多租户**，生产制造（MRP / 工单 / 质检）不在本期规划内。

- 🏢 **SaaS 多租户**：平台库 + 租户独立数据库；租户开通、停用、备份与数据源路由，现有单租户部署保持无感运行。
- 🧩 **商业进销存深化**：商品图片与上下架、等级价 / 协议价、库存锁定、调拨单、授信闭环、商品动销与库存周转报表。
- 📱 **B2B 订货商城**：**平台统一微信小程序**（uni-app / HBuilderX，租户在小程序内选择切换）+ ERP 商城后台，覆盖商品、购物车、订单、账期 / 微信支付、对账单与售后（小程序端与商城后台已落地，持续补全）。
- 🔗 **线上线下一体化**：商城订单支付成功自动生成销售订单、锁定库存并写入应收，发货走既有出库与履约流程。

完整方案、阶段拆分与验收标准见 [DOCS/下一阶段开发计划.md](DOCS/下一阶段开发计划.md)。

> 💬 **定制需求**：如有个性化功能定制、私有化部署或行业适配需求，欢迎在仓库 Issue / 讨论区留言，我们会跟进评估。

---

## 📌 许可与免责

- 本项目当前用于**演示与学习**。如需商用或二次分发，请自行确认并补充合适的开源许可证。
- 当前为原型/演示阶段，部分企业级能力（如生产制造、知识库/文档管理、即时通讯、移动端、预算管理等）尚未覆盖，后续按需扩展。

欢迎 Issue 与 PR（如用于学习或二次开发，请遵守相应许可与法律法规）。
