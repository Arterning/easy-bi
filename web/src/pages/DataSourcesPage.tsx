import { useState, useEffect, useCallback } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { Plus, MagnifyingGlass } from "@phosphor-icons/react"
import { api, type DataSourceDetail } from "@/lib/api"
import { PaginationBar } from "@/components/shared/PaginationBar"
import { UploadDialog } from "@/components/datasource/UploadDialog"
import { PreviewSheet } from "@/components/datasource/PreviewSheet"
import { DataSourceCard } from "@/components/datasource/DataSourceCard"
import { ConfirmDialog } from "@/components/shared/ConfirmDialog"

export function DataSourcesPage() {
  const [data, setData] = useState<DataSourceDetail[]>([])
  const [totalPages, setTotalPages] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState("")

  // Dialogs
  const [uploadOpen, setUploadOpen] = useState(false)
  const [preview, setPreview] = useState<{ dsId: number; table: string } | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<number | null>(null)

  const load = useCallback(async (p = page) => {
    setLoading(true)
    try {
      const res = await api.listDataSources(p, 20)
      // filter by search
      let list = res.data.content
      if (search) {
        list = list.filter((ds) => ds.fileName.toLowerCase().includes(search.toLowerCase()))
      }
      // fetch detail for each
      const details = await Promise.all(
        list.map(async (ds) => {
          const r = await api.getDataSource(ds.id)
          return r.data
        }),
      )
      setData(details)
      setTotalPages(res.data.totalPages)
    } finally {
      setLoading(false)
    }
  }, [page, search])

  useEffect(() => {
    load()
  }, [load])

  const handleUpload = async (file: File) => {
    await api.uploadFile(file)
    setPage(0)
    await load(0)
  }

  const handleDelete = async () => {
    if (deleteTarget == null) return
    await api.deleteDataSource(deleteTarget)
    setDeleteTarget(null)
    setPage(0)
    await load(0)
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">数据源管理</h1>
        <Button onClick={() => setUploadOpen(true)}>
          <Plus className="size-4 mr-1" />
          上传文件
        </Button>
      </div>

      <div className="relative max-w-sm">
        <MagnifyingGlass className="absolute left-2.5 top-2.5 size-4 text-muted-foreground" />
        <Input
          placeholder="搜索文件名..."
          className="pl-8"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      {loading ? (
        <div className="space-y-2">
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
        </div>
      ) : data.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
          <p className="text-lg">暂无数据源</p>
          <p className="text-sm">上传 CSV 或 Excel 文件开始使用</p>
        </div>
      ) : (
        <div className="space-y-2">
          {data.map((ds) => (
            <DataSourceCard
              key={ds.id}
              ds={ds}
              onPreview={(table) => setPreview({ dsId: ds.id, table })}
              onDelete={(id) => setDeleteTarget(id)}
            />
          ))}
        </div>
      )}

      <PaginationBar page={page} totalPages={totalPages} onPageChange={(p) => { setPage(p); load(p) }} />

      <UploadDialog open={uploadOpen} onOpenChange={setUploadOpen} onUpload={handleUpload} />

      {preview && (
        <PreviewSheet
          open
          onOpenChange={(o) => { if (!o) setPreview(null) }}
          dataSourceId={preview.dsId}
          tableName={preview.table}
        />
      )}

      <ConfirmDialog
        open={deleteTarget != null}
        onOpenChange={(o) => { if (!o) setDeleteTarget(null) }}
        title="删除数据源"
        description="删除后将同时删除该数据源下的所有数据表，此操作不可恢复。"
        confirmText="确认删除"
        variant="destructive"
        onConfirm={handleDelete}
      />
    </div>
  )
}
