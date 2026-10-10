"""Verify this generated bundle without connecting to a destination."""
import json
import sys
from etl import verify_bundle

if __name__ == '__main__':
    try:
        verify_bundle()
    except Exception:
        print(json.dumps({'status': 'FAIL', 'reason': 'BUNDLE_INTEGRITY'}))
        sys.exit(1)
    print(json.dumps({'status': 'PASS', 'scope': 'artifact integrity only'}))
