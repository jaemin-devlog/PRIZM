package com.prizm.careerkeyword.service;

import com.prizm.careerkeyword.model.CareerKeywordCategory;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Deterministically extracts only supported technologies and engineering concepts present in source text. */
@Component
public class CareerKeywordExtractor {

    private static final Pattern TOKEN = Pattern.compile("(?iu)\\.net\\b|[a-z][a-z0-9+#._/-]*|[가-힣]{2,}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final List<String> KOREAN_SUFFIXES = List.of(
            "하였습니다", "되었습니다", "했습니다", "됩니다", "합니다", "하였다", "입니다",
            "으로", "에서", "에게", "까지", "부터", "보다", "처럼", "했다", "하며", "하고",
            "하는", "하여", "이며", "의", "을", "를", "이", "가", "은", "는", "와", "과",
            "로", "에", "도", "만");

    private static final List<TechnologyPhrase> PHRASES = List.of(
            TechnologyPhrase.of("spring data jpa", "Spring Data JPA"),
            TechnologyPhrase.of("amazon web services", "Amazon Web Services"),
            TechnologyPhrase.of("google cloud platform", "Google Cloud Platform"),
            TechnologyPhrase.of("spring security", "Spring Security"),
            TechnologyPhrase.of("github actions", "GitHub Actions"),
            TechnologyPhrase.of("docker compose", "Docker Compose"),
            TechnologyPhrase.of("machine learning", "Machine Learning"),
            TechnologyPhrase.of("apache poi", "Apache POI"),
            TechnologyPhrase.of("spring batch", "Spring Batch"),
            TechnologyPhrase.of("spring cloud", "Spring Cloud"),
            TechnologyPhrase.of("spring boot", "Spring Boot"),
            TechnologyPhrase.of("react native", "React Native"),
            TechnologyPhrase.of("node js", "Node.js"),
            TechnologyPhrase.of("ci cd", "CI/CD"),
            TechnologyPhrase.of("rest api", "REST API"));

