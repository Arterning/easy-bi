import { useState, useEffect, useCallback } from "react"
import { Link, useNavigate } from "react-router-dom"
import { Button } from "@/components/ui/button"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Skeleton } from "@/components/ui/skeleton"
import { Plus, Pencil, Trash, Play, Download } from "@phosphor-icons/react"
import { api, type Dataset } from "@/lib/api"
import { PaginationBar } from "@/components/shared/PaginationBar"
import { DatasetFormDialog } from "@/components/dataset/DatasetFormDialog"
import { ConfirmDialog } from "@/components/shared/ConfirmDialog"

export function DatasetsPage() {
  const [datasets, setDatasets] = useState<Dataset[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)

  // Dialogs
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Dataset | null>(null)
  const navigate = useNavigate()
  const [deleteTarget, setDeleteTarget] = useState<number | null>(null)

  const load = useCallback(async (p = page) => {
    setLoading(true)
    try {
      const res = await api.listDatasets(p, 20)
      setDatasets(res.data.content)
      setTotalPages(res.data.totalPages)
    } finally {
      setLoading(false)
    }
  }, [page])

  useEffect(() => {
    load()
  }, [load])

  const handleCreate = async (req: { name: string; sql: string; description?: string }) => {
    await api.createDataset(req)
    setPage(0)
    await load(0)
  }

  const handleUpdate = async (req: { name: string; sql: string; description?: string }) => {
    if (!editing) return
    await api.updateDataset(editing.id, req)
    setEditing(null)
    await load(page)
  }

  const handleDelete = async () => {
    if (deleteTarget == null) return
    await api.deleteDataset(deleteTarget)
    setDeleteTarget(null)
    await load(page)
  }

  const handleExport = async (ds: Dataset) => {
    const blob = await api.exportDatasetCsv(ds.id)
    const url = URL.createObjectURL(blob)
    const a = document.createElement("a")
    a.href = url
    a.download = `${ds.name}.csv`
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">数据集管理</h1>
        <Button onClick={() => { setEditing(null); setFormOpen(true) }}>
          <Plus className="size-4 mr-1" />
          创建数据集
        </Button>
      </div>

      {loading ? (
        <div className="space-y-2">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
        </div>
      ) : datasets.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
          <p className="text-lg">暂无数据集</p>
          <p className="text-sm">创建数据集来保存常用的 SQL 查询</p>
        </div>
      ) : (
        <div className="overflow-auto rounded-md border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>名称</TableHead>
                <TableHead className="hidden md:table-cell">SQL</TableHead>
                <TableHead className="hidden md:table-cell">更新时间</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {datasets.map((ds) => (
                <TableRow key={ds.id}>
                  <TableCell className="font-medium">
                    <Link to={`/datasets/${ds.id}`} className="hover:underline text-primary">
                      {ds.name}
                    </Link>
                    {ds.description && (
                      <p className="text-xs text-muted-foreground">{ds.description}</p>
                    )}
                  </TableCell>
                  <TableCell className="hidden md:table-cell max-w-64 truncate font-mono text-xs">
                    {ds.sql.length > 60 ? ds.sql.slice(0, 60) + "..." : ds.sql}
                  </TableCell>
                  <TableCell className="hidden md:table-cell text-sm text-muted-foreground whitespace-nowrap">
                    {new Date(ds.updatedAt).toLocaleString("zh-CN")}
                  </TableCell>
                  <TableCell className="text-right whitespace-nowrap">
                    <div className="flex justify-end gap-1">
                      <Button variant="ghost" size="icon" onClick={() => navigate(`/datasets/${ds.id}`)}>
                        <Play className="size-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => { setEditing(ds); setFormOpen(true) }}
                      >
                        <Pencil className="size-4" />
                      </Button>
                      <Button variant="ghost" size="icon" onClick={() => handleExport(ds)}>
                        <Download className="size-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="text-destructive hover:text-destructive"
                        onClick={() => setDeleteTarget(ds.id)}
                      >
                        <Trash className="size-4" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <PaginationBar page={page} totalPages={totalPages} onPageChange={(p) => { setPage(p); load(p) }} />

      <DatasetFormDialog
        open={formOpen && !editing}
        onOpenChange={setFormOpen}
        onSave={handleCreate}
      />

      {editing && (
        <DatasetFormDialog
          open
          onOpenChange={(o) => { if (!o) setEditing(null) }}
          initial={editing}
          onSave={handleUpdate}
        />
      )}

      <ConfirmDialog
        open={deleteTarget != null}
        onOpenChange={(o) => { if (!o) setDeleteTarget(null) }}
        title="删除数据集"
        description="确认删除该数据集？此操作不可恢复。"
        confirmText="确认删除"
        variant="destructive"
        onConfirm={handleDelete}
      />
    </div>
  )
}
