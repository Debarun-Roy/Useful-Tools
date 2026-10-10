"""Independent draft-2020-12 contract assertions using B0's pinned jsonschema dependency."""
import copy
import json
from pathlib import Path
import sys
from jsonschema import Draft202012Validator
from referencing import Registry, Resource

root=Path(__file__).resolve().parents[2];base=root/'contracts/backend-support/v0.2.0';run=Path(sys.argv[1]);count=0
schemas={p.name:json.loads(p.read_text(encoding='utf-8')) for p in base.glob('*.schema.json')}
for schema in schemas.values():Draft202012Validator.check_schema(schema)
registry=Registry().with_resource('models.schema.json',Resource.from_contents(schemas['models.schema.json']))
validator=Draft202012Validator(schemas['models.schema.json'])
for case in json.loads((base/'fixtures.json').read_text(encoding='utf-8')):
    assert validator.is_valid(case['value'])==case['valid'],case['name'];count+=1
sample=json.loads((base/'customers-orders.json').read_text(encoding='utf-8'))
for op in ['generate','export']:
    v=Draft202012Validator(schemas[op+'-transport.schema.json'],registry=registry)
    body={'request':sample}
    if op=='export':body['expectedDigest']='a'*64
    v.validate(body);count+=1
    body['files']=[];assert not v.is_valid(body);count+=1
response=Draft202012Validator(schemas['generation-response.schema.json'])
manifest=Draft202012Validator(schemas['manifest.schema.json'])
for file in run.glob('*-preview.json'):
    payload=json.loads(file.read_text(encoding='utf-8'))
    if file.name=='browser-preview.json':payload={'success':True,'data':payload}
    response.validate(payload);manifest.validate(payload['data']['manifest']);count+=2
assert count>=19, 'Expected all actual HTTP preview contracts'
print(json.dumps({'status':'PASS','contractAssertions':count,'schemaFiles':len(schemas)}))
