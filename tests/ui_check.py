"""Browser regression checks for the packaged UI; device APIs are explicit mocks."""
from pathlib import Path
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from threading import Thread
from functools import partial
import re, sys
from playwright.sync_api import sync_playwright

root = Path(__file__).resolve().parents[1]
assets = root / 'app/src/main/assets'
output = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('work/ui-preview')
output.mkdir(parents=True, exist_ok=True)
main = (root / 'app/src/main/java/com/youlong/hd/MainActivity.java').read_text(encoding='utf-8')
native = (root / 'app/src/main/java/com/youlong/hd/ScreenFilterBridge.java').read_text(encoding='utf-8')
html = (assets / 'index.html').read_text(encoding='utf-8')
for method in re.findall(r'data-(?:call|result)="([^"]+)"', html):
    assert re.search(r'@JavascriptInterface\s+public\s+\w+\s+' + method + r'\(', main), method
for method in re.findall(r'data-native="([^"]+)"', html):
    assert re.search(r'@JavascriptInterface\s+public\s+\w+\s+' + method + r'\(', native), method
assert 'script-src &#39;unsafe-inline&#39;' not in html
assert 'frame-src \'none\'' in html and 'connect-src \'none\'' in html

class Handler(SimpleHTTPRequestHandler):
    def log_message(self, *args): pass
server = ThreadingHTTPServer(('127.0.0.1', 0), partial(Handler, directory=str(assets)))
Thread(target=server.serve_forever, daemon=True).start()
mock = r'''
window.calls = [];
const data = {
 getProtectStatus: JSON.stringify({on:false,duration:'0m'}),
 getMissingPermissions: JSON.stringify({accessibility:false,overlay:false,usage:false}),
 getEntryRequirements: JSON.stringify({notification:true,applist:true}),
 getActivationStatus: JSON.stringify({ready:false,source:0,externalAllowed:false,version:'ClearGuard 1.0.0',summary:'特权服务尚未连接'}),
 getInstalledApps: JSON.stringify([{name:'<img src=x onerror="window.pwned=1">',pkg:'test.hostile'},{name:'相机',pkg:'test.camera'}]),
 getShieldMode:'basic',getVolumeTriggerKey:'down',getVolumeTriggerCount:3,getShakeSensitivity:5,
 getAdSkipStats:'{}',getAvailableMemory:'3.2 GB',getBatteryTemp:30,getCurrentColorMode:'标准',
 getDiagnostics:'设备诊断示例', getBrightness:100,getContrast:100,getSaturation:100,getTemperature:50
};
function api(){return new Proxy({}, {get:(_,method)=>(...args)=>{window.calls.push([method,args]);return data[method]??false;}});}
window.Android=api();window.AndroidNative=api();
'''
try:
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page(viewport={'width':390,'height':844}, device_scale_factor=2)
        page.add_init_script(mock)
        errors=[]
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.on('dialog', lambda dialog: dialog.accept())
        page.goto(f'http://127.0.0.1:{server.server_port}/index.html')
        page.locator('#overview.active').wait_for()
        assert page.locator('#protection-pill').inner_text() == '守护尚未开启'
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
        page.screenshot(path=str(output/'clearguard-home.png'), full_page=True)
        for tab in ['protect','apps','tools','settings','overview']:
            page.locator(f'.bottom-nav [data-tab="{tab}"]').click()
            assert page.locator(f'#{tab}').is_visible()
        page.locator('.bottom-nav [data-tab="apps"]').click()
        assert page.locator('#app-list .app-row').count()==2
        assert page.locator('#app-list img').count()==0
        assert not page.evaluate('!!window.pwned')
        page.locator('#app-search').fill('camera')
        assert page.locator('#app-list .app-row').count()==1
        page.locator('#app-search').fill('')
        page.locator('#app-list .app-row').last.locator('button').first.click()
        assert page.evaluate("calls.some(x=>x[0]==='openAppInfo'&&x[1][0]==='test.camera')")
        page.locator('.bottom-nav [data-tab="tools"]').click()
        page.locator('#tools [data-action="terminal"]').click()
        assert page.evaluate("calls.some(x=>x[0]==='openTerminal')")
        page.locator('.bottom-nav [data-tab="settings"]').click()
        page.locator('#settings [data-action="activate"]').click()
        assert page.evaluate("calls.some(x=>x[0]==='openActivation')")
        page.locator('[data-result="getDiagnostics"]').click()
        page.locator('#result-dialog').wait_for(state='visible')
        assert page.locator('#result-body').inner_text()=='设备诊断示例'
        page.locator('[data-action="close-dialog"]').click()
        page.locator('#appearance').select_option('dark')
        assert page.locator('html').get_attribute('data-theme')=='dark'
        page.screenshot(path=str(output/'clearguard-settings.png'),full_page=True)
        assert not errors, errors
        browser.close()
finally:
    server.shutdown()
print('PASS five-page navigation, native routes, app search, hostile-label escaping, diagnostics and appearance')
