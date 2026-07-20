import { Outlet } from "react-router-dom"
import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar"
import { AppSidebar } from "./AppSidebar"
import { TooltipProvider } from "@/components/ui/tooltip"
import { Button } from "@/components/ui/button"
import { Sun, Moon, Desktop } from "@phosphor-icons/react"
import { useTheme } from "@/components/theme-provider"

const themes = ["light", "dark", "system"] as const
const themeIcons = { light: Sun, dark: Moon, system: Desktop } as const

export function AppLayout() {
  const { theme, setTheme } = useTheme()

  const cycleTheme = () => {
    const idx = themes.indexOf(theme)
    setTheme(themes[(idx + 1) % themes.length])
  }

  const Icon = themeIcons[theme]

  return (
    <TooltipProvider>
      <SidebarProvider>
        <AppSidebar />
        <SidebarInset>
          <header className="flex items-center justify-end px-6 py-3 border-b">
            <Button variant="ghost" size="icon" onClick={cycleTheme}>
              <Icon className="h-5 w-5" />
              <span className="sr-only">Toggle theme</span>
            </Button>
          </header>
          <main className="p-6">
            <Outlet />
          </main>
        </SidebarInset>
      </SidebarProvider>
    </TooltipProvider>
  )
}
