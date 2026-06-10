import duckdb
import pandas as pd


def run_pipeline(unified_df: pd.DataFrame) -> pd.DataFrame:
    """
    使用 DuckDB 对统一数据进行清洗和合并
    输入: ingest 产出的统一 DataFrame
    输出: 汇总成的基础表
    """
    if unified_df.empty:
        return pd.DataFrame()

    con = duckdb.connect()

    con.register("raw", unified_df)

    # 1. 基础清洗：过滤无效数据，统一字段
    con.execute("""
        CREATE TABLE cleaned AS
        SELECT
            "销售时间",
            "所属月份",
            "产品名称",
            "店铺名",
            COALESCE("品牌", '未知') AS "品牌",
            "平台",
            "销售数量",
            "销售额",
            "成本单价",
            "成本总额",
            "利润",
            "退款金额",
            "退货成本",
            "推广费",
            COALESCE("业务员", '未知') AS "业务员",
            "经手人"
        FROM raw
        WHERE "产品名称" IS NOT NULL AND "产品名称" != ''
    """)

    # 2. 按品牌+月份聚合推广费（从品牌通数据来）
    con.execute("""
        CREATE TABLE promotion_fees AS
        SELECT
            "品牌",
            "所属月份",
            SUM(CAST("推广费" AS DOUBLE)) AS "推广费总额"
        FROM cleaned
        WHERE CAST("推广费" AS DOUBLE) > 0
        GROUP BY "品牌", "所属月份"
    """)

    # 3. 将推广费合并回主表
    con.execute("""
        CREATE TABLE unified AS
        SELECT
            c."销售时间",
            c."所属月份",
            c."产品名称",
            c."店铺名",
            c."品牌",
            c."平台",
            c."销售数量",
            c."销售额",
            c."成本单价",
            c."成本总额",
            CASE WHEN CAST(c."利润" AS DOUBLE) != 0
                THEN CAST(c."利润" AS DOUBLE)
                ELSE CAST(c."销售额" AS DOUBLE) - CAST(c."成本总额" AS DOUBLE)
            END AS "利润",
            c."退款金额",
            c."退货成本",
            COALESCE(pf."推广费总额", 0) AS "推广费",
            c."业务员",
            c."经手人"
        FROM cleaned c
        LEFT JOIN promotion_fees pf
            ON c."品牌" = pf."品牌"
            AND c."所属月份" = pf."所属月份"
    """)

    result = con.execute("SELECT * FROM unified ORDER BY 品牌, 产品名称, 所属月份").fetchdf()

    con.close()
    return result
