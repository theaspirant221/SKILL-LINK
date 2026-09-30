import { useEffect, useRef, useState } from 'react';
import { AlertTriangle, ArrowRight, Check, FileCode, Github, Link2, LoaderCircle, RefreshCw, ShieldCheck } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, type AnalysisJob, type ApiProject, type GithubStatus, type Snapshot, type SnapshotDetail, ApiError } from '../../api/client';
import { DemoNote, SectionHeading } from '../../components/ui';

export default function RealProjectsPage() {
  const [searchParams] = useSearchParams();
  const [status, setStatus] = useState<GithubStatus | null>(null);
  const [projects, setProjects] = useState<ApiProject[]>([]);
  const [selectedProject, setSelectedProject] = useState<ApiProject | null>(null);
  const [job, setJob] = useState<AnalysisJob | null>(null);
  const [snapshots, setSnapshots] = useState<Snapshot[]>([]);
  const [selectedSnapshot, setSelectedSnapshot] = useState<SnapshotDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [snapshotWorking, setSnapshotWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [branchInput, setBranchInput] = useState('');
  const pollRef = useRef<number | undefined>();
  const callbackMessage = searchParams.get('github') === 'connected' ? 'GitHub authorization complete. Continue on the GitHub page to install the app and select repositories.' : searchParams.get('github') === 'error' ? `GitHub connection did not complete (${searchParams.get('code') ?? 'unknown error'}).` : null;

  async function load() {
    setLoading(true); setError(null);
    try {
      const [nextStatus, nextProjects] = await Promise.all([api.github.status(), api.projects.list()]);
      setStatus(nextStatus); setProjects(nextProjects); setSelectedProject((current) => current ?? nextProjects[0] ?? null);
    } catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Could not load the proof workspace.'); }
    finally { setLoading(false); }
  }

  async function loadSnapshots(projectId: string) {
    try {
      const list = await api.snapshots.list(projectId);
      setSnapshots(list);
      if (list.length > 0 && !selectedSnapshot) {
        // Auto-select latest READY snapshot
        const ready = list.find(s => s.status === 'READY') ?? list[0];
        if (ready) {
          const detail = await api.snapshots.get(projectId, ready.snapshotId);
          setSelectedSnapshot(detail);
        }
      }
    } catch {
      // Snapshots may not exist yet, ignore
      setSnapshots([]);
    }
  }

  useEffect(() => { void load(); return () => { if (pollRef.current) window.clearInterval(pollRef.current); }; }, []);

  useEffect(() => {
    if (selectedProject) {
      void loadSnapshots(selectedProject.projectId);
      setJob(null);
      setSelectedSnapshot(null);
    }
  }, [selectedProject?.projectId]);

  async function startAnalysis() { if (!selectedProject) return; setWorking(true); setError(null); try { const nextJob = await api.projects.startAnalysis(selectedProject.projectId); setJob(nextJob); if (nextJob.state !== 'COMPLETED' && nextJob.state !== 'FAILED') pollRef.current = window.setInterval(() => { void poll(nextJob.jobId); }, 1400); } catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Could not start analysis.'); } finally { setWorking(false); } }
  async function poll(jobId: string) { if (!selectedProject) return; try { const next = await api.projects.analysis(selectedProject.projectId, jobId); setJob(next); if (next.state === 'COMPLETED' || next.state === 'FAILED') { if (pollRef.current) window.clearInterval(pollRef.current); } } catch (reason) { if (pollRef.current) window.clearInterval(pollRef.current); setError(reason instanceof ApiError ? reason.message : 'Analysis status could not be loaded.'); } }
  async function retry() { if (!selectedProject || !job) return; setWorking(true); try { const next = await api.projects.retryAnalysis(selectedProject.projectId, job.jobId); setJob(next); pollRef.current = window.setInterval(() => { void poll(next.jobId); }, 1400); } catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Retry could not be started.'); } finally { setWorking(false); } }

  async function createSnapshot() {
    if (!selectedProject) return;
    setSnapshotWorking(true); setError(null);
    try {
      const snapshot = await api.snapshots.create(selectedProject.projectId, branchInput.trim() || undefined);
      // Reload snapshots
      await loadSnapshots(selectedProject.projectId);
      // Fetch detail
      const detail = await api.snapshots.get(selectedProject.projectId, snapshot.snapshotId);
      setSelectedSnapshot(detail);
    } catch (reason) {
      setError(reason instanceof ApiError ? `${reason.code}: ${reason.message}` : 'Could not create snapshot.');
    } finally {
      setSnapshotWorking(false);
    }
  }

  async function selectSnapshot(snapshotId: string) {
    if (!selectedProject) return;
    try {
      const detail = await api.snapshots.get(selectedProject.projectId, snapshotId);
      setSelectedSnapshot(detail);
    } catch (reason) {
      setError(reason instanceof ApiError ? reason.message : 'Could not load snapshot.');
    }
  }

  const connectionIssue = status && status.status !== 'CONNECTED';

  return <div className="page-stack">
    <DemoNote>Real project workspace. Repository sources are created on the GitHub page; snapshots freeze an exact commit for reproducible analysis.</DemoNote>
    <div className="page-header">
      <div>
        <div className="eyebrow">Candidate proof sources</div>
        <h1>Projects</h1>
        <p>Selected repository sources, immutable snapshots at exact commits, and analysis jobs. Repository access comes from the GitHub App installation.</p>
      </div>
      <div className="page-header-actions">
        <Link to="/app/github" className="button button-ghost"><Github size={15} /> GitHub connection</Link>
        <button className="button button-ghost" onClick={() => void load()}><RefreshCw size={15} /> Refresh</button>
      </div>
    </div>
    {callbackMessage && <div className={`notice-bar ${searchParams.get('github') === 'error' ? 'error' : ''}`}><ShieldCheck size={16} /><span>{callbackMessage}</span></div>}
    {error && <div className="notice-bar error"><AlertTriangle size={16} /><span>{error}</span><button onClick={() => setError(null)}>×</button></div>}
    {loading ? <section className="panel loading-block"><LoaderCircle className="spin" size={20} /><span>Loading project sources…</span></section> : <>
      <section className="panel github-connection-panel slim">
        <div className="github-connection-status">
          <span className={`status-pill ${connectionIssue ? 'status-info' : 'status-success'}`}><Github size={13} />{connectionIssue ? 'GitHub needs attention' : 'GitHub connected'}</span>
          <small>{connectionIssue ? 'Repository access is not active. Open the GitHub page to connect, install the app, or reconnect.' : `Connected as ${status?.githubLogin ?? 'unknown'} · installation ${status?.installation?.accountLogin ?? 'n/a'}`}</small>
        </div>
        <Link to="/app/github" className="button button-ghost button-small"><Link2 size={13} /> Open GitHub page <ArrowRight size={12} /></Link>
      </section>
      <section className="real-repository-layout">
        <section className="panel real-repository-panel">
          <SectionHeading eyebrow={`${projects.length} selected project source${projects.length === 1 ? '' : 's'}`} title="Your projects" description="Each project was explicitly selected from an authorized repository." />
          {projects.length === 0 ? <div className="inline-empty"><Github size={17} /> No repository sources yet. Select a repository on the GitHub page.</div> : <div className="real-project-grid">
            {projects.map((project) => <button className={`real-project-card ${selectedProject?.projectId === project.projectId ? 'selected' : ''}`} key={project.projectId} onClick={() => setSelectedProject(project)}><span className="real-project-card-icon"><Github size={17} /></span><strong>{project.fullName}</strong><small>{project.branch} · {project.primaryLanguage || 'Language not reported'}</small><span>{project.visibility.toLowerCase()}</span></button>)}
          </div>}
        </section>
        <section className="panel real-analysis-panel">
          <SectionHeading eyebrow="Selected project" title={selectedProject?.name ?? 'Nothing selected'} description={selectedProject ? `${selectedProject.fullName} · ${selectedProject.branch}` : 'Pick a project to analyze.'} />
          {selectedProject ? <>
            <div className="real-analysis-source">
              <div><span className="eyebrow">Source boundary</span><strong>{selectedProject.fullName}</strong><span>{selectedProject.visibility.toLowerCase()} · branch {selectedProject.branch}</span></div>
              <Github size={20} />
            </div>

            {/* Checkpoint D: Immutable Snapshot */}
            <div className="snapshot-section" style={{ marginTop: '1.5rem', borderTop: '1px solid var(--border)', paddingTop: '1rem' }}>
              <SectionHeading eyebrow="Checkpoint D" title="Immutable Repository Snapshot" description="SkillLink analyzes this exact commit. Every proof points to repository + immutable SHA + manifest." />
              
              <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem' }}>
                <input
                  type="text"
                  placeholder={`Branch (default: ${selectedProject.branch})`}
                  value={branchInput}
                  onChange={(e) => setBranchInput(e.target.value)}
                  style={{ flex: 1, padding: '0.5rem', border: '1px solid var(--border)', borderRadius: '6px' }}
                />
                <button className="button button-primary" onClick={() => void createSnapshot()} disabled={snapshotWorking}>
                  {snapshotWorking ? <><LoaderCircle className="spin" size={15} /> Creating…</> : <><FileCode size={15} /> Create Snapshot</>}
                </button>
              </div>

              {snapshots.length > 0 && (
                <div style={{ marginBottom: '1rem' }}>
                  <div className="eyebrow" style={{ marginBottom: '0.5rem' }}>{snapshots.length} snapshot{snapshots.length === 1 ? '' : 's'} for this project</div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem', maxHeight: '200px', overflowY: 'auto' }}>
                    {snapshots.map((snap) => (
                      <button
                        key={snap.snapshotId}
                        onClick={() => void selectSnapshot(snap.snapshotId)}
                        className={`real-project-card ${selectedSnapshot?.snapshot.snapshotId === snap.snapshotId ? 'selected' : ''}`}
                        style={{ textAlign: 'left', padding: '0.6rem' }}
                      >
                        <strong style={{ fontSize: '0.9rem' }}>{snap.shortSha} · {snap.branchName}</strong>
                        <small>{snap.status} · {snap.includedFileCount} files · {new Date(snap.createdAt).toLocaleString()}</small>
                        <small style={{ fontFamily: 'monospace', fontSize: '0.7rem' }}>{snap.commitSha.substring(0, 12)}…</small>
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {selectedSnapshot && (
                <div className="real-job-card" style={{ background: 'var(--panel-bg, #f9fafb)' }}>
                  <div className="real-job-head">
                    <div>
                      <span className="eyebrow">Snapshot {selectedSnapshot.snapshot.status}</span>
                      <strong>{selectedSnapshot.snapshot.shortSha} · {selectedSnapshot.snapshot.branchName}</strong>
                    </div>
                    <span className={`real-job-state ${selectedSnapshot.snapshot.status.toLowerCase()}`}><span />{selectedSnapshot.snapshot.status}</span>
                  </div>
                  
                  <div style={{ padding: '0.8rem', fontSize: '0.85rem', lineHeight: '1.5' }}>
                    <div><strong>Repository:</strong> {selectedSnapshot.snapshot.fullName}</div>
                    <div><strong>Commit SHA:</strong> <code style={{ fontSize: '0.8rem', wordBreak: 'break-all' }}>{selectedSnapshot.snapshot.commitSha}</code></div>
                    <div><strong>Short SHA:</strong> {selectedSnapshot.snapshot.shortSha}</div>
                    <div><strong>Branch:</strong> {selectedSnapshot.snapshot.branchName}</div>
                    <div><strong>Author:</strong> {selectedSnapshot.snapshot.commitAuthor || 'unknown'}</div>
                    <div><strong>Commit Time:</strong> {selectedSnapshot.snapshot.commitTimestamp ? new Date(selectedSnapshot.snapshot.commitTimestamp).toLocaleString() : 'unknown'}</div>
                    <div><strong>Snapshot Created:</strong> {selectedSnapshot.snapshot.snapshotCreatedAt ? new Date(selectedSnapshot.snapshot.snapshotCreatedAt).toLocaleString() : new Date(selectedSnapshot.snapshot.createdAt).toLocaleString()}</div>
                    <div><strong>File Policy:</strong> {selectedSnapshot.snapshot.filePolicyVersion} · <strong>Analysis Version:</strong> {selectedSnapshot.snapshot.analysisVersion}</div>
                    <div><strong>Files:</strong> {selectedSnapshot.snapshot.includedFileCount} included, {selectedSnapshot.snapshot.excludedFileCount} excluded, {selectedSnapshot.snapshot.fileCount} total</div>
                    <div><strong>Total Bytes:</strong> {selectedSnapshot.summary.totalBytes.toLocaleString()}</div>
                    <div><strong>Integrity Hash:</strong> <code style={{ fontSize: '0.7rem', wordBreak: 'break-all' }}>{selectedSnapshot.snapshot.integrityHash?.substring(0, 16)}…</code></div>
                    {selectedSnapshot.summary.secretRedactedFiles > 0 && (
                      <div style={{ color: 'var(--warning, #d97706)' }}><strong>Secrets Redacted:</strong> {selectedSnapshot.summary.totalSecrets} secrets in {selectedSnapshot.summary.secretRedactedFiles} files</div>
                    )}
                  </div>

                  <details style={{ margin: '0.5rem 0.8rem' }}>
                    <summary style={{ cursor: 'pointer', fontWeight: 600, fontSize: '0.85rem' }}>File Manifest ({selectedSnapshot.files.length} entries)</summary>
                    <div style={{ maxHeight: '300px', overflowY: 'auto', marginTop: '0.5rem', border: '1px solid var(--border)', borderRadius: '4px' }}>
                      <table style={{ width: '100%', fontSize: '0.75rem', borderCollapse: 'collapse' }}>
                        <thead>
                          <tr style={{ background: '#f3f4f6', position: 'sticky', top: 0 }}>
                            <th style={{ textAlign: 'left', padding: '0.3rem' }}>Path</th>
                            <th style={{ textAlign: 'left', padding: '0.3rem' }}>Lang</th>
                            <th style={{ textAlign: 'right', padding: '0.3rem' }}>Size</th>
                            <th style={{ textAlign: 'center', padding: '0.3rem' }}>Inc</th>
                            <th style={{ textAlign: 'left', padding: '0.3rem' }}>Reason</th>
                          </tr>
                        </thead>
                        <tbody>
                          {selectedSnapshot.files.map((f) => (
                            <tr key={f.path} style={{ borderTop: '1px solid #eee', background: f.included ? 'white' : '#fef2f2' }}>
                              <td style={{ padding: '0.25rem', fontFamily: 'monospace', maxWidth: '200px', overflow: 'hidden', textOverflow: 'ellipsis' }}>{f.path}</td>
                              <td style={{ padding: '0.25rem' }}>{f.language || '-'}</td>
                              <td style={{ padding: '0.25rem', textAlign: 'right' }}>{f.sizeBytes}</td>
                              <td style={{ padding: '0.25rem', textAlign: 'center' }}>{f.included ? '✓' : '✗'}</td>
                              <td style={{ padding: '0.25rem', fontSize: '0.7rem' }}>{f.exclusionReason || (f.secretRedacted ? `redacted ${f.secretCount}` : '')}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </details>

                  <div style={{ padding: '0.5rem 0.8rem', fontSize: '0.75rem', color: 'var(--muted)' }}>
                    <ShieldCheck size={12} style={{ display: 'inline', marginRight: '4px' }} />
                    SkillLink analyzes this exact commit. Snapshot is immutable once READY.
                  </div>
                </div>
              )}

              {!selectedSnapshot && snapshots.length === 0 && (
                <div className="inline-empty"><FileCode size={16} /> No snapshots yet. Create one from the current branch HEAD.</div>
              )}
            </div>

            {/* Existing Analysis Job */}
            <div style={{ marginTop: '1.5rem', borderTop: '1px solid var(--border)', paddingTop: '1rem' }}>
              <SectionHeading eyebrow="Analysis" title="Deterministic Analysis" description="Runs after snapshot is READY (Checkpoint E will use the immutable snapshot)." />
              {job && <div className="real-job-card"><div className="real-job-head"><div><span className="eyebrow">Analysis job</span><strong>{job.state === 'FAILED' ? 'Analysis failed' : job.state === 'COMPLETED' ? 'Evidence ready' : job.stage}</strong></div><span className={`real-job-state ${job.state.toLowerCase()}`}><span />{job.state}</span></div><div className="progress-track"><span style={{ width: `${job.progress}%` }} /></div><div className="real-job-foot"><span>{job.progress}% · {job.stage}</span>{job.state === 'FAILED' && <button className="text-action" onClick={() => void retry()}><RefreshCw size={13} /> Retry</button>}</div>{job.errorMessage && <div className="real-job-error"><AlertTriangle size={14} />{job.errorMessage}</div>}</div>}
              {(!job || job.state === 'FAILED') && connectionIssue ? <div className="inline-empty"><ShieldCheck size={16} /> Active GitHub repository access is required before analysis. Open the GitHub page to restore it.</div> : !job || job.state === 'FAILED' ? <button className="button button-primary button-full" onClick={() => void startAnalysis()} disabled={working}>{working ? <><LoaderCircle className="spin" size={15} /> Starting…</> : <><ShieldCheck size={15} /> Analyze current branch commit</>}</button> : job.state === 'COMPLETED' ? <Link className="button button-primary button-full" to={`/app/evidence?projectId=${selectedProject.projectId}`}><Check size={15} /> Review persisted evidence <ArrowRight size={14} /></Link> : <div className="real-analysis-wait"><LoaderCircle className="spin" size={15} /> Backend worker is processing this commit snapshot. This page polls actual job state.</div>}
            </div>
          </> : <div className="empty-state"><Github size={22} /><h3>Select a repository</h3><p>The project and snapshot are created only after an authorized repository is selected on the GitHub page.</p></div>}
        </section>
      </section>
    </>}
  </div>;
}
