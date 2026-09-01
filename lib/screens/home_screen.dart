import 'dart:async';

import 'package:flutter/material.dart';

import '../services/recorder_channel.dart';
import 'recordings_screen.dart';
import 'settings_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  RecorderStatus? _status;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _refresh();
    _timer = Timer.periodic(const Duration(seconds: 2), (_) => _refresh());
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _refresh() async {
    final s = await RecorderChannel.getStatus();
    if (mounted) {
      setState(() {
        _status = s;
      });
    }
  }

  Future<void> _toggleMonitor(bool on) async {
    if (on) {
      await RecorderChannel.startMonitor();
    } else {
      await RecorderChannel.stopMonitor();
    }
    _refresh();
  }

  @override
  Widget build(BuildContext context) {
    final st = _status;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Grabador de Llamadas'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _refresh,
          ),
          IconButton(
            icon: const Icon(Icons.settings),
            onPressed: () async {
              await Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const SettingsScreen()),
              );
              _refresh();
            },
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _statusCard(st),
          const SizedBox(height: 12),
          _permCard(st),
          const SizedBox(height: 12),
          _controlCard(st),
          const SizedBox(height: 12),
          _infoCard(),
        ],
      ),
    );
  }

  Widget _statusCard(RecorderStatus? st) {
    final recording = st?.recording == true;
    final ready = st?.ready == true;
    final running = st?.serviceRunning == true;

    final color = recording
        ? Colors.red
        : running && ready
            ? Colors.blue
            : Colors.orange;
    final icon = recording ? Icons.fiber_manual_record : Icons.phone_in_talk;
    final title = recording
        ? 'GRABANDO ${st!.type == 'WHATSAPP' ? 'WHATSAPP' : st.type == 'LLAMADA' ? 'LLAMADA' : st.type == 'MANUAL' ? 'MANUAL' : 'PRUEBA'}'
        : running && ready
            ? 'Vigilando llamadas'
            : 'Incompleto';
    final subtitle = recording
        ? 'Fuente: ${st!.source}  ·  ${st.durationLabel}  ·  Señal: ${st.amp} (pico ${st.peak})\n'
            'Altavoz: ${st.speaker ? "SÍ" : "NO"}  ·  Habla fuerte para probar'
        : running
            ? (ready
                ? (st!.lastPeak > 0
                    ? 'Última grabación: pico de señal ${st.lastPeak}'
                        '${st.lastPeak < 100 ? "  ⚠ SIN AUDIO" : "  ✓ con audio"}'
                    : 'Esperando llamada (GSM y WhatsApp)')
                : 'Faltan permisos para grabar')
            : 'Activa el grabador abajo';

    return Card(
      color: color.withValues(alpha: 0.12),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Icon(icon, color: color, size: 40),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title,
                      style: TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 18,
                          color: color)),
                  const SizedBox(height: 4),
                  Text(subtitle),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _permCard(RecorderStatus? st) {
    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Permisos',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
            const SizedBox(height: 8),
            _permRow('Micrófono y teléfono', st == null ? false : (st.mic && st.phone),
                st != null && !(st.mic && st.phone) ? 'Pedir' : null, () async {
              await RecorderChannel.requestPermissions();
              _refresh();
            }),
            _permRow('Acceso a notificaciones (WhatsApp)', st?.notificationAccess == true,
                st?.notificationAccess == false ? 'Abrir' : null, () async {
              await RecorderChannel.openNotificationAccess();
              _refresh();
            }),
            _permRow('Batería sin restricciones', st?.batteryIgnored == true,
                st?.batteryIgnored == false ? 'Abrir' : null, () async {
              await RecorderChannel.requestIgnoreBattery();
              _refresh();
            }),
          ],
        ),
      ),
    );
  }

  Widget _permRow(String label, bool ok, String? action, VoidCallback onTap) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Icon(ok ? Icons.check_circle : Icons.cancel,
              color: ok ? Colors.green : Colors.orange, size: 20),
          const SizedBox(width: 8),
          Expanded(child: Text(label)),
          if (action != null)
            TextButton(onPressed: onTap, child: Text(action)),
        ],
      ),
    );
  }

  Widget _controlCard(RecorderStatus? st) {
    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('Grabador activo'),
              subtitle: const Text('Se reinicia solo al encender el teléfono'),
              value: st?.serviceRunning == true,
              onChanged: _toggleMonitor,
            ),
            const Divider(),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    icon: Icon(st?.recording == true ? Icons.stop : Icons.fiber_manual_record),
                    label: Text(st?.recording == true ? 'Detener prueba' : 'Grabar prueba'),
                    onPressed: () async {
                      if (st?.recording == true) {
                        await RecorderChannel.manualStop();
                      } else {
                        await RecorderChannel.manualStart();
                      }
                      _refresh();
                    },
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: OutlinedButton.icon(
                    icon: const Icon(Icons.library_music),
                    label: const Text('Grabaciones'),
                    onPressed: () async {
                      await Navigator.push(
                        context,
                        MaterialPageRoute(builder: (_) => const RecordingsScreen()),
                      );
                      _refresh();
                    },
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _infoCard() {
    return Card(
      color: Colors.amber.withValues(alpha: 0.15),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: const Padding(
        padding: EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(children: [
              Icon(Icons.lightbulb, size: 20),
              SizedBox(width: 8),
              Text('Importante',
                  style: TextStyle(fontWeight: FontWeight.bold)),
            ]),
            SizedBox(height: 8),
            Text(
              'Cuando entre una llamada (normal o de WhatsApp) le saldrá un aviso '
              'con los botones "Grabar" y "No". Si toca "Grabar", empieza a grabar. '
              'Para oír a la otra persona se usa el altavoz (en WhatsApp la app lo '
              'enciende sola). En Ajustes puede cambiar a modo automático apagando '
              '"Preguntar antes de grabar".',
            ),
          ],
        ),
      ),
    );
  }
}
