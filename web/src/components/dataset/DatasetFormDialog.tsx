import { useState } from "react"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { SqlEditor } from "@/components/query/SqlEditor"
import type { Dataset, DatasetCreateRequest } from "@/lib/api"

interface DatasetFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  initial?: Dataset
  onSave: (req: DatasetCreateRequest) => Promise<void>
}

export function DatasetFormDialog({ open, onOpenChange, initial, onSave }: DatasetFormDialogProps) {
  const [name, setName] = useState(initial?.name ?? "")
  const [sql, setSql] = useState(initial?.sql ?? "")
  const [description, setDescription] = useState(initial?.description ?? "")
  const [saving, setSaving] = useState(false)

  const isEdit = !!initial

  const handleSave = async () => {
    if (!name.trim() || !sql.trim()) return
    setSaving(true)
    try {
      await onSave({
        name: name.trim(),
        sql: sql.trim(),
        description: description.trim() || undefined,
      })
      onOpenChange(false)
    } finally {
      setSaving(false)
    }
  }

  // Reset on open
  if (open && !initial && name !== "" && sql !== "") {
    setName("")
    setSql("")
    setDescription("")
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{isEdit ? "编辑数据集" : "创建数据集"}</DialogTitle>
          <DialogDescription>
            {isEdit ? "修改数据集的名称或 SQL" : "定义 SQL 查询并保存为可复用的数据集"}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3">
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
            <div className="mt-1 min-h-[180px]">
              <SqlEditor
                value={sql}
                onChange={setSql}
                onExecute={() => {}}
              />
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
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button onClick={handleSave} disabled={!name.trim() || !sql.trim() || saving}>
            {saving ? "保存中..." : "保存"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
