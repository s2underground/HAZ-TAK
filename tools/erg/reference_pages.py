"""Reference pages for HAZ-TAK, structured by hand from ERG2024.

Every text below is copied from the guidebook. build_dataset.py checks that
each one (tags removed, whitespace and line-break hyphens normalised) occurs
on the PDF pages listed for its page set, and fails otherwise.

Block types (rendered natively by the plugin):
  h      heading                     p     paragraph (may contain <b>)
  b, b2  bullet / sub-bullet         num   numbered step: n + text
  warn   warning box                 note  note line
  box    coloured box (color: orange, yellow, blue, green, white, black, dotted)
  q      flowchart question: q, yes (list of boxes), no ("next" or a box), action
  table  cols + rows (each cell verified separately)
  phone  name + lines (numbers become tap-to-dial)
  figure kind "paz" (drawn by the plugin, labels verified)
  link   plugin button: action + label (plugin wording, not verified)

"image": the text is drawn as outlines on the page (no text layer), so it was
checked by eye against the rendered page; True = the block's x, "q" = its question.
"""

PAGES = {}


def page(key, title, pdf_pages, blocks):
    PAGES[key] = {"title": title, "pdf": pdf_pages, "blocks": blocks}


# ---------------------------------------------------------------- p. 1
page("how_to_use", "How to use this guidebook", [3], [
    {"t": "h", "x": "RESIST RUSHING IN!"},
    {"t": "p", "x": "<b>APPROACH INCIDENT FROM UPWIND, AND UPHILL AND/OR UPSTREAM</b>"},
    {"t": "p", "x": "<b>STAY CLEAR OF ALL SPILLS, VAPORS, FUMES, SMOKE, AND POTENTIAL HAZARDS</b>"},
    {"t": "warn", "x": "<b>WARNING:</b> DO NOT USE THIS FLOWCHART if <b>more than one hazardous material/dangerous good</b> is involved. Immediately call the appropriate emergency response agency telephone number listed on the inside back cover of this guidebook. For <b>chemical or biological warfare agents</b>, refer to the “Criminal or Terrorist Use of Chemical, Biological and Radiological Agents” section."},
    {"t": "q", "q": "Do you see an explosive placard or label?",
     "yes": [{"color": "orange", "x": "For Divisions 1.1, 1.2, 1.3, or 1.5 — <b>Use GUIDE 112</b>"},
             {"color": "orange", "x": "For Divisions 1.4 or 1.6 — <b>Use GUIDE 114</b>"}],
     "action": "EXPLOSIVES"},
    {"t": "q", "q": "Do you know the UN/NA ID number?",
     "yes": [{"color": "yellow", "x": "Search for the ID number in the Yellow Pages"}], "action": "ID"},
    {"t": "q", "q": "Do you know the name of the material?",
     "yes": [{"color": "blue", "x": "Search for the material in the Blue Pages"}], "action": "NAME"},
    {"t": "q", "q": "Do you see a placard or label?",
     "yes": [{"color": "white", "x": "Refer to Pages 8-9"}], "action": "PLACARD"},
    {"t": "q", "q": "Do you see a railcar or road trailer?",
     "yes": [{"color": "white", "x": "Refer to Pages 10-15"}], "action": "RAIL"},
    {"t": "box", "color": "orange", "x": "<b>Use GUIDE 111</b>", "label": "NO to all", "action": "UNKNOWN"},
    {"t": "box", "color": "orange", "x": "<b>Determine the Guide Number</b>"},
    {"t": "box", "color": "dotted", "image": True, "x": "Redo the flowchart once the name of the material or the accurate UN/NA ID number is identified"},
    {"t": "box", "color": "dottedred", "x": "<b>If the Guide Number has a “P” next to it, the material may suffer a violent polymerization</b>"},
    {"t": "q", "q": "Is the material highlighted in green?",
     "no": {"color": "orange", "x": "<b>Use the appropriate Orange Guide</b>"}},
    {"t": "q", "q": "Is the material on fire?", "dark": True, "image": "q",
     "yes": [{"color": "orange", "x": "<b>USE the appropriate Orange Guide for EVACUATION distances, THEN</b>"},
             {"color": "green", "x": "PROTECT in downwind direction according to TABLE 1 for residual material release"}],
     "no": {"color": "green", "x": "<b>USE TABLE 1 for isolation and protective action distances, AND</b>",
            "then": {"color": "orange", "x": "CONSULT the appropriate Orange Guide"}}},
    {"t": "box", "color": "dottedgreen", "x": "If product includes the reference <b>(when spilled in water)</b>, consult TABLE 2 for a list of the generated gases (for information purposes only), AND do not use water as extinguishing agent."},
    {"t": "p", "x": "<b>BEFORE AN EMERGENCY - BECOME FAMILIAR WITH THIS GUIDEBOOK!</b>"},
    {"t": "p", "x": "First responders must be trained in the use of this guidebook."},
])

