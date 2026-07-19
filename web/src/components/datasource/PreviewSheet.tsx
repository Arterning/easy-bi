import { useState } from "react"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import { ResultTable } from "@/components/shared/ResultTable"
import { Skeleton } from "@/components/ui/skeleton"
import { api, type PreviewResult } from "@/lib/api"

interface PreviewSheetProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  dataSourceId: number
  tableName: string
}

export function PreviewSheet({ open, onOpenChange, dataSourceId, tableName }: PreviewSheetProps) {
  const [data, setData] = useState<PreviewResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = async () => {
    if (!open) return
    setLoading(true)
    setError(null)
    try {
      const res = await api.previewTable(dataSourceId, tableName, 50)
      setData(res.data)
    } catch (e) {
      setError(e instanceof Error ? e.message : "加载失败")
    } finally {
      setLoading(false)
    }
  }

  // Load on open
  if (open && !data && !loading && !error) {
    load()
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-[700px] sm:max-w-[700px] overflow-auto">
        <SheetHeader>
          <SheetTitle>数据预览</SheetTitle>
          <SheetDescription>
            表 {tableName} &middot; {data ? `${data.totalRows} 行` : "..."}
          </SheetDescription>
        </SheetHeader>

        <div className="mt-4">
          {loading && (
            <div className="space-y-2">
              <Skeleton className="h-6 w-full" />
              <Skeleton className="h-6 w-full" />
              <Skeleton className="h-6 w-full" />
            </div>
          )}
          {error && <p className="text-destructive">{error}</p>}
          {data && <ResultTable columns={data.columns.map((c) => c.name)} rows={data.rows} />}
        </div>
      </SheetContent>
    </Sheet>
  )
}
