import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Bar, BarChart, Cell, LabelList, ResponsiveContainer, XAxis, YAxis } from 'recharts'
import {
  useAiAnalytics,
  useAnalyticsSummary,
  useEmployeeAnalytics,
  usePriorityAnalytics,
  useTeamAnalytics,
} from '../api/analytics'
import { useTickets } from '../api/tickets'
import { Pill, priorityTone } from '../components/Pill'
import { formatRelative } from '../lib/format'
import { formatHours, isBreached, useNow } from '../lib/sla'
import type { LeaderboardRange, Priority, Ticket } from '../types'

const PRIORITY_COLORS: Record<Priority, string> = {
  CRITICAL: '#ef4444',
  HIGH: '#f59e0b',
  MEDIUM: '#9a9187',
  LOW: '#c9c2b8',
}

function Stat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="card p-5">
      <p className="mono text-muted">{label}</p>
      <p className="mt-2 font-display text-4xl">{value}</p>
      {hint && <p className="mono mt-1 text-muted">{hint}</p>}
    </div>
  )
}

function SectionTitle({ title, note }: { title: string; note?: string }) {
  return (
    <div className="flex flex-wrap items-baseline justify-between gap-3">
      <h2 className="text-xl">{title}</h2>
      {note && <span className="mono text-muted">{note}</span>}
    </div>
  )
}

const tableHead = 'border-b border-hairline px-3 py-2 text-left text-[11px] uppercase tracking-wide text-muted'
const tableCell = 'px-3 py-2.5 text-sm'

const RANGES: { value: LeaderboardRange; label: string }[] = [
  { value: '7d', label: '7 days' },
  { value: '30d', label: '30 days' },
  { value: 'all', label: 'All time' },
]

