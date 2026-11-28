/**
 * Login session (project.md section 8): the JWT plus the employee behind it.
 * Kept in localStorage and read through a tiny external store, so every screen
 * can check "who am I" without a provider.
 */

import { useSyncExternalStore } from 'react'
import type { Employee } from '../types'

const TOKEN_KEY = 'replai.token'
const USER_KEY = 'replai.user'

export interface Session {
  token: string | null
  user: Employee | null
}

function read(): Session {
  try {
    const token = window.localStorage.getItem(TOKEN_KEY)
    const rawUser = window.localStorage.getItem(USER_KEY)
    const user = rawUser ? (JSON.parse(rawUser) as Employee) : null
    return { token, user }
  } catch {
    return { token: null, user: null }
  }
}

let current: Session = read()
const listeners = new Set<() => void>()

function emit() {
  for (const listener of listeners) listener()
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

function getSnapshot(): Session {
  return current
}

export function useSession(): Session {
  return useSyncExternalStore(subscribe, getSnapshot, getSnapshot)
}

/** The bearer token for the API client (null when signed out). */
export function authToken(): string | null {
  return current.token
}

export function signIn(token: string, user: Employee) {
  current = { token, user }
  try {
    window.localStorage.setItem(TOKEN_KEY, token)
    window.localStorage.setItem(USER_KEY, JSON.stringify(user))
  } catch {
    /* private mode: keep the in-memory session */
  }
  emit()
}

export function signOut() {
  if (current.token === null && current.user === null) return
  current = { token: null, user: null }
  try {
    window.localStorage.removeItem(TOKEN_KEY)
    window.localStorage.removeItem(USER_KEY)
  } catch {
    /* nothing to clean up */
  }
  emit()
}

/** LEAD and ADMIN may assign from triage, see analytics and edit the knowledge base. */
export function isLeadOrAdmin(user: Employee | null): boolean {
  return user?.role === 'LEAD' || user?.role === 'ADMIN'
}
