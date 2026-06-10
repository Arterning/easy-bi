import duckdb
import pandas as pd


def generate_reports(unified_df: pd.DataFrame) -> dict[str, pd.DataFrame]:
    """
    从汇总成的基础表生成三张报表
    返回 {sheet名: DataFrame}
    """
    if unified_df.empty:
        return {}

    con = duckdb.connect()
    con.register("base", unified_df)

    # 过滤无效月份
    con.execute("""
        CREATE VIEW base_valid AS
        SELECT * FROM base
        WHERE "所属月份" IS NOT NULL AND "所属月份" != '' AND "所属月份" != 'nan'
    """)

    # ---------- 1. 产品维度 ----------
    con.execute("""
        CREATE TABLE report_product AS
        SELECT
            "产品名称",
            "所属月份",
            ROUND(SUM(CAST("销售额" AS DOUBLE)), 2) AS "销售总额",
            ROUND(SUM(CAST("成本总额" AS DOUBLE)), 2) AS "成本总额",
            ROUND(SUM(CAST("推广费" AS DOUBLE)), 2) AS "推广费",
            ROUND(SUM(CAST("利润" AS DOUBLE)), 2) AS "毛利额",
            CASE
                WHEN SUM(CAST("销售额" AS DOUBLE)) = 0 THEN 0
                ELSE ROUND(SUM(CAST("利润" AS DOUBLE)) / SUM(CAST("销售额" AS DOUBLE)), 4)
            END AS "毛利率"
        FROM base_valid
        GROUP BY "产品名称", "所属月份"
        ORDER BY "所属月份", "产品名称"
    """)

    # ---------- 2. 品牌维度 ----------
    con.execute("""
        CREATE TABLE report_brand AS
        SELECT
            ROW_NUMBER() OVER (ORDER BY "业务员", "品牌", "所属月份") AS "序号",
            "业务员",
            "品牌",
            "所属月份",
            ROUND(SUM(CAST("销售额" AS DOUBLE)), 2) AS "销售金额",
            ROUND(SUM(CAST("成本总额" AS DOUBLE)), 2) AS "成本金额",
            ROUND(SUM(CAST("推广费" AS DOUBLE)), 2) AS "推广费",
            0 AS "返点费用",
            ROUND(SUM(CAST("利润" AS DOUBLE)), 2) AS "毛利额",
            CASE
                WHEN SUM(CAST("销售额" AS DOUBLE)) = 0 THEN 0
                ELSE ROUND(SUM(CAST("利润" AS DOUBLE)) / SUM(CAST("销售额" AS DOUBLE)), 4)
            END AS "毛利率"
        FROM base_valid
        GROUP BY "业务员", "品牌", "所属月份"
        ORDER BY "业务员", "品牌", "所属月份"
    """)

    # ---------- 3. 业务员周报分析 ----------
    # 按业务员+品牌+产品+月份聚合
    con.execute("""
        CREATE TABLE weekly_raw AS
        SELECT
            "业务员",
            "品牌",
            "产品名称",
            "所属月份",
            SUM(CAST("销售额" AS DOUBLE)) AS "销售额",
            SUM(CAST("退款金额" AS DOUBLE)) AS "退款金额",
            SUM(CAST("推广费" AS DOUBLE)) AS "推广费"
        FROM base_valid
        GROUP BY "业务员", "品牌", "产品名称", "所属月份"
    """)

    # 用窗口函数取上月数据做环比
    con.execute("""
        CREATE TABLE weekly_with_lag AS
        SELECT
            "业务员",
            "品牌",
            "产品名称",
            "所属月份",
            "销售额",
            "退款金额",
            "推广费",
            LAG("销售额") OVER (
                PARTITION BY "业务员", "品牌", "产品名称"
                ORDER BY "所属月份"
            ) AS "上月销售额"
        FROM weekly_raw
    """)

    # 计算派生指标
    con.execute("""
        CREATE TABLE report_weekly AS
        SELECT
            ROW_NUMBER() OVER (ORDER BY "业务员", "品牌", "产品名称", "所属月份") AS "序号",
            "业务员",
            "品牌",
            "产品名称",
            NULL AS "本月目标",
            ROUND(COALESCE("上月销售额", 0), 2) AS "上月销售额",
            ROUND("销售额", 2) AS "本月销售额",
            ROUND("退款金额", 2) AS "本月退款金额",
            ROUND("推广费", 2) AS "本月推广费",
            CASE
                WHEN COALESCE("上月销售额", 0) = 0 THEN NULL
                ELSE ROUND(("销售额" - "上月销售额") / "上月销售额", 4)
            END AS "销售增长率",
            CASE
                WHEN ABS("销售额") = 0 THEN 0
                ELSE ROUND("退款金额" / ABS("销售额"), 4)
            END AS "退货率",
            CASE
                WHEN "销售额" = 0 THEN 0
                ELSE ROUND("推广费" / "销售额", 4)
            END AS "投入产出比",
            NULL AS "达成率",
            NULL AS "下单仓店数量",
            NULL AS "单仓月销",
            NULL AS "单仓月销同比",
            NULL AS "投券覆盖率",
            NULL AS "上周投券覆盖率",
            NULL AS "覆盖率周增长"
        FROM weekly_with_lag
        ORDER BY "业务员", "品牌", "产品名称", "所属月份"
    """)

    product_df = con.execute("SELECT * FROM report_product").fetchdf()
    brand_df = con.execute("SELECT * FROM report_brand").fetchdf()
    weekly_df = con.execute("SELECT * FROM report_weekly").fetchdf()

    con.close()

    return {
        "产品维度": product_df,
        "品牌维度": brand_df,
        "业务员周报分析": weekly_df,
    }
