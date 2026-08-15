import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'models.dart';
import 'workspace_painter.dart';

/// Halaman utama editor desainer nexa workspace.
class DesignerPage extends StatefulWidget {
  const DesignerPage({super.key});

  @override
  State<DesignerPage> createState() => _DesignerPageState();
}

class _DesignerPageState extends State<DesignerPage> {
  // GlobalKey untuk menghitung koordinat lokal relatif terhadap canvas stack
  final GlobalKey _canvasKey = GlobalKey();

  // List data utama workspace
  final List<NodeModel> _nodes = [];
  final List<Connection> _connections = [];

  // State untuk kabel konektor aktif yang sedang ditarik oleh user
  Offset? _activeWireStart;
  Offset? _activeWireEnd;
  String? _activeWireFromNodeId;
  int? _activeWireFromPortIndex;

  // Node yang sedang diseleksi (untuk diedit/dihapus)
  NodeModel? _selectedNode;

  // Counter sederhana untuk generate ID unik node
  int _nodeCounter = 0;

  @override
  void initState() {
    super.initState();
    // Tambahkan beberapa node default agar workspace tidak kosong saat awal dimuat
    _addInitialNodes();
  }

  /// Inisialisasi awal dengan beberapa node contoh agar user langsung paham.
  void _addInitialNodes() {
    final String injectId = _generateNodeId();
    final String debugId = _generateNodeId();

    _nodes.addAll([
      NodeModel(
        id: injectId,
        label: 'Inject 123',
        type: NodeType.inject,
        position: const Offset(80.0, 150.0),
        inputCount: 0, // Inject biasanya tidak punya input
        outputCount: 1,
      ),
      NodeModel(
        id: debugId,
        label: 'debug nfdsfjsjsfj',
        type: NodeType.debug,
        position: const Offset(350.0, 150.0),
        inputCount: 1,
        outputCount: 0, // Debug biasanya tidak punya output
      ),
    ]);

    // Hubungkan inject ke debug secara otomatis di awal
    _connections.add(Connection(
      id: 'conn_init',
      fromNodeId: injectId,
      fromPortIndex: 0,
      toNodeId: debugId,
      toPortIndex: 0,
    ));
  }

  /// Membuat ID unik untuk setiap node baru.
  String _generateNodeId() {
    _nodeCounter++;
    return 'nexa_node_$_nodeCounter';
  }

  /// Helper untuk menerjemahkan posisi layar global menjadi posisi lokal di dalam canvas stack.
  Offset _getLocalOffset(Offset globalOffset) {
    final RenderBox? renderBox = _canvasKey.currentContext?.findRenderObject() as RenderBox?;
    if (renderBox == null) return globalOffset;
    return renderBox.globalToLocal(globalOffset);
  }

  /// Menghitung posisi absolut dari Port Input pada node tertentu di koordinat canvas.
  Offset _getInputPortPosition(NodeModel node) {
    return Offset(
      node.position.dx,
      node.position.dy + (WorkspacePainter.nodeHeight / 2),
    );
  }

  /// Menghitung posisi absolut dari Port Output ke-i pada node tertentu di koordinat canvas.
  Offset _getOutputPortPosition(NodeModel node, int portIndex) {
    final double x = node.position.dx + WorkspacePainter.nodeWidth;
    final int totalPorts = node.outputCount;
    final double y = node.position.dy + 
        (WorkspacePainter.nodeHeight * (portIndex + 1)) / (totalPorts + 1);
    return Offset(x, y);
  }

  /// Menambahkan node baru ke canvas saat di-drop dari Palette.
  void _addNewNode(NodeType type, Offset localPosition) {
    setState(() {
      final String id = _generateNodeId();
      
      // Atur default port berdasarkan tipe node
      int inputs = 1;
      int outputs = 1;

      if (type == NodeType.inject) {
        inputs = 0;
      } else if (type == NodeType.debug || type == NodeType.mqttOut) {
        outputs = 0;
      } else if (type == NodeType.function) {
        // Function di Node-RED defaultnya 1 input, 1 output, tapi bisa multi-output
        outputs = 2; // Contoh multi-output default untuk pembuktian fitur
      }

      _nodes.add(NodeModel(
        id: id,
        label: '${type.displayLabel} ${_nodes.length + 1}',
        type: type,
        position: localPosition,
        inputCount: inputs,
        outputCount: outputs,
      ));
    });
  }

