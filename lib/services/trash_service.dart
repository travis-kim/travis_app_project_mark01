import 'package:flutter/foundation.dart';
import 'package:photo_manager/photo_manager.dart';

class TrashResult {
  TrashResult({required this.success, required this.message, this.undoSupported = false});

  final bool success;
  final String message;
  final bool undoSupported;
}

class TrashService {
  Future<TrashResult> moveToTrashOrRequestDelete(AssetEntity asset) async {
    try {
      final List<String> ids = <String>[asset.id];
      final List<String> removed = await PhotoManager.editor.deleteWithIds(ids);

      if (removed.isNotEmpty) {
        return TrashResult(
          success: true,
          message: '시스템 삭제/휴지통 확인 플로우가 완료되었습니다.',
          undoSupported: false,
        );
      }

      return TrashResult(
        success: false,
        message: '시스템 확인이 취소되었거나 실패했습니다.',
      );
    } catch (e) {
      if (kDebugMode) {
        print('delete flow error: $e');
      }
      return TrashResult(
        success: false,
        message: 'OS 정책으로 삭제를 완료하지 못했습니다. 갤러리 앱에서 수동 처리해 주세요.',
      );
    }
  }
}
