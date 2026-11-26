import type { ReactNode } from 'react'
import ThemeToggle from './ThemeToggle'

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
        <div className="mx-auto flex max-w-5xl items-center gap-7 px-6 py-4">
          <a href="/" className="font-display text-xl leading-none tracking-tight">
            Replai
          </a>
          <nav className="flex items-center gap-6">{nav}</nav>
          <div className="ml-auto flex items-center gap-2">{action}</div>
          <ThemeToggle />
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-6 py-10">{children}</main>

      <footer className="mx-auto max-w-5xl border-t border-hairline px-6 py-6 text-xs text-muted">
        Replai — tickets are classified, prioritised and drafted with retrieval over your knowledge base.
      </footer>
    </div>
  )
}
