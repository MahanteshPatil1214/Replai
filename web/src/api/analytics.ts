/** GET /api/analytics/* — every number on the dashboard (project.md section 10). */

import { useQuery } from '@tanstack/react-query'
import { api } from './client'
import type {
  AiAnalytics,
  AnalyticsSummary,
  EmployeeAnalytics,
  LeaderboardRange,
  PriorityAnalytics,
  TeamAnalytics,
  Ticket,
} from '../types'

export const analyticsKeys = {
  all: ['analytics'] as const,
  summary: () => [...analyticsKeys.all, 'summary'] as const,
  teams: () => [...analyticsKeys.all, 'teams'] as const,
  employees: (range: LeaderboardRange) => [...analyticsKeys.all, 'employees', range] as const,
  priority: () => [...analyticsKeys.all, 'priority'] as const,
  ai: () => [...analyticsKeys.all, 'ai'] as const,
  knowledgeGaps: () => [...analyticsKeys.all, 'knowledge-gaps'] as const,
}

export function useAnalyticsSummary() {
  return useQuery({
    queryKey: analyticsKeys.summary(),
    queryFn: () => api.get<AnalyticsSummary>('/api/analytics/summary'),
    staleTime: 10_000,
  })
}

export function useTeamAnalytics() {
  return useQuery({
    queryKey: analyticsKeys.teams(),
    queryFn: () => api.get<TeamAnalytics[]>('/api/analytics/teams'),
    staleTime: 10_000,
  })
}

export function useEmployeeAnalytics(range: LeaderboardRange) {
  return useQuery({
    queryKey: analyticsKeys.employees(range),
    queryFn: () => api.get<EmployeeAnalytics[]>(`/api/analytics/employees?range=${range}`),
    staleTime: 10_000,
  })
}

export function usePriorityAnalytics() {
  return useQuery({
    queryKey: analyticsKeys.priority(),
    queryFn: () => api.get<PriorityAnalytics[]>('/api/analytics/priority'),
    staleTime: 10_000,
  })
}

export function useAiAnalytics() {
  return useQuery({
    queryKey: analyticsKeys.ai(),
    queryFn: () => api.get<AiAnalytics>('/api/analytics/ai'),
    staleTime: 10_000,
  })
}

/** Tickets the knowledge base could not answer (knowledgeGap = true). */
export function useKnowledgeGaps() {
  return useQuery({
    queryKey: analyticsKeys.knowledgeGaps(),
    queryFn: () => api.get<Ticket[]>('/api/analytics/knowledge-gaps'),
    staleTime: 10_000,
  })
}
