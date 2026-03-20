# CLAUDE.md — MSA Dependency Graph Generator

## 프로젝트 개요

MSA 프로젝트 소스 코드를 분석하여 서비스 간 의존 관계를 자동으로 탐지하고 그래프로 시각화하는 툴.

- Backend: Kotlin + Spring Boot 3 (포트 8080)
- Frontend: React + Vite (개발 서버: 5173, nginx: 3000)
- DB: PostgreSQL 16 (Flyway 마이그레이션 관리)
- Cache: Redis 7

## 모듈 구조

```
msa-dependency-graph-generator/   ← Gradle root
└── backend/                       ← :backend 서브모듈
    └── src/main/kotlin/com/depgraph/
        ├── config/     Spring 설정 (Security, WebSocket, Cache, Async, Jackson)
        ├── controller/ REST + WebSocket 컨트롤러
        ├── domain/     JPA 엔티티 (Project, Service, Dependency)
        ├── dto/        요청/응답 DTO
        ├── exception/  글로벌 예외 처리
        ├── repository/ Spring Data JPA 리포지토리
        └── service/    비즈니스 로직
            ├── ingestion/  Git 클론 / ZIP 업로드 처리
            └── analyzer/   서비스 탐지 엔진
```

## 개발 규칙

### Gradle 작업 시 항상 모듈 경로 사용

```bash
# 올바름
./gradlew :backend:build
./gradlew :backend:test
./gradlew :backend:bootRun

# 잘못됨 (루트 태스크)
./gradlew build
```

### 빌드 전 컴파일 확인

Bean 설정, DB 매핑, 의존성 변경 후 반드시 컴파일 확인:

```bash
./gradlew :backend:compileKotlin
```

### 테스트

```bash
./gradlew :backend:test
```

H2 인메모리 DB를 사용하는 `application-test.yml` 프로파일로 실행됩니다.

### Frontend 작업

```bash
cd frontend
npm ci          # 의존성 설치
npm run dev     # 개발 서버 (Vite)
npm run build   # 프로덕션 빌드
npm run lint    # ESLint
```

## Docker Compose

| 명령 | 용도 |
|------|------|
| `docker-compose up --build` | 전체 스택 빌드 + 실행 |
| `docker-compose up postgres redis -d` | 개발 시 인프라만 실행 |
| `docker-compose down -v` | 볼륨 포함 완전 삭제 |

## API 엔드포인트 (주요)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/projects` | 프로젝트 목록 |
| POST | `/api/projects` | 프로젝트 생성 |
| POST | `/api/projects/{id}/ingest` | Git URL로 분석 시작 |
| POST | `/api/upload/{id}` | ZIP 파일 업로드 분석 |
| GET | `/api/graph/{projectId}` | 의존성 그래프 조회 |
| WS | `/ws` | WebSocket (STOMP) 엔드포인트 |

## 브랜치 전략

- worktree를 사용하지 않는다.
- main 브랜치에서 직접 작업하고 커밋한다.

## 커밋 메시지

- 커밋 메시지는 한글로 작성한다.
- 예: `feat: 프로젝트 분석 API 추가`, `fix: OAuth 리다이렉트 URL 수정`

## 코딩 컨벤션

### Kotlin

- Kotlin 관용어 적극 사용: `?.`, `?:`, `let`, `also`, `data class`, `sealed class`
- null safety 필수 — `!!` 사용 지양
- 로깅: `io.github.oshai:kotlin-logging-jvm` 사용

### 예외 처리

- `GlobalExceptionHandler`에 새 예외 타입 등록
- 커스텀 예외는 `backend/src/main/kotlin/com/depgraph/exception/Exceptions.kt`에 추가

### DB 스키마 변경

- 항상 Flyway 마이그레이션 파일로 관리 (`V{n}__{description}.sql`)
- 경로: `backend/src/main/resources/db/migration/`

## CI/CD

- `ci.yml`: main push 및 PR 시 빌드/테스트 자동 실행
- `docker.yml`: main push 시 GHCR로 Docker 이미지 자동 배포

이미지 태그: `ghcr.io/<owner>/msa-dependency-graph-generator/backend:latest`
