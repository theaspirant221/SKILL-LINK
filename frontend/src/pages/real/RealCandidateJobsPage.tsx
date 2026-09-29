import { AlertTriangle, BriefcaseBusiness, CheckCircle2, FileCheck2, LoaderCircle, LockKeyhole, RefreshCw, Send, ShieldCheck } from 'lucide-react';
import { useEffect, useState } from 'react';
import { api, type Application, type CandidateProofContract, type RecruiterJob, ApiError } from '../../api/client';
import { DemoNote, SectionHeading } from '../../components/ui';

const applicationStatusCopy: Record<string, { label: string; cls: string }> = {
  APPLIED: { label: 'Applied', cls: 'status-info' },
  REVIEWING: { label: 'In review', cls: 'status-warning' },
  SHORTLISTED: { label: 'Shortlisted', cls: 'status-success' },
  REJECTED: { label: 'Not selected', cls: 'status-danger' },
  WITHDRAWN: { label: 'Withdrawn', cls: 'status-neutral' },
};

const outcomeCopy: Record<string, { label: string; cls: string }> = {
  SUPPORTED: { label: 'Supported', cls: 'status-success' },
  PARTIAL: { label: 'Partial', cls: 'status-warning' },
  MISSING: { label: 'Missing', cls: 'status-danger' },
  DISPUTED: { label: 'Disputed', cls: 'status-purple' },
  UNREVIEWED: { label: 'Unreviewed', cls: 'status-neutral' },
};

function Pill({ label, cls }: { label: string; cls: string }) {
  return <span className={`status-pill status-pill-small ${cls}`}>{label}</span>;
}

