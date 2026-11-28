import { type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import ThemeToggle from './ThemeToggle'

/**
 * Header for the two public screens (sign in and the customer form):
 * no nav, because those pages work without an account.
 */
export default function PublicLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen">
      <header className="border-b border-hairline bg-paper/90 backdrop-blur">
        <div className="flex items-center gap-x-7 px-6 py-4">
          <Link to="/" className="font-display text-xl leading-none tracking-tight">
            Replai
          </Link>
          <Link to="/login" className="ml-auto text-sm text-muted transition-colors hover:text-ink">
            Sign in
          </Link>
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
