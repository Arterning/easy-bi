import { useState, useCallback } from "react"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { Play, FloppyDisk } from "@phosphor-icons/react"
import { api, type QueryResult } from "@/lib/api"
import { SqlEditor } from "@/components/query/SqlEditor"
import { TableBrowser } from "@/components/query/TableBrowser"
import { SaveDatasetDialog } from "@/components/query/SaveDatasetDialog"
import { ResultTable } from "@/components/shared/ResultTable"
import { PaginationBar } from "@/components/shared/PaginationBar"

function stripSqlComments(sql: string): string {
  return sql
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .replace(/--.*$/gm, "")
}

export function QueryPage() {
  const [sql, setSql] = useState("")
  const [result, setResult] = useState<QueryResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saveOpen, setSaveOpen] = useState(false)

  const cleanSql = stripSqlComments(sql)

  const execute = useCallback(async (p = 0) => {
    if (!cleanSql.trim()) return
    setLoading(true)
    setError(null)
    try {
      const res = await api.executeQuery(cleanSql, p, 50)
      setResult(res.data)
    } catch (e) {
      setError(e instanceof Error ? e.message : "查询失败")
      setResult(null)
    } finally {
      setLoading(false)
    }
  }, [sql])

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">SQL 查询</h1>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => setSaveOpen(true)} disabled={!sql.trim()}>
            <FloppyDisk className="size-4 mr-1" />
            保存为数据集
          </Button>
          <Button onClick={() => execute(0)} disabled={loading || !sql.trim()}>
            <Play className="size-4 mr-1" />
            执行 (Ctrl+Enter)
          </Button>
        </div>
      </div>

      <div className="flex gap-4">
        {/* Table browser */}
        <div className="w-56 shrink-0 rounded-md border p-2 max-h-[500px] overflow-auto">
          <TableBrowser />
        </div>

        {/* SQL editor */}
        <div className="flex-1 min-w-0">
          <SqlEditor value={sql} onChange={setSql} onExecute={() => execute(0)} />
        </div>
      </div>

      {/* Results */}
      {error && (
        <div className="rounded-md border border-destructive bg-destructive/10 p-4 text-sm text-destructive">
          {error}
        </div>
      )}

      {loading && (
        <div className="space-y-2">
          <Skeleton className="h-8 w-full" />
          <Skeleton className="h-8 w-full" />
          <Skeleton className="h-8 w-full" />
        </div>
      )}

      {result && !loading && (
        <div>
          <div className="flex items-center justify-between mb-2">
            <p className="text-sm text-muted-foreground">
              共 {result.totalRows} 行，当前第 {result.page + 1} 页
            </p>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                if (result.columns.length === 0) return
                const csv =
                  result.columns.join(",") +
                  "\n" +
                  result.rows.map((r) => r.map((c) => (c == null ? "" : String(c))).join(",")).join("\n")
                const blob = new Blob([csv], { type: "text/csv" })
                const url = URL.createObjectURL(blob)
                const a = document.createElement("a")
                a.href = url
                a.download = "query_result.csv"
                a.click()
                URL.revokeObjectURL(url)
              }}
            >
              导出 CSV
            </Button>
          </div>

          <ResultTable columns={result.columns} rows={result.rows} />

          <PaginationBar
            page={result.page}
            totalPages={Math.ceil(result.totalRows / result.size)}
            onPageChange={(p) => execute(p)}
          />
        </div>
      )}

      <SaveDatasetDialog open={saveOpen} onOpenChange={setSaveOpen} sql={cleanSql} />
    </div>
  )
}