    private static final Map<String, String> TECHNICAL_KEYWORDS = Map.ofEntries(
            Map.entry(".net", ".NET"),
            Map.entry("angular", "Angular"),
            Map.entry("ansible", "Ansible"),
            Map.entry("apache", "Apache"),
            Map.entry("api", "API"),
            Map.entry("aws", "AWS"),
            Map.entry("azure", "Azure"),
            Map.entry("backend", "Backend"),
            Map.entry("c#", "C#"),
            Map.entry("c++", "C++"),
            Map.entry("cassandra", "Cassandra"),
            Map.entry("ci/cd", "CI/CD"),
            Map.entry("css", "CSS"),
            Map.entry("db", "DB"),
            Map.entry("django", "Django"),
            Map.entry("docker", "Docker"),
            Map.entry("elasticsearch", "Elasticsearch"),
            Map.entry("fastapi", "FastAPI"),
            Map.entry("fcm", "FCM"),
            Map.entry("figma", "Figma"),
            Map.entry("firebase", "Firebase"),
            Map.entry("flask", "Flask"),
            Map.entry("flyway", "Flyway"),
            Map.entry("frontend", "Frontend"),
            Map.entry("gcp", "GCP"),
            Map.entry("git", "Git"),
            Map.entry("github", "GitHub"),
            Map.entry("go", "Go"),
            Map.entry("gradle", "Gradle"),
            Map.entry("grafana", "Grafana"),
            Map.entry("graphql", "GraphQL"),
            Map.entry("grpc", "gRPC"),
            Map.entry("hibernate", "Hibernate"),
            Map.entry("html", "HTML"),
            Map.entry("java", "Java"),
            Map.entry("java8", "Java8"),
            Map.entry("java11", "Java11"),
            Map.entry("java17", "Java17"),
            Map.entry("java21", "Java21"),
            Map.entry("javascript", "JavaScript"),
            Map.entry("jenkins", "Jenkins"),
            Map.entry("jpa", "JPA"),
            Map.entry("junit", "JUnit"),
            Map.entry("jwt", "JWT"),
            Map.entry("kafka", "Kafka"),
            Map.entry("kotlin", "Kotlin"),
            Map.entry("kubernetes", "Kubernetes"),
            Map.entry("linux", "Linux"),
            Map.entry("mariadb", "MariaDB"),
            Map.entry("maven", "Maven"),
            Map.entry("mongodb", "MongoDB"),
            Map.entry("mysql", "MySQL"),
            Map.entry("nestjs", "NestJS"),
            Map.entry("nginx", "Nginx"),
            Map.entry("node.js", "Node.js"),
            Map.entry("oauth2", "OAuth2"),
            Map.entry("ollama", "Ollama"),
            Map.entry("openapi", "OpenAPI"),
            Map.entry("oracle", "Oracle"),
            Map.entry("outbox", "Outbox"),
            Map.entry("pgvector", "pgvector"),
            Map.entry("php", "PHP"),
            Map.entry("playwright", "Playwright"),
            Map.entry("postgresql", "PostgreSQL"),
            Map.entry("prometheus", "Prometheus"),
            Map.entry("python", "Python"),
            Map.entry("querydsl", "QueryDSL"),
            Map.entry("rabbitmq", "RabbitMQ"),
            Map.entry("react", "React"),
            Map.entry("redis", "Redis"),
            Map.entry("rest", "REST"),
            Map.entry("ruby", "Ruby"),
            Map.entry("rust", "Rust"),
            Map.entry("spring", "Spring"),
            Map.entry("spring boot", "Spring Boot"),
            Map.entry("sql", "SQL"),
            Map.entry("swagger", "Swagger"),
            Map.entry("terraform", "Terraform"),
            Map.entry("testcontainers", "Testcontainers"),
            Map.entry("typescript", "TypeScript"),
            Map.entry("vite", "Vite"),
            Map.entry("vue", "Vue"),
            Map.entry("websocket", "WebSocket"),
            Map.entry("worker", "Worker"),
            Map.entry("데이터베이스", "데이터베이스"),
            Map.entry("동기화", "동기화"),
            Map.entry("동시성", "동시성"),
            Map.entry("모니터링", "모니터링"),
            Map.entry("메시징", "메시징"),
            Map.entry("배포", "배포"),
            Map.entry("백엔드", "백엔드"),
            Map.entry("벡터", "벡터"),
            Map.entry("병렬", "병렬"),
            Map.entry("비동기", "비동기"),
            Map.entry("서버", "서버"),
            Map.entry("선점", "선점"),
            Map.entry("색인", "색인"),
            Map.entry("아웃박스", "아웃박스"),
            Map.entry("이벤트", "이벤트"),
            Map.entry("임베딩", "임베딩"),
            Map.entry("정합성", "정합성"),
            Map.entry("캐시", "캐시"),
            Map.entry("테스트", "테스트"),
            Map.entry("트랜잭션", "트랜잭션"),
            Map.entry("프론트엔드", "프론트엔드"));

    private static final Map<String, String> NORMALIZED_ALIASES = Map.ofEntries(
            Map.entry("net", ".net"),
            Map.entry("amazon web services", "aws"),
            Map.entry("google cloud platform", "gcp"),
            Map.entry("backend", "backend"),
            Map.entry("백엔드", "backend"),
            Map.entry("frontend", "frontend"),
            Map.entry("프론트엔드", "frontend"),
            Map.entry("database", "db"),
            Map.entry("데이터베이스", "db"),
            Map.entry("아웃박스", "outbox"),
            Map.entry("java8", "java"),
            Map.entry("java11", "java"),
            Map.entry("java17", "java"),
            Map.entry("java21", "java"),
            Map.entry("js", "javascript"),
            Map.entry("ts", "typescript"),
            Map.entry("k8s", "kubernetes"),
            Map.entry("nodejs", "node.js"),
            Map.entry("node js", "node.js"),
            Map.entry("postgres", "postgresql"),
            Map.entry("reactjs", "react"),
            Map.entry("springboot", "spring boot"),
            Map.entry("vuejs", "vue"),
            Map.entry("ci cd", "ci/cd"));

