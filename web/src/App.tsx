import { NavLink, Navigate, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import Inbox from './pages/Inbox'
import KnowledgeBase from './pages/KnowledgeBase'
import NewTicket from './pages/NewTicket'
import TicketDetail from './pages/TicketDetail'

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
        <Route path="/tickets/:id" element={<TicketDetail />} />
        <Route path="/knowledge" element={<KnowledgeBase />} />
        <Route path="*" element={<Navigate to="/inbox" replace />} />
      </Routes>
    </Layout>
  )
}
