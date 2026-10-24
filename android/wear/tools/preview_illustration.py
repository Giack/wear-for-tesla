"""Preview a Car illustration as SVG, with the paint layer multiplied by a colour as the app does.

Usage: python3 tools/preview_illustration.py src/main/res/drawable/car_y_3q_paint.xml \
           src/main/res/drawable/car_y_3q_details.xml "#B3141C" preview.svg
Open the SVG in a browser. Part of Wear for Tesla, Apache-2.0.
"""
import sys, re, xml.etree.ElementTree as ET
A = '{http://schemas.android.com/apk/res/android}'
def mul(hexc, paint):
    c = [int(hexc[i:i+2], 16) for i in (1, 3, 5)]; p = [int(paint[i:i+2], 16) for i in (1, 3, 5)]
    return '#%02x%02x%02x' % tuple(a * b // 255 for a, b in zip(c, p))
def paths(f, paint=None):
    out = []
    for p in ET.parse(f).getroot().iter('path'):
        g = lambda k, d=None: p.get(A + k, d)
        fill = g('fillColor'); stroke = g('strokeColor')
        if paint and fill: fill = mul(fill, paint)
        out.append('<path d="%s" fill="%s" fill-opacity="%s" stroke="%s" stroke-opacity="%s" stroke-width="%s" stroke-linecap="%s"/>' % (
            g('pathData'), fill or 'none', g('fillAlpha', '1'), stroke or 'none', g('strokeAlpha', '1'), g('strokeWidth', '0'), g('strokeLineCap', 'butt')))
    return '\n'.join(out)
paint_xml, details_xml, colour, out = sys.argv[1:5]
open(out, 'w').write('<svg xmlns="http://www.w3.org/2000/svg" width="400" height="200" viewBox="0 0 200 100"><rect width="200" height="100" fill="#000"/>%s\n%s</svg>' % (paths(paint_xml, colour), paths(details_xml)))
