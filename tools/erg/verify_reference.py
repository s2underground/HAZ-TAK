"""Check every text in reference_pages.py against its PDF pages (used by build_dataset.py)."""
import re
from ergpdf import normalize_chars
from reference_pages import PAGES


def key(s):
    s = re.sub(r'<[^>]+>', '', s)
    s = normalize_chars(s).replace(' ', ' ')
    return re.sub(r'\s+', '', s)


def strings(block):
    t = block['t']
    if t == 'link':
        return
    if 'verify' in block:  # text interrupted on the page (figure labels, page break)
        yield from block['verify']
    img = block.get('image')
    for k in ('q', 'name') + (() if 'verify' in block else ('x',)):
        if k in block and not (img is True and k == 'x') and not (img == k):
            yield block[k]
    for y in block.get('yes', []):
        yield from strings(dict(y, t='box'))
    if isinstance(block.get('no'), dict):
        yield from strings(dict(block['no'], t='box'))
    if isinstance(block.get('then'), dict):
        yield from strings(dict(block['then'], t='box'))
    for c in block.get('cols', []):
        yield c
    for r in block.get('rows', []):
        yield from r
    yield from block.get('lines', [])
    yield from block.get('labels', [])


def verify(doc):
    problems = []
    count = 0
    for name, pg in PAGES.items():
        text = key(normalize_chars(' '.join(doc[p - 1].get_text() for p in pg['pdf'])))
        for b in pg['blocks']:
            for s in strings(b):
                for piece in s.split(' — '):
                    count += 1
                    if key(piece) not in text:
                        problems.append(f'reference {name}: not found on PDF {pg["pdf"]}: {piece!r}')
    return count, problems


def by_eye():
    return [(n, b.get(b['image'] if b['image'] != True else 'x')) for n, pg in PAGES.items() for b in pg['blocks'] if b.get('image')]


if __name__ == '__main__':
    import sys
    from ergpdf import open_pdf
    n, probs = verify(open_pdf(sys.argv[1]))
    print(n, 'strings checked'); print('\n'.join(probs) or 'all verbatim')
