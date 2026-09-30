export type ApiUser = { id: string; email: string; displayName: string; role: 'CANDIDATE' | 'RECRUITER' };
export type AuthResponse = { accessToken: string; accessTokenExpiresInSeconds: number; user: ApiUser };
export type GithubInstallation = { installationId: number; accountId: number; accountLogin: string; accountType: string; repositorySelection: string; status: string };
export type GithubConnectionStatus = 'DISCONNECTED' | 'CONNECTED' | 'REAUTH_REQUIRED' | 'INSTALLATION_MISSING' | 'INSTALLATION_REMOVED' | 'TOKEN_INVALID';
export type GithubStatus = { status: GithubConnectionStatus; connected: boolean; githubLogin: string | null; githubUserId: number | null; scopes: string | null; connectedAt: string | null; lastValidatedAt: string | null; installation: GithubInstallation | null };
export type GithubRepository = { id: string; name: string; fullName: string; owner: string; privateRepository: boolean; visibility: 'PUBLIC' | 'PRIVATE'; defaultBranch: string; primaryLanguage: string | null; updatedAt: string | null; sizeKb: number; description: string | null; accountLogin: string | null };
export type ApiProject = { projectId: string; repositoryId: string; name: string; fullName: string; branch: string; owner: string; primaryLanguage: string | null; visibility: string };
export type AnalysisJob = { jobId: string; projectId: string; snapshotId: string | null; state: 'QUEUED' | 'FETCHING' | 'ANALYZING' | 'MAPPING' | 'COMPLETED' | 'FAILED'; progress: number; stage: string; errorCode: string | null; errorMessage: string | null; createdAt: string; startedAt: string | null; completedAt: string | null };
export type Snapshot = {
  snapshotId: string;
  candidateId: string;
  projectId: string;
  repositoryId: string;
  githubRepositoryId: string | null;
  owner: string | null;
  repositoryName: string | null;
  fullName: string | null;
  commitSha: string;
  shortSha: string;
  branchName: string | null;
  commitAuthor: string | null;
  commitMessage: string | null;
  commitTimestamp: string | null;
  snapshotCreatedAt: string | null;
  analysisVersion: string;
  filePolicyVersion: string;
  status: 'CREATED' | 'FETCHING' | 'READY' | 'FAILED';
  fileCount: number;
  includedFileCount: number;
  excludedFileCount: number;
  totalBytes: number;
  integrityHash: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
};
export type SnapshotFile = {
  path: string;
  language: string | null;
  sizeBytes: number;
  contentHash: string;
  blobSha: string | null;
  included: boolean;
  exclusionReason: string | null;
  secretRedacted: boolean;
  secretCount: number;
};
export type SnapshotDetail = {
  snapshot: Snapshot;
  files: SnapshotFile[];
  summary: {
    totalFiles: number;
    includedFiles: number;
    excludedFiles: number;
    totalBytes: number;
    secretRedactedFiles: number;
    totalSecrets: number;
    integrityHash: string | null;
  };
};
export type AnalysisRun = {
  analysisRunId: string;
  snapshotId: string;
  candidateId: string;
  projectId: string;
  repositoryId: string;
  analyzerVersion: string;
  status: 'QUEUED' | 'RUNNING' | 'COMPLETE' | 'FAILED';
  startedAt: string | null;
  completedAt: string | null;
  failureCode: string | null;
  failureMessage: string | null;
  observationCount: number;
  createdAt: string;
  updatedAt: string;
  commitSha: string | null;
  shortSha: string | null;
};
export type Observation = {
  observationId: string;
  analysisRunId: string;
  snapshotId: string;
  observationType: string;
  category: string;
  factKey: string;
  factValue: string | null;
  language: string | null;
  framework: string | null;
  sourcePath: string | null;
  startLine: number | null;
  endLine: number | null;
  symbol: string | null;
  sourceHash: string | null;
  detector: string;
  detectorVersion: string;
  confidence: string;
  origin: string;
  createdAt: string;
};
export type FileError = {
  errorId: string;
  analysisRunId: string;
  snapshotId: string;
  sourcePath: string;
  errorCode: string;
  errorMessage: string;
  detector: string;
  createdAt: string;
};
export type AnalysisDetail = {
  run: AnalysisRun;
  observations: Observation[];
  fileErrors: FileError[];
  summary: {
    totalObservations: number;
    languageCount: number;
    frameworkCount: number;
    dependencyCount: number;
    endpointCount: number;
    databaseCount: number;
    securityCount: number;
    testCount: number;
    devopsCount: number;
    languages: string[];
    frameworks: string[];
  };
};
export type ApiEvidence = { evidenceId: string; skillId: string; skillName: string; projectName: string; repositoryFullName: string; snapshotId: string; commitSha: string; sourceType: string; sourceLocation: string; observation: string; evidenceStrength: 'WEAK' | 'MODERATE' | 'STRONG' | 'DIRECT'; verificationMethod: string; status: 'OBSERVED' | 'VERIFIED' | 'DISPUTED' | 'REDACTED'; visibility: string; observedAt: string; sourceHash: string | null; independentSignal: string | null };
export type ApiSkill = { skillId: string; key: string; name: string; category: string; status: string; freshnessState: string; lastVerifiedAt: string | null; latestEvidenceAt: string | null; evidenceCount: number };
export type ExaminationQuestion = { questionId: string; sequence: number; category: string; prompt: string; contextReferences: string[]; answerStatus: string | null; feedback: string | null };
export type Examination = { examinationId: string; projectId: string; status: string; result: string | null; policyVersion: string; promptVersion: string; startedAt: string | null; completedAt: string | null; questions: ExaminationQuestion[] };
export type Verification = { verificationId: string; skillId: string; skillName: string; status: string; policyKey: string; policyVersion: string; explanation: string; evidenceCount: number; defensePassed: boolean; practicalPassed: boolean; evaluatedAt: string };
export type Challenge = { challengeId: string; projectId: string; skillId: string; skillName: string; title: string; brief: string; acceptanceCriteria: string[]; executionMode: string; policyVersion: string; latestSubmissionId: string | null; latestSubmissionStatus: string | null; feedback: string | null; submittedAt: string | null };
export type JobRequirement = { requirementId: string; skillId: string; skillName: string; kind: string; sourcePhrase: string | null; recruiterEdited: boolean };
export type RecruiterJob = { jobId: string; title: string; company: string; location: string | null; status: string; originalDescription: string; createdAt: string; requirements: JobRequirement[] };
export type Application = { applicationId: string; jobId: string; candidateId: string; candidateDisplayName: string; jobTitle: string; status: string; note: string | null; createdAt: string };
export type ContractRequirement = { contractRequirementId: string; jobRequirementId: string; skillId: string; skillName: string; requiredKind: string; outcome: string; proofSummary: string; visibleEvidenceCount: number; verificationResultId: string | null; reviewerNote: string | null; reviewedAt: string | null };
export type ProofContract = { contractId: string; jobId: string; candidateId: string; version: number; status: string; createdAt: string; requirements: ContractRequirement[] };
export type CandidateContractRequirement = { contractRequirementId: string; skillId: string; skillName: string; requiredKind: string; outcome: string; proofSummary: string; visibleEvidenceCount: number; reviewedAt: string | null };
export type CandidateProofContract = { contractId: string; jobId: string; jobTitle: string; company: string; version: number; status: string; createdAt: string; requirements: CandidateContractRequirement[] };
export type PassportItem = { skillId: string; skillName: string; category: string; status: string; policyVersion: string | null; displaySummary: string; verifiedAt: string | null };
export type Passport = { passportId: string; publicIdentifier: string; candidateId: string; visibility: string; issuedAt: string; expiresAt: string | null; revoked: boolean; items: PassportItem[] };
export type PublicPassport = { publicIdentifier: string; candidateDisplayName: string; visibility: string; issuedAt: string; expiresAt: string | null; items: PassportItem[] };

