"""Word-multiset comparison of each extracted guide against an independent pdftotext dump."""
import html, json, re, subprocess, collections, sys
P, DATA = sys.argv[1], sys.argv[2]
d = json.load(open(DATA))

def words(s):
    s = html.unescape(re.sub(r'<[^>]+>', '', s))
    for a, b in (('“', '"'), ('”', '"'), ('’', "'"), ('‘', "'"), ('‑', '-')):
        s = s.replace(a, b)
    s = re.sub(r'[•–—]', ' ', s)
    s = re.sub(r'\bS ome\b', 'Some', s)          # pdftotext splits a drop-cap in guide 173's footnote
    return re.findall(r"[A-Za-z0-9]+", s)

bad = 0
for i, g in enumerate(d['guides']):
    p = 154 + 2 * i
    raw = subprocess.run(['pdftotext', '-f', str(p), '-l', str(p + 1), P, '-'], capture_output=True, text=True).stdout
    raw = re.sub(r'Page \d+|ERG 2024|Guides \(orange section\)', '', raw)
    raw = re.sub(r'^\s*GUIDE\s*$|^\s*' + g['guide'] + r'\s*$', '', raw, flags=re.M)
    a = collections.Counter(words(raw))
    b = collections.Counter(words(' '.join(x['x'] for x in g['blocks']) + ' ' + g['title'] + ' ' + g['title']))
    if a != b:
        bad += 1; print(g['guide'], 'pdf-only', dict(a - b), 'json-only', dict(b - a))
print('guides with word differences:', bad, 'of', len(d['guides']))
