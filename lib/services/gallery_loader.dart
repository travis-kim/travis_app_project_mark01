import 'package:photo_manager/photo_manager.dart';

class GalleryLoader {
  GalleryLoader({this.pageSize = 60, this.preloadThreshold = 10});

  final int pageSize;
  final int preloadThreshold;

  AssetPathEntity? _album;
  int _page = 0;
  bool _hasMore = true;
  bool _loading = false;
  final List<AssetEntity> _loaded = <AssetEntity>[];

  List<AssetEntity> get assets => List<AssetEntity>.unmodifiable(_loaded);
  bool get hasMore => _hasMore;

  Future<void> reset() async {
    _album = null;
    _page = 0;
    _hasMore = true;
    _loading = false;
    _loaded.clear();

    final List<AssetPathEntity> albums = await PhotoManager.getAssetPathList(
      type: RequestType.image,
      onlyAll: true,
      filterOption: FilterOptionGroup(
        orders: <OrderOption>[
          const OrderOption(type: OrderOptionType.createDate, asc: false),
        ],
      ),
    );

    if (albums.isEmpty) {
      _hasMore = false;
      return;
    }

    _album = albums.first;
    await loadNextPage();
  }

  Future<void> loadNextPage() async {
    if (_loading || !_hasMore || _album == null) return;
    _loading = true;
    try {
      final List<AssetEntity> pageItems = await _album!.getAssetListPaged(
        page: _page,
        size: pageSize,
      );
      if (pageItems.isEmpty) {
        _hasMore = false;
      } else {
        _loaded.addAll(pageItems);
        _page += 1;
        if (pageItems.length < pageSize) {
          _hasMore = false;
        }
      }
    } finally {
      _loading = false;
    }
  }

  Future<void> ensurePrefetch(int currentIndex) async {
    if (!_hasMore) return;
    if (_loaded.length - currentIndex <= preloadThreshold) {
      await loadNextPage();
    }
  }
}
