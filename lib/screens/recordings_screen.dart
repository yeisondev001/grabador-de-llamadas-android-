import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/material.dart';

import '../services/recorder_channel.dart';

class RecordingsScreen extends StatefulWidget {
  const RecordingsScreen({super.key});

  @override
  State<RecordingsScreen> createState() => _RecordingsScreenState();
}

class _RecordingsScreenState extends State<RecordingsScreen> {
  final _player = AudioPlayer();
  List<RecordingInfo> _items = [];
  String? _playingPath;
  bool _isPlaying = false;

  @override
  void initState() {
    super.initState();
    _load();
    _player.onPlayerStateChanged.listen((state) {
      if (!mounted) return;
      setState(() => _isPlaying = state == PlayerState.playing);
      if (state == PlayerState.stopped || state == PlayerState.completed) {
        setState(() => _playingPath = null);
      }
    });
  }

  @override
  void dispose() {
    _player.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final items = await RecorderChannel.getRecordings();
    if (mounted) setState(() => _items = items);
  }

  Future<void> _togglePlay(RecordingInfo rec) async {
    if (_playingPath == rec.path && _isPlaying) {
      await _player.pause();
      return;
    }
    if (_playingPath == rec.path && !_isPlaying) {
      await _player.resume();
      return;
    }
    await _player.play(DeviceFileSource(rec.path));
    setState(() => _playingPath = rec.path);
  }

  Future<void> _delete(RecordingInfo rec) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Eliminar grabación'),
        content: Text('¿Eliminar "${rec.name}"?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancelar'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Eliminar'),
          ),
        ],
      ),
    );
    if (ok == true) {
      if (_playingPath == rec.path) {
        await _player.stop();
      }
      await RecorderChannel.deleteRecording(rec.path);
      _load();
    }
  }

  String _dateLabel(DateTime d) {
    return '${d.day.toString().padLeft(2, '0')}/${d.month.toString().padLeft(2, '0')}/${d.year} '
        '${d.hour.toString().padLeft(2, '0')}:${d.minute.toString().padLeft(2, '0')}';
  }

  IconData _icon(String type) {
    switch (type) {
      case 'WhatsApp':
        return Icons.chat;
      case 'Llamada':
        return Icons.phone;
      case 'Diagnóstico':
        return Icons.science;
      default:
        return Icons.mic;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Grabaciones')),
      body: _items.isEmpty
          ? const Center(
              child: Text('Aún no hay grabaciones'),
            )
          : ListView.builder(
              itemCount: _items.length,
              itemBuilder: (context, i) {
                final rec = _items[i];
                final active = _playingPath == rec.path;
                return ListTile(
                  leading: Icon(_icon(rec.type),
                      color: active && _isPlaying ? Colors.red : null),
                  title: Text(rec.name,
                      style: const TextStyle(fontSize: 14)),
                  subtitle: Text(
                      '${rec.type} · ${_dateLabel(rec.modified)} · ${rec.sizeLabel}'),
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      IconButton(
                        icon: Icon(active && _isPlaying
                            ? Icons.pause_circle
                            : Icons.play_circle),
                        iconSize: 32,
                        onPressed: () => _togglePlay(rec),
                      ),
                      IconButton(
                        icon: const Icon(Icons.delete_outline),
                        onPressed: () => _delete(rec),
                      ),
                    ],
                  ),
                );
              },
            ),
    );
  }
}
