MathJax 3.2.2 (tex-svg-full.js)
Upstream: https://github.com/mathjax/MathJax/tree/3.2.2
Distribution: https://registry.npmjs.org/mathjax/-/mathjax-3.2.2.tgz
License: Apache-2.0, see LICENSE in this directory.
The distributed JavaScript is unmodified. renderer.html is this project's integration.

The 3.2 SVG bundle includes its font outlines and TeX extensions in one offline file.
No CDN, external font, runtime package download, or JavaScript interface to Android is used.
The renderer creates an isolated TeX parser for each expression. It returns SVG, which
AndroidSVG renders into a cached native Picture. The WebView is never attached to a window.
If the system WebView is unavailable or an expression is unsupported, source text remains visible.

Enabled packages: base, ams, newcommand, configmacros, boldsymbol, braket, cancel,
cases, centernot, color, extpfeil, mathtools, mhchem, physics, unicode, verb, amscd,
bbox, bussproofs, colortbl, empheq, enclose, gensymb, textcomp, textmacros, upgreek.
This is a math renderer, not a complete TeX engine; TikZ/PGFPlots, arbitrary LaTeX
packages, full siunitx, and full-document compilation are not supported.

AndroidSVG 1.4: https://bigbadaboom.github.io/androidsvg/ (Apache-2.0).
