import { Link } from 'react-router-dom'
import { useKnowledgeGaps } from '../api/analytics'
import { Pill, categoryTone, priorityTone } from '../components/Pill'
import { formatRelative } from '../lib/format'
import type { Ticket } from '../types'

/** The article the AI was missing, written straight from the ticket that exposed the gap. */
function articleParams(ticket: Ticket) {
  const params = new URLSearchParams()
  params.set('title', ticket.subject)
  params.set('content', `${ticket.summary ? `${ticket.summary}\n\n` : ''}${ticket.message}`)
  params.set('from', String(ticket.id))
  return params.toString()
}

export default function KnowledgeGaps() {
  const { data, isLoading, isError, error, refetch } = useKnowledgeGaps()

  if (isLoading) return <p className="text-sm text-muted">Looking for unanswered questions…</p>

  if (isError) {
    return (
      <div className="card border-red-500/40 p-6">
        <p className="text-sm text-red-600 dark:text-red-400">
          Could not reach ticket-service:{' '}
          {error instanceof Error ? error.message : 'unknown error'}
        </p>
        <button type="button" onClick={() => void refetch()} className="btn-quiet mt-4">
          Retry
        </button>
      </div>
    )
  }

  return (
    <div>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="mono text-muted">06 / Knowledge gaps</p>
          <h1 className="mt-2 text-4xl">What the AI could not answer</h1>
          <p className="mt-3 max-w-xl text-[15px] text-muted">
            Tickets flagged as knowledge gaps. Each one is a help article waiting to be written —
            start from the customer's own words.
          </p>
        </div>
        <p className="mono text-muted">{data?.length ?? 0} gaps</p>
      </div>

      {!data?.length ? (
        <div className="card mt-8 px-6 py-14 text-center">
          <p className="font-display text-2xl">No gaps</p>
          <p className="mt-2 text-sm text-muted">
            The knowledge base covered every ticket so far. Add an article when a new topic shows
            up.
          </p>
          <Link to="/knowledge" className="btn-primary mt-6">
            Open knowledge base
          </Link>
        </div>
      ) : (
        <div className="mt-6 flex flex-col gap-3">
          {data.map((ticket) => (
            <article key={ticket.id} className="card flex flex-wrap items-start gap-x-5 gap-y-3 p-5">
              <span className="mono mt-1 w-10 shrink-0 text-muted">#{ticket.id}</span>

              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium">{ticket.subject}</span>
                  <Pill tone={priorityTone(ticket.priority)}>{ticket.priority ?? 'unprioritised'}</Pill>
                  <Pill tone={categoryTone(ticket.category)}>{ticket.category ?? 'unclassified'}</Pill>
                  <Pill tone="amber">knowledge gap</Pill>
                </div>
                <p className="mt-1 line-clamp-2 text-sm text-muted">{ticket.summary ?? ticket.message}</p>
                <p className="mono mt-2 text-muted">
                  asked by {ticket.customerEmail} · {formatRelative(ticket.createdAt)}
                </p>
              </div>

              <div className="flex shrink-0 flex-wrap items-center gap-2">
                <Link to={`/tickets/${ticket.id}`} className="btn-quiet !py-1.5 text-xs">
                  View ticket
                </Link>
                <Link to={`/knowledge?${articleParams(ticket)}`} className="btn-primary !py-1.5 text-xs">
                  Create article from this ticket
                </Link>
              </div>
            </article>
          ))}
        </div>
      )}
    </div>
  )
}
