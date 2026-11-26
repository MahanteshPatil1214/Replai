import { useState, type FormEvent } from 'react'
import { useCreateArticle, useDeleteArticle, useSearch, useArticles } from '../api/kb'
import { formatTimestamp } from '../lib/format'

function ProgressBar({ score }: { score: number }) {
  const percent = Math.max(0, Math.min(100, Math.round(score * 100)))
  return (
    <div className="flex items-center gap-3">
      <div className="h-1.5 w-28 overflow-hidden rounded-full bg-hairline">
        <div className="h-full rounded-full bg-accent" style={{ width: `${percent}%` }} />
      </div>
      <span className="mono text-muted">{percent}%</span>
    </div>
  )
}

export default function KnowledgeBase() {
  const [query, setQuery] = useState('')
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const { data: articles, isLoading } = useArticles()
  const { data: search, isFetching: searching } = useSearch(query)
  const createArticle = useCreateArticle()
  const deleteArticle = useDeleteArticle()

  async function onAdd(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      await createArticle.mutateAsync({ title, content })
      setTitle('')
      setContent('')
      setShowForm(false)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not save the article')
    }
  }

  return (
    <div>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="mono text-muted">03 / Knowledge base</p>
          <h1 className="mt-2 text-4xl">What the AI reads</h1>
          <p className="mt-3 max-w-xl text-[15px] text-muted">
            Every article is split into chunks and embedded. Ask a question below to see exactly
            which passages would be retrieved for a ticket.
          </p>
        </div>
        <button type="button" className="btn-primary" onClick={() => setShowForm((v) => !v)}>
          {showForm ? 'Close' : 'Add article'}
        </button>
      </div>

      <section className="mt-8">
        <label htmlFor="search" className="mb-1.5 block text-sm font-medium">
          Test retrieval
        </label>
        <input
          id="search"
          className="field max-w-xl"
          placeholder="How long do refunds take?"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />

        {query.trim() && (
          <div className="card mt-4 divide-y divide-hairline">
            {searching && <p className="px-5 py-4 text-sm text-muted">Searching…</p>}
            {search && search.results.length === 0 && !searching && (
              <p className="px-5 py-4 text-sm text-muted">
                Nothing matched — the AI would say a human agent will follow up.
              </p>
            )}
            {search?.results.map((hit) => (
              <div key={`${hit.article_id}-${hit.chunk_text.slice(0, 24)}`} className="px-5 py-4">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <p className="text-sm font-medium">
                    <span className="mono mr-2 text-muted">{hit.article_id}</span>
                    {hit.title}
                  </p>
                  <ProgressBar score={hit.score} />
                </div>
                <p className="mt-2 text-sm text-muted">{hit.chunk_text}</p>
              </div>
            ))}
          </div>
        )}
      </section>

      {showForm && (
        <form onSubmit={onAdd} className="card mt-8 space-y-4 p-6">
          <div>
            <label htmlFor="kbTitle" className="mb-1.5 block text-sm font-medium">
              Title
            </label>
            <input
              id="kbTitle"
              required
              maxLength={300}
              className="field"
              placeholder="Refund policy"
              value={title}
              onChange={(event) => setTitle(event.target.value)}
            />
          </div>
          <div>
            <label htmlFor="kbContent" className="mb-1.5 block text-sm font-medium">
              Content
            </label>
            <textarea
              id="kbContent"
              required
              rows={7}
              className="field resize-y"
              placeholder="Write the article the agent would read…"
              value={content}
              onChange={(event) => setContent(event.target.value)}
            />
            <p className="mt-1.5 text-xs text-muted">
              It will be chunked (~400 characters with a small overlap) and embedded automatically.
            </p>
          </div>

          {error && (
            <p className="rounded-lg border border-red-500/40 bg-red-500/10 px-3 py-2 text-sm text-red-600 dark:text-red-400">
              {error}
            </p>
          )}

          <div className="flex justify-end gap-2">
            <button type="button" className="btn-quiet" onClick={() => setShowForm(false)}>
              Cancel
            </button>
            <button type="submit" className="btn-primary" disabled={createArticle.isPending}>
              {createArticle.isPending ? 'Embedding…' : 'Save article'}
            </button>
          </div>
        </form>
      )}

      <section className="mt-8">
        <div className="flex items-baseline justify-between">
          <h2 className="text-xl">Articles</h2>
          <span className="mono text-muted">{isLoading ? '…' : `${articles?.length ?? 0}`}</span>
        </div>

        {!articles?.length && !isLoading ? (
          <div className="card mt-4 px-6 py-10 text-center text-sm text-muted">
            The knowledge base is empty. Add an article to give the AI something to quote.
          </div>
        ) : (
          <ul className="mt-4 divide-y divide-hairline">
            {articles?.map((article) => (
              <li key={article.id} className="flex items-start gap-4 py-4">
                <span className="mono w-8 shrink-0 pt-1 text-muted">{article.id}</span>
                <div className="min-w-0 flex-1">
                  <p className="font-medium">{article.title}</p>
                  <p className="mt-1 text-sm text-muted">{article.content}</p>
                  <p className="mono mt-2 text-muted">
                    {article.chunkCount} chunks · {formatTimestamp(article.createdAt)}
                  </p>
                </div>
                <button
                  type="button"
                  className="btn-quiet !px-2.5 !py-1.5 text-xs"
                  disabled={deleteArticle.isPending}
                  onClick={() => {
                    if (window.confirm(`Delete "${article.title}" and its chunks?`)) {
                      deleteArticle.mutate(article.id)
                    }
                  }}
                >
                  Delete
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
