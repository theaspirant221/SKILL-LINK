import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { challenge as fixtureChallenge, examination as fixtureExam, initialState } from './fixtures';
import type { DemoState, Job, JobRequirement, SkillStatus } from './domain';

const STORAGE_KEY = 'skilllink-demo-state-v1';

type Store = DemoState & {
  signIn: (role?: 'candidate' | 'recruiter') => void;
  signOut: () => void;
  setRole: (role: 'candidate' | 'recruiter') => void;
  connectGithubFixture: () => void;
  startAnalysis: () => void;
  resetDemo: () => void;
  startExamination: () => void;
  submitAnswer: (text: string) => { accepted: boolean; message: string };
  submitChallenge: (text: string) => { passed: boolean; message: string };
  createJob: (title: string, company: string, description: string) => void;
};

const StoreContext = createContext<Store | null>(null);

function loadState(): DemoState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return initialState;
    return JSON.parse(raw) as DemoState;
  } catch {
    return initialState;
  }
}

function normalized(text: string) {
  return text.toLowerCase().replace(/[^a-z0-9]+/g, ' ');
}

function statusForRequirement(skillName: string, state: DemoState): { status: JobRequirement['status']; note: string } {
  const skill = state.skills.find((item) => item.name.toLowerCase() === skillName.toLowerCase());
  if (!skill) return { status: 'MISSING', note: 'No normalized skill or accepted evidence found.' };
  if (skill.status === 'VERIFIED') return { status: 'VERIFIED', note: `${skill.evidenceCount} evidence items; methods: ${skill.methods.join(', ')}.` };
  if (skill.status === 'PARTIAL') return { status: 'PARTIAL', note: skill.summary };
  if (skill.status === 'EVIDENCE_FOUND') return { status: 'EVIDENCE_FOUND', note: 'Source-backed observations exist; verification is not complete.' };
  if (skill.status === 'STALE' || skill.status === 'EXPIRED') return { status: 'STALE', note: `Last verified ${skill.lastVerifiedAt ?? 'date unavailable'}; refresh required.` };
  return { status: 'MISSING', note: 'No accepted proof is available.' };
}

function compileRequirements(description: string, state: DemoState): JobRequirement[] {
  const source = normalized(description);
  const catalog = [
    { skillId: 'java', skillName: 'Java', aliases: ['java'], kind: 'REQUIRED' as const },
    { skillId: 'spring-boot', skillName: 'Spring Boot', aliases: ['spring boot', 'spring'], kind: 'REQUIRED' as const },
    { skillId: 'rest-api', skillName: 'REST API Development', aliases: ['rest api', 'restful', 'api development'], kind: 'REQUIRED' as const },
    { skillId: 'postgresql', skillName: 'PostgreSQL', aliases: ['postgres', 'postgresql'], kind: 'REQUIRED' as const },
    { skillId: 'jwt', skillName: 'JWT Authentication', aliases: ['jwt', 'authentication', 'oauth'], kind: 'REQUIRED' as const },
    { skillId: 'testing', skillName: 'Testing', aliases: ['testing', 'unit test', 'junit'], kind: 'REQUIRED' as const },
    { skillId: 'docker', skillName: 'Docker', aliases: ['docker', 'container'], kind: 'PREFERRED' as const },
    { skillId: 'react', skillName: 'React', aliases: ['react'], kind: 'PREFERRED' as const },
  ];
  const included = catalog.filter((item) => item.aliases.some((alias) => source.includes(alias)));
  const fallback = included.length ? included : catalog.slice(0, 5);
  return fallback.map((item, index) => {
    const result = statusForRequirement(item.skillName, state);
    return { id: `req-new-${item.skillId}-${index}`, skillId: item.skillId, skillName: item.skillName, kind: item.kind, status: result.status, proofNote: result.note };
  });
}

