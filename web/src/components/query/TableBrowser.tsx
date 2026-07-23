import { useState, useEffect, useRef, useCallback } from "react"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import { CaretDown, Database, FileXls, FileText } from "@phosphor-icons/react"
import { api, type DataSourceTreeItem } from "@/lib/api"

interface ContextMenu {
  x: number
  y: number
  table: string
  column?: string
}

async function copyToClipboard(text: string) {
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    const textarea = document.createElement("textarea")
    textarea.value = text
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand("copy")
    document.body.removeChild(textarea)
  }
}

const FileIcon = ({ type }: { type: string }) =>
  type === "excel" ? (
    <FileXls className="size-3.5 text-emerald-600" weight="duotone" />
  ) : (
    <FileText className="size-3.5 text-amber-600" weight="duotone" />
  )

export function TableBrowser() {
  const [tree, setTree] = useState<DataSourceTreeItem[]>([])
  const [loading, setLoading] = useState(true)
  const [expandedDs, setExpandedDs] = useState<Set<number>>(new Set())
  const [expandedTable, setExpandedTable] = useState<Set<string>>(new Set())
  const [contextMenu, setContextMenu] = useState<ContextMenu | null>(null)
  const menuRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    api.listTables().then((res) => {
      setTree(res.data)
      setLoading(false)
    })
  }, [])

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setContextMenu(null)
      }
    }
    const handleScroll = () => setContextMenu(null)
    if (contextMenu) {
      document.addEventListener("mousedown", handleClickOutside)
      document.addEventListener("scroll", handleScroll, true)
    }
    return () => {
      document.removeEventListener("mousedown", handleClickOutside)
      document.removeEventListener("scroll", handleScroll, true)
    }
  }, [contextMenu])

  const toggleDs = (id: number) => {
    setExpandedDs((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const toggleTable = (name: string) => {
    setExpandedTable((prev) => {
      const next = new Set(prev)
      if (next.has(name)) next.delete(name)
      else next.add(name)
      return next
    })
  }

  const handleTableContextMenu = useCallback((e: React.MouseEvent, table: string) => {
    e.preventDefault()
    setContextMenu({ x: e.clientX, y: e.clientY, table })
  }, [])

  const handleColumnContextMenu = useCallback((e: React.MouseEvent, table: string, column: string) => {
    e.preventDefault()
    setContextMenu({ x: e.clientX, y: e.clientY, table, column })
  }, [])

  const handleCopyName = useCallback(async () => {
    if (!contextMenu) return
    const text = contextMenu.column ?? contextMenu.table
    await copyToClipboard(text)
    toast.success("复制成功")
    setContextMenu(null)
  }, [contextMenu])

  const handleSelectAll = useCallback(async () => {
    if (!contextMenu) return
    const text = `SELECT * FROM ${contextMenu.table}`
    await copyToClipboard(text)
    toast.success("复制成功")
    setContextMenu(null)
  }, [contextMenu])

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
        数据源
      </p>
      {tree.length === 0 ? (
        <p className="px-2 text-xs text-muted-foreground">暂无数据</p>
      ) : (
        tree.map((ds) => (
          <div key={ds.id}>
            <div
              className="flex cursor-pointer items-center gap-1 rounded-md px-2 py-1 hover:bg-accent text-sm"
              onClick={() => toggleDs(ds.id)}
            >
              <CaretDown
                className={`size-3 text-muted-foreground transition-transform ${expandedDs.has(ds.id) ? "rotate-0" : "-rotate-90"}`}
              />
              <FileIcon type={ds.fileType} />
              <span className="font-medium truncate flex-1">{ds.fileName}</span>
              <Badge variant="secondary" className="text-[10px] px-1 py-0">
                {ds.tables.length}
              </Badge>
            </div>
            {expandedDs.has(ds.id) && (
              <div className="ml-4 space-y-0.5 mt-0.5">
                {ds.tables.map((table) => (
                  <div key={table.name}>
                    <div
                      className="flex cursor-pointer items-center gap-1 rounded-md px-2 py-1 hover:bg-accent text-sm"
                      onClick={() => toggleTable(table.name)}
                      onContextMenu={(e) => handleTableContextMenu(e, table.name)}
                    >
                      <CaretDown
                        className={`size-2.5 text-muted-foreground transition-transform ${expandedTable.has(table.name) ? "rotate-0" : "-rotate-90"}`}
                      />
                      <Database className="size-3.5 text-blue-500" weight="duotone" />
                      <div className="flex-1 min-w-0">
                        <span className="font-medium truncate block">{table.displayName ?? table.name}</span>
                        {table.displayName && table.displayName !== table.name && (
                          <span className="text-[10px] text-muted-foreground font-mono truncate block">{table.name}</span>
                        )}
                      </div>
                      <Badge variant="secondary" className="ml-auto text-[10px] px-1 py-0">
                        {table.rowCount}
                      </Badge>
                    </div>
                    {expandedTable.has(table.name) && (
                      <div className="ml-5 space-y-0.5 mt-0.5">
                        {table.columns.map((col) => (
                          <div
                            key={col.name}
                            className="flex cursor-pointer items-center gap-1 rounded-md px-2 py-0.5 text-xs hover:bg-accent"
                            onContextMenu={(e) => handleColumnContextMenu(e, table.name, col.name)}
                          >
                            <span className="font-mono text-blue-600 dark:text-blue-400">{col.name}</span>
                            <span className="ml-auto text-muted-foreground">{col.type}</span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        ))
      )}

      {contextMenu && (
        <div
          ref={menuRef}
          className="fixed z-50 min-w-[120px] rounded-md border bg-popover p-1 shadow-md"
          style={{ left: contextMenu.x, top: contextMenu.y }}
        >
          <button
            className="flex w-full items-center rounded-sm px-2 py-1.5 text-sm text-popover-foreground hover:bg-accent"
            onClick={handleCopyName}
          >
            复制名称
          </button>
          {!contextMenu.column && (
            <button
              className="flex w-full items-center rounded-sm px-2 py-1.5 text-sm text-popover-foreground hover:bg-accent"
              onClick={handleSelectAll}
            >
              查询所有
            </button>
          )}
        </div>
      )}
    </div>
  )
}