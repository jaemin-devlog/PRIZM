package com.prizm.search.profile;

import static com.prizm.search.profile.ClaimSupportDecision.Reason.ACTION_NOT_SUPPORTED;
import static com.prizm.search.profile.ClaimSupportDecision.Reason.ACTOR_MISMATCH;
import static com.prizm.search.profile.ClaimSupportDecision.Reason.DIRECT_CLAIM_SUPPORT;
import static com.prizm.search.profile.ClaimSupportDecision.Reason.ENTITY_NOT_BOUND_TO_ACTION;
import static com.prizm.search.profile.ClaimSupportDecision.Reason.INSUFFICIENT_CLAIM_SUPPORT;
import static com.prizm.search.profile.ClaimSupportDecision.Reason.METRIC_MISMATCH;
import static com.prizm.search.profile.ClaimSupportDecision.Reason.NEGATED_TARGET_CLAIM;
import static com.prizm.search.profile.ClaimSupportDecision.Reason.NON_CLAIM_QUERY;
import static com.prizm.search.profile.ClaimSupportDecision.Reason.NOT_ADOPTED;
import static com.prizm.search.profile.ClaimSupportDecision.Reason.NUMERIC_VALUE_MISMATCH;
import static com.prizm.search.profile.ClaimSupportDecision.Reason.STATE_MISMATCH;
import static com.prizm.search.profile.ClaimSupportDecision.Reason.UNIT_MISMATCH;
import static com.prizm.search.profile.ClaimSupportDecision.Status.CONTRADICTED;
import static com.prizm.search.profile.ClaimSupportDecision.Status.SUPPORTED;
import static com.prizm.search.profile.ClaimSupportDecision.Status.UNSUPPORTED;

