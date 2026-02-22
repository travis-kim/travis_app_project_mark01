import 'package:photo_manager/photo_manager.dart';

class PermissionResult {
  PermissionResult({
    required this.authorized,
    required this.limited,
    required this.message,
  });

  final bool authorized;
  final bool limited;
  final String message;
}

class PermissionService {
  Future<PermissionResult> requestGalleryPermission() async {
    final PermissionState state = await PhotoManager.requestPermissionExtend();

    if (state == PermissionState.authorized) {
      return PermissionResult(
        authorized: true,
        limited: false,
        message: '사진 접근 권한이 허용되었습니다.',
      );
    }

    if (state == PermissionState.limited) {
      return PermissionResult(
        authorized: true,
        limited: true,
        message: '제한된 사진 접근 상태입니다. 선택된 사진만 표시됩니다.',
      );
    }

    return PermissionResult(
      authorized: false,
      limited: false,
      message: '사진 권한이 거부되었습니다. 설정에서 권한을 허용해 주세요.',
    );
  }

  Future<void> openPermissionSettings() => PhotoManager.openSetting();
}
