import { useState } from 'react';
import { ArrowRight, Check, Github, LockKeyhole, ShieldCheck } from 'lucide-react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Logo } from '../components/ui';
import { useStore } from '../store';

export default function AuthPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { signIn } = useStore();
  const params = new URLSearchParams(location.search);
  const [role, setRole] = useState<'candidate' | 'recruiter'>(params.get('role') === 'recruiter' ? 'recruiter' : 'candidate');
  const [mode, setMode] = useState<'signin' | 'signup'>('signup');
  const [email, setEmail] = useState('aman@example.com');
  const [name, setName] = useState('Aman Gautam');

  function submit(event: React.FormEvent) {
    event.preventDefault();
    signIn(role);
    navigate(role === 'recruiter' ? '/recruiter' : '/app');
  }

  return <div className="auth-page"><div className="auth-left"><Link to="/"><Logo /></Link><div className="auth-left-content"><span className="eyebrow">The proof workspace</span><h1>Let your work<br /><span>speak clearly.</span></h1><p>Connect a real project, trace the evidence, and build a proof passport that respects the difference between a claim and a capability.</p><div className="auth-bullets"><span><Check size={14} /> No unexplained skill scores</span><span><Check size={14} /> Private source by default</span><span><Check size={14} /> Human-owned verification</span></div></div><div className="auth-left-footer"><span><ShieldCheck size={14} /> Evidence controls active</span><span>SkillLink 2.0 · 2026</span></div></div><div className="auth-right"><div className="auth-form-wrap"><div className="mobile-auth-logo"><Logo /></div><div className="auth-form-head"><span className="eyebrow">{mode === 'signup' ? 'Start with proof' : 'Welcome back'}</span><h2>{mode === 'signup' ? 'Build your proof workspace.' : 'Sign in to SkillLink.'}</h2><p>{mode === 'signup' ? 'The demo uses local state. No live GitHub access happens until you choose it.' : 'Continue where you left off.'}</p></div><div className="role-switch"><button className={role === 'candidate' ? 'active' : ''} onClick={() => setRole('candidate')}><span className="role-switch-icon">C</span>Candidate</button><button className={role === 'recruiter' ? 'active' : ''} onClick={() => setRole('recruiter')}><span className="role-switch-icon">R</span>Recruiter</button></div><form onSubmit={submit} className="auth-form">{mode === 'signup' && <label>Name<input value={name} onChange={(e) => setName(e.target.value)} placeholder="Your name" required /></label>}<label>Work email<input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" required /></label>{mode === 'signup' && <label>Password<input type="password" defaultValue="demo-password" placeholder="Create a password" required minLength={8} /></label>}<button className="button button-primary button-full" type="submit">{mode === 'signup' ? 'Enter demo workspace' : 'Sign in to workspace'} <ArrowRight size={16} /></button></form><div className="auth-divider"><span>or continue with</span></div><button className="button button-provider button-full"><Github size={17} /> Connect with GitHub <span className="planned-tag">production</span></button><div className="auth-legal"><LockKeyhole size={13} /> Demo credentials never leave this browser. Production auth will use Spring Security + OAuth.</div><p className="auth-switch">{mode === 'signup' ? 'Already have an account?' : 'New to SkillLink?'} <button onClick={() => setMode(mode === 'signup' ? 'signin' : 'signup')}>{mode === 'signup' ? 'Sign in' : 'Create an account'}</button></p></div></div></div>;
}
