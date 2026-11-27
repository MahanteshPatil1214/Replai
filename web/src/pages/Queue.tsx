import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useEmployees, useTeams } from '../api/directory'
import { useTickets } from '../api/tickets'
import SlaCounter from '../components/SlaCounter'
import { Pill, categoryTone, priorityTone, statusTone } from '../components/Pill'
import { formatRelative } from '../lib/format'
import type { Ticket } from '../types'

function Row({ ticket, assignee }: { ticket: Ticket; assignee?: string }) {
  return (
    <Link
      to={`/tickets/${ticket.id}`}
      className="card flex flex-wrap items-start gap-x-5 gap-y-2 px-5 py-4 transition-colors hover:border-hairline-strong"
    >
      <span className="mono mt-1 w-10 shrink-0 text-muted">#{ticket.id}</span>

      <span className="min-w-0 flex-1">
        <span className="flex flex-wrap items-center gap-2">
          <span className="font-medium">{ticket.subject}</span>
          <Pill tone={priorityTone(ticket.priority)}>{ticket.priority ?? 'unprioritised'}</Pill>
          <Pill tone={categoryTone(ticket.category)}>{ticket.category ?? 'unclassified'}</Pill>
          <Pill tone={statusTone(ticket.status)}>{ticket.status.toLowerCase()}</Pill>
        </span>
        <span className="mt-1 block truncate text-sm text-muted">
          {ticket.summary ?? ticket.message}
        </span>
      </span>

      <span className="shrink-0 text-right">
        <span className="mono block text-muted">{ticket.customerEmail}</span>
        <span className="mono mt-1 block text-muted">
          {assignee ? `@${assignee}` : 'unassigned'} · {formatRelative(ticket.createdAt)}
        </span>
        <span className="mt-1 block">
          <SlaCounter ticket={ticket} compact />
        </span>
      </span>
    </Link>
  )
}

/** Team queue: one team's assigned tickets, in the order the API returns them
 *  (priority CRITICAL → LOW, then oldest first). */
export default function Queue() {
  const [team, setTeam] = useState<'all' | number>('all')
  const { data: teams } = useTeams()
  const { data: employees } = useEmployees()
  const { data: rawData, isLoading, isError, error, refetch, isFetching } = useTickets(
    team === 'all' ? {} : { team },
  )
  const data = rawData?.filter(
    (ticket) => ticket.status === 'ASSIGNED' || ticket.status === 'IN_PROGRESS',
  )

  const assigneeName = (id: number | null) =>
    id === null ? undefined : employees?.find((employee) => employee.id === id)?.name

  if (isLoading) {
    return <p className="text-sm text-muted">Loading the queue…</p>
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

  const teamName = team === 'all' ? 'All teams' : teams?.find((t) => t.id === team)?.name

  return (
    <div>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="mono text-muted">03 / Team queue</p>
          <h1 className="mt-2 text-4xl">{teamName ?? 'Queues'}</h1>
          <p className="mt-3 max-w-xl text-[15px] text-muted">
            Assigned tickets, ordered by priority — critical first — then the oldest waiting. SLA
            counters update on their own.
          </p>
        </div>

        <div className="flex items-end gap-4">
          <p className="mono text-muted">
            {data?.length ?? 0} in queue {isFetching ? '· refreshing' : ''}
          </p>
          <label className="flex flex-col gap-1.5">
            <span className="mono text-muted">TEAM</span>
            <select
              aria-label="Filter by team"
              className="field !w-auto !py-2 text-sm"
              value={team === 'all' ? 'all' : String(team)}
              onChange={(event) =>
                setTeam(event.target.value === 'all' ? 'all' : Number(event.target.value))
              }
            >
              <option value="all">All teams</option>
              {teams?.map((entry) => (
                <option key={entry.id} value={entry.id}>
                  {entry.name}
                </option>
              ))}
            </select>
          </label>
        </div>
      </div>

      {!data?.length ? (
        <div className="card mt-8 px-6 py-14 text-center">
          <p className="font-display text-2xl">The queue is clear</p>
          <p className="mt-2 text-sm text-muted">
            No assigned tickets here. Triage is where waiting tickets get picked up.
          </p>
          <div className="mt-6 flex justify-center gap-2">
            <Link to="/triage" className="btn-quiet">
              Triage board
            </Link>
            <Link to="/new" className="btn-primary">
              New ticket
            </Link>
          </div>
        </div>
      ) : (
        <div className="mt-6 flex flex-col gap-3">
          {data.map((ticket) => (
            <Row key={ticket.id} ticket={ticket} assignee={assigneeName(ticket.assigneeId)} />
          ))}
        </div>
      )}
    </div>
  )
}
