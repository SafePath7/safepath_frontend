import csv, json, time, urllib.parse, urllib.request, urllib.error
from pathlib import Path
root = Path(__file__).resolve().parent
source = root.parent / '공공데이터 모음' / '전국 경찰서(파출소) 위치.csv'
settings = (root / 'local.properties').read_text(encoding='utf-8')
key = next(line.split('=', 1)[1].strip().strip('"') for line in settings.splitlines() if line.startswith('KAKAO_REST_API_KEY='))
with source.open(encoding='cp949', newline='') as f:
    rows = list(csv.DictReader(f))
success, failed = [], []
for row in rows:
    address = row['위치'].strip() + ' ' + row['경찰서주소'].strip()
    request = urllib.request.Request('https://dapi.kakao.com/v2/local/search/address.json?' + urllib.parse.urlencode({'query': address, 'size': 1}), headers={'Authorization': 'KakaoAK ' + key})
    try:
        with urllib.request.urlopen(request, timeout=15) as response: docs = json.load(response)['documents']
        if not docs:
            query = row['경찰서명칭'].strip()
            req = urllib.request.Request('https://dapi.kakao.com/v2/local/search/keyword.json?' + urllib.parse.urlencode({'query': query, 'size': 15}), headers={'Authorization': 'KakaoAK ' + key})
            with urllib.request.urlopen(req, timeout=15) as response: candidates = json.load(response)['documents']
            normalized = lambda s: ''.join(s.split())
            docs = [d for d in candidates if normalized(d.get('place_name', '')) == normalized(query)]
        if docs: success.append((docs[0]['y'], docs[0]['x'], row['경찰서명칭'], address))
        else: failed.append((row['경찰서명칭'], address, '검색 결과 없음'))
    except urllib.error.HTTPError as e:
        if e.code in (401, 403, 429): raise SystemExit('API 인증 또는 한도 오류: HTTP ' + str(e.code))
        failed.append((row['경찰서명칭'], address, 'HTTP ' + str(e.code)))
    except Exception as e: failed.append((row['경찰서명칭'], address, type(e).__name__))
    time.sleep(0.04)
with (root / 'app/src/main/assets/police.csv').open('w', encoding='utf-8', newline='') as f:
    writer = csv.writer(f); writer.writerow(['latitude', 'longitude', 'name', 'address']); writer.writerows(success)
with (root / 'police-geocode-failures.csv').open('w', encoding='utf-8-sig', newline='') as f:
    writer = csv.writer(f); writer.writerow(['name', 'address', 'reason']); writer.writerows(failed)
print(f'total={len(rows)} success={len(success)} failed={len(failed)}')
