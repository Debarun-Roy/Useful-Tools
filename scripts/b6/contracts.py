"""Independent schema/OpenAPI validation of the exact HTTP exports."""
import copy,json,sys
from pathlib import Path
from jsonschema import Draft202012Validator
from openapi_spec_validator import validate
ROOT=Path(__file__).resolve().parents[2];run=Path(sys.argv[1]);checks=0
schema=json.loads((ROOT/'contracts/backend-support/v0.6.0/models.schema.json').read_text());Draft202012Validator.check_schema(schema);validator=Draft202012Validator(schema)
result=json.loads((run/'export-results.json').read_text());assert result['status']=='PASS' and len(result['exports'])==8
for item in result['exports']:
    project=ROOT/item['directory'];spec=json.loads((project/'auth-spec.json').read_text());validator.validate(spec);checks+=1
    document=json.loads((project/'openapi.json').read_text());validate(document);checks+=1
    expected={'/api/auth/'+name for name in ['register','login','logout','session','csrf-token']}
    if item['profile']:expected.add('/api/user/profile')
    assert set(document['paths'])==expected
    credentials=document['components']['schemas']['Credentials'];assert ('captchaToken' in credentials['required'])==(item['captcha']!='off');checks+=2
sample=json.loads((ROOT/'contracts/backend-support/v0.6.0/rest-sample.json').read_text())
for key,value in [('schemaVersion','0.1.0'),('target','java'),('project','../bad'),('pythonModule','x;evil'),('secret','not-permitted')]:
    bad=copy.deepcopy(sample);bad[key]=value;assert list(validator.iter_errors(bad));checks+=1
fixtures=json.loads((ROOT/'contracts/backend-support/v0.5.0/parity-fixtures.json').read_text());assert len(fixtures['negativeCases'])>=8 and len(fixtures['positiveSequence'])==9
assert (ROOT/'contracts/backend-support/v0.6.0/rest-sample.json').read_bytes()==(ROOT/'usefultools-frontend/src/pages/BackendSupportPage/pythonRestSample.json').read_bytes()
(run/'contract-results.json').write_text(json.dumps(dict(status='PASS',assertions=checks,projects=8,sharedNegativeFixtures=len(fixtures['negativeCases'])),indent=2)+'\n',encoding='utf-8');print('PASS independent contracts',checks)
