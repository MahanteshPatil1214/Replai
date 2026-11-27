import { useEffect, type ReactNode } from 'react'
import { useEmployees } from '../api/directory'
import { useActingAsId, useSetActingAsId } from '../lib/actingAs'
import ThemeToggle from './ThemeToggle'

/** The MVP stand-in for login: every action is attributed to this employee. */
function ActingAsSelect() {
  const { data: employees, isLoading } = useEmployees()
  const actingId = useActingAsId()
  const setActingId = useSetActingAsId()

  // Default to the first employee; keep the stored choice only while it is still valid.
  useEffect(() => {
    if (!employees?.length) return
    if (actingId === null || !employees.some((employee) => employee.id === actingId)) {
      setActingId(employees[0].id)
    }
  }, [employees, actingId, setActingId])

  if (isLoading || !employees?.length) return null

  return (
    <label className="flex items-center gap-2">
      <span className="mono hidden text-muted sm:inline">ACTING AS</span>
      <select
        aria-label="Acting as"
        className="rounded-lg border border-hairline bg-surface px-2 py-1.5 text-xs text-ink focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
        value={actingId ?? ''}
        onChange={(event) => setActingId(Number(event.target.value))}
      >
        {employees.map((employee) => (
          <option key={employee.id} value={employee.id}>
            {employee.name}
            {employee.role === 'LEAD' ? ' (lead)' : ''}
          </option>
        ))}
      </select>
    </label>
  )
}

export default function Layout({
  children,
  nav,
  action,
}: {
  children: ReactNode
  nav: ReactNode
  action: ReactNode
}) {
  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-10 border-b border-hairline bg-paper/90 backdrop-blur">
        <div className="flex flex-wrap items-center gap-x-7 gap-y-3 px-6 py-4">
          <a href="/" className="font-display text-xl leading-none tracking-tight">
            Replai
          </a>
          <nav className="flex flex-wrap items-center gap-x-6 gap-y-2">{nav}</nav>
          <div className="ml-auto flex flex-wrap items-center gap-3">
            {action}
            <ActingAsSelect />
          </div>
          <ThemeToggle />
        </div>
      </header>

      <main className="px-6 py-10">{children}</main>

      <footer className="border-t border-hairline px-6 py-6 text-xs text-muted">
        Replai — tickets are classified, prioritised and drafted with retrieval over your knowledge base.
      </footer>
    </div>
  )
}
