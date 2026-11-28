import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import type { DraftAction, Priority, Ticket, TicketStatus } from '../types'

export const ticketKeys = {
  all: ['tickets'] as const,
  list: (status?: TicketStatus | 'any', team?: number | 'all') =>
    [...ticketKeys.all, 'list', status ?? 'any', team ?? 'all'] as const,
  detail: (id: number) => [...ticketKeys.all, 'detail', id] as const,
}

export interface TicketFilters {
  status?: TicketStatus
  team?: number
  priority?: Priority
  assignee?: number
}

function listPath(filters: TicketFilters = {}): string {
  const params = new URLSearchParams()
  if (filters.status) params.set('status', filters.status)
  if (filters.team !== undefined) params.set('team', String(filters.team))
  if (filters.priority) params.set('priority', filters.priority)
  if (filters.assignee !== undefined) params.set('assignee', String(filters.assignee))
  const query = params.toString()
  return `/api/tickets${query ? `?${query}` : ''}`
}

export function fetchTickets(filters: TicketFilters = {}): Promise<Ticket[]> {
  return api.get<Ticket[]>(listPath(filters))
}

export function fetchTicket(id: number): Promise<Ticket> {
  return api.get<Ticket>(`/api/tickets/${id}`)
}

export interface CreateTicketInput {
  customerEmail: string
  subject: string
  message: string
}

export function useCreateTicket() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateTicketInput) => api.post<Ticket>('/api/tickets', input),
    onSuccess: (ticket) => {
      void queryClient.invalidateQueries({ queryKey: ticketKeys.all })
      void queryClient.setQueryData(ticketKeys.detail(ticket.id), ticket)
    },
  })
}

export function useTicket(id: number) {
  return useQuery({
    queryKey: ticketKeys.detail(id),
    queryFn: () => fetchTicket(id),
    enabled: Number.isFinite(id),
    // Poll while the AI is still working so the panel appears by itself.
    refetchInterval: (query) => (query.state.data?.status === 'OPEN' ? 2000 : false),
  })
}

export function useTickets(filters: TicketFilters = {}) {
  const status = filters.status
  const team = filters.team
  return useQuery({
    queryKey: ticketKeys.list(status, team),
    queryFn: () => fetchTickets(filters),
    refetchInterval: (query) =>
      query.state.data?.some((ticket) => ticket.status === 'OPEN') ? 3000 : false,
  })
}

export function useUpdateStatus() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, status }: { id: number; status: TicketStatus }) =>
      api.patch<Ticket>(`/api/tickets/${id}/status`, { status }),
    onSuccess: (ticket) => {
      void queryClient.invalidateQueries({ queryKey: ticketKeys.all })
      void queryClient.setQueryData(ticketKeys.detail(ticket.id), ticket)
    },
  })
}

/** PATCH /api/tickets/{id}/assign — used by the triage board. */
export function useAssignTicket() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, teamId, assigneeId }: { id: number; teamId: number; assigneeId: number }) =>
      api.patch<Ticket>(`/api/tickets/${id}/assign`, { teamId, assigneeId }),
    onSuccess: (ticket) => {
      void queryClient.invalidateQueries({ queryKey: ticketKeys.all })
      void queryClient.setQueryData(ticketKeys.detail(ticket.id), ticket)
    },
  })
}

export interface ResolveTicketInput {
  id: number
  finalReply: string
  draftAction: DraftAction
}

/** POST /api/tickets/{id}/resolve — final reply plus what happened to the AI draft. */
export function useResolveTicket() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...body }: ResolveTicketInput) =>
      api.post<Ticket>(`/api/tickets/${id}/resolve`, body),
    onSuccess: (ticket) => {
      void queryClient.invalidateQueries({ queryKey: ticketKeys.all })
      void queryClient.setQueryData(ticketKeys.detail(ticket.id), ticket)
    },
  })
}
