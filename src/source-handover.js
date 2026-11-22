// Only canonical, private Drive file links are accepted. This validates shape,
// not Drive access or archive freshness; neither can be inferred from a URL.
export function validateSourceHandover(data) {
  if (!data || typeof data.url !== 'string' || data.url.length > 2048) throw new Error('Invalid source handover configuration.');
  let url;
  try { url = new URL(data.url); } catch { throw new Error('Invalid source handover configuration.'); }
  if (url.protocol !== 'https:' || url.hostname !== 'drive.google.com' || url.port || url.username || url.password || url.hash || !/^\/file\/d\/[A-Za-z0-9_-]{10,200}\/view$/.test(url.pathname)) {
    throw new Error('Invalid source handover configuration.');
  }
  return {
    url: `https://drive.google.com${url.pathname}`,
    commit: typeof data.commit === 'string' && /^[a-f0-9]{40}$/.test(data.commit) ? data.commit : '',
  };
}
