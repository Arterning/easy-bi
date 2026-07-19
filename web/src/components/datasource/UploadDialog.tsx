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
import { Upload } from "@phosphor-icons/react"

interface UploadDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onUpload: (file: File) => Promise<void>
}

export function UploadDialog({ open, onOpenChange, onUpload }: UploadDialogProps) {
  const [file, setFile] = useState<File | null>(null)
  const [uploading, setUploading] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)

  const handleUpload = async () => {
    if (!file) return
    setUploading(true)
    try {
      await onUpload(file)
      setFile(null)
      onOpenChange(false)
    } finally {
      setUploading(false)
    }
  }

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault()
    const f = e.dataTransfer.files[0]
    if (f) setFile(f)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>上传数据源</DialogTitle>
          <DialogDescription>支持 CSV / Excel (.xls / .xlsx) 文件</DialogDescription>
        </DialogHeader>

        <div
          className="flex flex-col items-center justify-center gap-4 rounded-lg border border-dashed p-8 cursor-pointer hover:bg-accent/50 transition-colors"
          onDragOver={(e) => e.preventDefault()}
          onDrop={handleDrop}
          onClick={() => fileRef.current?.click()}
        >
          <Upload className="size-10 text-muted-foreground" weight="duotone" />
          <div className="text-center text-sm">
            <p className="font-medium">拖拽文件到此处</p>
            <p className="text-muted-foreground">或点击选择文件</p>
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

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button onClick={handleUpload} disabled={!file || uploading}>
            {uploading ? "上传中..." : "确认上传"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
