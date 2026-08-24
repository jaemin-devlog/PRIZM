/**
 * 문서 처리 작업을 claim하고 원문 추출, 청크 분할, 임베딩 저장을 거쳐 새 버전을 활성화한다.
 * lease와 claim version으로 중단된 작업을 복구하고 오래된 worker의 완료 처리를 막는다.
 */
package com.prizm.ingestion;
