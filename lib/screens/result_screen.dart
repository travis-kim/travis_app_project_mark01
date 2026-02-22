import 'package:flutter/material.dart';

class ResultScreen extends StatelessWidget {
  const ResultScreen({
    super.key,
    required this.keepCount,
    required this.trashCount,
    required this.onRestart,
  });

  final int keepCount;
  final int trashCount;
  final VoidCallback onRestart;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('정리 완료')),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              Text('남김: $keepCount장', style: Theme.of(context).textTheme.headlineSmall),
              const SizedBox(height: 8),
              Text('삭제/휴지통: $trashCount장', style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 20),
              const Text('삭제된 항목 복구는 갤러리의 최근 삭제함에서 진행하세요.'),
              const SizedBox(height: 20),
              FilledButton(onPressed: onRestart, child: const Text('다시 시작')),
            ],
          ),
        ),
      ),
    );
  }
}
