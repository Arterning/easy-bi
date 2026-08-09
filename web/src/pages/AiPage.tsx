import { useState, useRef, useEffect } from "react"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Brain, PaperPlaneRight, CaretDown, Spinner, XCircle,
  Plus, Trash,
} from "@phosphor-icons/react"
import { toast } from "sonner"
import { api, type ChatSession } from "@/lib/api"

interface UiMessage {
  id: string
  role: "user" | "assistant" | "tool" | "thinking" | "error"
  content: string
  toolName?: string
  toolArgs?: string
  collapsed?: boolean
}

export function AiPage() {
  // Session state
  const [sessions, setSessions] = useState<ChatSession[]>([])
  const [sessionId, setSessionId] = useState<string | null>(null)
  const [sessionsLoading, setSessionsLoading] = useState(true)

  // Chat state
  const [messages, setMessages] = useState<UiMessage[]>([])
  const [input, setInput] = useState("")
  const [loading, setLoading] = useState(false)
  const bottomRef = useRef<HTMLDivElement>(null)

  // Load session list
  const loadSessions = async () => {
    try {
      const res = await api.listSessions()
      setSessions(res.data)
    } catch {
      // ignore
    } finally {
      setSessionsLoading(false)
    }
  }

  useEffect(() => { loadSessions() }, [])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" })
  }, [messages])

  // ---- Session actions ----

  const selectSession = async (id: string) => {
    setSessionId(id)
    setMessages([])
    try {
      const res = await api.getSession(id)
      const msgs = res.data.messages ?? []
      const ui: UiMessage[] = []

      for (const m of msgs) {
        if (m.role === "system") continue // don't show system prompt in UI

        if (m.role === "user") {
          ui.push({ id: crypto.randomUUID(), role: "user", content: m.content ?? "" })
        } else if (m.role === "assistant" && m.tool_calls) {
          for (const tc of m.tool_calls) {
            ui.push({
              id: crypto.randomUUID(),
              role: "tool",
              content: "",
              toolName: tc.function.name,
              toolArgs: tc.function.arguments,
              collapsed: true,
            })
          }
        } else if (m.role === "tool") {
          // Match to the previous tool card with same tool_call_id
          for (let i = ui.length - 1; i >= 0; i--) {
            if (ui[i].role === "tool" && !ui[i].content) {
              ui[i] = { ...ui[i], content: m.content ?? "", collapsed: false }
              break
            }
          }
        } else if (m.role === "assistant" && m.content) {
          ui.push({ id: crypto.randomUUID(), role: "assistant", content: m.content })
        }
      }

      setMessages(ui)
    } catch {
      toast.error("加载会话失败")
    }
  }

  const newSession = () => {
    setSessionId(null)
    setMessages([])
  }

  const deleteSession = async (id: string) => {
    try {
      await api.deleteSession(id)
      setSessions((prev) => prev.filter((s) => s.id !== id))
      if (sessionId === id) newSession()
      toast.success("已删除")
    } catch {
      toast.error("删除失败")
    }
  }

  // ---- Chat actions ----

  const addMsg = (msg: UiMessage) => {
    setMessages((prev) => {
      if (msg.role === "thinking" && prev.length > 0 && prev[prev.length - 1].role === "thinking") {
        return [...prev.slice(0, -1), msg]
      }
      return [...prev, msg]
    })
  }

  const send = async (text?: string) => {
    const msg = text ?? input
    if (!msg.trim() || loading) return

    const isNew = sessionId == null
    const currentId = sessionId ?? crypto.randomUUID()

    if (isNew) setSessionId(currentId)

    const userMsg: UiMessage = { id: crypto.randomUUID(), role: "user", content: msg }
    setMessages((prev) => [...prev, userMsg])
    setInput("")
    setLoading(true)

    try {
      const res = await api.chatStream(currentId, msg, isNew)

      if (!res.ok) {
        const err = await res.json().catch(() => ({ message: res.statusText }))
        addMsg({ id: crypto.randomUUID(), role: "error", content: err.message ?? "请求失败" })
        setLoading(false)
        return
      }

      const reader = res.body!.getReader()
      const decoder = new TextDecoder()
      let buffer = ""

      const processBlock = (block: string) => {
        const lines = block.split("\n")
        let eventType = ""
        let data = ""

        for (const line of lines) {
          if (line.startsWith("event:")) eventType = line.slice(6).trim()
          else if (line.startsWith("data:")) data += line.slice(5).trim()
        }
        if (!eventType || !data) return

        switch (eventType) {
          case "thinking":
            addMsg({ id: crypto.randomUUID(), role: "thinking", content: data })
            break
          case "tool_call":
            try {
              const tc = JSON.parse(data)
              addMsg({ id: crypto.randomUUID(), role: "tool", content: "", toolName: tc.tool, toolArgs: tc.args, collapsed: true })
            } catch { /* ignore */ }
            break
          case "tool_result":
            try {
              const tr = JSON.parse(data)
              setMessages((prev) => {
                const copy = [...prev]
                for (let i = copy.length - 1; i >= 0; i--) {
                  if (copy[i].toolName === tr.tool && !copy[i].content) {
                    copy[i] = { ...copy[i], content: tr.result, collapsed: false }
                    break
                  }
                }
                return copy
              })
            } catch { /* ignore */ }
            break
          case "message":
            addMsg({ id: crypto.randomUUID(), role: "assistant", content: data })
            break
          case "error":
            addMsg({ id: crypto.randomUUID(), role: "error", content: data })
            break
        }
      }

      while (true) {
        const { done, value } = await reader.read()
        if (value) {
          buffer += decoder.decode(value, { stream: true })
        }
        const parts = buffer.split("\n\n")
        buffer = parts.pop() ?? ""
        for (const block of parts) {
          if (block.trim()) processBlock(block)
        }
        if (done) {
          if (buffer.trim()) processBlock(buffer)
          break
        }
      }
    } catch (e) {
      addMsg({ id: crypto.randomUUID(), role: "error", content: (e as Error).message })
    } finally {
      setLoading(false)
      // Refresh session list (title may have been set)
      loadSessions()
    }
  }

  const toggle = (id: string) => {
    setMessages((prev) => prev.map((m) => (m.id === id ? { ...m, collapsed: !m.collapsed } : m)))
  }

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault()
      send()
    }
  }

  const prompts = ["有哪些数据表？", "统计各部门销售额", "看看最近的订单数据", "生成月度汇总报表"]

  return (
    <div className="flex h-[calc(100vh-6rem)] gap-0">
      {/* ====== Session Sidebar ====== */}
      <div className="w-56 border-r flex flex-col shrink-0">
        <div className="p-2 border-b">
          <Button variant="outline" size="sm" className="w-full justify-start" onClick={newSession}>
            <Plus className="size-4 mr-1" />
            新对话
          </Button>
        </div>

        <div className="flex-1 overflow-auto">
          {sessionsLoading ? (
            <div className="p-2 space-y-1">
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
            </div>
          ) : sessions.length === 0 ? (
            <p className="p-3 text-xs text-muted-foreground text-center">暂无历史会话</p>
          ) : (
            sessions.map((s) => (
              <div
                key={s.id}
                className={`group flex items-center px-3 py-2 cursor-pointer hover:bg-accent text-sm ${
                  s.id === sessionId ? "bg-accent" : ""
                }`}
                onClick={() => selectSession(s.id)}
              >
                <div className="flex-1 min-w-0">
                  <p className="truncate">{s.title || "新对话"}</p>
                  <p className="text-[10px] text-muted-foreground">
                    {new Date(s.updatedAt).toLocaleDateString("zh-CN")}
                  </p>
                </div>
                <Button
                  variant="ghost"
                  size="icon"
                  className="size-6 opacity-0 group-hover:opacity-100 shrink-0"
                  onClick={(e) => { e.stopPropagation(); deleteSession(s.id) }}
                >
                  <Trash className="size-3" />
                </Button>
              </div>
            ))
          )}
        </div>
      </div>

      {/* ====== Chat Area ====== */}
      <div className="flex-1 flex flex-col min-w-0">
        <div className="flex items-center justify-between mb-4 px-2">
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <Brain className="size-6" weight="duotone" />AI 助手
          </h1>
        </div>

        <div className="flex-1 overflow-auto space-y-3 pr-2 pl-2">
          {messages.length === 0 && (
            <div className="flex flex-col items-center justify-center h-full text-muted-foreground gap-2">
              <Brain className="size-12" weight="duotone" />
              <p className="text-lg font-medium">AI 数据分析助手</p>
              <p className="text-sm max-w-md text-center">
                告诉我你想生成什么样的报表，我会自动查看数据、编写 SQL、执行查询并展示结果。
              </p>
              <div className="flex flex-wrap gap-2 mt-4 justify-center">
                {prompts.map((q) => (
                  <Button key={q} variant="outline" size="sm" onClick={() => send(q)}>{q}</Button>
                ))}
              </div>
            </div>
          )}

          {messages.map((msg) => (
            <div key={msg.id} className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}>
              <div className={`max-w-[80%] rounded-lg px-4 py-2.5 text-sm ${
                msg.role === "user" ? "bg-primary text-primary-foreground" :
                msg.role === "error" ? "bg-destructive/10 text-destructive border border-destructive/20" :
                msg.role === "thinking" ? "bg-muted text-muted-foreground italic" :
                msg.role === "tool" ? "bg-blue-50 dark:bg-blue-950 border border-blue-200 dark:border-blue-800" :
                "bg-muted"
              }`}>
                {msg.role === "tool" && (
                  <div>
                    <div className="flex items-center gap-2 cursor-pointer font-medium text-blue-700 dark:text-blue-300" onClick={() => toggle(msg.id)}>
                      <CaretDown className={`size-3 transition-transform ${msg.collapsed ? "-rotate-90" : ""}`} />
                      <span>🔧 {msg.toolName}</span>
                      {msg.toolArgs && <span className="text-xs text-muted-foreground font-mono truncate max-w-48">({msg.toolArgs.length > 60 ? msg.toolArgs.slice(0, 60) + "…" : msg.toolArgs})</span>}
                    </div>
                    {!msg.collapsed && msg.content && (
                      <pre className="mt-2 text-xs whitespace-pre-wrap bg-background/50 rounded p-2 max-h-48 overflow-auto">{msg.content}</pre>
                    )}
                  </div>
                )}
                {msg.role === "thinking" && <div className="flex items-center gap-2"><Spinner className="size-3 animate-spin" />{msg.content}</div>}
                {msg.role === "assistant" && <div className="whitespace-pre-wrap">{msg.content}</div>}
                {msg.role === "error" && <div className="flex items-center gap-2"><XCircle className="size-4" />{msg.content}</div>}
                {msg.role === "user" && <div>{msg.content}</div>}
              </div>
            </div>
          ))}
          <div ref={bottomRef} />
        </div>

        <div className="flex gap-2 mt-4 pt-4 border-t px-2">
          <Textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={onKeyDown}
            placeholder="描述你想生成的报表，例如：统计各部门的销售额..."
            className="min-h-[60px] resize-none"
            rows={2}
            disabled={loading}
          />
          <Button onClick={() => send()} disabled={!input.trim() || loading} size="icon" className="h-[60px] w-[60px] shrink-0">
            {loading ? <Spinner className="size-5 animate-spin" /> : <PaperPlaneRight className="size-5" />}
          </Button>
        </div>
      </div>
    </div>
  )
}
