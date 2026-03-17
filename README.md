# MSA Dependency Graph Generator

MSA(마이크로서비스 아키텍처) 프로젝트의 서비스 간 의존 관계를 자동으로 분석하고 시각화하는 도구입니다.
Git 저장소 URL 또는 ZIP 파일을 업로드하면 서비스 구조와 의존 관계를 그래프로 표현합니다.

## 주요 기능

- Git 저장소 클론 또는 ZIP 파일 업로드를 통한 소스 코드 분석
- 서비스 간 의존 관계 자동 탐지 (REST, 이벤트, DB 공유 등)
- 실시간 분석 진행 상황 WebSocket 스트리밍
- 인터랙티브 의존성 그래프 시각화
- 프로젝트별 분석 이력 관리

## 기술 스택

| 영역 | 기술 |
|------|------|
| Backend | Kotlin, Spring Boot 3, Spring WebFlux, WebSocket (STOMP) |
| Database | PostgreSQL 16, Flyway |
| Cache | Redis 7 |
| Frontend | Node.js 20, React (Vite), nginx |
| Container | Docker, Docker Compose |
| CI/CD | GitHub Actions, GHCR |

## 로컬 실행 (Docker Compose)

### 사전 요구사항

- Docker Desktop (또는 Docker Engine + Docker Compose)

### 전체 스택 실행

```bash
docker-compose up --build
```

서비스가 시작되면:

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080
- PostgreSQL: localhost:5432
- Redis: localhost:6379

### 서비스 종료

```bash
docker-compose down
```

볼륨 포함 완전 삭제:

```bash
docker-compose down -v
```

## 개발 환경 설정

### Backend (Kotlin / Spring Boot)

#### 사전 요구사항

- JDK 21 (Eclipse Temurin 권장)
- Docker (로컬 PostgreSQL/Redis 실행용)

#### 인프라 실행 (DB + Redis만)

```bash
docker-compose up postgres redis -d
```

#### 애플리케이션 실행

```bash
./gradlew :backend:bootRun
```

`application-local.yml` 프로파일이 자동 적용됩니다.

#### 테스트 실행

```bash
./gradlew :backend:test
```

#### 빌드

```bash
./gradlew :backend:build
```

### Frontend (Node.js / React)

#### 사전 요구사항

- Node.js 20+
- npm

#### 의존성 설치

```bash
cd frontend
npm ci
```

#### 개발 서버 실행

```bash
npm run dev
```

#### 프로덕션 빌드

```bash
npm run build
```

## 프로젝트 구조

```
msa-dependency-graph-generator/
├── backend/                    # Spring Boot 애플리케이션
│   ├── src/main/kotlin/
│   │   └── com/depgraph/
│   │       ├── config/         # Spring 설정 (Security, WebSocket, Cache 등)
│   │       ├── controller/     # REST / WebSocket 컨트롤러
│   │       ├── domain/         # JPA 엔티티
│   │       ├── dto/            # 요청/응답 DTO
│   │       ├── exception/      # 글로벌 예외 처리
│   │       ├── repository/     # Spring Data JPA 리포지토리
│   │       └── service/        # 비즈니스 로직, 분석 엔진
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── application-local.yml
│   │   └── db/migration/       # Flyway 마이그레이션
│   └── Dockerfile
├── frontend/                   # React SPA
│   ├── src/
│   ├── Dockerfile
│   └── nginx.conf
├── .github/workflows/          # CI/CD 파이프라인
│   ├── ci.yml
│   └── docker.yml
├── docker-compose.yml
└── settings.gradle.kts
```

## CI/CD

| 워크플로우 | 트리거 | 동작 |
|-----------|--------|------|
| `ci.yml` | push to main, PR | 백엔드 빌드/테스트, 프론트엔드 빌드/린트 |
| `docker.yml` | push to main | Docker 이미지 빌드 후 GHCR 푸시 |

Docker 이미지는 `ghcr.io/<owner>/msa-dependency-graph-generator/backend:latest` 및 `.../frontend:latest` 로 게시됩니다.

## 라이선스

MIT
