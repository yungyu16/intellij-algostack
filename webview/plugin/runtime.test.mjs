import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {test} from 'node:test';
import vm from 'node:vm';
import ts from 'typescript';

// 执行真实运行时源码；只替换 DOM、CSS 和滚动条库，不模拟 JCEF 渲染。
function loadRuntime({container = {style: {zoom: ''}}, callback = false} = {}) {
    const listeners = new Map();
    const messages = [];
    const body = {};
    let scrollbarTarget;
    const window = {
        addEventListener: (name, handler) => listeners.set(name, handler),
        removeEventListener: name => listeners.delete(name),
        ...(callback ? {sendInfo: json => messages.push(JSON.parse(json))} : {}),
    };
    const source = readFileSync(new URL('./runtime.ts', import.meta.url), 'utf8');
    const {outputText} = ts.transpileModule(source, {
        compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022},
    });
    vm.runInNewContext(outputText, {
        exports: {}, window,
        document: {body, getElementById: id => id === 'container' ? container : null},
        require: name => {
            if (name.endsWith('.css')) return {};
            assert.equal(name, 'overlayscrollbars');
            return {OverlayScrollbars: target => { scrollbarTarget = target; }};
        },
    });
    return {window, container, listeners, messages, body, get scrollbarTarget() { return scrollbarTarget; }};
}

test('共享运行时公开缩放入口，限制范围并支持恢复原比例', () => {
    const runtime = loadRuntime();
    const {window, container, listeners} = runtime;
    assert.equal(runtime.scrollbarTarget, runtime.body);
    listeners.get('load')();
    assert.equal(listeners.has('load'), false);
    window.setZoom(1000);
    assert.equal(container.style.zoom, '500%');
    window.setZoom(1);
    assert.equal(container.style.zoom, '25%');
    window.actualZoom();
    assert.equal(container.style.zoom, '');
    window.zoomIn();
    assert.equal(container.style.zoom, '120%');
    window.zoomOut();
    assert.equal(container.style.zoom, '');
});

test('可选宿主回调收到与缩放范围一致的状态', () => {
    const {window, messages} = loadRuntime({callback: true});
    window.setZoom(500);
    assert.deepEqual(messages.at(-1), {zoom: 500, canZoomIn: false, canZoomOut: true});
});

test('模板缺失容器时报告明确错误', () => {
    const {window} = loadRuntime({container: null});
    assert.throws(() => window.setZoom(100), /missing #container/);
});
