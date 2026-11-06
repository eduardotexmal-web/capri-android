#!/usr/bin/env bash
# How fast the free online AI (Pollinations) answers a short and a long message, first word and
# whole answer, with each of its quick models: for her chat's choice of model (host.js rankModels).
set -u
curl -s --max-time 20 https://text.pollinations.ai/models | python3 -c "import json,sys; d=json.load(sys.stdin); print('models:', [ (m.get('name'), m.get('reasoning', ''), m.get('vision', m.get('input_modalities', ''))) for m in d ])" 2>&1 | head -c 3000; echo
long=$(python3 -c "print('Hola Capri, te cuento mi día. ' + 'Fui a la escuela, luego comí tacos con mis amigos, jugamos videojuegos y hablamos de muchas cosas. ' * 12)")
for model in openai-fast openai-fast:low openai-fast openai-fast:low openai-fast openai-fast:low; do
  for msg in "hola capri que haces $RANDOM" "$long $RANDOM"; do
    body=$(python3 -c "
import json,sys
m, _, effort = sys.argv[1].partition(':')
b = {'model': m, 'stream': True, 'messages': [{'role': 'system', 'content': 'You are Capri. ' * 200}, {'role': 'user', 'content': sys.argv[2]}]}
if effort: b['reasoning_effort'] = effort
print(json.dumps(b))" "$model" "$msg")
    start=$(date +%s.%N)
    curl -sN --max-time 90 -H 'content-type: application/json' -d "$body" https://text.pollinations.ai/openai/chat/completions \
      | python3 -c "
import sys,time,json
t0=float(sys.argv[1]); first=None; words=None; think=None; n=0; head=''
for line in sys.stdin:
    if first is None: first=time.time()-t0
    n+=1
    if len(head)<160: head+=line.strip()[:160]
    try: d=json.loads(line[5:]).get('choices',[{}])[0].get('delta',{})
    except Exception: continue
    if think is None and (d.get('reasoning_content') or d.get('reasoning')): think=time.time()-t0
    if words is None and d.get('content'): words=time.time()-t0
r=lambda x: None if x is None else round(x,1)
print('$model', len(sys.argv[2]), 'chars: first byte', r(first), 's, thinking', r(think), 's, first word', r(words), 's, total', round(time.time()-t0,1), 's, lines', n, '|', head[:200])
" "$start" "$msg"
  done
done
# can it see? a red square, as her look sends a screen picture (image_url, base64)
img=$(python3 -c "
import zlib,struct,base64
w=h=64
raw=b''.join(b'\x00'+b'\xdd\x11\x11'*w for _ in range(h))
def chunk(t,d): return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b'')
print(base64.b64encode(png).decode())")
for model in openai-fast openai; do
  body=$(python3 -c "
import json,sys
print(json.dumps({'model': sys.argv[1], 'reasoning_effort': 'low', 'messages': [{'role': 'user', 'content': [{'type': 'text', 'text': 'What color is this picture? Answer with one word.'}, {'type': 'image_url', 'image_url': {'url': 'data:image/png;base64,' + sys.argv[2]}}]}]}))" "$model" "$img")
  echo "vision $model: $(curl -s --max-time 90 -H 'content-type: application/json' -d "$body" https://text.pollinations.ai/openai/chat/completions | head -c 600)"
done