    private static final Set<String> LANGUAGE_KEYWORDS = Set.of(
            "c#", "c++", "go", "java", "javascript", "kotlin", "php", "python", "ruby", "rust",
            "typescript");
    private static final Set<String> FRAMEWORK_KEYWORDS = Set.of(
            ".net", "angular", "django", "fastapi", "flask", "hibernate", "jpa", "nestjs", "react",
            "react native", "spring", "spring batch", "spring boot", "spring cloud", "spring data jpa",
            "vue");
    private static final Set<String> DATABASE_KEYWORDS = Set.of(
            "cassandra", "db", "elasticsearch", "mariadb", "mongodb", "mysql", "oracle", "pgvector",
            "postgresql", "redis", "sql");
    private static final Set<String> INFRASTRUCTURE_KEYWORDS = Set.of(
            "ansible", "apache", "aws", "azure", "docker", "docker compose", "gcp", "kubernetes",
            "linux", "nginx", "terraform");
    private static final Set<String> MESSAGING_KEYWORDS = Set.of(
            "fcm", "kafka", "outbox", "rabbitmq", "websocket", "메시징", "이벤트");
    private static final Set<String> SECURITY_KEYWORDS = Set.of("jwt", "oauth2", "spring security");
    private static final Set<String> TESTING_KEYWORDS = Set.of(
            "junit", "playwright", "testcontainers", "테스트");
    private static final Set<String> WEB_KEYWORDS = Set.of(
            "api", "backend", "css", "frontend", "graphql", "grpc", "html", "openapi", "rest",
            "rest api", "swagger", "server", "worker", "서버");
    private static final Set<String> TOOLING_KEYWORDS = Set.of(
            "apache poi", "ci/cd", "figma", "flyway", "git", "github", "github actions", "gradle",
            "jenkins", "maven", "ollama", "vite");

    Map<String, ExtractedKeyword> extract(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }

