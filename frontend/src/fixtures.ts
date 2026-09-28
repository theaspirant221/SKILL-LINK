import type {
  CandidateProfile,
  DemoState,
  Evidence,
  Examination,
  Job,
  JobRequirement,
  PracticalChallenge,
  Project,
  Repository,
  Skill,
} from './domain';

export const candidate: CandidateProfile = {
  name: 'Aman Gautam',
  initials: 'AG',
  headline: 'Java Backend Developer',
  location: 'Dehradun, India',
  institution: 'Graphic Era University',
  graduation: '2026',
  verificationProgress: 68,
};

export const repository: Repository = {
  id: 'repo-foodbridge',
  name: 'FoodBridge API',
  fullName: 'aman-gautam/foodbridge-api',
  description: 'A secure food donation and delivery API for local community kitchens.',
  branch: 'main',
  commitSha: 'abc1234d9e7f',
  commitLabel: 'abc1234',
  visibility: 'PUBLIC',
  languages: ['Java', 'SQL'],
  frameworks: ['Spring Boot', 'Spring Security', 'Spring Data JPA'],
  updatedAt: '28 Sep 2026',
  isFixture: true,
};

export const project: Project = {
  id: 'project-foodbridge',
  name: 'FoodBridge API',
  kind: 'Backend service',
  summary: 'Secure REST API that coordinates donors, kitchens, and delivery volunteers with role-aware access and persisted order state.',
  repository,
  analysisStatus: 'NOT_CONNECTED',
  fileCount: 84,
  testCount: 18,
  analysisStages: [
    'Reading repository structure',
    'Detecting technologies',
    'Inspecting architecture',
    'Locating tests',
    'Mapping skill evidence',
    'Preparing proof',
  ],
  completedStages: 0,
};

export const skills: Skill[] = [
  {
    id: 'java', name: 'Java', category: 'Backend engineering', status: 'VERIFIED', freshness: 'CURRENT',
    lastVerifiedAt: '25 Sep 2026', latestEvidenceAt: '28 Sep 2026', evidenceCount: 12,
    summary: 'Core language usage is corroborated across controllers, services, domain models, and tests.',
    methods: ['Repository evidence', 'Project defense'], relatedProjects: ['FoodBridge API', 'Expense Management System'],
  },
  {
    id: 'spring-boot', name: 'Spring Boot', category: 'Backend engineering', status: 'VERIFIED', freshness: 'CURRENT',
    lastVerifiedAt: '25 Sep 2026', latestEvidenceAt: '28 Sep 2026', evidenceCount: 9,
    summary: 'Application wiring, REST controllers, services, repositories, and configuration are visible in the snapshot.',
    methods: ['Repository evidence', 'Project defense', 'Practical verification'], relatedProjects: ['FoodBridge API'],
  },
  {
    id: 'rest-api', name: 'REST API Development', category: 'API design', status: 'VERIFIED', freshness: 'CURRENT',
    lastVerifiedAt: '25 Sep 2026', latestEvidenceAt: '28 Sep 2026', evidenceCount: 7,
    summary: 'Resource-oriented endpoints, validation, status codes, and error handling are present.',
    methods: ['Repository evidence', 'Project defense'], relatedProjects: ['FoodBridge API'],
  },
  {
    id: 'postgresql', name: 'PostgreSQL', category: 'Data', status: 'VERIFIED', freshness: 'CURRENT',
    lastVerifiedAt: '21 Sep 2026', latestEvidenceAt: '28 Sep 2026', evidenceCount: 6,
    summary: 'Relational persistence is backed by datasource configuration, JPA mappings, and integration tests.',
    methods: ['Dependency analysis', 'Repository evidence'], relatedProjects: ['FoodBridge API'],
  },
  {
    id: 'jwt', name: 'JWT Authentication', category: 'Security', status: 'EVIDENCE_FOUND', freshness: 'CURRENT',
    latestEvidenceAt: '28 Sep 2026', evidenceCount: 4,
    summary: 'Token generation and request filtering are observable; candidate defense and a practical authorization change are still required.',
    methods: ['Repository evidence'], relatedProjects: ['FoodBridge API'],
  },
  {
    id: 'testing', name: 'Testing', category: 'Quality', status: 'PARTIAL', freshness: 'AGING',
    lastVerifiedAt: '11 Aug 2026', latestEvidenceAt: '28 Sep 2026', evidenceCount: 4,
    summary: 'Unit and integration tests are present, but coverage of authorization failure paths needs more proof.',
    methods: ['Repository evidence'], relatedProjects: ['FoodBridge API'],
  },
  {
    id: 'docker', name: 'Docker', category: 'Delivery', status: 'NOT_VERIFIED', freshness: 'NOT_APPLICABLE',
    latestEvidenceAt: '—', evidenceCount: 0,
    summary: 'No repository-backed evidence has been accepted yet.', methods: [], relatedProjects: [],
  },
];