# ---------------------------------------------------------------- inside back cover
page("phone_numbers", "24-hour emergency response numbers", [391, 390], [
    {"t": "h", "x": "CANADA"},
    {"t": "phone", "name": "CANUTEC", "lines": ["1-888-CANUTEC (226-8832) (in Canada and U.S.)", "or 613-996-6666 *", "*666 (STAR 666) cellular (in Canada only)"]},
    {"t": "h", "x": "UNITED STATES"},
    {"t": "phone", "name": "CHEMTREC", "lines": ["1-800-424-9300", "(in the U.S., Canada and the U.S. Virgin Islands)", "For calls originating elsewhere: 703-527-3887 *"]},
    {"t": "phone", "name": "CHEMTEL, INC.", "lines": ["1-888-255-3924", "(in the U.S., Canada, Puerto Rico and the U.S. Virgin Islands)", "For calls originating elsewhere: 813-248-0573 *"]},
    {"t": "phone", "name": "INFOTRAC", "lines": ["1-800-535-5053", "(in the U.S., Canada and the U.S. Virgin Islands)", "For calls originating elsewhere: 352-323-3500 *"]},
    {"t": "phone", "name": "VERISK 3E", "lines": ["1-800-451-8346", "(in the U.S., Canada and the U.S. Virgin Islands)", "For calls originating elsewhere: 760-602-8703 *"]},
    {"t": "p", "x": "The emergency response information services shown above maintain periodically updated lists of state and Federal radiation authorities who provide information and technical assistance on handling incidents involving radioactive materials."},
    {"t": "phone", "name": "MILITARY SHIPMENTS, for assistance at incidents involving materials being shipped by, for, or to the Department of Defense (DOD), call one of the following numbers:", "lines": ["703-695-4695/4696 * - Explosives/ammunition incidents", "(U.S. Army Operations Center)", "1-800-851-8061 - All other hazardous materials/dangerous goods incidents", "(Defense Logistics Agency)"]},
    {"t": "phone", "name": "NATIONWIDE POISON CONTROL CENTER (United States only)", "lines": ["1-800-222-1222"]},
    {"t": "h", "x": "MEXICO"},
    {"t": "phone", "name": "CENACOM", "lines": ["555128-0000 extensions 36428, 36422, 36469, 37807, 37810"]},
    {"t": "phone", "name": "CONASENUSA", "lines": ["800-11-131-68 in the Republic of Mexico"]},
    {"t": "phone", "name": "SETIQ", "lines": ["800-00-21-400 or 55-5559-1588", "For calls originating elsewhere, call: +52-55-5559-1588"]},
    {"t": "h", "x": "ARGENTINA"},
    {"t": "phone", "name": "CIQUIME", "lines": ["0-800-222-2933 in the Republic of Argentina", "For calls originating elsewhere, call: +54-11-4552-8747*"]},
    {"t": "h", "x": "BRAZIL"},
    {"t": "phone", "name": "PRÓ-QUÍMICA", "lines": ["0-800-1108270 in Brazil", "For calls originating elsewhere, call: +55-19-3833-5310*"]},
    {"t": "h", "x": "COLOMBIA"},
    {"t": "phone", "name": "CISPROQUIM", "lines": ["01-800-091-6012 in Colombia", "For calls originating in Bogotá, Colombia call: 288-6012", "For calls originating elsewhere call: +57-1-288-6012 / +57-1-919-1919"]},
    {"t": "h", "x": "CHILE"},
    {"t": "phone", "name": "CITUC QUÍMICO", "lines": ["2-2247-3600 in the Republic of Chile", "For calls originating elsewhere call +56-2-2247-3600"]},
    {"t": "note", "x": "* Collect calls are accepted"},
])

