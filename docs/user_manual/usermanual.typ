
#import "@preview/polylux:0.4.0": *
#import "formatting.typ": *

#show: userguide.with(
   plugin-name: "HAZ-TAK",
   plugin-version: "0.1",
   platform: "ATAK",
   platform-version: "5.6.0",
)


#tak-slide[
  = Overview
#toolbox.side-by-side(columns: (.75fr, 9fr))[
#image("plugin_icon.png", width: 70%)
][
HAZ-TAK puts the *2024 Emergency Response Guidebook* (ERG2024) inside ATAK. Identify the material by ID number, name or placard, read its guide word for word, and draw the guidebook's isolation and protective action zones on the map for the whole team.

The zones are ordinary ATAK shapes and a marker, so every TAK client sees them, with or without HAZ-TAK. HAZ-TAK works fully offline. The guidebook is for the *initial phase* of a transportation incident.
]
= Getting started
- Tap the *HAZ-TAK* toolbar button. *Incidents on the map* is at the top.
- *1. Identify:* *ID number* (the 4 digits on the placard, orange panel or shipping paper), *Name*, *Placard*, or *Unknown* (Guide 111). Green entries are toxic-by-inhalation or water-reactive: they use the green tables. If the exact name isn't known, *Exact name not known* uses every entry with that ID and the largest distances.
- *2. Situation:* choose the spill size and whether a fire is involved (nothing is pre-selected). For green materials, check day or night (filled in from the sun) and enter the wind direction it blows *FROM*. Table 3 gases also need the container and wind speed; *Not sure* uses the largest.
- *3. Place:* *Tap map*, *My position* or *Map center*, or use the HAZ-TAK button in any marker's radial menu.
- Press *SEND TO ALL* on the incident card, or *Send to…*.
]

#tak-slide[
  = The zones
- *Red circle: initial isolation zone.* Isolate in all directions (Table 1/3 or the guide).
- *Orange square: protective action zone.* Downwind of the spill; its upwind edge passes through the spill and its length and width equal the protective action distance (ERG2024 p. 288). People in this area should be evacuated and/or sheltered-in-place.
- *Yellow circle: evacuation distance* from the guide (large spill or fire), when larger than the isolation circle.
- *Orange line: large-spill downwind evacuation* from the guide (drawn when the wind is entered).
- *Fire:* the guide's fire distance becomes the isolation circle and Table 1 still protects downwind for residual release (p. 280).
- *Whole contents released at once* (terrorism, sabotage, catastrophic failure): tick the box to double the distances (p. 289).
Every shape is drawn at the larger of the printed metric and US values; labels show the values as printed. "11.0+ km" means the distance can be larger in some conditions.

= The incident card
- The distances, each with its source (table or guide, page).
- *YOU:* your distance from the spill, upwind or downwind, and the zone you are in; a warning when you are within GPS error of an edge.
- *Team:* how many contacts are in each zone.
- *Guidebook rules that apply*, quoted with page numbers.
- *Wind or time changed…* recomputes and redraws the zones.
- *View guide*, *Rename…*, *Export…*, *Mark cleared / Reopen*, *Delete…*.
]

#tak-slide[
  = Sharing, clearing, deleting, exporting
- *SEND TO ALL* sends the marker first, then its shapes. A receiver with the same ERG edition recomputes the zones from its own data; if the sender's distances differ, the card says so and the receiver's values are drawn.
- Anyone can *Mark cleared* (callsign and Zulu time are logged); the zones turn grey.
- *Delete* removes the marker and all its zones: *This device only* or *Delete for everyone* (ATAK's remote delete). Deleting any part with ATAK's own delete removes the rest.
- Anyone can rename the marker or add to its remarks, with or without HAZ-TAK; HAZ-TAK only replaces its own *[HAZ-TAK]* block.
- *Export…* saves to `atak/export/HAZ-TAK/` as a data package, KMZ or GPX.

= Reference
Word for word from ERG2024: 24-hour emergency response numbers, how to use the guidebook and Table 1, factors that may change the distances, protective actions, national response centers, General First Aid.
]

#tak-slide[
  = Caveats
- Approach from upwind, uphill and/or upstream. Stay clear of spills, vapors, fumes and smoke.
- Placards can be missing or wrong and loads can be mixed. Switch to the specific guide as soon as the ID number or name is known.
- Get the shipping papers and call the emergency response number on them.
- Distances can be larger in valleys or between tall buildings, with inversions or snow cover, near sunset, above 30°C (86°F), or with more than one leaking container (pp. 280-281).
- HAZ-TAK does not replace training, technical specialists or department procedures.

= Source
2024 Emergency Response Guidebook (ERG2024), U.S. DOT PHMSA, Transport Canada, SCT (Mexico), CIQUIME (Argentina): #link("https://www.phmsa.dot.gov/training/hazmat/erg/emergency-response-guidebook-erg")[phmsa.dot.gov]. Guide text and table values are reproduced unchanged from the published PDF and checked by automated tests. HAZ-TAK is not endorsed by any of these agencies.
]
