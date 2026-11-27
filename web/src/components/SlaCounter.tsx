import { Pill } from './Pill'
import { dayCounter, formatDuration, isBreached, timeRemaining, timeTaken, useNow } from '../lib/sla'
import type { Ticket } from '../types'

/**
 * SLA counters (project.md section 6):
 * open   → "Day 2 of 3 · 1 day 4 hours left"
 * late   → red "SLA breached" badge
 * solved → "resolved in 1 day 4 hours"
 */
export default function SlaCounter({ ticket, compact = false }: { ticket: Ticket; compact?: boolean }) {
  const now = useNow()

  if (ticket.status === 'RESOLVED') {
    const taken = timeTaken(ticket)
    if (taken === null) return <span className="mono text-muted">—</span>
    return <span className={`mono ${compact ? 'text-muted' : 'text-ink'}`}>resolved in {taken}</span>
  }

  if (isBreached(ticket, now)) {
    const due = ticket.slaDueAt ? Date.parse(ticket.slaDueAt) : NaN
    const overdue = Number.isNaN(due) ? null : formatDuration(now - due)
    return (
      <span className="inline-flex flex-wrap items-center gap-2">
        <Pill tone="red">SLA breached</Pill>
        {!compact && overdue && (
          <span className="mono text-red-600 dark:text-red-400">{overdue} over the deadline</span>
        )}
      </span>
    )
  }

  const counter = dayCounter(ticket, now)
  const remaining = timeRemaining(ticket, now)

  if (!counter && remaining === null) {
    return <span className="mono text-muted">no SLA</span>
  }

  const text = [
    counter ? `Day ${counter.day} of ${counter.total}` : null,
    remaining !== null ? `${remaining} left` : null,
  ]
    .filter(Boolean)
    .join(' · ')

  return <span className={`mono ${compact ? 'text-muted' : 'text-ink'}`}>{text}</span>
}
