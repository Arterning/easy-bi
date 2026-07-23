// ---- API types (mirrors backend DTOs) ----

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

// DataSource
export interface DataSource {
  id: number
  fileName: string
  fileType: string
  fileSize: number
  createdAt: string
}

export interface ColumnInfo {
  name: string
  type: string
}

export interface TableInfo {
  name: string
  displayName: string
  sourceSheet: string | null
  rowCount: number
  columns: ColumnInfo[]
}

export interface DataSourceTreeItem {
  id: number
  fileName: string
  fileType: string
  tables: TableInfo[]
}

export interface UploadResult {
  dataSourceId: number
  fileName: string
  fileType: string
  fileSize: number
  tables: TableInfo[]
}

export interface PreviewResult {
  columns: ColumnInfo[]
  rows: unknown[][]
  totalRows: number
}

export interface DataSourceDetail {
  id: number
  fileName: string
  fileType: string
  fileSize: number
  createdAt: string
  tables: TableInfo[]
}

// Dataset
export interface Dataset {
  id: number
  name: string
  sql: string
  description: string | null
  createdAt: string
  updatedAt: string
}

export interface DatasetCreateRequest {
  name: string
  sql: string
  description?: string
}

// Append
export interface AppendResult {
  dataSourceId: number
  fileName: string
  tables: TableAppend[]
}

export interface TableAppend {
  tableName: string
  rowsBefore: number
  rowsAppended: number
  rowsAfter: number
  newColumns: string[]
  matchedColumns: string[]
  missingColumns: string[]
  skipped: boolean
  skipReason: string | null
}

// Query
export interface QueryResult {
  columns: string[]
  rows: unknown[][]
  totalRows: number
  page: number
  size: number
}

// Pagination
export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// Settings / LLM
export interface LlmSettings {
  baseUrl: string
  apiKey: string   // masked (e.g. "sk-****abcd")
  model: string
  maxTokens: number
  temperature: number
}

export interface LlmSettingsUpdate {
  baseUrl: string
  apiKey: string   // send masked ("****") or empty to keep existing
  model: string
  maxTokens: number
  temperature: number
}

// ---- API client ----

// In production (after build), frontend is served by backend on same origin.
// In dev, backend runs on port 8080 separately.
const BASE_URL = import.meta.env.PROD ? "/api" : "http://localhost:8080/api"

async function request<T>(
  url: string,
  options?: RequestInit,
): Promise<ApiResponse<T>> {
  const res = await fetch(`${BASE_URL}${url}`, {
    headers: { "Content-Type": "application/json" },
    ...options,
  })
  if (!res.ok) {
    const body = await res.json().catch(() => ({ message: res.statusText }))
    throw new Error(body.message ?? `HTTP ${res.status}`)
  }
  return res.json()
}

async function download(url: string): Promise<Blob> {
  const res = await fetch(`${BASE_URL}${url}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.blob()
}

export const api = {
  // DataSources
  uploadFile(file: File) {
    const form = new FormData()
    form.append("file", file)
    return fetch(`${BASE_URL}/datasources/upload`, {
      method: "POST",
      body: form,
    }).then((r) => {
      if (!r.ok) return r.json().then((b) => { throw new Error(b.message ?? "Upload failed") })
      return r.json() as Promise<ApiResponse<UploadResult>>
    })
  },

  listDataSources(page = 0, size = 20) {
    return request<Page<DataSource>>(
      `/datasources?page=${page}&size=${size}`,
    )
  },

  getDataSource(id: number) {
    return request<DataSourceDetail>(`/datasources/${id}`)
  },

  deleteDataSource(id: number) {
    return request<void>(`/datasources/${id}`, { method: "DELETE" })
  },

  previewTable(id: number, table: string, rows = 20) {
    return request<PreviewResult>(
      `/datasources/${id}/preview?table=${table}&rows=${rows}`,
    )
  },

  appendDataSource(id: number, file: File) {
    const form = new FormData()
    form.append("file", file)
    return fetch(`${BASE_URL}/datasources/${id}/append`, {
      method: "POST",
      body: form,
    }).then((r) => {
      if (!r.ok) return r.json().then((b) => { throw new Error(b.message ?? "Append failed") })
      return r.json() as Promise<ApiResponse<AppendResult>>
    })
  },

  // Datasets
  createDataset(req: DatasetCreateRequest) {
    return request<Dataset>("/datasets", {
      method: "POST",
      body: JSON.stringify(req),
    })
  },

  listDatasets(page = 0, size = 20) {
    return request<Page<Dataset>>(`/datasets?page=${page}&size=${size}`)
  },

  getDataset(id: number) {
    return request<Dataset>(`/datasets/${id}`)
  },

  updateDataset(id: number, req: DatasetCreateRequest) {
    return request<Dataset>(`/datasets/${id}`, {
      method: "PUT",
      body: JSON.stringify(req),
    })
  },

  deleteDataset(id: number) {
    return request<void>(`/datasets/${id}`, { method: "DELETE" })
  },

  executeDataset(id: number, page = 0, size = 50) {
    return request<QueryResult>(`/datasets/${id}/execute`, {
      method: "POST",
      body: JSON.stringify({ page, size }),
    })
  },

  exportDatasetCsv(id: number) {
    return download(`/datasets/${id}/export?format=csv`)
  },

  // Query
  executeQuery(sql: string, page = 0, size = 50) {
    return request<QueryResult>("/query/execute", {
      method: "POST",
      body: JSON.stringify({ sql, page, size }),
    })
  },

  listTables() {
    return request<DataSourceTreeItem[]>("/query/tables")
  },

  // Settings
  getLlmSettings() {
    return request<LlmSettings>("/settings/llm")
  },

  updateLlmSettings(req: LlmSettingsUpdate) {
    return request<LlmSettings>("/settings/llm", {
      method: "PUT",
      body: JSON.stringify(req),
    })
  },
}