const API_BASE = (import.meta.env.VITE_API_BASE_URL || '/api/v1').replace(/\/$/, '');
const ACCESS_TOKEN_KEY = 'skilllink_access_token';

export class ApiError extends Error {
  code: string;
  status: number;
  details: unknown;
  constructor(code: string, message: string, status: number, details?: unknown) { super(message); this.name = 'ApiError'; this.code = code; this.status = status; this.details = details; }
}

function readToken() { try { return sessionStorage.getItem(ACCESS_TOKEN_KEY); } catch { return null; } }
export function setAccessToken(token: string | null) { try { if (token) sessionStorage.setItem(ACCESS_TOKEN_KEY, token); else sessionStorage.removeItem(ACCESS_TOKEN_KEY); } catch { /* storage can be disabled; the request will simply require a new login */ } }
export function getAccessToken() { return readToken(); }

async function refreshAccessToken(): Promise<string | null> {
  const response = await fetch(`${API_BASE}/auth/refresh`, { method: 'POST', credentials: 'include', headers: { Accept: 'application/json' } });
  if (!response.ok) { setAccessToken(null); return null; }
  const data = await response.json() as AuthResponse;
  setAccessToken(data.accessToken);
  return data.accessToken;
}

async function request<T>(path: string, init: RequestInit = {}, allowRefresh = true): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  const token = readToken();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  const response = await fetch(`${API_BASE}${path}`, { ...init, headers, credentials: 'include' });
  const refreshableAuthPath = path === '/auth/me';
  if (response.status === 401 && allowRefresh && (refreshableAuthPath || !path.startsWith('/auth/'))) {
    const refreshed = await refreshAccessToken();
    if (refreshed) return request<T>(path, init, false);
  }
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  let payload: any = undefined;
  try { payload = text ? JSON.parse(text) : undefined; } catch { payload = undefined; }
  if (!response.ok) throw new ApiError(payload?.code || 'API_REQUEST_FAILED', payload?.message || `Request failed with status ${response.status}.`, response.status, payload?.details);
  return payload as T;
}

