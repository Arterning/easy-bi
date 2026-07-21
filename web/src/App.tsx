import { createBrowserRouter, RouterProvider, Navigate } from "react-router-dom"
import { AppLayout } from "@/components/layout/AppLayout"
import { DataSourcesPage } from "@/pages/DataSourcesPage"
import { DatasetsPage } from "@/pages/DatasetsPage"
import { DatasetDetailPage } from "@/pages/DatasetDetailPage"
import { DatasetEditPage } from "@/pages/DatasetEditPage"
import { QueryPage } from "@/pages/QueryPage"

const router = createBrowserRouter([
  {
    path: "/",
    element: <AppLayout />,
    children: [
      { index: true, element: <Navigate to="/datasources" replace /> },
      { path: "datasources", element: <DataSourcesPage /> },
      { path: "datasets", element: <DatasetsPage /> },
      { path: "datasets/new", element: <DatasetEditPage /> },
      { path: "datasets/:id", element: <DatasetDetailPage /> },
      { path: "datasets/:id/edit", element: <DatasetEditPage /> },
      { path: "query", element: <QueryPage /> },
    ],
  },
])

export function App() {
  return <RouterProvider router={router} />
}

export default App
