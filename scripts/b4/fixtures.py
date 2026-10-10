"""Synthetic B4 contracts only; no source data or credentials enter HTTP specifications."""
import copy,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def sample(target='sqlite',source='csv',variant='strict'):
    s=json.loads((ROOT/'contracts/backend-support/v0.4.0/etl-sample.json').read_text())
    s['target']=s['schema']['target']=target;s['source']['format']=source
    if variant in ('continue','upsert','cap'):s['errorMode']='continue'
    if variant=='upsert':s.update(mode='upsert',resumePolicy='upsert_replay',conflictKey=['id'],updateColumns=['name','amount','active','day','instant','metadata','parent_id'])
    if variant=='cap':s['rejects']['maxBytes']=256
    if variant=='utc':s['naiveTimestamp']='utc';s['mappings'][4]['transforms']=['date_dmy']
    if variant=='ignore':s['source']['unexpectedFields']='ignore'
    if variant=='types':
        for c in s['schema']['tables'][0]['columns']:
            if c['id'] in ('id','parent_id'):c['type']='bigint'
            if c['id']=='amount':c.update(precision=38,scale=18)
    if variant=='case':s['mappings'][1]['transforms']=['trim','lower','upper','cast']
    if variant=='composite':
        s.update(mode='upsert',resumePolicy='upsert_replay',conflictKey=['id','name'],updateColumns=['amount'])
        s['schema']['tables'][0].update(primaryKey=['id','name'],foreignKeys=[]);s['mappings'][1]['empty']='reject'
    return s
def row(i=1,**changes):
    value=dict(id=i,name='item '+str(i),amount='12.30',active=True,day='2026-09-25',instant='2026-09-25T12:00:00+05:30',metadata={'safe':'世界'},parent_id=None)
    value.update(changes);return value
