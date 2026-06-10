"""
数据整理（龙）— 多平台电商销售数据聚合报表工具
用法: uv run python main.py <输入Excel路径> [输出Excel路径]
"""

import sys
from pathlib import Path

from src.ingest import load_config, parse_all_platforms
from src.transform import run_pipeline
from src.reports import generate_reports
from src.export import write_excel


def main():
    if len(sys.argv) < 2:
        print("用法: uv run python main.py <输入Excel路径> [输出Excel路径]")
        print("示例: uv run python main.py 数据整理.xlsx 报表输出.xlsx")
        sys.exit(1)

    input_path = Path(sys.argv[1])
    if not input_path.exists():
        print(f"错误: 文件不存在 - {input_path}")
        sys.exit(1)

    output_path = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("data/output/报表输出.xlsx")

    print(f"[读取] {input_path}")
    config = load_config("config/schemas.yaml")

    print("[解析] 各平台数据...")
    unified = parse_all_platforms(input_path, config)
    print(f"  -> 共解析 {len(unified)} 行数据")

    if unified.empty:
        print("[错误] 未解析到任何数据，请检查输入文件")
        sys.exit(1)

    print("[合并] DuckDB 清洗合并...")
    consolidated = run_pipeline(unified)
    print(f"  -> 合并后 {len(consolidated)} 行")

    print("[报表] 生成三张报表...")
    reports = generate_reports(consolidated)
    for name, df in reports.items():
        print(f"  -> {name}: {len(df)} 行")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    print(f"[输出] 写入 Excel: {output_path}")
    write_excel(reports, output_path)
    print("[完成]")


if __name__ == "__main__":
    main()
