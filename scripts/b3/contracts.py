"""Independent JSON Schema validation of versioned samples, negatives and actual previews."""
import copy,json,sys
from pathlib import Path
from jsonschema import Draft202012Validator
from referencing import Registry,Resource
root=Path(__file__).resolve().parents[2];base=root/'contracts/backend-support/v0.3.0';run=Path(sys.argv[1]);count=0
schemas={p.name:json.loads(p.read_text(encoding='utf-8')) for p in base.glob('*.schema.json')}
for s in schemas.values():Draft202012Validator.check_schema(s)
registry=Registry().with_resource('models.schema.json',Resource.from_contents(schemas['models.schema.json']))
models=Draft202012Validator(schemas['models.schema.json'])
for module in ['migration','view','evaluator']:
    s=json.loads((base/(module+'-sample.json')).read_text(encoding='utf-8'));models.validate(s);count+=1
    for key in ['schemaVersion','templateVersion','module']:
        bad=copy.deepcopy(s);bad[key]='unknown';assert not models.is_valid(bad);count+=1
    bad=copy.deepcopy(s);bad['rawSql']='SELECT secret';assert not models.is_valid(bad);count+=1
    for op in ['generate','export']:
        v=Draft202012Validator(schemas[op+'-transport.schema.json'],registry=registry);body=dict(request=s)
        if op=='export':body['expectedDigest']='a'*64
        v.validate(body);count+=1;body['files']=[];assert not v.is_valid(body);count+=1
response=Draft202012Validator(schemas['generation-response.schema.json']);manifest=Draft202012Validator(schemas['manifest.schema.json']);previews=0
for file in run.glob('*-preview.json'):
    if file.name.endswith('-schema-preview.json'):continue
    p=json.loads(file.read_text(encoding='utf-8'));response.validate(dict(success=True,data=p));manifest.validate(p['manifest']);count+=2;previews+=1
assert previews>=22, 'All actual B3 HTTP previews required'
result=dict(status='PASS',assertions=count,schemas=len(schemas),previews=previews)
(run/'contract-results.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps(result))
