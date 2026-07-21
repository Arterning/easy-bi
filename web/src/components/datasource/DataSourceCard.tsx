import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible"
import { CaretDown, Eye, Trash, FileCsv, FileXls, Plus } from "@phosphor-icons/react"
import type { DataSourceDetail } from "@/lib/api"

interface DataSourceCardProps {
  ds: DataSourceDetail
  onPreview: (tableName: string) => void
  onAppend: (id: number) => void
  onDelete: (id: number) => void
}

export function DataSourceCard({ ds, onPreview, onAppend, onDelete }: DataSourceCardProps) {
  const [open, setOpen] = useState(false)

  return (
    <Collapsible open={open} onOpenChange={setOpen} className="rounded-lg border">
      <CollapsibleTrigger className="flex w-full cursor-pointer items-center justify-between p-4 hover:bg-accent/50 transition-colors">
          <div className="flex items-center gap-3">
            <CaretDown
              className={`size-4 text-muted-foreground transition-transform ${open ? "rotate-0" : "-rotate-90"}`}
            />
            {ds.fileType === "csv" ? (
              <FileCsv className="size-5 text-green-500" weight="duotone" />
            ) : (
              <FileXls className="size-5 text-blue-500" weight="duotone" />
            )}
            <div>
              <p className="font-medium">{ds.fileName}</p>
              <div className="flex gap-2 text-xs text-muted-foreground">
                <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                  {ds.fileType.toUpperCase()}
                </Badge>
                <span>{(ds.fileSize / 1024).toFixed(1)} KB</span>
                <span>{new Date(ds.createdAt).toLocaleDateString("zh-CN")}</span>
              </div>
            </div>
          </div>
          <div className="flex items-center gap-1" onClick={(e) => e.stopPropagation()}>
            <Button variant="ghost" size="sm" onClick={() => onAppend(ds.id)}>
              <Plus className="size-4 mr-1" />
              追加
            </Button>
            <Button
              variant="ghost"
              size="sm"
              className="text-destructive hover:text-destructive"
              onClick={() => onDelete(ds.id)}
            >
              <Trash className="size-4" />
            </Button>
          </div>
      </CollapsibleTrigger>

      <CollapsibleContent>
        <div className="border-t px-4 py-3 space-y-3">
          {ds.tables.map((table) => (
            <div key={table.name} className="flex items-center justify-between rounded-md bg-muted/50 px-3 py-2">
              <div>
                <p className="text-sm font-medium font-mono">{table.name}</p>
                <p className="text-xs text-muted-foreground">
                  {table.rowCount} 行 &middot;{" "}
                  {table.columns.map((c) => `${c.name} (${c.type})`).join(", ")}
                </p>
              </div>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => onPreview(table.name)}
              >
                <Eye className="size-4 mr-1" />
                预览
              </Button>
            </div>
          ))}
        </div>
      </CollapsibleContent>
    </Collapsible>
  )
}
