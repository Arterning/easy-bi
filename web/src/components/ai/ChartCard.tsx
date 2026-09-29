import ReactECharts from "echarts-for-react"
import type { EChartsOption, SeriesOption } from "echarts"
import { ChartBar, Table as TableIcon } from "@phosphor-icons/react"

export type ChartType = "bar" | "line" | "area" | "pie" | "scatter" | "number" | "table"

export interface ChartSpec {
  chartType: ChartType
  title: string
  xField?: string | null
  yFields: string[]
  seriesField?: string | null
  data: Record<string, unknown>[]
  totalRows: number
  truncated: boolean
}

function numericValue(value: unknown) {
  if (typeof value === "number") return value
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

function groupedSeries(spec: ChartSpec): SeriesOption[] {
  if (!spec.seriesField) {
    return spec.yFields.map((field) => ({
      name: field,
      type: spec.chartType === "area" ? "line" : spec.chartType,
      smooth: spec.chartType === "line" || spec.chartType === "area",
      areaStyle: spec.chartType === "area" ? {} : undefined,
      encode: { x: spec.xField ?? undefined, y: field },
    } as SeriesOption))
  }

  const groups = [...new Set(spec.data.map((row) => String(row[spec.seriesField!] ?? "未分类")))]
  return groups.flatMap((group) => spec.yFields.map((field) => ({
    name: spec.yFields.length > 1 ? `${group} · ${field}` : group,
    type: spec.chartType === "area" ? "line" : spec.chartType,
    smooth: spec.chartType === "line" || spec.chartType === "area",
    areaStyle: spec.chartType === "area" ? {} : undefined,
    data: spec.data
      .filter((row) => String(row[spec.seriesField!] ?? "未分类") === group)
      .map((row) => [row[spec.xField!], numericValue(row[field])]),
  } as SeriesOption)))
}

function chartOption(spec: ChartSpec): EChartsOption {
  if (spec.chartType === "pie") {
    const valueField = spec.yFields[0]
    return {
      tooltip: { trigger: "item" },
      legend: { type: "scroll", bottom: 0 },
      series: [{
        name: valueField,
        type: "pie",
        radius: ["42%", "70%"],
        center: ["50%", "45%"],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: "transparent", borderWidth: 2 },
        label: { formatter: "{b}\n{d}%" },
        data: spec.data.map((row) => ({ name: String(row[spec.xField!] ?? ""), value: numericValue(row[valueField]) })),
      }],
    }
  }

  const categoryAxis = spec.chartType !== "scatter"
  return {
    animationDuration: 500,
    color: ["#2563eb", "#14b8a6", "#f59e0b", "#8b5cf6", "#ef4444"],
    tooltip: { trigger: categoryAxis ? "axis" : "item" },
    legend: { type: "scroll", top: 0 },
    grid: { top: 42, right: 24, bottom: 42, left: 58, containLabel: true },
    dataset: spec.seriesField ? undefined : { source: spec.data },
    xAxis: { type: categoryAxis ? "category" : "value", axisLabel: { hideOverlap: true } },
    yAxis: { type: "value", splitLine: { lineStyle: { type: "dashed", opacity: 0.35 } } },
    dataZoom: spec.data.length > 15 ? [{ type: "inside" }, { type: "slider", height: 16, bottom: 4 }] : undefined,
    series: groupedSeries(spec),
  }
}

function DataTable({ spec }: { spec: ChartSpec }) {
  const columns = spec.data[0] ? Object.keys(spec.data[0]) : []
  return (
    <div className="max-h-80 overflow-auto rounded-lg border">
      <table className="w-full text-xs">
        <thead className="sticky top-0 bg-muted">
          <tr>{columns.map((column) => <th className="px-3 py-2 text-left font-medium" key={column}>{column}</th>)}</tr>
        </thead>
        <tbody>
          {spec.data.map((row, index) => (
            <tr className="border-t" key={index}>
              {columns.map((column) => <td className="whitespace-nowrap px-3 py-2" key={column}>{String(row[column] ?? "-")}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function ChartCard({ spec }: { spec: ChartSpec }) {
  const valueField = spec.yFields[0]
  const value = spec.data[0]?.[valueField]

  return (
    <section className="w-full rounded-xl border bg-card p-4 text-card-foreground shadow-sm">
      <div className="mb-3 flex items-center justify-between gap-3">
        <div className="flex min-w-0 items-center gap-2">
          {spec.chartType === "table" ? <TableIcon className="size-4 text-muted-foreground" /> : <ChartBar className="size-4 text-muted-foreground" />}
          <h3 className="truncate font-semibold">{spec.title}</h3>
        </div>
        <span className="shrink-0 text-xs text-muted-foreground">
          {spec.truncated ? `展示前 ${spec.data.length} / ${spec.totalRows} 行` : `${spec.totalRows} 行`}
        </span>
      </div>

      {spec.chartType === "number" ? (
        <div className="py-8 text-center">
          <div className="text-4xl font-semibold tracking-tight">{typeof value === "number" ? value.toLocaleString("zh-CN") : String(value ?? "-")}</div>
          <div className="mt-2 text-sm text-muted-foreground">{valueField}</div>
        </div>
      ) : spec.chartType === "table" ? (
        <DataTable spec={spec} />
      ) : spec.data.length === 0 ? (
        <div className="py-16 text-center text-sm text-muted-foreground">没有可展示的数据</div>
      ) : (
        <ReactECharts option={chartOption(spec)} notMerge lazyUpdate style={{ height: 360 }} />
      )}
    </section>
  )
}
