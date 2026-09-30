import { useEffect, useRef, useState } from 'react';
import { AlertTriangle, ArrowRight, Check, FileCode, Github, Link2, LoaderCircle, RefreshCw, ShieldCheck, Search, Code, Database, Lock, FlaskConical, Boxes, Layers } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, type AnalysisJob, type ApiProject, type GithubStatus, type Snapshot, type SnapshotDetail, type AnalysisRun, type AnalysisDetail, ApiError } from '../../api/client';
import { DemoNote, SectionHeading } from '../../components/ui';

export default function RealProjectsPage() {
  const [searchParams] = useSearchParams();
  const [status, setStatus] = useState<GithubStatus | null>(null);
  const [projects, setProjects] = useState<ApiProject[]>([]);
  const [selectedProject, setSelectedProject] = useState<ApiProject | null>(null);
  const [job, setJob] = useState<AnalysisJob | null>(null);
  const [snapshots, setSnapshots] = useState<Snapshot[]>([]);
  const [selectedSnapshot, setSelectedSnapshot] = useState<SnapshotDetail | null>(null);
  const [analysisRuns, setAnalysisRuns] = useState<AnalysisRun[]>([]);
  const [selectedAnalysis, setSelectedAnalysis] = useState<AnalysisDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [snapshotWorking, setSnapshotWorking] = useState(false);
  const [analysisWorking, setAnalysisWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [branchInput, setBranchInput] = useState('');
  const pollRef = useRef<number | undefined>();
  const analysisPollRef = useRef<number | undefined>();
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
        const ready = list.find(s => s.status === 'READY') ?? list[0];
        if (ready) {
          const detail = await api.snapshots.get(projectId, ready.snapshotId);
          setSelectedSnapshot(detail);
          await loadAnalysisRuns(projectId, ready.snapshotId);
        }
      }
    } catch {
      setSnapshots([]);
    }
  }

  async function loadAnalysisRuns(projectId: string, snapshotId: string) {
    try {
      const runs = await api.analysis.list(projectId, snapshotId);
      setAnalysisRuns(runs);
      if (runs.length > 0) {
        const latest = runs[0];
        if (latest.status === 'COMPLETE') {
          const detail = await api.analysis.detail(projectId, snapshotId, latest.analysisRunId);
          setSelectedAnalysis(detail);
        }
      }
    } catch {
      setAnalysisRuns([]);
    }
  }

  useEffect(() => { void load(); return () => { if (pollRef.current) window.clearInterval(pollRef.current); if (analysisPollRef.current) window.clearInterval(analysisPollRef.current); }; }, []);

  useEffect(() => {
    if (selectedProject) {
      void loadSnapshots(selectedProject.projectId);
      setJob(null);
      setSelectedSnapshot(null);
      setAnalysisRuns([]);
      setSelectedAnalysis(null);
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
      await loadSnapshots(selectedProject.projectId);
      const detail = await api.snapshots.get(selectedProject.projectId, snapshot.snapshotId);
      setSelectedSnapshot(detail);
      setAnalysisRuns([]);
      setSelectedAnalysis(null);
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
      await loadAnalysisRuns(selectedProject.projectId, snapshotId);
      setSelectedAnalysis(null);
    } catch (reason) {
      setError(reason instanceof ApiError ? reason.message : 'Could not load snapshot.');
    }
  }

  async function createDeterministicAnalysis() {
    if (!selectedProject || !selectedSnapshot) return;
    if (selectedSnapshot.snapshot.status !== 'READY') {
      setError('Snapshot must be READY before deterministic analysis');
      return;
    }
    setAnalysisWorking(true); setError(null);
    try {
      const run = await api.analysis.create(selectedProject.projectId, selectedSnapshot.snapshot.snapshotId);
      setAnalysisRuns(prev => [run, ...prev]);
      // Poll for completion
      analysisPollRef.current = window.setInterval(() => { void pollDeterministicAnalysis(run.analysisRunId); }, 1500);
    } catch (reason) {
      setError(reason instanceof ApiError ? `${reason.code}: ${reason.message}` : 'Could not start deterministic analysis.');
    } finally {
      setAnalysisWorking(false);
    }
  }

  async function pollDeterministicAnalysis(runId: string) {
    if (!selectedProject || !selectedSnapshot) return;
    try {
      const run = await api.analysis.get(selectedProject.projectId, selectedSnapshot.snapshot.snapshotId, runId);
      setAnalysisRuns(prev => prev.map(r => r.analysisRunId === runId ? run : r));
      if (run.status === 'COMPLETE') {
        if (analysisPollRef.current) window.clearInterval(analysisPollRef.current);
        const detail = await api.analysis.detail(selectedProject.projectId, selectedSnapshot.snapshot.snapshotId, runId);
        setSelectedAnalysis(detail);
      } else if (run.status === 'FAILED') {
        if (analysisPollRef.current) window.clearInterval(analysisPollRef.current);
        setError(`Deterministic analysis failed: ${run.failureCode} - ${run.failureMessage}`);
      }
    } catch (reason) {
      if (analysisPollRef.current) window.clearInterval(analysisPollRef.current);
      setError(reason instanceof ApiError ? reason.message : 'Could not poll analysis status.');
    }
  }

  async function selectAnalysisRun(runId: string) {
    if (!selectedProject || !selectedSnapshot) return;
    try {
      const detail = await api.analysis.detail(selectedProject.projectId, selectedSnapshot.snapshot.snapshotId, runId);
      setSelectedAnalysis(detail);
    } catch (reason) {
      setError(reason instanceof ApiError ? reason.message : 'Could not load analysis detail.');
    }
  }

  const connectionIssue = status && status.status !== 'CONNECTED';

  return <div className="page-stack">
    <DemoNote>Real project workspace. Repository sources are created on the GitHub page; snapshots freeze an exact commit for reproducible analysis.</DemoNote>
    <div className="page-header">
      <div>
        <div className="eyebrow">Candidate proof sources</div>
        <h1>Projects</h1>
        <p>Selected repository sources, immutable snapshots at exact commits, and deterministic analysis. Repository access comes from the GitHub App installation.</p>
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
                    <div><strong>Files:</strong> {selectedSnapshot.snapshot.includedFileCount} included, {selectedSnapshot.snapshot.excludedFileCount} excluded</div>
                    <div><strong>Integrity Hash:</strong> <code style={{ fontSize: '0.7rem', wordBreak: 'break-all' }}>{selectedSnapshot.snapshot.integrityHash?.substring(0, 16)}…</code></div>
                  </div>

                  <div style={{ padding: '0.5rem 0.8rem', fontSize: '0.75rem', color: 'var(--muted)' }}>
                    <ShieldCheck size={12} style={{ display: 'inline', marginRight: '4px' }} />
                    SkillLink analyzes this exact commit. Snapshot is immutable once READY.
                  </div>
                </div>
              )}
            </div>

            {/* Checkpoint E: Deterministic Repository Analyzer */}
            {selectedSnapshot && selectedSnapshot.snapshot.status === 'READY' && (
              <div className="deterministic-analysis-section" style={{ marginTop: '1.5rem', borderTop: '2px solid var(--border)', paddingTop: '1rem' }}>
                <SectionHeading eyebrow="Checkpoint E" title="Deterministic Repository Analyzer" description="What objectively exists inside that immutable snapshot? No LLM, only AST and manifest parsing." />
                
                <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem' }}>
                  <button className="button button-primary" onClick={() => void createDeterministicAnalysis()} disabled={analysisWorking}>
                    {analysisWorking ? <><LoaderCircle className="spin" size={15} /> Analyzing…</> : <><Search size={15} /> Analyze Snapshot</>}
                  </button>
                  <span style={{ fontSize: '0.8rem', color: 'var(--muted)', alignSelf: 'center' }}>
                    Analyzer version: deterministic-v1 / deterministic-java-v1
                  </span>
                </div>

                {analysisRuns.length > 0 && (
                  <div style={{ marginBottom: '1rem' }}>
                    <div className="eyebrow" style={{ marginBottom: '0.5rem' }}>{analysisRuns.length} analysis run{analysisRuns.length === 1 ? '' : 's'} for snapshot {selectedSnapshot.snapshot.shortSha}</div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem', maxHeight: '200px', overflowY: 'auto' }}>
                      {analysisRuns.map((run) => (
                        <button
                          key={run.analysisRunId}
                          onClick={() => void selectAnalysisRun(run.analysisRunId)}
                          className={`real-project-card ${selectedAnalysis?.run.analysisRunId === run.analysisRunId ? 'selected' : ''}`}
                          style={{ textAlign: 'left', padding: '0.6rem' }}
                        >
                          <strong style={{ fontSize: '0.9rem' }}>{run.analyzerVersion} · {run.status}</strong>
                          <small>{run.observationCount} observations · {new Date(run.createdAt).toLocaleString()}</small>
                          <small style={{ fontSize: '0.7rem' }}>{run.status === 'FAILED' ? `${run.failureCode}: ${run.failureMessage}` : run.status === 'RUNNING' ? 'Running...' : run.status === 'QUEUED' ? 'Queued...' : 'Complete'}</small>
                        </button>
                      ))}
                    </div>
                  </div>
                )}

                {selectedAnalysis && (
                  <div className="real-job-card" style={{ background: 'var(--panel-bg, #f9fafb)' }}>
                    <div className="real-job-head">
                      <div>
                        <span className="eyebrow">Analysis {selectedAnalysis.run.status}</span>
                        <strong>{selectedAnalysis.run.analyzerVersion} · {selectedAnalysis.run.observationCount} facts</strong>
                      </div>
                      <span className={`real-job-state ${selectedAnalysis.run.status.toLowerCase()}`}><span />{selectedAnalysis.run.status}</span>
                    </div>

                    <div style={{ padding: '0.8rem', fontSize: '0.85rem', lineHeight: '1.5' }}>
                      <div><strong>Snapshot:</strong> {selectedAnalysis.run.shortSha} · {selectedSnapshot.snapshot.fullName}</div>
                      <div><strong>Commit:</strong> <code style={{ fontSize: '0.75rem' }}>{selectedAnalysis.run.commitSha?.substring(0, 12)}</code></div>
                      <div><strong>Analyzer:</strong> {selectedAnalysis.run.analyzerVersion} · DETERMINISTIC only</div>
                      <div><strong>Observations:</strong> {selectedAnalysis.summary.totalObservations} total</div>
                      <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', marginTop: '0.5rem' }}>
                        <span style={{ background: '#dbeafe', padding: '0.2rem 0.5rem', borderRadius: '12px', fontSize: '0.75rem' }}><Layers size={12} style={{ display: 'inline' }} /> {selectedAnalysis.summary.languageCount} lang</span>
                        <span style={{ background: '#fef3c7', padding: '0.2rem 0.5rem', borderRadius: '12px', fontSize: '0.75rem' }}><Boxes size={12} style={{ display: 'inline' }} /> {selectedAnalysis.summary.frameworkCount} fw</span>
                        <span style={{ background: '#dcfce7', padding: '0.2rem 0.5rem', borderRadius: '12px', fontSize: '0.75rem' }}><Code size={12} style={{ display: 'inline' }} /> {selectedAnalysis.summary.dependencyCount} deps</span>
                        <span style={{ background: '#ede9fe', padding: '0.2rem 0.5rem', borderRadius: '12px', fontSize: '0.75rem' }}><Link2 size={12} style={{ display: 'inline' }} /> {selectedAnalysis.summary.endpointCount} endpoints</span>
                        <span style={{ background: '#fce7f3', padding: '0.2rem 0.5rem', borderRadius: '12px', fontSize: '0.75rem' }}><Database size={12} style={{ display: 'inline' }} /> {selectedAnalysis.summary.databaseCount} db</span>
                        <span style={{ background: '#fee2e2', padding: '0.2rem 0.5rem', borderRadius: '12px', fontSize: '0.75rem' }}><Lock size={12} style={{ display: 'inline' }} /> {selectedAnalysis.summary.securityCount} sec</span>
                        <span style={{ background: '#ccfbf1', padding: '0.2rem 0.5rem', borderRadius: '12px', fontSize: '0.75rem' }}><FlaskConical size={12} style={{ display: 'inline' }} /> {selectedAnalysis.summary.testCount} test</span>
                      </div>
                      <div><strong>Languages:</strong> {selectedAnalysis.summary.languages.join(', ') || 'none'}</div>
                      <div><strong>Frameworks:</strong> {selectedAnalysis.summary.frameworks.join(', ') || 'none'}</div>
                    </div>

                    {/* Grouped findings */}
                    <div style={{ padding: '0.8rem' }}>
                      <details open style={{ marginBottom: '0.8rem' }}>
                        <summary style={{ cursor: 'pointer', fontWeight: 600, fontSize: '0.9rem' }}><Code size={14} style={{ display: 'inline', marginRight: '4px' }} /> Languages & File Counts</summary>
                        <div style={{ marginTop: '0.5rem', fontSize: '0.8rem' }}>
                          {selectedAnalysis.observations.filter(o => o.category === 'LANGUAGE').map(o => (
                            <div key={o.observationId} style={{ padding: '0.2rem 0', borderBottom: '1px solid #eee' }}>
                              <code>{o.observationType}</code> <strong>{o.factKey}</strong> = {o.factValue} <small>({o.detector} @ {o.sourcePath || 'manifest'})</small>
                            </div>
                          ))}
                        </div>
                      </details>

                      <details style={{ marginBottom: '0.8rem' }}>
                        <summary style={{ cursor: 'pointer', fontWeight: 600, fontSize: '0.9rem' }}><Boxes size={14} style={{ display: 'inline', marginRight: '4px' }} /> Frameworks & Dependencies</summary>
                        <div style={{ marginTop: '0.5rem', fontSize: '0.8rem' }}>
                          {selectedAnalysis.observations.filter(o => o.category === 'FRAMEWORK' || o.category === 'DEPENDENCY').map(o => (
                            <div key={o.observationId} style={{ padding: '0.2rem 0', borderBottom: '1px solid #eee' }}>
                              <strong>{o.factKey}</strong> {o.factValue ? `(${o.factValue})` : ''} <small>from {o.sourcePath} · {o.framework || o.language} · {o.symbol}</small>
                              {o.startLine && <small> · lines {o.startLine}-{o.endLine}</small>}
                            </div>
                          ))}
                        </div>
                      </details>

                      <details style={{ marginBottom: '0.8rem' }}>
                        <summary style={{ cursor: 'pointer', fontWeight: 600, fontSize: '0.9rem' }}><Link2 size={14} style={{ display: 'inline', marginRight: '4px' }} /> API Endpoints (HTTP_ENDPOINT)</summary>
                        <div style={{ marginTop: '0.5rem', fontSize: '0.8rem' }}>
                          {selectedAnalysis.observations.filter(o => o.observationType === 'HTTP_ENDPOINT').map(o => (
                            <div key={o.observationId} style={{ padding: '0.3rem 0', borderBottom: '1px solid #eee', fontFamily: 'monospace' }}>
                              <span style={{ background: '#ede9fe', padding: '0.1rem 0.3rem', borderRadius: '4px', fontWeight: 700 }}>{o.factKey}</span> {o.factValue}
                              <div style={{ fontSize: '0.7rem', color: '#666' }}>{o.symbol} @ {o.sourcePath} lines {o.startLine}-{o.endLine} · hash {o.sourceHash?.substring(0, 8)}</div>
                            </div>
                          ))}
                          {selectedAnalysis.observations.filter(o => o.observationType === 'HTTP_ENDPOINT').length === 0 && <small>No endpoints detected</small>}
                        </div>
                      </details>

                      <details style={{ marginBottom: '0.8rem' }}>
                        <summary style={{ cursor: 'pointer', fontWeight: 600, fontSize: '0.9rem' }}><Database size={14} style={{ display: 'inline', marginRight: '4px' }} /> Database Signals</summary>
                        <div style={{ marginTop: '0.5rem', fontSize: '0.8rem' }}>
                          {selectedAnalysis.observations.filter(o => o.category === 'DATABASE').map(o => (
                            <div key={o.observationId} style={{ padding: '0.2rem 0', borderBottom: '1px solid #eee' }}>
                              <code>{o.observationType}</code> <strong>{o.factKey}</strong> = {o.factValue} <small>@ {o.sourcePath} · {o.symbol} · lines {o.startLine}-{o.endLine}</small>
                            </div>
                          ))}
                        </div>
                      </details>

                      <details style={{ marginBottom: '0.8rem' }}>
                        <summary style={{ cursor: 'pointer', fontWeight: 600, fontSize: '0.9rem' }}><Lock size={14} style={{ display: 'inline', marginRight: '4px' }} /> Security Signals</summary>
                        <div style={{ marginTop: '0.5rem', fontSize: '0.8rem' }}>
                          {selectedAnalysis.observations.filter(o => o.category === 'SECURITY').map(o => (
                            <div key={o.observationId} style={{ padding: '0.2rem 0', borderBottom: '1px solid #eee' }}>
                              <strong>{o.factKey}</strong> found @ {o.sourcePath} <small>({o.observationType} · {o.symbol})</small>
                            </div>
                          ))}
                          {selectedAnalysis.observations.filter(o => o.category === 'SECURITY').length === 0 && <small>No security configuration detected (negative test passes)</small>}
                        </div>
                      </details>

                      <details style={{ marginBottom: '0.8rem' }}>
                        <summary style={{ cursor: 'pointer', fontWeight: 600, fontSize: '0.9rem' }}><FlaskConical size={14} style={{ display: 'inline', marginRight: '4px' }} /> Testing Signals</summary>
                        <div style={{ marginTop: '0.5rem', fontSize: '0.8rem' }}>
                          {selectedAnalysis.observations.filter(o => o.category === 'TESTING').map(o => (
                            <div key={o.observationId} style={{ padding: '0.2rem 0', borderBottom: '1px solid #eee' }}>
                              <strong>{o.factKey}</strong> {o.factValue} <small>@ {o.sourcePath}</small>
                            </div>
                          ))}
                        </div>
                      </details>

                      <details style={{ marginBottom: '0.8rem' }}>
                        <summary style={{ cursor: 'pointer', fontWeight: 600, fontSize: '0.9rem' }}><Boxes size={14} style={{ display: 'inline', marginRight: '4px' }} /> DevOps / Docker / CI</summary>
                        <div style={{ marginTop: '0.5rem', fontSize: '0.8rem' }}>
                          {selectedAnalysis.observations.filter(o => o.category === 'DEVOPS').map(o => (
                            <div key={o.observationId} style={{ padding: '0.2rem 0', borderBottom: '1px solid #eee' }}>
                              <strong>{o.observationType}</strong> {o.factKey} = {o.factValue} <small>@ {o.sourcePath}</small>
                            </div>
                          ))}
                        </div>
                      </details>

                      {selectedAnalysis.fileErrors.length > 0 && (
                        <details style={{ marginBottom: '0.8rem' }}>
                          <summary style={{ cursor: 'pointer', fontWeight: 600, fontSize: '0.9rem', color: '#d97706' }}><AlertTriangle size={14} style={{ display: 'inline', marginRight: '4px' }} /> File Errors ({selectedAnalysis.fileErrors.length}) — Failure Isolation</summary>
                          <div style={{ marginTop: '0.5rem', fontSize: '0.8rem' }}>
                            {selectedAnalysis.fileErrors.map(e => (
                              <div key={e.errorId} style={{ padding: '0.2rem 0', borderBottom: '1px solid #eee', color: '#92400e' }}>
                                {e.sourcePath}: {e.errorCode} - {e.errorMessage} <small>({e.detector})</small>
                              </div>
                            ))}
                          </div>
                        </details>
                      )}

                      <details>
                        <summary style={{ cursor: 'pointer', fontWeight: 600, fontSize: '0.9rem' }}>All Observations ({selectedAnalysis.observations.length}) — Deterministic Only</summary>
                        <div style={{ maxHeight: '400px', overflowY: 'auto', marginTop: '0.5rem', border: '1px solid var(--border)', borderRadius: '4px' }}>
                          <table style={{ width: '100%', fontSize: '0.7rem', borderCollapse: 'collapse' }}>
                            <thead>
                              <tr style={{ background: '#f3f4f6', position: 'sticky', top: 0 }}>
                                <th style={{ textAlign: 'left', padding: '0.3rem' }}>Type</th>
                                <th style={{ textAlign: 'left', padding: '0.3rem' }}>Category</th>
                                <th style={{ textAlign: 'left', padding: '0.3rem' }}>Fact</th>
                                <th style={{ textAlign: 'left', padding: '0.3rem' }}>Source</th>
                                <th style={{ textAlign: 'left', padding: '0.3rem' }}>Symbol</th>
                              </tr>
                            </thead>
                            <tbody>
                              {selectedAnalysis.observations.map(o => (
                                <tr key={o.observationId} style={{ borderTop: '1px solid #eee' }}>
                                  <td style={{ padding: '0.2rem' }}>{o.observationType}</td>
                                  <td style={{ padding: '0.2rem' }}>{o.category}</td>
                                  <td style={{ padding: '0.2rem' }}><strong>{o.factKey}</strong>={o.factValue?.substring(0, 50)}</td>
                                  <td style={{ padding: '0.2rem', fontFamily: 'monospace' }}>{o.sourcePath}:{o.startLine}</td>
                                  <td style={{ padding: '0.2rem', fontFamily: 'monospace' }}>{o.symbol?.substring(0, 40)}</td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      </details>
                    </div>

                    <div style={{ padding: '0.5rem 0.8rem', fontSize: '0.75rem', color: 'var(--muted)' }}>
                      <ShieldCheck size={12} style={{ display: 'inline', marginRight: '4px' }} />
                      Every observation traceable to source: snapshotId {selectedAnalysis.run.snapshotId} · commit {selectedAnalysis.run.commitSha?.substring(0, 12)} · analyzer {selectedAnalysis.run.analyzerVersion} · origin DETERMINISTIC
                    </div>
                  </div>
                )}

                {analysisRuns.length === 0 && (
                  <div className="inline-empty"><Search size={16} /> No deterministic analysis yet. Click Analyze Snapshot to run language detection, manifest parsing, Java AST, endpoint extraction, DB/security/test/devops signals.</div>
                )}
              </div>
            )}

            {/* Legacy Analysis Job */}
            <div style={{ marginTop: '1.5rem', borderTop: '1px solid var(--border)', paddingTop: '1rem' }}>
              <SectionHeading eyebrow="Legacy" title="Old Analysis Flow" description="Previous evidence mapping (Checkpoint B/C). Deterministic analyzer is the new source of truth." />
              {job && <div className="real-job-card"><div className="real-job-head"><div><span className="eyebrow">Analysis job</span><strong>{job.state === 'FAILED' ? 'Analysis failed' : job.state === 'COMPLETED' ? 'Evidence ready' : job.stage}</strong></div><span className={`real-job-state ${job.state.toLowerCase()}`}><span />{job.state}</span></div><div className="progress-track"><span style={{ width: `${job.progress}%` }} /></div><div className="real-job-foot"><span>{job.progress}% · {job.stage}</span>{job.state === 'FAILED' && <button className="text-action" onClick={() => void retry()}><RefreshCw size={13} /> Retry</button>}</div>{job.errorMessage && <div className="real-job-error"><AlertTriangle size={14} />{job.errorMessage}</div>}</div>}
              {(!job || job.state === 'FAILED') && connectionIssue ? <div className="inline-empty"><ShieldCheck size={16} /> Active GitHub repository access is required before analysis.</div> : !job || job.state === 'FAILED' ? <button className="button button-primary button-full" onClick={() => void startAnalysis()} disabled={working}>{working ? <><LoaderCircle className="spin" size={15} /> Starting…</> : <><ShieldCheck size={15} /> Analyze current branch commit (legacy)</>}</button> : job.state === 'COMPLETED' ? <Link className="button button-primary button-full" to={`/app/evidence?projectId=${selectedProject.projectId}`}><Check size={15} /> Review persisted evidence <ArrowRight size={14} /></Link> : <div className="real-analysis-wait"><LoaderCircle className="spin" size={15} /> Backend worker is processing.</div>}
            </div>
          </> : <div className="empty-state"><Github size={22} /><h3>Select a repository</h3><p>The project and snapshot are created only after an authorized repository is selected on the GitHub page.</p></div>}
        </section>
      </section>
    </>}
  </div>;
}
