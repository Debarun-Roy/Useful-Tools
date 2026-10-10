"""Synthetic contract fixtures; shared HTTP/export/database assertions."""
import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
def sample(target='sqlite'):
    value=json.loads((ROOT/'contracts/backend-support/v0.2.0/customers-orders.json').read_text(encoding='utf-8'))
    value['target']=target
    return value

def complex_schema(target):
    def col(id, type='integer', nullable=False, **options): return dict(id=id,name=id,type=type,nullable=nullable,**options)
    def literal(value): return dict(kind='literal',value=value)
    def table(id, columns):return dict(id=id,name=id,columns=columns,primaryKey=['id'],unique=[],foreignKeys=[],indexes=[],checks=[])
    a=table('parent',[col('id'),col('part','bigint'),col('label','varchar',length=40,default=literal("O'Brien; -- \\ path")),
        col('price','decimal',precision=8,scale=2,default=literal(12.5)),col('flag','boolean',default=literal(True)),
        col('day','date',default=literal('2026-09-20')),col('instant','timestamp',default=literal('2026-09-20T00:00:00Z')),
        col('meta','json',default=literal('{"safe":"value"}')),col('note','text',True)])
    a['name']='select"客户';a['primaryKey']=['part','id'];a['unique']=[['label']]
    a['checks']=[dict(column='price',op='ge',value=0)]
    a['indexes']=[dict(id='by_price',name='index"金额',columns=['price','id'],unique=False)]
    b=table('child',[col('id'),col('ref_part','bigint',True),col('ref_id','integer',True)])
    b['foreignKeys']=[dict(columns=['ref_part','ref_id'],tableId='parent',targetColumns=['part','id'],onDelete='SET NULL',onUpdate='CASCADE')]
    c=table('tree',[col('id'),col('parent_id','integer',True)])
    c['foreignKeys']=[dict(columns=['parent_id'],tableId='tree',targetColumns=['id'],onDelete='RESTRICT',onUpdate='NO ACTION')]
    s=sample(target);s['tables']=[b,c,a];return s

def twenty_tables():
    s=sample();base=s['tables'][0];s['tables']=[]
    for i in range(20):
        t=copy.deepcopy(base);t['id']=t['name']=f'table_{i:02}';s['tables'].append(t)
    return s
