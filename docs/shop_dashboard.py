#!/usr/bin/env python3
"""
DuckDB E-Commerce Multi-Platform Dashboard
Outputs: 6-Sheet Excel Report + Plotly Interactive HTML Dashboard
https://duckdblab.org/en/post/duckdb-ecommerce-multi-platform-dashboard/
"""

import duckdb
import pandas as pd
import numpy as np
from datetime import datetime, timedelta
import random

# ============ Step 1: Generate mock data (replace with real CSV paths) ============
print("🔄 Generating simulated order data...")

def gen_orders(platform, stores, n_days=90):
    """Generate n_days of order data for a platform"""
    skus = [f"{platform[:2]}-{chr(65+i)}-{random.randint(100,999)}"
            for i in range(random.randint(15, 25))]
    categories = {
        'Apparel': ['Men', 'Women', 'Kids'],
        'Electronics': ['Phones', 'Accessories', 'Headphones'],
        'Home': ['Kitchen', 'Bedding', 'Storage']
    }
    province_pool = ['Guangdong', 'Zhejiang', 'Jiangsu', 'Shanghai', 'Beijing',
                     'Sichuan', 'Hubei', 'Shandong', 'Fujian', 'Henan']
    start_date = datetime.now() - timedelta(days=n_days)

    rows = []
    for store in stores:
        for day_offset in range(n_days):
            n_orders = random.randint(5, 30)
            date = start_date + timedelta(days=day_offset)
            for _ in range(n_orders):
                cat = random.choice(list(categories.keys()))
                sub_cat = random.choice(categories[cat])
                sku = random.choice(skus)
                qty = random.randint(1, 5)
                price = random.choice([29.9, 49.9, 79.9, 99, 129, 199, 299, 499])
                rows.append({
                    'order_id': f"{platform[:2]}{date.strftime('%y%m%d')}{random.randint(10000,99999)}",
                    'order_date': date.strftime('%Y-%m-%d'),
                    'store': store,
                    'sku': sku,
                    'category': cat,
                    'sub_category': sub_cat,
                    'quantity': qty,
                    'amount': round(qty * price, 2),
                    'province': province_pool,
                    'platform': platform
                })
    return pd.DataFrame(rows)

# Generate data for 3 platforms
taobao_df = gen_orders('Taobao', ['Flagship Store', 'Specialty Store', 'Factory Store'])
pdd_df = gen_orders('Pinduoduo', ['Official Flagship', 'Brand Store'])
jd_df = gen_orders('JD', ['JD Self-Operated', 'Third-Party Store'])

taobao_df.to_csv('taobao_orders.csv', index=False)
pdd_df.to_csv('pdd_orders.csv', index=False)
jd_df.to_csv('jd_orders.csv', index=False)

print(f"  ✅ Taobao: {len(taobao_df)} orders")
print(f"  ✅ Pinduoduo: {len(pdd_df)} orders")
print(f"  ✅ JD: {len(jd_df)} orders")

# ============ Step 2: Cross-platform analysis with DuckDB ============
print("\n🔄 Running DuckDB cross-platform analysis...")

con = duckdb.connect()

# 2a. KPI Overview
kpi_overview = con.execute("""
    WITH unified AS (
        SELECT * FROM read_csv_auto('taobao_orders.csv')
        UNION ALL BY NAME
        SELECT * FROM read_csv_auto('pdd_orders.csv')
        UNION ALL BY NAME
        SELECT * FROM read_csv_auto('jd_orders.csv')
    )
    SELECT
        platform,
        COUNT(*) AS order_count,
        ROUND(SUM(amount), 0) AS total_revenue,
        ROUND(AVG(amount), 2) AS avg_order_value,
        ROUND(SUM(quantity), 0) AS total_units,
        ROUND(SUM(amount) / NULLIF(SUM(quantity), 0), 2) AS avg_unit_price,
        COUNT(DISTINCT sku) AS sku_count
    FROM unified
    GROUP BY platform
    ORDER BY total_revenue DESC
""").fetchdf()

print("\n📊 Platform KPIs:")
print(kpi_overview.to_string(index=False))

