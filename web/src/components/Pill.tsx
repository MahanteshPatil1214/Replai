import type { Category } from '../types'

type Tone = 'neutral' | 'red' | 'amber' | 'green' | 'violet' | 'ink'

const TONES: Record<Tone, string> = {
  neutral: 'border-hairline text-muted',
  ink: 'border-ink/25 text-ink',
  red: 'border-red-500/35 text-red-600 dark:text-red-400',
  amber: 'border-amber-500/40 text-amber-700 dark:text-amber-400',
  green: 'border-emerald-500/35 text-emerald-700 dark:text-emerald-400',
  violet: 'border-accent/40 text-accent',
}

export function Pill({ tone = 'neutral', children }: { tone?: Tone; children: React.ReactNode }) {
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[11px] font-medium tracking-wide uppercase ${TONES[tone]}`}
    >
      {children}
    </span>
  )
}

const PRIORITY_TONE: Record<string, Tone> = {
  CRITICAL: 'red',
  HIGH: 'amber',
  MEDIUM: 'neutral',
  LOW: 'neutral',
}
const SENTIMENT_TONE: Record<string, Tone> = { POSITIVE: 'green', NEGATIVE: 'red', NEUTRAL: 'neutral' }
const STATUS_TONE: Record<string, Tone> = {
  OPEN: 'neutral',
  NEEDS_TRIAGE: 'amber',
  ASSIGNED: 'ink',
  IN_PROGRESS: 'violet',
  RESOLVED: 'green',
}
const DRAFT_TONE: Record<string, Tone> = { ACCEPTED: 'green', EDITED: 'amber', REJECTED: 'red' }

export const priorityTone = (value: string | null | undefined): Tone =>
  PRIORITY_TONE[value ?? ''] ?? 'neutral'

export const sentimentTone = (value: string | null | undefined): Tone =>
  SENTIMENT_TONE[value ?? ''] ?? 'neutral'

export const statusTone = (value: string | null | undefined): Tone =>
  STATUS_TONE[value ?? ''] ?? 'neutral'

export const draftTone = (value: string | null | undefined): Tone =>
  DRAFT_TONE[value ?? ''] ?? 'neutral'

export const categoryTone = (_value: Category | string | null | undefined): Tone => 'ink'
