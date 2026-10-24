# 09: Remaining Car illustrations

**What to build:** Add the Model 3 illustration and the side view for both models, in the same style and layer structure, so every supported model and view has a tinted Car illustration.

**Blocked by:** 04

**Status:** done

- [x] Image provider returns the right illustration for Model 3/Model Y × three-quarter/side; tested
- [x] Visually checked on the watch for each combination
- [x] Sources and licence committed

## Comments

- 2026-10-23: Three new drawings on the Model Y's grid and layer split: Model 3 three-quarter (lower roof and hood, short rear deck, same wheels and lower line, so the two read as one family), and side views of both models. `IllustrationView` gains `SIDE`; `IllustrationLayers` maps model × view to the paint and details drawables, tested for all four combinations and that no two share a drawing. The home screen keeps the three-quarter view and the Tile now draws the side view, which fills its wide, short slot better. Tile title and the home fallback line now follow the profile's model ("Model 3"), which was fixed to "Model Y". `ILLUSTRATIONS.md` lists the drawings; `tools/preview_illustration.py` previews any drawing in any paint as SVG (Apache-2.0, like the drawings). Seen on the watch (Personal build, Official renders hidden for the check, then restored): Model Y and Model 3 on the home screen (three-quarter) and on the Tile (side). In Fleet mode the car's real configuration replaces a manually picked model at the next cloud read, as the spec requires, so a manual Model 3 on a Fleet-linked Model Y only lasts until then.