export const evidence: Evidence[] = [
  {
    id: 'ev-jwt-service', skillId: 'jwt', skillName: 'JWT Authentication', sourceType: 'STATIC_ANALYSIS', sourceLabel: 'Direct implementation', location: 'src/main/java/com/foodbridge/security/JwtService.java',
    observation: 'Token generation and signature validation methods are present. The analyzer found issuer and expiry handling in the service.', strength: 'DIRECT', status: 'OBSERVED', observedAt: '28 Sep 2026', snapshot: 'abc1234', visibility: 'PUBLIC_SUMMARY', projectId: 'project-foodbridge', independentSignal: 'Referenced by JwtAuthenticationFilter',
  },
  {
    id: 'ev-jwt-filter', skillId: 'jwt', skillName: 'JWT Authentication', sourceType: 'STATIC_ANALYSIS', sourceLabel: 'Request pipeline', location: 'src/main/java/com/foodbridge/security/JwtAuthenticationFilter.java',
    observation: 'Bearer token extraction and SecurityContext population are wired into the request filter chain.', strength: 'DIRECT', status: 'OBSERVED', observedAt: '28 Sep 2026', snapshot: 'abc1234', visibility: 'PUBLIC_SUMMARY', projectId: 'project-foodbridge', independentSignal: 'Filter is registered in SecurityConfig',
  },
  {
    id: 'ev-security-config', skillId: 'spring-boot', skillName: 'Spring Boot', sourceType: 'STATIC_ANALYSIS', sourceLabel: 'Framework configuration', location: 'src/main/java/com/foodbridge/security/SecurityConfig.java',
    observation: 'Spring Security filter-chain configuration defines authenticated routes and disables session state for the API.', strength: 'DIRECT', status: 'OBSERVED', observedAt: '28 Sep 2026', snapshot: 'abc1234', visibility: 'PUBLIC_SUMMARY', projectId: 'project-foodbridge', independentSignal: 'Uses @Bean SecurityFilterChain',
  },
  {
    id: 'ev-pom', skillId: 'spring-boot', skillName: 'Spring Boot', sourceType: 'DEPENDENCY', sourceLabel: 'Dependency manifest', location: 'pom.xml',
    observation: 'Spring Boot web, validation, security, data-jpa, and PostgreSQL driver dependencies are declared.', strength: 'STRONG', status: 'OBSERVED', observedAt: '28 Sep 2026', snapshot: 'abc1234', visibility: 'PUBLIC_SUMMARY', projectId: 'project-foodbridge', independentSignal: 'Corroborated by source annotations',
  },
  {
    id: 'ev-orders-controller', skillId: 'rest-api', skillName: 'REST API Development', sourceType: 'STATIC_ANALYSIS', sourceLabel: 'Endpoint implementation', location: 'src/main/java/com/foodbridge/orders/OrderController.java',
    observation: 'Order resources expose validated request DTOs, explicit HTTP responses, and a protected route for status changes.', strength: 'DIRECT', status: 'OBSERVED', observedAt: '28 Sep 2026', snapshot: 'abc1234', visibility: 'RECRUITER_SHARED', projectId: 'project-foodbridge', independentSignal: 'Controller delegates to OrderService',
  },
  {
    id: 'ev-repository', skillId: 'postgresql', skillName: 'PostgreSQL', sourceType: 'STATIC_ANALYSIS', sourceLabel: 'Persistence mapping', location: 'src/main/java/com/foodbridge/orders/OrderRepository.java',
    observation: 'Spring Data repository and entity mappings persist order state to a PostgreSQL datasource.', strength: 'STRONG', status: 'OBSERVED', observedAt: '28 Sep 2026', snapshot: 'abc1234', visibility: 'PUBLIC_SUMMARY', projectId: 'project-foodbridge', independentSignal: 'application-test.yml uses PostgreSQL test profile',
  },
  {
    id: 'ev-tests', skillId: 'testing', skillName: 'Testing', sourceType: 'STATIC_ANALYSIS', sourceLabel: 'Test suite', location: 'src/test/java/com/foodbridge/orders/OrderServiceTest.java',
    observation: 'Service behavior is covered for successful order creation and duplicate order rejection; authorization failure coverage is not yet observed.', strength: 'MODERATE', status: 'OBSERVED', observedAt: '28 Sep 2026', snapshot: 'abc1234', visibility: 'PUBLIC_SUMMARY', projectId: 'project-foodbridge', independentSignal: '18 test files detected in snapshot',
  },
];

