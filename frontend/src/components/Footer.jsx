import { Link } from 'react-router-dom'

const LINKS = [
  { label: 'About',       href: '/about' },
  { label: 'How it works', href: '/how-it-works' },
  { label: 'Safety',      href: '/safety' },
  { label: 'Contact',     href: '/contact' },
  { label: 'Terms',       href: '/terms' },
]

export default function Footer() {
  return (
    <footer className="bg-gray-900 text-gray-400 mt-auto">
      <div className="max-w-6xl mx-auto px-4 py-8 flex flex-col md:flex-row items-center justify-between gap-4">
        <nav className="flex flex-wrap justify-center gap-6">
          {LINKS.map((link) => (
            <Link
              key={link.href}
              to={link.href}
              className="text-sm hover:text-white transition-colors"
            >
              {link.label}
            </Link>
          ))}
        </nav>

        <div className="flex items-center gap-2">
          <div className="w-6 h-6 rounded-full bg-blue-500 flex items-center justify-center">
            <svg viewBox="0 0 24 24" fill="white" className="w-3.5 h-3.5">
              <path d="M18.92 6.01C18.72 5.42 18.16 5 17.5 5h-11c-.66 0-1.21.42-1.42 1.01L3 12v8c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1h12v1c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-8l-2.08-5.99z"/>
            </svg>
          </div>
          <span className="text-sm font-semibold text-white">CoVoSIO</span>
        </div>
      </div>

      <div className="border-t border-gray-800 py-3 text-center text-xs text-gray-600">
        © 2026 CoVoSIO. All rights reserved.
      </div>
    </footer>
  )
}
