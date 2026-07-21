import { useState, useRef } from "react"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Badge } from "@/components/ui/badge"
import { Upload, CheckCircle, PlusCircle, WarningCircle, SkipForward } from "@phosphor-icons/react"
import { api, type AppendResult, type TableAppend } from "@/lib/api"

interface AppendDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  dataSourceId: number
  tableCount: number
}

export function AppendDialog({ open, onOpenChange, dataSourceId, tableCount }: AppendDialogProps) {
  const [file, setFile] = useState<File | null>(null)
  const [uploading, setUploading] = useState(false)
  const [result, setResult] = useState<AppendResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const fileRef = useRef<HTMLInputElement>(null)

  const handleAppend = async () => {
    if (!file) return
    setUploading(true)
    setError(null)
    try {
      const res = await api.appendDataSource(dataSourceId, file)
      setResult(res.data)
    } catch (e) {
      setError(e instanceof Error ? e.message : "追加失败")
    } finally {
      setUploading(false)
    }
  }

  const handleClose = () => {
    setFile(null)
    setResult(null)
    setError(null)
    onOpenChange(false)
  }

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault()
    const f = e.dataTransfer.files[0]
    if (f) setFile(f)
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>追加数据</DialogTitle>
          <DialogDescription>
            上传同结构文件，按 Sheet 顺序追加到 {tableCount} 张表
          </DialogDescription>
        </DialogHeader>

        {!result ? (
          <>
            <div
              className="flex flex-col items-center justify-center gap-4 rounded-lg border border-dashed p-8 cursor-pointer hover:bg-accent/50 transition-colors"
              onDragOver={(e) => e.preventDefault()}
              onDrop={handleDrop}
              onClick={() => fileRef.current?.click()}
            >
              <Upload className="size-10 text-muted-foreground" weight="duotone" />
              <div className="text-center text-sm">
                <p className="font-medium">拖拽文件 或 点击选择</p>
                <p className="text-muted-foreground">CSV / XLS / XLSX</p>
              </div>
              <input
                ref={fileRef}
                type="file"
                accept=".csv,.xls,.xlsx"
                className="hidden"
                onChange={(e) => {
                  const f = e.target.files?.[0]
                  if (f) setFile(f)
                }}
              />
            </div>

            {file && (
              <p className="text-sm text-center">
                已选择: <span className="font-medium">{file.name}</span> ({(file.size / 1024).toFixed(1)} KB)
              </p>
            )}

            {error && (
              <div className="rounded-md border border-destructive bg-destructive/10 p-3 text-sm text-destructive">
                {error}
              </div>
            )}

            <DialogFooter>
              <Button variant="outline" onClick={handleClose}>取消</Button>
              <Button onClick={handleAppend} disabled={!file || uploading}>
                {uploading ? "追加中..." : "确认追加"}
              </Button>
            </DialogFooter>
          </>
        ) : (
          <>
            <div className="space-y-3">
              {result.tables.map((ta: TableAppend) => (
                <div key={ta.tableName} className="rounded-md border p-3">
                  <div className="flex items-center justify-between mb-2">
                    <span className="font-mono font-medium text-sm">{ta.tableName}</span>
                    {ta.skipped ? (
                      <Badge variant="secondary"><SkipForward className="size-3 mr-1" />跳过</Badge>
                    ) : (
                      <Badge variant="default"><CheckCircle className="size-3 mr-1" />成功</Badge>
                    )}
                  </div>

                  {ta.skipped ? (
                    <p className="text-xs text-muted-foreground">{ta.skipReason}</p>
                  ) : (
                    <div className="space-y-1 text-xs text-muted-foreground">
                      <p>
                        {ta.rowsBefore.toLocaleString()} → {ta.rowsAfter.toLocaleString()} 行
                        （+{ta.rowsAppended.toLocaleString()}）
                      </p>
                      {ta.matchedColumns.length > 0 && (
                        <p><CheckCircle className="size-3 inline text-green-500 mr-1" />
                          {ta.matchedColumns.length} 列匹配</p>
                      )}
                      {ta.newColumns.length > 0 && (
                        <p><PlusCircle className="size-3 inline text-blue-500 mr-1" />
                          新增 {ta.newColumns.length} 列: {ta.newColumns.join(", ")}</p>
                      )}
                      {ta.missingColumns.length > 0 && (
                        <p><WarningCircle className="size-3 inline text-yellow-500 mr-1" />
                          缺失 {ta.missingColumns.length} 列 (填 NULL): {ta.missingColumns.join(", ")}</p>
                      )}
                    </div>
                  )}
                </div>
              ))}
            </div>

            <DialogFooter>
              <Button variant="outline" onClick={handleClose}>关闭</Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}
