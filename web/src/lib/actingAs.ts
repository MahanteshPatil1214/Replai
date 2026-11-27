/**
 * "Acting as" — the MVP stand-in for login (project.md section 8).
 * The chosen employee is kept in localStorage and read by every screen through
 * a tiny external store, so no provider is needed.
 */

import { useSyncExternalStore } from 'react'

const STORAGE_KEY = 'replai.actingAs'

function read(): number | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (raw === null) return null
    const id = Number(raw)
    return Number.isFinite(id) ? id : null
  } catch {
    return null
  }
}

let current: number | null = read()
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

function getSnapshot() {
  return current
}

export function useActingAsId(): number | null {
  return useSyncExternalStore(subscribe, getSnapshot, getSnapshot)
}

export function useSetActingAsId(): (id: number | null) => void {
  return (id: number | null) => {
    current = id
    try {
      if (id === null) window.localStorage.removeItem(STORAGE_KEY)
      else window.localStorage.setItem(STORAGE_KEY, String(id))
    } catch {
      /* private mode: keep the in-memory value */
    }
    emit()
  }
}
