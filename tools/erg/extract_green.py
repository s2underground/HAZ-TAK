"""Extract green Table 1 (pp. 290-333 printed) and Table 2 from ERG2024.

Table 1 is printed rotated 90 deg: a table row runs along the page's x axis, columns along y.
Cell borders are drawn as short vertical line segments; each distinct x of those segments is a
row-group boundary, and the y breakpoints of the segments are the column boundaries. Several
material names can share one row group (one set of distances)."""
import json, re, sys
from collections import defaultdict
from ergpdf import open_pdf, printed_page, join_lines, green_rects

T1_COLS = ['night_mi', 'night_km', 'day_mi', 'day_km', 'iso_ft', 'iso_m']   # for each spill size, by increasing y


def vsegs(page):
    out = []
    for dr in page.get_drawings():
        if (dr.get('width') or 0) > 0.6:
            continue
        for it in dr['items']:
            if it[0] == 'l':
                p, q = it[1], it[2]
                if abs(p.x - q.x) < 0.3 and abs(p.y - q.y) > 5:
                    out.append((round(p.x, 1), round(min(p.y, q.y), 1), round(max(p.y, q.y), 1)))
    return out


def table_geometry(page):
    segs = vsegs(page)
    byx = defaultdict(list)
    for x, y0, y1 in segs:
        byx[x].append((y0, y1))
    # row separators: x positions whose segments tile the table width (many segments)
    seps = sorted(x for x, s in byx.items() if len(s) >= 10)
    # column breakpoints from the separator with the most segments
    best = max(seps, key=lambda x: len(byx[x]))
    ys = sorted({round(y, 0) for a, b in byx[best] for y in (a, b)})
    return seps, ys


def cell_text(words):
    # rotated text: reading order within a cell = increasing x (lines), then decreasing y
    ws = sorted(words, key=lambda w: (round(w[0], 0), -w[1]))
    lines = []
    for w in ws:
        if lines and abs(lines[-1][0] - w[0]) < 2:
            lines[-1][1].append(w)
        else:
            lines.append([w[0], [w]])
    return [' '.join(x[4] for x in sorted(l[1], key=lambda w: -w[1])) for l in lines]


DIST_M = re.compile(r'^(\d+) m \((\d+) ft\)$')
DIST_KM = re.compile(r'^(\d+\.\d)(\+?) km \((\d+\.\d)(\+?) mi\)$')


def parse_dist_pair(m_txt, ft_txt):
    m = re.fullmatch(r'(\d+) m', m_txt); f = re.fullmatch(r'\((\d+) ft\)', ft_txt)
    if not (m and f):
        raise ValueError(f'bad isolation cell: {m_txt!r} {ft_txt!r}')
    return {'m': int(m.group(1)), 'ft': int(f.group(1))}


def parse_km_pair(km_txt, mi_txt):
    k = re.fullmatch(r'(\d+\.\d)(\+?) km', km_txt); mi = re.fullmatch(r'\((\d+\.\d)(\+?) mi\)', mi_txt)
    if not (k and mi):
        raise ValueError(f'bad protective-action cell: {km_txt!r} {mi_txt!r}')
    return {'km': k.group(1), 'mi': mi.group(1), 'more': bool(k.group(2) or mi.group(2))}


def parse_table1_page(page, pno):
    seps, ys = table_geometry(page)
    # columns (by y): 0..6 large spill (night mi .. iso m), 6..12 small, then name, guide, id
    if len(ys) != 16:
        raise ValueError(f'p{pno}: expected 16 column breakpoints, got {len(ys)}: {ys}')
    cols = list(zip(ys[:-1], ys[1:]))
    names = ['L_' + c for c in T1_COLS] + ['S_' + c for c in T1_COLS] + ['name', 'guide', 'id']
    words = page.get_text('words')
    green = green_rects(page)
    groups = []
    for a, b in zip(seps[:-1], seps[1:]):
        gw = [w for w in words if a < (w[0] + w[2]) / 2 < b]
        if not gw:
            continue
        cells = defaultdict(list)
        for w in gw:
            yc = (w[1] + w[3]) / 2
            for nm, (c0, c1) in zip(names, cols):
                if c0 <= yc < c1:
                    cells[nm].append(w); break
        groups.append((a, b, cells))
    out = []
    for a, b, cells in groups:
        # entries: each line in the ID column starts one; name lines follow by x
        ids = sorted(cells['id'], key=lambda w: w[0])
        guides = sorted(cells['guide'], key=lambda w: w[0])
        name_lines = cell_text(cells['name'])
        name_xs = sorted({round(w[0], 0) for w in cells['name']})
        if len(ids) != len(guides):
            raise ValueError(f'p{pno}: ids/guides mismatch {[w[4] for w in ids]} {[w[4] for w in guides]}')
        entries = []
        for k, idw in enumerate(ids):
            x_next = ids[k + 1][0] - 1 if k + 1 < len(ids) else b
            lines = [ln for ln, x in zip(name_lines, name_xs) if idw[0] - 2 <= x < x_next]
            entries.append({'id': idw[4], 'guide': guides[k][4], 'name': join_lines(lines)})
        row = {}
        large_text = ' '.join(cell_text(cells['L_iso_m']) + cell_text(cells['L_iso_ft']) + cell_text(cells['L_day_km'])
                              + cell_text(cells['L_day_mi']) + cell_text(cells['L_night_km']) + cell_text(cells['L_night_mi']))
        for sz in ('S', 'L'):
            t = {c: ' '.join(cell_text(cells[f'{sz}_{c}'])) for c in T1_COLS}
            if sz == 'L' and 'Refer' in large_text:
                if large_text.replace('  ', ' ').strip() != 'Refer to Table 3':
                    raise ValueError(f'p{pno}: odd large cell {large_text!r}')
                row['large'] = 'TABLE3'; continue
            if not any(t.values()):
                row['small' if sz == 'S' else 'large'] = None; continue
            row['small' if sz == 'S' else 'large'] = {
                'isolate': parse_dist_pair(t['iso_m'], t['iso_ft']),
                'day': parse_km_pair(t['day_km'], t['day_mi']),
                'night': parse_km_pair(t['night_km'], t['night_mi'])}
        for e in entries:
            e.update(row)
            e['page'] = printed_page(pno)
            out.append(e)
    return out


