import type { ReactNode } from 'react';
import { ArrowUpRight, Check, CircleAlert, Clock3, FileCode2, Fingerprint, GitCommitHorizontal, LockKeyhole, ShieldCheck, Sparkles, TriangleAlert } from 'lucide-react';
import type { Evidence, EvidenceStrength, FreshnessState, SkillStatus } from '../domain';

export function Logo({ compact = false }: { compact?: boolean }) {
  return <div className={`brand-lockup ${compact ? 'brand-lockup-compact' : ''}`} aria-label="SkillLink">
    <span className="brand-mark"><span /></span>
    {!compact && <span className="brand-name">skill<span>link</span></span>}
  </div>;
}

const statusCopy: Record<SkillStatus, string> = {
  SELF_CLAIM: 'Self claim', EVIDENCE_FOUND: 'Evidence found', PARTIAL: 'Partial', VERIFIED: 'Verified', STALE: 'Stale', EXPIRED: 'Expired', NOT_VERIFIED: 'Missing', DISPUTED: 'Disputed',
};

const statusClass: Record<SkillStatus, string> = {
  SELF_CLAIM: 'status-neutral', EVIDENCE_FOUND: 'status-info', PARTIAL: 'status-warning', VERIFIED: 'status-success', STALE: 'status-warning', EXPIRED: 'status-danger', NOT_VERIFIED: 'status-neutral', DISPUTED: 'status-purple',
};

export function StatusPill({ status, small = false }: { status: SkillStatus; small?: boolean }) {
  const Icon = status === 'VERIFIED' ? ShieldCheck : status === 'PARTIAL' || status === 'STALE' ? TriangleAlert : status === 'DISPUTED' ? CircleAlert : status === 'EVIDENCE_FOUND' ? Fingerprint : Clock3;
  return <span className={`status-pill ${statusClass[status]} ${small ? 'status-pill-small' : ''}`}><Icon size={small ? 12 : 14} strokeWidth={2.2} />{statusCopy[status]}</span>;
}

export function FreshnessPill({ state }: { state: FreshnessState }) {
  const content: Record<FreshnessState, { label: string; cls: string }> = {
    CURRENT: { label: 'Current', cls: 'fresh-current' }, AGING: { label: 'Aging', cls: 'fresh-aging' }, STALE: { label: 'Stale', cls: 'fresh-stale' }, EXPIRED: { label: 'Expired', cls: 'fresh-expired' }, NOT_APPLICABLE: { label: 'Not tracked', cls: 'fresh-na' },
  };
  const item = content[state];
  return <span className={`freshness-pill ${item.cls}`}><span className="freshness-dot" />{item.label}</span>;
}

const strengthLabel: Record<EvidenceStrength, string> = { WEAK: 'Weak', MODERATE: 'Moderate', STRONG: 'Strong', DIRECT: 'Direct' };

export function StrengthLabel({ strength }: { strength: EvidenceStrength }) {
  return <span className={`strength-label strength-${strength.toLowerCase()}`}><span className="strength-bar"><i /><i /><i /><i /></span>{strengthLabel[strength]}</span>;
}

export function SourceChip({ children, tone = 'default' }: { children: ReactNode; tone?: 'default' | 'blue' | 'purple' }) {
  return <span className={`source-chip source-chip-${tone}`}><span className="source-chip-dot" />{children}</span>;
}

export function EvidenceCard({ evidence, onOpen }: { evidence: Evidence; onOpen?: () => void }) {
  return <article className="evidence-card">
    <div className="evidence-card-top">
      <div className="evidence-type"><span className="evidence-icon"><FileCode2 size={16} /></span><div><div className="eyebrow">{evidence.sourceType.replace('_', ' ')}</div><h3>{evidence.skillName}</h3></div></div>
      <StrengthLabel strength={evidence.strength} />
    </div>
    <p className="evidence-observation">{evidence.observation}</p>
    <div className="evidence-source"><GitCommitHorizontal size={14} /><code>{evidence.location}</code></div>
    <div className="evidence-card-bottom"><span className="evidence-meta">{evidence.sourceLabel} · {evidence.observedAt}</span><span className={`visibility-label visibility-${evidence.visibility.toLowerCase()}`}><LockKeyhole size={12} />{evidence.visibility === 'PUBLIC_SUMMARY' ? 'Public summary' : evidence.visibility.replace('_', ' ').toLowerCase()}</span>{onOpen && <button className="text-action" onClick={onOpen}>Inspect <ArrowUpRight size={13} /></button>}</div>
  </article>;
}

export function MetricCard({ label, value, detail, icon, tone = 'blue', action }: { label: string; value: string | number; detail: string; icon: ReactNode; tone?: string; action?: ReactNode }) {
  return <div className={`metric-card metric-${tone}`}><div className="metric-top"><span className="metric-icon">{icon}</span>{action}</div><div className="metric-value">{value}</div><div className="metric-label">{label}</div><div className="metric-detail">{detail}</div></div>;
}

export function SectionHeading({ eyebrow, title, description, action }: { eyebrow?: string; title: string; description?: string; action?: ReactNode }) {
  return <div className="section-heading"><div>{eyebrow && <div className="eyebrow">{eyebrow}</div>}<h2>{title}</h2>{description && <p>{description}</p>}</div>{action}</div>;
}

export function EmptyState({ icon, title, description, action }: { icon: ReactNode; title: string; description: string; action?: ReactNode }) {
  return <div className="empty-state"><div className="empty-icon">{icon}</div><h3>{title}</h3><p>{description}</p>{action}</div>;
}

export function TinyVerified() { return <span className="tiny-verified"><Check size={11} /> verified</span>; }

export function DemoNote({ children }: { children?: ReactNode }) {
  return <div className="demo-note"><Sparkles size={15} /><span><strong>Demo workspace</strong> {children ?? 'This view uses an offline fixture and local state. Live integrations are not being simulated.'}</span></div>;
}
