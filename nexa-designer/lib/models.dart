import 'package:flutter/material.dart';

/// Enum yang merepresentasikan jenis-jenis node yang tersedia di Nexa Designer.
enum NodeType {
  inject,
  debug,
  function,
  mqttIn,
  mqttOut,
  delay,
  trigger;

  /// Mendapatkan label teks yang ramah pengguna untuk setiap tipe node.
  String get displayLabel {
    switch (this) {
      case NodeType.inject:
        return 'Inject';
      case NodeType.debug:
        return 'Debug';
      case NodeType.function:
        return 'Function';
      case NodeType.mqttIn:
        return 'MQTT In';
      case NodeType.mqttOut:
        return 'MQTT Out';
      case NodeType.delay:
        return 'Delay';
      case NodeType.trigger:
        return 'Trigger';
    }
  }

  /// Mendapatkan warna representasi untuk setiap tipe node (mirip skema warna Node-RED).
  Color get color {
    switch (this) {
      case NodeType.inject:
        return const Color(0xFFC0DEED); // Biru muda
      case NodeType.debug:
        return const Color(0xFFD8BFD8); // Ungu muda
      case NodeType.function:
        return const Color(0xFFFDD0A2); // Jingga muda
      case NodeType.mqttIn:
      case NodeType.mqttOut:
        return const Color(0xFFD2E5D0); // Hijau muda
      case NodeType.delay:
      case NodeType.trigger:
        return const Color(0xFFE6C280); // Kuning gelap / emas
    }
  }

  /// Mendapatkan ikon representasi untuk setiap tipe node.
  IconData get icon {
    switch (this) {
      case NodeType.inject:
        return Icons.play_arrow;
      case NodeType.debug:
        return Icons.bug_report;
      case NodeType.function:
        return Icons.code;
      case NodeType.mqttIn:
        return Icons.cloud_download;
      case NodeType.mqttOut:
        return Icons.cloud_upload;
      case NodeType.delay:
        return Icons.hourglass_empty;
      case NodeType.trigger:
        return Icons.bolt;
    }
  }
}

/// Model data untuk menampung properti satu Node di dalam workspace.
class NodeModel {
  final String id;
  final String label;
  final NodeType type;
  Offset position;
  final int inputCount;
  final int outputCount;

  NodeModel({
    required this.id,
    required this.label,
    required this.type,
    required this.position,
    this.inputCount = 1,
    this.outputCount = 1,
  });

  /// Mengonversi objek NodeModel menjadi Map untuk representasi JSON Nexa.
  Map<String, dynamic> toJson() {
    return <String, dynamic>{
      'id': id,
      'type': type.name,
      'label': label,
      'x': position.dx,
      'y': position.dy,
      'inputs': inputCount,
      'outputs': outputCount,
    };
  }

  /// Membuat salinan dari NodeModel dengan beberapa properti yang dimodifikasi.
  NodeModel copyWith({
    String? id,
    String? label,
    NodeType? type,
    Offset? position,
    int? inputCount,
    int? outputCount,
  }) {
    return NodeModel(
      id: id ?? this.id,
      label: label ?? this.label,
      type: type ?? this.type,
      position: position ?? this.position,
      inputCount: inputCount ?? this.inputCount,
      outputCount: outputCount ?? this.outputCount,
    );
  }
}

/// Model data yang mendefinisikan koneksi kabel (wire) dari port output ke port input.
class Connection {
  final String id;
  final String fromNodeId;
  final int fromPortIndex; // Indeks port output (0-indexed)
  final String toNodeId;
  final int toPortIndex; // Indeks port input (biasanya 0)

  const Connection({
    required this.id,
    required this.fromNodeId,
    required this.fromPortIndex,
    required this.toNodeId,
    required this.toPortIndex,
  });

  /// Mengonversi objek Connection menjadi Map untuk representasi JSON Nexa.
  Map<String, dynamic> toJson() {
    return <String, dynamic>{
      'id': id,
      'from': {
        'nodeId': fromNodeId,
        'port': fromPortIndex,
      },
      'to': {
        'nodeId': toNodeId,
        'port': toPortIndex,
      },
    };
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is Connection &&
          runtimeType == other.runtimeType &&
          fromNodeId == other.fromNodeId &&
          fromPortIndex == other.fromPortIndex &&
          toNodeId == other.toNodeId &&
          toPortIndex == other.toPortIndex;

  @override
  int get hashCode =>
      fromNodeId.hashCode ^
      fromPortIndex.hashCode ^
      toNodeId.hashCode ^
      toPortIndex.hashCode;
}
