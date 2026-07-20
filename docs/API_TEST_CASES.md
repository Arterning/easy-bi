# BI 报表系统 - API 测试用例

> 测试环境：Spring Boot 3.5.16 + H2 文件模式  
> 前置条件：服务运行在 `http://localhost:8080`

---

## 1. 数据源上传

### 1.1 上传 CSV 文件

```bash
curl -s -X POST http://localhost:8080/api/datasources/upload \
  -F "file=@data/test_sales.csv"
```

**预期响应：**

```json
{
  "code": 200,
  "message": "导入成功",
  "data": {
    "dataSourceId": 33,
    "fileName": "test_sales.csv",
    "fileType": "csv",
    "fileSize": 298,
    "tables": [
      {
        "name": "T_33",
        "rowCount": 8,
        "columns": [
          { "name": "姓名",   "type": "VARCHAR(1024)" },
          { "name": "部门",   "type": "VARCHAR(1024)" },
          { "name": "销售额", "type": "BIGINT" },
          { "name": "日期",   "type": "DATE" }
        ]
      }
    ]
  }
}
```

### 1.2 上传第二个 CSV（用于 JOIN 测试）

```bash
curl -s -X POST http://localhost:8080/api/datasources/upload \
  -F "file=@data/test_salary.csv"
```

**预期：** dataSourceId=34，表名 `T_34`，列：员工姓名/部门/基本工资

---

## 2. 数据源管理

### 2.1 数据源列表（分页）

```bash
curl -s "http://localhost:8080/api/datasources?page=0&size=20"
```

### 2.2 数据源详情（含表结构 + 行数）

```bash
curl -s http://localhost:8080/api/datasources/33
```

**预期：** 返回 `fileName`, `fileType`, `tables` 数组（每个表含 `name` / `rowCount` / `columns`）

### 2.3 数据预览

```bash
curl -s "http://localhost:8080/api/datasources/33/preview?table=T_33&rows=3"
```

**预期：** 返回前 3 行数据 + 列信息 + 总行数

### 2.4 删除数据源（级联删表）

```bash
curl -s -X DELETE http://localhost:8080/api/datasources/33
```

**预期：** `"message": "删除成功"`，BI_DATA 中对应表被 DROP

---

## 3. 即席 SQL 查询

### 3.1 浏览所有可用表

```bash
curl -s http://localhost:8080/api/query/tables
```

**预期：** 返回 `T_33` 和 `T_34` 各含列信息 + 行数

### 3.2 简单 SELECT

```bash
curl -s -X POST http://localhost:8080/api/query/execute \
  -H "Content-Type: application/json" \
  -d '{"sql":"SELECT * FROM BI_DATA.T_33","page":0,"size":5}'
```

**预期：** 返回前 5 行，`totalRows=8`

### 3.3 跨表 JOIN

```bash
curl -s -X POST http://localhost:8080/api/query/execute \
  -H "Content-Type: application/json" \
  -d '{"sql":"SELECT s.姓名, s.部门, s.销售额, s.日期, sa.基本工资 FROM BI_DATA.T_33 s JOIN BI_DATA.T_34 sa ON s.姓名 = sa.员工姓名 AND s.部门 = sa.部门","page":0,"size":10}'
```

**预期：**

```json
{
  "code": 200,
  "data": {
    "columns": ["姓名","部门","销售额","日期","基本工资"],
    "rows": [
      ["张三","销售部",12000,"2024-01-15",8000],
      ["李四","销售部",8500,"2024-02-20",7500],
      ["王五","技术部",5000,"2024-01-10",12000],
      ["赵六","技术部",6200,"2024-03-05",11000],
      ["钱七","市场部",15000,"2024-01-25",9000],
      ["孙八","市场部",9800,"2024-02-14",8500],
      ["周九","销售部",11200,"2024-03-18",7800],
      ["吴十","技术部",7300,"2024-01-30",13000]
    ],
    "totalRows": 8
  }
}
```

---

## 4. 数据集 CRUD

### 4.1 创建数据集

```bash
curl -s -X POST http://localhost:8080/api/datasets \
  -H "Content-Type: application/json" \
  -d @data/create_dataset.json
```

`data/create_dataset.json`:

```json
{
  "name": "员工销售与薪资汇总",
  "sql": "SELECT s.姓名, s.部门, s.销售额, s.日期, sa.基本工资 FROM BI_DATA.T_33 s JOIN BI_DATA.T_34 sa ON s.姓名 = sa.员工姓名 AND s.部门 = sa.部门",
  "description": "关联销售表和薪资表"
}
```

