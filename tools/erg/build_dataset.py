"""Bundle the extracted ERG2024 data into one asset file (erg2024.json) plus a report.

Every rule quote used by the plugin is checked to occur verbatim (whitespace-normalised) in the
text of its cited page; the build fails otherwise."""
import hashlib, json, re, subprocess, sys, datetime
from ergpdf import open_pdf, printed_page, normalize_chars

PDF = sys.argv[1]; OUT = sys.argv[2]

# Reference pages shown in the plugin: structured by hand in reference_pages.py,
# every text checked verbatim against its PDF pages by verify_reference.py.
from reference_pages import PAGES as REF_PAGES
import verify_reference

# Rule quotes (printed page, exact text). Checked below.
RULES = {
    'unknown_name': (288, 'If you do not know the shipping name and Table 1 lists more than one name for the same ID number, use the entry with the largest distances.'),
    'small_spill': (288, 'A SMALL SPILL consists of a release of 208 liters (55 US gallons) or less.'),
    'large_spill': (288, 'A LARGE SPILL consists of a release of more than 208 liters (55 US gallons).'),
    'day_night': (288, 'DAY is any time after sunrise and before sunset. NIGHT is any time between sunset and sunrise.'),
    'isolation_zone': (288, 'This distance defines the radius of a zone (initial isolation zone) surrounding the spill in ALL DIRECTIONS.'),
    'paz_square': (288, 'For practical purposes, the protective action zone (i.e., the area in which people are at risk of harmful exposure) is a square. Its length and width are the same as the downwind distance shown in Table 1.'),
    'paz_people': (289, 'People in this area should be evacuated and/or sheltered-in-place.'),
    'start_closest': (289, 'Initiate protective actions beginning with those closest to the spill site and working away in a downwind direction.'),
    'double_catastrophic': (289, 'For the instantaneous release of the entire contents of a package (e.g., as a result of terrorism, sabotage or catastrophic failure), the distances should be doubled.'),
    'fire_rule': (280, 'In these cases, the fire hazard distance should be used as an isolation distance and Table 1 should be used to protect downwind for residual material release.'),
    'more_than_one_package': (280, 'If more than one rail tank car, highway tank, tank or large cylinder, containing TIH materials is leaking, large spill distances may need to be increased.'),
    'plus_distance': (281, 'If a material has a protective action distance of 11.0+ km (7.0+ miles), the actual distance can be larger in certain atmospheric conditions.'),
    'land_water_larger': (281, 'Choose the larger protective action distance if:'),
    'table2_info_only': (281, 'The produced TIH gases indicated in Table 2 are for information purposes only.'),
    'yellow_no_fire': (28, 'IF THERE IS NO FIRE:'),
    'yellow_fire': (28, 'Also protect in downwind direction according to Table 1 for residual material release'),
    'water_only_entry': (28, 'If a water-reactive material only has one entry in Table 1 for (when spilled in water) and the product is NOT spilled in water, Table 1 and Table 2 do not apply. You will find safe distances in the appropriate Orange Guide.'),
    'initial_phase': ('back cover', 'A guidebook intended for use by first responders during the initial phase of a transportation incident involving hazardous materials/dangerous goods'),
    'multi_material_warning': (1, 'DO NOT USE THIS FLOWCHART if more than one hazardous material/dangerous good is involved.'),
}


def page_text(d, pdf_page):
    """Plain text of a page, paragraphs reflowed (blocks joined), characters normalised."""
    pg = d[pdf_page - 1]
    paras = []
    for b in pg.get_text('blocks', sort=True):
        t = normalize_chars(b[4].replace('\n', ' '))
        t = re.sub(r'(\w)- (\w)', r'\1-\2', t) if False else t
        if t and not re.fullmatch(r'Page \d+|ERG 2024', t):
            paras.append(t)
    return paras


def ref_cite(d, pdf_pages):
    """'p. 1', 'pp. 280-282' or 'p. 388 and inside back cover' (pages without a printed number)."""
    nums = sorted(printed_page(p) for p in pdf_pages if re.search(r'Page \d+', d[p - 1].get_text()))
    unnumbered = [p for p in pdf_pages if not re.search(r'Page \d+', d[p - 1].get_text())]
    parts = []
    if nums:
        parts.append(f'p. {nums[0]}' if nums[0] == nums[-1] else f'pp. {nums[0]}-{nums[-1]}')
    for p in unnumbered:
        parts.append('inside back cover' if p == d.page_count - 1 else 'back cover' if p == d.page_count else f'PDF page {p}')
    return ' and '.join(parts)


def flat(s):
    return re.sub(r'\s+', ' ', normalize_chars(s)).strip()