# ---------------------------------------------------------------- pp. 384-386
page("national_response_centers", "National response centers", [386, 387, 388], [
    {"t": "p", "x": "For the purposes of this guidebook, the terms hazardous materials/dangerous goods are synonymous."},
    {"t": "h", "x": "CANADA"},
    {"t": "num", "n": "1.", "x": "<b>CANUTEC</b>"},
    {"t": "p", "x": "CANUTEC is the Canadian Transport Emergency Centre operated by the Transportation of Dangerous Goods Directorate of Transport Canada."},
    {"t": "p", "x": "CANUTEC provides a national bilingual (French and English) advisory service and is staffed by professional scientists experienced and trained in interpreting technical information and providing emergency response advice."},
    {"t": "phone", "name": "In an emergency, CANUTEC may be called at", "lines": ["1-888-CANUTEC (226-8832)", "or collect at 613-996-6666 (24 hours)", "*666 cellular (Press Star 666, Canada only)"]},
    {"t": "p", "x": "In a non-emergency situation, please call the information line at 613-992-4624 (24 hours)."},
    {"t": "p", "x": "The Transportation of Dangerous Goods Act, 1992 (TDG Act) requires reporting dangerous goods incidents which meet or exceed established reporting criteria listed in the Transportation of Dangerous Goods Regulations (TDG Regulations). For more information, consult the TDG website:"},
    {"t": "p", "x": "https://tc.canada.ca/en/dangerous-goods/canutec/reporting-requirements"},
    {"t": "num", "n": "2.", "x": "<b>PROVINCIAL/TERRITORIAL AGENCIES</b>"},
    {"t": "p", "x": "Although technical information and emergency response assistance can be obtained from CANUTEC, there are federal, provincial and territorial regulations requiring the reporting of dangerous goods incidents to certain authorities."},
    {"t": "p", "x": "The following list of provincial/territorial agencies is supplied for your convenience."},
    {"t": "table", "cols": ["Province", "Emergency Authority and/or Telephone Number"], "phones": 1, "rows": [
        ["Alberta", "Local Police and Provincial Authority 1-800-272-9600"],
        ["British Columbia", "Local Police and Provincial Authority 1-800-663-3456"],
        ["Manitoba", "Local Police or Fire Department and Provincial Authority 1-855-944-4888"],
        ["New Brunswick", "Local Police or Provincial Authority 1-800-565-1633"],
        ["Newfoundland and Labrador", "Local Police and Provincial Authority 1-800-563-9089"],
        ["Northwest Territories", "Local Police and Provincial Authority 1-867-920-8130"],
        ["Nova Scotia", "Local Police or Provincial Authority 1-800-565-1633"],
        ["Nunavut", "Local Police and Provincial Authority 1-867-920-8130"],
        ["Ontario", "Local Police and Provincial Authority 1-800-268-6060"],
        ["Prince Edward Island", "Local Police or Provincial Authority 1-800-565-1633"],
        ["Quebec", "Local Police and Provincial Authority 1-866-694-5454"],
        ["Saskatchewan", "Local Police and Provincial Authority 1-800-667-7525"],
        ["Yukon", "Local Police and Provincial Authority 1-867-667-7244"],
    ]},
    {"t": "num", "n": "3.", "x": "<b>EMERGENCY RESPONSE ASSISTANCE PLANS (Applies in Canada ONLY)</b>"},
    {"t": "p", "x": "An ERAP or Emergency Response Assistance Plan is a Transport Canada approved plan that describes what is to be done in the event of a transportation incident involving certain high-risk dangerous goods that require specialized emergency response personnel and equipment."},
    {"t": "p", "x": "For shipments that require an ERAP, an ERAP reference number and telephone number to obtain assistance will be included on the shipping paper. Anyone can call the ERAP telephone number. If additional information is required, or to determine if the product involved in the emergency requires an ERAP, contact CANUTEC."},
    {"t": "h", "x": "UNITED STATES"},
    {"t": "h", "x": "NATIONAL RESPONSE CENTER (NRC)"},
    {"t": "p", "x": "The NRC, which is operated by the U.S. Coast Guard, receives reports required when hazardous materials are spilled. After receiving notification of an incident, the NRC will immediately notify the appropriate Federal On-Scene Coordinator and concerned Federal agencies. Federal law requires that anyone who releases into the environment a reportable quantity of a hazardous material (including oil when water is, or may be affected) or a material identified as a marine pollutant, must immediately notify the NRC. When in doubt as to whether the amount released equals the required reporting levels for these materials, the NRC should be notified."},
    {"t": "phone", "name": "CALL NRC (24 hours)", "lines": ["1-800-424-8802", "(Toll-free in the U.S., Canada, and the U.S. Virgin Islands)", "202-267-2675 in the District of Columbia"]},
    {"t": "note", "x": "Calling the emergency response telephone number, CHEMTREC®, CHEMTEL, INC., INFOTRAC or 3E COMPANY, does not constitute compliance with regulatory requirements to call the NRC."},
])

