// WebView 页面共享运行时：滚动条、缩放与宿主（Kotlin/JCEF）双向通信。
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
        zoomIn: () => void;
        zoomOut: () => void;
        actualZoom: () => void;
        sendInfo?: (json: string) => void;
    }
}

const gState = {
    zoom: 100,
};

function setZoom(zoom = 100) {
    zoom = normalizeZoom(zoom);
    const container = document.getElementById('container');
    if (!container) throw new Error('WebView template is missing #container');

    if (zoom === 100) {
        container.style.zoom = '';
    } else {
        container.style.zoom = `${zoom}%`;
    }

    gState.zoom = zoom;
    updateInfo();
}

function zoomIn() {
    setZoom(gState.zoom * 1.2);
}

function zoomOut() {
    setZoom(gState.zoom / 1.2);
}

function actualZoom() {
    setZoom(100);
}

function normalizeZoom(zoom: number): number {
    return Math.min(Math.max(zoom, MIN_ZOOM), MAX_ZOOM);
}

function init() {
    setZoom(gState.zoom);
    window.removeEventListener('load', init);
}

window.addEventListener('load', init);

// 保留可选状态回调；当前 Kotlin 宿主未注入 sendInfo，缩放值及边界由宿主自己维护。
function updateInfo() {
    const info = {
        zoom: gState.zoom,
        canZoomIn: gState.zoom < MAX_ZOOM,
        canZoomOut: gState.zoom > MIN_ZOOM,
    };
    window.sendInfo && window.sendInfo(JSON.stringify(info));
}

window.setZoom = setZoom;
window.zoomIn = zoomIn;
window.zoomOut = zoomOut;
window.actualZoom = actualZoom;

OverlayScrollbars(document.body, {});
