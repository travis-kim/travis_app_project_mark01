import 'package:flutter/material.dart';

import 'screens/swipe_screen.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const PhotoCleanerApp());
}

class PhotoCleanerApp extends StatelessWidget {
  const PhotoCleanerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Photo Cleaner MVP',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
      home: const SwipeScreen(),
    );
  }
}
