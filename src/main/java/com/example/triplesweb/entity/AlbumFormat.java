package com.example.triplesweb.entity;

/**
 * 앨범 규격(발매 형식). 목록/상세 배지와 관리자 선택에 사용.
 */
public enum AlbumFormat {
    /** 싱글 (디지털) */
    DIGITAL_SINGLE,
    /** 앨범 (디지털) */
    DIGITAL_ALBUM,
    /** 더블 앨범 */
    DOUBLE_ALBUM,
    /** EP (미니 앨범) */
    EP,
    /** 콘셉트 앨범 */
    CONCEPT,
    /** 라이브 앨범 */
    LIVE,
    /** 리패키지 */
    REPACKAGE,
    /** 컴필레이션 앨범 */
    COMPILATION;

    public String labelKo() {
        return switch (this) {
            case DIGITAL_SINGLE -> "싱글 (디지털)";
            case DIGITAL_ALBUM -> "앨범 (디지털)";
            case DOUBLE_ALBUM -> "더블 앨범";
            case EP -> "EP (미니 앨범)";
            case CONCEPT -> "콘셉트 앨범";
            case LIVE -> "라이브 앨범";
            case REPACKAGE -> "리패키지";
            case COMPILATION -> "컴필레이션 앨범";
        };
    }
}
