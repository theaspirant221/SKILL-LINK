import { describe, expect, it } from 'vitest';
import { evidence, initialState, skills } from './fixtures';

describe('SkillLink proof fixture', () => {
  it('keeps evidence traceable to an immutable repository snapshot', () => {
    expect(evidence.length).toBeGreaterThan(0);
    expect(evidence.every((item) => item.projectId === initialState.project.id)).toBe(true);
    expect(evidence.every((item) => item.snapshot === initialState.project.repository.commitLabel)).toBe(true);
    expect(evidence.some((item) => item.location.endsWith('JwtService.java'))).toBe(true);
  });

  it('does not start the demo with an unearned verified JWT result', () => {
    const jwt = skills.find((skill) => skill.id === 'jwt');
    expect(jwt?.status).toBe('EVIDENCE_FOUND');
    expect(initialState.challenge.status).toBe('NOT_STARTED');
    expect(initialState.examination.status).toBe('NOT_STARTED');
  });

  it('keeps the seeded job requirement-by-requirement', () => {
    expect(initialState.job.requirements.map((item) => item.skillName)).toEqual([
      'Java', 'Spring Boot', 'REST API Development', 'PostgreSQL', 'JWT Authentication', 'Testing', 'Docker',
    ]);
    expect(initialState.job.requirements.some((item) => item.status === 'MISSING')).toBe(true);
  });
});
