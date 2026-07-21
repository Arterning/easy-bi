import { useState, useEffect } from "react"
import { useParams, useSearchParams, useNavigate } from "react-router-dom"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { Skeleton } from "@/components/ui/skeleton"
import { SqlEditor } from "@/components/query/SqlEditor"
import { ArrowLeft, FloppyDisk } from "@phosphor-icons/react"
import { api, type Dataset } from "@/lib/api"

export function DatasetEditPage() {
  const { id } = useParams<{ id: string }>()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const isCreate = !id

  const [dataset, setDataset] = useState<Dataset | null>(null)
  const [loading, setLoading] = useState(!isCreate)
  const [name, setName] = useState("")
  const [sql, setSql] = useState(isCreate ? searchParams.get("sql") ?? "" : "")
  const [description, setDescription] = useState("")
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!id) return
    api.getDataset(Number(id)).then((res) => {
      setDataset(res.data)
      setName(res.data.name)
      setSql(res.data.sql)
      setDescription(res.data.description ?? "")
    }).finally(() => setLoading(false))
  }, [id])

  const handleSave = async () => {
    if (!name.trim() || !sql.trim()) return
    setSaving(true)
    try {
      if (isCreate) {
        const res = await api.createDataset({
          name: name.trim(),
          sql: sql.trim(),
          description: description.trim() || undefined,
        })
        navigate(`/datasets/${res.data.id}`)
      } else {
        await api.updateDataset(Number(id), {
          name: name.trim(),
          sql: sql.trim(),
          description: description.trim() || undefined,
        })
        navigate(`/datasets/${id}`)
      }
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-10 w-full" />
        <Skeleton className="h-48 w-full" />
        <Skeleton className="h-20 w-full" />
      </div>
    )
  }

  if (!isCreate && !dataset) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
        <p className="text-lg">数据集不存在</p>
        <Button variant="link" onClick={() => navigate("/datasets")}>
          返回数据集列表
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="icon" onClick={() => navigate(isCreate ? "/datasets" : `/datasets/${id}`)}>
          <ArrowLeft className="size-5" />
        </Button>
        <h1 className="text-2xl font-bold">{isCreate ? "创建数据集" : "编辑数据集"}</h1>
      </div>

      <div className="space-y-4 max-w-3xl">
        <div>
          <label className="text-sm font-medium">名称</label>
          <Input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="输入数据集名称"
            className="mt-1"
          />
        </div>

        <div>
          <label className="text-sm font-medium">SQL 语句</label>
          <div className="mt-1 min-h-[300px]">
            <SqlEditor value={sql} onChange={setSql} onExecute={() => {}} />
          </div>
        </div>

        <div>
          <label className="text-sm font-medium">描述（可选）</label>
          <Textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="输入描述"
            className="mt-1"
            rows={2}
          />
        </div>

        <div className="flex gap-2">
          <Button onClick={handleSave} disabled={!name.trim() || !sql.trim() || saving}>
            <FloppyDisk className="size-4 mr-1" />
            {saving ? "保存中..." : "保存"}
          </Button>
          <Button variant="outline" onClick={() => navigate(isCreate ? "/datasets" : `/datasets/${id}`)}>
            取消
          </Button>
        </div>
      </div>
    </div>
  )
}
