"""Standard-library validation for Solarius resource pack + Blockbench source models."""
from pathlib import Path
import json, zipfile, re, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
pack=root/'resource-pack'
models=pack/'assets/solarius/models/item'
items=pack/'assets/solarius/items'
source=root/'models/blockbench'
mcmeta=json.loads((pack/'pack.mcmeta').read_text())
assert mcmeta['pack']['min_format']==[75,0]
assert mcmeta['pack']['max_format']==[75,0]
assert 'pack.png' in [x.name for x in pack.iterdir()]
for name,n in [('skeletal_hand_left',122),('skeletal_hand_right',122),('solar_sword',300),('sun_orb',13)]:
    item=json.loads((items/(name+'.json')).read_text())
    model=json.loads((models/(name+'.json')).read_text())
    bb=json.loads((source/(name+'.bbmodel')).read_text())
    assert item['model']['model']=='solarius:item/'+name
    assert len(bb['elements'])==len(model['elements'])==n
    for cube in model['elements']:
        a,b=cube['from'],cube['to']
        assert all(-16<=v<=32 for v in a+b)
        assert all(x<y for x,y in zip(a,b))
        assert set(cube['faces'])=={'north','east','south','west','up','down'}
        for face in cube['faces'].values():
            key=face['texture'].lstrip('#')
            asset=model['textures'][key].split(':')[1] + '.png'
            assert (pack/'assets/solarius/textures'/asset).exists(),asset
    assert len(bb['outliner'])==len(bb['groups'])
    # All Java element rotations are vanilla-supported and Blockbench rotates around the same origin.
    for cube, bbcube in zip(model['elements'], bb['elements']):
        if 'rotation' in cube:
            assert cube['rotation']['angle'] in (-45,-22.5,22.5,45)
            assert isinstance(bbcube['rotation'], list) and len(bbcube['rotation']) == 3
            assert cube['rotation']['origin']==bbcube['origin']
    assert 'body' not in str(bb['groups']).lower()
zip_path=root/'Solarius-ResourcePack-1.21.11.zip'
with zipfile.ZipFile(zip_path) as z:
    assert z.testzip() is None
    paths={p.relative_to(pack).as_posix() for p in pack.rglob('*') if p.is_file()}
    assert paths==set(z.namelist())
print('PASS: resource pack 1.21.11, all four custom models and Blockbench sources; zip integrity')

# Basic plugin source and metadata integrity checks (not a Java compile or server test).
plugin=(root/'src/main/resources/plugin.yml').read_text()
source_java=(root/'src/main/java/com/plattsmp/solarius/SolariusPlugin.java').read_text()
assert "name: Solarius" in plugin and "version: '4.0.0'" in plugin
assert re.search(r'^  cooldown:',plugin,re.M) and 'cooldownCommand' in source_java
for cmd in ['give','energy','setenergy','reload','reset','beam','duelist','sunfall']:
    assert cmd in source_java
pom = ET.parse(root/'pom.xml').getroot()
namespace={'m':'http://maven.apache.org/POM/4.0.0'}
assert pom.find('m:version',namespace).text=='4.0.0'
assert '1.21.11-R0.1-SNAPSHOT' in (root/'pom.xml').read_text()
print('PASS: Solarius 4.0 command registration and Maven version metadata')
