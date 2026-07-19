import { useNavigate, useLocation } from "react-router-dom"
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarFooter,
  SidebarRail,
} from "@/components/ui/sidebar"
import { FileText, Table, Terminal, Database } from "@phosphor-icons/react"

const items = [
  { title: "数据源", url: "/datasources", icon: Database },
  { title: "数据集", url: "/datasets", icon: Table },
  { title: "SQL 查询", url: "/query", icon: Terminal },
]

export function AppSidebar() {
  const navigate = useNavigate()
  const location = useLocation()

  return (
    <Sidebar collapsible="icon">
      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupLabel className="flex items-center gap-2 text-base font-semibold">
            <FileText className="size-5" weight="duotone" />
            <span>BI 报表系统</span>
          </SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {items.map((item) => (
                <SidebarMenuItem key={item.url}>
                  <SidebarMenuButton
                    onClick={() => navigate(item.url)}
                    isActive={location.pathname.startsWith(item.url)}
                    tooltip={item.title}
                  >
                    <item.icon className="size-5" weight="duotone" />
                    <span>{item.title}</span>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>
      <SidebarFooter>
        <div className="px-2 py-2 text-xs text-muted-foreground">BI v1.0.0</div>
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  )
}
