/**
 * Assets integrity check for the WebView front-end.
 *
 * Run with: node tests/assets_check.js
 * Exits non-zero on any problem.
 */
const fs = require('fs');
const path = require('path');
const os = require('os');
const { execFileSync } = require('child_process');

const ROOT = path.join(__dirname, '..');
const HTML_PATH = path.join(ROOT, 'app/src/main/assets/index.html');
const ASSETS_DIR = path.join(ROOT, 'app/src/main/assets');

let failures = 0;

function ok(name) {
    console.log('PASS ' + name);
}

function skip(name, reason) {
    console.log('SKIP ' + name + (reason ? ' — ' + reason : ''));
}
function bad(name, detail) {
    console.log('FAIL ' + name + (detail ? ' — ' + detail : ''));
    failures++;
}

// ---------------------------------------------------------------------------
// 1. required shell assets exist and are POSIX-sh with LF line endings
// ---------------------------------------------------------------------------
const requiredSh = [
    'uninstall_helpers.sh',
];
for (const name of requiredSh) {
    const p = path.join(ASSETS_DIR, name);
    if (!fs.existsSync(p)) {
        bad('asset-exists:' + name, 'file missing');
        continue;
    }
    const content = fs.readFileSync(p, 'utf8');
    if (content.includes('\r\n')) {
        bad('asset-lf:' + name, 'contains CRLF line endings');
    } else {
        ok('asset-lf:' + name);
    }
    if (!content.startsWith('#!/system/bin/sh')) {
        bad('asset-shebang:' + name, 'missing #!/system/bin/sh');
    } else {
        ok('asset-shebang:' + name);
    }
}

// ---------------------------------------------------------------------------
// 2. index.html exists and is non-trivial
// ---------------------------------------------------------------------------
// NOTE (2026-10): the WebView front-end (assets/index.html) is intentionally NOT
// part of this repository, so a missing file is not a failure. When the file IS
// present (e.g. a fork that added its own front-end) all of its checks still run.
const HTML_PRESENT = fs.existsSync(HTML_PATH);
if (HTML_PRESENT) {
    const sz = fs.statSync(HTML_PATH).size;
    if (sz > 1024) {
        ok('index.html size (' + sz + ' bytes)');
    } else {
        bad('index.html size', 'suspiciously small: ' + sz + ' bytes');
    }
} else {
    skip('index.html', 'front-end HTML is not shipped in this repository');
}

// ---------------------------------------------------------------------------
// 3. index.html: div balance
// ---------------------------------------------------------------------------
if (HTML_PRESENT) {
const html = fs.readFileSync(HTML_PATH, 'utf8');
const divOpens = (html.match(/<div\b/g) || []).length;
const divCloses = (html.match(/<\/div>/g) || []).length;
if (divOpens === divCloses) {
    ok('html-div-balance (' + divOpens + ' divs)');
} else {
    bad('html-div-balance', divOpens + ' open vs ' + divCloses + ' close');
}

// ---------------------------------------------------------------------------
// 4. index.html: every <script> block parses as JavaScript
// ---------------------------------------------------------------------------
const inlineBlocks = [];
const scriptRe = /<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/g;
let m;
while ((m = scriptRe.exec(html)) !== null) {
    if (m[1].trim().length > 0) inlineBlocks.push(m[1]);
}
for (const match of html.matchAll(/<script[^>]*\bsrc="([^"]+)"[^>]*>/g)) {
    const file = path.resolve(ASSETS_DIR, match[1]);
    if (!file.startsWith(ASSETS_DIR + path.sep) || !fs.existsSync(file)) {
        bad('js-source', match[1]);
    } else {
        inlineBlocks.push(fs.readFileSync(file, 'utf8'));
    }
}
if (inlineBlocks.length === 0) {
    bad('js-blocks', 'no script sources found');
}
inlineBlocks.forEach((block, i) => {
    const tmp = path.join(os.tmpdir(), 'assets_check_' + i + '.js');
    fs.writeFileSync(tmp, block, 'utf8');
    try {
        execFileSync('node', ['--check', tmp], { stdio: 'pipe' });
        ok('js-syntax:block#' + i);
    } catch (e) {
        bad('js-syntax:block#' + i, String(e.stderr).slice(0, 300));
    } finally {
        fs.unlinkSync(tmp);
    }
});

// ---------------------------------------------------------------------------
// 5. every element id referenced through $('...') exists (static ones only)
// ---------------------------------------------------------------------------
const dynamicIds = new Set([
    // ids created at runtime inside modal/confirm overlays
    'cfmOk', 'cfmCancel', 'dgrOk', 'dgrCancel', 'modalGo', 'modalCancel',
    'tplCancel', 'privGo', 'privCancel', 'edSave', 'edCancel',
]);
const definedIds = new Set();
for (const d of html.matchAll(/\sid="([^"]+)"/g)) definedIds.add(d[1]);
const referencedIds = new Set();
for (const block of inlineBlocks) {
    for (const r of block.matchAll(/\$\('([A-Za-z0-9_-]+)'\)/g)) {
        referencedIds.add(r[1]);
    }
}
const missing = [...referencedIds].filter((id) => !definedIds.has(id) && !dynamicIds.has(id));
if (missing.length === 0) {
    ok('js-dom-ids (' + referencedIds.size + ' checked)');
} else {
    bad('js-dom-ids', 'missing ids: ' + missing.join(', '));
}

} // end of front-end HTML checks

// ---------------------------------------------------------------------------
// 6. site-lists: plain-text domain lists must exist and be non-empty
//    (SiteFilterService reads site-lists/<category>.txt at runtime)
// ---------------------------------------------------------------------------
const siteListsDir = path.join(ASSETS_DIR, 'site-lists');
const requiredSiteLists = ['threat.txt', 'adult.txt', 'gambling.txt'];
for (const name of requiredSiteLists) {
    const p = path.join(siteListsDir, name);
    if (!fs.existsSync(p)) {
        bad('site-list-exists:' + name, 'file missing');
        continue;
    }
    const sz = fs.statSync(p).size;
    if (sz < 1000) {
        bad('site-list-size:' + name, 'suspiciously small: ' + sz + ' bytes');
    } else {
        ok('site-list-size:' + name + ' (' + sz + ' bytes)');
    }
    // make sure no stale .gz companion lingers
    const gz = p + '.gz';
    if (fs.existsSync(gz)) {
        bad('site-list-stale-gz:' + name, '.txt.gz still present next to .txt');
    }
}

// ---------------------------------------------------------------------------
// 7. virus bloom filter must exist and be non-trivial
// ---------------------------------------------------------------------------
const bloomPath = path.join(ASSETS_DIR, 'virus-bloom.bin');
if (!fs.existsSync(bloomPath)) {
    bad('virus-bloom', 'virus-bloom.bin missing from assets');
} else {
    const bloomSz = fs.statSync(bloomPath).size;
    if (bloomSz < 1000000) {
        bad('virus-bloom-size', 'suspiciously small: ' + bloomSz + ' bytes');
    } else {
        ok('virus-bloom-size (' + bloomSz + ' bytes)');
    }
}

// ---------------------------------------------------------------------------
console.log('');
if (failures === 0) {
    console.log('assets check: ALL PASS');
    process.exit(0);
}
console.log('assets check: ' + failures + ' FAILURES');
process.exit(1);