  /// Mengekspor state workspace saat ini menjadi skema JSON nexa framework.
  String _generateWorkspaceJson() {
    final Map<String, dynamic> output = {
      'workspaceId': 'nexa_default_flow',
      'version': '1.0.0',
      'nodes': _nodes.map((node) => node.toJson()).toList(),
      'connections': _connections.map((conn) => conn.toJson()).toList(),
    };
    return const JsonEncoder.withIndent('  ').convert(output);
  }

  @override
  Widget build(BuildContext context) {
    final String jsonText = _generateWorkspaceJson();

    return Scaffold(
      backgroundColor: const Color(0xFFF5F5F5),
      body: Row(
        children: [
          // 1. PALETTE (Bilah Kiri)
          _buildPalette(),

          // 2. WORKSPACE CANVAS (Area Tengah)
          Expanded(
            child: _buildWorkspace(),
          ),

          // 3. JSON OUTPUT & INFO SIDEBAR (Bilah Kanan)
          _buildInfoSidebar(jsonText),
        ],
      ),
    );
  }

  /// Widget Bilah Kiri: Menampilkan daftar tipe node yang bisa di-drag.
  Widget _buildPalette() {
    return Container(
      width: 220,
      decoration: BoxDecoration(
        color: Colors.white,
        border: Border(right: BorderSide(color: Colors.grey.shade300)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.all(16.0),
            color: Colors.blueGrey.shade900,
            width: double.infinity,
            child: const Text(
              'Nexa Palette',
              style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16),
            ),
          ),
          const Padding(
            padding: EdgeInsets.all(12.0),
            child: Text(
              'DRAG & DROP TO CANVAS',
              style: TextStyle(color: Colors.grey, fontSize: 12, fontWeight: FontWeight.w600),
            ),
          ),
          Expanded(
            child: ListView(
              padding: const EdgeInsets.symmetric(horizontal: 12.0),
              children: NodeType.values.map((type) => _buildPaletteItem(type)).toList(),
            ),
          ),
        ],
      ),
    );
  }

  /// Membangun satu item Draggable di Palette.
  Widget _buildPaletteItem(NodeType type) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8.0),
      child: Draggable<NodeType>(
        data: type,
        feedback: Material(
          color: Colors.transparent,
          child: Opacity(
            opacity: 0.8,
            child: _buildNodeCardPreview(type),
          ),
        ),
        childWhenDragging: Opacity(
          opacity: 0.4,
          child: _buildNodeCardPreview(type),
        ),
        child: _buildNodeCardPreview(type),
      ),
    );
  }

  /// Tampilan fisik kartu node di Palette.
  Widget _buildNodeCardPreview(NodeType type) {
    return Container(
      width: 196,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: type.color,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: Colors.black26),
      ),
      child: Row(
        children: [
          Icon(type.icon, size: 18, color: Colors.black87),
          const SizedBox(width: 8),
          Text(
            type.displayLabel,
            style: const TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w600,
              color: Colors.black87,
              decoration: TextDecoration.none,
            ),
          ),
        ],
      ),
    );
  }

  /// Widget Area Tengah: Canvas tempat node digambar dan dihubungkan.
  Widget _buildWorkspace() {
    return DragTarget<NodeType>(
      onAcceptWithDetails: (details) {
        // Dapatkan posisi drop relatif terhadap koordinat Stack Canvas
        final Offset localPos = _getLocalOffset(details.offset);
        // Sesuaikan koordinat drop agar posisi mouse berada di ujung atas-kiri container node
        _addNewNode(details.data, localPos);
      },
      builder: (context, candidateData, rejectedData) {
        return GestureDetector(
          onTap: () {
            setState(() {
              _selectedNode = null;
            });
          },
          child: Container(
            color: const Color(0xFFF9F9F9),
            child: ClipRect(
              child: Stack(
                key: _canvasKey,
                children: [
                  // Latar Belakang: Grid Dot & Kabel Wire
                  Positioned.fill(
                    child: CustomPaint(
                      painter: WorkspacePainter(
                        nodes: _nodes,
                        connections: _connections,
                        activeWireStart: _activeWireStart,
                        activeWireEnd: _activeWireEnd,
                      ),
                    ),
                  ),

                  // Merender daftar Node yang aktif di Canvas
                  ..._nodes.map((node) => _buildNodeOnCanvas(node)),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  /// Membangun satu objek Node yang ter-posisi di Canvas.
  Widget _buildNodeOnCanvas(NodeModel node) {
    final bool isSelected = _selectedNode?.id == node.id;

    return Positioned(
      left: node.position.dx,
      top: node.position.dy,
      width: WorkspacePainter.nodeWidth,
      height: WorkspacePainter.nodeHeight,
      child: GestureDetector(
        onTap: () {
          setState(() {
            _selectedNode = node;
          });
        },
        onPanUpdate: (details) {
          setState(() {
            // Update posisi koordinat node saat digeser di canvas
            node.position += details.delta;
          });
        },
        child: Container(
          decoration: BoxDecoration(
            color: node.type.color,
            borderRadius: BorderRadius.circular(4),
            border: Border.all(
              color: isSelected ? Colors.red.shade400 : Colors.black26,
              width: isSelected ? 2.5 : 1.0,
            ),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.08),
                blurRadius: 4,
                offset: const Offset(0, 2),
              ),
            ],
          ),
          child: Stack(
            clipBehavior: Clip.none,
            children: [
              // Konten Utama Node: Ikon & Label
              Center(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 12.0),
                  child: Row(
                    children: [
                      Icon(node.type.icon, size: 16, color: Colors.black54),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          node.label,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.bold,
                            color: Colors.black87,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),

              // Port Input (Lingkaran di Sisi Kiri Node)
              if (node.inputCount > 0)
                Positioned(
                  left: -6,
                  top: (WorkspacePainter.nodeHeight / 2) - 6,
                  child: Tooltip(
                    message: 'Input Port',
                    child: Container(
                      width: 12,
                      height: 12,
                      decoration: BoxDecoration(
                        color: Colors.grey.shade400,
                        shape: BoxShape.circle,
                        border: Border.all(color: Colors.black45, width: 1.5),
                      ),
                    ),
                  ),
                ),

              // Port Output (Satu atau beberapa lingkaran di Sisi Kanan Node)
              if (node.outputCount > 0)
                ...List.generate(node.outputCount, (index) {
                  // Hitung posisi vertikal setiap output port secara seimbang
                  final double portY = 
                      (WorkspacePainter.nodeHeight * (index + 1)) / (node.outputCount + 1) - 6;

                  return Positioned(
                    right: -6,
                    top: portY,
                    child: GestureDetector(
                      onPanStart: (details) {
                        setState(() {
                          // Dapatkan koordinat awal tarikan kabel
                          _activeWireStart = _getOutputPortPosition(node, index);
                          _activeWireEnd = _activeWireStart;
                          _activeWireFromNodeId = node.id;
                          _activeWireFromPortIndex = index;
                        });
                      },
                      onPanUpdate: (details) {
                        setState(() {
                          // Update posisi ujung kabel mengikuti cursor mouse
                          _activeWireEnd = _getLocalOffset(details.globalPosition);
                        });
                      },
                      onPanEnd: (details) {
                        _handleWireConnectionDrop();
                      },
                      child: Tooltip(
                        message: 'Output Port ${index + 1}',
                        child: Container(
                          width: 12,
                          height: 12,
                          decoration: BoxDecoration(
                            color: Colors.grey.shade600,
                            shape: BoxShape.circle,
                            border: Border.all(color: Colors.white, width: 1.5),
                          ),
                        ),
                      ),
                    ),
                  );
                }),
            ],
          ),
        ),
      ),
    );
  }

  /// Memproses logika dropping kabel untuk menyambungkan port.
  /// Menggunakan algoritma deteksi tabrakan (Collision Detection) jarak radius 24px.
  void _handleWireConnectionDrop() {
    if (_activeWireEnd == null || _activeWireFromNodeId == null || _activeWireFromPortIndex == null) {
      _resetActiveWireState();
      return;
    }

    NodeModel? targetNode;
    int targetPortIndex = 0;

    // Iterasi untuk mencari apakah ada port input node yang tertabrak ujung drag kabel
    for (final NodeModel node in _nodes) {
      if (node.inputCount > 0 && node.id != _activeWireFromNodeId) {
        final Offset portPos = _getInputPortPosition(node);
        // Hitung jarak Euclidean
        final double distance = (portPos - _activeWireEnd!).distance;
        
        if (distance < 24.0) { // Toleransi radius tabrakan
          targetNode = node;
          targetPortIndex = 0; // Sementara port input diindeks 0
          break;
        }
      }
    }

    if (targetNode != null) {
      final Connection newConn = Connection(
        id: 'conn_${DateTime.now().millisecondsSinceEpoch}',
        fromNodeId: _activeWireFromNodeId!,
        fromPortIndex: _activeWireFromPortIndex!,
        toNodeId: targetNode.id,
        toPortIndex: targetPortIndex,
      );

      setState(() {
        // Cek apakah koneksi ini sudah ada untuk mencegah duplikasi
        if (!_connections.contains(newConn)) {
          _connections.add(newConn);
        }
      });
    }

    _resetActiveWireState();
  }

  /// Mereset status rendering kabel aktif ke kondisi awal.
  void _resetActiveWireState() {
    setState(() {
      _activeWireStart = null;
      _activeWireEnd = null;
      _activeWireFromNodeId = null;
      _activeWireFromPortIndex = null;
    });
  }

  /// Widget Bilah Kanan: Menampilkan data node terpilih, kontrol hapus, dan file JSON output.
  Widget _buildInfoSidebar(String jsonText) {
    return Container(
      width: 320,
      decoration: BoxDecoration(
        color: Colors.white,
        border: Border(left: BorderSide(color: Colors.grey.shade300)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Judul Sidebar Kanan
          Container(
            padding: const EdgeInsets.all(16.0),
            color: Colors.blueGrey.shade800,
            width: double.infinity,
            child: const Text(
              'Nexa Workspace Properties',
              style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 14),
            ),
          ),

          // Detail Node Terpilih
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: _selectedNode == null
                ? const Text(
                    'Pilih node pada canvas untuk memunculkan detail & kontrol penghapusan.',
                    style: TextStyle(color: Colors.grey, fontSize: 13, fontStyle: FontStyle.italic),
                  )
                : Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'SELECTED NODE',
                        style: TextStyle(color: Colors.blueGrey.shade700, fontSize: 11, fontWeight: FontWeight.bold),
                      ),
                      const SizedBox(height: 8),
                      Row(
                        children: [
                          Icon(_selectedNode!.type.icon, color: Colors.blueGrey),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              _selectedNode!.label,
                              style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 4),
                      Text('ID: ${_selectedNode!.id}', style: const TextStyle(color: Colors.grey, fontSize: 12)),
                      Text('Tipe: ${_selectedNode!.type.name}', style: const TextStyle(color: Colors.grey, fontSize: 12)),
                      Text('Koordinat: (${_selectedNode!.position.dx.toStringAsFixed(1)}, ${_selectedNode!.position.dy.toStringAsFixed(1)})', style: const TextStyle(color: Colors.grey, fontSize: 12)),
                      const SizedBox(height: 12),
                      ElevatedButton.icon(
                        onPressed: _deleteSelectedNode,
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.red.shade50,
                          foregroundColor: Colors.red.shade700,
                          elevation: 0,
                          side: BorderSide(color: Colors.red.shade100),
                        ),
                        icon: const Icon(Icons.delete_outline, size: 18),
                        label: const Text('Delete Node'),
                      ),
                    ],
                  ),
          ),
          const Divider(),

          // Output JSON untuk Nexa Framework
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
            child: Text(
              'GENERATED NEXA JSON',
              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 11, color: Colors.blueGrey),
            ),
          ),
          Expanded(
            child: Container(
              margin: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 4.0),
              padding: const EdgeInsets.all(8.0),
              width: double.infinity,
              decoration: BoxDecoration(
                color: Colors.grey.shade50,
                borderRadius: BorderRadius.circular(4),
                border: Border.all(color: Colors.grey.shade200),
              ),
              child: SingleChildScrollView(
                child: Text(
                  jsonText,
                  style: const TextStyle(
                    fontFamily: 'monospace',
                    fontSize: 11,
                    color: Colors.black87,
                  ),
                ),
              ),
            ),
          ),
          
          // Tombol Salin JSON
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: SizedBox(
              width: double.infinity,
              child: ElevatedButton.icon(
                onPressed: () {
                  Clipboard.setData(ClipboardData(text: jsonText));
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('JSON Workspace berhasil disalin!')),
                  );
                },
                icon: const Icon(Icons.copy),
                label: const Text('Copy JSON Code'),
              ),
            ),
          ),
        ],
      ),
    );
  }

  /// Menghapus node yang sedang aktif dipilih beserta semua kabel koneksinya.
  void _deleteSelectedNode() {
    if (_selectedNode == null) return;

    final String nodeId = _selectedNode!.id;

    setState(() {
      // 1. Hapus semua kabel koneksi yang mengarah dari/ke node tersebut
      _connections.removeWhere(
        (conn) => conn.fromNodeId == nodeId || conn.toNodeId == nodeId,
      );

      // 2. Hapus objek node dari list nodes
      _nodes.removeWhere((node) => node.id == nodeId);

      // 3. Reset pilihan seleksi
      _selectedNode = null;
    });
  }
}
