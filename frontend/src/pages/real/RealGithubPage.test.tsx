import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import RealGithubPage from './RealGithubPage';
import { api, ApiError, type GithubRepository, type GithubStatus } from '../../api/client';

vi.mock('../../api/client', () => {
  return {
    api: {
      github: {
        connectUrl: () => '/api/v1/github/connect',
        installUrl: () => '/api/v1/github/install',
        status: vi.fn(),
        repositories: vi.fn(),
        select: vi.fn(),
        disconnect: vi.fn(),
      },
    },
    isDemoMode: false,
    ApiError: class ApiError extends Error {
      code: string; status: number;
      constructor(code: string, message: string, status: number) { super(message); this.code = code; this.status = status; }
    },
  };
});

const DISCONNECTED: GithubStatus = { status: 'DISCONNECTED', connected: false, githubLogin: null, githubUserId: null, scopes: null, connectedAt: null, lastValidatedAt: null, installation: null };
const CONNECTED: GithubStatus = {
  status: 'CONNECTED', connected: true, githubLogin: 'octocat', githubUserId: 9001, scopes: '', connectedAt: '2026-09-29T10:00:00Z', lastValidatedAt: '2026-09-29T10:00:00Z',
  installation: { installationId: 42, accountId: 5001, accountLogin: 'acme-org', accountType: 'Organization', repositorySelection: 'SELECTED', status: 'ACTIVE' },
};
const REPOSITORIES: GithubRepository[] = [
  { id: '7001', name: 'foodbridge', fullName: 'acme-org/foodbridge', owner: 'acme-org', privateRepository: true, visibility: 'PRIVATE', defaultBranch: 'main', primaryLanguage: 'Java', updatedAt: '2026-09-01T10:00:00Z', sizeKb: 4200, description: 'Fixture repo', accountLogin: 'acme-org' },
  { id: '7002', name: 'other-tool', fullName: 'acme-org/other-tool', owner: 'acme-org', privateRepository: false, visibility: 'PUBLIC', defaultBranch: 'main', primaryLanguage: 'Python', updatedAt: null, sizeKb: 100, description: null, accountLogin: 'acme-org' },
];

function renderPage(initialEntry = '/app/github') {
  return render(<MemoryRouter initialEntries={[initialEntry]}>
    <Routes>
      <Route path="/app/github" element={<RealGithubPage />} />
      <Route path="/app/projects" element={<div>projects page</div>} />
    </Routes>
  </MemoryRouter>);
}

describe('RealGithubPage (server-backed GitHub workspace)', () => {
  beforeEach(() => {
    vi.mocked(api.github.status).mockReset();
    vi.mocked(api.github.repositories).mockReset();
    vi.mocked(api.github.select).mockReset();
    vi.mocked(api.github.disconnect).mockReset();
  });

  it('shows a loading state while the connection status is fetched', () => {
    vi.mocked(api.github.status).mockReturnValue(new Promise(() => {}));
    renderPage();
    expect(screen.getByText(/Loading GitHub connection state/i)).toBeTruthy();
  });

  it('renders a connect button pointing at the backend connect URL when disconnected', async () => {
    vi.mocked(api.github.status).mockResolvedValue(DISCONNECTED);
    renderPage();
    const connect = await screen.findByRole('link', { name: /connect github/i });
    expect(connect.getAttribute('href')).toBe('/api/v1/github/connect');
    expect(api.github.repositories).not.toHaveBeenCalled();
  });

  it('prompts to install the app when authorized but not installed', async () => {
    vi.mocked(api.github.status).mockResolvedValue({ ...CONNECTED, status: 'INSTALLATION_MISSING', connected: false });
    renderPage();
    const install = await screen.findByRole('link', { name: /install skilllink github app/i });
    expect(install.getAttribute('href')).toBe('/api/v1/github/install');
  });

  it('shows account, installation context, and only installation-authorized repositories', async () => {
    vi.mocked(api.github.status).mockResolvedValue(CONNECTED);
    vi.mocked(api.github.repositories).mockResolvedValue(REPOSITORIES);
    renderPage();
    expect(await screen.findByText('octocat')).toBeTruthy();
    expect(screen.getByText('acme-org')).toBeTruthy();
    expect(screen.getByText('acme-org/foodbridge')).toBeTruthy();
    expect(screen.getByText('acme-org/other-tool')).toBeTruthy();
    expect(screen.getByText(/2 accessible through the installation/i)).toBeTruthy();
    expect(screen.queryByText(/FoodBridge API/i)).toBeNull();
  });

  it('keeps repository selection explicit', async () => {
    vi.mocked(api.github.status).mockResolvedValue(CONNECTED);
    vi.mocked(api.github.repositories).mockResolvedValue(REPOSITORIES);
    vi.mocked(api.github.select).mockResolvedValue({ projectId: 'p1', projectName: 'foodbridge', repositoryFullName: 'acme-org/foodbridge', commitBranch: 'main' });
    renderPage();
    const selectButtons = await screen.findAllByRole('button', { name: /select/i });
    expect(selectButtons.length).toBe(2);
    selectButtons[0].click();
    await waitFor(() => expect(api.github.select).toHaveBeenCalledWith('7001'));
    expect(await screen.findByText(/is now a project source/i)).toBeTruthy();
  });

  it('shows an explicit empty state when the installation grants no repositories', async () => {
    vi.mocked(api.github.status).mockResolvedValue(CONNECTED);
    vi.mocked(api.github.repositories).mockResolvedValue([]);
    renderPage();
    expect(await screen.findByText(/No repositories are accessible through this installation/i)).toBeTruthy();
  });

  it('surfaces API errors without falling back to demo fixtures', async () => {
    vi.mocked(api.github.status).mockRejectedValue(new ApiError('API_REQUEST_FAILED', 'Request failed with status 500.', 500));
    renderPage();
    expect(await screen.findByText(/Request failed with status 500/i)).toBeTruthy();
    expect(screen.queryByText(/FoodBridge API/i)).toBeNull();
    expect(screen.queryByText(/offline demo/i)).toBeNull();
  });

  it('disconnects through the API and reports preserved evidence', async () => {
    vi.mocked(api.github.status).mockResolvedValueOnce(CONNECTED).mockResolvedValue(DISCONNECTED);
    vi.mocked(api.github.repositories).mockResolvedValue(REPOSITORIES);
    vi.mocked(api.github.disconnect).mockResolvedValue(undefined);
    renderPage();
    const disconnect = await screen.findByRole('button', { name: /disconnect/i });
    disconnect.click();
    await waitFor(() => expect(api.github.disconnect).toHaveBeenCalled());
    expect(await screen.findByText(/Existing evidence is preserved/i)).toBeTruthy();
  });

  it('shows a failure banner when the GitHub callback reports an error', async () => {
    vi.mocked(api.github.status).mockResolvedValue(DISCONNECTED);
    renderPage('/app/github?github=error&code=GITHUB_STATE_INVALID');
    expect(await screen.findByText(/did not complete \(GITHUB_STATE_INVALID\)/i)).toBeTruthy();
  });

  it('shows a success banner when the GitHub callback completes', async () => {
    vi.mocked(api.github.status).mockResolvedValue(DISCONNECTED);
    renderPage('/app/github?github=connected');
    expect(await screen.findByText(/GitHub authorization complete/i)).toBeTruthy();
  });
});
