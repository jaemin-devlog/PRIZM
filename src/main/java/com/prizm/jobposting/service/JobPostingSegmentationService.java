package com.prizm.jobposting.service;

import com.prizm.jobposting.dto.response.JobPostingItemResponse;
import com.prizm.jobposting.exception.InvalidJobPostingSegmentationException;
import com.prizm.jobposting.exception.JobPostingItemLimitExceededException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Deterministically separates job-posting structure without a model or external service. */
@Service
public class JobPostingSegmentationService {

    public static final int MAX_ITEM_LENGTH = 500;
    public static final int MAX_ITEM_COUNT = 100;
    private static final int MAX_SECTION_HEADING_CODE_POINTS = 40;
    private static final int PRIMARY_LIST_LEVEL = 1;
    private static final int SECONDARY_LIST_LEVEL = 2;

    private static final Pattern UNICODE_WHITESPACE = Pattern.compile(
            "[\\p{Z}\\s]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern HTML_LINE_BREAK = Pattern.compile(
            "(?i)<br\\s*/?>", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern MARKDOWN_HEADING_PREFIX = Pattern.compile(
            "^#{1,6}\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern MARKDOWN_TABLE_SEPARATOR = Pattern.compile(
            "^:?-{1,}:?$", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern HEADING_MARKER_PREFIX = Pattern.compile(
            "^[►▶]\\s*", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern ASCII_OR_DASH_BULLET_PREFIX = Pattern.compile(
            "^[-‐‑‒–—―*](?:\\s+|$|(?=[\\p{L}]))", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern UNICODE_BULLET_PREFIX = Pattern.compile(
            "^[•●◦∘▪▫‣⁃·ㆍ○◉]\\s*", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern NUMBERED_PREFIX = Pattern.compile(
            "^\\d{1,4}[.)](?:\\s+|(?=[\\p{L}]))", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern HEADCOUNT_METADATA = Pattern.compile(
            "^(?:.{1,80}\\s+)?\\d{1,4}\\s*명(?:\\s*(?:모집|채용))?$",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern HIRING_STAGE_METADATA = Pattern.compile(
            "^(?:서류(?:전형|심사)?|과제전형|코딩테스트|인성검사|\\d{1,2}차\\s*(?:면접|인터뷰)|"
                    + "최종\\s*(?:면접|인터뷰|합격)|처우협의)$",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern STANDALONE_TIME_RANGE = Pattern.compile(
            "^(?:오전|오후)?\\s*\\d{1,2}:\\d{2}\\s*[~～–-]\\s*"
                    + "(?:오전|오후)?\\s*\\d{1,2}:\\d{2}$",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern DATE_METADATA = Pattern.compile(
            ".*(?:\\d{4}[-./년]\\s*\\d{1,2}(?:[-./월]\\s*\\d{1,2})?|"
                    + "\\d{1,2}[-./]\\d{1,2}[-./]\\d{2,4}).*",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern PROCESS_SEQUENCE = Pattern.compile(
            ".+(?:>|→|➜|➡).+(?:>|→|➜|➡).+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern COMPENSATION_RANGE = Pattern.compile(
            "(?i)^.*(?:\\p{Sc}\\s*)?\\d[\\d,]*(?:\\.\\d+)?\\s*"
                    + "(?:[-–—~]|to)\\s*(?:\\p{Sc}\\s*)?\\d[\\d,]*(?:\\.\\d+)?"
                    + "(?:\\s+.*)?$",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern PERCENTAGE_METADATA = Pattern.compile(
            "(?i)^(?:up\\s+to\\s+)?\\d{1,3}(?:\\.\\d+)?%\\s+.+$",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern BRACKETED_HEADING = Pattern.compile(
            "^\\[[^]\\r\\n]{2,80}]$", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern ENGLISH_ACTION_REQUIREMENT = Pattern.compile(
            "(?i)^(?:design|build|develop|operate|lead|enable|ensure|partner|drive|mentor|own|"
                    + "create|implement|maintain|improve|collaborate|deliver|manage|architect|"
                    + "support|optimize|establish|contribute|validate|monitor|test|review)\\b.*",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern ENGLISH_QUALIFICATION_REQUIREMENT = Pattern.compile(
            "(?i)^(?:\\d+\\+?\\s+years?\\b|experience\\b|proficiency\\b|ability\\b|"
                    + "solid understanding\\b|strong knowledge\\b|familiarity\\b|fluency\\b|"
                    + "fluent\\b|bachelor(?:'s)?\\b|master(?:'s)?\\b|bs/ms\\b|degree\\b).*",
            Pattern.UNICODE_CHARACTER_CLASS);

    private static final Set<String> SEARCHABLE_SECTION_STEMS = Set.of(
            "업무", "역할", "자격", "요건", "역량", "우대", "찾고", "함께하고싶", "이면좋", "더좋",
            "responsibil", "requirement", "qualification", "preferred", "lookingfor",
            "whoyouare", "whatyoudo", "whatyoull", "yourimpact", "skills");
    private static final Set<String> EXCLUDED_SECTION_STEMS = Set.of(
            "복지", "혜택", "기회", "참고", "전형", "접수", "지원안내", "지원방법", "제출서류", "유의사항",
            "근무조건", "근무정보", "근무지", "근무시간", "급여", "연봉", "고용형태",
            "개인정보", "법적", "benefit", "perk", "privacy", "legal", "hiringprocess",
            "recruitmentprocess", "applicationprocess", "howtoapply", "workcondition",
            "compensation", "candidateprivacy", "equalopportunity", "opportunit", "location", "employment",
            "deadline", "workplace");
    private static final Set<String> INTRO_SECTION_STEMS = Set.of(
            "소개", "개요", "about", "overview");
    private static final Set<String> STRUCTURAL_SECTION_STEMS = Set.of(
            "모집부문", "채용분야", "상세내용", "position", "jobdetails", "jobtitle");
    private static final Set<String> REQUIREMENT_STEMS = Set.of(
            "경험", "역량", "지식", "이해", "가능", "능력", "전공", "학위", "자세", "필요",
            "개발", "설계", "운영", "구축", "구현", "개선", "관리", "해결", "분석", "활용",
            "담당", "협업", "소통", "배포", "최적화", "experience", "proficiency", "ability",
            "knowledge", "understanding", "familiar", "degree", "years", "develop", "design",
            "operate", "build", "implement", "maintain", "improve", "manage", "lead", "mentor",
            "collaborate", "troubleshoot");
    private static final Set<String> STRONG_REQUIREMENT_STEMS = Set.of(
            "경험", "역량", "지식", "이해", "능력", "전공", "학위", "자세", "필요",
            "experience", "proficiency", "ability", "knowledge", "understanding", "familiar",
            "degree", "years");
    private static final Set<String> METADATA_VALUE_STEMS = Set.of(
            "정규직", "계약직", "인턴", "fulltime", "parttime", "contract", "internship",
            "hybrid", "onsite", "remote", "오피스", "office");
    private static final Set<String> METADATA_LABEL_STEMS = Set.of(
            "학력", "경력", "채용인원", "모집인원", "workhours", "applicationperiod",
            "applicationmethod");
    private static final Set<String> METADATA_SECTION_HEADINGS = Set.of(
            "학력", "경력", "경력사항", "경력구분", "채용인원", "모집인원", "채용인원수", "모집인원수",
            "headcount", "openings", "numberofopenings");
    private static final Set<String> APPLICATION_CONTEXT_STEMS = Set.of(
            "apply", "applying", "application", "submit", "지원서", "입사지원", "지원하기", "제출");
    private static final Set<String> FORM_CONTROL_STEMS = Set.of(
            "required", "optional", "field", "upload", "attach", "question", "response",
            "필수", "선택", "입력", "업로드", "첨부", "질문", "응답", "별표");
    private static final Set<String> BLOCK_METADATA_STEMS = Set.of(
            "salary", "compensation", "reward", "bonus", "payrange", "package", "remuneration",
            "location", "region", "workplace", "employment", "contracttype", "workarrangement",
            "급여", "연봉", "보상", "성과급", "근무지", "고용형태", "근무형태");
    private static final Set<String> LEGAL_PRIVACY_STEMS = Set.of(
            "privacy", "legal", "personalinformation", "dataprocessing", "개인정보", "법적");
    private static final Set<String> HEADING_CONNECTORS = Set.of(
            "and", "the", "of", "to", "for", "in", "with");

    /** Returns normalized items in source order with stable, one-based identifiers. */
    public List<JobPostingItemResponse> segment(String content) {
        if (content == null) {
            return List.of();
        }

        String[] rawLines = normalizeLineEndings(content).split("\\n", -1);
        List<ParsedLine> lines = new ArrayList<>(rawLines.length);
        for (String rawLine : rawLines) {
            lines.add(parseLine(rawLine));
        }

        boolean structuredPosting = isStructuredPosting(lines);
        List<BlockRole> blockRoles = classifyDocumentBlocks(lines);
        List<DraftItem> orderedItems = new ArrayList<>();
        Set<String> seenNormalizedItems = new LinkedHashSet<>();
        String currentSection = null;
        SectionRole currentRole = structuredPosting ? SectionRole.UNKNOWN : SectionRole.SEARCHABLE;
        String groupingSection = null;
        int groupingListLevel = 0;
        for (int index = 0; index < lines.size(); index++) {
            ParsedLine line = lines.get(index);
            if (line.text().isEmpty()) {
                continue;
            }
            if (blockRoles.get(index).excludesCandidates()) {
                groupingSection = null;
                groupingListLevel = 0;
                continue;
            }
            if (isStandaloneMetadata(line.text())) {
                groupingSection = null;
                groupingListLevel = 0;
                continue;
            }
            if (isSectionHeading(lines, index)) {
                currentSection = normalizeSection(line.text());
                SectionRole declaredRole = classifySectionRole(line.text());
                if (declaredRole == SectionRole.SEARCHABLE
                        || declaredRole == SectionRole.EXCLUDED) {
                    currentRole = declaredRole;
                }
                else if (declaredRole == SectionRole.STRUCTURAL) {
                    currentRole = SectionRole.UNKNOWN;
                }
                else if (!line.contextualHeading()
                        && currentRole != SectionRole.SEARCHABLE) {
                    currentRole = SectionRole.UNKNOWN;
                }
                groupingSection = null;
                groupingListLevel = 0;
                continue;
            }
            if (currentRole == SectionRole.SEARCHABLE
                    && isSearchableSubheading(lines, index)) {
                currentSection = normalizeSection(line.text());
                groupingSection = null;
                groupingListLevel = 0;
                continue;
            }
            if (groupingSection != null
                    && (!line.listItem() || line.listLevel() <= groupingListLevel)) {
                groupingSection = null;
                groupingListLevel = 0;
            }
            boolean requirementLike = isRequirementLike(line.text());
            if (isGroupingParent(lines, index)) {
                groupingSection = normalizeSection(line.text());
                groupingListLevel = line.listLevel();
                continue;
            }
            if (currentRole == SectionRole.UNKNOWN
                    && isInlineSearchableRequirement(line.text())) {
                currentRole = SectionRole.SEARCHABLE;
            }
            if (currentRole != SectionRole.SEARCHABLE
                    && startsImplicitSearchableRun(lines, index)) {
                currentRole = SectionRole.SEARCHABLE;
                currentSection = null;
            }
            if (!shouldKeepLeaf(line, currentRole, structuredPosting, requirementLike)) {
                continue;
            }

            String itemSection = groupingSection != null
                    && line.listLevel() > groupingListLevel
                    ? groupingSection
                    : currentSection;
            List<String> atomicItems = line.listItem()
                    ? List.of(line.text())
                    : splitAtClearSentenceBoundaries(line.text());
            for (String sentence : atomicItems) {
                String normalizedItem = normalizeWhitespace(sentence);
                if (!hasEnoughLettersOrDigits(normalizedItem)
                        || !seenNormalizedItems.add(normalizedItem)) {
                    continue;
                }
                List<String> boundedItems = splitForSearchLimit(normalizedItem);
                if (orderedItems.size() + boundedItems.size() > MAX_ITEM_COUNT) {
                    throw new JobPostingItemLimitExceededException(MAX_ITEM_COUNT);
                }
                for (String boundedItem : boundedItems) {
                    orderedItems.add(new DraftItem(itemSection, boundedItem));
                }
            }
        }

        List<JobPostingItemResponse> response = new ArrayList<>(orderedItems.size());
        for (int index = 0; index < orderedItems.size(); index++) {
            DraftItem item = orderedItems.get(index);
            response.add(new JobPostingItemResponse(index + 1, item.section(), item.text()));
        }
        return List.copyOf(response);
    }

    private static ParsedLine parseLine(String rawLine) {
        boolean markdownHeading = MARKDOWN_HEADING_PREFIX
                .matcher(normalizeWhitespace(rawLine))
                .find();
        String remaining = normalizePresentationMarkup(rawLine);
        if (MARKDOWN_TABLE_SEPARATOR.matcher(remaining).matches()) {
            return new ParsedLine("", 0, false, false, false);
        }
        Matcher headingMarker = HEADING_MARKER_PREFIX.matcher(remaining);
        if (headingMarker.find()) {
            remaining = normalizePresentationMarkup(remaining.substring(headingMarker.end()));
            return new ParsedLine(remaining, 0, true, true, false);
        }
        int listLevel = 0;
        boolean groupingCandidate = false;
        while (!remaining.isEmpty()) {
            Matcher asciiOrDashBullet = ASCII_OR_DASH_BULLET_PREFIX.matcher(remaining);
            Matcher unicodeBullet = UNICODE_BULLET_PREFIX.matcher(remaining);
            Matcher numbered = NUMBERED_PREFIX.matcher(remaining);
            if (asciiOrDashBullet.find()) {
                remaining = normalizePresentationMarkup(
                        remaining.substring(asciiOrDashBullet.end()));
                listLevel = SECONDARY_LIST_LEVEL;
                groupingCandidate = false;
            }
            else if (unicodeBullet.find()) {
                int marker = remaining.codePointAt(0);
                remaining = normalizePresentationMarkup(remaining.substring(unicodeBullet.end()));
                groupingCandidate = marker == '•' || marker == '●';
                boolean nestedMarker = marker == '◦' || marker == '∘';
                listLevel = nestedMarker ? SECONDARY_LIST_LEVEL : PRIMARY_LIST_LEVEL;
            }
            else if (numbered.find()) {
                remaining = normalizePresentationMarkup(remaining.substring(numbered.end()));
                listLevel = SECONDARY_LIST_LEVEL;
                groupingCandidate = false;
            }
            else {
                break;
            }
        }
        return new ParsedLine(remaining, listLevel, markdownHeading, false, groupingCandidate);
    }

    private static String normalizePresentationMarkup(String value) {
        String normalized = normalizeWhitespace(HTML_LINE_BREAK.matcher(value).replaceAll(" "));
        if (normalized.startsWith("|")) {
            normalized = normalizeWhitespace(normalized.substring(1));
        }
        if (normalized.endsWith("|")) {
            normalized = normalizeWhitespace(normalized.substring(0, normalized.length() - 1));
        }
        while (normalized.endsWith("\\")) {
            normalized = normalizeWhitespace(normalized.substring(0, normalized.length() - 1));
        }
        normalized = normalizeWhitespace(MARKDOWN_HEADING_PREFIX.matcher(normalized).replaceFirst(""));
        while (isWrappedInMarkdownEmphasis(normalized, "**")
                || isWrappedInMarkdownEmphasis(normalized, "__")) {
            normalized = normalizeWhitespace(normalized.substring(2, normalized.length() - 2));
        }
        return normalized;
    }

    private static boolean isWrappedInMarkdownEmphasis(String value, String marker) {
        return value.length() >= marker.length() * 2
                && value.startsWith(marker)
                && value.endsWith(marker);
    }

    private static boolean isSectionHeading(List<ParsedLine> lines, int index) {
        ParsedLine current = lines.get(index);
        if (current.headingMarker()) {
            return true;
        }
        if (current.text().isEmpty()) {
            return false;
        }
        if (BRACKETED_HEADING.matcher(current.text()).matches()) {
            return true;
        }
        if (current.listItem()) {
            return false;
        }
        if (hasInlineLabelValue(current.text())) {
            return false;
        }
        if (endsWithSectionColon(current.text())) {
            return true;
        }
        if (classifySectionRole(current.text()) != SectionRole.UNKNOWN
                && isRoleHeadingShape(current.text())) {
            return true;
        }
        if (endsWithSentenceTerminator(current.text())
                || !isCompactSectionHeading(current.text())
                || isRequirementLike(current.text())) {
            return false;
        }
        return nextNonEmptyLineIsListItem(lines, index)
                || followingLinesStartRequirementRun(lines, index);
    }

    private static boolean isRoleHeadingShape(String text) {
        return text.codePointCount(0, text.length()) <= 60
                && text.split(" ").length <= 10;
    }

    private static boolean isGroupingParent(List<ParsedLine> lines, int index) {
        ParsedLine current = lines.get(index);
        if (!current.listItem() || !current.groupingCandidate()) {
            return false;
        }
        for (int nextIndex = index + 1; nextIndex < lines.size(); nextIndex++) {
            ParsedLine next = lines.get(nextIndex);
            if (next.text().isEmpty()) {
                continue;
            }
            return next.listItem() && next.listLevel() > current.listLevel();
        }
        return false;
    }

    private static SectionRole classifySectionRole(String text) {
        String canonical = canonicalizeHeading(text);
        if (METADATA_SECTION_HEADINGS.contains(canonical)
                || containsAny(canonical, EXCLUDED_SECTION_STEMS)
                || containsAny(canonical, INTRO_SECTION_STEMS)
                || (canonical.contains("팀") && canonical.contains("알려"))
                || (canonical.contains("합류") && canonical.contains("여정"))
                || (canonical.contains("채용")
                        && (canonical.contains("절차")
                                || canonical.contains("과정")
                                || canonical.contains("방식")
                                || canonical.contains("프로세스")))) {
            return SectionRole.EXCLUDED;
        }
        if (containsAny(canonical, SEARCHABLE_SECTION_STEMS)) {
            return SectionRole.SEARCHABLE;
        }
        if (containsAny(canonical, STRUCTURAL_SECTION_STEMS)) {
            return SectionRole.STRUCTURAL;
        }
        return SectionRole.UNKNOWN;
    }

    private static boolean nextNonEmptyLineIsListItem(List<ParsedLine> lines, int index) {
        for (int nextIndex = index + 1; nextIndex < lines.size(); nextIndex++) {
            ParsedLine next = lines.get(nextIndex);
            if (!next.text().isEmpty()) {
                return next.listItem();
            }
        }
        return false;
    }

    private static boolean followingLinesStartRequirementRun(List<ParsedLine> lines, int index) {
        int requirementCount = 0;
        int inspectedCount = 0;
        for (int nextIndex = index + 1;
                nextIndex < lines.size() && inspectedCount < 4;
                nextIndex++) {
            ParsedLine next = lines.get(nextIndex);
            if (next.text().isEmpty()) {
                continue;
            }
            if (isSectionHeading(lines, nextIndex) || isStandaloneMetadata(next.text())) {
                break;
            }
            inspectedCount++;
            if (!isStrongRequirementLike(next.text())) {
                break;
            }
            requirementCount++;
            if (requirementCount >= 2) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStructuredPosting(List<ParsedLine> lines) {
        int strongRequirementCount = 0;
        for (int index = 0; index < lines.size(); index++) {
            ParsedLine line = lines.get(index);
            if (line.text().isEmpty()) {
                continue;
            }
            if (classifySectionRole(line.text()) != SectionRole.UNKNOWN
                    && isSectionHeading(lines, index)) {
                return true;
            }
            if (isStrongRequirementLike(line.text())) {
                strongRequirementCount++;
            }
        }
        return strongRequirementCount >= 2;
    }

    private static List<BlockRole> classifyDocumentBlocks(List<ParsedLine> lines) {
        List<BlockRole> roles = new ArrayList<>(lines.size());
        BlockRole activeRole = BlockRole.UNKNOWN;
        for (int index = 0; index < lines.size(); index++) {
            ParsedLine line = lines.get(index);
            if (line.text().isEmpty()) {
                roles.add(activeRole);
                continue;
            }

            boolean sectionHeading = isSectionHeading(lines, index);
            SectionRole sectionRole = classifySectionRole(line.text());
            if ((sectionHeading && sectionRole == SectionRole.SEARCHABLE
                    && (activeRole != BlockRole.APPLICATION_FORM
                            || isSearchableBlockBoundary(lines, index)))
                    || isInlineSearchableRequirement(line.text())) {
                activeRole = BlockRole.JOB_CONTENT;
            }
            else if (startsApplicationFormBlock(lines, index)) {
                activeRole = BlockRole.APPLICATION_FORM;
            }
            else if (startsMetadataBlock(lines, index)) {
                activeRole = BlockRole.METADATA;
            }
            else if (sectionHeading
                    && sectionRole == SectionRole.EXCLUDED
                    && activeRole != BlockRole.APPLICATION_FORM) {
                activeRole = isLegalOrPrivacyText(line.text())
                        ? BlockRole.LEGAL_OR_PRIVACY
                        : BlockRole.EXCLUDED_CONTENT;
            }
            else if (activeRole == BlockRole.EXCLUDED_CONTENT
                    && ((sectionHeading
                            && (followingRequirementLeafCount(lines, index, 5) >= 2
                                    || nextNonEmptyLineIsRequirementBullet(lines, index)))
                            || startsImplicitSearchableRun(lines, index))) {
                activeRole = BlockRole.JOB_CONTENT;
            }
            roles.add(activeRole);
        }
        return List.copyOf(roles);
    }

    private static boolean isSearchableBlockBoundary(List<ParsedLine> lines, int index) {
        return followingRequirementLeafCount(lines, index, 4) >= 1
                || nextNonEmptyLineIsRequirementBullet(lines, index);
    }

    private static boolean nextNonEmptyLineIsRequirementBullet(
            List<ParsedLine> lines,
            int index) {
        for (int nextIndex = index + 1; nextIndex < lines.size(); nextIndex++) {
            ParsedLine next = lines.get(nextIndex);
            if (!next.text().isEmpty()) {
                return next.listItem() && isRequirementLike(next.text());
            }
        }
        return false;
    }

    private static boolean startsApplicationFormBlock(List<ParsedLine> lines, int index) {
        ParsedLine current = lines.get(index);
        if (current.listItem()
                || endsWithSentenceTerminator(current.text())
                || !containsAny(canonicalizeHeading(current.text()), APPLICATION_CONTEXT_STEMS)) {
            return false;
        }

        int signalCount = 0;
        int inspectedCount = 0;
        for (int nextIndex = index + 1;
                nextIndex < lines.size() && inspectedCount < 10;
                nextIndex++) {
            ParsedLine next = lines.get(nextIndex);
            if (next.text().isEmpty()) {
                continue;
            }
            if (classifySectionRole(next.text()) == SectionRole.SEARCHABLE
                    && isSectionHeading(lines, nextIndex)) {
                break;
            }
            inspectedCount++;
            if (isFormControlLike(next.text())) {
                signalCount++;
            }
            if (signalCount >= 2) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFormControlLike(String text) {
        String canonical = canonicalizeHeading(text);
        if (containsAny(canonical, FORM_CONTROL_STEMS)
                || text.endsWith("*")
                || text.endsWith("?")
                || text.endsWith("？")) {
            return true;
        }
        return text.codePointCount(0, text.length()) <= 40
                && text.split(" ").length <= 4
                && !endsWithSentenceTerminator(text)
                && !isRequirementLike(text)
                && classifySectionRole(text) == SectionRole.UNKNOWN;
    }

    private static boolean startsMetadataBlock(List<ParsedLine> lines, int index) {
        ParsedLine current = lines.get(index);
        if (isCompensationValue(current.text())) {
            return true;
        }

        String canonical = canonicalizeHeading(current.text());
        if (current.listItem()
                || endsWithSentenceTerminator(current.text())
                || !containsAny(canonical, BLOCK_METADATA_STEMS)) {
            return false;
        }

        int signalCount = 0;
        int inspectedCount = 0;
        for (int nextIndex = index + 1;
                nextIndex < lines.size() && inspectedCount < 8;
                nextIndex++) {
            ParsedLine next = lines.get(nextIndex);
            if (next.text().isEmpty()) {
                continue;
            }
            if (classifySectionRole(next.text()) == SectionRole.SEARCHABLE
                    && isSectionHeading(lines, nextIndex)) {
                break;
            }
            inspectedCount++;
            if (isBlockMetadataLike(next.text())) {
                signalCount++;
            }
            if (signalCount >= 2) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlockMetadataLike(String text) {
        String canonical = canonicalizeHeading(text);
        return isStandaloneMetadata(text)
                || isCompensationValue(text)
                || containsAny(canonical, BLOCK_METADATA_STEMS)
                || isMetadataPipe(text);
    }

    private static boolean isCompensationValue(String text) {
        return COMPENSATION_RANGE.matcher(text).matches()
                || PERCENTAGE_METADATA.matcher(text).matches();
    }

    private static boolean isMetadataPipe(String text) {
        int separator = text.indexOf('|');
        if (separator <= 0 || separator + 1 >= text.length()) {
            return false;
        }
        String label = canonicalizeHeading(text.substring(0, separator));
        String value = normalizeWhitespace(text.substring(separator + 1));
        return hasEnoughLettersOrDigits(value)
                && (containsAny(label, BLOCK_METADATA_STEMS)
                        || text.substring(0, separator).codePoints().anyMatch(Character::isDigit));
    }

    private static boolean isLegalOrPrivacyText(String text) {
        return containsAny(canonicalizeHeading(text), LEGAL_PRIVACY_STEMS);
    }

    private static boolean isSearchableSubheading(List<ParsedLine> lines, int index) {
        ParsedLine current = lines.get(index);
        return !current.listItem()
                && !endsWithSentenceTerminator(current.text())
                && !hasInlineLabelValue(current.text())
                && current.text().codePointCount(0, current.text().length()) <= 60
                && current.text().split(" ").length <= 6
                && hasHeadingCase(current.text())
                && followingRequirementLeafCount(lines, index, 5) >= 2;
    }

    private static int followingRequirementLeafCount(
            List<ParsedLine> lines,
            int index,
            int maximumInspected) {
        int leafCount = 0;
        int inspectedCount = 0;
        for (int nextIndex = index + 1;
                nextIndex < lines.size() && inspectedCount < maximumInspected;
                nextIndex++) {
            ParsedLine next = lines.get(nextIndex);
            if (next.text().isEmpty()) {
                continue;
            }
            if (classifySectionRole(next.text()) != SectionRole.UNKNOWN
                    && isSectionHeading(lines, nextIndex)) {
                break;
            }
            inspectedCount++;
            if (isRequirementLike(next.text()) || looksLikeTechnologyList(next.text())) {
                leafCount++;
            }
        }
        return leafCount;
    }

    private static boolean looksLikeTechnologyList(String text) {
        return text.codePointCount(0, text.length()) <= 160
                && text.chars().filter(value -> value == ',').limit(2).count() >= 2;
    }

    private static boolean hasHeadingCase(String text) {
        boolean foundCasedLetter = false;
        for (String word : text.split(" ")) {
            String lower = word.toLowerCase(Locale.ROOT);
            if (HEADING_CONNECTORS.contains(lower)) {
                continue;
            }
            int firstLetter = word.codePoints().filter(Character::isLetter).findFirst().orElse(-1);
            if (firstLetter < 0 || (!Character.isUpperCase(firstLetter)
                    && !Character.isTitleCase(firstLetter))) {
                if (word.codePoints().anyMatch(Character::isLowerCase)) {
                    return false;
                }
                continue;
            }
            foundCasedLetter = true;
        }
        return foundCasedLetter || text.codePoints().noneMatch(Character::isLowerCase);
    }

    private static boolean startsImplicitSearchableRun(List<ParsedLine> lines, int index) {
        ParsedLine current = lines.get(index);
        if (current.text().isEmpty()
                || current.listItem()
                || !isStrongRequirementLike(current.text())) {
            return false;
        }
        int strongRequirementCount = 1;
        int inspectedCount = 0;
        for (int nextIndex = index + 1;
                nextIndex < lines.size() && inspectedCount < 4;
                nextIndex++) {
            ParsedLine next = lines.get(nextIndex);
            if (next.text().isEmpty()) {
                continue;
            }
            if (isSectionHeading(lines, nextIndex) || isStandaloneMetadata(next.text())) {
                break;
            }
            inspectedCount++;
            if (!isStrongRequirementLike(next.text())) {
                break;
            }
            strongRequirementCount++;
            if (strongRequirementCount >= 2
                    && next.listItem()
                    && !endsWithSentenceTerminator(current.text())) {
                return true;
            }
            if (strongRequirementCount >= 3) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldKeepLeaf(
            ParsedLine line,
            SectionRole currentRole,
            boolean structuredPosting,
            boolean requirementLike) {
        if (!structuredPosting || currentRole == SectionRole.SEARCHABLE) {
            return true;
        }
        if (currentRole == SectionRole.EXCLUDED || currentRole == SectionRole.STRUCTURAL) {
            return false;
        }
        return (line.listItem() && requirementLike)
                || isInlineSearchableRequirement(line.text());
    }

    private static boolean isInlineSearchableRequirement(String text) {
        int separator = firstMetadataSeparatorIndex(text);
        if (separator <= 0 || separator + 1 >= text.length()) {
            return false;
        }
        String label = text.substring(0, separator);
        String value = text.substring(separator + 1);
        return classifySectionRole(label) == SectionRole.SEARCHABLE
                && isRequirementLike(value);
    }

    private static boolean isRequirementLike(String text) {
        String canonical = canonicalizeHeading(text);
        return containsAny(canonical, REQUIREMENT_STEMS)
                || ENGLISH_ACTION_REQUIREMENT.matcher(text).matches()
                || ENGLISH_QUALIFICATION_REQUIREMENT.matcher(text).matches();
    }

    private static boolean isStrongRequirementLike(String text) {
        String canonical = canonicalizeHeading(text);
        return containsAny(canonical, STRONG_REQUIREMENT_STEMS)
                || ENGLISH_ACTION_REQUIREMENT.matcher(text).matches()
                || ENGLISH_QUALIFICATION_REQUIREMENT.matcher(text).matches();
    }

    private static boolean containsAny(String value, Set<String> stems) {
        return stems.stream().anyMatch(value::contains);
    }

    private static boolean isStandaloneMetadata(String text) {
        if (HEADCOUNT_METADATA.matcher(text).matches()
                || HIRING_STAGE_METADATA.matcher(text).matches()
                || STANDALONE_TIME_RANGE.matcher(text).matches()
                || PROCESS_SEQUENCE.matcher(text).matches()) {
            return true;
        }

        String canonical = canonicalizeHeading(text);
        if (containsAny(canonical, METADATA_VALUE_STEMS) && !isRequirementLike(text)) {
            return true;
        }
        if ((canonical.contains("마감") || canonical.contains("deadline"))
                && DATE_METADATA.matcher(text).matches()) {
            return true;
        }

        int separator = firstMetadataSeparatorIndex(text);
        if (separator <= 0) {
            return false;
        }
        String label = text.substring(0, separator);
        return classifySectionRole(label) == SectionRole.EXCLUDED
                || containsAny(canonicalizeHeading(label), STRUCTURAL_SECTION_STEMS)
                || containsAny(canonicalizeHeading(label), METADATA_LABEL_STEMS);
    }

    private static boolean hasInlineLabelValue(String text) {
        int separator = firstMetadataSeparatorIndex(text);
        if (separator <= 0) {
            return false;
        }
        int valueStart = separator + 1;
        if (separator + 2 < text.length() && text.charAt(separator) == ' '
                && text.charAt(separator + 1) == '/') {
            valueStart = separator + 3;
        }
        return valueStart < text.length() && hasEnoughLettersOrDigits(text.substring(valueStart));
    }

    private static int firstMetadataSeparatorIndex(String text) {
        int colon = firstColonIndex(text);
        int spacedSlash = text.indexOf(" / ");
        if (colon < 0) {
            return spacedSlash;
        }
        if (spacedSlash < 0) {
            return colon;
        }
        return Math.min(colon, spacedSlash);
    }

    private static int firstColonIndex(String text) {
        int asciiColon = text.indexOf(':');
        int fullWidthColon = text.indexOf('：');
        if (asciiColon < 0) {
            return fullWidthColon;
        }
        if (fullWidthColon < 0) {
            return asciiColon;
        }
        return Math.min(asciiColon, fullWidthColon);
    }

    private static String canonicalizeHeading(String text) {
        StringBuilder canonical = new StringBuilder(text.length());
        text.toLowerCase(Locale.ROOT).codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(canonical::appendCodePoint);
        return canonical.toString();
    }

    private static boolean isCompactSectionHeading(String text) {
        if (text.codePointCount(0, text.length()) > MAX_SECTION_HEADING_CODE_POINTS) {
            return false;
        }
        return text.split(" ").length <= 2;
    }

    private static String normalizeSection(String text) {
        String normalized = text;
        while (endsWithSectionColon(normalized)) {
            normalized = normalizeWhitespace(normalized.substring(0, normalized.length() - 1));
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static List<String> splitAtClearSentenceBoundaries(String text) {
        List<String> sentences = new ArrayList<>();
        int sentenceStart = 0;
        int index = 0;
        while (index < text.length()) {
            if (!isSentenceTerminator(text.charAt(index))) {
                index++;
                continue;
            }

            int boundaryEnd = index + 1;
            while (boundaryEnd < text.length()
                    && isSentenceTerminator(text.charAt(boundaryEnd))) {
                boundaryEnd++;
            }
            while (boundaryEnd < text.length() && isClosingMark(text.charAt(boundaryEnd))) {
                boundaryEnd++;
            }
            if (boundaryEnd == text.length() || text.charAt(boundaryEnd) == ' ') {
                addNonBlank(sentences, text.substring(sentenceStart, boundaryEnd));
                while (boundaryEnd < text.length() && text.charAt(boundaryEnd) == ' ') {
                    boundaryEnd++;
                }
                sentenceStart = boundaryEnd;
                index = boundaryEnd;
            }
            else {
                index = boundaryEnd;
            }
        }
        if (sentenceStart < text.length()) {
            addNonBlank(sentences, text.substring(sentenceStart));
        }
        return sentences;
    }

    private static List<String> splitForSearchLimit(String text) {
        if (text.length() <= MAX_ITEM_LENGTH) {
            return List.of(text);
        }

        List<String> bounded = new ArrayList<>();
        int start = 0;
        while (text.length() - start > MAX_ITEM_LENGTH) {
            int maximumEnd = safeUtf16Boundary(text, start + MAX_ITEM_LENGTH);
            int split = preferredBoundary(text, start, maximumEnd);
            split = preserveMinimumEligibleRemainder(text, start, split);
            String chunk = normalizeWhitespace(text.substring(start, split));
            if (!hasEnoughLettersOrDigits(chunk)) {
                split = preserveMinimumEligibleRemainder(text, start, maximumEnd);
                chunk = normalizeWhitespace(text.substring(start, split));
            }
            if (split <= start || !hasEnoughLettersOrDigits(chunk)) {
                throw cannotSplitWithinSearchLimit();
            }
            bounded.add(chunk);
            start = split;
            while (start < text.length() && text.charAt(start) == ' ') {
                start++;
            }
        }
        if (start < text.length()) {
            String finalChunk = normalizeWhitespace(text.substring(start));
            if (!hasEnoughLettersOrDigits(finalChunk)) {
                throw cannotSplitWithinSearchLimit();
            }
            bounded.add(finalChunk);
        }
        return List.copyOf(bounded);
    }

    private static int preserveMinimumEligibleRemainder(String text, int start, int proposedSplit) {
        int split = proposedSplit;
        while (split > start) {
            String remainder = normalizeWhitespace(text.substring(split));
            if (remainder.isEmpty() || hasEnoughLettersOrDigits(remainder)) {
                return split;
            }
            split = text.offsetByCodePoints(split, -1);
        }
        return split;
    }

    private static InvalidJobPostingSegmentationException cannotSplitWithinSearchLimit() {
        return new InvalidJobPostingSegmentationException(
                "job posting item cannot be split within the 500 character search limit");
    }

    private static int preferredBoundary(String text, int start, int maximumEnd) {
        for (int index = maximumEnd - 1; index > start; index--) {
            char current = text.charAt(index);
            if (isGenericDelimiter(current)
                    && (index + 1 == text.length() || text.charAt(index + 1) == ' ')) {
                return index + 1;
            }
        }
        for (int index = maximumEnd - 1; index > start; index--) {
            if (text.charAt(index) == ' ') {
                return index;
            }
        }
        return maximumEnd;
    }

    private static int safeUtf16Boundary(String text, int proposedEnd) {
        int end = Math.min(proposedEnd, text.length());
        if (end > 0 && end < text.length()
                && Character.isHighSurrogate(text.charAt(end - 1))
                && Character.isLowSurrogate(text.charAt(end))) {
            return end - 1;
        }
        return end;
    }

    private static boolean hasEnoughLettersOrDigits(String text) {
        return text.codePoints().filter(Character::isLetterOrDigit).limit(2).count() == 2;
    }

    private static String normalizeLineEndings(String content) {
        return content.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u0085', '\n')
                .replace('\u2028', '\n')
                .replace('\u2029', '\n');
    }

    private static String normalizeWhitespace(String value) {
        return UNICODE_WHITESPACE.matcher(value).replaceAll(" ").strip();
    }

    private static void addNonBlank(List<String> target, String candidate) {
        String normalized = normalizeWhitespace(candidate);
        if (!normalized.isEmpty()) {
            target.add(normalized);
        }
    }

    private static boolean endsWithSectionColon(String text) {
        return text.endsWith(":") || text.endsWith("：");
    }

    private static boolean endsWithSentenceTerminator(String text) {
        int index = text.length() - 1;
        while (index >= 0 && isClosingMark(text.charAt(index))) {
            index--;
        }
        return index >= 0 && isSentenceTerminator(text.charAt(index));
    }

    private static boolean isSentenceTerminator(char value) {
        return value == '.' || value == '?' || value == '!'
                || value == '。' || value == '？' || value == '！';
    }

    private static boolean isClosingMark(char value) {
        return value == '\'' || value == '"' || value == '’' || value == '”'
                || value == ')' || value == ']' || value == '}';
    }

    private static boolean isGenericDelimiter(char value) {
        return isSentenceTerminator(value)
                || value == ';' || value == '；'
                || value == ',' || value == '，'
                || value == ':' || value == '：';
    }

    private enum SectionRole {
        SEARCHABLE,
        EXCLUDED,
        STRUCTURAL,
        UNKNOWN
    }

    private enum BlockRole {
        JOB_CONTENT,
        APPLICATION_FORM,
        METADATA,
        LEGAL_OR_PRIVACY,
        EXCLUDED_CONTENT,
        UNKNOWN;

        private boolean excludesCandidates() {
            return this == APPLICATION_FORM
                    || this == METADATA
                    || this == LEGAL_OR_PRIVACY
                    || this == EXCLUDED_CONTENT;
        }
    }

    private record ParsedLine(
            String text,
            int listLevel,
            boolean headingMarker,
            boolean contextualHeading,
            boolean groupingCandidate) {

        private boolean listItem() {
            return listLevel > 0;
        }
    }

    private record DraftItem(String section, String text) {
    }
}
