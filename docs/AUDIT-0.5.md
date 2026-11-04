# HAZ-TAK pre-release audit (November 2026, version 0.4 → 0.5)

Before the first public release, HAZ-TAK 0.4 was audited in four independent
parts. Each part was done by a reviewer that had not written the code and
worked from the sources: the ERG2024 PDF, the ATAK SDK's bytecode and the
plugin's code. Every finding was then checked against those sources before
it was fixed. A fifth review checked the fixes for regressions.

| Audit | Method | Result |
|---|---|---|
| 1. Data vs the guidebook | Own parsers and rendered pages; **every** Table 1 row (537), Table 2 row (108), Table 3 cell, index entry (2,771 both sections, green highlighting) and placard; the guide distances of all 62 guides with an EVACUATION section | All matched except one missing guide distance (finding A) |
| 2. Doctrine and geometry | `Zones.compute` run on 3.4 million input combinations, checked against the guide and green-section rules; geometry at latitudes 0–85° and ±180° | 2 high, 3 medium, 2 low |
| 3. ATAK integration and sharing | ATAK SDK classes decompiled to check behaviour; 200,000 fuzzed CoT details; lifecycle, threading, deletes, R8, exports | 1 critical, 1 high, 5 lower |
| 4. Crisis usability | Every screen and string read as a stressed, untrained user would read it | 1 critical, 7 high, 10 medium |

## What was wrong, and what changed in 0.5

### Could give less protection than the guidebook

- **A. Guide 113 fire.** The guide's EMERGENCY RESPONSE section (p. 157) says "Stop all traffic and clear the area for at least 1600 meters (1 mile) in all directions and let burn". The extractor read only the EVACUATION section, so it drew 800 m. This affected 45 index entries. **Fixed:** the extractor now also reads that sentence. It adds a yellow "Fire reaches cargo: clear the area" ring, citing p. 157. A test checks every guide: no fire case draws less than any fire distance in its guide.
- **B. "YOU: outside" inside a downwind evacuation.** The position check ignored the guide's large-spill downwind evacuation distance. Standing 200 m downwind of a large gasoline spill was reported "outside", in green, although Guide 128 says "at least 300 meters". **Fixed:**
  - Anyone on the downwind side within that distance is now "inside the large-spill downwind evacuation distance". The guidebook gives no width, so the check is deliberately broad.
  - With no wind entered, anyone within reach of a downwind zone is "possibly inside".
  - The team count uses the same check.
  - "Outside" is never shown in green, and a near-edge position leads with "treat it as inside".
- **C. Retyping an ID kept the old material.** After choosing 1017 Chlorine, typing 1005 left Chlorine selected. **Fixed:** the selection now clears when the typed number or name stops matching it.
- **D. "My position" put the spill on the responder.** The ERG says responders stay back and upwind, so placing the spill at the responder's own fix shifts every zone upwind by that gap. **Fixed:** the button is now "Spill is at my position" and asks for confirmation. The placement message also says how far the spill is from you.
- **E. Day/night was never checked again** after the incident was placed. **Fixed:** an amber banner on the incident card says when it has become day or night at the incident, with a button to update the zones.

### Could lose an incident or show wrong zones on teammates' maps

- **F. One lost packet could delete an incident.** 0.4 sent removals for zones an edit no longer needed. If the updated marker was lost on a mesh but the removal arrived, HAZ-TAK receivers deleted the whole incident. **Fixed:**
  - A removed zone never deletes an incident. If the incident still needs that zone, HAZ-TAK draws it again; only deleting the marker deletes the incident.
  - Every SEND TO ALL now also removes any zone the incident no longer has. This covers devices that missed an edit and old zones a TAK server replays.
  - Old zones that arrived from the network are now cleaned up too.
- **G. The "sender's distances differ" warning never appeared** on a receiver, because storing the incident overwrote the sender's numbers. **Fixed:** the sender's numbers are kept and compared every time; this build's distances are still the ones drawn.
- **H. A newer sender could freeze an incident.** If the sender used a material name or code this build doesn't have, the update was ignored and the old zones stayed with no warning. **Fixed:** the sender's distances are drawn, with the warning.
- **Smaller fixes:**
  - Distances from an unknown guidebook edition are capped at 25 km (it was 100 km), and values under 1 m are refused.
  - Control characters in a callsign no longer break KML/GPX exports.
  - "HAZ n" numbering ignores names numbered 1000 or more.
  - Duplicate incident names show who placed each one.
  - The quick "Wind or time changed…" dialog applies its change to the incident as it is now and refuses an invalid wind.

### Wording and rules that were missing or could mislead

