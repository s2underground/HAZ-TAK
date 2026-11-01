"""Extract the yellow (by ID) and blue (by name) indexes of ERG2024.

Each entry: id ("" for the explosives rows shown as "— —"), guide (e.g. "116P"), name,
highlighted (green = TIH and/or water-reactive -> also use the green tables), page (printed).
"""
import json, re, sys
from ergpdf import open_pdf, green_rects, lines_in, printed_page, join_lines

ID_RE = re.compile(r'^\d{4}$')
GUIDE_RE = re.compile(r'^1[1-7]\dP?$')


def in_green(rects, x0, x1, y):
    return any(r.x0 - 1 <= x0 and x1 <= r.x1 + 1 and r.y0 - 0.5 <= y <= r.y1 + 0.5 for r in rects)


def parse_yellow_page(page, pno):
    W = page.rect.width
    words = page.get_text('words')
    rects = green_rects(page)
    out = []
    for (cx0, cx1) in ((0, W / 2), (W / 2, W)):
        cur = None
        for ln in lines_in(words, cx0, cx1):
            toks = [w[4] for w in ln['words']]
            if toks[:2] in (['ID', 'Guide'],) or toks[:1] == ['No.'] or toks[:1] == ['Page'] or (len(toks) == 1 and toks[0].isdigit() and ln['y0'] > 520):
                continue
            ym = (ln['y0'] + ln['y1']) / 2
            start = None
            if len(toks) >= 2 and ID_RE.match(toks[0]) and GUIDE_RE.match(toks[1]):
                start = (toks[0], toks[1], toks[2:])
            elif len(toks) >= 3 and toks[0] == '—' and toks[1] == '—' and GUIDE_RE.match(toks[2]):
                start = ('', toks[2], toks[3:])
            if start:
                cur = {'id': start[0], 'guide': start[1], 'parts': [' '.join(start[2])],
                       'highlighted': in_green(rects, ln['x0'] - 0.5, ln['x0'] + 5, ym),
                       'page': printed_page(pno)}
                out.append(cur)
            elif cur is not None:
                cur['parts'].append(' '.join(toks))
            else:
                raise ValueError(f'orphan line p{pno}: {ln["text"]}')
    for e in out:
        e['name'] = join_lines(e.pop('parts'))
    return out


def parse_blue_page(page, pno):
    W = page.rect.width
    words = page.get_text('words')
    rects = green_rects(page)
    out = []
    for (cx0, cx1) in ((0, W / 2), (W / 2, W)):
        lines = [ln for ln in lines_in(words, cx0, cx1)]
        lines = [ln for ln in lines if not ([w[4] for w in ln['words']][:2] in (['Name', 'of'], ['No.', 'No.'])
                 or [w[4] for w in ln['words']][:1] == ['Page'])]
        if not lines:
            continue
        cur = None
        for ln in lines:
            toks = [w[4] for w in ln['words']]
            if len(toks) == 1 and toks[0].isdigit() and len(toks[0]) < 4 and ln['y0'] > 520:
                continue
            ym = (ln['y0'] + ln['y1']) / 2
            # trailing guide + ID tokens
            g = i = None
            if len(toks) >= 2 and GUIDE_RE.match(toks[-2]) and (ID_RE.match(toks[-1]) or toks[-1] == '—'):
                g, i, body = toks[-2], toks[-1], toks[:-2]
            elif len(toks) >= 3 and GUIDE_RE.match(toks[-3]) and toks[-2:] == ['—', '—']:
                g, i, body = toks[-3], '', toks[:-3]
            else:
                body = toks
            if g:
                cur = {'id': ('' if i == '—' else i), 'guide': g, 'parts': [' '.join(body)],
                       'highlighted': in_green(rects, ln['x0'] - 0.5, ln['x0'] + 5, ym), 'page': printed_page(pno)}
                out.append(cur)
            else:
                if cur is None:
                    raise ValueError(f'orphan blue line p{pno}: {ln["text"]}')
                cur['parts'].append(' '.join(body))
    for e in out:
        e['name'] = join_lines(e.pop('parts'))
        if e['guide'] is None:
            raise ValueError(f'blue entry without guide/ID: {e}')
    return out


def main(pdf, outdir):
    d = open_pdf(pdf)
    yellow, blue = [], []
    for pno in range(31, 90):
        yellow += parse_yellow_page(d[pno - 1], pno)
    for pno in range(91, 150):
        blue += parse_blue_page(d[pno - 1], pno)
    json.dump(yellow, open(f'{outdir}/yellow.json', 'w'), indent=0, ensure_ascii=False)
    json.dump(blue, open(f'{outdir}/blue.json', 'w'), indent=0, ensure_ascii=False)
    print('yellow', len(yellow), 'highlighted', sum(e['highlighted'] for e in yellow))
    print('blue', len(blue), 'highlighted', sum(e['highlighted'] for e in blue))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