export default function Analytics() {
  const [range, setRange] = useState<LeaderboardRange>('7d')
  const now = useNow()

  const summary = useAnalyticsSummary()
  const teams = useTeamAnalytics()
  const employees = useEmployeeAnalytics(range)
  const priority = usePriorityAnalytics()
  const ai = useAiAnalytics()
  const allTickets = useTickets()

  if (summary.isLoading) return <p className="text-sm text-muted">Loading the dashboard…</p>

  if (summary.isError) {
    return (
      <div className="card border-red-500/40 p-6">
        <p className="text-sm text-red-600 dark:text-red-400">
          Could not reach ticket-service:{' '}
          {summary.error instanceof Error ? summary.error.message : 'unknown error'}
        </p>
        <button type="button" onClick={() => void summary.refetch()} className="btn-quiet mt-4">
          Retry
        </button>
      </div>
    )
  }

  const tickets = allTickets.data ?? []
  const breached = tickets
    .filter((ticket) => isBreached(ticket, now))
    .sort((a, b) => Date.parse(a.slaDueAt ?? '0') - Date.parse(b.slaDueAt ?? '0'))
    .slice(0, 5)
  const oldestOpen = tickets
    .filter((ticket) => ticket.status !== 'RESOLVED')
    .sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt))
    .slice(0, 5)

  const priorityData = (priority.data ?? []).map((row) => ({
    priority: row.priority as Priority,
    open: row.open,
  }))

  return (
    <div>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="mono text-muted">05 / Analytics</p>
          <h1 className="mt-2 text-4xl">The dashboard</h1>
          <p className="mt-3 max-w-xl text-[15px] text-muted">
            Everything below is counted from the tickets table: how fast we answer, who is carrying
            the load, and how much of the AI's work the team keeps.
          </p>
        </div>
        <Link to="/gaps" className="btn-quiet">
          Knowledge gaps →
        </Link>
      </div>

      {/* Overview */}
      <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="OPEN" value={String(summary.data?.open ?? 0)} hint="waiting right now" />
        <Stat label="RESOLVED" value={String(summary.data?.resolved ?? 0)} hint="all time" />
        <Stat
          label="AVG RESOLUTION"
          value={formatHours(summary.data?.avgResolutionHours ?? null)}
          hint="created → resolved"
        />
        <Stat
          label="SLA MET"
          value={summary.data?.slaMetPercent === null || summary.data?.slaMetPercent === undefined
            ? '—'
            : `${Math.round(summary.data.slaMetPercent)}%`}
          hint="resolved before the deadline"
        />
      </div>

      {/* Open by priority */}
      <section className="card mt-6 p-6">
        <SectionTitle
          title="Open by priority"
          note={priority.isLoading ? 'loading…' : 'CRITICAL → LOW'}
        />
        <div className="mt-5 h-52 w-full">
          {priority.isError ? (
            <p className="text-sm text-red-600 dark:text-red-400">
              Could not load the priority breakdown.
            </p>
          ) : (
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={priorityData} margin={{ top: 8, right: 8, left: 8, bottom: 0 }} barCategoryGap="30%">
                <XAxis
                  dataKey="priority"
                  axisLine={false}
                  tickLine={false}
                  tick={{ fontSize: 11, fill: 'var(--muted)' }}
                />
                <YAxis hide domain={[0, 'dataMax']} allowDecimals={false} />
                <Bar dataKey="open" radius={[4, 4, 0, 0]} isAnimationActive={false}>
                  {priorityData.map((entry) => (
                    <Cell key={entry.priority} fill={PRIORITY_COLORS[entry.priority] ?? '#9a9187'} />
                  ))}
                  <LabelList
                    dataKey="open"
                    position="top"
                    fill="var(--muted)"
                    fontSize={11}
                  />
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
      </section>

      {/* Team performance */}
      <section className="card mt-6 p-6">
        <SectionTitle title="Team performance" note="solved and open per team" />
        <div className="mt-4 overflow-x-auto">
          <table className="w-full min-w-[560px] border-collapse text-left">
            <thead>
              <tr>
                <th className={tableHead}>Team</th>
                <th className={tableHead}>Resolved</th>
                <th className={tableHead}>Open</th>
                <th className={tableHead}>Avg resolution</th>
                <th className={tableHead}>SLA met</th>
              </tr>
            </thead>
            <tbody>
              {teams.data?.map((row) => (
                <tr key={row.teamId} className="border-b border-hairline last:border-0">
                  <td className={`${tableCell} font-medium`}>{row.teamName}</td>
                  <td className={`${tableCell} mono`}>{row.resolved}</td>
                  <td className={`${tableCell} mono`}>{row.open}</td>
                  <td className={`${tableCell} mono`}>{formatHours(row.avgResolutionHours)}</td>
                  <td className={`${tableCell} mono`}>
                    {row.slaMetPercent === null ? '—' : `${Math.round(row.slaMetPercent)}%`}
                  </td>
                </tr>
              ))}
              {!teams.data?.length && (
                <tr>
                  <td className={`${tableCell} text-muted`} colSpan={5}>
                    No team numbers yet.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {/* Employee leaderboard + AI quality */}
      <div className="mt-6 grid gap-6 lg:grid-cols-5">
        <section className="card p-6 lg:col-span-3">
          <SectionTitle title="Employee leaderboard" note="who solved the most" />
          <div className="mt-4 flex gap-2">
            {RANGES.map((entry) => (
              <button
                key={entry.value}
                type="button"
                className={range === entry.value ? 'btn-primary !py-1.5 text-xs' : 'btn-quiet !py-1.5 text-xs'}
                onClick={() => setRange(entry.value)}
              >
                {entry.label}
              </button>
            ))}
          </div>

          <div className="mt-4 overflow-x-auto">
            <table className="w-full min-w-[420px] border-collapse text-left">
              <thead>
                <tr>
                  <th className={tableHead}>#</th>
                  <th className={tableHead}>Employee</th>
                  <th className={tableHead}>Team</th>
                  <th className={tableHead}>Solved</th>
                  <th className={tableHead}>Avg time</th>
                </tr>
              </thead>
              <tbody>
                {employees.isLoading && (
                  <tr>
                    <td className={`${tableCell} text-muted`} colSpan={5}>
                      Loading…
                    </td>
                  </tr>
                )}
                {employees.data?.map((row, index) => (
                  <tr key={row.employeeId} className="border-b border-hairline last:border-0">
                    <td className={`${tableCell} mono text-muted`}>{index + 1}</td>
                    <td className={`${tableCell} font-medium`}>{row.name}</td>
                    <td className={`${tableCell} text-muted`}>{row.team}</td>
                    <td className={`${tableCell} mono`}>{row.resolved}</td>
                    <td className={`${tableCell} mono`}>{formatHours(row.avgResolutionHours)}</td>
                  </tr>
                ))}
                {employees.data?.length === 0 && (
                  <tr>
                    <td className={`${tableCell} text-muted`} colSpan={5}>
                      Nobody has solved a ticket in this range yet.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>

        {/* AI quality — accent reserved for AI content */}
        <section className="rounded-xl border border-accent/45 bg-accent-soft p-6 lg:col-span-2">
          <h2 className="text-xl text-accent">AI draft quality</h2>
          {ai.isLoading ? (
            <p className="mt-4 text-sm text-muted">Loading…</p>
          ) : (
            <>
              <p className="mt-4 font-display text-5xl text-accent">
                {ai.data?.acceptanceRate === null || ai.data?.acceptanceRate === undefined
                  ? '—'
                  : `${Math.round(ai.data.acceptanceRate)}%`}
              </p>
              <p className="mono mt-1 text-muted">ACCEPTANCE RATE</p>

              <div className="mt-5 grid grid-cols-3 gap-3">
                <div className="rounded-lg border border-accent/30 bg-surface px-3 py-3 text-center">
                  <p className="mono text-lg text-emerald-700 dark:text-emerald-400">
                    {ai.data?.accepted ?? 0}
                  </p>
                  <p className="mono text-muted">ACCEPTED</p>
                </div>
                <div className="rounded-lg border border-accent/30 bg-surface px-3 py-3 text-center">
                  <p className="mono text-lg text-amber-700 dark:text-amber-400">
                    {ai.data?.edited ?? 0}
                  </p>
                  <p className="mono text-muted">EDITED</p>
                </div>
                <div className="rounded-lg border border-accent/30 bg-surface px-3 py-3 text-center">
                  <p className="mono text-lg text-red-600 dark:text-red-400">
                    {ai.data?.rejected ?? 0}
                  </p>
                  <p className="mono text-muted">REJECTED</p>
                </div>
              </div>
              <p className="mt-4 text-sm text-muted">
                Drafts kept as-is prove the retrieval; edits and rejections tell you which articles
                to write next.
              </p>
            </>
          )}
        </section>
      </div>

      {/* SLA: breached and oldest open */}
      <section className="mt-6 grid gap-6 lg:grid-cols-2">
        <div className="card p-6">
          <SectionTitle title="SLA breached" note={`${breached.length} overdue`} />
          {breached.length === 0 ? (
            <p className="mt-3 text-sm text-muted">Nothing is past its deadline.</p>
          ) : (
            <ul className="mt-3 divide-y divide-hairline">
              {breached.map((ticket) => (
                <TicketLine key={ticket.id} ticket={ticket} />
              ))}
            </ul>
          )}
        </div>

        <div className="card p-6">
          <SectionTitle title="Oldest open tickets" note={`${oldestOpen.length} waiting longest`} />
          {oldestOpen.length === 0 ? (
            <p className="mt-3 text-sm text-muted">Every ticket is resolved.</p>
          ) : (
            <ul className="mt-3 divide-y divide-hairline">
              {oldestOpen.map((ticket) => (
                <TicketLine key={ticket.id} ticket={ticket} />
              ))}
            </ul>
          )}
        </div>
      </section>
    </div>
  )
}

function TicketLine({ ticket }: { ticket: Ticket }) {
  return (
    <li>
      <Link
        to={`/tickets/${ticket.id}`}
        className="flex flex-wrap items-center gap-2 py-2.5 hover:underline"
      >
        <span className="mono text-muted">#{ticket.id}</span>
        <Pill tone={priorityTone(ticket.priority)}>{ticket.priority ?? '—'}</Pill>
        <span className="min-w-0 flex-1 truncate text-sm">{ticket.subject}</span>
        <span className="mono text-muted">{formatRelative(ticket.createdAt)}</span>
      </Link>
    </li>
  )
}
