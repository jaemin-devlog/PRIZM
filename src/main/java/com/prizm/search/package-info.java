/**
 * 인증된 사용자의 ACTIVE 문서 버전에서 Career Evidence를 찾고 원문 위치를 제시한다.
 *
 * <p>Dense 검색으로 후보를 가져온 뒤 질의의 식별자와 숫자, 핵심 표현을 기준으로 관련성을
 * 확인한다. 필요한 경우에만 제한된 fallback과 rescue를 적용하고, 선택된 근거는 원문에서
 * 그대로 발췌한 snippet으로 위치화한다. 이 패키지는 경력의 진위나 경험 유무, 요구사항
 * 충족 여부를 판정하지 않는다.</p>
 */
package com.prizm.search;
