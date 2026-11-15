"""Use current Kakao place coordinates only, without address centroid fallback."""
import csv, json, time, urllib.parse, urllib.request
from pathlib import Path
root = Path(__file__).resolve().parent
source = root.parent / '공공데이터 모음' / '전국 경찰서(파출소) 위치.csv'
settings = (root / 'local.properties').read_text(encoding='utf-8')
key = next(line.split('=', 1)[1].strip().strip('"') for line in settings.splitlines() if line.startswith('KAKAO_REST_API_KEY='))
with source.open(encoding='cp949', newline='') as f: rows = list(csv.DictReader(f))
success, failed = [], []
normalize = lambda value: ''.join(value.split())
for row in rows:
    name = row['경찰서명칭'].strip()
    address = row['위치'].strip() + ' ' + row['경찰서주소'].strip()
    request = urllib.request.Request('https://dapi.kakao.com/v2/local/search/keyword.json?' + urllib.parse.urlencode({'query': name, 'size': 15}), headers={'Authorization': 'KakaoAK ' + key})
    # Any API/network failure aborts without modifying the existing asset.
    with urllib.request.urlopen(request, timeout=15) as response: docs = json.load(response)['documents']
    province = row['위치'].split()[0]
    prefixes = {'경상남도': '경남', '경상북도': '경북', '전라남도': '전남', '전라북도': '전북', '충청남도': '충남', '충청북도': '충북'}
    prefix = prefixes.get(province, province[:2])
    matches = [d for d in docs if normalize(d['place_name']) == normalize(name)
               and d.get('category_name', '').split(' > ')[-1] == '경찰서']
    if len(matches) > 1:
        matches = [d for d in matches if d.get('address_name', '').startswith((province, prefix))]
    if len(matches) > 1:
        matches = [d for d in matches if d.get('phone')]
    if len(matches) == 1:
        doc = matches[0]
        lat, lng = float(doc['y']), float(doc['x'])
        if not (30 <= lat <= 40 and 120 <= lng <= 135): raise ValueError('Invalid coordinate')
        success.append((doc['y'], doc['x'], name, doc.get('road_address_name') or doc['address_name']))
    else: failed.append((name, address, '검색 결과 없음' if not matches else '동일 이름 중복 결과'))
    time.sleep(.04)
if not success: raise RuntimeError('No verified results; keeping existing asset')
target = root / 'app/src/main/assets/police.csv'
temporary = target.with_suffix('.csv.tmp')
with temporary.open('w', encoding='utf-8', newline='') as f:
    w = csv.writer(f); w.writerow(['latitude', 'longitude', 'name', 'address']); w.writerows(success)
temporary.replace(target)
with (root / 'police-geocode-failures.csv').open('w', encoding='utf-8-sig', newline='') as f:
    w = csv.writer(f); w.writerow(['name', 'address', 'reason']); w.writerows(failed)
print(f'total={len(rows)} included={len(success)} excluded={len(failed)}')