        boolean[] reserved = new boolean[text.length()];
        Map<String, MutableKeyword> extracted = new LinkedHashMap<>();
        for (TechnologyPhrase phrase : PHRASES) {
            Matcher matcher = phrase.pattern().matcher(text);
            while (matcher.find()) {
                if (isReserved(reserved, matcher.start(), matcher.end())) {
                    continue;
                }
                Arrays.fill(reserved, matcher.start(), matcher.end(), true);
                String normalized = normalizeLookup(phrase.normalized());
                String canonical = TECHNICAL_KEYWORDS.getOrDefault(normalized, phrase.canonical());
                add(
                        extracted,
                        normalized,
                        canonical,
                        categoryFor(normalized),
                        matcher.group(),
                        matcher.start());
            }
        }

        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            if (isReserved(reserved, matcher.start(), matcher.end())) {
                continue;
            }
            String candidate = cleanCandidate(matcher.group());
            String normalized = normalizeLookup(candidate);
            String canonical = TECHNICAL_KEYWORDS.get(normalized);
            if (canonical == null) {
                continue;
            }
            add(
                    extracted,
                    normalized,
                    canonical,
                    categoryFor(normalized),
                    candidate,
                    matcher.start());
        }

        Map<String, ExtractedKeyword> result = new LinkedHashMap<>();
        extracted.forEach((normalized, keyword) -> result.put(normalized, keyword.toValue(normalized)));
        return Map.copyOf(result);
    }

    String normalizeLookup(String keyword) {
        if (keyword == null) {
            return "";
        }
        String normalized = WHITESPACE.matcher(cleanCandidate(keyword).strip())
                .replaceAll(" ")
                .toLowerCase(Locale.ROOT);
        return NORMALIZED_ALIASES.getOrDefault(normalized, normalized);
    }

    private String cleanCandidate(String candidate) {
        int start = 0;
        int end = candidate.length();
        while (start < end && isTrimmable(candidate.charAt(start))) {
            start++;
        }
        while (end > start && isTrimmable(candidate.charAt(end - 1))) {
            end--;
        }
        return stripKoreanSuffix(candidate.substring(start, end));
    }

    private boolean isTrimmable(char value) {
        return value == '.' || value == '/' || value == '_' || value == '-';
    }

    private String stripKoreanSuffix(String candidate) {
        if (candidate.isEmpty() || !candidate.codePoints().allMatch(this::isKoreanSyllable)) {
            return candidate;
        }
        for (String suffix : KOREAN_SUFFIXES) {
            if (candidate.endsWith(suffix) && candidate.length() - suffix.length() >= 2) {
                return candidate.substring(0, candidate.length() - suffix.length());
            }
        }
        return candidate;
    }

    private boolean isKoreanSyllable(int codePoint) {
        return codePoint >= '가' && codePoint <= '힣';
    }

    private boolean isReserved(boolean[] reserved, int start, int end) {
        for (int index = start; index < end; index++) {
            if (reserved[index]) {
                return true;
            }
        }
        return false;
    }

    private CareerKeywordCategory categoryFor(String normalized) {
        if (LANGUAGE_KEYWORDS.contains(normalized)) {
            return CareerKeywordCategory.LANGUAGE;
        }
        if (FRAMEWORK_KEYWORDS.contains(normalized)) {
            return CareerKeywordCategory.FRAMEWORK;
        }
        if (DATABASE_KEYWORDS.contains(normalized)) {
            return CareerKeywordCategory.DATABASE;
        }
        if (INFRASTRUCTURE_KEYWORDS.contains(normalized)) {
            return CareerKeywordCategory.INFRASTRUCTURE;
        }
        if (MESSAGING_KEYWORDS.contains(normalized)) {
            return CareerKeywordCategory.MESSAGING;
        }
        if (SECURITY_KEYWORDS.contains(normalized)) {
            return CareerKeywordCategory.SECURITY;
        }
        if (TESTING_KEYWORDS.contains(normalized)) {
            return CareerKeywordCategory.TESTING;
        }
        if (WEB_KEYWORDS.contains(normalized)) {
            return CareerKeywordCategory.WEB;
        }
        if (TOOLING_KEYWORDS.contains(normalized)) {
            return CareerKeywordCategory.TOOLING;
        }
        return CareerKeywordCategory.ENGINEERING_CONCEPT;
    }

    private void add(
            Map<String, MutableKeyword> extracted,
            String normalized,
            String keyword,
            CareerKeywordCategory category,
            String matchedTerm,
            int index) {
        extracted.compute(normalized, (ignored, current) -> {
            if (current == null) {
                return new MutableKeyword(keyword, category, matchedTerm, index);
            }
            current.frequency++;
            current.addMatchedTerm(matchedTerm);
            if (index < current.firstIndex) {
                current.firstIndex = index;
                current.firstMatchLength = matchedTerm.length();
            }
            return current;
        });
    }

    private record TechnologyPhrase(String normalized, String canonical, Pattern pattern) {

        private static TechnologyPhrase of(String normalized, String canonical) {
            String expression = Arrays.stream(normalized.split(" "))
                    .map(Pattern::quote)
                    .reduce((left, right) -> left + "\\s+" + right)
                    .orElseThrow();
            Pattern pattern = Pattern.compile(
                    "(?iu)(?<![\\p{L}\\p{N}])" + expression + "(?![a-z0-9])");
            return new TechnologyPhrase(normalized, canonical, pattern);
        }
    }

    private static final class MutableKeyword {
        private final String keyword;
        private final CareerKeywordCategory category;
        private final Map<String, String> matchedTerms = new LinkedHashMap<>();
        private int frequency = 1;
        private int firstIndex;
        private int firstMatchLength;

        private MutableKeyword(
                String keyword,
                CareerKeywordCategory category,
                String matchedTerm,
                int firstIndex) {
            this.keyword = keyword;
            this.category = category;
            this.firstIndex = firstIndex;
            this.firstMatchLength = matchedTerm.length();
            addMatchedTerm(matchedTerm);
        }

        private void addMatchedTerm(String matchedTerm) {
            matchedTerms.putIfAbsent(matchedTerm.toLowerCase(Locale.ROOT), matchedTerm);
        }

        private ExtractedKeyword toValue(String normalized) {
            return new ExtractedKeyword(
                    normalized,
                    keyword,
                    category,
                    frequency,
                    firstIndex,
                    firstMatchLength,
                    List.copyOf(new LinkedHashSet<>(matchedTerms.values())));
        }
    }
}
