import { Navigate, Route, Routes } from 'react-router-dom';
import AppShell from './components/AppShell';
import { useStore } from './store';
import { isDemoMode } from './api/client';
import LandingPage from './pages/LandingPage';
import AuthPage from './pages/AuthPage';
import OverviewPage from './pages/OverviewPage';
import ProjectsPage from './pages/ProjectsPage';
import EvidencePage from './pages/EvidencePage';
import SkillsPage from './pages/SkillsPage';
import ExaminerPage from './pages/ExaminerPage';
import PassportPage from './pages/PassportPage';
import VerifyPage from './pages/VerifyPage';
import NotFoundPage from './pages/NotFoundPage';
import RecruiterDashboard from './pages/RecruiterDashboard';
import JobsPage from './pages/JobsPage';
import ProofReviewPage from './pages/ProofReviewPage';
import RealOverviewPage from './pages/real/RealOverviewPage';
import RealProjectsPage from './pages/real/RealProjectsPage';
import RealEvidencePage from './pages/real/RealEvidencePage';
import RealSkillsPage from './pages/real/RealSkillsPage';
import RealExaminerPage from './pages/real/RealExaminerPage';
import RealPassportPage from './pages/real/RealPassportPage';
import RealRecruiterPage from './pages/real/RealRecruiterPage';

function Protected({ children, role }: { children: React.ReactNode; role?: 'candidate' | 'recruiter' }) {
  const { signedIn, role: currentRole } = useStore();
  if (!signedIn) return <Navigate to="/signin" replace />;
  if (role && currentRole !== role) return <Navigate to={role === 'recruiter' ? '/recruiter' : '/app'} replace />;
  return <>{children}</>;
}

export default function App() {
  const CandidateOverview = isDemoMode ? OverviewPage : RealOverviewPage;
  const CandidateProjects = isDemoMode ? ProjectsPage : RealProjectsPage;
  const CandidateEvidence = isDemoMode ? EvidencePage : RealEvidencePage;
  const CandidateSkills = isDemoMode ? SkillsPage : RealSkillsPage;
  const CandidateExaminer = isDemoMode ? ExaminerPage : RealExaminerPage;
  const CandidatePassport = isDemoMode ? PassportPage : RealPassportPage;
  const RecruiterHome = isDemoMode ? RecruiterDashboard : RealRecruiterPage;
  const RecruiterJobs = isDemoMode ? JobsPage : RealRecruiterPage;
  const RecruiterCandidate = isDemoMode ? ProofReviewPage : RealRecruiterPage;
  return <Routes>
    <Route path="/" element={<LandingPage />} />
    <Route path="/signin" element={<AuthPage />} />
    <Route path="/verify/:proofId" element={<VerifyPage />} />
    <Route path="/app" element={<Protected role="candidate"><AppShell><CandidateOverview /></AppShell></Protected>} />
    <Route path="/app/projects" element={<Protected role="candidate"><AppShell><CandidateProjects /></AppShell></Protected>} />
    <Route path="/app/evidence" element={<Protected role="candidate"><AppShell><CandidateEvidence /></AppShell></Protected>} />
    <Route path="/app/skills" element={<Protected role="candidate"><AppShell><CandidateSkills /></AppShell></Protected>} />
    <Route path="/app/examiner" element={<Protected role="candidate"><AppShell><CandidateExaminer /></AppShell></Protected>} />
    <Route path="/app/passport" element={<Protected role="candidate"><AppShell><CandidatePassport /></AppShell></Protected>} />
    <Route path="/recruiter" element={<Protected role="recruiter"><AppShell><RecruiterHome /></AppShell></Protected>} />
    <Route path="/recruiter/jobs" element={<Protected role="recruiter"><AppShell><RecruiterJobs /></AppShell></Protected>} />
    <Route path="/recruiter/candidate" element={<Protected role="recruiter"><AppShell><RecruiterCandidate /></AppShell></Protected>} />
    <Route path="*" element={<NotFoundPage />} />
  </Routes>;
}
