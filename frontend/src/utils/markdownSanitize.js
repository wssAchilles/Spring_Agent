import DOMPurify from 'dompurify'

/** Phase 03: sanitize HTML before v-html to prevent stored XSS. */
export function sanitizeHtml(html) {
  if (html == null || html === '') return ''
  return DOMPurify.sanitize(String(html), { USE_PROFILES: { html: true } })
}

export default { sanitizeHtml }
