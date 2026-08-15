import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'models.dart';

/// Painter khusus untuk menggambar grid latar belakang dan kabel bezier
/// yang menghubungkan port input/output antar-node di workspace.
class WorkspacePainter extends CustomPainter {
  final List<NodeModel> nodes;
  final List<Connection> connections;
  
  // Posisi kabel yang sedang ditarik secara aktif oleh pengguna (jika ada)
  final Offset? activeWireStart;
  final Offset? activeWireEnd;

  // Ukuran standar visual node agar koordinat port sinkron dengan UI
  static const double nodeWidth = 160.0;
  static const double nodeHeight = 48.0;

  WorkspacePainter({
    required this.nodes,
    required this.connections,
    this.activeWireStart,
    this.activeWireEnd,
  });

  @override
  void paint(Canvas canvas, Size size) {
    _drawGrid(canvas, size);
    _drawConnections(canvas);
    _drawActiveWire(canvas);
  }

  /// Menggambar grid latar belakang bertipe dot (titik-titik halus) seperti Node-RED.
  void _drawGrid(Canvas canvas, Size size) {
    final Paint gridPaint = Paint()
      ..color = Colors.grey.withOpacity(0.15)
      ..strokeWidth = 1.0;

    const double step = 20.0; // Jarak antar titik grid
    for (double x = 0; x < size.width; x += step) {
      for (double y = 0; y < size.height; y += step) {
        canvas.drawCircle(Offset(x, y), 1.0, gridPaint);
      }
    }
  }

  /// Menggambar semua kabel koneksi yang menghubungkan port output ke port input.
  void _drawConnections(Canvas canvas) {
    final Paint wirePaint = Paint()
      ..color = const Color(0xFF999999) // Warna abu-abu kabel Node-RED
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3.0
      ..strokeCap = StrokeCap.round
      ..isAntiAlias = true;

    for (final Connection conn in connections) {
      // Cari node asal dan tujuan berdasarkan ID koneksi
      final NodeModel? fromNode = _findNodeById(conn.fromNodeId);
      final NodeModel? toNode = _findNodeById(conn.toNodeId);

      if (fromNode != null && toNode != null) {
        // Hitung posisi absolut port asal (output di sisi kanan)
        final Offset start = _getOutputPortPosition(fromNode, conn.fromPortIndex);
        // Hitung posisi absolut port tujuan (input di sisi kiri)
        final Offset end = _getInputPortPosition(toNode, conn.toPortIndex);

        _drawBezierCurve(canvas, start, end, wirePaint);
      }
    }
  }

  /// Menggambar kabel aktif yang sedang ditarik interaktif dari port ke posisi cursor mouse.
  void _drawActiveWire(Canvas canvas) {
    if (activeWireStart != null && activeWireEnd != null) {
      final Paint activeWirePaint = Paint()
        ..color = Colors.blue.withOpacity(0.7) // Warna biru transparan untuk kabel aktif
        ..style = PaintingStyle.stroke
        ..strokeWidth = 3.0
        ..strokeCap = StrokeCap.round
        ..isAntiAlias = true;

      _drawBezierCurve(canvas, activeWireStart!, activeWireEnd!, activeWirePaint);
    }
  }

  /// Menggambar kurva Bezier kubik yang indah yang melengkung dari titik awal ke titik akhir.
  void _drawBezierCurve(Canvas canvas, Offset start, Offset end, Paint paint) {
    final Path path = Path();
    path.moveTo(start.dx, start.dy);

    // Hitung jarak X antara titik awal dan akhir
    final double dx = (end.dx - start.dx).abs();
    // Tentukan tingkat kelengkungan (minimum 50px untuk mencegah garis lurus kaku)
    final double controlOffset = math.max(50.0, dx * 0.5);

    // Titik kontrol pertama ditarik ke kanan dari titik awal
    final double cp1x = start.dx + controlOffset;
    final double cp1y = start.dy;

    // Titik kontrol kedua ditarik ke kiri dari titik akhir
    final double cp2x = end.dx - controlOffset;
    final double cp2y = end.dy;

    // Gambar kurva bezier kubik
    path.cubicTo(cp1x, cp1y, cp2x, cp2y, end.dx, end.dy);
    canvas.drawPath(path, paint);
  }

  /// Helper untuk mencari Node berdasarkan ID-nya.
  NodeModel? _findNodeById(String id) {
    for (final NodeModel node in nodes) {
      if (node.id == id) return node;
    }
    return null;
  }

  /// Mendapatkan koordinat absolut port input (sisi kiri node).
  Offset _getInputPortPosition(NodeModel node, int portIndex) {
    // Port input ditempatkan di bagian tengah sisi kiri dari container node
    return Offset(
      node.position.dx,
      node.position.dy + (nodeHeight / 2),
    );
  }

  /// Mendapatkan koordinat absolut port output (sisi kanan node).
  /// Mendukung multi-output dengan mendistribusikan posisinya secara vertikal.
  Offset _getOutputPortPosition(NodeModel node, int portIndex) {
    final double x = node.position.dx + nodeWidth;
    
    // Distribusikan port secara vertikal jika ada lebih dari 1 output
    final int totalPorts = node.outputCount;
    final double y = node.position.dy + 
        (nodeHeight * (portIndex + 1)) / (totalPorts + 1);

    return Offset(x, y);
  }

  @override
  bool shouldRepaint(covariant WorkspacePainter oldDelegate) {
    return oldDelegate.nodes != nodes ||
        oldDelegate.connections != connections ||
        oldDelegate.activeWireStart != activeWireStart ||
        oldDelegate.activeWireEnd != activeWireEnd;
  }
}
