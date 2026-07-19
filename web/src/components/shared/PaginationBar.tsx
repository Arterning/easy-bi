import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination"

interface PaginationBarProps {
  page: number
  totalPages: number
  onPageChange: (page: number) => void
}

export function PaginationBar({ page, totalPages, onPageChange }: PaginationBarProps) {
  if (totalPages <= 1) return null

  const pages: number[] = []
  const start = Math.max(0, page - 2)
  const end = Math.min(totalPages - 1, page + 2)
  for (let i = start; i <= end; i++) pages.push(i)

  return (
    <Pagination className="mt-4">
      <PaginationContent>
        <PaginationItem>
          <PaginationPrevious
            onClick={() => onPageChange(Math.max(0, page - 1))}
            className={page <= 0 ? "pointer-events-none opacity-50" : "cursor-pointer"}
          />
        </PaginationItem>

        {start > 0 && (
          <>
            <PaginationItem>
              <PaginationLink onClick={() => onPageChange(0)} className="cursor-pointer">1</PaginationLink>
            </PaginationItem>
            {start > 1 && (
              <PaginationItem>
                <span className="px-2 text-muted-foreground">...</span>
              </PaginationItem>
            )}
          </>
        )}

        {pages.map((p) => (
          <PaginationItem key={p}>
            <PaginationLink
              onClick={() => onPageChange(p)}
              isActive={p === page}
              className="cursor-pointer"
            >
              {p + 1}
            </PaginationLink>
          </PaginationItem>
        ))}

        {end < totalPages - 1 && (
          <>
            {end < totalPages - 2 && (
              <PaginationItem>
                <span className="px-2 text-muted-foreground">...</span>
              </PaginationItem>
            )}
            <PaginationItem>
              <PaginationLink
                onClick={() => onPageChange(totalPages - 1)}
                className="cursor-pointer"
              >
                {totalPages}
              </PaginationLink>
            </PaginationItem>
          </>
        )}

        <PaginationItem>
          <PaginationNext
            onClick={() => onPageChange(Math.min(totalPages - 1, page + 1))}
            className={page >= totalPages - 1 ? "pointer-events-none opacity-50" : "cursor-pointer"}
          />
        </PaginationItem>
      </PaginationContent>
    </Pagination>
  )
}
