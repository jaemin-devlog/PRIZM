package com.prizm.search.evaluation.searchv3.typed;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.CandidateObservation;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.DateConstraint;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.DateObservation;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.DateOperator;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.Direction;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.IdentifierNumberConstraint;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.IdentifierNumberObservation;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.LiteralIdentifierConstraint;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.LiteralIdentifierObservation;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.QuantityConstraint;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.QuantityObservation;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.QuantityOperator;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.SourceSlice;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeterministicTypedParserTest {

    private final DeterministicTypedQueryParser queryParser = new DeterministicTypedQueryParser();
    private final DeterministicTypedObservationExtractor observationExtractor =
            new DeterministicTypedObservationExtractor();

    @Test
    void parsesKoreanQuantityWithGroundedCoreQualifierAndDirection() {
        String query = "미응답 비율을 50% 이상 감소시킨 경험이 있나요?";

        QuantityConstraint value = (QuantityConstraint) queryParser.parse(query).get(0);

        assertThat(value.operator()).isEqualTo(QuantityOperator.GTE);
        assertThat(value.value()).isEqualByComparingTo("50");
        assertThat(value.normalizedUnit()).isEqualTo("%");
        assertThat(value.qualifier().normalized()).isEqualTo("미응답 비율");
        assertThat(value.qualifier().span().surface()).isEqualTo("미응답 비율");
        assertThat(value.direction().direction()).isEqualTo(Direction.DECREASE);
        assertThat(value.direction().span().surface()).isEqualTo("감소");
        assertThat(value.span().surface()).isEqualTo("50% 이상 감소");
        assertRoundTrip(query, value.span());
        assertRoundTrip(query, value.qualifier().span());
    }

    @Test
    void parsesEnglishPrefixComparatorAndRightOrOfQualifier() {
        String query = "Did the approved flow reach a task completion rate of at least 80%?";

        QuantityConstraint value = (QuantityConstraint) queryParser.parse(query).get(0);

        assertThat(value.operator()).isEqualTo(QuantityOperator.GTE);
        assertThat(value.span().surface()).isEqualTo("at least 80%");
        assertThat(value.qualifier().normalized()).isEqualTo("task completion rate");
        assertThat(value.qualifier().span().surface()).isEqualTo("task completion rate");
    }

    @Test
    void parsesRangeDurationAndPlainEqualityWithoutIncludingQualifierInCore() {
        QuantityConstraint range = (QuantityConstraint) queryParser
                .parse("한 분기에 교육 키트 50~100건을 점검했나요?").get(0);
        QuantityConstraint duration = (QuantityConstraint) queryParser
                .parse("community operations를 3년 이상 운영했나요?").get(0);
        QuantityConstraint equality = (QuantityConstraint) queryParser
                .parse("사용자 1,300명이 이용했나요?").get(0);

        assertThat(range.operator()).isEqualTo(QuantityOperator.RANGE);
        assertThat(range.value()).isEqualByComparingTo("50");
        assertThat(range.upperValue()).isEqualByComparingTo("100");
        assertThat(range.qualifier().normalized()).isEqualTo("교육 키트");
        assertThat(duration.qualifier().normalized()).isEqualTo("community operations");
        assertThat(equality.span().surface()).isEqualTo("1,300명");
        assertThat(equality.qualifier().normalized()).isEqualTo("사용자");
    }

    @Test
    void prefersLongestKoreanUnitAndRequiresUnicodeUnitBoundary() {
        QuantityConstraint months = (QuantityConstraint) queryParser.parse("3개월").get(0);
        QuantityConstraint items = (QuantityConstraint) queryParser.parse("3개").get(0);
        QuantityObservation observedMonths = first(
                observationExtractor.extract(source(0, "운영 기간은 3개월이었다.")), QuantityObservation.class);
        QuantityObservation observedItems = first(
                observationExtractor.extract(source(0, "검토 대상은 3개였다.")), QuantityObservation.class);

        assertThat(months.normalizedUnit()).isEqualTo("개월");
        assertThat(months.span().surface()).isEqualTo("3개월");
        assertThat(items.normalizedUnit()).isEqualTo("개");
        assertThat(observedMonths.normalizedUnit()).isEqualTo("개월");
        assertThat(observedMonths.span().surface()).isEqualTo("3개월");
        assertThat(observedItems.normalizedUnit()).isEqualTo("개");
        assertThat(queryParser.parse("3개xyz")).isEmpty();
        assertThat(observationExtractor.extract(source(0, "3개xyz"))).isEmpty();
    }

    @Test
    void rejectsInvalidCommaGroupingWithoutTailMatching() {
        assertThat(queryParser.parse("사용자 12,34명")).isEmpty();
        assertThat(queryParser.parse("사용자 １２，３４명")).isEmpty();
        assertThat(observationExtractor.extract(source(0, "사용자 12,34명"))).isEmpty();
        assertThat(observationExtractor.extract(source(0, "사용자 １２，３４명"))).isEmpty();

        QuantityConstraint valid = (QuantityConstraint) queryParser.parse("사용자 1,234명").get(0);
        assertThat(valid.value()).isEqualByComparingTo("1234");
    }

    @Test
    void distinguishesEnglishStrictAfterFromKoreanInclusiveAfter() {
        DateConstraint english = (DateConstraint) queryParser
                .parse("Was the approved service launch date after 2025-06-30?").get(0);
        DateConstraint korean = (DateConstraint) queryParser
                .parse("전국 rollout 시작일이 2025-06-30 이후인가요?").get(0);

        assertThat(english.operator()).isEqualTo(DateOperator.GT);
        assertThat(english.qualifier().normalized()).isEqualTo("approved service launch date");
        assertThat(korean.operator()).isEqualTo(DateOperator.GTE);
        assertThat(korean.qualifier().normalized()).isEqualTo("전국 rollout 시작일");
    }

    @Test
    void parsesDatePrecisionAndInclusiveRangeBeforeLowerPriorityNumbers() {
        DateConstraint range = (DateConstraint) queryParser
                .parse("정책 조사 기간이 2024-03-01부터 2025-02-28까지였나요?").get(0);
        DateConstraint month = (DateConstraint) queryParser.parse("출시는 2025년 3월부터인가요?").get(0);
        DateConstraint yearRange = (DateConstraint) queryParser.parse("운영 기간은 2024~2025인가요?").get(0);

        assertThat(range.operator()).isEqualTo(DateOperator.RANGE);
        assertThat(range.interval().startInclusive()).isEqualTo(LocalDate.of(2024, 3, 1));
        assertThat(range.interval().endInclusive()).isEqualTo(LocalDate.of(2025, 2, 28));
        assertThat(month.operator()).isEqualTo(DateOperator.GTE);
        assertThat(month.interval().startInclusive()).isEqualTo(LocalDate.of(2025, 3, 1));
        assertThat(yearRange.interval().endInclusive()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(queryParser.parse("invalid 2025-02-30")).isEmpty();
    }

    @Test
    void appliesPrecedenceToIdentifierNumbersAndExactLiterals() {
        List<TypedValueModel.QueryConstraint> identifiers = queryParser.parse("HTTP/2와 Java 17, v2.0을 사용했다");
        LiteralIdentifierConstraint literal = (LiteralIdentifierConstraint) queryParser
                .parse("NimbusCache를 사용했나요?").get(0);
        LiteralIdentifierConstraint quoted = (LiteralIdentifierConstraint) queryParser
                .parse("\"사용자 조사\" 경험이 있나요?").get(0);

        assertThat(identifiers).hasSize(3).allMatch(IdentifierNumberConstraint.class::isInstance);
        assertThat(((IdentifierNumberConstraint) identifiers.get(0)).normalizedSegments())
                .containsExactly(BigInteger.valueOf(2));
        assertThat(literal.normalizedLiteral()).isEqualTo("nimbuscache");
        assertThat(quoted.normalizedLiteral()).isEqualTo("사용자조사");
        assertThat(queryParser.parse("ordinary lowercase prose only")).isEmpty();
        assertThat(queryParser.parse("1.3k users")).isEmpty();
    }

    @Test
    void preservesBareTitleCaseNumberContractWithoutAForbiddenIdentifierDictionary() {
        assertThat(queryParser.parse("Java 17").get(0)).isInstanceOf(IdentifierNumberConstraint.class);
        assertThat(queryParser.parse("React 19").get(0)).isInstanceOf(IdentifierNumberConstraint.class);
        assertThat(observationExtractor.extract(source(0, "Java 17")).get(0))
                .isInstanceOf(IdentifierNumberObservation.class);
        assertThat(observationExtractor.extract(source(0, "React 19")).get(0))
                .isInstanceOf(IdentifierNumberObservation.class);

        // These surfaces are structurally indistinguishable without a lexical ontology. The conservative v1
        // grammar types all TitleCase + number pairs and records the resulting semantic ambiguity as a limitation.
        assertThat(queryParser.parse("Top 10").get(0)).isInstanceOf(IdentifierNumberConstraint.class);
        assertThat(queryParser.parse("Phase 2").get(0)).isInstanceOf(IdentifierNumberConstraint.class);
        assertThat(queryParser.parse("May 2025").get(0)).isInstanceOf(IdentifierNumberConstraint.class);
    }

    @Test
    void reportsUnicodeCodePointOffsetsInsteadOfUtf16Offsets() {
        String query = "😀 사용자 1,300명이 이용했나요?";

        QuantityConstraint value = (QuantityConstraint) queryParser.parse(query).get(0);

        int expectedStart = query.codePointCount(0, query.indexOf("1,300명"));
        assertThat(value.span().startInclusive()).isEqualTo(expectedStart);
        assertThat(value.span().surface()).isEqualTo("1,300명");
        assertRoundTrip(query, value.span());
    }

    @Test
    void extractsSourceOnlyObservationsWithAbsoluteProvenance() {
        SourceSlice source = source(
                100,
                "검증 단계를 분리한 뒤 재처리 오류율을 정확히 50% 감소시켰다.");

        QuantityObservation value = (QuantityObservation) observationExtractor.extract(source).get(0);

        assertThat(value.value()).isEqualByComparingTo("50");
        assertThat(value.qualifier().normalized()).isEqualTo("재처리 오류율");
        assertThat(value.qualifier().span().surface()).isEqualTo("재처리 오류율");
        assertThat(value.direction().direction()).isEqualTo(Direction.DECREASE);
        assertThat(value.span().startInclusive()).isGreaterThanOrEqualTo(100);
        assertThat(value.source()).isSameAs(source);
    }

    @Test
    void extractsRightHandGenitiveAndEnglishPercentageQualifiers() {
        QuantityObservation korean = (QuantityObservation) observationExtractor
                .extract(source(0, "분기 점검에서는 75건의 교육 키트를 확인했다.")).get(0);
        QuantityObservation english = (QuantityObservation) observationExtractor
                .extract(source(0, "The flow achieved an 80% task completion rate in rollout.")).get(0);

        assertThat(korean.qualifier().normalized()).isEqualTo("교육 키트");
        assertThat(korean.qualifier().span().surface()).isEqualTo("교육 키트");
        assertThat(english.qualifier().normalized()).isEqualTo("task completion rate");
    }

    @Test
    void boundsMixedLanguageQuantityQualifierWithoutAbsorbingEnglishProse() {
        QuantityObservation data = (QuantityObservation) observationExtractor
                .extract(source(0, "The import job validated 데이터 2,329건 before publishing.")).get(0);
        QuantityObservation users = (QuantityObservation) observationExtractor
                .extract(source(0, "A small preview involved 사용자 300명 and stopped.")).get(0);

        assertThat(data.qualifier().normalized()).isEqualTo("데이터");
        assertThat(users.qualifier().normalized()).isEqualTo("사용자");
    }

    @Test
    void retainsContiguousQualifierHeadInsteadOfUsingGoldOrQueryToDropIt() {
        QuantityObservation value = (QuantityObservation) observationExtractor.extract(source(
                0, "한시적 community operations pilot은 2년 동안 운영된 뒤 종료됐다.")).get(0);

        assertThat(value.qualifier().normalized()).isEqualTo("community operations pilot");
        assertThat(value.qualifier().span().surface()).isEqualTo("community operations pilot");
    }

    @Test
    void extractsDatesIdentifierNumbersAndNearMatchLiteralsWithoutDuplicates() {
        DateObservation date = first(
                observationExtractor.extract(source(50, "The approved service launch date was 2025-07-15.")),
                DateObservation.class);
        IdentifierNumberObservation identifier = first(
                observationExtractor.extract(source(0, "The gateway negotiated HTTP/1.1 in production.")),
                IdentifierNumberObservation.class);
        LiteralIdentifierObservation literal = first(
                observationExtractor.extract(source(0, "A prototype mentioned ZephyrDBX.")),
                LiteralIdentifierObservation.class);

        assertThat(date.interval().startInclusive()).isEqualTo(LocalDate.of(2025, 7, 15));
        assertThat(date.qualifier().normalized()).isEqualTo("approved service launch date");
        assertThat(identifier.normalizedIdentifier()).isEqualTo("http");
        assertThat(identifier.normalizedSegments()).containsExactly(BigInteger.ONE, BigInteger.ONE);
        assertThat(literal.normalizedLiteral()).isEqualTo("zephyrdbx");
    }

    @Test
    void neverAssociatesQualifierAcrossAtomicSourceSlices() {
        List<CandidateObservation> observations = observationExtractor.extractAll(List.of(
                new SourceSlice("D", "V", "C1", null, 0, "사용자"),
                new SourceSlice("D", "V", "C2", null, 4, "1,300명")));

        QuantityObservation value = first(observations, QuantityObservation.class);
        assertThat(value.qualifier()).isEqualTo(TypedValueModel.Qualifier.empty());
    }

    @Test
    void normalizesFullWidthDigitsAndCommaWithoutChangingSourceSurface() {
        QuantityConstraint value = (QuantityConstraint) queryParser.parse("사용자 １，３００명 이상").get(0);

        assertThat(value.value()).isEqualByComparingTo(new BigDecimal("1300"));
        assertThat(value.span().surface()).isEqualTo("１，３００명 이상");
    }

    private SourceSlice source(int base, String text) {
        return new SourceSlice("DOC", "V01", "CHILD", null, base, text);
    }

    @SuppressWarnings("unchecked")
    private <T> T first(List<? extends CandidateObservation> values, Class<T> type) {
        return (T) values.stream().filter(type::isInstance).findFirst().orElseThrow();
    }

    private void assertRoundTrip(String text, TypedValueModel.CodePointSpan span) {
        int charStart = text.offsetByCodePoints(0, span.startInclusive());
        int charEnd = text.offsetByCodePoints(0, span.endExclusive());
        assertThat(text.substring(charStart, charEnd)).isEqualTo(span.surface());
    }
}
