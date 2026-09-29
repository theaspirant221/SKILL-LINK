import { ArrowLeft, Check, ExternalLink, Fingerprint, LoaderCircle, LockKeyhole, ShieldCheck } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { api, type PublicPassport } from '../../api/client';
import { Logo, StatusPill } from '../../components/ui';

function formatDate(value: string | null) { return value ? new Date(value).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : 'date unavailable'; }

export default function RealVerifyPage() {
  const { proofId } = useParams();
  const [passport, setPassport] = useState<PublicPassport | null>(null);
  const [state, setState] = useState<'loading' | 'missing' | 'ready'>('loading');

  useEffect(() => {
    let cancelled = false;
    setState('loading');
    setPassport(null);
    if (!proofId) { setState('missing'); return; }
    api.passport.public(proofId)
      .then((next) => { if (!cancelled) { setPassport(next); setState('ready'); } })
      .catch(() => { if (!cancelled) setState('missing'); });
    return () => { cancelled = true; };
  }, [proofId]);

  return <div className="verify-page">
    <header className="verify-header">
      <Link to="/"><Logo /></Link>
      <div className="verify-header-right">
        <span><LockKeyhole size={13} /> Public summary · source protected</span>
        <Link to="/signin" className="button button-ghost button-small">Create your passport <ExternalLink size={14} /></Link>
      </div>
    </header>
    <main className="verify-main">
      <Link to="/" className="back-link"><ArrowLeft size={14} /> Back to SkillLink</Link>
      {state === 'loading' && <div className="panel loading-block verify-loading"><LoaderCircle className="spin" size={18} /> Verifying this proof identifier…</div>}
      {state === 'missing' && <div className="verify-card verify-missing">
        <div className="verify-missing-icon"><LockKeyhole size={26} /></div>
        <div className="eyebrow">Proof passport</div>
        <h1>This proof is not publicly available.</h1>
        <p>The identifier may be incorrect, or the holder set the passport to private, or the passport expired or was revoked. Public visibility is an explicit holder decision, and private repository source is never exposed through this page.</p>
        <Link to="/" className="button button-ghost">Back to SkillLink</Link>
      </div>}
      {state === 'ready' && passport && <div className="verify-card">
        <div className="verify-card-top">
          <div className="passport-seal"><Fingerprint size={27} /></div>
          <div>
            <div className="eyebrow">Verified Proof Passport</div>
            <h1>{passport.candidateDisplayName}</h1>
            <p>Public proof summary · source protected</p>
          </div>
          <div className="verified-stamp"><ShieldCheck size={16} /> Authentic passport</div>
        </div>
        <div className="verify-id-row">
          <span>Proof ID <code>{passport.publicIdentifier}</code></span>
          <span>Issued {formatDate(passport.issuedAt)}</span>
          {passport.expiresAt && <span>Expires {formatDate(passport.expiresAt)}</span>}
        </div>
        <div className="verify-summary">
          <div>
            <span className="eyebrow">Proof summary</span>
            <h2>Capability with a traceable path.</h2>
            <p>Each skill below passed an explicit policy gate: repository evidence pinned to a commit, a grounded project defense, and a reviewer-owned practical verification. Private source and assessment answers are not exposed.</p>
          </div>
          <div className="verify-summary-stat"><strong>{passport.items.length}</strong><span>verified skill{passport.items.length === 1 ? '' : 's'}</span></div>
        </div>
        <div className="verify-skills">
          <div className="verify-section-title"><span>Verified capabilities</span><span>Policy version</span></div>
          {passport.items.map((item) => <div className="verify-skill-row" key={item.skillId}>
            <div className="verify-skill-name">
              <span className="verify-check"><Check size={13} /></span>
              <div><strong>{item.skillName}</strong><small>{item.category} · verified {formatDate(item.verifiedAt)}</small></div>
            </div>
            <span className="verify-policy">{item.policyVersion ?? 'unavailable'}</span>
          </div>)}
        </div>
        <div className="verify-provenance">
          <div>
            <div className="eyebrow">Provenance</div>
            <strong>Repository evidence · project defense · practical verification</strong>
            <p>Verification decisions are recorded against immutable snapshots and a versioned policy. This page shows only policy-verified summaries.</p>
          </div>
          <StatusPill status="VERIFIED" small />
        </div>
        <div className="verify-footer">
          <span><ShieldCheck size={14} /> Per-skill policy versions listed above</span>
          <span>SkillLink is a verification layer, not a hiring decision.</span>
        </div>
      </div>}
      <p className="verify-disclaimer">This page is a public summary. To request more detail, the holder must explicitly share additional proof.</p>
    </main>
  </div>;
}
