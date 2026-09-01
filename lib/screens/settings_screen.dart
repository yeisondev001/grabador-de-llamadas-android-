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

  Future<void> _set(String key, bool value) async {
    setState(() => _settings[key] = value);
    await RecorderChannel.setSetting(key, value);
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
          SwitchListTile(
            title: const Text('Forzar micrófono'),
            subtitle: const Text(
                'Graba directo por micrófono desde el primer segundo (recomendado). El altavoz se enciende solo'),
            value: _settings['forceMic'] == true,
            onChanged: (v) => _set('forceMic', v),
          ),
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
                  '· Llamada normal: la app intenta grabar por el canal de voz '
                  '(ambas vías nativas). Si el teléfono lo bloquea, cambia a '
                  'micrófono automáticamente y necesitarás el altavoz.\n\n'
                  '· WhatsApp: se graba por micrófono siempre; el altavoz debe '
                  'estar encendido para oír a la otra persona.\n\n'
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
