import { useEffect, useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { login } from '../api/auth'
import { signIn, useSession } from '../lib/auth'

/** Sign in screen. Public: the customer ticket form (/new) stays open. */
export default function Login() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const { token } = useSession()
  const location = useLocation()
  const navigate = useNavigate()

  const from = (location.state as { from?: string } | null)?.from ?? '/inbox'

  // Already signed in (or just signed in): go where the user wanted to be.
  useEffect(() => {
    if (token) navigate(from, { replace: true })
  }, [token, from, navigate])

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setPending(true)
    try {
      const session = await login(email, password)
      signIn(session.token, session.employee)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not sign in')
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-6 py-16">
      <div className="w-full max-w-md">
        <p className="font-display text-2xl tracking-tight">Replai</p>
        <p className="mono mt-8 text-muted">SIGN IN</p>
        <h1 className="mt-2 text-4xl">Work the queue as yourself</h1>
        <p className="mt-3 text-[15px] text-muted">
          Agents get their own tickets. Leads and admins also get triage, analytics and the
          knowledge base.
        </p>

        <form onSubmit={onSubmit} className="card mt-8 space-y-4 p-6">
          <div>
            <label htmlFor="email" className="mb-1.5 block text-sm font-medium">
              Email
            </label>
            <input
              id="email"
              type="email"
              required
              autoComplete="username"
              className="field"
              placeholder="ravi.sharma@replai.dev"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </div>

          <div>
            <label htmlFor="password" className="mb-1.5 block text-sm font-medium">
              Password
            </label>
            <input
              id="password"
              type="password"
              required
              autoComplete="current-password"
              className="field"
              placeholder="Your password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </div>

          {error && (
            <p className="rounded-lg border border-red-500/40 bg-red-500/10 px-3 py-2 text-sm text-red-600 dark:text-red-400">
              {error}
            </p>
          )}

          <button type="submit" className="btn-primary w-full" disabled={pending}>
            {pending ? 'Signing in.' : 'Sign in'}
          </button>
        </form>

        <div className="card mt-4 p-5">
          <p className="mono text-muted">SEEDED ACCOUNTS</p>
          <ul className="mono mt-2 space-y-1 text-muted">
            <li>neha.verma@replai.dev (admin)</li>
            <li>asha.kumar@replai.dev (lead)</li>
            <li>ravi.sharma@replai.dev (agent)</li>
          </ul>
          <p className="mt-3 text-xs text-muted">
            Password comes from SEED_PASSWORD, default <span className="mono">replai123</span>.
          </p>
        </div>
      </div>
    </div>
  )
}
