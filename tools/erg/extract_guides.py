"""Extract the orange guides (111-174) and the General First Aid section verbatim.

Output per guide: number, title, page (printed), and an ordered list of blocks:
  {"t": "section", "x": "POTENTIAL HAZARDS"}      white bar headings
  {"t": "head",    "x": "FIRE OR EXPLOSION"}      size-9 bold headings
  {"t": "sub",     "x": "Small Fire"}              whole-line bold text without a bullet
  {"t": "bullet",  "x": "..."}                     bullet item (may contain <b>, <sub>, <sup>)
  {"t": "para",    "x": "..."}                     other text
Text inside blocks is HTML-escaped with only <b>, <sub>, <sup> tags added.
"""
import html, json, re, sys
from ergpdf import open_pdf, printed_page, normalize_chars

WHITE = 0xffffff
ORANGE = 0xf89728


def spans_of(page):
    out = []
    for b in page.get_text('dict')['blocks']:
        for l in b.get('lines', []):
            for s in l['spans']:
                if not s['text'].strip() and s['text'] != ' ':
                    continue
                out.append(s)
    return out


def group_lines(spans):
    """Group spans into visual lines; small (sub/superscript) spans join the line they sit on."""
    main = [s for s in spans if s['size'] >= 7.5]
    small = [s for s in spans if s['size'] < 7.5]
    main.sort(key=lambda s: (round(s['bbox'][1], 0), s['bbox'][0]))
    lines = []
    for s in main:
        y = s['bbox'][1]
        if lines and abs(lines[-1]['y'] - y) < 2.0:
            lines[-1]['spans'].append(s)
        else:
            lines.append({'y': y, 'spans': [s]})
    for s in small:
        cy = (s['bbox'][1] + s['bbox'][3]) / 2
        best = min(lines, key=lambda ln: abs((ln['y'] + 5) - cy))
        s = dict(s)
        s['_script'] = 'sub' if cy > best['y'] + 5 else 'sup'
        best['spans'].append(s)
    for ln in lines:
        ln['spans'].sort(key=lambda s: s['bbox'][0])
    return lines


def is_bold(s):
    return 'Bold' in s['font'] or 'Demi' in s['font']


def render(spans):
    """Spans -> escaped text with <b>/<sub>/<sup>. Adjacent bold runs merge."""
    out, open_b = [], False
    for s in spans:
        t = normalize_chars_keep_space(s['text'])
        if s.get('_script'):
            if open_b: out.append('</b>'); open_b = False
            out.append(f"<{s['_script']}>{html.escape(t.strip(), quote=False)}</{s['_script']}>")
            continue
        b = is_bold(s) and t.strip() != ''
        if b and not open_b: out.append('<b>'); open_b = True
        if not b and open_b and t.strip(): out.append('</b>'); open_b = False
        out.append(html.escape(t, quote=False))
    if open_b: out.append('</b>')
    txt = ''.join(out)
    txt = re.sub(r'</b>(\s*)<b>', r'\1', txt)
    return re.sub(r'\s+', ' ', txt).strip()


def normalize_chars_keep_space(s):
    s = re.sub(r'[\x00-\x08\x0b-\x1f]', '', s)
    return (s.replace('“', '"').replace('”', '"').replace('‘', "'").replace('’', "'")
             .replace('‑', '-').replace(' ', ' '))


def join_text(a, b):
    if a.endswith('-') and not a.endswith(' -'):
        r = a + b
    else:
        r = a + ' ' + b
    return re.sub(r'</b>(\s*)<b>', r'\1', r)


def body_lines(page, exclude=()):
    key = lambda x: (round(x['bbox'][0], 1), round(x['bbox'][1], 1), x['text'])
    ex = {key(x) for x in exclude}
    lines = group_lines([x for x in spans_of(page) if key(x) not in ex])
    H = page.rect.height
    keep = []
    for ln in lines:
        sp = ln['spans']
        if ln['y'] > H - 30:                       # footer: "Page n", "ERG 2024"
            continue
        if all(s['color'] == ORANGE for s in sp):  # running header "Guides (orange section)"
            continue
        keep.append(ln)
    return keep


