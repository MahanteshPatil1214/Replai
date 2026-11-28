import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useEmployees, useTeams } from '../api/directory'
import { useAssignTicket, useTickets } from '../api/tickets'
import { Pill, categoryTone, priorityTone, sentimentTone } from '../components/Pill'
import { isLeadOrAdmin, useSession } from '../lib/auth'
import { formatRelative } from '../lib/format'
import type { Ticket } from '../types'

/** Why this ticket landed in the triage queue (project.md section 5). */
function triageReasons(ticket: Ticket): string[] {
  const reasons: string[] = []
  if (ticket.needsHuman) reasons.push('needs a human')
  if (ticket.knowledgeGap) reasons.push('knowledge gap')
  if (!ticket.category) reasons.push('no category')
  if (reasons.length === 0) reasons.push('AI did not answer in time')
  return reasons
}

function AssignControl({ ticket }: { ticket: Ticket }) {
  const [teamId, setTeamId] = useState('')
  const [assigneeId, setAssigneeId] = useState('')
  const { user } = useSession()
  const { data: teams } = useTeams()
  const { data: employees, isLoading: loadingEmployees } = useEmployees(
    teamId ? Number(teamId) : undefined,
    teamId !== '',
  )
  const assign = useAssignTicket()

  // Once a team is picked, prefer the signed-in employee when they belong to it.
  useEffect(() => {
    if (!employees?.length) return
    if (assigneeId && employees.some((employee) => employee.id === Number(assigneeId))) return
    const preferred = employees.find((employee) => employee.id === user?.id)
    setAssigneeId(String((preferred ?? employees[0]).id))
  }, [employees, assigneeId, user])

  const teamName = teams?.find((team) => team.id === Number(teamId))?.name
  const employeeName = employees?.find((employee) => employee.id === Number(assigneeId))?.name

  return (
    <div className="flex flex-wrap items-end gap-3">
      <label className="flex flex-col gap-1.5">
        <span className="mono text-muted">TEAM</span>
        <select
          aria-label={`Team for ticket ${ticket.id}`}
          className="field !w-auto !py-2 text-sm"
          value={teamId}
          onChange={(event) => {
            setTeamId(event.target.value)
            setAssigneeId('')
          }}
        >
          <option value="">Choose a team…</option>
          {teams?.map((team) => (
            <option key={team.id} value={team.id}>
              {team.name}
            </option>
          ))}
        </select>
      </label>

      <label className="flex flex-col gap-1.5">
        <span className="mono text-muted">EMPLOYEE</span>
        <select
          aria-label={`Assignee for ticket ${ticket.id}`}
          className="field !w-auto !py-2 text-sm"
          disabled={!teamId || loadingEmployees}
          value={assigneeId}
          onChange={(event) => setAssigneeId(event.target.value)}
        >
          <option value="">
            {teamId ? (loadingEmployees ? 'Loading…' : 'Choose an employee…') : 'Pick a team first'}
          </option>
          {employees?.map((employee) => (
            <option key={employee.id} value={employee.id}>
              {employee.name}
              {employee.role !== 'AGENT' ? ` (${employee.role.toLowerCase()})` : ''}
            </option>
          ))}
        </select>
      </label>

      <button
        type="button"
        className="btn-primary"
        disabled={!teamId || !assigneeId || assign.isPending}
        onClick={() =>
          assign.mutate(
            { id: ticket.id, teamId: Number(teamId), assigneeId: Number(assigneeId) },
            { onError: () => undefined },
          )
        }
      >
        {assign.isPending ? 'Assigning…' : 'Assign'}
      </button>

      <p className="mono text-muted">
        {assign.isError && teamName
          ? `could not assign: ${assign.error instanceof Error ? assign.error.message : 'unknown error'}`
          : assign.isSuccess && employeeName
            ? `→ ${teamName} · ${employeeName}`
            : ''}
      </p>
    </div>
  )
}

/** Triage board: NEEDS_TRIAGE tickets with a team-then-employee assign control. */
export default function Triage() {
  const { user } = useSession()
  const canAssign = isLeadOrAdmin(user)
  const { data, isLoading, isError, error, refetch, isFetching } = useTickets({
    status: 'NEEDS_TRIAGE',
  })

  if (isLoading) return <p className="text-sm text-muted">Loading the triage board…</p>

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
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="mono text-muted">04 / Triage board</p>
          <h1 className="mt-2 text-4xl">Waiting for a human</h1>
          <p className="mt-3 max-w-xl text-[15px] text-muted">
            Tickets the AI would not answer on its own. Pick the team, then the employee who picks
            it up — the queue keeps priority order.
          </p>
        </div>
        <p className="mono text-muted">
          {data?.length ?? 0} to triage {isFetching ? '· refreshing' : ''}
        </p>
      </div>

      {!data?.length ? (
        <div className="card mt-8 px-6 py-14 text-center">
          <p className="font-display text-2xl">Nothing to triage</p>
          <p className="mt-2 text-sm text-muted">Every ticket has a team and an owner.</p>
        </div>
      ) : (
        <div className="mt-6 flex flex-col gap-4">
          {data.map((ticket) => (
            <article key={ticket.id} className="card p-5">
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="mono text-muted">#{ticket.id}</span>
                    <Pill tone={priorityTone(ticket.priority)}>{ticket.priority ?? 'unprioritised'}</Pill>
                    <Pill tone={categoryTone(ticket.category)}>{ticket.category ?? 'unclassified'}</Pill>
                    <Pill tone={sentimentTone(ticket.sentiment)}>{ticket.sentiment ?? 'unknown sentiment'}</Pill>
                  </div>
                  <Link to={`/tickets/${ticket.id}`} className="mt-2 block hover:underline">
                    <h2 className="text-xl">{ticket.subject}</h2>
                  </Link>
                  <p className="mt-2 line-clamp-2 text-sm text-muted">
                    {ticket.summary ?? ticket.message}
                  </p>
                  <div className="mt-3 flex flex-wrap items-center gap-2">
                    <span className="mono text-muted">WHY TRIAGE:</span>
                    {triageReasons(ticket).map((reason) => (
                      <Pill key={reason} tone="amber">
                        {reason}
                      </Pill>
                    ))}
                    {typeof ticket.confidence === 'number' && (
                      <span className="mono text-muted">
                        confidence {Math.round(ticket.confidence * 100)}%
                      </span>
                    )}
                    <span className="mono text-muted">· {formatRelative(ticket.createdAt)}</span>
                  </div>
                </div>
              </div>

              <div className="mt-4 border-t border-hairline pt-4">
                {canAssign ? (
                  <AssignControl ticket={ticket} />
                ) : (
                  <p className="mono text-muted">
                    Only leads and admins pick the team - your job is the tickets assigned to you.
                  </p>
                )}
              </div>
            </article>
          ))}
        </div>
      )}
    </div>
  )
}
