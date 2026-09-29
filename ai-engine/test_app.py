from fastapi.testclient import TestClient

from app import app

client = TestClient(app)


def test_health():
    response = client.get('/health')
    assert response.status_code == 200
    assert response.json()['service'] == 'skilllink-ai-engine'


def test_analyze_requires_grounded_references_for_observations():
    response = client.post('/v1/analyze', json={
        'repository_id': 'repo-1',
        'project_id': 'project-1',
        'commit_sha': 'abc1234',
        'deterministic_facts': {
            'languages': ['Java'],
            'skill_observations': [
                {'skill_key': 'java', 'observations': ['parsed'], 'evidence_references': []},
                {'skill_key': 'spring-boot', 'observations': ['manifest'], 'evidence_references': [{
                    'source_type': 'DEPENDENCY', 'location': 'pom.xml', 'source_hash': 'blob',
                    'observation': 'Spring Boot declared', 'strength': 'MODERATE'
                }]},
            ],
        },
    })
    assert response.status_code == 200
    payload = response.json()
    assert [item['skill_key'] for item in payload['skills']] == ['spring-boot']
    assert payload['grounding_warnings'] == []


def test_examiner_context_is_reference_bound():
    response = client.post('/v1/examiner/questions', json={
        'project_id': 'project-1',
        'snapshot_id': 'snapshot-1',
        'skill_keys': ['jwt-authentication'],
        'evidence_references': [{
            'source_type': 'STATIC_ANALYSIS', 'location': 'src/JwtService.java',
            'source_hash': 'blob-jwt', 'observation': 'JWT parser call', 'strength': 'DIRECT'
        }],
    })
    assert response.status_code == 200
    questions = response.json()
    assert questions
    assert all(question['context_references'][0]['location'] == 'src/JwtService.java' for question in questions)
    assert all('src/JwtService.java' in question['expected_signals'] for question in questions)
