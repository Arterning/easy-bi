import { useState, useEffect, useCallback } from "react"
import { useParams, Link, useNavigate } from "react-router-dom"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import {
  ArrowLeft,
  Pencil,
  Download,
  Play,
} from "@phosphor-icons/react"
import { api, type Dataset, type QueryResult } from "@/lib/api"
import { SqlEditor } from "@/components/query/SqlEditor"
import { ResultTable } from "@/components/shared/ResultTable"
import { PaginationBar } from "@/components/shared/PaginationBar"

export function DatasetDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [dataset, setDataset] = useState<Dataset | null>(null)
  const [loading, setLoading] = useState(true)

  // Query
  const [result, setResult] = useState<QueryResult | null>(null)
  const [queryLoading, setQueryLoading] = useState(false)
  const [queryError, setQueryError] = useState<string | null>(null)

  const load = useCallback(async () => {
    if (!id) return
    setLoading(true)
    try {
      const res = await api.getDataset(Number(id))
      setDataset(res.data)
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => {
    load()
  }, [load])

  const execute = useCallback(async (p = 0) => {
    if (!id || !dataset) return
    setQueryLoading(true)
    setQueryError(null)
    try {
      const res = await api.executeDataset(Number(id), p, 50)
      setResult(res.data)
    } catch (e) {
      setQueryError(e instanceof Error ? e.message : "查询失败")
      setResult(null)
    } finally {
      setQueryLoading(false)
    }
  }, [id, dataset])

  // Auto execute on first load
  useEffect(() => {
    if (dataset && !result && !queryLoading) {
      execute(0)
    }
  }, [dataset])

  const handleExport = async () => {
    if (!id || !dataset) return
    const blob = await api.exportDatasetCsv(Number(id))
    const url = URL.createObjectURL(blob)
    const a = document.createElement("a")
    a.href = url
    a.download = `${dataset.name}.csv`
    a.click()
    URL.revokeObjectURL(url)
  }

  if (loading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-64 w-full" />
      </div>
    )
  }

  if (!dataset) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
        <p className="text-lg">数据集不存在</p>
        <Link to="/datasets" className="mt-2 text-primary hover:underline">
          返回数据集列表
        </Link>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="icon" onClick={() => navigate("/datasets")}>
            <ArrowLeft className="size-5" />
          </Button>
          <div>
            <h1 className="text-2xl font-bold">{dataset.name}</h1>
            {dataset.description && (
              <p className="text-sm text-muted-foreground">{dataset.description}</p>
            )}
          </div>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => navigate(`/datasets/${id}/edit`)}>
            <Pencil className="size-4 mr-1" />
            编辑
          </Button>
          <Button variant="outline" onClick={handleExport}>
            <Download className="size-4 mr-1" />
            导出 CSV
          </Button>
          <Button onClick={() => execute(0)} disabled={queryLoading}>
            <Play className="size-4 mr-1" />
            刷新
          </Button>
        </div>
      </div>

      {/* SQL display */}
      <div>
        <p className="text-sm font-medium mb-1 text-muted-foreground">SQL 语句</p>
        <SqlEditor
          value={dataset.sql}
          onChange={() => {}}
          onExecute={() => {}}
          readOnly
        />
      </div>

      {/* Results */}
      {queryError && (
        <div className="rounded-md border border-destructive bg-destructive/10 p-4 text-sm text-destructive">
          {queryError}
        </div>
      )}

      {queryLoading && (
        <div className="space-y-2">
          <Skeleton className="h-8 w-full" />
          <Skeleton className="h-8 w-full" />
          <Skeleton className="h-8 w-full" />
        </div>
      )}

      {result && !queryLoading && (
        <div>
          <p className="text-sm text-muted-foreground mb-2">
            共 {result.totalRows} 行，当前第 {result.page + 1} 页
          </p>
          <ResultTable columns={result.columns} rows={result.rows} />
          <PaginationBar
            page={result.page}
            totalPages={Math.ceil(result.totalRows / result.size)}
            onPageChange={(p) => execute(p)}
          />
        </div>
      )}
    </div>
  )
}
