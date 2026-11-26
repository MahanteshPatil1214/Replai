import { useEffect, useState } from 'react'

function isDark() {
  return document.documentElement.classList.contains('dark')
}

export default function ThemeToggle() {
  const [dark, setDark] = useState(isDark)

  useEffect(() => {
    document.documentElement.classList.toggle('dark', dark)
    try {
      localStorage.setItem('replai-theme', dark ? 'dark' : 'light')
    } catch {
      /* storage may be unavailable */
    }
  }, [dark])

  return (
    <button
      type="button"
      onClick={() => setDark((value) => !value)}
      className="btn-quiet !px-2.5 !py-1.5 text-xs"
      aria-label={dark ? 'Switch to light mode' : 'Switch to dark mode'}
      title={dark ? 'Light mode' : 'Dark mode'}
    >
      {dark ? 'Light' : 'Dark'}
    </button>
  )
}
