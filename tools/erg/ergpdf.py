"""Shared helpers for reading the ERG2024 PDF with PyMuPDF (word positions + drawn shapes)."""
import re
import pymupdf

GREEN = (0.552, 0.778, 0.247)   # highlight fill used in yellow/blue/green sections


def open_pdf(path):
    return pymupdf.open(path)


def printed_page(pdf_page):
    """PDF page number (1-based) -> printed page number (PDF page = printed + 2)."""
    return pdf_page - 2


def is_green(fill, tol=0.03):
    return fill is not None and all(abs(a - b) < tol for a, b in zip(fill, GREEN))


def green_rects(page):
    out = []
    for dr in page.get_drawings():
        if is_green(dr.get('fill')):
            for it in dr['items']:
                if it[0] == 're':
                    out.append(it[1])
            if not any(it[0] == 're' for it in dr['items']):
                out.append(dr['rect'])
    return out


def lines_in(words, x0, x1, ytol=2.0):
    """Group words whose x-centre is in [x0,x1) into lines (sorted top to bottom)."""
    ws = [w for w in words if x0 <= (w[0] + w[2]) / 2 < x1]
    ws.sort(key=lambda w: (round(w[1], 1), w[0]))
    lines = []
    for w in ws:
        if lines and abs(lines[-1]['y0'] - w[1]) <= ytol:
            lines[-1]['words'].append(w)
        else:
            lines.append({'y0': w[1], 'y1': w[3], 'words': [w]})
    for ln in lines:
        ln['words'].sort(key=lambda w: w[0])
        ln['x0'] = ln['words'][0][0]
        ln['text'] = ' '.join(w[4] for w in ln['words'])
    return lines


def norm_space(s):
    s = re.sub(r'\s+', ' ', s).strip()
    # rejoin words hyphenated across a line break only where the PDF really broke a word is not
    # detectable reliably; we keep hyphens as printed.
    return s


def join_lines(parts):
    """Join wrapped name/text lines. A line ending in '-' was broken after a real hyphen in ERG
    names (e.g. "water-" / "reactive"), so no space is added; "1," + "3-diol" -> "1,3-diol"."""
    out = ''
    for p in parts:
        p = p.strip()
        if not p:
            continue
        if not out:
            out = p
        elif out.endswith('-') and not out.endswith(' -'):
            out += p
        elif re.search(r'\d,$', out) and re.match(r'\d+-', p):
            out += p          # chemical locants: "1," + "3-diol" -> "1,3-diol
        else:
            out += ' ' + p
    return normalize_chars(out)


def normalize_chars(s):
    s = re.sub(r'[\x00-\x08\x0b-\x1f]', '', s)
    s = s.replace('\u201c', '"').replace('\u201d', '"').replace('\u2018', "'").replace('\u2019', "'")
    s = s.replace('\u2011', '-').replace('\u00a0', ' ')
    return re.sub(r'\s+', ' ', s).strip()