export const api = {
  auth: {
    register: (body: { email: string; password: string; displayName: string }) => request<AuthResponse>('/auth/register', { method: 'POST', body: JSON.stringify(body) }).then((data) => { setAccessToken(data.accessToken); return data; }),
    login: (body: { email: string; password: string }) => request<AuthResponse>('/auth/login', { method: 'POST', body: JSON.stringify(body) }).then((data) => { setAccessToken(data.accessToken); return data; }),
    me: () => request<ApiUser>('/auth/me'),
    logout: () => request<{ message: string }>('/auth/logout', { method: 'POST' }).finally(() => setAccessToken(null)),
  },
  github: {
    connectUrl: () => `${API_BASE}/github/connect`,
    installUrl: () => `${API_BASE}/github/install`,
    status: () => request<GithubStatus>('/github/status'),
    repositories: () => request<GithubRepository[]>('/github/repositories'),
    select: (githubRepositoryId: string) => request<{ projectId: string; projectName: string; repositoryFullName: string; commitBranch: string }>(`/github/repositories/${encodeURIComponent(githubRepositoryId)}/select`, { method: 'POST' }),
    disconnect: () => request<void>('/github/connection', { method: 'DELETE' }),
  },
  projects: {
    list: () => request<ApiProject[]>('/projects'),
    get: (projectId: string) => request<ApiProject>(`/projects/${projectId}`),
    startAnalysis: (projectId: string) => request<AnalysisJob>(`/projects/${projectId}/analysis`, { method: 'POST' }),
    analysis: (projectId: string, jobId: string) => request<AnalysisJob>(`/projects/${projectId}/analysis/${jobId}`),
    retryAnalysis: (projectId: string, jobId: string) => request<AnalysisJob>(`/projects/${projectId}/analysis/${jobId}/retry`, { method: 'POST' }),
    evidence: (projectId: string) => request<ApiEvidence[]>(`/projects/${projectId}/evidence`),
  },
  snapshots: {
    create: (projectId: string, branch?: string) =>
      request<Snapshot>(`/projects/${projectId}/snapshots`, {
        method: 'POST',
        body: JSON.stringify({ branch: branch || null }),
      }),
    list: (projectId: string) => request<Snapshot[]>(`/projects/${projectId}/snapshots`),
    get: (projectId: string, snapshotId: string) =>
      request<SnapshotDetail>(`/projects/${projectId}/snapshots/${snapshotId}`),
    files: (projectId: string, snapshotId: string) =>
      request<SnapshotFile[]>(`/projects/${projectId}/snapshots/${snapshotId}/files`),
  },
  analysis: {
    create: (projectId: string, snapshotId: string) =>
      request<AnalysisRun>(`/projects/${projectId}/snapshots/${snapshotId}/analysis`, {
        method: 'POST',
      }),
    list: (projectId: string, snapshotId: string) =>
      request<AnalysisRun[]>(`/projects/${projectId}/snapshots/${snapshotId}/analysis`),
    get: (projectId: string, snapshotId: string, runId: string) =>
      request<AnalysisRun>(`/projects/${projectId}/snapshots/${snapshotId}/analysis/${runId}`),
    detail: (projectId: string, snapshotId: string, runId: string) =>
      request<AnalysisDetail>(`/projects/${projectId}/snapshots/${snapshotId}/analysis/${runId}/detail`),
    observations: (projectId: string, snapshotId: string, runId: string) =>
      request<Observation[]>(`/projects/${projectId}/snapshots/${snapshotId}/analysis/${runId}/observations`),
  },
  skills: { mine: () => request<ApiSkill[]>('/candidates/me/skills') },
  candidate: {
    openJobs: () => request<RecruiterJob[]>('/jobs'),
    apply: (jobId: string, note?: string) => request<Application>(`/jobs/${encodeURIComponent(jobId)}/applications`, { method: 'POST', body: JSON.stringify({ note: note?.trim() ? note.trim() : null }) }),
    applications: () => request<Application[]>('/candidates/me/applications'),
    proofContracts: () => request<CandidateProofContract[]>('/candidates/me/proof-contracts'),
  },
  evidence: {
    dispute: (evidenceId: string, reason: string) => request<void>(`/evidence/${evidenceId}/dispute`, { method: 'POST', body: JSON.stringify({ reason }) }),
    visibility: (evidenceId: string, visibility: string) => request<void>(`/evidence/${evidenceId}/visibility`, { method: 'PATCH', body: JSON.stringify({ visibility }) }),
  },
  examinations: {
    start: (projectId: string) => request<Examination>(`/projects/${projectId}/examinations`, { method: 'POST' }),
    get: (examinationId: string) => request<Examination>(`/examinations/${examinationId}`),
    answer: (examinationId: string, questionId: string, text: string) => request<Examination>(`/examinations/${examinationId}/questions/${questionId}/answers`, { method: 'POST', body: JSON.stringify({ text }) }),
  },
  challenges: {
    create: (projectId: string, skillId: string) => request<Challenge>('/candidates/me/challenges', { method: 'POST', body: JSON.stringify({ projectId, skillId }) }),
    get: (challengeId: string) => request<Challenge>(`/candidates/me/challenges/${challengeId}`),
    submit: (challengeId: string, text: string) => request<Challenge>(`/candidates/me/challenges/${challengeId}/submissions`, { method: 'POST', body: JSON.stringify({ text }) }),
  },
  verifications: { list: () => request<Verification[]>('/candidates/me/verifications'), evaluate: (skillId: string) => request<Verification>(`/candidates/me/verifications/skills/${skillId}/evaluate`, { method: 'POST' }) },
  passport: {
    get: () => request<Passport>('/candidates/me/passport'),
    issue: () => request<Passport>('/candidates/me/passport/issue', { method: 'POST' }),
    visibility: (visibility: string) => request<Passport>('/candidates/me/passport/visibility', { method: 'PATCH', body: JSON.stringify({ visibility }) }),
    public: (identifier: string) => request<PublicPassport>(`/public/passports/${encodeURIComponent(identifier)}`),
  },
  recruiter: {
    jobs: () => request<RecruiterJob[]>('/recruiter/jobs'),
    createJob: (body: { title: string; company: string; description: string; location?: string }) => request<RecruiterJob>('/recruiter/jobs', { method: 'POST', body: JSON.stringify(body) }),
    applications: (jobId: string) => request<Application[]>(`/recruiter/jobs/${jobId}/applications`),
    updateApplication: (applicationId: string, status: string) => request<Application>(`/recruiter/applications/${applicationId}`, { method: 'PATCH', body: JSON.stringify({ status }) }),
    contract: (applicationId: string) => request<ProofContract>(`/recruiter/applications/${applicationId}/proof-contract`, { method: 'POST' }),
    reviewContractRequirement: (contractId: string, requirementId: string, outcome: string, reviewerNote: string) => request<ProofContract>(`/recruiter/proof-contracts/${contractId}/requirements/${requirementId}`, { method: 'PATCH', body: JSON.stringify({ outcome, reviewerNote }) }),
  },
};

export const isDemoMode = import.meta.env.VITE_DEMO_MODE !== 'false';
export { API_BASE };
