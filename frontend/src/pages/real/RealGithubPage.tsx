import { AlertTriangle, ArrowRight, CheckCircle2, Github, LoaderCircle, LockKeyhole, RefreshCw, Search, ShieldCheck, Unplug } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, type GithubConnectionStatus, type GithubRepository, type GithubStatus, ApiError } from '../../api/client';
import { DemoNote, SectionHeading } from '../../components/ui';

const statusCopy: Record<GithubConnectionStatus, { label: string; cls: string; help: string }> = {
  DISCONNECTED: { label: 'Not connected', cls: 'status-neutral', help: 'Connect GitHub to start the proof flow.' },
  CONNECTED: { label: 'Connected', cls: 'status-success', help: 'Your GitHub App connection is active.' },
  REAUTH_REQUIRED: { label: 'Reauthorization required', cls: 'status-warning', help: 'The GitHub session expired. Reconnect to restore access.' },
  INSTALLATION_MISSING: { label: 'App not installed', cls: 'status-info', help: 'You authorized SkillLink, but the GitHub App is not installed yet. Install it and select the repositories to share.' },
  INSTALLATION_REMOVED: { label: 'Installation removed', cls: 'status-danger', help: 'The SkillLink GitHub App installation was removed or suspended. Reinstall it to restore repository access.' },
  TOKEN_INVALID: { label: 'Access rejected', cls: 'status-danger', help: 'GitHub rejected SkillLink access. Reconnect GitHub or review the app installation.' },
};

