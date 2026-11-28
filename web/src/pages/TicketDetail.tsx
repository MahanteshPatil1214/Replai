import { useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useEmployees, useTeams } from '../api/directory'
import { useResolveTicket, useTicket, useUpdateStatus } from '../api/tickets'
import CopyButton from '../components/CopyButton'
import SlaCounter from '../components/SlaCounter'
import { Pill, categoryTone, draftTone, priorityTone, sentimentTone, statusTone } from '../components/Pill'
import { useSession } from '../lib/auth'
import { formatTimestamp } from '../lib/format'
import type { DraftAction, Ticket } from '../types'

const LOW_CONFIDENCE = 0.45

const choiceButton = (selected: boolean) => (selected ? 'btn-primary' : 'btn-quiet')

/** The AI panel: summary, sentiment, confidence, sources and the draft reply. */
function AiPanel({ ticket }: { ticket: Ticket }) {
  const pending = ticket.status === 'OPEN'
  const lowConfidence = ticket.confidence !== null && ticket.confidence < LOW_CONFIDENCE

  return (
    <section className="rounded-xl border border-accent/45 bg-accent-soft p-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
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
          <div className="mt-4 flex flex-wrap items-center gap-2">
            <Pill tone={categoryTone(ticket.category)}>{ticket.category ?? 'General'}</Pill>
            <Pill tone={priorityTone(ticket.priority)}>Priority {ticket.priority ?? 'MEDIUM'}</Pill>
            <Pill tone={sentimentTone(ticket.sentiment)}>{ticket.sentiment ?? 'NEUTRAL'}</Pill>
            {ticket.confidence !== null && (
              <Pill tone={lowConfidence ? 'red' : 'violet'}>
                Confidence {Math.round(ticket.confidence * 100)}%
              </Pill>
            )}
          </div>

          {lowConfidence && (
            <p className="mt-3 text-sm text-red-600 dark:text-red-400">
              Low confidence — the retrieval found nothing convincing, so this draft needs a human
              before it goes out.
            </p>
          )}
          {ticket.needsHuman && !lowConfidence && (
            <p className="mt-3 text-sm text-amber-700 dark:text-amber-400">
              Flagged as needing a human.
            </p>
          )}

          <div className="mt-5">
            <p className="mono text-muted">SUMMARY</p>
            <p className="mt-1 text-[15px]">{ticket.summary ?? 'No summary was produced.'}</p>
          </div>

          <div className="mt-5">
            <div className="flex items-center justify-between gap-3">
              <p className="mono text-muted">SUGGESTED REPLY</p>
              {ticket.suggestedReply && <CopyButton text={ticket.suggestedReply} />}
            </div>
            <p className="mt-2 whitespace-pre-wrap rounded-lg border border-accent/30 bg-surface px-4 py-3 text-[15px] leading-relaxed">
              {ticket.suggestedReply ?? 'No draft was produced — write the reply below.'}
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
  )
}

