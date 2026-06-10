import re
from pathlib import Path
from datetime import datetime

import pandas as pd
import yaml


def load_config(config_path="config/schemas.yaml"):
    with open(config_path, encoding="utf-8") as f:
        return yaml.safe_load(f)


def _safe_float(v):
    if v is None:
        return 0.0
    try:
        return float(v)
    except (ValueError, TypeError):
        return 0.0


def _extract_month(dt_val):
    if dt_val is None:
        return None
    s = str(dt_val).strip()
    m = re.search(r"(\d{4})[-/](\d{1,2})", s)
    if m:
        return f"{m.group(1)}-{int(m.group(2)):02d}"
    # 支持 yyyyMMdd 格式
    m2 = re.match(r"(\d{4})(\d{2})\d{2}", s)
    if m2:
        return f"{m2.group(1)}-{m2.group(2)}"
    # 支持 yyyy年mm月
    m3 = re.search(r"(\d{4})年(\d{1,2})月", s)
    if m3:
        return f"{m3.group(1)}-{int(m3.group(2)):02d}"
    return None


def _match_by_contains(source_val, mapping_df, source_col, mapping_col, result_cols):
    """模糊匹配：source_val 包含 mapping_col 的值"""
    if not source_val:
        return None
    sv = str(source_val).lower()
    for _, row in mapping_df.iterrows():
        mv = str(row.get(mapping_col, "")).lower()
        if mv and mv in sv:
            return {k: row.get(k) for k in result_cols}
    return None


def parse_platform(file_path, platform_name, plat_conf, all_mappings):
    """解析一个平台的数据为统一格式的 DataFrame"""
    df = pd.read_excel(file_path, sheet_name=plat_conf.get("sheet", platform_name), dtype=str)
    if df.empty:
        return pd.DataFrame()

    fields = plat_conf["fields"]
    platform_name_val = plat_conf.get("platform_name", platform_name)
    result_rows = []

    for _, row in df.iterrows():
        out = {
            "销售时间": None,
            "所属月份": None,
            "产品名称": None,
            "店铺名": None,
            "品牌": None,
            "平台": platform_name_val,
            "销售数量": 0.0,
            "销售额": 0.0,
            "成本单价": 0.0,
            "成本总额": 0.0,
            "利润": 0.0,
            "退款金额": 0.0,
            "退货成本": 0.0,
            "推广费": 0.0,
            "业务员": None,
            "经手人": None,
        }

        for ufield, sfield in fields.items():
            if sfield is None:
                continue
            if sfield == "1":
                out[ufield] = 1.0
                continue
            raw = row.get(sfield)
            if ufield in ("销售数量", "销售额", "成本单价", "成本总额", "利润", "退款金额", "退货成本", "推广费"):
                out[ufield] = _safe_float(raw)
            else:
                out[ufield] = str(raw).strip() if raw is not None else None

        out["销售时间"] = str(row.get(fields.get("销售时间", ""))) if fields.get("销售时间") else None

        if out["销售时间"] and out["销售时间"] != "None":
            out["所属月份"] = _extract_month(out["销售时间"])
        else:
            out["所属月份"] = None

        qty = out["销售数量"]
        if qty < 0:
            out["退款金额"] = abs(out["销售额"])
            out["退货成本"] = abs(qty * out["成本单价"]) if out["成本单价"] else 0.0

        # 如果没有自带品牌/业务员，尝试从映射表匹配
        if (not plat_conf.get("has_brand") or not plat_conf.get("has_salesperson")) and all_mappings is not None:
            mapping_conf = plat_conf.get("mapping_table")
            if mapping_conf and mapping_conf in all_mappings:
                mdf = all_mappings[mapping_conf]
                mjoin = plat_conf.get("mapping_join", {})
                source_field_local = mjoin.get("source_field")
                if source_field_local and source_field_local in row:
                    source_val = str(row[source_field_local]) if row[source_field_local] else None
                    if source_val:
                        result = _match_by_contains(
                            source_val,
                            mdf,
                            mjoin.get("mapping_field"),
                            mjoin.get("mapping_field"),
                            [mjoin.get("brand_field"), mjoin.get("salesperson_field")],
                        )
                        if result:
                            if not plat_conf.get("has_brand") and not out["品牌"]:
                                out["品牌"] = result.get(mjoin.get("brand_field"))
                            if not plat_conf.get("has_salesperson") and not out["业务员"]:
                                out["业务员"] = result.get(mjoin.get("salesperson_field"))

        if out["产品名称"] is None or out["产品名称"] in ("", "None"):
            continue

        result_rows.append(out)

    return pd.DataFrame(result_rows)


def parse_all_platforms(file_path, config):
    """从 Excel 文件中解析所有平台数据"""
    plat_configs = config.get("platforms", {})

    # 先加载所有映射表
    all_mappings = {}
    mapping_configs = config.get("mappings", {})
    try:
        xls = pd.ExcelFile(file_path)
        for mname, mconf in mapping_configs.items():
            if mname in xls.sheet_names:
                mdf = pd.read_excel(file_path, sheet_name=mname, dtype=str)
                if not mdf.empty:
                    all_mappings[mname] = mdf
    except Exception:
        pass

    all_dfs = []
    for pname, pconf in plat_configs.items():
        sheet_name = pconf.get("sheet", pname)
        try:
            xls = pd.ExcelFile(file_path)
            if sheet_name not in xls.sheet_names:
                continue
        except Exception:
            continue
        df = parse_platform(file_path, pname, pconf, all_mappings)
        if df is not None and not df.empty:
            all_dfs.append(df)

    if not all_dfs:
        return pd.DataFrame()

    result = pd.concat(all_dfs, ignore_index=True)
    return result
