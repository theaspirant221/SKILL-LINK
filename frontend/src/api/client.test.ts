import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, getAccessToken, setAccessToken } from './client';

function jsonResponse(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, text: () => Promise.resolve(JSON.stringify(body)), json: () => Promise.resolve(body) };
}

const fetchMock = vi.fn();

describe('candidate API client', () => {
  beforeEach(() => { fetchMock.mockReset(); vi.stubGlobal('fetch', fetchMock); setAccessToken('token-1'); });
  afterEach(() => { vi.unstubAllGlobals(); setAccessToken(null); });

  it('registers a candidate without any client-supplied role field', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ accessToken: 'token-2', accessTokenExpiresInSeconds: 900, user: { id: 'u1', email: 'it@example.com', displayName: 'It Candidate', role: 'CANDIDATE' } }));
    const response = await api.auth.register({ email: 'it@example.com', password: 'correct-horse-battery', displayName: 'It Candidate' });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/auth/register');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual({ email: 'it@example.com', password: 'correct-horse-battery', displayName: 'It Candidate' });
    expect(response.user.role).toBe('CANDIDATE');
    expect(getAccessToken()).toBe('token-2');
  });

  it('lists open jobs with an authenticated GET /jobs', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse([]));
    await api.candidate.openJobs();
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/jobs');
    expect(init.method).toBeUndefined();
    expect(init.headers.get('Authorization')).toBe('Bearer token-1');
    expect(init.headers.get('Accept')).toBe('application/json');
  });

  it('applies to a job and trims the optional note', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ applicationId: 'a1', jobId: 'job-1', candidateId: 'c1', candidateDisplayName: 'Candidate One', jobTitle: 'Backend Engineer', status: 'APPLIED', note: 'context', createdAt: '2026-09-29T00:00:00Z' }));
    await api.candidate.apply('job-1', '  context  ');
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/jobs/job-1/applications');
    expect(init.method).toBe('POST');
    expect(init.headers.get('Content-Type')).toBe('application/json');
    expect(JSON.parse(init.body)).toEqual({ note: 'context' });
  });

  it('sends a null note when the application note is blank', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ applicationId: 'a2', jobId: 'job-2', candidateId: 'c1', candidateDisplayName: 'Candidate One', jobTitle: 'Platform Engineer', status: 'APPLIED', note: null, createdAt: '2026-09-29T00:00:00Z' }));
    await api.candidate.apply('job-2', '   ');
    const [, init] = fetchMock.mock.calls[0];
    expect(JSON.parse(init.body)).toEqual({ note: null });
  });

  it('lists the candidate applications history', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse([]));
    await api.candidate.applications();
    const [url] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/candidates/me/applications');
  });

  it('loads the candidate view of shared proof contracts', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse([]));
    await api.candidate.proofContracts();
    const [url] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/candidates/me/proof-contracts');
  });

  it('loads a public passport by identifier without a session', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ publicIdentifier: 'slp_example', candidateDisplayName: 'Candidate One', visibility: 'PUBLIC_SUMMARY', issuedAt: '2026-09-28T00:00:00Z', expiresAt: null, items: [] }));
    const passport = await api.passport.public('slp_example');
    const [url] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/public/passports/slp_example');
    expect(passport.candidateDisplayName).toBe('Candidate One');
  });

  it('surfaces the structured error envelope instead of a generic failure', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ code: 'APPLICATION_EXISTS', message: 'You already have an application for this job.' }, 409));
    const failure: unknown = await api.candidate.apply('job-1').catch((reason) => reason);
    expect(failure).toBeInstanceOf(ApiError);
    if (failure instanceof ApiError) {
      expect(failure.code).toBe('APPLICATION_EXISTS');
      expect(failure.status).toBe(409);
      expect(failure.message).toBe('You already have an application for this job.');
    }
  });
});
