import { useState, useEffect } from "react"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import { CaretDown, Database } from "@phosphor-icons/react"
import { api, type TableInfo } from "@/lib/api"

interface TableBrowserProps {
  onInsertTable: (name: string) => void
  onInsertColumn: (table: string, column: string) => void
}

export function TableBrowser({ onInsertTable, onInsertColumn }: TableBrowserProps) {
  const [tables, setTables] = useState<TableInfo[]>([])
  const [loading, setLoading] = useState(true)
  const [expanded, setExpanded] = useState<Set<string>>(new Set())

  useEffect(() => {
    api.listTables().then((res) => {
      setTables(res.data)
      setLoading(false)
    })
  }, [])

  const toggle = (name: string) => {
    setExpanded((prev) => {
      const next = new Set(prev)
      if (next.has(name)) next.delete(name)
      else next.add(name)
      return next
    })
  }

  if (loading) {
    return (
      <div className="space-y-2 p-2">
        <Skeleton className="h-6 w-full" />
        <Skeleton className="h-6 w-full" />
      </div>
    )
  }

  return (
    <div className="space-y-1">
      <p className="px-2 text-xs font-medium text-muted-foreground uppercase tracking-wider">
        可用表
      </p>
      {tables.length === 0 ? (
        <p className="px-2 text-xs text-muted-foreground">暂无数据表</p>
      ) : (
        tables.map((table) => (
          <div key={table.name}>
            <div
              className="flex cursor-pointer items-center gap-1 rounded-md px-2 py-1 hover:bg-accent text-sm"
              onClick={() => {
                toggle(table.name)
                onInsertTable(table.name)
              }}
            >
              <CaretDown
                className={`size-3 text-muted-foreground transition-transform ${expanded.has(table.name) ? "rotate-0" : "-rotate-90"}`}
              />
              <Database className="size-3.5 text-blue-500" weight="duotone" />
              <span className="font-medium">{table.name}</span>
              <Badge variant="secondary" className="ml-auto text-[10px] px-1 py-0">
                {table.rowCount}
              </Badge>
            </div>
            {expanded.has(table.name) && (
              <div className="ml-5 space-y-0.5 mt-0.5">
                {table.columns.map((col) => (
                  <div
                    key={col.name}
                    className="flex cursor-pointer items-center gap-1 rounded-md px-2 py-0.5 text-xs hover:bg-accent"
                    onClick={() => onInsertColumn(table.name, col.name)}
                  >
                    <span className="font-mono text-blue-600 dark:text-blue-400">{col.name}</span>
                    <span className="ml-auto text-muted-foreground">{col.type}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        ))
      )}
    </div>
  )
}
