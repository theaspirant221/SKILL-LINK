import { useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { Activity, Bell, BookOpenCheck, BriefcaseBusiness, ChevronDown, ChevronRight, CircleHelp, Code2, FileCheck2, Fingerprint, GitBranch, GraduationCap, LayoutDashboard, Menu, Network, Plus, Search, Settings2, ShieldCheck, Sparkles, Users, X } from 'lucide-react';
import { useStore } from '../store';
import { Logo } from './ui';
import type { UserRole } from '../domain';

const candidateNav = [
  { label: 'Overview', to: '/app', icon: LayoutDashboard },
  { label: 'Projects', to: '/app/projects', icon: Code2 },
  { label: 'Evidence', to: '/app/evidence', icon: FileCheck2 },
  { label: 'Skills', to: '/app/skills', icon: Network },
  { label: 'AI Examiner', to: '/app/examiner', icon: Sparkles, badge: 'Live' },
  { label: 'Proof Passport', to: '/app/passport', icon: Fingerprint },
];

const recruiterNav = [
  { label: 'Overview', to: '/recruiter', icon: LayoutDashboard },
  { label: 'Jobs & proof contracts', to: '/recruiter/jobs', icon: BriefcaseBusiness },
  { label: 'Candidate proof', to: '/recruiter/candidate', icon: Users },
];

function NavItem({ item, onNavigate }: { item: (typeof candidateNav)[number]; onNavigate: () => void }) {
  const Icon = item.icon;
  return <NavLink to={item.to} end={item.to === '/app' || item.to === '/recruiter'} onClick={onNavigate} className={({ isActive }) => `nav-item ${isActive ? 'nav-item-active' : ''}`}><Icon size={17} strokeWidth={1.8} /><span>{item.label}</span>{item.badge && <span className="nav-badge">{item.badge}</span>}</NavLink>;
}

export default function AppShell({ children }: { children: React.ReactNode }) {
  const { candidate, role, setRole, signOut, resetDemo } = useStore();
  const [mobileOpen, setMobileOpen] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();
  const isRecruiter = role === 'recruiter' || location.pathname.startsWith('/recruiter');
  const nav = isRecruiter ? recruiterNav : candidateNav;
  const pageName = location.pathname.includes('examiner') ? 'AI Examiner' : location.pathname.includes('evidence') ? 'Evidence' : location.pathname.includes('skills') ? 'Skills' : location.pathname.includes('passport') ? 'Proof Passport' : location.pathname.includes('projects') ? 'Projects' : location.pathname.includes('jobs') ? 'Jobs & proof contracts' : location.pathname.includes('candidate') ? 'Candidate proof' : 'Overview';

  function changeRole(next: UserRole) {
    setRole(next);
    navigate(next === 'recruiter' ? '/recruiter' : '/app');
    setMobileOpen(false);
  }

  return <div className="app-shell">
    {mobileOpen && <button className="mobile-scrim" aria-label="Close navigation" onClick={() => setMobileOpen(false)} />}
    <aside className={`sidebar ${mobileOpen ? 'sidebar-open' : ''}`}>
      <div className="sidebar-head"><Link to={isRecruiter ? '/recruiter' : '/app'} onClick={() => setMobileOpen(false)}><Logo /></Link><button className="icon-button mobile-close" onClick={() => setMobileOpen(false)} aria-label="Close navigation"><X size={18} /></button></div>
      <div className="workspace-switcher"><div className="workspace-avatar">{isRecruiter ? 'NL' : candidate.initials}</div><div className="workspace-copy"><strong>{isRecruiter ? 'Northstar Labs' : 'Aman Gautam'}</strong><span>{isRecruiter ? 'Recruiter workspace' : 'Candidate workspace'}</span></div><ChevronDown size={15} className="muted-icon" /></div>
      <div className="sidebar-scroll">
        <div className="nav-group"><div className="nav-group-label">{isRecruiter ? 'Hiring workspace' : 'Proof workspace'}</div>{nav.map((item) => <NavItem key={item.to} item={item} onNavigate={() => setMobileOpen(false)} />)}</div>
        <div className="nav-group nav-group-secondary"><div className="nav-group-label">Workspace</div>
          {!isRecruiter && <NavLink to="/app/projects" className="nav-item" onClick={() => setMobileOpen(false)}><GitBranch size={17} strokeWidth={1.8} /><span>GitHub source</span><span className="nav-status-dot" /></NavLink>}
          {isRecruiter && <NavLink to="/recruiter/jobs" className="nav-item" onClick={() => setMobileOpen(false)}><BookOpenCheck size={17} strokeWidth={1.8} /><span>Proof contracts</span></NavLink>}
          <button className="nav-item nav-item-button"><Bell size={17} strokeWidth={1.8} /><span>Notifications</span><span className="notification-count">2</span></button>
        </div>
        <div className="sidebar-spacer" />
        <div className="nav-group"><NavLink to="/app/skills" className="nav-item" onClick={() => setMobileOpen(false)}><CircleHelp size={17} strokeWidth={1.8} /><span>Help center</span></NavLink><button className="nav-item nav-item-button"><Settings2 size={17} strokeWidth={1.8} /><span>Settings</span></button></div>
      </div>
      <div className="sidebar-footer"><div className="trust-mini"><ShieldCheck size={15} /><span>Evidence controls active</span></div><button className="profile-mini" onClick={() => changeRole(isRecruiter ? 'candidate' : 'recruiter')}><span className="profile-avatar">{candidate.initials}</span><span className="profile-mini-copy"><strong>{candidate.name}</strong><small>Switch to {isRecruiter ? 'candidate' : 'recruiter'}</small></span><ChevronRight size={14} /></button><button className="sidebar-reset" onClick={() => { resetDemo(); signOut(); navigate('/'); }}>Reset demo workspace</button></div>
    </aside>
    <div className="app-main">
      <header className="topbar"><div className="topbar-left"><button className="icon-button menu-trigger" onClick={() => setMobileOpen(true)} aria-label="Open navigation"><Menu size={19} /></button><div className="breadcrumbs"><span>{isRecruiter ? 'Recruiter' : 'Candidate'}</span><ChevronRight size={14} /><strong>{pageName}</strong></div></div><div className="topbar-actions"><button className="search-trigger"><Search size={16} /><span>Search</span><kbd>⌘ K</kbd></button><button className="icon-button notification-button" aria-label="Notifications"><Bell size={17} /><span /></button><button className="topbar-help" aria-label="Help"><CircleHelp size={17} /></button><div className="topbar-avatar">{candidate.initials}</div></div></header>
      <main className="page-content">{children}</main>
    </div>
  </div>;
}