export default function RealCandidateJobsPage() {
  const [jobs, setJobs] = useState<RecruiterJob[]>([]);
  const [applications, setApplications] = useState<Application[]>([]);
  const [contracts, setContracts] = useState<CandidateProofContract[] | null>(null);
  const [contractsNotice, setContractsNotice] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [applyingJobId, setApplyingJobId] = useState<string | null>(null);
  const [note, setNote] = useState('');

  async function loadApplications() {
    try { setApplications(await api.candidate.applications()); } catch { /* surfaced by the page-level load; keep the previous list on refresh */ }
  }

  async function loadContracts() {
    try { setContracts(await api.candidate.proofContracts()); setContractsNotice(null); } catch (reason) { setContracts([]); setContractsNotice(reason instanceof ApiError ? reason.message : 'Shared Proof Contracts could not be loaded.'); }
  }

  async function load() {
    setLoading(true); setError(null);
    try {
      const [nextJobs, nextApplications] = await Promise.all([api.candidate.openJobs(), api.candidate.applications()]);
      setJobs(nextJobs); setApplications(nextApplications);
      await loadContracts();
    } catch (reason) {
      setError(reason instanceof ApiError ? reason.message : 'Open jobs could not be loaded.');
    } finally { setLoading(false); }
  }

  useEffect(() => { void load(); }, []);

  async function apply(job: RecruiterJob) {
    setWorking(true); setError(null); setMessage(null);
    try {
      const application = await api.candidate.apply(job.jobId, note);
      setApplications((current) => [application, ...current.filter((item) => item.applicationId !== application.applicationId)]);
      setApplyingJobId(null); setNote('');
      setMessage(`Application sent for ${job.title}. The recruiter sees your policy-backed proof, never raw private source.`);
    } catch (reason) {
      setError(reason instanceof ApiError ? reason.message : 'The application could not be submitted.');
      await loadApplications();
    } finally { setWorking(false); }
  }

  const appliedJobIds = new Set(applications.map((application) => application.jobId));

  return <div className="page-stack">
    <DemoNote>Applications are persisted against your candidate account. Shared Proof Contracts show exactly what a recruiter can inspect; reviewer notes stay private to the recruiter.</DemoNote>
    <div className="page-header">
      <div>
        <div className="eyebrow">Candidate job discovery</div>
        <h1>Jobs</h1>
        <p>Apply with proof instead of claims. Requirements come from a controlled skill taxonomy, and your repository source stays private unless you explicitly share it.</p>
      </div>
      <div className="page-header-actions"><button className="button button-ghost" onClick={() => void load()}><RefreshCw size={15} /> Refresh</button></div>
    </div>
    {error && <div className="notice-bar error"><AlertTriangle size={16} />{error}</div>}
    {message && <div className="notice-bar"><CheckCircle2 size={16} />{message}</div>}
    {loading ? <div className="panel loading-block"><LoaderCircle className="spin" size={18} /> Loading open roles…</div> : <div className="candidate-jobs-layout">
      <section className="panel">
        <SectionHeading eyebrow={`${jobs.length} open role${jobs.length === 1 ? '' : 's'}`} title="Open roles" description="Each role lists the normalized taxonomy requirements a recruiter will check proof against." />
        {jobs.length === 0 ? <div className="inline-empty"><BriefcaseBusiness size={17} /> No open roles yet. Recruiter jobs appear here as soon as they are published.</div> : <div className="candidate-job-list">
          {jobs.map((job) => <article className={`candidate-job-card ${appliedJobIds.has(job.jobId) ? 'applied' : ''}`} key={job.jobId}>
            <div className="candidate-job-head">
              <div>
                <strong>{job.title}</strong>
                <small>{job.company}{job.location ? ` · ${job.location}` : ''} · posted {new Date(job.createdAt).toLocaleDateString()}</small>
              </div>
              {appliedJobIds.has(job.jobId)
                ? <span className="inline-status success"><CheckCircle2 size={13} /> Applied</span>
                : <button className="button button-primary button-small" onClick={() => { setApplyingJobId(applyingJobId === job.jobId ? null : job.jobId); setNote(''); }} disabled={working}>Apply <Send size={13} /></button>}
            </div>
            <div className="source-chip-row">{job.requirements.map((requirement) => <span className={`source-chip ${requirement.kind === 'REQUIRED' ? 'source-chip-blue' : ''}`} key={requirement.requirementId}><span className="source-chip-dot" />{requirement.skillName}</span>)}</div>
            {applyingJobId === job.jobId && !appliedJobIds.has(job.jobId) && <div className="candidate-apply-note">
              <label>Optional note to the recruiter<textarea value={note} onChange={(event) => setNote(event.target.value)} rows={3} placeholder="Anything context-relevant. Proof is attached automatically from your verified skills." /></label>
              <div className="candidate-apply-actions">
                <span><ShieldCheck size={13} /> No source code is shared. Only policy-backed summaries and evidence you marked recruiter-visible.</span>
                <div>
                  <button className="button button-ghost button-small" onClick={() => { setApplyingJobId(null); setNote(''); }} disabled={working}>Cancel</button>
                  <button className="button button-primary button-small" onClick={() => void apply(job)} disabled={working}>{working ? <LoaderCircle className="spin" size={13} /> : <Send size={13} />} Submit application</button>
                </div>
              </div>
            </div>}
          </article>)}
        </div>}
      </section>
      <div className="candidate-jobs-side">
        <section className="panel">
          <SectionHeading eyebrow="Recruiter-owned workflow" title="Your applications" description="Status changes are made by the recruiter. SkillLink never auto-scores or auto-rejects." />
          {applications.length === 0 ? <div className="inline-empty"><FileCheck2 size={17} /> You have not applied to a role yet.</div> : <div className="candidate-app-list">
            {applications.map((application) => {
              const status = applicationStatusCopy[application.status] ?? { label: application.status.replaceAll('_', ' '), cls: 'status-neutral' };
              return <div className="candidate-app-row" key={application.applicationId}>
                <div className="candidate-app-row-head">
                  <strong>{application.jobTitle}</strong>
                  <Pill label={status.label} cls={status.cls} />
                </div>
                <small>Applied {new Date(application.createdAt).toLocaleDateString()}</small>
                {application.note && <p>“{application.note}”</p>}
              </div>;
            })}
          </div>}
        </section>
        <section className="panel">
          <SectionHeading eyebrow="What recruiters can inspect" title="Shared Proof Contracts" description="Requirement-by-requirement proof attached to your applications. Reviewer notes are never included here." />
          {contracts === null ? <div className="inline-empty"><LoaderCircle className="spin" size={16} /> Loading shared contracts…</div>
            : contractsNotice ? <div className="inline-empty"><LockKeyhole size={16} /> {contractsNotice}</div>
            : contracts.length === 0 ? <div className="inline-empty"><LockKeyhole size={16} /> No Proof Contract has been generated for your applications yet.</div>
            : <div className="candidate-contract-list">{contracts.map((contract) => <article className="candidate-contract-card" key={contract.contractId}>
              <div className="candidate-contract-head">
                <div>
                  <strong>{contract.jobTitle}</strong>
                  <small>{contract.company} · contract v{contract.version} · {contract.status.replaceAll('_', ' ').toLowerCase()} · {new Date(contract.createdAt).toLocaleDateString()}</small>
                </div>
                <ShieldCheck size={17} />
              </div>
              <div className="candidate-contract-rows">{contract.requirements.map((requirement) => {
                const outcome = outcomeCopy[requirement.outcome] ?? { label: requirement.outcome.replaceAll('_', ' '), cls: 'status-neutral' };
                return <div className="candidate-contract-row" key={requirement.contractRequirementId}>
                  <div>
                    <strong>{requirement.skillName}</strong>
                    <small>{requirement.requiredKind} · {requirement.visibleEvidenceCount} recruiter-visible evidence item{requirement.visibleEvidenceCount === 1 ? '' : 's'}</small>
                    <p>{requirement.proofSummary}</p>
                  </div>
                  <Pill label={outcome.label} cls={outcome.cls} />
                </div>;
              })}</div>
            </article>)}</div>}
        </section>
      </div>
    </div>}
  </div>;
}
