/** SLA counters (project.md section 6): "Day 2 of 3", time left, breached, time taken. */

import { useEffect, useState } from 'react'
import type { Ticket } from '../types'

const DAY_MS = 86_400_000

/** A ticking clock so countdowns move without re-rendering the whole tree every second. */
export function useNow(intervalMs = 30_000): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), intervalMs)
    return () => window.clearInterval(timer)
  }, [intervalMs])
  return now
}

function parse(value: string | null): number | null {
  if (!value) return null
  const time = Date.parse(value)
  return Number.isNaN(time) ? null : time
}

/** The resolve-within window: derived from slaDueAt vs createdAt (no policy endpoint). */
export function slaWindowMs(ticket: Ticket): number | null {
  const created = parse(ticket.createdAt)
  const due = parse(ticket.slaDueAt)
  if (created === null || due === null) return null
  return Math.max(due - created, 0)
}

/** Red badge rule: overdue and not resolved (the scheduler flag or our own clock). */
export function isBreached(ticket: Ticket, now: number): boolean {
  if (ticket.status === 'RESOLVED') return false
  if (ticket.slaBreached === true) return true
  const due = parse(ticket.slaDueAt)
  return due !== null && now > due
}

/** "Day 2 of 3" — days elapsed against the resolve-within window. */
export function dayCounter(ticket: Ticket, now: number): { day: number; total: number } | null {
  const created = parse(ticket.createdAt)
  const window = slaWindowMs(ticket)
  if (created === null || window === null || window <= 0) return null
  const total = Math.max(1, Math.round(window / DAY_MS))
  const elapsed = Math.max(0, now - created)
  const day = Math.min(total, Math.floor(elapsed / DAY_MS) + 1)
  return { day, total }
}

export function timeRemaining(ticket: Ticket, now: number): string | null {
  const due = parse(ticket.slaDueAt)
  if (due === null) return null
  return formatDuration(due - now)
}

/** Resolved tickets: time taken from createdAt to resolvedAt (same as the analytics). */
export function timeTaken(ticket: Ticket): string | null {
  const created = parse(ticket.createdAt)
  const resolved = parse(ticket.resolvedAt)
  if (created === null || resolved === null) return null
  return formatDuration(resolved - created)
}

/** Human duration: "1 day 4 hours", "5 hours 20 min", "45 min". */
export function formatDuration(ms: number): string {
  const minutesTotal = Math.max(0, Math.round(ms / 60_000))
  const days = Math.floor(minutesTotal / 1440)
  const hours = Math.floor((minutesTotal % 1440) / 60)
  const minutes = minutesTotal % 60

  const parts: string[] = []
  if (days > 0) parts.push(`${days} day${days === 1 ? '' : 's'}`)
  if (hours > 0) parts.push(`${hours} hour${hours === 1 ? '' : 's'}`)
  if (days === 0 && hours === 0 && minutes > 0) parts.push(`${minutes} min`)
  if (parts.length === 0) return 'less than a minute'
  return parts.join(' ')
}

export function formatHours(value: number | null): string {
  if (value === null) return '—'
  return value >= 10 ? `${Math.round(value)} h` : `${value.toFixed(1)} h`
}