# ---------------------------------------------------------------- pp. 280-282
page("green_intro", "Factors that may change the distances", [282, 283, 284], [
    {"t": "h", "x": "TABLE 1 - INITIAL ISOLATION AND PROTECTIVE ACTION DISTANCES"},
    {"t": "p", "x": "This table suggests distances useful to protect people from vapors/gases resulting from spills involving:"},
    {"t": "b", "x": "materials that are considered toxic by inhalation (TIH) (PIH in the US)"},
    {"t": "b", "x": "materials which produce toxic gases upon contact with water"},
    {"t": "p", "x": "This table provides first responders with initial guidance until technically qualified emergency response personnel are available. For each material, first responders will find distances for the following zones:"},
    {"t": "b", "x": "The <b>Initial Isolation Zone</b> defines an area surrounding the incident in which people may be exposed to dangerous (upwind) and life-threatening (downwind) concentrations of material."},
    {"t": "b", "x": "The <b>Protective Action Zone</b> defines an area downwind from the incident in which people may become incapacitated and unable to take protective action and/or incur serious or irreversible health effects. Table 1 provides specific guidance for small and large spills occurring day or night."},
    {"t": "p", "x": "Adjusting distances for a specific incident involves many interdependent variables. These adjustments should only be made by technically qualified personnel. For this reason, no precise guidance can be provided in this document to aid in adjusting the table distances; however, general guidance follows."},
    {"t": "h", "x": "Factors that May Change the Protective Action Distances"},
    {"t": "h2", "x": "Fire"},
    {"t": "p", "x": "In the orange section, under EVACUATION – Fire, the evacuation distance required to protect against fragmentation hazard of a large container is clearly indicated. If involved in a fire, the toxic hazard may be less dangerous than the fire or explosion hazard."},
    {"t": "p", "x": "In these cases, the fire hazard distance should be used as an isolation distance and Table 1 should be used to protect downwind for residual material release."},
    {"t": "h2", "x": "Worst-case scenario: terrorism, sabotage or catastrophic accident"},
    {"t": "p", "x": "Initial isolation and protective action distances are derived from historical data on transportation incidents and the use of statistical models. For worst-case scenarios involving the instantaneous release of the entire contents of a package (e.g., as a result of terrorism, sabotage or catastrophic accident), the distances may increase substantially."},
    {"t": "p", "x": "For such events, doubling the initial isolation and protective action distances is appropriate in absence of other information."},
    {"t": "h2", "x": "When more than one large package is leaking"},
    {"t": "p", "x": "If more than one rail tank car, highway tank, tank or large cylinder, containing TIH materials is leaking, large spill distances may need to be increased."},
    {"t": "h2", "x": "Other factors that can increase the protective action distance:"},
    {"t": "b", "x": "If a material has a protective action distance of 11.0+ km (7.0+ miles), the actual distance can be larger in certain atmospheric conditions."},
    {"t": "b", "x": "If the material’s vapor plume is channeled in a valley or between many tall buildings, protective action distances may be larger than shown due to less mixing of the plume with the atmosphere."},
    {"t": "b", "x": "If there is a daytime spill in a region with known strong temperature inversions or snow cover, or it occurs near sunset, this may require an increase of the protective action distance because airborne contaminants mix and disperse more slowly and may travel much farther downwind."},
    {"t": "b2", "x": "In such cases, the nighttime protective action distances may be more appropriate."},
    {"t": "b", "x": "If the temperature of the liquid spill or the outdoor temperature exceeds 30°C (86°F), the protective action distance may be larger."},
    {"t": "h2", "x": "Water-reactive materials"},
    {"t": "p", "x": "Materials that react with water to produce large amounts of toxic gases are included in Table 1. Some of these materials have 2 entries in Table 1. They are identified by (when spilled on land) since they are TIH products and (when spilled in water) because they produce additional toxic gases when spilled in water."},
    {"t": "p", "x": "Choose the larger protective action distance if:"},
    {"t": "b", "x": "it is not clear whether the spill is on land or in water"},
    {"t": "b", "x": "the spill occurs both on land and in water"},
    {"t": "h", "x": "TABLE 2 - WATER-REACTIVE MATERIALS WHICH PRODUCE TOXIC GASES"},
    {"t": "p", "x": "This table lists materials which produce large amounts of Toxic Inhalation Hazard gases (TIH) when spilled in water as well as the TIH gases that are produced."},
    {"t": "note", "x": "<b>NOTE:</b> The produced TIH gases indicated in Table 2 are for information purposes only. In Table 1, the initial isolation and protective action distances have already taken into consideration the produced TIH gas."},
    {"t": "p", "x": "When a water-reactive TIH-producing material is spilled into a river or stream, the source of the toxic gas may flow downstream for a great distance."},
    {"t": "link", "action": "MATERIAL_TABLES", "label": "Table 1 and Table 2 rows: open a material's guide (View guide)"},
    {"t": "h", "x": "TABLE 3 - INITIAL ISOLATION AND PROTECTIVE ACTION DISTANCES FOR LARGE SPILLS FOR DIFFERENT QUANTITIES OF SIX COMMON TIH (PIH IN THE US) GASES"},
    {"t": "p", "x": "This table lists materials that may be more commonly encountered. These materials are:"},
    {"t": "b", "x": "UN1005 - Ammonia, anhydrous"},
    {"t": "b", "x": "UN1017 - Chlorine"},
    {"t": "b", "x": "UN1040 - Ethylene oxide and UN1040 - Ethylene oxide with nitrogen"},
    {"t": "b", "x": "UN1050 - Hydrogen chloride, anhydrous and UN2186 - Hydrogen chloride, refrigerated liquid"},
    {"t": "b", "x": "UN1052 - Hydrogen fluoride, anhydrous"},
    {"t": "b", "x": "UN1079 - Sulfur dioxide/Sulphur dioxide"},
    {"t": "p", "x": "This table provides initial isolation and protective action distances for large spills (more than 208 liters or 55 US gallons):"},
    {"t": "b", "x": "involving different container types (therefore different volume capacities)"},
    {"t": "b", "x": "for daytime and nighttime situations"},
    {"t": "b", "x": "for different wind speeds (low, moderate and high)"},
    {"t": "link", "action": "TABLE3", "label": "Open Table 3 (all six gases)"},
])

