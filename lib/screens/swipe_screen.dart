import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:photo_manager/photo_manager.dart';

import '../models/photo_action.dart';
import '../services/gallery_loader.dart';
import '../services/permission_service.dart';
import '../services/trash_service.dart';
import 'result_screen.dart';

class SwipeScreen extends StatefulWidget {
  const SwipeScreen({super.key});

  @override
  State<SwipeScreen> createState() => _SwipeScreenState();
}

class _SwipeScreenState extends State<SwipeScreen> {
  final PermissionService _permissionService = PermissionService();
  final GalleryLoader _galleryLoader = GalleryLoader();
  final TrashService _trashService = TrashService();

  bool _loading = true;
  bool _permissionGranted = false;
  bool _limited = false;

  int _currentIndex = 0;
  int _keepCount = 0;
  int _trashCount = 0;
  LastAction? _lastAction;

  @override
  void initState() {
    super.initState();
    _initialize();
  }

  Future<void> _initialize() async {
    setState(() {
      _loading = true;
    });

    final PermissionResult permission = await _permissionService.requestGalleryPermission();

    if (!mounted) return;

    if (!permission.authorized) {
      setState(() {
        _permissionGranted = false;
        _loading = false;
      });
      return;
    }

    await _galleryLoader.reset();

    if (!mounted) return;
    setState(() {
      _permissionGranted = true;
      _limited = permission.limited;
      _loading = false;
      _currentIndex = 0;
      _keepCount = 0;
      _trashCount = 0;
      _lastAction = null;
    });

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(permission.message)),
    );
  }

  AssetEntity? get _currentAsset {
    final List<AssetEntity> assets = _galleryLoader.assets;
    if (_currentIndex < 0 || _currentIndex >= assets.length) {
      return null;
    }
    return assets[_currentIndex];
  }

  Future<void> _onKeep() async {
    final AssetEntity? asset = _currentAsset;
    if (asset == null) return;

    setState(() {
      _lastAction = LastAction(asset: asset, decision: SwipeDecision.keep, index: _currentIndex);
      _keepCount += 1;
      _currentIndex += 1;
    });

    await _galleryLoader.ensurePrefetch(_currentIndex);
    if (!mounted) return;

    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Keep로 기록했습니다.')),
    );
    setState(() {});
  }

  Future<void> _onTrash() async {
    final AssetEntity? asset = _currentAsset;
    if (asset == null) return;

    final TrashResult result = await _trashService.moveToTrashOrRequestDelete(asset);

    if (!mounted) return;

    if (!result.success) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(result.message)),
      );
      return;
    }

    setState(() {
      _lastAction = LastAction(
        asset: asset,
        decision: SwipeDecision.trash,
        index: _currentIndex,
        undoSupported: result.undoSupported,
        undoMessage: result.undoSupported
            ? null
            : '삭제/휴지통 이동 건은 OS 정책상 앱에서 즉시 복구(Undo)할 수 없습니다. 최근 삭제함에서 복구해 주세요.',
      );
      _trashCount += 1;
      _currentIndex += 1;
    });

    await _galleryLoader.ensurePrefetch(_currentIndex);
    if (!mounted) return;

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(result.message)),
    );
    setState(() {});
  }

  void _undo() {
    final LastAction? action = _lastAction;
    if (action == null) return;

    if (action.decision == SwipeDecision.keep) {
      setState(() {
        _keepCount = (_keepCount - 1).clamp(0, 1 << 30);
        _currentIndex = action.index;
        _lastAction = null;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('최근 Keep 동작을 되돌렸습니다.')),
      );
      return;
    }

    final bool canUndoDelete = action.undoSupported ?? false;
    if (!canUndoDelete) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(action.undoMessage ?? '이 동작은 Undo를 지원하지 않습니다.')),
      );
      return;
    }
  }

  Future<void> _tryLoadMoreIfNeeded() async {
    if (_currentAsset != null || !_galleryLoader.hasMore) return;
    setState(() {
      _loading = true;
    });
    await _galleryLoader.loadNextPage();
    if (!mounted) return;
    setState(() {
      _loading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    final AssetEntity? current = _currentAsset;
    final bool completed = _permissionGranted && !_loading && current == null && !_galleryLoader.hasMore;

    if (_permissionGranted && !_loading && current == null && _galleryLoader.hasMore) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _tryLoadMoreIfNeeded();
      });
    }

    if (completed) {
      return ResultScreen(
        keepCount: _keepCount,
        trashCount: _trashCount,
        onRestart: _initialize,
      );
    }

    return Scaffold(
      appBar: AppBar(title: const Text('Photo Cleaner MVP')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: _loading
            ? const Center(child: CircularProgressIndicator())
            : !_permissionGranted
                ? _PermissionDeniedView(
                    message: '사진 권한이 거부되었습니다. 설정에서 권한을 허용해 주세요.',
                    onRequestAgain: _initialize,
                    onOpenSettings: _permissionService.openPermissionSettings,
                  )
                : Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: <Widget>[
                      if (_limited)
                        const Padding(
                          padding: EdgeInsets.only(bottom: 8),
                          child: Text('제한 접근: 선택된 사진만 표시됩니다.'),
                        ),
                      Text('진행: ${_currentIndex + 1} / ${_galleryLoader.assets.length}'),
                      const SizedBox(height: 12),
                      Expanded(
                        child: current == null
                            ? const Center(child: CircularProgressIndicator())
                            : _SwipeCard(
                                asset: current,
                                onSwipeLeft: _onTrash,
                                onSwipeRight: _onKeep,
                              ),
                      ),
                      const SizedBox(height: 12),
                      const Text('왼쪽: 휴지통/시스템 삭제 요청, 오른쪽: Keep'),
                      const SizedBox(height: 8),
                      Row(
                        children: <Widget>[
                          Expanded(
                            child: OutlinedButton(
                              onPressed: _undo,
                              child: const Text('Undo (최근 1개)'),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
      ),
    );
  }
}

class _SwipeCard extends StatefulWidget {
  const _SwipeCard({
    required this.asset,
    required this.onSwipeLeft,
    required this.onSwipeRight,
  });

  final AssetEntity asset;
  final Future<void> Function() onSwipeLeft;
  final Future<void> Function() onSwipeRight;

  @override
  State<_SwipeCard> createState() => _SwipeCardState();
}

class _SwipeCardState extends State<_SwipeCard> {
  double _dx = 0;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onHorizontalDragUpdate: (DragUpdateDetails details) {
        setState(() {
          _dx += details.delta.dx;
        });
      },
      onHorizontalDragEnd: (_) async {
        if (_dx < -140) {
          await widget.onSwipeLeft();
        } else if (_dx > 140) {
          await widget.onSwipeRight();
        }
        if (mounted) {
          setState(() {
            _dx = 0;
          });
        }
      },
      child: Container(
        decoration: BoxDecoration(
          color: Colors.grey.shade200,
          borderRadius: BorderRadius.circular(16),
        ),
        alignment: Alignment.center,
        child: FutureBuilder<Uint8List?>(
          key: ValueKey<String>(widget.asset.id),
          future: widget.asset.thumbnailDataWithSize(const ThumbnailSize(1080, 1080)),
          builder: (BuildContext context, AsyncSnapshot<Uint8List?> snapshot) {
            if (snapshot.connectionState != ConnectionState.done) {
              return const CircularProgressIndicator();
            }
            final Uint8List? bytes = snapshot.data;
            if (bytes == null) {
              return const Text('이미지를 불러올 수 없습니다.');
            }
            return ClipRRect(
              borderRadius: BorderRadius.circular(16),
              child: Image.memory(bytes, fit: BoxFit.cover, width: double.infinity, height: double.infinity),
            );
          },
        ),
      ),
    );
  }
}

class _PermissionDeniedView extends StatelessWidget {
  const _PermissionDeniedView({
    required this.message,
    required this.onRequestAgain,
    required this.onOpenSettings,
  });

  final String message;
  final Future<void> Function() onRequestAgain;
  final Future<void> Function() onOpenSettings;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          Text(message, textAlign: TextAlign.center),
          const SizedBox(height: 12),
          FilledButton(
            onPressed: onRequestAgain,
            child: const Text('권한 다시 요청'),
          ),
          const SizedBox(height: 8),
          OutlinedButton(
            onPressed: onOpenSettings,
            child: const Text('설정 열기'),
          ),
        ],
      ),
    );
  }
}
