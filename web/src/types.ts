/** Shared types. These mirror contracts/ and the two service APIs. */

export type TicketStatus = 'OPEN' | 'NEEDS_TRIAGE' | 'ASSIGNED' | 'IN_PROGRESS' | 'RESOLVED'
export type Priority = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
export type Sentiment = 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE'
export type Category = 'Software' | 'Infrastructure' | 'Billing' | 'Account' | 'General'
export type DraftAction = 'ACCEPTED' | 'EDITED' | 'REJECTED'
export type EmployeeRole = 'AGENT' | 'LEAD'

export interface SourceRef {
  articleId: number
  title: string
}

export interface Ticket {
  id: number
  customerEmail: string
  subject: string
  message: string
  status: TicketStatus
  category: string | null
  priority: Priority | null
  sentiment: Sentiment | null
  summary: string | null
  suggestedReply: string | null
  confidence: number | null
  needsHuman: boolean | null
  knowledgeGap: boolean | null
  teamId: number | null
  assigneeId: number | null
  sources: SourceRef[] | null
  finalReply: string | null
  draftAction: DraftAction | null
  slaDueAt: string | null
  slaBreached: boolean | null
  createdAt: string
  assignedAt: string | null
  resolvedAt: string | null
  resolvedBy: number | null
}

export interface Team {
  id: number
  name: string
}

export interface Employee {
  id: number
  name: string
  email: string
  teamId: number
  role: EmployeeRole
}

/* ---- Analytics (GET /api/analytics/*) ---- */

export interface AnalyticsSummary {
  open: number
  resolved: number
  avgResolutionHours: number | null
  slaMetPercent: number | null
}

export interface TeamAnalytics {
  teamId: number
  teamName: string
  resolved: number
  open: number
  avgResolutionHours: number | null
  slaMetPercent: number | null
}

export interface EmployeeAnalytics {
  employeeId: number
  name: string
  team: string
  resolved: number
  avgResolutionHours: number | null
}

export interface PriorityAnalytics {
  priority: Priority
  open: number
}

export interface AiAnalytics {
  accepted: number
  edited: number
  rejected: number
  acceptanceRate: number | null
}

export type LeaderboardRange = '7d' | '30d' | 'all'

/* ---- Knowledge base (ai-service) ---- */

export interface Article {
  id: number
  title: string
  content: string
  createdAt: string
  chunkCount: number
}

export interface SearchHit {
  article_id: number
  title: string
  chunk_text: string
  score: number
}
