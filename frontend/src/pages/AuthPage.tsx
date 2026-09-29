import { useState } from 'react';
import { AlertTriangle, ArrowRight, Check, Github, LoaderCircle, LockKeyhole, ShieldCheck } from 'lucide-react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Logo } from '../components/ui';
import { useStore } from '../store';
import { api, ApiError, isDemoMode } from '../api/client';

export default function AuthPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { signIn } = useStore();
  const params = new URLSearchParams(location.search);
  const [role, setRole] = useState<'candidate' | 'recruiter'>(params.get('role') === 'recruiter' ? 'recruiter' : 'candidate');
  const [mode, setMode] = useState<'signin' | 'signup'>('signup');
  const [email, setEmail] = useState(isDemoMode ? 'aman@example.com' : '');
  const [name, setName] = useState(isDemoMode ? 'Aman Gautam' : '');
  const [password, setPassword] = useState(isDemoMode ? 'demo-password' : '');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: React.FormEvent) {
    event.preventDefault(); setError(null);
    if (isDemoMode) { signIn(role); navigate(role === 'recruiter' ? '/recruiter' : '/app'); return; }
    setLoading(true);
    try {
      const response = mode === 'signup'
        ? await api.auth.register({ email, password, displayName: name, role: role === 'candidate' ? 'CANDIDATE' : 'RECRUITER' })
        : await api.auth.login({ email, password });
      signIn(response.user.role === 'RECRUITER' ? 'recruiter' : 'candidate');
      navigate(response.user.role === 'RECRUITER' ? '/recruiter' : '/app');
    } catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Authentication could not be completed.'); }
    finally { setLoading(false); }
  }

  return <div className="auth-page"><div className="auth-left"><Link to="/"><Logo /></Link><div className="auth-left-content"><span className="eyebrow">The proof workspace</span><h1>Let your work<br /><span>speak clearly.</span></h1><p>Connect a real project, trace the evidence, and build a proof passport that respects the difference between a claim and a capability.</p><div className="auth-bullets"><span><Check size={14} /> No unexplained skill scores</span><span><Check size={14} /> Private source by default</span><span><Check size={14} /> Human-owned verification</span></div></div><div className="auth-left-footer"><span><ShieldCheck size={14} /> Evidence controls active</span><span>SkillLink 2.0 · 2026</span></div></div><div className="auth-right"><div className="auth-form-wrap"><div className="mobile-auth-logo"><Logo /></div><div className="auth-form-head"><span className="eyebrow">{mode === 'signup' ? 'Start with proof' : 'Welcome back'}</span><h2>{mode === 'signup' ? 'Build your proof workspace.' : 'Sign in to SkillLink.'}</h2><p>{isDemoMode ? 'Offline deterministic demo mode is active. No live GitHub or AI result is implied.' : mode === 'signup' ? 'Create your account, then authorize a repository from the protected workspace.' : 'Continue with your server-backed proof workspace.'}</p></div><div className="role-switch"><button type="button" className={role === 'candidate' ? 'active' : ''} onClick={() => setRole('candidate')}><span className="role-switch-icon">C</span>Candidate</button><button type="button" className={role === 'recruiter' ? 'active' : ''} onClick={() => setRole('recruiter')}><span className="role-switch-icon">R</span>Recruiter</button></div>{error && <div className="notice-bar error auth-error"><AlertTriangle size={14} />{error}</div>}<form onSubmit={submit} className="auth-form">{mode === 'signup' && <label>Name<input value={name} onChange={(e) => setName(e.target.value)} placeholder="Your name" required /></label>}<label>Work email<input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" required /></label><label>Password<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder={isDemoMode ? 'demo-password' : 'At least 12 characters'} required minLength={isDemoMode ? 8 : 12} /></label><button className="button button-primary button-full" type="submit" disabled={loading}>{loading ? <><LoaderCircle className="spin" size={16} /> Working…</> : <>{mode === 'signup' ? isDemoMode ? 'Enter offline demo' : 'Create proof workspace' : 'Sign in to workspace'} <ArrowRight size={16} /></>}</button></form><div className="auth-divider"><span>or continue with</span></div><button type="button" className="button button-provider button-full" onClick={() => { if (isDemoMode) { signIn(role); navigate(role === 'recruiter' ? '/recruiter' : '/app'); } }} disabled={!isDemoMode}><Github size={17} /> {isDemoMode ? 'Enter offline demo with GitHub' : 'Connect GitHub after account setup'}{!isDemoMode && <span className="planned-tag">after signup</span>}</button><div className="auth-legal"><LockKeyhole size={13} />{isDemoMode ? 'Demo fixtures stay in this browser and are labeled offline.' : 'Passwords and GitHub tokens are handled by the Spring Security API; access tokens stay server-side where required.'}</div><p className="auth-switch">{mode === 'signup' ? 'Already have an account?' : 'New to SkillLink?'} <button type="button" onClick={() => { setError(null); setMode(mode === 'signup' ? 'signin' : 'signup'); }}>{mode === 'signup' ? 'Sign in' : 'Create an account'}</button></p></div></div></div>;
}
