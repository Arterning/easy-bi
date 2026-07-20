import { useEffect, useRef } from "react"
import { EditorView, keymap, placeholder, lineNumbers } from "@codemirror/view"
import { EditorState } from "@codemirror/state"
import { sql } from "@codemirror/lang-sql"
import { defaultKeymap } from "@codemirror/commands"
import { searchKeymap } from "@codemirror/search"
import { syntaxHighlighting, defaultHighlightStyle } from "@codemirror/language"

interface SqlEditorProps {
  value: string
  onChange: (value: string) => void
  onExecute: () => void
  readOnly?: boolean
}

export function SqlEditor({ value, onChange, onExecute, readOnly = false }: SqlEditorProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const viewRef = useRef<EditorView | null>(null)

  useEffect(() => {
    if (!containerRef.current) return

    const sqlLang = sql({})

    const view = new EditorView({
      doc: value,
      extensions: [
        sqlLang,
        lineNumbers(),
        placeholder("输入 SQL 查询语句..."),
        syntaxHighlighting(defaultHighlightStyle),
        keymap.of([
          ...defaultKeymap,
          ...searchKeymap,
          {
            key: "Ctrl-Enter",
            run: () => {
              onExecute()
              return true
            },
          },
        ]),
        EditorView.updateListener.of((update) => {
          if (update.docChanged) {
            onChange(update.state.doc.toString())
          }
        }),
        EditorState.readOnly.of(readOnly),
        EditorView.theme({
          "&": { fontSize: "14px", height: "100%" },
          ".cm-scroller": { fontFamily: "'Jetbrains Mono Variable', monospace", overflow: "auto" },
          ".cm-gutters": { display: readOnly ? "none" : "" },
        }),
      ],
      parent: containerRef.current,
    })

    viewRef.current = view
    return () => view.destroy()
  }, [])

  // Sync external value changes
  useEffect(() => {
    if (viewRef.current && viewRef.current.state.doc.toString() !== value) {
      viewRef.current.dispatch({
        changes: {
          from: 0,
          to: viewRef.current.state.doc.length,
          insert: value,
        },
      })
    }
  }, [value])

  return (
    <div
      ref={containerRef}
      className="h-[600px] rounded-md border bg-background overflow-hidden"
    />
  )
}