/** Accept / Edit / Reject + the reply that actually goes out. */
function ReplyComposer({ ticket, userName }: { ticket: Ticket; userName: string | null }) {
  const [choice, setChoice] = useState<DraftAction | null>(null)
  const [reply, setReply] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const resolve = useResolveTicket()

  const text = reply ?? ticket.suggestedReply ?? ''
  const picked: DraftAction = choice ?? (ticket.suggestedReply ? 'ACCEPTED' : 'REJECTED')
  // Untouched text counts as ACCEPTED; anything the agent changed is EDITED.
  const draftAction: DraftAction =
    picked === 'REJECTED'
      ? 'REJECTED'
      : text.trim() !== (ticket.suggestedReply ?? '').trim()
        ? 'EDITED'
        : 'ACCEPTED'

  const actionNote =
    draftAction === 'EDITED'
      ? 'text differs from the AI draft → recorded as EDITED'
      : draftAction === 'REJECTED'
        ? 'AI draft refused → recorded as REJECTED'
        : 'sent as-is → recorded as ACCEPTED'

  async function onResolve() {
    setError(null)
    if (!text.trim()) {
      setError('Write the final reply before resolving.')
      return
    }
    try {
      // resolvedBy comes from the JWT on the server; nothing is chosen here.
      await resolve.mutateAsync({
        id: ticket.id,
        finalReply: text.trim(),
        draftAction,
      })
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not resolve the ticket')
    }
  }

  return (
    <section className="card mt-6 p-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h2 className="text-lg">Resolve ticket</h2>
        <span className="mono text-muted">
          resolving as {userName ?? '-'}
        </span>
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-2">
        <span className="mono text-muted">AI DRAFT</span>
        <button
          type="button"
          className={choiceButton(picked === 'ACCEPTED')}
          onClick={() => setChoice('ACCEPTED')}
        >
          Accept
        </button>
        <button
          type="button"
          className={choiceButton(picked === 'EDITED')}
          onClick={() => {
            setChoice('EDITED')
            textareaRef.current?.focus()
          }}
        >
          Edit
        </button>
        <button
          type="button"
          className={choiceButton(picked === 'REJECTED')}
          onClick={() => {
            setChoice('REJECTED')
            setReply('')
            textareaRef.current?.focus()
          }}
        >
          Reject
        </button>
        <span className="mono text-muted">· {actionNote}</span>
      </div>

      <label htmlFor="finalReply" className="mb-1.5 mt-5 block text-sm font-medium">
        Final reply to {ticket.customerEmail}
      </label>
      <textarea
        id="finalReply"
        ref={textareaRef}
        rows={7}
        className="field resize-y"
        placeholder="Write the reply you are sending…"
        value={text}
        onChange={(event) => setReply(event.target.value)}
      />

      {error && (
        <p className="mt-3 rounded-lg border border-red-500/40 bg-red-500/10 px-3 py-2 text-sm text-red-600 dark:text-red-400">
          {error}
        </p>
      )}

      <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
        <p className="mono text-muted">{text.trim() ? `${text.trim().length} characters` : 'reply is empty'}</p>
        <button
          type="button"
          className="btn-primary"
          disabled={resolve.isPending || !text.trim()}
          onClick={() => void onResolve()}
        >
          {resolve.isPending ? 'Resolving…' : 'Resolve ticket'}
        </button>
      </div>
    </section>
  )
}

/** What was actually sent, once the ticket is RESOLVED. */
function ResolvedPanel({ ticket, employeeName }: { ticket: Ticket; employeeName: string | null }) {
  return (
    <section className="card mt-6 p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-lg">Resolution</h2>
        <div className="flex items-center gap-2">
          {ticket.draftAction && <Pill tone={draftTone(ticket.draftAction)}>{ticket.draftAction}</Pill>}
          <span className="mono text-muted">
            {ticket.resolvedAt ? formatTimestamp(ticket.resolvedAt) : ''}
          </span>
        </div>
      </div>

      <p className="mt-4 whitespace-pre-wrap text-[15px] leading-relaxed">
        {ticket.finalReply ?? 'No reply was recorded.'}
      </p>

      <p className="mono mt-4 text-muted">
        resolved by {employeeName ?? (ticket.resolvedBy ? `#${ticket.resolvedBy}` : 'unknown')}
        {ticket.draftAction === 'REJECTED'
          ? ' · the AI draft was rejected'
          : ticket.draftAction === 'EDITED'
            ? ' · the AI draft was edited'
            : ticket.draftAction === 'ACCEPTED'
              ? ' · the AI draft was accepted as-is'
              : ''}
      </p>
    </section>
  )
}

