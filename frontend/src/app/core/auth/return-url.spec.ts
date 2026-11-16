import { safeReturnUrl } from './return-url';

describe('safeReturnUrl', () => {
  it('keeps internal routes with query string', () => {
    expect(safeReturnUrl('/requests?status=DRAFT&page=2')).toBe('/requests?status=DRAFT&page=2');
  });

  it.each([
    undefined,
    '',
    'requests',
    'https://evil.example.com',
    '//evil.example.com',
    '/\\evil.example.com',
    '/login',
    '/login?returnUrl=%2Frequests',
  ])('falls back to the dashboard for %s', (raw) => {
    expect(safeReturnUrl(raw)).toBe('/dashboard');
  });
});
