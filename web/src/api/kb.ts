import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import type { Article, SearchHit } from '../types'

export const kbKeys = {
  all: ['kb'] as const,
  articles: () => [...kbKeys.all, 'articles'] as const,
  search: (q: string) => [...kbKeys.all, 'search', q] as const,
}

export function fetchArticles(): Promise<Article[]> {
  return api.get<Article[]>('/kb/articles')
}

export function searchChunks(query: string, limit = 3): Promise<{ query: string; results: SearchHit[] }> {
  const params = new URLSearchParams({ q: query, limit: String(limit) })
  return api.get<{ query: string; results: SearchHit[] }>(`/kb/search?${params.toString()}`)
}

export interface CreateArticleInput {
  title: string
  content: string
}

export function useCreateArticle() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateArticleInput) => api.post<Article>('/kb/articles', input),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: kbKeys.all }),
  })
}

export function useDeleteArticle() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api.delete<void>(`/kb/articles/${id}`),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: kbKeys.all }),
  })
}

export function useArticles() {
  return useQuery({
    queryKey: kbKeys.articles(),
    queryFn: fetchArticles,
  })
}

export function useSearch(query: string) {
  return useQuery({
    queryKey: kbKeys.search(query),
    queryFn: () => searchChunks(query),
    enabled: query.trim().length > 0,
  })
}