def parse_guide_pages(pages):
    number, title, blocks = None, [], []
    cur = None
    for page in pages:
        hdr = [x for x in spans_of(page) if x['color'] == WHITE and x['bbox'][1] < 48 and 'AvantGarde' in x['font']]
        tl = {}
        for x in sorted(hdr, key=lambda x: (round(x['bbox'][1]), x['bbox'][0])):
            t = x['text'].strip()
            if x['size'] >= 15:
                if re.fullmatch(r'\d{3}', t): number = number or t
            elif page is pages[0]:
                tl.setdefault(round(x['bbox'][1]), []).append(x['text'])
        for k in sorted(tl):
            title.append(''.join(tl[k]).strip())
        title = [re.sub(r'/\s+', '/', ' '.join(title))] if title else []
        lines = body_lines(page, hdr)
        bullet_x = None
        prev_y = None
        for ln in lines:
            gap = prev_y is not None and ln['y'] - prev_y > 16
            prev_y = ln['y']
            if gap:
                cur = None
            sp = ln['spans']
            txt_plain = ''.join(s['text'] for s in sp).strip()
            if txt_plain.startswith('In Canada, an Emergency Response Assistance Plan'):
                cur = {'t': 'canada', 'x': render(sp)}; blocks.append(cur); continue
            if cur is not None and cur['t'] == 'canada':
                cur['x'] = join_text(cur['x'], render(sp)); continue
            if any('AvantGardeSC' in s['font'] for s in sp) and sp[0]['color'] != WHITE:
                if cur is not None and cur['t'] == 'footnote':
                    cur['x'] = join_text(cur['x'], normalize_chars(txt_plain))
                else:
                    cur = {'t': 'footnote', 'x': normalize_chars(txt_plain)}; blocks.append(cur)
                continue
            if all(s['color'] == WHITE for s in sp) and txt_plain in ('•', ''):
                continue
            if all(s['color'] == WHITE for s in sp):
                if all(is_bold(s) and s['size'] >= 9.5 for s in sp if s['text'].strip()):
                    blocks.append({'t': 'section', 'x': normalize_chars(txt_plain)}); cur = None
                elif cur is not None and cur['t'] == 'titlenote':
                    cur['x'] = join_text(cur['x'], normalize_chars(txt_plain))
                else:
                    cur = {'t': 'titlenote', 'x': normalize_chars(txt_plain)}; blocks.append(cur)
                continue
            if re.match(r'^[\u2013\u2014]', txt_plain):
                first = dict(sp[0]); first['text'] = re.sub(r'^\s*[\u2013\u2014]+\s*', '', first['text'])
                cur = {'t': 'bullet2', 'x': render([first] + sp[1:])}; blocks.append(cur)
                continue
            if sp[0]['text'].strip() == '•' or sp[0]['text'].strip().startswith('•'):
                bullet_x = sp[0]['bbox'][0]
                rest = list(sp)
                first = dict(rest[0]); first['text'] = first['text'].strip()[1:]
                rest[0] = first
                cur = {'t': 'bullet', 'x': render(rest)}
                blocks.append(cur)
                continue
            if all(is_bold(s) for s in sp if s['text'].strip()) and max(s['size'] for s in sp) >= 8.9:
                blocks.append({'t': 'head', 'x': normalize_chars(txt_plain)}); cur = None
                continue
            x0 = sp[0]['bbox'][0]
            if cur is not None and cur['t'] in ('bullet', 'bullet2', 'para') and bullet_x is not None and x0 > bullet_x + 4:
                cur['x'] = join_text(cur['x'], render(sp))
                continue
            if all(is_bold(s) for s in sp if s['text'].strip()):
                # a bold line that continues a bold sub/para (wrapped CAUTION lines) joins it
                if cur is not None and cur['t'] == 'sub' and prev_wrapped(cur):
                    cur['x'] = join_text(cur['x'], normalize_chars(txt_plain)); continue
                cur = {'t': 'sub', 'x': normalize_chars(txt_plain)}
                blocks.append(cur)
                continue
            if cur is not None and cur['t'] == 'para':
                cur['x'] = join_text(cur['x'], render(sp)); continue
            cur = {'t': 'para', 'x': render(sp)}
            blocks.append(cur)
    return number, normalize_chars(' '.join(title)), blocks


def prev_wrapped(block):
    """A bold sub-heading never ends with sentence punctuation; a wrapped CAUTION sentence does not end yet."""
    return not re.search(r'[.:)]$', block['x']) and len(block['x']) > 40


def main(pdf, outdir):
    d = open_pdf(pdf)
    guides = []
    for first in range(154, 282, 2):
        n, title, blocks = parse_guide_pages([d[first - 1], d[first]])
        blank = any(b['x'] == 'There are no materials that refer to this guide.' for b in blocks)
        guides.append({'guide': n, 'title': title, 'page': printed_page(first), 'blank': blank,
                       'blocks': blocks})
    # General First Aid: PDF pages 152-153
    _, _, fa = parse_guide_pages([d[151], d[152]])
    json.dump({'guides': guides, 'generalFirstAid': {'page': printed_page(152), 'blocks': fa}},
              open(f'{outdir}/guides.json', 'w'), indent=1, ensure_ascii=False)
    print(len(guides), 'guides', guides[0]['guide'], '...', guides[-1]['guide'])


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
