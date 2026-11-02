package kali.sync.application;

/** 동기화가 찾은 발행 대상이 0개라 아무것도 바꾸지 않을 때 (스펙 5. 발행의 발행 대상). */
public class NoPublishTargetException extends RuntimeException {

    public NoPublishTargetException() {
        super("발행 대상(publish: true) 노트가 0개라 아무것도 바꾸지 않았다. 볼트 경로를 확인한다");
    }
}
