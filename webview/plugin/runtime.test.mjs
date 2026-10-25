import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {test} from 'node:test';
import vm from 'node:vm';
import ts from 'typescript';

// 执行真实运行时源码；只替换 DOM、CSS 和滚动条库，不模拟 JCEF 渲染。
function loadRuntime({container = {style: {zoom: ''}}} = {}) {
    const listeners = new Map();
    const body = {};
    let scrollbarTarget;
    const window = {
        addEventListener: (name, handler) => listeners.set(name, handler),
        removeEventListener: name => listeners.delete(name),
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
    return {window, container, listeners, body, get scrollbarTarget() { return scrollbarTarget; }};
}

test('共享运行时公开缩放入口，限制范围并支持恢复原比例', () => {
    const runtime = loadRuntime();
    const {window, container, listeners} = runtime;
    assert.equal(runtime.scrollbarTarget, runtime.body);
    listeners.get('load')();
    assert.equal(listeners.has('load'), false);
    assert.equal(container.style.zoom, '');
    for (const [input, expected] of [
        [1000, '500%'], [500, '500%'], [1, '25%'], [25, '25%'],
        [-1, '25%'], [Infinity, '500%'], [-Infinity, '25%'], [125.5, '125.5%'],
        [100, ''], [120, '120%'], [undefined, ''],
    ]) {
        window.setZoom(input);
        assert.equal(container.style.zoom, expected);
    }
});

test('运行时仅暴露绝对缩放，不再发送状态回调', () => {
    const {window} = loadRuntime();
    assert.deepEqual(Object.keys(window).sort(), ['addEventListener', 'removeEventListener', 'setZoom']);
    window.sendInfo = () => assert.fail('不应调用已退休的状态协议');
    window.setZoom(500);
});

test('宿主在 load 前设置的缩放不被默认初始化覆盖', () => {
    const {window, container, listeners} = loadRuntime();
    window.setZoom(150);
    listeners.get('load')?.();
    assert.equal(container.style.zoom, '150%');
    assert.equal(listeners.has('load'), false);
});

test('模板缺失容器时报告明确错误', () => {
    const {window, listeners} = loadRuntime({container: null});
    assert.throws(() => window.setZoom(100), /missing #container/);
    assert.throws(() => listeners.get('load')(), /missing #container/);
});
