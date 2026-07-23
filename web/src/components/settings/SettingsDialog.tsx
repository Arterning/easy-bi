import { useState, useEffect } from "react"
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
import { api, type LlmSettings } from "@/lib/api"
import { toast } from "sonner"

interface SettingsDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function SettingsDialog({ open, onOpenChange }: SettingsDialogProps) {
  const [baseUrl, setBaseUrl] = useState("")
  const [apiKey, setApiKey] = useState("")
  const [model, setModel] = useState("")
  const [maxTokens, setMaxTokens] = useState(4096)
  const [temperature, setTemperature] = useState(0.1)
  const [saving, setSaving] = useState(false)
  const [loading, setLoading] = useState(false)

  // Load settings on open
  useEffect(() => {
    if (!open) return
    setLoading(true)
    api.getLlmSettings()
      .then((res) => {
        const s: LlmSettings = res.data
        setBaseUrl(s.baseUrl ?? "")
        setApiKey(s.apiKey ?? "")
        setModel(s.model ?? "")
        setMaxTokens(s.maxTokens ?? 4096)
        setTemperature(s.temperature ?? 0.1)
      })
      .catch((e) => toast.error("加载设置失败: " + e.message))
      .finally(() => setLoading(false))
  }, [open])

  const handleSave = async () => {
    if (!baseUrl.trim() || !model.trim()) return
    setSaving(true)
    try {
      await api.updateLlmSettings({
        baseUrl: baseUrl.trim(),
        apiKey: apiKey.trim(),  // backend keeps old key if this contains **** or empty
        model: model.trim(),
        maxTokens,
        temperature,
      })
      toast.success("设置已保存")
      onOpenChange(false)
    } catch (e) {
      toast.error("保存失败: " + (e as Error).message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>LLM 设置</DialogTitle>
          <DialogDescription>
            配置 AI 助手使用的模型参数。保存后立即生效。
          </DialogDescription>
        </DialogHeader>

        {loading ? (
          <div className="py-8 text-center text-muted-foreground text-sm">加载中...</div>
        ) : (
          <div className="space-y-3">
            <div>
              <label className="text-sm font-medium">Base URL</label>
              <Input
                value={baseUrl}
                onChange={(e) => setBaseUrl(e.target.value)}
                placeholder="https://api.deepseek.com/v1"
                className="mt-1"
              />
            </div>
            <div>
              <label className="text-sm font-medium">API Key</label>
              <Input
                type="password"
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                placeholder="留空或 **** 表示不修改"
                className="mt-1"
              />
              <p className="text-[10px] text-muted-foreground mt-0.5">
                密钥加密存储，回显时脱敏显示
              </p>
            </div>
            <div>
              <label className="text-sm font-medium">Model</label>
              <Input
                value={model}
                onChange={(e) => setModel(e.target.value)}
                placeholder="deepseek-chat"
                className="mt-1"
              />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-sm font-medium">Max Tokens</label>
                <Input
                  type="number"
                  value={maxTokens}
                  onChange={(e) => setMaxTokens(Number(e.target.value) || 0)}
                  min={1}
                  className="mt-1"
                />
              </div>
              <div>
                <label className="text-sm font-medium">Temperature</label>
                <Input
                  type="number"
                  value={temperature}
                  onChange={(e) => setTemperature(Number(e.target.value) || 0)}
                  min={0}
                  max={2}
                  step={0.1}
                  className="mt-1"
                />
              </div>
            </div>
          </div>
        )}

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button onClick={handleSave} disabled={!baseUrl.trim() || !model.trim() || saving || loading}>
            {saving ? "保存中..." : "保存"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
