# easy-bi

> 轻量级 BI 报表系统 — 上传 Excel/CSV，写 SQL，出报表。零配置，开箱即用。

## 特性

- **📁 一键导入** — CSV / Excel（.xls / .xlsx），每个 Sheet 自动建表，列类型自动推断
- **🔍 SQL 即席查询** — 左侧浏览表结构，右侧 CodeMirror 语法高亮编辑器，`Ctrl+Enter` 执行
- **📊 数据集管理** — 保存常用 SQL 为数据集，支持分页查看、CSV 导出
- **➕ 数据追加** — 后续上传同结构文件，按 Sheet 顺序自动追加，新增列自动扩充
- **🛡️ SQL 安全** — 仅允许 SELECT/WITH 等只读语句，禁止 DML/DDL
- **🌓 深色模式** — 自适应系统主题

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3.5 + JDK 17 |
| 元数据库 | H2（JPA 管理） |
| 分析引擎 | DuckDB（JdbcTemplate，支持 `read_xlsx`/`read_csv` 原生导入） |
| 前端 | React 19 + TypeScript + Shadcn/ui v4 + Tailwind CSS v4 |
| SQL 编辑器 | CodeMirror 6（SQL 语法高亮） |
| 图标 | Phosphor Icons |

## 快速开始

### 环境要求

- JDK 17+
- Node.js 18+ / pnpm

### 1. 启动后端

```bash
# 构建 jar
build-api.bat

# 启动（默认端口 8080，堆内存 4G）
run-api.bat
```

或手动：

```bash
cd api
..\apache-maven-3.9.9\bin\mvn.cmd clean package -DskipTests
java -Xms512m -Xmx4g -Dfile.encoding=UTF-8 -jar target\bi-api-1.0.0.jar
```

启动后：
- API: http://localhost:8080
- H2 Console: http://localhost:8080/h2-console

### 2. 启动前端

```bash
cd web
pnpm install
pnpm dev
```

浏览器打开 http://localhost:5173

### 3. 快速体验

1. 准备一个 Excel 文件（如销售订单表）
2. 打开 http://localhost:5173 → **数据源** → 点击「上传文件」
3. 上传后展开卡片，可看到自动创建的表和列
4. 切换到 **SQL 查询**，写 SQL：
   ```sql
   SELECT 部门, SUM(销售额) AS 总销售额
   FROM main."T_1_0"
   GROUP BY 部门
   ORDER BY 总销售额 DESC
   ```
5. `Ctrl+Enter` 执行，查看结果
6. 点击「保存为数据集」，后续可在「数据集」中快速复用

## 项目结构

```
easy-bi/
├── api/                          # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/java/com/bi/
│       ├── BiApplication.java
│       ├── config/
│       │   ├── DuckDbConfig.java         # DuckDB 数据源（单连接 + excel 扩展）
│       │   └── WebConfig.java            # CORS
│       ├── controller/
│       │   ├── DataSourceController.java # 上传 / 列表 / 预览 / 删除 / 追加
│       │   ├── DatasetController.java    # 数据集 CRUD / 执行 / 导出
│       │   └── QueryController.java      # 即席查询 / 浏览表结构
│       ├── service/
│       │   ├── FileImportService.java    # CSV/Excel 导入 + 追加（DuckDB 原生）
│       │   ├── TableManagementService.java # 表管理（DROP / 列信息 / 行数）
│       │   ├── DataSourceService.java    # 数据源业务
│       │   ├── DatasetService.java       # 数据集业务
│       │   └── QueryService.java         # SQL 校验 + 分页执行
│       └── model/
│           ├── entity/                   # JPA 实体（DataSource, Dataset）
│           └── dto/                      # 数据传输对象
├── web/                          # React 前端
│   └── src/
│       ├── lib/api.ts                    # API 客户端
│       ├── components/
│       │   ├── layout/                   # AppLayout + Sidebar
│       │   ├── datasource/               # 上传 / 预览 / 追加
│       │   ├── dataset/                  # 数据集表单
│       │   ├── query/                    # SQL 编辑器 + 表浏览器
│       │   └── shared/                   # ResultTable / PaginationBar
│       └── pages/                        # 三个主页面
├── build-api.bat                 # 构建后端 jar
├── run-api.bat                   # 启动后端
└── data/                         # 运行时数据（H2 + DuckDB + 上传文件）
    ├── bi_meta.mv.db             # H2 元数据库
    └── bi_data.duckdb            # DuckDB 分析数据库
```

## API 概览

```
POST   /api/datasources/upload          上传 CSV/Excel
GET    /api/datasources                 数据源列表
GET    /api/datasources/{id}            数据源详情（含表/列信息）
DELETE /api/datasources/{id}            删除数据源
GET    /api/datasources/{id}/preview    预览表数据
POST   /api/datasources/{id}/append     追加数据到已有数据源

POST   /api/datasets                    创建数据集
GET    /api/datasets                    数据集列表
GET    /api/datasets/{id}               数据集详情
PUT    /api/datasets/{id}               更新数据集
DELETE /api/datasets/{id}               删除数据集
POST   /api/datasets/{id}/execute       执行数据集
GET    /api/datasets/{id}/export        导出 CSV

POST   /api/query/execute               即席 SQL 查询
GET    /api/query/tables                浏览所有可用表
```

## 配置

`api/src/main/resources/application.yml`：

```yaml
server.port: 8080                        # 后端端口
spring.servlet.multipart.max-file-size: 200MB   # 上传文件大小限制
duckdb.url: jdbc:duckdb:./data/bi_data.duckdb   # DuckDB 文件路径
bi.query.timeout-seconds: 60             # SQL 查询超时
bi.upload-dir: ./data/uploads            # 上传文件存储目录
```

## License

MIT
