/** Tailwind tokens are available for shadcn-style additions; the current proof UI uses the same tokens in styles.css. */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: { extend: { colors: { ink: '#070A0F', panel: '#111827', brand: '#2563EB', verified: '#36D399' } } },
  plugins: [],
};
