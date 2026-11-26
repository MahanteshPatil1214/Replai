import { Link, useParams } from 'react-router-dom'
import { useTicket, useUpdateStatus } from '../api/tickets'
import CopyButton from '../components/CopyButton'
import { Pill, categoryTone, priorityTone, sentimentTone, statusTone } from '../components/Pill'
import { formatTimestamp } from '../lib/format'

export default function TicketDetail() {
  const params = useParams()
  const id = Number(params.id)
  const { data: ticket, isLoading, isError, error, refetch } = useTicket(id)
  const updateStatus = useUpdateStatus()

  if (isLoading) return <p className="text-sm text-muted">Loading ticket…</p>

  if (isError || !ticket) {
    return (
      <div className="card border-red-500/40 p-6">
        <p className="text-sm text-red-600 dark:text-red-400">
          {error instanceof Error ? error.message : `Ticket ${params.id} was not found`}
        </p>
        <div className="mt-4 flex gap-2">
          <button type="button" onClick={() => void refetch()} className="btn-quiet">
            Retry
          </button>
          <Link to="/inbox" className="btn-quiet">
            Back to inbox
          </Link>
        </div>
      </div>
    )
  }

  const pending = ticket.status === 'OPEN'

  return (
    <div>
      <Link to="/inbox" className="text-sm text-muted hover:text-ink">
        ← All tickets
      </Link>

      <div className="mt-4 flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className="mono text-muted">#{ticket.id}</span>
            <Pill tone={statusTone(ticket.status)}>{ticket.status.toLowerCase()}</Pill>
          </div>
          <h1 className="mt-2 text-3xl">{ticket.subject}</h1>
          <p className="mono mt-2 text-muted">
            {ticket.customerEmail} · {formatTimestamp(ticket.createdAt)}
          </p>
        </div>

        {ticket.status !== 'RESOLVED' && (
          <button
            type="button"
            className="btn-quiet"
            disabled={updateStatus.isPending}
            onClick={() => updateStatus.mutate({ id: ticket.id, status: 'RESOLVED' })}
          >
            {updateStatus.isPending ? 'Saving…' : 'Mark resolved'}
          </button>
        )}
      </div>

      <section className="card mt-8 p-6">
        <h2 className="text-lg">Customer message</h2>
        <p className="mt-3 whitespace-pre-wrap text-[15px] leading-relaxed">{ticket.message}</p>
      </section>

      {/* AI panel — the accent colour is reserved for generated content. */}
      <section className="mt-6 rounded-xl border border-accent/45 bg-accent-soft p-6">
        <div className="flex items-center justify-between gap-4">
          <h2 className="text-lg text-accent">AI analysis</h2>
          <span className="mono text-muted">{pending ? 'running…' : 'complete'}</span>
        </div>

        {pending ? (
          <div className="mt-4 space-y-3 text-sm text-muted">
            <p>Reading the knowledge base and drafting a reply…</p>
            <div className="h-1.5 w-1/3 animate-pulse rounded-full bg-accent/40" />
          </div>
        ) : (
          <>
            <div className="mt-4 flex flex-wrap gap-2">
              <Pill tone={categoryTone(ticket.category)}>{ticket.category ?? 'General'}</Pill>
              <Pill tone={priorityTone(ticket.priority)}>Priority {ticket.priority ?? 'MEDIUM'}</Pill>
              <Pill tone={sentimentTone(ticket.sentiment)}>{ticket.sentiment ?? 'NEUTRAL'}</Pill>
            </div>

            <div className="mt-5">
              <p className="mono text-muted">SUMMARY</p>
              <p className="mt-1 text-[15px]">{ticket.summary}</p>
            </div>

            <div className="mt-5">
              <div className="flex items-center justify-between gap-3">
                <p className="mono text-muted">SUGGESTED REPLY</p>
                {ticket.suggestedReply && <CopyButton text={ticket.suggestedReply} />}
              </div>
              <p className="mt-2 whitespace-pre-wrap rounded-lg border border-accent/30 bg-surface px-4 py-3 text-[15px] leading-relaxed">
                {ticket.suggestedReply}
              </p>
            </div>

            <div className="mt-5">
              <p className="mono text-muted">BASED ON</p>
              {ticket.sources && ticket.sources.length > 0 ? (
                <ul className="mt-2 flex flex-wrap gap-2">
                  {ticket.sources.map((source) => (
                    <li key={source.articleId}>
                      <Link
                        to="/knowledge"
                        className="inline-flex items-center gap-2 rounded-lg border border-accent/35 bg-surface px-3 py-1.5 text-sm hover:border-accent"
                      >
                        <span className="mono text-muted">{source.articleId}</span>
                        {source.title}
                      </Link>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="mt-2 text-sm text-muted">
                  No matching knowledge base article — a human agent will follow up.
                </p>
              )}
            </div>
          </>
        )}
      </section>
    </div>
  )
}
