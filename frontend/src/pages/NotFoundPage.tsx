import { Link } from 'react-router-dom';
import { Logo } from '../components/ui';
export default function NotFoundPage() { return <div className="not-found"><Logo /><h1>That proof path does not exist.</h1><p>The page may be private, revoked, or not built yet.</p><Link to="/" className="button button-primary">Return home</Link></div>; }
