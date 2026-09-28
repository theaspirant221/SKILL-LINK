import { Navigate, Route, Routes } from 'react-router-dom';
import AppShell from './components/AppShell';
import { useStore } from './store';
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

function Protected({ children, role }: { children: React.ReactNode; role?: 'candidate' | 'recruiter' }) {
  const { signedIn, role: currentRole } = useStore();
  if (!signedIn) return <Navigate to="/signin" replace />;
  if (role && currentRole !== role) return <Navigate to={role === 'recruiter' ? '/recruiter' : '/app'} replace />;
  return <>{children}</>;
}

export default function App() {
  return <Routes>
    <Route path="/" element={<LandingPage />} />
    <Route path="/signin" element={<AuthPage />} />
    <Route path="/verify/:proofId" element={<VerifyPage />} />
    <Route path="/app" element={<Protected role="candidate"><AppShell><OverviewPage /></AppShell></Protected>} />
    <Route path="/app/projects" element={<Protected role="candidate"><AppShell><ProjectsPage /></AppShell></Protected>} />
    <Route path="/app/evidence" element={<Protected role="candidate"><AppShell><EvidencePage /></AppShell></Protected>} />
    <Route path="/app/skills" element={<Protected role="candidate"><AppShell><SkillsPage /></AppShell></Protected>} />
    <Route path="/app/examiner" element={<Protected role="candidate"><AppShell><ExaminerPage /></AppShell></Protected>} />
    <Route path="/app/passport" element={<Protected role="candidate"><AppShell><PassportPage /></AppShell></Protected>} />
    <Route path="/recruiter" element={<Protected role="recruiter"><AppShell><RecruiterDashboard /></AppShell></Protected>} />
    <Route path="/recruiter/jobs" element={<Protected role="recruiter"><AppShell><JobsPage /></AppShell></Protected>} />
    <Route path="/recruiter/candidate" element={<Protected role="recruiter"><AppShell><ProofReviewPage /></AppShell></Protected>} />
    <Route path="*" element={<NotFoundPage />} />
  </Routes>;
}
