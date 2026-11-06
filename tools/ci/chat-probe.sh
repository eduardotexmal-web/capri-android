#!/usr/bin/env bash
# How fast the free online AI (Pollinations) answers a short and a long message, first word and
# whole answer, with each of its quick models: for her chat's choice of model (host.js rankModels).
set -u
curl -s --max-time 20 https://text.pollinations.ai/models | python3 -c "import json,sys; d=json.load(sys.stdin); print('models:', [ (m.get('name'), m.get('reasoning', ''), m.get('vision', m.get('input_modalities', ''))) for m in d ])" 2>&1 | head -c 3000; echo
long=$(python3 -c "print('Hola Capri, te cuento mi día. ' + 'Fui a la escuela, luego comí tacos con mis amigos, jugamos videojuegos y hablamos de muchas cosas. ' * 12)")
for model in openai openai-fast mistral; do
  for msg in "hola capri que haces" "$long"; do
    body=$(python3 -c "import json,sys; print(json.dumps({'model': sys.argv[1], 'stream': True, 'messages': [{'role': 'system', 'content': 'You are Capri. ' * 200}, {'role': 'user', 'content': sys.argv[2]}]}))" "$model" "$msg")
    start=$(date +%s.%N)
    curl -sN --max-time 90 -H 'content-type: application/json' -d "$body" https://text.pollinations.ai/openai/chat/completions \
      | python3 -c "
import sys,time
t0=float(sys.argv[1]); first=None; n=0; head=''
for line in sys.stdin:
    if first is None: first=time.time()-t0
    n+=1
    if len(head)<160: head+=line.strip()[:160]
print('$model', len(sys.argv[2]), 'chars: first', None if first is None else round(first,1), 's, total', round(time.time()-t0,1), 's, lines', n, '|', head[:160])
" "$start" "$msg"
  done
done
