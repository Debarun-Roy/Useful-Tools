"""Synthetic B3 models used by actual HTTP exports and database assertions."""
import copy
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def sample(module,target='sqlite'):
    s=json.loads((ROOT/f'contracts/backend-support/v0.3.0/{module}-sample.json').read_text(encoding='utf-8'))
    s['target']=target
    for key in ['before','after','schema']:
        if key in s and 'target' in s[key]:s[key]['target']=target
    if module=='migration':s['externalDependenciesReviewed']=True
    return s
def col(alias,id):return dict(kind='column',sourceAlias=alias,columnId=id)
def aggregate(function,argument,name):return dict(expression=dict(kind='aggregate',function=function,argument=argument),name=name)
def migrations(target):
    additions=sample('migration',target);t=additions['after']['tables'][0]
    t['columns'] += [dict(id='note',name='note',type='text',nullable=False,default=dict(kind='literal',value="O'Reilly 世界")),dict(id='flag',name='flag',type='boolean',nullable=False,default=dict(kind='literal',value=True))]
    t['indexes'].append(dict(id='email_index',name='email_index',columns=['email'],unique=False))
    rename=sample('migration',target);rename['after']=copy.deepcopy(rename['before'])
    rename['after']['tables'][0]['name']='clients';rename['after']['tables'][0]['columns'][1]['name']='display_name'
    rename['renames']=dict(tables=[dict(tableId='customers',fromName='customers',toName='clients')],columns=[dict(tableId='customers',columnId='name',fromName='name',toName='display_name')])
    noop=sample('migration',target);noop['after']=copy.deepcopy(noop['before'])
    index=copy.deepcopy(noop);index['after']['tables'][0]['indexes']=[dict(id='by_active',name='by_active',columns=['active'],unique=False)]
    return dict(additions=additions,rename=rename,noop=noop,index=index)
def views(target):
    left=sample('view',target);left['projections'] += [aggregate('count',None,'joined_rows'),aggregate('sum',col('o','amount'),'total'),aggregate('min',col('o','amount'),'minimum'),aggregate('max',col('o','amount'),'maximum'),aggregate('avg',col('o','amount'),'average')]
    inner=copy.deepcopy(left);inner['name']='inner_totals';inner['joins'][0]['type']='inner'
    null=sample('view',target);null['name']='unmatched';null['where']=dict(op='isNull',column=col('o','id'))
    literal=sample('view',target);literal['name']='literal_filter';literal['where']=dict(op='eq',left=col('c','name'),right=dict(kind='literal',value="O'Reilly 世界"))
    selfjoin=sample('view',target);selfjoin['name']='self_join';selfjoin['joins']=[dict(type='inner',source=dict(tableId='customers',alias='other'),left=col('c','id'),right=col('other','id'))];selfjoin['projections']=[dict(expression=col('other','name'),name='customer')];selfjoin['groupBy']=[]
    nested=copy.deepcopy(literal);nested['name']='nested_filter';nested['where']=dict(op='and',terms=[literal['where'],dict(op='not',term=dict(op='isNull',column=col('c','name')))])
    return dict(left=left,inner=inner,null=null,literal=literal,self=selfjoin,nested=nested)