def main(pdf, outdir):
    d = open_pdf(pdf)
    t1 = []
    for pno in range(292, 336):
        pg = d[pno - 1]
        txt = pg.get_text()
        if 'TABLE 1' not in txt:
            print('skip p', pno, txt[:60].replace('\n', ' ')); continue
        t1 += parse_table1_page(pg, pno)
    json.dump(t1, open(f'{outdir}/table1.json', 'w'), indent=0, ensure_ascii=False)
    print('table1 entries', len(t1), 'distinct ids', len({e['id'] for e in t1}))


if __name__ == '__main__' and len(sys.argv) == 3:
    main(sys.argv[1], sys.argv[2])


# ---------------- Table 2 and Table 3 (text layout is clean enough) ----------------
import subprocess

def layout_text(pdf, pno):
    return subprocess.run(['pdftotext', '-layout', '-f', str(pno), '-l', str(pno), pdf, '-'],
                          capture_output=True, text=True, check=True).stdout


GAS_RE = r'(?:Br2|Cl2|HBr|HCl|HCN|HF|HI|H2S|NH3|NO2|PH3|SO2)'
T2_ROW = re.compile(r'^\s*(\d{4})\s+(1\d\dP?)\s+(.+?)\s+(' + GAS_RE + r'(?:\s+' + GAS_RE + r')*)\s*$')


def parse_table2(pdf):
    rows = []
    for pno in range(337, 342):
        lines = layout_text(pdf, pno).splitlines()
        cur = None
        for ln in lines:
            m = T2_ROW.match(ln)
            if m:
                cur = {'id': m.group(1), 'guide': m.group(2), 'parts': [m.group(3)],
                       'gases': m.group(4).split(), 'page': printed_page(pno)}
                rows.append(cur); continue
            s = ln.strip()
            if cur is not None and s and ln.startswith(' ' * 12) and not re.search(r'Chemical Symbols|Page \d|Use this list', s) \
                    and not re.match(r'^(Br|Cl|HBr|HCl|HCN|HF|HI|H|NH|NO|PH|SO)\s', s):
                cur['parts'].append(s)
            elif s.startswith('Chemical Symbols') or not s:
                cur = None if s.startswith('Chemical Symbols') else cur
    for r in rows:
        r['name'] = re.sub(r'\s*TABLE 2$', '', join_lines(r.pop('parts')))   # margin tab label
    return rows


NUM = r'(\d+\.\d)(\+?)\s+\((\d+\.\d)(\+?)\)'
T3_DATA = re.compile(r'^(.*?)\s*(\d+)\s+\((\d+)\)\s+' + r'\s+'.join([NUM] * 6) + r'\s*$')
T3_HDR = re.compile(r'UN(\d{4}) (.+?): Large Spills')


def parse_table3(pdf):
    gases, cur, pending = [], None, []
    for pno in range(343, 346):
        for ln in layout_text(pdf, pno).splitlines():
            hs = T3_HDR.findall(ln)
            if hs:
                if cur is None or cur['rows']:
                    cur = {'materials': [], 'rows': [], 'page': printed_page(pno)}
                    gases.append(cur)
                for i, n in hs:
                    cur['materials'].append({'id': i, 'names': [x.strip() for x in n.split(' / ')]})
                pending = []
                continue
            m = T3_DATA.match(ln)
            if m and cur is not None:
                g = m.groups()
                cont = join_lines(pending + [g[0].strip()])
                vals = g[3:]
                def pair(k):
                    km, p1, mi, p2 = vals[4 * k:4 * k + 4]
                    return {'km': km, 'mi': mi, 'more': bool(p1 or p2)}
                cur['rows'].append({'container': cont,
                                    'isolate': {'m': int(g[1]), 'ft': int(g[2])},
                                    'day': {'low': pair(0), 'moderate': pair(1), 'high': pair(2)},
                                    'night': {'low': pair(3), 'moderate': pair(4), 'high': pair(5)}})
                pending = []
                continue
            s = re.sub(r'\b(TRANSPORT|CONTAINER)\b', '', ln).strip()
            if cur is not None and s and not re.search(r'Page \d|TABLE 3|ISOLATE|PROTECT|wind|mph|Meters|DAY|NIGHT|means distance|OF SIX', s):
                pending.append(s)
    return gases


def main_tables(pdf, outdir):
    t2 = parse_table2(pdf)
    t3 = parse_table3(pdf)
    json.dump(t2, open(f'{outdir}/table2.json', 'w'), indent=0, ensure_ascii=False)
    json.dump(t3, open(f'{outdir}/table3.json', 'w'), indent=1, ensure_ascii=False)
    print('table2 rows', len(t2), '| table3 gases', len(t3), 'rows', sum(len(g['rows']) for g in t3))


if __name__ == '__main__' and len(sys.argv) > 3 and sys.argv[3] == 'tables':
    main_tables(sys.argv[1], sys.argv[2])
