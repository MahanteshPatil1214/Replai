/** Shared types. These mirror contracts/ and the two service APIs. */

export type TicketStatus = 'OPEN' | 'ENRICHED' | 'RESOLVED'
export type Priority = 'LOW' | 'MEDIUM' | 'HIGH'
export type Sentiment = 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE'
export type Category = 'Billing' | 'Technical' | 'Account' | 'General'

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
  sources: SourceRef[] | null
  createdAt: string
}

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