export const examination: Examination = {
  id: 'exam-foodbridge-jwt', projectId: 'project-foodbridge', status: 'NOT_STARTED', currentIndex: 0, answers: [],
  questions: [
    {
      id: 'q-location', category: 'CODE_LOCATION',
      prompt: 'Where does this project validate a bearer token, and how does the request reach a controller after validation?',
      context: 'The snapshot contains JwtService.java, JwtAuthenticationFilter.java, and SecurityConfig.java.',
      expectedSignals: ['JwtAuthenticationFilter', 'JwtService', 'SecurityContext', 'filter chain'],
    },
    {
      id: 'q-architecture', category: 'DESIGN_DECISION',
      prompt: 'Why did you use JWT rather than server-side sessions for FoodBridge, and what trade-off does that introduce?',
      context: 'The security configuration disables session state and authenticates API requests with a bearer token.',
      expectedSignals: ['stateless', 'session', 'scale', 'revocation', 'expiry'],
    },
    {
      id: 'q-security', category: 'SECURITY',
      prompt: 'A user with a valid token can call an order status endpoint intended for kitchen staff. What would you inspect and change?',
      context: 'The current evidence proves authentication wiring, but not role authorization for every protected route.',
      expectedSignals: ['role', 'authority', 'PreAuthorize', 'SecurityConfig', 'test'],
    },
  ],
};

export const challenge: PracticalChallenge = {
  id: 'challenge-role-authorization', skillId: 'jwt', title: 'Close the role-authorization gap',
  brief: 'Kitchen staff report that any authenticated user can change an order status. Add a role guard to the endpoint and cover the failure path with a regression test.',
  acceptanceCriteria: [
    'Only users with the KITCHEN_MANAGER authority can change order status.',
    'Unauthenticated and unauthorized requests receive a safe 401/403 response.',
    'A regression test demonstrates the denied path.',
    'The change is explained in terms of the existing filter and controller flow.',
  ],
  starterCode: `@PatchMapping("/{orderId}/status")\npublic ResponseEntity<OrderResponse> updateStatus(\n    @PathVariable UUID orderId,\n    @Valid @RequestBody UpdateOrderStatusRequest request) {\n  return ResponseEntity.ok(orderService.updateStatus(orderId, request));\n}`,
  status: 'NOT_STARTED',
};

const jobRequirements: JobRequirement[] = [
  { id: 'req-java', skillId: 'java', skillName: 'Java', kind: 'REQUIRED', status: 'VERIFIED', proofNote: '12 evidence items across two projects; latest defense 25 Sep 2026.' },
  { id: 'req-spring', skillId: 'spring-boot', skillName: 'Spring Boot', kind: 'REQUIRED', status: 'VERIFIED', proofNote: 'FoodBridge API contains controllers, services, repositories, and configuration.' },
  { id: 'req-rest', skillId: 'rest-api', skillName: 'REST API Development', kind: 'REQUIRED', status: 'VERIFIED', proofNote: 'Validated resource endpoints observed in OrderController.java.' },
  { id: 'req-postgres', skillId: 'postgresql', skillName: 'PostgreSQL', kind: 'REQUIRED', status: 'VERIFIED', proofNote: 'Datasource, JPA mappings, and repository signals observed.' },
  { id: 'req-jwt', skillId: 'jwt', skillName: 'JWT Authentication', kind: 'REQUIRED', status: 'EVIDENCE_FOUND', proofNote: 'Implementation is observable; targeted defense and practical verification are still open.' },
  { id: 'req-testing', skillId: 'testing', skillName: 'Testing', kind: 'REQUIRED', status: 'PARTIAL', proofNote: 'Tests exist; authorization failure-path evidence is incomplete.' },
  { id: 'req-docker', skillId: 'docker', skillName: 'Docker', kind: 'PREFERRED', status: 'MISSING', proofNote: 'No accepted Docker evidence in the current snapshot.' },
];

export const seededJob: Job = {
  id: 'job-java-backend', title: 'Junior Java Backend Developer', company: 'Northstar Labs', location: 'Bengaluru · Hybrid', createdAt: '28 Sep 2026',
  sourceDescription: 'Build secure Spring Boot services, design REST APIs, work with PostgreSQL, and write tests. Docker experience is preferred.', requirements: jobRequirements,
};

export const initialState: DemoState = {
  signedIn: false,
  role: 'candidate',
  candidate,
  githubConnected: false,
  project,
  evidence,
  skills,
  examination,
  challenge,
  job: seededJob,
};
