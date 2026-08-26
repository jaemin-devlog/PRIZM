package com.prizm.documenttag.model;

/** SYSTEM은 모든 USER가 사용할 수 있는 공용 태그이고, USER는 생성한 소유자에게만 보이는 태그다. */
public enum TagSource {
    SYSTEM,
    USER
}
