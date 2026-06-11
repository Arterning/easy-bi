"""生成一个简单的应用图标"""
from PIL import Image, ImageDraw, ImageFont

SIZE = 256
OUTPUT = "app.ico"


def create_icon():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # 圆角矩形背景 - 深蓝色
    margin = 8
    draw.rounded_rectangle(
        [margin, margin, SIZE - margin, SIZE - margin],
        radius=40,
        fill=(26, 115, 232, 255),
    )

    # 白色圆角矩形（模拟表格/卡片）
    inner_m = 50
    draw.rounded_rectangle(
        [inner_m, inner_m + 20, SIZE - inner_m, SIZE - inner_m - 10],
        radius=16,
        fill=(255, 255, 255, 230),
    )

    # 三条柱状图（蓝色调）
    bar_colors = [(66, 133, 244), (52, 168, 83), (251, 188, 4)]
    bar_base_y = SIZE - inner_m - 20
    bar_width = 30
    bar_gap = 20
    start_x = (SIZE - (bar_width * 3 + bar_gap * 2)) // 2

    bar_heights = [100, 150, 80]
    for i, (bh, bc) in enumerate(zip(bar_heights, bar_colors)):
        x = start_x + i * (bar_width + bar_gap)
        y1 = bar_base_y - bh + 20
        y2 = bar_base_y + 10
        draw.rounded_rectangle([x, y1, x + bar_width, y2], radius=6, fill=bc + (220,))

    # 底部文字 "Excel 报表"（用像素画代替，不依赖中文字体）
    # 画三个小横线代表文字
    line_y = SIZE - 30
    for lx in [70, 95, 120, 155, 180]:
        draw.rectangle([lx, line_y, lx + 18, line_y + 3], fill=(255, 255, 255, 200))

    # 调整尺寸并保存为 ico（多尺寸）
    img.save(OUTPUT, format="ICO", sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    print(f"图标已生成: {OUTPUT}")


if __name__ == "__main__":
    create_icon()
