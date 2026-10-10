"""B0 contract checks and narrow fixture probes, NOT the B1 validation service."""
import json
from decimal import Decimal, InvalidOperation
from pathlib import Path
from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'contracts/backend-support/v0.1.0'


def probe(request):
    findings = []
    module, spec = request['module'], request['spec']
    if module == 'view':
        fields = {(t['id'], c['id']) for t in spec['schema']['tables'] for c in t['columns']}
        for projection in spec['projection']:
            field = projection['field']
            if (field['tableId'], field['columnId']) not in fields:
                findings.append('UNKNOWN_FIELD')
    if module == 'migration':
        before = {(t['id'], c['id']) for t in spec['before']['tables'] for c in t['columns']}
        after = {(t['id'], c['id']) for t in spec['after']['tables'] for c in t['columns']}
        if before - after:
            findings.append('DESTRUCTIVE_CHANGE')
    return findings


def main():
    schema = json.loads((BASE / 'models.schema.json').read_text())
    Draft202012Validator.check_schema(schema)
    fixtures = json.loads((BASE / 'fixtures.json').read_text())
    assert len(fixtures) >= 15
    names = set()
    for case in fixtures:
        assert case['name'] not in names
        names.add(case['name'])
        validator = Draft202012Validator({**schema, '$ref': '#/$defs/' + case['model']})
        errors = list(validator.iter_errors(case['value']))
        expected = case['expected']
        assert (not errors) == expected['structural'], (case['name'], [e.message for e in errors])
        if errors:
            assert any(e.validator == expected['keyword'] and ('/' + '/'.join(map(str,e.absolute_path)) if e.absolute_path else '').startswith(expected['path']) for e in errors), (case['name'], errors)
        elif case['model'] == 'GenerationRequest':
            assert probe(case['value']) == expected['findings'], case['name']
        for row in case.get('rowCases', []):
            try:
                value = Decimal(row['row']['amount'])
                valid = value.is_finite()
            except (InvalidOperation, TypeError, ValueError):
                valid = False
            assert valid == row['valid'], case['name']
        print('PASS', case['name'], 'structural + declared fixture probes')
    print(f'{len(fixtures)} contract cases passed; no SQL generation or auth correctness claim.')

if __name__ == '__main__':
    main()