export default function TicketDetail() {
  const params = useParams()
  const id = Number(params.id)
  const navigate = useNavigate()
  const { data: ticket, isLoading, isError, error, refetch } = useTicket(id)
  const { data: employees } = useEmployees()
  const { data: teams } = useTeams()
  const updateStatus = useUpdateStatus()
  const { user } = useSession()

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
          <Link to="/queue" className="btn-quiet">
            Back to the queue
          </Link>
        </div>
      </div>
    )
  }

  const nameOf = (employeeId: number | null) =>
    employeeId === null ? null : (employees?.find((employee) => employee.id === employeeId)?.name ?? null)
  const teamName = ticket.teamId
    ? (teams?.find((team) => team.id === ticket.teamId)?.name ?? null)
    : null

  return (
    <div>
      <button type="button" className="text-sm text-muted hover:text-ink" onClick={() => navigate(-1)}>
        ← Back
      </button>

      <div className="mt-4 flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className="mono text-muted">#{ticket.id}</span>
            <Pill tone={statusTone(ticket.status)}>{ticket.status.toLowerCase()}</Pill>
            <Pill tone={priorityTone(ticket.priority)}>{ticket.priority ?? 'unprioritised'}</Pill>
            {ticket.category && <Pill tone={categoryTone(ticket.category)}>{ticket.category}</Pill>}
          </div>
          <h1 className="mt-2 text-3xl">{ticket.subject}</h1>
          <p className="mono mt-2 text-muted">
            {ticket.customerEmail} · {formatTimestamp(ticket.createdAt)}
            {teamName ? ` · ${teamName}` : ''}
            {nameOf(ticket.assigneeId) ? ` · @${nameOf(ticket.assigneeId)}` : ''}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {ticket.status === 'ASSIGNED' && (
            <button
              type="button"
              className="btn-quiet"
              disabled={updateStatus.isPending}
              onClick={() => updateStatus.mutate({ id: ticket.id, status: 'IN_PROGRESS' })}
            >
              {updateStatus.isPending ? 'Saving…' : 'Start progress'}
            </button>
          )}
          {ticket.status === 'IN_PROGRESS' && (
            <button
              type="button"
              className="btn-quiet"
              disabled={updateStatus.isPending}
              onClick={() => updateStatus.mutate({ id: ticket.id, status: 'ASSIGNED' })}
            >
              {updateStatus.isPending ? 'Saving…' : 'Back to queue'}
            </button>
          )}
          {ticket.status === 'NEEDS_TRIAGE' && (
            <Link to="/triage" className="btn-quiet">
              Open in triage
            </Link>
          )}
        </div>
      </div>

      <section className="card mt-6 flex flex-wrap items-center justify-between gap-4 px-6 py-4">
        <div className="flex items-center gap-3">
          <span className="mono text-muted">SLA</span>
          <SlaCounter ticket={ticket} />
        </div>
        <span className="mono text-muted">
          opened {formatTimestamp(ticket.createdAt)}
          {ticket.assignedAt ? ` · assigned ${formatTimestamp(ticket.assignedAt)}` : ''}
          {ticket.resolvedAt ? ` · resolved ${formatTimestamp(ticket.resolvedAt)}` : ''}
        </span>
      </section>

      <section className="card mt-6 p-6">
        <h2 className="text-lg">Customer message</h2>
        <p className="mt-3 whitespace-pre-wrap text-[15px] leading-relaxed">{ticket.message}</p>
      </section>

      {/* AI panel — the accent colour is reserved for generated content. */}
      <div className="mt-6">
        <AiPanel ticket={ticket} />
      </div>

      {ticket.status === 'RESOLVED' ? (
        <ResolvedPanel ticket={ticket} employeeName={nameOf(ticket.resolvedBy)} />
      ) : ticket.status === 'OPEN' ? (
        /* the AI is still working; the composer appears with the draft */
        null
      ) : (
        <ReplyComposer
          ticket={ticket}
          userName={user?.name ?? nameOf(ticket.assigneeId)}
        />
      )}
    </div>
  )
}