**预期：** `"message": "创建成功"`，返回 `id`, `name`, `sql`, `createdAt`

### 4.2 数据集列表

```bash
curl -s "http://localhost:8080/api/datasets?page=0&size=20"
```

### 4.3 数据集详情

```bash
curl -s http://localhost:8080/api/datasets/1
```

### 4.4 更新数据集

```bash
curl -s -X PUT http://localhost:8080/api/datasets/1 \
  -H "Content-Type: application/json" \
  -d '{"name":"员工汇总(更新)","sql":"SELECT * FROM BI_DATA.T_33","description":"已更新"}'
```

### 4.5 执行数据集（分页）

```bash
curl -s -X POST http://localhost:8080/api/datasets/1/execute \
  -H "Content-Type: application/json" \
  -d '{"page":0,"size":50}'
```

### 4.6 导出数据集为 CSV

```bash
curl -s "http://localhost:8080/api/datasets/1/export?format=csv"
```

**预期：**

```csv
姓名,部门,销售额,日期,基本工资
张三,销售部,12000,2024-01-15,8000
李四,销售部,8500,2024-02-20,7500
王五,技术部,5000,2024-01-10,12000
赵六,技术部,6200,2024-03-05,11000
钱七,市场部,15000,2024-01-25,9000
孙八,市场部,9800,2024-02-14,8500
周九,销售部,11200,2024-03-18,7800
吴十,技术部,7300,2024-01-30,13000
```

### 4.7 删除数据集

```bash
curl -s -X DELETE http://localhost:8080/api/datasets/1
```

---

## 5. SQL 安全拦截

### 5.1 拒绝 DROP

```bash
curl -s -X POST http://localhost:8080/api/query/execute \
  -H "Content-Type: application/json" \
  -d '{"sql":"DROP TABLE BI_DATA.T_33","page":0,"size":5}'
```

**预期：** `"message": "仅允许 SELECT 和 WITH (CTE) 开头的只读语句"`

### 5.2 拒绝 INSERT

```bash
curl -s -X POST http://localhost:8080/api/query/execute \
  -H "Content-Type: application/json" \
  -d "{\"sql\":\"INSERT INTO BI_DATA.T_33 VALUES ('hack','test',1,'2024-01-01')\",\"page\":0,\"size\":5}"
```

**预期：** 同上，拦截

---

## 测试数据

### `data/test_sales.csv`

```csv
姓名,部门,销售额,日期
张三,销售部,12000,2024-01-15
李四,销售部,8500,2024-02-20
王五,技术部,5000,2024-01-10
赵六,技术部,6200,2024-03-05
钱七,市场部,15000,2024-01-25
孙八,市场部,9800,2024-02-14
周九,销售部,11200,2024-03-18
吴十,技术部,7300,2024-01-30
```

### `data/test_salary.csv`

```csv
员工姓名,部门,基本工资
张三,销售部,8000
李四,销售部,7500
王五,技术部,12000
赵六,技术部,11000
钱七,市场部,9000
孙八,市场部,8500
周九,销售部,7800
吴十,技术部,13000
```

---

## 快速回归测试（一键）

```bash
echo "=== 1. 浏览表结构 ==="
curl -s http://localhost:8080/api/query/tables | python -m json.tool

echo -e "\n=== 2. 简单查询 ==="
curl -s -X POST http://localhost:8080/api/query/execute \
  -H "Content-Type: application/json" \
  -d '{"sql":"SELECT * FROM BI_DATA.T_33","page":0,"size":3}'

echo -e "\n=== 3. JOIN 查询 ==="
curl -s -X POST http://localhost:8080/api/query/execute \
  -H "Content-Type: application/json" \
  -d '{"sql":"SELECT s.姓名, s.销售额, sa.基本工资 FROM BI_DATA.T_33 s JOIN BI_DATA.T_34 sa ON s.姓名 = sa.员工姓名","page":0,"size":10}'

echo -e "\n=== 4. 执行数据集 ==="
curl -s -X POST http://localhost:8080/api/datasets/1/execute \
  -H "Content-Type: application/json" \
  -d '{"page":0,"size":50}'

echo -e "\n=== 5. SQL 安全测试 ==="
curl -s -X POST http://localhost:8080/api/query/execute \
  -H "Content-Type: application/json" \
  -d '{"sql":"DROP TABLE BI_DATA.T_33","page":0,"size":5}'

echo -e "\n=== 完成 ==="
```
