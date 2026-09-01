import 'package:flutter/material.dart';
import 'screens/home_screen.dart';

void main() {
  runApp(const GrabadorApp());
}

class GrabadorApp extends StatelessWidget {
  const GrabadorApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Grabador de Llamadas',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF1565C0)),
      ),
      home: const HomeScreen(),
    );
  }
}
