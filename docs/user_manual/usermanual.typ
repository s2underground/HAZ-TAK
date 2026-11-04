
#import "@preview/polylux:0.4.0": *
#import "formatting.typ": *

#show: userguide.with(
   plugin-name: "HAZ-TAK",
   plugin-version: "0.5",
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
- Tap the *HAZ-TAK* toolbar button. *Incidents on the map* is at the top. The red *Emergency numbers* button is on every screen.
- More than one hazardous material involved? Don't use the flowchart: call the emergency response number now (ERG2024 p. 1).
- *1. Identify:* *ID number* (the 4 digits on the placard, orange panel or shipping paper; on an orange panel it's the bottom number), *Name* (shipping name; a misspelling gets *Did you mean*), *Placard*, or *Unknown* (Guide 111). Tap the matching material; changing the number or name clears the choice. Green entries are toxic-by-inhalation or water-reactive: they use the green tables. If the exact name isn't known, *Exact name not known* uses every entry with that ID and the largest distances.
- The chosen answer is always shown in teal with a bright border (for example which of two entries for the same ID number you picked).
- *2. Situation:* one card per question, each marked *REQUIRED*, *ANSWERED* or *OPTIONAL*: spill size, *Is the material on fire?* (nothing is pre-selected), land or water for water-reactive materials, day or night for green materials (filled in from the sun), and the wind direction it blows *FROM*. The wind is required for green materials; if it isn't known yet, *Wind not known yet* places the isolation circle now, labelled *PROTECTIVE ACTION ZONE NOT DRAWN*, until you enter it. Spill size *Not sure* uses large. Table 3 gases also need the container and wind speed; *Not sure* uses the largest.
- *3. Place:* *Tap map* (tap the spill itself), *Spill is at my position* (only if you are at the spill), *Map center*, or the HAZ-TAK button in any marker's radial menu. If a button is greyed, the line under it says what's still needed.
- Press *SEND TO ALL* on the incident card, or *Send to…*.
]

#tak-slide[
  = The zones
- *Red circle: initial isolation zone.* Isolate in all directions (Table 1/3 or the guide). In a Table 1 isolation zone protective clothing and respiratory protection are required (p. 288). "Isolate and evacuate" when the guide gives the same distance for both (e.g. a fire).
- *Orange square: protective action zone.* Downwind of the spill; its upwind edge passes through the spill and its length and width equal the protective action distance (ERG2024 p. 288). People in this area should be evacuated and/or sheltered-in-place.
- *Yellow circle: evacuation distance* from the guide (large spill or fire, e.g. "fire reaches cargo: clear the area"), when larger than the isolation circle.
- *Orange line: large-spill downwind evacuation* from the guide (drawn when the wind is entered).
- *Blue arrow: the wind you entered,* pointing downwind, drawn when nothing else shows the direction. It is not a distance. Green materials get the downwind square; the guides give other materials circles (and a downwind line for some large spills), and some guides say to increase the distance downwind as necessary: the card quotes that.
- *Fire:* the guide's fire distance becomes the isolation circle and Table 1 still protects downwind for residual release (p. 280).
- *Whole contents released at once* (terrorism, sabotage, catastrophic failure): tick the box to double the distances (p. 289).
Every shape is drawn at the larger of the printed metric and US values; labels show the values as printed, with the guide's "at least". The distances are minimums for the first phase. "11.0+ km" means the distance can be larger in some conditions.

]

#tak-slide[
  = The incident card
- The distances, each with its source (table or guide, page).
- *YOU:* your distance from the spill, upwind or downwind, and which drawn distance you are inside (including the downwind evacuation line's distance); "possibly" when the wind isn't entered; a warning when you are within GPS error of an edge. "Outside the drawn distances" is never shown as safe.
- An amber banner when it has become day or night at the incident since it was placed (the Table 1 distances change), with a button to update; *NOT SENT* until you share it.
- *Team:* how many contacts are in each zone.
- *Guidebook rules that apply*, quoted with page numbers.
- Tap an incident in the list to open its card; tap it again (or *Hide*) to close it.
- *Wind or time changed…* recomputes and redraws the zones.
- *Edit incident* opens the incident in the new-incident form: change the material or any answer, then *Save and send to all* (or this device only). The marker stays put; the card records who edited it and when. If someone else changed it while you were editing, HAZ-TAK asks before overwriting.
- *View guide*, *Rename…*, *Export…*, *Mark cleared / Reopen*, *Delete…*.
]

#tak-slide[
  = Sharing, clearing, deleting, exporting
- *SEND TO ALL* sends the marker first, then its shapes, then removes any zone it no longer has (so clients without HAZ-TAK don't keep an old square). A receiver with the same ERG edition recomputes the zones from its own data; if the sender's distances differ, the card says so and the receiver's values are drawn. Every device should run the same HAZ-TAK version.
- *Mark cleared* when the incident is over: callsign and Zulu time are logged and the zones turn grey for everyone. It deletes nothing.
- *Delete* removes the marker and all its zones: *This device only* or *Delete for everyone* (ATAK's remote delete). Deleting the marker with ATAK's own delete removes its zones too; a zone deleted on its own is redrawn while the incident needs it.
- Anyone can rename the marker or add to its remarks, with or without HAZ-TAK; HAZ-TAK only replaces its own *[HAZ-TAK]* block.
- *Export…* saves to `atak/export/HAZ-TAK/` as a data package, KMZ or GPX.

= Reference
Word for word from ERG2024, redrawn for the screen: the flowchart (with buttons into ID / Name / Placard / Unknown), 24-hour emergency response numbers (tap to dial), placards, how to use Table 1 (with the protective action zone figure), factors that may change the distances, evacuate or shelter (table), background on Table 1, national response centers, Table 3, General First Aid. A guide also shows the material's Table 1, 2 and 3 rows as tables.
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