# ---------------------------------------------------------------- pp. 283-285
page("protective_actions", "Protective actions: evacuate or shelter", [285, 286, 287], [
    {"t": "p", "x": "Protective actions are the steps taken to preserve the health and safety of emergency responders and the public during an incident involving releases of hazardous materials/dangerous goods."},
    {"t": "p", "x": "Table 1 - Initial Isolation and Protective Action Distances (green section) predicts the size of the area that could be affected by a cloud of toxic gas. People in this area should be evacuated and/or sheltered-in-place inside buildings."},
    {"t": "p", "x": "<b>Isolate hazard area and deny entry</b> means to keep everybody away from the area if they are not directly involved in emergency response operations. Unprotected emergency responders should not be allowed to enter the isolation zone."},
    {"t": "p", "x": "This \"isolation\" task is done to establish control over the area of operations. This is the first step for any protective actions that may follow."},
    {"t": "p", "x": "<b>Evacuate</b> means to move all people from a threatened area to a safer place. To perform an evacuation, there must be enough time for people to be warned, get ready, and leave an area. If there is enough time, evacuation is the best protective action."},
    {"t": "p", "x": "Begin evacuating people nearby and those who are outdoors in direct view of the scene. When additional help arrives, expand the area to be evacuated downwind and crosswind to at least the extent recommended in this guidebook."},
    {"t": "p", "x": "Even after people move to the distances recommended, they may not be completely safe from harm. They should not be permitted to gather at such distances. Send evacuees to a definite place, by a specific route, far enough away so they will not have to relocate again if the wind shifts."},
    {"t": "p", "x": "<b>Shelter-in-place</b> means people should seek shelter inside a building and remain inside until the danger passes. It is vital for first responders to maintain communications with sheltered-in-place people so that they are advised about changing conditions."},
    {"t": "p", "x": "Sheltering-in-place is used either when:"},
    {"t": "b", "x": "evacuating the public would cause greater risk than staying where they are"},
    {"t": "b", "x": "an evacuation cannot be safely performed"},
    {"t": "p", "x": "Direct the people inside to:"},
    {"t": "b", "x": "close all doors and windows"},
    {"t": "b", "x": "shut off all ventilating, heating and cooling systems"},
    {"t": "b", "x": "stay far from windows to avoid shattered glass and projectile metal fragments in the event of a fire and/or explosion"},
    {"t": "b", "x": "seal cracks around doors, windows and vents with duct tape or wet cloths"},
    {"t": "b", "x": "tune in to local media, and remain inside until told it is safe to leave by first responders or emergency response authorities"},
    {"t": "b", "x": "breathe through a wet cloth until an all clear has been communicated"},
    {"t": "p", "x": "Vehicles can offer some protection for a short period if the windows are closed and the ventilation systems are shut off. Vehicles are not nearly as effective as buildings for in-place protection."},
    {"t": "h", "x": "PROTECTIVE ACTION DECISION FACTORS TO CONSIDER"},
    {"t": "p", "x": "The choice of protective actions for a given situation depends on a number of factors. For some cases, evacuation may be the best option; in others, sheltering-in-place may be the best course. Sometimes, these two actions may be used in combination. In any emergency, officials need to quickly give the public instructions. The public will need continuing information and instructions while being evacuated or sheltered-in-place."},
    {"t": "p", "x": "<b>The hazardous materials/dangerous goods:</b>"},
    {"t": "b", "x": "degree of health hazard"}, {"t": "b", "x": "chemical and physical properties"},
    {"t": "b", "x": "amount involved"}, {"t": "b", "x": "containment/control of release"},
    {"t": "b", "x": "rate of vapor movement"},
    {"t": "p", "x": "<b>The population threatened:</b>"},
    {"t": "b", "x": "location"}, {"t": "b", "x": "number of people"},
    {"t": "b", "x": "time available to evacuate or shelter-in-place"},
    {"t": "b", "x": "ability to control evacuation or shelter-in-place"},
    {"t": "b", "x": "building types and availability"},
    {"t": "b", "x": "special institutions or populations, e.g., nursing homes, hospitals, prisons"},
    {"t": "p", "x": "<b>The weather conditions:</b>"},
    {"t": "b", "x": "effect on vapor and cloud movement"}, {"t": "b", "x": "potential for change"},
    {"t": "b", "x": "effect on evacuation or shelter-in-place"},
    {"t": "note", "x": "<b>NOTE:</b> Every hazardous materials/dangerous goods incident is different. Each will have special problems and concerns. Actions to protect the public must be carefully selected. This section can help with initial decisions on how to protect the public. Officials must continue to gather information and monitor the situation until the threat is removed."},
    {"t": "p", "x": "The following table can help to decide if evacuation or sheltering-in-place is the best option:"},
    {"t": "table", "cols": ["Consider Evacuation:", "Consider Sheltering-in-place:"], "rows": [
        ["Vapors are flammable.", "Vapors are toxic, and people are likely to be exposed by evacuating."],
        ["Buildings cannot be closed tightly.", "Buildings can be quickly sealed by closing all windows and ventilation systems, if applicable."],
        ["The vapors are continuously generated and will hug the ground, or it will take a long time for the vapors to clear the area.", "The vapors will quickly rise in the air column or rapidly dissipate."],
        ["For anyone outdoors.", "For anyone already indoors."],
        ["There are few people to evacuate.", "There are too many people to evacuate for current available resources."],
        ["The threat seems stable but long-lasting.", "Circumstances are changing too quickly to evacuate safely."],
    ]},
])