- **"At least" is kept** wherever the guide says it ("ISOLATE AT LEAST 50 meters…").
- **"Isolate and evacuate" circle.** When a guide gives the same distance to isolate and to evacuate, as in many fire cases, the circle now says so. "Including emergency responders" is shown where the guide says it (Guides 112, 114, 140).
- **Downwind note.** 22 guides say "increase the immediate precautionary measure distance, in the downwind direction, as necessary". That sentence is now quoted on the card. The wind help no longer says that only green materials have a downwind component.
- **What the zones mean.** Under each Table 1 zone the card quotes what it means: protective clothing and respiratory protection are required in the isolation zone, and the public is evacuated crosswind (p. 288). People in the protective action zone should be evacuated and/or sheltered-in-place (p. 289).
- **More than one material.** The ERG's warning ("DO NOT USE THIS FLOWCHART if more than one hazardous material…") now heads step 1.
- **"Wind not known yet"** places the isolation circle for a green material straight away. It is labelled "PROTECTIVE ACTION ZONE NOT DRAWN: WIND UNKNOWN" on every device and in the remarks, and the card has an "Enter the wind direction now" button. Before, nothing could be placed until the wind was known.
- **Spill size.** The ERG's examples are now shown (a drum is a small spill, a large package or many small packages a large one), and there is a "Not sure: use large" button.
- **Table 3** wind speeds are shown in km/h and mph, and "Not sure" says it uses the largest values.
- **Emergency numbers.** A red "Emergency numbers" button sits next to Back on every guide and reference screen; on the main screen the red button under Reference is the way in (0.6: the extra main-screen button was removed as a duplicate). Each number on the phone pages has its own large call button, and there is a 911 button marked as HAZ-TAK's addition.
- **Names.** A misspelled name gets "Did you mean". A 2- or 3-digit number gets a hint that it is the hazard code from the top half of an orange panel, not the ID number.
- **Guide 140 fire.** A note says the 1,600 m ammonium-nitrate fire distance is applied to every Guide 140 material, as a conservative choice.
- **Other smaller changes:**
  - Distances are shown metres first.
  - Important help text is 13 sp or larger.
  - "Mark cleared" explains that it deletes nothing, and sits apart from Delete.
  - An incident that hasn't been sent yet is marked NOT SENT.
  - The "Name not known" rule is quoted only when Table 1 applies.
  - The land/water rule quote includes its condition.
  - The position check uses the same ellipsoid as the drawing (up to 30–60 m apart at 11–22 km before).

## Regression review of the fixes

A fifth reviewer checked the 0.5 changes against 0.4. It compared the zones for all 115,632 index, ID-only and guide-only scenarios under the old and new code: the only change is the new 1,609 m ring for Guide 113 with fire. It also found seven smaller slips, all now fixed:

- The card said "This build doesn't have ERG2024" for an incident this build can't compute.
- Choosing a placard and then typing an ID kept the placard's guide.
- An out-of-range distance from a sender, or a sender on a newer version, was flagged only until the incident was stored.
- The quick wind dialog could reset day/night.
- The day/night banner also appeared when the user had chosen day or night themselves.
- The "not a direction" wind warning could stay after a valid wind was picked.

## Decided, not changed

- **Table 1 isolation vs the guide's "immediate precautionary measure".** Example: chlorine, small spill: Table 1 gives 60 m and Guide 124 says "at least 100 meters". The ERG flowchart (p. 1: "USE TABLE 1 for isolation and protective action distances") and the guides' EVACUATION sections (Guide 124: "See Table 1 - Initial Isolation and Protective Action Distances"; Guide 125: "For highlighted materials: see Table 1 …") direct green materials to Table 1, so HAZ-TAK draws the Table 1 value. *Confirmed by the developer (Nov 4, 2026): the guidebook's Table 1 is the authority.*
- **Ignored on purpose, all acceptable for initial response:**
  - "P" (polymerization) is information only.
  - Guide 114's 15 m for 1.4S packages is smaller than what is drawn.
  - "If tank … is involved in a fire" is applied to any fire, which is more protective.
  - Explosives division distances are taken from Guides 112 and 114.
- **Every device must run the same version.** 0.4 and earlier react badly to 0.5's zone removals, and they ignore incidents placed with "wind not known yet".

## Verified correct (selection)

- **Data:** every value, name, guide number and highlight against the PDF (audit 1).
- **Every scenario combination:** isolation is never smaller than the guide's fire isolation; large spills use large-spill rows or Table 3; land/water unknown uses the larger distances; ×2 applies to everything; labels match drawn sizes (audit 2).
- **Square:** orientation and geometry, and the sun-based day/night threshold (audit 2).
- **Robustness:** no crash or absurd output from 200,000 fuzzed details; threading and plugin unload; persistence across ATAK restarts; R8 rules; export validity (audit 3).

Tests: 105 unit tests, including new ones for every fix above.
