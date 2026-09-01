import 'package:flutter/material.dart';

import '../services/recorder_channel.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  Map<String, dynamic> _settings = {};

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final s = await RecorderChannel.getSettings();
    if (mounted) setState(() => _settings = s);
  }

  Future<void> _set(String key, Object value) async {
    setState(() => _settings[key] = value);
    await RecorderChannel.setSetting(key, value);
  }

  static const _sources = {
    'MIC': 'Micrófono (predeterminado)',
    'VOICE_COMMUNICATION': 'Canal de llamadas de Internet (probar con WhatsApp)',
    'VOICE_CALL': 'Canal interno del teléfono (probar con llamadas normales)',
    'CAMCORDER': 'Cámara (alternativa)',
    'DEFAULT': 'Predeterminado del sistema (alternativa)',
    'VOICE_RECOGNITION': 'Reconocimiento de voz (alternativa)',
    'UNPROCESSED': 'Audio sin procesar (alternativa)',
  };

  Widget _sourceDropdown(String title, String prefKey) {
    final current = (_settings[prefKey] as String?) ?? 'MIC';
    return ListTile(
      title: Text(title),
      subtitle: Text(_sources[current] ?? current),
      trailing: DropdownButton<String>(
        value: current,
        items: _sources.keys
            .map((s) => DropdownMenuItem(value: s, child: Text(s, style: const TextStyle(fontSize: 13))))
            .toList(),
        onChanged: (v) {
          if (v != null) _set(prefKey, v);
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Ajustes')),
      body: ListView(
        padding: const EdgeInsets.all(8),
        children: [
          SwitchListTile(
            title: const Text('Preguntar antes de grabar'),
            subtitle: const Text(
                'Muestra un aviso con los botones "Grabar" / "No" cuando entra una llamada. Si lo apagas, graba automáticamente'),
            value: _settings['askBeforeRecord'] == true,
            onChanged: (v) => _set('askBeforeRecord', v),
          ),
          SwitchListTile(
            title: const Text('Grabar llamadas normales'),
            subtitle: const Text('Llamadas GSM del teléfono'),
            value: _settings['recordGsm'] == true,
            onChanged: (v) => _set('recordGsm', v),
          ),
          SwitchListTile(
            title: const Text('Grabar llamadas de WhatsApp'),
            subtitle: const Text('Detecta la llamada por la notificación'),
            value: _settings['recordWs'] == true,
            onChanged: (v) => _set('recordWs', v),
          ),
          SwitchListTile(
            title: const Text('Altavoz automático'),
            subtitle: const Text(
                'Enciende el altavoz al detectar llamada de WhatsApp'),
            value: _settings['autoSpeaker'] == true,
            onChanged: (v) => _set('autoSpeaker', v),
          ),
          _sourceDropdown('Fuente para llamadas normales', 'audioSourceGsm'),
          _sourceDropdown('Fuente para WhatsApp', 'audioSourceWs'),
          const Divider(),
          const Padding(
            padding: EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Cómo funciona',
                    style: TextStyle(fontWeight: FontWeight.bold)),
                SizedBox(height: 8),
                Text(
                  '· Si las grabaciones salen mudas, prueba otra fuente de audio '
                  'para cada tipo de llamada (arriba). Durante una llamada, mira '
                  'en la pantalla principal el número de "Señal": 0 = silencio, '
                  'más de 100 = hay audio.\n\n'
                  '· Las grabaciones se guardan en '
                  'Android/media/com.papa.grabador_llamadas/Grabaciones.',
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
