import { ArrowRight, CheckCircle2, CircleDashed, Clock3, LockKeyhole, ShieldCheck } from 'lucide-react';
import { Link } from 'react-router-dom';
import type { Skill } from '../domain';
import { FreshnessPill, StatusPill } from './ui';

export function ProofRail({ skills }: { skills: Skill[] }) {
  const verified = skills.filter((skill) => skill.status === 'VERIFIED').length;
  const evidenceFound = skills.filter((skill) => skill.status === 'EVIDENCE_FOUND').length;
  const missing = skills.filter((skill) => skill.status === 'NOT_VERIFIED').length;
  return <aside className="proof-rail">
    <div className="proof-rail-header"><div><div className="eyebrow">Proof health</div><h3>Make capability inspectable</h3></div><ShieldCheck size={18} className="rail-shield" /></div>
    <div className="rail-progress"><div className="rail-progress-row"><strong>{verified + evidenceFound}/{skills.length}</strong><span>skills with evidence</span></div><div className="progress-track"><span style={{ width: `${Math.round(((verified + evidenceFound) / skills.length) * 100)}%` }} /></div><p>{missing ? `${missing} skill${missing > 1 ? 's' : ''} still need a proof path.` : 'All claimed skills have a proof path.'}</p></div>
    <div className="rail-list"><div className="rail-list-head"><span>Verification map</span><span>State</span></div>{skills.slice(0, 5).map((skill) => <Link className="rail-skill" to={`/app/skills#${skill.id}`} key={skill.id}><span className="rail-skill-name"><span className={`rail-dot rail-dot-${skill.status.toLowerCase()}`} />{skill.name}</span><span className="rail-skill-state">{skill.status === 'VERIFIED' ? <CheckCircle2 size={14} /> : skill.status === 'EVIDENCE_FOUND' ? <CircleDashed size={14} /> : skill.status === 'PARTIAL' ? <Clock3 size={14} /> : <span>—</span>}</span></Link>)}</div>
    <div className="rail-next"><div className="rail-next-icon"><LockKeyhole size={16} /></div><div><div className="eyebrow">Next proof action</div><strong>Finish JWT verification</strong><p>Defense + practical task unlocks a recruiter-ready proof.</p><Link to="/app/examiner">Continue <ArrowRight size={13} /></Link></div></div>
  </aside>;
}
