import { Link } from 'react-router-dom'
import { useTickets } from '../api/tickets'
import { Pill, categoryTone, priorityTone, statusTone } from '../components/Pill'
import { formatRelative } from '../lib/format'
import type { Ticket } from '../types'

function Row({ ticket }: { ticket: Ticket }) {
  return (
    <Link
      to={`/tickets/${ticket.id}`}
      className="card flex items-start gap-5 px-5 py-4 transition-colors hover:border-hairline-strong"
    >
      <span className="mono mt-1 w-10 shrink-0 text-muted">#{ticket.id}</span>

      <span className="min-w-0 flex-1">
        <span className="flex flex-wrap items-center gap-2">
          <span className="font-medium">{ticket.subject}</span>
          <Pill tone={priorityTone(ticket.priority)}>{ticket.priority ?? '—'}</Pill>
          <Pill tone={categoryTone(ticket.category)}>{ticket.category ?? 'unclassified'}</Pill>
          <Pill tone={statusTone(ticket.status)}>{ticket.status.toLowerCase()}</Pill>
        </span>
        <span className="mt-1 block truncate text-sm text-muted">
          {ticket.summary ?? ticket.message}
        </span>
      </span>

      <span className="shrink-0 text-right">
        <span className="mono block text-muted">{ticket.customerEmail}</span>
        <span className="mono mt-1 block text-muted">{formatRelative(ticket.createdAt)}</span>
      </span>
    </Link>
  )
}

export default function Inbox() {
  const { data, isLoading, isError, error, refetch, isFetching } = useTickets()

  if (isLoading) {
    return <p className="text-sm text-muted">Loading the inbox…</p>
  }

  if (isError) {
    return (
      <div className="card border-red-500/40 p-6">
        <p className="text-sm text-red-600 dark:text-red-400">
          Could not reach ticket-service: {error instanceof Error ? error.message : 'unknown error'}
        </p>
        <button type="button" onClick={() => void refetch()} className="btn-quiet mt-4">
          Retry
        </button>
      </div>
    )
  }

  return (
    <div>
      <div className="flex items-end justify-between gap-4">
        <div>
          <p className="mono text-muted">02 / Inbox</p>
          <h1 className="mt-2 text-4xl">Tickets</h1>
        </div>
        <p className="mono text-muted">
          {data?.length ?? 0} total {isFetching ? '· refreshing' : ''}
        </p>
      </div>

      {!data?.length ? (
        <div className="card mt-8 px-6 py-14 text-center">
          <p className="font-display text-2xl">No tickets yet</p>
          <p className="mt-2 text-sm text-muted">
            Create the first one and watch the AI fill in the details.
          </p>
          <Link to="/new" className="btn-primary mt-6">
            New ticket
          </Link>
        </div>
      ) : (
        <div className="mt-6 flex flex-col gap-3">
          {data.map((ticket) => (
            <Row key={ticket.id} ticket={ticket} />
          ))}
        </div>
      )}
    </div>
  )
}