# ---------------------------------------------------------------- pp. 288-289
page("how_to_use_table1", "How to use Table 1 (zones)", [290, 291], [
    {"t": "num", "n": "(1)", "x": "The responder should already have:"},
    {"t": "b", "x": "identified the material by its ID number and name (if you cannot find an ID number, use the Name of Material index in the blue section to find that number);"},
    {"t": "b", "x": "confirmed that the material is highlighted in green in the yellow or blue section. If not, Table 1 doesn’t apply;"},
    {"t": "b", "x": "found the three-digit guide for the material, in order to consult emergency actions it recommends along with this table; and"},
    {"t": "b", "x": "noted the wind direction"},
    {"t": "num", "n": "(2)", "x": "Look in Table 1 (green section) for the ID number and name of the material involved. Some ID numbers have more than one shipping name listed. Look for the specific name of the material. If you do not know the shipping name and Table 1 lists more than one name for the same ID number, use the entry with the largest distances."},
    {"t": "num", "n": "(3)", "x": "Determine if the incident involves a SMALL or LARGE spill and if it is DAY or NIGHT. A SMALL SPILL consists of a release of 208 liters (55 US gallons) or less. This generally corresponds to a spill from a single small package (for example, a drum), a small cylinder, or a small leak from a large package. A LARGE SPILL consists of a release of more than 208 liters (55 US gallons). This usually involves a spill from a large package, or multiple spills from many small packages. DAY is any time after sunrise and before sunset. NIGHT is any time between sunset and sunrise."},
    {"t": "num", "n": "(4)", "x": "Look up the INITIAL ISOLATION DISTANCE. This distance defines the radius of a zone (initial isolation zone) surrounding the spill in ALL DIRECTIONS. In this zone, protective clothing and respiratory protection is required. Evacuate the general public in a direction perpendicular to wind direction (crosswind) and away from the spill."},
    {"t": "num", "n": "(5)", "x": "Look up the PROTECTIVE ACTION DISTANCE. For a given material, spill size, and whether day or night, Table 1 gives the downwind distance—in kilometers and miles—from the spill or leak source, for which you should consider protective actions. For practical purposes, the protective action zone (i.e., the area in which people are at risk of harmful exposure) is a square. Its length and width are the same as the downwind distance shown in Table 1. Protective actions are the steps you take to preserve the health and safety of emergency responders and the public. People in this area should be evacuated and/or sheltered-in-place. For more information, consult the “Protective Actions” section.",
     "verify": ["Look up the PROTECTIVE ACTION DISTANCE. For a given material, spill size, and whether day or night, Table 1 gives the downwind distance—in kilometers and miles—from the spill or leak source, for which you should consider protective actions. For practical purposes, the protective action zone (i.e., the area in which people are at risk of harmful exposure) is a square. Its length and width are the same as the downwind distance shown in Table 1. Protective actions are the steps you take to preserve the health and safety of emergency responders and",
                "the public. People in this area should be evacuated and/or sheltered-in-place. For more information, consult the “Protective Actions” section."]},
    {"t": "num", "n": "(6)", "x": "Initiate protective actions beginning with those closest to the spill site and working away in a downwind direction. When a water-reactive TIH (PIH in the US) producing material is spilled into a river or stream, the source of the toxic gas may move with the current or stretch from the spill point downstream for a large distance."},
    {"t": "p", "x": "In the figure below, the spill is located at the center of the small black circle. The larger circle represents the initial isolation zone around the spill. The square (the protective action zone) is the area in which you should take protective actions."},
    {"t": "figure", "kind": "paz", "labels": ["Wind Direction", "UPWIND", "DOWNWIND", "Protective Action Zone", "Initial Isolation Zone", "Downwind Distance", "1/2 Downwind Distance", "Crosswind Direction(s)", "Spill"]},
    {"t": "note", "x": "<b>Note 1:</b> For factors that may change the protective action distances, see the \"Introduction to Green Tables\" section."},
    {"t": "note", "x": "<b>Note 2:</b> When a product in Table 1 has the mention (when spilled in water), you can refer to Table 2 for the list of gases produced when these materials are spilled in water. The TIH gases indicated in Table 2 are for information purposes only."},
    {"t": "note", "x": "<b>Note 3:</b> For the instantaneous release of the entire contents of a package (e.g., as a result of terrorism, sabotage or catastrophic failure), the distances should be doubled."},
    {"t": "p", "x": "For more information on the material, safety precautions and mitigation procedures, call the emergency response telephone number listed on the shipping paper or the appropriate response agency as soon as possible."},
    {"t": "link", "action": "TABLE3", "label": "Open Table 3 (all six gases)"},
])

