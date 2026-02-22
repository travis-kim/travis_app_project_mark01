# Photo Cleaner MVP (Android-first)

사진 정리 앱 MVP입니다. 한 장씩 카드를 크게 보여주고, 스와이프로 `Keep / 휴지통` 분류를 진행합니다.

## MVP UX 포함 사항
- 사진 1장 카드 표시
- 오른쪽 스와이프: Keep
- 왼쪽 스와이프: 휴지통 이동 시도
  - Android 11+(R): `MediaStore.createTrashRequest(...)`
  - Android 10(Q): `RecoverableSecurityException` 기반 시스템 확인
  - Android 9 이하: 일반 delete fallback (기기/갤러리 구현 차이 존재)
- Undo (최근 1개)
- 권한 거부 시 재요청/설정 이동 UX
- 삭제는 즉시 영구삭제 기본값으로 사용하지 않음(가능한 경우 OS Trash 사용)

## 기술 선택 (최소 의존)
- Jetpack Compose: 빠른 MVP UI 구현 및 유지보수 용이성
- AndroidX Lifecycle/ViewModel: 상태 관리
- 외부 이미지 라이브러리 미사용: MVP 단순화 (ImageDecoder 사용)

## 파일 구조
- `app/src/main/java/com/example/photocleaner/MainActivity.kt`
  - UI, ViewModel, MediaStore 접근 로직 포함
- `app/src/main/AndroidManifest.xml`
  - 최신 Android 이미지 권한 선언
- Gradle Kotlin DSL 구성

## 빌드/실행
1) JDK 17~21 권장 (AGP 호환)
2) Android SDK 설치 후 아래 실행

```bash
# 예시: JDK 21 지정
JAVA_HOME=/path/to/jdk21 gradle assembleDebug
```

3) Android Studio에서 `Run 'app'`

## 단계별 구현 포인트
1. 권한 처리
   - T+ (`READ_MEDIA_IMAGES`) / 그 이하 (`READ_EXTERNAL_STORAGE`) 분기
2. MediaStore query로 이미지 목록 로드
3. 단일 카드 + 수평 스와이프 임계치 처리
4. Trash/Delete 요청 + 시스템 확인 인텐트 결과 처리
5. Undo(최근 1개)

## iOS 확장 고려 사항 (다음 단계)
- `Photos` framework의 `PHAsset` 기반 동일 개념 매핑
- 최근 삭제 앨범 이동/삭제 확인 UX를 OS 정책에 맞춤
- Android와 공통 도메인 모델(`PhotoItem`, `SwipeDecision`)을 Kotlin Multiplatform 또는 서버 동기화 모델로 정렬
- TODO: iOS에서 Limited Photos Access 상태(선택된 사진만 접근) 전용 안내 UX 세분화

## TODO
- 성능: 썸네일 로딩/프리페치 최적화 (현재는 단순 decode)
- 접근성: TalkBack 라벨/제스처 대체 버튼
- 안정성: 삭제/복구 이벤트 로깅, 제조사별 예외 텔레메트리
- 테스트: UI 테스트 + MediaStore 통합 테스트 더미 계층 분리
