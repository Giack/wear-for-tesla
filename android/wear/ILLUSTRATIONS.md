# Car illustrations

The car drawings in `src/main/res/drawable/car_*` are original, stylised artwork made for this project and
released under the project's licence (Apache-2.0). They are not derived from Tesla's images or 3D models.

| Model   | Three-quarter front (home screen)        | Side (Tile)                                  |
|---------|------------------------------------------|----------------------------------------------|
| Model Y | `car_y_3q_paint`, `car_y_3q_details`     | `car_y_side_paint`, `car_y_side_details`     |
| Model 3 | `car_3_3q_paint`, `car_3_3q_details`     | `car_3_side_paint`, `car_3_side_details`     |

Each illustration has two layers on the same 200×100 grid:

- `*_paint.xml`: the body. White areas become the car's paint colour and grey areas a shaded version of it
  (the app multiplies the layer by the paint colour); black stays black (ground shadow).
- `*_details.xml`: glass, lights and wheels, drawn on top in fixed colours.

The Model 3 shares the Model Y's wheels and lower body line, with a lower roof and hood and a short rear deck,
so the two read as the same family. Both views of a model use the same wheel size.

To add a model or a view, keep the same grid and layer split, map it in `IllustrationLayers`
(`render/IllustratedCarImages.kt`) and extend `IllustrationLayersTest`. Preview a drawing in any paint with
`python3 tools/preview_illustration.py <paint.xml> <details.xml> "#RRGGBB" out.svg`.
