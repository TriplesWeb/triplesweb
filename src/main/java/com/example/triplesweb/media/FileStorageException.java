package com.example.triplesweb.media;

/**
 * 파일 저장·삭제 등 스토리지 처리 실패 시 사용하는 런타임 예외.
 */
public class FileStorageException extends RuntimeException {
    public FileStorageException (String msg) {
        super(msg);
    }
    public FileStorageException(String msg, Throwable t)
    {
        super(msg, t);
    }

}