export function StoreProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<DemoState>(loadState);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  }, [state]);

  const signIn = useCallback((role: 'candidate' | 'recruiter' = 'candidate') => {
    setState((current) => ({ ...current, signedIn: true, role }));
  }, []);

  const signOut = useCallback(() => {
    setState((current) => ({ ...current, signedIn: false }));
  }, []);

  const setRole = useCallback((role: 'candidate' | 'recruiter') => {
    setState((current) => ({ ...current, role }));
  }, []);

  const connectGithubFixture = useCallback(() => {
    setState((current) => ({
      ...current,
      githubConnected: true,
      project: { ...current.project, analysisStatus: 'CONNECTED', completedStages: 0 },
      analysisNotice: 'Connected to the explicitly labeled FoodBridge API offline fixture. No live GitHub data was accessed in this demo.',
    }));
  }, []);

  const startAnalysis = useCallback(() => {
    setState((current) => ({ ...current, project: { ...current.project, analysisStatus: 'PROCESSING', completedStages: 0 }, analysisNotice: 'Analysis job queued from the offline repository snapshot.' }));
    let stage = 0;
    const timer = window.setInterval(() => {
      stage += 1;
      setState((current) => ({
        ...current,
        project: {
          ...current.project,
          analysisStatus: stage >= current.project.analysisStages.length ? 'COMPLETED' : 'PROCESSING',
          completedStages: stage,
          analyzedAt: stage >= current.project.analysisStages.length ? '28 Sep 2026 · 14:32 IST' : current.project.analyzedAt,
        },
        analysisNotice: stage >= current.project.analysisStages.length ? 'Analysis complete. Every observation below points to the abc1234 repository snapshot.' : current.analysisNotice,
      }));
      if (stage >= 6) window.clearInterval(timer);
    }, 650);
  }, []);

  const resetDemo = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY);
    setState(initialState);
  }, []);

  const startExamination = useCallback(() => {
    setState((current) => ({ ...current, examination: { ...current.examination, status: 'IN_PROGRESS', currentIndex: Math.min(current.examination.currentIndex, current.examination.questions.length - 1) } }));
  }, []);

  const submitAnswer = useCallback((text: string) => {
    const trimmed = text.trim();
    if (trimmed.length < 24) return { accepted: false, message: 'Give the examiner a little more reasoning. Aim for at least two concrete implementation details.' };
    let accepted = false;
    setState((current) => {
      const question = current.examination.questions[current.examination.currentIndex];
      const answerText = normalized(trimmed);
      const signalHits = question.expectedSignals.filter((signal) => answerText.includes(normalized(signal))).length;
      accepted = signalHits >= 1 && trimmed.length >= 55;
      const answer = { questionId: question.id, text: trimmed, result: accepted ? 'MEETS_BAR' as const : 'NEEDS_DEPTH' as const, feedback: accepted ? 'Grounded answer: you connected the explanation to the project snapshot.' : 'The answer needs a more specific file, flow, or trade-off from this project.' };
      const answers = [...current.examination.answers.filter((item) => item.questionId !== question.id), answer];
      const isLast = current.examination.currentIndex >= current.examination.questions.length - 1;
      const nextExam = { ...current.examination, answers, currentIndex: isLast ? current.examination.currentIndex : current.examination.currentIndex + 1, status: isLast ? 'COMPLETED' as const : 'IN_PROGRESS' as const, result: isLast && answers.filter((item) => item.result === 'MEETS_BAR').length >= 2 ? 'PASSED' as const : isLast ? 'PARTIAL' as const : undefined, completedAt: isLast ? '28 Sep 2026 · 14:38 IST' : undefined };
      return { ...current, examination: nextExam };
    });
    return { accepted, message: accepted ? 'Answer grounded. The next project-specific probe is ready.' : 'Answer recorded, but the examiner is asking for deeper project-specific reasoning.' };
  }, []);

  const submitChallenge = useCallback((text: string) => {
    const answerText = normalized(text);
    const hasGuard = answerText.includes('preauthorize') || answerText.includes('hasrole') || answerText.includes('authority');
    const hasTest = answerText.includes('test') || answerText.includes('mockmvc') || answerText.includes('403');
    const passed = text.trim().length >= 60 && hasGuard && hasTest;
    setState((current) => {
      const skill = current.skills.find((item) => item.id === 'jwt');
      const updatedSkills = current.skills.map((item) => item.id === 'jwt' && passed ? { ...item, status: 'VERIFIED' as SkillStatus, lastVerifiedAt: '28 Sep 2026', freshness: 'CURRENT' as const, methods: ['Repository evidence', 'Project defense', 'Practical verification'], evidenceCount: item.evidenceCount + 2, summary: 'Authentication and role authorization are now supported by repository evidence, a passing project defense, and a bounded practical change.' } : item);
      const updatedEvidence = passed ? [...current.evidence, { id: 'ev-practical-jwt', skillId: 'jwt', skillName: 'JWT Authentication', sourceType: 'PRACTICAL_VERIFICATION' as const, sourceLabel: 'Bounded practical task', location: 'Candidate patch · role authorization', observation: 'Candidate added an authorization guard and described a regression test for the denied path. This demo records the result; production execution belongs in an isolated sandbox.', strength: 'DIRECT' as const, status: 'VERIFIED' as const, observedAt: '28 Sep 2026', snapshot: 'challenge-role-authorization', visibility: 'RECRUITER_SHARED' as const, projectId: 'project-foodbridge', independentSignal: 'Defense answer referenced SecurityConfig and the filter chain' }] : current.evidence;
      const updatedJob = { ...current.job, requirements: current.job.requirements.map((req) => req.skillId === 'jwt' && passed ? { ...req, status: 'VERIFIED' as const, proofNote: 'Repository implementation plus passing defense and practical role-authorization task.' } : req) };
      return { ...current, skills: updatedSkills, evidence: updatedEvidence, challenge: { ...current.challenge, status: passed ? 'PASSED' as const : 'NEEDS_CHANGES' as const, submission: text, feedback: passed ? 'Passed the demo rubric. In production this patch would run in an isolated ephemeral workspace with tests.' : 'Add an explicit role guard and a denied-path test before resubmitting.' }, candidate: { ...current.candidate, verificationProgress: passed ? 82 : current.candidate.verificationProgress }, job: updatedJob, analysisNotice: passed ? 'JWT Authentication is now VERIFIED under policy v0.1-demo.' : 'Practical submission needs another pass.' };
    });
    return { passed, message: passed ? 'Practical task passed. Verification policy can now combine the evidence.' : 'Submission needs an explicit authorization guard and a denied-path test.' };
  }, []);

  const createJob = useCallback((title: string, company: string, description: string) => {
    setState((current) => {
      const requirements = compileRequirements(description, current);
      const job: Job = { id: `job-${Date.now()}`, title: title.trim() || 'Untitled role', company: company.trim() || 'New organization', location: 'Remote · To be confirmed', createdAt: '28 Sep 2026', sourceDescription: description.trim(), requirements };
      return { ...current, job, role: 'recruiter', signedIn: true };
    });
  }, []);

  const value = useMemo(() => ({ ...state, signIn, signOut, setRole, connectGithubFixture, startAnalysis, resetDemo, startExamination, submitAnswer, submitChallenge, createJob }), [state, signIn, signOut, setRole, connectGithubFixture, startAnalysis, resetDemo, startExamination, submitAnswer, submitChallenge, createJob]);
  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>;
}

export function useStore() {
  const context = useContext(StoreContext);
  if (!context) throw new Error('useStore must be used inside StoreProvider');
  return context;
}

export { fixtureExam, fixtureChallenge };
