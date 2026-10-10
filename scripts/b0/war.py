"""Inspect actual WAR bytes, resources, XML registrations and bytecode target."""
import sys
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

war = Path(sys.argv[1])
with zipfile.ZipFile(war) as archive:
    names = set(archive.namelist())
    required = {'WEB-INF/classes/common/ApiResponse.class', 'WEB-INF/classes/passwordgenerator/controller/SessionStatusController.class', 'WEB-INF/classes/units.sql', 'WEB-INF/classes/passwordgenerator/properties/config.properties', 'WEB-INF/web.xml'}
    assert required <= names, required - names
    assert len([n for n in names if n.endswith('.class')]) >= 233
    assert not any('servlet-api' in n or 'tomcat-embed' in n for n in names)
    assert [n for n in names if '/sqlite-jdbc-' in n] == ['WEB-INF/lib/sqlite-jdbc-3.45.1.0.jar']
    assert [n for n in names if '/gson-' in n] == ['WEB-INF/lib/gson-2.10.1.jar']
    config = archive.read('WEB-INF/classes/passwordgenerator/properties/config.properties').decode()
    assert 'sqlite3_driver' in config and 'sqlite3_url' in config  # never print values
    data = archive.read('WEB-INF/classes/common/ApiResponse.class')
    assert int.from_bytes(data[6:8], 'big') == 61, 'Expected Java 17 bytecode'
    xml = ET.fromstring(archive.read('WEB-INF/web.xml'))
    ns = {'j':'https://jakarta.ee/xml/ns/jakartaee'}
    filters = [x.text for x in xml.findall('j:filter/j:filter-class',ns)]
    for name in ['AuthFilter','CsrfFilter','GuestRestrictionFilter']:
        assert 'common.filter.'+name in filters
    for name in filters:
        assert 'WEB-INF/classes/'+name.replace('.','/')+'.class' in names
print('PASS WAR: compiled classes, Java 17 bytecode, resources, filters, provided Servlet API')
