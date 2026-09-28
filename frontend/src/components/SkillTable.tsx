import { ArrowUpRight, ChevronRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import type { Skill } from '../domain';
import { FreshnessPill, StatusPill } from './ui';

export function SkillTable({ skills, compact = false }: { skills: Skill[]; compact?: boolean }) {
  return <div className={`skill-table-wrap ${compact ? 'skill-table-compact' : ''}`}><table className="skill-table"><thead><tr><th>Capability</th><th>Status</th><th>Freshness</th><th>Evidence</th><th>Verification method</th><th /></tr></thead><tbody>{skills.map((skill) => <tr key={skill.id} id={skill.id}><td><div className="skill-cell"><span className="skill-bullet" /><div><strong>{skill.name}</strong><small>{skill.category}</small></div></div></td><td><StatusPill status={skill.status} small /></td><td><FreshnessPill state={skill.freshness} /></td><td><span className="table-count">{skill.evidenceCount || '—'} <small>{skill.evidenceCount ? 'items' : ''}</small></span></td><td><span className="method-text">{skill.methods.length ? skill.methods.join(' · ') : 'Proof path needed'}</span></td><td><Link className="row-action" to={`/app/skills#${skill.id}`} aria-label={`Inspect ${skill.name}`}><ArrowUpRight size={15} /></Link></td></tr>)}</tbody></table></div>;
}

export function SkillList({ skills }: { skills: Skill[] }) {
  return <div className="skill-list">{skills.map((skill) => <Link className="skill-list-row" to={`/app/skills#${skill.id}`} key={skill.id}><span className="skill-list-mark"><span /></span><span className="skill-list-copy"><strong>{skill.name}</strong><small>{skill.evidenceCount ? `${skill.evidenceCount} evidence items · ${skill.methods[0] ?? 'No verification yet'}` : 'No accepted evidence yet'}</small></span><StatusPill status={skill.status} small /><ChevronRight size={15} className="muted-icon" /></Link>)}</div>;
}