# 2b. Daily Sales Trend
daily_trend = con.execute("""
    WITH unified AS (
        SELECT * FROM read_csv_auto('taobao_orders.csv')
        UNION ALL BY NAME
        SELECT * FROM read_csv_auto('pdd_orders.csv')
        UNION ALL BY NAME
        SELECT * FROM read_csv_auto('jd_orders.csv')
    )
    SELECT order_date, platform, ROUND(SUM(amount), 0) AS sales
    FROM unified
    GROUP BY order_date, platform
    ORDER BY order_date, platform
""").fetchdf()

# 2c. SKU Sales Ranking
sku_rank = con.execute("""
    WITH unified AS (
        SELECT * FROM read_csv_auto('taobao_orders.csv')
        UNION ALL BY NAME
        SELECT * FROM read_csv_auto('pdd_orders.csv')
        UNION ALL BY NAME
        SELECT * FROM read_csv_auto('jd_orders.csv')
    )
    SELECT
        sku, category, sub_category,
        ROUND(SUM(amount), 0) AS total_revenue,
        SUM(quantity) AS total_units,
        ROUND(AVG(amount / quantity), 2) AS avg_price,
        COUNT(DISTINCT platform) AS platforms_covered
    FROM unified
    GROUP BY sku, category, sub_category
    ORDER BY total_revenue DESC
    LIMIT 20
""").fetchdf()

# 2d. Category Analysis
cat_analysis = con.execute("""
    WITH unified AS (
        SELECT * FROM read_csv_auto('taobao_orders.csv')
        UNION ALL BY NAME
        SELECT * FROM read_csv_auto('pdd_orders.csv')
        UNION ALL BY NAME
        SELECT * FROM read_csv_auto('jd_orders.csv')
    )
    SELECT
        category, platform,
        ROUND(SUM(amount), 0) AS revenue,
        COUNT(*) AS order_count,
        ROUND(SUM(amount) / SUM(SUM(amount)) OVER (PARTITION BY category) * 100, 1) AS platform_share_pct
    FROM unified
    GROUP BY category, platform
    ORDER BY category, revenue DESC
""").fetchdf()

# 2e. Top 3 Products Per Platform
top3_per_platform = con.execute("""
    WITH unified AS (
        SELECT * FROM read_csv_auto('taobao_orders.csv')
        UNION ALL BY NAME
        SELECT * FROM read_csv_auto('pdd_orders.csv')
        UNION ALL BY NAME
        SELECT * FROM read_csv_auto('jd_orders.csv')
    ),
    sku_sales AS (
        SELECT platform, sku, category,
               ROUND(SUM(amount), 0) AS sales,
               ROW_NUMBER() OVER (PARTITION BY platform ORDER BY SUM(amount) DESC) AS rank
        FROM unified
        GROUP BY platform, sku, category
    )
    SELECT platform, sku, category, sales
    FROM sku_sales
    WHERE rank <= 3
    ORDER BY platform, rank
""").fetchdf()

# 2f. Overall Sales Trend
overall_trend = con.execute("""
    WITH unified AS (
        SELECT * FROM read_csv_auto('taobao_orders.csv')
        UNION ALL BY NAME
        SELECT * FROM read_csv_auto('pdd_orders.csv')
        UNION ALL BY NAME
        SELECT * FROM read_csv_auto('jd_orders.csv')
    )
    SELECT order_date, ROUND(SUM(amount), 0) AS total_sales
    FROM unified
    GROUP BY order_date
    ORDER BY order_date
""").fetchdf()

con.close()
print("  ✅ DuckDB analysis complete")

# ============ Step 3: Output to Excel (6 Sheets) ============
print("\n🔄 Generating Excel report...")
with pd.ExcelWriter('ecommerce_multi_platform_report.xlsx', engine='openpyxl') as writer:
    kpi_overview.to_excel(writer, sheet_name='KPI_Overview', index=False)
    overall_trend.to_excel(writer, sheet_name='Daily_Sales_Trend', index=False)
    sku_rank.to_excel(writer, sheet_name='SKU_Ranking', index=False)
    cat_analysis.to_excel(writer, sheet_name='Category_Analysis', index=False)
    top3_per_platform.to_excel(writer, sheet_name='Top3_Per_Platform', index=False)
    daily_trend.to_excel(writer, sheet_name='Daily_By_Platform', index=False)
print("  ✅ ecommerce_multi_platform_report.xlsx generated")

# ============ Step 4: Output interactive Plotly HTML dashboard ============
print("\n🔄 Generating interactive HTML dashboard...")
import plotly.express as px
import plotly.graph_objects as go

