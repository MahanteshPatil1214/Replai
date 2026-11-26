import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useCreateTicket } from '../api/tickets'

const EMPTY = { customerEmail: '', subject: '', message: '' }

export default function NewTicket() {
  const [form, setForm] = useState(EMPTY)
  const [error, setError] = useState<string | null>(null)
  const createTicket = useCreateTicket()
  const navigate = useNavigate()

  function update(field: keyof typeof form) {
    return (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
      setForm((current) => ({ ...current, [field]: event.target.value }))
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      const ticket = await createTicket.mutateAsync(form)
      setForm(EMPTY)
      navigate(`/tickets/${ticket.id}`)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not create the ticket')
    }
  }

  return (
    <div className="mx-auto max-w-2xl">
      <p className="mono text-muted">01 / New ticket</p>
      <h1 className="mt-2 text-4xl">How can we help?</h1>
      <p className="mt-3 max-w-xl text-[15px] text-muted">
        Tell us what happened. Replai will classify the ticket, judge its urgency and draft a reply
        grounded in our help articles — you'll see it in a moment.
      </p>

      <form onSubmit={onSubmit} className="card mt-8 space-y-5 p-6">
        <div>
          <label htmlFor="customerEmail" className="mb-1.5 block text-sm font-medium">
            Your email
          </label>
          <input
            id="customerEmail"
            type="email"
            required
            autoComplete="email"
            placeholder="you@example.com"
            className="field"
            value={form.customerEmail}
            onChange={update('customerEmail')}
          />
        </div>

        <div>
          <label htmlFor="subject" className="mb-1.5 block text-sm font-medium">
            Subject
          </label>
          <input
            id="subject"
            required
            maxLength={300}
            placeholder="Payment failed at checkout"
            className="field"
            value={form.subject}
            onChange={update('subject')}
          />
        </div>

        <div>
          <label htmlFor="message" className="mb-1.5 block text-sm font-medium">
            What went wrong?
          </label>
          <textarea
            id="message"
            required
            rows={7}
            placeholder="Describe the problem in your own words…"
            className="field resize-y"
            value={form.message}
            onChange={update('message')}
          />
        </div>

        {error && (
          <p className="rounded-lg border border-red-500/40 bg-red-500/10 px-3 py-2 text-sm text-red-600 dark:text-red-400">
            {error}
          </p>
        )}

        <div className="flex items-center justify-between pt-1">
          <Link to="/inbox" className="text-sm text-muted hover:text-ink">
            View existing tickets
          </Link>
          <button type="submit" className="btn-primary" disabled={createTicket.isPending}>
            {createTicket.isPending ? 'Sending…' : 'Send ticket'}
          </button>
        </div>
      </form>
    </div>
  )
}