# ---------------------------------------------------------------- p. 7
page("placard_intro", "Using the placards", [9], [
    {"t": "h", "x": "INTRODUCTION TO THE TABLE OF MARKINGS, LABELS AND PLACARDS"},
    {"t": "warn", "x": "<b>USE THIS TABLE ONLY WHEN THE ID NUMBER OR PROPER SHIPPING NAME IS NOT AVAILABLE.</b>"},
    {"t": "p", "x": "This section displays the placards used on transport vehicles carrying hazardous materials/dangerous goods with the applicable reference GUIDE circled. Follow these steps:"},
    {"t": "num", "n": "1.", "x": "Approach scene from upwind, uphill and/or upstream at a safe distance to safely identify and/or read the placard or orange panel. Use binoculars if available."},
    {"t": "num", "n": "2.", "x": "Match the vehicle placard(s) with one of the placards displayed in this section."},
    {"t": "num", "n": "3.", "x": "Consult the circled guide number associated with the placard. Use that guide information for now. For example:"},
    {"t": "b", "x": "Use GUIDE 127 for a FLAMMABLE (Class 3) placard"},
    {"t": "b", "x": "Use GUIDE 153 for a CORROSIVE (Class 8) placard"},
    {"t": "b", "x": "Use GUIDE 111 when the DANGER or DANGEROUS placard is displayed or the nature of the spilled, leaking or burning material is not known. Also use this GUIDE when the presence of hazardous materials/dangerous goods is suspected but no placards can be seen."},
    {"t": "p", "x": "If multiple placards point to more than one guide, initially use the most conservative guide (i.e., the guide requiring the greatest degree of protective actions)."},
    {"t": "num", "n": "4.", "x": "Guides associated with the placards provide the most significant risk and/or hazard information."},
    {"t": "num", "n": "5.", "x": "When specific information, such as ID number or proper shipping name, becomes available, the more specific Guide recommended for that material must be consulted."},
    {"t": "num", "n": "6.", "x": "A single asterisk (*) on orange placards represents an explosive's compatibility group letter. The asterisk must be replaced with the appropriate compatibility group letter. Refer to the “Glossary” section."},
    {"t": "num", "n": "7.", "x": "Double asterisks (**) on orange placards represent the division of the explosive. The double asterisks must be replaced with the appropriate division number."},
    {"t": "link", "action": "PLACARD", "label": "Pick a placard"},
])

