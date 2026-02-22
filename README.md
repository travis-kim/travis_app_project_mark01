# Photo Cleaner MVP (Flutter: Android + iOS)

Android 우선 검증을 목표로 하되 iOS에서도 빌드 가능하도록 구성한 Flutter 공용 MVP입니다.

## 프로젝트 폴더 구조

```text
photo_cleaner_mvp/
├─ pubspec.yaml
├─ analysis_options.yaml
├─ lib/
│  ├─ main.dart
│  ├─ models/
│  │  └─ photo_action.dart
│  ├─ services/
│  │  ├─ permission_service.dart
│  │  ├─ gallery_loader.dart
│  │  └─ trash_service.dart
│  └─ screens/
│     ├─ swipe_screen.dart
│     └─ result_screen.dart
├─ android/
│  ├─ build.gradle
│  ├─ settings.gradle
│  ├─ gradle.properties
│  └─ app/
│     ├─ build.gradle
│     └─ src/main/
│        ├─ AndroidManifest.xml
│        ├─ kotlin/com/example/photo_cleaner_mvp/MainActivity.kt
│        └─ res/values/styles.xml
└─ ios/
   └─ Runner/
      └─ Info.plist
```

## 의존성 (`pubspec.yaml`)
- `photo_manager`: 갤러리 권한, 앨범 조회, 페이징 로드, 시스템 삭제 요청 플로우 연계
- `cupertino_icons`: 기본 아이콘

## 핵심 UX / 기능
1. 권한 요청(허용/거부/제한 접근 UI)
2. 최신순 사진 로드 + 페이지 단위 로딩 + 임계치 프리로드
3. 카드형 1장 뷰 + 좌/우 스와이프
4. 좌 스와이프: OS 삭제/휴지통 요청 플로우(`deleteWithIds` 통해 시스템 확인 연계)
5. 우 스와이프: keep 로컬 기록
6. Undo 최근 1개
   - keep는 되돌림 가능
   - delete/trash는 OS 정책상 앱 내 즉시 복구 제한(최근 삭제함 복구 안내)
7. 완료 화면(삭제/남김 카운트)

## 플랫폼 설정

### Android
- `READ_MEDIA_IMAGES` (Android 13+)
- `READ_EXTERNAL_STORAGE` (`maxSdkVersion=32`)
- Flutter embedding v2

### iOS
- `NSPhotoLibraryUsageDescription`
- `NSPhotoLibraryAddUsageDescription`

## 에러/예외 UX
- 권한 거부: 재요청 버튼 + 설정 이동 버튼
- 제한 접근: 배너 안내
- 삭제 실패/취소: 스낵바 안내
- Undo 불가(삭제 건): 이유 + 대안(갤러리 최근 삭제함 복구) 스낵바 안내

## 실행 방법

```bash
flutter pub get
flutter run -d android
# 또는 iOS
flutter run -d ios
```

> 참고: 실제 iOS 실행은 macOS + Xcode 환경이 필요합니다.