html = """
<html><head><meta charset="utf-8">
<title>E-Commerce Multi-Platform Dashboard</title>
<style>
body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; margin: 20px; background: #f5f5f5; }
h1 { color: #2c3e50; text-align: center; }
.container { max-width: 1400px; margin: 0 auto; }
.card { background: white; padding: 20px; margin: 15px 0; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
.card h2 { color: #34495e; margin-top: 0; }
.kpi-row { display: flex; gap: 15px; flex-wrap: wrap; }
.kpi-card { flex: 1; min-width: 150px; background: #f8f9fa; padding: 15px; border-radius: 8px; text-align: center; }
.kpi-value { font-size: 24px; font-weight: bold; color: #2c3e50; }
.kpi-label { font-size: 13px; color: #7f8c8d; }
</style></head><body>
<div class="container">
<h1>🦆 E-Commerce Multi-Platform Dashboard</h1>
<p style="text-align:center;color:#7f8c8d;">Data Period: Last 90 Days | Platforms: Taobao / Pinduoduo / JD</p>
"""

# KPI cards
kpi_card_html = '<div class="card"><h2>📊 KPI Overview</h2><div class="kpi-row">'
for _, row in kpi_overview.head(3).iterrows():
    revenue = f"${row['total_revenue']:,.0f}" if 'total_revenue' in row else f"¥{row.iloc[1]:,.0f}"
    orders = row['order_count'] if 'order_count' in row else row.iloc[2]
    kpi_card_html += f"""
    <div class="kpi-card">
        <div class="kpi-label">{row['platform']}</div>
        <div class="kpi-value">{revenue}</div>
        <div style="font-size:12px;color:#95a5a6;">{orders} orders</div>
    </div>"""
kpi_card_html += '</div></div>'
html += kpi_card_html

# Figure 1: Overall sales trend
fig1 = px.line(overall_trend, x='order_date', y='total_sales',
               title='📈 Total Sales Trend (All Platforms Combined)',
               labels={'order_date': 'Date', 'total_sales': 'Revenue (¥)'})
fig1.update_layout(template='plotly_white', height=400)
html += f'<div class="card">{fig1.to_html(full_html=False, include_plotlyjs="cdn")}</div>'

# Figure 2: Daily trends by platform
fig2 = px.line(daily_trend, x='order_date', y='sales', color='platform',
               title='📊 Daily Sales by Platform',
               labels={'order_date': 'Date', 'sales': 'Revenue (¥)', 'platform': 'Platform'})
fig2.update_layout(template='plotly_white', height=400)
html += f'<div class="card">{fig2.to_html(full_html=False, include_plotlyjs="cdn")}</div>'

# Figure 3: Category sunburst
fig3 = px.sunburst(cat_analysis, path=['category', 'platform'], values='revenue',
                   title='🎯 Category-Platform Revenue Distribution',
                   color='revenue', color_continuous_scale='blues')
fig3.update_layout(height=500)
html += f'<div class="card">{fig3.to_html(full_html=False, include_plotlyjs="cdn")}</div>'

# Figure 4: SKU Top 20
fig4 = px.bar(sku_rank.head(20), x='total_revenue', y='sku', color='category',
              orientation='h',
              title='🏆 Top 20 SKUs by Revenue',
              labels={'total_revenue': 'Revenue (¥)', 'sku': 'SKU', 'category': 'Category'},
              text='total_revenue')
fig4.update_layout(template='plotly_white', height=600, yaxis={'categoryorder':'total ascending'})
html += f'<div class="card">{fig4.to_html(full_html=False, include_plotlyjs="cdn")}</div>'

html += """
<div class="card" style="text-align:center;color:#7f8c8d;">
<p>🦆 Powered by DuckDB &middot; Static HTML dashboard, data as of generation time</p>
</div></div></body></html>"""

with open('ecommerce_dashboard.html', 'w', encoding='utf-8') as f:
    f.write(html)
print("  ✅ ecommerce_dashboard.html generated")

print("\n" + "="*50)
print("🎉 Delivery Complete!")
print("  📁 ecommerce_multi_platform_report.xlsx (6 Sheets)")
print("  📁 ecommerce_dashboard.html (Plotly Interactive Dashboard)")
print("="*50)