# ---------------------------------------------------------------- pp. 286-287
page("table1_background", "Where the Table 1 distances come from", [288, 289], [
    {"t": "h", "x": "BACKGROUND ON TABLE 1 – INITIAL ISOLATION AND PROTECTIVE ACTION DISTANCES"},
    {"t": "p", "x": "Initial isolation and protective action distances in this guidebook were determined for small and large spills occurring during day or night. The overall analysis, statistical in nature, was conducted using:"},
    {"t": "b", "x": "state-of-the-art emission rate and dispersion models"},
    {"t": "b", "x": "statistical release data from the U.S. Department of Transportation (DOT) Hazardous Materials Information System (HMIS) database"},
    {"t": "b", "x": "meteorological observations from more than 120 locations in the United States, Canada, and Mexico"},
    {"t": "b", "x": "the most current toxicological exposure guidelines"},
    {"t": "p", "x": "For each chemical, thousands of hypothetical releases were modeled to account for the statistical variance in both release amount and atmospheric conditions. Based on this statistical sample, they selected the 90th percentile protective action distance for each chemical and category to appear in the table. A brief description of the analysis is provided below."},
    {"t": "p", "x": "A detailed report outlining the methodology and data used to generate the initial isolation and protective action distances may be obtained from the U.S. DOT, Pipeline and Hazardous Materials Safety Administration (PHMSA)."},
    {"t": "h", "x": "DESCRIPTION OF THE ANALYSIS"},
    {"t": "p", "x": "Release amounts and emission rates into the atmosphere were statistically modeled based on:"},
    {"t": "b", "x": "data from the U.S. DOT HMIS database"},
    {"t": "b", "x": "container types and sizes authorized for transport as specified in 49 CFR §172.101 and Part 173"},
    {"t": "b", "x": "physical properties of the individual materials"},
    {"t": "b", "x": "atmospheric data from a historical database"},
    {"t": "p", "x": "For liquefied gases, which can flash to form both a vapor/aerosol mixture and an evaporating pool, the emission model calculated one or both of:"},
    {"t": "b", "x": "the release of vapor due to evaporation of pools on the ground"},
    {"t": "b", "x": "direct release of vapors from the container"},
    {"t": "p", "x": "The emission model also calculated the emission of toxic vapor by-products generated from spilling water-reactive materials in water."},
    {"t": "p", "x": "Small spills involve 208 liters (55 US gallons) or less."},
    {"t": "p", "x": "Large spills involve greater quantities."},
    {"t": "p", "x": "Downwind dispersion of the vapor was estimated for each case modeled. Using a database containing hourly meteorological data from 120 American, Canadian, and Mexican cities, the atmospheric parameters affecting the dispersion and the emission rate were selected."},
    {"t": "p", "x": "The dispersion calculation accounted for both the:"},
    {"t": "b", "x": "time-dependent emission rate from the source"},
    {"t": "b", "x": "density of the vapor plume (i.e., heavy gas effects)"},
    {"t": "p", "x": "Since atmospheric mixing is less effective at dispersing vapor plumes during nighttime, day and night were separated in the analysis."},
    {"t": "p", "x": "In the table:"},
    {"t": "b", "x": "day refers to time periods after sunrise and before sunset"},
    {"t": "b", "x": "night includes all hours between sunset and sunrise"},
    {"t": "p", "x": "Toxicological short-term exposure guidelines for the materials were applied to determine the downwind distance to which people may:"},
    {"t": "b", "x": "become incapacitated and unable to take protective action"},
    {"t": "b", "x": "incur serious health effects after a single, or rare, exposure"},
    {"t": "p", "x": "When available, toxicological exposure guidelines were chosen from AEGL-2 or ERPG-2 emergency response guidelines. AEGL-2 values were the first choice."},
    {"t": "p", "x": "For materials without AEGL-2 or ERPG-2 values, emergency response guidelines were estimated based on lethal concentration limits derived from animal-based-studies. This approach was recommended by an independent panel of toxicological experts from industry and academia."},
])

# Not verified (plugin wording): box "label", "link" blocks, num "n".
PLUGIN_KEYS = {"label", "action", "n", "color", "t", "dark", "phones", "kind", "verify", "image"}
