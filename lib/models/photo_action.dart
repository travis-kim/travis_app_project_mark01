import 'package:photo_manager/photo_manager.dart';

enum SwipeDecision { keep, trash }

class LastAction {
  LastAction({
    required this.asset,
    required this.decision,
    required this.index,
    this.undoSupported,
    this.undoMessage,
  });

  final AssetEntity asset;
  final SwipeDecision decision;
  final int index;
  final bool? undoSupported;
  final String? undoMessage;
}
