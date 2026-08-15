import 'package:flutter/material.dart';
import 'designer_page.dart';

void main() {
  runApp(const NexaDesignerApp());
}

/// Widget utama aplikasi Nexa Designer.
class NexaDesignerApp extends StatelessWidget {
  const NexaDesignerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Nexa Designer Workspace',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: Colors.blueGrey,
          brightness: Brightness.light,
        ),
        dividerTheme: const DividerThemeData(
          space: 1,
          thickness: 1,
        ),
        elevatedButtonTheme: ElevatedButtonThemeData(
          style: ElevatedButton.styleFrom(
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(6),
            ),
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          ),
        ),
      ),
      home: const DesignerPage(),
    );
  }
}
