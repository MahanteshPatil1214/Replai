import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import type { Ticket, TicketStatus } from '../types'

export const ticketKeys = {
  all: ['tickets'] as const,
  list: () => [...ticketKeys.all, 'list'] as const,
  detail: (id: number) => [...ticketKeys.all, 'detail', id] as const,
}

export function fetchTickets(): Promise<Ticket[]> {
  return api.get<Ticket[]>('/api/tickets')
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
    // Poll while the AI is still working so the panel appears by itself.
    refetchInterval: (query) => (query.state.data?.status === 'OPEN' ? 2000 : false),
  })
}

export function useTickets() {
  return useQuery({
    queryKey: ticketKeys.list(),
    queryFn: fetchTickets,
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