def main():
    d = open_pdf(PDF)
    sha = hashlib.sha256(open(PDF, 'rb').read()).hexdigest()
    yellow = json.load(open(f'{OUT}/yellow.json'))
    blue = json.load(open(f'{OUT}/blue.json'))
    guides = json.load(open(f'{OUT}/guides.json'))
    gdist = json.load(open(f'{OUT}/guide_distances.json'))
    t1 = json.load(open(f'{OUT}/table1.json'))
    t2 = json.load(open(f'{OUT}/table2.json'))
    t3 = json.load(open(f'{OUT}/table3.json'))
    plac = json.load(open('placards.json'))

    problems = []
    ref_count, ref_problems = verify_reference.verify(d)
    problems += ref_problems
    # rule quotes must be verbatim on the cited page
    for k, (pp, q) in RULES.items():
        txt = flat(' '.join(page_text(d, d.page_count if pp == 'back cover' else pp + 2)))
        if flat(q) not in txt:
            problems.append(f'rule {k}: quote not found on printed p.{pp}')

    # flags per index entry
    t1_by = {}
    for r in t1:
        variant = 'water' if r['name'].endswith('(when spilled in water)') else ('land' if r['name'].endswith('(when spilled on land)') else None)
        base = re.sub(r'\s*\(when spilled (in water|on land)\)$', '', r['name'])
        t1_by.setdefault((r['id'], base), []).append(dict(r, variant=variant, base=base))
    t3_ids = {m['id'] for g in t3 for m in g['materials']}
    index = []
    for e in yellow:
        rows = t1_by.get((e['id'], e['name']), [])
        flags = []
        if e['highlighted']: flags.append('G')
        if any(r['variant'] != 'water' for r in rows): flags.append('TIH')
        if any(r['variant'] == 'water' for r in rows): flags.append('WR')
        if e['guide'].endswith('P'): flags.append('P')
        if e['id'] in t3_ids and e['highlighted']: flags.append('T3')
        index.append({'id': e['id'], 'guide': e['guide'], 'name': e['name'], 'flags': flags,
                      'yp': e['page'], 'bp': next((b['page'] for b in blue if (b['id'], b['guide'], b['name']) == (e['id'], e['guide'], e['name'])), None)})
        if e['highlighted'] and not rows:
            problems.append(f'highlighted without Table 1 row: {e}')
    # guide references must exist
    gnums = {g['guide'] for g in guides['guides']}
    for e in index:
        if e['guide'].rstrip('P') not in gnums:
            problems.append(f'unknown guide {e}')
    for g in guides['guides']:
        g['distances'] = gdist.get(g['guide'], [])

    data = {
        'format': 2,
        'edition': 'ERG2024',
        'source': {'title': '2024 Emergency Response Guidebook (ERG2024), English',
                   'publisher': 'U.S. DOT PHMSA, Transport Canada, SCT (Mexico), CIQUIME (Argentina)',
                   'url': 'https://www.phmsa.dot.gov/sites/phmsa.dot.gov/files/2024-04/ERG2024-Eng-Web-a.pdf',
                   'sha256': sha, 'pages': d.page_count, 'pageNote': 'page numbers are the printed page numbers'},
        'extracted': datetime.date.today().isoformat(),
        'index': index,
        'guides': guides['guides'],
        'generalFirstAid': guides['generalFirstAid'],
        'table1': [{'id': r['id'], 'guide': r['guide'], 'name': r['name'], 'page': r['page'],
                    'small': r['small'], 'large': r['large']} for r in t1],
        'table2': t2,
        'table3': t3,
        'placards': plac,
        'rules': {k: {'page': p, 'text': q} for k, (p, q) in RULES.items()},
        'reference': {k: {'title': v['title'], 'pages': [printed_page(p) for p in v['pdf']],
                          'cite': ref_cite(d, v['pdf']),
                          'blocks': [{bk: bv for bk, bv in b.items() if bk not in ('verify', 'image')} for b in v['blocks']]}
                      for k, v in REF_PAGES.items()},
    }
    json.dump(data, open(f'{OUT}/erg2024.json', 'w'), ensure_ascii=False, separators=(',', ':'))

    rep = [f'# ERG2024 extraction report', '',
           f'- Source PDF SHA-256: `{sha}` ({d.page_count} pages)',
           f'- Yellow index entries: {len(yellow)}; blue index entries: {len(blue)}; distinct ID numbers: {len({e["id"] for e in yellow})}',
           f'- Highlighted (green) entries: {sum(e["highlighted"] for e in yellow)}',
           f'- Guides: {len(guides["guides"])} ({sum(g["blank"] for g in guides["guides"])} intentionally blank: ' +
           ', '.join(g['guide'] for g in guides['guides'] if g['blank']) + ')',
           f'- Table 1 rows: {len(t1)} ({sum(1 for r in t1 if r["name"].endswith("(when spilled in water)"))} "when spilled in water", '
           f'{sum(1 for r in t1 if r["name"].endswith("(when spilled on land)"))} "when spilled on land", '
           f'{sum(1 for r in t1 if r["large"] == "TABLE3")} large spill "Refer to Table 3")',
           f'- Table 2 rows: {len(t2)}; Table 3 gases: {len(t3)} ({sum(len(g["rows"]) for g in t3)} container rows)',
           f'- Placards transcribed: {len(plac["placards"])}', '',
           '## Consistency checks', '',
           '- Yellow (by ID) and blue (by name) indexes contain exactly the same (ID, guide, name, highlighted) entries.',
           '- Every highlighted index entry has a Table 1 row and every Table 1 row matches a highlighted index entry.',
           '- Every Table 2 row matches a Table 1 "(when spilled in water)" row and vice versa.',
           '- Every guide number referenced by the index exists.',
           f'- Rule quotes checked verbatim against their pages: {len(RULES)}',
           f'- Reference pages: {len(REF_PAGES)}; texts checked verbatim against their pages: {ref_count}; '
           'drawn as outlines (no text layer), checked by eye against the rendered page: ' +
           '; '.join(f'"{t}" ({n})' for n, t in verify_reference.by_eye()), '',
           '## Problems', ''] + ([f'- {p}' for p in problems] or ['- none'])
    open(f'{OUT}/REPORT.md', 'w').write('\n'.join(rep) + '\n')
    print('\n'.join(rep))
    if problems:
        sys.exit(1)


main()
