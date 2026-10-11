"""Isolated regression tests. No servers, real PID inspection or real port probes.

Run from repository root: python -m unittest scripts.local.test_dev_lifecycle -v
"""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from scripts.local import dev


class StaleRunTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.run = Path(temp.name)
        self.record = {'supervisorPid': 101, 'pids': [102, 103],
                       'startedAt': '2026-01-01T00:00:00+00:00'}
        self.save_record()
        (self.run / 'backend-ready.json').write_text('{}')
        (self.run / 'backend.log').write_text('preserved evidence')
        self.process = self.enterContext(patch.object(dev, 'process_state', return_value={'state': 'exited'}))
        self.ports = self.enterContext(patch.object(dev, 'occupied_local_ports', return_value=[]))

    def save_record(self):
        (self.run / 'processes.json').write_text(json.dumps(self.record))

    def test_readiness_marker_does_not_make_dead_run_live(self):
        self.assertEqual(dev.run_state(self.run)['state'], 'stale')
        self.assertFalse((self.run / 'stopped').exists())

    def test_recovery_preserves_evidence_and_is_idempotent(self):
        original = (self.run / 'processes.json').read_bytes()
        self.assertTrue(dev.recover_stale_run(self.run))
        self.assertTrue((self.run / 'stopped').exists())
        self.assertTrue((self.run / 'recovered-stale.json').exists())
        self.assertEqual((self.run / 'processes.json').read_bytes(), original)
        self.assertEqual((self.run / 'backend.log').read_text(), 'preserved evidence')
        self.assertTrue((self.run / 'backend-ready.json').exists())
        self.assertFalse(dev.recover_stale_run(self.run))

    def test_stop_recovers_dead_supervisor_without_waiting(self):
        with patch.object(dev, 'active', return_value=self.run), patch.object(dev.time, 'sleep') as sleep:
            dev.stop()
        sleep.assert_not_called()
        self.assertTrue((self.run / 'stopped').exists())

    def test_live_child_blocks_recovery_even_without_listener(self):
        self.process.side_effect = [{'state': 'exited'}, {'state': 'running'}, {'state': 'exited'}]
        self.assertFalse(dev.recover_stale_run(self.run))
        self.assertFalse((self.run / 'stopped').exists())

    def test_unreadable_process_is_not_assumed_dead(self):
        self.process.return_value = {'state': 'unknown'}
        self.assertFalse(dev.recover_stale_run(self.run))
        self.assertEqual(dev.run_state(self.run)['state'], 'unknown')

    def test_occupied_port_blocks_recovery(self):
        self.ports.return_value = [8080]
        self.assertFalse(dev.recover_stale_run(self.run))

    def test_legacy_reused_pids_are_not_treated_as_owned(self):
        self.process.return_value = {'state': 'running', 'createdAt': 2000000000.0}
        self.assertTrue(dev.recover_stale_run(self.run))
        result = json.loads((self.run / 'recovered-stale.json').read_text())
        self.assertTrue(all(p['state'] == 'pid-reused' for p in result['processes']))

    def test_matching_creation_identity_is_live(self):
        self.record['createdAt'] = {str(pid): 1700000000.0 for pid in (101, 102, 103)}
        self.save_record()
        self.process.return_value = {'state': 'running', 'createdAt': 1700000000.0}
        self.assertFalse(dev.recover_stale_run(self.run))

    def test_missing_or_partial_record_blocks_recovery(self):
        (self.run / 'processes.json').unlink()
        self.assertEqual(dev.run_state(self.run)['state'], 'unknown')
        self.assertFalse(dev.recover_stale_run(self.run))
        self.record['pids'] = []
        self.save_record()
        self.assertFalse(dev.recover_stale_run(self.run))

    def test_stopped_run_does_not_probe_processes(self):
        (self.run / 'stopped').touch()
        self.assertEqual(dev.run_state(self.run)['state'], 'stopped')
        self.process.assert_not_called()


if __name__ == '__main__':
    unittest.main()
