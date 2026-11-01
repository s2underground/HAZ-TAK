"""From each guide's EVACUATION section, list every distance statement verbatim with its
sub-heading, the metres value and whether it applies in all directions or downwind."""
import json, re, sys

DIST = re.compile(r'(\d[\d,]*) meters \(([^)]*)\)')


def plain(x):
    return re.sub(r'<[^>]+>', '', x)


def statements(guide):
    out, in_evac, sub = [], False, None
    for b in guide['blocks']:
        if b['t'] == 'head':
            in_evac = b['x'] == 'EVACUATION'; sub = None; continue
        if b['t'] == 'section':
            in_evac = False; continue
        if not in_evac:
            continue
        if b['t'] == 'sub':
            sub = b['x']; continue
        text = plain(b['x'])
        for m in DIST.finditer(text):
            # the clause containing this distance (split at ';' so "ISOLATE ...; also, consider evacuation ..." become two)
            start = text.rfind(';', 0, m.start()) + 1
            end = text.find(';', m.end()); end = len(text) if end < 0 else end
            clause = text[start:end].strip()
            kind = 'downwind' if re.search(r'downwind', clause, re.I) else ('all' if re.search(r'all directions', clause, re.I) else 'unspecified')
            act = 'isolate' if re.search(r'isolate', clause, re.I) else ('evacuate' if re.search(r'evacuat', clause, re.I) else 'other')
            out.append({'sub': sub, 'm': int(m.group(1).replace(',', '')), 'other': m.group(2), 'dir': kind,
                        'action': act, 'text': text})
    return out


if __name__ == '__main__':
    g = json.load(open(sys.argv[1]))
    res = {x['guide']: statements(x) for x in g['guides']}
    json.dump(res, open(sys.argv[2], 'w'), indent=1)
    import collections
    print(collections.Counter((s['sub'], s['dir'], s['action']) for v in res.values() for s in v))
    print('guides without any distance:', [k for k, v in res.items() if not v])
