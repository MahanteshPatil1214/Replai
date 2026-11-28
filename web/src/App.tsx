import { type ReactNode } from 'react'
import { Link, NavLink, Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import Layout from './components/Layout'
import PublicLayout from './components/PublicLayout'
import Analytics from './pages/Analytics'
import KnowledgeGaps from './pages/Gaps'
import Inbox from './pages/Inbox'
import KnowledgeBase from './pages/KnowledgeBase'
import Login from './pages/Login'
import NewTicket from './pages/NewTicket'
import Queue from './pages/Queue'
import TicketDetail from './pages/TicketDetail'
import Triage from './pages/Triage'
import { isLeadOrAdmin, useSession } from './lib/auth'
import type { EmployeeRole } from './types'

const navClass = ({ isActive }: { isActive: boolean }) =>
  `text-sm transition-colors ${isActive ? 'text-ink' : 'text-muted hover:text-ink'}`

/** Everything except login and the customer form needs a session. */
function ProtectedLayout({ nav, action }: { nav: ReactNode; action: ReactNode }) {
  const { token } = useSession()
  const location = useLocation()

  if (!token) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />
  }
  return <Layout nav={nav} action={action}>
    <Outlet />
  </Layout>
}

/** Client-side half of RBAC: screens a role may not open (the API says no too). */
function RequireRole({ roles, children }: { roles: EmployeeRole[]; children: ReactNode }) {
  const { user } = useSession()

  if (!user || !roles.includes(user.role)) {
    return (
      <div className="card p-6">
        <p className="mono text-muted">403</p>
        <h1 className="mt-2 text-3xl">Not your screen</h1>
        <p className="mt-3 text-[15px] text-muted">
          This needs one of {roles.join(' or ')} - you are signed in as{' '}
          {user?.role.toLowerCase() ?? 'nobody'}.
        </p>
        <Link to="/inbox" className="btn-quiet mt-4">
          Back to the inbox
        </Link>
      </div>
    )
  }
  return <>{children}</>
}

export default function App() {
  const { user } = useSession()
  const canSeeAnalytics = isLeadOrAdmin(user)

  const nav = (
    <>
      <NavLink to="/inbox" className={navClass}>
        Inbox
      </NavLink>
      <NavLink to="/queue" className={navClass}>
        Queue
      </NavLink>
      <NavLink to="/triage" className={navClass}>
        Triage
      </NavLink>
      {canSeeAnalytics && (
        <NavLink to="/analytics" className={navClass}>
          Analytics
        </NavLink>
      )}
      <NavLink to="/knowledge" className={navClass}>
        Knowledge base
      </NavLink>
    </>
  )

  const action = (
    <NavLink
      to="/new"
      className={({ isActive }) => (isActive ? 'btn-primary !py-2 text-xs' : 'btn-quiet !py-2 text-xs')}
    >
      New ticket
    </NavLink>
  )

  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      {/* The customer form stays public: creating a ticket needs no account. */}
      <Route
        path="/new"
        element={
          <PublicLayout>
            <NewTicket />
          </PublicLayout>
        }
      />
      <Route path="/" element={<Navigate to="/inbox" replace />} />
      <Route element={<ProtectedLayout nav={nav} action={action} />}>
        <Route path="/inbox" element={<Inbox />} />
        <Route path="/queue" element={<Queue />} />
        <Route path="/triage" element={<Triage />} />
        <Route
          path="/analytics"
          element={
            <RequireRole roles={['LEAD', 'ADMIN']}>
              <Analytics />
            </RequireRole>
          }
        />
        <Route
          path="/gaps"
          element={
            <RequireRole roles={['LEAD', 'ADMIN']}>
              <KnowledgeGaps />
            </RequireRole>
          }
        />
        <Route path="/tickets/:id" element={<TicketDetail />} />
        <Route path="/knowledge" element={<KnowledgeBase />} />
        <Route path="*" element={<Navigate to="/inbox" replace />} />
      </Route>
    </Routes>
  )
}
