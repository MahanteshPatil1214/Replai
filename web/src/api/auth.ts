/** POST /api/auth/login and GET /api/auth/me. */

import { api } from './client'
import type { Employee } from '../types'

export interface LoginResponse {
  token: string
  employee: Employee
}

export function login(email: string, password: string): Promise<LoginResponse> {
  return api.post<LoginResponse>('/api/auth/login', { email, password })
}

export function fetchMe(): Promise<Employee> {
  return api.get<Employee>('/api/auth/me')
}
