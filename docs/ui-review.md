# UI review notes

The visual system is deliberately evidence-tooling oriented:

- dark base with restrained blue/cyan trust cues
- green/amber/red/purple statuses paired with labels and icons
- compact tables for recruiters, cards for source evidence, and a list alternative for graph relationships
- explicit demo note to prevent fixture data being mistaken for live analysis
- responsive mobile views switch dense tables to stacked cards and prioritize passport, evidence, examiner, and proof gaps
- reduced-motion media query is included
- focus-visible outlines and semantic buttons/links are used throughout the main flows

Before production launch, run automated contrast checks and manual screen-reader checks against WCAG 2.2 AA, especially the skill graph, examiner progress, public passport, dialogs, and mobile proof-review tables.
