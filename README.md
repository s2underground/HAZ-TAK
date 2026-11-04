HAZ-TAK


_________________________________________________________________
PURPOSE AND CAPABILITIES

HAZ-TAK is an ATAK plugin for the initial response to a hazardous
materials (dangerous goods) incident, built on the 2024 Emergency Response
Guidebook (ERG2024; U.S. DOT PHMSA, Transport Canada, SCT Mexico, CIQUIME
Argentina):

- Identify the material by 4-digit UN/NA ID number (yellow pages), by name
  (blue pages), by placard (Table of Markings, Labels and Placards), or as
  unknown cargo (Guide 111). If the exact shipping name isn't known, every
  entry with that ID is used and the largest distances win, as the
  guidebook says.
- Read the orange guide word for word (potential hazards, public safety,
  protective clothing, evacuation, emergency response, first aid), plus
  the material's Table 1, 2 and 3 rows.
- Draw the guidebook's zones as native ATAK shapes any TAK client sees:
  - green (TIH / water-reactive) materials: the initial isolation circle
    and the protective action square downwind from Table 1, or Table 3 for
    large spills of the six common TIH gases;
  - other materials, or a placard only: the guide's isolation distance,
    plus its large-spill evacuation and fire distances;
  - fire: the guide's fire distance as the isolation circle, with Table 1
    still protecting downwind (ERG2024 p. 280);
  - optional: distances doubled for a catastrophic or deliberate release
    (p. 289).
- Nothing is pre-selected: spill size and fire are chosen for every
  incident; day/night is filled in from the sun (sunrise to sunset) and
  can be changed; the wind direction is entered by the user.
- Every drawn distance is the larger of the printed metric and US values;
  nothing is interpolated or reduced. Labels show the values as printed.
- Incident list (Go to, Delete), incident card with where you are
  (isolation zone, protective action zone, outside; upwind or downwind),
  team counts, the guidebook rules that applied (quoted with page), and
  provenance (edition, table, page, who placed it, when).
- Send to everyone or chosen contacts, mark cleared (logged), update the
  wind or day/night, rename, export as ATAK data package, KMZ or GPX,
  delete locally or for everyone.
- Reference pages word for word, redrawn for the screen: the flowchart,
  24-hour emergency response numbers (tap to dial), placards, how to use
  Table 1 (with the zone figure), factors that change the distances,
  evacuate or shelter, national response centers, Table 3, General First
  Aid.

HAZ-TAK is fully offline. It does not replace training, the shipping
papers, the emergency response numbers, or technical specialists. The
guidebook is for the initial phase of a transportation incident.

_________________________________________________________________
STATUS