import com.prizm.search.profile.QueryClaimRequirements.Action;
import com.prizm.search.profile.QueryClaimRequirements.Direction;
import com.prizm.search.profile.QueryClaimRequirements.Metric;
import com.prizm.search.profile.QueryClaimRequirements.NumericConstraint;
import com.prizm.search.profile.QueryClaimRequirements.Polarity;
import com.prizm.search.profile.QueryClaimRequirements.State;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic, local-window claim support used only for production eligibility. */
public final class StructuredClaimSupportEvaluator {

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}+#._-]*");
    private static final Pattern ASCII_ENTITY = Pattern.compile("^[a-z][a-z0-9+.#_-]*$");
    private static final Pattern CLAIM_QUESTION_ENDING = Pattern.compile(
            ".*(?:나요|는가|인가요|있나요)\\s*[?!.。！？]*$");
    private static final Pattern NUMERIC = Pattern.compile(
            "(?<![\\p{L}\\p{N}])(?<value>\\d[\\d,]*(?:\\.\\d+)?)\\s*"
                    + "(?<scale>만|천)?\\s*"
                    + "(?<unit>밀리초|ms|초|분|시간|퍼센트|%|건|회|개|명|번|행|기가바이트|테라바이트|gb|tb)?",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> GENERIC_ENTITIES = Set.of(
            "api", "app", "application", "backend", "batch", "candidate", "claim",
            "data", "db", "direct", "evidence", "experience", "frontend", "key",
            "metadata", "metric", "mobile", "production", "project", "query", "response", "result",
            "service", "session", "system", "team", "user", "worker", "workflow",
            "p50", "p90", "p95", "p99", "ms", "gb", "tb");
    private static final Set<String> SUBJECT_STOP_WORDS = Set.of(
            "경험", "근거", "사용", "구현", "적용", "운영", "배포", "생산", "환경",
            "직접", "문제", "어떻게", "있나요", "했나요", "인가요", "했어", "있어",
            "개선", "줄였", "낮췄", "해결", "복구", "구조", "변경", "실제", "현재",
            "그리고", "또는", "통해", "대한", "관련", "수행", "만든", "사용한");
    private static final List<String> OTHER_ACTOR_MARKERS = List.of(
            "다른 팀", "타 팀", "협력 업체", "외부 업체", "거버넌스 팀", "네트워크 팀",
            "추천 팀", "별도 팀", "다른 조직", "타 조직", "담당자가");
    private static final Pattern OTHER_ACTOR_SUBJECT = Pattern.compile(
            "(?:(?:다른|타|외부|협력|별도)\\s*(?:[\\p{L}\\p{N}]+\\s*)?"
                    + "(?:팀|업체|조직|담당자|협력사)|(?:[\\p{L}\\p{N}]+\\s+)?파트너)"
                    + "(?:이|가|에서|은|는)");
    private static final List<String> FIRST_PERSON_MARKERS = List.of(
            "본인이", "내가", "직접", "담당했다", "수행했다");
    private static final List<String> NOT_ADOPTED_MARKERS = List.of(
            "도입하지 않았", "채택하지 않았", "사용하지 않았", "운영하지 않았",
            "적용하지 않았", "포함하지 않았", "연결하지 않았", "배포하지 않았",
            "도입하지 않", "채택하지 않", "사용하지 않", "운영하지 않",
            "적용하지 않", "포함하지 않", "연결하지 않", "배포하지 않",
            "제거했다", "제거했", "제거됐다", "제거되었");
    private static final Pattern TECHNOLOGY_DECLARATION = Pattern.compile(
            "(?:사용\\s*기술|기술(?:\\s*스택)?|tech(?:nology)?\\s*stack|stack|skills?)\\s*[:：]");
    private static final Pattern PROJECT_SCOPE_MARKER = Pattern.compile(
            "(?:프로젝트|project|서비스|service|제품|product|시스템|system)");
    private static final List<String> PROTOTYPE_MARKERS = List.of(
            "prototype", "프로토타입", "검증 환경", "실험에서", "교육용", "후보로", "비교했");
    private static final List<String> HISTORICAL_MARKERS = List.of(
            "과거", "이전 회사", "이전 조직", "현재 플랫폼에서는", "현재 .* 아니다");
    private static final List<String> PRODUCTION_MARKERS = List.of(
            "production", "생산 환경", "생산 앱", "생산 로봇", "생산 분석", "생산 트래픽",
            "실제 시스템", "실제 정산", "운영 중", "운영 환경", "배포했다", "배포했다",
            "배포했", "출시했", "적용됐다", "적용했다");
    private static final List<String> NON_ASSERTIVE_MARKERS = List.of(
            "계획", "검토", "예제", "질문", "자동화만", "출시일", "배포판",
            "재배포 절차", "라고 전했다",
            "사실이 아니다");

    public QueryClaimRequirements extract(String query) {
        String normalized = normalize(query);
        Set<Action> actions = extractActions(normalized);
        Set<String> entities = entities(normalized);
        List<NumericConstraint> numeric = numericConstraints(normalized);
        boolean claimQuestion = isClaimQuestion(normalized, actions, entities, numeric);
        State state = requiredState(normalized, actions);
        return new QueryClaimRequirements(
                claimQuestion,
                claimQuestion,
                actions,
                entities,
                subjectTerms(normalized, entities),
                claimQuestion ? Polarity.POSITIVE : Polarity.UNSPECIFIED,
                state,
                metric(normalized),
                numeric,
                direction(normalized));
    }

    public ClaimSupportDecision evaluate(String query, String candidateContent) {
        return evaluate(extract(query), candidateContent);
    }

    public ClaimSupportDecision evaluate(
            QueryClaimRequirements requirements,
            String candidateContent) {
        if (!requirements.claimQuestion()) {
            return new ClaimSupportDecision(SUPPORTED, Set.of(NON_CLAIM_QUERY), false, "");
        }
        String normalizedContent = normalize(candidateContent);
        if (normalizedContent.contains("?")
                || normalizedContent.contains("？")
                || normalizedContent.matches(".*(?:맞습니까|습니까)\\s*[.!。！]*$")) {
            return new ClaimSupportDecision(
                    UNSUPPORTED, Set.of(INSUFFICIENT_CLAIM_SUPPORT), false, "");
        }
        if (containsAny(normalizedContent, List.of(
                "내용을 정정", "주장을 정정", "이를 철회", "주장을 철회", "주장은 철회",
                "내용을 번복", "발언을 거둡", "앞 문장을 부인", "사실이 아닙", "거짓입니다",
                "실제로는 하지 않았"))) {
            return new ClaimSupportDecision(
                    CONTRADICTED, Set.of(NEGATED_TARGET_CLAIM), false, "");
        }
        if (containsAny(normalizedContent, List.of("아니,", "아니.", "맞나요", "맞습니까"))) {
            return new ClaimSupportDecision(
                    CONTRADICTED, Set.of(NEGATED_TARGET_CLAIM), false, "");
        }

        List<String> windows = claimWindows(candidateContent);
        for (String window : windows) {
            WindowEvaluation evaluation = evaluateWindow(requirements, window);
            if (evaluation.supported()) {
                return new ClaimSupportDecision(
                        SUPPORTED,
                        Set.of(DIRECT_CLAIM_SUPPORT),
                        true,
                        window);
            }
        }
        if (hasProjectScopedTechnologyUsage(requirements, normalizedContent)) {
            return new ClaimSupportDecision(
                    SUPPORTED,
                    Set.of(DIRECT_CLAIM_SUPPORT),
                    true,
                    normalizedContent);
        }

        EnumSet<ClaimSupportDecision.Reason> contradictions = EnumSet.noneOf(
                ClaimSupportDecision.Reason.class);
        EnumSet<ClaimSupportDecision.Reason> missing = EnumSet.noneOf(
                ClaimSupportDecision.Reason.class);
        boolean related = false;
        for (String window : windows) {
            WindowEvaluation evaluation = evaluateWindow(requirements, window);
            if (!evaluation.related()) {
                continue;
            }
            related = true;
            contradictions.addAll(evaluation.contradictions());
            missing.addAll(evaluation.missing());
        }
        if (!contradictions.isEmpty()) {
            return new ClaimSupportDecision(CONTRADICTED, contradictions, false, "");
        }
        if (!related) {
            if (!requirements.entities().isEmpty()) {
                missing.add(ENTITY_NOT_BOUND_TO_ACTION);
            }
            missing.add(INSUFFICIENT_CLAIM_SUPPORT);
        }
        if (missing.isEmpty()) {
            missing.add(INSUFFICIENT_CLAIM_SUPPORT);
        }
        return new ClaimSupportDecision(UNSUPPORTED, missing, false, "");
    }

    private WindowEvaluation evaluateWindow(
            QueryClaimRequirements requirements,
            String window) {
        Set<String> windowEntities = entities(window);
        Set<Action> windowActions = extractActions(window);
        Set<NumericConstraint> windowNumeric = Set.copyOf(numericConstraints(window));
        Set<Metric> windowMetrics = metrics(window);
        int subjectMatches = matchedSubjectTerms(requirements.subjectTerms(), window);
        boolean entityRelated = !requirements.entities().isEmpty()
                && requirements.entities().stream().anyMatch(
                        entity -> entityMentioned(entity, windowEntities, window));
        boolean numericRelated = !requirements.numericConstraints().isEmpty()
                && requirements.numericConstraints().stream().anyMatch(windowNumeric::contains);
        boolean metricRelated = requirements.metric() != Metric.UNKNOWN
                && windowMetrics.contains(requirements.metric());
        boolean related = entityRelated
                || numericRelated
                || metricRelated
                || subjectMatches >= requiredSubjectMatches(requirements);

        EnumSet<ClaimSupportDecision.Reason> contradictions = EnumSet.noneOf(
                ClaimSupportDecision.Reason.class);
        EnumSet<ClaimSupportDecision.Reason> missing = EnumSet.noneOf(
                ClaimSupportDecision.Reason.class);
        boolean projectScopedTechnologyDeclaration = isProjectScopedTechnologyDeclaration(
                requirements, window, windowEntities);

        if (!requirements.entities().isEmpty()
                && !requirements.entities().stream()
                        .allMatch(entity -> entityMentioned(entity, windowEntities, window))) {
            missing.add(ENTITY_NOT_BOUND_TO_ACTION);
        }
        if (!projectScopedTechnologyDeclaration
                && !requirements.actions().isEmpty()
                && !supportsActions(requirements.actions(), windowActions, window)) {
            missing.add(ACTION_NOT_SUPPORTED);
        }
        if (requirements.actorRequired()
                && hasOtherActorSubject(window)
                && !containsAny(window, FIRST_PERSON_MARKERS)) {
            contradictions.add(ACTOR_MISMATCH);
        }
        if (isTargetNegated(requirements, window)) {
            contradictions.add(NEGATED_TARGET_CLAIM);
        }
        if (isNotAdopted(requirements, window)) {
            contradictions.add(NOT_ADOPTED);
        }
        evaluateState(requirements, window, contradictions, missing);
        evaluateMetric(requirements, windowMetrics, window, contradictions, missing);
        evaluateNumeric(requirements, windowNumeric, contradictions, missing);

        int requiredSubjects = requiredSubjectMatches(requirements);
        if (requirements.entities().isEmpty()
                && requirements.numericConstraints().isEmpty()
                && subjectMatches < requiredSubjects) {
            missing.add(INSUFFICIENT_CLAIM_SUPPORT);
        }
        boolean unknownVocabularySupport = !missing.isEmpty()
                && missing.stream().allMatch(reason -> reason == ACTION_NOT_SUPPORTED
                        || reason == METRIC_MISMATCH)
                && (!missing.contains(ACTION_NOT_SUPPORTED) || windowActions.isEmpty())
                && (!missing.contains(METRIC_MISMATCH) || windowMetrics.isEmpty())
                && directlyBindsClaim(requirements, window, windowEntities, windowNumeric, subjectMatches)
                && isAffirmativeClaimWindow(window);
        boolean supported = contradictions.isEmpty()
                && (unknownVocabularySupport
                        || missing.isEmpty()
                                && hasAffirmativeEvidence(
                                        requirements,
                                        window,
                                        windowActions,
                                        projectScopedTechnologyDeclaration));
        return new WindowEvaluation(supported, related, contradictions, missing);
    }

    private static boolean directlyBindsClaim(
            QueryClaimRequirements requirements,
            String window,
            Set<String> windowEntities,
            Set<NumericConstraint> windowNumeric,
            int subjectMatches) {
        boolean entitiesBound = requirements.entities().stream()
                .allMatch(entity -> entityMentioned(entity, windowEntities, window));
        boolean numericBound = requirements.numericConstraints().stream().allMatch(windowNumeric::contains);
        int requiredSubjects = requiredSubjectMatches(requirements);
        boolean subjectsBound = subjectMatches >= requiredSubjects;
        boolean hasDirectAnchor = !requirements.entities().isEmpty()
                || !requirements.numericConstraints().isEmpty()
                || requiredSubjects > 0;
        return hasDirectAnchor && entitiesBound && numericBound && subjectsBound;
    }

    private static boolean isProjectScopedTechnologyDeclaration(
            QueryClaimRequirements requirements,
            String window,
            Set<String> windowEntities) {
        return requirements.actions().equals(Set.of(Action.USE))
                && requirements.metric() == Metric.UNKNOWN
                && requirements.numericConstraints().isEmpty()
                && requirements.requiredState() != State.PRODUCTION
                && !containsAny(window, PROTOTYPE_MARKERS)
                && (TECHNOLOGY_DECLARATION.matcher(window).find()
                        || (window.contains("기술") && windowEntities.size() >= 2))
                && (PROJECT_SCOPE_MARKER.matcher(window).find() || windowEntities.size() >= 2);
    }

    private static boolean hasProjectScopedTechnologyUsage(
            QueryClaimRequirements requirements,
            String content) {
        Set<String> contentEntities = entities(content);
        if (!requirements.actions().equals(Set.of(Action.USE))
                || requirements.metric() != Metric.UNKNOWN
                || !requirements.numericConstraints().isEmpty()
                || requirements.requiredState() == State.PRODUCTION
                || !containsTechnologyDeclaration(content)
                || !(PROJECT_SCOPE_MARKER.matcher(content).find() || contentEntities.size() >= 2)
                || containsAny(content, List.of("검토", "비교", "후보"))
                || isTargetNegated(requirements, content)
                || isNotAdopted(requirements, content)
                || requirements.actorRequired() && containsAny(content, OTHER_ACTOR_MARKERS)
                        && !containsAny(content, FIRST_PERSON_MARKERS)) {
            return false;
        }
        return requirements.entities().stream()
                .allMatch(entity -> entityMentioned(entity, contentEntities, content));
    }

    private static boolean containsTechnologyDeclaration(String value) {
        return TECHNOLOGY_DECLARATION.matcher(value).find() || value.contains("기술");
    }

    private static boolean entityMentioned(String entity, Set<String> windowEntities, String window) {
        if (windowEntities.contains(entity)) {
            return true;
        }
        if (!ASCII_ENTITY.matcher(entity).matches() || entity.length() < 4) {
            return false;
        }
        String compactWindow = window.replaceAll("[^a-z0-9+#]", "");
        return compactWindow.contains(entity);
    }

    private static void evaluateState(
            QueryClaimRequirements requirements,
            String window,
            Set<ClaimSupportDecision.Reason> contradictions,
            Set<ClaimSupportDecision.Reason> missing) {
        if (requirements.requiredState() == State.ANY) {
            return;
        }
        boolean historical = containsAny(window, HISTORICAL_MARKERS);
        boolean prototype = containsAny(window, PROTOTYPE_MARKERS);
        boolean production = containsAny(window, PRODUCTION_MARKERS);
        if (requirements.requiredState() == State.PRODUCTION) {
            if (historical || prototype && !production) {
                contradictions.add(STATE_MISMATCH);
            } else if (!production) {
                missing.add(STATE_MISMATCH);
            }
        } else if (requirements.requiredState() == State.CURRENT && historical) {
            contradictions.add(STATE_MISMATCH);
        }
    }

    private static void evaluateMetric(
            QueryClaimRequirements requirements,
            Set<Metric> windowMetrics,
            String window,
            Set<ClaimSupportDecision.Reason> contradictions,
            Set<ClaimSupportDecision.Reason> missing) {
        if (requirements.metric() == Metric.UNKNOWN) {
            return;
        }
        if (metricExplicitlyDenied(requirements.metric(), windowMetrics, window)) {
            contradictions.add(METRIC_MISMATCH);
            return;
        }
        if (windowMetrics.contains(requirements.metric())) {
            return;
        }
        if (windowMetrics.isEmpty()) {
            missing.add(METRIC_MISMATCH);
        } else {
            contradictions.add(METRIC_MISMATCH);
        }
    }

    private static void evaluateNumeric(
            QueryClaimRequirements requirements,
            Set<NumericConstraint> windowNumeric,
            Set<ClaimSupportDecision.Reason> contradictions,
            Set<ClaimSupportDecision.Reason> missing) {
        for (NumericConstraint required : requirements.numericConstraints()) {
            if (windowNumeric.contains(required)) {
                continue;
            }
            boolean sameValue = windowNumeric.stream()
                    .anyMatch(candidate -> candidate.value().compareTo(required.value()) == 0);
            boolean sameUnit = windowNumeric.stream()
                    .anyMatch(candidate -> candidate.unit().equals(required.unit()));
            if (sameValue) {
                contradictions.add(UNIT_MISMATCH);
            } else if (sameUnit && !windowNumeric.isEmpty()) {
                contradictions.add(NUMERIC_VALUE_MISMATCH);
            } else if (windowNumeric.isEmpty()) {
                missing.add(NUMERIC_VALUE_MISMATCH);
            } else {
                contradictions.add(NUMERIC_VALUE_MISMATCH);
            }
        }
    }

    private static boolean metricExplicitlyDenied(
            Metric required,
            Set<Metric> observed,
            String window) {
        if (!observed.contains(required)) {
            return false;
        }
        for (String marker : metricMarkers(required)) {
            Pattern denial = Pattern.compile(
                    Pattern.quote(marker) + ".{0,24}(?:아니|아닙|아닌|아니며)");
            if (denial.matcher(window).find()) {
                return true;
            }
        }
        return false;
    }

    private static List<String> metricMarkers(Metric metric) {
        return switch (metric) {
            case COST -> List.of("비용", "cost");
            case STORAGE_VOLUME -> List.of("저장 용량", "저장량", "스토리지 읽기량", "보관량");
            case MEMORY -> List.of("메모리", "rss", "heap");
            case LATENCY -> List.of("응답 시간", "응답시간", "지연", "p95", "p99", "조회 시간", "표시 시간");
            case DURATION -> List.of("완료 시간", "실행 시간", "처리 시간", "소요 시간");
            case RATE -> List.of("비율", "누락률", "성공률", "crash-free", "퍼센트");
            case COUNT -> List.of("개수", "건수", "중복", "유실", "손실", "신고", "series");
            case THROUGHPUT -> List.of("처리량", "throughput");
            case UNKNOWN -> List.of();
        };
    }

    private static boolean hasAffirmativeEvidence(
            QueryClaimRequirements requirements,
            String window,
            Set<Action> windowActions,
            boolean projectScopedTechnologyDeclaration) {
        if (CLAIM_QUESTION_ENDING.matcher(window).matches()
                || window.matches(".*[?？]\\s*$")
                || containsAny(window, NON_ASSERTIVE_MARKERS)) {
            return false;
        }
        if (!projectScopedTechnologyDeclaration
                && !requirements.actions().isEmpty()
                && windowActions.isEmpty()) {
            return false;
        }
        if (projectScopedTechnologyDeclaration) {
            return true;
        }
        if (!requirements.numericConstraints().isEmpty()) {
            return true;
        }
        return containsAny(window, List.of(
                        "했다", "하였다", "직접", "담당", "적용", "운영", "배포", "사용",
                        "구현", "개발", "해결", "복구", "개선", "줄였", "낮췄", "막았",
                        "차단", "직렬화", "검증", "한 번만", "0건"))
                || isAffirmativeClaimWindow(window);
    }

    private static boolean isAffirmativeClaimWindow(String window) {
        return !CLAIM_QUESTION_ENDING.matcher(window).matches()
                && !window.matches(".*[?？]\\s*$")
                && !containsAny(window, NON_ASSERTIVE_MARKERS)
                && !containsAny(window, PROTOTYPE_MARKERS)
                && window.matches(".*(?:했다|하였다|했습니다|되었습니다|됐다|였다)\\s*$");
    }

    private static boolean supportsActions(
            Set<Action> required,
            Set<Action> candidate,
            String window) {
        for (Action action : required) {
            if (candidate.contains(action)) {
                continue;
            }
            if (action == Action.SOLVE
                    && required.contains(Action.IMPROVE)
                    && candidate.contains(Action.IMPROVE)) {
                continue;
            }
            if (action == Action.SOLVE && containsAny(window, List.of("한 번만", "0건", "계속한다"))) {
                continue;
            }
            if (action == Action.USE
                    && candidate.stream().anyMatch(Set.of(
                            Action.IMPLEMENT, Action.APPLY, Action.DEPLOY)::contains)) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static boolean isTargetNegated(
            QueryClaimRequirements requirements,
            String window) {
        if (requirements.actions().stream().anyMatch(action -> actionDenied(action, window))) {
            return true;
        }
        if (requirements.actions().contains(Action.OVERWRITE)
                && window.contains("덮어쓰지 않")) {
            return true;
        }
        if (requirements.actions().contains(Action.STOP)
                && (window.contains("중단하지 않") || window.contains("계속한다") || window.contains("계속했다"))) {
            return true;
        }
        if (requirements.actions().contains(Action.DISCARD)
                && (window.contains("버리지 않") || window.contains("삭제하지 않")
                        || window.contains("보정 작업") || window.contains("유지했다"))) {
            return true;
        }
        if (requirements.actions().contains(Action.IMPLEMENT) && window.contains("구현하지 않")) {
            return true;
        }
        if (requirements.actions().contains(Action.USE)
                && (window.contains("사용하지 않") || window.contains("운영하지 않"))) {
            return true;
        }
        if (requirements.actions().contains(Action.APPLY) && window.contains("적용하지 않")) {
            return true;
        }
        if (requirements.actions().contains(Action.DEPLOY) && window.contains("배포하지 않")) {
            return true;
        }
        return false;
    }

    private static boolean actionDenied(Action action, String window) {
        for (String marker : actionMarkers(action)) {
            Pattern denial = Pattern.compile(
                    Pattern.quote(marker)
                            + "(?:을|를|은|는|이|가)?\\s*"
                            + "(?:(?:직접\\s*)?(?:담당|수행)\\s*)?"
                            + "하지\\s*(?:않|못)");
            if (denial.matcher(window).find()) {
                return true;
            }
        }
        return false;
    }

    private static List<String> actionMarkers(Action action) {
        return switch (action) {
            case IMPLEMENT -> List.of("구현", "개발", "작성");
            case USE -> List.of("사용", "활용", "운영", "도입", "채택", "연결");
            case APPLY -> List.of("적용");
            case DEPLOY -> List.of("배포", "출시");
            case IMPROVE -> List.of("개선", "단축", "감소", "향상");
            case SOLVE -> List.of("해결", "복구", "대응");
            case DISCARD -> List.of("폐기", "삭제", "제거");
            case STOP -> List.of("중단", "정지");
            case OVERWRITE -> List.of("덮어쓰");
            case SERIALIZE -> List.of("직렬화");
            case VERIFY -> List.of("검증", "확인");
            case RESTRUCTURE -> List.of("분리", "전환", "구조 변경");
        };
    }

    private static boolean hasOtherActorSubject(String window) {
        return containsAny(window, OTHER_ACTOR_MARKERS)
                || OTHER_ACTOR_SUBJECT.matcher(window).find();
    }

    private static boolean isNotAdopted(
            QueryClaimRequirements requirements,
            String window) {
        boolean adoptionQuestion = requirements.actions().contains(Action.USE)
                || requirements.actions().contains(Action.APPLY)
                || requirements.actions().contains(Action.DEPLOY)
                || requirements.requiredState() == State.PRODUCTION;
        return adoptionQuestion && containsAny(window, NOT_ADOPTED_MARKERS);
    }

    private static Set<Action> extractActions(String normalized) {
        EnumSet<Action> actions = EnumSet.noneOf(Action.class);
        if (containsAny(normalized, List.of("구현", "개발", "작성", "재작성"))) {
            actions.add(Action.IMPLEMENT);
        }
        if (containsAny(normalized, List.of("사용", "활용", "도입", "채택", "운영", "연결"))) {
            actions.add(Action.USE);
        }
        if (normalized.contains("적용")) {
            actions.add(Action.APPLY);
        }
        if (containsAny(normalized, List.of("배포", "출시"))) {
            actions.add(Action.DEPLOY);
        }
        if (containsAny(normalized, List.of(
                "개선", "낮췄", "낮추", "줄였", "줄이", "줄었", "단축", "감소", "향상"))) {
            actions.add(Action.IMPROVE);
        }
        if (containsAny(normalized, List.of(
                "해결", "복구", "대응", "방지", "막았", "막은", "차단", "않도록",
                "재시도", "재처리", "다시 실행", "재실행", "이어 실행"))) {
            actions.add(Action.SOLVE);
        }
        if (containsAny(normalized, List.of("버려", "버리", "폐기", "삭제", "제거"))) {
            actions.add(Action.DISCARD);
        }
        if (containsAny(normalized, List.of("중단", "멈추"))) {
            actions.add(Action.STOP);
        }
        if (normalized.contains("덮어쓰") || normalized.contains("덮어썼")) {
            actions.add(Action.OVERWRITE);
        }
        if (normalized.contains("직렬화")) {
            actions.add(Action.SERIALIZE);
        }
        if (containsAny(normalized, List.of("검증", "확인"))) {
            actions.add(Action.VERIFY);
        }
        if (containsAny(normalized, List.of("구조를 바", "구조 변경", "분리", "전환"))
                || normalized.contains("구조") && normalized.contains("변경")) {
            actions.add(Action.RESTRUCTURE);
        }
        return Set.copyOf(actions);
    }

    private static State requiredState(String normalized, Set<Action> actions) {
        if (containsAny(normalized, List.of(
                "production", "생산 환경", "생산 앱", "생산 로봇", "생산 분석", "생산 트래픽",
                "실제 시스템", "실제 정산"))
                ) {
            return State.PRODUCTION;
        }
        if (normalized.contains("현재")) {
            return State.CURRENT;
        }
        if (actions.contains(Action.USE) || actions.contains(Action.APPLY)) {
            return State.USED;
        }
        return State.ANY;
    }

    private static boolean isClaimQuestion(
            String normalized,
            Set<Action> actions,
            Set<String> entities,
            List<NumericConstraint> numeric) {
        boolean interrogative = containsAny(normalized, List.of(
                "했나요", "했는가", "했나", "했어", "인가요", "있는가", "있나요"))
                || CLAIM_QUESTION_ENDING.matcher(normalized).matches();
        boolean experienceQuestion = containsAny(normalized, List.of(
                "경험이 있", "경험이 있는", "이력이 있"));
        boolean hasClaimTarget = !actions.isEmpty() || !numeric.isEmpty() || !entities.isEmpty();
        boolean descriptiveQuestion = containsAny(normalized, List.of("몇 ", "몇 개", "몇 건", "무엇", "어떤"));
        return hasClaimTarget
                && (!descriptiveQuestion || !actions.isEmpty())
                && (interrogative || experienceQuestion);
    }

    private static Set<String> entities(String value) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(value);
        while (matcher.find()) {
            String token = trimKoreanSuffix(trimToken(matcher.group())).toLowerCase(Locale.ROOT);
            if (ASCII_ENTITY.matcher(token).matches()
                    && token.length() >= 2
                    && !GENERIC_ENTITIES.contains(token)
                    && !token.chars().allMatch(Character::isDigit)) {
                result.add(token);
            }
        }
        return Set.copyOf(result);
    }

    private static Set<String> subjectTerms(String query, Set<String> entities) {
        Set<String> terms = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(query);
        while (matcher.find()) {
            String token = trimKoreanSuffix(trimToken(matcher.group()));
            if (token.length() >= 2
                    && !entities.contains(token)
                    && !SUBJECT_STOP_WORDS.contains(token)
                    && !isClaimGrammarToken(token)
                    && !GENERIC_ENTITIES.contains(token)
                    && !token.chars().allMatch(Character::isDigit)) {
                terms.add(token);
            }
        }
        return Set.copyOf(terms);
    }

    private static boolean isClaimGrammarToken(String token) {
        return containsAny(token, List.of(
                "했나요", "했는가", "했나", "했어", "인가요", "있는가", "있나요",
                "경험", "근거", "증거", "이력", "구현", "개발", "사용", "활용",
                "도입", "채택", "운영", "적용", "배포", "출시", "개선", "낮추",
                "줄이", "단축", "감소", "향상", "해결", "복구", "대응", "방지",
                "차단", "검증", "확인", "변경", "재구성"));
    }

    private static int matchedSubjectTerms(Set<String> required, String window) {
        String normalized = normalize(window);
        return (int) required.stream().filter(normalized::contains).count();
    }

    private static int requiredSubjectMatches(QueryClaimRequirements requirements) {
        if (requirements.subjectTerms().isEmpty()) {
            return 0;
        }
        return Math.min(2, requirements.subjectTerms().size());
    }

    private static List<NumericConstraint> numericConstraints(String value) {
        List<NumericConstraint> result = new ArrayList<>();
        Matcher matcher = NUMERIC.matcher(value);
        while (matcher.find()) {
            String unit = matcher.group("unit");
            String scale = matcher.group("scale");
            if ((unit == null || unit.isBlank()) && (scale == null || scale.isBlank())) {
                continue;
            }
            BigDecimal number = new BigDecimal(matcher.group("value").replace(",", ""));
            if ("만".equals(scale)) {
                number = number.multiply(BigDecimal.valueOf(10_000L));
            } else if ("천".equals(scale)) {
                number = number.multiply(BigDecimal.valueOf(1_000L));
            }
            result.add(new NumericConstraint(number, canonicalUnit(unit, scale)));
        }
        return List.copyOf(new LinkedHashSet<>(result));
    }

    private static String canonicalUnit(String unit, String scale) {
        if (unit == null || unit.isBlank()) {
            return Objects.requireNonNullElse(scale, "count");
        }
        return switch (unit.toLowerCase(Locale.ROOT)) {
            case "밀리초", "ms" -> "ms";
            case "초" -> "s";
            case "분" -> "min";
            case "시간" -> "h";
            case "퍼센트", "%" -> "percent";
            case "기가바이트", "gb" -> "gb";
            case "테라바이트", "tb" -> "tb";
            default -> unit;
        };
    }

    private static Metric metric(String value) {
        Set<Metric> metrics = metrics(value);
        if (metrics.contains(Metric.COST)) {
            return Metric.COST;
        }
        if (metrics.contains(Metric.MEMORY)) {
            return Metric.MEMORY;
        }
        if (metrics.contains(Metric.STORAGE_VOLUME)) {
            return Metric.STORAGE_VOLUME;
        }
        if (metrics.contains(Metric.LATENCY)) {
            return Metric.LATENCY;
        }
        if (metrics.contains(Metric.DURATION)) {
            return Metric.DURATION;
        }
        if (metrics.contains(Metric.RATE)) {
            return Metric.RATE;
        }
        if (metrics.contains(Metric.THROUGHPUT)) {
            return Metric.THROUGHPUT;
        }
        return metrics.contains(Metric.COUNT) ? Metric.COUNT : Metric.UNKNOWN;
    }

    private static Set<Metric> metrics(String value) {
        EnumSet<Metric> result = EnumSet.noneOf(Metric.class);
        if (containsAny(value, List.of("비용", "cost"))) {
            result.add(Metric.COST);
        }
        if (containsAny(value, List.of("메모리", "rss", "heap"))) {
            result.add(Metric.MEMORY);
        }
        if (containsAny(value, List.of("저장량", "저장 용량", "스토리지 읽기량", "유입량", "보관량"))) {
            result.add(Metric.STORAGE_VOLUME);
        }
        if (containsAny(value, List.of(
                "p50", "p90", "p95", "p99", "응답 시간", "응답시간", "latency", "지연",
                "디코딩", "조회 시간", "표시 시간", "쿼리 응답"))) {
            result.add(Metric.LATENCY);
        }
        if (containsAny(value, List.of("완료 시간", "실행 시간", "처리 시간", "소요 시간"))) {
            result.add(Metric.DURATION);
        }
        if (containsAny(value, List.of("퍼센트", "%", "비율", "누락률", "성공률", "crash-free"))) {
            result.add(Metric.RATE);
        }
        if (containsAny(value, List.of("처리량", "초당", "throughput"))) {
            result.add(Metric.THROUGHPUT);
        }
        if (containsAny(value, List.of(
                "건", "개", "명", "회", "중복", "유실", "손실", "신고", "series", "task"))) {
            result.add(Metric.COUNT);
        }
        return Set.copyOf(result);
    }

    private static Direction direction(String value) {
        if (containsAny(value, List.of("0건", "방지", "막", "차단", "피했다"))) {
            return Direction.PREVENT;
        }
        if (containsAny(value, List.of("줄", "낮", "단축", "감소"))) {
            return Direction.DECREASE;
        }
        if (containsAny(value, List.of("증가", "높", "향상", "개선"))) {
            return Direction.INCREASE;
        }
        return Direction.UNKNOWN;
    }

    private static List<String> claimWindows(String content) {
        String source = Objects.requireNonNullElse(content, "")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        List<String> windows = new ArrayList<>();
        for (String paragraph : source.split("\\n\\s*\\n+")) {
            String normalized = normalize(paragraph);
            List<String> sentences = java.util.Arrays.stream(normalized.split(
                            "[.!?]+(?=\\s|$)"))
                    .map(String::strip)
                    .filter(sentence -> !sentence.isBlank())
                    .toList();
            windows.addAll(sentences);
            for (int size = 2; size <= 3; size++) {
                for (int index = 0; index + size <= sentences.size(); index++) {
                    windows.add(String.join(". ", sentences.subList(index, index + size)));
                }
            }
        }
        return List.copyOf(windows);
    }

    private static boolean containsAny(String value, List<String> markers) {
        return markers.stream().anyMatch(value::contains);
    }

    private static String normalize(String value) {
        return SearchTokenNormalizer.normalize(Objects.requireNonNullElse(value, ""))
                .replaceAll("\\s+", " ")
                .strip();
    }

    private static String trimToken(String value) {
        int end = value.length();
        while (end > 0 && ".-_".indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(0, end);
    }

    private static String trimKoreanSuffix(String value) {
        for (String suffix : List.of(
                "했나요", "인가요", "있는가", "있나요", "으로", "에서", "까지", "에게",
                "했다", "하는", "한", "된", "인", "을", "를", "은", "는", "이", "가", "과", "와",
                "의", "로", "에")) {
            if (value.endsWith(suffix) && value.length() - suffix.length() >= 2) {
                return value.substring(0, value.length() - suffix.length());
            }
        }
        return value;
    }

    private record WindowEvaluation(
            boolean supported,
            boolean related,
            Set<ClaimSupportDecision.Reason> contradictions,
            Set<ClaimSupportDecision.Reason> missing) {

        private WindowEvaluation {
            contradictions = Set.copyOf(contradictions);
            missing = Set.copyOf(missing);
        }
    }
}
