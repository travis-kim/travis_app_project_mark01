# Photo Cleaner MVP (Flutter: Android + iOS)

Android 우선 검증 + iOS 확장 가능한 Flutter 공용 MVP입니다.

## 프로젝트 폴더 구조

```text
photo_cleaner_mvp/
├─ pubspec.yaml
├─ analysis_options.yaml
├─ lib/
│  ├─ main.dart
│  ├─ models/photo_action.dart
│  ├─ services/
│  │  ├─ permission_service.dart
│  │  ├─ gallery_loader.dart
│  │  └─ trash_service.dart
│  └─ screens/
│     ├─ swipe_screen.dart
│     └─ result_screen.dart
├─ android/
│  └─ app/src/main/AndroidManifest.xml
└─ ios/
   └─ Runner/Info.plist
```

## pubspec.yaml 의존성
- `photo_manager`: 권한 요청, 갤러리 최신순 조회, OS 삭제/확인 플로우 연계
- `cupertino_icons`: 기본 아이콘

## 구현 기능
1) 갤러리 권한 요청(허용/거부/제한 접근)
2) 최신순 사진 조회 + 페이징 + 프리로딩
3) 카드형 1장 뷰 + 좌/우 스와이프
4) 좌 스와이프: OS 삭제/휴지통 확인 플로우 연결
5) 우 스와이프: keep 로컬 기록
6) Undo 최근 1개
   - keep: 즉시 되돌림 가능
   - trash/delete: OS 정책상 앱 즉시 복원 불가 시 사유 안내 + 최근 삭제함 복구 유도
7) 완료 화면: 삭제/남김 카운트

## Android 권한/설정
- `READ_MEDIA_IMAGES` (Android 13+)
- `READ_EXTERNAL_STORAGE` with `maxSdkVersion=32`

파일: `android/app/src/main/AndroidManifest.xml`

## iOS 권한/설정
- `NSPhotoLibraryUsageDescription`
- `NSPhotoLibraryAddUsageDescription`

파일: `ios/Runner/Info.plist`

## 에러/예외 UX
- 권한 거부: 재요청 + 설정 열기 버튼
- 제한 접근: 상단 배너 안내
- 삭제 실패/취소: 스낵바 안내
- Undo 불가(삭제 건): 이유/대안(최근 삭제함 복구) 스낵바 안내

---

## 핸드폰에서 바로 확인하는 방법

### 0) Flutter 설치 확인
```bash
flutter --version
```

### 1) (중요) 표준 Flutter 플랫폼 파일 생성
현재 저장소는 핵심 코드 중심이므로, 로컬에서 아래 1회 실행해 Flutter 표준 실행 파일을 채워주세요.

```bash
flutter create . --platforms=android,ios
```

> `lib/` 코드는 유지됩니다. 생성된 파일 중 충돌이 나면 `lib/`와 `pubspec.yaml`은 현재 저장소 버전을 유지하세요.

### 2) 패키지 설치
```bash
flutter pub get
```

### 3) Android 실기기 실행
```bash
flutter devices
flutter run -d <android_device_id>
```

### 4) iOS 실기기/시뮬레이터 실행(macOS + Xcode)
```bash
flutter devices
flutter run -d <ios_device_id>
```

## 확인 체크리스트(실기기)
- 앱 시작 시 권한 팝업 노출
- 사진 1장 카드 표시
- 우 스와이프 → Keep + 스낵바
- 좌 스와이프 → 시스템 삭제/확인 플로우 + 스낵바
- Undo 버튼 동작(keep 복원 / delete는 안내)
- 모든 사진 처리 후 결과 화면 카운트 표시
