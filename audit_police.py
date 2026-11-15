import csv, json, math, time, urllib.request, urllib.parse
from pathlib import Path
root = Path(__file__).resolve().parent
key = next(x.split('=',1)[1].strip().strip('"') for x in (root/'local.properties').read_text(encoding='utf-8').splitlines() if x.startswith('KAKAO_REST_API_KEY='))
with (root/'app/src/main/assets/police.csv').open(encoding='utf-8', newline='') as f: rows = list(csv.DictReader(f))
out=[]
for row in rows:
    name=row['name']; url='https://dapi.kakao.com/v2/local/search/keyword.json?'+urllib.parse.urlencode({'query':name,'size':15})
    try:
        req=urllib.request.Request(url,headers={'Authorization':'KakaoAK '+key})
        with urllib.request.urlopen(req,timeout=15) as r: docs=json.load(r)['documents']
        norm=lambda s: ''.join(s.split())
        matches=[d for d in docs if norm(d['place_name']) == norm(name)]
        if not matches: out.append((name,'검색 결과 없음','','',row['address'])); continue
        doc=matches[0]; lat=float(row['latitude']); lng=float(row['longitude']); lat2=float(doc['y']); lng2=float(doc['x'])
        dy=(lat-lat2)*111320; dx=(lng-lng2)*111320*math.cos(math.radians(lat2)); distance=math.hypot(dx,dy)
        out.append((name,'일치' if distance <= 100 else '거리 확인',round(distance),doc.get('road_address_name') or doc.get('address_name'),row['address']))
    except Exception as e: out.append((name,'조회 오류','','',type(e).__name__))
    time.sleep(.04)
with (root/'police-location-audit.csv').open('w',encoding='utf-8-sig',newline='') as f:
    w=csv.writer(f); w.writerow(['name','result','distance_m','map_address','source_address']); w.writerows(out)
from collections import Counter
print(dict(Counter(x[1] for x in out)))
