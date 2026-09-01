import 'package:flutter/services.dart';

class RecordingInfo {
  final String name;
  final String path;
  final int sizeBytes;
  final DateTime modified;

  RecordingInfo({
    required this.name,
    required this.path,
    required this.sizeBytes,
    required this.modified,
  });

  String get type {
    if (name.startsWith('DIAG')) return 'Diagnóstico';
    if (name.startsWith('WHATSAPP')) return 'WhatsApp';
    if (name.startsWith('LLAMADA')) return 'Llamada';
    return 'Prueba';
  }

  String get sizeLabel {
    if (sizeBytes < 1024) return '$sizeBytes B';
    if (sizeBytes < 1024 * 1024) return '${(sizeBytes / 1024).toStringAsFixed(0)} KB';
    return '${(sizeBytes / 1024 / 1024).toStringAsFixed(1)} MB';
  }

  factory RecordingInfo.fromMap(dynamic m) {
    return RecordingInfo(
      name: m['name'] ?? '',
      path: m['path'] ?? '',
      sizeBytes: (m['size'] as num?)?.toInt() ?? 0,
      modified: DateTime.fromMillisecondsSinceEpoch((m['modified'] as num?)?.toInt() ?? 0),
    );
  }
}

class RecorderStatus {
  final bool serviceRunning;
  final bool recording;
  final String type;
  final String source;
  final int durationMs;
  final int amp;
  final int peak;
  final int lastPeak;
  final bool speaker;
  final bool mic;
  final bool phone;
  final bool notifPerm;
  final bool notificationAccess;
  final bool batteryIgnored;
  final bool monitorEnabledPref;

  RecorderStatus({
    required this.serviceRunning,
    required this.recording,
    required this.type,
    required this.source,
    required this.durationMs,
    required this.amp,
    required this.peak,
    required this.lastPeak,
    required this.speaker,
    required this.mic,
    required this.phone,
    required this.notifPerm,
    required this.notificationAccess,
    required this.batteryIgnored,
    required this.monitorEnabledPref,
  });

  bool get hasRuntimePerms => mic && phone && notifPerm;
  bool get ready => hasRuntimePerms && notificationAccess;

  String get durationLabel {
    final total = durationMs ~/ 1000;
    final m = (total ~/ 60).toString().padLeft(2, '0');
    final s = (total % 60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  factory RecorderStatus.fromMap(dynamic m) {
    return RecorderStatus(
      serviceRunning: m['serviceRunning'] == true,
      recording: m['recording'] == true,
      type: m['type'] ?? '',
      source: m['source'] ?? '',
      durationMs: (m['durationMs'] as num?)?.toInt() ?? 0,
      amp: (m['amp'] as num?)?.toInt() ?? 0,
      peak: (m['peak'] as num?)?.toInt() ?? 0,
      lastPeak: (m['lastPeak'] as num?)?.toInt() ?? 0,
      speaker: m['speaker'] == true,
      mic: m['mic'] == true,
      phone: m['phone'] == true,
      notifPerm: m['notifPerm'] == true,
      notificationAccess: m['notificationAccess'] == true,
      batteryIgnored: m['batteryIgnored'] == true,
      monitorEnabledPref: m['monitorEnabledPref'] == true,
    );
  }
}

class RecorderChannel {
  static const _ch = MethodChannel('grabador/native');

  static Future<RecorderStatus?> getStatus() async {
    try {
      final r = await _ch.invokeMethod('getStatus');
      return RecorderStatus.fromMap(r);
    } catch (_) {
      return null;
    }
  }

  static Future<void> startMonitor() => _ch.invokeMethod('startMonitor');
  static Future<void> stopMonitor() => _ch.invokeMethod('stopMonitor');
  static Future<void> manualStart() => _ch.invokeMethod('manualStart');
  static Future<void> manualStop() => _ch.invokeMethod('manualStop');

  static Future<Map<String, dynamic>> getSettings() async {
    try {
      final r = await _ch.invokeMethod('getSettings');
      return Map<String, dynamic>.from(r);
    } catch (_) {
      return {};
    }
  }

  static Future<void> setSetting(String key, Object value) =>
      _ch.invokeMethod('setSetting', {'key': key, 'value': value});

  static Future<List<RecordingInfo>> getRecordings() async {
    try {
      final r = await _ch.invokeMethod('getRecordings') as List;
      return r.map(RecordingInfo.fromMap).toList();
    } catch (_) {
      return [];
    }
  }

  static Future<void> deleteRecording(String path) =>
      _ch.invokeMethod('deleteRecording', {'path': path});

  static Future<bool> hasNotificationAccess() async {
    try {
      return await _ch.invokeMethod('hasNotificationAccess') == true;
    } catch (_) {
      return false;
    }
  }

  static Future<void> openNotificationAccess() =>
      _ch.invokeMethod('openNotificationAccess');

  static Future<bool> isIgnoringBattery() async {
    try {
      return await _ch.invokeMethod('isIgnoringBattery') == true;
    } catch (_) {
      return false;
    }
  }

  static Future<void> requestIgnoreBattery() =>
      _ch.invokeMethod('requestIgnoreBattery');

  static Future<void> requestPermissions() =>
      _ch.invokeMethod('requestPermissions');
}
