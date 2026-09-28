export type UserRole = 'candidate' | 'recruiter';
export type AnalysisStatus = 'NOT_CONNECTED' | 'CONNECTED' | 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
export type EvidenceStrength = 'WEAK' | 'MODERATE' | 'STRONG' | 'DIRECT';
export type SkillStatus = 'SELF_CLAIM' | 'EVIDENCE_FOUND' | 'PARTIAL' | 'VERIFIED' | 'STALE' | 'EXPIRED' | 'NOT_VERIFIED' | 'DISPUTED';
export type FreshnessState = 'CURRENT' | 'AGING' | 'STALE' | 'EXPIRED' | 'NOT_APPLICABLE';
export type RequirementKind = 'REQUIRED' | 'PREFERRED';
export type ProofVisibility = 'PRIVATE' | 'RECRUITER_SHARED' | 'PUBLIC_SUMMARY' | 'PUBLIC';

export interface CandidateProfile {
  name: string;
  initials: string;
  headline: string;
  location: string;
  institution: string;
  graduation: string;
  verificationProgress: number;
}

export interface Repository {
  id: string;
  name: string;
  fullName: string;
  description: string;
  branch: string;
  commitSha: string;
  commitLabel: string;
  visibility: 'PUBLIC' | 'PRIVATE';
  languages: string[];
  frameworks: string[];
  updatedAt: string;
  isFixture: boolean;
}

export interface Project {
  id: string;
  name: string;
  kind: string;
  summary: string;
  repository: Repository;
  analysisStatus: AnalysisStatus;
  analyzedAt?: string;
  fileCount: number;
  testCount: number;
  analysisStages: string[];
  completedStages: number;
}

export interface Evidence {
  id: string;
  skillId: string;
  skillName: string;
  sourceType: 'REPOSITORY' | 'DEPENDENCY' | 'STATIC_ANALYSIS' | 'PROJECT_DEFENSE' | 'PRACTICAL_VERIFICATION';
  sourceLabel: string;
  location: string;
  observation: string;
  strength: EvidenceStrength;
  status: 'OBSERVED' | 'VERIFIED' | 'DISPUTED';
  observedAt: string;
  snapshot: string;
  visibility: ProofVisibility;
  projectId: string;
  independentSignal?: string;
}

export interface Skill {
  id: string;
  name: string;
  category: string;
  status: SkillStatus;
  freshness: FreshnessState;
  lastVerifiedAt?: string;
  latestEvidenceAt: string;
  evidenceCount: number;
  summary: string;
  methods: string[];
  relatedProjects: string[];
}

export interface ExamQuestion {
  id: string;
  category: 'CODE_LOCATION' | 'ARCHITECTURE' | 'SECURITY' | 'DEBUGGING' | 'DESIGN_DECISION';
  prompt: string;
  context: string;
  expectedSignals: string[];
}

export interface ExamAnswer {
  questionId: string;
  text: string;
  result: 'PENDING' | 'MEETS_BAR' | 'NEEDS_DEPTH';
  feedback?: string;
}

export interface Examination {
  id: string;
  projectId: string;
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED';
  currentIndex: number;
  questions: ExamQuestion[];
  answers: ExamAnswer[];
  result?: 'PASSED' | 'PARTIAL' | 'NEEDS_REVIEW';
  completedAt?: string;
}

export interface PracticalChallenge {
  id: string;
  skillId: string;
  title: string;
  brief: string;
  acceptanceCriteria: string[];
  starterCode: string;
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'PASSED' | 'NEEDS_CHANGES';
  submission?: string;
  feedback?: string;
}

export interface JobRequirement {
  id: string;
  skillId: string;
  skillName: string;
  kind: RequirementKind;
  status: 'VERIFIED' | 'PARTIAL' | 'EVIDENCE_FOUND' | 'MISSING' | 'STALE';
  proofNote: string;
}

export interface Job {
  id: string;
  title: string;
  company: string;
  location: string;
  createdAt: string;
  sourceDescription: string;
  requirements: JobRequirement[];
}

export interface DemoState {
  signedIn: boolean;
  role: UserRole;
  candidate: CandidateProfile;
  githubConnected: boolean;
  project: Project;
  evidence: Evidence[];
  skills: Skill[];
  examination: Examination;
  challenge: PracticalChallenge;
  job: Job;
  analysisNotice?: string;
}
