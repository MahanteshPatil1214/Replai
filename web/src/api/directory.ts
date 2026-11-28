/** GET /api/teams and GET /api/employees - the directory used for routing controls. */

import { useQuery } from '@tanstack/react-query'
import { api } from './client'
import type { Employee, Team } from '../types'

export const directoryKeys = {
  all: ['directory'] as const,
  teams: () => [...directoryKeys.all, 'teams'] as const,
  employees: (team?: number) => [...directoryKeys.all, 'employees', team ?? 'all'] as const,
}

export function fetchTeams(): Promise<Team[]> {
  return api.get<Team[]>('/api/teams')
}

export function fetchEmployees(team?: number): Promise<Employee[]> {
  const query = team !== undefined ? `?team=${team}` : ''
  return api.get<Employee[]>(`/api/employees${query}`)
}

export function useTeams() {
  return useQuery({
    queryKey: directoryKeys.teams(),
    queryFn: fetchTeams,
    staleTime: 60_000,
  })
}

export function useEmployees(team?: number, enabled = true) {
  return useQuery({
    queryKey: directoryKeys.employees(team),
    queryFn: () => fetchEmployees(team),
    staleTime: 60_000,
    enabled,
  })
}