export default function RealGithubPage() {
  const [searchParams] = useSearchParams();
  const [status, setStatus] = useState<GithubStatus | null>(null);
  const [repositories, setRepositories] = useState<GithubRepository[]>([]);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [reposLoading, setReposLoading] = useState(false);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const callbackState = searchParams.get('github');
  const callbackCode = searchParams.get('code');

  async function load(showSpinner = true) {
    if (showSpinner) setLoading(true);
    setError(null);
    try {
      const next = await api.github.status();
      setStatus(next);
      if (next.connected) {
        setReposLoading(true);
        try { setRepositories(await api.github.repositories()); } catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Repositories could not be loaded from GitHub.'); setRepositories([]); } finally { setReposLoading(false); }
      } else {
        setRepositories([]);
      }
    } catch (reason) {
      setError(reason instanceof ApiError ? reason.message : 'The GitHub connection status could not be loaded.');
    } finally {
      if (showSpinner) setLoading(false);
    }
  }

  useEffect(() => { void load(); }, []);

  async function select(repository: GithubRepository) {
    setWorking(true); setError(null); setMessage(null);
    try {
      await api.github.select(repository.id);
      setSelectedIds((current) => current.includes(repository.id) ? current : [...current, repository.id]);
      setMessage(`${repository.fullName} is now a project source. Analysis stays an explicit later step.`);
    } catch (reason) {
      setError(reason instanceof ApiError ? reason.message : 'Repository selection failed.');
    } finally { setWorking(false); }
  }

  async function disconnect() {
    setWorking(true); setError(null); setMessage(null);
    try {
      await api.github.disconnect();
      setMessage('GitHub disconnected. Stored tokens were revoked and repository access has stopped. Existing evidence is preserved.');
      await load();
    } catch (reason) {
      setError(reason instanceof ApiError ? reason.message : 'Could not disconnect GitHub.');
    } finally { setWorking(false); }
  }

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) return repositories;
    return repositories.filter((repository) => repository.fullName.toLowerCase().includes(term) || (repository.primaryLanguage ?? '').toLowerCase().includes(term));
  }, [repositories, search]);

  if (loading) return <div className="page-stack"><DemoNote>Real GitHub workspace. The connection state below comes from the API; there is no offline fallback on this page.</DemoNote><div className="panel loading-block"><LoaderCircle className="spin" size={20} /><span>Loading GitHub connection state…</span></div></div>;

  const currentStatus = status?.status ?? 'DISCONNECTED';
  const copy = statusCopy[currentStatus];
  const needsInstall = currentStatus === 'INSTALLATION_MISSING' || currentStatus === 'INSTALLATION_REMOVED';
  const needsReauth = currentStatus === 'REAUTH_REQUIRED' || currentStatus === 'TOKEN_INVALID';

  return <div className="page-stack">
    <DemoNote>Real GitHub workspace backed by the SkillLink GitHub App: installation decides which repositories are accessible, user authorization identifies your GitHub account. This page never falls back to demo fixtures.</DemoNote>
    <div className="page-header">
      <div>
        <div className="eyebrow">Candidate GitHub connection</div>
        <h1>GitHub</h1>
        <p>Connect once, install the app on the account you want to prove, and explicitly select repositories. SkillLink only ever sees what you grant.</p>
      </div>
      <div className="page-header-actions">
        {status?.connected && <a className="button button-ghost" href={api.github.installUrl()}><Github size={15} /> Manage repositories</a>}
        <button className="button button-ghost" onClick={() => void load()}><RefreshCw size={15} /> Refresh</button>
      </div>
    </div>

    {callbackState === 'connected' && <div className="notice-bar"><CheckCircle2 size={16} /><span>GitHub authorization complete.</span></div>}
    {callbackState === 'error' && <div className="notice-bar error"><AlertTriangle size={16} /><span>GitHub connection did not complete ({callbackCode ?? 'unknown error'}). Nothing was stored.</span></div>}
    {error && <div className="notice-bar error"><AlertTriangle size={16} /><span>{error}</span><button onClick={() => setError(null)} aria-label="Dismiss error">×</button></div>}
    {message && <div className="notice-bar"><CheckCircle2 size={16} /><span>{message}</span></div>}

    <section className={`panel github-connection-panel ${status?.connected ? 'connected' : ''}`}>
      <div className="github-connection-status">
        <span className={`status-pill ${copy.cls}`}><ShieldCheck size={13} />{copy.label}</span>
        <small>{copy.help}</small>
      </div>
      {status?.connected || needsReauth ? <div className="github-connection-grid">
        <div><span className="eyebrow">GitHub user</span><strong>{status?.githubLogin ?? 'unknown'}</strong><small>authorized {status?.connectedAt ? new Date(status.connectedAt).toLocaleDateString() : '—'}</small></div>
        <div><span className="eyebrow">Installation account</span><strong>{status?.installation?.accountLogin ?? 'not installed'}</strong><small>{status?.installation ? `${status.installation.accountType.toLowerCase()} · repository access ${status.installation.repositorySelection.toLowerCase()}` : 'install the app to grant repository access'}</small></div>
        <div><span className="eyebrow">Repositories accessible</span><strong>{repositories.length}</strong><small>through the active installation</small></div>
      </div> : null}
      <div className="github-connection-actions">
        {currentStatus === 'DISCONNECTED' && <a className="button button-primary" href={api.github.connectUrl()}><Github size={16} /> Connect GitHub</a>}
        {needsInstall && <a className="button button-primary" href={api.github.installUrl()}><Github size={16} /> Install SkillLink GitHub App</a>}
        {needsReauth && <a className="button button-primary" href={api.github.connectUrl()}><Github size={16} /> Reconnect GitHub</a>}
        {status?.connected && <a className="button button-ghost" href={api.github.connectUrl()}><RefreshCw size={15} /> Reauthorize</a>}
        {(status?.connected || needsReauth) && <button className="button button-danger" onClick={() => void disconnect()} disabled={working}>{working ? <LoaderCircle className="spin" size={15} /> : <Unplug size={15} />} Disconnect</button>}
      </div>
    </section>

    {status?.connected && <section className="panel github-repository-panel">
      <SectionHeading
        eyebrow={`${repositories.length} accessible through the installation`}
        title="Repositories"
        description="Only repositories the GitHub App installation can access are listed. Selecting one persists intent; analysis happens later, only when you start it."
        action={<div className="search-input"><Search size={14} /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search repositories" aria-label="Search repositories" /></div>} />
      {reposLoading ? <div className="inline-empty"><LoaderCircle className="spin" size={16} /> Loading repositories from GitHub…</div>
        : filtered.length === 0 ? <div className="inline-empty"><Github size={17} /> {repositories.length === 0 ? 'No repositories are accessible through this installation. Grant access to at least one repository on GitHub (Manage repositories).' : 'No repositories match your search.'}</div>
        : <div className="real-repo-list">
          {filtered.map((repository) => <div className={`real-repo-row ${selectedIds.includes(repository.id) ? 'selected' : ''}`} key={repository.id}>
            <span className="real-repo-icon"><Github size={16} /></span>
            <span className="real-repo-copy"><strong>{repository.fullName}</strong><small>{repository.description || 'No description'} · {repository.primaryLanguage || 'Language not reported'} · default {repository.defaultBranch}</small></span>
            <span className="real-repo-meta">
              <span><LockKeyhole size={11} />{repository.visibility.toLowerCase()}</span>
              <small>{repository.accountLogin ?? 'installation'} · updated {repository.updatedAt ? new Date(repository.updatedAt).toLocaleDateString() : '—'}</small>
            </span>
            {selectedIds.includes(repository.id) ? <CheckCircle2 size={16} className="real-repo-selected" /> : <button className="button button-ghost button-small" onClick={() => void select(repository)} disabled={working}>Select <ArrowRight size={13} /></button>}
          </div>)}
        </div>}
      {selectedIds.length > 0 && <div className="github-selected-note"><CheckCircle2 size={14} /> {selectedIds.length} selected as project source{selectedIds.length === 1 ? '' : 's'}. <Link to="/app/projects" className="text-action">Open projects <ArrowRight size={12} /></Link></div>}
    </section>}
  </div>;
}
