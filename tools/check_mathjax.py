from pathlib import Path
import json
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1] / 'app/src/main/assets/mathjax'
cases = {
    'chemistry': r'\ce{2H2 + O2 -> 2H2O}',
    'chemical_units': r'\pu{1.2e3 J mol-1}',
    'physics': r'\pdv[2]{f}{x} + \dv{y}{t} + \qty(\frac{1}{x})',
    'braket': r'\bra{\psi}\ket{\phi}',
    'align': r'\begin{align}x&=1\\y&=2\end{align}',
    'cases': r'f(x)=\begin{cases}x^2&x>0\\0&x\leq0\end{cases}',
    'cancel': r'\frac{\cancel{x}y}{\cancel{x}}=y',
    'mathtools': r'\mathclap{abcdefgh}\xrightarrow{n\to\infty}0',
    'unicode': r'\text{速度}=\frac{\text{路程}}{\text{时间}}',
    'custom_macro': r'{\newcommand{\myfoo}{x^2}\myfoo+1}',
    'long_matrix': '\\begin{bmatrix}' + '\\\\'.join(['&'.join(str(i) for i in range(15))]*20) + '\\end{bmatrix}',
    'require_bundled': r'\require{mhchem}\ce{H2SO4}',
    'commutative_diagram': r'\begin{CD}A @>f>> B\\ @VgVV @VVhV\\ C @>>k> D\end{CD}',
    'enclose': r'\enclose{circle}{x+y}',
    'tag': r'\frac{1}{2}\tag{1}',
}
with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page()
    errors=[]
    unexpected=[]
    page.on('pageerror', lambda error: errors.append(str(error)))
    def route(request):
        name = request.request.url.removeprefix('https://math.zuoti.local/') or 'renderer.html'
        if name in ['renderer.html','tex-svg-full.js']:
            request.fulfill(path=str(ROOT/name))
        else:
            unexpected.append(request.request.url)
            request.abort()
    page.route('**/*', route)
    page.goto('https://math.zuoti.local/renderer.html')
    page.wait_for_function('window.MathJax && MathJax.startup && MathJax.startup.document')
    results={}
    for name,source in cases.items():
        result=page.evaluate('(s) => JSON.parse(renderFormula(s, 32, "#171717", true))',source)
        assert 'error' not in result, (name,result)
        assert result['width']>0 and result['height']>0
        assert '<path' in result['svg'] or '<text' in result['svg']

        results[name]={'width':result['width'],'height':result['height'],'svg_bytes':len(result['svg'])}
    defined=page.evaluate('(s)=>JSON.parse(renderFormula(s,32,"#000000",true))',r'\newcommand{\isolatedfoo}{x}\isolatedfoo')
    assert 'error' not in defined, defined
    after=page.evaluate('(s)=>JSON.parse(renderFormula(s,32,"#000000",true))',r'\isolatedfoo')
    assert 'error' in after, 'A macro leaked into the next formula'
    recursive=page.evaluate('(s)=>JSON.parse(renderFormula(s,32,"#000000",true))',r'\def\loop{\loop}\loop')
    assert 'error' in recursive, 'Recursive macro was not bounded'
    unknown=page.evaluate('JSON.parse(renderFormula("\\\\notARealCommand{x}",32,"#000000",true))')
    assert 'error' in unknown, unknown
    assert not errors,errors
    assert not unexpected,unexpected
    browser.close()
    print(json.dumps(results,indent=2))
    print(f'{len(results)} extended formula cases passed; macro isolation and invalid/recursive formula fallback passed; no network requests.')