0.1 (Oct 2026): first test build.
0.2 (Nov 2026): device-test fixes: clear selected state, one card per
situation question, reference pages rebuilt as structured text and
tables, wind always enterable with a wind arrow for circle-only zones.
0.3 (Nov 2026): new icon (orange HAZ diamond) that new users recognise.
0.4 (Nov 2026): edit an incident (material and situation, with an edit
trail and a conflict check), tap again to collapse the card, keyboard
always shows Done.
0.5 (Nov 2026): pre-release audit fixes (see docs/AUDIT-0.5.md): Guide 113
cargo-fire distance, downwind evacuation in the position check, wind
not known yet, day/night banner, emergency numbers on every screen,
safer sharing (a lost packet can't delete an incident), and more.
Planned: 1.1 camera placard reader (photo -> 4-digit ID with Tesseract,
offline, always confirmed by the user); later rail car / road trailer
identification, the chemical warfare agent section, Spanish/French text.

_________________________________________________________________
POINT OF CONTACTS

S2Underground - s2underground@protonmail.com

_________________________________________________________________
PORTS REQUIRED

None. HAZ-TAK makes no network connections of its own; incidents are
shared through ATAK's normal CoT sending.

_________________________________________________________________
EQUIPMENT REQUIRED

None.

_________________________________________________________________
EQUIPMENT SUPPORTED

Any device running ATAK-CIV 5.6.0 or newer.

_________________________________________________________________
COMPILATION

Standard ATAK plugin build. `./gradlew assembleCivRelease` for release.
For the 5.5.1 developer build of ATAK, build with
`./gradlew assembleCivDebug -PatakVersion=5.5.0`.

Unit tests (`./gradlew testCivDebugUnitTest`) pin the dataset to the
printed guidebook (values with page numbers), check its consistency, and
test every rule used to choose distances, the protective action square,
the sun calculation (against PyEphem), the CoT detail (including fuzzing)
and the exports. One test computes the zones for every material in the
index.

_________________________________________________________________
THE ERG DATA

app/src/main/assets/erg2024.json is generated from the published ERG2024
PDF by the scripts in tools/erg/ (see tools/erg/REPORT.md for the checks):

    python3 tools/erg/extract_index.py  ERG2024-Eng-Web-a.pdf out
    python3 tools/erg/extract_guides.py ERG2024-Eng-Web-a.pdf out
    python3 tools/erg/extract_green.py  ERG2024-Eng-Web-a.pdf out
    python3 tools/erg/extract_green.py  ERG2024-Eng-Web-a.pdf out tables
    python3 tools/erg/guide_distances.py out/guides.json out/guide_distances.json
    (cd tools/erg && python3 build_dataset.py ERG2024-Eng-Web-a.pdf ../../out)
    python3 tools/erg/check_guides_text.py ERG2024-Eng-Web-a.pdf out/erg2024.json

(PyMuPDF and poppler's pdftotext are needed.) The placard table is
transcribed by hand in tools/erg/placards.json. For ERG2028, run the same
scripts on the new PDF, review REPORT.md and the failing golden tests, and
ship the new edition as a separate asset.

_________________________________________________________________
DEVELOPER NOTES

- core/ is pure Java: Erg (dataset), Search, Scenario (the user's
  choices), Zones (which table or guide applies; the rules), Geometry
  (protective action square), Sun, HazRecord, HazCodec (<__haztak/>
  detail), Drawing (the shapes, shared by map and exports), Labels,
  Names, PositionCheck, Exports.
- atak/: HazStore (marker + shapes with UIDs <marker>.iso, .paz, .dw,
  .ev1..; circles anchored to the marker, square and line redrawn when it
  moves), HazDetailHandler, HazMenuHandler (radial button), Sharing,
  Exporter.
- ui/: HazPane (built in code), PlacardIcon (original drawings).
- CoT detail:
  <__haztak v="1" ed="ERG2024" id="1017" mat="Chlorine" spill="L"
   fire="0" tod="N" tods="sun" wf="270" cont="Rail tank car" ws="MODERATE"
   x2="0" iso_m="1000" paz_m="9012.3" lbl="UN1017 Chlorine" by="CALLSIGN"
   t="..." st="ACTIVE|CLEARED" n="3"/>
  Guide-only incidents carry g (and pl for a placard) instead of id. A
  receiver with the same edition recomputes the zones from its own data
  and flags any difference; unknown attributes are kept and re-sent.
- Incident lifecycle (liveness, registry, whole-incident delete, remarks
  merge, radial icon) follows Blast 1.0.

_________________________________________________________________
ACKNOWLEDGMENTS

- 2024 Emergency Response Guidebook (ERG2024), U.S. Department of
  Transportation PHMSA, Transport Canada, Secretariat of Infrastructure,
  Communications and Transportation (Mexico), CIQUIME (Argentina). Guide
  text and table values are reproduced unchanged; the governments' names
  and seals are not used as endorsement. HAZ-TAK is not endorsed by any of
  these agencies. Transport Canada's terms allow the guidebook to be
  reproduced without further permission and forbid resale; HAZ-TAK is
  free. (Not legal advice.)
- No third-party code or libraries are bundled in the plugin.
