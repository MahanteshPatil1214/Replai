import { NavLink, Navigate, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import Analytics from './pages/Analytics'
import KnowledgeGaps from './pages/Gaps'
import Inbox from './pages/Inbox'
import KnowledgeBase from './pages/KnowledgeBase'
import NewTicket from './pages/NewTicket'
import Queue from './pages/Queue'
import TicketDetail from './pages/TicketDetail'
import Triage from './pages/Triage'

const navClass = ({ isActive }: { isActive: boolean }) =>
  `text-sm transition-colors ${isActive ? 'text-ink' : 'text-muted hover:text-ink'}`

export default function App() {
  return (
    <Layout
      nav={
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
          <NavLink to="/analytics" className={navClass}>
            Analytics
          </NavLink>
          <NavLink to="/knowledge" className={navClass}>
            Knowledge base
          </NavLink>
        </>
      }
      action={
        <NavLink to="/new" className={({ isActive }) => (isActive ? 'btn-primary !py-2 text-xs' : 'btn-quiet !py-2 text-xs')}>
          New ticket
        </NavLink>
      }
    >
      <Routes>
        <Route path="/" element={<Navigate to="/inbox" replace />} />
        <Route path="/new" element={<NewTicket />} />
        <Route path="/inbox" element={<Inbox />} />
        <Route path="/queue" element={<Queue />} />
        <Route path="/triage" element={<Triage />} />
        <Route path="/analytics" element={<Analytics />} />
        <Route path="/gaps" element={<KnowledgeGaps />} />
        <Route path="/tickets/:id" element={<TicketDetail />} />
        <Route path="/knowledge" element={<KnowledgeBase />} />
        <Route path="*" element={<Navigate to="/inbox" replace />} />
      </Routes>
    </Layout>
  )
}
