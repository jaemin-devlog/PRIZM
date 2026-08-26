package com.prizm.search.profile;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 자연어 표현 차이로 dense 후보를 놓쳤을 때 사용할 보수적인 검색 변형을 만든다.
 *
 * <p>조사와 질문형 어미를 최소한으로 정리한 변형부터 적용하고, 필요한 경우에만 제한된
 * 의미 별칭을 덧붙인다. 식별자와 숫자 anchor를 보존한 변형만 후보 조회에 사용하며, 최종
 * 관련성 판단과 근거 위치화는 원래 질의를 기준으로 한다.</p>
 */
public final class NaturalLanguageQueryFallback {

    public static final int MAX_VARIANTS = 2;

    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}+#._-]*");
    private static final Pattern LATIN_IDENTIFIER_PARTICLE = Pattern.compile(
            "(?i)(?<=[a-z0-9+#.])(?:으로|에서|에게서|로|을|를|은|는|이|가|와|과|의|에)"
                    + "(?=\\s|[?!.。！？]|$)");
    private static final Pattern WHAT_DID_YOU_DO = Pattern.compile(
            "뭐\\s*했(?:어|나요|습니까)\\s*[?!.。！？]*$");
    private static final Pattern TELL_ME = Pattern.compile(
            "알려\\s*줘(?:요)?\\s*[?!.。！？]*$");
    private static final Pattern EXPERIENCE_REQUEST = Pattern.compile(
            "(?:경험|뭐\\s*했|알려\\s*줘|어떤|활용|사용|구현|어떻게\\s*해결)");
    private static final Pattern ASCII_IDENTIFIER = Pattern.compile(
            "(?i)^[a-z][a-z0-9+#._-]*$");
    private static final Set<String> ANCHOR_STOP_WORDS = Set.of(
            "경험", "프로젝트", "시스템", "구현", "활용", "활용한", "사용", "사용한",
            "수행", "문제", "어떻게", "해결", "해결했어", "왜", "뭐했어", "알려줘",
            "어떤", "사용했어", "근거", "관련", "있어", "있나요");
    private static final List<String> KOREAN_PARTICLES = List.of(
            "에게서", "으로", "에서", "로", "을", "를", "은", "는", "이", "가",
            "와", "과", "의", "에");

    private NaturalLanguageQueryFallback() {
    }

    public static Optional<String> variant(String query) {
        return variants(query).stream().findFirst();
    }

    /** 변경 폭이 작은 순서로 보수적인 검색 변형을 최대 두 개 반환한다. */
    public static List<String> variants(String query) {
        LinkedHashSet<String> variants = new LinkedHashSet<>();
        String original = query.trim();
        String canonical = SearchTokenNormalizer.canonicalizeTechnologyNames(query).trim();
        String minimal = minimalVariant(canonical);
        addIfChanged(variants, original, minimal);

        String aliasSource = minimal.equals(original) ? canonical : minimal;
        String semantic = semanticAliasVariant(aliasSource);
        addIfChanged(variants, original, semantic);
        addIfChanged(variants, original, compactSemanticVariant(semantic));

        return variants.stream().limit(MAX_VARIANTS).toList();
    }

    /** 변형 과정에서 원래 질의의 필수 식별자와 숫자 anchor가 모두 유지됐는지 확인한다. */
    public static boolean preservesRequiredAnchors(
            String originalQuery,
            String variant,
            Set<String> requiredIdentifiers) {
        String normalizedVariant = SearchTokenNormalizer.normalize(variant);
        boolean identifiersPreserved = requiredIdentifiers.stream()
                .allMatch(normalizedVariant::contains);
        Set<NumericQueryAnchors.NumericAnchor> variantNumbers =
                Set.copyOf(NumericQueryAnchors.extract(variant));
        return identifiersPreserved
                && variantNumbers.containsAll(NumericQueryAnchors.extract(originalQuery));
    }

    private static String minimalVariant(String canonical) {
        String variant = LATIN_IDENTIFIER_PARTICLE.matcher(canonical).replaceAll("");
        variant = WHAT_DID_YOU_DO.matcher(variant).replaceAll("수행 경험");
        variant = TELL_ME.matcher(variant).replaceAll("").trim();
        variant = variant.replace("활용한 경험", "활용 경험");
        variant = normalizeSpaces(variant);

        if (isBareLatinIdentifier(variant)) {
            variant = variant + " 경험";
        }
        if (variant.equals("배포 경험")) {
            variant = "배포 환경 구축 경험";
        }

        return normalizeSpaces(variant);
    }

    private static String semanticAliasVariant(String value) {
        String variant = value;
        variant = variant.replaceAll("실제\\s*사용자가\\s*있는", "실사용");
        variant = variant.replaceAll("실제\\s*운영\\s*환경(?:에)?", "운영 환경 ");
        variant = variant.replaceAll("서비스(?:를|을)?\\s*올려\\s*본", "서비스 배포");
        variant = variant.replaceAll("배포해\\s*본", "배포");
        variant = variant.replaceAll("운영해\\s*본", "운영");
        variant = variant.replaceAll("스프레드시트(?!\\s*엑셀)", "스프레드시트 엑셀");
        variant = variant.replaceAll("기존\\s+([^?!.。！？]+?)만\\s+골라\\s+갱신", "기존 $1 갱신");
        variant = variant.replaceAll("확정\\s*직전(?:에)?", "확정 전");
        variant = variant.replaceAll("바뀌었는지", "변경 여부");
        variant = variant.replaceAll("다시\\s*확인", "재검증");
        if (containsFileServingConcept(variant)) {
            variant = variant.replaceAll("애플리케이션\\s*대신", "");
            variant = variant.replaceAll("제공하게\\s*한", "직접 서빙");
            variant = variant.replaceAll("제공한", "직접 서빙한");
            variant = variant.replaceAll("제공", "서빙");
        }
        variant = variant.replaceAll("서비스(?:를|을)?\\s*배포한\\s*경험", "서비스 배포 환경 구축 경험");
        variant = variant.replace("배포 경험", "배포 환경 구축 경험");
        return normalizeSpaces(variant);
    }

    private static boolean containsFileServingConcept(String value) {
        String normalized = SearchTokenNormalizer.normalize(value);
        return normalized.contains("파일")
                && normalized.contains("웹 서버")
                && normalized.contains("제공");
    }

    private static String compactSemanticVariant(String value) {
        String normalized = SearchTokenNormalizer.normalize(value);
        if (normalized.contains("확정 전")
                && normalized.contains("상태")
                && normalized.contains("재검증")) {
            return "확정 전 상태 재검증 경험";
        }
        if (normalized.contains("웹 서버")
                && normalized.contains("파일")
                && normalized.contains("서빙")) {
            return "웹 서버 파일 직접 서빙 경험";
        }
        if (normalized.contains("실사용")
                && normalized.contains("서비스")
                && normalized.contains("운영")) {
            return "실사용 서비스 운영 경험";
        }
        if (normalized.contains("스프레드시트")
                && normalized.contains("엑셀")
                && normalized.contains("기존 데이터")
                && normalized.contains("갱신")) {
            return "스프레드시트 엑셀 기존 데이터 갱신 경험";
        }
        return normalizeSpaces(value);
    }

    private static void addIfChanged(Set<String> variants, String original, String candidate) {
        if (!candidate.isBlank() && !candidate.equals(original)) {
            variants.add(candidate);
        }
    }

    private static String normalizeSpaces(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    public static boolean requiresDirectAnchor(String query) {
        String canonical = SearchTokenNormalizer.canonicalizeTechnologyNames(query).trim();
        return isExperienceRequest(canonical) || isBareLatinIdentifier(canonical);
    }

    public static boolean isExperienceRequest(String query) {
        return EXPERIENCE_REQUEST.matcher(query).find();
    }

    public static boolean hasDirectAnchor(String query, String content) {
        Set<String> anchors = anchorTerms(query);
        if (anchors.isEmpty()) {
            return true;
        }
        Set<String> contentTerms = normalizedTerms(content);
        return anchors.stream().anyMatch(contentTerms::contains);
    }

    static Set<String> anchorTerms(String query) {
        Set<String> terms = normalizedTerms(query);
        terms.removeAll(ANCHOR_STOP_WORDS);
        return Set.copyOf(terms);
    }

    private static Set<String> normalizedTerms(String value) {
        Matcher matcher = TOKEN_PATTERN.matcher(SearchTokenNormalizer.normalize(value));
        Set<String> terms = new LinkedHashSet<>();
        while (matcher.find()) {
            String term = stripParticle(matcher.group());
            if (term.codePointCount(0, term.length()) >= 2) {
                terms.add(term);
            }
        }
        return terms;
    }

    private static String stripParticle(String value) {
        for (String particle : KOREAN_PARTICLES) {
            if (value.endsWith(particle)
                    && value.codePointCount(0, value.length())
                    > particle.codePointCount(0, particle.length()) + 1) {
                return value.substring(0, value.length() - particle.length());
            }
        }
        return value;
    }

    private static boolean isBareLatinIdentifier(String value) {
        Set<String> terms = normalizedTerms(value);
        return terms.size() == 1 && ASCII_IDENTIFIER.matcher(terms.iterator().next()).matches();
    }
}
