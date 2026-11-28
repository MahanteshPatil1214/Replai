import { type ReactNode } from 'react'
import { signOut, useSession } from '../lib/auth'
import ThemeToggle from './ThemeToggle'

/** Who you are signed in as (project.md section 8), with a way out. */
function SignedInUser() {
  const { user } = useSession()
  if (!user) return null

  return (
    <div className="flex items-center gap-3">
      <span className="hidden text-right sm:block">
        <span className="block text-sm leading-tight text-ink">{user.name}</span>
        <span className="mono text-muted">{user.role}</span>
      </span>
      <button type="button" className="btn-quiet !py-1.5 text-xs" onClick={() => signOut()}>
        Sign out
      </button>
    </div>
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
            <SignedInUser />
          </div>
          <ThemeToggle />
        </div>
      </header>

      <main className="px-6 py-10">{children}</main>

      <footer className="border-t border-hairline px-6 py-6 text-xs text-muted">
        Replai - tickets are classified, prioritised and drafted with retrieval over your knowledge base.
      </footer>
    </div>
  )
}
