// WebView 页面共享运行时：滚动条与宿主（Kotlin/JCEF）控制的缩放。
// 两个页面入口显式 import 本模块；vite 通过 manualChunks 将其拆为共享 chunk（两页各引用一份）。
// 与 Kotlin 侧的调用契约见 webview/README.md。
import 'normalize-css/normalize.css';
import 'overlayscrollbars/overlayscrollbars.css';
import {OverlayScrollbars} from 'overlayscrollbars';

// 缩放范围与 Kotlin WebDocumentView#zoom 的 coerceIn(25.0, 500.0) 保持一致，改动需双侧同步。
const MAX_ZOOM = 500;
const MIN_ZOOM = 25;

declare global {
    interface Window {
        setZoom: (zoom?: number) => void;
    }
}

function setZoom(zoom = 100) {
    zoom = normalizeZoom(zoom);
    const container = document.getElementById('container');
    if (!container) throw new Error('WebView template is missing #container');

    if (zoom === 100) {
        container.style.zoom = '';
    } else {
        container.style.zoom = `${zoom}%`;
    }

    // 宿主可能在 load 前设置缩放，此时不再用默认比例覆盖它。
    window.removeEventListener('load', init);
}

function normalizeZoom(zoom: number): number {
    return Math.min(Math.max(zoom, MIN_ZOOM), MAX_ZOOM);
}

function init() {
    setZoom();
}

window.addEventListener('load', init);

window.setZoom = setZoom;

OverlayScrollbars(document.body, {});
